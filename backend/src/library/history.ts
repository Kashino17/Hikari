import type Database from "better-sqlite3";

/**
 * Verlauf = was tatsächlich angeschaut wurde. Bisher hing er an
 * feed_items.seen_at, das nur der alte Reel-Player setzte — seit dessen
 * Abschaffung wurde der Verlauf nie mehr fortgeschrieben, und Serienfolgen
 * oder Filme (ohne feed_items-Zeile) tauchten darin nie auf.
 *
 * Jetzt schreibt jeder Fortschritts-Push einen Eintrag. Ab dieser Schwelle
 * zählt ein Video als angeschaut — kurzes Reinklicken landet nicht im Verlauf.
 */
export const HISTORY_MIN_SECONDS = 10;

export function recordWatch(db: Database.Database, videoId: string, seconds: number): void {
  if (seconds < HISTORY_MIN_SECONDS) return;
  const exists = db.prepare("SELECT 1 FROM videos WHERE id = ?").get(videoId);
  if (!exists) return;
  db.prepare(
    `INSERT INTO watch_history (video_id, watched_at) VALUES (?, ?)
     ON CONFLICT(video_id) DO UPDATE SET watched_at = excluded.watched_at`,
  ).run(videoId, Date.now());
}

export function removeFromHistory(db: Database.Database, videoId: string): boolean {
  return db.prepare("DELETE FROM watch_history WHERE video_id = ?").run(videoId).changes > 0;
}

export function clearHistory(db: Database.Database): number {
  return db.prepare("DELETE FROM watch_history").run().changes;
}

/**
 * Zeilen im Format von LibraryVideoDto — dieselben Felder wie
 * „Neu hinzugefügt", aber ohne Pflicht-Join auf downloaded_videos: gestreamte
 * YouTube-Videos haben keine Serverdatei und gehören trotzdem dazu.
 */
const LIBRARY_VIDEO_SELECT = `
  SELECT v.*, c.title AS channelTitle,
         COALESCE(vp.progress_seconds, fi.progress_seconds) AS progress_seconds,
         COALESCE(vp.updated_at, 0) AS progress_updated_at,
         s.overall_score AS overall_score,
         CASE WHEN dv.video_id IS NULL THEN 0 ELSE 1 END AS downloaded
    FROM videos v
    JOIN channels c ON c.id = v.channel_id
    LEFT JOIN feed_items fi ON fi.video_id = v.id
    LEFT JOIN video_progress vp ON vp.video_id = v.id
    LEFT JOIN scores s ON s.video_id = v.id
    LEFT JOIN downloaded_videos dv ON dv.video_id = v.id`;

/** Gestreamte Videos sind nur abspielbar, wenn sie entweder als Datei
 *  vorliegen oder von YouTube kommen (Live-Proxy). */
const PLAYABLE = "(dv.video_id IS NOT NULL OR v.channel_id != 'manual')";

export function listHistory(db: Database.Database, limit = 50): unknown[] {
  return db
    .prepare(
      `${LIBRARY_VIDEO_SELECT}
       JOIN watch_history wh ON wh.video_id = v.id
       WHERE ${PLAYABLE}
       ORDER BY wh.watched_at DESC
       LIMIT ?`,
    )
    .all(limit);
}

/**
 * Angefangen, aber nicht fertig (5 %–95 %), zuletzt geschaut zuerst — über
 * alle Quellen. Vorher kam „Weiterschauen" nur aus den 20 jüngsten Server-
 * Downloads, ein gestreamtes YouTube-Video konnte dort nie auftauchen.
 */
export function listContinueWatching(db: Database.Database, limit = 30): unknown[] {
  return db
    .prepare(
      `${LIBRARY_VIDEO_SELECT}
       WHERE vp.progress_seconds IS NOT NULL
         AND v.duration_seconds > 0
         AND vp.progress_seconds >= ${HISTORY_MIN_SECONDS}
         AND vp.progress_seconds < v.duration_seconds * 0.95
         AND ${PLAYABLE}
       ORDER BY vp.updated_at DESC
       LIMIT ?`,
    )
    .all(limit);
}

/**
 * Neueste Videos der abonnierten YouTube-Kanäle — gestreamt, ohne Download.
 * Reihum gemischt (1. Video jedes Kanals, dann das 2. …), sonst füllt ein
 * fleißiger Kanal die ganze Reihe und die anderen tauchen nie auf.
 */
export function listChannelUploads(
  db: Database.Database,
  subscribedCondition: string,
  limit = 40,
  perChannel = 12,
): unknown[] {
  return db
    .prepare(
      `WITH ranked AS (
         SELECT v.id,
                ROW_NUMBER() OVER (
                  PARTITION BY v.channel_id
                  ORDER BY COALESCE(v.published_at, v.discovered_at) DESC
                ) AS rn,
                COALESCE(v.published_at, v.discovered_at) AS ts
           FROM videos v
           LEFT JOIN scores s ON s.video_id = v.id
          WHERE v.channel_id != 'manual'
            AND ${subscribedCondition}
            AND COALESCE(v.format, 'long') != 'short'
            AND COALESCE(s.decision, 'approved') = 'approved'
       )
       ${LIBRARY_VIDEO_SELECT}
       JOIN ranked r ON r.id = v.id
       WHERE r.rn <= ?
       ORDER BY r.rn ASC, r.ts DESC
       LIMIT ?`,
    )
    .all(perChannel, limit);
}
