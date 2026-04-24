import { writeFileSync, existsSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import Database from "better-sqlite3";
import { beforeEach, describe, expect, it } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import { runCleanup } from "./cleanup.js";

function seedVideo(db: Database.Database, id: string, size: number, lastServed: number, saved: 0 | 1, filePath: string) {
  db.prepare(
    `INSERT OR IGNORE INTO channels (id, url, title, added_at) VALUES ('UC1','x','x',0)`,
  ).run();
  db.prepare(
    `INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at)
     VALUES (?, 'UC1', ?, 0, 0, 0)`,
  ).run(id, `title-${id}`);
  db.prepare(
    `INSERT INTO feed_items (video_id, added_to_feed_at, saved) VALUES (?, 0, ?)`,
  ).run(id, saved);
  db.prepare(
    `INSERT INTO downloaded_videos (video_id, file_path, file_size_bytes, downloaded_at, last_served_at)
     VALUES (?, ?, ?, 0, ?)`,
  ).run(id, filePath, size, lastServed);
}

describe("runCleanup", () => {
  let db: Database.Database;
  let videoDir: string;

  beforeEach(() => {
    db = new Database(":memory:");
    applyMigrations(db);
    videoDir = mkdtempSync(join(tmpdir(), "hikari-cleanup-"));
  });

  it("deletes oldest non-saved files when over limit", () => {
    for (const { id, lastServed, saved } of [
      { id: "old-unsaved", lastServed: 1000, saved: 0 as const },
      { id: "mid-saved", lastServed: 2000, saved: 1 as const },
      { id: "new-unsaved", lastServed: 3000, saved: 0 as const },
    ]) {
      const p = join(videoDir, `${id}.mp4`);
      writeFileSync(p, Buffer.alloc(4 * 1024 * 1024, 0));
      seedVideo(db, id, 4 * 1024 * 1024, lastServed, saved, p);
    }

    const result = runCleanup({ db, limitBytes: 10 * 1024 * 1024 });

    expect(result.deletedCount).toBe(1);
    expect(result.deletedVideoIds).toEqual(["old-unsaved"]);
    expect(existsSync(join(videoDir, "old-unsaved.mp4"))).toBe(false);
    expect(existsSync(join(videoDir, "mid-saved.mp4"))).toBe(true);
    expect(db.prepare("SELECT COUNT(*) as c FROM downloaded_videos").get()).toEqual({ c: 2 });
  });

  it("never deletes saved videos, even if over limit", () => {
    for (const { id, lastServed } of [
      { id: "saved-1", lastServed: 1000 },
      { id: "saved-2", lastServed: 2000 },
    ]) {
      const p = join(videoDir, `${id}.mp4`);
      writeFileSync(p, Buffer.alloc(8 * 1024 * 1024, 0));
      seedVideo(db, id, 8 * 1024 * 1024, lastServed, 1, p);
    }

    const result = runCleanup({ db, limitBytes: 10 * 1024 * 1024 });
    expect(result.deletedCount).toBe(0);
    expect(existsSync(join(videoDir, "saved-1.mp4"))).toBe(true);
  });
});
