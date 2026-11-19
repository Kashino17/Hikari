import { mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { NewsDiscover, diversify, matchesTopic, stripSite, tidy, videoFrom } from "./discover.js";

const para = (n: number) =>
  Array.from(
    { length: n },
    (_, i) => `<p>Absatz ${i}: ${"Ein ganz normaler Satz über KI und Claude. ".repeat(4)}</p>`,
  ).join("");

function page(opts: { paywall?: boolean; body?: string } = {}) {
  return `<!doctype html><html lang="de"><head>
    <title>Claude kann jetzt Videos schneiden - Beispiel</title>
    <meta property="og:type" content="article"><meta property="og:site_name" content="Beispiel">
    <meta property="og:image" content="/bild.jpg"><link rel="icon" href="/fav.png">
    ${opts.paywall ? '<script type="application/ld+json">{"@type":"NewsArticle","isAccessibleForFree":false}</script>' : ""}
    </head><body><article><h1>Claude kann jetzt Videos schneiden</h1>
    ${opts.body ?? para(8)}
    <iframe src="https://www.youtube.com/embed/abcdefghijk"></iframe>
    ${para(2)}</article></body></html>`;
}

function fakeFetch(html: string): typeof fetch {
  return (async (input: string | URL | Request) =>
    new Response(html, { status: 200, headers: { "content-type": "text/html" } }) as Response & {
      url: string;
    }) as unknown as typeof fetch;
}

describe("discover helpers", () => {
  it("stripSite entfernt Seitennamen am Titelende", () => {
    expect(stripSite("Apple baut eigenen KI-Agenten | World News", "Hindustan Times")).toBe(
      "Apple baut eigenen KI-Agenten",
    );
    expect(stripSite("OpenAI bekommt Gegenwind von Testern - CNET", "CNET")).toBe(
      "OpenAI bekommt Gegenwind von Testern",
    );
    expect(stripSite("GTA 6 ist da - 6 Fakten zum Gaming", "FinanzNachrichten.de")).toBe(
      "GTA 6 ist da - 6 Fakten zum Gaming",
    );
  });

  it("videoFrom erkennt YouTube, Vimeo und Dateien", () => {
    expect(
      videoFrom("https://www.youtube-nocookie.com/embed/9KvI-zg4mfo?x=1", "https://a.de"),
    ).toEqual({ type: "youtube", id: "9KvI-zg4mfo" });
    expect(videoFrom("https://player.vimeo.com/video/123", "https://a.de")).toEqual({
      type: "vimeo",
      id: "123",
    });
    expect(videoFrom("/clip.mp4", "https://a.de/x")).toEqual({
      type: "file",
      src: "https://a.de/clip.mp4",
    });
    expect(videoFrom("https://a.de/seite", "https://a.de")).toBeNull();
  });

  it("tidy wirft Autoren-Kasten und Lärm am Ende weg", () => {
    const long = "x".repeat(130);
    const out = tidy([
      { t: "p", text: long },
      { t: "p", text: "More from CNET" },
      { t: "p", text: long },
      { t: "img", src: "https://a.de/avatar.jpg?w=128", caption: "" },
      { t: "p", text: "Reporter" },
    ]);
    expect(out).toEqual([
      { t: "p", text: long },
      { t: "p", text: long },
    ]);
  });

  it("matchesTopic verlangt das Stichwort im Kopf des Artikels", () => {
    expect(matchesTopic("KI-Agenten China", "Chinas neue Agenten erobern den Markt")).toBe(true);
    expect(matchesTopic("Anthropic", "Fußball: Bayern gewinnt")).toBe(false);
  });

  it("diversify: neueste zuerst, gleiche Themen nicht hintereinander", () => {
    const out = diversify([
      { topic: "A", at: 5 },
      { topic: "A", at: 4 },
      { topic: "B", at: 3 },
    ]);
    expect(out.map((o) => o.topic)).toEqual(["A", "B", "A"]);
  });
});

describe("NewsDiscover.article", () => {
  it("zerlegt einen freien Artikel in Text, Bild und Video", async () => {
    const d = new NewsDiscover(mkdtempSync(join(tmpdir(), "news-")), fakeFetch(page()));
    const a = await d.article("https://beispiel.de/claude-video");
    expect(a.ok).toBe(true);
    expect(a.title).toBe("Claude kann jetzt Videos schneiden");
    expect(a.favicon).toBe("https://beispiel.de/fav.png");
    expect(a.blocks[0]).toMatchObject({ t: "img", src: "https://beispiel.de/bild.jpg" });
    expect(
      a.blocks.some((b) => b.t === "video" && b.type === "youtube" && b.id === "abcdefghijk"),
    ).toBe(true);
    expect(a.blocks.filter((b) => b.t === "p").length).toBeGreaterThan(5);
    // zweiter Aufruf kommt von der Platte
    expect((await d.cached(a.id))?.title).toBe(a.title);
  });

  it("lehnt Artikel hinter Bezahlschranke ab", async () => {
    const d = new NewsDiscover(
      mkdtempSync(join(tmpdir(), "news-")),
      fakeFetch(page({ paywall: true })),
    );
    const a = await d.article("https://beispiel.de/plus");
    expect(a.ok).toBe(false);
    expect(a.reason).toBe("isAccessibleForFree");
  });

  it("lehnt Anrisse ab", async () => {
    const d = new NewsDiscover(
      mkdtempSync(join(tmpdir(), "news-")),
      fakeFetch(page({ body: para(1) })),
    );
    const a = await d.article("https://beispiel.de/kurz");
    expect(a.ok).toBe(false);
    expect(a.reason).toBe("zu kurz");
  });

  it("speichert eigene Stichwörter ohne Doppelte", async () => {
    const d = new NewsDiscover(mkdtempSync(join(tmpdir(), "news-")), fakeFetch(""));
    expect(await d.setTopics(["OpenAI", " OpenAI ", "", "Gaming"])).toEqual(["OpenAI", "Gaming"]);
    expect(await d.topics()).toEqual(["OpenAI", "Gaming"]);
  });
});
