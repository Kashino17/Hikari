/**
 * Körperfortschritt: Gewichtstrend und Prognose zum Zielgewicht, Umfänge, Fortschrittsfotos.
 * Fotos liegen unter <dir>/body (nicht im Repo), ausgeliefert über /fitness/body/photo/:file.
 */
import { type FitDb, addDays, localDay } from "./db.js";
import { type Goal, computeTargets, getProfile, saveProfile } from "./profile.js";

/** kg pro Woche aus den letzten 28 Tagen (lineare Regression), null bei zu wenigen Werten. */
export function weightTrend(rows: { day: string; weight: number }[]): number | null {
  const pts = rows.filter((r) => r.day >= addDays(localDay(), -28));
  if (pts.length < 3) return null;
  const t0 = Date.parse(`${pts[0]!.day}T12:00:00`);
  const xs = pts.map((p) => (Date.parse(`${p.day}T12:00:00`) - t0) / 86_400_000);
  const ys = pts.map((p) => p.weight);
  const mx = xs.reduce((a, b) => a + b, 0) / xs.length;
  const my = ys.reduce((a, b) => a + b, 0) / ys.length;
  const den = xs.reduce((a, x) => a + (x - mx) ** 2, 0);
  if (!den) return null;
  const slope = xs.reduce((a, x, i) => a + (x - mx) * (ys[i]! - my), 0) / den;
  return Math.round(slope * 7 * 100) / 100;
}

/** Gesundes Tempo je Ziel in Anteil Körpergewicht pro Woche (Muskelaufbau: 0,25–0,5 %, Abnehmen: 0,5–1 %). */
export const RATE_BAND: Record<Goal, [number, number]> = {
  muskeln: [0.0025, 0.005],
  zunehmen: [0.004, 0.0075],
  abnehmen: [-0.01, -0.005],
  halten: [-0.0015, 0.0015],
  fitter: [-0.0015, 0.0015],
};

/**
 * Kalorienziel nachsteuern wie gute Ernährungs-Coaches: alle 14 Tage den Gewichtstrend gegen das
 * Zielband prüfen und um 100 kcal anpassen. Nur wenn das Tagebuch zeigt, dass das Ziel auch gegessen
 * wird (≥ 10 Tage von 14 eingetragen, im Schnitt ≥ 90 % des Ziels) — sonst ist das Problem die Menge,
 * nicht das Ziel, und es gibt nur einen Hinweis.
 */
export function adaptCalories(db: FitDb, now = Date.now()): { adjust: number; note: string } | null {
  const p = getProfile(db);
  if (now - p.kcalAdjustAt < 14 * 86_400_000) return null;
  const today = localDay(new Date(now));
  const weights = db.prepare("SELECT day, weight FROM body_log WHERE weight IS NOT NULL AND day >= ? ORDER BY day").all(addDays(today, -28)) as { day: string; weight: number }[];
  if (weights.length < 4 || weights[0]!.day > addDays(today, -13)) return null;
  const trend = weightTrend(weights);
  if (trend == null) return null;
  const bw = weights[weights.length - 1]!.weight;
  const [lo, hi] = RATE_BAND[p.goal].map((r) => r * bw) as [number, number];
  const dir = trend < lo ? 1 : trend > hi ? -1 : 0;
  const fmt = (v: number) => `${v > 0 ? "+" : ""}${v.toFixed(2).replace(".", ",")} kg/Woche`;
  if (!dir) {
    saveProfile(db, { kcalAdjustAt: now, kcalAdjustNote: `Gewicht ${fmt(trend)} — im Zielbereich, Ziel bleibt` });
    return { adjust: p.kcalAdjust, note: "im Zielbereich" };
  }
  // Wird das Ziel überhaupt gegessen? (nur für eine Erhöhung entscheidend)
  const rows = db.prepare("SELECT day, totals FROM food_log WHERE day >= ? AND day < ?").all(addDays(today, -14), today) as { day: string; totals: string }[];
  const perDay = new Map<string, number>();
  for (const r of rows) perDay.set(r.day, (perDay.get(r.day) ?? 0) + (Number((JSON.parse(r.totals) as { kcal?: number }).kcal) || 0));
  const target = computeTargets(p).kcal;
  const avg = perDay.size ? [...perDay.values()].reduce((a, b) => a + b, 0) / perDay.size : 0;
  if (dir > 0 && (perDay.size < 10 || avg < target * 0.9)) {
    const note = perDay.size < 10
      ? `Gewicht ${fmt(trend)} — zu langsam. Trag dein Essen ein, dann kann der Coach dein Ziel richtig nachstellen.`
      : `Gewicht ${fmt(trend)} — zu langsam. Du isst im Schnitt ${Math.round(avg)} kcal, Ziel ${target} — erst das Ziel erreichen.`;
    saveProfile(db, { kcalAdjustAt: now, kcalAdjustNote: note });
    return { adjust: p.kcalAdjust, note };
  }
  const adjust = Math.max(-600, Math.min(600, p.kcalAdjust + dir * 100));
  const note = `Gewicht ${fmt(trend)} — ${dir > 0 ? "zu langsam, Ziel +100 kcal" : "zu schnell, Ziel −100 kcal"}`;
  saveProfile(db, { kcalAdjust: adjust, kcalAdjustAt: now, kcalAdjustNote: note });
  return { adjust, note };
}

export function bodyProgress(db: FitDb) {
  const p = getProfile(db);
  const weights = db.prepare("SELECT day, weight FROM body_log WHERE weight IS NOT NULL AND day >= ? ORDER BY day").all(addDays(localDay(), -365)) as { day: string; weight: number }[];
  const trend = weightTrend(weights);
  const last = weights[weights.length - 1] ?? null;
  let eta: string | null = null;
  if (p.targetWeightKg && last && trend && Math.sign(p.targetWeightKg - last.weight) === Math.sign(trend) && Math.abs(trend) >= 0.05) {
    const weeks = (p.targetWeightKg - last.weight) / trend;
    if (weeks > 0 && weeks < 156) eta = addDays(localDay(), Math.round(weeks * 7));
  }
  const measures = db.prepare("SELECT day, chest, arm, waist, hips, thigh FROM body_measure ORDER BY day").all();
  const photos = (db.prepare("SELECT id, day, pose, file FROM body_photo ORDER BY day DESC, at DESC").all() as { id: string; day: string; pose: string; file: string }[])
    .map((x) => ({ ...x, url: `/fitness/body/photo/${x.file}` }));
  // Gesundes Tempo fürs Ziel (kg/Woche) — damit der Verlauf eingeordnet werden kann.
  const bw = last?.weight ?? p.weightKg;
  const band = RATE_BAND[p.goal].map((r) => Math.round(r * bw * 100) / 100);
  return { weights, trend, target: p.targetWeightKg ?? null, eta, measures, photos, goal: p.goal, band, adjustNote: p.kcalAdjustNote };
}
