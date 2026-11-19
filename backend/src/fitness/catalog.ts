/**
 * Übungskatalog (backend/fitness/exercises.json) und Skill-Bäume (backend/fitness/skills.json).
 * Texte (Ausführung, häufige Fehler, Sicherheit) sind von Hand auf Deutsch geschrieben; die
 * Videos/Bilder werden vom Server einmal geladen und unter ~/.hikari/fitness/media zwischengespeichert.
 */
import { readFileSync } from "node:fs";

export type Pattern =
  | "push"
  | "pull"
  | "squat"
  | "hinge"
  | "lunge"
  | "core"
  | "cardio"
  | "mobility"
  | "stretch"
  | "skill";

export interface MediaRef {
  /** Herkunfts-URL der Videoschleife (mp4/gif/webm) — der Server lädt sie einmal. */
  video?: string;
  /** Standbild (Vorschau) */
  image?: string;
  /** Zweite Perspektive (Seite) */
  video2?: string;
  credit?: string;
}

export interface Exercise {
  id: string;
  name: string;
  en?: string;
  pattern: Pattern;
  muscles: string[];
  secondary?: string[];
  equipment: string[];
  /** Schwierigkeit 1 (sehr leicht) … 10 (Elite) */
  level: number;
  unit: "reps" | "sec";
  perSide?: boolean;
  jump?: boolean;
  /** Belastete Regionen: knie, schulter, handgelenk, ruecken, ellbogen, huefte, nacken, sprunggelenk */
  stress: string[];
  /** In welchen Blöcken die Übung vorkommen darf */
  use: ("warmup" | "main" | "cooldown")[];
  /** Grundmenge (Wdh. bzw. Sekunden je Satz) für Level 2 */
  base: number;
  /** Sekunden je Wiederholung (Zeitschätzung) */
  secPerRep?: number;
  regress?: string;
  progress?: string;
  steps: string[];
  mistakes: string[];
  tips?: string[];
  breathing?: string;
  media?: MediaRef;
}

export interface SkillStep {
  name: string;
  /** Übung aus dem Katalog (für Video + Anleitung) */
  exercise?: string;
  goal: { type: "hold" | "reps"; sets: number; value: number };
  description: string;
  cues: string[];
  /** YouTube-Tutorials (IDs) */
  videos?: { id: string; title: string; channel: string; start?: number }[];
}

export interface Skill {
  id: string;
  name: string;
  en: string;
  color: string;
  icon: string;
  group: "balance" | "zug" | "druck" | "core" | "beine";
  summary: string;
  requires?: string[];
  prep: string[];
  steps: SkillStep[];
  videos?: { id: string; title: string; channel: string; start?: number }[];
  safety: string[];
}

export interface Catalog {
  exercises: Exercise[];
  byId: Map<string, Exercise>;
  skills: Skill[];
  skillById: Map<string, Skill>;
}

export function loadCatalog(exFile: string, skillFile: string): Catalog {
  const exercises = (JSON.parse(readFileSync(exFile, "utf8")) as { exercises: Exercise[] })
    .exercises;
  const skills = (JSON.parse(readFileSync(skillFile, "utf8")) as { skills: Skill[] }).skills;
  return {
    exercises,
    byId: new Map(exercises.map((e) => [e.id, e])),
    skills,
    skillById: new Map(skills.map((s) => [s.id, s])),
  };
}

/** Ausrüstung, die der Nutzer angeben kann. »boden« = nur Körper und Boden (immer vorhanden). */
export const EQUIPMENT: Record<string, string> = {
  boden: "Nur Körpergewicht",
  wand: "Wand",
  klimmzugstange: "Klimmzugstange",
  barren: "Dip-Barren / Stühle",
  ringe: "Turnringe",
  parallettes: "Parallettes",
  bank: "Bank / Stuhl",
  band: "Widerstandsband",
  kurzhantel: "Kurzhanteln",
  kettlebell: "Kettlebell",
  springseil: "Springseil",
  matte: "Matte",
};

export function hasEquipment(e: Exercise, owned: string[]): boolean {
  const set = new Set([...owned, "boden", "wand", "matte"]);
  return e.equipment.every((q) => set.has(q));
}
