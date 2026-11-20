/**
 * Workouts auf Zeit mit Bestzeiten (wie die »Götter«-Workouts bei Freeletics, mit eigenen Namen):
 * feste Runden und Mengen, so schnell wie sauber möglich. Nicht machbare Übungen (Ausrüstung,
 * Beschwerden, keine Sprünge) ersetzt der Server durch die nächstliegende machbare im selben Muster.
 * Zielzeiten Gold/Silber/Bronze aus den Sekunden je Wiederholung des Katalogs.
 */
import { existsSync, readFileSync } from "node:fs";
import type { Catalog, Exercise } from "./catalog.js";
import { type FitDb, localDay } from "./db.js";

export interface Benchmark { id: string; name: string; kurz: string; level: number; runden: number; uebungen: { ex: string; menge: number }[] }

export function loadBenchmarks(dataDir: string): Benchmark[] {
  const f = `${dataDir}/benchmarks.json`;
  return existsSync(f) ? (JSON.parse(readFileSync(f, "utf8")) as Benchmark[]) : [];
}

/** Übungen auflösen: nicht machbare durch die nächste machbare (gleiches Muster, Wiederholungen). */
export function resolveItems(b: Benchmark, catalog: Catalog, ok: (e: Exercise) => boolean) {
  const used = new Set<string>();
  const near = (lvl: number) => (a: Exercise, c: Exercise) => Math.abs(a.level - lvl) - Math.abs(c.level - lvl);
  const cand = (x: Exercise) => x.unit === "reps" && x.use.includes("main") && ok(x) && !used.has(x.id) && !b.uebungen.some((u) => u.ex === x.id);
  return b.uebungen.map((u) => {
    const e = catalog.byId.get(u.ex);
    if (e && ok(e)) { used.add(e.id); return { ex: e.id, menge: u.menge, statt: null as string | null }; }
    const lvl = e?.level ?? 3;
    // Erst gleiches Muster; gibt es dort nichts (z. B. Handgelenk → kein Drücken), ein anderes.
    // Erst gleiches Muster. Fremdes Muster nur für Drücken (Handgelenk) und Ausdauer (Sprünge) —
    // ein Zug-Workout ohne Ziehen wäre ein anderes Workout, das gilt dann als nicht machbar.
    const alt = catalog.exercises.filter((x) => x.pattern === e?.pattern && cand(x)).sort(near(lvl))[0]
      ?? (e && ["push", "cardio"].includes(e.pattern)
        ? catalog.exercises.filter((x) => ["core", "squat", "lunge", "hinge", "cardio"].includes(x.pattern) && cand(x)).sort(near(lvl))[0]
        : undefined);
    if (!alt) return null;
    used.add(alt.id);
    return { ex: alt.id, menge: u.menge, statt: e?.name ?? u.ex };
  });
}

export function targets(items: { ex: string; menge: number }[], runden: number, catalog: Catalog) {
  const perRound = items.reduce((s, it) => {
    const e = catalog.byId.get(it.ex);
    // Langsame Tempo-Übungen zählen auf Zeit höchstens 3 s je Wiederholung.
    return s + it.menge * Math.min(3, e?.secPerRep ?? 2.5) * (e?.perSide ? 2 : 1) + 6; // + Wechsel
  }, 0);
  const est = perRound * runden;
  // Auf Zeit wird zügiger gearbeitet als im Training (secPerRep ist Trainings-Tempo).
  return { gold: Math.round(est * 0.75), silber: Math.round(est * 1.0), bronze: Math.round(est * 1.35) };
}

export function tierFor(seconds: number, t: { gold: number; silber: number; bronze: number }) {
  return seconds <= t.gold ? "gold" : seconds <= t.silber ? "silber" : seconds <= t.bronze ? "bronze" : null;
}

/** Version eines Workouts = welche Übungen ersetzt wurden. Zeiten verschiedener Versionen sind nicht vergleichbar. */
export function subsKey(subs: Record<string, string>): string {
  return JSON.stringify(Object.fromEntries(Object.entries(subs).sort(([a], [b]) => a.localeCompare(b))));
}
const sameVersion = "COALESCE(subs, '{}') = ?";
export function history(db: FitDb, bench: string, subs: Record<string, string> = {}) {
  return db.prepare(`SELECT day, seconds FROM benchmark_log WHERE bench = ? AND ${sameVersion} ORDER BY at DESC LIMIT 20`).all(bench, subsKey(subs)) as { day: string; seconds: number }[];
}
export function best(db: FitDb, bench: string, subs: Record<string, string> = {}): number | null {
  return (db.prepare(`SELECT MIN(seconds) AS s FROM benchmark_log WHERE bench = ? AND ${sameVersion}`).get(bench, subsKey(subs)) as { s: number | null }).s;
}
export function saveRun(db: FitDb, bench: string, seconds: number, subs: Record<string, string>, day = localDay()) {
  const before = best(db, bench, subs);
  db.prepare("INSERT INTO benchmark_log(bench, day, seconds, subs, at) VALUES(?, ?, ?, ?, ?)").run(bench, day, seconds, subsKey(subs), Date.now());
  return { pb: before == null || seconds < before, before };
}
