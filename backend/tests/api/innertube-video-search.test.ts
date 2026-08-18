import { expect, test } from "vitest";
import { itVideoSearch } from "../../src/api/music-innertube.js";

const body = {
  contents: [
    { videoRenderer: { videoId: "aaaaaaaaaaa" } },
    { videoRenderer: { videoId: "bbbbbbbbbbb" } },
    { videoRenderer: { videoId: "aaaaaaaaaaa" } },
    { videoRenderer: { videoId: "nix" } },
  ],
};
const okFetch = (async () => new Response(JSON.stringify(body), { status: 200 })) as typeof fetch;

test("liefert deduplizierte Video-IDs, respektiert max", async () => {
  expect(await itVideoSearch(okFetch, "weltraum")).toEqual(["aaaaaaaaaaa", "bbbbbbbbbbb"]);
  expect(await itVideoSearch(okFetch, "weltraum", 1)).toEqual(["aaaaaaaaaaa"]);
});

test("Fehler/leer ⇒ undefined", async () => {
  const failFetch = (async () => new Response("x", { status: 500 })) as typeof fetch;
  expect(await itVideoSearch(failFetch, "q")).toBeUndefined();
  const emptyFetch = (async () => new Response("{}", { status: 200 })) as typeof fetch;
  expect(await itVideoSearch(emptyFetch, "q")).toBeUndefined();
});
