import { describe, expect, it } from "vitest";
import {
  MANGADEX_UA,
  buildQuery,
  createMangadexClient,
  isMangadexOrgHost,
  retryWaitMs,
} from "../../src/manga/mangadex/client.js";

describe("MangaDex-Client", () => {
  it("Listen als key[]=…, Klammern kodiert", () => {
    expect(buildQuery({ "a[]": ["de", "en"], "order[x]": "desc", limit: 5 })).toBe(
      "?a%5B%5D=de&a%5B%5D=en&order%5Bx%5D=desc&limit=5",
    );
    expect(buildQuery(undefined)).toBe("");
  });

  it("Wartezeit: retry-after, sonst x-ratelimit-retry-after (Unix-Sekunden), sonst Rückfall", () => {
    expect(retryWaitMs(new Headers({ "retry-after": "7" }), 0, 99)).toBe(7000);
    expect(retryWaitMs(new Headers({ "x-ratelimit-retry-after": "100" }), 90_000, 99)).toBe(10_250);
    expect(retryWaitMs(new Headers(), 0, 99)).toBe(99);
  });

  it("eigener User-Agent, 429 → warten → nochmal, dann JSON", async () => {
    const seen: { url: string; ua: string | null }[] = [];
    const sleeps: number[] = [];
    let n = 0;
    const client = createMangadexClient({
      sleep: async (ms) => {
        sleeps.push(ms);
      },
      fetchImpl: async (url, init) => {
        seen.push({ url, ua: new Headers(init?.headers).get("user-agent") });
        n++;
        if (n === 1) return new Response("", { status: 429, headers: { "retry-after": "3" } });
        return new Response(JSON.stringify({ ok: 1 }), { status: 200 });
      },
    });
    const r = await client.api<{ ok: number }>("/manga", { limit: 1 });
    expect(r).toEqual({ ok: 1 });
    expect(seen).toHaveLength(2);
    expect(seen[0]).toEqual({ url: "https://api.mangadex.org/manga?limit=1", ua: MANGADEX_UA });
    expect(sleeps).toContain(3000);
  });

  it("höchstens vier API-Anfragen pro Sekunde", async () => {
    const sleeps: number[] = [];
    const client = createMangadexClient({
      sleep: async (ms) => {
        sleeps.push(ms);
      },
      fetchImpl: async () => new Response("{}", { status: 200 }),
    });
    await Promise.all([1, 2, 3, 4, 5].map(() => client.api("/x")));
    // Erste sofort, danach je 250 ms Abstand.
    expect(sleeps.length).toBe(4);
    expect(Math.max(...sleeps)).toBeGreaterThanOrEqual(900);
  });

  it("Fehlerstatus wirft", async () => {
    const client = createMangadexClient({
      fetchImpl: async () => new Response("nope", { status: 404 }),
    });
    await expect(client.api("/manga/x")).rejects.toThrow(/HTTP 404/);
  });

  it("Rückmeldung geht als JSON an api.mangadex.network, ohne Persönliches", async () => {
    const posts: { url: string; body: string }[] = [];
    const client = createMangadexClient({
      fetchImpl: async (url, init) => {
        posts.push({ url, body: String(init?.body) });
        return new Response("", { status: 200 });
      },
    });
    client.report({
      url: "https://a.mangadex.network/x",
      success: true,
      bytes: 5,
      duration: 9,
      cached: false,
    });
    await new Promise((r) => setTimeout(r, 0));
    expect(posts[0]?.url).toBe("https://api.mangadex.network/report");
    expect(JSON.parse(posts[0]?.body ?? "{}")).toEqual({
      url: "https://a.mangadex.network/x",
      success: true,
      bytes: 5,
      duration: 9,
      cached: false,
    });
  });

  it("mangadex.org-Hosts erkennen", () => {
    expect(isMangadexOrgHost("https://uploads.mangadex.org/covers/x.jpg")).toBe(true);
    expect(isMangadexOrgHost("https://abc.mangadex.network/data/x")).toBe(false);
  });
});
