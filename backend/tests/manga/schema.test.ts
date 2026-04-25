import { test, expect } from "vitest";
import Database from "better-sqlite3";
import { applyMigrations } from "../../src/db/migrations.js";

test("manga tables exist after migration", () => {
  const db = new Database(":memory:");
  applyMigrations(db);
  const tables = db
    .prepare("SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'manga_%'")
    .all() as { name: string }[];
  const names = tables.map((t) => t.name).sort();
  expect(names).toEqual([
    "manga_arcs",
    "manga_chapter_read",
    "manga_chapters",
    "manga_library",
    "manga_pages",
    "manga_progress",
    "manga_series",
    "manga_sync_jobs",
  ]);
});

test("manga_pages indexes exist", () => {
  const db = new Database(":memory:");
  applyMigrations(db);
  const idx = db
    .prepare("SELECT name FROM sqlite_master WHERE type='index' AND name LIKE 'idx_manga_%'")
    .all() as { name: string }[];
  expect(idx.map((i) => i.name).sort()).toEqual([
    "idx_manga_chapters_series_num",
    "idx_manga_pages_chapter",
  ]);
});
