/**
 * Programme über mehrere Wochen (Freeletics »Journeys«): Ziel, Phasen mit Volumen, Intensität und
 * Fokus, Entlastungswochen. Der Planer liest die aktuelle Phase (PlanContext.program), die KI auch.
 * Dazu die Muskelbalance: erledigte Arbeitssätze je Gruppe in einer Woche.
 */
import { existsSync, readFileSync } from "node:fs";
import type { Catalog } from "./catalog.js";
import { type FitDb, addDays, kvGet, kvSet, weekday } from "./db.js";
import type { DayType } from "./planner.js";

export interface ProgramPhase {
  wochen: number[];
  name: string;
  volumen: number;
  intensitaet: "leicht" | "mittel" | "hart";
  fokus: Partial<Record<DayType, number>>;
  notiz: string;
}
export interface Program {
  id: string;
  name: string;
  kurz: string;
  ziel: string;
  wochen: number;
  bild: string;
  fuer: string;
  braucht?: string[];
  phasen: ProgramPhase[];
}
export interface ProgramPhaseCtx {
  id: string;
  name: string;
  week: number;
  weeks: number;
  phase: string;
  note: string;
  volume: number;
  intensity: ProgramPhase["intensitaet"];
  focus: Partial<Record<DayType, number>>;
  deload: boolean;
  done: boolean;
}

let PROGRAMS: Program[] = [];
/** Einmal beim Start gesetzt (registerFitnessRoutes) — buildContext liest die Liste. */
export function setPrograms(list: Program[]) { PROGRAMS = list; }
export function getPrograms(): Program[] { return PROGRAMS; }

export function loadPrograms(dataDir: string): Program[] {
  const f = `${dataDir}/programs.json`;
  return existsSync(f) ? (JSON.parse(readFileSync(f, "utf8")) as Program[]) : [];
}

export const mondayOf = (day: string) => addDays(day, -weekday(day));

interface ProgramState { id: string; start: string }

export function programState(db: FitDb): ProgramState | null {
  return kvGet<ProgramState>(db, "program");
}

/** Phase des laufenden Programms an einem Tag — oder null (kein Programm / vorbei). */
export function phaseFor(db: FitDb, programs: Program[], day: string): ProgramPhaseCtx | null {
  const st = programState(db);
  if (!st) return null;
  const p = programs.find((x) => x.id === st.id);
  if (!p) return null;
  const week = Math.floor((Date.parse(`${mondayOf(day)}T12:00:00`) - Date.parse(`${st.start}T12:00:00`)) / (7 * 86_400_000)) + 1;
  if (week < 1) return null;
  const done = week > p.wochen;
  const ph = p.phasen.find((x) => x.wochen.includes(Math.min(week, p.wochen))) ?? p.phasen[p.phasen.length - 1];
  if (!ph) return null;
  return {
    id: p.id, name: p.name, week: Math.min(week, p.wochen), weeks: p.wochen, phase: ph.name, note: ph.notiz,
    volume: done ? 1 : ph.volumen, intensity: ph.intensitaet, focus: done ? {} : ph.fokus,
    deload: !done && ph.volumen <= 0.7, done,
  };
}

export function startProgram(db: FitDb, id: string, today: string) {
  kvSet(db, "program", { id, start: mondayOf(today) });
}
export function stopProgram(db: FitDb) {
  db.prepare("DELETE FROM kv WHERE key = 'program'").run();
}

const GROUP_OF: Record<string, string> = { push: "druck", pull: "zug", squat: "beine", lunge: "beine", hinge: "beine", core: "core", cardio: "ausdauer" };

/** Erledigte Arbeitssätze je Gruppe (Haupt- und Abschlussblöcke) zwischen from und to. */
export function balance(db: FitDb, catalog: Catalog, from: string, to: string): Record<string, number> {
  const rows = db.prepare("SELECT plan, result FROM sessions WHERE status = 'done' AND day >= ? AND day <= ?").all(from, to) as { plan: string; result: string | null }[];
  const out: Record<string, number> = { druck: 0, zug: 0, beine: 0, core: 0, ausdauer: 0 };
  for (const r of rows) {
    const plan = JSON.parse(r.plan) as { blocks: { kind: string; rounds?: number; items: { ex: string }[] }[] };
    const res = r.result ? (JSON.parse(r.result) as { done?: Record<string, number[]> }) : {};
    plan.blocks.forEach((b, bi) => {
      if (b.kind !== "main" && b.kind !== "finisher") return;
      b.items.forEach((it, ii) => {
        const g = GROUP_OF[catalog.byId.get(it.ex)?.pattern ?? ""];
        if (!g) return;
        const sets = res.done?.[`${bi}:${ii}`]?.filter((v) => v != null).length ?? 0;
        out[g] = (out[g] ?? 0) + sets;
      });
    });
  }
  return out;
}
