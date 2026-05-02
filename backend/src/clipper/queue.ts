import type Database from "better-sqlite3";

export interface QueueJob {
  videoId: string;
  queuedAt: number;
  attempts: number;
}

/** Idempotent — re-queueing a video that's already in the queue is a no-op. */
export function enqueue(db: Database.Database, videoId: string): void {
  db.prepare(`
    INSERT OR IGNORE INTO clipper_queue (video_id, queued_at, attempts)
    VALUES (?, ?, 0)
  `).run(videoId, Date.now());
}

/**
 * Atomic dequeue + lock. Picks the unlocked job with the shortest video
 * duration first, ties broken by oldest queued_at. Returns null if no job
 * available.
 */
export function dequeue(db: Database.Database): QueueJob | null {
  return db.transaction((): QueueJob | null => {
    const row = db.prepare(`
      SELECT q.video_id AS videoId, q.queued_at AS queuedAt, q.attempts AS attempts
        FROM clipper_queue q
        JOIN videos v ON v.id = q.video_id
       WHERE q.locked_at IS NULL
       ORDER BY v.duration_seconds ASC, q.queued_at ASC
       LIMIT 1
    `).get() as QueueJob | undefined;
    if (!row) return null;

    db.prepare(`
      UPDATE clipper_queue
         SET locked_at = ?, locked_step = 'analyzing'
       WHERE video_id = ?
    `).run(Date.now(), row.videoId);

    return row;
  })();
}

export function setStep(
  db: Database.Database,
  videoId: string,
  step: "analyzing" | "rendering",
): void {
  db.prepare("UPDATE clipper_queue SET locked_step = ? WHERE video_id = ?")
    .run(step, videoId);
}

export function complete(db: Database.Database, videoId: string): void {
  db.prepare("DELETE FROM clipper_queue WHERE video_id = ?").run(videoId);
}

export function fail(
  db: Database.Database,
  videoId: string,
  error: string,
): void {
  db.prepare(`
    UPDATE clipper_queue
       SET locked_at  = NULL,
           locked_step = NULL,
           attempts   = attempts + 1,
           last_error = ?
     WHERE video_id = ?
  `).run(error, videoId);
}

/**
 * Unlock jobs whose lock has gone stale (worker crashed mid-job).
 * Increments attempts so repeat failures eventually get skipped.
 * Returns the number of rows unlocked.
 */
export function unlockStale(
  db: Database.Database,
  olderThanMs: number,
): number {
  const cutoff = Date.now() - olderThanMs;
  const res = db.prepare(`
    UPDATE clipper_queue
       SET locked_at  = NULL,
           locked_step = NULL,
           attempts   = attempts + 1,
           last_error = COALESCE(last_error, 'auto-unlocked stale lock')
     WHERE locked_at IS NOT NULL
       AND locked_at < ?
  `).run(cutoff);
  return res.changes;
}

/**
 * True if `now` falls within the configured nightly clipping window.
 * Wraps midnight: startHour=22, endHour=8 means 22:00..23:59 OR 00:00..07:59.
 * When startHour < endHour (non-wrapping window), it's a simple range check.
 */
export function isWindowActive(
  now: Date,
  startHour: number,
  endHour: number,
): boolean {
  const h = now.getHours();
  if (startHour < endHour) {
    return h >= startHour && h < endHour;
  }
  // Wraps midnight
  return h >= startHour || h < endHour;
}
