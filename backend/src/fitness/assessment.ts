/**
 * Fitness-Test: Einstufung zum Start und alle 4 Wochen. Vier kurze Prüfungen, jede ergibt eine Stufe
 * 1–5 (grobe Normen für trainierende Erwachsene), der Schnitt ist das Level des Coachs.
 * Ohne Klimmzugstange/Ringe ersetzt »Burpees in 60 s« den Klimmzug.
 */
import type { Catalog } from "./catalog.js";
import { type FitDb, addDays, kvSet, localDay } from "./db.js";
import { getProfile, saveProfile } from "./profile.js";

export interface TestDef {
  id: string;
  ex: string;
  name: string;
  unit: "reps" | "sec";
  /** max = so viel wie möglich am Stück; timed = Wiederholungen in [seconds] */
  mode: "max" | "timed";
  seconds?: number;
  how: string;
  /** Schwellen für Stufe 2, 3, 4, 5 (Männer); Frauen × femaleFactor */
  norms: [number, number, number, number];
  femaleFactor: number;
}

const BASE: TestDef[] = [
  { id: "liegestuetz", ex: "pushup", name: "Liegestütze", unit: "reps", mode: "max", how: "So viele saubere Liegestütze wie möglich am Stück — Körper gerade, Brust fast am Boden. Sobald die Form bricht, ist Schluss.", norms: [8, 18, 30, 45], femaleFactor: 0.5 },
  { id: "kniebeuge", ex: "squat", name: "Kniebeugen in 60 s", unit: "reps", mode: "timed", seconds: 60, how: "Eine Minute lang Kniebeugen, Oberschenkel mindestens parallel zum Boden, oben ganz aufrichten.", norms: [25, 35, 45, 55], femaleFactor: 0.9 },
  { id: "plank", ex: "plank", name: "Unterarmstütz", unit: "sec", mode: "max", how: "So lange halten, wie der Körper eine gerade Linie bleibt. Hüfte sinkt oder Po geht hoch → Stopp.", norms: [30, 60, 120, 180], femaleFactor: 1 },
];
const PULL: TestDef = { id: "klimmzug", ex: "pullup", name: "Klimmzüge", unit: "reps", mode: "max", how: "Aus dem ganz gestreckten Hang, Kinn über die Stange, ohne Schwung. So viele wie möglich.", norms: [1, 4, 9, 15], femaleFactor: 0.4 };
const BURPEE: TestDef = { id: "burpees", ex: "burpee", name: "Burpees in 60 s", unit: "reps", mode: "timed", seconds: 60, how: "Eine Minute Burpees: Brust zum Boden, oben ein kleiner Sprung mit gestreckter Hüfte.", norms: [10, 15, 20, 25], femaleFactor: 0.85 };

const FIST: Partial<TestDef> = {
  ex: "fist_pushup",
  name: "Liegestütze auf Fäusten",
  how: "So viele saubere Liegestütze auf den Fäusten wie möglich am Stück — Handgelenk gerade, Körper gerade, Brust fast am Boden. Sobald die Form bricht, ist Schluss.",
};

/** Tests passend zum Profil: Beschwerden und Ausrüstung (ok = Übung machbar). Nicht machbare fallen weg. */
export function testsFor(db: FitDb, ok: (exId: string) => boolean = () => true): TestDef[] {
  const p = getProfile(db);
  const canPull = p.equipment.some((q) => ["klimmzugstange", "ringe"].includes(q));
  const out: TestDef[] = [];
  for (const t of BASE) {
    if (ok(t.ex)) out.push(t);
    else if (t.id === "liegestuetz" && FIST.ex && ok(FIST.ex)) out.push({ ...t, ...FIST } as TestDef);
  }
  const extra = canPull && ok(PULL.ex) ? PULL : ok(BURPEE.ex) ? BURPEE : null;
  if (extra) out.push(extra);
  return out;
}

export function tierOf(t: TestDef, value: number, female: boolean): number {
  const f = female ? t.femaleFactor : 1;
  let tier = 1;
  for (const n of t.norms) if (value >= Math.round(n * f)) tier++;
  return tier;
}

interface TestRow { day: string; results: string; tiers: string; level: number }

export function testHistory(db: FitDb) {
  return (db.prepare("SELECT day, results, tiers, level FROM test_log ORDER BY at DESC LIMIT 12").all() as TestRow[]).map(
    (r) => ({ day: r.day, results: JSON.parse(r.results) as Record<string, number>, tiers: JSON.parse(r.tiers) as Record<string, number>, level: r.level }),
  );
}

export function testState(db: FitDb, catalog: Catalog, ok?: (exId: string) => boolean) {
  const hist = testHistory(db);
  const last = hist[0] ?? null;
  const today = localDay();
  const next = last ? addDays(last.day, 28) : today;
  const daysUntil = Math.max(0, Math.round((Date.parse(`${next}T12:00:00`) - Date.parse(`${today}T12:00:00`)) / 86_400_000));
  return {
    tests: testsFor(db, ok).filter((t) => catalog.byId.has(t.ex)),
    last,
    history: hist,
    due: !last || daysUntil === 0,
    daysUntil,
  };
}

export function saveTest(
  db: FitDb,
  results: Record<string, unknown>,
  ok?: (exId: string) => boolean,
  day = localDay(),
) {
  const p = getProfile(db);
  const female = p.sex !== "m";
  const defs = testsFor(db, ok);
  const tiers: Record<string, number> = {};
  const clean: Record<string, number> = {};
  for (const t of defs) {
    // Ausgelassen (leer) zählt nicht — sonst drückt ein übersprungener Test das Level auf 1.
    const raw = results[t.id];
    if (raw == null || raw === "" || !Number.isFinite(Number(raw))) continue;
    const v = Math.max(0, Math.round(Number(raw)));
    clean[t.id] = v;
    tiers[t.id] = tierOf(t, v, female);
  }
  const vals = Object.values(tiers);
  if (!vals.length) return null;
  const level = Math.min(5, Math.max(1, Math.round(vals.reduce((a, b) => a + b, 0) / vals.length)));
  const prev = testHistory(db)[0] ?? null;
  const now = Date.now();
  db.prepare("INSERT INTO test_log(day, results, tiers, level, at) VALUES(?, ?, ?, ?, ?)").run(day, JSON.stringify(clean), JSON.stringify(tiers), level, now);
  const before = p.level;
  saveProfile(db, { level });
  kvSet(db, "level_changed_at", now);
  db.prepare("INSERT INTO progress_events(day, kind, ex_from, ex_to, level, at) VALUES(?, 'test', NULL, NULL, ?, ?)").run(day, level, now);
  return { level, before, tiers, results: clean, prev };
}
