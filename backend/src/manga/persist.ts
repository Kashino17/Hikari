import type Database from "better-sqlite3";

export function seriesId(source: string, slug: string): string {
  return `${source}:${slug}`;
}
export function chapterId(source: string, slug: string, number: number): string {
  return `${source}:${slug}:${number}`;
}
export function pageId(
  source: string,
  slug: string,
  chapterNumber: number,
  pageNumber: number,
): string {
  return `${source}:${slug}:${chapterNumber}:${String(pageNumber).padStart(2, "0")}`;
}

export interface UpsertSeriesInput {
  source: string;
  sourceSlug: string;
  title: string;
  sourceUrl: string;
  author?: string;
  description?: string;
  coverPath?: string;
  status?: "ongoing" | "completed";
}

export function upsertSeries(db: Database.Database, input: UpsertSeriesInput): string {
  const id = seriesId(input.source, input.sourceSlug);
  const now = Date.now();
  db.prepare(
    `INSERT INTO manga_series (id, source, source_url, title, author, description, cover_path, status, added_at)
     VALUES (@id, @source, @source_url, @title, @author, @description, @cover_path, @status, @added_at)
     ON CONFLICT(id) DO UPDATE SET
       title = excluded.title,
       author = excluded.author,
       description = excluded.description,
       cover_path = COALESCE(excluded.cover_path, manga_series.cover_path),
       status = excluded.status,
       source_url = excluded.source_url`,
  ).run({
    id,
    source: input.source,
    source_url: input.sourceUrl,
    title: input.title,
    author: input.author ?? null,
    description: input.description ?? null,
    cover_path: input.coverPath ?? null,
    status: input.status ?? null,
    added_at: now,
  });
  return id;
}

export interface UpsertChapterInput {
  source: string;
  seriesSlug: string;
  number: number;
  title?: string;
  sourceUrl: string;
  arcId?: string | null;
  pageCount?: number;
  isAvailable?: boolean;
  publishedAt?: number;
}

export function upsertChapter(db: Database.Database, input: UpsertChapterInput): string {
  const id = chapterId(input.source, input.seriesSlug, input.number);
  const sId = seriesId(input.source, input.seriesSlug);
  const now = Date.now();
  db.prepare(
    `INSERT INTO manga_chapters
       (id, series_id, arc_id, number, title, source_url, page_count, is_available, published_at, added_at)
     VALUES
       (@id, @series_id, @arc_id, @number, @title, @source_url, @page_count, @is_available, @published_at, @added_at)
     ON CONFLICT(id) DO UPDATE SET
       title = excluded.title,
       source_url = excluded.source_url,
       arc_id = COALESCE(excluded.arc_id, manga_chapters.arc_id),
       page_count = COALESCE(excluded.page_count, manga_chapters.page_count),
       is_available = excluded.is_available,
       published_at = COALESCE(excluded.published_at, manga_chapters.published_at)`,
  ).run({
    id,
    series_id: sId,
    arc_id: input.arcId ?? null,
    number: input.number,
    title: input.title ?? null,
    source_url: input.sourceUrl,
    page_count: input.pageCount ?? null,
    is_available: input.isAvailable === false ? 0 : 1,
    published_at: input.publishedAt ?? null,
    added_at: now,
  });
  return id;
}

export interface UpsertPageInput {
  source: string;
  seriesSlug: string;
  chapterNumber: number;
  pageNumber: number;
  sourceUrl: string;
  localPath?: string;
  bytes?: number;
  width?: number;
  height?: number;
}

export function upsertPage(db: Database.Database, input: UpsertPageInput): string {
  const id = pageId(input.source, input.seriesSlug, input.chapterNumber, input.pageNumber);
  const cId = chapterId(input.source, input.seriesSlug, input.chapterNumber);
  db.prepare(
    `INSERT INTO manga_pages (id, chapter_id, page_number, source_url, local_path, bytes, width, height)
     VALUES (@id, @chapter_id, @page_number, @source_url, @local_path, @bytes, @width, @height)
     ON CONFLICT(id) DO UPDATE SET
       source_url = excluded.source_url,
       local_path = COALESCE(excluded.local_path, manga_pages.local_path),
       bytes = COALESCE(excluded.bytes, manga_pages.bytes),
       width = COALESCE(excluded.width, manga_pages.width),
       height = COALESCE(excluded.height, manga_pages.height)`,
  ).run({
    id,
    chapter_id: cId,
    page_number: input.pageNumber,
    source_url: input.sourceUrl,
    local_path: input.localPath ?? null,
    bytes: input.bytes ?? null,
    width: input.width ?? null,
    height: input.height ?? null,
  });
  return id;
}
