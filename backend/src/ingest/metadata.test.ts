import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { fetchVideoMetadata } from "./metadata.js";

vi.mock("../yt-dlp/client.js", () => ({
  runYtDlp: vi.fn(),
  YtDlpError: class extends Error {},
}));

const fixture = readFileSync(
  resolve(import.meta.dirname, "../../tests/fixtures/sample-video-metadata.json"),
  "utf8",
);

describe("fetchVideoMetadata", () => {
  beforeEach(() => vi.clearAllMocks());

  it("parses yt-dlp JSON output into VideoMetadata shape", async () => {
    const { runYtDlp } = await import("../yt-dlp/client.js");
    vi.mocked(runYtDlp).mockResolvedValue({ stdout: fixture, stderr: "" });

    const meta = await fetchVideoMetadata("dQw4w9WgXcQ");
    expect(meta).toMatchObject({
      id: "dQw4w9WgXcQ",
      title: "Why is this number everywhere?",
      durationSeconds: 612,
      aspectRatio: "16:9",
      defaultLanguage: "en",
      captionsUrl: "https://youtube.com/caption-url-en",
      isLive: false,
    });
  });

  it("correctly classifies a 9:16 video as vertical aspect ratio", async () => {
    const { runYtDlp } = await import("../yt-dlp/client.js");
    const vertical = JSON.stringify({
      ...JSON.parse(fixture),
      width: 720,
      height: 1280,
    });
    vi.mocked(runYtDlp).mockResolvedValue({ stdout: vertical, stderr: "" });

    const meta = await fetchVideoMetadata("short-id");
    expect(meta.aspectRatio).toBe("9:16");
  });
});
