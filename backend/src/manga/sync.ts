import type Database from "better-sqlite3";
import { upsertPage } from "./persist.js";
import { downloadPage } from "./image-store.js";
import type { MangaSourceAdapter } from "./sources/types.js";

interface ChapterSyncInput {
  db: Database.Database;
  adapter: MangaSourceAdapter;
  seriesSlug: string;
  chapterNumber: number;
  chapterUrl: string;
  mangaDir: string;
  onProgress?: (delta: { pagesDone?: number }) => void;
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
      await worker(items[idx]);
    }
  });
  await Promise.all(runners);
}

function extFromUrl(url: string): string {
  const m = url.match(/\.(png|jpe?g|webp)(\?.*)?$/i);
  if (!m) return ".jpg";
  return `.${m[1].toLowerCase().replace("jpeg", "jpg")}`;
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
      // Page download failure: leave local_path NULL, sync continues.
    }
  });
}
