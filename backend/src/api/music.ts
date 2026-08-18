import type { FastifyInstance } from "fastify";
import { proxyMediaStream } from "../stream/proxy.js";
import {
  type CacheEntry,
  cacheGet,
  cachePut,
  dedupInflight,
  loadStreamCache,
  saveStreamCacheAsync,
  saveStreamCacheSync,
} from "../stream/url-cache.js";
import { runPreferEmbedded, runYtDlp } from "../yt-dlp/client.js";
import {
  type ArtistPage,
  type HomeFeed,
  itArtistPage,
  itChannelPlaylists,
  itChannelVideos,
  itHome,
  itPlaylistTracks,
  itRelated,
  itSearchFull,
  itSearchSongs,
  itSearchTyped,
  itSuggestions,
} from "./music-innertube.js";

/** Einzelner (Mit-)Interpret eines Tracks — channelId nur bei UC…-Kanälen. */
export interface TrackArtist {
  name: string;
  channelId: string | null;
}

export interface MusicTrack {
  videoId: string;
  title: string;
  /** Anzeige-String ALLER Interpreten ("A, B & C") — Separatoren wie von YTM. */
  uploader: string;
  thumbnailUrl: string;
  durationSeconds: number;
  uploaderUrl?: string;
  views?: number;
  /** Alle Interpreten einzeln (Kollaborationen) — nur aus Innertube-Pfaden. */
  artists?: TrackArtist[];
}

/**
 * Suchvorschlag der Autovervollständigung: entweder reine Text-Query
 * (kind "query", alle Id-/Bild-Felder null) oder ein Entity-Treffer mit
 * Miniatur-Thumbnail — Songs/Artists/Alben/Playlists passend zur Eingabe.
 */
export interface MusicSuggestion {
  text: string;
  kind: "query" | "song" | "artist" | "album" | "playlist" | "video";
  thumbnailUrl: string | null;
  subtitle: string | null;
  videoId: string | null;
  channelId: string | null;
  playlistId: string | null;
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
const RELATED_CACHE_TTL_MS = 10 * 60 * 1000;
const HOME_CACHE_TTL_MS = 30 * 60 * 1000;
const STREAM_CACHE_TTL_MS = 6 * 60 * 60 * 1000; // googlevideo-URLs leben ~6 h; bei 403/410 löst der Proxy ohnehin frisch auf
// Vorlauf, bevor die Video-URL eines laufenden Songs im Hintergrund aufgelöst
// wird: erst soll das Audio sauber anlaufen, danach ist der Player-Umschalter
// ohne Wartezeit bereit.
const VIDEO_PREWARM_DELAY_MS = 3_000;
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

/**
 * Mindestdauer pro Modus für die Vollsuche — kurze Clips/Trailer sind selten
 * Hörbuch oder Podcast-Folge (gleiche Heuristik wie im Client). Greift der
 * Filter zu hart (<4 Treffer), gewinnt die ungefilterte Liste.
 */
const MODE_MIN_DURATION_S: Record<SearchMode, number> = {
  music: 0,
  audiobook: 600,
  podcast: 300,
  truecrime: 300,
};

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
export interface ArtistInfo {
  channelId: string;
  name: string;
  avatarUrl: string | null;
  bannerUrl: string | null;
  subscriberCount: number;
  description: string;
  verified: boolean;
}

/** Normalisierte Playlist (Antwort von /music/artist/:channelId/playlists). */
export interface PlaylistSummary {
  playlistId: string;
  name: string;
  thumbnailUrl: string;
  videoCount: number;
  uploaderName: string;
}

/** Normalisierter Künstler-Treffer (/music/search/full, /music/search/typed). */
export interface ArtistSearchResult {
  channelId: string;
  name: string;
  thumbnailUrl: string;
  subscribers: number;
}

/** Normalisierter Album-Treffer — Piped liefert Alben als playlist-Items. */
export interface AlbumSearchResult {
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
    subscribers:
      typeof item.subscribers === "number" && item.subscribers > 0 ? item.subscribers : 0,
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
  /**
   * Vorlauf, nach dem beim Abspielen eines Songs die Video-URL im Hintergrund
   * aufgelöst wird (ms). `null` schaltet das Vorwärmen ab — in Tests sinnvoll,
   * sonst zählen die Prefetch-Aufrufe bei yt-dlp-Erwartungen mit.
   */
  videoPrewarmDelayMs?: number | null;
}

export async function registerMusicRoutes(
  app: FastifyInstance,
  deps: MusicDeps = {},
): Promise<void> {
  const ytDlp = deps.ytDlp ?? runYtDlp;
  const fetchImpl = deps.fetchImpl ?? fetch;
  const now = deps.now ?? Date.now;
  const retryDelays = deps.retryDelaysMs ?? AUDIO_RETRY_DELAYS_MS;
  const searchStagger = deps.searchStaggerMs ?? SEARCH_STAGGER_MS;
  const videoPrewarmDelayMs =
    deps.videoPrewarmDelayMs === undefined ? VIDEO_PREWARM_DELAY_MS : deps.videoPrewarmDelayMs;

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
  const suggestionsCache = new Map<string, CacheEntry<MusicSuggestion[]>>();
  const inflightSuggestions = new Map<string, Promise<MusicSuggestion[] | undefined>>();
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

    // Musik läuft Innertube-first (zuverlässig, korrekte Song-Metadaten);
    // die Video-Modi (Hörbuch/Podcast/True-Crime) kann WEB_REMIX nicht und
    // bleiben auf Piped.
    const tracks = await dedupInflight(inflightSearches, cacheKey, async () =>
      modeParam === "music"
        ? ((await itSearchSongs(fetchImpl, q)) ?? (await searchPiped(q, filter)))
        : searchPiped(q, filter),
    );
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
      const res = await fetchImpl(`${base}/search?q=${encodeURIComponent(q)}&filter=${filter}`, {
        signal,
      });
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

    const suggestions = await dedupInflight(
      inflightSuggestions,
      cacheKey,
      async () => (await itSuggestions(fetchImpl, q)) ?? (await fetchSuggestions(q)),
    );
    if (!suggestions) {
      reply.header("cache-control", "public, max-age=300");
      return [];
    }
    cachePut(suggestionsCache, cacheKey, suggestions, now());
    reply.header("cache-control", "public, max-age=300");
    return suggestions;
  });

  /** Piped /suggestions — Strings werden zu reinen Query-Vorschlägen. */
  async function fetchSuggestions(q: string): Promise<MusicSuggestion[] | undefined> {
    return raceInstances(async (base, signal) => {
      const res = await fetchImpl(`${base}/suggestions?query=${encodeURIComponent(q)}`, { signal });
      if (!res.ok) throw new Error(`suggestions failed on ${base}`);
      const body = (await res.json()) as unknown;
      if (!Array.isArray(body)) throw new Error(`bad payload from ${base}`);
      return body
        .filter((s): s is string => typeof s === "string")
        .map((text) => ({
          text,
          kind: "query" as const,
          thumbnailUrl: null,
          subtitle: null,
          videoId: null,
          channelId: null,
          playlistId: null,
        }));
    }, SUGGESTIONS_TIMEOUT_MS);
  }

  // --- YouTube-Music-artige Vollsuche ---
  // Vier Teilsuchen (Songs, Alben, Artists, Playlists) laufen parallel;
  // einzelne fehlschlagende Teilsuchen liefern leere Sektionen, erst wenn
  // alle vier scheitern gibt es 502. Die Video-Modi (Hörbuch/Podcast/
  // True-Crime) bekommen dieselben Sektionen aus der Piped-Videosuche.

  app.get<{ Querystring: { q?: string; mode?: string } }>(
    "/music/search/full",
    async (req, reply) => {
      const q = (req.query.q ?? "").trim();
      if (!q) return reply.code(400).send({ error: "missing query parameter q" });
      const modeParam = req.query.mode ?? "music";
      if (!isSearchMode(modeParam)) {
        return reply.code(400).send({ error: `unknown mode "${modeParam}"` });
      }

      const cacheKey = `${modeParam}:${q.toLowerCase()}`;
      const cached = cacheGet(fullSearchCache, cacheKey, FULL_SEARCH_CACHE_TTL_MS, now());
      if (cached) {
        reply.header("cache-control", "public, max-age=300");
        return cached;
      }

      const result = await dedupInflight(inflightFullSearches, cacheKey, () =>
        modeParam === "music" ? fullSearch(q) : fullVideoSearch(q, modeParam),
      );
      if (!result) return reply.code(502).send({ error: "all music search providers unavailable" });
      cachePut(fullSearchCache, cacheKey, result, now());
      reply.header("cache-control", "public, max-age=300");
      return result;
    },
  );

  /**
   * Vollsuche der Video-Modi: Videos als Songs-Sektion (Dauer-Heuristik wie
   * im Client), Kanäle als Artists, Playlists wie gehabt — Alben gibt es in
   * diesen Welten nicht.
   */
  async function fullVideoSearch(
    q: string,
    mode: SearchMode,
  ): Promise<FullSearchResult | undefined> {
    const isStream = (i: PipedSearchItem) => i.type === "stream" || i.url?.includes("v=");
    const [videoItems, channelItems, playlistItems] = await Promise.all([
      searchPipedItems(q, "videos", (candidates) => candidates.some(isStream)),
      searchPipedItems(q, "channels"),
      searchPipedItems(q, "playlists"),
    ]);
    if (!videoItems && !channelItems && !playlistItems) return undefined;

    const all = (videoItems ?? [])
      .filter(isStream)
      .map(normalizeItem)
      .filter((t): t is MusicTrack => t !== null);
    const minSeconds = MODE_MIN_DURATION_S[mode];
    const longEnough = all.filter((t) => t.durationSeconds >= minSeconds);
    const songs = (longEnough.length >= 4 ? longEnough : all).slice(0, 10);
    const artists = (channelItems ?? [])
      .filter((i) => i.type === "channel")
      .map(normalizeChannelItem)
      .filter((a): a is ArtistSearchResult => a !== null)
      .slice(0, 6);
    const playlists = (playlistItems ?? [])
      .filter((i) => i.type === "playlist")
      .map(normalizePlaylistItem)
      .filter((p): p is PlaylistSummary => p !== null)
      .slice(0, 6);

    return { topResult: pickTopResult(q, songs, artists), songs, artists, albums: [], playlists };
  }

  /**
   * Top-Treffer-Heuristik: bevorzugt ein Artist, dessen Name im Query
   * vorkommt (oder umgekehrt), sonst der erste Song, sonst nichts.
   */
  function pickTopResult(
    q: string,
    songs: MusicTrack[],
    artists: ArtistSearchResult[],
  ): FullSearchResult["topResult"] {
    const needle = q.toLowerCase();
    const topArtist = artists.find(
      (a) => a.name.toLowerCase().includes(needle) || needle.includes(a.name.toLowerCase()),
    );
    return topArtist
      ? { type: "artist", ...topArtist }
      : songs[0]
        ? { type: "song", ...songs[0] }
        : null;
  }

  async function fullSearch(q: string): Promise<FullSearchResult | undefined> {
    // Innertube-first — liefert echte YTM-Sektionen; Piped nur noch Fallback.
    const it = await itSearchFull(fetchImpl, q);
    if (it) return { topResult: pickTopResult(q, it.songs, it.artists), ...it };

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

    return { topResult: pickTopResult(q, songs, artists), songs, artists, albums, playlists };
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
  async function typedSearch(
    q: string,
    type: TypedSearchType,
  ): Promise<TypedSearchResult | undefined> {
    const it = await itSearchTyped(fetchImpl, q, type);
    if (it) return it;
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
    if (!PLAYLIST_ID_RE.test(playlistId))
      return reply.code(400).send({ error: "invalid playlistId" });

    const cached = cacheGet(playlistCache, playlistId, PLAYLIST_CACHE_TTL_MS, now());
    if (cached) {
      reply.header("cache-control", "public, max-age=300");
      return cached;
    }

    const tracks = await dedupInflight(
      inflightPlaylists,
      playlistId,
      async () =>
        (await itPlaylistTracks(fetchImpl, playlistId)) ?? (await fetchPlaylistTracks(playlistId)),
    );
    if (!tracks) return reply.code(502).send({ error: "all music providers unavailable" });
    cachePut(playlistCache, playlistId, tracks, now());
    reply.header("cache-control", "public, max-age=300");
    return tracks;
  });

  /** Piped /playlists/{id} — die Tracks stecken in relatedStreams. */
  async function fetchPlaylistTracks(playlistId: string): Promise<MusicTrack[] | undefined> {
    return raceInstances(async (base, signal) => {
      const res = await fetchImpl(`${base}/playlists/${encodeURIComponent(playlistId)}`, {
        signal,
      });
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

  // Negativ-Cache gegen yt-dlp-Stürme: schlägt die Auflösung fehl, hämmert
  // die App im Sekundentakt nach — jeder Versuch ein frischer yt-dlp-Lauf,
  // der YouTubes Rate-Limit weiter reizt und die Ausfall-Welle verlängert.
  // 20 s Sperre pro videoId beruhigt das, ohne echte Retries zu verhindern.
  const resolveFailUntil = new Map<string, number>();
  const RESOLVE_FAIL_COOLDOWN_MS = 20_000;

  async function resolveAudioUrl(videoId: string, force: boolean): Promise<string | undefined> {
    const cached = force ? undefined : cacheGet(streamCache, videoId, STREAM_CACHE_TTL_MS, now());
    if (cached) return cached;
    if (!force && (resolveFailUntil.get(videoId) ?? 0) > now()) return undefined;
    const existing = inflightResolutions.get(videoId);
    if (existing) return existing;
    const pending = extractAudioUrl(videoId).then((url) => {
      if (!url) resolveFailUntil.set(videoId, now() + RESOLVE_FAIL_COOLDOWN_MS);
      else resolveFailUntil.delete(videoId);
      return url;
    });
    inflightResolutions.set(videoId, pending);
    try {
      return await pending;
    } finally {
      if (inflightResolutions.get(videoId) === pending) inflightResolutions.delete(videoId);
    }
  }

  async function extractAudioUrl(videoId: string): Promise<string | undefined> {
    try {
      // web_embedded-first (siehe runPreferEmbedded): nur diese URLs sind
      // voll rangebar — alle anderen Clients kappt googlevideo nach ~768 KiB.
      const result = await runPreferEmbedded(
        ytDlp,
        [
          "--no-playlist",
          // IPv4 erzwingen: macOS rotiert IPv6-Privacy-Adressen — die
          // googlevideo-URL bindet an die Auflöser-Adresse, der Node-Fetch
          // geht danach über eine ANDERE Temporär-Adresse raus → 403 → 502.
          // Die IPv4 (hinter NAT) ist stabil, damit passt die Bindung immer.
          "-4",
          "-f",
          "bestaudio[ext=m4a]/bestaudio/best",
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
    } catch (err) {
      // Grund sichtbar machen: yt-dlp-Fehler wurden hier bisher verschluckt —
      // die 502-Welle vom 18.08. war deshalb nur per Raten diagnostizierbar.
      app.log.warn(
        { videoId, err: err instanceof Error ? err.message.slice(0, 500) : String(err) },
        "audio resolve failed",
      );
      return undefined;
    }
  }

  // Gemeinsame Optionen für den extrahierten proxyMediaStream (stream/proxy.js):
  // injizierte fetch-Implementierung + Test-Retry-Delays aus MusicDeps.
  const proxyOpts = {
    fetchImpl,
    headerTimeoutMs: AUDIO_HEADER_TIMEOUT_MS,
    retryDelaysMs: retryDelays,
  };

  // Wellen-Brecher: googlevideo drosselt diese IP zeitweise komplett (403
  // für ALLES, mehrere Minuten — Diagnose 18.08.). Scheitern ≥3 verschiedene
  // Videos binnen 30 s upstream, ist die Welle da: 60 s lang sofort 503
  // liefern statt mit weiteren yt-dlp-Läufen und Fetches die Drossel zu
  // füttern. Retry-After sagt der App, wann es sich wieder lohnt.
  const upstreamFails: { videoId: string; at: number }[] = [];
  let waveUntil = 0;
  const noteUpstreamFail = (videoId: string) => {
    const t = now();
    upstreamFails.push({ videoId, at: t });
    while (upstreamFails.length > 0 && (upstreamFails[0]?.at ?? 0) < t - 30_000) {
      upstreamFails.shift();
    }
    const distinct = new Set(upstreamFails.map((f) => f.videoId));
    if (distinct.size >= 3) {
      waveUntil = t + 60_000;
      app.log.warn({ distinct: distinct.size }, "googlevideo wave detected — cooling down 60s");
    }
  };

  app.get<{ Params: { videoId: string } }>("/music/audio/:videoId", async (req, reply) => {
    const { videoId } = req.params;
    if (!VIDEO_ID_RE.test(videoId)) return reply.code(400).send({ error: "invalid videoId" });
    if (waveUntil > now()) {
      const retryAfterS = Math.ceil((waveUntil - now()) / 1000);
      reply.header("retry-after", String(retryAfterS));
      return reply.code(503).send({ error: "youtube throttling — retry later" });
    }
    // Songstart (kein Range oder ab Byte 0) — kein Seek/Nachladen mitten drin:
    // guter Moment, die Video-URL für den Player-Umschalter vorzuwärmen.
    const range = req.headers.range;
    if (!range || /^bytes=0-$/.test(range.trim())) prewarmVideoUrl(videoId);

    const result = await proxyMediaStream(
      reply,
      range,
      (force) => resolveAudioUrl(videoId, force),
      "audio",
      proxyOpts,
    );
    if (reply.statusCode === 502) {
      // Auch Upstream-403 (nicht nur Auflösungsfehler) sperrt die videoId kurz.
      resolveFailUntil.set(videoId, now() + RESOLVE_FAIL_COOLDOWN_MS);
      noteUpstreamFail(videoId);
    }
    return result;
  });

  // --- Video-Stream (Audio↔Video-Umschalter im Player) ---
  // Eigener URL-Cache getrennt vom Audio-Cache: dieselbe videoId hat zwei
  // verschiedene googlevideo-URLs (bestaudio vs. muxed MP4).

  const videoStreamCache = new Map<string, CacheEntry<string>>();
  const inflightVideoResolutions = new Map<string, Promise<string | undefined>>();

  async function resolveVideoUrl(videoId: string, force: boolean): Promise<string | undefined> {
    const cached = force
      ? undefined
      : cacheGet(videoStreamCache, videoId, STREAM_CACHE_TTL_MS, now());
    if (cached) return cached;
    return dedupInflight(inflightVideoResolutions, videoId, () => extractVideoUrl(videoId));
  }

  // Video-URL vorwärmen, während der Song läuft: das Auflösen dauert 4–10 s,
  // und genau die wartet man sonst beim Umschalten Audio→Video im Player ab
  // (Nutzerbeschwerde 18.08.2026: „teilweise 30+ Sekunden"). Höchstens ein
  // Vorlauf gleichzeitig, nicht während einer Drossel-Welle — die Drossel
  // soll nicht mit Extra-Auflösungen gefüttert werden.
  let prewarmInFlight = false;
  function prewarmVideoUrl(videoId: string): void {
    if (videoPrewarmDelayMs === null || prewarmInFlight) return;
    if (waveUntil > now()) return;
    if (cacheGet(videoStreamCache, videoId, STREAM_CACHE_TTL_MS, now())) return;
    prewarmInFlight = true;
    const timer = setTimeout(() => {
      void resolveVideoUrl(videoId, false)
        .catch(() => undefined)
        .finally(() => {
          prewarmInFlight = false;
        });
    }, videoPrewarmDelayMs);
    // Der Vorlauf darf den Prozess nicht am Beenden hindern.
    timer.unref?.();
  }

  async function extractVideoUrl(videoId: string): Promise<string | undefined> {
    try {
      // web_embedded-first wie beim Audio-Pfad (voll rangebare URLs).
      const result = await runPreferEmbedded(
        ytDlp,
        [
          "--no-playlist",
          // IPv4 wie beim Audio-Pfad — Bindung an die stabile NAT-IPv4.
          "-4",
          "-f",
          // Muxed MP4 (Video+Audio in einer Datei), NUR progressives HTTPS:
          // ohne [protocol=https] löst yt-dlp gern HLS-Manifeste (m3u8) auf,
          // deren googlevideo-Segmente vom Handy aus nicht abspielbar sind —
          // ExoPlayer zeigt dann nur das stehende Thumbnail. Format 18 ist
          // YouTubes immer vorhandenes progressives 360p-MP4.
          "best[height<=720][ext=mp4][vcodec!=none][acodec!=none][protocol=https]/18/best[ext=mp4][protocol=https]",
          "-g",
          `https://www.youtube.com/watch?v=${videoId}`,
        ],
        { timeoutMs: 45_000, maxRetries: 1 },
      );
      const url = result.stdout.trim().split("\n")[0];
      if (!url?.startsWith("http")) return undefined;
      // Sicherheitsnetz: Manifest-URLs (HLS/DASH) sind hier nie brauchbar.
      if (url.includes(".m3u8") || url.includes("/manifest/")) return undefined;
      cachePut(videoStreamCache, videoId, url, now());
      return url;
    } catch (err) {
      app.log.warn(
        { videoId, err: err instanceof Error ? err.message.slice(0, 500) : String(err) },
        "video resolve failed",
      );
      return undefined;
    }
  }

  app.get<{ Params: { videoId: string } }>("/music/video/:videoId", async (req, reply) => {
    const { videoId } = req.params;
    if (!VIDEO_ID_RE.test(videoId)) return reply.code(400).send({ error: "invalid videoId" });
    return proxyMediaStream(
      reply,
      req.headers.range,
      (force) => resolveVideoUrl(videoId, force),
      "video",
      proxyOpts,
    );
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

  // Die komplette Innertube-Artist-Seite wird EINMAL geholt und von
  // /artist, /top, /playlists und /page gemeinsam genutzt — sonst wäre jeder
  // der drei Alt-Endpunkte ein eigener browse-Roundtrip.
  const artistPageCache = new Map<string, CacheEntry<ArtistPage>>();
  const inflightArtistPages = new Map<string, Promise<ArtistPage | undefined>>();

  async function getArtistPage(channelId: string): Promise<ArtistPage | undefined> {
    const cached = cacheGet(artistPageCache, channelId, ARTIST_CACHE_TTL_MS, now());
    if (cached) return cached;
    const page = await dedupInflight(inflightArtistPages, channelId, () =>
      itArtistPage(fetchImpl, channelId),
    );
    if (page) cachePut(artistPageCache, channelId, page, now());
    return page;
  }

  app.get<{ Params: { channelId: string } }>("/music/artist/:channelId", async (req, reply) => {
    const { channelId } = req.params;
    if (!CHANNEL_ID_RE.test(channelId)) return reply.code(400).send({ error: "invalid channelId" });

    const cached = cacheGet(artistCache, channelId, ARTIST_CACHE_TTL_MS, now());
    if (cached) {
      reply.header("cache-control", "public, max-age=300");
      return cached;
    }
    const artist =
      (await getArtistPage(channelId))?.artist ??
      (await dedupInflight(inflightArtists, channelId, () => fetchArtist(channelId)));
    if (!artist) return reply.code(502).send({ error: "all music providers unavailable" });
    cachePut(artistCache, channelId, artist, now());
    reply.header("cache-control", "public, max-age=300");
    return artist;
  });

  /**
   * Kanal-Uploads als Top-Songs — für normale YouTube-Kanäle (True Crime,
   * Podcasts …), die auf YouTube Music keinen Songs-Shelf haben. Bevorzugt
   * der direkte Kanal-Browse (braucht keinen Namen); die Piped-Namenssuche
   * strikt gefiltert auf den eigenen Kanal bleibt Fallback.
   */
  async function fetchChannelUploads(
    channelId: string,
    name: string,
  ): Promise<MusicTrack[] | undefined> {
    const uploads = await itChannelVideos(fetchImpl, channelId);
    if (uploads && uploads.length > 0) return uploads;
    if (!name) return undefined;
    return fetchArtistTop(channelId, name);
  }

  // Komplette Artist-Seite in einem Call — Top-Songs, Alben, Singles,
  // Playlists und ähnliche Künstler, alles garantiert vom richtigen Kanal.
  app.get<{ Params: { channelId: string }; Querystring: { name?: string } }>(
    "/music/artist/:channelId/page",
    async (req, reply) => {
      const { channelId } = req.params;
      if (!CHANNEL_ID_RE.test(channelId))
        return reply.code(400).send({ error: "invalid channelId" });
      const name = (req.query.name ?? "").trim();

      let page = await getArtistPage(channelId);
      if (page && page.topSongs.length === 0) {
        // Kein YTM-Artist (z. B. True-Crime-Kanal): die Seite hätte nur einen
        // Header — Kanal-Uploads nachladen, damit sie nicht leer bleibt.
        // `latest` behält die Upload-Reihenfolge, topSongs sortiert nach Views.
        const uploads = await dedupInflight(inflightArtistTops, `${channelId}:uploads`, () =>
          fetchChannelUploads(channelId, name),
        );
        if (uploads && uploads.length > 0) {
          const popular = [...uploads].sort((a, b) => (b.views ?? 0) - (a.views ?? 0));
          page = { ...page, topSongs: popular, latest: uploads };
          // Kanal-Playlists über den Playlists-Tab — best-effort.
          if (page.playlists.length === 0) {
            const channelPlaylists = await dedupInflight(
              inflightArtistPlaylists,
              `${channelId}:tabs`,
              () => itChannelPlaylists(fetchImpl, channelId),
            );
            if (channelPlaylists && channelPlaylists.length > 0) {
              page = { ...page, playlists: channelPlaylists };
            }
          }
          cachePut(artistPageCache, channelId, page, now());
        }
      } else if (page && page.latest.length === 0) {
        // Music-Artist: YTM liefert nur die beliebtesten Songs — die
        // "Neuste"-Liste kommt best-effort aus den Kanal-Uploads.
        const uploads = await dedupInflight(inflightArtistTops, `${channelId}:uploads`, () =>
          itChannelVideos(fetchImpl, channelId),
        );
        if (uploads && uploads.length > 0) {
          page = { ...page, latest: uploads.slice(0, 25) };
          cachePut(artistPageCache, channelId, page, now());
        }
      }
      if (page) {
        reply.header("cache-control", "public, max-age=300");
        return page;
      }
      // Innertube down: Minimal-Seite aus den Piped-Fallbacks bauen.
      const artist = await dedupInflight(inflightArtists, channelId, () => fetchArtist(channelId));
      if (!artist) return reply.code(502).send({ error: "all music providers unavailable" });
      const topSongs =
        (await dedupInflight(inflightArtistTops, `${channelId}:${artist.name.toLowerCase()}`, () =>
          fetchArtistTop(channelId, artist.name),
        )) ?? [];
      reply.header("cache-control", "public, max-age=300");
      return {
        artist,
        topSongs,
        latest: [],
        albums: [],
        singles: [],
        playlists: [],
        related: [],
      } satisfies ArtistPage;
    },
  );

  async function fetchArtist(channelId: string): Promise<ArtistInfo | undefined> {
    for (const base of PIPED_INSTANCES) {
      try {
        const res = await fetchImpl(`${base}/channel/${channelId}`, {
          signal: AbortSignal.timeout(6000),
        });
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
      if (!CHANNEL_ID_RE.test(channelId))
        return reply.code(400).send({ error: "invalid channelId" });
      const name = (req.query.name ?? "").trim();
      if (!name) return reply.code(400).send({ error: "missing query parameter name" });

      const cacheKey = `${channelId}:${name.toLowerCase()}`;
      const cached = cacheGet(artistTopCache, cacheKey, ARTIST_CACHE_TTL_MS, now());
      if (cached) {
        reply.header("cache-control", "public, max-age=300");
        return cached;
      }
      let tracks = (await getArtistPage(channelId))?.topSongs;
      if (tracks && tracks.length === 0) {
        // Kein YTM-Artist: Kanal-Uploads statt leerer Liste (siehe /page).
        tracks =
          (await dedupInflight(inflightArtistTops, `${channelId}:uploads`, () =>
            fetchChannelUploads(channelId, name),
          )) ?? tracks;
      }
      tracks ??= await dedupInflight(inflightArtistTops, cacheKey, () =>
        fetchArtistTop(channelId, name),
      );
      if (!tracks) return reply.code(502).send({ error: "all music providers unavailable" });
      // Leere Listen nicht cachen — der Uploads-Fallback darf es beim
      // nächsten Aufruf erneut versuchen.
      if (tracks.length > 0) cachePut(artistTopCache, cacheKey, tracks, now());
      reply.header("cache-control", "public, max-age=300");
      return tracks;
    },
  );

  async function fetchArtistTop(
    channelId: string,
    name: string,
  ): Promise<MusicTrack[] | undefined> {
    for (const base of PIPED_INSTANCES) {
      try {
        const res = await fetchImpl(`${base}/search?q=${encodeURIComponent(name)}&filter=videos`, {
          signal: AbortSignal.timeout(6000),
        });
        if (!res.ok) continue;
        const body = (await res.json()) as { items?: PipedSearchItem[] };
        if (!Array.isArray(body.items)) continue;
        const streams = body.items
          .filter((i) => i.type === "stream")
          .map(normalizeItem)
          .filter((t): t is MusicTrack => t !== null);
        if (streams.length === 0) continue; // instance degraded — try the next one
        // NUR Treffer des eigenen Kanals — fremde Uploader mit ähnlichem Namen
        // haben auf einer Artist-Seite nichts verloren. Lieber wenige, aber
        // korrekte Songs als ein voller Pool mit Fremdmaterial.
        return streams
          .filter((t) => t.uploaderUrl === `/channel/${channelId}`)
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
      if (!CHANNEL_ID_RE.test(channelId))
        return reply.code(400).send({ error: "invalid channelId" });
      const name = (req.query.name ?? "").trim();
      if (!name) return reply.code(400).send({ error: "missing query parameter name" });

      const cacheKey = `${channelId}:${name.toLowerCase()}`;
      const cached = cacheGet(artistPlaylistCache, cacheKey, ARTIST_CACHE_TTL_MS, now());
      if (cached) {
        reply.header("cache-control", "public, max-age=300");
        return cached;
      }
      const page = await getArtistPage(channelId);
      const playlists =
        (page
          ? [...page.playlists, ...page.albums, ...page.singles].map(
              ({ playlistId, name: plName, thumbnailUrl, videoCount }) => ({
                playlistId,
                name: plName,
                thumbnailUrl,
                videoCount,
                uploaderName: page.artist.name,
              }),
            )
          : undefined) ??
        (await dedupInflight(inflightArtistPlaylists, cacheKey, () =>
          fetchArtistPlaylists(channelId, name),
        ));
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
        // NUR eigene Kanal-Playlists — fremde Treffer sind auf einer
        // Artist-Seite Chaos, nicht Inhalt.
        return playlists
          .filter((p) => p.uploaderUrl === `/channel/${channelId}`)
          .slice(0, 20)
          .map(({ uploaderUrl: _uploaderUrl, ...rest }) => rest);
      } catch {
        // dead instance — try the next one
      }
    }
    return undefined;
  }

  // --- Empfehlungen (Innertube-only, kein Piped-Äquivalent) ---

  const relatedCache = new Map<string, CacheEntry<MusicTrack[]>>();
  const inflightRelated = new Map<string, Promise<MusicTrack[] | undefined>>();

  // Radio-Queue zu einem Seed-Song — Grundlage für "Ähnliche Songs",
  // Song-Radio und personalisierte Mixe aus dem Hörverlauf.
  app.get<{ Params: { videoId: string } }>("/music/related/:videoId", async (req, reply) => {
    const { videoId } = req.params;
    if (!VIDEO_ID_RE.test(videoId)) return reply.code(400).send({ error: "invalid videoId" });

    const cached = cacheGet(relatedCache, videoId, RELATED_CACHE_TTL_MS, now());
    if (cached) {
      reply.header("cache-control", "public, max-age=300");
      return cached;
    }
    const tracks = await dedupInflight(inflightRelated, videoId, () =>
      itRelated(fetchImpl, videoId),
    );
    if (!tracks) return reply.code(502).send({ error: "related lookup failed" });
    cachePut(relatedCache, videoId, tracks, now());
    reply.header("cache-control", "public, max-age=300");
    return tracks;
  });

  const homeCache = new Map<string, CacheEntry<HomeFeed>>();
  const inflightHome = new Map<string, Promise<HomeFeed | undefined>>();

  // Kuratierter YTM-Home-Feed (Quick Picks, Playlists, Alben, Charts).
  app.get("/music/home", async (_req, reply) => {
    const cached = cacheGet(homeCache, "home", HOME_CACHE_TTL_MS, now());
    if (cached) {
      reply.header("cache-control", "public, max-age=600");
      return cached;
    }
    const feed = await dedupInflight(inflightHome, "home", () => itHome(fetchImpl));
    if (!feed) return reply.code(502).send({ error: "home feed unavailable" });
    cachePut(homeCache, "home", feed, now());
    reply.header("cache-control", "public, max-age=600");
    return feed;
  });
}
