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
      "channels",
      "daily_mix_items",
      "downloaded_videos",
      "feed_items",
      "feed_settings",
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
      "pending_imports",
      "scores",
      "series",
      "sponsor_segments",
      "video_progress",
      "videos",
      "watch_history",
      "watch_later",
    ]);
  });

  it("is idempotent — can run twice without error", () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    expect(() => applyMigrations(db)).not.toThrow();
  });
});

describe("migrations", () => {
  it("daily_mix_items und feed_settings existieren mit erwarteten Spalten", () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    const mixCols = (
      db.prepare("PRAGMA table_info(daily_mix_items)").all() as { name: string }[]
    ).map((c) => c.name);
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
    const rows = db.prepare("SELECT id, format, source FROM videos ORDER BY id").all() as {
      id: string;
      format: string;
      source: string;
    }[];
    expect(rows).toEqual([
      { id: "v-long", format: "long", source: "subscription" },
      { id: "v-short", format: "short", source: "subscription" },
    ]);
  });
});

/**
 * Baut auf einer frischen DB den Altbestand nach: Clipper-, Bewertungs- und
 * Discovery-Tabellen/-Spalten, wie sie vor der Abo-Umstellung existierten —
 * inklusive Index auf einer zu löschenden Spalte (DROP COLUMN scheitert sonst).
 */
function legacyDb(): Database.Database {
  const db = new Database(":memory:");
  db.pragma("foreign_keys = ON");
  applyMigrations(db);
  db.exec(`
    CREATE TABLE clips (
      id TEXT PRIMARY KEY,
      parent_video_id TEXT NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
      order_in_parent INTEGER NOT NULL,
      file_path TEXT NOT NULL,
      UNIQUE(parent_video_id, order_in_parent)
    );
    CREATE INDEX idx_clips_parent ON clips(parent_video_id);
    CREATE TABLE clipper_queue (video_id TEXT PRIMARY KEY REFERENCES videos(id), queued_at INTEGER NOT NULL);
    CREATE TABLE clipper_runtime (id INTEGER PRIMARY KEY CHECK (id = 1), force_until INTEGER, updated_at INTEGER);
    CREATE TABLE filter_config (id INTEGER PRIMARY KEY CHECK (id = 1), filter_json TEXT NOT NULL);
    CREATE TABLE channel_filters (channel_id TEXT PRIMARY KEY REFERENCES channels(id), filter_json TEXT NOT NULL);
    CREATE TABLE discovery_settings (id INTEGER PRIMARY KEY CHECK (id = 1), discovery_ratio REAL);
    CREATE TABLE channel_match_scores (channel_id TEXT PRIMARY KEY REFERENCES channels(id), calculated_score REAL);
    CREATE TABLE category_preferences (category TEXT PRIMARY KEY, weight REAL);
    ALTER TABLE videos ADD COLUMN clip_status TEXT;
    CREATE INDEX idx_videos_clip_status ON videos(clip_status) WHERE clip_status IS NOT NULL;
    ALTER TABLE feed_items ADD COLUMN is_pre_clipper INTEGER DEFAULT 0;
    ALTER TABLE channels ADD COLUMN auto_approve INTEGER DEFAULT 0;
  `);
  db.pragma("user_version = 0");
  return db;
}

function tableNames(db: Database.Database): string[] {
  return (
    db.prepare("SELECT name FROM sqlite_master WHERE type='table'").all() as { name: string }[]
  ).map((t) => t.name);
}

function columnNames(db: Database.Database, table: string): string[] {
  return (db.prepare(`PRAGMA table_info(${table})`).all() as { name: string }[]).map((c) => c.name);
}

describe("Migration: Clipper, Bewertung und Discovery entfernt", () => {
  it("frische DB bekommt weder Clip- noch Bewertungs-Tabellen/-Spalten", () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    expect(columnNames(db, "videos")).not.toContain("clip_status");
    expect(columnNames(db, "feed_items")).not.toContain("is_pre_clipper");
    expect(columnNames(db, "channels")).not.toContain("auto_approve");
    expect(db.pragma("user_version", { simple: true })).toBe(3);
  });

  it("räumt Altbestand ab: Tabellen, Spalten und Index auf clip_status", () => {
    const db = legacyDb();
    db.prepare(
      "INSERT INTO channels (id,url,title,added_at,status) VALUES ('c1','x','c',0,'subscribed')",
    ).run();
    db.prepare(
      "INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at, clip_status) VALUES ('v1','c1','t',0,60,0,'done')",
    ).run();
    db.prepare(
      "INSERT INTO clips (id, parent_video_id, order_in_parent, file_path) VALUES ('k1','v1',0,'/c.mp4')",
    ).run();
    db.prepare(
      "INSERT INTO feed_items (video_id, added_to_feed_at, is_pre_clipper) VALUES ('v1',0,1)",
    ).run();

    applyMigrations(db);

    const names = tableNames(db);
    for (const gone of [
      "clips",
      "clipper_queue",
      "clipper_runtime",
      "filter_config",
      "channel_filters",
      "discovery_settings",
      "channel_match_scores",
      "category_preferences",
    ]) {
      expect(names).not.toContain(gone);
    }
    expect(columnNames(db, "videos")).not.toContain("clip_status");
    expect(columnNames(db, "feed_items")).not.toContain("is_pre_clipper");
    expect(columnNames(db, "channels")).not.toContain("auto_approve");
    expect(
      db.prepare("SELECT 1 FROM sqlite_master WHERE name = 'idx_videos_clip_status'").get(),
    ).toBeUndefined();
    // Die Daten selbst bleiben: nur die Clip-Spalte ist weg.
    expect(db.prepare("SELECT COUNT(*) c FROM feed_items").get()).toEqual({ c: 1 });
    expect(db.prepare("SELECT title FROM videos WHERE id='v1'").get()).toEqual({ title: "t" });

    expect(() => applyMigrations(db)).not.toThrow(); // idempotent
  });
});

describe("Migration: Feed = nur Abos (einmalige Datenbereinigung)", () => {
  function seed(db: Database.Database): void {
    db.exec(`
      INSERT INTO channels (id, url, title, added_at, is_active, status) VALUES
        ('sub',     'x', 'Abo',          0, 1, 'subscribed'),
        ('gone',    'x', 'Abbestellt',   0, 0, 'subscribed'),
        ('probe',   'x', 'Probe',        0, 0, 'probe'),
        ('blocked', 'x', 'Geblockt',     0, 0, 'blocked'),
        ('manual',  'manual:hikari', 'Manuell', 0, 1, 'subscribed');
      INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at) VALUES
        ('v-sub',     'sub',     't', 0, 60, 0),
        ('v-gone',    'gone',    't', 0, 60, 0),
        ('v-probe',   'probe',   't', 0, 60, 0),
        ('v-saved',   'probe',   't', 0, 60, 0),
        ('v-later',   'probe',   't', 0, 60, 0),
        ('v-dl',      'probe',   't', 0, 60, 0),
        ('v-blocked', 'blocked', 't', 0, 60, 0),
        ('v-manual',  'manual',  't', 0, 60, 0);
      INSERT INTO feed_items (video_id, added_to_feed_at, saved) VALUES
        ('v-sub', 0, 0), ('v-gone', 0, 0), ('v-probe', 0, 0), ('v-saved', 0, 1),
        ('v-later', 0, 0), ('v-dl', 0, 0), ('v-blocked', 0, 0), ('v-manual', 0, 0);
      INSERT INTO watch_later (video_id, added_at) VALUES ('v-later', 0);
      INSERT INTO downloaded_videos (video_id, file_path, file_size_bytes, downloaded_at)
        VALUES ('v-dl', '/x.mp4', 1, 0);
      INSERT INTO daily_mix_items (mix_date, video_id, position, source, duration_seconds) VALUES
        ('2026-11-05', 'v-sub', 0, 'subscription', 60),
        ('2026-11-05', 'v-probe', 1, 'probe', 60),
        ('2026-11-05', 'v-saved', 2, 'probe', 60);
      INSERT INTO ingest_queue (video_id, channel_id, queued_at, source) VALUES
        ('q-sub', 'sub', 0, 'subscription'),
        ('q-probe', 'probe', 0, 'probe'),
        ('q-topic', 'discovery-topics', 0, 'topic'),
        ('q-gone', 'gone', 0, 'subscription');
    `);
  }

  it("löscht Fremd-Feed-Einträge und -Jobs, verschont Gespeichertes, Später, Downloads, Importe", () => {
    const db = legacyDb();
    seed(db);
    applyMigrations(db);

    const feed = (
      db.prepare("SELECT video_id FROM feed_items ORDER BY video_id").all() as {
        video_id: string;
      }[]
    ).map((r) => r.video_id);
    expect(feed).toEqual(["v-dl", "v-later", "v-manual", "v-saved", "v-sub"]);

    const mix = (
      db.prepare("SELECT video_id FROM daily_mix_items ORDER BY video_id").all() as {
        video_id: string;
      }[]
    ).map((r) => r.video_id);
    expect(mix).toEqual(["v-sub"]);

    const jobs = (
      db.prepare("SELECT video_id FROM ingest_queue ORDER BY video_id").all() as {
        video_id: string;
      }[]
    ).map((r) => r.video_id);
    expect(jobs).toEqual(["q-sub"]);

    // videos und channels bleiben vollständig; „Später ansehen" leert
    // Migration 3 (die Liste gibt es in der App nicht mehr).
    expect(db.prepare("SELECT COUNT(*) c FROM videos").get()).toEqual({ c: 8 });
    expect(db.prepare("SELECT COUNT(*) c FROM channels").get()).toEqual({ c: 5 });
    expect(db.prepare("SELECT COUNT(*) c FROM watch_later").get()).toEqual({ c: 0 });
    expect(db.pragma("user_version", { simple: true })).toBe(3);
  });

  it("läuft nur einmal — spätere Läufe fassen den Bestand nicht mehr an", () => {
    const db = legacyDb();
    applyMigrations(db); // Einmal-Bereinigung auf leerer DB
    seed(db);
    applyMigrations(db);
    expect(db.prepare("SELECT COUNT(*) c FROM feed_items").get()).toEqual({ c: 8 });
    expect(db.prepare("SELECT COUNT(*) c FROM ingest_queue").get()).toEqual({ c: 4 });
  });

  it("aktive Bestandskanäle ohne Status zählen als Abo und bleiben im Feed", () => {
    const db = legacyDb();
    db.exec(`
      INSERT INTO channels (id, url, title, added_at, is_active) VALUES ('old', 'x', 'Alt', 0, 1);
      INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at)
        VALUES ('v-old', 'old', 't', 0, 60, 0);
      INSERT INTO feed_items (video_id, added_to_feed_at) VALUES ('v-old', 0);
    `);
    applyMigrations(db);
    expect(db.prepare("SELECT COUNT(*) c FROM feed_items").get()).toEqual({ c: 1 });
  });
});
