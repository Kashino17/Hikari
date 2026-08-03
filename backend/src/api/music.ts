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
const STREAM_CACHE_TTL_MS = 30 * 60 * 1000; // googlevideo URLs expire after ~6h; stay well below
const CACHE_MAX_ENTRIES = 200;
const VIDEO_ID_RE = /^[A-Za-z0-9_-]{11}$/;
const CHANNEL_ID_RE = /^[A-Za-z0-9_-]{10,}$/;

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

interface PipedSearchItem {
  url?: string;
  type?: string;
  title?: string;
  uploaderName?: string;
  uploader?: string;
  uploaderUrl?: string;
  thumbnail?: string;
  duration?: number;
  views?: number;
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

/** Piped-Playlist-Treffer aus /search?filter=playlists. */
interface PipedPlaylistItem {
  url?: string;
  type?: string;
  name?: string;
  thumbnail?: string;
  uploaderName?: string;
  uploaderUrl?: string;
  videos?: number;
}

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

export interface MusicDeps {
  ytDlp?: typeof runYtDlp;
  fetchImpl?: typeof fetch;
  now?: () => number;
}

export async function registerMusicRoutes(app: FastifyInstance, deps: MusicDeps = {}): Promise<void> {
  const ytDlp = deps.ytDlp ?? runYtDlp;
  const fetchImpl = deps.fetchImpl ?? fetch;
  const now = deps.now ?? Date.now;

  const searchCache = new Map<string, CacheEntry<MusicTrack[]>>();
  const streamCache = new Map<string, CacheEntry<string>>();

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
    if (cached) return cached;

    for (const base of PIPED_INSTANCES) {
      try {
        const res = await fetchImpl(
          `${base}/search?q=${encodeURIComponent(q)}&filter=${filter}`,
          { signal: AbortSignal.timeout(6000) },
        );
        if (!res.ok) continue;
        const body = (await res.json()) as { items?: PipedSearchItem[] };
        if (!Array.isArray(body.items)) continue;
        const tracks = body.items
          .filter((i) => i.type === "stream" || i.url?.includes("v="))
          .map(normalizeItem)
          .filter((t): t is MusicTrack => t !== null);
        if (tracks.length === 0) continue; // instance is up but degraded — try the next one
        cachePut(searchCache, cacheKey, tracks, now());
        return tracks;
      } catch {
        // dead instance — try the next one
      }
    }
    return reply.code(502).send({ error: "all music search providers unavailable" });
  });

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

      let upstream: Response;
      try {
        // Kein Abort-Timeout: der Body streamt so lange, wie der Song spielt.
        upstream = await fetchImpl(url, { headers: range ? { range } : {} });
      } catch {
        continue;
      }
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

  app.get<{ Params: { channelId: string } }>("/music/artist/:channelId", async (req, reply) => {
    const { channelId } = req.params;
    if (!CHANNEL_ID_RE.test(channelId)) return reply.code(400).send({ error: "invalid channelId" });

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
    return reply.code(502).send({ error: "all music providers unavailable" });
  });

  app.get<{ Params: { channelId: string }; Querystring: { name?: string } }>(
    "/music/artist/:channelId/top",
    async (req, reply) => {
      const { channelId } = req.params;
      if (!CHANNEL_ID_RE.test(channelId)) return reply.code(400).send({ error: "invalid channelId" });
      const name = (req.query.name ?? "").trim();
      if (!name) return reply.code(400).send({ error: "missing query parameter name" });

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
      return reply.code(502).send({ error: "all music providers unavailable" });
    },
  );

  app.get<{ Params: { channelId: string }; Querystring: { name?: string } }>(
    "/music/artist/:channelId/playlists",
    async (req, reply) => {
      const { channelId } = req.params;
      if (!CHANNEL_ID_RE.test(channelId)) return reply.code(400).send({ error: "invalid channelId" });
      const name = (req.query.name ?? "").trim();
      if (!name) return reply.code(400).send({ error: "missing query parameter name" });

      for (const base of PIPED_INSTANCES) {
        try {
          const res = await fetchImpl(
            `${base}/search?q=${encodeURIComponent(name)}&filter=playlists`,
            { signal: AbortSignal.timeout(6000) },
          );
          if (!res.ok) continue;
          const body = (await res.json()) as { items?: PipedPlaylistItem[] };
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
      return reply.code(502).send({ error: "all music providers unavailable" });
    },
  );
}
