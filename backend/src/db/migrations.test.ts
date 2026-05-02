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
      "channel_match_scores",
      "channels",
      "clipper_queue",
      "clipper_runtime",
      "clips",
      "discovery_settings",
      "downloaded_videos",
      "feed_items",
      "filter_config",
      "manga_arcs",
      "manga_chapter_read",
      "manga_chapters",
      "manga_library",
      "manga_pages",
      "manga_progress",
      "manga_series",
      "manga_sync_jobs",
      "scores",
      "series",
      "sponsor_segments",
      "videos",
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
    // Manually create a minimal pre-clipper schema (without is_pre_clipper).
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
    // Apply the pre-existing migrations FIRST (without clipper additions).
    // We simulate this by applying the current migrations and then verifying
    // backfill behavior: insert a feed_item BEFORE the migration would
    // re-run, then confirm the row got is_pre_clipper=1.
    applyMigrations(db);
    db.prepare("INSERT INTO channels (id,url,title,added_at) VALUES ('c1','x','c',0)").run();
    db.prepare(`
      INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at)
      VALUES ('v1', 'c1', 't', 0, 100, 0)
    `).run();
    // Insert a row directly using is_pre_clipper=0 to mimic legacy data
    db.prepare(`
      INSERT INTO feed_items (video_id, added_to_feed_at, is_pre_clipper)
      VALUES ('v1', 0, 0)
    `).run();
    // Re-running migrations should NOT re-backfill (idempotency); is_pre_clipper stays 0.
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
    // Same parent_video_id + same order_in_parent → must throw
    expect(() => insertClip.run("c-b", 0)).toThrow(/UNIQUE/);
  });
});
