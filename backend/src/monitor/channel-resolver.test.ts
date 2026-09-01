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
      banner: null,
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

  it("picks the widest banner thumbnail (ratio >= 2.5)", async () => {
    const { runYtDlp } = await import("../yt-dlp/client.js");
    vi.mocked(runYtDlp).mockResolvedValue({
      stdout: JSON.stringify({
        channel_id: "UC1",
        channel: "BannerChannel",
        thumbnails: [
          { url: "https://yt3.example/banner-sm.jpg", width: 1060, height: 175 },
          { url: "https://yt3.example/banner-lg.jpg", width: 2560, height: 424 },
          { url: "https://yt3.example/avatar.jpg", width: 900, height: 900 },
        ],
      }),
      stderr: "",
    });
    const result = await resolveChannel("https://x");
    expect(result.banner).toBe("https://yt3.example/banner-lg.jpg");
    expect(result.thumbnail).toBe("https://yt3.example/avatar.jpg");
  });

  it("returns null banner when no wide thumbnails exist", async () => {
    const { runYtDlp } = await import("../yt-dlp/client.js");
    vi.mocked(runYtDlp).mockResolvedValue({
      stdout: JSON.stringify({
        channel_id: "UC1",
        channel: "AvatarOnly",
        thumbnails: [{ url: "https://yt3.example/a.jpg", width: 800, height: 800 }],
      }),
      stderr: "",
    });
    const result = await resolveChannel("https://x");
    expect(result.banner).toBeNull();
  });
});

describe("refreshChannelMetadata", () => {
  beforeEach(() => vi.clearAllMocks());

  it("schreibt die aufgelösten Metadaten in die channels-Zeile", async () => {
    const { runYtDlp } = await import("../yt-dlp/client.js");
    vi.mocked(runYtDlp).mockResolvedValue({
      stdout: JSON.stringify({
        channel_id: "UC1",
        channel: "TEDx Talks",
        uploader_id: "@TEDx",
        channel_follower_count: 44600000,
        thumbnails: [
          { url: "https://yt3.example/banner.jpg", width: 2560, height: 424 },
          { url: "https://yt3.example/avatar.jpg", width: 900, height: 900 },
        ],
      }),
      stderr: "",
    });

    const updates: unknown[][] = [];
    const db = {
      prepare: (sql: string) => ({
        run: (...args: unknown[]) => {
          if (sql.includes("UPDATE channels")) updates.push(args);
        },
      }),
    };

    const { refreshChannelMetadata } = await import("./channel-resolver.js");
    await refreshChannelMetadata(db as never, "UC1", "https://www.youtube.com/channel/UC1");

    expect(updates).toEqual([
      ["@TEDx", null, 44600000, "https://yt3.example/avatar.jpg", "https://yt3.example/banner.jpg", "UC1"],
    ]);
  });
});
