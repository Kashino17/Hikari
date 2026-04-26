import Database from "better-sqlite3";
import { beforeEach, expect, test } from "vitest";
import { applyMigrations } from "../../src/db/migrations.js";
import {
  chapterId,
  pageId,
  seriesId,
  upsertChapter,
  upsertPage,
  upsertSeries,
} from "../../src/manga/persist.js";

let db: Database.Database;

beforeEach(() => {
  db = new Database(":memory:");
  db.pragma("foreign_keys = ON");
  applyMigrations(db);
});

test("id helpers produce stable namespaced ids", () => {
  expect(seriesId("onepiecetube", "one-piece")).toBe("onepiecetube:one-piece");
  expect(chapterId("onepiecetube", "one-piece", 1095)).toBe("onepiecetube:one-piece:1095");
  expect(chapterId("onepiecetube", "one-piece", 1095.5)).toBe("onepiecetube:one-piece:1095.5");
  expect(pageId("onepiecetube", "one-piece", 1095, 1)).toBe("onepiecetube:one-piece:1095:01");
  expect(pageId("onepiecetube", "one-piece", 1095, 12)).toBe("onepiecetube:one-piece:1095:12");
});

test("upsertSeries inserts on first call, updates on second", () => {
  const id = upsertSeries(db, {
    source: "onepiecetube",
    sourceSlug: "one-piece",
    title: "One Piece",
    sourceUrl: "https://onepiece.tube/manga/one-piece",
  });
  expect(id).toBe("onepiecetube:one-piece");
  const row1 = db.prepare("SELECT title FROM manga_series WHERE id = ?").get(id) as {
    title: string;
  };
  expect(row1.title).toBe("One Piece");

  upsertSeries(db, {
    source: "onepiecetube",
    sourceSlug: "one-piece",
    title: "One Piece (updated)",
    sourceUrl: "https://onepiece.tube/manga/one-piece",
  });
  const row2 = db.prepare("SELECT title FROM manga_series WHERE id = ?").get(id) as {
    title: string;
  };
  expect(row2.title).toBe("One Piece (updated)");

  const count = db.prepare("SELECT COUNT(*) as c FROM manga_series").get() as { c: number };
  expect(count.c).toBe(1);
});

test("upsertChapter is idempotent", () => {
  upsertSeries(db, {
    source: "onepiecetube",
    sourceSlug: "one-piece",
    title: "One Piece",
    sourceUrl: "https://onepiece.tube/manga/one-piece",
  });
  const id = upsertChapter(db, {
    source: "onepiecetube",
    seriesSlug: "one-piece",
    number: 1095,
    title: "Title here",
    sourceUrl: "https://onepiece.tube/manga/one-piece/kapitel-1095",
  });
  expect(id).toBe("onepiecetube:one-piece:1095");
  upsertChapter(db, {
    source: "onepiecetube",
    seriesSlug: "one-piece",
    number: 1095,
    sourceUrl: "https://onepiece.tube/manga/one-piece/kapitel-1095",
  });
  const c = db.prepare("SELECT COUNT(*) as c FROM manga_chapters").get() as { c: number };
  expect(c.c).toBe(1);
});

test("upsertChapter stores page count and availability metadata", () => {
  upsertSeries(db, {
    source: "onepiecetube",
    sourceSlug: "one-piece",
    title: "One Piece",
    sourceUrl: "https://onepiece.tube/manga/one-piece",
  });
  const id = upsertChapter(db, {
    source: "onepiecetube",
    seriesSlug: "one-piece",
    number: 1,
    title: "Das Abenteuer beginnt",
    sourceUrl: "https://onepiece.tube/manga/kapitel/1/1",
    pageCount: 0,
    isAvailable: false,
  });
  const row = db
    .prepare("SELECT page_count, is_available FROM manga_chapters WHERE id = ?")
    .get(id) as { page_count: number; is_available: number };
  expect(row).toEqual({ page_count: 0, is_available: 0 });

  upsertChapter(db, {
    source: "onepiecetube",
    seriesSlug: "one-piece",
    number: 1,
    sourceUrl: "https://onepiece.tube/manga/kapitel/1/1",
    pageCount: 15,
    isAvailable: true,
  });
  const updated = db
    .prepare("SELECT page_count, is_available FROM manga_chapters WHERE id = ?")
    .get(id) as { page_count: number; is_available: number };
  expect(updated).toEqual({ page_count: 15, is_available: 1 });
});

test("upsertPage stores local_path and bytes", () => {
  upsertSeries(db, {
    source: "onepiecetube",
    sourceSlug: "one-piece",
    title: "One Piece",
    sourceUrl: "https://onepiece.tube/manga/one-piece",
  });
  upsertChapter(db, {
    source: "onepiecetube",
    seriesSlug: "one-piece",
    number: 1095,
    sourceUrl: "https://onepiece.tube/manga/one-piece/kapitel-1095",
  });
  const id = upsertPage(db, {
    source: "onepiecetube",
    seriesSlug: "one-piece",
    chapterNumber: 1095,
    pageNumber: 1,
    sourceUrl: "https://x/p.jpg",
    localPath: "onepiecetube/one-piece/1095/01.jpg",
    bytes: 12345,
  });
  expect(id).toBe("onepiecetube:one-piece:1095:01");
  const row = db.prepare("SELECT local_path, bytes FROM manga_pages WHERE id = ?").get(id) as {
    local_path: string;
    bytes: number;
  };
  expect(row.local_path).toBe("onepiecetube/one-piece/1095/01.jpg");
  expect(row.bytes).toBe(12345);
});

test("upsertPage preserves existing local_path when called again without one", () => {
  upsertSeries(db, {
    source: "onepiecetube",
    sourceSlug: "one-piece",
    title: "One Piece",
    sourceUrl: "https://x",
  });
  upsertChapter(db, {
    source: "onepiecetube",
    seriesSlug: "one-piece",
    number: 1095,
    sourceUrl: "https://x",
  });
  upsertPage(db, {
    source: "onepiecetube",
    seriesSlug: "one-piece",
    chapterNumber: 1095,
    pageNumber: 1,
    sourceUrl: "https://x/p.png",
    localPath: "x/01.png",
    bytes: 100,
  });
  // Second call without localPath should NOT clobber the existing one
  upsertPage(db, {
    source: "onepiecetube",
    seriesSlug: "one-piece",
    chapterNumber: 1095,
    pageNumber: 1,
    sourceUrl: "https://x/p.png",
  });
  const row = db
    .prepare(
      "SELECT local_path, bytes FROM manga_pages WHERE id = 'onepiecetube:one-piece:1095:01'",
    )
    .get() as { local_path: string; bytes: number };
  expect(row.local_path).toBe("x/01.png");
  expect(row.bytes).toBe(100);
});
