import { load as loadHtml } from "cheerio";
import {
  type MangaSourceAdapter,
  type RawSeries,
  type RawSeriesDetail,
  type RawPage,
  SourceLayoutError,
} from "./types.js";

const BASE = "https://onepiece.tube";
const LIST_URL = `${BASE}/manga/kapitel-mangaliste`;

interface ListData {
  category?: {
    id: number;
    name: string;
    description?: string;
    published?: string;
  };
  entries?: Array<{
    id: number;
    name: string;
    number: number;
    arc_id?: number;
    pages?: number;
    is_available?: boolean;
    date?: string; // "DD.MM.YYYY"
    href: string;
  }>;
  arcs?: Array<{ id: number; name: string; min: number; max: number }>;
}

interface ChapterData {
  chapter?: {
    name?: string;
    pages?: Array<{ url: string; width?: number; height?: number; type?: string }>;
  };
  currentChapter?: unknown;
}

async function fetchHtml(url: string): Promise<string> {
  const r = await fetch(url, {
    headers: {
      "User-Agent": "Mozilla/5.0 Hikari/0.1",
      Accept: "text/html,application/xhtml+xml",
    },
  });
  if (!r.ok) throw new Error(`HTTP ${r.status} for ${url}`);
  return r.text();
}

function extractData<T>(html: string, url: string): T {
  const $ = loadHtml(html);
  let payload: string | undefined;

  $("script").each((_, el) => {
    const text = $(el).html() ?? "";
    const m = text.match(/window\.__data\s*=\s*({[\s\S]*?});\s*$/m);
    if (m) {
      payload = m[1];
      return false; // break cheerio each
    }
  });

  if (!payload) {
    throw new SourceLayoutError(
      "window.__data not found in page",
      url,
      'script[contains("window.__data")]',
    );
  }

  try {
    return JSON.parse(payload) as T;
  } catch (err) {
    throw new SourceLayoutError(
      `window.__data JSON parse failed: ${(err as Error).message}`,
      url,
    );
  }
}

// "DD.MM.YYYY" → epoch-ms (UTC midnight)
function parseGermanDate(s: string | undefined): number | undefined {
  if (!s) return undefined;
  const m = s.match(/^(\d{2})\.(\d{2})\.(\d{4})$/);
  if (!m) return undefined;
  const [, d, mo, y] = m;
  const t = Date.UTC(Number(y), Number(mo) - 1, Number(d));
  return Number.isNaN(t) ? undefined : t;
}

async function listSeries(): Promise<RawSeries[]> {
  // Fetch the listing page to validate it's reachable and parseable
  extractData<ListData>(await fetchHtml(LIST_URL), LIST_URL);

  return [
    {
      sourceSlug: "one-piece",
      title: "One Piece",
      sourceUrl: LIST_URL,
      author: "Eiichiro Oda",
      status: "ongoing",
    },
  ];
}

async function fetchSeriesDetail(seriesUrl: string): Promise<RawSeriesDetail> {
  const data = extractData<ListData>(await fetchHtml(seriesUrl), seriesUrl);

  if (!data.entries || data.entries.length === 0) {
    throw new SourceLayoutError("No entries[] in window.__data", seriesUrl);
  }

  // entries are newest-first; sort ascending by chapter number
  const chapters = [...data.entries]
    .sort((a, b) => a.number - b.number)
    .map((e) => {
      const publishedAt = parseGermanDate(e.date);
      return {
        number: e.number,
        title: e.name,
        sourceUrl: e.href,
        ...(publishedAt !== undefined ? { publishedAt } : {}),
      };
    });

  // Build arc membership map from entries
  const chapterNumbersByArcId = new Map<number, number[]>();
  for (const e of data.entries) {
    if (e.arc_id == null) continue;
    if (!chapterNumbersByArcId.has(e.arc_id)) {
      chapterNumbersByArcId.set(e.arc_id, []);
    }
    chapterNumbersByArcId.get(e.arc_id)!.push(e.number);
  }

  // Sort arcs by min chapter ascending, assign arcOrder = index
  const sortedArcs = [...(data.arcs ?? [])].sort((a, b) => a.min - b.min);
  const arcs = sortedArcs.map((a, i) => ({
    title: a.name.trim(),
    arcOrder: i,
    chapterNumbers: (chapterNumbersByArcId.get(a.id) ?? []).slice().sort((x, y) => x - y),
  }));

  const description = data.category?.description;
  return {
    ...(description !== undefined ? { description } : {}),
    arcs,
    chapters,
  };
}

async function fetchChapterPages(chapterUrl: string): Promise<RawPage[]> {
  const data = extractData<ChapterData>(await fetchHtml(chapterUrl), chapterUrl);
  const rawPages = data.chapter?.pages ?? [];

  if (rawPages.length === 0) {
    throw new SourceLayoutError("No chapter.pages[] in window.__data", chapterUrl);
  }

  return rawPages.map((p, i) => ({
    pageNumber: i + 1,
    sourceUrl: p.url,
  }));
}

export const onePieceTubeAdapter: MangaSourceAdapter = {
  id: "onepiecetube",
  name: "One Piece Tube",
  baseUrl: BASE,
  listSeries,
  fetchSeriesDetail,
  fetchChapterPages,
};
