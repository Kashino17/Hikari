import { mkdtempSync, readFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import Database from "better-sqlite3";
import Fastify, { type FastifyInstance } from "fastify";
import { beforeEach, describe, expect, it } from "vitest";
import { registerEbookRoutes } from "../../src/api/ebooks.js";
import { applyMigrations } from "../../src/db/migrations.js";
import type { PoliteHttp } from "../../src/ebooks/http.js";
import { type RawBook, hideBook, upsertBook } from "../../src/ebooks/persist.js";
import { normalizeState } from "../../src/ebooks/state-merge.js";

const fx = (n: string) => readFileSync(join(__dirname, "../fixtures/ebooks", n));

function raw(id: string, over: Partial<RawBook> = {}): RawBook {
  return {
    source: "gutenberg",
    sourceId: id,
    title: `Buch ${id}`,
    authors: ["Autor"],
    language: "de",
    subjects: [],
    format: "epub",
    fileUrl: `https://x.test/${id}`,
    sourceUrl: `https://src.test/${id}`,
    ...over,
  };
}

describe("/api/ebooks", () => {
  let db: Database.Database;
  let app: FastifyInstance;
  let httpBody: Buffer;
  beforeEach(async () => {
    db = new Database(":memory:");
    applyMigrations(db);
    httpBody = fx("long.epub");
    const http: PoliteHttp = { get: async () => ({ status: 200, body: httpBody }) };
    app = Fastify();
    registerEbookRoutes(app, {
      db,
      ebookDir: mkdtempSync(join(tmpdir(), "eb-")),
      coverDir: mkdtempSync(join(tmpdir(), "cv-")),
      http,
      sources: [],
    });
    // 7 Krimis, 2 Romane, 1 PDF-Sachbuch, 1 verstecktes
    for (let i = 1; i <= 7; i++)
      upsertBook(db, raw(`k${i}`, { wordCount: i * 10_000, popularity: i }), ["krimi"], 1000 + i);
    upsertBook(
      db,
      raw("r1", { title: "Das Fräulein von Scuderi", wordCount: 20_000 }),
      ["roman"],
      2000,
    );
    upsertBook(
      db,
      raw("r2", { title: "Die Straße", authors: ["Max Müller"], wordCount: 150_000 }),
      ["roman"],
      2001,
    );
    upsertBook(
      db,
      raw("p1", { source: "bpb", format: "pdf", title: "Demokratie" }),
      ["politik"],
      2002,
    );
    upsertBook(db, raw("h1", { title: "Versteckt" }), ["krimi"], 2003);
    hideBook(db, "gutenberg:h1", "zu kurz");
  });

  const get = async (url: string) => {
    const r = await app.inject({ method: "GET", url });
    return { status: r.statusCode, json: r.json() };
  };
  const ids = (books: { id: string }[]) => books.map((b) => b.id).sort();

  it("home: Reihen nur für Kategorien ab 6 Büchern, neueste zuerst", async () => {
    const { json } = await get("/api/ebooks/home");
    expect(json.rows.map((r: { category: string }) => r.category)).toEqual(["krimi"]);
    expect(json.rows[0].label).toBe("Krimi & Mystery");
    expect(json.rows[0].books).toHaveLength(7);
    expect(json.newest[0].id).toBe("bpb:p1");
    expect(json.categories).toEqual(
      expect.arrayContaining([
        { id: "krimi", label: "Krimi & Mystery", group: "belletristik", count: 7 },
        { id: "roman", label: "Roman", group: "belletristik", count: 2 },
      ]),
    );
    expect(json.syncing).toBe(false);
  });

  it("home: Regale, Buch des Tages und Autoren", async () => {
    const { json } = await get("/api/ebooks/home");
    const keys = json.shelves.map((x: { key: string }) => x.key);
    expect(keys).toEqual(expect.arrayContaining(["beliebt", "kurz", "kat:krimi"]));
    const shelf = (k: string) => json.shelves.find((x: { key: string }) => x.key === k);
    expect(shelf("beliebt").books[0].id).toBe("gutenberg:k7");
    expect(shelf("kat:krimi").title).toBe("Krimi & Mystery");
    // kurz = unter 2 Stunden Lesezeit (bei 230 Wörtern pro Minute)
    expect(ids(shelf("kurz").books)).toEqual(["gutenberg:k1", "gutenberg:k2", "gutenberg:r1"]);
    expect(json.featured.id).toMatch(/^gutenberg:/);
    // Autoren ab zwei Büchern, versteckte zählen nicht
    expect(json.authors).toEqual([{ name: "Autor", count: 9 }]);
  });

  it("home mit zuletzt gelesenem Buch: Regal „Weil du … liest“", async () => {
    const { json } = await get(`/api/ebooks/home?because=${encodeURIComponent("gutenberg:k1")}`);
    const s = json.shelves.find((x: { key: string }) => x.key === "because");
    expect(s.title).toBe("Weil du Buch k1 liest");
    expect(ids(s.books)).not.toContain("gutenberg:k1");
    expect(s.books.length).toBeGreaterThan(0);
  });

  it("list nach Autor", async () => {
    const { json } = await get(`/api/ebooks/list?author=${encodeURIComponent("Max Müller")}`);
    expect(ids(json.books)).toEqual(["gutenberg:r2"]);
  });

  it("Detail: mehr vom Autor und ähnliche Bücher", async () => {
    const { json } = await get(`/api/ebooks/${encodeURIComponent("gutenberg:r1")}`);
    expect(ids(json.moreByAuthor)).not.toContain("gutenberg:r1");
    expect(json.moreByAuthor.length).toBeGreaterThan(0);
    expect(ids(json.similar)).toEqual(["gutenberg:r2"]);
  });

  it("dasselbe Werk zweimal im Katalog erscheint nur einmal (das beliebtere)", async () => {
    upsertBook(
      db,
      raw("f1", { title: "Faust", authors: ["Goethe"], popularity: 3 }),
      ["drama"],
      3000,
    );
    upsertBook(
      db,
      raw("f2", { title: "Faust", authors: ["Goethe"], popularity: 10 }),
      ["drama"],
      3001,
    );
    const { json } = await get("/api/ebooks/list?q=faust");
    expect(ids(json.books)).toEqual(["gutenberg:f2"]);
    expect((await get("/api/ebooks/gutenberg%3Af1")).status).toBe(200);
  });

  it("BookCard hat Format, Minuten und Titelbild-Weg", async () => {
    const { json } = await get("/api/ebooks/list?category=roman&sort=titel");
    expect(json.books[0]).toMatchObject({
      id: "gutenberg:r1",
      title: "Das Fräulein von Scuderi",
      authors: ["Autor"],
      format: "epub",
      categories: ["roman"],
      source: "gutenberg",
      wordCount: 20_000,
      minutes: 87,
    });
    expect(json.books[0].coverUrl).toBeUndefined();
  });

  it("list filtert nach Format, Kategorie und Länge", async () => {
    expect(ids((await get("/api/ebooks/list?format=pdf")).json.books)).toEqual(["bpb:p1"]);
    expect((await get("/api/ebooks/list?category=krimi")).json.total).toBe(7);
    expect(ids((await get("/api/ebooks/list?length=kurz&category=krimi")).json.books)).toEqual([
      "gutenberg:k1",
      "gutenberg:k2",
    ]);
    expect(ids((await get("/api/ebooks/list?length=lang")).json.books)).toEqual(["gutenberg:r2"]);
  });

  it("Suche mit Umlauten, ß und Wortanfängen", async () => {
    for (const q of ["fräul", "Fraulein", "FRÄULEIN scud"])
      expect(ids((await get(`/api/ebooks/list?q=${encodeURIComponent(q)}`)).json.books), q).toEqual(
        ["gutenberg:r1"],
      );
    expect(ids((await get("/api/ebooks/list?q=strasse")).json.books)).toEqual(["gutenberg:r2"]);
    expect(ids((await get("/api/ebooks/list?q=muller")).json.books)).toEqual(["gutenberg:r2"]);
    expect((await get(`/api/ebooks/list?q=${encodeURIComponent('"(')}`)).status).toBe(200);
  });

  it("versteckte Bücher fehlen in Listen, sind aber direkt abrufbar", async () => {
    expect(ids((await get("/api/ebooks/list?q=versteckt")).json.books)).toEqual([]);
    expect((await get("/api/ebooks/gutenberg%3Ah1")).status).toBe(200);
  });

  it("sortiert nach Beliebtheit", async () => {
    const { json } = await get("/api/ebooks/list?category=krimi&sort=beliebt&limit=2");
    expect(json.books.map((b: { id: string }) => b.id)).toEqual(["gutenberg:k7", "gutenberg:k6"]);
    expect(json.total).toBe(7);
  });

  it("Detail hat Quelle, Beschreibung und Download-Stand", async () => {
    const { json } = await get("/api/ebooks/bpb%3Ap1");
    expect(json).toMatchObject({
      id: "bpb:p1",
      format: "pdf",
      sourceUrl: "https://src.test/p1",
      sourceName: "Bundeszentrale für politische Bildung",
      downloaded: false,
    });
    expect((await get("/api/ebooks/nix%3A1")).status).toBe(404);
  });

  it("Datei laden: EPUB wird geliefert, danach gilt es als geladen", async () => {
    const r = await app.inject({ method: "GET", url: "/api/ebooks/gutenberg%3Ar1/file" });
    expect(r.statusCode).toBe(200);
    expect(r.headers["content-type"]).toBe("application/epub+zip");
    expect(r.rawPayload.length).toBe(fx("long.epub").length);
    expect((await get("/api/ebooks/gutenberg%3Ar1")).json.downloaded).toBe(true);
  });

  it("Vorbereiten: erst laden lassen, dann ist es bereit", async () => {
    const post = async () =>
      (await app.inject({ method: "POST", url: "/api/ebooks/gutenberg%3Ar2/prepare" })).json();
    const first = await post();
    expect(["loading", "ready"]).toContain(first.status);
    for (let i = 0; i < 50 && (await post()).status !== "ready"; i++)
      await new Promise((r) => setTimeout(r, 20));
    expect((await post()).status).toBe("ready");
    httpBody = fx("short.epub");
    let s = await post();
    expect(s.status).toBe("ready"); // schon geladen — bleibt bereit
    s = (await app.inject({ method: "POST", url: "/api/ebooks/gutenberg%3Ak2/prepare" })).json();
    for (let i = 0; i < 50 && s.status === "loading"; i++) {
      await new Promise((r) => setTimeout(r, 20));
      s = (await app.inject({ method: "POST", url: "/api/ebooks/gutenberg%3Ak2/prepare" })).json();
    }
    expect(s).toMatchObject({ status: "hidden" });
    expect(
      (await app.inject({ method: "POST", url: "/api/ebooks/nix%3A1/prepare" })).statusCode,
    ).toBe(404);
  });

  it("Datei eines zu kurzen Buchs → 410", async () => {
    httpBody = fx("short.epub");
    const r = await app.inject({ method: "GET", url: "/api/ebooks/gutenberg%3Ak1/file" });
    expect(r.statusCode).toBe(410);
    expect(r.json().error).toMatch(/zu kurz/);
  });

  it("Lesestand: PUT führt zusammen, GET liefert alle", async () => {
    const put = (body: unknown) =>
      app.inject({
        method: "PUT",
        url: "/api/ebooks/state/gutenberg%3Ar1",
        payload: body as object,
      });
    await put({ bookmarks: [{ id: "m1", cfi: "c1", at: 10 }], highlights: [] });
    const r = await put({ bookmarks: [{ id: "m2", cfi: "c2", at: 20 }], highlights: [] });
    expect(r.json().bookmarks.map((m: { id: string }) => m.id)).toEqual(["m1", "m2"]);
    const all = (await get("/api/ebooks/state")).json;
    expect(Object.keys(all)).toEqual(["gutenberg:r1"]);
    expect(all["gutenberg:r1"].bookmarks).toHaveLength(2);
  });

  it("Merkliste und Gelesen als Filter", async () => {
    await app.inject({
      method: "PUT",
      url: "/api/ebooks/state/gutenberg%3Ak3",
      payload: { saved: { value: true, at: 1 }, bookmarks: [], highlights: [] },
    });
    await app.inject({
      method: "PUT",
      url: "/api/ebooks/state/gutenberg%3Ak4",
      payload: { finished: { value: true, at: 1 }, bookmarks: [], highlights: [] },
    });
    expect(ids((await get("/api/ebooks/list?state=gemerkt")).json.books)).toEqual(["gutenberg:k3"]);
    expect(ids((await get("/api/ebooks/list?state=gelesen")).json.books)).toEqual(["gutenberg:k4"]);
  });

  it("Titelbild: einmal holen, danach aus dem Speicher; ohne Bild 404", async () => {
    upsertBook(db, raw("c1", { coverUrl: "https://img.test/c1.jpg" }), ["roman"], 3000);
    const png = Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47]), Buffer.alloc(200)]);
    httpBody = png;
    const a = await app.inject({ method: "GET", url: "/api/ebooks/gutenberg%3Ac1/cover" });
    expect(a.statusCode).toBe(200);
    expect(a.headers["content-type"]).toBe("image/png");
    httpBody = Buffer.from("anders");
    const b = await app.inject({ method: "GET", url: "/api/ebooks/gutenberg%3Ac1/cover" });
    expect(b.rawPayload.equals(png)).toBe(true);
    expect(
      (await app.inject({ method: "GET", url: "/api/ebooks/gutenberg%3Ar1/cover" })).statusCode,
    ).toBe(404);
    const { json } = await get("/api/ebooks/gutenberg%3Ac1");
    expect(json.coverUrl).toBe("/api/ebooks/gutenberg%3Ac1/cover?v=2");
  });

  it("Bücher ohne echtes Titelbild bekommen keinen Bildpfad (die App zeichnet eines)", async () => {
    upsertBook(db, raw("c2", { coverUrl: "https://img.test/c2.jpg" }), ["roman"], 3000);
    db.prepare("UPDATE ebooks SET cover_status = 'none' WHERE id = 'gutenberg:c2'").run();
    expect((await get("/api/ebooks/gutenberg%3Ac2")).json.coverUrl).toBeUndefined();
    expect(
      (await app.inject({ method: "GET", url: "/api/ebooks/gutenberg%3Ac2/cover" })).statusCode,
    ).toBe(404);
  });

  it("Titelbild aus Open Library wird ausgeliefert", async () => {
    upsertBook(db, raw("c3", { coverUrl: "https://img.test/c3.jpg" }), ["roman"], 3000);
    db.prepare(
      "UPDATE ebooks SET cover_status = 'openlibrary', cover_alt_url = 'https://covers.openlibrary.org/b/id/1-L.jpg' WHERE id = 'gutenberg:c3'",
    ).run();
    httpBody = Buffer.concat([Buffer.from([0xff, 0xd8, 0xff]), Buffer.alloc(300)]);
    const r = await app.inject({ method: "GET", url: "/api/ebooks/gutenberg%3Ac3/cover" });
    expect(r.statusCode).toBe(200);
    expect(r.headers["content-type"]).toBe("image/jpeg");
  });

  it("Abgleich starten antwortet sofort mit 202", async () => {
    const r = await app.inject({ method: "POST", url: "/api/ebooks/sync" });
    expect(r.statusCode).toBe(202);
  });
});

describe("normalizeState", () => {
  it("macht aus Unsinn einen leeren Lesestand", () => {
    expect(normalizeState(null)).toEqual({ bookmarks: [], highlights: [] });
    expect(normalizeState({ bookmarks: "x" })).toEqual({ bookmarks: [], highlights: [] });
    expect(normalizeState({ saved: { value: true, at: 1 } })).toEqual({
      saved: { value: true, at: 1 },
      bookmarks: [],
      highlights: [],
    });
  });
});
