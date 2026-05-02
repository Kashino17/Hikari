import { afterEach, describe, expect, it, vi } from "vitest";
import { renderClip } from "./remotion-renderer.js";

vi.mock("@remotion/bundler", () => ({
  bundle: vi.fn(async () => "/fake/bundle"),
}));
vi.mock("@remotion/renderer", () => ({
  selectComposition: vi.fn(async () => ({
    id: "Clip", width: 1080, height: 1920, fps: 30, durationInFrames: 1800,
  })),
  renderMedia: vi.fn(async () => undefined),
}));
vi.mock("node:fs/promises", async () => ({
  ...(await vi.importActual<object>("node:fs/promises")),
  stat: vi.fn(async () => ({ size: 4_500_000 })),
}));

afterEach(() => vi.clearAllMocks());

describe("renderClip", () => {
  it("calls renderMedia with smart-crop input props and returns file info", async () => {
    const { renderMedia } = await import("@remotion/renderer");
    const out = await renderClip({
      inputPath: "/orig/v1.mp4",
      videoWidth: 1920, videoHeight: 1080,
      spec: {
        startSec: 30, endSec: 80,
        focus: { x: 0.4, y: 0.2, w: 0.2, h: 0.6 },
        reason: "test",
      },
      outputPath: "/clips/c1.mp4",
    });
    expect(out.filePath).toBe("/clips/c1.mp4");
    expect(out.sizeBytes).toBe(4_500_000);
    const callArgs = (renderMedia as any).mock.calls[0][0];
    expect(callArgs.inputProps.src).toBe("file:///orig/v1.mp4");
    expect(callArgs.inputProps.startSec).toBe(30);
    expect(callArgs.inputProps.endSec).toBe(80);
    expect(callArgs.inputProps.cropW).toBeCloseTo(1080 * (9 / 16), 0);
    expect(callArgs.codec).toBe("h264");
  });

  it("propagates renderMedia errors", async () => {
    const { renderMedia } = await import("@remotion/renderer");
    (renderMedia as any).mockImplementationOnce(async () => {
      throw new Error("ffmpeg crashed");
    });
    await expect(renderClip({
      inputPath: "/orig/v1.mp4",
      videoWidth: 1920, videoHeight: 1080,
      spec: {
        startSec: 30, endSec: 80,
        focus: { x: 0, y: 0, w: 1, h: 1 },
        reason: "test",
      },
      outputPath: "/clips/c1.mp4",
    })).rejects.toThrow(/ffmpeg crashed/);
  });
});
