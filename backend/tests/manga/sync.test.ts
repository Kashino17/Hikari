import { test, expect, beforeEach, afterEach, vi } from "vitest";
import Database from "better-sqlite3";
import { mkdtempSync, rmSync, existsSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { applyMigrations } from "../../src/db/migrations.js";
import { upsertSeries, upsertChapter } from "../../src/manga/persist.js";
import { runChapterSync, runSeriesSync, runFullSync } from "../../src/manga/sync.js";
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

function fakeAdapterWithSeriesAndChapters(): MangaSourceAdapter {
  let pageFetches = 0;
  return {
    id: "fake",
    name: "Fake",
    baseUrl: "https://fake.test",
    listSeries: async () => [
      {
        sourceSlug: "x",
        title: "X",
        sourceUrl: "https://fake.test/manga/x",
      },
    ],
    fetchSeriesDetail: async () => ({
      chapters: [
        { number: 1, sourceUrl: "https://fake.test/manga/x/1" },
        { number: 2, sourceUrl: "https://fake.test/manga/x/2" },
      ],
      arcs: [{ title: "Arc One", arcOrder: 0, chapterNumbers: [1, 2] }],
    }),
    fetchChapterPages: async () => {
      pageFetches++;
      return [{ pageNumber: 1, sourceUrl: `https://fake.test/p${pageFetches}.png` }];
    },
  };
}

test("runSeriesSync writes series, chapters, arcs, and pages", async () => {
  vi.stubGlobal("fetch", vi.fn(async () => ({
    ok: true,
    arrayBuffer: async () => new ArrayBuffer(1),
  })));

  await runSeriesSync({
    db,
    adapter: fakeAdapterWithSeriesAndChapters(),
    seriesSlug: "x",
    seriesUrl: "https://fake.test/manga/x",
    seriesTitle: "X",
    mangaDir: baseDir,
  });

  const ch = db.prepare("SELECT number FROM manga_chapters ORDER BY number").all() as { number: number }[];
  expect(ch.map((c) => c.number)).toEqual([1, 2]);

  const arcs = db.prepare("SELECT title FROM manga_arcs").all() as { title: string }[];
  expect(arcs).toHaveLength(1);
  expect(arcs[0].title).toBe("Arc One");

  // chapters should have arc_id pointing at the arc
  const chWithArc = db.prepare("SELECT arc_id FROM manga_chapters WHERE number = 1").get() as { arc_id: string | null };
  expect(chWithArc.arc_id).toBeTruthy();

  const pages = db.prepare("SELECT COUNT(*) as c FROM manga_pages").get() as { c: number };
  expect(pages.c).toBe(2); // one page per chapter
});

test("runSeriesSync sets total_chapters and last_synced_at on the series", async () => {
  vi.stubGlobal("fetch", vi.fn(async () => ({
    ok: true,
    arrayBuffer: async () => new ArrayBuffer(1),
  })));
  await runSeriesSync({
    db,
    adapter: fakeAdapterWithSeriesAndChapters(),
    seriesSlug: "x",
    seriesUrl: "https://fake.test/manga/x",
    seriesTitle: "X",
    mangaDir: baseDir,
  });
  const s = db.prepare("SELECT total_chapters, last_synced_at FROM manga_series WHERE id = 'fake:x'").get() as { total_chapters: number; last_synced_at: number };
  expect(s.total_chapters).toBe(2);
  expect(typeof s.last_synced_at).toBe("number");
  expect(s.last_synced_at).toBeGreaterThan(0);
});

test("runFullSync creates a sync_jobs row and marks it done", async () => {
  vi.stubGlobal("fetch", vi.fn(async () => ({
    ok: true,
    arrayBuffer: async () => new ArrayBuffer(1),
  })));

  const adapter = fakeAdapterWithSeriesAndChapters();
  const job = await runFullSync({ db, adapter, mangaDir: baseDir });
  expect(job.status).toBe("done");
  expect(job.done_chapters).toBe(2);
  expect(job.done_pages).toBe(2);
  expect(job.finished_at).toBeTruthy();
});

test("runFullSync second run is idempotent", async () => {
  vi.stubGlobal("fetch", vi.fn(async () => ({
    ok: true,
    arrayBuffer: async () => new ArrayBuffer(1),
  })));

  const adapter = fakeAdapterWithSeriesAndChapters();
  await runFullSync({ db, adapter, mangaDir: baseDir });
  await runFullSync({ db, adapter, mangaDir: baseDir });

  const series = db.prepare("SELECT COUNT(*) as c FROM manga_series").get() as { c: number };
  const chapters = db.prepare("SELECT COUNT(*) as c FROM manga_chapters").get() as { c: number };
  const pages = db.prepare("SELECT COUNT(*) as c FROM manga_pages").get() as { c: number };
  expect(series.c).toBe(1);
  expect(chapters.c).toBe(2);
  expect(pages.c).toBe(2);
  // But two job rows
  const jobs = db.prepare("SELECT COUNT(*) as c FROM manga_sync_jobs").get() as { c: number };
  expect(jobs.c).toBe(2);
});

test("runFullSync marks job 'failed' with error_message when adapter throws", async () => {
  const broken: MangaSourceAdapter = {
    id: "broke", name: "Broke", baseUrl: "https://x",
    listSeries: async () => { throw new Error("boom"); },
    fetchSeriesDetail: async () => ({ chapters: [], arcs: [] }),
    fetchChapterPages: async () => [],
  };
  const job = await runFullSync({ db, adapter: broken, mangaDir: baseDir });
  expect(job.status).toBe("failed");
  expect(job.error_message).toMatch(/boom/);
});

// Fix #2 — error fidelity: SourceLayoutError fields preserved
test("runFullSync error_message preserves SourceLayoutError fields", async () => {
  const { SourceLayoutError } = await import("../../src/manga/sources/types.js");
  const broken: MangaSourceAdapter = {
    id: "broke", name: "Broke", baseUrl: "https://x",
    listSeries: async () => { throw new SourceLayoutError("bad layout", "https://example.com/list", "a.foo"); },
    fetchSeriesDetail: async () => ({ chapters: [], arcs: [] }),
    fetchChapterPages: async () => [],
  };
  const job = await runFullSync({ db, adapter: broken, mangaDir: baseDir });
  expect(job.status).toBe("failed");
  const parsed = JSON.parse(job.error_message ?? "");
  expect(parsed.kind).toBe("SourceLayoutError");
  expect(parsed.url).toBe("https://example.com/list");
  expect(parsed.selector).toBe("a.foo");
});

// Fix #3 — partial failures recorded in error_message even when status=done
test("runFullSync records partial failures in error_message even when status=done", async () => {
  const adapter = fakeAdapterWithSeriesAndChapters();
  // fakeAdapterWithSeriesAndChapters generates URLs p1.png and p2.png for the two chapters.
  // Only p1.png matches "p1", so exactly 1 page fails.
  vi.stubGlobal("fetch", vi.fn(async (input: string) => {
    if (input.includes("p1")) return { ok: false, status: 500 };
    return { ok: true, arrayBuffer: async () => new ArrayBuffer(1) };
  }));
  const job = await runFullSync({ db, adapter, mangaDir: baseDir });
  expect(job.status).toBe("done");
  expect(job.error_message).toBeTruthy();
  const parsed = JSON.parse(job.error_message ?? "");
  expect(parsed.kind).toBe("PartialFailure");
  expect(parsed.pagesFailed).toBeGreaterThanOrEqual(1);
});

// Fix #4 — total_pages is populated
test("runFullSync populates total_pages and done_pages", async () => {
  vi.stubGlobal("fetch", vi.fn(async () => ({
    ok: true, arrayBuffer: async () => new ArrayBuffer(1),
  })));
  const adapter = fakeAdapterWithSeriesAndChapters();
  const job = await runFullSync({ db, adapter, mangaDir: baseDir });
  expect(job.total_pages).toBe(2);
  expect(job.done_pages).toBe(2);
});
