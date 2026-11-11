import Database from "better-sqlite3";
import { describe, expect, it } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import { enrichGenres, ensureFilmGenres, ensureSeriesGenres, mapGenres } from "./genres.js";

const DAY = 24 * 60 * 60 * 1000;

/** Antwortet je Adresse; eine Zahl ist ein HTTP-Fehler, unbekannte Adressen sind 404. */
function fakeFetch(routes: Record<string, unknown>) {
  const calls: string[] = [];
  const impl = (async (input: string | URL | Request) => {
    const url = decodeURIComponent(String(input));
    calls.push(url);
    const key = Object.keys(routes).find((k) => url.includes(k));
    if (!key) return new Response("not found", { status: 404 });
    const body = routes[key];
    if (typeof body === "number") return new Response("fehler", { status: body });
    return new Response(JSON.stringify(body), { status: 200 });
  }) as typeof fetch;
  return { impl, calls };
}

const tvmaze = (name: string, genres: string[], type = "Scripted") => [
  { show: { id: 1, name, genres, type } },
];
/** Wikipedia-Suche + Kurzbeschreibung + Wikidata-Genres für einen Film. */
function wikiFilm(title: string, description: string, genres: Record<string, string>) {
  return {
    "list=search": { query: { search: [{ title: `${title} (film)` }] } },
    "page/summary": { description, wikibase_item: "Q1" },
    "props=claims": {
      entities: {
        Q1: {
          claims: {
            P136: Object.keys(genres).map((id) => ({ mainsnak: { datavalue: { value: { id } } } })),
          },
        },
      },
    },
    "props=labels": {
      entities: Object.fromEntries(
        Object.entries(genres).map(([id, label]) => [id, { labels: { en: { value: label } } }]),
      ),
    },
  };
}

function setup() {
  const db = new Database(":memory:");
  applyMigrations(db);
  db.prepare(
    "INSERT INTO channels (id,url,title,added_at) VALUES ('manual','x','Manuell',0)",
  ).run();
  db.prepare("INSERT INTO series (id,title,added_at) VALUES ('mf','Modern Family',0)").run();
  db.prepare(
    "INSERT INTO videos (id,channel_id,title,published_at,duration_seconds,discovered_at,is_movie) VALUES ('film','manual','Interstellar',0,10144,0,1)",
  ).run();
  return db;
}
const seriesRow = (db: Database.Database) =>
  db.prepare("SELECT genres, genres_checked_at FROM series WHERE id='mf'").get() as {
    genres: string | null;
    genres_checked_at: number | null;
  };
const filmRow = (db: Database.Database) =>
  db.prepare("SELECT genres, genres_checked_at FROM videos WHERE id='film'").get() as {
    genres: string | null;
    genres_checked_at: number | null;
  };

describe("Zuordnung der Genre-Angaben", () => {
  it("übersetzt die Angaben der Dienste in die Kategorien der App", () => {
    expect(mapGenres(["Comedy", "Family"])).toEqual(["comedy"]);
    expect(mapGenres(["Drama", "Horror", "Thriller"])).toEqual(["drama", "thriller"]);
    expect(mapGenres(["science fiction film", "adventure film", "drama film"])).toEqual([
      "scifi",
      "action",
      "drama",
    ]);
    expect(mapGenres(["Crime", "Mystery"])).toEqual(["thriller"]);
    expect(mapGenres(["romantic comedy"])).toEqual(["comedy", "drama"]);
  });

  it("erkennt Anime und Zeichentrick auch über die Art der Sendung", () => {
    expect(mapGenres(["Action", "Adventure", "Anime", "Fantasy"])).toEqual([
      "anime",
      "action",
      "scifi",
    ]);
    expect(mapGenres(["Comedy"], "Animation")).toEqual(["anime", "comedy"]);
  });

  it("lässt eine Doku eine Doku sein", () => {
    expect(mapGenres(["Nature"], "Documentary")).toEqual(["docs"]);
    expect(mapGenres(["documentary film", "crime film"])).toEqual(["docs"]);
  });

  it("nennt höchstens drei Kategorien und rät nichts", () => {
    expect(mapGenres(["Action", "Comedy", "Drama", "Thriller", "Music"])).toHaveLength(3);
    expect(mapGenres(["Family", "Children"])).toEqual([]);
    expect(mapGenres([])).toEqual([]);
  });
});

describe("Kategorien nachschlagen", () => {
  it("holt die Kategorien einer Serie von TVmaze — eine Comedy landet nicht mehr bei den Dokus", async () => {
    const db = setup();
    const { impl } = fakeFetch({
      "tvmaze.com/search": tvmaze("Modern Family", ["Comedy", "Family"]),
    });
    expect(await ensureSeriesGenres({ db, fetchImpl: impl, now: 1000 * DAY }, "mf")).toEqual([
      "comedy",
    ]);
    expect(seriesRow(db)).toEqual({ genres: "comedy", genres_checked_at: 1000 * DAY });
  });

  it("holt die Kategorien eines Films über Wikipedia und Wikidata", async () => {
    const db = setup();
    const { impl } = fakeFetch(
      wikiFilm("Interstellar", "2014 film by Christopher Nolan", {
        Q24925: "science fiction film",
        Q319221: "adventure film",
      }),
    );
    expect(await ensureFilmGenres({ db, fetchImpl: impl }, "film")).toEqual(["scifi", "action"]);
    expect(filmRow(db).genres).toBe("scifi,action");
  });

  it("nimmt bei Filmen ohne Genre in Wikidata die Kurzbeschreibung", async () => {
    const db = setup();
    const { impl } = fakeFetch(wikiFilm("Interstellar", "2014 epic science fiction film", {}));
    expect(await ensureFilmGenres({ db, fetchImpl: impl }, "film")).toEqual(["scifi"]);
  });

  it("weicht bei Serien auf Wikidata aus, wenn TVmaze sie nicht kennt", async () => {
    const db = setup();
    const { impl } = fakeFetch({
      "tvmaze.com/search": [],
      ...wikiFilm("Modern Family", "American television sitcom series", { Q1: "sitcom" }),
    });
    expect(await ensureSeriesGenres({ db, fetchImpl: impl }, "mf")).toEqual(["comedy"]);
  });

  it("merkt sich »nichts gefunden« und fragt erst nach zwei Wochen wieder", async () => {
    const db = setup();
    const { impl, calls } = fakeFetch({
      "tvmaze.com/search": [],
      "list=search": { query: { search: [] } },
    });
    expect(await ensureSeriesGenres({ db, fetchImpl: impl, now: 1000 * DAY }, "mf")).toEqual([]);
    expect(seriesRow(db)).toEqual({ genres: "", genres_checked_at: 1000 * DAY });
    const after = calls.length;
    expect(await ensureSeriesGenres({ db, fetchImpl: impl, now: 1005 * DAY }, "mf")).toBeNull();
    expect(calls.length).toBe(after);
    expect(await ensureSeriesGenres({ db, fetchImpl: impl, now: 1015 * DAY }, "mf")).toEqual([]);
    expect(calls.length).toBeGreaterThan(after);
  });

  it("fragt nicht noch einmal, wenn die Kategorien schon feststehen", async () => {
    const db = setup();
    db.prepare("UPDATE series SET genres = 'comedy', genres_checked_at = 1 WHERE id = 'mf'").run();
    const { impl, calls } = fakeFetch({});
    expect(await ensureSeriesGenres({ db, fetchImpl: impl }, "mf")).toBeNull();
    expect(calls).toEqual([]);
  });

  it("vermerkt nichts, wenn ein Dienst ausbremst — der nächste Lauf versucht es wieder", async () => {
    const db = setup();
    const busy = fakeFetch({
      "tvmaze.com/search": tvmaze("Modern Family", ["Comedy"]),
      "list=search": 429,
    });
    expect(await enrichGenres({ db, fetchImpl: busy.impl, pauseMs: 0 })).toEqual({
      checked: 2,
      found: 1,
    });
    expect(seriesRow(db).genres).toBe("comedy");
    expect(filmRow(db)).toEqual({ genres: null, genres_checked_at: null });

    const ok = fakeFetch(wikiFilm("Interstellar", "2014 science fiction film", {}));
    expect(await enrichGenres({ db, fetchImpl: ok.impl, pauseMs: 0 })).toEqual({
      checked: 1,
      found: 1,
    });
    expect(filmRow(db).genres).toBe("scifi");

    // Jetzt fehlt nichts mehr — der Lauf fragt niemanden.
    const still = fakeFetch({});
    expect(await enrichGenres({ db, fetchImpl: still.impl, pauseMs: 0 })).toEqual({
      checked: 0,
      found: 0,
    });
    expect(still.calls).toEqual([]);
  });
});
