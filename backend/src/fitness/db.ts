/**
 * Eigene Datenbank für Fitness (Training, Skills, Ernährung): ~/.hikari/fitness/fitness.db.
 * Getrennt von hikari.db, damit der Bereich die Migrationen der übrigen App nie berührt.
 */
import Database from "better-sqlite3";
import { ensureDir } from "./ai.js";

const SCHEMA = `CREATE TABLE IF NOT EXISTS replay_seen (
  id TEXT PRIMARY KEY,           -- X-Hikari-Req: einmalige Kennung einer Anfrage vom Handy
  at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS benchmark_log (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  bench TEXT NOT NULL,
  day TEXT NOT NULL,
  seconds INTEGER NOT NULL,
  subs TEXT,                     -- JSON: ersetzte Übungen {original: ersatz}
  at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS benchmark_log_bench ON benchmark_log(bench);
CREATE TABLE IF NOT EXISTS body_measure (
  day TEXT PRIMARY KEY,
  chest REAL, arm REAL, waist REAL, hips REAL, thigh REAL,
  at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS body_photo (
  id TEXT PRIMARY KEY,
  day TEXT NOT NULL,
  pose TEXT NOT NULL,            -- vorn | seite | hinten
  file TEXT NOT NULL,
  at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS health_log (
  day TEXT PRIMARY KEY,
  steps INTEGER,
  sleep_min INTEGER,             -- Schlaf der Nacht, die an diesem Tag endet
  resting_hr REAL,
  active_kcal REAL,
  updated_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS test_log (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  day TEXT NOT NULL,
  results TEXT NOT NULL,         -- JSON {testId: Wert}
  tiers TEXT NOT NULL,           -- JSON {testId: Stufe 1–5}
  level INTEGER NOT NULL,
  at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS variant_map (
  base TEXT PRIMARY KEY,         -- diese Übung …
  current TEXT NOT NULL,         -- … wird ersetzt durch diese
  since TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS progress_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  day TEXT NOT NULL,
  kind TEXT NOT NULL,            -- auf | ab | level_auf | level_ab | test
  ex_from TEXT,
  ex_to TEXT,
  level INTEGER,
  at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS progress_events_day ON progress_events(day);

CREATE TABLE IF NOT EXISTS kv (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL,
  updated_at INTEGER NOT NULL
);
-- Ein Trainingstag. plan = JSON (Blöcke, Übungen), result = JSON (was wirklich gemacht wurde).
CREATE TABLE IF NOT EXISTS sessions (
  id TEXT PRIMARY KEY,
  day TEXT NOT NULL,
  kind TEXT NOT NULL,            -- coach | skill | custom
  status TEXT NOT NULL,          -- planned | active | done | skipped
  plan TEXT NOT NULL,
  result TEXT,
  feedback TEXT,
  source TEXT,                   -- Modell oder 'regeln'
  created_at INTEGER NOT NULL,
  done_at INTEGER
);
CREATE INDEX IF NOT EXISTS sessions_day ON sessions(day);
-- Stand je Übung: Dosis-Faktor aus dem Feedback, Bestwerte, gemieden bis.
CREATE TABLE IF NOT EXISTS exercise_state (
  exercise_id TEXT PRIMARY KEY,
  dose REAL NOT NULL DEFAULT 1,
  best REAL,
  last REAL,
  times INTEGER NOT NULL DEFAULT 0,
  technique INTEGER,             -- letzte Selbsteinschätzung 1–3
  avoid_until TEXT,
  disliked INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL
);
-- Schmerzen/Beschwerden je Körperregion (aus dem Feedback).
CREATE TABLE IF NOT EXISTS pain_log (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  day TEXT NOT NULL,
  region TEXT NOT NULL,
  level INTEGER NOT NULL,        -- 1–10
  note TEXT,
  session_id TEXT
);
CREATE TABLE IF NOT EXISTS skill_progress (
  skill_id TEXT PRIMARY KEY,
  step INTEGER NOT NULL DEFAULT 0,
  active INTEGER NOT NULL DEFAULT 0,
  started_at INTEGER,
  updated_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS skill_log (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  skill_id TEXT NOT NULL,
  step INTEGER NOT NULL,
  day TEXT NOT NULL,
  value REAL NOT NULL,           -- Sekunden oder Wiederholungen (bester Satz)
  sets TEXT,                     -- JSON-Liste aller Sätze
  feel INTEGER,                  -- 1 schwer … 3 leicht
  at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS skill_log_skill ON skill_log(skill_id, at);
CREATE TABLE IF NOT EXISTS body_log (
  day TEXT PRIMARY KEY,
  weight REAL,
  waist REAL,
  note TEXT
);
-- Ernährungstagebuch.
CREATE TABLE IF NOT EXISTS food_log (
  id TEXT PRIMARY KEY,
  day TEXT NOT NULL,
  meal TEXT NOT NULL,            -- fruehstueck | mittag | abend | snack
  title TEXT NOT NULL,
  source TEXT NOT NULL,          -- foto | rezept | suche | frei
  photo TEXT,
  recipe_id TEXT,
  items TEXT NOT NULL,           -- JSON [{name, grams, n:{...}}]
  totals TEXT NOT NULL,          -- JSON Nährstoffe der ganzen Mahlzeit
  ai_note TEXT,
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS food_log_day ON food_log(day);
-- Rezeptkatalog (übersetzt, Nährwerte je Portion berechnet).
CREATE TABLE IF NOT EXISTS recipes (
  id TEXT PRIMARY KEY,
  source TEXT NOT NULL,
  data TEXT NOT NULL,            -- JSON (Titel, Zutaten, Schritte, Bilder, Video, Nährwerte, Tags)
  kcal REAL,
  protein REAL,
  tags TEXT,
  updated_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS recipe_state (
  recipe_id TEXT PRIMARY KEY,
  favorite INTEGER NOT NULL DEFAULT 0,
  rating INTEGER,
  cooked INTEGER NOT NULL DEFAULT 0,
  hidden INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL
);
-- Lebensmittel-Zwischenspeicher (Nährwerte je 100 g), damit dieselbe Zutat nie zweimal gesucht wird.
CREATE TABLE IF NOT EXISTS foods (
  key TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  source TEXT NOT NULL,
  per100 TEXT NOT NULL,
  updated_at INTEGER NOT NULL
);
-- Nachrichten der Coaches (Training, Ernährung) — Verlauf für den Kontext.
CREATE TABLE IF NOT EXISTS coach_notes (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  kind TEXT NOT NULL,            -- training | ernaehrung | woche
  day TEXT NOT NULL,
  data TEXT NOT NULL,
  model TEXT,
  at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS coach_notes_kind ON coach_notes(kind, day);
-- Achtsamkeit: jede Meditation, Atemübung, Yoga-Einheit, jedes Video (siehe mind.ts).
CREATE TABLE IF NOT EXISTS mind_log (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  day TEXT NOT NULL,
  kind TEXT NOT NULL,            -- meditation | atem | yoga | video
  ref TEXT,                      -- id (gefuehrt/atem/video) oder 'stufe-N'
  seconds INTEGER NOT NULL,
  mood_before INTEGER,           -- 1 schlecht … 5 sehr gut
  mood_after INTEGER,
  note TEXT,
  at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS mind_log_day ON mind_log(day);
`;

export type FitDb = Database.Database;

export function openFitnessDb(file: string): FitDb {
  ensureDir(file);
  const db = new Database(file);
  db.pragma("journal_mode = WAL");
  db.pragma("busy_timeout = 4000");
  db.exec(SCHEMA);
  return db;
}

export function kvGet<T>(db: FitDb, key: string): T | null {
  const row = db.prepare("SELECT value FROM kv WHERE key = ?").get(key) as
    | { value: string }
    | undefined;
  if (!row) return null;
  try {
    return JSON.parse(row.value) as T;
  } catch {
    return null;
  }
}

export function kvSet(db: FitDb, key: string, value: unknown) {
  db.prepare(
    "INSERT INTO kv(key, value, updated_at) VALUES(?, ?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at",
  ).run(key, JSON.stringify(value), Date.now());
}

/** Lokaler Kalendertag (Zeitzone des Macs = Kadirs Zeitzone). */
/**
 * Tag, zu dem ein Ergebnis gehört: vom Gerät mitgeschickt (body.day) oder aus dem Zeitpunkt, an dem es
 * offline entstand (Kopfzeile X-Hikari-At) — sonst heute. Höchstens 30 Tage zurück, nie in der Zukunft.
 */
export function clientDay(bodyDay?: unknown, at?: unknown): string {
  const today = localDay();
  const back = new Date();
  back.setDate(back.getDate() - 30);
  const min = localDay(back);
  if (typeof bodyDay === "string" && /^\d{4}-\d{2}-\d{2}$/.test(bodyDay) && bodyDay <= today && bodyDay >= min) return bodyDay;
  const ms = Number(at);
  if (Number.isFinite(ms) && ms > 0 && ms <= Date.now() + 60_000) {
    const d = localDay(new Date(ms));
    if (d >= min && d <= today) return d;
  }
  return today;
}

export function localDay(d = new Date()): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

export function addDays(day: string, n: number): string {
  const [y, m, d] = day.split("-").map(Number) as [number, number, number];
  const dt = new Date(y, m - 1, d + n, 12);
  return localDay(dt);
}

export function weekday(day: string): number {
  const [y, m, d] = day.split("-").map(Number) as [number, number, number];
  // 0 = Montag … 6 = Sonntag
  return (new Date(y, m - 1, d, 12).getDay() + 6) % 7;
}
