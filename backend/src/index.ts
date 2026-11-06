import "dotenv/config";
import { mkdirSync, existsSync, readdirSync, unlinkSync } from "node:fs";
import { mkdir } from "node:fs/promises";
import { join } from "node:path";
import fastifyStatic from "@fastify/static";
import fastifyCompress from "@fastify/compress";
import fastifyMultipart from "@fastify/multipart";
import Fastify from "fastify";
import cron from "node-cron";
import { registerAuth } from "./api/auth.js";
import { registerCors } from "./api/cors.js";
import { registerChannelsRoutes } from "./api/channels.js";
import { registerDownloadsRoutes } from "./api/downloads.js";
import { registerFeedRoutes } from "./api/feed.js";
import { registerHealthRoute } from "./api/health.js";
import { registerStatsRoutes } from "./api/stats.js";
import { registerVideosRoutes } from "./api/videos.js";
import { registerMangaRoutes } from "./api/manga.js";
import { registerMusicRoutes } from "./api/music.js";
import { itChannelShorts } from "./api/music-innertube.js";
import { registerNewsRoutes } from "./api/news.js";
import { registerStreamRoutes } from "./api/stream.js";
import { registerWatchLaterRoutes } from "./api/watch-later.js";
import { registerRussianRoutes } from "./api/russian.js";
import { registerRussianConversationRoutes } from "./api/russian-conversation.js";
import { registerVideoFullRoute } from "./api/video-full.js";
import { registerCastRoutes } from "./api/cast.js";
import { registerVoiceRoutes } from "./api/voice.js";
import { VoiceJobStore, VoiceProcessor } from "./voice/jobs.js";
import { VoiceSummarizer } from "./voice/summarize.js";
import { WhisperWorker } from "./voice/whisper.js";
import { loadConfig } from "./config.js";
import { openDatabase } from "./db/connection.js";
import { buildDailyMix } from "./feed/daily-mix.js";
import { SUBSCRIBED_CHANNEL_IDS_SQL } from "./feed/subscriptions.js";
import { runCleanup } from "./download/cleanup.js";
import { MetadataExtractor } from "./import/metadata-extractor.js";
import { fetchVideoMetadata } from "./ingest/metadata.js";
import { fetchChannelFeedConditional } from "./monitor/rss-poller.js";
import { computePollIntervalMs, isChannelDue } from "./monitor/cadence.js";
import { refreshChannelMetadata } from "./monitor/channel-resolver.js";
import { processNewVideo } from "./pipeline/orchestrator.js";
import {
  enqueueIngest,
  claimNextIngest,
  completeIngest,
  failIngest,
  isTransientInfraError,
  requeueIngest,
  unlockStaleIngest,
} from "./ingest/queue.js";
import { fetchSponsorSegments } from "./sponsorblock/client.js";

const cfg = loadConfig();
mkdirSync(cfg.videoDir, { recursive: true });
mkdirSync(cfg.mangaDir, { recursive: true });
mkdirSync(cfg.coverDir, { recursive: true });

const db = openDatabase(cfg.dbPath);
// Import-Metadaten (Serie/Staffel/Folge) per Claude verfeinern — optional, nur
// mit API-Key. Videos werden NICHT bewertet: der Feed besteht aus den Abos.
const extractor = cfg.claude.apiKey
  ? new MetadataExtractor({ apiKey: cfg.claude.apiKey, model: cfg.claude.model })
  : null;

// Startup consistency check: orphan files (file on disk without DB row)
for (const f of readdirSync(cfg.videoDir)) {
  if (!f.endsWith(".mp4")) continue;
  const videoId = f.replace(/\.mp4$/, "");
  const row = db.prepare("SELECT 1 FROM downloaded_videos WHERE video_id = ?").get(videoId);
  if (!row) {
    unlinkSync(join(cfg.videoDir, f));
  }
}
// Startup consistency check: DB rows without files
const orphanRows = db
  .prepare("SELECT video_id, file_path FROM downloaded_videos")
  .all() as { video_id: string; file_path: string }[];
for (const r of orphanRows) {
  if (!existsSync(r.file_path)) {
    db.prepare("DELETE FROM downloaded_videos WHERE video_id = ?").run(r.video_id);
    db.prepare("UPDATE feed_items SET playback_failed = 1 WHERE video_id = ?").run(r.video_id);
  }
}

const app = Fastify({ logger: { level: "info" } });
// Response-Kompression für die JSON-APIs, vor allen Routen registriert.
// @fastify/compress komprimiert nur komprimierbare Content-Types (text/*,
// application/json, ...) — audio/* und video/* laufen per Default
// unkomprimiert durch, der Musik-Proxy-Stream bleibt also unberührt.
await app.register(fastifyCompress);
// Opt-in CORS for JSON routes (HIKARI_CORS_ORIGINS allowlist). Registered
// BEFORE auth so a preflight OPTIONS — which carries no Authorization header —
// is answered 204 here instead of being 401'd by the auth hook. No-op by
// default (empty allowlist), so localhost / native-client behavior is unchanged.
registerCors(app, { origins: cfg.corsOrigins });
// Opt-in auth: when HIKARI_AUTH_TOKEN is set, mutating requests must carry it.
// No-op (open) by default for the single-user localhost deployment. Registered
// before routes so the onRequest hook covers them; GET media stays open.
registerAuth(app, { token: cfg.authToken });
// CORS for video assets — a browser <video> on another origin (Web-Client,
// Cast-Receiver) needs Access-Control-Allow-Origin to render the pixels.
// Wildcard is safe here: videos are public, single-user backend, no auth.
const videoCorsHeaders = (res: { setHeader: (name: string, value: string) => void }) => {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");
};
await app.register(fastifyStatic, {
  root: cfg.videoDir,
  prefix: "/videos/",
  setHeaders: videoCorsHeaders,
});
await app.register(fastifyStatic, { root: cfg.coverDir, prefix: "/covers/", decorateReply: false });
// Static mockups for design exploration — served as plain HTML
const mockupsDir = new URL("../mockups", import.meta.url).pathname;
await app.register(fastifyStatic, { root: mockupsDir, prefix: "/mockups/", decorateReply: false });
await app.register(fastifyMultipart, { limits: { fileSize: 10 * 1024 * 1024 } });
await registerChannelsRoutes(app, { db });
const prefetchStreams = registerStreamRoutes(app, {
  streamCachePath: join(cfg.dataDir, "video-stream-cache.json"),
  videoDir: cfg.videoDir,
});
await registerFeedRoutes(app, {
  db,
  dailyBudget: cfg.dailyBudget,
  prefetchStreams,
});
await registerWatchLaterRoutes(app, { db });
await registerRussianRoutes(app);
await registerRussianConversationRoutes(app);
await registerHealthRoute(app, { db, videoDir: cfg.videoDir });
await registerStatsRoutes(app, { db });
await registerVideosRoutes(app, { db, videoDir: cfg.videoDir, coverDir: cfg.coverDir, extractor });
await registerDownloadsRoutes(app, { db, diskLimitBytes: cfg.diskLimitBytes });
await registerMangaRoutes(app, { db, mangaDir: cfg.mangaDir });
await mkdir(join(cfg.dataDir, "music"), { recursive: true });
await registerMusicRoutes(app, {
  streamCachePath: join(cfg.dataDir, "music-stream-cache.json"),
  audioDir: join(cfg.dataDir, "music"),
});

// Sprachnachrichten: Whisper transkribiert, Qwen (LM Studio) fasst zusammen.
// Beide Modelle liegen nur im Speicher, solange gearbeitet wird, und werden
// nach VOICE_IDLE_MINUTES Leerlauf entladen.
{
  const idleMs = Number(process.env.VOICE_IDLE_MINUTES ?? 10) * 60_000;
  const voiceStore = new VoiceJobStore(join(cfg.dataDir, "voice"));
  const whisper = new WhisperWorker({
    script: new URL("../voice/whisper_worker.py", import.meta.url).pathname,
    idleMs,
    language: process.env.VOICE_LANGUAGE?.trim() || null,
    log: (msg) => app.log.info(msg),
  });
  const voiceLlm = {
    baseUrl: cfg.lmstudio.baseUrl,
    model: process.env.VOICE_LLM_MODEL ?? "qwen/qwen3.8-27b",
  };
  const summarizer = new VoiceSummarizer({ ...voiceLlm, ttlSeconds: idleMs / 1000 });
  const voiceProcessor = new VoiceProcessor({
    store: voiceStore,
    transcribe: (p) => whisper.transcribe(p),
    summarize: (t) => summarizer.summarize(t),
    log: (msg, extra) => app.log.info(extra ?? {}, msg),
  });
  await registerVoiceRoutes(app, {
    store: voiceStore,
    processor: voiceProcessor,
    whisper,
    lmstudio: voiceLlm,
  });
  const resumed = voiceProcessor.recover();
  if (resumed > 0) app.log.info({ resumed }, "voice jobs resumed");
  app.addHook("onClose", async () => whisper.stop());
}

await registerNewsRoutes(app, { db, cfg });
registerVideoFullRoute(app, db);
registerCastRoutes(app, cfg.port);

// 15-min channel polling — NUR abonnierte Kanäle. Videos anderer Kanäle
// kommen nie in den Feed, also werden sie auch nicht abgefragt.
// Reentrancy guard: a slow poll can outlast the 15-min interval. Without this
// flag the next tick would start concurrently and race the (non-atomic) dedup
// in processNewVideo.
let isPolling = false;
async function pollAllChannels(): Promise<void> {
  if (isPolling) {
    app.log.info("channel poll skipped — previous run still in progress");
    return;
  }
  isPolling = true;
  try {
    const now = Date.now();
    const channels = db
      .prepare(
        `SELECT id, url, last_polled_at AS lastPolledAt, rss_etag AS rssEtag,
                rss_last_modified AS rssLastModified, thumbnail_url AS thumbnailUrl
           FROM channels WHERE id IN (${SUBSCRIBED_CHANNEL_IDS_SQL})`,
      )
      .all() as {
      id: string;
      url: string;
      lastPolledAt: number | null;
      rssEtag: string | null;
      rssLastModified: string | null;
      thumbnailUrl: string | null;
    }[];
    const recentPublished = db.prepare(
      "SELECT published_at AS publishedAt FROM videos WHERE channel_id = ? ORDER BY published_at DESC LIMIT 10",
    );
    for (const c of channels) {
      // Adaptive cadence: skip a channel that isn't due yet for its own
      // upload rhythm. The cron still fires every 15 min; dormant channels are
      // simply checked far less often, cutting wasted RSS/yt-dlp load.
      const pub = (recentPublished.all(c.id) as { publishedAt: number }[]).map((r) => r.publishedAt);
      const interval = computePollIntervalMs(pub, now);
      if (!isChannelDue(c.lastPolledAt, interval, now)) continue;

      try {
        // Conditional fetch: send the last ETag/Last-Modified so YouTube can
        // answer 304 when nothing changed (the common case). On 304 there's
        // nothing to enqueue; on 200 we enqueue new ids and store fresh
        // validators. The poll only ENQUEUEs (RSS + cheap insert) — the heavy
        // pipeline runs in drainIngestQueue, off this loop.
        const result = await fetchChannelFeedConditional(c.id, {
          etag: c.rssEtag,
          lastModified: c.rssLastModified,
        });
        if (result.status === "ok") {
          for (const e of result.entries) {
            enqueueIngest(db, e.videoId, c.id);
          }
          db.prepare(
            "UPDATE channels SET rss_etag = ?, rss_last_modified = ? WHERE id = ?",
          ).run(result.etag, result.lastModified, c.id);
        }
        // Shorts-Tab zusätzlich zum RSS: Kanal-RSS enthält Shorts unzuverlässig.
        // Best-effort — enqueueIngest skippt bereits bekannte Videos ohnehin.
        try {
          const shortIds = await itChannelShorts(fetch, c.id);
          for (const id of shortIds ?? []) enqueueIngest(db, id, c.id);
        } catch {
          // ein Innertube-Schluckauf darf den Poll nicht brechen
        }
      } catch (err) {
        app.log.warn({ err, channelId: c.id }, "channel poll failed");
      } finally {
        // Always advance the watermark — even on RSS failure — so a channel
        // whose newest video keeps failing doesn't freeze last_polled_at forever.
        db.prepare("UPDATE channels SET last_polled_at = ? WHERE id = ?").run(Date.now(), c.id);
      }

      // Kanäle aus der Zeit vor den Karten-Metadaten haben kein Avatar/Banner.
      // Einmal nachziehen, wenn die Karte leer ist — fire-and-forget, damit der
      // Poll nicht pro Kanal zehn Sekunden auf yt-dlp wartet.
      if (!c.thumbnailUrl) {
        refreshChannelMetadata(db, c.id, c.url).catch((err) =>
          app.log.debug({ err, channelId: c.id }, "channel metadata backfill failed"),
        );
      }
    }
  } finally {
    isPolling = false;
  }
}

cron.schedule("*/15 * * * *", () => {
  pollAllChannels().catch((err) => app.log.error({ err }, "channel poll crashed"));
});

// Tagesmix: früh morgens frisch bauen; zusätzlich Top-up nach jedem Drain
// (unten) und lazy beim ersten /feed-Abruf des Tages.
cron.schedule("0 6 * * *", () => {
  try {
    buildDailyMix(db);
  } catch (err) {
    app.log.error({ err }, "daily mix build crashed");
  }
});
try {
  buildDailyMix(db);
} catch (err) {
  app.log.error({ err }, "daily mix startup build crashed");
}

// Durable ingest drain: claim queued videos one at a time and run the full
// pipeline. A reentrancy guard keeps concurrent ticks from racing; each tick
// drains a bounded batch so it can't run unbounded. A failed video is retried
// (attempts++) until the dead-letter cap, never blocking the rest.
let isDraining = false;
const DRAIN_BATCH = 20;
// Nach einem transienten Infra-Fehler (Netz weg) pausiert der Drain:
// sonst wiederholt jede Minute derselbe Job seinen yt-dlp-Metadaten-Fetch —
// Log-Spam und unnoetige YouTube-Requests von der Mac-IP (Rate-Limit-Risiko).
const INFRA_COOLDOWN_MS = 10 * 60 * 1000;
let infraCooldownUntil = 0;
async function drainIngestQueue(): Promise<void> {
  if (isDraining) return;
  if (Date.now() < infraCooldownUntil) return;
  isDraining = true;
  let processedAny = false;
  try {
    for (let i = 0; i < DRAIN_BATCH; i++) {
      const job = claimNextIngest(db);
      if (!job) break;
      try {
        // Jobs nicht (mehr) abonnierter Kanäle erledigen sich hier ohne
        // Netzaufruf — processNewVideo prüft das Abo vor allem anderen.
        const result = await processNewVideo({
          db,
          videoId: job.video_id,
          channelId: job.channel_id,
          fetchMetadata: fetchVideoMetadata,
          fetchSponsorSegments,
        });
        completeIngest(db, job.video_id);
        if (result === "added") processedAny = true;
      } catch (err) {
        const msg = err instanceof Error ? err.message : String(err);
        if (isTransientInfraError(err)) {
          // Netz weg: Job zurücklegen ohne attempts++ und den
          // Drain fuer INFRA_COOLDOWN_MS aussetzen — ohne Infrastruktur
          // scheitert der Rest genauso, und Minuten-Retries hämmern nur
          // yt-dlp/YouTube und das Log.
          requeueIngest(db, job.video_id, msg);
          infraCooldownUntil = Date.now() + INFRA_COOLDOWN_MS;
          app.log.warn(
            { err, videoId: job.video_id },
            "ingest paused for 10min — transient infra error",
          );
          break;
        }
        failIngest(db, job.video_id, msg);
        app.log.warn({ err, videoId: job.video_id }, "ingest job failed (will retry)");
      }
    }
  } finally {
    isDraining = false;
  }
  // Neue Abo-Videos sofort in den Tagesmix aufnehmen.
  if (processedAny) {
    try {
      buildDailyMix(db);
    } catch {
      // best-effort — der 6-Uhr-Cron und der lazy Build fangen es auf
    }
  }
}

// Recover stale locks from a previous crash, then drain every minute.
unlockStaleIngest(db);
cron.schedule("* * * * *", () => {
  drainIngestQueue().catch((err) => app.log.error({ err }, "ingest drain crashed"));
});

// Daily cleanup at 04:00
cron.schedule("0 4 * * *", () => {
  const result = runCleanup({ db, limitBytes: cfg.diskLimitBytes });
  if (result.deletedCount > 0) {
    app.log.info({ result }, "cleanup completed");
  }
});

app.listen({ port: cfg.port, host: "0.0.0.0" }).catch((err) => {
  app.log.error(err);
  process.exit(1);
});
