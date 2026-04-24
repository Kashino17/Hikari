import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchTranscript, parseVtt } from "./transcript.js";

const vtt = readFileSync(
  resolve(import.meta.dirname, "../../tests/fixtures/sample-captions.vtt"),
  "utf8",
);

describe("parseVtt", () => {
  it("extracts plain text lines, joined with spaces, no cues or timestamps", () => {
    expect(parseVtt(vtt)).toBe("Welcome to the video. Today we look at the number e.");
  });

  it("returns empty string for empty VTT", () => {
    expect(parseVtt("WEBVTT\n\n")).toBe("");
  });
});

describe("fetchTranscript", () => {
  afterEach(() => vi.restoreAllMocks());

  it("fetches URL and returns parsed transcript", async () => {
    vi.spyOn(global, "fetch").mockResolvedValue(new Response(vtt, { status: 200 }));
    const text = await fetchTranscript("https://example.com/captions.vtt");
    expect(text).toContain("Welcome to the video");
  });

  it("returns null on non-200", async () => {
    vi.spyOn(global, "fetch").mockResolvedValue(new Response("", { status: 404 }));
    expect(await fetchTranscript("https://example.com/missing.vtt")).toBeNull();
  });
});
