import { mkdtempSync, readdirSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import Database from "better-sqlite3";
import { describe, expect, it } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import { enrichArtwork, normalizeTitle, pickShow } from "./artwork.js";

const IMAGE = Buffer.alloc(8_000, 1);

function fakeFetch(routes: Record<string, unknown>): typeof fetch {
  return (async (input: string | URL | Request) => {
    const url = String(input);
    const key = Object.keys(routes).find((k) => url.includes(k));
    if (!key) return new Response("not found", { status: 404 });
    const body = routes[key];
    if (Buffer.isBuffer(body)) return new Response(body, { status: 200 });
    return new Response(JSON.stringify(body), { status: 200 });
  }) as typeof fetch;
}

function setup() {
  const db = new Database(":memory:");
  applyMigrations(db);
  db.prepare("INSERT INTO channels (id,url,title,added_at) VALUES ('manual','x','Manuell',0)").run();
  return { db, coverDir: mkdtempSync(join(tmpdir(), "hikari-art-")) };
}

describe("Titelabgleich", () => {
  it("ignoriert Groß-/Kleinschreibung, Leer- und Sonderzeichen", () => {
    expect(normalizeTitle("Dragon Ball: Super!")).toBe(normalizeTitle("dragonball super"));
  });

  it("nimmt nur die Serie mit genau passendem Namen", () => {
    const hits = [
      { show: { id: 1, name: "Ted Lasso", image: { original: "a" } } },
      { show: { id: 2, name: "Ted", image: { original: "b" } } },
    ];
    expect(pickShow("Ted", hits)?.id).toBe(2);
    expect(pickShow("Teddy", hits)).toBeNull();
  });
});

describe("enrichArtwork", () => {
  it("speichert Plakat und Hintergrund einer gefundenen Serie", async () => {
    const { db, coverDir } = setup();
    db.prepare("INSERT INTO series (id,title,added_at) VALUES ('ted','Ted',0)").run();
    const res = await enrichArtwork({
      db,
      coverDir,
      fetchImpl: fakeFetch({
        "search/shows": [{ show: { id: 7, name: "Ted", image: { original: "https://img/p.jpg" } } }],
        "shows/7/images": [{ type: "background", resolutions: { original: { url: "https://img/b.jpg" } } }],
        "img/": IMAGE,
      }),
    });
    expect(res.seriesFound).toBe(1);
    expect(db.prepare("SELECT poster_url, backdrop_url FROM series WHERE id='ted'").get()).toEqual({
      poster_url: "/covers/poster_ted.jpg",
      backdrop_url: "/covers/backdrop_ted.jpg",
    });
    expect(readdirSync(coverDir).sort()).toEqual(["backdrop_ted.jpg", "poster_ted.jpg"]);
  });

  it("merkt sich eine erfolglose Suche und fragt nicht sofort wieder", async () => {
    const { db, coverDir } = setup();
    db.prepare("INSERT INTO series (id,title,added_at) VALUES ('x','Gibt es nicht',0)").run();
    const fetchImpl = fakeFetch({ "search/shows": [] });
    expect((await enrichArtwork({ db, coverDir, fetchImpl })).checked).toBe(1);
    expect((await enrichArtwork({ db, coverDir, fetchImpl })).checked).toBe(0);
    expect(db.prepare("SELECT poster_url FROM series WHERE id='x'").get()).toEqual({ poster_url: null });
  });

  it("holt für einen Film das Plakat aus der Wikipedia, aber nur von einer Filmseite", async () => {
    const { db, coverDir } = setup();
    db.prepare(
      "INSERT INTO videos (id,channel_id,title,published_at,duration_seconds,discovered_at,is_movie) VALUES ('f1','manual','Interstellar',0,10000,0,1)",
    ).run();
    const res = await enrichArtwork({
      db,
      coverDir,
      fetchImpl: fakeFetch({
        "list=search": { query: { search: [{ title: "Interstellar (film)" }] } },
        "page/summary": { description: "2014 film by Christopher Nolan", originalimage: { source: "https://img/f.jpg" } },
        "img/": IMAGE,
      }),
    });
    expect(res.filmsFound).toBe(1);
    expect(db.prepare("SELECT poster_url FROM videos WHERE id='f1'").get()).toEqual({
      poster_url: "/covers/poster_f1.jpg",
    });
  });
});
