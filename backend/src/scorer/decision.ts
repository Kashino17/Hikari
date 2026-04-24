import type { Score } from "./types.js";

export type Decision = "approved" | "rejected";

export const DEFAULT_THRESHOLDS = {
  minOverall: 60,
  maxClickbait: 4,
  maxManipulation: 3,
} as const;

export function decide(score: Score, thresholds = DEFAULT_THRESHOLDS): Decision {
  if (score.overallScore < thresholds.minOverall) return "rejected";
  if (score.clickbaitRisk > thresholds.maxClickbait) return "rejected";
  if (score.emotionalManipulation > thresholds.maxManipulation) return "rejected";
  return "approved";
}
