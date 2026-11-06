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

/**
 * Drops a column only if it exists. SQLite refuses `DROP COLUMN` on a column
 * that is part of an index — every user-created index of the table that
 * mentions the column is dropped first. PK/UNIQUE/FK columns can't be dropped
 * this way at all; none of the callers touches such a column.
 */
function dropColumnIfExists(db: Database.Database, table: string, column: string): void {
  const cols = db.prepare(`PRAGMA table_info(${table})`).all() as ColumnInfo[];
  if (!cols.some((c) => c.name === column)) return;
  const indexes = db
    .prepare(
      "SELECT name, sql FROM sqlite_master WHERE type = 'index' AND tbl_name = ? AND sql IS NOT NULL",
    )
    .all(table) as { name: string; sql: string }[];
  const mentions = new RegExp(`\\b${column}\\b`);
  for (const idx of indexes) {
    if (mentions.test(idx.sql)) db.exec(`DROP INDEX IF EXISTS ${idx.name}`);
  }
  db.exec(`ALTER TABLE ${table} DROP COLUMN ${column}`);
}

/**
 * Feed = nur Abos (Migration 1). Einmalig: alles, was Bewertung, Discovery
 * und Clipper in den Feed gespült haben, verschwindet — Warteschlangen-Jobs
 * und Feed-Einträge von Videos, deren Kanal nicht abonniert ist.
 *
 * Bewusst verschont: gespeicherte Videos (saved = 1), "Später ansehen",
 * heruntergeladene Videos und manuelle Importe — das hat der Nutzer selbst
 * behalten bzw. angelegt. videos, channels und watch_later bleiben unberührt.
 *
 * Die Abo-Definition steht hier absichtlich als eingefrorene Kopie (statt
 * Import aus feed/subscriptions.ts): eine Migration darf sich nicht still
 * mitändern, wenn die Laufzeit-Definition später angepasst wird.
 */
const SUBS_ONLY_MIGRATION_VERSION = 1;
function migrateToSubscriptionsOnly(db: Database.Database): void {
  const subscribed = `SELECT id FROM channels
     WHERE status = 'subscribed' AND is_active = 1 AND id <> 'manual'`;
  const foreignVideos = `SELECT id FROM videos
     WHERE channel_id NOT IN (${subscribed}) AND channel_id <> 'manual'`;
  db.transaction(() => {
    db.exec(`DELETE FROM ingest_queue WHERE channel_id NOT IN (${subscribed})`);
    db.exec(`DELETE FROM daily_mix_items WHERE video_id IN (${foreignVideos})`);
    db.exec(`
      DELETE FROM feed_items
       WHERE COALESCE(saved, 0) = 0
         AND video_id IN (${foreignVideos})
         AND video_id NOT IN (SELECT video_id FROM watch_later)
         AND video_id NOT IN (SELECT video_id FROM downloaded_videos)
    `);
  })();
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

  // Herkunft des Imports (Seiten- bzw. Quell-URL). Ohne sie war nach dem
  // Abschluss nicht mehr nachvollziehbar, woher ein Video stammt — weder für
  // den Nutzer noch für Re-Tests des Scrapers.
  addColumnIfMissing(db, "videos", "source_url", "TEXT");

  addColumnIfMissing(db, "feed_items", "progress_seconds", "REAL DEFAULT 0");
  addColumnIfMissing(db, "feed_items", "queued_at", "INTEGER");
  addColumnIfMissing(db, "feed_items", "queue_order", "INTEGER");

  addColumnIfMissing(db, "manga_chapters", "is_available", "INTEGER DEFAULT 1");

  // Channel banner art (16:7-ish wide image), extracted from yt-dlp.
  addColumnIfMissing(db, "channels", "banner_url", "TEXT");

  // RSS conditional-fetch validators — the ETag / Last-Modified from the last
  // successful poll, sent back as If-None-Match / If-Modified-Since so YouTube
  // can answer 304 Not Modified when nothing changed.
  addColumnIfMissing(db, "channels", "rss_etag", "TEXT");
  addColumnIfMissing(db, "channels", "rss_last_modified", "TEXT");

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

  // Laufende Importe: sichtbar, solange sie laden.
  //
  // Ein Import dauert bei einer Serienfolge Minuten. Bis er fertig ist, hatte
  // der Nutzer bisher keinerlei Anhaltspunkt, ob ueberhaupt etwas passiert.
  // Die Zeile hier NICHT in `videos` zu schreiben ist Absicht: Genau das war
  // der alte Fehler, der Folgen in der Bibliothek zeigte, die sich nicht
  // abspielen liessen. Erst wenn die Datei vollstaendig auf der Platte liegt,
  // wandert der Eintrag hinueber; bis dahin lebt er ausschliesslich hier.
  //
  // Die Metadatenfelder sind waehrend des Downloads editierbar — beim
  // Abschluss werden sie von hier uebernommen, nicht die urspruenglich
  // uebergebenen Werte.
  db.exec(`
    CREATE TABLE IF NOT EXISTS pending_imports (
      id TEXT PRIMARY KEY,
      page_url TEXT NOT NULL,
      media_url TEXT,
      title TEXT,
      series_id TEXT,
      series_title TEXT,
      season INTEGER,
      episode INTEGER,
      dub_language TEXT,
      sub_language TEXT,
      is_movie INTEGER DEFAULT 0,
      thumbnail_url TEXT,
      status TEXT NOT NULL DEFAULT 'queued',
      downloaded_bytes INTEGER DEFAULT 0,
      total_bytes INTEGER,
      speed_bps INTEGER,
      eta_seconds INTEGER,
      fragment_index INTEGER,
      fragment_count INTEGER,
      error TEXT,
      started_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL
    )
  `);
  db.exec(
    "CREATE INDEX IF NOT EXISTS idx_pending_imports_started ON pending_imports(started_at DESC)",
  );
  // Request-Header des mitgelesenen Streams, damit ein gescheiterter Import
  // per POST /imports/:id/retry ohne die App erneut geladen werden kann.
  addColumnIfMissing(db, "pending_imports", "referer", "TEXT");
  addColumnIfMissing(db, "pending_imports", "cookie", "TEXT");
  addColumnIfMissing(db, "pending_imports", "user_agent", "TEXT");
  addColumnIfMissing(db, "pending_imports", "attempts", "INTEGER DEFAULT 0");
  // Ein Neustart bricht jeden laufenden yt-dlp-Prozess ab. Zeilen, die noch
  // 'downloading' sagen, sind danach Leichen — als abgebrochen markieren,
  // statt einen Fortschritt vorzutaeuschen, der sich nie wieder bewegt.
  db.exec(
    "UPDATE pending_imports SET status = 'failed', error = 'Serverneustart wahrend des Downloads' " +
      "WHERE status IN ('queued', 'downloading')",
  );

  // Clipper, KI-Bewertung und Discovery sind entfernt — ihre Tabellen und
  // Spalten gleich mit, damit nichts davon je wieder anlaufen kann.
  // Idempotent (IF EXISTS); frische DBs bekommen sie über schema.sql gar nicht.
  db.exec(`
    DROP TABLE IF EXISTS clips;
    DROP TABLE IF EXISTS clipper_queue;
    DROP TABLE IF EXISTS clipper_runtime;
    DROP TABLE IF EXISTS filter_config;
    DROP TABLE IF EXISTS channel_filters;
    DROP TABLE IF EXISTS discovery_settings;
    DROP TABLE IF EXISTS channel_match_scores;
    DROP TABLE IF EXISTS category_preferences;
  `);
  dropColumnIfExists(db, "videos", "clip_status");
  dropColumnIfExists(db, "feed_items", "is_pre_clipper");
  dropColumnIfExists(db, "channels", "auto_approve");

  // Verlauf: ein Eintrag pro tatsächlich angeschautem Video (siehe
  // library/history.ts). Löst feed_items.seen_at ab, das nicht mehr gesetzt wird.
  db.exec(`
    CREATE TABLE IF NOT EXISTS watch_history (
      video_id TEXT PRIMARY KEY REFERENCES videos(id) ON DELETE CASCADE,
      watched_at INTEGER NOT NULL
    )
  `);
  db.exec("CREATE INDEX IF NOT EXISTS idx_watch_history_at ON watch_history(watched_at DESC)");

  // Versionierte Einmal-Migrationen (PRAGMA user_version). Läuft NACH dem
  // status-Backfill oben, damit aktive Bestandskanäle schon als Abo zählen.
  const version = db.pragma("user_version", { simple: true }) as number;
  if (version < SUBS_ONLY_MIGRATION_VERSION) {
    migrateToSubscriptionsOnly(db);
    db.pragma(`user_version = ${SUBS_ONLY_MIGRATION_VERSION}`);
  }
  if (version < HISTORY_MIGRATION_VERSION) {
    migrateHistoryAndSaved(db);
    db.pragma(`user_version = ${HISTORY_MIGRATION_VERSION}`);
  }
}

/**
 * Migration 2: Verlauf aus dem echten Wiedergabefortschritt vorbefüllen und
 * die abgeschaffte Speichern-Funktion aufräumen. Gespeicherte Videos hingen
 * sonst für immer in der Bibliothek, ohne dass die App sie noch entfernen kann.
 */
const HISTORY_MIGRATION_VERSION = 2;
function migrateHistoryAndSaved(db: Database.Database): void {
  db.transaction(() => {
    db.exec(`
      INSERT OR IGNORE INTO watch_history (video_id, watched_at)
      SELECT vp.video_id, vp.updated_at
        FROM video_progress vp
        JOIN videos v ON v.id = vp.video_id
       WHERE vp.progress_seconds >= 10
    `);
    db.exec("UPDATE feed_items SET saved = 0 WHERE saved = 1");
  })();
}
