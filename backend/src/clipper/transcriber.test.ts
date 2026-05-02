import { describe, expect, it, vi } from "vitest";
import { transcribe } from "./transcriber.js";

const MOCK_WHISPER_JSON = JSON.stringify({
  text: "Und rein schicken.",
  segments: [
    {
      start: 0,
      end: 1.0,
      words: [
        { word: " Und", start: 0.0, end: 0.22 },
        { word: " rein", start: 0.22, end: 0.44 },
        { word: " schicken.", start: 0.44, end: 0.84 },
      ],
    },
  ],
  language: "de",
});

describe("transcribe", () => {
  it("invokes whisper CLI and parses word-level captions", async () => {
    const execFn: any = vi.fn(async () => ({}));
    const fsReadFn: any = vi.fn(async () => MOCK_WHISPER_JSON);
    const captions = await transcribe("/fake/clip.mp4", { execFn, fsReadFn });
    expect(captions).toHaveLength(3);
    expect(captions[0]).toEqual({ start: 0, end: 0.22, text: "Und" });
    expect(captions[2]).toEqual({ start: 0.44, end: 0.84, text: "schicken." });
    // Verify whisper was called with the file + json output
    const args = execFn.mock.calls[0][1];
    expect(args).toContain("/fake/clip.mp4");
    expect(args).toContain("--word_timestamps");
    expect(args).toContain("--output_format");
    expect(args).toContain("json");
  });

  it("handles empty/no-segments output gracefully", async () => {
    const execFn: any = vi.fn(async () => ({}));
    const fsReadFn: any = vi.fn(async () =>
      JSON.stringify({ text: "", segments: [], language: "de" })
    );
    const captions = await transcribe("/fake/clip.mp4", { execFn, fsReadFn });
    expect(captions).toEqual([]);
  });

  it("skips empty word strings (whitespace-only)", async () => {
    const execFn: any = vi.fn(async () => ({}));
    const fsReadFn: any = vi.fn(async () => JSON.stringify({
      segments: [{ words: [
        { word: " Hi", start: 0, end: 0.1 },
        { word: " ", start: 0.1, end: 0.15 },
        { word: " there", start: 0.15, end: 0.4 },
      ]}],
    }));
    const captions = await transcribe("/fake/clip.mp4", { execFn, fsReadFn });
    expect(captions).toHaveLength(2);
    expect(captions.map(c => c.text)).toEqual(["Hi", "there"]);
  });
});
