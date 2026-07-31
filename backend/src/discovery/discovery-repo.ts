import type Database from "better-sqlite3";
import type { Category } from "../scorer/types.js";
import type {
  CategoryWeights,
  DiscoverySettings,
  DiscoverySettingsUpdate,
} from "./types.js";

const ALL_CATEGORIES: readonly Category[] = [
  "science",
  "tech",
  "philosophy",
  "history",
  "math",
  "art",
  "language",
  "society",
  "other",
];

// Anti-Doom-Scroll bias: 30% Discovery, 70% bewährt + hohe Quali-Hürde.
// Category-Weights starten neutral (0.5) — User tuned später im UI.
export const DEFAULT_DISCOVERY_RATIO = 0.3;
export const DEFAULT_QUALITY_THRESHOLD = 65;
export const DEFAULT_CATEGORY_WEIGHT = 0.5;

function buildDefaultWeights(): CategoryWeights {
  const out = {} as CategoryWeights;
  for (const c of ALL_CATEGORIES) out[c] = DEFAULT_CATEGORY_WEIGHT;
  return out;
}

interface SettingsRow {
  discovery_ratio: number;
  quality_threshold: number;
  category_weights: string;
  updated_at: number;
}

function isCategory(value: string): value is Category {
  return (ALL_CATEGORIES as readonly string[]).includes(value);
}

function isFiniteNumber(v: unknown): v is number {
  return typeof v === "number" && Number.isFinite(v);
}

export class DiscoveryValidationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "DiscoveryValidationError";
  }
}

function validateRatio(v: unknown): number {
  if (!isFiniteNumber(v) || v < 0 || v > 1) {
    throw new DiscoveryValidationError("discoveryRatio must be a number in [0, 1]");
  }
  return v;
}

function validateThreshold(v: unknown): number {
  if (!isFiniteNumber(v) || !Number.isInteger(v) || v < 0 || v > 100) {
    throw new DiscoveryValidationError(
      "qualityThreshold must be an integer in [0, 100]",
    );
  }
  return v;
}

/**
 * Validates a partial weights map: keys must be known categories, values
 * must be numbers in [0, 1]. Unknown keys are rejected so typos surface
 * immediately rather than silently disappearing into the JSON blob.
 */
function validateWeightsPartial(v: unknown): Partial<CategoryWeights> {
  if (typeof v !== "object" || v === null || Array.isArray(v)) {
    throw new DiscoveryValidationError("categoryWeights must be an object");
  }
  const out: Partial<CategoryWeights> = {};
  for (const [key, value] of Object.entries(v)) {
    if (!isCategory(key)) {
      throw new DiscoveryValidationError(`unknown category: ${key}`);
    }
    if (!isFiniteNumber(value) || value < 0 || value > 1) {
      throw new DiscoveryValidationError(
        `categoryWeights.${key} must be a number in [0, 1]`,
      );
    }
    out[key] = value;
  }
  return out;
}

function parseWeights(raw: string): CategoryWeights {
  const parsed = JSON.parse(raw) as Partial<Record<string, number>>;
  const merged = buildDefaultWeights();
  for (const c of ALL_CATEGORIES) {
    const v = parsed[c];
    if (isFiniteNumber(v)) merged[c] = v;
  }
  return merged;
}

function seedDefaults(db: Database.Database): DiscoverySettings {
  const now = Date.now();
  const weights = buildDefaultWeights();
  db.transaction(() => {
    db.prepare(
      `INSERT INTO discovery_settings
         (id, discovery_ratio, quality_threshold, category_weights, updated_at)
       VALUES (1, ?, ?, ?, ?)`,
    ).run(DEFAULT_DISCOVERY_RATIO, DEFAULT_QUALITY_THRESHOLD, JSON.stringify(weights), now);
    const upsertPref = db.prepare(
      `INSERT INTO category_preferences (category, weight, updated_at)
       VALUES (?, ?, ?)
       ON CONFLICT(category) DO UPDATE SET weight = excluded.weight,
                                            updated_at = excluded.updated_at`,
    );
    for (const c of ALL_CATEGORIES) upsertPref.run(c, weights[c], now);
  })();
  return {
    discoveryRatio: DEFAULT_DISCOVERY_RATIO,
    qualityThreshold: DEFAULT_QUALITY_THRESHOLD,
    categoryWeights: weights,
    updatedAt: now,
  };
}

/**
 * Reads the discovery settings row, seeding defaults on first access.
 * Mirrors the lazy-init pattern used in scorer/filter-repo.ts.
 */
export function getSettings(db: Database.Database): DiscoverySettings {
  const row = db
    .prepare(
      `SELECT discovery_ratio, quality_threshold, category_weights, updated_at
       FROM discovery_settings WHERE id = 1`,
    )
    .get() as SettingsRow | undefined;
  if (!row) return seedDefaults(db);
  return {
    discoveryRatio: row.discovery_ratio,
    qualityThreshold: row.quality_threshold,
    categoryWeights: parseWeights(row.category_weights),
    updatedAt: row.updated_at,
  };
}

/**
 * Partial update — any field omitted keeps its current value. Throws
 * DiscoveryValidationError on invalid input. category_preferences mirror
 * is updated atomically with the JSON column.
 */
export function updateSettings(
  db: Database.Database,
  patch: DiscoverySettingsUpdate,
): DiscoverySettings {
  const current = getSettings(db);

  const nextRatio =
    patch.discoveryRatio !== undefined
      ? validateRatio(patch.discoveryRatio)
      : current.discoveryRatio;

  const nextThreshold =
    patch.qualityThreshold !== undefined
      ? validateThreshold(patch.qualityThreshold)
      : current.qualityThreshold;

  const weightsPatch =
    patch.categoryWeights !== undefined
      ? validateWeightsPartial(patch.categoryWeights)
      : {};
  const nextWeights: CategoryWeights = { ...current.categoryWeights, ...weightsPatch };

  const now = Date.now();
  db.transaction(() => {
    db.prepare(
      `UPDATE discovery_settings
         SET discovery_ratio = ?, quality_threshold = ?,
             category_weights = ?, updated_at = ?
       WHERE id = 1`,
    ).run(nextRatio, nextThreshold, JSON.stringify(nextWeights), now);

    const upsertPref = db.prepare(
      `INSERT INTO category_preferences (category, weight, updated_at)
       VALUES (?, ?, ?)
       ON CONFLICT(category) DO UPDATE SET weight = excluded.weight,
                                            updated_at = excluded.updated_at`,
    );
    for (const [category, weight] of Object.entries(weightsPatch)) {
      upsertPref.run(category, weight, now);
    }
  })();

  return {
    discoveryRatio: nextRatio,
    qualityThreshold: nextThreshold,
    categoryWeights: nextWeights,
    updatedAt: now,
  };
}

interface ScoreAggregate {
  avg_score: number | null;
  count: number;
}

interface CategoryFrequencyRow {
  category: string;
  count: number;
}

// Quality term is 0–100 (overall_score); weight is 0–1, so we lift it onto
// the same scale via *100 before mixing.
const QUALITY_WEIGHT = 0.6;
const PREFERENCE_WEIGHT = 0.4;

/**
 * Recomputes the cached match-score for a channel using formula (b):
 *
 *   0.6 * avg(overall_score)              — quality signal, 0–100
 * + 0.4 * categoryWeights[dominant] * 100 — preference fit, 0–100
 *
 * `dominant` is the most-frequent category among the channel's scored
 * videos; ties are broken alphabetically for determinism. Returns `null`
 * (and writes nothing) when the channel has no scored videos yet — caller
 * should retry once scoring catches up.
 */
export function recalculateChannelScore(
  db: Database.Database,
  channelId: string,
): number | null {
  const agg = db
    .prepare(
      `SELECT AVG(s.overall_score) AS avg_score, COUNT(*) AS count
         FROM scores s
         JOIN videos v ON v.id = s.video_id
        WHERE v.channel_id = ?`,
    )
    .get(channelId) as ScoreAggregate;

  if (agg.count === 0 || agg.avg_score === null) return null;

  const dominant = db
    .prepare(
      `SELECT s.category AS category, COUNT(*) AS count
         FROM scores s
         JOIN videos v ON v.id = s.video_id
        WHERE v.channel_id = ?
        GROUP BY s.category
        ORDER BY count DESC, s.category ASC
        LIMIT 1`,
    )
    .get(channelId) as CategoryFrequencyRow | undefined;

  const settings = getSettings(db);
  const weight =
    dominant && isCategory(dominant.category)
      ? settings.categoryWeights[dominant.category]
      : DEFAULT_CATEGORY_WEIGHT;

  const finalScore =
    QUALITY_WEIGHT * agg.avg_score + PREFERENCE_WEIGHT * weight * 100;
  const now = Date.now();

  db.prepare(
    `INSERT INTO channel_match_scores (channel_id, calculated_score, last_updated)
     VALUES (?, ?, ?)
     ON CONFLICT(channel_id) DO UPDATE
       SET calculated_score = excluded.calculated_score,
           last_updated     = excluded.last_updated`,
  ).run(channelId, finalScore, now);

  return finalScore;
}
