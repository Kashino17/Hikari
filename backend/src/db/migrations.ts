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

  // RSS conditional-fetch validators — the ETag / Last-Modified from the last
  // successful poll, sent back as If-None-Match / If-Modified-Since so YouTube
  // can answer 304 Not Modified when nothing changed.
  addColumnIfMissing(db, "channels", "rss_etag", "TEXT");
  addColumnIfMissing(db, "channels", "rss_last_modified", "TEXT");

  // Auto-Clipper: per-video clip lifecycle status.
  // NULL = legacy/pre-clipper; values: pending | analyzing | rendering | done
  //                                    | no_highlights | failed
  addColumnIfMissing(db, "videos", "clip_status", "TEXT");

  // Auto-Clipper: legacy items predating the clipper get is_pre_clipper=1
  // and continue to behave as full-length feed items. New clipper-path items
  // never go into feed_items; they go into the new `clips` table instead.
  const feedCols = db.prepare("PRAGMA table_info(feed_items)").all() as { name: string }[];
  const hadPreClipper = feedCols.some((c) => c.name === "is_pre_clipper");
  addColumnIfMissing(db, "feed_items", "is_pre_clipper", "INTEGER DEFAULT 0");
  if (!hadPreClipper) {
    // First migration: every existing row is legacy. From here on, new rows
    // are inserted with explicit is_pre_clipper values.
    db.exec(`UPDATE feed_items SET is_pre_clipper = 1`);
  }

  // Clipper runtime state singleton — seed if not yet present.
  db.exec(`
    INSERT OR IGNORE INTO clipper_runtime (id, force_until, updated_at)
    VALUES (1, 0, ${Date.now()})
  `);

  // Per-clip display mode. "smart-crop" (default) crops tightly to focus.
  // "fit" letterboxes the full 16:9 frame in 9:16 with blur-bg padding —
  // for slides / text-heavy content where cropping would hide info.
  addColumnIfMissing(db, "clips", "display_mode", "TEXT NOT NULL DEFAULT 'smart-crop'");

  // Per-clip sub-segment overrides for display_mode/focus. JSON array
  // [{startSec, endSec, mode, focus?}, ...] in clip-local seconds. NULL means
  // use the single clip-level display_mode for the whole duration (backwards
  // compat).
  addColumnIfMissing(db, "clips", "display_segments", "TEXT");

  // Word-level captions JSON: [{start, end, text}, ...] in clip-local seconds.
  // NULL means transcription hasn't run yet; client falls back to no subtitles.
  addColumnIfMissing(db, "clips", "captions", "TEXT");

  // AI-generated context summary per clip (1-3 short sentences). Generated
  // from the clip's Whisper-transcribed captions, not from the parent video
  // title. Displayed as a top overlay during the first ~6s of playback.
  addColumnIfMissing(db, "clips", "context", "TEXT");

  // Etappe 2 (Feed-Streaming-Umbau): Format-/Quellen-/Zusammenfassungs-Spalten.
  // aspect_ratio existiert auf Ur-DBs vor dem initialen Schema evtl. nicht —
  // fuer den Format-Backfill unten absichern.
  addColumnIfMissing(db, "videos", "aspect_ratio", "TEXT");
  addColumnIfMissing(db, "videos", "format", "TEXT"); // 'short' | 'long'
  addColumnIfMissing(db, "videos", "source", "TEXT"); // 'subscription' | 'probe' | 'topic' | 'backfill'
  addColumnIfMissing(db, "videos", "summary", "TEXT"); // KI-Kurzbeschreibung fuer Feed-Karten
  // Backfill: Bestandsvideos nach Heuristik klassifizieren (Hochkant + <=3 min = Short).
  db.exec(`UPDATE videos SET format = CASE
    WHEN aspect_ratio = '9:16' AND duration_seconds <= 180 THEN 'short' ELSE 'long' END
    WHERE format IS NULL`);
  db.exec("UPDATE videos SET source = 'subscription' WHERE source IS NULL");
  // Ur-DBs vor dem initialen Schema absichern (wie aspect_ratio oben).
  addColumnIfMissing(db, "feed_items", "seen_at", "INTEGER");
  // Backfill Etappe 2: approvte Videos aus der Clip-Ära haben nie eine
  // feed_items-Row bekommen (der Feed bestand aus ihren Clips). Ohne Row wären
  // sie im videobasierten Feed unsichtbar. Gesehen-Status erbt vom Clip-Zustand:
  // wurde IRGENDEIN Clip des Videos gesehen, gilt das Video als gesehen.
  db.exec(`
    INSERT OR IGNORE INTO feed_items (video_id, added_to_feed_at, seen_at, is_pre_clipper)
    SELECT s.video_id,
           COALESCE(
             (SELECT MIN(c.added_to_feed_at) FROM clips c WHERE c.parent_video_id = s.video_id),
             (SELECT v.discovered_at FROM videos v WHERE v.id = s.video_id)
           ),
           (SELECT MAX(c.seen_at) FROM clips c
             WHERE c.parent_video_id = s.video_id AND c.seen_at IS NOT NULL),
           1
      FROM scores s
     WHERE s.decision = 'approved'
       AND NOT EXISTS (SELECT 1 FROM feed_items f WHERE f.video_id = s.video_id)
  `);

  // Kurzform-Feed: Länge entscheidet über das Format, nicht das Seitenverhältnis.
  // Kurze Querformat-Videos gehörten bisher in die Langvideo-Schlange.
  db.exec("UPDATE videos SET format = 'short' WHERE duration_seconds <= 180 AND format <> 'short'");
  db.exec("UPDATE videos SET format = 'long' WHERE duration_seconds > 180 AND format <> 'long'");

  // Etappe 3 (Discovery): Kanal-Status + Ingest-Quelle.
  addColumnIfMissing(db, "channels", "is_active", "INTEGER DEFAULT 1"); // Ur-DB-Absicherung
  addColumnIfMissing(db, "channels", "status", "TEXT"); // 'subscribed' | 'probe' | 'blocked'
  addColumnIfMissing(db, "ingest_queue", "source", "TEXT"); // 'subscription' | 'probe' | 'topic' | 'backfill'
  // Bestand: alle aktiven Kanaele sind Abos; inaktive bleiben statuslos (weiches Loeschen).
  db.exec("UPDATE channels SET status = 'subscribed' WHERE status IS NULL AND is_active = 1");
}
