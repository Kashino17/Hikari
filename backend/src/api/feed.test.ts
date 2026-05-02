import Database from "better-sqlite3";
import Fastify from "fastify";
import { beforeEach, describe, expect, it } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import { applyCooldown, listFeedRaw, registerFeedRoutes } from "./feed.js";
import type { RawFeedRow } from "./feed.js";

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
  // is_pre_clipper=1: these are legacy feed items, equivalent to what the migration
  // backfills for rows that pre-date the auto-clipper pipeline.
  db.prepare(
    `INSERT INTO feed_items (video_id, added_to_feed_at, seen_at, saved, is_pre_clipper)
     VALUES (?, ?, ?, ?, 1)`,
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

  it("GET /feed (new mode) returns only unseen items sorted by recency", async () => {
    const now = Date.now();
    // seed 3 seen + 2 unseen items across unique parents/channels to avoid cooldown reorder
    seedFeedItem(db, "seen1", now - 1000, true);
    seedFeedItem(db, "seen2", now - 2000, true);
    seedFeedItem(db, "unseen1", now - 500);
    seedFeedItem(db, "unseen2", now - 300);
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });

    const res = await app.inject({ method: "GET", url: "/feed" });
    const body = res.json() as { videoId: string }[];
    // seen items must NOT appear in "new" mode (listFeedRaw filters seen_at IS NULL)
    expect(body.find((x) => x.videoId === "seen1")).toBeUndefined();
    expect(body.find((x) => x.videoId === "seen2")).toBeUndefined();
    // unseen items must appear
    const ids = body.map((x) => x.videoId);
    expect(ids).toContain("unseen1");
    expect(ids).toContain("unseen2");
  });

  it("GET /feed?mode=new returns unseen legacy items newest-first (no seen bleed-through)", async () => {
    const now = Date.now();
    for (let i = 0; i < 12; i++) {
      seedFeedItem(db, `seen${i}`, now - i * 1000, true);
    }
    seedFeedItem(db, "unseen_only", now - 50);
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });

    const res = await app.inject({ method: "GET", url: "/feed?mode=new" });
    expect(res.statusCode).toBe(200);
    const body = res.json() as { videoId: string }[];
    // Only the unseen item should appear — seen items are excluded from listFeedRaw
    expect(body).toHaveLength(1);
    expect(body[0].videoId).toBe("unseen_only");
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

// ---------------------------------------------------------------------------
// New helpers: listFeedRaw UNION + applyCooldown
// ---------------------------------------------------------------------------

describe("listFeedRaw UNION", () => {
  it("returns clip rows with kind='clip' and parentVideoId of source video, plus legacy rows", () => {
    const db = new Database(":memory:");
    applyMigrations(db);

    db.prepare("INSERT INTO channels (id,url,title,added_at) VALUES ('c1','x','Ch',0)").run();
    db.prepare(`
      INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at)
      VALUES ('parent1', 'c1', 't', 0, 600, 0), ('legacy1', 'c1', 'l', 0, 600, 0)
    `).run();
    db.prepare(`
      INSERT INTO scores (video_id, overall_score, category, clickbait_risk,
        educational_value, emotional_manipulation, reasoning, model_used,
        scored_at, decision)
      VALUES ('parent1', 80, 'math', 0, 9, 0, 'r', 'mock', 0, 'approved'),
             ('legacy1', 80, 'tech', 0, 9, 0, 'r', 'mock', 0, 'approved')
    `).run();
    db.prepare(`
      INSERT INTO clips (id, parent_video_id, order_in_parent,
        start_seconds, end_seconds, file_path, file_size_bytes,
        focus_x, focus_y, focus_w, focus_h,
        reason, created_at, added_to_feed_at)
      VALUES ('clip1', 'parent1', 0, 30, 90, '/c.mp4', 5000000, 0, 0, 1, 1, 'r', 2000, 2000)
    `).run();
    db.prepare(`
      INSERT INTO feed_items (video_id, added_to_feed_at, is_pre_clipper)
      VALUES ('legacy1', 1000, 1)
    `).run();

    const rows = listFeedRaw(db, 50);

    const clip = rows.find((r) => r.id === "clip1");
    expect(clip).toBeTruthy();
    expect(clip!.kind).toBe("clip");
    expect(clip!.parentVideoId).toBe("parent1");

    const legacy = rows.find((r) => r.id === "legacy1");
    expect(legacy).toBeTruthy();
    expect(legacy!.kind).toBe("legacy");
    expect(legacy!.parentVideoId).toBe("legacy1");
  });
});

function row(id: string, parent: string, channel: string, cat: string, t: number): RawFeedRow {
  return {
    kind: "clip", id, parentVideoId: parent, channelId: channel,
    category: cat, addedToFeedAt: t, durationSec: 60,
  };
}

describe("applyCooldown", () => {
  it("never places same parent_video_id within 3-item window", () => {
    const cands: RawFeedRow[] = [
      row("c1", "p1", "ch1", "math", 1000),
      row("c2", "p1", "ch1", "math", 999),
      row("c3", "p2", "ch2", "tech", 998),
      row("c4", "p1", "ch1", "math", 997),
    ];
    const out = applyCooldown(cands, 4);
    const parents = out.map((r) => r.parentVideoId);
    for (let i = 0; i < parents.length - 2; i++) {
      expect(new Set([parents[i], parents[i+1], parents[i+2]]).size).toBeGreaterThan(1);
    }
  });

  it("allows channel up to 2× in window, blocks 3rd until cooldown", () => {
    // All same category so topic-mix doesn't interfere — isolates channel cooldown.
    const cands: RawFeedRow[] = [
      row("c1", "p1", "ch1", "math", 1000),
      row("c2", "p2", "ch1", "math", 999),
      row("c3", "p3", "ch1", "math", 998),
      row("c4", "p4", "ch2", "math", 997),
      row("c5", "p5", "ch1", "math", 996),
    ];
    const out = applyCooldown(cands, 5);
    expect(out[0].channelId).toBe("ch1");
    expect(out[1].channelId).toBe("ch1");
    expect(out[2].channelId).not.toBe("ch1");
  });

  it("topic-mix lookahead: prefers different category from last when possible", () => {
    const cands: RawFeedRow[] = [
      row("c1", "p1", "ch1", "math", 1000),
      row("c2", "p2", "ch2", "math", 999),
      row("c3", "p3", "ch3", "tech", 998),
    ];
    const out = applyCooldown(cands, 3);
    expect(out[0].id).toBe("c1");
    expect(out[1].id).toBe("c3");
    expect(out[2].id).toBe("c2");
  });
});
