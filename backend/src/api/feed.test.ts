import Database from "better-sqlite3";
import Fastify from "fastify";
import { beforeEach, describe, expect, it } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import { registerFeedRoutes } from "./feed.js";

function seedFeedItem(
  db: Database.Database,
  id: string,
  addedAt: number,
  seen = false,
  saved = false,
) {
  db.prepare(
    "INSERT OR IGNORE INTO channels (id, url, title, added_at) VALUES ('UC1','x','c',0)",
  ).run();
  db.prepare(
    `INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at)
     VALUES (?, 'UC1', ?, 0, 60, 0)`,
  ).run(id, `t-${id}`);
  db.prepare(
    `INSERT INTO scores (video_id, overall_score, category, clickbait_risk, educational_value,
      emotional_manipulation, reasoning, model_used, scored_at, decision)
     VALUES (?, 80, 'science', 1, 9, 0, 'ok', 'mock', 0, 'approved')`,
  ).run(id);
  db.prepare(
    `INSERT INTO downloaded_videos (video_id, file_path, file_size_bytes, downloaded_at)
     VALUES (?, '/x', 0, 0)`,
  ).run(id);
  db.prepare(
    `INSERT INTO feed_items (video_id, added_to_feed_at, seen_at, saved) VALUES (?, ?, ?, ?)`,
  ).run(id, addedAt, seen ? addedAt : null, saved ? 1 : 0);
}

describe("feed API", () => {
  let db: Database.Database;
  beforeEach(() => {
    db = new Database(":memory:");
    applyMigrations(db);
  });

  it("GET /feed returns all unseen items, newest first, without daily budget capping", async () => {
    const today = Date.now();
    for (let i = 0; i < 20; i++) {
      seedFeedItem(db, `v${i}`, today - i * 1000);
    }
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 5 });

    const res = await app.inject({ method: "GET", url: "/feed" });
    expect(res.statusCode).toBe(200);
    const body = res.json() as { videoId: string }[];
    expect(body).toHaveLength(20);
    expect(body[0].videoId).toBe("v0");
  });

  it("GET /feed excludes seen items older than the newest baseline", async () => {
    const now = Date.now();
    for (let i = 0; i < 12; i++) {
      seedFeedItem(db, `seen${i}`, now - (i + 1) * 1000, true);
    }
    seedFeedItem(db, "unseen1", now - 500);
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });

    const res = await app.inject({ method: "GET", url: "/feed" });
    const body = res.json() as { videoId: string }[];
    expect(body.map((x) => x.videoId)).toEqual([
      "unseen1",
      "seen0",
      "seen1",
      "seen2",
      "seen3",
      "seen4",
      "seen5",
      "seen6",
      "seen7",
      "seen8",
    ]);
  });

  it("POST /feed/:id/seen marks the item seen", async () => {
    seedFeedItem(db, "v1", Date.now());
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });
    const res = await app.inject({ method: "POST", url: "/feed/v1/seen" });
    expect(res.statusCode).toBe(204);
    const row = db
      .prepare("SELECT seen_at FROM feed_items WHERE video_id='v1'")
      .get() as { seen_at: number | null };
    expect(row.seen_at).toBeGreaterThan(0);
  });

  it("POST /feed/:id/save toggles saved, DELETE unsets", async () => {
    seedFeedItem(db, "v1", Date.now());
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });
    await app.inject({ method: "POST", url: "/feed/v1/save" });
    expect(
      db.prepare("SELECT saved FROM feed_items WHERE video_id='v1'").get(),
    ).toEqual({ saved: 1 });
    await app.inject({ method: "DELETE", url: "/feed/v1/save" });
    expect(
      db.prepare("SELECT saved FROM feed_items WHERE video_id='v1'").get(),
    ).toEqual({ saved: 0 });
  });

  it("POST /feed/:id/unplayable sets playback_failed", async () => {
    seedFeedItem(db, "v1", Date.now());
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });
    await app.inject({ method: "POST", url: "/feed/v1/unplayable" });
    expect(
      db.prepare("SELECT playback_failed FROM feed_items WHERE video_id='v1'").get(),
    ).toEqual({ playback_failed: 1 });
  });

  it("GET /feed?mode=new keeps the newest 10 items even after they were seen", async () => {
    const now = Date.now();
    for (let i = 0; i < 12; i++) {
      seedFeedItem(db, `seen${i}`, now - i * 1000, true);
    }
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });

    const res = await app.inject({ method: "GET", url: "/feed?mode=new" });
    expect(res.statusCode).toBe(200);
    const body = res.json() as { videoId: string }[];
    expect(body.map((x) => x.videoId)).toEqual([
      "seen0",
      "seen1",
      "seen2",
      "seen3",
      "seen4",
      "seen5",
      "seen6",
      "seen7",
      "seen8",
      "seen9",
    ]);
  });

  it("GET /feed?mode=old returns only seen items, newest seenAt first", async () => {
    const now = Date.now();
    seedFeedItem(db, "seen_old", now - 5000, true);
    seedFeedItem(db, "seen_new", now - 1000, true);
    seedFeedItem(db, "unseen1", now - 500);
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });

    const res = await app.inject({ method: "GET", url: "/feed?mode=old" });
    expect(res.statusCode).toBe(200);
    const body = res.json() as { videoId: string; seenAt: number }[];
    // unseen1 must not appear
    expect(body.find((x) => x.videoId === "unseen1")).toBeUndefined();
    // both seen items must appear
    expect(body.some((x) => x.videoId === "seen_old")).toBe(true);
    expect(body.some((x) => x.videoId === "seen_new")).toBe(true);
    // newest seenAt first
    expect(body[0].videoId).toBe("seen_new");
  });

  it("GET /feed?mode=saved returns saved items regardless of seen state", async () => {
    const now = Date.now();
    seedFeedItem(db, "saved_seen", now - 3000, true, true);
    seedFeedItem(db, "saved_new", now - 1000, false, true);
    seedFeedItem(db, "plain_new", now - 500, false, false);
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });

    const res = await app.inject({ method: "GET", url: "/feed?mode=saved" });
    expect(res.statusCode).toBe(200);
    const body = res.json() as { videoId: string }[];
    expect(body.map((x) => x.videoId)).toEqual(["saved_new", "saved_seen"]);
  });

  it("GET /queue returns an automatic daily queue when no explicit queue exists", async () => {
    const now = Date.now();
    seedFeedItem(db, "seen_saved", now - 3000, true, true);
    seedFeedItem(db, "fresh", now - 1000, false, false);
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });

    const res = await app.inject({ method: "GET", url: "/queue" });
    expect(res.statusCode).toBe(200);
    const body = res.json() as { videoId: string; educationalValue: number }[];
    expect(body.map((x) => x.videoId)).toContain("fresh");
    expect(body[0].educationalValue).toBe(9);
  });

  it("POST /queue/:id pins an explicit queue order and DELETE removes it", async () => {
    const now = Date.now();
    seedFeedItem(db, "first", now - 2000);
    seedFeedItem(db, "second", now - 1000);
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });

    expect((await app.inject({ method: "POST", url: "/queue/second" })).statusCode).toBe(204);
    expect((await app.inject({ method: "POST", url: "/queue/first" })).statusCode).toBe(204);

    const queued = await app.inject({ method: "GET", url: "/queue" });
    expect((queued.json() as { videoId: string }[]).map((x) => x.videoId)).toEqual([
      "second",
      "first",
    ]);

    expect((await app.inject({ method: "DELETE", url: "/queue/second" })).statusCode).toBe(204);
    const afterDelete = await app.inject({ method: "GET", url: "/queue" });
    expect((afterDelete.json() as { videoId: string }[]).map((x) => x.videoId)).toEqual([
      "first",
    ]);
  });

  it("GET /feed?mode=invalid returns 400", async () => {
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });
    const res = await app.inject({ method: "GET", url: "/feed?mode=invalid" });
    expect(res.statusCode).toBe(400);
    expect(res.json()).toEqual({ error: "mode must be new, saved, or old" });
  });

  it("DELETE /feed/:id returns 404 for unknown video", async () => {
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });
    const res = await app.inject({ method: "DELETE", url: "/feed/nonexistent" });
    expect(res.statusCode).toBe(404);
    expect(res.json()).toEqual({ error: "video not found" });
  });

  it("DELETE /feed/:id cascades and returns 204", async () => {
    seedFeedItem(db, "del1", Date.now(), true);
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });

    const res = await app.inject({ method: "DELETE", url: "/feed/del1" });
    expect(res.statusCode).toBe(204);

    // Verify cascading deletes
    expect(db.prepare("SELECT 1 FROM videos WHERE id = 'del1'").get()).toBeUndefined();
    expect(db.prepare("SELECT 1 FROM feed_items WHERE video_id = 'del1'").get()).toBeUndefined();
    expect(db.prepare("SELECT 1 FROM downloaded_videos WHERE video_id = 'del1'").get()).toBeUndefined();
    expect(db.prepare("SELECT 1 FROM scores WHERE video_id = 'del1'").get()).toBeUndefined();
  });
});
