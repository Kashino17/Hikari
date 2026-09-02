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
import { registerDiscoveryRoutes as registerDiscoverySettingsRoutes } from "./api/discovery.js";
import { registerDiscoveryRoutes } from "./routes/discovery.js";
import { registerDownloadsRoutes } from "./api/downloads.js";
import { registerFeedRoutes } from "./api/feed.js";
import { registerFilterRoutes } from "./api/filter.js";
import { registerHealthRoute } from "./api/health.js";
import { registerStatsRoutes } from "./api/stats.js";
import { registerVideosRoutes } from "./api/videos.js";
import { registerMangaRoutes } from "./api/manga.js";
import { registerMusicRoutes } from "./api/music.js";
import { itChannelShorts } from "./api/music-innertube.js";
import { registerNewsRoutes } from "./api/news.js";
import { summarizeVideoTranscript } from "./clipper/context-summarizer.js";
import { registerStreamRoutes } from "./api/stream.js";
import { registerWatchLaterRoutes } from "./api/watch-later.js";
import { registerClipperStatusRoutes } from "./api/clipper-status.js";
import { registerVideoFullRoute } from "./api/video-full.js";
import { loadConfig } from "./config.js";
import { openDatabase } from "./db/connection.js";
import { runDiscoveryCycle } from "./discovery/feed-sources.js";
import { describeAiWindow, isAiWindowOpen } from "./pipeline/ai-window.js";
import { buildDailyMix } from "./feed/daily-mix.js";
import { runCleanup } from "./download/cleanup.js";
import { fetchVideoMetadata } from "./ingest/metadata.js";
import { fetchTranscript } from "./ingest/transcript.js";
import { fetchChannelFeedConditional } from "./monitor/rss-poller.js";
import { computePollIntervalMs, isChannelDue } from "./monitor/cadence.js";
import { refreshChannelMetadata } from "./monitor/channel-resolver.js";
import { processNewVideo } from "./pipeline/orchestrator.js";
import {
  outdatedFeedItemCount,
  rescoreLegacyShorts,
  rescoreOutdatedFeedItems,
} from "./pipeline/rescore-shorts.js";
import {
  enqueueIngest,
  claimNextIngest,
  completeIngest,
  failIngest,
  isTransientInfraError,
  requeueIngest,
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
const summarizeForFeed = (title: string, transcript: string) =>
  summarizeVideoTranscript(title, transcript, {
    baseUrl: cfg.clipper.baseUrl,
    model: cfg.clipper.model,
  });
await registerChannelsRoutes(app, {
  db,
  scorer,
  videoDir: cfg.videoDir,
  clipperEnabled: cfg.clipper.enabled,
  summarize: summarizeForFeed,
});
const prefetchStreams = registerStreamRoutes(app, {
  streamCachePath: join(cfg.dataDir, "video-stream-cache.json"),
  videoDir: cfg.videoDir,
});
await registerFeedRoutes(app, {
  db,
  dailyBudget: cfg.dailyBudget,
  prefetchStreams,
  onLowStock: () => triggerDiscovery("feed low stock"),
});
await registerWatchLaterRoutes(app, { db });
await registerFilterRoutes(app, {
  db,
  onFilterChanged: () => {
    // Neue Vorgaben sollen sofort wirken: ungesehenen Mix verwerfen, neu
    // mischen und passende Inhalte suchen.
    try {
      db.prepare(
        `DELETE FROM daily_mix_items WHERE video_id IN (
           SELECT m.video_id FROM daily_mix_items m
             JOIN feed_items f ON f.video_id = m.video_id
            WHERE f.seen_at IS NULL)`,
      ).run();
      buildDailyMix(db);
    } catch (err) {
      app.log.warn({ err }, "feed rebuild after filter change failed");
    }
    triggerDiscovery("filter changed");
  },
});
await registerDiscoverySettingsRoutes(app, { db });
await registerDiscoveryRoutes(app, { db });
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

await registerNewsRoutes(app, { db, cfg });
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
      .prepare(
        `SELECT id, url, last_polled_at AS lastPolledAt, rss_etag AS rssEtag,
                rss_last_modified AS rssLastModified, thumbnail_url AS thumbnailUrl
           FROM channels WHERE is_active = 1`,
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

// Discovery-Anstoß mit Cooldown: der Feed ruft ihn auch selbst, sobald der
// Vorrat knapp wird — so entsteht nie ein leerer Feed.
let discoveryRunning = false;
let discoveryNextAllowed = 0;
const DISCOVERY_COOLDOWN_MS = 20 * 60 * 1000;
function triggerDiscovery(reason: string): void {
  if (discoveryRunning || Date.now() < discoveryNextAllowed) return;
  // Themensuche bewertet jeden Fund per Sprachmodell — außerhalb des Fensters
  // wartet sie. Der Vorrat reicht ohnehin für Tage.
  if (!isAiWindowOpen(new Date(), cfg.aiWindow)) {
    app.log.debug({ reason }, "discovery verschoben — KI-Fenster geschlossen");
    return;
  }
  discoveryRunning = true;
  discoveryNextAllowed = Date.now() + DISCOVERY_COOLDOWN_MS;
  app.log.info({ reason }, "discovery cycle started");
  runDiscoveryCycle(db)
    .catch((err) => app.log.error({ err }, "discovery cycle crashed"))
    .finally(() => {
      discoveryRunning = false;
    });
}

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

// Discovery: Probe-Kanäle, Themen-Suche, Backfill — zweimal täglich reicht,
// die Quellen sind gedrosselt und der Scorer bleibt der Türsteher.
// Alle 2 Stunden: der Feed ist unendlich, also braucht er stetigen Nachschub.
cron.schedule("15 */2 * * *", () => {
  triggerDiscovery("cron");
});

// Durable ingest drain: claim queued videos one at a time and run the full
// pipeline. A reentrancy guard keeps concurrent ticks from racing; each tick
// drains a bounded batch so it can't run unbounded. A failed video is retried
// (attempts++) until the dead-letter cap, never blocking the rest.
let isDraining = false;
const DRAIN_BATCH = 20;
// So viele ungesehene Shorts soll der Feed mindestens vorrätig haben.
const SHORT_STOCK_TARGET = 60;
// Nach einem transienten Infra-Fehler (Scorer-LLM aus) pausiert der Drain:
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
        await processNewVideo({
          db,
          videoId: job.video_id,
          channelId: job.channel_id,
          fetchMetadata: fetchVideoMetadata,
          fetchTranscript,
          fetchSponsorSegments,
          scorer,
          clipperEnabled: cfg.clipper.enabled,
          summarize: summarizeForFeed,
          source: job.source ?? undefined,
        });
        completeIngest(db, job.video_id);
        processedAny = true;
      } catch (err) {
        const msg = err instanceof Error ? err.message : String(err);
        if (isTransientInfraError(err)) {
          // Scorer-LLM aus / Netz weg: Job zurücklegen ohne attempts++ und den
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
  // Frisch approvte Videos sofort in den Tagesmix aufnehmen.
  if (processedAny) {
    try {
      buildDailyMix(db);
    } catch {
      // best-effort — der 6-Uhr-Cron und der lazy Build fangen es auf
    }
  }

  // Läuft gerade kein Ingest, arbeitet der Scorer den Short-Rückstand ab:
  // vor dem Kurzform-Hinweis fielen native Shorts pauschal durch. Nur bei
  // knappem Short-Vorrat, damit der Feed shortslastig bleibt.
  if (!processedAny) {
    const shortStock = (
      db
        .prepare(
          `SELECT COUNT(*) AS c FROM feed_items f JOIN videos v ON v.id = f.video_id
            WHERE f.seen_at IS NULL AND f.playback_failed = 0 AND v.format = 'short'`,
        )
        .get() as { c: number }
    ).c;
    // Vorrang: Nach geänderten Vorgaben den Bestand aufräumen — sonst stehen
    // Videos im Feed, die den neuen Regeln nicht mehr entsprechen.
    let didRescore = false;
    try {
      if (outdatedFeedItemCount(db) > 0) {
        const removed = await rescoreOutdatedFeedItems({ db, scorer, limit: 6 });
        didRescore = true;
        if (removed > 0) {
          app.log.info({ removed }, "feed items dropped after re-evaluation");
          buildDailyMix(db);
        }
      }
    } catch (err) {
      app.log.warn({ err }, "feed re-evaluation failed");
    }

    if (!didRescore && shortStock < SHORT_STOCK_TARGET) {
      try {
        const n = await rescoreLegacyShorts({ db, scorer, limit: 6 });
        if (n > 0) {
          app.log.info({ approved: n }, "legacy shorts rescored");
          buildDailyMix(db);
        }
      } catch (err) {
        app.log.warn({ err }, "short rescore failed");
      }
    }
  }
}

// Recover stale locks from a previous crash, then drain every minute.
unlockStaleIngest(db);
let aiWindowWasOpen = isAiWindowOpen(new Date(), cfg.aiWindow);
cron.schedule("* * * * *", () => {
  // Der Drain holt Metadaten, zieht Transkripte UND bewertet per Sprachmodell.
  // Außerhalb des Fensters sammelt sich die Warteschlange einfach an; nichts
  // geht verloren, es wird nur später abgearbeitet.
  const open = isAiWindowOpen(new Date(), cfg.aiWindow);
  if (open !== aiWindowWasOpen) {
    aiWindowWasOpen = open;
    app.log.info(
      {
        window: describeAiWindow(cfg.aiWindow),
        queued: (
          db.prepare("SELECT COUNT(*) AS c FROM ingest_queue").get() as { c: number }
        ).c,
      },
      open ? "KI-Fenster geöffnet — Warteschlange wird abgearbeitet" : "KI-Fenster geschlossen",
    );
  }
  if (!open) return;
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
