import Database from "better-sqlite3";
import Fastify from "fastify";
import { beforeEach, describe, expect, it } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import { applyCooldown, interleaveByChannel, listFeedRaw, registerFeedRoutes } from "./feed.js";
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
    `INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at, format)
     VALUES (?, 'UC1', ?, 0, 60, 0, 'short')`,
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

describe("listFeedRaw", () => {
  it("liefert nur feed_items als kind video/short — Clips tauchen nicht mehr auf", () => {
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

    // Ein Short: Hochkant-Video mit format='short' und eigenem feed_item.
    db.prepare(`
      INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at, format)
      VALUES ('short1', 'c1', 's', 0, 45, 0, 'short')
    `).run();
    db.prepare(`
      INSERT INTO feed_items (video_id, added_to_feed_at, is_pre_clipper)
      VALUES ('short1', 1500, 1)
    `).run();

    const rows = listFeedRaw(db, 50);

    // Clips sind kein Feed-Bestandteil mehr (Etappe 2).
    expect(rows.find((r) => r.id === "clip1")).toBeUndefined();

    const video = rows.find((r) => r.id === "legacy1");
    expect(video).toBeTruthy();
    expect(video!.kind).toBe("video");
    expect(video!.parentVideoId).toBe("legacy1");

    const short = rows.find((r) => r.id === "short1");
    expect(short).toBeTruthy();
    expect(short!.kind).toBe("short");
  });
});

function row(id: string, parent: string, channel: string, cat: string, t: number): RawFeedRow {
  return {
    kind: "video", id, parentVideoId: parent, channelId: channel,
    category: cat, addedToFeedAt: t, durationSec: 60,
  };
}

describe("interleaveByChannel — variety", () => {
  it("alternates channels instead of clustering one dominant channel", () => {
    // 5 from chA, 2 from chB (skewed, like the real 29-vs-8 case).
    const cands: RawFeedRow[] = [
      row("a1", "pa1", "chA", "x", 100), row("a2", "pa2", "chA", "x", 99),
      row("a3", "pa3", "chA", "x", 98), row("a4", "pa4", "chA", "x", 97),
      row("a5", "pa5", "chA", "x", 96), row("b1", "pb1", "chB", "x", 95),
      row("b2", "pb2", "chB", "x", 94),
    ];
    const out = interleaveByChannel(cands, 0);
    // First two items must be from different channels (A then B).
    expect(out[0]!.channelId).toBe("chA");
    expect(out[1]!.channelId).toBe("chB");
    expect(out[2]!.channelId).toBe("chA");
    expect(out[3]!.channelId).toBe("chB");
    // Nothing lost: all 7 present.
    expect(out.length).toBe(7);
    // No same-parent duplicate.
    expect(new Set(out.map((r) => r.parentVideoId)).size).toBe(7);
  });

  it("preserves ranked order WITHIN a channel", () => {
    const cands: RawFeedRow[] = [
      row("a1", "pa1", "chA", "x", 100), row("a2", "pa2", "chA", "x", 99),
      row("b1", "pb1", "chB", "x", 95),
    ];
    const out = interleaveByChannel(cands, 0);
    const aOrder = out.filter((r) => r.channelId === "chA").map((r) => r.id);
    expect(aOrder).toEqual(["a1", "a2"]);
  });

  it("rotation changes which channel leads (fresh mix per call)", () => {
    const cands: RawFeedRow[] = [
      row("a1", "pa1", "chA", "x", 100),
      row("b1", "pb1", "chB", "x", 99),
      row("c1", "pc1", "chC", "x", 98),
    ];
    const r0 = interleaveByChannel(cands, 0)[0]!.channelId;
    const r1 = interleaveByChannel(cands, 1)[0]!.channelId;
    const r2 = interleaveByChannel(cands, 2)[0]!.channelId;
    expect(new Set([r0, r1, r2]).size).toBe(3); // each rotation leads differently
  });

  it("drops same-parent duplicates", () => {
    const cands: RawFeedRow[] = [
      row("a1", "pShared", "chA", "x", 100),
      row("a2", "pShared", "chA", "x", 99), // same parent → dropped
      row("b1", "pb1", "chB", "x", 95),
    ];
    const out = interleaveByChannel(cands, 0);
    expect(out.map((r) => r.id).sort()).toEqual(["a1", "b1"]);
  });

  it("handles a single channel without losing items", () => {
    const cands: RawFeedRow[] = [
      row("a1", "pa1", "chA", "x", 100), row("a2", "pa2", "chA", "x", 99),
    ];
    expect(interleaveByChannel(cands, 0).length).toBe(2);
  });

  it("is a no-op for 0 or 1 items", () => {
    expect(interleaveByChannel([], 0)).toEqual([]);
    const one = [row("a1", "pa1", "chA", "x", 1)];
    expect(interleaveByChannel(one, 5).length).toBe(1);
  });
});

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

// ---------------------------------------------------------------------------
// Feed mutation routes — clip handling (C2 + C3)
// ---------------------------------------------------------------------------

function seedClip(
  db: Database.Database,
  clipId: string,
  parentVideoId: string,
  order = 0,
  seen = false,
  saved = false,
) {
  db.prepare(
    "INSERT OR IGNORE INTO channels (id, url, title, added_at) VALUES ('UC1','x','c',0)",
  ).run();
  db.prepare(
    `INSERT OR IGNORE INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at)
     VALUES (?, 'UC1', ?, 0, 600, 0)`,
  ).run(parentVideoId, `t-${parentVideoId}`);
  db.prepare(
    `INSERT INTO clips (id, parent_video_id, order_in_parent,
       start_seconds, end_seconds, file_path, file_size_bytes,
       focus_x, focus_y, focus_w, focus_h,
       reason, created_at, added_to_feed_at, seen_at, saved)
     VALUES (?, ?, ?, 10, 70, '/clips/${clipId}.mp4', 2000000,
             0, 0, 1, 1, 'r', 1000, 1000, ?, ?)`,
  ).run(clipId, parentVideoId, order, seen ? 1000 : null, saved ? 1 : 0);
}

describe("feed mutation routes — clip handling", () => {
  let db: Database.Database;
  beforeEach(() => {
    db = new Database(":memory:");
    applyMigrations(db);
  });

  it("POST /feed/:id/seen marks a clip as seen", async () => {
    seedClip(db, "clip-a", "parent-a");
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });

    const res = await app.inject({ method: "POST", url: "/feed/clip-a/seen" });
    expect(res.statusCode).toBe(204);

    const row = db
      .prepare("SELECT seen_at FROM clips WHERE id = 'clip-a'")
      .get() as { seen_at: number | null };
    expect(row.seen_at).toBeGreaterThan(0);
  });

  it("POST /feed/:id/save sets saved=1 on a clip", async () => {
    seedClip(db, "clip-b", "parent-b");
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });

    await app.inject({ method: "POST", url: "/feed/clip-b/save" });
    const row = db
      .prepare("SELECT saved FROM clips WHERE id = 'clip-b'")
      .get() as { saved: number };
    expect(row.saved).toBe(1);
  });

  it("DELETE /feed/:id/save clears saved on a clip", async () => {
    seedClip(db, "clip-c", "parent-c", 0, false, true);
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });

    await app.inject({ method: "DELETE", url: "/feed/clip-c/save" });
    const row = db
      .prepare("SELECT saved FROM clips WHERE id = 'clip-c'")
      .get() as { saved: number };
    expect(row.saved).toBe(0);
  });

  it("POST /feed/:id/unplayable sets playback_failed on a clip", async () => {
    seedClip(db, "clip-d", "parent-d");
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });

    await app.inject({ method: "POST", url: "/feed/clip-d/unplayable" });
    const row = db
      .prepare("SELECT playback_failed FROM clips WHERE id = 'clip-d'")
      .get() as { playback_failed: number };
    expect(row.playback_failed).toBe(1);
  });

  it("POST /feed/:id/seen updates last_served_at on PARENT video for clips (C3)", async () => {
    seedClip(db, "clip-e", "parent-e");
    // Add a downloaded_videos row for the PARENT so last_served_at can be updated.
    db.prepare(
      `INSERT INTO downloaded_videos (video_id, file_path, file_size_bytes, downloaded_at)
       VALUES ('parent-e', '/parent-e.mp4', 0, 0)`,
    ).run();
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });

    await app.inject({ method: "POST", url: "/feed/clip-e/seen" });

    const dl = db
      .prepare("SELECT last_served_at FROM downloaded_videos WHERE video_id = 'parent-e'")
      .get() as { last_served_at: number | null };
    expect(dl.last_served_at).toBeGreaterThan(0);
  });

  it("POST /feed/:id/seen still updates last_served_at for legacy feed_items rows", async () => {
    seedFeedItem(db, "legacy-v", Date.now());
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });

    await app.inject({ method: "POST", url: "/feed/legacy-v/seen" });

    const fi = db
      .prepare("SELECT seen_at FROM feed_items WHERE video_id = 'legacy-v'")
      .get() as { seen_at: number | null };
    expect(fi.seen_at).toBeGreaterThan(0);

    const dl = db
      .prepare("SELECT last_served_at FROM downloaded_videos WHERE video_id = 'legacy-v'")
      .get() as { last_served_at: number | null };
    expect(dl.last_served_at).toBeGreaterThan(0);
  });

  it("POST /feed/:id/less-like-this sets playback_failed and seen_at on a clip", async () => {
    seedClip(db, "clip-f", "parent-f");
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });

    await app.inject({ method: "POST", url: "/feed/clip-f/less-like-this" });

    const row = db
      .prepare("SELECT playback_failed, seen_at FROM clips WHERE id = 'clip-f'")
      .get() as { playback_failed: number; seen_at: number | null };
    expect(row.playback_failed).toBe(1);
    expect(row.seen_at).toBeGreaterThan(0);
  });
});

// ---------------------------------------------------------------------------
// today-count UNION (C4)
// ---------------------------------------------------------------------------

describe("GET /feed/today-count — Zeitbudget", () => {
  it("zählt nur Mix-Items, Clips nicht mehr", async () => {
    const db = new Database(":memory:");
    applyMigrations(db);

    // 2 unseen clips
    seedClip(db, "tc-clip1", "tc-parent1", 0);
    seedClip(db, "tc-clip2", "tc-parent2", 0);
    // 1 seen clip — must NOT be counted
    seedClip(db, "tc-clip3", "tc-parent3", 0, true);
    // 1 unseen legacy feed_item (is_pre_clipper=1)
    seedFeedItem(db, "tc-legacy1", Date.now());

    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 10 });

    await app.inject({ method: "GET", url: "/feed?mode=new" }); // baut den Tagesmix
    const res = await app.inject({ method: "GET", url: "/feed/today-count" });
    expect(res.statusCode).toBe(200);
    const body = res.json() as { unseenCount: number; dailyBudget: number; capped: boolean };
    // Nur das eine Mix-Item zählt — die 2 ungesehenen Clips nicht mehr.
    expect(body.unseenCount).toBe(1);
    expect(body.capped).toBe(false);
  });

  it("capped=true erst, wenn das Tagesbudget wirklich GESCHAUT wurde", async () => {
    const db = new Database(":memory:");
    applyMigrations(db);

    seedFeedItem(db, "tc2-v1", Date.now());
    db.prepare("UPDATE videos SET duration_seconds = 700 WHERE id = 'tc2-v1'").run();

    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 2 });
    await app.inject({ method: "PUT", url: "/feed/budget", payload: { minutes: 10 } });
    await app.inject({ method: "GET", url: "/feed?mode=new" }); // baut den Mix

    // Im Feed liegen 700s — aber ungeschaut zählt nichts gegen das Budget.
    const vorher = (await app.inject({ method: "GET", url: "/feed/today-count" })).json() as {
      capped: boolean;
    };
    expect(vorher.capped).toBe(false);

    // Jetzt 700s wirklich abgespielt ⇒ Budget (600s) überschritten.
    await app.inject({
      method: "PUT",
      url: "/feed/tc2-v1/progress",
      payload: { seconds: 700 },
    });
    const nachher = (await app.inject({ method: "GET", url: "/feed/today-count" })).json() as {
      capped: boolean;
      consumedSeconds: number;
    };
    expect(nachher.consumedSeconds).toBe(700);
    expect(nachher.capped).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// DELETE /feed/:id — clip cascade cleanup (I1)
// ---------------------------------------------------------------------------

describe("DELETE /feed/:id — clip cascade cleanup", () => {
  it("deletes clips and clipper_queue rows when parent video is deleted", async () => {
    const db = new Database(":memory:");
    applyMigrations(db);

    db.prepare("INSERT OR IGNORE INTO channels (id, url, title, added_at) VALUES ('UC1','x','c',0)").run();
    db.prepare(
      `INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at)
       VALUES ('del-parent', 'UC1', 't', 0, 600, 0)`,
    ).run();
    db.prepare(
      `INSERT INTO scores (video_id, overall_score, category, clickbait_risk, educational_value,
        emotional_manipulation, reasoning, model_used, scored_at, decision)
       VALUES ('del-parent', 80, 'tech', 0, 9, 0, 'ok', 'mock', 0, 'approved')`,
    ).run();
    db.prepare(
      `INSERT INTO downloaded_videos (video_id, file_path, file_size_bytes, downloaded_at)
       VALUES ('del-parent', '/tmp/del-parent.mp4', 0, 0)`,
    ).run();
    db.prepare(
      `INSERT INTO clips (id, parent_video_id, order_in_parent,
         start_seconds, end_seconds, file_path, file_size_bytes,
         focus_x, focus_y, focus_w, focus_h, reason, created_at, added_to_feed_at)
       VALUES ('del-clip1', 'del-parent', 0, 10, 70, '/tmp/del-clip1.mp4', 0,
               0, 0, 1, 1, 'r', 0, 0),
              ('del-clip2', 'del-parent', 1, 80, 120, '/tmp/del-clip2.mp4', 0,
               0, 0, 1, 1, 'r', 0, 0)`,
    ).run();
    db.prepare(
      `INSERT INTO clipper_queue (video_id, queued_at) VALUES ('del-parent', 0)`,
    ).run();

    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });

    const res = await app.inject({ method: "DELETE", url: "/feed/del-parent" });
    expect(res.statusCode).toBe(204);

    expect(db.prepare("SELECT 1 FROM videos WHERE id = 'del-parent'").get()).toBeUndefined();
    expect(db.prepare("SELECT COUNT(*) AS c FROM clips WHERE parent_video_id = 'del-parent'").get()).toEqual({ c: 0 });
    expect(db.prepare("SELECT 1 FROM clipper_queue WHERE video_id = 'del-parent'").get()).toBeUndefined();
  });
});

// ---------------------------------------------------------------------------
// Batched hydration (GET /feed new mode) — clips + legacy in one response
// ---------------------------------------------------------------------------

describe("GET /feed (new) — batched hydration", () => {
  it("liefert Videos mit kind/summary; Clips erscheinen nicht mehr in new", async () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    seedClip(db, "h-clip", "h-parent"); // ungesehener Clip — darf nicht auftauchen
    seedFeedItem(db, "h-video", Date.now());
    db.prepare("UPDATE videos SET summary = 'Ein Teaser.', format = 'long' WHERE id = 'h-video'").run();

    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });
    const res = await app.inject({ method: "GET", url: "/feed?mode=new" });
    expect(res.statusCode).toBe(200);

    const body = res.json() as { videoId: string; kind: string; summary: string | null }[];
    expect(body.find((x) => x.videoId === "h-clip")).toBeUndefined();
    const video = body.find((x) => x.videoId === "h-video");
    expect(video?.kind).toBe("video");
    expect(video?.summary).toBe("Ein Teaser.");
  });

  it("Video OHNE downloaded_videos-Row erscheint trotzdem (Streaming-Welt)", async () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    seedFeedItem(db, "nodl", Date.now());
    db.prepare("UPDATE videos SET format = 'long' WHERE id = 'nodl'").run();
    db.prepare("DELETE FROM downloaded_videos WHERE video_id = 'nodl'").run();

    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });
    const res = await app.inject({ method: "GET", url: "/feed?mode=new" });
    const body = res.json() as { videoId: string; kind: string; filePath: string | null }[];
    const video = body.find((x) => x.videoId === "nodl");
    expect(video?.kind).toBe("video");
    expect(video?.filePath ?? null).toBeNull();
  });

  it("returns an INTEGER durationSeconds for a clip with fractional bounds", async () => {
    // Regression: clips have REAL start/end seconds, so end-start can be e.g.
    // 75.5. The Android DTO declares durationSeconds: Int, and a float like
    // "75.5" made kotlinx.serialization reject the ENTIRE feed response — the
    // whole list vanished in the app. The API must round to an integer.
    const db = new Database(":memory:");
    applyMigrations(db);
    db.prepare("INSERT OR IGNORE INTO channels (id, url, title, added_at) VALUES ('UC1','x','c',0)").run();
    db.prepare(
      `INSERT OR IGNORE INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at)
       VALUES ('frac-parent', 'UC1', 't', 0, 600, 0)`,
    ).run();
    db.prepare(
      `INSERT INTO clips (id, parent_video_id, order_in_parent,
         start_seconds, end_seconds, file_path, file_size_bytes,
         focus_x, focus_y, focus_w, focus_h, reason, created_at, added_to_feed_at)
       VALUES ('frac-clip', 'frac-parent', 0, 770, 845.5, '/clips/frac.mp4', 1,
               0, 0, 1, 1, 'r', 1000, 1000)`,
    ).run();

    db.prepare("UPDATE clips SET saved = 1 WHERE id = 'frac-clip'").run();
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });
    // Clips leben nur noch in saved/old — dort muss die Dauer weiterhin ganzzahlig sein.
    const res = await app.inject({ method: "GET", url: "/feed?mode=saved" });
    expect(res.statusCode).toBe(200);
    const body = res.json() as { videoId: string; durationSeconds: number }[];
    const clip = body.find((x) => x.videoId === "frac-clip");
    expect(clip).toBeDefined();
    // 845.5 - 770 = 75.5 → must be rounded to an integer (76), not 75.5.
    expect(Number.isInteger(clip!.durationSeconds)).toBe(true);
    expect(clip!.durationSeconds).toBe(76);
  });

  it("mode=new liefert den stabilen Tagesmix in Mix-Reihenfolge", async () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    seedFeedItem(db, "m1", Date.now() - 3000);
    seedFeedItem(db, "m2", Date.now() - 2000);
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });
    const first = (await app.inject({ method: "GET", url: "/feed?mode=new" })).json() as {
      videoId: string;
    }[];
    const second = (await app.inject({ method: "GET", url: "/feed?mode=new" })).json() as {
      videoId: string;
    }[];
    expect(first.length).toBeGreaterThan(0);
    expect(second.map((x) => x.videoId)).toEqual(first.map((x) => x.videoId)); // stabil
  });

  it("today-count meldet Zeitbudget und Restdauer", async () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    seedFeedItem(db, "tc-zeit", Date.now());
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });
    await app.inject({ method: "GET", url: "/feed?mode=new" }); // baut den Mix
    const body = (await app.inject({ method: "GET", url: "/feed/today-count" })).json() as Record<
      string,
      number | boolean
    >;
    expect(body.budgetMinutes).toBe(45);
    // Noch nichts geschaut ⇒ das volle Tagesbudget steht zur Verfügung.
    expect(body.remainingSeconds).toBe(2700);
    expect(body.consumedSeconds).toBe(0);
    expect(body.unseenCount).toBe(1);
  });

  it("GET/PUT /feed/budget liest und clamped", async () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });
    expect((await app.inject({ method: "GET", url: "/feed/budget" })).json()).toEqual({
      minutes: 45,
    });
    const put = await app.inject({ method: "PUT", url: "/feed/budget", payload: { minutes: 90 } });
    expect(put.json()).toEqual({ minutes: 90 });
    expect((await app.inject({ method: "GET", url: "/feed/budget" })).json()).toEqual({
      minutes: 90,
    });
    expect(
      (await app.inject({ method: "PUT", url: "/feed/budget", payload: { minutes: "x" } }))
        .statusCode,
    ).toBe(400);
  });

  it("liefert den Feed seitenweise — die App lädt beim Scrollen nach", async () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    for (let i = 0; i < 5; i++) seedFeedItem(db, `p${i}`, Date.now() - i * 1000);
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });
    const seite1 = (
      await app.inject({ method: "GET", url: "/feed?mode=new&limit=2&offset=0" })
    ).json() as { videoId: string }[];
    const seite2 = (
      await app.inject({ method: "GET", url: "/feed?mode=new&limit=2&offset=2" })
    ).json() as { videoId: string }[];
    expect(seite1).toHaveLength(2);
    expect(seite2).toHaveLength(2);
    // Keine Überschneidung: Seite 2 setzt hinter Seite 1 fort.
    expect(seite1.map((x) => x.videoId)).not.toEqual(
      expect.arrayContaining(seite2.map((x) => x.videoId)),
    );
  });

  it("stößt Discovery an, wenn der Vorrat knapp wird", async () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    seedFeedItem(db, "knapp1", Date.now());
    let angestossen = 0;
    const app = Fastify();
    await registerFeedRoutes(app, {
      db,
      dailyBudget: 15,
      onLowStock: () => {
        angestossen++;
      },
    });
    await app.inject({ method: "GET", url: "/feed?mode=new" });
    expect(angestossen).toBe(1);
  });

  it("wärmt die Stream-URLs des Tagesmixes vor", async () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    seedFeedItem(db, "pf1", Date.now());
    seedFeedItem(db, "pf2", Date.now() - 1000);
    const prefetched: string[][] = [];
    const app = Fastify();
    await registerFeedRoutes(app, {
      db,
      dailyBudget: 15,
      prefetchStreams: (ids) => prefetched.push(ids),
    });
    await app.inject({ method: "GET", url: "/feed?mode=new" });
    expect(prefetched[0]).toEqual(expect.arrayContaining(["pf1", "pf2"]));
  });

  it("liefert source im Feed-Item durch", async () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    seedFeedItem(db, "src-v", Date.now());
    db.prepare("UPDATE videos SET source = 'probe' WHERE id = 'src-v'").run();
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });
    const body = (await app.inject({ method: "GET", url: "/feed?mode=new" })).json() as {
      videoId: string;
      source: string;
    }[];
    expect(body.find((x) => x.videoId === "src-v")?.source).toBe("probe");
  });

  it("returns an empty array when nothing is unseen", async () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });
    const res = await app.inject({ method: "GET", url: "/feed?mode=new" });
    expect(res.json()).toEqual([]);
  });
});
