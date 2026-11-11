import Database from "better-sqlite3";
import { describe, expect, it } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import { CONTINUE_LIMIT, dismissContinue, listContinueWatching } from "./history.js";

interface Row {
  id: string;
  progress_seconds: number;
  up_next: number;
}

function setup() {
  const db = new Database(":memory:");
  applyMigrations(db);
  db.prepare("INSERT INTO channels (id,url,title,added_at) VALUES ('a','x','Kanal',0)").run();
  db.prepare("INSERT INTO series (id,title,added_at) VALUES ('ted','Ted',0)").run();
  db.prepare(
    "INSERT INTO series (id,title,added_at) VALUES ('ahs','American Horror Story',0)",
  ).run();
  return db;
}

/** Legt ein Video an; `at` ist der Zeitpunkt des letzten Schauens, `seen` der Fortschritt in Sekunden. */
function video(
  db: Database.Database,
  id: string,
  opts: {
    series?: string;
    season?: number;
    episode?: number;
    duration?: number;
    seen?: number;
    at?: number;
  } = {},
) {
  db.prepare(
    `INSERT INTO videos (id,channel_id,title,published_at,duration_seconds,discovered_at,format,series_id,season,episode)
     VALUES (?,?,?,0,?,0,'long',?,?,?)`,
  ).run(
    id,
    "a",
    id,
    opts.duration ?? 1000,
    opts.series ?? null,
    opts.season ?? null,
    opts.episode ?? null,
  );
  if (opts.seen !== undefined) {
    db.prepare(
      "INSERT INTO video_progress (video_id,progress_seconds,updated_at) VALUES (?,?,?)",
    ).run(id, opts.seen, opts.at ?? 1);
  }
}

const list = (db: Database.Database) => listContinueWatching(db) as Row[];
const ids = (db: Database.Database) => list(db).map((r) => r.id);

describe("Weiterschauen", () => {
  it("zeigt je Serie nur einen Eintrag: die zuletzt gesehene Folge", () => {
    const db = setup();
    video(db, "ahs-2-9", { series: "ahs", season: 2, episode: 9, seen: 300, at: 10 });
    video(db, "ahs-3-8", { series: "ahs", season: 3, episode: 8, seen: 400, at: 20 });
    video(db, "ahs-3-10", { series: "ahs", season: 3, episode: 10, seen: 24, at: 30 });
    video(db, "ted-1-2", { series: "ted", season: 1, episode: 2, seen: 500, at: 40 });
    expect(ids(db)).toEqual(["ted-1-2", "ahs-3-10"]);
  });

  it("springt nach einer zu Ende gesehenen Folge auf die nächste", () => {
    const db = setup();
    video(db, "ted-1-1", { series: "ted", season: 1, episode: 1, seen: 990, at: 50 });
    video(db, "ted-1-2", { series: "ted", season: 1, episode: 2 });
    video(db, "ted-1-3", { series: "ted", season: 1, episode: 3 });
    const [entry] = list(db);
    expect(entry).toMatchObject({ id: "ted-1-2", up_next: 1, progress_seconds: 0 });
  });

  it("findet die nächste Folge auch über die Staffelgrenze", () => {
    const db = setup();
    video(db, "ted-1-9", { series: "ted", season: 1, episode: 9, seen: 1000, at: 5 });
    video(db, "ted-2-1", { series: "ted", season: 2, episode: 1 });
    expect(ids(db)).toEqual(["ted-2-1"]);
  });

  it("lässt eine zu Ende gesehene Serie und fertige Filme verschwinden", () => {
    const db = setup();
    video(db, "ted-1-1", { series: "ted", season: 1, episode: 1, seen: 100, at: 1 });
    video(db, "ted-1-2", { series: "ted", season: 1, episode: 2, seen: 960, at: 9 }); // letzte Folge, fertig
    video(db, "film-fertig", { duration: 6000, seen: 5800, at: 8 });
    video(db, "film-offen", { duration: 6000, seen: 3000, at: 7 });
    expect(ids(db)).toEqual(["film-offen"]);
  });

  it("behält bei der nächsten Folge eine schon angefangene Stelle, eine fertige beginnt neu", () => {
    const db = setup();
    video(db, "ted-1-1", { series: "ted", season: 1, episode: 1, seen: 1000, at: 90 });
    video(db, "ted-1-2", { series: "ted", season: 1, episode: 2, seen: 200, at: 3 });
    expect(list(db)[0]).toMatchObject({ id: "ted-1-2", progress_seconds: 200, up_next: 1 });

    const again = setup();
    video(again, "ted-1-1", { series: "ted", season: 1, episode: 1, seen: 1000, at: 90 });
    video(again, "ted-1-2", { series: "ted", season: 1, episode: 2, seen: 1000, at: 3 });
    expect(list(again)[0]).toMatchObject({ id: "ted-1-2", progress_seconds: 0, up_next: 1 });
  });

  it("zählt kaum Angespieltes nicht und sortiert nach zuletzt gesehen", () => {
    const db = setup();
    video(db, "kurz", { seen: 3, at: 99 });
    video(db, "alt", { seen: 300, at: 10 });
    video(db, "neu", { seen: 300, at: 20 });
    expect(ids(db)).toEqual(["neu", "alt"]);
  });

  it("zeigt höchstens die zehn zuletzt gesehenen Titel", () => {
    const db = setup();
    for (let i = 1; i <= 14; i++) video(db, `film-${i}`, { seen: 100, at: i });
    const shown = ids(db);
    expect(shown).toHaveLength(CONTINUE_LIMIT);
    expect(shown[0]).toBe("film-14");
    expect(shown.at(-1)).toBe("film-5");
  });

  it("aus der Reihe Entferntes bleibt weg, bis man es wieder schaut", () => {
    const db = setup();
    video(db, "ted-1-1", { series: "ted", season: 1, episode: 1, seen: 300, at: 100 });
    video(db, "ted-1-2", { series: "ted", season: 1, episode: 2 });
    video(db, "film", { seen: 300, at: 50 });

    expect(dismissContinue(db, "ted-1-1", 200)).toBe(true);
    expect(ids(db)).toEqual(["film"]);
    // Fortschritt bleibt erhalten — nur die Reihe vergisst den Titel.
    expect(
      db.prepare("SELECT progress_seconds FROM video_progress WHERE video_id='ted-1-1'").get(),
    ).toEqual({ progress_seconds: 300 });

    // Eine andere Folge derselben Serie geschaut: Die Serie ist wieder da.
    db.prepare(
      "INSERT INTO video_progress (video_id,progress_seconds,updated_at) VALUES ('ted-1-2', 60, 300)",
    ).run();
    expect(ids(db)).toEqual(["ted-1-2", "film"]);
    expect(dismissContinue(db, "gibt-es-nicht")).toBe(false);
  });
});
