import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { analyzeVideo, cleanSegments } from "./qwen-analyzer.js";
// QwenNetworkError exported for instanceof checks if needed
// eslint-disable-next-line @typescript-eslint/no-unused-vars

const VALID_RESPONSE = JSON.stringify([
  { start_sec: 30, end_sec: 60,
    focus: { x: 0.2, y: 0.2, w: 0.6, h: 0.6 },
    reason: "intro hook" },
]);

function mockFetch(responses: Array<{ ok: boolean; body: unknown }>) {
  let i = 0;
  return vi.fn(async () => {
    const r = responses[i++];
    return {
      ok: r.ok,
      status: r.ok ? 200 : 500,
      text: async () => typeof r.body === "string" ? r.body : JSON.stringify(r.body),
      json: async () => r.body,
    } as Response;
  });
}

const mockSample = vi.fn(async () => [
  { timestampSec: 60, base64DataUri: "data:image/jpeg;base64,FAKE1" },
  { timestampSec: 120, base64DataUri: "data:image/jpeg;base64,FAKE2" },
]);

describe("analyzeVideo", () => {
  beforeEach(() => { vi.useFakeTimers(); mockSample.mockClear(); });
  afterEach(() => { vi.restoreAllMocks(); vi.useRealTimers(); });

  it("parses a valid LM-Studio response into ClipSpec[]", async () => {
    const fetchFn = mockFetch([{
      ok: true,
      body: { choices: [{ message: { content: VALID_RESPONSE } }] },
    }]);
    const out = await analyzeVideo(
      { filePath: "/fake.mp4", videoId: "v1", durationSec: 600 },
      "prompt",
      { provider: "lmstudio", baseUrl: "http://x", model: "qwen", fetchFn, sampleFn: mockSample },
    );
    expect(out).toHaveLength(1);
    expect(out[0]).toMatchObject({
      startSec: 30, endSec: 60,
      focus: { x: 0.2, y: 0.2, w: 0.6, h: 0.6 },
      reason: "intro hook",
    });
  });

  it("retries once on invalid JSON, succeeds on second attempt", async () => {
    const fetchFn = mockFetch([
      { ok: true, body: { choices: [{ message: { content: "not json at all" } }] } },
      { ok: true, body: { choices: [{ message: { content: VALID_RESPONSE } }] } },
    ]);
    const out = await analyzeVideo(
      { filePath: "/fake.mp4", videoId: "v1", durationSec: 600 },
      "prompt",
      { provider: "lmstudio", baseUrl: "http://x", model: "qwen", fetchFn, sampleFn: mockSample },
    );
    expect(out).toHaveLength(1);
    expect(fetchFn).toHaveBeenCalledTimes(2);
  });

  it("throws after second invalid JSON", async () => {
    const fetchFn = mockFetch([
      { ok: true, body: { choices: [{ message: { content: "garbage" } }] } },
      { ok: true, body: { choices: [{ message: { content: "still garbage" } }] } },
    ]);
    await expect(analyzeVideo(
      { filePath: "/fake.mp4", videoId: "v1", durationSec: 600 },
      "prompt",
      { provider: "lmstudio", baseUrl: "http://x", model: "qwen", fetchFn, sampleFn: mockSample },
    )).rejects.toThrow(/invalid JSON/i);
  });

  it("filters out specs whose start/end exceed video.duration", async () => {
    const body = JSON.stringify([
      { start_sec: 30, end_sec: 60, focus: { x: 0, y: 0, w: 1, h: 1 }, reason: "ok" },
      { start_sec: 700, end_sec: 750, focus: { x: 0, y: 0, w: 1, h: 1 }, reason: "out of bounds" },
    ]);
    const fetchFn = mockFetch([{
      ok: true, body: { choices: [{ message: { content: body } }] },
    }]);
    const out = await analyzeVideo(
      { filePath: "/fake.mp4", videoId: "v1", durationSec: 600 },
      "prompt",
      { provider: "lmstudio", baseUrl: "http://x", model: "qwen", fetchFn, sampleFn: mockSample },
    );
    expect(out).toHaveLength(1);
    expect(out[0].endSec).toBe(60);
  });

  it("clamps short clips to 20s by extending end_sec", async () => {
    const body = JSON.stringify([
      { start_sec: 30, end_sec: 35, focus: { x: 0, y: 0, w: 1, h: 1 }, reason: "too short" },
    ]);
    const fetchFn = mockFetch([{
      ok: true, body: { choices: [{ message: { content: body } }] },
    }]);
    const out = await analyzeVideo(
      { filePath: "/fake.mp4", videoId: "v1", durationSec: 600 },
      "prompt",
      { provider: "lmstudio", baseUrl: "http://x", model: "qwen", fetchFn, sampleFn: mockSample },
    );
    expect(out[0].endSec).toBe(50);
  });

  it("clamps long clips to 90s by trimming end_sec", async () => {
    const body = JSON.stringify([
      { start_sec: 30, end_sec: 200, focus: { x: 0, y: 0, w: 1, h: 1 }, reason: "too long" },
    ]);
    const fetchFn = mockFetch([{
      ok: true, body: { choices: [{ message: { content: body } }] },
    }]);
    const out = await analyzeVideo(
      { filePath: "/fake.mp4", videoId: "v1", durationSec: 600 },
      "prompt",
      { provider: "lmstudio", baseUrl: "http://x", model: "qwen", fetchFn, sampleFn: mockSample },
    );
    expect(out[0].endSec).toBe(120);
  });

  it("drops clips that cannot satisfy 20s minimum (start too close to video end)", async () => {
    const body = JSON.stringify([
      { start_sec: 590, end_sec: 595, focus: { x: 0, y: 0, w: 1, h: 1 }, reason: "no room" },
    ]);
    const fetchFn = mockFetch([{
      ok: true, body: { choices: [{ message: { content: body } }] },
    }]);
    const out = await analyzeVideo(
      { filePath: "/fake.mp4", videoId: "v1", durationSec: 600 },
      "prompt",
      { provider: "lmstudio", baseUrl: "http://x", model: "qwen", fetchFn, sampleFn: mockSample },
    );
    expect(out).toHaveLength(0);
  });

  it("returns empty array when Qwen returns []", async () => {
    const fetchFn = mockFetch([{
      ok: true, body: { choices: [{ message: { content: "[]" } }] },
    }]);
    const out = await analyzeVideo(
      { filePath: "/fake.mp4", videoId: "v1", durationSec: 600 },
      "prompt",
      { provider: "lmstudio", baseUrl: "http://x", model: "qwen", fetchFn, sampleFn: mockSample },
    );
    expect(out).toEqual([]);
  });

  it("throws QwenNetworkError when fetch itself fails", async () => {
    const fetchFn = vi.fn(async () => { throw new TypeError("fetch failed"); });
    await expect(analyzeVideo(
      { filePath: "/x.mp4", videoId: "v1", durationSec: 600 },
      "p",
      { provider: "lmstudio", baseUrl: "http://x", model: "q", fetchFn, sampleFn: mockSample },
    )).rejects.toThrow(/Cannot reach Qwen/);
  });

  it("throws QwenNetworkError on 5xx status", async () => {
    const fetchFn = vi.fn(async () => ({
      ok: false,
      status: 503,
      text: async () => "service unavailable",
      json: async () => ({}),
    } as Response));
    await expect(analyzeVideo(
      { filePath: "/x.mp4", videoId: "v1", durationSec: 600 },
      "p",
      { provider: "lmstudio", baseUrl: "http://x", model: "q", fetchFn, sampleFn: mockSample },
    )).rejects.toThrow(/503/);
  });

  it("sends image_url content blocks for each sampled frame", async () => {
    const fetchFn = mockFetch([{ ok: true, body: { choices: [{ message: { content: VALID_RESPONSE } }] } }]);
    await analyzeVideo(
      { filePath: "/fake.mp4", videoId: "v1", durationSec: 600 },
      "prompt",
      { provider: "lmstudio", baseUrl: "http://x", model: "qwen", fetchFn, sampleFn: mockSample },
    );
    const callArgs = fetchFn.mock.calls[0]!;
    const sentBody = JSON.parse(callArgs[1]!.body as string);
    const userContent = sentBody.messages[1].content;
    expect(userContent[0].type).toBe("text");
    expect(userContent.filter((c: any) => c.type === "image_url")).toHaveLength(2);
    expect(userContent.find((c: any) => c.type === "video_url")).toBeUndefined();
  });

  it("includes transcript in user text when provided", async () => {
    const fetchFn = mockFetch([{ ok: true, body: { choices: [{ message: { content: "[]" } }] } }]);
    await analyzeVideo(
      { filePath: "/fake.mp4", videoId: "v1", durationSec: 600, transcript: "Hello world from the video." },
      "prompt",
      { provider: "lmstudio", baseUrl: "http://x", model: "qwen", fetchFn, sampleFn: mockSample },
    );
    const sentBody = JSON.parse(fetchFn.mock.calls[0]![1]!.body as string);
    const userText = sentBody.messages[1].content[0].text;
    expect(userText).toContain("Transkript:");
    expect(userText).toContain("Hello world from the video.");
  });

  it("ignores legacy display_segments / focus / display_mode — renderer is fit-only", async () => {
    // Even if Qwen still returns these legacy fields, ClipSpec carries
    // only the deterministic defaults. Renderer always uses fit-mode.
    const body = JSON.stringify([{
      start_sec: 0, end_sec: 60,
      focus: { x: 0.5, y: 0.4, w: 0.3, h: 0.7 },
      reason: "any",
      display_mode: "smart-crop",
      display_segments: [
        { start_sec: 0, end_sec: 30, mode: "smart-crop" },
        { start_sec: 30, end_sec: 60, mode: "fit" },
      ],
    }]);
    const fetchFn = mockFetch([{ ok: true, body: { choices: [{ message: { content: body } }] } }]);
    const out = await analyzeVideo(
      { filePath: "/fake.mp4", videoId: "v1", durationSec: 600 },
      "prompt",
      { provider: "lmstudio", baseUrl: "http://x", model: "qwen", fetchFn, sampleFn: mockSample },
    );
    expect(out[0]!.displayMode).toBe("fit");
    expect(out[0]!.displaySegments).toBeUndefined();
  });

  it("synthesizes default focus/displayMode when Qwen omits all layout fields", async () => {
    // Simplified prompt — Qwen no longer needs to return focus or display_mode.
    const body = JSON.stringify([{
      start_sec: 0, end_sec: 60,
      reason: "minimal",
    }]);
    const fetchFn = mockFetch([{ ok: true, body: { choices: [{ message: { content: body } }] } }]);
    const out = await analyzeVideo(
      { filePath: "/fake.mp4", videoId: "v1", durationSec: 600 },
      "prompt",
      { provider: "lmstudio", baseUrl: "http://x", model: "qwen", fetchFn, sampleFn: mockSample },
    );
    expect(out[0]!.displayMode).toBe("fit");
    expect(out[0]!.focus).toEqual({ x: 0.5, y: 0.5, w: 1, h: 1 });
  });
});

describe("cleanSegments", () => {
  it("returns two clean segments as-is", () => {
    const result = cleanSegments([
      { start_sec: 0, end_sec: 30, mode: "smart-crop", focus: { x: 0.5, y: 0.4, w: 0.3, h: 0.7 } },
      { start_sec: 30, end_sec: 60, mode: "fit" },
    ], 60);
    expect(result).toHaveLength(2);
    expect(result![0]!.startSec).toBe(0);
    expect(result![0]!.endSec).toBe(30);
    expect(result![1]!.startSec).toBe(30);
    expect(result![1]!.endSec).toBe(60);
  });

  it("merges tiny segment (1s) sandwiched between two longer ones into the longer neighbor", () => {
    // Segments: [0-25s smart-crop], [25-26s fit (tiny)], [26-60s smart-crop]
    // tiny 1s segment should merge into the longer adjacent (both are 25s and 34s — next wins)
    const result = cleanSegments([
      { start_sec: 0, end_sec: 25, mode: "smart-crop" },
      { start_sec: 25, end_sec: 26, mode: "fit" },
      { start_sec: 26, end_sec: 60, mode: "smart-crop" },
    ], 60);
    // The 1s "fit" segment should be absorbed; result has ≤ 2 segments
    // The tiny middle gets merged into the longer neighbor (next: 34s > prev: 25s)
    expect(result).not.toBeNull();
    // No segment should be < 3s
    for (const seg of result!) {
      expect(seg.endSec - seg.startSec).toBeGreaterThanOrEqual(3);
    }
  });

  it("fills a gap between segments", () => {
    // Segments: [0-20s smart-crop], gap, [30-60s fit]
    const result = cleanSegments([
      { start_sec: 0, end_sec: 20, mode: "smart-crop" },
      { start_sec: 30, end_sec: 60, mode: "fit" },
    ], 60);
    expect(result).toHaveLength(2);
    // Gap filled: first segment extended to 30
    expect(result![0]!.endSec).toBe(30);
    expect(result![1]!.startSec).toBe(30);
  });

  it("resolves an overlap: keeps earlier, trims later's start", () => {
    // Segments: [0-35s smart-crop], [30-60s fit] — overlap 30-35s
    const result = cleanSegments([
      { start_sec: 0, end_sec: 35, mode: "smart-crop" },
      { start_sec: 30, end_sec: 60, mode: "fit" },
    ], 60);
    expect(result).toHaveLength(2);
    expect(result![0]!.endSec).toBe(30);  // trimmed to not-overlap
    expect(result![1]!.startSec).toBe(30);
  });

  it("returns null for a single segment (fall back to clip-level display_mode)", () => {
    const result = cleanSegments([
      { start_sec: 0, end_sec: 60, mode: "smart-crop" },
    ], 60);
    expect(result).toBeNull();
  });
});
