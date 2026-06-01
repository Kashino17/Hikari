import type { Score } from "./types.js";

export type Decision = "approved" | "rejected";

export interface Thresholds {
  minOverall: number;
  maxClickbait: number;
  maxManipulation: number;
}

export const DEFAULT_THRESHOLDS: Thresholds = {
  minOverall: 60,
  maxClickbait: 4,
  maxManipulation: 3,
};

export function decide(score: Score, thresholds: Thresholds = DEFAULT_THRESHOLDS): Decision {
  // Safety net: a non-finite axis (NaN/±Infinity from a malformed payload) makes
  // every < / > comparison below false, which would silently APPROVE. Reject
  // defensively — scorers also validate upstream (score-schema).
  if (
    !Number.isFinite(score.overallScore) ||
    !Number.isFinite(score.clickbaitRisk) ||
    !Number.isFinite(score.emotionalManipulation)
  ) {
    return "rejected";
  }
  if (score.overallScore < thresholds.minOverall) return "rejected";
  if (score.clickbaitRisk > thresholds.maxClickbait) return "rejected";
  if (score.emotionalManipulation > thresholds.maxManipulation) return "rejected";
  return "approved";
}
