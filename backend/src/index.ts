import "dotenv/config";
import { mkdirSync, existsSync, readdirSync, unlinkSync } from "node:fs";
import { join } from "node:path";
import fastifyStatic from "@fastify/static";
import fastifyMultipart from "@fastify/multipart";
import Fastify from "fastify";
import cron from "node-cron";
import { registerChannelsRoutes } from "./api/channels.js";
import { registerFeedRoutes } from "./api/feed.js";
import { registerFilterRoutes } from "./api/filter.js";
import { registerHealthRoute } from "./api/health.js";
import { registerStatsRoutes } from "./api/stats.js";
import { registerVideosRoutes } from "./api/videos.js";
import { registerMangaRoutes } from "./api/manga.js";
import { loadConfig } from "./config.js";
import { openDatabase } from "./db/connection.js";
import { runCleanup } from "./download/cleanup.js";
import { downloadVideo } from "./download/worker.js";
import { fetchVideoMetadata } from "./ingest/metadata.js";
import { fetchTranscript } from "./ingest/transcript.js";
import { fetchChannelFeed } from "./monitor/rss-poller.js";
import { processNewVideo } from "./pipeline/orchestrator.js";
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
await app.register(fastifyStatic, { root: cfg.videoDir, prefix: "/videos/" });
await app.register(fastifyStatic, { root: cfg.coverDir, prefix: "/covers/", decorateReply: false });
// Static mockups for design exploration — served as plain HTML
const mockupsDir = new URL("../mockups", import.meta.url).pathname;
await app.register(fastifyStatic, { root: mockupsDir, prefix: "/mockups/", decorateReply: false });
await app.register(fastifyMultipart, { limits: { fileSize: 10 * 1024 * 1024 } });
await registerChannelsRoutes(app, { db, scorer, videoDir: cfg.videoDir });
await registerFeedRoutes(app, { db, dailyBudget: cfg.dailyBudget });
await registerFilterRoutes(app, { db });
await registerHealthRoute(app, { db, videoDir: cfg.videoDir });
await registerStatsRoutes(app, { db });
await registerVideosRoutes(app, { db, videoDir: cfg.videoDir, coverDir: cfg.coverDir, extractor });
await registerMangaRoutes(app, { db, mangaDir: cfg.mangaDir });

// 15-min channel polling
cron.schedule("*/15 * * * *", async () => {
  const channels = db
    .prepare("SELECT id FROM channels WHERE is_active = 1")
    .all() as { id: string }[];
  for (const c of channels) {
    try {
      const entries = await fetchChannelFeed(c.id);
      for (const e of entries) {
        await processNewVideo({
          db,
          videoId: e.videoId,
          channelId: c.id,
          fetchMetadata: fetchVideoMetadata,
          fetchTranscript,
          fetchSponsorSegments,
          scorer,
          download: (id) => downloadVideo({ videoId: id, outDir: cfg.videoDir }),
        });
      }
      db.prepare("UPDATE channels SET last_polled_at = ? WHERE id = ?").run(Date.now(), c.id);
    } catch (err) {
      app.log.warn({ err, channelId: c.id }, "channel poll failed");
    }
  }
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
