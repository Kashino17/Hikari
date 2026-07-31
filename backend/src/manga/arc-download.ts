import type Database from "better-sqlite3";
import type { MangaSourceAdapter } from "./sources/types.js";
import { runChapterSync } from "./sync.js";

export interface ArcManifestPage {
  pageId: string;
  chapterId: string;
  chapterNumber: number;
  pageNumber: number;
  bytes: number | null;
  ready: boolean;
}

export interface ArcManifest {
  arcId: string;
  arcOrder: number;
  arcTitle: string;
  seriesId: string;
  seriesSlug: string;
  seriesTitle: string;
  chapters: number;
  pages: ArcManifestPage[];
  totalBytes: number;
  readyPages: number;
}

interface ArcRow {
  id: string;
  series_id: string;
  title: string;
  arc_order: number;
}

interface SeriesRow {
  id: string;
  source: string;
  title: string;
}

interface ChapterRow {
  id: string;
  number: number;
  source_url: string;
}

interface PageManifestRow {
  id: string;
  chapter_id: string;
  chapter_number: number;
  page_number: number;
  bytes: number | null;
  local_path: string | null;
}

// IDs in the schema are formed as `<source>:<seriesSlug>` (series) and
// `<source>:<seriesSlug>:arc-<n>` (arc). Strip the source prefix to recover
// the slug used by sync.ts when laying out files on disk.
function stripSourcePrefix(id: string, source: string): string {
  const prefix = `${source}:`;
  return id.startsWith(prefix) ? id.slice(prefix.length) : id;
}

/**
 * Returns the per-page manifest for a manga arc — what the phone needs to
 * pull every page over /api/manga/page/:pageId. `ready=true` means the
 * server already has the page on disk; `ready=false` means the caller
 * should trigger /arcs/:arcId/download first (or wait if one's running).
 */
export function getArcManifest(
  db: Database.Database,
  arcId: string,
): ArcManifest | null {
  const arc = db
    .prepare("SELECT id, series_id, title, arc_order FROM manga_arcs WHERE id = ?")
    .get(arcId) as ArcRow | undefined;
  if (!arc) return null;

  const series = db
    .prepare("SELECT id, source, title FROM manga_series WHERE id = ?")
    .get(arc.series_id) as SeriesRow | undefined;
  if (!series) return null;

  const seriesSlug = stripSourcePrefix(series.id, series.source);

  const pages = db
    .prepare(
      `SELECT p.id, p.chapter_id, c.number AS chapter_number,
              p.page_number, p.bytes, p.local_path
         FROM manga_pages p
         JOIN manga_chapters c ON c.id = p.chapter_id
        WHERE c.arc_id = ?
        ORDER BY c.number ASC, p.page_number ASC`,
    )
    .all(arcId) as PageManifestRow[];

  const chaptersCount = db
    .prepare("SELECT COUNT(*) AS n FROM manga_chapters WHERE arc_id = ?")
    .get(arcId) as { n: number };

  const manifestPages: ArcManifestPage[] = pages.map((p) => ({
    pageId: p.id,
    chapterId: p.chapter_id,
    chapterNumber: p.chapter_number,
    pageNumber: p.page_number,
    bytes: p.bytes,
    ready: p.local_path !== null,
  }));

  return {
    arcId: arc.id,
    arcOrder: arc.arc_order,
    arcTitle: arc.title,
    seriesId: series.id,
    seriesSlug,
    seriesTitle: series.title,
    chapters: chaptersCount.n,
    pages: manifestPages,
    totalBytes: pages.reduce((sum, p) => sum + (p.bytes ?? 0), 0),
    readyPages: pages.filter((p) => p.local_path !== null).length,
  };
}

export interface ArcSyncInput {
  db: Database.Database;
  adapter: MangaSourceAdapter;
  arcId: string;
  mangaDir: string;
  onProgress?: (delta: {
    chaptersDone?: number;
    pagesDone?: number;
    pagesFailed?: number;
  }) => void;
}

export class ArcNotFoundError extends Error {
  constructor(arcId: string) {
    super(`arc not found: ${arcId}`);
    this.name = "ArcNotFoundError";
  }
}

export class AdapterMismatchError extends Error {
  constructor(arcSource: string, adapterId: string) {
    super(`adapter mismatch: arc source=${arcSource}, adapter=${adapterId}`);
    this.name = "AdapterMismatchError";
  }
}

/**
 * Server-side: ensure every available chapter of this arc has its pages on
 * disk. Idempotent — chapters already complete are skipped by runChapterSync.
 * Page failures are counted but don't abort the run; the manifest reflects
 * the gap so the phone can retry just the missing pages later.
 */
export async function runArcSync(input: ArcSyncInput): Promise<void> {
  const arcRow = input.db
    .prepare(
      `SELECT s.id AS seriesId, s.source AS source
         FROM manga_arcs a
         JOIN manga_series s ON s.id = a.series_id
        WHERE a.id = ?`,
    )
    .get(input.arcId) as { seriesId: string; source: string } | undefined;
  if (!arcRow) throw new ArcNotFoundError(input.arcId);
  if (arcRow.source !== input.adapter.id) {
    throw new AdapterMismatchError(arcRow.source, input.adapter.id);
  }

  const seriesSlug = stripSourcePrefix(arcRow.seriesId, arcRow.source);

  const chapters = input.db
    .prepare(
      `SELECT id, number, source_url
         FROM manga_chapters
        WHERE arc_id = ? AND is_available = 1
        ORDER BY number ASC`,
    )
    .all(input.arcId) as ChapterRow[];

  for (const ch of chapters) {
    try {
      await runChapterSync({
        db: input.db,
        adapter: input.adapter,
        seriesSlug,
        chapterNumber: ch.number,
        chapterUrl: ch.source_url,
        mangaDir: input.mangaDir,
        onProgress: (d) => {
          if (d.pagesDone !== undefined) input.onProgress?.({ pagesDone: d.pagesDone });
          if (d.pagesFailed !== undefined) input.onProgress?.({ pagesFailed: d.pagesFailed });
        },
      });
    } catch {
      input.onProgress?.({ pagesFailed: 1 });
    }
    input.onProgress?.({ chaptersDone: 1 });
  }
}
