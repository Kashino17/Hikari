import { existsSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import Database from "better-sqlite3";
import { describe, expect, it } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import {
  type MangaCandidate,
  enrichMangaCovers,
  ensureMangaCover,
  isMangaCoverFile,
  mangaCoverFile,
  pickManga,
} from "./cover.js";
import { upsertSeries } from "./persist.js";

const IMAGE = Buffer.alloc(8_000, 1);
const DAY = 24 * 60 * 60 * 1000;

/** Zählt die Aufrufe je Adresse mit; unbekannte Adressen antworten mit 404. */
function fakeFetch(routes: Record<string, unknown>) {
  const calls: string[] = [];
  const impl = (async (input: string | URL | Request) => {
    const url = String(input);
    calls.push(url);
    const key = Object.keys(routes).find((k) => url.includes(k));
    if (!key) return new Response("not found", { status: 404 });
    const body = routes[key];
    if (body instanceof Error) throw body;
    if (typeof body === "number") return new Response("fehler", { status: body });
    if (Buffer.isBuffer(body)) return new Response(body, { status: 200 });
    return new Response(JSON.stringify(body), { status: 200 });
  }) as typeof fetch;
  return { impl, calls };
}

const aniList = (media: object[]) => ({ data: { Page: { media } } });
const onePiece = {
  format: "MANGA",
  title: { romaji: "ONE PIECE", english: "One Piece", native: "ONE PIECE" },
  synonyms: ["원피스"],
  coverImage: { extraLarge: "https://img.example/anilist-one-piece.jpg" },
};

function setup() {
  const db = new Database(":memory:");
  applyMigrations(db);
  const id = upsertSeries(db, {
    source: "onepiecetube",
    sourceSlug: "one-piece",
    title: "One Piece",
    sourceUrl: "https://example.org/one-piece",
  });
  return { db, id, coverDir: mkdtempSync(join(tmpdir(), "hikari-manga-cover-")) };
}

const row = (db: Database.Database, id: string) =>
  db.prepare("SELECT cover_path, cover_checked_at FROM manga_series WHERE id = ?").get(id) as {
    cover_path: string | null;
    cover_checked_at: number | null;
  };

describe("Auswahl des Treffers", () => {
  const c = (names: string[], extra: Partial<MangaCandidate> = {}): MangaCandidate => ({
    names,
    aliases: [],
    cover: "x",
    series: true,
    ...extra,
  });

  it("nimmt nur die Reihe mit genau passendem Namen", () => {
    const hit = pickManga("One Piece", [c(["One Piece Party"]), c(["ONE PIECE", "One Piece"])]);
    expect(hit?.names).toEqual(["ONE PIECE", "One Piece"]);
    expect(pickManga("One Piece", [c(["One Piece Party"])])).toBeNull();
  });

  it("zieht den Haupttitel einem Zweitnamen vor, auch wenn der weiter vorn steht", () => {
    const hit = pickManga("One Piece", [
      c(["Wan Piece"], { aliases: ["One Piece"], cover: "falsch" }),
      c(["One Piece"], { cover: "richtig" }),
    ]);
    expect(hit?.cover).toBe("richtig");
  });

  it("greift auf Zweitnamen zurück, wenn kein Haupttitel passt", () => {
    const hit = pickManga("Attack on Titan", [
      c(["Shingeki no Kyojin"], { aliases: ["Attack on Titan"] }),
    ]);
    expect(hit?.names).toEqual(["Shingeki no Kyojin"]);
  });

  it("zieht die fortlaufende Reihe dem gleichnamigen Roman vor", () => {
    const hit = pickManga("Monster", [
      c(["Monster"], { series: false, cover: "roman" }),
      c(["Monster"], { cover: "manga" }),
    ]);
    expect(hit?.cover).toBe("manga");
  });

  it("überspringt Treffer ohne Bild", () => {
    expect(pickManga("One Piece", [c(["One Piece"], { cover: null })])).toBeNull();
  });
});

describe("Dateinamen", () => {
  it("lässt nur eigene Cover-Dateien zu", () => {
    expect(mangaCoverFile("onepiecetube:one-piece")).toBe("manga_onepiecetube_one-piece.jpg");
    expect(isMangaCoverFile("manga_onepiecetube_one-piece.jpg")).toBe(true);
    expect(isMangaCoverFile("poster_abc.jpg")).toBe(false);
    expect(isMangaCoverFile("manga_../../etc.jpg")).toBe(false);
    expect(isMangaCoverFile("manga_a.jpg/../x")).toBe(false);
  });
});

describe("Titelbild besorgen", () => {
  it("holt das Cover von AniList und trägt es ein", async () => {
    const { db, id, coverDir } = setup();
    const { impl, calls } = fakeFetch({
      "graphql.anilist.co": aniList([onePiece]),
      "img.example": IMAGE,
    });

    expect(await ensureMangaCover({ db, coverDir, fetchImpl: impl, now: 1000 * DAY }, id)).toBe(
      true,
    );

    expect(row(db, id)).toEqual({
      cover_path: "manga_onepiecetube_one-piece.jpg",
      cover_checked_at: 1000 * DAY,
    });
    expect(existsSync(join(coverDir, "manga_onepiecetube_one-piece.jpg"))).toBe(true);
    expect(calls.some((u) => u.includes("kitsu.io"))).toBe(false);
  });

  it("fragt Kitsu, wenn AniList die Reihe nicht kennt", async () => {
    const { db, id, coverDir } = setup();
    const { impl } = fakeFetch({
      "graphql.anilist.co": aniList([]),
      "kitsu.io": {
        data: [
          {
            attributes: {
              subtype: "manga",
              canonicalTitle: "One Piece",
              titles: { en: "One Piece" },
              posterImage: { original: "https://img.example/kitsu.jpg" },
            },
          },
        ],
      },
      "img.example": IMAGE,
    });

    expect(await ensureMangaCover({ db, coverDir, fetchImpl: impl }, id)).toBe(true);
    expect(row(db, id).cover_path).toBe("manga_onepiecetube_one-piece.jpg");
  });

  it("tut nichts, wenn schon ein Cover da ist", async () => {
    const { db, id, coverDir } = setup();
    db.prepare("UPDATE manga_series SET cover_path = 'manga_eigenes.jpg' WHERE id = ?").run(id);
    const { impl, calls } = fakeFetch({});

    expect(await ensureMangaCover({ db, coverDir, fetchImpl: impl }, id)).toBeNull();
    expect(calls).toEqual([]);
    expect(row(db, id).cover_path).toBe("manga_eigenes.jpg");
  });

  it("merkt sich eine erfolglose Suche und fragt erst nach zwei Wochen wieder", async () => {
    const { db, id, coverDir } = setup();
    const { impl, calls } = fakeFetch({
      "graphql.anilist.co": aniList([]),
      "kitsu.io": { data: [] },
    });
    const opts = { db, coverDir, fetchImpl: impl };

    expect(await ensureMangaCover({ ...opts, now: 1000 * DAY }, id)).toBe(false);
    expect(row(db, id)).toEqual({ cover_path: null, cover_checked_at: 1000 * DAY });
    const afterFirst = calls.length;

    expect(await ensureMangaCover({ ...opts, now: 1005 * DAY }, id)).toBeNull();
    expect(calls.length).toBe(afterFirst);

    expect(await ensureMangaCover({ ...opts, now: 1015 * DAY }, id)).toBe(false);
    expect(calls.length).toBeGreaterThan(afterFirst);
  });

  it("vermerkt nichts, wenn ein Dienst ausfällt und der andere nichts findet", async () => {
    const { db, id, coverDir } = setup();
    const { impl } = fakeFetch({ "graphql.anilist.co": 429, "kitsu.io": { data: [] } });

    await expect(ensureMangaCover({ db, coverDir, fetchImpl: impl }, id)).rejects.toThrow(
      "AniList HTTP 429",
    );
    expect(row(db, id)).toEqual({ cover_path: null, cover_checked_at: null });
  });

  it("vermerkt nichts, wenn das gefundene Bild nicht ladbar ist", async () => {
    const { db, id, coverDir } = setup();
    const { impl } = fakeFetch({ "graphql.anilist.co": aniList([onePiece]), "img.example": 503 });

    await expect(ensureMangaCover({ db, coverDir, fetchImpl: impl }, id)).rejects.toThrow(
      "cover download failed",
    );
    expect(row(db, id)).toEqual({ cover_path: null, cover_checked_at: null });
  });
});

describe("Sammellauf", () => {
  it("fasst nur Reihen ohne Cover an und übersteht einen Netzfehler", async () => {
    const { db, id, coverDir } = setup();
    const hat = upsertSeries(db, {
      source: "x",
      sourceSlug: "naruto",
      title: "Naruto",
      sourceUrl: "u",
      coverPath: "manga_x_naruto.jpg",
    });
    const offline = fakeFetch({
      "graphql.anilist.co": new Error("netz weg"),
      "kitsu.io": new Error("netz weg"),
    });
    expect(await enrichMangaCovers({ db, coverDir, fetchImpl: offline.impl })).toEqual({
      checked: 1,
      found: 0,
    });
    expect(row(db, id).cover_checked_at).toBeNull();

    const online = fakeFetch({ "graphql.anilist.co": aniList([onePiece]), "img.example": IMAGE });
    expect(await enrichMangaCovers({ db, coverDir, fetchImpl: online.impl })).toEqual({
      checked: 1,
      found: 1,
    });
    expect(row(db, hat).cover_path).toBe("manga_x_naruto.jpg");

    // Jetzt meldet sich keine Reihe mehr — der Lauf fragt niemanden.
    const still = fakeFetch({});
    expect(await enrichMangaCovers({ db, coverDir, fetchImpl: still.impl })).toEqual({
      checked: 0,
      found: 0,
    });
    expect(still.calls).toEqual([]);
  });
});
