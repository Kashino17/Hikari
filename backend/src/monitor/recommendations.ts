import type Database from "better-sqlite3";
import { getFilterState } from "../scorer/filter-repo.js";
import { searchChannels, type ChannelSearchResult } from "./channel-search.js";

export interface RecommendationResult extends ChannelSearchResult {
  matchingTags: string[];   // which likeTags surfaced this channel
}

const MAX_TAGS = 3;          // cap on parallel yt-dlp searches per call
const PER_TAG_LIMIT = 8;     // YouTube returns up to N hits per tag
const RESULT_LIMIT = 12;     // final returned-to-client count

interface CacheEntry {
  expires: number;
  results: RecommendationResult[];
}

const cache = new Map<string, CacheEntry>();
const TTL_MS = 60 * 60 * 1000; // 1h

function cacheKey(tags: string[], excludedIds: string[]): string {
  return `${tags.sort().join(",")}|${excludedIds.sort().join(",")}`;
}

/**
 * Topic-driven recommendations: for each `filter.likeTags`, run a YouTube
 * channel search; aggregate; exclude already-subscribed; rank by how many
 * tags surfaced the channel + subscriber count as tiebreaker.
 *
 * Cached 1h per (tags, excluded-ids) tuple — calling this 100x in a row
 * hits yt-dlp once.
 */
export async function recommendChannels(db: Database.Database): Promise<RecommendationResult[]> {
  const state = getFilterState(db);
  const tags = state.filter.likeTags.slice(0, MAX_TAGS);
  if (tags.length === 0) return [];

  const subscribedIds = (db
    .prepare("SELECT id FROM channels WHERE is_active=1")
    .all() as { id: string }[]).map((r) => r.id);

  const key = cacheKey(tags, subscribedIds);
  const hit = cache.get(key);
  if (hit && hit.expires > Date.now()) return hit.results;

  // Parallel searches — yt-dlp calls are independent, fan out.
  const perTagResults = await Promise.all(
    tags.map(async (tag) => {
      try {
        const found = await searchChannels(tag, PER_TAG_LIMIT);
        return { tag, found };
      } catch {
        return { tag, found: [] as ChannelSearchResult[] };
      }
    }),
  );

  const subscribedSet = new Set(subscribedIds);
  const seen = new Map<string, RecommendationResult>();

  for (const { tag, found } of perTagResults) {
    for (const ch of found) {
      if (subscribedSet.has(ch.channelId)) continue;
      const existing = seen.get(ch.channelId);
      if (existing) {
        if (!existing.matchingTags.includes(tag)) existing.matchingTags.push(tag);
      } else {
        seen.set(ch.channelId, { ...ch, matchingTags: [tag] });
      }
    }
  }

  // Rank: more matching tags first, then subscriber count desc.
  const ranked = [...seen.values()].sort((a, b) => {
    if (b.matchingTags.length !== a.matchingTags.length) {
      return b.matchingTags.length - a.matchingTags.length;
    }
    return (b.subscribers ?? 0) - (a.subscribers ?? 0);
  });

  const results = ranked.slice(0, RESULT_LIMIT);
  cache.set(key, { expires: Date.now() + TTL_MS, results });
  return results;
}

/** For tests: clear cache between runs. */
export function clearRecommendationCache(): void {
  cache.clear();
}
