import Database from "better-sqlite3";
import { describe, expect, it } from "vitest";
import { applyMigrations } from "./migrations.js";

describe("applyMigrations", () => {
  it("creates all expected tables on a fresh database", () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    const tables = db
      .prepare("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")
      .all() as { name: string }[];
    const names = tables.map((t) => t.name);
    expect(names).toEqual([
      "category_preferences",
      "channel_filters",
      "channel_match_scores",
      "channels",
      "clipper_queue",
      "clipper_runtime",
      "clips",
      "daily_mix_items",
      "discovery_settings",
      "downloaded_videos",
      "feed_items",
      "feed_settings",
      "filter_config",
      "ingest_queue",
      "manga_arcs",
      "manga_chapter_read",
      "manga_chapters",
      "manga_library",
      "manga_pages",
      "manga_progress",
      "manga_series",
      "manga_sync_jobs",
      "news_briefings",
      "scores",
      "series",
      "sponsor_segments",
      "videos",
      "watch_later",
    ]);
  });

  it("is idempotent — can run twice without error", () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    expect(() => applyMigrations(db)).not.toThrow();
  });
});

describe("clipper migrations", () => {
  it("creates clips table with all required columns", () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    const cols = db.prepare("PRAGMA table_info(clips)").all() as { name: string }[];
    const names = cols.map((c) => c.name);
    expect(names).toEqual(
      expect.arrayContaining([
        "id", "parent_video_id", "order_in_parent",
        "start_seconds", "end_seconds", "file_path", "file_size_bytes",
        "focus_x", "focus_y", "focus_w", "focus_h",
        "reason", "created_at",
        "added_to_feed_at", "seen_at", "saved", "playback_failed", "progress_seconds",
      ]),
    );
  });

  it("creates clipper_queue table with lock fields", () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    const cols = db.prepare("PRAGMA table_info(clipper_queue)").all() as { name: string }[];
    expect(cols.map((c) => c.name)).toEqual(
      expect.arrayContaining([
        "video_id", "queued_at", "attempts", "last_error", "locked_at", "locked_step",
      ]),
    );
  });

  it("adds clip_status column to videos", () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    const cols = db.prepare("PRAGMA table_info(videos)").all() as { name: string }[];
    expect(cols.find((c) => c.name === "clip_status")).toBeTruthy();
  });

  it("adds is_pre_clipper column to feed_items, defaulting to 0", () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    const cols = db.prepare("PRAGMA table_info(feed_items)").all() as
      { name: string; dflt_value: string | null }[];
    const col = cols.find((c) => c.name === "is_pre_clipper");
    expect(col).toBeTruthy();
    expect(col?.dflt_value).toBe("0");
  });

  it("backfills is_pre_clipper=1 on rows that pre-existed the column", () => {
    const db = new Database(":memory:");
    db.exec(`CREATE TABLE channels (id TEXT PRIMARY KEY, url TEXT, title TEXT, added_at INTEGER);`);
    db.exec(`CREATE TABLE videos (id TEXT PRIMARY KEY, channel_id TEXT, title TEXT,
      published_at INTEGER, duration_seconds INTEGER, discovered_at INTEGER);`);
    db.exec(`CREATE TABLE feed_items (video_id TEXT PRIMARY KEY, added_to_feed_at INTEGER);`);
    db.prepare("INSERT INTO channels (id,url,title,added_at) VALUES ('c1','x','c',0)").run();
    db.prepare(`
      INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at)
      VALUES ('v1', 'c1', 't', 0, 100, 0)
    `).run();
    db.prepare("INSERT INTO feed_items (video_id, added_to_feed_at) VALUES ('v1', 0)").run();
    applyMigrations(db);
    const row = db.prepare("SELECT is_pre_clipper FROM feed_items WHERE video_id='v1'")
      .get() as { is_pre_clipper: number };
    expect(row.is_pre_clipper).toBe(1);
  });

  it("does NOT re-backfill is_pre_clipper on subsequent migrations (idempotency)", () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    db.prepare("INSERT INTO channels (id,url,title,added_at) VALUES ('c1','x','c',0)").run();
    db.prepare(`
      INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at)
      VALUES ('v1', 'c1', 't', 0, 100, 0)
    `).run();
    db.prepare(`
      INSERT INTO feed_items (video_id, added_to_feed_at, is_pre_clipper)
      VALUES ('v1', 0, 0)
    `).run();
    applyMigrations(db);
    const row = db.prepare("SELECT is_pre_clipper FROM feed_items WHERE video_id='v1'")
      .get() as { is_pre_clipper: number };
    expect(row.is_pre_clipper).toBe(0);
  });

  it("clips table has UNIQUE(parent_video_id, order_in_parent)", () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    db.prepare("INSERT INTO channels (id,url,title,added_at) VALUES ('c1','x','c',0)").run();
    db.prepare(`
      INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at)
      VALUES ('v1', 'c1', 't', 0, 100, 0)
    `).run();
    const insertClip = db.prepare(`
      INSERT INTO clips (id, parent_video_id, order_in_parent,
        start_seconds, end_seconds, file_path, file_size_bytes,
        focus_x, focus_y, focus_w, focus_h,
        reason, created_at, added_to_feed_at)
      VALUES (?, 'v1', ?, 0, 60, '/p.mp4', 1, 0, 0, 1, 1, 'r', 0, 0)
    `);
    insertClip.run("c-a", 0);
    expect(() => insertClip.run("c-b", 0)).toThrow(/UNIQUE/);
  });

  it("daily_mix_items und feed_settings existieren mit erwarteten Spalten", () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    const mixCols = (db.prepare("PRAGMA table_info(daily_mix_items)").all() as { name: string }[]).map(
      (c) => c.name,
    );
    expect(mixCols).toEqual(
      expect.arrayContaining(["mix_date", "video_id", "position", "source", "duration_seconds"]),
    );
    const fsCols = (db.prepare("PRAGMA table_info(feed_settings)").all() as { name: string }[]).map(
      (c) => c.name,
    );
    expect(fsCols).toEqual(expect.arrayContaining(["daily_time_budget_minutes"]));
  });

  it("channels.status backfilled auf subscribed, ingest_queue hat source", () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    db.prepare(
      "INSERT INTO channels (id,url,title,added_at,is_active) VALUES ('c1','x','c',0,1)",
    ).run();
    db.prepare("UPDATE channels SET status = NULL").run();
    applyMigrations(db);
    expect(db.prepare("SELECT status FROM channels WHERE id='c1'").get()).toEqual({
      status: "subscribed",
    });
    const cols = (db.prepare("PRAGMA table_info(ingest_queue)").all() as { name: string }[]).map(
      (c) => c.name,
    );
    expect(cols).toContain("source");
  });

  it("backfillt feed_items für approvte Clip-Ära-Videos (seen erbt vom Clip)", () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    db.prepare("INSERT INTO channels (id,url,title,added_at) VALUES ('c1','x','c',0)").run();
    db.prepare(`
      INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at)
      VALUES ('unseen-v', 'c1', 't', 0, 600, 111), ('seen-v', 'c1', 't', 0, 600, 222)
    `).run();
    db.prepare(`
      INSERT INTO scores (video_id, overall_score, category, clickbait_risk, educational_value,
        emotional_manipulation, reasoning, model_used, scored_at, decision)
      VALUES ('unseen-v', 80, 'x', 0, 9, 0, 'r', 'm', 0, 'approved'),
             ('seen-v',   80, 'x', 0, 9, 0, 'r', 'm', 0, 'approved')
    `).run();
    const clip = db.prepare(`
      INSERT INTO clips (id, parent_video_id, order_in_parent, start_seconds, end_seconds,
        file_path, file_size_bytes, focus_x, focus_y, focus_w, focus_h, reason,
        created_at, added_to_feed_at, seen_at)
      VALUES (?, ?, 0, 0, 60, '/c.mp4', 1, 0, 0, 1, 1, 'r', 500, 500, ?)
    `);
    clip.run("cl-unseen", "unseen-v", null);
    clip.run("cl-seen", "seen-v", 999);

    applyMigrations(db); // idempotent — Backfill legt fehlende feed_items an

    const rows = db
      .prepare("SELECT video_id, seen_at, is_pre_clipper FROM feed_items ORDER BY video_id")
      .all() as { video_id: string; seen_at: number | null; is_pre_clipper: number }[];
    expect(rows).toEqual([
      { video_id: "seen-v", seen_at: 999, is_pre_clipper: 1 },
      { video_id: "unseen-v", seen_at: null, is_pre_clipper: 1 },
    ]);

    applyMigrations(db); // nochmal — darf nichts duplizieren
    expect(db.prepare("SELECT COUNT(*) c FROM feed_items").get()).toEqual({ c: 2 });
  });

  it("videos hat format/source/summary und Bestand wird als short/long backfilled", () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    const cols = (db.prepare("PRAGMA table_info(videos)").all() as { name: string }[]).map(
      (c) => c.name,
    );
    expect(cols).toEqual(expect.arrayContaining(["format", "source", "summary"]));

    db.prepare("INSERT INTO channels (id,url,title,added_at) VALUES ('c1','x','c',0)").run();
    db.prepare(`
      INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, aspect_ratio, discovered_at)
      VALUES ('v-short', 'c1', 't', 0, 45, '9:16', 0),
             ('v-long',  'c1', 't', 0, 1200, '16:9', 0)
    `).run();
    db.prepare("UPDATE videos SET format = NULL, source = NULL").run();
    applyMigrations(db); // idempotent — Backfill klassifiziert den Bestand
    const rows = db
      .prepare("SELECT id, format, source FROM videos ORDER BY id")
      .all() as { id: string; format: string; source: string }[];
    expect(rows).toEqual([
      { id: "v-long", format: "long", source: "subscription" },
      { id: "v-short", format: "short", source: "subscription" },
    ]);
  });
});
