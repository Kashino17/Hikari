/**
 * Körperfortschritt: Gewichtstrend und Prognose zum Zielgewicht, Umfänge, Fortschrittsfotos.
 * Fotos liegen unter <dir>/body (nicht im Repo), ausgeliefert über /fitness/body/photo/:file.
 */
import { type FitDb, addDays, localDay } from "./db.js";
import { getProfile } from "./profile.js";

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
  return { weights, trend, target: p.targetWeightKg ?? null, eta, measures, photos, goal: p.goal };
}
