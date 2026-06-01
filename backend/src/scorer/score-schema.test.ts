import { describe, expect, it } from "vitest";
import { parseScore, validateScore } from "./score-schema.js";

const valid = {
  overallScore: 80,
  category: "science",
  clickbaitRisk: 2,
  educationalValue: 8,
  emotionalManipulation: 1,
  reasoning: "Solid, calm explainer.",
};

describe("parseScore", () => {
  it("parses a valid JSON score", () => {
    const s = parseScore(JSON.stringify(valid));
    expect(s.overallScore).toBe(80);
    expect(s.category).toBe("science");
  });

  it("throws on non-JSON content", () => {
    expect(() => parseScore("not json at all")).toThrow(/non-JSON/);
  });

  it("throws on a missing field", () => {
    const { reasoning, ...rest } = valid;
    expect(() => parseScore(JSON.stringify(rest))).toThrow(/invalid score/);
  });

  it("throws on an unknown category", () => {
    expect(() => parseScore(JSON.stringify({ ...valid, category: "gaming" }))).toThrow();
  });

  it("rejects out-of-range numbers", () => {
    expect(() => parseScore(JSON.stringify({ ...valid, overallScore: 250 }))).toThrow();
    expect(() => parseScore(JSON.stringify({ ...valid, clickbaitRisk: -3 }))).toThrow();
  });

  it("rejects non-finite numbers encoded as strings", () => {
    // JSON has no NaN literal; models sometimes emit "NaN" as a string.
    expect(() => parseScore(JSON.stringify({ ...valid, overallScore: "NaN" }))).toThrow();
  });

  it("coerces numeric strings the model sometimes emits", () => {
    const s = parseScore(JSON.stringify({ ...valid, overallScore: "80", clickbaitRisk: "2" }));
    expect(s.overallScore).toBe(80);
    expect(s.clickbaitRisk).toBe(2);
  });
});

describe("validateScore", () => {
  it("accepts a structured score object (e.g. Claude tool_use)", () => {
    expect(validateScore(valid).overallScore).toBe(80);
  });

  it("throws on a malformed object", () => {
    expect(() => validateScore({ overallScore: 80 })).toThrow(/invalid score object/);
  });
});
