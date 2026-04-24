import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchSponsorSegments } from "./client.js";

describe("fetchSponsorSegments", () => {
  afterEach(() => vi.restoreAllMocks());

  it("maps API response to flat segment list", async () => {
    vi.spyOn(global, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify([
          { category: "sponsor", segment: [12.5, 38.2] },
          { category: "selfpromo", segment: [120, 145] },
        ]),
        { status: 200 },
      ),
    );
    const segs = await fetchSponsorSegments("abc123");
    expect(segs).toEqual([
      { category: "sponsor", startSeconds: 12.5, endSeconds: 38.2 },
      { category: "selfpromo", startSeconds: 120, endSeconds: 145 },
    ]);
  });

  it("returns empty array on 404", async () => {
    vi.spyOn(global, "fetch").mockResolvedValue(new Response("[]", { status: 404 }));
    expect(await fetchSponsorSegments("noseg")).toEqual([]);
  });
});
