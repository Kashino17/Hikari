import { describe, expect, it } from "vitest";
import { computeCropRect } from "./crop-math.js";

describe("computeCropRect", () => {
  it("portrait video (9:16) returns full frame regardless of focus", () => {
    const rect = computeCropRect({
      videoWidth: 1080, videoHeight: 1920,
      focus: { x: 0.3, y: 0.3, w: 0.4, h: 0.4 },
      targetAspect: 9 / 16,
    });
    expect(rect).toEqual({ x: 0, y: 0, w: 1080, h: 1920 });
  });

  it("landscape 16:9 with center-focus crops a 9:16 column around focus center", () => {
    const rect = computeCropRect({
      videoWidth: 1920, videoHeight: 1080,
      // x,y are CENTER coords (not top-left). Centered at frame middle.
      focus: { x: 0.5, y: 0.5, w: 0.6, h: 0.6 },
      targetAspect: 9 / 16,
    });
    // For 9:16 at 1080 height: cropW = 1080 * 9/16 = 607.5
    expect(rect.h).toBe(1080);
    expect(rect.w).toBeCloseTo(607.5, 0);
    // Focus center x = 0.5 * 1920 = 960; crop center should match.
    const cropCenterX = rect.x + rect.w / 2;
    expect(cropCenterX).toBeCloseTo(960, 0);
  });

  it("clamps crop to frame edges when focus is near the edge", () => {
    const rect = computeCropRect({
      videoWidth: 1920, videoHeight: 1080,
      focus: { x: 0.0, y: 0.0, w: 0.2, h: 0.4 },  // top-left corner
      targetAspect: 9 / 16,
    });
    expect(rect.x).toBe(0);   // can't go negative
    expect(rect.y).toBe(0);
    expect(rect.x + rect.w).toBeLessThanOrEqual(1920);
  });

  it("expands crop horizontally when focus_region is too narrow for 9:16", () => {
    const rect = computeCropRect({
      videoWidth: 1920, videoHeight: 1080,
      focus: { x: 0.45, y: 0.0, w: 0.1, h: 1.0 },
      targetAspect: 9 / 16,
    });
    expect(rect.h).toBe(1080);
    expect(rect.w).toBeCloseTo(607.5, 0);
  });

  it("returns rect with target aspect ratio (within rounding)", () => {
    const rect = computeCropRect({
      videoWidth: 1920, videoHeight: 1080,
      focus: { x: 0.3, y: 0.2, w: 0.4, h: 0.6 },
      targetAspect: 9 / 16,
    });
    expect(rect.w / rect.h).toBeCloseTo(9 / 16, 3);
  });
});
