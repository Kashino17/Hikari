import "dotenv/config";
import { mkdirSync, existsSync, readdirSync, unlinkSync } from "node:fs";
import { mkdir } from "node:fs/promises";
import { join } from "node:path";
import fastifyStatic from "@fastify/static";
import fastifyMultipart from "@fastify/multipart";
import Fastify from "fastify";
import cron from "node-cron";
import { registerAuth } from "./api/auth.js";
import { registerCors } from "./api/cors.js";
import { registerChannelsRoutes } from "./api/channels.js";
import { registerDiscoveryRoutes as registerDiscoverySettingsRoutes } from "./api/discovery.js";
import { registerDiscoveryRoutes } from "./routes/discovery.js";
import { registerDownloadsRoutes } from "./api/downloads.js";
import { registerFeedRoutes } from "./api/feed.js";
import { registerFilterRoutes } from "./api/filter.js";
import { registerHealthRoute } from "./api/health.js";
import { registerStatsRoutes } from "./api/stats.js";
import { registerVideosRoutes } from "./api/videos.js";
import { registerMangaRoutes } from "./api/manga.js";
import { registerClipperStatusRoutes } from "./api/clipper-status.js";
import { registerVideoFullRoute } from "./api/video-full.js";
import { loadConfig } from "./config.js";
import { openDatabase } from "./db/connection.js";
import { runCleanup } from "./download/cleanup.js";
import { downloadVideo } from "./download/worker.js";
import { fetchVideoMetadata } from "./ingest/metadata.js";
import { fetchTranscript } from "./ingest/transcript.js";
import { fetchChannelFeed } from "./monitor/rss-poller.js";
import { computePollIntervalMs, isChannelDue } from "./monitor/cadence.js";
import { processNewVideo } from "./pipeline/orchestrator.js";
import {
  enqueueIngest,
  claimNextIngest,
  completeIngest,
  failIngest,
  unlockStaleIngest,
} from "./ingest/queue.js";
import { createScorer } from "./scorer/factory.js";
import { MetadataExtractor } from "./scorer/metadata-extractor.js";
import { fetchSponsorSegments } from "./sponsorblock/client.js";

const cfg = loadConfig();
mkdirSync(cfg.videoDir, { recursive: true });
mkdirSync(cfg.mangaDir, { recursive: true });
mkdirSync(cfg.coverDir, { recursive: true });

const db = openDatabase(cfg.dbPath);
const scorer = createScorer(cfg);
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
// Opt-in CORS for JSON routes (HIKARI_CORS_ORIGINS allowlist). Registered
// BEFORE auth so a preflight OPTIONS — which carries no Authorization header —
// is answered 204 here instead of being 401'd by the auth hook. No-op by
// default (empty allowlist), so localhost / native-client behavior is unchanged.
registerCors(app, { origins: cfg.corsOrigins });
// Opt-in auth: when HIKARI_AUTH_TOKEN is set, mutating requests must carry it.
// No-op (open) by default for the single-user localhost deployment. Registered
// before routes so the onRequest hook covers them; GET media stays open.
registerAuth(app, { token: cfg.authToken });
// CORS for video assets — Remotion's compositor (headless Chrome) bundles
// the React composition under a different origin and loads videos via
// <video> tag, which requires Access-Control-Allow-Origin to render the
// pixels (otherwise we get silent black frames). Wildcard is safe here:
// videos are public, single-user backend, no auth.
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
// Clips are written to <videoDir>/clips/<uuid>.mp4 by the clipper worker.
// Ensure the directory exists even if the worker hasn't run yet (backend boots first).
await mkdir(join(cfg.videoDir, "clips"), { recursive: true });
await app.register(fastifyStatic, {
  root: join(cfg.videoDir, "clips"),
  prefix: "/clips/",
  decorateReply: false,
  setHeaders: videoCorsHeaders,
});
// Static mockups for design exploration — served as plain HTML
const mockupsDir = new URL("../mockups", import.meta.url).pathname;
await app.register(fastifyStatic, { root: mockupsDir, prefix: "/mockups/", decorateReply: false });
await app.register(fastifyMultipart, { limits: { fileSize: 10 * 1024 * 1024 } });
await registerChannelsRoutes(app, { db, scorer, videoDir: cfg.videoDir });
await registerFeedRoutes(app, { db, dailyBudget: cfg.dailyBudget });
await registerFilterRoutes(app, { db });
await registerDiscoverySettingsRoutes(app, { db });
await registerDiscoveryRoutes(app, { db });
await registerHealthRoute(app, { db, videoDir: cfg.videoDir });
await registerStatsRoutes(app, { db });
await registerVideosRoutes(app, { db, videoDir: cfg.videoDir, coverDir: cfg.coverDir, extractor });
await registerDownloadsRoutes(app, { db, diskLimitBytes: cfg.diskLimitBytes });
await registerMangaRoutes(app, { db, mangaDir: cfg.mangaDir });
registerClipperStatusRoutes(app, db, {
  startHour: cfg.clipper.scheduleStartHour,
  endHour:   cfg.clipper.scheduleEndHour,
}, cfg.clipper);
registerVideoFullRoute(app, db);

// 15-min channel polling.
// Reentrancy guard: a slow poll (dozens of yt-dlp downloads) can outlast the
// 15-min interval. Without this flag the next tick would start concurrently and
// race the (non-atomic) dedup in processNewVideo, duplicating downloads/clips.
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
      .prepare("SELECT id, last_polled_at AS lastPolledAt FROM channels WHERE is_active = 1")
      .all() as { id: string; lastPolledAt: number | null }[];
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
        const entries = await fetchChannelFeed(c.id);
        // The poll only ENQUEUEs (RSS + cheap insert). The heavy
        // metadata/transcript/download/score pipeline runs in drainIngestQueue,
        // off this loop — so a slow ingest can't stall the next poll and an
        // in-flight ingest survives a restart (it stays in ingest_queue).
        for (const e of entries) {
          enqueueIngest(db, e.videoId, c.id);
        }
      } catch (err) {
        app.log.warn({ err, channelId: c.id }, "channel poll failed");
      } finally {
        // Always advance the watermark — even on RSS failure — so a channel
        // whose newest video keeps failing doesn't freeze last_polled_at forever.
        db.prepare("UPDATE channels SET last_polled_at = ? WHERE id = ?").run(Date.now(), c.id);
      }
    }
  } finally {
    isPolling = false;
  }
}

cron.schedule("*/15 * * * *", () => {
  pollAllChannels().catch((err) => app.log.error({ err }, "channel poll crashed"));
});

// Durable ingest drain: claim queued videos one at a time and run the full
// pipeline. A reentrancy guard keeps concurrent ticks from racing; each tick
// drains a bounded batch so it can't run unbounded. A failed video is retried
// (attempts++) until the dead-letter cap, never blocking the rest.
let isDraining = false;
const DRAIN_BATCH = 20;
async function drainIngestQueue(): Promise<void> {
  if (isDraining) return;
  isDraining = true;
  try {
    for (let i = 0; i < DRAIN_BATCH; i++) {
      const job = claimNextIngest(db);
      if (!job) break;
      try {
        await processNewVideo({
          db,
          videoId: job.video_id,
          channelId: job.channel_id,
          fetchMetadata: fetchVideoMetadata,
          fetchTranscript,
          fetchSponsorSegments,
          scorer,
          download: (id) => downloadVideo({ videoId: id, outDir: cfg.videoDir }),
        });
        completeIngest(db, job.video_id);
      } catch (err) {
        const msg = err instanceof Error ? err.message : String(err);
        failIngest(db, job.video_id, msg);
        app.log.warn({ err, videoId: job.video_id }, "ingest job failed (will retry)");
      }
    }
  } finally {
    isDraining = false;
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
