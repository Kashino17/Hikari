import Database from "better-sqlite3";
import { beforeEach, describe, expect, it } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import {
  complete,
  dequeue,
  enqueue,
  fail,
  isWindowActive,
  setStep,
  unlockStale,
} from "./queue.js";

function makeDb(): Database.Database {
  const db = new Database(":memory:");
  applyMigrations(db);
  db.prepare("INSERT INTO channels (id, url, title, added_at) VALUES (?,?,?,?)")
    .run("ch1", "x", "ch1", 0);
  db.prepare(`
    INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at)
    VALUES (?, ?, ?, ?, ?, ?)
  `).run("v1", "ch1", "v1", 0, 600, 0);
  db.prepare(`
    INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at)
    VALUES (?, ?, ?, ?, ?, ?)
  `).run("v2", "ch1", "v2", 0, 300, 0);
  return db;
}

describe("clipper queue", () => {
  let db: Database.Database;
  beforeEach(() => { db = makeDb(); });

  it("enqueue adds a row with locked_at NULL", () => {
    enqueue(db, "v1");
    const row = db.prepare("SELECT * FROM clipper_queue WHERE video_id=?").get("v1") as any;
    expect(row).toBeTruthy();
    expect(row.locked_at).toBeNull();
    expect(row.attempts).toBe(0);
  });

  it("enqueue is idempotent — second call does not fail", () => {
    enqueue(db, "v1");
    expect(() => enqueue(db, "v1")).not.toThrow();
    const count = db.prepare("SELECT COUNT(*) as c FROM clipper_queue").get() as any;
    expect(count.c).toBe(1);
  });

  it("dequeue picks shortest video first (priority by duration)", () => {
    enqueue(db, "v1");
    enqueue(db, "v2");
    const job = dequeue(db);
    expect(job?.videoId).toBe("v2");
  });

  it("dequeue locks the picked job (locked_at + locked_step set)", () => {
    enqueue(db, "v1");
    const job = dequeue(db);
    expect(job).toBeTruthy();
    const row = db.prepare("SELECT * FROM clipper_queue WHERE video_id=?")
      .get("v1") as any;
    expect(row.locked_at).not.toBeNull();
    expect(row.locked_step).toBe("analyzing");
  });

  it("dequeue returns null when no unlocked jobs available", () => {
    enqueue(db, "v1");
    dequeue(db);
    expect(dequeue(db)).toBeNull();
  });

  it("setStep updates the locked_step column", () => {
    enqueue(db, "v1");
    dequeue(db);
    setStep(db, "v1", "rendering");
    const row = db.prepare("SELECT locked_step FROM clipper_queue WHERE video_id=?")
      .get("v1") as any;
    expect(row.locked_step).toBe("rendering");
  });

  it("complete removes the job from the queue", () => {
    enqueue(db, "v1");
    dequeue(db);
    complete(db, "v1");
    const row = db.prepare("SELECT * FROM clipper_queue WHERE video_id=?").get("v1");
    expect(row).toBeUndefined();
  });

  it("fail unlocks + increments attempts + records error", () => {
    enqueue(db, "v1");
    dequeue(db);
    fail(db, "v1", "qwen returned garbage");
    const row = db.prepare("SELECT * FROM clipper_queue WHERE video_id=?")
      .get("v1") as any;
    expect(row.locked_at).toBeNull();
    expect(row.attempts).toBe(1);
    expect(row.last_error).toBe("qwen returned garbage");
  });

  it("unlockStale unlocks jobs with locked_at older than threshold", () => {
    enqueue(db, "v1");
    dequeue(db);
    db.prepare("UPDATE clipper_queue SET locked_at=? WHERE video_id=?")
      .run(Date.now() - 31 * 60 * 1000, "v1");
    const unlocked = unlockStale(db, 30 * 60 * 1000);
    expect(unlocked).toBe(1);
    const row = db.prepare("SELECT * FROM clipper_queue WHERE video_id=?")
      .get("v1") as any;
    expect(row.locked_at).toBeNull();
    expect(row.attempts).toBe(1);
  });
});

describe("isWindowActive", () => {
  it("returns true at 22:00 sharp", () => {
    expect(isWindowActive(new Date("2026-05-02T22:00:00"), 22, 8)).toBe(true);
  });
  it("returns true at 02:00 (within window)", () => {
    expect(isWindowActive(new Date("2026-05-02T02:00:00"), 22, 8)).toBe(true);
  });
  it("returns true at 07:59 (window ends at 08:00 exclusive)", () => {
    expect(isWindowActive(new Date("2026-05-02T07:59:00"), 22, 8)).toBe(true);
  });
  it("returns false at 08:00 sharp", () => {
    expect(isWindowActive(new Date("2026-05-02T08:00:00"), 22, 8)).toBe(false);
  });
  it("returns false at 14:00 (outside window)", () => {
    expect(isWindowActive(new Date("2026-05-02T14:00:00"), 22, 8)).toBe(false);
  });
  it("returns false at 21:59", () => {
    expect(isWindowActive(new Date("2026-05-02T21:59:00"), 22, 8)).toBe(false);
  });
});
