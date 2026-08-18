import { existsSync } from "node:fs";
import { join } from "node:path";
import type { FastifyInstance } from "fastify";
import { proxyMediaStream } from "../stream/proxy.js";
import {
  cacheGet,
  cachePut,
  dedupInflight,
  loadStreamCache,
  saveStreamCacheAsync,
  saveStreamCacheSync,
} from "../stream/url-cache.js";
import { runPreferEmbedded, runYtDlp } from "../yt-dlp/client.js";

const VIDEO_ID_RE = /^[A-Za-z0-9_-]{11}$/;
// googlevideo-URLs leben ~6 h; TTL knapp darunter, damit der Proxy nie mit
// einer sterbenden URL startet (bei 403/410 löst er ohnehin frisch auf).
const STREAM_CACHE_TTL_MS = 5 * 60 * 60 * 1000;
// Debounce für die Cache-Persistenz: bündelt Schreibzugriffe statt pro Request.
const CACHE_SAVE_DEBOUNCE_MS = 5000;
// So viele Videos werden pro Aufruf vorgewärmt (der Feed reicht die nächsten
// Items durch; mehr bringt nichts, die URLs leben ohnehin nur ~5 h).
const PREFETCH_MAX = 8;
// Muxed MP4 (Video+Audio in einer Datei), NUR progressives HTTPS: ohne
// [protocol=https] löst yt-dlp gern HLS-Manifeste (m3u8) auf, deren
// googlevideo-Segmente vom Handy aus nicht abspielbar sind. Format 18 ist
// YouTubes immer vorhandenes progressives 360p-MP4.
const VIDEO_FORMAT =
  "best[height<=720][ext=mp4][vcodec!=none][acodec!=none][protocol=https]/18/best[ext=mp4][protocol=https]";

export interface StreamDeps {
  ytDlp?: typeof runYtDlp;
  fetchImpl?: typeof fetch;
  now?: () => number;
  /** Pfad für den persistenten Stream-URL-Cache; ohne Pfad nur In-Memory. */
  streamCachePath?: string;
  /** Fallback: existiert <videoDir>/<id>.mp4, wird per 302 auf /videos/ umgeleitet. */
  videoDir?: string;
  /** Verzögerungen vor Retries derselben Upstream-URL im Proxy (ms). */
  retryDelaysMs?: number[];
}

/**
 * Live-Streaming für Feed-Videos: löst die googlevideo-URL erst beim Abspielen
 * auf und proxied die Bytes — Videos sind damit sofort nach der Freigabe
 * anschaubar, ohne dass der Server sie herunterladen muss (Spec Etappe 1).
 */
/** Serielle Vorauflösung: wärmt Stream-URLs, bevor der Player sie braucht. */
export type PrefetchStreams = (videoIds: string[]) => void;

export function registerStreamRoutes(
  app: FastifyInstance,
  deps: StreamDeps = {},
): PrefetchStreams {
  const ytDlp = deps.ytDlp ?? runYtDlp;
  const now = deps.now ?? Date.now;
  const cache = loadStreamCache(deps.streamCachePath, STREAM_CACHE_TTL_MS);
  const inflight = new Map<string, Promise<string | undefined>>();

  // Persistenz wie bei der Musik: dirty-Flag + gebündelter unref-Timer,
  // synchroner Flush beim Server-Close.
  let cacheDirty = false;
  let cacheTimer: ReturnType<typeof setTimeout> | undefined;
  const markDirty = () => {
    if (!deps.streamCachePath) return;
    cacheDirty = true;
    if (cacheTimer) return;
    cacheTimer = setTimeout(() => {
      cacheTimer = undefined;
      if (!cacheDirty || !deps.streamCachePath) return;
      cacheDirty = false;
      void saveStreamCacheAsync(deps.streamCachePath, cache);
    }, CACHE_SAVE_DEBOUNCE_MS);
    cacheTimer.unref?.();
  };
  app.addHook("onClose", () => {
    if (cacheTimer) {
      clearTimeout(cacheTimer);
      cacheTimer = undefined;
    }
    if (deps.streamCachePath && cacheDirty) {
      cacheDirty = false;
      saveStreamCacheSync(deps.streamCachePath, cache);
    }
  });

  async function extract(videoId: string): Promise<string | undefined> {
    try {
      // web_embedded-first: nur diese URLs sind voll rangebar (music.ts).
      // -4: macOS rotiert IPv6-Privacy-Adressen — die googlevideo-URL wäre an
      // eine Temporär-Adresse gebunden, der spätere Fetch ginge über eine
      // andere raus → 403. Die NAT-IPv4 ist stabil (siehe music.ts).
      const result = await runPreferEmbedded(
        ytDlp,
        ["--no-playlist", "-4", "-f", VIDEO_FORMAT, "-g", `https://www.youtube.com/watch?v=${videoId}`],
        { timeoutMs: 45_000, maxRetries: 1 },
      );
      const url = result.stdout.trim().split("\n")[0];
      if (!url?.startsWith("http")) return undefined;
      // Sicherheitsnetz: Manifest-URLs (HLS/DASH) sind hier nie brauchbar.
      if (url.includes(".m3u8") || url.includes("/manifest/")) return undefined;
      cachePut(cache, videoId, url, now());
      markDirty();
      return url;
    } catch {
      return undefined;
    }
  }

  const resolve = (videoId: string, force: boolean): Promise<string | undefined> => {
    const cached = force ? undefined : cacheGet(cache, videoId, STREAM_CACHE_TTL_MS, now());
    if (cached) return Promise.resolve(cached);
    return dedupInflight(inflight, videoId, () => extract(videoId));
  };

  // Vorauflösung: ohne sie kostet JEDES noch nicht angefragte Video 5–11 s
  // yt-dlp-Lauf, bevor das erste Byte fließt — im Feed sieht das aus, als würde
  // ab dem ersten kalten Video "nichts mehr laden". Seriell, damit nicht fünf
  // yt-dlp-Prozesse gleichzeitig auf YouTube losgehen.
  const prefetchQueue: string[] = [];
  let prefetchRunning = false;
  const drainPrefetch = async (): Promise<void> => {
    if (prefetchRunning) return;
    prefetchRunning = true;
    try {
      while (prefetchQueue.length > 0) {
        const id = prefetchQueue.shift();
        if (!id) continue;
        if (cacheGet(cache, id, STREAM_CACHE_TTL_MS, now())) continue;
        await resolve(id, false).catch(() => undefined);
      }
    } finally {
      prefetchRunning = false;
    }
  };
  const prefetch: PrefetchStreams = (videoIds) => {
    for (const id of videoIds.slice(0, PREFETCH_MAX)) {
      if (!VIDEO_ID_RE.test(id)) continue;
      if (cacheGet(cache, id, STREAM_CACHE_TTL_MS, now())) continue;
      if (prefetchQueue.includes(id)) continue;
      prefetchQueue.push(id);
    }
    void drainPrefetch();
  };

  // Wellen-Brecher (Defense-in-Depth zum -4-Fix): schlagen ≥3 verschiedene
  // Videos binnen 30 s upstream fehl, drosselt googlevideo gerade — dann 60 s
  // lang sofort 503 statt weiterer Auflösungen, die die Drossel füttern.
  const upstreamFails = new Map<string, number>();
  let breakerUntil = 0;
  const noteUpstreamFail = (videoId: string) => {
    const now = Date.now();
    upstreamFails.set(videoId, now);
    for (const [id, at] of upstreamFails) {
      if (now - at > 30_000) upstreamFails.delete(id);
    }
    if (upstreamFails.size >= 3) {
      breakerUntil = now + 60_000;
      upstreamFails.clear();
      app.log.warn("stream circuit breaker open — googlevideo throttling suspected (60s pause)");
    }
  };

  app.get<{ Params: { videoId: string } }>("/stream/video/:videoId", async (req, reply) => {
    const { videoId } = req.params;
    if (!VIDEO_ID_RE.test(videoId)) return reply.code(400).send({ error: "invalid videoId" });
    if (Date.now() < breakerUntil) {
      return reply.code(503).header("retry-after", "60").send({ error: "upstream throttled" });
    }

    // Erst auflösen: scheitert YouTube komplett, aber der Server hat noch eine
    // heruntergeladene Datei, spielt die App diese über den statischen Mount.
    const url = await resolve(videoId, false);
    if (!url && deps.videoDir && existsSync(join(deps.videoDir, `${videoId}.mp4`)))
      return reply.redirect(`/videos/${videoId}.mp4`, 302);

    const out = await proxyMediaStream(
      reply,
      req.headers.range,
      (force) => resolve(videoId, force),
      "video",
      {
        fetchImpl: deps.fetchImpl,
        retryDelaysMs: deps.retryDelaysMs,
      },
    );
    if (reply.statusCode === 502) noteUpstreamFail(videoId);
    return out;
  });

  return prefetch;
}
