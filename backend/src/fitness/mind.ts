/**
 * Achtsamkeit: Meditation, Atem, Yoga-Pfad (6 Stufen zum Freischalten), Affirmationen, Wissen.
 * Inhalte liegen als JSON in backend/fitness/mind/ (Haltungen aus der yoga-api, MIT; Videos von
 * YouTube, gestreamt über den eigenen Server). Gemacht wird hier nur, was zählt: Protokoll,
 * Freischalten, Serie, Tagesimpuls.
 */
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { addDays, type FitDb, localDay } from "./db.js";

export interface MindVideo {
  id: string;
  kind: "meditation" | "yoga" | "yoga-nidra";
  title: string;
  orig?: string;
  channel: string;
  seconds: number;
  views?: number;
  lang: "de" | "en";
  themes: string[];
  level: number | null;
  note?: string;
}
export interface Asana {
  id: string;
  api: number;
  de: string;
  sanskrit: string;
  en: string;
  stufe: number;
  art: string;
  kurz: string;
  schritte: string[];
  fehler: string[];
  atem: string;
  wirkung: string[];
  achtsamkeit: string;
  vorsicht: string;
  erleichterung: string;
  vorstufe: string | null;
  halten: { start: number; ziel: number };
  seiten: boolean;
}
export interface FlowStep {
  phase: "ankommen" | "atem" | "asana" | "stille" | "savasana";
  asana: string | null;
  sekunden: number;
  seite: "links" | "rechts" | null;
  sage: string;
}
export interface YogaLevel {
  stufe: number;
  name: string;
  untertitel: string;
  minuten: number;
  ziel: string;
  intention: string;
  philosophie: string;
  freischalten: { einheiten: number; tage: number };
  flow: FlowStep[];
}
export interface MindContent {
  atem: { id: string; name: string; kurz: string; wofuer: string[]; phasen: { typ: string; sekunden: number; sage: string }[]; runden_standard: number; anleitung: string[]; warum: string; vorsicht: string }[];
  affirmationen: { text: string; thema: string }[];
  impulse: { titel: string; text: string }[];
  deepdives: { id: string; titel: string; untertitel: string; minuten: number; farbe: string; abschnitte: { h: string; text: string }[] }[];
  gefuehrt: { id: string; titel: string; untertitel: string; minuten: number; themen: string[]; schritte: { sage: string; pause: number }[] }[];
}

export interface MindCatalog {
  videos: MindVideo[];
  asanas: Asana[];
  levels: YogaLevel[];
  content: MindContent;
}

const EMPTY: MindContent = { atem: [], affirmationen: [], impulse: [], deepdives: [], gefuehrt: [] };

export function loadMind(dir: string): MindCatalog {
  const read = <T>(f: string, fallback: T): T => {
    const p = join(dir, f);
    return existsSync(p) ? (JSON.parse(readFileSync(p, "utf8")) as T) : fallback;
  };
  return {
    videos: read<MindVideo[]>("videos.json", []),
    asanas: read<Asana[]>("asanas.json", []),
    levels: read<YogaLevel[]>("yoga-levels.json", []).sort((a, b) => a.stufe - b.stufe),
    content: { ...EMPTY, ...read<Partial<MindContent>>("content.json", {}) },
  };
}

export interface MindLogRow {
  id: number;
  day: string;
  kind: string;
  ref: string | null;
  seconds: number;
  mood_before: number | null;
  mood_after: number | null;
  note: string | null;
  at: number;
}

/** Kleine, stabile Zahl aus einem Text — gleiche Auswahl den ganzen Tag. */
export function daySeed(day: string, salt = ""): number {
  let h = 2166136261;
  for (const c of day + salt) h = Math.imul(h ^ c.charCodeAt(0), 16777619) >>> 0;
  return h;
}

/** Stand des Yoga-Pfads: je Stufe gemachte Einheiten/Tage, freigeschaltet (auch manuell). */
export function yogaProgress(db: FitDb, levels: YogaLevel[]) {
  const rows = db
    .prepare("SELECT ref, COUNT(*) AS n, COUNT(DISTINCT day) AS d FROM mind_log WHERE kind = 'yoga' AND ref LIKE 'stufe-%' GROUP BY ref")
    .all() as { ref: string; n: number; d: number }[];
  const by = new Map(rows.map((r) => [Number(r.ref.slice(6)), r]));
  const mrow = db.prepare("SELECT value FROM kv WHERE key = 'mind:unlocked'").get() as { value: string } | undefined;
  const manual = new Set<number>(mrow ? (JSON.parse(mrow.value) as number[]) : []);
  let open = 1;
  const out = levels.map((l) => {
    const r = by.get(l.stufe);
    const sessions = r?.n ?? 0;
    const days = r?.d ?? 0;
    const met = sessions >= l.freischalten.einheiten && days >= l.freischalten.tage;
    return { stufe: l.stufe, sessions, days, need: l.freischalten, met, manual: manual.has(l.stufe + 1) };
  });
  // Stufe N+1 ist offen, wenn N geschafft (oder ausdrücklich übersprungen) ist — der Reihe nach.
  for (const p of out) {
    if (p.stufe < open) continue;
    if (p.stufe === open && (p.met || p.manual) && open < levels.length) open = p.stufe + 1;
  }
  return { open, levels: out.map((p) => ({ ...p, unlocked: p.stufe <= open, current: p.stufe === open })) };
}

export function setManualUnlock(db: FitDb, stufe: number) {
  const row = db.prepare("SELECT value FROM kv WHERE key = 'mind:unlocked'").get() as { value: string } | undefined;
  const list = new Set<number>(row ? (JSON.parse(row.value) as number[]) : []);
  list.add(stufe);
  db.prepare(
    "INSERT INTO kv(key, value, updated_at) VALUES('mind:unlocked', ?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at",
  ).run(JSON.stringify([...list]), Date.now());
}

/** Minuten, Serie (Tage in Folge mit Übung, heute oder gestern beginnend), Woche, Stimmung. */
export function mindStats(db: FitDb, today = localDay()) {
  const rows = db.prepare("SELECT day, SUM(seconds) AS s, COUNT(*) AS n FROM mind_log GROUP BY day").all() as { day: string; s: number; n: number }[];
  const by = new Map(rows.map((r) => [r.day, r]));
  let streak = 0;
  let d = by.has(today) ? today : addDays(today, -1);
  while (by.has(d)) {
    streak++;
    d = addDays(d, -1);
  }
  const week = Array.from({ length: 7 }, (_, i) => {
    const day = addDays(today, i - 6);
    return { day, minutes: Math.round((by.get(day)?.s ?? 0) / 60) };
  });
  const total = rows.reduce((a, r) => a + r.s, 0);
  const mood = db
    .prepare("SELECT AVG(mood_after - mood_before) AS delta, COUNT(*) AS n FROM mind_log WHERE mood_before IS NOT NULL AND mood_after IS NOT NULL")
    .get() as { delta: number | null; n: number };
  return {
    streak,
    todayMinutes: Math.round((by.get(today)?.s ?? 0) / 60),
    totalMinutes: Math.round(total / 60),
    sessions: rows.reduce((a, r) => a + r.n, 0),
    week,
    moodDelta: mood.n ? Math.round((mood.delta ?? 0) * 10) / 10 : null,
    moodCount: mood.n,
  };
}
