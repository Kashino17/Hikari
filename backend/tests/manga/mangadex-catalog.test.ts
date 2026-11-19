import { existsSync, mkdtempSync, readdirSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import Database from "better-sqlite3";
import Fastify, { type FastifyInstance } from "fastify";
import { beforeEach, describe, expect, it } from "vitest";
import { registerMangaCatalogRoutes } from "../../src/api/manga-catalog.js";
import { applyMigrations } from "../../src/db/migrations.js";
import {
  catalogHome,
  catalogLastRun,
  checkReadableChapters,
  listCatalog,
  normalizeSearch,
  runMangaCatalogSync,
  shouldSyncCatalogOnStart,
  upsertCatalog,
} from "../../src/manga/mangadex/catalog.js";
import { type MdManga, catalogFromManga } from "../../src/manga/mangadex/map.js";
import { JPEG, fakeClient, fx } from "./mangadex-fake.js";

const LIST = fx<{ data: MdManga[]; total: number }>("manga-list-de.json");
const STATS = fx<unknown>("statistics.json");
const KUSURIYA = "mangadex:e18fe8c6-f6dc-4f05-8462-7b2083ff9a6c";

/** Eine Reihe nur auf Englisch (aus Chainsaw Man abgeleitet). */
function englishOnly(): MdManga {
  const m = LIST.data.find((x) => x.id.startsWith("a77742b1")) as MdManga;
  return {
    ...m,
    id: "00000000-0000-4000-8000-000000000001",
    attributes: {
      ...m.attributes,
      title: { en: "Nur Englisch Ärger" },
      altTitles: [],
      availableTranslatedLanguages: ["en", "fr"],
      originalLanguage: "ko",
      status: "completed",
    },
  };
}

function listRoute(byLang: Record<string, MdManga[]>) {
  return (q: Record<string, unknown> | undefined) => {
    const lang = (q?.["availableTranslatedLanguage[]"] as string[])[0] as string;
    const all = byLang[lang] ?? [];
    const offset = Number(q?.offset ?? 0);
    const limit = Number(q?.limit ?? 100);
    return {
      result: "ok",
      data: all.slice(offset, offset + limit),
      limit,
      offset,
      total: all.length,
    };
  };
}

describe("runMangaCatalogSync", () => {
  let db: Database.Database;
  beforeEach(() => {
    db = new Database(":memory:");
    applyMigrations(db);
  });

  it("holt beide Sprachen, schreibt Zeilen mit Beliebtheit und merkt sich den Lauf", async () => {
    const client = fakeClient({
      routes: [
        ["/manga", listRoute({ de: LIST.data, en: [...LIST.data.slice(2), englishOnly()] })],
        ["/statistics/manga", () => STATS],
      ],
    });
    expect(shouldSyncCatalogOnStart(db, 5000)).toBe(true);
    const r = await runMangaCatalogSync({ db, client, now: () => 5000 });
    expect(r).toMatchObject({ de: 8, en: 7, total: 9 });

    const n = db.prepare("SELECT COUNT(*) AS n FROM manga_catalog").get() as { n: number };
    expect(n.n).toBe(9);
    const ku = db.prepare("SELECT * FROM manga_catalog WHERE id = ?").get(KUSURIYA) as Record<
      string,
      unknown
    >;
    expect(ku.popularity).toBe(143035);
    expect(ku.title_de).toBe("Die Tagebücher der Apothekerin - Geheimnisse am Kaiserhof");
    expect(JSON.parse(ku.langs_json as string)).toEqual(["de", "en"]);
    expect(catalogLastRun(db)).toBe(5000);
    expect(shouldSyncCatalogOnStart(db, 5000 + 60_000)).toBe(false);
    expect(shouldSyncCatalogOnStart(db, 5000 + 25 * 3600_000)).toBe(true);

    // Anfrage wie in der Doku: sortiert nach Followern, nur jugendfrei/anzüglich, mit Cover+Autoren.
    const first = client.calls.find((c) => c.path === "/manga");
    expect(first?.query).toMatchObject({
      "availableTranslatedLanguage[]": ["de"],
      "order[followedCount]": "desc",
      "includes[]": ["cover_art", "author", "artist"],
      "contentRating[]": ["safe", "suggestive"],
      hasAvailableChapters: "true",
      limit: 100,
      offset: 0,
    });
    // Statistik gebündelt: ein Aufruf je Seite, kein Titel doppelt gefragt.
    const stats = client.calls.filter((c) => c.path === "/statistics/manga");
    expect(stats).toHaveLength(2);
    expect((stats[1]?.query?.["manga[]"] as string[]).length).toBe(1);
  });

  it("blättert seitenweise und hält sich an die Obergrenze je Sprache", async () => {
    const client = fakeClient({
      routes: [
        ["/manga", listRoute({ de: LIST.data, en: [] })],
        ["/statistics/manga", () => ({ result: "ok", statistics: {} })],
      ],
    });
    const r = await runMangaCatalogSync({ db, client, pageSize: 3, maxPerLang: 5 });
    expect(r.de).toBe(5);
    const offsets = client.calls
      .filter(
        (c) =>
          c.path === "/manga" &&
          (c.query?.["availableTranslatedLanguage[]"] as string[])[0] === "de",
      )
      .map((c) => [c.query?.offset, c.query?.limit]);
    expect(offsets).toEqual([
      [0, 3],
      [3, 2],
    ]);
  });

  it("verschwundene Titel bleiben, bekommen aber missing_since; Wiederkehr löscht die Marke", async () => {
    const routes = (de: MdManga[]): Array<[string, (q: never) => unknown]> => [
      ["/manga", listRoute({ de, en: [] })],
      ["/statistics/manga", () => ({ result: "ok", statistics: {} })],
    ];
    await runMangaCatalogSync({
      db,
      client: fakeClient({ routes: routes(LIST.data) }),
      now: () => 1,
    });
    await runMangaCatalogSync({
      db,
      client: fakeClient({ routes: routes(LIST.data.filter((m) => !m.id.startsWith("e18fe8c6"))) }),
      now: () => 2,
    });
    const miss = () =>
      (
        db.prepare("SELECT missing_since AS m FROM manga_catalog WHERE id = ?").get(KUSURIYA) as {
          m: number | null;
        }
      ).m;
    expect(miss()).toBe(2);
    await runMangaCatalogSync({
      db,
      client: fakeClient({ routes: routes(LIST.data) }),
      now: () => 3,
    });
    expect(miss()).toBeNull();
  });

  it("ein Fehler mittendrin markiert nichts als verschwunden und meldet den Fehler", async () => {
    await runMangaCatalogSync({
      db,
      client: fakeClient({
        routes: [
          ["/manga", listRoute({ de: LIST.data, en: [] })],
          ["/statistics/manga", () => ({ result: "ok", statistics: {} })],
        ],
      }),
      now: () => 1,
    });
    const r = await runMangaCatalogSync({
      db,
      client: fakeClient({ routes: [] }),
      now: () => 2,
    });
    expect(r.error).toMatch(/404/);
    const n = db
      .prepare("SELECT COUNT(*) AS n FROM manga_catalog WHERE missing_since IS NOT NULL")
      .get() as { n: number };
    expect(n.n).toBe(0);
    expect(catalogLastRun(db)).toBe(1);
  });
});

describe("normalizeSearch", () => {
  it("klein, Umlaute und Akzente weg", () => {
    expect(normalizeSearch("Die TAGEBÜCHER – Pokémon Straße")).toBe(
      "die tagebucher pokemon strasse",
    );
  });
});

describe("/api/manga/catalog", () => {
  let db: Database.Database;
  let app: FastifyInstance;
  let coverDir: string;
  let client: ReturnType<typeof fakeClient>;

  beforeEach(() => {
    db = new Database(":memory:");
    applyMigrations(db);
    LIST.data.forEach((m, i) => {
      const row = catalogFromManga(m, 1);
      upsertCatalog(db, { ...row, popularity: 1000 - i });
    });
    upsertCatalog(db, { ...catalogFromManga(englishOnly(), 1), popularity: 5 });
    // Wie nach der Kapitel-Prüfung: jede gemeldete Sprache hat lesbare Kapitel.
    db.exec(`UPDATE manga_catalog SET readable_at = 1,
      readable_de = CASE WHEN langs_json LIKE '%"de"%' THEN 12 ELSE 0 END,
      readable_en = CASE WHEN langs_json LIKE '%"en"%' THEN 3 ELSE 0 END`);
    coverDir = mkdtempSync(join(tmpdir(), "mdcv-"));
    client = fakeClient({});
    app = Fastify();
    registerMangaCatalogRoutes(app, { db, client, coverDir });
  });

  type Card = {
    id: string;
    title: string;
    titleDe?: string;
    tags: string[];
    langs: string[];
    kind: string;
    status: string;
    coverUrl?: string;
    source: string;
    authors: string[];
  };

  it("list: Sprachfilter", async () => {
    const all = (await app.inject("/api/manga/catalog/list")).json() as { total: number };
    expect(all.total).toBe(9);
    const de = (await app.inject("/api/manga/catalog/list?lang=de")).json() as {
      total: number;
      items: Card[];
    };
    expect(de.total).toBe(8);
    expect(de.items.every((c) => c.langs.includes("de"))).toBe(true);
    const en = (await app.inject("/api/manga/catalog/list?lang=en")).json() as { items: Card[] };
    expect(en.items.some((c) => c.title === "Nur Englisch Ärger")).toBe(true);
  });

  it("list: Suche umlaut- und groß/klein-unabhängig über Titel, deutschen Titel, Autoren", async () => {
    for (const q of ["tagebücher", "TAGEBUCHER", "Tagebucher Apothekerin"]) {
      const r = (await app.inject(`/api/manga/catalog/list?q=${encodeURIComponent(q)}`)).json() as {
        items: Card[];
      };
      expect(r.items.map((c) => c.id)).toEqual([KUSURIYA]);
    }
    const ar = (await app.inject("/api/manga/catalog/list?q=arger")).json() as { items: Card[] };
    expect(ar.items.map((c) => c.title)).toEqual(["Nur Englisch Ärger"]);
    const author = (await app.inject("/api/manga/catalog/list?q=fujimoto")).json() as {
      items: Card[];
    };
    expect(author.items.map((c) => c.title).sort()).toEqual(["Chainsaw Man", "Nur Englisch Ärger"]);
    // Zweittitel zählen auch.
    const alt = (await app.inject("/api/manga/catalog/list?q=kusuriya")).json() as {
      items: Card[];
    };
    expect(alt.items.map((c) => c.id)).toEqual([KUSURIYA]);
  });

  it("list: Genre, Art, Status, Sortierung, Seiten", async () => {
    const isekai = (await app.inject("/api/manga/catalog/list?genre=Isekai")).json() as {
      items: Card[];
    };
    expect(isekai.items.map((c) => c.title).sort()).toEqual([
      "The Eminence in Shadow",
      "Welcome to Demon School! Iruma-kun",
    ]);
    const manhwa = (await app.inject("/api/manga/catalog/list?kind=Manhwa")).json() as {
      items: Card[];
    };
    expect(manhwa.items.map((c) => c.title).sort()).toEqual([
      "Nur Englisch Ärger",
      "Solo Leveling",
    ]);
    const done = (await app.inject("/api/manga/catalog/list?status=completed&lang=de")).json() as {
      items: Card[];
    };
    expect(done.items.every((c) => c.status === "completed")).toBe(true);
    expect(done.items).toHaveLength(4);
    const az = (await app.inject("/api/manga/catalog/list?sort=az&limit=2&offset=1")).json() as {
      total: number;
      items: Card[];
    };
    expect(az.total).toBe(9);
    expect(az.items.map((c) => c.title)).toEqual(["Jujutsu Kaisen", "Komi Can't Communicate"]);
    const pop = (await app.inject("/api/manga/catalog/list?limit=1")).json() as { items: Card[] };
    expect(pop.items[0]?.title).toBe("Solo Leveling");
  });

  it("Karte: Felder und Cover-Pfad über unseren Server", async () => {
    const r = (await app.inject("/api/manga/catalog/list?q=kusuriya")).json() as { items: Card[] };
    const c = r.items[0] as Card & { lastChapter: unknown };
    expect(c).toMatchObject({
      id: KUSURIYA,
      title: "The Apothecary Diaries",
      titleDe: "Die Tagebücher der Apothekerin - Geheimnisse am Kaiserhof",
      kind: "Manga",
      source: "mangadex",
      langs: ["de", "en"],
      authors: ["Hyuuga Natsu", "Nanao Itsuki", "Nekokurage"],
    });
    expect(c.coverUrl).toBe(`/api/manga/catalog/${encodeURIComponent(KUSURIYA)}/cover`);
  });

  it("home: Regale je Sprache gefiltert, leere Regale fehlen, Genres gezählt", async () => {
    const home = (await app.inject("/api/manga/catalog/home?lang=all")).json() as {
      shelves: { key: string; title: string; subtitle: string; items: Card[] }[];
      genres: { name: string; count: number }[];
    };
    const keys = home.shelves.map((s) => s.key);
    expect(keys).toEqual(["de", "popular", "completed", "romcom", "action", "isekai", "webtoon"]);
    const shelf = (k: string) =>
      home.shelves.find((s) => s.key === k) as { title: string; items: Card[] };
    expect(shelf("de").title).toBe("Auf Deutsch");
    expect(shelf("de").items).toHaveLength(8);
    expect(shelf("popular").items[0]?.title).toBe("Solo Leveling");
    expect(shelf("completed").items.every((c) => c.status === "completed")).toBe(true);
    expect(
      shelf("romcom").items.every((c) => c.tags.includes("Romantik") || c.tags.includes("Komödie")),
    ).toBe(true);
    expect(shelf("isekai").items).toHaveLength(2);
    expect(
      shelf("webtoon")
        .items.map((c) => c.kind)
        .sort(),
    ).toEqual(["Manhwa", "Manhwa"]);
    expect(home.genres[0]).toEqual({ name: "Action", count: 7 });

    const de = (await app.inject("/api/manga/catalog/home?lang=de")).json() as {
      shelves: { key: string; items: Card[] }[];
    };
    expect(de.shelves.every((s) => s.items.every((c) => c.langs.includes("de")))).toBe(true);
    expect(de.shelves.find((s) => s.key === "webtoon")?.items).toHaveLength(1);
  });

  it("Cover: einmal holen, auf Platte legen, danach von dort", async () => {
    const url = `/api/manga/catalog/${encodeURIComponent(KUSURIYA)}/cover`;
    const a = await app.inject(url);
    expect(a.statusCode).toBe(200);
    expect(a.headers["content-type"]).toBe("image/jpeg");
    expect(a.rawPayload.equals(JPEG)).toBe(true);
    const b = await app.inject(url);
    expect(b.statusCode).toBe(200);
    expect(client.imageCalls).toEqual([
      "https://uploads.mangadex.org/covers/e18fe8c6-f6dc-4f05-8462-7b2083ff9a6c/530a4ffb-87cb-4156-bdeb-61670532e20f.jpg.512.jpg",
    ]);
    expect(existsSync(join(coverDir, "manga-catalog"))).toBe(true);
    expect(readdirSync(join(coverDir, "manga-catalog"))).toHaveLength(1);
    expect((await app.inject("/api/manga/catalog/mangadex:nope/cover")).statusCode).toBe(404);
  });
});

describe("checkReadableChapters — nur wirklich lesbare Kapitel zählen", () => {
  it("zählt je Sprache ohne externe/leere Kapitel, versteckt Reihen ohne lesbare Kapitel", async () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    const rows = LIST.data.slice(0, 3).map((m, i) => {
      const row = catalogFromManga(m, 1);
      upsertCatalog(db, { ...row, langs: ["de", "en"], popularity: 100 - i });
      return row.id;
    });
    // Erste Reihe: nur 4 englische lesbar; zweite: nichts (lizenziert); dritte: 2 deutsche, 7 englische.
    const counts: Record<string, Record<string, number>> = {
      [rows[0]!]: { de: 0, en: 4 },
      [rows[1]!]: { de: 0, en: 0 },
      [rows[2]!]: { de: 2, en: 7 },
    };
    const client = fakeClient({
      routes: [
        [
          "/chapter",
          (q) => {
            expect(q?.includeExternalUrl).toBe(0);
            expect(q?.includeEmptyPages).toBe(0);
            const id = `mangadex:${q?.manga}`;
            const lang = (q?.["translatedLanguage[]"] as string[])[0]!;
            return { result: "ok", data: [], total: counts[id]?.[lang] ?? 0 };
          },
        ],
      ],
    });
    const n = await checkReadableChapters({ db, client, limit: 10 });
    expect(n).toBe(3);
    const list = listCatalog(db, {});
    expect(list.items.map((c) => c.id).sort()).toEqual([rows[0], rows[2]].sort());
    const first = list.items.find((c) => c.id === rows[0])!;
    expect(first.langs).toEqual(["en"]);
    expect(first.chapters).toEqual({ de: 0, en: 4 });
    expect(listCatalog(db, { lang: "de" }).items.map((c) => c.id)).toEqual([rows[2]]);
  });

  it("ungeprüfte Reihen erscheinen nicht; geprüfte werden erst nach einer Woche neu gezählt", async () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    const row = catalogFromManga(LIST.data[0]!, 1);
    upsertCatalog(db, { ...row, langs: ["en"], popularity: 1 });
    expect(listCatalog(db, {}).total).toBe(0);
    const client = fakeClient({
      routes: [["/chapter", () => ({ result: "ok", data: [], total: 5 })]],
    });
    await checkReadableChapters({ db, client, limit: 10, now: () => 1_000 });
    expect(listCatalog(db, {}).total).toBe(1);
    const before = client.calls.length;
    await checkReadableChapters({ db, client, limit: 10, now: () => 2_000 });
    expect(client.calls.length).toBe(before);
  });
});

describe("Regal „Auf Deutsch“", () => {
  it("zeigt nur Reihen mit mindestens 10 deutschen Kapiteln; die Liste mit lang=de alle", () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    LIST.data
      .slice(0, 2)
      .forEach((m, i) =>
        upsertCatalog(db, { ...catalogFromManga(m, 1), langs: ["de"], popularity: 10 - i }),
      );
    const [a, b] = LIST.data.slice(0, 2).map((m) => `mangadex:${m.id}`);
    db.prepare(
      "UPDATE manga_catalog SET readable_at = 1, readable_de = ?, readable_en = 0 WHERE id = ?",
    ).run(2, a);
    db.prepare(
      "UPDATE manga_catalog SET readable_at = 1, readable_de = ?, readable_en = 0 WHERE id = ?",
    ).run(25, b);
    const shelf = catalogHome(db, "all").shelves.find((s) => s.key === "de");
    expect(shelf?.items.map((c) => c.id)).toEqual([b]);
    expect(listCatalog(db, { lang: "de" }).total).toBe(2);
  });
});
