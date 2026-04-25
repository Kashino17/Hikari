import { randomUUID } from "node:crypto";
import type Database from "better-sqlite3";
import { upsertPage, upsertSeries, upsertChapter, seriesId } from "./persist.js";
import { downloadPage } from "./image-store.js";
import type { MangaSourceAdapter } from "./sources/types.js";
import { SourceLayoutError } from "./sources/types.js";

function serializeSyncError(err: unknown): string {
  if (err instanceof SourceLayoutError) {
    return JSON.stringify({
      kind: "SourceLayoutError",
      message: err.message,
      url: err.url,
      selector: err.selector ?? null,
      stack: err.stack ?? null,
    });
  }
  if (err instanceof Error) {
    return JSON.stringify({
      kind: err.name,
      message: err.message,
      stack: err.stack ?? null,
    });
  }
  return String(err);
}

interface ChapterSyncInput {
  db: Database.Database;
  adapter: MangaSourceAdapter;
  seriesSlug: string;
  chapterNumber: number;
  chapterUrl: string;
  mangaDir: string;
  onProgress?: (delta: { pagesDone?: number; pagesFailed?: number; pagesQueued?: number }) => void;
}

const CONCURRENCY = 4;

async function withConcurrency<T>(
  items: T[],
  worker: (item: T) => Promise<void>,
  limit = CONCURRENCY,
): Promise<void> {
  let i = 0;
  const runners = Array.from({ length: limit }, async () => {
    while (i < items.length) {
      const idx = i++;
      await worker(items[idx]!);
    }
  });
  await Promise.all(runners);
}

function extFromUrl(url: string): string {
  const m = url.match(/\.(png|jpe?g|webp)(\?.*)?$/i);
  if (!m) return ".jpg";
  return `.${m[1]!.toLowerCase().replace("jpeg", "jpg")}`;
}

export async function runChapterSync(input: ChapterSyncInput): Promise<void> {
  const rawPages = await input.adapter.fetchChapterPages(input.chapterUrl);

  // Insert page rows up-front with local_path NULL so the API can report progress.
  for (const p of rawPages) {
    upsertPage(input.db, {
      source: input.adapter.id,
      seriesSlug: input.seriesSlug,
      chapterNumber: input.chapterNumber,
      pageNumber: p.pageNumber,
      sourceUrl: p.sourceUrl,
    });
  }

  input.db
    .prepare("UPDATE manga_chapters SET page_count = ? WHERE id = ?")
    .run(rawPages.length, `${input.adapter.id}:${input.seriesSlug}:${input.chapterNumber}`);

  input.onProgress?.({ pagesQueued: rawPages.length });

  await withConcurrency(rawPages, async (p) => {
    const ext = extFromUrl(p.sourceUrl);
    const relativePath = `${input.adapter.id}/${input.seriesSlug}/${input.chapterNumber}/${String(p.pageNumber).padStart(2, "0")}${ext}`;
    try {
      const result = await downloadPage({
        sourceUrl: p.sourceUrl,
        baseDir: input.mangaDir,
        relativePath,
      });
      upsertPage(input.db, {
        source: input.adapter.id,
        seriesSlug: input.seriesSlug,
        chapterNumber: input.chapterNumber,
        pageNumber: p.pageNumber,
        sourceUrl: p.sourceUrl,
        localPath: result.relativePath,
        bytes: result.bytes,
      });
      input.onProgress?.({ pagesDone: 1 });
    } catch {
      input.onProgress?.({ pagesFailed: 1 });
    }
  });
}

interface SeriesSyncInput {
  db: Database.Database;
  adapter: MangaSourceAdapter;
  seriesSlug: string;
  seriesUrl: string;
  seriesTitle: string;
  mangaDir: string;
  onProgress?: (delta: { chaptersDone?: number; pagesDone?: number; pagesFailed?: number; pagesQueued?: number }) => void;
}

export async function runSeriesSync(input: SeriesSyncInput): Promise<void> {
  upsertSeries(input.db, {
    source: input.adapter.id,
    sourceSlug: input.seriesSlug,
    title: input.seriesTitle,
    sourceUrl: input.seriesUrl,
  });

  const detail = await input.adapter.fetchSeriesDetail(input.seriesUrl);

  // Persist arcs first so chapters can reference them.
  const arcByNumber = new Map<number, string>();
  for (const arc of detail.arcs) {
    const arcId = `${input.adapter.id}:${input.seriesSlug}:arc-${arc.arcOrder}`;
    input.db.prepare(
      `INSERT INTO manga_arcs (id, series_id, title, arc_order, chapter_start, chapter_end)
       VALUES (?, ?, ?, ?, ?, ?)
       ON CONFLICT(id) DO UPDATE SET
         title = excluded.title,
         arc_order = excluded.arc_order,
         chapter_start = excluded.chapter_start,
         chapter_end = excluded.chapter_end`,
    ).run(
      arcId,
      seriesId(input.adapter.id, input.seriesSlug),
      arc.title,
      arc.arcOrder,
      arc.chapterNumbers[0] ?? null,
      arc.chapterNumbers[arc.chapterNumbers.length - 1] ?? null,
    );
    for (const n of arc.chapterNumbers) arcByNumber.set(n, arcId);
  }

  for (const ch of detail.chapters) {
    upsertChapter(input.db, {
      source: input.adapter.id,
      seriesSlug: input.seriesSlug,
      number: ch.number,
      ...(ch.title !== undefined && { title: ch.title }),
      sourceUrl: ch.sourceUrl,
      arcId: arcByNumber.get(ch.number) ?? null,
      ...(ch.publishedAt !== undefined && { publishedAt: ch.publishedAt }),
    });
  }

  input.db.prepare("UPDATE manga_series SET total_chapters = ?, last_synced_at = ? WHERE id = ?").run(
    detail.chapters.length,
    Date.now(),
    seriesId(input.adapter.id, input.seriesSlug),
  );

  for (const ch of detail.chapters) {
    await runChapterSync({
      db: input.db,
      adapter: input.adapter,
      seriesSlug: input.seriesSlug,
      chapterNumber: ch.number,
      chapterUrl: ch.sourceUrl,
      mangaDir: input.mangaDir,
      onProgress: (d) => {
        if (d.pagesDone !== undefined) input.onProgress?.({ pagesDone: d.pagesDone });
        if (d.pagesFailed !== undefined) input.onProgress?.({ pagesFailed: d.pagesFailed });
        if (d.pagesQueued !== undefined) input.onProgress?.({ pagesQueued: d.pagesQueued });
      },
    });
    input.onProgress?.({ chaptersDone: 1 });
  }
}

interface FullSyncInput {
  db: Database.Database;
  adapter: MangaSourceAdapter;
  mangaDir: string;
}

export interface SyncJobRow {
  id: string;
  source: string;
  series_id: string | null;
  status: "queued" | "running" | "done" | "failed";
  total_chapters: number;
  done_chapters: number;
  total_pages: number;
  done_pages: number;
  error_message: string | null;
  started_at: number;
  finished_at: number | null;
}

export async function runFullSync(input: FullSyncInput): Promise<SyncJobRow> {
  const id = randomUUID();
  const now = Date.now();
  input.db.prepare(
    `INSERT INTO manga_sync_jobs (id, source, status, started_at) VALUES (?, ?, 'running', ?)`,
  ).run(id, input.adapter.id, now);

  try {
    const series = await input.adapter.listSeries();

    let totalChapters = 0;
    for (const s of series) {
      const detail = await input.adapter.fetchSeriesDetail(s.sourceUrl);
      totalChapters += detail.chapters.length;
    }
    input.db.prepare("UPDATE manga_sync_jobs SET total_chapters = ? WHERE id = ?").run(totalChapters, id);

    let doneChapters = 0;
    let donePages = 0;
    let pagesFailed = 0;
    let pagesQueued = 0;
    for (const s of series) {
      await runSeriesSync({
        db: input.db,
        adapter: input.adapter,
        seriesSlug: s.sourceSlug,
        seriesUrl: s.sourceUrl,
        seriesTitle: s.title,
        mangaDir: input.mangaDir,
        onProgress: (d) => {
          if (d.chaptersDone) {
            doneChapters += d.chaptersDone;
            input.db.prepare("UPDATE manga_sync_jobs SET done_chapters = ? WHERE id = ?").run(doneChapters, id);
          }
          if (d.pagesDone) {
            donePages += d.pagesDone;
            input.db.prepare("UPDATE manga_sync_jobs SET done_pages = ? WHERE id = ?").run(donePages, id);
          }
          if (d.pagesFailed) {
            pagesFailed += d.pagesFailed;
          }
          if (d.pagesQueued) {
            pagesQueued += d.pagesQueued;
            input.db.prepare("UPDATE manga_sync_jobs SET total_pages = ? WHERE id = ?").run(pagesQueued, id);
          }
        },
      });
    }

    input.db.prepare(
      "UPDATE manga_sync_jobs SET status = 'done', finished_at = ? WHERE id = ?",
    ).run(Date.now(), id);

    if (pagesFailed > 0) {
      input.db.prepare(
        "UPDATE manga_sync_jobs SET error_message = ? WHERE id = ?",
      ).run(JSON.stringify({ kind: "PartialFailure", pagesFailed }), id);
    }
  } catch (err) {
    input.db.prepare(
      "UPDATE manga_sync_jobs SET status = 'failed', error_message = ?, finished_at = ? WHERE id = ?",
    ).run(serializeSyncError(err), Date.now(), id);
  }

  return input.db.prepare("SELECT * FROM manga_sync_jobs WHERE id = ?").get(id) as SyncJobRow;
}
