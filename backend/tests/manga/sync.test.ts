import { test, expect, beforeEach, afterEach, vi } from "vitest";
import Database from "better-sqlite3";
import { mkdtempSync, rmSync, existsSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { applyMigrations } from "../../src/db/migrations.js";
import { upsertSeries, upsertChapter } from "../../src/manga/persist.js";
import { runChapterSync } from "../../src/manga/sync.js";
import type { MangaSourceAdapter } from "../../src/manga/sources/types.js";

let db: Database.Database;
let baseDir: string;

beforeEach(() => {
  db = new Database(":memory:");
  db.pragma("foreign_keys = ON");
  applyMigrations(db);
  baseDir = mkdtempSync(join(tmpdir(), "manga-sync-"));
  vi.restoreAllMocks();
});

afterEach(() => {
  rmSync(baseDir, { recursive: true, force: true });
});

function fakeAdapter(): MangaSourceAdapter {
  return {
    id: "fake",
    name: "Fake",
    baseUrl: "https://fake.test",
    listSeries: async () => [],
    fetchSeriesDetail: async () => ({ chapters: [], arcs: [] }),
    fetchChapterPages: async () => [
      { pageNumber: 1, sourceUrl: "https://fake.test/p1.png" },
      { pageNumber: 2, sourceUrl: "https://fake.test/p2.png" },
    ],
  };
}

test("runChapterSync downloads pages and stores local_path with correct extension", async () => {
  upsertSeries(db, {
    source: "fake", sourceSlug: "x", title: "X",
    sourceUrl: "https://fake.test/manga/x",
  });
  upsertChapter(db, {
    source: "fake", seriesSlug: "x", number: 1,
    sourceUrl: "https://fake.test/manga/x/1",
  });

  const bytes = Buffer.from([0x89, 0x50, 0x4e, 0x47]); // PNG header start
  vi.stubGlobal("fetch", vi.fn(async () => ({
    ok: true,
    arrayBuffer: async () => bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength),
  })));

  await runChapterSync({
    db,
    adapter: fakeAdapter(),
    seriesSlug: "x",
    chapterNumber: 1,
    chapterUrl: "https://fake.test/manga/x/1",
    mangaDir: baseDir,
  });

  const pages = db
    .prepare("SELECT page_number, local_path, bytes FROM manga_pages ORDER BY page_number")
    .all() as { page_number: number; local_path: string | null; bytes: number | null }[];

  expect(pages).toHaveLength(2);
  // Extension must come from URL, not hardcoded jpg
  expect(pages[0].local_path).toBe("fake/x/1/01.png");
  expect(pages[1].local_path).toBe("fake/x/1/02.png");
  expect(existsSync(join(baseDir, pages[0].local_path!))).toBe(true);
  expect(existsSync(join(baseDir, pages[1].local_path!))).toBe(true);
  expect(pages[0].bytes).toBe(bytes.length);
});

test("runChapterSync updates manga_chapters.page_count", async () => {
  upsertSeries(db, {
    source: "fake", sourceSlug: "x", title: "X",
    sourceUrl: "https://fake.test/manga/x",
  });
  upsertChapter(db, {
    source: "fake", seriesSlug: "x", number: 1,
    sourceUrl: "https://fake.test/manga/x/1",
  });

  vi.stubGlobal("fetch", vi.fn(async () => ({
    ok: true,
    arrayBuffer: async () => new ArrayBuffer(4),
  })));

  await runChapterSync({
    db,
    adapter: fakeAdapter(),
    seriesSlug: "x",
    chapterNumber: 1,
    chapterUrl: "https://fake.test/manga/x/1",
    mangaDir: baseDir,
  });

  const ch = db.prepare("SELECT page_count FROM manga_chapters WHERE id = 'fake:x:1'").get() as { page_count: number };
  expect(ch.page_count).toBe(2);
});

test("runChapterSync is idempotent — second run does not duplicate rows", async () => {
  upsertSeries(db, {
    source: "fake", sourceSlug: "x", title: "X",
    sourceUrl: "https://fake.test/manga/x",
  });
  upsertChapter(db, {
    source: "fake", seriesSlug: "x", number: 1,
    sourceUrl: "https://fake.test/manga/x/1",
  });

  vi.stubGlobal("fetch", vi.fn(async () => ({
    ok: true,
    arrayBuffer: async () => new ArrayBuffer(1),
  })));

  await runChapterSync({
    db, adapter: fakeAdapter(),
    seriesSlug: "x", chapterNumber: 1,
    chapterUrl: "https://fake.test/manga/x/1",
    mangaDir: baseDir,
  });
  await runChapterSync({
    db, adapter: fakeAdapter(),
    seriesSlug: "x", chapterNumber: 1,
    chapterUrl: "https://fake.test/manga/x/1",
    mangaDir: baseDir,
  });

  const c = db.prepare("SELECT COUNT(*) as c FROM manga_pages").get() as { c: number };
  expect(c.c).toBe(2); // 2 pages, not 4
});

test("runChapterSync survives a single page download failure — that page's local_path stays NULL", async () => {
  upsertSeries(db, {
    source: "fake", sourceSlug: "x", title: "X",
    sourceUrl: "https://fake.test/manga/x",
  });
  upsertChapter(db, {
    source: "fake", seriesSlug: "x", number: 1,
    sourceUrl: "https://fake.test/manga/x/1",
  });

  let calls = 0;
  vi.stubGlobal("fetch", vi.fn(async (input: string) => {
    calls++;
    if (input.includes("p2")) {
      return { ok: false, status: 500 };
    }
    return { ok: true, arrayBuffer: async () => new ArrayBuffer(2) };
  }));

  await runChapterSync({
    db, adapter: fakeAdapter(),
    seriesSlug: "x", chapterNumber: 1,
    chapterUrl: "https://fake.test/manga/x/1",
    mangaDir: baseDir,
  });

  const pages = db
    .prepare("SELECT page_number, local_path FROM manga_pages ORDER BY page_number")
    .all() as { page_number: number; local_path: string | null }[];

  expect(pages).toHaveLength(2);
  expect(pages[0].local_path).toBeTruthy();   // p1 succeeded
  expect(pages[1].local_path).toBeNull();     // p2 failed → NULL
});
