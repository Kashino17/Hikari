import { mkdir } from "node:fs/promises";
import Database from "better-sqlite3";
import pino from "pino";
import { loadConfig } from "../src/config.js";
import { applyMigrations } from "../src/db/migrations.js";
import { processNextJob } from "../src/clipper/worker.js";
import { isWindowActive, unlockStale } from "../src/clipper/queue.js";
import { analyzeVideo } from "../src/clipper/qwen-analyzer.js";
import { renderClip } from "../src/clipper/remotion-renderer.js";

const log = pino({ name: "clipper-worker", transport: { target: "pino-pretty" } });

async function main(): Promise<void> {
  const cfg = loadConfig(process.env);
  const db = new Database(cfg.dbPath);
  db.pragma("journal_mode = WAL");
  applyMigrations(db);

  const mediaDir = `${cfg.videoDir}/clips`;
  await mkdir(mediaDir, { recursive: true });

  log.info(
    {
      schedule: `${cfg.clipper.scheduleStartHour}:00–${cfg.clipper.scheduleEndHour}:00`,
      model: cfg.clipper.model,
      provider: cfg.clipper.provider,
    },
    "clipper-worker started",
  );

  const recovered = unlockStale(db, 30 * 60 * 1000);
  if (recovered > 0) log.warn({ recovered }, "unlocked stale locks");

  const POLL_INTERVAL_MS = 60_000;

  let stopping = false;
  process.on("SIGTERM", () => {
    stopping = true;
    log.info("SIGTERM received, draining");
  });
  process.on("SIGINT", () => {
    stopping = true;
    log.info("SIGINT received, draining");
  });

  while (!stopping) {
    if (
      !isWindowActive(
        new Date(),
        cfg.clipper.scheduleStartHour,
        cfg.clipper.scheduleEndHour,
      )
    ) {
      await sleep(POLL_INTERVAL_MS);
      continue;
    }
    try {
      const ran = await processNextJob(db, {
        analyze: analyzeVideo,
        render: renderClip,
        mediaDir,
        analyzerConfig: {
          provider: cfg.clipper.provider,
          baseUrl: cfg.clipper.baseUrl,
          model: cfg.clipper.model,
        },
      });
      if (!ran) await sleep(POLL_INTERVAL_MS);
    } catch (e) {
      log.error({ err: e }, "unhandled error in processNextJob");
      await sleep(POLL_INTERVAL_MS);
    }
  }

  log.info("clipper-worker stopped cleanly");
  db.close();
}

function sleep(ms: number): Promise<void> {
  return new Promise((res) => setTimeout(res, ms));
}

main().catch((e) => {
  log.fatal({ err: e }, "fatal");
  process.exit(1);
});
