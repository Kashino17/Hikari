import { describe, expect, it } from "vitest";
import { decide } from "./decision.js";
import type { Score } from "./types.js";

const base: Score = {
  overallScore: 70,
  category: "science",
  clickbaitRisk: 2,
  educationalValue: 8,
  emotionalManipulation: 1,
  reasoning: "",
};

describe("decide", () => {
  it("approves when all thresholds pass", () => {
    expect(decide(base)).toBe("approved");
  });

  it("rejects when overall_score < 60", () => {
    expect(decide({ ...base, overallScore: 59 })).toBe("rejected");
  });

  it("rejects when clickbait_risk > 4", () => {
    expect(decide({ ...base, clickbaitRisk: 5 })).toBe("rejected");
  });

  it("rejects when emotional_manipulation > 3", () => {
    expect(decide({ ...base, emotionalManipulation: 4 })).toBe("rejected");
  });
});
