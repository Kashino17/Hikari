import { describe, it, expect, beforeEach } from "vitest";
import Database from "better-sqlite3";
import { applyMigrations } from "../db/migrations.js";
import {
  enqueueIngest,
  claimNextIngest,
  completeIngest,
  failIngest,
  unlockStaleIngest,
  pendingIngestCount,
  deadIngestCount,
  MAX_INGEST_ATTEMPTS,
  STALE_LOCK_MS,
} from "./queue.js";

function makeDb(): Database.Database {
  const db = new Database(":memory:");
  applyMigrations(db);
  db.prepare("INSERT INTO channels (id, url, title, added_at) VALUES ('ch1','x','C',0)").run();
  return db;
}

function seedVideo(db: Database.Database, id: string): void {
  db.prepare(
    `INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at)
     VALUES (?, 'ch1', 't', 0, 60, 0)`,
  ).run(id);
}

describe("ingest queue", () => {
  let db: Database.Database;
  beforeEach(() => {
    db = makeDb();
  });

  it("enqueues a new video and claims it FIFO", () => {
    enqueueIngest(db, "v1", "ch1");
    enqueueIngest(db, "v2", "ch1");
    const first = claimNextIngest(db);
    expect(first?.video_id).toBe("v1");
    expect(first?.channel_id).toBe("ch1");
    expect(first?.locked_at).toBeTypeOf("number");
  });

  it("is idempotent — re-enqueue does not duplicate or reset", () => {
    enqueueIngest(db, "v1", "ch1");
    const a = claimNextIngest(db); // locks v1
    expect(a?.video_id).toBe("v1");
    enqueueIngest(db, "v1", "ch1"); // no-op, must not clear the lock
    expect(claimNextIngest(db)).toBeNull(); // still locked, nothing else queued
  });

  it("does not enqueue a video already in the library", () => {
    seedVideo(db, "known");
    enqueueIngest(db, "known", "ch1");
    expect(pendingIngestCount(db)).toBe(0);
  });

  it("a locked job is not re-claimed until the lock goes stale", () => {
    enqueueIngest(db, "v1", "ch1");
    claimNextIngest(db);
    expect(claimNextIngest(db)).toBeNull();
  });

  it("reclaims a stale lock", () => {
    enqueueIngest(db, "v1", "ch1");
    claimNextIngest(db);
    // Force the lock into the past.
    db.prepare("UPDATE ingest_queue SET locked_at = ? WHERE video_id = 'v1'").run(
      Date.now() - STALE_LOCK_MS - 1000,
    );
    expect(claimNextIngest(db)?.video_id).toBe("v1");
  });

  it("unlockStaleIngest resets only stale locks", () => {
    enqueueIngest(db, "v1", "ch1");
    enqueueIngest(db, "v2", "ch1");
    claimNextIngest(db); // locks v1 (fresh)
    db.prepare("UPDATE ingest_queue SET locked_at = ? WHERE video_id = 'v2'").run(
      Date.now() - STALE_LOCK_MS - 1,
    );
    // v2 was never claimed by us but simulate a stale lock on it directly.
    db.prepare("UPDATE ingest_queue SET locked_at = ? WHERE video_id = 'v2'").run(
      Date.now() - STALE_LOCK_MS - 1,
    );
    const reset = unlockStaleIngest(db);
    expect(reset).toBe(1); // only v2's stale lock
  });

  it("complete removes the job", () => {
    enqueueIngest(db, "v1", "ch1");
    claimNextIngest(db);
    completeIngest(db, "v1");
    expect(pendingIngestCount(db)).toBe(0);
    expect(claimNextIngest(db)).toBeNull();
  });

  it("fail increments attempts, clears the lock, and keeps the job for retry", () => {
    enqueueIngest(db, "v1", "ch1");
    claimNextIngest(db);
    failIngest(db, "v1", "network blip");
    const again = claimNextIngest(db);
    expect(again?.video_id).toBe("v1");
    expect(again?.attempts).toBe(1);
    expect(again?.last_error).toBe("network blip");
  });

  it("drops a job after MAX_INGEST_ATTEMPTS (dead-letter)", () => {
    enqueueIngest(db, "v1", "ch1");
    for (let i = 0; i < MAX_INGEST_ATTEMPTS; i++) {
      claimNextIngest(db);
      failIngest(db, "v1", "perma-fail");
    }
    expect(claimNextIngest(db)).toBeNull(); // exhausted → not claimable
    expect(pendingIngestCount(db)).toBe(0);
    expect(deadIngestCount(db)).toBe(1);
  });

  it("truncates an overlong error message", () => {
    enqueueIngest(db, "v1", "ch1");
    claimNextIngest(db);
    failIngest(db, "v1", "x".repeat(5000));
    const row = db
      .prepare("SELECT last_error FROM ingest_queue WHERE video_id='v1'")
      .get() as { last_error: string };
    expect(row.last_error.length).toBe(1000);
  });
});
