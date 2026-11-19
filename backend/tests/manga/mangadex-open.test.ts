import { existsSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import Database from "better-sqlite3";
import Fastify, { type FastifyInstance } from "fastify";
import { beforeEach, describe, expect, it } from "vitest";
import { registerMangaCatalogRoutes } from "../../src/api/manga-catalog.js";
import { registerMangaRoutes } from "../../src/api/manga.js";
import { applyMigrations } from "../../src/db/migrations.js";
import { upsertCatalog } from "../../src/manga/mangadex/catalog.js";
import { type MdAtHome, type MdManga, catalogFromManga } from "../../src/manga/mangadex/map.js";
import { openCatalogSeries } from "../../src/manga/mangadex/open.js";
import { createMangadexAdapter } from "../../src/manga/sources/mangadex.js";
import { onePieceTubeAdapter } from "../../src/manga/sources/onepiece-tube.js";
import { runChapterSync } from "../../src/manga/sync.js";
import { fakeClient, fx } from "./mangadex-fake.js";

const LIST = fx<{ data: MdManga[] }>("manga-list-de.json");
const FEED = fx<{ data: unknown[]; total: number }>("feed-kage.json");
const AT_HOME = fx<MdAtHome>("at-home.json");
const UUID = "77bee52c-d2d6-44ad-a33a-1734c1fe696a";
const SERIES = `mangadex:${UUID}`;
const DE_CH1 = "ea4cdf47-9092-4514-a76f-1eadfdf9f8db";

function feedRoute(q: Record<string, unknown> | undefined) {
  const offset = Number(q?.offset ?? 0);
  return {
    result: "ok",
    data: offset === 0 ? FEED.data : [],
    limit: 500,
    offset,
    total: FEED.total,
  };
}

function setup(now = { t: 1_000_000 }) {
  const db = new Database(":memory:");
  db.pragma("foreign_keys = ON");
  applyMigrations(db);
  const manga = LIST.data.find((m) => m.id === UUID) as MdManga;
  upsertCatalog(db, catalogFromManga(manga, 1));
  const atHomeCalls: string[] = [];
  const client = fakeClient({
    routes: [
      [`/manga/${UUID}/feed`, feedRoute],
      [
        "/at-home/server/",
        (_q, path) => {
          atHomeCalls.push(path);
          // Zweiter Aufruf = frischer Server.
          return atHomeCalls.length === 1
            ? AT_HOME
            : { ...AT_HOME, baseUrl: "https://frisch.mangadex.network" };
        },
      ],
    ],
  });
  const coverDir = mkdtempSync(join(tmpdir(), "mdo-cv-"));
  const mangaDir = mkdtempSync(join(tmpdir(), "mdo-mg-"));
  return { db, client, coverDir, mangaDir, now, atHomeCalls };
}

describe("openCatalogSeries", () => {
  it("legt die Reihe an und übernimmt den Kapitel-Feed (de + en, je Sprache eindeutig)", async () => {
    const s = setup();
    const r = await openCatalogSeries(
      { db: s.db, client: s.client, coverDir: s.coverDir, now: () => s.now.t },
      SERIES,
    );
    expect(r).toEqual({ seriesId: SERIES });

    const series = s.db.prepare("SELECT * FROM manga_series WHERE id = ?").get(SERIES) as Record<
      string,
      unknown
    >;
    expect(series).toMatchObject({
      source: "mangadex",
      title: "The Eminence in Shadow",
      author: "Sakano Anri, Aizawa Daisuke",
      status: "ongoing",
      source_url: `https://mangadex.org/title/${UUID}`,
      cover_path: `manga_mangadex_${UUID}.jpg`,
      last_synced_at: 1_000_000,
    });
    expect(existsSync(join(s.coverDir, `manga_mangadex_${UUID}.jpg`))).toBe(true);

    const de = s.db
      .prepare(
        "SELECT id, number, lang, scanlation_group AS g, source_url AS url, page_count AS pages FROM manga_chapters WHERE series_id = ? AND lang = 'de' ORDER BY number",
      )
      .all(SERIES);
    expect(de).toEqual([
      { id: `${SERIES}:de:1`, number: 1, lang: "de", g: "Black Poison", url: DE_CH1, pages: 37 },
      {
        id: `${SERIES}:de:47`,
        number: 47,
        lang: "de",
        g: "Black Poison",
        url: expect.stringMatching(/^b25e301f-/),
        pages: 32,
      },
    ]);
    const en = s.db
      .prepare("SELECT id FROM manga_chapters WHERE series_id = ? AND lang = 'en'")
      .all(SERIES) as { id: string }[];
    expect(en.some((c) => c.id === `${SERIES}:en:10.1`)).toBe(true);
    const total = (
      s.db.prepare("SELECT total_chapters AS n FROM manga_series WHERE id = ?").get(SERIES) as {
        n: number;
      }
    ).n;
    expect(total).toBe(de.length + en.length);

    // Feed-Anfrage wie dokumentiert.
    const feedCall = s.client.calls.find((c) => c.path.endsWith("/feed"));
    expect(feedCall?.query).toMatchObject({
      "translatedLanguage[]": ["de", "en"],
      "order[chapter]": "asc",
      limit: 500,
      offset: 0,
      "includes[]": ["scanlation_group"],
    });
  });

  it("teilt die Kapitel je Sprache in Zehner-Blöcke zum Herunterladen", async () => {
    const s = setup();
    await openCatalogSeries(
      { db: s.db, client: s.client, coverDir: s.coverDir, now: () => s.now.t },
      SERIES,
    );
    const arcs = s.db
      .prepare(
        "SELECT id, title, arc_order AS o FROM manga_arcs WHERE series_id = ? ORDER BY arc_order",
      )
      .all(SERIES) as { id: string; title: string; o: number }[];
    // Deutsch: Kapitel 1 und 47 → zwei Blöcke; jeder Block trägt seine Sprache in der Kennung.
    const de = arcs.filter((a) => a.id.includes(":de:"));
    expect(de.map((a) => a.id)).toEqual([`${SERIES}:de:arc-1`, `${SERIES}:de:arc-5`]);
    expect(de.map((a) => a.title)).toEqual(["Kapitel 1", "Kapitel 47"]);
    expect(
      arcs.some((a) => a.id.includes(":en:") && /^Kapitel \d+(\.\d+)?–\d+(\.\d+)?$/.test(a.title)),
    ).toBe(true);
    // Jedes Kapitel hängt an genau dem Block seiner Sprache.
    const orphans = s.db
      .prepare(
        "SELECT COUNT(*) AS n FROM manga_chapters WHERE series_id = ? AND (arc_id IS NULL OR arc_id NOT LIKE '%:' || lang || ':arc-%')",
      )
      .get(SERIES) as { n: number };
    expect(orphans.n).toBe(0);
    // Englische und deutsche Blöcke haben verschiedene Ordnungen (eigener Ordner auf dem Handy).
    expect(new Set(arcs.map((a) => a.o)).size).toBe(arcs.length);
  });

  it("frisch geöffnet: kein neuer Feed; nach 6 h schon", async () => {
    const s = setup();
    const deps = { db: s.db, client: s.client, coverDir: s.coverDir, now: () => s.now.t };
    await openCatalogSeries(deps, SERIES);
    // Ein Feed-Abruf = Seite ab offset 0 (der Feed hat 102 Einträge, die Attrappe liefert 60).
    const feeds = () =>
      s.client.calls.filter((c) => c.path.endsWith("/feed") && c.query?.offset === 0).length;
    expect(feeds()).toBe(1);
    s.now.t += 60 * 60 * 1000;
    await openCatalogSeries(deps, SERIES);
    expect(feeds()).toBe(1);
    s.now.t += 6 * 60 * 60 * 1000;
    await openCatalogSeries(deps, SERIES);
    expect(feeds()).toBe(2);
  });

  it("deutscher Titel geht vor", async () => {
    const s = setup();
    const ku = LIST.data.find((m) => m.id.startsWith("e18fe8c6")) as MdManga;
    upsertCatalog(s.db, catalogFromManga(ku, 1));
    const client = fakeClient({
      routes: [["/manga/", () => ({ result: "ok", data: [], limit: 500, offset: 0, total: 0 })]],
    });
    const id = `mangadex:${ku.id}`;
    await openCatalogSeries({ db: s.db, client, coverDir: s.coverDir }, id);
    const t = s.db.prepare("SELECT title FROM manga_series WHERE id = ?").get(id) as {
      title: string;
    };
    expect(t.title).toBe("Die Tagebücher der Apothekerin - Geheimnisse am Kaiserhof");
  });
});

describe("MangaDex-Adapter", () => {
  it("fetchChapterPages: at-home → Seitenadressen (data, nicht data-saver)", async () => {
    const s = setup();
    const adapter = createMangadexAdapter(s.client);
    const pages = await adapter.fetchChapterPages(DE_CH1);
    expect(pages).toHaveLength(37);
    expect(pages[0]).toEqual({
      pageNumber: 1,
      sourceUrl: `${AT_HOME.baseUrl}/data/${AT_HOME.chapter.hash}/${AT_HOME.chapter.data[0]}`,
    });
    expect(s.atHomeCalls).toEqual([`/at-home/server/${DE_CH1}`]);
  });

  it("gehört nicht zum globalen Abgleich", () => {
    const adapter = createMangadexAdapter(fakeClient({}));
    expect(adapter.catalogOnly).toBe(true);
    expect(onePieceTubeAdapter.catalogOnly).toBeFalsy();
  });

  it("Seite scheitert → einmal mit frischem Server, Erfolg und Fehler werden gemeldet", async () => {
    const s = setup();
    const client = fakeClient({
      routes: [
        [
          "/at-home/server/",
          () => {
            s.atHomeCalls.push("x");
            return s.atHomeCalls.length === 1
              ? AT_HOME
              : { ...AT_HOME, baseUrl: "https://frisch.mangadex.network" };
          },
        ],
      ],
      images: (url) =>
        url.startsWith(AT_HOME.baseUrl)
          ? { status: 502 }
          : { status: 200, body: Buffer.alloc(1234, 1), headers: { "x-cache": "HIT from x" } },
    });
    const adapter = createMangadexAdapter(client);
    const pages = await adapter.fetchChapterPages(DE_CH1);
    const first = pages[0] as { sourceUrl: string };
    const res = await adapter.downloadPage?.({
      sourceUrl: first.sourceUrl,
      baseDir: s.mangaDir,
      relativePath: "mangadex/x/de/1/01.jpg",
    });
    expect(res).toEqual({ relativePath: "mangadex/x/de/1/01.jpg", bytes: 1234 });
    expect(client.imageCalls[1]).toBe(
      `https://frisch.mangadex.network/data/${AT_HOME.chapter.hash}/${AT_HOME.chapter.data[0]}`,
    );
    expect(client.reports).toHaveLength(2);
    expect(client.reports[0]).toMatchObject({
      url: first.sourceUrl,
      success: false,
      cached: false,
    });
    expect(client.reports[1]).toMatchObject({ success: true, bytes: 1234, cached: true });
    expect(typeof client.reports[1]?.duration).toBe("number");
  });

  it("Bilder von mangadex.org selbst werden nicht gemeldet", async () => {
    const s = setup();
    const client = fakeClient({
      routes: [
        ["/at-home/server/", () => ({ ...AT_HOME, baseUrl: "https://uploads.mangadex.org" })],
      ],
    });
    const adapter = createMangadexAdapter(client);
    const pages = await adapter.fetchChapterPages(DE_CH1);
    await adapter.downloadPage?.({
      sourceUrl: (pages[0] as { sourceUrl: string }).sourceUrl,
      baseDir: s.mangaDir,
      relativePath: "a/01.jpg",
    });
    expect(client.reports).toHaveLength(0);
  });
});

describe("Kapitel lesen (POST /api/manga/chapters/:id/sync)", () => {
  let s: ReturnType<typeof setup>;
  let app: FastifyInstance;
  beforeEach(async () => {
    s = setup();
    await openCatalogSeries({ db: s.db, client: s.client, coverDir: s.coverDir }, SERIES);
    const adapter = createMangadexAdapter(s.client);
    app = Fastify();
    registerMangaRoutes(app, {
      db: s.db,
      mangaDir: s.mangaDir,
      coverDir: s.coverDir,
      adapterFor: (id) => (id === "mangadex" ? adapter : undefined),
    });
    registerMangaCatalogRoutes(app, { db: s.db, client: s.client, coverDir: s.coverDir });
  });

  it("lädt die Seiten wie bei onepiece.tube, Ordner ohne Doppelpunkt", async () => {
    const chapterId = `${SERIES}:de:1`;
    const r = await app.inject({ method: "POST", url: `/api/manga/chapters/${chapterId}/sync` });
    expect(r.statusCode).toBe(202);
    // Hintergrundlauf abwarten.
    for (let i = 0; i < 100; i++) {
      const done = s.db
        .prepare(
          "SELECT COUNT(*) AS n FROM manga_pages WHERE chapter_id = ? AND local_path IS NOT NULL",
        )
        .get(chapterId) as { n: number };
      if (done.n === 37) break;
      await new Promise((res) => setTimeout(res, 10));
    }
    const pages = (await app.inject(`/api/manga/chapters/${chapterId}/pages`)).json() as {
      id: string;
      ready: boolean;
    }[];
    expect(pages).toHaveLength(37);
    expect(pages.every((p) => p.ready)).toBe(true);
    expect(pages[0]?.id).toBe(`${chapterId}:01`);
    const p1 = s.db
      .prepare("SELECT local_path AS p FROM manga_pages WHERE id = ?")
      .get(`${chapterId}:01`) as { p: string };
    expect(p1.p).toBe(`mangadex/${UUID}/de/1/01.jpg`);
    expect(existsSync(join(s.mangaDir, p1.p))).toBe(true);
  });

  it("direkt über runChapterSync mit Kapitel-Kennung als Slug", async () => {
    const adapter = createMangadexAdapter(s.client);
    await runChapterSync({
      db: s.db,
      adapter,
      seriesSlug: `${UUID}:de`,
      chapterNumber: 47,
      chapterUrl: DE_CH1,
      mangaDir: s.mangaDir,
    });
    const n = s.db
      .prepare("SELECT page_count AS n FROM manga_chapters WHERE id = ?")
      .get(`${SERIES}:de:47`) as { n: number };
    expect(n.n).toBe(37);
  });

  it("Reihen-Detail: lang + scanlationGroup je Kapitel, langs der Reihe", async () => {
    const r = (await app.inject(`/api/manga/series/${SERIES}`)).json() as {
      langs: string[];
      chapters: { id: string; lang: string; scanlationGroup: string | null }[];
    };
    expect(r.langs).toEqual(["de", "en"]);
    const c = r.chapters.find((x) => x.id === `${SERIES}:de:1`);
    expect(c).toMatchObject({ lang: "de", scanlationGroup: "Black Poison" });
  });

  it("globaler Abgleich lässt MangaDex aus", async () => {
    const r = await app.inject({
      method: "POST",
      url: "/api/manga/sync",
      payload: { sourceId: "mangadex" },
    });
    expect(r.statusCode).toBe(400);
    expect(s.client.calls.filter((c) => c.path.startsWith("/manga")).length).toBe(2);
  });

  it("POST /api/manga/catalog/:id/open liefert die Reihen-Kennung, Unbekanntes 404", async () => {
    const r = await app.inject({ method: "POST", url: `/api/manga/catalog/${SERIES}/open` });
    expect(r.statusCode).toBe(200);
    expect(r.json()).toEqual({ seriesId: SERIES });
    const x = await app.inject({ method: "POST", url: "/api/manga/catalog/mangadex:nope/open" });
    expect(x.statusCode).toBe(404);
  });
});
