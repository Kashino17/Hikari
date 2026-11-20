/**
 * Vollständigkeit eines Ernährungstags. Wer nur das Frühstück einträgt, hat nicht 500 kcal gegessen —
 * solche Tage dürfen weder Durchschnitte noch die Kalorien-Nachsteuerung noch Noten verfälschen.
 *
 * komplett  – zählt überall (selbst markiert, oder ≥ 60 % des Kalorienziels bzw. alle Hauptmahlzeiten)
 * teilweise – eingetragen, aber erkennbar lückenhaft (oder selbst so markiert): zählt nicht für Schnitte
 * leer      – nichts eingetragen: zählt nirgends (nicht »0 kcal gegessen«)
 * offen     – heute, noch nicht abgeschlossen: zählt erst, wenn als vollständig markiert
 */
import { type FitDb, localDay } from "./db.js";

export type DayState = "komplett" | "teilweise" | "leer" | "offen";
const MAIN = new Set(["fruehstueck", "mittag", "abend"]);

export function dayStates(
  db: FitDb,
  from: string,
  to: string,
  targetKcal: number,
  mealsPerDay: number,
): Record<string, { state: DayState; manual: boolean; kcal: number }> {
  const rows = db.prepare("SELECT day, meal, totals FROM food_log WHERE day >= ? AND day <= ?").all(from, to) as { day: string; meal: string; totals: string }[];
  const manual = new Map(
    (db.prepare("SELECT day, status FROM food_day WHERE day >= ? AND day <= ?").all(from, to) as { day: string; status: DayState }[]).map((r) => [r.day, r.status]),
  );
  const agg = new Map<string, { kcal: number; main: Set<string> }>();
  for (const r of rows) {
    const a = agg.get(r.day) ?? { kcal: 0, main: new Set<string>() };
    a.kcal += Number((JSON.parse(r.totals) as { kcal?: number }).kcal) || 0;
    if (MAIN.has(r.meal)) a.main.add(r.meal);
    agg.set(r.day, a);
  }
  const today = localDay();
  const out: Record<string, { state: DayState; manual: boolean; kcal: number }> = {};
  for (const day of new Set([...agg.keys(), ...manual.keys()])) {
    const a = agg.get(day);
    const m = manual.get(day);
    const kcal = Math.round(a?.kcal ?? 0);
    if (m && a) out[day] = { state: m, manual: true, kcal };
    else if (!a) out[day] = { state: "leer", manual: false, kcal: 0 };
    else if (day === today) out[day] = { state: "offen", manual: false, kcal };
    else {
      const full = a.kcal >= targetKcal * 0.6 || a.main.size >= Math.min(3, mealsPerDay);
      out[day] = { state: full ? "komplett" : "teilweise", manual: false, kcal };
    }
  }
  return out;
}

export function dayState(db: FitDb, day: string, targetKcal: number, mealsPerDay: number) {
  return dayStates(db, day, day, targetKcal, mealsPerDay)[day] ?? { state: "leer" as DayState, manual: false, kcal: 0 };
}

export function setDayState(db: FitDb, day: string, status: "komplett" | "teilweise" | null) {
  if (!status) db.prepare("DELETE FROM food_day WHERE day = ?").run(day);
  else db.prepare("INSERT INTO food_day(day, status, at) VALUES(?, ?, ?) ON CONFLICT(day) DO UPDATE SET status = excluded.status, at = excluded.at").run(day, status, Date.now());
}
