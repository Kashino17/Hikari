import type { FastifyInstance } from "fastify";
import { runYtDlp } from "../yt-dlp/client.js";

export interface MusicTrack {
  videoId: string;
  title: string;
  uploader: string;
  thumbnailUrl: string;
  durationSeconds: number;
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
  thumbnail?: string;
  duration?: number;
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
      const cached = force ? undefined : cacheGet(streamCache, videoId, STREAM_CACHE_TTL_MS, now());
      if (cached) return { url: cached };

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
        if (!url?.startsWith("http")) return reply.code(502).send({ error: "no audio stream found" });
        cachePut(streamCache, videoId, url, now());
        return { url };
      } catch {
        return reply.code(502).send({ error: "audio extraction failed" });
      }
    },
  );
}
