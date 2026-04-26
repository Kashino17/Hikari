import type Database from "better-sqlite3";
import { getFilterState } from "../scorer/filter-repo.js";
import { searchChannels, type ChannelSearchResult } from "./channel-search.js";
import { pickAvatar, pickBanner } from "./channel-resolver.js";
import { runYtDlp } from "../yt-dlp/client.js";

export interface RecommendationResult extends ChannelSearchResult {
  /** Which user-likeTags surfaced this channel. */
  matchingTags: string[];
  /** Days since last public upload (null = couldn't determine). */
  lastUploadDays: number | null;
}

const MAX_TAGS = 3;          // cap on parallel yt-dlp searches per call
const PER_TAG_LIMIT = 8;     // YouTube returns up to N hits per tag
const RESULT_LIMIT = 12;     // final returned-to-client count
const PRE_RANK_POOL = 18;    // how many candidates to deep-fetch activity for

// Quality thresholds
const MIN_SUBS_UNVERIFIED = 1_000;
const MAX_INACTIVE_DAYS = 365;

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
 * channel search; aggregate; exclude already-subscribed; pre-filter low-quality
 * channels; deep-fetch activity (last upload date) for the top pool; drop
 * channels inactive >1y; rank by composite score.
 *
 * Cached 1h per (tags, excluded-ids). Pass `bypassCache: true` to ignore the
 * cache and shuffle equal-score tiers, so the user gets fresh-feeling results
 * on a manual refresh.
 */
export async function recommendChannels(
  db: Database.Database,
  opts: { bypassCache?: boolean } = {},
): Promise<RecommendationResult[]> {
  const state = getFilterState(db);
  const tags = state.filter.likeTags.slice(0, MAX_TAGS);
  if (tags.length === 0) return [];

  const subscribedIds = (db
    .prepare("SELECT id FROM channels WHERE is_active=1")
    .all() as { id: string }[]).map((r) => r.id);

  const key = cacheKey(tags, subscribedIds);
  if (opts.bypassCache) {
    cache.delete(key);
  } else {
    const hit = cache.get(key);
    if (hit && hit.expires > Date.now()) return hit.results;
  }

  // 1) Parallel searches per tag
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

  // 2) Aggregate, dedupe, exclude subscribed
  const subscribedSet = new Set(subscribedIds);
  const seen = new Map<string, RecommendationResult>();
  for (const { tag, found } of perTagResults) {
    for (const ch of found) {
      if (subscribedSet.has(ch.channelId)) continue;
      const existing = seen.get(ch.channelId);
      if (existing) {
        if (!existing.matchingTags.includes(tag)) existing.matchingTags.push(tag);
      } else {
        seen.set(ch.channelId, { ...ch, matchingTags: [tag], lastUploadDays: null });
      }
    }
  }

  // 3) Pre-filter: drop tiny / unverified-no-subs noise
  const preFiltered = [...seen.values()].filter((c) => {
    if (c.verified) return true;
    return (c.subscribers ?? 0) >= MIN_SUBS_UNVERIFIED;
  });

  // 4) Initial ranking (cheap), take top pool for activity fetch
  preFiltered.sort(scoreCompare);
  const pool = preFiltered.slice(0, PRE_RANK_POOL);

  // 5) Deep-fetch channel info for pool in parallel — gets activity AND
  //    fills in banner art + a higher-quality avatar (the flat-mode search
  //    sometimes returns null thumbnails). One yt-dlp call per channel.
  await Promise.all(
    pool.map(async (c) => {
      const info = await fetchChannelDeepInfo(c.channelId);
      c.lastUploadDays = info.lastUploadDays;
      c.banner = info.banner;
      // Only overwrite if the deep fetch produced something — preserves the
      // search-mode thumbnail when the channel page lookup yields nothing.
      if (info.thumbnail) c.thumbnail = info.thumbnail;
      if (info.handle && !c.handle) c.handle = info.handle;
    }),
  );

  // 6) Drop inactive (no detectable upload in MAX_INACTIVE_DAYS).
  //    `null` = couldn't determine — keep it (don't punish for yt-dlp glitches).
  const active = pool.filter(
    (c) => c.lastUploadDays === null || c.lastUploadDays <= MAX_INACTIVE_DAYS,
  );

  // 7) Re-rank with full composite score (now including activity)
  active.sort(scoreCompare);

  // 8) Bypass-mode: shuffle channels with the same score so the user gets
  //    a different ordering each manual refresh.
  if (opts.bypassCache) shuffleEqualScores(active);

  const results = active.slice(0, RESULT_LIMIT);
  cache.set(key, { expires: Date.now() + TTL_MS, results });
  return results;
}

/** For tests: clear cache between runs. */
export function clearRecommendationCache(): void {
  cache.clear();
}

// ─── Scoring ────────────────────────────────────────────────────────────────

function score(c: RecommendationResult): number {
  const tagBoost = c.matchingTags.length * 1_000;
  const subsBoost = Math.log10((c.subscribers ?? 0) + 1) * 100;
  const verifiedBoost = c.verified ? 100 : 0;
  const activityBoost = activityScore(c.lastUploadDays);
  return tagBoost + subsBoost + verifiedBoost + activityBoost;
}

function activityScore(days: number | null): number {
  if (days === null) return 0;
  if (days <= 30) return 200;
  if (days <= 90) return 100;
  if (days <= 180) return 50;
  if (days <= 365) return 0;
  return -300;
}

function scoreCompare(a: RecommendationResult, b: RecommendationResult): number {
  return score(b) - score(a);
}

function shuffleEqualScores(arr: RecommendationResult[]): void {
  let i = 0;
  while (i < arr.length) {
    const head = arr[i]!;
    let j = i + 1;
    while (j < arr.length && score(arr[j]!) === score(head)) j++;
    // Fisher-Yates within [i, j)
    for (let k = j - 1; k > i; k--) {
      const r = i + Math.floor(Math.random() * (k - i + 1));
      const tmp = arr[k]!;
      arr[k] = arr[r]!;
      arr[r] = tmp;
    }
    i = j;
  }
}

// ─── Activity fetch ─────────────────────────────────────────────────────────

interface ChannelDeepInfo {
  lastUploadDays: number | null;
  thumbnail: string | null;
  banner: string | null;
  handle: string | null;
}

/**
 * One yt-dlp call against the channel URL with `--playlist-items 1` —
 * returns enough JSON for both the latest-upload timestamp AND the
 * channel-level art (banner + avatar). Replaces the older timestamp-only
 * `fetchLastUploadDays` so recommendation cards can show real banners
 * instead of placeholder gradients.
 */
async function fetchChannelDeepInfo(channelId: string): Promise<ChannelDeepInfo> {
  const empty: ChannelDeepInfo = {
    lastUploadDays: null, thumbnail: null, banner: null, handle: null,
  };
  try {
    const result = await runYtDlp(
      [
        "--flat-playlist",
        "--playlist-items", "1",
        "--dump-single-json",
        "--no-warnings",
        `https://www.youtube.com/channel/${channelId}`,
      ],
      { timeoutMs: 10_000 },
    );
    if (!result.stdout.trim()) return empty;
    const parsed = JSON.parse(result.stdout) as {
      thumbnails?: { url?: string; width?: number | null; height?: number | null }[];
      uploader_id?: string | null;
      entries?: { timestamp?: number | null }[];
    };
    const ts = parsed.entries?.[0]?.timestamp;
    const lastUploadDays =
      typeof ts === "number" && ts > 0
        ? Math.max(0, Math.round((Date.now() / 1000 - ts) / 86_400))
        : null;
    return {
      lastUploadDays,
      thumbnail: pickAvatar(parsed.thumbnails),
      banner: pickBanner(parsed.thumbnails),
      handle: parsed.uploader_id ?? null,
    };
  } catch {
    return empty;
  }
}
