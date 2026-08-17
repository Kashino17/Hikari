/**
 * Innertube-Anbindung an YouTube Music (music.youtube.com/youtubei/v1) —
 * derselbe keylose API-Zugang, den der Web-Client von YouTube Music nutzt.
 * Ersetzt die unzuverlässigen Piped-Instanzen als primäre Quelle; Piped
 * bleibt in music.ts nur noch Fallback.
 *
 * Alle Funktionen geben bei Fehlern `undefined` zurück statt zu werfen —
 * die Aufrufer entscheiden über Fallback oder 502.
 */
import type {
  AlbumSearchResult,
  ArtistInfo,
  ArtistSearchResult,
  MusicTrack,
  PlaylistSummary,
} from "./music.js";

const IT_BASE = "https://music.youtube.com/youtubei/v1";
const IT_TIMEOUT_MS = 8000;

/** WEB_REMIX = YouTube-Music-Webclient; hl/gl steuern Sprache der Feeds. */
const IT_CONTEXT = {
  client: {
    clientName: "WEB_REMIX",
    clientVersion: "1.20241127.01.00",
    hl: "de",
    gl: "DE",
  },
} as const;

/**
 * Vorgefertigte Filter-Params der YTM-Suche (Konstruktion wie in ytmusicapi:
 * "EgWKAQ" + Typ-Kürzel + "AWoMEA4QChADEAQQCRAF"; Playlists haben ein
 * eigenes Format).
 */
export const IT_SEARCH_PARAMS = {
  songs: "EgWKAQIIAWoMEA4QChADEAQQCRAF",
  albums: "EgWKAQIYAWoMEA4QChADEAQQCRAF",
  artists: "EgWKAQIgAWoMEA4QChADEAQQCRAF",
  playlists: "Eg-KAQwIABAAGAAgACgBMABqChAEEAMQCRAFEAo%3D",
} as const;

export interface AlbumPageItem {
  /** Für /music/playlist/:id nutzbar — bei Alben die MPREb-Browse-Id. */
  playlistId: string;
  name: string;
  artistName: string;
  thumbnailUrl: string;
  videoCount: number;
  year?: number;
  /** Original-Browse-Id (MPREb…) — identisch zu playlistId bei Alben. */
  browseId?: string;
}

export interface ArtistPage {
  artist: ArtistInfo;
  topSongs: MusicTrack[];
  albums: AlbumPageItem[];
  singles: AlbumPageItem[];
  playlists: PlaylistSummary[];
  related: ArtistSearchResult[];
}

export interface HomeItem {
  kind: "song" | "playlist" | "album" | "artist";
  song?: MusicTrack;
  playlist?: PlaylistSummary;
  album?: AlbumSearchResult;
  artist?: ArtistSearchResult;
}

export interface HomeSection {
  title: string;
  items: HomeItem[];
}

export interface HomeFeed {
  sections: HomeSection[];
}

export interface FullSearchSections {
  songs: MusicTrack[];
  artists: ArtistSearchResult[];
  albums: AlbumSearchResult[];
  playlists: PlaylistSummary[];
}

type Dict = Record<string, unknown>;

function isDict(v: unknown): v is Dict {
  return typeof v === "object" && v !== null && !Array.isArray(v);
}

/** Tiefensuche in Dokument-Reihenfolge: alle Werte unter `key` einsammeln. */
export function findAllByKey(root: unknown, key: string, out: unknown[] = []): unknown[] {
  if (Array.isArray(root)) {
    for (const v of root) findAllByKey(v, key, out);
    return out;
  }
  if (isDict(root)) {
    for (const [k, v] of Object.entries(root)) {
      if (k === key) out.push(v);
      findAllByKey(v, key, out);
    }
  }
  return out;
}

/** Pfad-Navigation ohne Wirf-Gefahr: nav(obj, "a", 0, "b"). */
function nav(root: unknown, ...path: (string | number)[]): unknown {
  let cur: unknown = root;
  for (const step of path) {
    if (typeof step === "number") {
      if (!Array.isArray(cur)) return undefined;
      cur = cur[step];
    } else {
      if (!isDict(cur)) return undefined;
      cur = cur[step];
    }
  }
  return cur;
}

/** Verkettet die texts eines {runs:[{text}]}-Knotens. */
function runText(node: unknown): string {
  const runs = nav(node, "runs");
  if (!Array.isArray(runs)) return "";
  return runs.map((r) => (isDict(r) && typeof r.text === "string" ? r.text : "")).join("");
}

/** Alle run-Objekte eines Knotens (für browseEndpoint-Suche in Untertiteln). */
function runsOf(node: unknown): Dict[] {
  const runs = nav(node, "runs");
  if (!Array.isArray(runs)) return [];
  return runs.filter(isDict);
}

/** "3:33" | "1:02:33" → Sekunden; alles andere → undefined. */
function durationToSeconds(text: string): number | undefined {
  if (!/^\d{1,2}(:\d{2}){1,2}$/.test(text.trim())) return undefined;
  const parts = text
    .trim()
    .split(":")
    .map((p) => Number.parseInt(p, 10));
  return parts.reduce((acc, p) => acc * 60 + p, 0);
}

/** Größtes Thumbnail eines Knotens (musicThumbnailRenderer o. ä.). */
function bestThumbnail(node: unknown): string {
  const lists = findAllByKey(node, "thumbnails").filter(Array.isArray) as unknown[][];
  for (const list of lists) {
    const last = list[list.length - 1];
    const url = nav(last, "url");
    if (typeof url === "string" && url.startsWith("http")) return url;
  }
  return "";
}

/**
 * Grobe Zahl aus Texten wie "1,23 Mio. Abonnenten" / "1.2M subscribers".
 * Nur fürs Anzeigen gedacht — im Zweifel 0.
 */
function approxCount(text: string): number {
  const m = /([\d.,]+)\s*(Mrd|Mio|Tsd|B|M|K)?/i.exec(text);
  if (!m?.[1]) return 0;
  const raw = m[1];
  // deutsches Format: Punkt = Tausender, Komma = Dezimal
  const normalized = raw.includes(",")
    ? raw.replace(/\./g, "").replace(",", ".")
    : raw.replace(/,/g, "");
  const value = Number.parseFloat(normalized);
  if (!Number.isFinite(value)) return 0;
  const unit = (m[2] ?? "").toLowerCase();
  const mult =
    unit === "mrd" || unit === "b"
      ? 1e9
      : unit === "mio" || unit === "m"
        ? 1e6
        : unit === "tsd" || unit === "k"
          ? 1e3
          : 1;
  return Math.round(value * mult);
}

/** Erster browseId in einem Knoten (navigationEndpoint.browseEndpoint). */
function browseIdOf(node: unknown): string | undefined {
  const id = nav(node, "navigationEndpoint", "browseEndpoint", "browseId");
  return typeof id === "string" ? id : undefined;
}

const VIDEO_ID_RE = /^[A-Za-z0-9_-]{11}$/;

function songThumb(videoId: string): string {
  return `https://i.ytimg.com/vi/${videoId}/mqdefault.jpg`;
}

/** POST an einen Innertube-Endpoint; wirft bei HTTP-/Parse-Fehlern. */
async function itCall(fetchImpl: typeof fetch, endpoint: string, payload: Dict): Promise<unknown> {
  const res = await fetchImpl(`${IT_BASE}/${endpoint}?prettyPrint=false`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      origin: "https://music.youtube.com",
      "user-agent":
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36",
    },
    body: JSON.stringify({ context: IT_CONTEXT, ...payload }),
    signal: AbortSignal.timeout(IT_TIMEOUT_MS),
  });
  if (!res.ok) throw new Error(`innertube ${endpoint} -> ${res.status}`);
  return (await res.json()) as unknown;
}

// ————— Item-Parser —————

/** musicResponsiveListItemRenderer → MusicTrack (Songs in Suche/Playlist/Shelf). */
function parseSongItem(item: unknown, fallbackUploader = ""): MusicTrack | null {
  const videoId =
    (nav(item, "playlistItemData", "videoId") as string | undefined) ??
    (findAllByKey(item, "watchEndpoint")
      .map((w) => nav(w, "videoId"))
      .find((v) => typeof v === "string") as string | undefined);
  if (typeof videoId !== "string" || !VIDEO_ID_RE.test(videoId)) return null;

  const flexCols = (nav(item, "flexColumns") as unknown[] | undefined) ?? [];
  const col0 = nav(flexCols[0], "musicResponsiveListItemFlexColumnRenderer", "text");
  const title = runText(col0).trim();
  if (!title) return null;

  // Interpret: erster Run in Spalte 2 mit Kanal-browseId, sonst erster Run.
  const col1Runs = runsOf(nav(flexCols[1], "musicResponsiveListItemFlexColumnRenderer", "text"));
  const artistRun =
    col1Runs.find((r) => (browseIdOf(r) ?? "").startsWith("UC")) ??
    col1Runs.find((r) => typeof r.text === "string" && (r.text as string).trim() !== "•");
  const uploader = ((artistRun?.text as string | undefined) ?? fallbackUploader).trim();
  const channelId = browseIdOf(artistRun);

  // Dauer: letzter Zeit-förmige Run irgendwo im Item (fixedColumns oder Spalte 2).
  const allTexts = findAllByKey(item, "text").filter((t): t is string => typeof t === "string");
  const runTexts = findAllByKey(item, "runs")
    .filter(Array.isArray)
    .flatMap((runs) => (runs as unknown[]).map((r) => nav(r, "text")))
    .filter((t): t is string => typeof t === "string");
  const durationText = [...allTexts, ...runTexts]
    .reverse()
    .find((t) => durationToSeconds(t) !== undefined);
  const durationSeconds = durationText ? (durationToSeconds(durationText) ?? 0) : 0;

  return {
    videoId,
    title,
    uploader,
    thumbnailUrl: songThumb(videoId),
    durationSeconds,
    ...(channelId ? { uploaderUrl: `/channel/${channelId}` } : {}),
  };
}

/** musicResponsiveListItemRenderer (Artist-Suche) → ArtistSearchResult. */
function parseArtistItem(item: unknown): ArtistSearchResult | null {
  const channelId = browseIdOf(item);
  if (!channelId?.startsWith("UC")) return null;
  const flexCols = (nav(item, "flexColumns") as unknown[] | undefined) ?? [];
  const name = runText(
    nav(flexCols[0], "musicResponsiveListItemFlexColumnRenderer", "text"),
  ).trim();
  if (!name) return null;
  const subtitle = runText(nav(flexCols[1], "musicResponsiveListItemFlexColumnRenderer", "text"));
  return {
    channelId,
    name,
    thumbnailUrl: bestThumbnail(item),
    subscribers: /abonnent|subscriber/i.test(subtitle) ? approxCount(subtitle) : 0,
  };
}

/** musicResponsiveListItemRenderer (Album-Suche, browseId MPREb…) → AlbumSearchResult. */
function parseAlbumSearchItem(item: unknown): AlbumSearchResult | null {
  const browseId = browseIdOf(item);
  if (!browseId?.startsWith("MPREb")) return null;
  const flexCols = (nav(item, "flexColumns") as unknown[] | undefined) ?? [];
  const name = runText(
    nav(flexCols[0], "musicResponsiveListItemFlexColumnRenderer", "text"),
  ).trim();
  if (!name) return null;
  const subtitleRuns = runsOf(
    nav(flexCols[1], "musicResponsiveListItemFlexColumnRenderer", "text"),
  );
  const artistRun =
    subtitleRuns.find((r) => (browseIdOf(r) ?? "").startsWith("UC")) ??
    subtitleRuns.find((r) => {
      const t = (r.text as string | undefined)?.trim() ?? "";
      return t !== "" && t !== "•" && !/^(Album|Single|EP)$/i.test(t) && !/^\d{4}$/.test(t);
    });
  return {
    playlistId: browseId,
    name,
    artistName: ((artistRun?.text as string | undefined) ?? "").trim(),
    thumbnailUrl: bestThumbnail(item),
    videoCount: 0,
  };
}

/** musicResponsiveListItemRenderer (Playlist-Suche, browseId VL…) → PlaylistSummary. */
function parsePlaylistSearchItem(item: unknown): PlaylistSummary | null {
  const browseId = browseIdOf(item);
  if (!browseId?.startsWith("VL")) return null;
  const flexCols = (nav(item, "flexColumns") as unknown[] | undefined) ?? [];
  const name = runText(
    nav(flexCols[0], "musicResponsiveListItemFlexColumnRenderer", "text"),
  ).trim();
  if (!name) return null;
  const subtitleRuns = runsOf(
    nav(flexCols[1], "musicResponsiveListItemFlexColumnRenderer", "text"),
  );
  const countRun = subtitleRuns
    .map((r) => (typeof r.text === "string" ? r.text : ""))
    .find((t) => /\d/.test(t) && /(titel|songs?|videos?|tracks?)/i.test(t));
  const uploaderRun = subtitleRuns.find((r) => {
    const t = (r.text as string | undefined)?.trim() ?? "";
    return t !== "" && t !== "•" && !/^Playlist$/i.test(t) && !(countRun && t === countRun);
  });
  return {
    playlistId: browseId.slice(2),
    name,
    thumbnailUrl: bestThumbnail(item),
    videoCount: countRun ? Number.parseInt(/\d+/.exec(countRun)?.[0] ?? "0", 10) : 0,
    uploaderName: ((uploaderRun?.text as string | undefined) ?? "").trim(),
  };
}

/** musicTwoRowItemRenderer (Karussells) → Album/Single-Eintrag. */
function parseTwoRowAlbum(item: unknown, fallbackArtist: string): AlbumPageItem | null {
  const browseId = browseIdOf(nav(item, "title", "runs", 0)) ?? browseIdOf(item);
  if (!browseId?.startsWith("MPREb")) return null;
  const name = runText(nav(item, "title")).trim();
  if (!name) return null;
  const subtitle = runText(nav(item, "subtitle"));
  const year = /(\d{4})/.exec(subtitle)?.[1];
  return {
    playlistId: browseId,
    browseId,
    name,
    artistName: fallbackArtist,
    thumbnailUrl: bestThumbnail(item),
    videoCount: 0,
    ...(year ? { year: Number.parseInt(year, 10) } : {}),
  };
}

/** Untertitel eines Karussell-Items ("Single • 2021" / "Album • 2019"). */
function twoRowSubtitle(item: unknown): string {
  return runText(nav(item, "subtitle"));
}

// ————— Öffentliche Funktionen —————

/** Song-Suche (Filter music_songs-Äquivalent). */
export async function itSearchSongs(
  fetchImpl: typeof fetch,
  q: string,
): Promise<MusicTrack[] | undefined> {
  try {
    const body = await itCall(fetchImpl, "search", { query: q, params: IT_SEARCH_PARAMS.songs });
    const items = findAllByKey(body, "musicResponsiveListItemRenderer");
    const tracks = items
      .map((i) => parseSongItem(i))
      .filter((t): t is MusicTrack => t !== null)
      .slice(0, 24);
    return tracks.length > 0 ? tracks : undefined;
  } catch {
    return undefined;
  }
}

/** Typspezifische Suche — gleiche Shapes wie die Piped-Pfade in music.ts. */
export async function itSearchTyped(
  fetchImpl: typeof fetch,
  q: string,
  type: "songs" | "albums" | "artists" | "playlists",
): Promise<
  MusicTrack[] | ArtistSearchResult[] | AlbumSearchResult[] | PlaylistSummary[] | undefined
> {
  try {
    const body = await itCall(fetchImpl, "search", { query: q, params: IT_SEARCH_PARAMS[type] });
    const items = findAllByKey(body, "musicResponsiveListItemRenderer");
    switch (type) {
      case "songs": {
        const r = items
          .map((i) => parseSongItem(i))
          .filter((t): t is MusicTrack => t !== null)
          .slice(0, 20);
        return r.length > 0 ? r : undefined;
      }
      case "artists": {
        const r = items
          .map(parseArtistItem)
          .filter((a): a is ArtistSearchResult => a !== null)
          .slice(0, 20);
        return r.length > 0 ? r : undefined;
      }
      case "albums": {
        const r = items
          .map(parseAlbumSearchItem)
          .filter((a): a is AlbumSearchResult => a !== null)
          .slice(0, 20);
        return r.length > 0 ? r : undefined;
      }
      case "playlists": {
        const r = items
          .map(parsePlaylistSearchItem)
          .filter((p): p is PlaylistSummary => p !== null)
          .slice(0, 20);
        return r.length > 0 ? r : undefined;
      }
    }
  } catch {
    return undefined;
  }
}

/** Vier Teilsuchen parallel — Sektionen der YTM-artigen Vollsuche. */
export async function itSearchFull(
  fetchImpl: typeof fetch,
  q: string,
): Promise<FullSearchSections | undefined> {
  const [songs, albums, artists, playlists] = await Promise.all([
    itSearchTyped(fetchImpl, q, "songs"),
    itSearchTyped(fetchImpl, q, "albums"),
    itSearchTyped(fetchImpl, q, "artists"),
    itSearchTyped(fetchImpl, q, "playlists"),
  ]);
  if (!songs && !albums && !artists && !playlists) return undefined;
  return {
    songs: ((songs as MusicTrack[] | undefined) ?? []).slice(0, 10),
    artists: ((artists as ArtistSearchResult[] | undefined) ?? []).slice(0, 6),
    albums: ((albums as AlbumSearchResult[] | undefined) ?? []).slice(0, 6),
    playlists: ((playlists as PlaylistSummary[] | undefined) ?? []).slice(0, 6),
  };
}

/** Suchvorschläge (music/get_search_suggestions). */
export async function itSuggestions(
  fetchImpl: typeof fetch,
  q: string,
): Promise<string[] | undefined> {
  try {
    const body = await itCall(fetchImpl, "music/get_search_suggestions", { input: q });
    const renderers = findAllByKey(body, "searchSuggestionRenderer");
    const suggestions = renderers
      .map((r) => nav(r, "navigationEndpoint", "searchEndpoint", "query"))
      .filter((s): s is string => typeof s === "string" && s.length > 0);
    return suggestions.length > 0 ? suggestions.slice(0, 10) : undefined;
  } catch {
    return undefined;
  }
}

/**
 * Playlist-/Album-Tracks. Playlist-Ids werden als "VL<id>" gebrowst,
 * Album-Browse-Ids (MPREb…) direkt.
 */
export async function itPlaylistTracks(
  fetchImpl: typeof fetch,
  playlistId: string,
): Promise<MusicTrack[] | undefined> {
  try {
    const browseId =
      playlistId.startsWith("MPREb") || playlistId.startsWith("VL")
        ? playlistId
        : `VL${playlistId}`;
    const body = await itCall(fetchImpl, "browse", { browseId });
    // Album-Tracks haben keinen eigenen Interpreten pro Zeile — der steht auf
    // Album-Seiten in der Kopfzeile (straplineTextOne).
    const headerArtist = runText(findAllByKey(body, "straplineTextOne")[0]).trim();
    const items = findAllByKey(body, "musicResponsiveListItemRenderer");
    const tracks = items
      .map((i) => parseSongItem(i, headerArtist))
      .filter((t): t is MusicTrack => t !== null);
    return tracks.length > 0 ? tracks : undefined;
  } catch {
    return undefined;
  }
}

/** Radio/Related über `next` — Vorschlags-Queue zu einem Seed-Song. */
export async function itRelated(
  fetchImpl: typeof fetch,
  videoId: string,
): Promise<MusicTrack[] | undefined> {
  try {
    const body = await itCall(fetchImpl, "next", {
      videoId,
      playlistId: `RDAMVM${videoId}`,
      isAudioOnly: true,
    });
    const items = findAllByKey(body, "playlistPanelVideoRenderer");
    const seen = new Set<string>([videoId]);
    const tracks: MusicTrack[] = [];
    for (const item of items) {
      const id = nav(item, "videoId");
      if (typeof id !== "string" || !VIDEO_ID_RE.test(id) || seen.has(id)) continue;
      const title = runText(nav(item, "title")).trim();
      if (!title) continue;
      seen.add(id);
      const bylineRuns = runsOf(nav(item, "longBylineText"));
      const uploader = ((bylineRuns[0]?.text as string | undefined) ?? "").trim();
      const durationSeconds = durationToSeconds(runText(nav(item, "lengthText"))) ?? 0;
      tracks.push({ videoId: id, title, uploader, thumbnailUrl: songThumb(id), durationSeconds });
      if (tracks.length >= 25) break;
    }
    return tracks.length > 0 ? tracks : undefined;
  } catch {
    return undefined;
  }
}

/** Komplette Artist-Seite über einen browse-Aufruf. */
export async function itArtistPage(
  fetchImpl: typeof fetch,
  channelId: string,
): Promise<ArtistPage | undefined> {
  try {
    const body = await itCall(fetchImpl, "browse", { browseId: channelId });

    // Header: musicImmersiveHeaderRenderer (üblich) oder musicVisualHeaderRenderer.
    const header =
      findAllByKey(body, "musicImmersiveHeaderRenderer")[0] ??
      findAllByKey(body, "musicVisualHeaderRenderer")[0];
    const name = runText(nav(header, "title")).trim();
    if (!name) return undefined;
    const subscriberText = runText(
      nav(header, "subscriptionButton", "subscribeButtonRenderer", "subscriberCountText"),
    );
    const artist: ArtistInfo = {
      channelId,
      name,
      avatarUrl: bestThumbnail(nav(header, "thumbnail")) || null,
      bannerUrl: null,
      subscriberCount: approxCount(subscriberText),
      description: runText(nav(header, "description")).slice(0, 500),
      verified: false,
    };

    // Top-Songs: erstes musicShelfRenderer der Seite (YTM zeigt dort ~5).
    const songShelf = findAllByKey(body, "musicShelfRenderer")[0];
    let topSongs = findAllByKey(songShelf ?? {}, "musicResponsiveListItemRenderer")
      .map((i) => parseSongItem(i, name))
      .filter((t): t is MusicTrack => t !== null)
      .slice(0, 20);

    // "Mehr anzeigen" des Songs-Shelfs zeigt auf die volle Songs-Playlist —
    // damit die Artist-Seite mehr als 5 Titel hat, best-effort nachladen.
    if (topSongs.length < 10 && songShelf) {
      const moreId = [
        ...findAllByKey(nav(songShelf, "title") ?? {}, "browseId"),
        ...findAllByKey(nav(songShelf, "bottomEndpoint") ?? {}, "browseId"),
      ].find(
        (id): id is string =>
          typeof id === "string" && !id.startsWith("UC") && !id.startsWith("MPREb"),
      );
      if (moreId) {
        const more = await itPlaylistTracks(fetchImpl, moreId);
        if (more) {
          const seen = new Set(topSongs.map((t) => t.videoId));
          for (const t of more) {
            if (seen.has(t.videoId)) continue;
            seen.add(t.videoId);
            topSongs.push({ ...t, uploader: t.uploader || name });
            if (topSongs.length >= 20) break;
          }
        }
      }
    }
    topSongs = topSongs.slice(0, 20);

    // Karussells: Typzuordnung über browseId-Präfix statt lokalisierter Titel.
    const albums: AlbumPageItem[] = [];
    const singles: AlbumPageItem[] = [];
    const playlists: PlaylistSummary[] = [];
    const related: ArtistSearchResult[] = [];
    for (const carousel of findAllByKey(body, "musicCarouselShelfRenderer")) {
      for (const wrapped of findAllByKey(carousel, "musicTwoRowItemRenderer")) {
        const browseId = browseIdOf(nav(wrapped, "title", "runs", 0)) ?? browseIdOf(wrapped);
        if (!browseId) continue;
        if (browseId.startsWith("MPREb")) {
          const album = parseTwoRowAlbum(wrapped, name);
          if (!album) continue;
          // "Single"/"EP" steht sprachunabhängig im Untertitel.
          if (/single|ep\b/i.test(twoRowSubtitle(wrapped))) singles.push(album);
          else albums.push(album);
        } else if (browseId.startsWith("UC")) {
          const relName = runText(nav(wrapped, "title")).trim();
          if (!relName) continue;
          related.push({
            channelId: browseId,
            name: relName,
            thumbnailUrl: bestThumbnail(wrapped),
            subscribers: approxCount(twoRowSubtitle(wrapped)),
          });
        } else if (browseId.startsWith("VL") || browseId.startsWith("RDCLAK")) {
          const plName = runText(nav(wrapped, "title")).trim();
          if (!plName) continue;
          playlists.push({
            playlistId: browseId.startsWith("VL") ? browseId.slice(2) : browseId,
            name: plName,
            thumbnailUrl: bestThumbnail(wrapped),
            videoCount: 0,
            uploaderName: name,
          });
        }
      }
    }

    return {
      artist,
      topSongs,
      albums: albums.slice(0, 12),
      singles: singles.slice(0, 12),
      playlists: playlists.slice(0, 12),
      related: related.slice(0, 12),
    };
  } catch {
    return undefined;
  }
}

/** Home-Feed (FEmusic_home) — kuratierte Karussells ohne Login. */
export async function itHome(fetchImpl: typeof fetch): Promise<HomeFeed | undefined> {
  try {
    const body = await itCall(fetchImpl, "browse", { browseId: "FEmusic_home" });
    const sections: HomeSection[] = [];
    for (const carousel of findAllByKey(body, "musicCarouselShelfRenderer")) {
      const title = runText(
        nav(carousel, "header", "musicCarouselShelfBasicHeaderRenderer", "title"),
      ).trim();
      if (!title) continue;
      const items: HomeItem[] = [];

      // Quick-Picks-Songs (Listen-Items)
      for (const listItem of findAllByKey(carousel, "musicResponsiveListItemRenderer")) {
        const song = parseSongItem(listItem);
        if (song) items.push({ kind: "song", song });
      }
      // Kachel-Items (Playlists/Alben/Artists/Videos)
      for (const twoRow of findAllByKey(carousel, "musicTwoRowItemRenderer")) {
        const browseId = browseIdOf(nav(twoRow, "title", "runs", 0)) ?? browseIdOf(twoRow);
        const watchId = findAllByKey(twoRow, "watchEndpoint")
          .map((w) => nav(w, "videoId"))
          .find((v): v is string => typeof v === "string");
        const itemName = runText(nav(twoRow, "title")).trim();
        if (!itemName) continue;
        if (browseId?.startsWith("MPREb")) {
          const subtitleRuns = runsOf(nav(twoRow, "subtitle"));
          const artistName = (
            (subtitleRuns.find((r) => (browseIdOf(r) ?? "").startsWith("UC"))?.text as
              | string
              | undefined) ??
            (subtitleRuns[0]?.text as string | undefined) ??
            ""
          ).trim();
          items.push({
            kind: "album",
            album: {
              playlistId: browseId,
              name: itemName,
              artistName,
              thumbnailUrl: bestThumbnail(twoRow),
              videoCount: 0,
            },
          });
        } else if (browseId?.startsWith("UC")) {
          items.push({
            kind: "artist",
            artist: {
              channelId: browseId,
              name: itemName,
              thumbnailUrl: bestThumbnail(twoRow),
              subscribers: approxCount(twoRowSubtitle(twoRow)),
            },
          });
        } else if (browseId?.startsWith("VL") || browseId?.startsWith("RDCLAK")) {
          items.push({
            kind: "playlist",
            playlist: {
              playlistId: browseId.startsWith("VL") ? browseId.slice(2) : browseId,
              name: itemName,
              thumbnailUrl: bestThumbnail(twoRow),
              videoCount: 0,
              uploaderName: runText(nav(twoRow, "subtitle")).replace(/•/g, "").trim(),
            },
          });
        } else if (watchId && VIDEO_ID_RE.test(watchId)) {
          items.push({
            kind: "song",
            song: {
              videoId: watchId,
              title: itemName,
              uploader: runText(nav(twoRow, "subtitle")).split("•")[0]?.trim() ?? "",
              thumbnailUrl: songThumb(watchId),
              durationSeconds: 0,
            },
          });
        }
      }

      if (items.length >= 3) sections.push({ title, items: items.slice(0, 20) });
    }
    return sections.length > 0 ? { sections } : undefined;
  } catch {
    return undefined;
  }
}
