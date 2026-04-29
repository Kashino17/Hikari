import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import Database from "better-sqlite3";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import {
  AdapterMismatchError,
  ArcNotFoundError,
  getArcManifest,
  runArcSync,
} from "./arc-download.js";
import type {
  MangaSourceAdapter,
  RawPage,
  RawSeries,
  RawSeriesDetail,
} from "./sources/types.js";

interface TestFixture {
  source: string;
  seriesSlug: string;
  seriesId: string;
  arcId: string;
}

function makeDb(): Database.Database {
  const db = new Database(":memory:");
  db.pragma("foreign_keys = ON");
  applyMigrations(db);
  return db;
}

function seedArc(
  db: Database.Database,
  fx: TestFixture,
  options: { chapters?: { number: number; pages: number; ready: number }[] } = {},
): void {
  const now = Date.now();
  db.prepare(
    `INSERT INTO manga_series (id, source, source_url, title, added_at)
     VALUES (?, ?, ?, ?, ?)`,
  ).run(fx.seriesId, fx.source, "https://example/x", "Test Series", now);
  db.prepare(
    `INSERT INTO manga_arcs (id, series_id, title, arc_order, chapter_start, chapter_end)
     VALUES (?, ?, ?, ?, ?, ?)`,
  ).run(fx.arcId, fx.seriesId, "Arc One", 1, 1, 3);

  for (const ch of options.chapters ?? []) {
    const chapterId = `${fx.source}:${fx.seriesSlug}:${ch.number}`;
    db.prepare(
      `INSERT INTO manga_chapters
         (id, series_id, arc_id, number, source_url, page_count, is_available, added_at)
       VALUES (?, ?, ?, ?, ?, ?, 1, ?)`,
    ).run(chapterId, fx.seriesId, fx.arcId, ch.number, `https://example/c${ch.number}`, ch.pages, now);

    for (let p = 1; p <= ch.pages; p++) {
      const pageId = `${chapterId}:${String(p).padStart(2, "0")}`;
      const isReady = p <= ch.ready;
      db.prepare(
        `INSERT INTO manga_pages
           (id, chapter_id, page_number, source_url, local_path, bytes)
         VALUES (?, ?, ?, ?, ?, ?)`,
      ).run(
        pageId,
        chapterId,
        p,
        `https://example/p${p}`,
        isReady ? `rel/${pageId}.jpg` : null,
        isReady ? 1000 + p : null,
      );
    }
  }
}

const FX: TestFixture = {
  source: "fixsrc",
  seriesSlug: "test-series",
  seriesId: "fixsrc:test-series",
  arcId: "fixsrc:test-series:arc-1",
};

describe("getArcManifest", () => {
  it("returns null for unknown arcId", () => {
    const db = makeDb();
    expect(getArcManifest(db, "nope:none:arc-1")).toBeNull();
  });

  it("collects pages from every chapter of the arc, ordered by chapter then page", () => {
    const db = makeDb();
    seedArc(db, FX, {
      chapters: [
        { number: 1, pages: 3, ready: 3 },
        { number: 2, pages: 2, ready: 0 },
      ],
    });
    const m = getArcManifest(db, FX.arcId);
    expect(m).not.toBeNull();
    expect(m?.chapters).toBe(2);
    expect(m?.pages).toHaveLength(5);
    expect(m?.pages.map((p) => `${p.chapterNumber}.${p.pageNumber}`)).toEqual([
      "1.1",
      "1.2",
      "1.3",
      "2.1",
      "2.2",
    ]);
  });

  it("strips the source prefix when exposing seriesSlug", () => {
    const db = makeDb();
    seedArc(db, FX, { chapters: [{ number: 1, pages: 1, ready: 0 }] });
    expect(getArcManifest(db, FX.arcId)?.seriesSlug).toBe("test-series");
  });

  it("ready flag mirrors local_path presence", () => {
    const db = makeDb();
    seedArc(db, FX, { chapters: [{ number: 1, pages: 4, ready: 2 }] });
    const m = getArcManifest(db, FX.arcId);
    expect(m?.pages.map((p) => p.ready)).toEqual([true, true, false, false]);
  });

  it("totalBytes sums every page's bytes (NULL → 0)", () => {
    const db = makeDb();
    seedArc(db, FX, { chapters: [{ number: 1, pages: 3, ready: 2 }] });
    // bytes for ready pages: 1001 + 1002 = 2003; third is NULL → 0
    expect(getArcManifest(db, FX.arcId)?.totalBytes).toBe(2003);
  });

  it("readyPages counts only pages with local_path set", () => {
    const db = makeDb();
    seedArc(db, FX, {
      chapters: [
        { number: 1, pages: 3, ready: 1 },
        { number: 2, pages: 2, ready: 2 },
      ],
    });
    expect(getArcManifest(db, FX.arcId)?.readyPages).toBe(3);
  });
});

describe("runArcSync", () => {
  let mangaDir: string;
  let db: Database.Database;

  beforeEach(() => {
    mangaDir = mkdtempSync(join(tmpdir(), "hikari-arc-test-"));
    db = makeDb();
  });
  afterEach(() => {
    rmSync(mangaDir, { recursive: true, force: true });
  });

  function makeFakeAdapter(id: string, pagesPerChapter: number): MangaSourceAdapter {
    return {
      id,
      name: id,
      baseUrl: "https://example",
      listSeries: async (): Promise<RawSeries[]> => [],
      fetchSeriesDetail: async (): Promise<RawSeriesDetail> => ({ arcs: [], chapters: [] }),
      fetchChapterPages: async (chapterUrl: string): Promise<RawPage[]> => {
        // Write a tiny placeholder so downloadPage's HTTP fetch isn't needed —
        // BUT runChapterSync calls downloadPage for real, which uses fetch().
        // For unit-testing the adapter mismatch / not-found paths only, the
        // HTTP doesn't need to succeed. Tests below stay in those paths.
        void chapterUrl;
        return Array.from({ length: pagesPerChapter }, (_, i) => ({
          pageNumber: i + 1,
          sourceUrl: "https://example/missing.jpg",
        }));
      },
    };
  }

  it("throws ArcNotFoundError for unknown arc", async () => {
    const adapter = makeFakeAdapter(FX.source, 1);
    await expect(
      runArcSync({ db, adapter, arcId: "nope:none:arc-99", mangaDir }),
    ).rejects.toBeInstanceOf(ArcNotFoundError);
  });

  it("throws AdapterMismatchError when adapter source differs from arc source", async () => {
    seedArc(db, FX, { chapters: [{ number: 1, pages: 1, ready: 0 }] });
    const wrong = makeFakeAdapter("other-source", 1);
    await expect(runArcSync({ db, adapter: wrong, arcId: FX.arcId, mangaDir })).rejects.toBeInstanceOf(
      AdapterMismatchError,
    );
  });

  it("emits one chaptersDone event per available chapter (failures included)", async () => {
    seedArc(db, FX, {
      chapters: [
        { number: 1, pages: 1, ready: 0 },
        { number: 2, pages: 1, ready: 0 },
      ],
    });
    // Adapter that throws → every chapter "fails" but loop continues.
    const failing: MangaSourceAdapter = {
      id: FX.source,
      name: FX.source,
      baseUrl: "https://example",
      listSeries: async () => [],
      fetchSeriesDetail: async () => ({ arcs: [], chapters: [] }),
      fetchChapterPages: async () => {
        throw new Error("simulated source failure");
      },
    };

    let chaptersDone = 0;
    await runArcSync({
      db,
      adapter: failing,
      arcId: FX.arcId,
      mangaDir,
      onProgress: (d) => {
        if (d.chaptersDone) chaptersDone += d.chaptersDone;
      },
    });
    expect(chaptersDone).toBe(2);
  });

  it("skips chapters marked is_available=0", async () => {
    seedArc(db, FX, { chapters: [{ number: 1, pages: 1, ready: 0 }] });
    db.prepare("UPDATE manga_chapters SET is_available = 0 WHERE id = ?").run(
      `${FX.source}:${FX.seriesSlug}:1`,
    );
    const adapter = makeFakeAdapter(FX.source, 1);

    let chaptersDone = 0;
    await runArcSync({
      db,
      adapter,
      arcId: FX.arcId,
      mangaDir,
      onProgress: (d) => {
        if (d.chaptersDone) chaptersDone += d.chaptersDone;
      },
    });
    expect(chaptersDone).toBe(0);
  });
});

