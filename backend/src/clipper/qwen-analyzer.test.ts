import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { analyzeVideo } from "./qwen-analyzer.js";

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

describe("analyzeVideo", () => {
  beforeEach(() => { vi.useFakeTimers(); });
  afterEach(() => { vi.restoreAllMocks(); vi.useRealTimers(); });

  it("parses a valid LM-Studio response into ClipSpec[]", async () => {
    const fetchFn = mockFetch([{
      ok: true,
      body: { choices: [{ message: { content: VALID_RESPONSE } }] },
    }]);
    const out = await analyzeVideo(
      { filePath: "/fake.mp4", videoId: "v1", durationSec: 600 },
      "prompt",
      { provider: "lmstudio", baseUrl: "http://x", model: "qwen", fetchFn },
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
      { provider: "lmstudio", baseUrl: "http://x", model: "qwen", fetchFn },
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
      { provider: "lmstudio", baseUrl: "http://x", model: "qwen", fetchFn },
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
      { provider: "lmstudio", baseUrl: "http://x", model: "qwen", fetchFn },
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
      { provider: "lmstudio", baseUrl: "http://x", model: "qwen", fetchFn },
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
      { provider: "lmstudio", baseUrl: "http://x", model: "qwen", fetchFn },
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
      { provider: "lmstudio", baseUrl: "http://x", model: "qwen", fetchFn },
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
      { provider: "lmstudio", baseUrl: "http://x", model: "qwen", fetchFn },
    );
    expect(out).toEqual([]);
  });
});
