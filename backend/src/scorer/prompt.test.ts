import { describe, expect, it } from "vitest";
import { SCORING_SYSTEM_PROMPT } from "./prompt.js";

describe("SCORING_SYSTEM_PROMPT", () => {
  it("is a non-empty string", () => {
    expect(SCORING_SYSTEM_PROMPT).toBeTypeOf("string");
    expect(SCORING_SYSTEM_PROMPT.length).toBeGreaterThan(100);
  });

  it("mentions key concepts Kadir cares about", () => {
    expect(SCORING_SYSTEM_PROMPT.toLowerCase()).toMatch(/clickbait|sensationalis/);
    expect(SCORING_SYSTEM_PROMPT.toLowerCase()).toMatch(/educational|lehrreich|learn/);
  });
});
