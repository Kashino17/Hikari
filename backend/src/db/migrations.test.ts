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
      "downloaded_videos",
      "feed_items",
      "filter_config",
      "manga_arcs",
      "manga_chapter_read",
      "manga_chapters",
      "manga_library",
      "manga_pages",
      "manga_progress",
      "manga_series",
      "manga_sync_jobs",
      "scores",
      "series",
      "sponsor_segments",
      "videos",
    ]);
  });

  it("is idempotent — can run twice without error", () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    expect(() => applyMigrations(db)).not.toThrow();
  });
});
