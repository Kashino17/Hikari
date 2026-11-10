import { mkdtempSync, readdirSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import Database from "better-sqlite3";
import { describe, expect, it } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import { ArtworkScout, enrichArtwork, normalizeTitle, pickShow } from "./artwork.js";

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
  db.prepare(
    "INSERT INTO channels (id,url,title,added_at) VALUES ('manual','x','Manuell',0)",
  ).run();
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
        "search/shows": [
          { show: { id: 7, name: "Ted", image: { original: "https://img/p.jpg" } } },
        ],
        "shows/7/images": [
          { type: "background", resolutions: { original: { url: "https://img/b.jpg" } } },
        ],
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

  it("erkennt eine Serie auch unter ihrem deutschen Zweitnamen", async () => {
    const { db, coverDir } = setup();
    db.prepare("INSERT INTO series (id,title,added_at) VALUES ('hdg','Haus des Geldes',0)").run();
    const res = await enrichArtwork({
      db,
      coverDir,
      fetchImpl: fakeFetch({
        "search/shows": [
          {
            show: { id: 27436, name: "La Casa de Papel", image: { original: "https://img/p.jpg" } },
          },
        ],
        "shows/27436/akas": [{ name: "Money Heist" }, { name: "Haus des Geldes" }],
        "shows/27436/images": [],
        "img/": IMAGE,
      }),
    });
    expect(res.seriesFound).toBe(1);
    expect(db.prepare("SELECT poster_url FROM series WHERE id='hdg'").get()).toEqual({
      poster_url: "/covers/poster_hdg.jpg",
    });
  });

  it("nimmt keinen Treffer, dessen Zweitnamen auch nicht passen", async () => {
    const { db, coverDir } = setup();
    db.prepare("INSERT INTO series (id,title,added_at) VALUES ('ted','Ted',0)").run();
    const res = await enrichArtwork({
      db,
      coverDir,
      fetchImpl: fakeFetch({
        "search/shows": [
          { show: { id: 9, name: "Ted Lasso", image: { original: "https://img/p.jpg" } } },
        ],
        "shows/9/akas": [{ name: "Тед Лассо" }],
        "img/": IMAGE,
      }),
    });
    expect(res.seriesFound).toBe(0);
    expect(readdirSync(coverDir)).toEqual([]);
  });

  it("merkt sich eine erfolglose Suche und fragt nicht sofort wieder", async () => {
    const { db, coverDir } = setup();
    db.prepare("INSERT INTO series (id,title,added_at) VALUES ('x','Gibt es nicht',0)").run();
    const fetchImpl = fakeFetch({ "search/shows": [] });
    expect((await enrichArtwork({ db, coverDir, fetchImpl })).checked).toBe(1);
    expect((await enrichArtwork({ db, coverDir, fetchImpl })).checked).toBe(0);
    expect(db.prepare("SELECT poster_url FROM series WHERE id='x'").get()).toEqual({
      poster_url: null,
    });
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
        "page/summary": {
          description: "2014 film by Christopher Nolan",
          originalimage: { source: "https://img/f.jpg" },
        },
        "img/": IMAGE,
      }),
    });
    expect(res.filmsFound).toBe(1);
    expect(db.prepare("SELECT poster_url FROM videos WHERE id='f1'").get()).toEqual({
      poster_url: "/covers/poster_f1.jpg",
    });
  });
});

describe("Plakatsuche beim Import", () => {
  const tedRoutes = {
    "search/shows": [{ show: { id: 7, name: "Ted", image: { original: "https://img/p.jpg" } } }],
    "shows/7/images": [
      { type: "background", resolutions: { original: { url: "https://img/b.jpg" } } },
    ],
    "img/": IMAGE,
  };

  /** Zählt die Suchanfragen mit — das Ergebnis muss für spätere Folgen reichen. */
  function counting(routes: Record<string, unknown>) {
    const inner = fakeFetch(routes);
    let searches = 0;
    const fetchImpl = (async (input: string | URL | Request) => {
      if (String(input).includes("search/shows") || String(input).includes("list=search"))
        searches++;
      return inner(input);
    }) as typeof fetch;
    return { fetchImpl, searches: () => searches };
  }

  it("findet das Plakat schon während des Downloads und trägt es ein, sobald es die Serie gibt", async () => {
    const { db, coverDir } = setup();
    const { fetchImpl, searches } = counting(tedRoutes);
    const scout = new ArtworkScout({ db, coverDir, fetchImpl });

    // Import beginnt: die Serie existiert noch nicht.
    scout.seriesArtwork("ted", "Ted");
    await scout.idle();
    expect(readdirSync(coverDir).sort()).toEqual(["backdrop_ted.jpg", "poster_ted.jpg"]);

    // Erste Folge fertig: Serie wird angelegt, der Import meldet sich noch einmal.
    db.prepare("INSERT INTO series (id,title,added_at) VALUES ('ted','Ted',0)").run();
    scout.seriesArtwork("ted", "Ted");
    await scout.idle();

    expect(db.prepare("SELECT poster_url, backdrop_url FROM series WHERE id='ted'").get()).toEqual({
      poster_url: "/covers/poster_ted.jpg",
      backdrop_url: "/covers/backdrop_ted.jpg",
    });
    expect(searches()).toBe(1);
  });

  it("fragt für eine Serie ohne Treffer nicht bei jeder Folge wieder nach", async () => {
    const { db, coverDir } = setup();
    const { fetchImpl, searches } = counting({ "search/shows": [] });
    const scout = new ArtworkScout({ db, coverDir, fetchImpl });

    scout.seriesArtwork("x", "Gibt es nicht");
    db.prepare("INSERT INTO series (id,title,added_at) VALUES ('x','Gibt es nicht',0)").run();
    for (let episode = 0; episode < 5; episode++) scout.seriesArtwork("x", "Gibt es nicht");
    await scout.idle();

    expect(searches()).toBe(1);
    // Als geprüft vermerkt — auch der Sammellauf lässt die Serie jetzt in Ruhe.
    expect((await enrichArtwork({ db, coverDir, fetchImpl })).checked).toBe(0);
  });

  it("lässt ein vorhandenes Plakat unangetastet", async () => {
    const { db, coverDir } = setup();
    db.prepare(
      "INSERT INTO series (id,title,added_at,poster_url) VALUES ('ted','Ted',0,'/covers/eigenes.jpg')",
    ).run();
    const { fetchImpl, searches } = counting(tedRoutes);
    const scout = new ArtworkScout({ db, coverDir, fetchImpl });
    scout.seriesArtwork("ted", "Ted");
    await scout.idle();
    expect(searches()).toBe(0);
    expect(db.prepare("SELECT poster_url FROM series WHERE id='ted'").get()).toEqual({
      poster_url: "/covers/eigenes.jpg",
    });
  });

  it("sucht für einen Film unter dem endgültigen Titel noch einmal, wenn der vorläufige nichts ergab", async () => {
    const { db, coverDir } = setup();
    const { fetchImpl, searches } = counting({
      "srsearch=Interstellar": { query: { search: [{ title: "Interstellar (film)" }] } },
      "list=search": { query: { search: [] } },
      "page/summary": {
        description: "2014 film by Christopher Nolan",
        originalimage: { source: "https://img/f.jpg" },
      },
      "img/": IMAGE,
    });
    const scout = new ArtworkScout({ db, coverDir, fetchImpl });

    scout.film("f1", "Stream ansehen");
    await scout.idle();
    db.prepare(
      "INSERT INTO videos (id,channel_id,title,published_at,duration_seconds,discovered_at,is_movie) VALUES ('f1','manual','Interstellar',0,10000,0,1)",
    ).run();
    scout.film("f1", "Stream ansehen");
    await scout.idle();

    expect(searches()).toBe(2);
    expect(db.prepare("SELECT poster_url FROM videos WHERE id='f1'").get()).toEqual({
      poster_url: "/covers/poster_f1.jpg",
    });
  });

  it("übersteht einen Netzfehler und versucht es beim nächsten Mal wieder", async () => {
    const { db, coverDir } = setup();
    db.prepare("INSERT INTO series (id,title,added_at) VALUES ('ted','Ted',0)").run();
    let fail = true;
    const ok = fakeFetch(tedRoutes);
    const fetchImpl = (async (input: string | URL | Request) => {
      if (fail) throw new Error("offline");
      return ok(input);
    }) as typeof fetch;
    const scout = new ArtworkScout({ db, coverDir, fetchImpl });

    scout.seriesArtwork("ted", "Ted");
    await scout.idle();
    expect(
      db.prepare("SELECT poster_url, artwork_checked_at FROM series WHERE id='ted'").get(),
    ).toEqual({
      poster_url: null,
      artwork_checked_at: null,
    });

    fail = false;
    scout.seriesArtwork("ted", "Ted");
    await scout.idle();
    expect(db.prepare("SELECT poster_url FROM series WHERE id='ted'").get()).toEqual({
      poster_url: "/covers/poster_ted.jpg",
    });
  });
});
