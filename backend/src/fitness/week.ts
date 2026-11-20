/**
 * Woche: Vorschau (was kommt wann), Muskelbalance und der Wochenrückblick des Coachs.
 * Rückblick wird nur auf Wunsch erzeugt (kostet eine KI-Anfrage) und als coach_notes kind 'woche'
 * mit dem Montag der Woche gespeichert.
 */
import type { FitnessAi } from "./ai.js";
import type { Catalog } from "./catalog.js";
import { buildContext, sessionsBetween } from "./coach.js";
import { type FitDb, addDays, localDay, weekday } from "./db.js";
import { DAY_TYPES, type DayType, chooseDayType } from "./planner.js";
import { computeTargets, getProfile } from "./profile.js";
import { balance, getPrograms, mondayOf, phaseFor } from "./program.js";

/** Die laufende Woche: erledigt, geplant, und für offene Trainingstage die voraussichtliche Art. */
export function weekPreview(db: FitDb, catalog: Catalog, today = localDay()) {
  const monday = mondayOf(today);
  const p = getProfile(db);
  const sessions = sessionsBetween(db, monday, addDays(monday, 6));
  const predicted: { day: string; type: DayType }[] = [];
  const days = Array.from({ length: 7 }, (_, i) => {
    const day = addDays(monday, i);
    const s = sessions.filter((x) => x.day === day && x.status !== "skipped").sort((a, b) => (a.status === "done" ? -1 : 1) - (b.status === "done" ? -1 : 1))[0];
    const training = p.trainingDays.includes(weekday(day));
    let pred: DayType | null = null;
    if (!s && training && day >= today) {
      // Voraussichtlich: wie der Planer wählen würde, mit den schon vorhergesagten Tagen davor.
      const ctx = buildContext(db, catalog, day);
      for (const q of predicted) {
        const daysAgo = Math.round((Date.parse(`${day}T12:00:00`) - Date.parse(`${q.day}T12:00:00`)) / 86_400_000);
        ctx.recent.unshift({ daysAgo, groups: DAY_TYPES[q.type]?.groups ?? [], dayType: q.type, hard: false });
      }
      ctx.recent.sort((a, b) => a.daysAgo - b.daysAgo);
      pred = chooseDayType(ctx);
      predicted.push({ day, type: pred });
    }
    return {
      day,
      training,
      session: s ? { id: s.id, status: s.status, title: s.plan.title, dayType: s.plan.dayType, minutes: Math.round((s.result?.seconds ?? 0) / 60) || null } : null,
      predicted: pred ? { dayType: pred, title: DAY_TYPES[pred]?.title ?? pred } : null,
    };
  });
  const bal = balance(db, catalog, monday, addDays(monday, 6));
  const avg: Record<string, number> = {};
  for (let w = 1; w <= 4; w++) {
    const m = addDays(monday, -7 * w);
    const b = balance(db, catalog, m, addDays(m, 6));
    for (const [k, v] of Object.entries(b)) avg[k] = (avg[k] ?? 0) + v / 4;
  }
  const lastMonday = addDays(monday, -7);
  const lastWeekDone = sessionsBetween(db, lastMonday, addDays(lastMonday, 6)).filter((s) => s.status === "done").length;
  const rev = db.prepare("SELECT data FROM coach_notes WHERE kind = 'woche' AND day = ? ORDER BY at DESC LIMIT 1").get(lastMonday) as { data: string } | undefined;
  return {
    monday,
    days,
    program: phaseFor(db, getPrograms(), today),
    balance: { week: bal, avg4: Object.fromEntries(Object.entries(avg).map(([k, v]) => [k, Math.round(v * 10) / 10])) },
    review: { monday: lastMonday, sessions: lastWeekDone, headline: rev ? (JSON.parse(rev.data).headline as string) : null },
  };
}

const WEEK_SYSTEM = `Du bist der persönliche Coach des Nutzers (Körpergewicht/Calisthenics, Ernährung). Schreib seinen Wochenrückblick auf Deutsch, du-Form, ohne Emojis, ehrlich und konkret — wie ein guter Personal Trainer am Montag.
Du bekommst: Einheiten der Woche mit Feedback, Muskelbalance (Arbeitssätze je Gruppe, dazu der Schnitt der 4 Wochen davor), Auf-/Abstiege bei Übungen, Ernährung (Noten des Ernährungs-Coachs, Kalorien/Eiweiß in % vom Ziel), Gewicht, Programm und die kommende Phase.
Regeln: Zahlen nennen, wo sie etwas zeigen. Ungleichgewichte ansprechen. Nichts erfinden, was nicht in den Daten steht. Wurde kaum trainiert: freundlich, aber klar, und einen realistischen Plan machen.
Antworte NUR mit JSON: {"headline":"eine Zeile","zahlen":"ein Satz mit den wichtigsten Zahlen","gut":["…"],"achten":["…"],"naechste_woche":["konkret, was du nächste Woche planst/änderst"],"fokus":"ein kurzer Satz als Motto für die Woche"}`;

export async function weekReview(ai: FitnessAi, db: FitDb, catalog: Catalog, monday: string) {
  const sunday = addDays(monday, 6);
  const name = (id: string) => catalog.byId.get(id)?.name ?? id;
  const sessions = sessionsBetween(db, monday, sunday).filter((s) => s.status === "done");
  const p = getProfile(db);
  const bal = balance(db, catalog, monday, sunday);
  const prev: Record<string, number> = {};
  for (let w = 1; w <= 4; w++) {
    const m = addDays(monday, -7 * w);
    for (const [k, v] of Object.entries(balance(db, catalog, m, addDays(m, 6)))) prev[k] = Math.round(((prev[k] ?? 0) + v / 4) * 10) / 10;
  }
  const events = db.prepare("SELECT day, kind, ex_from, ex_to, level FROM progress_events WHERE day >= ? AND day <= ?").all(monday, sunday) as { day: string; kind: string; ex_from: string | null; ex_to: string | null; level: number | null }[];
  const scores = (db.prepare("SELECT day, data FROM coach_notes WHERE kind = 'ernaehrung' AND day >= ? AND day <= ? ORDER BY at").all(monday, sunday) as { day: string; data: string }[])
    .reduce<Record<string, number>>((a, r) => { try { a[r.day] = JSON.parse(r.data).score; } catch { /* */ } return a; }, {});
  const food = db.prepare("SELECT day, totals FROM food_log WHERE day >= ? AND day <= ?").all(monday, sunday) as { day: string; totals: string }[];
  const perDay: Record<string, { kcal: number; protein: number }> = {};
  for (const f of food) { const t = JSON.parse(f.totals); const d = (perDay[f.day] ??= { kcal: 0, protein: 0 }); d.kcal += t.kcal ?? 0; d.protein += t.protein ?? 0; }
  const nDays = Object.keys(perDay).length;
  const tg = computeTargets(p);
  const weights = db.prepare("SELECT day, weight FROM body_log WHERE weight IS NOT NULL AND day >= ? AND day <= ? ORDER BY day").all(addDays(monday, -7), sunday) as { day: string; weight: number }[];
  const phase = phaseFor(db, getPrograms(), monday);
  const nextPhase = phaseFor(db, getPrograms(), addDays(monday, 7));
  const input = {
    woche: `${monday} bis ${sunday}`,
    ziel: p.goal,
    trainingstage_geplant: p.trainingDays.length,
    einheiten: sessions.map((s) => ({
      tag: s.day, titel: s.plan.title, art: s.plan.dayType, intensitaet: s.plan.intensity, minuten: Math.round((s.result?.seconds ?? 0) / 60),
      gefuehl: s.feedback?.overall, schmerz: s.feedback?.pain ?? [],
    })),
    muskelbalance_saetze: { diese_woche: bal, schnitt_4_wochen_davor: prev },
    fortschritt: events.map((e) => ({ tag: e.day, art: e.kind, von: e.ex_from ? name(e.ex_from) : null, zu: e.ex_to ? name(e.ex_to) : null, level: e.level })),
    ernaehrung: {
      tage_mit_eintraegen: Object.keys(perDay).length,
      noten: scores,
      schnitt_kcal_prozent_vom_ziel: nDays ? Math.round((Object.values(perDay).reduce((a, d) => a + d.kcal, 0) / nDays / tg.kcal) * 100) : null,
      schnitt_eiweiss_prozent_vom_ziel: nDays ? Math.round((Object.values(perDay).reduce((a, d) => a + d.protein, 0) / nDays / tg.protein) * 100) : null,
    },
    gewicht: weights.map((w) => ({ tag: w.day, kg: w.weight })),
    programm: phase ? { name: phase.name, woche: `${phase.week} von ${phase.weeks}`, phase: phase.phase } : null,
    naechste_programmphase: nextPhase && !nextPhase.done ? { phase: nextPhase.phase, notiz: nextPhase.note, entlastung: nextPhase.deload } : null,
  };
  const { value, model } = await ai.ask(
    { task: "text", system: WEEK_SYSTEM, input, maxTokens: 1600, temperature: 0.5 },
    (raw) => {
      const r = raw as { headline?: string; zahlen?: string; gut?: string[]; achten?: string[]; naechste_woche?: string[]; fokus?: string };
      if (!r.headline) throw new Error("leer");
      const list = (a: unknown) => (Array.isArray(a) ? a.map((s) => String(s).slice(0, 240)).slice(0, 4) : []);
      return { headline: String(r.headline).slice(0, 140), zahlen: String(r.zahlen ?? "").slice(0, 300), gut: list(r.gut), achten: list(r.achten), naechste_woche: list(r.naechste_woche), fokus: String(r.fokus ?? "").slice(0, 140) };
    },
  );
  const data = { ...value, sessions: sessions.length, balance: bal, at: Date.now() };
  db.prepare("INSERT INTO coach_notes(kind, day, data, model, at) VALUES('woche', ?, ?, ?, ?)").run(monday, JSON.stringify(data), model, Date.now());
  return data;
}
