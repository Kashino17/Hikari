import Database from "better-sqlite3";
import Fastify from "fastify";
import { describe, expect, it, vi } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import { type NewsDeps, registerNewsRoutes } from "./news.js";

const FEED_XML = `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
  <channel>
    <title>Fixture</title>
    <item>
      <title>Meldung Eins</title>
      <link>https://example.com/a1</link>
      <description>Beschreibung eins.</description>
      <pubDate>Fri, 01 Aug 2025 09:00:00 GMT</pubDate>
      <media:content url="https://example.com/feed1.jpg" medium="image"/>
    </item>
    <item>
      <title>Meldung Zwei</title>
      <link>https://example.com/a2</link>
      <description>Beschreibung zwei.</description>
      <pubDate>Fri, 01 Aug 2025 08:00:00 GMT</pubDate>
    </item>
  </channel>
</rss>`;

const GOOGLE_XML = `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0">
  <channel>
    <title>Google News</title>
    <item>
      <title>Stadt plant Radwege - Lokalzeitung</title>
      <link>https://news.google.com/rss/articles/CBMiabc</link>
      <description>Snippets aus Google News.</description>
      <pubDate>Fri, 01 Aug 2025 07:00:00 GMT</pubDate>
    </item>
  </channel>
</rss>`;

const ARTICLE_HTML = `<!doctype html><html><head>
<meta property="og:image" content="https://example.com/og1.jpg"/>
</head><body>
<p>Dies ist der erste Absatz des Artikels mit genuegend Text fuer den Lead.</p>
<p>Zweiter Absatz mit weiteren Details zur Meldung aus dem Feed.</p>
</body></html>`;

function okText(body: string): Response {
  return { ok: true, text: async () => body } as unknown as Response;
}

function makeFetch(feedXml: string = FEED_XML) {
  return vi.fn(async (input: string | URL | Request) => {
    const url = String(input);
    if (url.includes("news.google.com")) return okText(GOOGLE_XML);
    if (url.includes("example.com/a")) return okText(ARTICLE_HTML);
    return okText(feedXml);
  }) as unknown as typeof fetch;
}

function makeSummarizer() {
  return vi.fn(async (items: { title: string }[]) =>
    items.map((_, i) => ({ headline: `Schlagzeile ${i}`, summary: `Zusammenfassung ${i}` })),
  );
}

const NOW = Date.parse("2025-08-01T10:00:00Z");

async function makeApp(deps: NewsDeps) {
  const app = Fastify();
  await registerNewsRoutes(app, deps);
  return app;
}

function makeDb(): Database.Database {
  const db = new Database(":memory:");
  applyMigrations(db);
  return db;
}

describe("GET /news/topics", () => {
  it("returns the topic registry", async () => {
    const app = await makeApp({});
    const res = await app.inject({ method: "GET", url: "/news/topics" });
    expect(res.statusCode).toBe(200);
    const body = res.json();
    expect(body.length).toBeGreaterThan(0);
    expect(body[0]).toEqual({
      key: expect.any(String),
      label: expect.any(String),
      lang: expect.stringMatching(/^(de|en)$/),
    });
    expect(body.find((t: { key: string }) => t.key === "politik")).toMatchObject({
      label: "Politik",
      lang: "de",
    });
    await app.close();
  });
});

describe("GET /news/briefing", () => {
  it("fulfills the JSON contract with mocked feeds + summarizer", async () => {
    const fetchImpl = makeFetch();
    const summarizer = makeSummarizer();
    const app = await makeApp({ fetchImpl, summarizer, now: () => NOW });
    const res = await app.inject({ method: "GET", url: "/news/briefing?lang=de" });
    expect(res.statusCode).toBe(200);
    const items = res.json();
    // default topics politik,technologie,wissen — same fixture everywhere,
    // dedupe by title collapses to the two fixture items
    expect(items).toHaveLength(2);
    expect(items[0]).toEqual({
      id: expect.any(String),
      title: "Schlagzeile 0",
      summary: "Zusammenfassung 0",
      source: expect.any(String),
      url: "https://example.com/a1",
      imageUrls: ["https://example.com/feed1.jpg", "https://example.com/og1.jpg"],
      videoUrl: null,
      topic: expect.any(String),
      publishedAt: "2025-08-01T09:00:00.000Z",
    });
    expect(summarizer).toHaveBeenCalledTimes(1);
    await app.close();
  });

  it("serves the second identical request from cache without fetching", async () => {
    const db = makeDb();
    const fetchImpl = makeFetch();
    const summarizer = makeSummarizer();
    const app = await makeApp({ db, fetchImpl, summarizer, now: () => NOW });
    const first = await app.inject({ method: "GET", url: "/news/briefing?lang=de" });
    expect(first.statusCode).toBe(200);
    const callsAfterFirst = (fetchImpl as unknown as ReturnType<typeof vi.fn>).mock.calls.length;
    expect(callsAfterFirst).toBeGreaterThan(0);

    const second = await app.inject({ method: "GET", url: "/news/briefing?lang=de" });
    expect(second.statusCode).toBe(200);
    expect(second.json()).toEqual(first.json());
    expect((fetchImpl as unknown as ReturnType<typeof vi.fn>).mock.calls.length).toBe(
      callsAfterFirst,
    );

    // force=1 bypasses the cache
    await app.inject({ method: "GET", url: "/news/briefing?lang=de&force=1" });
    expect((fetchImpl as unknown as ReturnType<typeof vi.fn>).mock.calls.length).toBeGreaterThan(
      callsAfterFirst,
    );
    await app.close();
  });

  it("rejects lang=fr with 400", async () => {
    const app = await makeApp({ fetchImpl: makeFetch() });
    const res = await app.inject({ method: "GET", url: "/news/briefing?lang=fr" });
    expect(res.statusCode).toBe(400);
    await app.close();
  });

  it("falls back to title/description when the summarizer throws", async () => {
    const fetchImpl = makeFetch();
    const summarizer = vi.fn(async () => {
      throw new Error("LLM down");
    });
    const app = await makeApp({ fetchImpl, summarizer, now: () => NOW });
    const res = await app.inject({ method: "GET", url: "/news/briefing?lang=de" });
    expect(res.statusCode).toBe(200);
    const items = res.json();
    expect(items[0].title).toBe("Meldung Eins");
    expect(items[0].summary).toBe("Beschreibung eins.");
    await app.close();
  });

  it("resolves free-text topics via Google News and extracts the source", async () => {
    const fetchImpl = makeFetch();
    const summarizer = makeSummarizer();
    const app = await makeApp({ fetchImpl, summarizer, now: () => NOW });
    const res = await app.inject({
      method: "GET",
      url: "/news/briefing?lang=de&topics=radwege",
    });
    expect(res.statusCode).toBe(200);
    const items = res.json();
    expect(items).toHaveLength(1);
    expect(items[0]).toMatchObject({
      url: "https://news.google.com/rss/articles/CBMiabc",
      source: "Lokalzeitung",
      topic: "radwege",
      imageUrls: [],
      videoUrl: null,
    });
    await app.close();
  });
});
