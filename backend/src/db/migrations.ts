import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import type Database from "better-sqlite3";

const here = dirname(fileURLToPath(import.meta.url));

interface ColumnInfo {
  name: string;
}

/**
 * Adds a column only if it doesn't already exist. SQLite has no "ADD COLUMN
 * IF NOT EXISTS" so we look it up via PRAGMA first.
 */
function addColumnIfMissing(
  db: Database.Database,
  table: string,
  column: string,
  ddlType: string,
): void {
  const cols = db.prepare(`PRAGMA table_info(${table})`).all() as ColumnInfo[];
  if (cols.some((c) => c.name === column)) return;
  db.exec(`ALTER TABLE ${table} ADD COLUMN ${column} ${ddlType}`);
}

export function applyMigrations(db: Database.Database): void {
  const schema = readFileSync(join(here, "schema.sql"), "utf8");
  db.exec(schema);

  // Live-DB upgrades: columns added after v0.8.x channels page polish.
  // Each call is idempotent — safe across reboots and across CI test DBs.
  addColumnIfMissing(db, "channels", "handle", "TEXT");
  addColumnIfMissing(db, "channels", "description", "TEXT");
  addColumnIfMissing(db, "channels", "subscribers", "INTEGER");
  addColumnIfMissing(db, "channels", "thumbnail_url", "TEXT");

  // Netflix-style upgrades
  addColumnIfMissing(db, "videos", "series_id", "TEXT REFERENCES series(id)");
  addColumnIfMissing(db, "videos", "season", "INTEGER");
  addColumnIfMissing(db, "videos", "episode", "INTEGER");
  addColumnIfMissing(db, "videos", "dub_language", "TEXT");
  addColumnIfMissing(db, "videos", "sub_language", "TEXT");
  addColumnIfMissing(db, "videos", "is_movie", "INTEGER DEFAULT 0");

  addColumnIfMissing(db, "feed_items", "progress_seconds", "REAL DEFAULT 0");
  addColumnIfMissing(db, "feed_items", "queued_at", "INTEGER");
  addColumnIfMissing(db, "feed_items", "queue_order", "INTEGER");

  addColumnIfMissing(db, "manga_chapters", "is_available", "INTEGER DEFAULT 1");

  // Green-Card / "Vertrauenskanal" — when 1, processNewVideo skips the AI scorer
  // for videos from this channel (still respects isLive + duration filter).
  addColumnIfMissing(db, "channels", "auto_approve", "INTEGER DEFAULT 0");

  // Channel banner art (16:7-ish wide image), extracted from yt-dlp.
  addColumnIfMissing(db, "channels", "banner_url", "TEXT");
}
