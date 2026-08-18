import type Database from "better-sqlite3";

export interface IngestQueueRow {
  video_id: string;
  channel_id: string;
  queued_at: number;
  attempts: number;
  last_error: string | null;
  locked_at: number | null;
}

// A lock older than this is re-claimable (worker crashed mid-ingest).
export const STALE_LOCK_MS = 15 * 60 * 1000;
// After this many failed attempts a video is dropped from the queue — it's
// almost certainly a permanent failure (private, removed, geo-blocked).
export const MAX_INGEST_ATTEMPTS = 4;

/**
 * Enqueue a newly-seen video for ingestion. Idempotent: INSERT OR IGNORE means
 * re-enqueueing an already-queued video keeps its attempts/lock state. Skips
 * videos already in the library so a re-poll doesn't re-ingest known content.
 */
export function enqueueIngest(
  db: Database.Database,
  videoId: string,
  channelId: string,
): void {
  const known = db.prepare("SELECT 1 FROM videos WHERE id = ?").get(videoId);
  if (known) return;
  db.prepare(
    "INSERT OR IGNORE INTO ingest_queue (video_id, channel_id, queued_at) VALUES (?, ?, ?)",
  ).run(videoId, channelId, Date.now());
}

/**
 * Atomically claim the next available job (unlocked or stale-locked) that
 * hasn't exhausted its retries. A transaction prevents two workers from
 * claiming the same row.
 */
export function claimNextIngest(db: Database.Database): IngestQueueRow | null {
  const claim = db.transaction((): IngestQueueRow | null => {
    const now = Date.now();
    const row = db
      .prepare(
        `SELECT * FROM ingest_queue
         WHERE (locked_at IS NULL OR locked_at < ?)
           AND attempts < ?
         ORDER BY queued_at ASC
         LIMIT 1`,
      )
      .get(now - STALE_LOCK_MS, MAX_INGEST_ATTEMPTS) as IngestQueueRow | undefined;
    if (!row) return null;
    db.prepare("UPDATE ingest_queue SET locked_at = ? WHERE video_id = ?").run(now, row.video_id);
    return { ...row, locked_at: now };
  });
  return claim();
}

/** Mark a job done — remove it from the queue. */
export function completeIngest(db: Database.Database, videoId: string): void {
  db.prepare("DELETE FROM ingest_queue WHERE video_id = ?").run(videoId);
}

/**
 * Record a failure: increment attempts, clear the lock, store the error. The
 * job stays for retry until attempts reaches MAX_INGEST_ATTEMPTS, after which
 * claimNextIngest stops returning it (effectively a dead-letter).
 */
export function failIngest(db: Database.Database, videoId: string, error: string): void {
  db.prepare(
    `UPDATE ingest_queue
     SET attempts = attempts + 1, last_error = ?, locked_at = NULL
     WHERE video_id = ?`,
  ).run(error.slice(0, 1000), videoId);
}

/**
 * Lock freigeben OHNE attempts zu erhöhen — für transiente Infrastruktur-
 * Ausfälle (Scorer-LLM aus, Netz weg): der Job soll später einfach wieder
 * drankommen, statt nach MAX_INGEST_ATTEMPTS Minuten als Dead-Letter zu enden.
 */
export function requeueIngest(db: Database.Database, videoId: string, error: string): void {
  db.prepare(
    "UPDATE ingest_queue SET locked_at = NULL, last_error = ? WHERE video_id = ?",
  ).run(error.slice(0, 1000), videoId);
}

/**
 * Verbindungs-/Infrastrukturfehler (LM Studio aus, DNS, Timeout) — im
 * Gegensatz zu Inhaltsfehlern (Age-Gate, Parse-Fehler), die ein Retry nie
 * heilt und die deshalb weiterhin attempts verbrauchen.
 */
export function isTransientInfraError(err: unknown): boolean {
  const msg = err instanceof Error ? err.message : String(err);
  return /fetch failed|ECONNREFUSED|ECONNRESET|ETIMEDOUT|EAI_AGAIN|socket hang up/i.test(msg);
}

/** Reset stale locks on startup so a crash mid-ingest doesn't strand rows. */
export function unlockStaleIngest(db: Database.Database): number {
  return db
    .prepare(
      "UPDATE ingest_queue SET locked_at = NULL WHERE locked_at IS NOT NULL AND locked_at < ?",
    )
    .run(Date.now() - STALE_LOCK_MS).changes;
}

/** Pending (claimable, not exhausted) job count — for observability. */
export function pendingIngestCount(db: Database.Database): number {
  const row = db
    .prepare("SELECT COUNT(*) AS c FROM ingest_queue WHERE attempts < ?")
    .get(MAX_INGEST_ATTEMPTS) as { c: number };
  return row.c;
}

/** Dead-lettered (retry-exhausted) job count — for observability. */
export function deadIngestCount(db: Database.Database): number {
  const row = db
    .prepare("SELECT COUNT(*) AS c FROM ingest_queue WHERE attempts >= ?")
    .get(MAX_INGEST_ATTEMPTS) as { c: number };
  return row.c;
}
