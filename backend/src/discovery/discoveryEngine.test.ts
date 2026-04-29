import Database from "better-sqlite3";
import { beforeEach, describe, expect, it } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import {
  calculateChannelScore,
  getDiscoveryCandidates,
  type ChannelCandidate,
  type ScoreWeights,
  type UserPreferences,
} from "./discoveryEngine.js";
import type { CategoryWeights } from "./types.js";

// ─── Fixtures ──────────────────────────────────────────────────────────────

const ZERO_WEIGHTS: CategoryWeights = {
  science: 0, tech: 0, philosophy: 0, history: 0, math: 0,
  art: 0, language: 0, society: 0, other: 0,
};

// Per-axis isolation weights — pin one axis to 1, zero the rest. Final score
// then equals 100 × that single axis breakdown, which makes assertions exact.
const ONLY_CATEGORY:   ScoreWeights = { category: 1, similarity: 0, quality: 0, longForm: 0 };
const ONLY_SIMILARITY: ScoreWeights = { category: 0, similarity: 1, quality: 0, longForm: 0 };
const ONLY_QUALITY:    ScoreWeights = { category: 0, similarity: 0, quality: 1, longForm: 0 };

function makeChannel(overrides: Partial<ChannelCandidate> = {}): ChannelCandidate {
  return {
    id: "UC_test",
    title: "Test",
    thumbnailUrl: null,
    bannerUrl: null,
    subscribers: 0,
    videoCount: 10,
    longFormRatio: 0,
    avgOverallScore: 0,
    avgEducationalValue: 0,
    avgClickbaitRisk: 0,
    categoryDistribution: {},
    ...overrides,
  };
}

function makePrefs(overrides: Partial<UserPreferences> = {}): UserPreferences {
  return {
    categoryWeights: ZERO_WEIGHTS,
    followedCategoryProfile: {},
    followedChannelIds: [],
    qualityThreshold: 0,
    longFormMinSeconds: 600,
    ...overrides,
  };
}

// ─── 1. Score-axis validation ──────────────────────────────────────────────

describe("calculateChannelScore — Category axis", () => {
  it("computes the weighted dot product of channel distribution × user weights", () => {
    const channel = makeChannel({
      videoCount: 10,
      categoryDistribution: { math: 8, philosophy: 2 },
    });
    const prefs = makePrefs({
      categoryWeights: { ...ZERO_WEIGHTS, math: 1.0, philosophy: 0.5 },
      weights: ONLY_CATEGORY,
    });
    const out = calculateChannelScore(channel, prefs);
    // 1.0 × (8/10) + 0.5 × (2/10) = 0.9
    expect(out.breakdown.category).toBeCloseTo(0.9, 5);
    expect(out.score).toBeCloseTo(90, 3);
  });

  it("is zero when channel categories don't overlap user-weighted ones", () => {
    const out = calculateChannelScore(
      makeChannel({ videoCount: 5, categoryDistribution: { art: 5 } }),
      makePrefs({
        categoryWeights: { ...ZERO_WEIGHTS, math: 1.0 },
        weights: ONLY_CATEGORY,
      }),
    );
    expect(out.breakdown.category).toBe(0);
  });

  it("clamps the axis at 1 even if a malformed channel overshoots", () => {
    // Defensive: counts/totalVideos should always normalise to ≤ 1, but the
    // clamp is the safety net if upstream ever feeds a broken aggregate.
    const out = calculateChannelScore(
      makeChannel({ videoCount: 1, categoryDistribution: { math: 10 } }),
      makePrefs({
        categoryWeights: { ...ZERO_WEIGHTS, math: 1.0 },
        weights: ONLY_CATEGORY,
      }),
    );
    expect(out.breakdown.category).toBe(1);
  });
});

describe("calculateChannelScore — Similarity axis (Cosine)", () => {
  it("returns 1.0 for identical category distributions", () => {
    const dist = { math: 5, philosophy: 3 };
    const out = calculateChannelScore(
      makeChannel({ videoCount: 8, categoryDistribution: dist }),
      makePrefs({ followedCategoryProfile: { ...dist }, weights: ONLY_SIMILARITY }),
    );
    expect(out.breakdown.similarity).toBeCloseTo(1, 5);
  });

  it("returns 0 for fully disjoint category distributions", () => {
    const out = calculateChannelScore(
      makeChannel({ videoCount: 5, categoryDistribution: { art: 5 } }),
      makePrefs({ followedCategoryProfile: { math: 5 }, weights: ONLY_SIMILARITY }),
    );
    expect(out.breakdown.similarity).toBe(0);
  });

  it("is volume-invariant — same shape, different counts → 1.0", () => {
    const out = calculateChannelScore(
      makeChannel({ videoCount: 10, categoryDistribution: { math: 10 } }),
      makePrefs({ followedCategoryProfile: { math: 100 }, weights: ONLY_SIMILARITY }),
    );
    expect(out.breakdown.similarity).toBeCloseTo(1, 5);
  });
});

describe("calculateChannelScore — Quality axis", () => {
  it("composes overall, educational, inverse-clickbait, and subscriber signal", () => {
    const channel = makeChannel({
      videoCount: 5,
      avgOverallScore: 80,
      avgEducationalValue: 90,
      avgClickbaitRisk: 10,
      subscribers: 10_000,
    });
    const out = calculateChannelScore(channel, makePrefs({ weights: ONLY_QUALITY }));
    // 0.45·0.8 + 0.30·0.9 + 0.15·(1-0.1) + 0.10·subSignal(10k) ≈ 0.845
    // subSignal = log10(10001)/log10(100001) ≈ 0.8000
    expect(out.breakdown.quality).toBeCloseTo(0.845, 2);
  });

  it("ignores subscriber signal when subscribers is null", () => {
    const out = calculateChannelScore(
      makeChannel({
        videoCount: 5,
        avgOverallScore: 100,
        avgEducationalValue: 100,
        avgClickbaitRisk: 0,
        subscribers: null,
      }),
      makePrefs({ weights: ONLY_QUALITY }),
    );
    // 0.45 + 0.30 + 0.15 + 0.10·0 = 0.90
    expect(out.breakdown.quality).toBeCloseTo(0.9, 5);
  });
});

// ─── 2. Edge case: empty category map ──────────────────────────────────────

describe("calculateChannelScore — empty category distribution", () => {
  it("scores 0 on category and similarity but still computes quality + longForm", () => {
    const channel = makeChannel({
      videoCount: 5,
      categoryDistribution: {}, // e.g. videos exist but none scored yet
      longFormRatio: 0.6,
      avgOverallScore: 50,
      avgEducationalValue: 50,
      avgClickbaitRisk: 50,
    });
    const prefs = makePrefs({
      // User has preferences set — should still be ignored, no overlap possible.
      categoryWeights: { ...ZERO_WEIGHTS, math: 1.0 },
      followedCategoryProfile: { math: 10 },
      // default SCORE_WEIGHTS via prefs.weights = undefined.
    });
    const out = calculateChannelScore(channel, prefs);

    expect(out.breakdown.category).toBe(0);
    expect(out.breakdown.similarity).toBe(0);
    expect(out.breakdown.longForm).toBeCloseTo(0.6, 5);
    expect(out.breakdown.quality).toBeGreaterThan(0);

    // Final score is purely the quality and longForm contributions
    // under the default 0.30/0.20/0.35/0.15 mix. Engine rounds to 1 decimal,
    // so allow a 0.1 tolerance window.
    const expected = 100 * (0.35 * out.breakdown.quality + 0.15 * 0.6);
    expect(Math.abs(out.score - expected)).toBeLessThanOrEqual(0.1);
  });
});

// ─── 3. Sort-order + filtering via the DB pipeline ─────────────────────────

interface SeedVideo {
  id: string;
  durationSeconds: number;
  category: string;
  overall: number;
  clickbait: number;
  eduValue: number;
}

function seedChannel(
  db: Database.Database,
  opts: {
    id: string;
    title: string;
    subscribers?: number | null;
    videos: SeedVideo[];
  },
): void {
  db.prepare(
    `INSERT INTO channels (id, url, title, added_at, is_active, subscribers)
     VALUES (?, ?, ?, ?, 1, ?)`,
  ).run(
    opts.id,
    `https://yt.example/${opts.id}`,
    opts.title,
    Date.now(),
    opts.subscribers ?? null,
  );
  const insertVideo = db.prepare(
    `INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at)
     VALUES (?, ?, ?, ?, ?, ?)`,
  );
  const insertScore = db.prepare(
    `INSERT INTO scores (video_id, overall_score, category, clickbait_risk,
                         educational_value, emotional_manipulation, reasoning,
                         model_used, scored_at, decision)
     VALUES (?, ?, ?, ?, ?, 0, 'test', 'test', ?, 'approved')`,
  );
  for (const v of opts.videos) {
    insertVideo.run(v.id, opts.id, v.id, Date.now(), v.durationSeconds, Date.now());
    insertScore.run(v.id, v.overall, v.category, v.clickbait, v.eduValue, Date.now());
  }
}

describe("getDiscoveryCandidates — sort and filter", () => {
  let db: Database.Database;

  beforeEach(() => {
    db = new Database(":memory:");
    applyMigrations(db);
  });

  it("returns candidates sorted by score descending", () => {
    seedChannel(db, {
      id: "A", title: "high-quality math",
      subscribers: 50_000,
      videos: [1, 2, 3].map((i) => ({
        id: `vA${i}`, durationSeconds: 1200, category: "math",
        overall: 90, clickbait: 5, eduValue: 95,
      })),
    });
    seedChannel(db, {
      id: "B", title: "mid",
      subscribers: 5_000,
      videos: [1, 2].map((i) => ({
        id: `vB${i}`, durationSeconds: 700, category: "math",
        overall: 60, clickbait: 30, eduValue: 60,
      })),
    });
    seedChannel(db, {
      id: "C", title: "low",
      subscribers: 100,
      videos: [{
        id: "vC1", durationSeconds: 90, category: "art",
        overall: 30, clickbait: 70, eduValue: 20,
      }],
    });

    const prefs = makePrefs({
      categoryWeights: { ...ZERO_WEIGHTS, math: 1.0 },
      followedCategoryProfile: { math: 10 },
      qualityThreshold: 0,
      longFormMinSeconds: 600,
    });

    const out = getDiscoveryCandidates(db, prefs, 10);
    expect(out.map((r) => r.id)).toEqual(["A", "B", "C"]);
    expect(out[0].score).toBeGreaterThan(out[1].score);
    expect(out[1].score).toBeGreaterThan(out[2].score);
  });

  it("drops candidates below qualityThreshold", () => {
    seedChannel(db, {
      id: "good", title: "good",
      subscribers: 50_000,
      videos: [{
        id: "vg", durationSeconds: 1200, category: "math",
        overall: 90, clickbait: 5, eduValue: 95,
      }],
    });
    seedChannel(db, {
      id: "bad", title: "bad",
      subscribers: 100,
      videos: [{
        id: "vb", durationSeconds: 60, category: "art",
        overall: 20, clickbait: 80, eduValue: 10,
      }],
    });

    const prefs = makePrefs({
      categoryWeights: { ...ZERO_WEIGHTS, math: 1.0 },
      qualityThreshold: 50,
      longFormMinSeconds: 600,
    });

    const out = getDiscoveryCandidates(db, prefs, 10);
    expect(out.map((r) => r.id)).toEqual(["good"]);
  });

  it("excludes followed channel ids from the candidate pool", () => {
    seedChannel(db, {
      id: "followed", title: "F",
      subscribers: 50_000,
      videos: [{
        id: "vf", durationSeconds: 1200, category: "math",
        overall: 100, clickbait: 0, eduValue: 100,
      }],
    });
    seedChannel(db, {
      id: "fresh", title: "X",
      subscribers: 5_000,
      videos: [{
        id: "vx", durationSeconds: 1200, category: "math",
        overall: 70, clickbait: 20, eduValue: 70,
      }],
    });

    const prefs = makePrefs({
      categoryWeights: { ...ZERO_WEIGHTS, math: 1.0 },
      followedChannelIds: ["followed"],
      qualityThreshold: 0,
    });
    const out = getDiscoveryCandidates(db, prefs, 10);
    expect(out.map((r) => r.id)).toEqual(["fresh"]);
  });

  it("respects the limit parameter", () => {
    for (const i of [1, 2, 3, 4]) {
      seedChannel(db, {
        id: `ch${i}`, title: `ch${i}`,
        subscribers: 10_000 * i,
        videos: [{
          id: `v${i}`, durationSeconds: 1200, category: "math",
          overall: 50 + 10 * i, clickbait: 20, eduValue: 60 + i,
        }],
      });
    }
    const prefs = makePrefs({
      categoryWeights: { ...ZERO_WEIGHTS, math: 1.0 },
      qualityThreshold: 0,
    });
    expect(getDiscoveryCandidates(db, prefs, 2)).toHaveLength(2);
  });
});
