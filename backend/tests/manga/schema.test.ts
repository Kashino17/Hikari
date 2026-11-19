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
    "manga_catalog",
    "manga_catalog_meta",
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
    "idx_manga_catalog_pop",
    "idx_manga_chapters_series_num",
    "idx_manga_pages_chapter",
  ]);
});

test("Migration: alte Kapitel bekommen lang 'de' und eine Spalte für die Gruppe", () => {
  const db = new Database(":memory:");
  // Stand vor MangaDex: manga_chapters ohne lang/scanlation_group.
  db.exec(`
    CREATE TABLE manga_series (id TEXT PRIMARY KEY, source TEXT NOT NULL, source_url TEXT NOT NULL,
      title TEXT NOT NULL, author TEXT, description TEXT, cover_path TEXT, status TEXT,
      total_chapters INTEGER DEFAULT 0, added_at INTEGER NOT NULL, last_synced_at INTEGER);
    CREATE TABLE manga_chapters (id TEXT PRIMARY KEY, series_id TEXT NOT NULL, arc_id TEXT,
      number REAL NOT NULL, title TEXT, source_url TEXT NOT NULL, page_count INTEGER DEFAULT 0,
      published_at INTEGER, added_at INTEGER NOT NULL);
    INSERT INTO manga_series (id, source, source_url, title, added_at)
      VALUES ('onepiecetube:one-piece', 'onepiecetube', 'u', 'One Piece', 1);
    INSERT INTO manga_chapters (id, series_id, number, source_url, added_at)
      VALUES ('onepiecetube:one-piece:1095', 'onepiecetube:one-piece', 1095, 'u', 1);
  `);
  applyMigrations(db);
  const row = db
    .prepare("SELECT lang, scanlation_group AS g FROM manga_chapters WHERE id = ?")
    .get("onepiecetube:one-piece:1095");
  expect(row).toEqual({ lang: "de", g: null });
  // Zweiter Start ändert nichts und wirft nicht.
  applyMigrations(db);
  // MangaDex-Kapitel ohne Sprache werden nicht zu 'de' umgedeutet.
  db.prepare(
    "INSERT INTO manga_chapters (id, series_id, number, source_url, added_at) VALUES (?, ?, 1, 'x', 1)",
  ).run("mangadex:u:en:1", "onepiecetube:one-piece");
  applyMigrations(db);
  const md = db.prepare("SELECT lang FROM manga_chapters WHERE id = ?").get("mangadex:u:en:1");
  expect(md).toEqual({ lang: null });
});
