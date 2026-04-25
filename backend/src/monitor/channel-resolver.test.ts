import { beforeEach, describe, expect, it, vi } from "vitest";
import { resolveChannel } from "./channel-resolver.js";

vi.mock("../yt-dlp/client.js", () => ({
  runYtDlp: vi.fn(),
  YtDlpError: class YtDlpError extends Error {},
}));

describe("resolveChannel", () => {
  beforeEach(() => vi.clearAllMocks());

  it("extracts channel_id and uploader from yt-dlp JSON output", async () => {
    const { runYtDlp } = await import("../yt-dlp/client.js");
    vi.mocked(runYtDlp).mockResolvedValue({
      stdout: '{"channel_id":"UCabc123","channel":"3Blue1Brown"}',
      stderr: "",
    });

    const result = await resolveChannel("https://www.youtube.com/@3blue1brown");
    expect(result).toEqual({
      channelId: "UCabc123",
      title: "3Blue1Brown",
      handle: null,
      description: null,
      subscribers: null,
      thumbnail: null,
    });
    expect(runYtDlp).toHaveBeenCalledWith([
      "--flat-playlist",
      "--playlist-items",
      "1",
      "--dump-single-json",
      "--no-warnings",
      "https://www.youtube.com/@3blue1brown",
    ]);
  });

  it("throws when channel_id is missing from yt-dlp output", async () => {
    const { runYtDlp } = await import("../yt-dlp/client.js");
    vi.mocked(runYtDlp).mockResolvedValue({ stdout: "{}", stderr: "" });

    await expect(resolveChannel("https://invalid")).rejects.toThrow(/channel_id/);
  });

  it("picks the square avatar thumbnail, skipping banners that come first", async () => {
    // yt-dlp's channel-URL output puts banners (wide aspect) before avatars
    // (square). Mock that ordering — we expect the resolver to skip past
    // the banners and grab the 900x900 avatar.
    const { runYtDlp } = await import("../yt-dlp/client.js");
    vi.mocked(runYtDlp).mockResolvedValue({
      stdout: JSON.stringify({
        channel_id: "UC1",
        channel: "SpongeLore",
        uploader_id: "@SpongeLore",
        thumbnails: [
          { url: "https://yt3.example/banner1.jpg", width: 1060, height: 175 },
          { url: "https://yt3.example/banner2.jpg", width: 2560, height: 424 },
          { url: "https://yt3.example/avatar.jpg", width: 900, height: 900 },
        ],
      }),
      stderr: "",
    });

    const result = await resolveChannel("https://www.youtube.com/@SpongeLore");
    expect(result.thumbnail).toBe("https://yt3.example/avatar.jpg");
  });

  it("falls back to last thumbnail when no square one is found", async () => {
    const { runYtDlp } = await import("../yt-dlp/client.js");
    vi.mocked(runYtDlp).mockResolvedValue({
      stdout: JSON.stringify({
        channel_id: "UC1",
        channel: "X",
        thumbnails: [
          { url: "https://yt3.example/wide-a.jpg", width: 100, height: 50 },
          { url: "https://yt3.example/wide-b.jpg", width: 200, height: 80 },
        ],
      }),
      stderr: "",
    });

    const result = await resolveChannel("https://x");
    expect(result.thumbnail).toBe("https://yt3.example/wide-b.jpg");
  });

  it("normalizes protocol-relative URLs", async () => {
    const { runYtDlp } = await import("../yt-dlp/client.js");
    vi.mocked(runYtDlp).mockResolvedValue({
      stdout: JSON.stringify({
        channel_id: "UC1",
        channel: "X",
        thumbnails: [{ url: "//yt3.example/a.jpg", width: 100, height: 100 }],
      }),
      stderr: "",
    });
    const result = await resolveChannel("https://x");
    expect(result.thumbnail).toBe("https://yt3.example/a.jpg");
  });
});
