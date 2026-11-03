import { mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import Database from "better-sqlite3";
import Fastify from "fastify";
import { describe, expect, it } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import { registerDownloadsRoutes } from "./downloads.js";
import { registerFeedRoutes } from "./feed.js";
import { registerVideosRoutes } from "./videos.js";

function seedSeriesVideo(db: Database.Database): void {
  db.prepare("INSERT OR IGNORE INTO channels (id,url,title,added_at) VALUES ('UC1','x','K',0)").run();
  db.prepare("INSERT INTO series (id,title,added_at) VALUES ('s1','Serie',0)").run();
  db.prepare(
    `INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at,
                         series_id, season, episode, format)
     VALUES ('v1', 'UC1', 'Folge 3', 0, 3000, 0, 's1', 1, 3, 'long')`,
  ).run();
  db.prepare(
    `INSERT INTO downloaded_videos (video_id, file_path, file_size_bytes, downloaded_at)
     VALUES ('v1', '/tmp/v1.mp4', 100, 1000)`,
  ).run();
}

async function buildApp(db: Database.Database) {
  const dir = mkdtempSync(join(tmpdir(), "hikari-progress-"));
  const app = Fastify();
  await registerFeedRoutes(app, { db, dailyBudget: 10 });
  await registerVideosRoutes(app, { db, videoDir: dir, coverDir: dir, extractor: null });
  await registerDownloadsRoutes(app, { db, diskLimitBytes: 0 });
  return app;
}

describe("Wiedergabefortschritt für Serienfolgen ohne feed_items", () => {
  it("PUT /feed/:id/progress schreibt video_progress und /series/:id liefert ihn", async () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    seedSeriesVideo(db);

    const app = await buildApp(db);
    const put = await app.inject({
      method: "PUT",
      url: "/feed/v1/progress",
      payload: { seconds: 1200 },
    });
    expect(put.statusCode).toBe(204);

    // Kein feed_items-Insert: Serienfolgen sollen den Feed nicht fluten.
    const feedRows = db.prepare("SELECT COUNT(*) AS c FROM feed_items").get() as { c: number };
    expect(feedRows.c).toBe(0);

    const series = (await app.inject({ method: "GET", url: "/series/s1" })).json() as {
      videos: { id: string; progress_seconds: number; progress_updated_at: number }[];
    };
    const episode = series.videos.find((v) => v.id === "v1");
    expect(episode?.progress_seconds).toBe(1200);
    expect(episode?.progress_updated_at).toBeGreaterThan(0);
  });

  it("GET /downloads liefert den Fortschritt mit", async () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    seedSeriesVideo(db);
    db.prepare(
      "INSERT INTO video_progress (video_id, progress_seconds, updated_at) VALUES ('v1', 1200, 0)",
    ).run();

    const app = await buildApp(db);
    const body = (await app.inject({ method: "GET", url: "/downloads" })).json() as {
      series: { episodes: { id: string; progress_seconds: number }[] }[];
    };
    expect(body.series[0]?.episodes[0]?.progress_seconds).toBe(1200);
  });

  it("Clip-Fortschritt überschreibt nicht den Fortschritt der ganzen Folge", async () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    seedSeriesVideo(db);
    db.prepare(
      "INSERT INTO video_progress (video_id, progress_seconds, updated_at) VALUES ('v1', 1200, 0)",
    ).run();
    db.prepare(
      `INSERT INTO clips (id, parent_video_id, order_in_parent, start_seconds, end_seconds,
         file_path, file_size_bytes, focus_x, focus_y, focus_w, focus_h, created_at, added_to_feed_at)
       VALUES ('c1', 'v1', 0, 0, 60, '/tmp/c1.mp4', 10, 0, 0, 1, 1, 0, 0)`,
    ).run();

    const app = await buildApp(db);
    await app.inject({ method: "PUT", url: "/feed/c1/progress", payload: { seconds: 30 } });

    const row = db
      .prepare("SELECT progress_seconds FROM video_progress WHERE video_id = 'v1'")
      .get() as { progress_seconds: number };
    expect(row.progress_seconds).toBe(1200);
  });
});
