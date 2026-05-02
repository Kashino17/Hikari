import { describe, expect, it } from "vitest";
import { pickFrameCount, pickTimestamps } from "./frame-sampler.js";

describe("pickFrameCount", () => {
  it("clamps to MIN 6 for short videos", () => {
    expect(pickFrameCount(60)).toBe(6);
    expect(pickFrameCount(180)).toBe(6);
  });
  it("scales linearly in middle range", () => {
    expect(pickFrameCount(600)).toBe(10);   // 10 min → 10 frames
    expect(pickFrameCount(900)).toBe(15);   // 15 min → 15 frames
  });
  it("clamps to MAX 16 for long videos", () => {
    expect(pickFrameCount(1800)).toBe(16);
    expect(pickFrameCount(7200)).toBe(16);
  });
});

describe("pickTimestamps", () => {
  it("returns N evenly-spaced points in (0, duration)", () => {
    expect(pickTimestamps(600, 6)).toEqual([86, 171, 257, 343, 429, 514]);
    // Note: rounded to integers
  });
  it("never includes 0 or duration as a timestamp", () => {
    const ts = pickTimestamps(60, 6);
    expect(ts[0]).toBeGreaterThan(0);
    expect(ts[ts.length - 1]).toBeLessThan(60);
  });
  it("returns ascending order", () => {
    const ts = pickTimestamps(900, 12);
    for (let i = 1; i < ts.length; i++) expect(ts[i]).toBeGreaterThan(ts[i-1]!);
  });
});
