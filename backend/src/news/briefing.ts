import type Database from "better-sqlite3";
import { type Config, loadConfig } from "../config.js";
import { enrichItems } from "./enrich.js";
import { type NewsItem, findTopic, parseFeedItems } from "./feeds.js";
import { type NewsLang, fetchGoogleNewsTopic } from "./google-news.js";
import { summarizeItems } from "./summarize.js";

export interface BriefingItem {
  id: string;
  title: string;
  summary: string;
  source: string;
  url: string;
  imageUrls: string[];
  videoUrl: string | null;
  topic: string;
  publishedAt: string;
}

export interface BriefingParams {
  topics: string[];
  city?: string | undefined;
  lang: NewsLang;
  limit?: number | undefined;
  force?: boolean;
}

export interface BriefingDeps {
  db?: Database.Database | undefined;
  cfg?: Config | undefined;
  fetchImpl?: typeof fetch | undefined;
  summarizer?: typeof summarizeItems | undefined;
  now?: (() => number) | undefined;
}

const FEED_TIMEOUT_MS = 6000;

function normalizeTitle(title: string): string {
  return title
    .toLowerCase()
    .replace(/[\p{P}\p{S}]+/gu, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function sourceFromFeedUrl(feedUrl: string): string {
  try {
    return new URL(feedUrl).hostname.replace(/^www\./, "");
  } catch {
    return feedUrl;
  }
}

interface ResolvedQuery {
  /** topic value reported in each BriefingItem */
  topic: string;
  /** curated feed URLs, or null → Google News query */
  feeds: string[] | null;
  query: string;
}

function resolveQueries(topics: string[], city: string | undefined): ResolvedQuery[] {
  const queries: ResolvedQuery[] = topics.map((t) => {
    const hit = findTopic(t);
    return hit
      ? { topic: hit.key, feeds: hit.feeds, query: hit.label }
      : { topic: t, feeds: null, query: t };
  });
  if (city) queries.push({ topic: "lokal", feeds: null, query: city });
  return queries;
}

async function fetchFeedItems(feedUrl: string, fetchImpl: typeof fetch): Promise<NewsItem[]> {
  const res = await fetchImpl(feedUrl, {
    signal: AbortSignal.timeout(FEED_TIMEOUT_MS),
    headers: { "user-agent": "Hikari-News/1.0" },
  });
  if (!res.ok) throw new Error(`feed ${feedUrl} failed: ${res.status}`);
  return parseFeedItems(await res.text(), sourceFromFeedUrl(feedUrl));
}

function cacheKey(
  date: string,
  lang: NewsLang,
  topics: string[],
  city: string | undefined,
): string {
  return `${date}|${lang}|${[...topics].sort().join(",")}|${city ?? ""}`;
}

/**
 * Builds the daily briefing: resolve topics → fetch feeds in parallel →
 * dedupe by normalized title → newest first, capped at limit → enrich →
 * summarize. Cached per day in news_briefings; force bypasses the cache.
 */
export async function buildBriefing(
  params: BriefingParams,
  deps: BriefingDeps = {},
): Promise<BriefingItem[]> {
  const fetchImpl = deps.fetchImpl ?? fetch;
  const now = deps.now ?? Date.now;
  const limit = params.limit ?? 10;
  const date = new Date(now()).toISOString().slice(0, 10);
  const key = cacheKey(date, params.lang, params.topics, params.city);

  if (deps.db && !params.force) {
    const row = deps.db.prepare("SELECT payload FROM news_briefings WHERE key = ?").get(key) as
      | { payload: string }
      | undefined;
    if (row) return JSON.parse(row.payload) as BriefingItem[];
  }

  const queries = resolveQueries(params.topics, params.city);

  const collected: { item: NewsItem; topic: string }[] = [];
  await Promise.all(
    queries.map(async (q) => {
      if (q.feeds) {
        await Promise.all(
          q.feeds.map(async (feedUrl) => {
            try {
              for (const item of await fetchFeedItems(feedUrl, fetchImpl)) {
                collected.push({ item, topic: q.topic });
              }
            } catch {
              // dead/slow feed — skip it, other feeds still contribute
            }
          }),
        );
      } else {
        try {
          for (const item of await fetchGoogleNewsTopic(q.query, params.lang, fetchImpl)) {
            collected.push({ item, topic: q.topic });
          }
        } catch {
          // Google News unreachable — skip this query
        }
      }
    }),
  );

  // Dedupe by normalized title (same story across feeds/sources)
  const seen = new Set<string>();
  const deduped = collected.filter(({ item }) => {
    const norm = normalizeTitle(item.title);
    if (!norm || seen.has(norm)) return false;
    seen.add(norm);
    return true;
  });

  deduped.sort((a, b) => Date.parse(b.item.publishedAt) - Date.parse(a.item.publishedAt));
  const top = deduped.slice(0, limit);

  const enriched = await enrichItems(
    top.map((t) => t.item),
    fetchImpl,
  );
  const summarizer = deps.summarizer ?? summarizeItems;
  const cfg = deps.cfg ?? loadConfig();
  let summaries: Awaited<ReturnType<typeof summarizeItems>>;
  try {
    summaries = await summarizer(enriched, cfg, { fetchImpl }, params.lang);
  } catch {
    // summarizeItems itself never throws, but an injected summarizer might —
    // degrade to title/description instead of failing the whole briefing.
    summaries = enriched.map((item) => ({
      headline: item.title,
      summary: item.description.slice(0, 300),
    }));
  }

  const items: BriefingItem[] = enriched.map((item, i) => ({
    id: item.id,
    title: summaries[i]?.headline ?? item.title,
    summary: summaries[i]?.summary ?? item.description.slice(0, 300),
    source: item.source,
    url: item.url,
    imageUrls: item.imageUrls,
    videoUrl: item.videoUrl,
    topic: top[i]?.topic ?? "",
    publishedAt: item.publishedAt,
  }));

  if (deps.db) {
    deps.db
      .prepare("INSERT OR REPLACE INTO news_briefings (key, created_at, payload) VALUES (?, ?, ?)")
      .run(key, now(), JSON.stringify(items));
  }

  return items;
}
