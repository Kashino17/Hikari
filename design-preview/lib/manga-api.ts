const API_BASE = process.env.HIKARI_API_BASE_URL ?? "http://localhost:3000";

export interface ApiSeries {
  id: string;
  source: string;
  title: string;
  author?: string;
  description?: string;
  coverPath?: string;
  totalChapters: number;
  lastSyncedAt?: number;
}

export interface ApiArc {
  id: string;
  title: string;
  arcOrder: number;
  chapterStart?: number;
  chapterEnd?: number;
}

export interface ApiChapter {
  id: string;
  number: number;
  title?: string;
  arcId?: string;
  pageCount: number;
  isRead: 0 | 1;
}

export interface ApiSeriesDetail extends ApiSeries {
  arcs: ApiArc[];
  chapters: ApiChapter[];
}

export interface ApiPage {
  id: string;
  pageNumber: number;
  ready: boolean;
}

export interface ApiContinue {
  seriesId: string;
  title: string;
  coverPath?: string;
  chapterId: string;
  pageNumber: number;
  updatedAt: number;
}

export interface ApiSyncJob {
  id: string;
  source: string;
  status: "queued" | "running" | "done" | "failed";
  total_chapters: number;
  done_chapters: number;
  total_pages: number;
  done_pages: number;
  error_message?: string;
  started_at: number;
  finished_at?: number;
}

async function getJson<T>(path: string, init?: RequestInit): Promise<T> {
  const r = await fetch(`${API_BASE}${path}`, { cache: "no-store", ...init });
  if (!r.ok) throw new Error(`${path}: ${r.status}`);
  return r.json() as Promise<T>;
}

export const mangaApi = {
  listSeries: () => getJson<ApiSeries[]>("/api/manga/series"),
  getSeries: (id: string) =>
    getJson<ApiSeriesDetail>(`/api/manga/series/${encodeURIComponent(id)}`),
  getChapterPages: (chapterId: string) =>
    getJson<ApiPage[]>(`/api/manga/chapters/${encodeURIComponent(chapterId)}/pages`),
  getContinue: () => getJson<ApiContinue[]>("/api/manga/continue"),
  pageUrl: (pageId: string) => `${API_BASE}/api/manga/page/${encodeURIComponent(pageId)}`,

  addToLibrary: (seriesId: string) =>
    fetch(`${API_BASE}/api/manga/library/${encodeURIComponent(seriesId)}`, { method: "POST" }),
  removeFromLibrary: (seriesId: string) =>
    fetch(`${API_BASE}/api/manga/library/${encodeURIComponent(seriesId)}`, { method: "DELETE" }),
  setProgress: (seriesId: string, chapterId: string, pageNumber: number) =>
    fetch(`${API_BASE}/api/manga/progress/${encodeURIComponent(seriesId)}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ chapterId, pageNumber }),
    }),
  markRead: (chapterId: string) =>
    fetch(`${API_BASE}/api/manga/chapters/${encodeURIComponent(chapterId)}/read`, { method: "PUT" }),

  startSync: () => fetch(`${API_BASE}/api/manga/sync`, { method: "POST" }),
  startChapterSync: (chapterId: string) =>
    fetch(`${API_BASE}/api/manga/chapters/${encodeURIComponent(chapterId)}/sync`, { method: "POST" }),
  listJobs: () => getJson<ApiSyncJob[]>("/api/manga/sync/jobs"),
};

export const MANGA_API_BASE = API_BASE;
