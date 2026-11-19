import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import {
  type MdAtHome,
  type MdChapterEntry,
  type MdManga,
  catalogFromManga,
  chaptersFromFeed,
  mapKind,
  mapTags,
  pageUrls,
} from "../../src/manga/mangadex/map.js";

const fx = <T>(n: string): T =>
  JSON.parse(readFileSync(join(__dirname, "../fixtures/manga/mangadex", n), "utf8")) as T;
const list = fx<{ data: MdManga[] }>("manga-list-de.json").data;
const byId = (prefix: string) => list.find((m) => m.id.startsWith(prefix)) as MdManga;
const tag = (en: string, group = "genre") => ({
  id: en,
  type: "tag",
  attributes: { name: { en }, group },
});

describe("mapTags", () => {
  it("übersetzt Genres und Themen ins Deutsche, ignoriert den Rest", () => {
    const tags = [
      "Action",
      "Adventure",
      "Romance",
      "Comedy",
      "Slice of Life",
      "Psychological",
      "Historical",
      "School Life",
      "Ghosts",
      "Demons",
      "Monsters",
      "Award Winning",
      "Long Strip",
      "Gore",
    ].map((t) => tag(t));
    expect(mapTags(tags)).toEqual([
      "Action",
      "Abenteuer",
      "Romantik",
      "Komödie",
      "Alltag",
      "Psycho",
      "Historisch",
      "Schule",
      "Geister",
      "Dämonen",
      "Monster",
    ]);
  });

  it("Isekai und Reincarnation werden beide zu einem »Isekai«", () => {
    expect(mapTags([tag("Reincarnation", "theme"), tag("Isekai")])).toEqual(["Isekai"]);
  });

  it("echte Daten: Kage no Jitsuryokusha", () => {
    expect(mapTags(byId("77bee52c").attributes.tags)).toEqual([
      "Action",
      "Komödie",
      "Drama",
      "Fantasy",
      "Schule",
      "Isekai",
      "Dämonen",
    ]);
  });
});

describe("mapKind", () => {
  it("Originalsprache → Art", () => {
    expect(mapKind("ja")).toBe("Manga");
    expect(mapKind("ko")).toBe("Manhwa");
    expect(mapKind("zh")).toBe("Manhua");
    expect(mapKind("zh-hk")).toBe("Manhua");
    expect(mapKind("en")).toBe("Webcomic");
    expect(mapKind("fr")).toBe("Webcomic");
  });
});

describe("catalogFromManga", () => {
  it("Kusuriya: deutscher Zweittitel, englischer Titel, Autoren, Cover, Sprachen", () => {
    const row = catalogFromManga(byId("e18fe8c6"), 1000);
    expect(row.id).toBe("mangadex:e18fe8c6-f6dc-4f05-8462-7b2083ff9a6c");
    expect(row.source).toBe("mangadex");
    expect(row.title).toBe("The Apothecary Diaries");
    expect(row.titleDe).toBe("Die Tagebücher der Apothekerin - Geheimnisse am Kaiserhof");
    expect(row.authors).toEqual(["Hyuuga Natsu", "Nanao Itsuki", "Nekokurage"]);
    expect(row.kind).toBe("Manga");
    expect(row.langs).toEqual(["de", "en"]);
    expect(row.status).toBe("ongoing");
    expect(row.year).toBe(2017);
    expect(row.contentRating).toBe("suggestive");
    expect(row.lastChapter).toBeNull();
    expect(row.coverUrl).toBe(
      "https://uploads.mangadex.org/covers/e18fe8c6-f6dc-4f05-8462-7b2083ff9a6c/530a4ffb-87cb-4156-bdeb-61670532e20f.jpg.512.jpg",
    );
    expect(row.tags).toEqual(["Romantik", "Drama", "Mystery", "Historisch"]);
    expect(row.updatedAt).toBe(Date.parse("2026-07-21T16:06:22+00:00"));
    expect(row.syncedAt).toBe(1000);
    // Suchtext enthält Original-, deutschen und Zweittitel sowie Autoren — normalisiert.
    expect(row.searchText).toContain("kusuriya no hitorigoto");
    expect(row.searchText).toContain("die tagebucher der apothekerin");
    expect(row.searchText).toContain("hyuuga natsu");
  });

  it("Solo Leveling: koreanisch → Manhwa, englischer Zweittitel, deutsche Beschreibung gekürzt", () => {
    const row = catalogFromManga(byId("32d76d19"), 1);
    expect(row.title).toBe("Solo Leveling");
    expect(row.titleDe).toBeNull();
    expect(row.kind).toBe("Manhwa");
    expect(row.lastChapter).toBe("200");
    expect(row.description?.startsWith("Als sich vor zehn Jahren")).toBe(true);
    expect(row.description?.length).toBeLessThanOrEqual(600);
    expect(row.description?.endsWith("…")).toBe(true);
    // Doppelte Namen (Autor = Zeichner) nur einmal.
    expect(new Set(row.authors).size).toBe(row.authors.length);
  });

  it("ohne englischen Titel bleibt der Originaltitel; Leerzeichen an Namen weg", () => {
    const row = catalogFromManga(byId("d7037b2a"), 1);
    expect(row.title).toBe("Welcome to Demon School! Iruma-kun");
    expect(row.authors).toEqual(["Nishi Osamu"]);
    const m = byId("d7037b2a");
    const bare = {
      ...m,
      attributes: { ...m.attributes, altTitles: [], description: { en: "Erster.\n\nZweiter." } },
    };
    const r2 = catalogFromManga(bare, 1);
    expect(r2.title).toBe("Mairimashita! Iruma-kun");
    expect(r2.description).toBe("Erster.");
  });

  it("Markdown-Links in Beschreibungen werden zu reinem Text", () => {
    const m = byId("d7037b2a");
    const md = {
      ...m,
      attributes: {
        ...m.attributes,
        description: { en: "Siehe **[Seite](https://x.test)** hier." },
      },
    };
    expect(catalogFromManga(md, 1).description).toBe("Siehe Seite hier.");
  });
});

describe("chaptersFromFeed", () => {
  const kage = fx<{ data: MdChapterEntry[] }>("feed-kage.json").data;

  it("Kapitel je Sprache, doppelte Gruppen aufgelöst (mehr Seiten, dann neuer)", () => {
    const ch = chaptersFromFeed(kage);
    const de = ch.filter((c) => c.lang === "de");
    expect(de.map((c) => c.number)).toEqual([1, 47]);
    expect(de[0]?.group).toBe("Black Poison");
    expect(de[0]?.uuid).toBe("ea4cdf47-9092-4514-a76f-1eadfdf9f8db");

    const en = ch.filter((c) => c.lang === "en");
    const keys = en.map((c) => c.number);
    expect(new Set(keys).size).toBe(keys.length);
    // 39: Weeaboo hat 36 Seiten, Biamam 35.
    expect(en.find((c) => c.number === 39)?.group).toBe("Weeaboo Scans");
    // 40: gleich viele Seiten → das neuere (Weeaboo, 30.04.).
    expect(en.find((c) => c.number === 40)?.uuid.startsWith("ab37b160")).toBe(true);
    // 50: 33 Seiten schlägt 32.
    expect(en.find((c) => c.number === 50)?.uuid.startsWith("f7170eaf")).toBe(true);
    expect(en.find((c) => c.number === 10.1)?.pages).toBe(21);
    expect(en.find((c) => c.number === 1)?.publishedAt).toBe(
      Date.parse("2018-12-30T12:51:36+00:00"),
    );
  });

  it("nach Sprache und Nummer sortiert", () => {
    const ch = chaptersFromFeed(kage);
    const en = ch.filter((c) => c.lang === "en").map((c) => c.number);
    expect(en).toEqual([...en].sort((a, b) => a - b));
  });

  it("externe Kapitel und Kapitel ohne Seiten fallen weg (Kusuriya ist lizenziert)", () => {
    const ku = fx<{ data: MdChapterEntry[] }>("feed-kusuriya.json").data;
    expect(ku.length).toBeGreaterThan(0);
    expect(chaptersFromFeed(ku)).toEqual([]);
  });

  it("nicht verfügbare und nicht-numerische Kapitel fallen weg, Oneshot = 0", () => {
    const base = kage[0] as MdChapterEntry;
    const mk = (chapter: string | null, extra: object = {}) => ({
      ...base,
      id: `id-${chapter}`,
      attributes: { ...base.attributes, chapter, ...extra },
    });
    const ch = chaptersFromFeed([mk(null), mk("Extra"), mk("5", { isUnavailable: true })]);
    expect(ch.map((c) => c.number)).toEqual([0]);
  });
});

describe("pageUrls", () => {
  it("baut ${baseUrl}/data/${hash}/${datei} aus der at-home-Antwort", () => {
    const ah = fx<MdAtHome>("at-home.json");
    const urls = pageUrls(ah);
    expect(urls).toHaveLength(37);
    expect(urls[0]).toBe(
      "https://cmdxd98sb0x3yprd.mangadex.network/data/227da3038eeaede79c7a7ff49349fa18/1-9fe5778433ad4a64e83b5c7a0d9b73b7e35f650535ac41e7d68f9c50fddb0fc5.jpg",
    );
  });
});
