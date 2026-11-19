import { readFileSync } from "node:fs";
import { join } from "node:path";
import { gzipSync } from "node:zlib";
import { describe, expect, it } from "vitest";
import { type PoliteHttp, createPoliteHttp } from "../../src/ebooks/http.js";
import { bpbSource } from "../../src/ebooks/sources/bpb.js";
import { gutenbergSource } from "../../src/ebooks/sources/gutenberg.js";
import { openAccessSource } from "../../src/ebooks/sources/openaccess.js";
import { SourceBlocked, type SourceContext } from "../../src/ebooks/sources/types.js";
import { wikisourceSource } from "../../src/ebooks/sources/wikisource.js";
import { isWholeBook } from "../../src/ebooks/whole-book.js";

const fx = (name: string) => readFileSync(join(__dirname, "../fixtures/ebooks", name));

/** Falsches Netz: die erste Regel, deren Prüfung auf die URL passt, antwortet. */
function fakeHttp(
  rules: [(url: string) => boolean, number, Buffer | string][],
): PoliteHttp & { calls: string[] } {
  const calls: string[] = [];
  return {
    calls,
    async get(url: string) {
      calls.push(url);
      for (const [match, status, body] of rules)
        if (match(url)) return { status, body: Buffer.isBuffer(body) ? body : Buffer.from(body) };
      return { status: 404, body: Buffer.from("") };
    },
  };
}

function ctx(http: PoliteHttp): SourceContext {
  const mem = new Map<string, unknown>();
  return {
    http,
    cache: { get: (k) => mem.get(k) as never, set: (k, v) => void mem.set(k, v) },
    log: () => {},
  };
}

describe("Gutenberg", () => {
  it("liest nur deutsche Texte aus dem Katalog des Spiegels", async () => {
    const http = fakeHttp([
      [(u) => u.startsWith("https://gutenberg.pglaf.org/"), 200, fx("pg_catalog.csv")],
    ]);
    const books = await gutenbergSource.listCatalog(ctx(http));
    expect(books).toHaveLength(12);
    expect(books[0]).toMatchObject({
      source: "gutenberg",
      sourceId: "2054",
      title: "Iphigenie auf Tauris",
      authors: ["Johann Wolfgang von Goethe"],
      language: "de",
      format: "epub",
      fileUrl: "https://gutenberg.pglaf.org/cache/epub/2054/pg2054-images-3.epub",
      coverUrl: "https://gutenberg.pglaf.org/cache/epub/2054/pg2054.cover.medium.jpg",
      sourceUrl: "https://www.gutenberg.org/ebooks/2054",
    });
    expect(books[0].subjects).toContain("DE Drama");
    expect(books.every((b) => b.language === "de")).toBe(true);
  });

  it("lädt zuerst die gepackte Fassung (5 statt 21 MB)", async () => {
    const http = fakeHttp([
      [(u) => u.endsWith("pg_catalog.csv.gz"), 200, gzipSync(fx("pg_catalog.csv"))],
    ]);
    expect(await gutenbergSource.listCatalog(ctx(http))).toHaveLength(12);
    expect(http.calls).toEqual(["https://gutenberg.pglaf.org/cache/epub/feeds/pg_catalog.csv.gz"]);
  });

  it("nimmt die Hauptseite, wenn der Spiegel ausfällt", async () => {
    const http = fakeHttp([
      [(u) => u.startsWith("https://gutenberg.pglaf.org/"), 500, ""],
      [(u) => u.startsWith("https://www.gutenberg.org/"), 200, fx("pg_catalog.csv")],
    ]);
    expect(await gutenbergSource.listCatalog(ctx(http))).toHaveLength(12);
  });

  it("Übersetzer zählen nicht als Autor", async () => {
    const csv = `Text#,Type,Issued,Title,Language,Authors,Subjects,LoCC,Bookshelves\n5,Text,2000-01-01,Der Rabe,de,"Poe, Edgar Allan, 1809-1849; Wollheim, Hedwig, 1888-1937 [Translator]",Poetry,,DE Lyrik\n`;
    const http = fakeHttp([[() => true, 200, csv]]);
    const [b] = await gutenbergSource.listCatalog(ctx(http));
    expect(b.authors).toEqual(["Edgar Allan Poe"]);
  });
});

describe("Wikisource", () => {
  const http = () =>
    fakeHttp([
      [(u) => u.includes("generator=categorymembers"), 200, fx("wikisource-roman.json")],
      [(u) => u.includes("prop=revisions"), 200, fx("wikisource-content.json")],
    ]);

  it("nimmt nur fertige oder korrigierte, ganze Werke", async () => {
    const books = await wikisourceSource.listCatalog(ctx(http()));
    // Unvollständiges fehlt schon in der Quelle; Kurzes fällt im gemeinsamen Filter.
    expect(books.map((b) => b.title).sort()).toEqual([
      "Auf dunklem Pfade",
      "Effi Briest",
      "Ein kurzes Märchen",
    ]);
    expect(
      books
        .filter((b) => isWholeBook(b).ok)
        .map((b) => b.title)
        .sort(),
    ).toEqual(["Auf dunklem Pfade", "Effi Briest"]);
  });

  it("liest Autor, Jahr und Beschreibung aus den Textdaten", async () => {
    const books = await wikisourceSource.listCatalog(ctx(http()));
    const effi = books.find((b) => b.title === "Effi Briest");
    expect(effi).toMatchObject({
      source: "wikisource",
      sourceId: "Effi Briest",
      authors: ["Theodor Fontane"],
      year: 1896,
      description: "Roman über eine junge Frau.",
      format: "epub",
      fileUrl: "https://ws-export.wmcloud.org/?lang=de&format=epub&page=Effi%20Briest",
      sourceUrl: "https://de.wikisource.org/wiki/Effi_Briest",
      wordCount: undefined,
    });
    const pfad = books.find((b) => b.title === "Auf dunklem Pfade");
    expect(pfad?.subjects).toContain("Kriminalliteratur");
    expect(pfad?.wordCount).toBeGreaterThan(8000);
  });

  it("fragt unveränderte Seiten beim zweiten Lauf nicht noch einmal ab", async () => {
    const c = ctx(http());
    await wikisourceSource.listCatalog(c);
    const h = c.http as ReturnType<typeof fakeHttp>;
    const before = h.calls.filter((u) => u.includes("prop=revisions")).length;
    await wikisourceSource.listCatalog(c);
    expect(h.calls.filter((u) => u.includes("prop=revisions")).length).toBe(before);
  });
});

describe("bpb", () => {
  it("nimmt nur Bücher mit freiem Download, nie die Leseprobe", async () => {
    const http = fakeHttp([
      [(u) => u.includes("sitemap.xml"), 200, fx("bpb-sitemap.xml")],
      [(u) => u.includes("deutschland-archiv-2017"), 200, fx("bpb-book-epub.html")],
      [(u) => u.includes("volk-und-elite"), 200, fx("bpb-book-leseprobe.html")],
    ]);
    const books = await bpbSource.listCatalog(ctx(http));
    expect(books).toHaveLength(1);
    expect(books[0]).toMatchObject({
      source: "bpb",
      sourceId: "265522",
      title: "Deutschland Archiv 2017",
      format: "epub",
      fileUrl: "https://www.bpb.de/system/files/datei/Deutschland_Archiv_2017.epub?download=1",
      coverUrl: "https://www.bpb.de/cache/images/3/265523_teaser_publikation_800.jpg?D9067",
      sourceUrl: "https://www.bpb.de/shop/buecher/schriftenreihe/265522/deutschland-archiv-2017/",
    });
    expect(books[0].description).toMatch(/^Das Online-Portal "Deutschland Archiv"/);
    expect(http.calls.some((u) => u.includes("/themen/"))).toBe(false);
  });
});

describe("Open Access (DOAB)", () => {
  it("bevorzugt EPUB, nimmt sonst PDF, nur Deutsch", async () => {
    const http = fakeHttp([
      [
        (u) => u.startsWith("https://directory.doabooks.org/rest/search"),
        200,
        fx("doab-search.json"),
      ],
    ]);
    const books = await openAccessSource.listCatalog(ctx(http));
    expect(books.map((b) => [b.title, b.format])).toEqual([
      ["Demokratie in Europa", "epub"],
      ["Nur PDF", "pdf"],
    ]);
    expect(books[0]).toMatchObject({
      authors: ["Erika Muster"],
      year: 2021,
      fileUrl: "https://directory.doabooks.org/bitstreams/b2/retrieve",
      sourceUrl: "https://directory.doabooks.org/handle/20.500.12854/1",
    });
  });

  it("403 heißt gesperrt", async () => {
    const http = fakeHttp([[() => true, 403, "You address is not allowed to access this API."]]);
    await expect(openAccessSource.listCatalog(ctx(http))).rejects.toBeInstanceOf(SourceBlocked);
  });
});

describe("createPoliteHttp", () => {
  it("wartet bei 503 nach retry-after und versucht es erneut", async () => {
    const sleeps: number[] = [];
    let n = 0;
    const http = createPoliteHttp({
      perSecond: 1000,
      sleep: async (ms) => void sleeps.push(ms),
      fetchImpl: async () => {
        n++;
        return n === 1
          ? new Response("busy", { status: 503, headers: { "retry-after": "2" } })
          : new Response("ok", { status: 200 });
      },
    });
    const r = await http.get("https://x.test/a");
    expect(r.status).toBe(200);
    expect(r.body.toString()).toBe("ok");
    expect(sleeps).toContain(2000);
  });

  it("wartet nie länger als 15 Minuten, auch wenn retry-after mehr verlangt", async () => {
    const sleeps: number[] = [];
    let n = 0;
    const http = createPoliteHttp({
      perSecond: 1000,
      sleep: async (ms) => void sleeps.push(ms),
      fetchImpl: async () =>
        ++n === 1
          ? new Response("", { status: 503, headers: { "retry-after": "3600" } })
          : new Response("ok"),
    });
    await http.get("https://x.test/a");
    expect(Math.max(...sleeps)).toBe(15 * 60 * 1000);
  });

  it("gibt nach drei Fehlschlägen auf", async () => {
    const http = createPoliteHttp({
      perSecond: 1000,
      sleep: async () => {},
      fetchImpl: async () => new Response("busy", { status: 429 }),
    });
    await expect(http.get("https://x.test/a")).rejects.toThrow(/429/);
  });

  it("schickt den eigenen User-Agent ohne persönliche Daten", async () => {
    let ua = "";
    const http = createPoliteHttp({
      perSecond: 1000,
      sleep: async () => {},
      fetchImpl: async (_u, init) => {
        ua = new Headers(init?.headers).get("user-agent") ?? "";
        return new Response("ok");
      },
    });
    await http.get("https://x.test/a");
    expect(ua).toBe("Hikari-Ebooks/1.0 (private reader)");
  });
});
