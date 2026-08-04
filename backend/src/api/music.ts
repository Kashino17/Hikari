import { readFileSync, renameSync, writeFileSync } from "node:fs";
import { rename, writeFile } from "node:fs/promises";
import { Readable } from "node:stream";
import type { FastifyInstance } from "fastify";
import { runYtDlp } from "../yt-dlp/client.js";

export interface MusicTrack {
  videoId: string;
  title: string;
  uploader: string;
  thumbnailUrl: string;
  durationSeconds: number;
  uploaderUrl?: string;
  views?: number;
}

/** Public Piped instances tried in order until one answers with usable JSON. */
const PIPED_INSTANCES = [
  "https://api.piped.private.coffee",
  "https://pipedapi.kavin.rocks",
  "https://pipedapi.reallyaweso.me",
  "https://api.piped.privacydev.net",
];

const SEARCH_CACHE_TTL_MS = 10 * 60 * 1000;
const ARTIST_CACHE_TTL_MS = 10 * 60 * 1000;
const SUGGESTIONS_CACHE_TTL_MS = 10 * 60 * 1000;
const FULL_SEARCH_CACHE_TTL_MS = 10 * 60 * 1000;
const PLAYLIST_CACHE_TTL_MS = 30 * 60 * 1000;
const STREAM_CACHE_TTL_MS = 6 * 60 * 60 * 1000; // googlevideo-URLs leben ~6 h; bei 403/410 löst der Proxy ohnehin frisch auf
const CACHE_MAX_ENTRIES = 200;
const SEARCH_INSTANCE_TIMEOUT_MS = 6000;
// Vorschläge müssen schnell da sein — kürzeres Timeout als die Suche.
const SUGGESTIONS_TIMEOUT_MS = 4000;
// Gestaffelter Start der parallelen Instanz-Suche: die erste Instanz sofort,
// weitere erst verzögert — gewinnt eine frühe Instanz, werden die späteren
// Starts per gemeinsamem AbortSignal abgebrochen (weniger Instanz-Traffic).
const SEARCH_STAGGER_MS = [0, 400, 1200, 2400];
// Timeout NUR für die Header-Phase des Upstream-Fetches im Audio-Proxy;
// nach Response-Eingang streamt der Body ohne Timeout weiter.
const AUDIO_HEADER_TIMEOUT_MS = 12_000;
// Verzögerungen vor Retries derselben Upstream-URL, bevor teuer neu aufgelöst wird.
const AUDIO_RETRY_DELAYS_MS = [300, 1000];
// Debounce für die Stream-Cache-Persistenz: bündelt Schreibzugriffe.
const STREAM_CACHE_SAVE_DEBOUNCE_MS = 5000;
const VIDEO_ID_RE = /^[A-Za-z0-9_-]{11}$/;
const CHANNEL_ID_RE = /^[A-Za-z0-9_-]{10,}$/;
// YouTube-Playlist-IDs: alphanumerisch + -_ (z.B. "OLAK5uy_..." / "PL...").
const PLAYLIST_ID_RE = /^[A-Za-z0-9_-]{2,64}$/;

/**
 * Piped kennt keinen Hörbuch-/Podcast-Filter — nur die feste Filterliste von
 * YouTube. Hörbücher und Podcasts laufen deshalb als gewöhnliche Videosuche;
 * die inhaltliche Einordnung (Stichwort im Query, Dauer-Heuristik) macht der
 * Client.
 */
const SEARCH_MODES = {
  music: "music_songs",
  audiobook: "videos",
  podcast: "videos",
  truecrime: "videos",
} as const;

type SearchMode = keyof typeof SEARCH_MODES;

function isSearchMode(value: string): value is SearchMode {
  return value in SEARCH_MODES;
}

/** Filter der typspezifischen Suche (/music/search/typed). */
const TYPED_SEARCH_FILTERS = {
  songs: "music_songs",
  albums: "music_albums",
  artists: "music_artists",
  playlists: "playlists",
} as const;

type TypedSearchType = keyof typeof TYPED_SEARCH_FILTERS;

function isTypedSearchType(value: string): value is TypedSearchType {
  return value in TYPED_SEARCH_FILTERS;
}

interface CacheEntry<T> { at: number; value: T }

function cacheGet<T>(map: Map<string, CacheEntry<T>>, key: string, ttlMs: number, now: number): T | undefined {
  const hit = map.get(key);
  if (hit && now - hit.at < ttlMs) return hit.value;
  if (hit) map.delete(key);
  return undefined;
}

function cachePut<T>(map: Map<string, CacheEntry<T>>, key: string, value: T, now: number): void {
  if (map.size >= CACHE_MAX_ENTRIES) {
    const oldest = map.keys().next().value;
    if (oldest !== undefined) map.delete(oldest);
  }
  map.set(key, { at: now, value });
}

/**
 * In-Flight-Dedup: gleichzeitige identische Anfragen (Prefetch + Play,
 * doppelt gerenderte App-Screens) teilen sich ein laufendes Promise.
 */
async function dedupInflight<T>(map: Map<string, Promise<T>>, key: string, run: () => Promise<T>): Promise<T> {
  const existing = map.get(key);
  if (existing) return existing;
  const pending = run();
  map.set(key, pending);
  try {
    return await pending;
  } finally {
    if (map.get(key) === pending) map.delete(key);
  }
}

/** Wartet `ms`, bricht aber sofort ab, sobald das Signal feuert (Staffel-Start). */
function waitForStagger(ms: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    const onAbort = () => {
      clearTimeout(timer);
      reject(signal.reason as unknown);
    };
    const timer = setTimeout(() => {
      signal.removeEventListener("abort", onAbort);
      resolve();
    }, ms);
    signal.addEventListener("abort", onAbort, { once: true });
  });
}

/** Lädt den persistierten Stream-URL-Cache; abgelaufene Einträge werden verworfen. */
function loadStreamCache(path: string | undefined, ttlMs: number): Map<string, CacheEntry<string>> {
  const map = new Map<string, CacheEntry<string>>();
  if (!path) return map;
  try {
    const raw = JSON.parse(readFileSync(path, "utf8")) as Record<string, CacheEntry<string>>;
    const now = Date.now();
    for (const [key, entry] of Object.entries(raw)) {
      if (map.size >= CACHE_MAX_ENTRIES) break; // gleiche Obergrenze wie cachePut
      if (typeof entry?.at === "number" && typeof entry?.value === "string" && now - entry.at < ttlMs) {
        map.set(key, entry);
      }
    }
  } catch {
    // keine oder korrupte Datei — mit leerem Cache starten
  }
  return map;
}

/** Schreibt den Stream-URL-Cache atomar (tmp + rename) — asynchron, für den Debounce-Timer. */
async function saveStreamCacheAsync(path: string, map: Map<string, CacheEntry<string>>): Promise<void> {
  try {
    const tmp = `${path}.tmp`;
    await writeFile(tmp, JSON.stringify(Object.fromEntries(map)));
    await rename(tmp, path);
  } catch {
    // Persistenz ist best-effort — ein Schreibfehler darf keinen Request brechen
  }
}

/** Synchrone Variante für Prozess-Shutdown und Server-Close. */
function saveStreamCacheSync(path: string, map: Map<string, CacheEntry<string>>): void {
  try {
    const tmp = `${path}.tmp`;
    writeFileSync(tmp, JSON.stringify(Object.fromEntries(map)));
    renameSync(tmp, path);
  } catch {
    // Persistenz ist best-effort — ein Schreibfehler darf keinen Request brechen
  }
}

interface PipedSearchItem {
  url?: string;
  type?: string;
  title?: string;
  /** Treffer-Name bei channel-/playlist-Items (statt title). */
  name?: string;
  uploaderName?: string;
  uploader?: string;
  uploaderUrl?: string;
  thumbnail?: string;
  duration?: number;
  views?: number;
  /** Abonnentenzahl bei channel-Items. */
  subscribers?: number;
  /** Video-Anzahl bei playlist-Items. */
  videos?: number;
}

/** Piped /channel/{id} — relatedStreams ist auf den Instanzen meist leer und wird ignoriert. */
interface PipedChannel {
  id?: string;
  name?: string;
  avatarUrl?: string | null;
  bannerUrl?: string | null;
  description?: string;
  subscriberCount?: number;
  verified?: boolean;
}

/** Normalisierte Artist-Seite (Antwort von /music/artist/:channelId). */
interface ArtistInfo {
  channelId: string;
  name: string;
  avatarUrl: string | null;
  bannerUrl: string | null;
  subscriberCount: number;
  description: string;
  verified: boolean;
}

/** Normalisierte Playlist (Antwort von /music/artist/:channelId/playlists). */
interface PlaylistSummary {
  playlistId: string;
  name: string;
  thumbnailUrl: string;
  videoCount: number;
  uploaderName: string;
}

/** Normalisierter Künstler-Treffer (/music/search/full, /music/search/typed). */
interface ArtistSearchResult {
  channelId: string;
  name: string;
  thumbnailUrl: string;
  subscribers: number;
}

/** Normalisierter Album-Treffer — Piped liefert Alben als playlist-Items. */
interface AlbumSearchResult {
  playlistId: string;
  name: string;
  artistName: string;
  thumbnailUrl: string;
  videoCount: number;
}

/** Antwort von /music/search/full. */
interface FullSearchResult {
  topResult:
    | ({ type: "artist" } & ArtistSearchResult)
    | ({ type: "song" } & MusicTrack)
    | ({ type: "album" } & AlbumSearchResult)
    | ({ type: "playlist" } & PlaylistSummary)
    | null;
  songs: MusicTrack[];
  artists: ArtistSearchResult[];
  albums: AlbumSearchResult[];
  playlists: PlaylistSummary[];
}

type TypedSearchResult =
  | MusicTrack[]
  | ArtistSearchResult[]
  | AlbumSearchResult[]
  | PlaylistSummary[];

function normalizeItem(item: PipedSearchItem): MusicTrack | null {
  const url = item.url ?? "";
  const videoId = url.includes("v=") ? url.split("v=")[1]?.split("&")[0] : url.split("/").pop();
  if (!videoId || !VIDEO_ID_RE.test(videoId)) return null;
  // duration <= 0 marks livestreams — not extractable as audio tracks
  if (typeof item.duration !== "number" || item.duration <= 0) return null;
  return {
    videoId,
    title: item.title ?? "",
    uploader: item.uploaderName ?? item.uploader ?? "",
    // YouTube CDN directly — Piped proxy thumbnails die with their instance
    thumbnailUrl: `https://i.ytimg.com/vi/${videoId}/mqdefault.jpg`,
    durationSeconds: item.duration,
    ...(item.uploaderUrl ? { uploaderUrl: item.uploaderUrl } : {}),
    // -1/0 heißt "unbekannt" (z.B. music_songs-Filter) — lieber weglassen
    ...(typeof item.views === "number" && item.views > 0 ? { views: item.views } : {}),
  };
}

/** Extrahiert die playlistId aus dem list=-Parameter einer Piped-URL. */
function playlistIdFromUrl(url: string): string | undefined {
  if (!url.includes("list=")) return undefined;
  const id = url.split("list=")[1]?.split("&")[0];
  return id || undefined;
}

/** Normalisiert einen channel-Treffer aus der Piped-Suche. */
function normalizeChannelItem(item: PipedSearchItem): ArtistSearchResult | null {
  const url = item.url ?? "";
  const channelId = url.startsWith("/channel/") ? url.slice(9).split(/[/?]/)[0] : undefined;
  if (!channelId || !CHANNEL_ID_RE.test(channelId)) return null;
  const name = item.name ?? item.title ?? "";
  if (!name) return null;
  return {
    channelId,
    name,
    thumbnailUrl: item.thumbnail ?? "",
    subscribers: typeof item.subscribers === "number" && item.subscribers > 0 ? item.subscribers : 0,
  };
}

/** Normalisiert einen playlist-Treffer als Album (uploaderName = artistName). */
function normalizeAlbumItem(item: PipedSearchItem): AlbumSearchResult | null {
  const playlistId = playlistIdFromUrl(item.url ?? "");
  if (!playlistId) return null;
  return {
    playlistId,
    name: item.name ?? "",
    artistName: item.uploaderName ?? "",
    thumbnailUrl: item.thumbnail ?? "",
    videoCount: typeof item.videos === "number" && item.videos > 0 ? item.videos : 0,
  };
}

/** Normalisiert einen playlist-Treffer als PlaylistSummary. */
function normalizePlaylistItem(item: PipedSearchItem): PlaylistSummary | null {
  const playlistId = playlistIdFromUrl(item.url ?? "");
  if (!playlistId) return null;
  return {
    playlistId,
    name: item.name ?? "",
    thumbnailUrl: item.thumbnail ?? "",
    videoCount: typeof item.videos === "number" && item.videos > 0 ? item.videos : 0,
    uploaderName: item.uploaderName ?? "",
  };
}

export interface MusicDeps {
  ytDlp?: typeof runYtDlp;
  fetchImpl?: typeof fetch;
  now?: () => number;
  /** Pfad für den persistenten Stream-URL-Cache; ohne Pfad nur In-Memory. */
  streamCachePath?: string;
  /** Verzögerungen vor Retries derselben Upstream-URL im Audio-Proxy (ms). */
  retryDelaysMs?: number[];
  /** Staffelung der parallelen Instanz-Suche pro Instanz (ms); leer = alle sofort. */
  searchStaggerMs?: number[];
}

export async function registerMusicRoutes(app: FastifyInstance, deps: MusicDeps = {}): Promise<void> {
  const ytDlp = deps.ytDlp ?? runYtDlp;
  const fetchImpl = deps.fetchImpl ?? fetch;
  const now = deps.now ?? Date.now;
  const retryDelays = deps.retryDelaysMs ?? AUDIO_RETRY_DELAYS_MS;
  const searchStagger = deps.searchStaggerMs ?? SEARCH_STAGGER_MS;
  const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

  const searchCache = new Map<string, CacheEntry<MusicTrack[]>>();
  const streamCache = loadStreamCache(deps.streamCachePath, STREAM_CACHE_TTL_MS);

  // Persistenz debouncen: jeder aufgelöste Song markiert den Cache nur als
  // dirty; ein unref'd Timer schreibt gebündelt nach 5 s, statt mitten im
  // Streaming-Prozess synchron die ganze Datei umzuschreiben.
  let streamCacheDirty = false;
  let streamCacheTimer: ReturnType<typeof setTimeout> | undefined;
  const flushStreamCache = () => {
    if (streamCacheTimer) {
      clearTimeout(streamCacheTimer);
      streamCacheTimer = undefined;
    }
    if (!deps.streamCachePath || !streamCacheDirty) return;
    streamCacheDirty = false;
    saveStreamCacheSync(deps.streamCachePath, streamCache);
  };
  const onSigterm = () => {
    flushStreamCache();
    process.exit(0);
  };
  if (deps.streamCachePath) {
    app.addHook("onClose", () => {
      flushStreamCache();
      process.off("beforeExit", flushStreamCache);
      process.off("SIGTERM", onSigterm);
    });
    process.once("beforeExit", flushStreamCache);
    process.once("SIGTERM", onSigterm);
  }
  const scheduleStreamCachePersist = () => {
    if (!deps.streamCachePath) return;
    streamCacheDirty = true;
    streamCacheTimer ??= setTimeout(() => {
      streamCacheTimer = undefined;
      if (!streamCacheDirty || !deps.streamCachePath) return;
      streamCacheDirty = false;
      void saveStreamCacheAsync(deps.streamCachePath, streamCache);
    }, STREAM_CACHE_SAVE_DEBOUNCE_MS);
    streamCacheTimer.unref();
  };

  // In-Flight-Dedup: gleichzeitige Auflösungen derselben videoId (Prefetch +
  // Play, Retries) teilen sich einen yt-dlp-Prozess.
  const inflightResolutions = new Map<string, Promise<string | undefined>>();
  // In-Flight-Dedup für identische Suchen (App rendert doppelt) — wie bei
  // inflightResolutions, keyed auf den Cache-Key (Query + Mode).
  const inflightSearches = new Map<string, Promise<MusicTrack[] | undefined>>();
  // Caches + In-Flight-Dedup der neuen Such-Endpunkte — gleiches Muster wie
  // searchCache/inflightSearches.
  const suggestionsCache = new Map<string, CacheEntry<string[]>>();
  const inflightSuggestions = new Map<string, Promise<string[] | undefined>>();
  const fullSearchCache = new Map<string, CacheEntry<FullSearchResult>>();
  const inflightFullSearches = new Map<string, Promise<FullSearchResult | undefined>>();
  const typedSearchCache = new Map<string, CacheEntry<TypedSearchResult>>();
  const inflightTypedSearches = new Map<string, Promise<TypedSearchResult | undefined>>();
  const playlistCache = new Map<string, CacheEntry<MusicTrack[]>>();
  const inflightPlaylists = new Map<string, Promise<MusicTrack[] | undefined>>();

  app.get<{ Querystring: { q?: string; mode?: string } }>("/music/search", async (req, reply) => {
    const q = (req.query.q ?? "").trim();
    if (!q) return reply.code(400).send({ error: "missing query parameter q" });
    const modeParam = req.query.mode ?? "music";
    if (!isSearchMode(modeParam)) {
      return reply.code(400).send({ error: `unknown mode "${modeParam}"` });
    }
    const filter = SEARCH_MODES[modeParam];

    const cacheKey = `${modeParam}:${q.toLowerCase()}`;
    const cached = cacheGet(searchCache, cacheKey, SEARCH_CACHE_TTL_MS, now());
    if (cached) {
      reply.header("cache-control", "public, max-age=300");
      return cached;
    }

    const tracks = await dedupInflight(inflightSearches, cacheKey, () => searchPiped(q, filter));
    if (!tracks) return reply.code(502).send({ error: "all music search providers unavailable" });
    cachePut(searchCache, cacheKey, tracks, now());
    reply.header("cache-control", "public, max-age=300");
    return tracks;
  });

  /**
   * Generischer Instanz-Wettlauf: alle Instanzen konkurrieren — die erste mit
   * brauchbarem Payload gewinnt, statt bei toten Instanzen sequentiell bis zu
   * 24 s abzuwarten. Gestaffelter Start (erste sofort, weitere verzögert); der
   * Gewinner bricht über das gemeinsame Signal die verbliebenen Starts und
   * laufenden Anfragen ab. `run` wirft bei toten/degradierten Instanzen.
   */
  async function raceInstances<T>(
    run: (base: string, signal: AbortSignal) => Promise<T>,
    timeoutMs: number,
  ): Promise<T | undefined> {
    const roundAbort = new AbortController();
    const attempts = PIPED_INSTANCES.map(async (base, i) => {
      const delay = searchStagger[i] ?? 0;
      if (delay > 0) await waitForStagger(delay, roundAbort.signal);
      const result = await run(
        base,
        AbortSignal.any([AbortSignal.timeout(timeoutMs), roundAbort.signal]),
      );
      // Gewonnen — Rest der Runde abbrechen, bevor Promise.any auflöst.
      roundAbort.abort();
      return result;
    });
    try {
      return await Promise.any(attempts);
    } catch {
      return undefined;
    }
  }

  /**
   * Rohsuche bei Piped: liefert die ungefilterten items der Gewinner-Instanz.
   * `isUsable` entscheidet, ob ein Payload als Treffer gilt — degradierte
   * Instanzen liefern z.B. Listen ohne passende Treffer.
   */
  async function searchPipedItems(
    q: string,
    filter: string,
    isUsable: (items: PipedSearchItem[]) => boolean = (items) => items.length > 0,
  ): Promise<PipedSearchItem[] | undefined> {
    return raceInstances(async (base, signal) => {
      const res = await fetchImpl(
        `${base}/search?q=${encodeURIComponent(q)}&filter=${filter}`,
        { signal },
      );
      if (!res.ok) throw new Error(`search failed on ${base}`);
      const body = (await res.json()) as { items?: PipedSearchItem[] };
      if (!Array.isArray(body.items)) throw new Error(`bad payload from ${base}`);
      if (!isUsable(body.items)) throw new Error(`degraded instance ${base}`);
      return body.items;
    }, SEARCH_INSTANCE_TIMEOUT_MS);
  }

  /** Songsuche: rohe Items auf Streams filtern und zu MusicTracks normalisieren. */
  async function searchPiped(q: string, filter: string): Promise<MusicTrack[] | undefined> {
    const isStream = (i: PipedSearchItem) => i.type === "stream" || i.url?.includes("v=");
    const items = await searchPipedItems(q, filter, (candidates) => candidates.some(isStream));
    if (!items) return undefined;
    const tracks = items
      .filter(isStream)
      .map(normalizeItem)
      .filter((t): t is MusicTrack => t !== null);
    return tracks.length > 0 ? tracks : undefined;
  }

  // --- Autovervollständigung ---
  // Vorschläge sind optional: schlagen alle Instanzen fehl, wird trotzdem 200
  // mit leerer Liste geliefert statt 502 (und nichts gecacht).

  app.get<{ Querystring: { q?: string } }>("/music/suggestions", async (req, reply) => {
    const q = (req.query.q ?? "").trim();
    if (!q) return reply.code(400).send({ error: "missing query parameter q" });

    const cacheKey = q.toLowerCase();
    const cached = cacheGet(suggestionsCache, cacheKey, SUGGESTIONS_CACHE_TTL_MS, now());
    if (cached) {
      reply.header("cache-control", "public, max-age=300");
      return cached;
    }

    const suggestions = await dedupInflight(inflightSuggestions, cacheKey, () =>
      fetchSuggestions(q),
    );
    if (!suggestions) {
      reply.header("cache-control", "public, max-age=300");
      return [];
    }
    cachePut(suggestionsCache, cacheKey, suggestions, now());
    reply.header("cache-control", "public, max-age=300");
    return suggestions;
  });

  /** Piped /suggestions — liefert ein JSON-Array von Strings. */
  async function fetchSuggestions(q: string): Promise<string[] | undefined> {
    return raceInstances(async (base, signal) => {
      const res = await fetchImpl(`${base}/suggestions?query=${encodeURIComponent(q)}`, { signal });
      if (!res.ok) throw new Error(`suggestions failed on ${base}`);
      const body = (await res.json()) as unknown;
      if (!Array.isArray(body)) throw new Error(`bad payload from ${base}`);
      return body.filter((s): s is string => typeof s === "string");
    }, SUGGESTIONS_TIMEOUT_MS);
  }

  // --- YouTube-Music-artige Vollsuche ---
  // Vier Teilsuchen (Songs, Alben, Artists, Playlists) laufen parallel;
  // einzelne fehlschlagende Teilsuchen liefern leere Sektionen, erst wenn
  // alle vier scheitern gibt es 502.

  app.get<{ Querystring: { q?: string } }>("/music/search/full", async (req, reply) => {
    const q = (req.query.q ?? "").trim();
    if (!q) return reply.code(400).send({ error: "missing query parameter q" });

    const cacheKey = q.toLowerCase();
    const cached = cacheGet(fullSearchCache, cacheKey, FULL_SEARCH_CACHE_TTL_MS, now());
    if (cached) {
      reply.header("cache-control", "public, max-age=300");
      return cached;
    }

    const result = await dedupInflight(inflightFullSearches, cacheKey, () => fullSearch(q));
    if (!result) return reply.code(502).send({ error: "all music search providers unavailable" });
    cachePut(fullSearchCache, cacheKey, result, now());
    reply.header("cache-control", "public, max-age=300");
    return result;
  });

  async function fullSearch(q: string): Promise<FullSearchResult | undefined> {
    const [songItems, albumItems, artistItems, playlistItems] = await Promise.all([
      searchPipedItems(q, "music_songs"),
      searchPipedItems(q, "music_albums"),
      searchPipedItems(q, "music_artists"),
      searchPipedItems(q, "playlists"),
    ]);
    if (!songItems && !albumItems && !artistItems && !playlistItems) return undefined;

    const songs = (songItems ?? [])
      .filter((i) => i.type === "stream" || i.url?.includes("v="))
      .map(normalizeItem)
      .filter((t): t is MusicTrack => t !== null)
      .slice(0, 10);
    const artists = (artistItems ?? [])
      .filter((i) => i.type === "channel")
      .map(normalizeChannelItem)
      .filter((a): a is ArtistSearchResult => a !== null)
      .slice(0, 6);
    const albums = (albumItems ?? [])
      .filter((i) => i.type === "playlist")
      .map(normalizeAlbumItem)
      .filter((a): a is AlbumSearchResult => a !== null)
      .slice(0, 6);
    const playlists = (playlistItems ?? [])
      .filter((i) => i.type === "playlist")
      .map(normalizePlaylistItem)
      .filter((p): p is PlaylistSummary => p !== null)
      .slice(0, 6);

    // Top-Treffer-Heuristik: bevorzugt ein Artist, dessen Name im Query
    // vorkommt (oder umgekehrt), sonst der erste Song, sonst nichts.
    const needle = q.toLowerCase();
    const topArtist = artists.find(
      (a) => a.name.toLowerCase().includes(needle) || needle.includes(a.name.toLowerCase()),
    );
    const topResult: FullSearchResult["topResult"] = topArtist
      ? { type: "artist", ...topArtist }
      : songs[0]
        ? { type: "song", ...songs[0] }
        : null;
    return { topResult, songs, artists, albums, playlists };
  }

  // --- Typspezifische Suche (größere Liste eines Typs, Limit 20) ---

  app.get<{ Querystring: { q?: string; type?: string } }>(
    "/music/search/typed",
    async (req, reply) => {
      const q = (req.query.q ?? "").trim();
      if (!q) return reply.code(400).send({ error: "missing query parameter q" });
      const typeParam = req.query.type ?? "songs";
      if (!isTypedSearchType(typeParam)) {
        return reply.code(400).send({ error: `unknown type "${typeParam}"` });
      }

      const cacheKey = `${typeParam}:${q.toLowerCase()}`;
      const cached = cacheGet(typedSearchCache, cacheKey, FULL_SEARCH_CACHE_TTL_MS, now());
      if (cached) {
        reply.header("cache-control", "public, max-age=300");
        return cached;
      }

      const result = await dedupInflight(inflightTypedSearches, cacheKey, () =>
        typedSearch(q, typeParam),
      );
      if (!result) return reply.code(502).send({ error: "all music search providers unavailable" });
      cachePut(typedSearchCache, cacheKey, result, now());
      reply.header("cache-control", "public, max-age=300");
      return result;
    },
  );

  /** Gleiche Normalisierung wie /music/search/full, nur ohne Sektions-Limit. */
  async function typedSearch(q: string, type: TypedSearchType): Promise<TypedSearchResult | undefined> {
    const items = await searchPipedItems(q, TYPED_SEARCH_FILTERS[type]);
    if (!items) return undefined;
    switch (type) {
      case "songs":
        return items
          .filter((i) => i.type === "stream" || i.url?.includes("v="))
          .map(normalizeItem)
          .filter((t): t is MusicTrack => t !== null)
          .slice(0, 20);
      case "artists":
        return items
          .filter((i) => i.type === "channel")
          .map(normalizeChannelItem)
          .filter((a): a is ArtistSearchResult => a !== null)
          .slice(0, 20);
      case "albums":
        return items
          .filter((i) => i.type === "playlist")
          .map(normalizeAlbumItem)
          .filter((a): a is AlbumSearchResult => a !== null)
          .slice(0, 20);
      case "playlists":
        return items
          .filter((i) => i.type === "playlist")
          .map(normalizePlaylistItem)
          .filter((p): p is PlaylistSummary => p !== null)
          .slice(0, 20);
    }
  }

  // --- Playlist-Inhalt ---

  app.get<{ Params: { playlistId: string } }>("/music/playlist/:playlistId", async (req, reply) => {
    const { playlistId } = req.params;
    if (!PLAYLIST_ID_RE.test(playlistId)) return reply.code(400).send({ error: "invalid playlistId" });

    const cached = cacheGet(playlistCache, playlistId, PLAYLIST_CACHE_TTL_MS, now());
    if (cached) {
      reply.header("cache-control", "public, max-age=300");
      return cached;
    }

    const tracks = await dedupInflight(inflightPlaylists, playlistId, () =>
      fetchPlaylistTracks(playlistId),
    );
    if (!tracks) return reply.code(502).send({ error: "all music providers unavailable" });
    cachePut(playlistCache, playlistId, tracks, now());
    reply.header("cache-control", "public, max-age=300");
    return tracks;
  });

  /** Piped /playlists/{id} — die Tracks stecken in relatedStreams. */
  async function fetchPlaylistTracks(playlistId: string): Promise<MusicTrack[] | undefined> {
    return raceInstances(async (base, signal) => {
      const res = await fetchImpl(`${base}/playlists/${encodeURIComponent(playlistId)}`, { signal });
      if (!res.ok) throw new Error(`playlist failed on ${base}`);
      const body = (await res.json()) as { relatedStreams?: PipedSearchItem[] };
      if (!Array.isArray(body.relatedStreams)) throw new Error(`bad payload from ${base}`);
      const tracks = body.relatedStreams
        .filter((i) => i.type === "stream" || i.url?.includes("v="))
        .map(normalizeItem)
        .filter((t): t is MusicTrack => t !== null);
      if (tracks.length === 0) throw new Error(`degraded instance ${base}`);
      return tracks;
    }, SEARCH_INSTANCE_TIMEOUT_MS);
  }

  app.get<{ Params: { videoId: string }; Querystring: { force?: string } }>(
    "/music/stream/:videoId",
    async (req, reply) => {
      const { videoId } = req.params;
      if (!VIDEO_ID_RE.test(videoId)) return reply.code(400).send({ error: "invalid videoId" });

      // force=1 umgeht den Cache — für Wiederholversuche, wenn eine gecachte
      // googlevideo-URL mitten im Playback stirbt.
      const force = req.query.force === "1" || req.query.force === "true";
      const url = await resolveAudioUrl(videoId, force);
      if (!url) return reply.code(502).send({ error: "audio extraction failed" });
      return { url };
    },
  );

  async function resolveAudioUrl(videoId: string, force: boolean): Promise<string | undefined> {
    const cached = force ? undefined : cacheGet(streamCache, videoId, STREAM_CACHE_TTL_MS, now());
    if (cached) return cached;
    const existing = inflightResolutions.get(videoId);
    if (existing) return existing;
    const pending = extractAudioUrl(videoId);
    inflightResolutions.set(videoId, pending);
    try {
      return await pending;
    } finally {
      if (inflightResolutions.get(videoId) === pending) inflightResolutions.delete(videoId);
    }
  }

  async function extractAudioUrl(videoId: string): Promise<string | undefined> {
    try {
      const result = await ytDlp(
        [
          "--no-playlist",
          "-f", "bestaudio[ext=m4a]/bestaudio/best",
          "-g",
          `https://www.youtube.com/watch?v=${videoId}`,
        ],
        { timeoutMs: 45_000, maxRetries: 1 },
      );
      const url = result.stdout.trim().split("\n")[0];
      if (!url?.startsWith("http")) return undefined;
      cachePut(streamCache, videoId, url, now());
      scheduleStreamCachePersist();
      return url;
    } catch {
      return undefined;
    }
  }

  // Streaming-Proxy: das Handy holt Audio-Bytes vom Mac statt direkt von
  // googlevideo — die URLs dort sind an Netz/IP des Auflösers gebunden und
  // spielen von fremden Netzen aus nicht zuverlässig ab.
  app.get<{ Params: { videoId: string } }>("/music/audio/:videoId", async (req, reply) => {
    const { videoId } = req.params;
    if (!VIDEO_ID_RE.test(videoId)) return reply.code(400).send({ error: "invalid videoId" });

    const range = req.headers.range;
    let resolved = false;
    for (const force of [false, true]) {
      const url = await resolveAudioUrl(videoId, force);
      if (!url) continue;
      resolved = true;

      let upstream: Response | undefined;
      // Transiente Fetch-Exceptions (Reset, Header-Timeout): erst dieselbe URL
      // kurz retryen — sie lebt meist noch — und erst dann teuer neu auflösen.
      for (let attempt = 0; ; attempt++) {
        // Timeout NUR für die Header-Phase: hängt googlevideo bei Connect/Headern,
        // hängt sonst der Request ewig. Nach Response-Eingang wird der Timer
        // gecleart — der Body streamt ohne Timeout, so lange der Song spielt.
        const headerAbort = new AbortController();
        const headerTimer = setTimeout(() => headerAbort.abort(), AUDIO_HEADER_TIMEOUT_MS);
        try {
          upstream = await fetchImpl(url, {
            headers: range ? { range } : {},
            signal: headerAbort.signal,
          });
          break;
        } catch {
          if (attempt >= retryDelays.length) break;
          await sleep(retryDelays[attempt] ?? 0);
        } finally {
          clearTimeout(headerTimer);
        }
      }
      if (!upstream) continue;
      // 403/410 = abgelaufene oder netzfremde URL → einmal frisch auflösen
      if (upstream.status === 403 || upstream.status === 410) continue;
      if (!upstream.ok && upstream.status !== 206) continue;

      reply.code(upstream.status);
      for (const name of ["content-type", "content-length", "content-range", "accept-ranges"]) {
        const value = upstream.headers.get(name);
        if (value) reply.header(name, value);
      }
      if (!upstream.headers.get("accept-ranges")) reply.header("accept-ranges", "bytes");
      return reply.send(upstream.body ? Readable.fromWeb(upstream.body) : "");
    }
    return reply
      .code(502)
      .send({ error: resolved ? "upstream audio fetch failed" : "audio extraction failed" });
  });

  // --- Artist-Seiten ---
  // /playlists/{id} und /channels/tabs sind auf den Instanzen degradiert —
  // Top-Songs und Playlists kommen deshalb über die Suche statt über Kanal-Tabs.
  // In-Memory-Cache (10 min) wie searchCache: sonst ist jede Artist-Seite ein
  // voller Piped-Roundtrip. In-Flight-Dedup für doppelt gerenderte Screens.

  const artistCache = new Map<string, CacheEntry<ArtistInfo>>();
  const artistTopCache = new Map<string, CacheEntry<MusicTrack[]>>();
  const artistPlaylistCache = new Map<string, CacheEntry<PlaylistSummary[]>>();
  const inflightArtists = new Map<string, Promise<ArtistInfo | undefined>>();
  const inflightArtistTops = new Map<string, Promise<MusicTrack[] | undefined>>();
  const inflightArtistPlaylists = new Map<string, Promise<PlaylistSummary[] | undefined>>();

  app.get<{ Params: { channelId: string } }>("/music/artist/:channelId", async (req, reply) => {
    const { channelId } = req.params;
    if (!CHANNEL_ID_RE.test(channelId)) return reply.code(400).send({ error: "invalid channelId" });

    const cached = cacheGet(artistCache, channelId, ARTIST_CACHE_TTL_MS, now());
    if (cached) {
      reply.header("cache-control", "public, max-age=300");
      return cached;
    }
    const artist = await dedupInflight(inflightArtists, channelId, () => fetchArtist(channelId));
    if (!artist) return reply.code(502).send({ error: "all music providers unavailable" });
    cachePut(artistCache, channelId, artist, now());
    reply.header("cache-control", "public, max-age=300");
    return artist;
  });

  async function fetchArtist(channelId: string): Promise<ArtistInfo | undefined> {
    for (const base of PIPED_INSTANCES) {
      try {
        const res = await fetchImpl(`${base}/channel/${channelId}`, { signal: AbortSignal.timeout(6000) });
        if (!res.ok) continue;
        const body = (await res.json()) as PipedChannel;
        if (!body || typeof body.name !== "string" || body.name.length === 0) continue;
        return {
          channelId,
          name: body.name,
          avatarUrl: body.avatarUrl ?? null,
          bannerUrl: body.bannerUrl ?? null,
          subscriberCount:
            typeof body.subscriberCount === "number" && body.subscriberCount > 0
              ? body.subscriberCount
              : 0,
          description: (body.description ?? "").slice(0, 500),
          verified: body.verified === true,
        };
      } catch {
        // dead instance — try the next one
      }
    }
    return undefined;
  }

  app.get<{ Params: { channelId: string }; Querystring: { name?: string } }>(
    "/music/artist/:channelId/top",
    async (req, reply) => {
      const { channelId } = req.params;
      if (!CHANNEL_ID_RE.test(channelId)) return reply.code(400).send({ error: "invalid channelId" });
      const name = (req.query.name ?? "").trim();
      if (!name) return reply.code(400).send({ error: "missing query parameter name" });

      const cacheKey = `${channelId}:${name.toLowerCase()}`;
      const cached = cacheGet(artistTopCache, cacheKey, ARTIST_CACHE_TTL_MS, now());
      if (cached) {
        reply.header("cache-control", "public, max-age=300");
        return cached;
      }
      const tracks = await dedupInflight(inflightArtistTops, cacheKey, () =>
        fetchArtistTop(channelId, name),
      );
      if (!tracks) return reply.code(502).send({ error: "all music providers unavailable" });
      cachePut(artistTopCache, cacheKey, tracks, now());
      reply.header("cache-control", "public, max-age=300");
      return tracks;
    },
  );

  async function fetchArtistTop(channelId: string, name: string): Promise<MusicTrack[] | undefined> {
    for (const base of PIPED_INSTANCES) {
      try {
        const res = await fetchImpl(
          `${base}/search?q=${encodeURIComponent(name)}&filter=videos`,
          { signal: AbortSignal.timeout(6000) },
        );
        if (!res.ok) continue;
        const body = (await res.json()) as { items?: PipedSearchItem[] };
        if (!Array.isArray(body.items)) continue;
        const streams = body.items
          .filter((i) => i.type === "stream")
          .map(normalizeItem)
          .filter((t): t is MusicTrack => t !== null);
        if (streams.length === 0) continue; // instance degraded — try the next one
        // Treffer des eigenen Kanals zuerst; zu wenige davon → alle Streams.
        const own = streams.filter((t) => t.uploaderUrl === `/channel/${channelId}`);
        const pool = own.length >= 3 ? own : streams;
        return pool
          .sort((a, b) => (b.views ?? 0) - (a.views ?? 0))
          .slice(0, 20);
      } catch {
        // dead instance — try the next one
      }
    }
    return undefined;
  }

  app.get<{ Params: { channelId: string }; Querystring: { name?: string } }>(
    "/music/artist/:channelId/playlists",
    async (req, reply) => {
      const { channelId } = req.params;
      if (!CHANNEL_ID_RE.test(channelId)) return reply.code(400).send({ error: "invalid channelId" });
      const name = (req.query.name ?? "").trim();
      if (!name) return reply.code(400).send({ error: "missing query parameter name" });

      const cacheKey = `${channelId}:${name.toLowerCase()}`;
      const cached = cacheGet(artistPlaylistCache, cacheKey, ARTIST_CACHE_TTL_MS, now());
      if (cached) {
        reply.header("cache-control", "public, max-age=300");
        return cached;
      }
      const playlists = await dedupInflight(inflightArtistPlaylists, cacheKey, () =>
        fetchArtistPlaylists(channelId, name),
      );
      if (!playlists) return reply.code(502).send({ error: "all music providers unavailable" });
      cachePut(artistPlaylistCache, cacheKey, playlists, now());
      reply.header("cache-control", "public, max-age=300");
      return playlists;
    },
  );

  async function fetchArtistPlaylists(
    channelId: string,
    name: string,
  ): Promise<PlaylistSummary[] | undefined> {
    for (const base of PIPED_INSTANCES) {
      try {
        const res = await fetchImpl(
          `${base}/search?q=${encodeURIComponent(name)}&filter=playlists`,
          { signal: AbortSignal.timeout(6000) },
        );
        if (!res.ok) continue;
        const body = (await res.json()) as { items?: PipedSearchItem[] };
        if (!Array.isArray(body.items)) continue;
        const playlists = body.items
          .filter((i) => i.type === "playlist")
          .map((i) => {
            const url = i.url ?? "";
            const playlistId = url.includes("list=")
              ? url.split("list=")[1]?.split("&")[0]
              : undefined;
            if (!playlistId) return null;
            return {
              playlistId,
              name: i.name ?? "",
              thumbnailUrl: i.thumbnail ?? "",
              videoCount: typeof i.videos === "number" && i.videos > 0 ? i.videos : 0,
              uploaderName: i.uploaderName ?? "",
              uploaderUrl: i.uploaderUrl,
            };
          })
          .filter((p): p is NonNullable<typeof p> => p !== null);
        // Eigene Kanal-Playlists zuerst; keine davon → alle Treffer.
        const own = playlists.filter((p) => p.uploaderUrl === `/channel/${channelId}`);
        const pool = own.length > 0 ? own : playlists;
        return pool
          .slice(0, 20)
          .map(({ uploaderUrl: _uploaderUrl, ...rest }) => rest);
      } catch {
        // dead instance — try the next one
      }
    }
    return undefined;
  }
}
