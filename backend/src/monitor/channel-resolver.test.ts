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
});
