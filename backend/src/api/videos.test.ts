import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import Fastify from "fastify";
import fastifyStatic from "@fastify/static";
import { describe, expect, it } from "vitest";
import { registerVideosRoutes } from "./videos.js";

describe("videos API", () => {
  it("serves MP4 with Content-Type and supports Range requests", async () => {
    const dir = mkdtempSync(join(tmpdir(), "hikari-videos-"));
    writeFileSync(join(dir, "vid1.mp4"), Buffer.alloc(10000, 0xaa));

    const app = Fastify();
    await app.register(fastifyStatic, { root: dir, prefix: "/videos/" });
    await registerVideosRoutes(app, { db: undefined as never, videoDir: dir, coverDir: dir, extractor: null });

    const full = await app.inject({ method: "GET", url: "/videos/vid1.mp4" });
    expect(full.statusCode).toBe(200);
    expect(full.body.length).toBe(10000);

    const ranged = await app.inject({
      method: "GET",
      url: "/videos/vid1.mp4",
      headers: { range: "bytes=0-99" },
    });
    expect(ranged.statusCode).toBe(206);
    expect(ranged.body.length).toBe(100);
  });

  it("GET /library liefert watchLater und history hydratisiert", async () => {
    const Database = (await import("better-sqlite3")).default;
    const { applyMigrations } = await import("../db/migrations.js");
    const db = new Database(":memory:");
    applyMigrations(db);
    const seedVideo = (id: string) => {
      db.prepare(
        "INSERT OR IGNORE INTO channels (id,url,title,added_at) VALUES ('c1','x','C',0)",
      ).run();
      db.prepare(
        `INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at, format)
         VALUES (?, 'c1', ?, 0, 600, 0, 'long')`,
      ).run(id, `t-${id}`);
      db.prepare(
        `INSERT INTO scores (video_id, overall_score, category, clickbait_risk, educational_value,
          emotional_manipulation, reasoning, model_used, scored_at, decision)
         VALUES (?, 80, 'x', 1, 9, 0, 'ok', 'mock', 0, 'approved')`,
      ).run(id);
      db.prepare(
        "INSERT INTO feed_items (video_id, added_to_feed_at, is_pre_clipper) VALUES (?, 0, 1)",
      ).run(id);
    };
    seedVideo("wl1");
    seedVideo("seen1");
    db.prepare("INSERT INTO watch_later (video_id, added_at) VALUES ('wl1', 1000)").run();
    db.prepare("UPDATE feed_items SET seen_at = 2000 WHERE video_id = 'seen1'").run();

    const dir = mkdtempSync(join(tmpdir(), "hikari-lib-"));
    const app = Fastify();
    await registerVideosRoutes(app, { db, videoDir: dir, coverDir: dir, extractor: null });
    const body = (await app.inject({ method: "GET", url: "/library" })).json() as {
      watchLater: { videoId: string }[];
      history: { videoId: string }[];
    };
    expect(body.watchLater.map((x) => x.videoId)).toEqual(["wl1"]);
    expect(body.history.map((x) => x.videoId)).toEqual(["seen1"]);
  });

  it("returns 404 for missing file", async () => {
    const dir = mkdtempSync(join(tmpdir(), "hikari-videos-empty-"));
    const app = Fastify();
    await app.register(fastifyStatic, { root: dir, prefix: "/videos/" });
    await registerVideosRoutes(app, { db: undefined as never, videoDir: dir, coverDir: dir, extractor: null });

    const res = await app.inject({ method: "GET", url: "/videos/nope.mp4" });
    expect(res.statusCode).toBe(404);
  });
});
