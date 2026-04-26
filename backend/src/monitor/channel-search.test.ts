import { afterEach, describe, expect, it, vi } from "vitest";
import { searchChannels } from "./channel-search.js";

vi.mock("../yt-dlp/client.js", () => ({
  runYtDlp: vi.fn(),
  YtDlpError: class extends Error {},
}));

describe("searchChannels", () => {
  afterEach(() => vi.restoreAllMocks());

  it("returns empty list for queries shorter than 2 chars", async () => {
    const { runYtDlp } = await import("../yt-dlp/client.js");
    expect(await searchChannels("", 10)).toEqual([]);
    expect(await searchChannels("a", 10)).toEqual([]);
    expect(runYtDlp).not.toHaveBeenCalled();
  });

  it("parses yt-dlp search output into typed channel results", async () => {
    const { runYtDlp } = await import("../yt-dlp/client.js");
    vi.mocked(runYtDlp).mockResolvedValue({
      stdout: JSON.stringify({
        entries: [
          {
            ie_key: "YoutubeTab",
            id: "UCYO_jab_esuFRV4b17AJtAw",
            channel_id: "UCYO_jab_esuFRV4b17AJtAw",
            channel: "3Blue1Brown",
            channel_url: "https://www.youtube.com/channel/UCYO_jab_esuFRV4b17AJtAw",
            uploader_id: "@3blue1brown",
            description: "math channel",
            channel_follower_count: 8280000,
            channel_is_verified: true,
            thumbnails: [{ url: "//yt3.googleusercontent.com/foo=s88" }],
          },
        ],
      }),
      stderr: "",
    });

    const results = await searchChannels("3blue1brown", 10);
    expect(results).toEqual([
      {
        channelId: "UCYO_jab_esuFRV4b17AJtAw",
        channelUrl: "https://www.youtube.com/channel/UCYO_jab_esuFRV4b17AJtAw",
        title: "3Blue1Brown",
        handle: "@3blue1brown",
        description: "math channel",
        subscribers: 8280000,
        thumbnail: "https://yt3.googleusercontent.com/foo=s88",
        banner: null,
        verified: true,
      },
    ]);
  });

  it("filters out entries with no channel id", async () => {
    const { runYtDlp } = await import("../yt-dlp/client.js");
    vi.mocked(runYtDlp).mockResolvedValue({
      stdout: JSON.stringify({
        entries: [
          { ie_key: "YoutubeTab" }, // no id
          { ie_key: "YoutubeTab", channel_id: "UC1", channel: "ok" },
        ],
      }),
      stderr: "",
    });
    const results = await searchChannels("ok", 10);
    expect(results).toHaveLength(1);
    expect(results[0]?.channelId).toBe("UC1");
  });

  it("clamps limit and forwards to yt-dlp args", async () => {
    const { runYtDlp } = await import("../yt-dlp/client.js");
    vi.mocked(runYtDlp).mockResolvedValue({ stdout: '{"entries":[]}', stderr: "" });
    await searchChannels("abc", 999);
    const callArgs = vi.mocked(runYtDlp).mock.calls[0]![0];
    const idx = callArgs.indexOf("--playlist-end");
    expect(callArgs[idx + 1]).toBe("25");
  });

  it("URL-encodes the query", async () => {
    const { runYtDlp } = await import("../yt-dlp/client.js");
    vi.mocked(runYtDlp).mockResolvedValue({ stdout: '{"entries":[]}', stderr: "" });
    await searchChannels("3 blue 1 brown", 10);
    const url = vi.mocked(runYtDlp).mock.calls[0]![0].at(-1)!;
    expect(url).toContain("search_query=3%20blue%201%20brown");
    expect(url).toContain("sp=EgIQAg%253D%253D");
  });
});
