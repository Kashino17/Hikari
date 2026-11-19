import type { DownloadInput, DownloadResult } from "../image-store.js";

export interface RawSeries {
  sourceSlug: string;
  title: string;
  sourceUrl: string;
  coverUrl?: string;
  author?: string;
  status?: "ongoing" | "completed";
}

export interface RawArc {
  title: string;
  arcOrder: number;
  chapterNumbers: number[];
}

export interface RawChapter {
  number: number;
  title?: string;
  sourceUrl: string;
  pageCount?: number;
  isAvailable?: boolean;
  publishedAt?: number;
  /** Sprache des Kapitels ("de", "en"). */
  lang?: string;
}

export interface RawPage {
  pageNumber: number;
  sourceUrl: string;
}

export interface RawSeriesDetail {
  description?: string;
  arcs: RawArc[];
  chapters: RawChapter[];
}

export interface MangaSourceAdapter {
  readonly id: string;
  readonly name: string;
  readonly baseUrl: string;
  listSeries(): Promise<RawSeries[]>;
  fetchSeriesDetail(seriesUrl: string): Promise<RawSeriesDetail>;
  fetchChapterPages(chapterUrl: string): Promise<RawPage[]>;
  /**
   * Reihen kommen über den Katalog (MangaDex), nicht über listSeries — der globale
   * Abgleich (POST /api/manga/sync) lässt diese Quelle aus.
   */
  readonly catalogOnly?: boolean;
  /** Eigenes Laden einer Seite (Kennung, Wiederholung, Rückmeldung); sonst image-store. */
  downloadPage?(input: DownloadInput): Promise<DownloadResult>;
}

export class SourceLayoutError extends Error {
  constructor(
    message: string,
    public readonly url: string,
    public readonly selector?: string,
  ) {
    super(message);
    this.name = "SourceLayoutError";
  }
}
