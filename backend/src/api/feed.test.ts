import Database from "better-sqlite3";
import Fastify from "fastify";
import { beforeEach, describe, expect, it } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import { applyCooldown, interleaveByChannel, rankCandidates, registerFeedRoutes } from "./feed.js";
import type { RawFeedRow } from "./feed.js";

function seedFeedItem(
  db: Database.Database,
  id: string,
  addedAt: number,
  seen = false,
  saved = false,
  channelId = "UC1",
) {
  db.prepare(
    "INSERT OR IGNORE INTO channels (id, url, title, added_at, is_active, status) VALUES ('UC1','x','c',0,1,'subscribed')",
  ).run();
  db.prepare(
    `INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at, format)
     VALUES (?, ?, ?, 0, 60, 0, 'short')`,
  ).run(id, channelId, `t-${id}`);
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
    `INSERT INTO feed_items (video_id, added_to_feed_at, seen_at, saved)
     VALUES (?, ?, ?, ?)`,
  ).run(id, addedAt, seen ? addedAt : null, saved ? 1 : 0);
}

/** Kanal, der NICHT abonniert ist (alte Probe aus der entfernten Discovery). */
function seedProbeChannel(db: Database.Database) {
  db.prepare(
    "INSERT OR IGNORE INTO channels (id, url, title, added_at, is_active, status) VALUES ('UC-probe','x','p',0,0,'probe')",
  ).run();
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
    // seen items must NOT appear in "new" mode
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
    // Only the unseen item should appear — seen items are excluded
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

  it("POST /feed/:id/seen updates last_served_at of the downloaded file", async () => {
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

  it("POST /feed/:id/less-like-this sets playback_failed and seen_at", async () => {
    seedFeedItem(db, "meh", Date.now());
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });
    await app.inject({ method: "POST", url: "/feed/meh/less-like-this" });
    const row = db
      .prepare("SELECT playback_failed, seen_at FROM feed_items WHERE video_id = 'meh'")
      .get() as { playback_failed: number; seen_at: number | null };
    expect(row.playback_failed).toBe(1);
    expect(row.seen_at).toBeGreaterThan(0);
  });
});

// ---------------------------------------------------------------------------
// Feed = nur Abos
// ---------------------------------------------------------------------------

describe("Feed = nur abonnierte Kanäle", () => {
  let db: Database.Database;
  beforeEach(() => {
    db = new Database(":memory:");
    applyMigrations(db);
    seedProbeChannel(db);
  });

  it("new, old und die automatische Queue zeigen keine Videos fremder Kanäle", async () => {
    const now = Date.now();
    seedFeedItem(db, "abo-neu", now);
    seedFeedItem(db, "abo-alt", now - 1000, true);
    seedFeedItem(db, "probe-neu", now, false, false, "UC-probe");
    seedFeedItem(db, "probe-alt", now - 1000, true, false, "UC-probe");
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });

    const ids = async (url: string) =>
      ((await app.inject({ method: "GET", url })).json() as { videoId: string }[]).map(
        (x) => x.videoId,
      );
    expect(await ids("/feed?mode=new")).toEqual(["abo-neu"]);
    expect(await ids("/feed?mode=old")).toEqual(["abo-alt"]);
    expect(await ids("/queue")).not.toContain("probe-neu");
    expect(await ids("/queue")).not.toContain("probe-alt");
  });

  it("abbestellter Kanal verschwindet sofort aus dem Feed", async () => {
    seedFeedItem(db, "v1", Date.now());
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });
    expect((await app.inject({ method: "GET", url: "/feed" })).json()).toHaveLength(1);
    db.prepare("UPDATE channels SET is_active = 0 WHERE id = 'UC1'").run();
    expect((await app.inject({ method: "GET", url: "/feed" })).json()).toEqual([]);
  });

  it("Gespeichertes bleibt sichtbar — auch von fremden Kanälen", async () => {
    seedFeedItem(db, "probe-saved", Date.now(), true, true, "UC-probe");
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });
    const body = (await app.inject({ method: "GET", url: "/feed?mode=saved" })).json() as {
      videoId: string;
    }[];
    expect(body.map((x) => x.videoId)).toEqual(["probe-saved"]);
  });

  it("manuelle Importe landen nie im Feed", async () => {
    db.prepare(
      "INSERT INTO channels (id, url, title, added_at, is_active, status) VALUES ('manual','manual:hikari','M',0,1,'subscribed')",
    ).run();
    seedFeedItem(db, "folge1", Date.now(), false, true, "manual");
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });
    expect((await app.inject({ method: "GET", url: "/feed" })).json()).toEqual([]);
    expect((await app.inject({ method: "GET", url: "/feed?mode=saved" })).json()).toEqual([]);
  });

  it("entfernte Bewertungs-Endpunkte antworten 404", async () => {
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });
    expect((await app.inject({ method: "GET", url: "/feed/rescore" })).statusCode).toBe(404);
    expect((await app.inject({ method: "POST", url: "/feed/rescore" })).statusCode).toBe(404);
    expect((await app.inject({ method: "GET", url: "/rejected" })).statusCode).toBe(404);
  });
});

// ---------------------------------------------------------------------------
// Reihenfolge-Helfer: rankCandidates, interleaveByChannel, applyCooldown
// ---------------------------------------------------------------------------

function row(id: string, channel: string, cat: string, t: number): RawFeedRow {
  return { kind: "video", id, channelId: channel, category: cat, addedToFeedAt: t, durationSec: 60 };
}

describe("rankCandidates", () => {
  it("sortiert neueste zuerst, Gleichstand nach id", () => {
    const out = rankCandidates([row("b", "c", "x", 5), row("a", "c", "x", 5), row("n", "c", "x", 9)]);
    expect(out.map((r) => r.id)).toEqual(["n", "a", "b"]);
  });
});

describe("interleaveByChannel — variety", () => {
  it("alternates channels instead of clustering one dominant channel", () => {
    // 5 from chA, 2 from chB (skewed, like the real 29-vs-8 case).
    const cands: RawFeedRow[] = [
      row("a1", "chA", "x", 100), row("a2", "chA", "x", 99),
      row("a3", "chA", "x", 98), row("a4", "chA", "x", 97),
      row("a5", "chA", "x", 96), row("b1", "chB", "x", 95),
      row("b2", "chB", "x", 94),
    ];
    const out = interleaveByChannel(cands, 0);
    // First two items must be from different channels (A then B).
    expect(out[0]!.channelId).toBe("chA");
    expect(out[1]!.channelId).toBe("chB");
    expect(out[2]!.channelId).toBe("chA");
    expect(out[3]!.channelId).toBe("chB");
    // Nothing lost: all 7 present.
    expect(out.length).toBe(7);
    // No duplicate.
    expect(new Set(out.map((r) => r.id)).size).toBe(7);
  });

  it("preserves ranked order WITHIN a channel", () => {
    const cands: RawFeedRow[] = [
      row("a1", "chA", "x", 100), row("a2", "chA", "x", 99),
      row("b1", "chB", "x", 95),
    ];
    const out = interleaveByChannel(cands, 0);
    const aOrder = out.filter((r) => r.channelId === "chA").map((r) => r.id);
    expect(aOrder).toEqual(["a1", "a2"]);
  });

  it("rotation changes which channel leads (fresh mix per call)", () => {
    const cands: RawFeedRow[] = [
      row("a1", "chA", "x", 100),
      row("b1", "chB", "x", 99),
      row("c1", "chC", "x", 98),
    ];
    const r0 = interleaveByChannel(cands, 0)[0]!.channelId;
    const r1 = interleaveByChannel(cands, 1)[0]!.channelId;
    const r2 = interleaveByChannel(cands, 2)[0]!.channelId;
    expect(new Set([r0, r1, r2]).size).toBe(3); // each rotation leads differently
  });

  it("drops duplicate videos", () => {
    const cands: RawFeedRow[] = [
      row("a1", "chA", "x", 100),
      row("a1", "chA", "x", 99), // same video → dropped
      row("b1", "chB", "x", 95),
    ];
    const out = interleaveByChannel(cands, 0);
    expect(out.map((r) => r.id).sort()).toEqual(["a1", "b1"]);
  });

  it("handles a single channel without losing items", () => {
    const cands: RawFeedRow[] = [
      row("a1", "chA", "x", 100), row("a2", "chA", "x", 99),
    ];
    expect(interleaveByChannel(cands, 0).length).toBe(2);
  });

  it("is a no-op for 0 or 1 items", () => {
    expect(interleaveByChannel([], 0)).toEqual([]);
    const one = [row("a1", "chA", "x", 1)];
    expect(interleaveByChannel(one, 5).length).toBe(1);
  });
});

describe("applyCooldown", () => {
  it("never places the same video within 3-item window", () => {
    const cands: RawFeedRow[] = [
      row("p1", "ch1", "math", 1000),
      row("p1", "ch1", "math", 999),
      row("p2", "ch2", "tech", 998),
      row("p1", "ch1", "math", 997),
    ];
    const out = applyCooldown(cands, 4);
    const parents = out.map((r) => r.id);
    for (let i = 0; i < parents.length - 2; i++) {
      expect(new Set([parents[i], parents[i+1], parents[i+2]]).size).toBeGreaterThan(1);
    }
  });

  it("allows channel up to 2× in window, blocks 3rd until cooldown", () => {
    // All same category so topic-mix doesn't interfere — isolates channel cooldown.
    const cands: RawFeedRow[] = [
      row("c1", "ch1", "math", 1000),
      row("c2", "ch1", "math", 999),
      row("c3", "ch1", "math", 998),
      row("c4", "ch2", "math", 997),
      row("c5", "ch1", "math", 996),
    ];
    const out = applyCooldown(cands, 5);
    expect(out[0].channelId).toBe("ch1");
    expect(out[1].channelId).toBe("ch1");
    expect(out[2].channelId).not.toBe("ch1");
  });

  it("topic-mix lookahead: prefers different category from last when possible", () => {
    const cands: RawFeedRow[] = [
      row("c1", "ch1", "math", 1000),
      row("c2", "ch2", "math", 999),
      row("c3", "ch3", "tech", 998),
    ];
    const out = applyCooldown(cands, 3);
    expect(out[0].id).toBe("c1");
    expect(out[1].id).toBe("c3");
    expect(out[2].id).toBe("c2");
  });
});

// ---------------------------------------------------------------------------
// today-count
// ---------------------------------------------------------------------------

describe("GET /feed/today-count — Zeitbudget", () => {
  it("zählt nur Mix-Items abonnierter Kanäle", async () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    seedProbeChannel(db);
    seedFeedItem(db, "tc-probe1", Date.now(), false, false, "UC-probe");
    seedFeedItem(db, "tc-probe2", Date.now(), false, false, "UC-probe");
    seedFeedItem(db, "tc-legacy1", Date.now());

    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 10 });

    await app.inject({ method: "GET", url: "/feed?mode=new" }); // baut den Tagesmix
    const res = await app.inject({ method: "GET", url: "/feed/today-count" });
    expect(res.statusCode).toBe(200);
    const body = res.json() as { unseenCount: number; dailyBudget: number; capped: boolean };
    // Nur das eine Abo-Video zählt — die zwei fremden nicht.
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
// Batched hydration (GET /feed new mode)
// ---------------------------------------------------------------------------

describe("GET /feed (new) — batched hydration", () => {
  it("liefert Videos mit kind/summary", async () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    seedFeedItem(db, "h-video", Date.now());
    db.prepare("UPDATE videos SET summary = 'Ein Teaser.', format = 'long' WHERE id = 'h-video'").run();

    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });
    const res = await app.inject({ method: "GET", url: "/feed?mode=new" });
    expect(res.statusCode).toBe(200);

    const body = res.json() as { videoId: string; kind: string; summary: string | null }[];
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
    db.prepare("UPDATE videos SET source = 'subscription' WHERE id = 'src-v'").run();
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });
    const body = (await app.inject({ method: "GET", url: "/feed?mode=new" })).json() as {
      videoId: string;
      source: string;
    }[];
    expect(body.find((x) => x.videoId === "src-v")?.source).toBe("subscription");
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
