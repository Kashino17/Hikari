import type { Score } from "./types.js";

export type Decision = "approved" | "rejected";

export const DEFAULT_THRESHOLDS = {
  minOverall: 60,
  maxClickbait: 4,
  maxManipulation: 3,
} as const;

export function decide(score: Score, thresholds = DEFAULT_THRESHOLDS): Decision {
  // Safety net: a non-finite axis (NaN/±Infinity from a malformed LLM payload)
  // makes every `<`/`>` comparison below false, which would silently APPROVE.
  // Reject defensively. Scorers validate upstream (score-schema), so this only
  // fires if validation is bypassed.
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
