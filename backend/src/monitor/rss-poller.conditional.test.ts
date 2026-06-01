import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchChannelFeedConditional } from "./rss-poller.js";

const fixture = readFileSync(
  resolve(import.meta.dirname, "../../tests/fixtures/sample-channel-rss.xml"),
  "utf8",
);

describe("fetchChannelFeedConditional", () => {
  afterEach(() => vi.restoreAllMocks());

  it("sends If-None-Match / If-Modified-Since from prior validators", async () => {
    const spy = vi.spyOn(global, "fetch").mockResolvedValue(
      new Response(fixture, {
        status: 200,
        headers: { etag: 'W/"new"', "last-modified": "Wed, 01 Jan 2026 00:00:00 GMT" },
      }),
    );
    await fetchChannelFeedConditional("UCxxx", {
      etag: 'W/"old"',
      lastModified: "Tue, 31 Dec 2025 00:00:00 GMT",
    });
    const [, init] = spy.mock.calls[0]!;
    expect((init as RequestInit).headers).toMatchObject({
      "If-None-Match": 'W/"old"',
      "If-Modified-Since": "Tue, 31 Dec 2025 00:00:00 GMT",
    });
  });

  it("sends no conditional headers when there are no priors", async () => {
    const spy = vi
      .spyOn(global, "fetch")
      .mockResolvedValue(new Response(fixture, { status: 200 }));
    await fetchChannelFeedConditional("UCxxx", { etag: null, lastModified: null });
    const [, init] = spy.mock.calls[0]!;
    expect((init as RequestInit).headers).toEqual({});
  });

  it("returns notModified on 304", async () => {
    vi.spyOn(global, "fetch").mockResolvedValue(new Response(null, { status: 304 }));
    const res = await fetchChannelFeedConditional("UCxxx", {
      etag: 'W/"x"',
      lastModified: null,
    });
    expect(res).toEqual({ status: "notModified" });
  });

  it("returns parsed entries + fresh validators on 200", async () => {
    vi.spyOn(global, "fetch").mockResolvedValue(
      new Response(fixture, {
        status: 200,
        headers: { etag: 'W/"fresh"', "last-modified": "Wed, 01 Jan 2026 12:00:00 GMT" },
      }),
    );
    const res = await fetchChannelFeedConditional("UCxxx", { etag: null, lastModified: null });
    expect(res.status).toBe("ok");
    if (res.status === "ok") {
      expect(res.entries).toHaveLength(2);
      expect(res.etag).toBe('W/"fresh"');
      expect(res.lastModified).toBe("Wed, 01 Jan 2026 12:00:00 GMT");
    }
  });

  it("throws on a non-200/304 error", async () => {
    vi.spyOn(global, "fetch").mockResolvedValue(new Response("nope", { status: 500 }));
    await expect(
      fetchChannelFeedConditional("UCxxx", { etag: null, lastModified: null }),
    ).rejects.toThrow(/500/);
  });
});
