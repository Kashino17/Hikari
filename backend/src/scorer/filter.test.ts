import { describe, expect, it } from "vitest";
import { DEFAULT_FILTER, buildPrompt, validateFilter } from "./filter.js";

describe("buildPrompt", () => {
  it("includes the user's like-tags as a Themen line", () => {
    const out = buildPrompt({ ...DEFAULT_FILTER, likeTags: ["Mathe", "Physik"] });
    expect(out).toMatch(/Themen: Mathe, Physik/);
  });

  it("renders dislike tags as bullet list", () => {
    const out = buildPrompt({ ...DEFAULT_FILTER, dislikeTags: ["Drama", "Reaction"] });
    expect(out).toMatch(/– Drama/);
    expect(out).toMatch(/– Reaction/);
  });

  it("renders 'no special exclusions' when dislike list is empty", () => {
    const out = buildPrompt({ ...DEFAULT_FILTER, dislikeTags: [] });
    expect(out).toMatch(/keine speziellen Ausschlüsse/);
  });

  it("uppercases language codes in hard-filter section", () => {
    const out = buildPrompt({ ...DEFAULT_FILTER, languages: ["de", "en"] });
    expect(out).toMatch(/Sprachen: DE, EN/);
  });

  it("formats duration window in minutes", () => {
    const out = buildPrompt({
      ...DEFAULT_FILTER,
      minDurationSec: 300,
      maxDurationSec: 1200,
    });
    expect(out).toMatch(/Dauer: 5min–20min/);
  });

  it("includes examples block only when non-empty", () => {
    const without = buildPrompt({ ...DEFAULT_FILTER, examples: "" });
    const withEx = buildPrompt({ ...DEFAULT_FILTER, examples: "3Blue1Brown rocks" });
    expect(without).not.toMatch(/Beispiele/);
    expect(withEx).toMatch(/3Blue1Brown rocks/);
  });

  it("always emits the strict-JSON output instruction", () => {
    const out = buildPrompt(DEFAULT_FILTER);
    expect(out).toMatch(/Strikt JSON/);
    expect(out).toMatch(/category/);
  });
});

describe("validateFilter", () => {
  it("accepts a well-formed FilterConfig", () => {
    expect(validateFilter(DEFAULT_FILTER)).toEqual(DEFAULT_FILTER);
  });

  it("rejects missing fields", () => {
    expect(validateFilter({ likeTags: [] })).toBeNull();
  });

  it("rejects non-string array members", () => {
    expect(
      validateFilter({ ...DEFAULT_FILTER, likeTags: [1, 2] }),
    ).toBeNull();
  });

  it("rejects negative durations", () => {
    expect(
      validateFilter({ ...DEFAULT_FILTER, minDurationSec: -10 }),
    ).toBeNull();
  });

  it("rejects max < min duration", () => {
    expect(
      validateFilter({ ...DEFAULT_FILTER, minDurationSec: 600, maxDurationSec: 300 }),
    ).toBeNull();
  });

  it("rejects scoreThreshold out of 0–100 range", () => {
    expect(validateFilter({ ...DEFAULT_FILTER, scoreThreshold: 150 })).toBeNull();
    expect(validateFilter({ ...DEFAULT_FILTER, scoreThreshold: -5 })).toBeNull();
  });

  it("rejects non-object input", () => {
    expect(validateFilter(null)).toBeNull();
    expect(validateFilter("bad")).toBeNull();
    expect(validateFilter(42)).toBeNull();
  });
});
