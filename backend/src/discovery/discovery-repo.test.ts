import Database from "better-sqlite3";
import { beforeEach, describe, expect, it } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import {
  DEFAULT_CATEGORY_WEIGHT,
  DEFAULT_DISCOVERY_RATIO,
  DEFAULT_QUALITY_THRESHOLD,
  DiscoveryValidationError,
  getSettings,
  recalculateChannelScore,
  updateSettings,
} from "./discovery-repo.js";

function makeDb(): Database.Database {
  const db = new Database(":memory:");
  db.pragma("foreign_keys = ON");
  applyMigrations(db);
  return db;
}

interface PrefRow {
  category: string;
  weight: number;
}

const ALL_CATEGORIES = [
  "science",
  "tech",
  "philosophy",
  "history",
  "math",
  "art",
  "language",
  "society",
  "other",
] as const;

describe("getSettings", () => {
  it("seeds defaults on first call (lazy-init)", () => {
    const db = makeDb();
    const s = getSettings(db);
    expect(s.discoveryRatio).toBe(DEFAULT_DISCOVERY_RATIO);
    expect(s.qualityThreshold).toBe(DEFAULT_QUALITY_THRESHOLD);
    for (const c of ALL_CATEGORIES) {
      expect(s.categoryWeights[c]).toBe(DEFAULT_CATEGORY_WEIGHT);
    }
  });

  it("seeds the category_preferences mirror with all 9 categories", () => {
    const db = makeDb();
    getSettings(db);
    const rows = db
      .prepare("SELECT category, weight FROM category_preferences ORDER BY category")
      .all() as PrefRow[];
    expect(rows).toHaveLength(9);
    for (const r of rows) {
      expect(r.weight).toBe(DEFAULT_CATEGORY_WEIGHT);
      expect(ALL_CATEGORIES).toContain(r.category);
    }
  });

  it("returns the same row on subsequent calls (no re-seed)", () => {
    const db = makeDb();
    const a = getSettings(db);
    const b = getSettings(db);
    expect(a.updatedAt).toBe(b.updatedAt);
    const count = db
      .prepare("SELECT COUNT(*) AS n FROM discovery_settings")
      .get() as { n: number };
    expect(count.n).toBe(1);
  });
});

describe("updateSettings — happy path", () => {
  let db: Database.Database;
  beforeEach(() => {
    db = makeDb();
  });

  it("partial update preserves untouched fields", () => {
    const before = getSettings(db);
    const after = updateSettings(db, { discoveryRatio: 0.7 });
    expect(after.discoveryRatio).toBe(0.7);
    expect(after.qualityThreshold).toBe(before.qualityThreshold);
    expect(after.categoryWeights).toEqual(before.categoryWeights);
  });

  it("persists to DB and bumps updated_at", () => {
    const before = getSettings(db);
    const after = updateSettings(db, { qualityThreshold: 80 });
    expect(after.updatedAt).toBeGreaterThanOrEqual(before.updatedAt);
    const reread = getSettings(db);
    expect(reread.qualityThreshold).toBe(80);
  });

  it("syncs category_preferences row when a weight changes", () => {
    updateSettings(db, { categoryWeights: { science: 0.9 } });
    const row = db
      .prepare("SELECT weight FROM category_preferences WHERE category = ?")
      .get("science") as { weight: number };
    expect(row.weight).toBe(0.9);
  });

  it("merges partial weights with current values", () => {
    updateSettings(db, { categoryWeights: { science: 0.9, math: 0.8 } });
    const after = updateSettings(db, { categoryWeights: { art: 0.4 } });
    expect(after.categoryWeights.science).toBe(0.9);
    expect(after.categoryWeights.math).toBe(0.8);
    expect(after.categoryWeights.art).toBe(0.4);
    expect(after.categoryWeights.tech).toBe(DEFAULT_CATEGORY_WEIGHT);
  });
});

describe("updateSettings — validation", () => {
  let db: Database.Database;
  beforeEach(() => {
    db = makeDb();
  });

  it("rejects discoveryRatio out of [0, 1]", () => {
    expect(() => updateSettings(db, { discoveryRatio: -0.1 })).toThrow(
      DiscoveryValidationError,
    );
    expect(() => updateSettings(db, { discoveryRatio: 1.5 })).toThrow(
      DiscoveryValidationError,
    );
  });

  it("rejects non-finite discoveryRatio", () => {
    expect(() => updateSettings(db, { discoveryRatio: Number.NaN })).toThrow(
      DiscoveryValidationError,
    );
  });

  it("rejects qualityThreshold non-integer", () => {
    expect(() => updateSettings(db, { qualityThreshold: 65.5 })).toThrow(
      DiscoveryValidationError,
    );
  });

  it("rejects qualityThreshold out of [0, 100]", () => {
    expect(() => updateSettings(db, { qualityThreshold: 150 })).toThrow(
      DiscoveryValidationError,
    );
    expect(() => updateSettings(db, { qualityThreshold: -1 })).toThrow(
      DiscoveryValidationError,
    );
  });

  it("rejects unknown category keys", () => {
    expect(() =>
      updateSettings(db, {
        categoryWeights: { music: 0.5 } as unknown as Record<string, number>,
      }),
    ).toThrow(DiscoveryValidationError);
  });

  it("rejects weight values out of [0, 1]", () => {
    expect(() => updateSettings(db, { categoryWeights: { science: 1.5 } })).toThrow(
      DiscoveryValidationError,
    );
    expect(() => updateSettings(db, { categoryWeights: { science: -0.1 } })).toThrow(
      DiscoveryValidationError,
    );
  });

  it("does not partially apply when a single weight is invalid (atomic)", () => {
    expect(() =>
      updateSettings(db, {
        categoryWeights: { science: 0.9, math: 2.0 },
      }),
    ).toThrow(DiscoveryValidationError);
    const after = getSettings(db);
    expect(after.categoryWeights.science).toBe(DEFAULT_CATEGORY_WEIGHT);
  });
});

describe("recalculateChannelScore", () => {
  let db: Database.Database;

  beforeEach(() => {
    db = makeDb();
    db.prepare(
      "INSERT INTO channels (id, url, title, added_at) VALUES (?, ?, ?, ?)",
    ).run("ch1", "https://yt/ch1", "Channel 1", Date.now());
  });

  function insertScoredVideo(
    videoId: string,
    channelId: string,
    overallScore: number,
    category: string,
  ): void {
    db.prepare(
      `INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at)
       VALUES (?, ?, ?, ?, ?, ?)`,
    ).run(videoId, channelId, `T-${videoId}`, Date.now(), 600, Date.now());
    db.prepare(
      `INSERT INTO scores
         (video_id, overall_score, category, clickbait_risk, educational_value,
          emotional_manipulation, reasoning, model_used, scored_at, decision)
       VALUES (?, ?, ?, 0, 80, 0, 'r', 'm', ?, 'keep')`,
    ).run(videoId, overallScore, category, Date.now());
  }

  it("returns null and writes nothing when channel has no scored videos", () => {
    expect(recalculateChannelScore(db, "ch1")).toBeNull();
    const row = db
      .prepare("SELECT 1 FROM channel_match_scores WHERE channel_id = ?")
      .get("ch1");
    expect(row).toBeUndefined();
  });

  it("computes 0.6 * avg(score) + 0.4 * weight*100 (formula b)", () => {
    insertScoredVideo("v1", "ch1", 80, "science");
    insertScoredVideo("v2", "ch1", 60, "science");
    // avg = 70, dominant = science, weight = 0.5 (default) → 0.6*70 + 0.4*50 = 62
    const result = recalculateChannelScore(db, "ch1");
    expect(result).toBeCloseTo(62, 5);
  });

  it("uses the dominant (most-frequent) category for the weight term", () => {
    insertScoredVideo("v1", "ch1", 50, "math");
    insertScoredVideo("v2", "ch1", 50, "math");
    insertScoredVideo("v3", "ch1", 50, "art");
    updateSettings(db, { categoryWeights: { math: 1.0, art: 0.0 } });
    // avg = 50, dominant = math (2x vs 1x), weight = 1.0 → 0.6*50 + 0.4*100 = 70
    expect(recalculateChannelScore(db, "ch1")).toBeCloseTo(70, 5);
  });

  it("UPSERTs — second call overwrites the cached score", () => {
    insertScoredVideo("v1", "ch1", 50, "science");
    const first = recalculateChannelScore(db, "ch1");
    insertScoredVideo("v2", "ch1", 100, "science");
    const second = recalculateChannelScore(db, "ch1");
    expect(second).not.toBe(first);
    const count = db
      .prepare("SELECT COUNT(*) AS n FROM channel_match_scores WHERE channel_id = ?")
      .get("ch1") as { n: number };
    expect(count.n).toBe(1);
  });

  it("writes the channel_id, calculated_score, last_updated row", () => {
    insertScoredVideo("v1", "ch1", 80, "tech");
    const score = recalculateChannelScore(db, "ch1");
    const row = db
      .prepare(
        "SELECT channel_id, calculated_score, last_updated FROM channel_match_scores WHERE channel_id = ?",
      )
      .get("ch1") as {
      channel_id: string;
      calculated_score: number;
      last_updated: number;
    };
    expect(row.channel_id).toBe("ch1");
    expect(row.calculated_score).toBeCloseTo(score ?? 0, 5);
    expect(row.last_updated).toBeGreaterThan(0);
  });
});
