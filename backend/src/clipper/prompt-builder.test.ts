import { describe, expect, it } from "vitest";
import { buildClipperPrompt } from "./prompt-builder.js";
import type { FilterConfig } from "../scorer/filter.js";

const SAMPLE_FILTER: FilterConfig = {
  likeTags: ["lehrreich", "math"],
  dislikeTags: ["clickbait"],
  moodTags: ["ruhig"],
  depthTags: ["tiefgründig"],
  languages: ["de", "en"],
  minDurationSec: 60,
  maxDurationSec: 3600,
  examples: "kurze prägnante Erklärungen",
  scoreThreshold: 60,
};

describe("buildClipperPrompt", () => {
  it("includes the operational rules verbatim", () => {
    const out = buildClipperPrompt(SAMPLE_FILTER, { aspectRatio: "16:9" });
    expect(out).toContain("zwischen 20s und 60s");
    expect(out).toContain("Toleranz bis 90s");
    expect(out).toContain("1 pro 5 Min Original-Dauer");
    expect(out).toContain("ausschließlich gültiges JSON-Array");
  });

  it("renders all FilterConfig fields", () => {
    const out = buildClipperPrompt(SAMPLE_FILTER, { aspectRatio: "16:9" });
    expect(out).toContain("lehrreich, math");
    expect(out).toContain("clickbait");
    expect(out).toContain("ruhig");
    expect(out).toContain("tiefgründig");
    expect(out).toContain("de, en");
    expect(out).toContain("kurze prägnante Erklärungen");
  });

  it("does not include any layout/crop instructions (deterministic fit-mode renderer)", () => {
    const out = buildClipperPrompt(SAMPLE_FILTER, { aspectRatio: "16:9" });
    expect(out).not.toContain("focus.x");
    expect(out).not.toContain("display_mode");
    expect(out).not.toContain("smart-crop");
    expect(out).toContain("start_sec");
    expect(out).toContain("reason");
  });

  it("handles empty FilterConfig fields gracefully", () => {
    const empty: FilterConfig = {
      likeTags: [], dislikeTags: [], moodTags: [], depthTags: [], languages: [],
      minDurationSec: 0, maxDurationSec: 0, examples: "", scoreThreshold: 0,
    };
    expect(() => buildClipperPrompt(empty, { aspectRatio: "16:9" })).not.toThrow();
    const out = buildClipperPrompt(empty, { aspectRatio: "16:9" });
    expect(out).toContain("Highlight-Analyst");
  });

  it("matches snapshot for stable filter input (regression-guard)", () => {
    const out = buildClipperPrompt(SAMPLE_FILTER, { aspectRatio: "16:9" });
    expect(out).toMatchSnapshot();
  });
});
