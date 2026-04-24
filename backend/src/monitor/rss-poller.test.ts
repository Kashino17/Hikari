import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchChannelFeed, parseChannelFeed } from "./rss-poller.js";

const fixture = readFileSync(
  resolve(import.meta.dirname, "../../tests/fixtures/sample-channel-rss.xml"),
  "utf8",
);

describe("parseChannelFeed", () => {
  it("extracts video IDs and titles in feed order", () => {
    const entries = parseChannelFeed(fixture);
    expect(entries).toEqual([
      { videoId: "dQw4w9WgXcQ", title: "Sample video one", publishedAt: 1776938400000 },
      { videoId: "aBcDeFgHiJk", title: "Sample video two", publishedAt: 1776852000000 },
    ]);
  });

  it("returns empty array for feed with no entries", () => {
    const empty = '<?xml version="1.0"?><feed xmlns="http://www.w3.org/2005/Atom"></feed>';
    expect(parseChannelFeed(empty)).toEqual([]);
  });
});

describe("fetchChannelFeed", () => {
  afterEach(() => vi.restoreAllMocks());

  it("calls YouTube RSS URL with the given channel_id", async () => {
    const fetchSpy = vi.spyOn(global, "fetch").mockResolvedValue(
      new Response(fixture, { status: 200 })
    );
    const entries = await fetchChannelFeed("UCxxx");
    expect(fetchSpy).toHaveBeenCalledWith(
      "https://www.youtube.com/feeds/videos.xml?channel_id=UCxxx"
    );
    expect(entries).toHaveLength(2);
  });

  it("throws on non-200 response", async () => {
    vi.spyOn(global, "fetch").mockResolvedValue(new Response("not found", { status: 404 }));
    await expect(fetchChannelFeed("UCmissing")).rejects.toThrow(/404/);
  });
});
