import { expect, test } from "vitest";
import { itChannelShorts } from "../../src/api/music-innertube.js";

function lockup(videoId: string) {
  return {
    shortsLockupViewModel: {
      onTap: { innertubeCommand: { reelWatchEndpoint: { videoId } } },
    },
  };
}

const body = {
  contents: [lockup("aaaaaaaaaaa"), lockup("bbbbbbbbbbb"), lockup("aaaaaaaaaaa"), lockup("kurz")],
};
const okFetch = (async () => new Response(JSON.stringify(body), { status: 200 })) as typeof fetch;

test("liefert deduplizierte, valide Shorts-IDs", async () => {
  const ids = await itChannelShorts(okFetch, "UCtest");
  expect(ids).toEqual(["aaaaaaaaaaa", "bbbbbbbbbbb"]); // "kurz" faellt an VIDEO_ID_RE
});

test("HTTP-Fehler oder leerer Tab ⇒ undefined", async () => {
  const failFetch = (async () => new Response("x", { status: 500 })) as typeof fetch;
  expect(await itChannelShorts(failFetch, "UCtest")).toBeUndefined();
  const emptyFetch = (async () => new Response("{}", { status: 200 })) as typeof fetch;
  expect(await itChannelShorts(emptyFetch, "UCtest")).toBeUndefined();
});
