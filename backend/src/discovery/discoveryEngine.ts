import type Database from "better-sqlite3";
import type { CategoryWeights } from "./types.js";

// Local AI Discovery Engine
//
// Scores channels the user is NOT following based on:
//   1. Category match    — does the channel publish in topics the user likes?
//   2. Similarity        — how close is its category mix to followed channels?
//   3. Quality           — high educational value, low clickbait, healthy size
//   4. Long-form bias    — Hikari's anti-doom-scroll thesis: prefer >X min videos
//
// Pure local logic. No external API calls. All aggregates are computed from
// the local SQLite store. Caller passes in user preferences explicitly so the
// scoring stays testable in isolation from the filter_config table.

export interface UserPreferences {
  /** User's per-category preference (each value 0..1). Sourced from
   *  discovery_settings.category_weights. Drives the category-match axis. */
  categoryWeights: CategoryWeights;
  /** Aggregated category distribution across followed channels — used as the
   *  similarity reference vector. Values are counts; normalised internally. */
  followedCategoryProfile: Record<string, number>;
  /** Channels to exclude from the candidate set (typically `is_active=1` ids). */
  followedChannelIds: string[];
  /** Minimum total score (0–100) a candidate must reach to be returned. */
  qualityThreshold: number;
  /** Videos at or above this duration count as "long-form" (Doku/Lernen). */
  longFormMinSeconds: number;
  /** Per-axis score weights. Optional — falls back to SCORE_WEIGHTS. Must sum to 1. */
  weights?: ScoreWeights;
}

export interface ScoreWeights {
  category: number;
  similarity: number;
  quality: number;
  longForm: number;
}

export interface ChannelCandidate {
  id: string;
  title: string;
  thumbnailUrl: string | null;
  bannerUrl: string | null;
  subscribers: number | null;
  videoCount: number;
  /** Share of this channel's videos with duration >= longFormMinSeconds. 0..1 */
  longFormRatio: number;
  /** Average overall_score of this channel's scored videos. 0..100 */
  avgOverallScore: number;
  /** Average educational_value of scored videos. 0..100 */
  avgEducationalValue: number;
  /** Average clickbait_risk of scored videos. 0..100 (lower is better) */
  avgClickbaitRisk: number;
  /** Per-category video counts for this channel. */
  categoryDistribution: Record<string, number>;
}

export interface ScoredCandidate extends ChannelCandidate {
  score: number;          // final 0..100
  breakdown: {
    category: number;     // 0..1
    similarity: number;   // 0..1
    quality: number;      // 0..1
    longForm: number;     // 0..1
  };
}

// ─── Tunables ──────────────────────────────────────────────────────────────
//
// TODO(rem): set weight distribution. Must sum to 1.0.
//
// Why this matters: weights encode Hikari's product thesis. Heavy `quality`
// weight = strict gatekeeper, fewer but better candidates. Heavy `similarity`
// weight = "more of what you already follow" (echo-chamber risk). Heavy
// `category` weight = topic-driven discovery (broader, less personal).
// `longForm` is the anti-doom-scroll signal — at 0 it's ignored, at 0.3+ it
// dominates and short-form channels rarely surface.
//
// Good starting point given the v1.0 design (positive curation > engagement):
//   category 0.30, similarity 0.20, quality 0.35, longForm 0.15
// But pick what feels right for Hikari.
export const SCORE_WEIGHTS: ScoreWeights = {
  category: 0.30,
  similarity: 0.20,
  quality: 0.35,
  longForm: 0.15,
} as const;

/** Soft cap for subscriber-based quality signal — beyond this, more subs
 *  doesn't add credit (so a 500k channel doesn't crush a high-quality 50k one). */
const SUBSCRIBER_QUALITY_CAP = 100_000;

// ─── Similarity ────────────────────────────────────────────────────────────
//
// TODO(rem): implement the similarity score between a candidate's category
// distribution and the user's followedCategoryProfile.
//
// Returns a value in [0, 1]. Both inputs are unnormalised category->count maps;
// you'll want to normalise into proportion vectors before comparing.
//
// Approaches to consider:
//   (a) Cosine similarity over the two distribution vectors — captures shape,
//       insensitive to volume. Standard for recommender baselines.
//   (b) Jaccard on the sets of categories present — coarser, ignores weights,
//       but very intuitive ("we share 2 of 3 topics").
//   (c) 1 - L1 distance / 2 over normalised vectors — penalises mismatch in
//       proportion (a channel that's 90% science vs your 50/50 science+philo
//       scores lower than a 50/50 candidate would).
//
// Edge cases: if either side is empty, return 0 (not 1 — no overlap).
function calculateSimilarity(
  candidate: Record<string, number>,
  followedProfile: Record<string, number>,
): number {
  const keys = new Set([...Object.keys(candidate), ...Object.keys(followedProfile)]);
  let dot = 0, normA = 0, normB = 0;
  for (const k of keys) {
    const a = candidate[k] ?? 0;
    const b = followedProfile[k] ?? 0;
    dot += a * b;
    normA += a * a;
    normB += b * b;
  }
  if (normA === 0 || normB === 0) return 0;
  return dot / (Math.sqrt(normA) * Math.sqrt(normB));
}

// ─── Public API ────────────────────────────────────────────────────────────

/**
 * Score one channel against a user's preferences. Pure function — no DB I/O.
 * Returns a ScoredCandidate with a 0..100 score and a per-axis breakdown
 * (useful for debugging and for surfacing "why" to the UI).
 */
export function calculateChannelScore(
  channel: ChannelCandidate,
  prefs: UserPreferences,
): ScoredCandidate {
  const totalVideos = Math.max(1, channel.videoCount);

  // 1. Category match — weighted dot product between the channel's category
  //    distribution (normalised to share-of-videos) and the user's per-category
  //    preference vector (already 0..1). High value when the channel publishes
  //    heavily in categories the user weighted high.
  let category = 0;
  for (const [cat, count] of Object.entries(channel.categoryDistribution)) {
    const userWeight = prefs.categoryWeights[cat as keyof CategoryWeights] ?? 0;
    category += userWeight * (count / totalVideos);
  }
  category = clamp01(category);

  // 2. Similarity — distribution overlap with followed-channel profile.
  const similarity = clamp01(
    calculateSimilarity(channel.categoryDistribution, prefs.followedCategoryProfile),
  );

  // 3. Quality — composite of overall score, educational value (positive),
  //    inverse clickbait risk, and a soft-capped subscriber signal.
  const subSignal = channel.subscribers
    ? Math.min(1, Math.log10(channel.subscribers + 1) / Math.log10(SUBSCRIBER_QUALITY_CAP + 1))
    : 0;
  const quality = clamp01(
    0.45 * (channel.avgOverallScore / 100) +
      0.30 * (channel.avgEducationalValue / 100) +
      0.15 * (1 - channel.avgClickbaitRisk / 100) +
      0.10 * subSignal,
  );

  // 4. Long-form — Hikari's anti-doom-scroll signal. Channel scoring uses the
  //    pre-aggregated longFormRatio (computed against prefs.longFormMinSeconds
  //    when candidates were loaded from the DB).
  const longForm = clamp01(channel.longFormRatio);

  const w = prefs.weights ?? SCORE_WEIGHTS;
  const score = 100 * (
    w.category   * category +
    w.similarity * similarity +
    w.quality    * quality +
    w.longForm   * longForm
  );

  return {
    ...channel,
    score: Math.round(score * 10) / 10,
    breakdown: { category, similarity, quality, longForm },
  };
}

/**
 * Discovery pipeline:
 *   1. Pull candidate channels from DB (those NOT in prefs.followedChannelIds).
 *   2. Score each.
 *   3. Drop anything below prefs.qualityThreshold.
 *   4. Sort desc by score, take top `limit`.
 *
 * One synchronous SQL roundtrip for channel aggregates, one for category
 * distributions — both indexed via existing schema indexes.
 */
export function getDiscoveryCandidates(
  db: Database.Database,
  prefs: UserPreferences,
  limit: number,
): ScoredCandidate[] {
  validateWeights(prefs.weights ?? SCORE_WEIGHTS);
  const candidates = loadCandidates(db, prefs);
  const scored = candidates
    .map((c) => calculateChannelScore(c, prefs))
    .filter((c) => c.score >= prefs.qualityThreshold)
    .sort((a, b) => b.score - a.score);
  return scored.slice(0, Math.max(0, limit));
}

// ─── Internals ─────────────────────────────────────────────────────────────

interface AggregateRow {
  id: string;
  title: string;
  thumbnailUrl: string | null;
  bannerUrl: string | null;
  subscribers: number | null;
  videoCount: number;
  longFormCount: number;
  avgOverallScore: number | null;
  avgEducationalValue: number | null;
  avgClickbaitRisk: number | null;
}

interface CategoryRow {
  channel_id: string;
  category: string;
  cnt: number;
}

function loadCandidates(
  db: Database.Database,
  prefs: UserPreferences,
): ChannelCandidate[] {
  // SQLite doesn't bind list parameters — build placeholders inline. The ids
  // come from the followed-channels list (server-side), so no injection risk,
  // but we still bind via prepared params.
  const placeholders =
    prefs.followedChannelIds.length === 0
      ? "''" // never matches; keeps SQL syntactically valid for empty list
      : prefs.followedChannelIds.map(() => "?").join(",");

  const aggregates = db
    .prepare(
      `SELECT
         c.id                                          AS id,
         c.title                                       AS title,
         c.thumbnail_url                               AS thumbnailUrl,
         c.banner_url                                  AS bannerUrl,
         c.subscribers                                 AS subscribers,
         COUNT(v.id)                                   AS videoCount,
         SUM(CASE WHEN v.duration_seconds >= ? THEN 1 ELSE 0 END) AS longFormCount,
         AVG(s.overall_score)                          AS avgOverallScore,
         AVG(s.educational_value)                      AS avgEducationalValue,
         AVG(s.clickbait_risk)                         AS avgClickbaitRisk
       FROM channels c
       LEFT JOIN videos v ON v.channel_id = c.id
       LEFT JOIN scores s ON s.video_id   = v.id
       WHERE c.id NOT IN (${placeholders})
       GROUP BY c.id
       HAVING videoCount > 0`,
    )
    .all(prefs.longFormMinSeconds, ...prefs.followedChannelIds) as AggregateRow[];

  if (aggregates.length === 0) return [];

  const idList = aggregates.map((a) => a.id);
  const idPlaceholders = idList.map(() => "?").join(",");
  const catRows = db
    .prepare(
      `SELECT v.channel_id AS channel_id, s.category AS category, COUNT(*) AS cnt
       FROM videos v JOIN scores s ON s.video_id = v.id
       WHERE v.channel_id IN (${idPlaceholders})
       GROUP BY v.channel_id, s.category`,
    )
    .all(...idList) as CategoryRow[];

  const distByChannel = new Map<string, Record<string, number>>();
  for (const row of catRows) {
    const m = distByChannel.get(row.channel_id) ?? {};
    m[row.category] = row.cnt;
    distByChannel.set(row.channel_id, m);
  }

  return aggregates.map((a) => ({
    id: a.id,
    title: a.title,
    thumbnailUrl: a.thumbnailUrl,
    bannerUrl: a.bannerUrl,
    subscribers: a.subscribers,
    videoCount: a.videoCount,
    longFormRatio: a.videoCount > 0 ? a.longFormCount / a.videoCount : 0,
    avgOverallScore: a.avgOverallScore ?? 0,
    avgEducationalValue: a.avgEducationalValue ?? 0,
    avgClickbaitRisk: a.avgClickbaitRisk ?? 0,
    categoryDistribution: distByChannel.get(a.id) ?? {},
  }));
}

function clamp01(n: number): number {
  if (Number.isNaN(n)) return 0;
  if (n < 0) return 0;
  if (n > 1) return 1;
  return n;
}

function validateWeights(w: ScoreWeights): void {
  const sum = w.category + w.similarity + w.quality + w.longForm;
  if (Math.abs(sum - 1) > 0.001) {
    throw new Error(
      `discoveryEngine: weights must sum to 1.0, got ${sum}.`,
    );
  }
  for (const [k, v] of Object.entries(w)) {
    if (!Number.isFinite(v) || v < 0) {
      throw new Error(`discoveryEngine: weight ${k}=${v} must be a non-negative finite number.`);
    }
  }
}

// Exported for tests only.
export const __test__ = { calculateSimilarity, loadCandidates, clamp01 };
