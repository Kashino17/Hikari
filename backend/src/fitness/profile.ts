/**
 * Profil (Körper, Ziele, Ausrüstung, Trainingstage, Ernährungsvorlieben) und daraus abgeleitete
 * Tagesziele für Energie, Makro- und Mikronährstoffe.
 *
 * Mikronährstoff-Ziele: Referenzwerte der DGE (D-A-CH) für Erwachsene 25–50; »max« = Obergrenze
 * (gesättigte Fette, Zucker, Salz), dort ist weniger besser.
 */
import { type FitDb, kvGet, kvSet } from "./db.js";

export type Goal = "abnehmen" | "muskeln" | "zunehmen" | "halten" | "fitter";
export type Sex = "m" | "w";

export interface Profile {
  name: string;
  sex: Sex;
  birthYear: number;
  heightCm: number;
  weightKg: number;
  targetWeightKg: number | null;
  goal: Goal;
  /** 1 Einsteiger … 5 sehr fortgeschritten */
  level: number;
  /** Alltag ohne Training: 1 sitzend, 2 leicht, 3 aktiv, 4 körperlich hart */
  activity: number;
  /** 0 = Montag … 6 = Sonntag */
  trainingDays: number[];
  sessionMinutes: number;
  equipment: string[];
  /** Körperregionen mit dauerhaften Beschwerden (knie, schulter, …) */
  limitations: string[];
  /** Schwerpunkte: kraft, ausdauer, beweglichkeit, skills, abnehmen */
  focus: string[];
  /** Keine Sprünge (Nachbarn, Knie) */
  noJumps: boolean;
  diet: "alles" | "vegetarisch" | "vegan" | "pescetarisch";
  allergies: string[];
  dislikes: string[];
  mealsPerDay: number;
  cookingMinutes: number;
  onboarded: boolean;
  updatedAt: number;
}

export const DEFAULT_PROFILE: Profile = {
  name: "",
  sex: "m",
  birthYear: 1998,
  heightCm: 178,
  weightKg: 78,
  targetWeightKg: null,
  goal: "muskeln",
  level: 2,
  activity: 1,
  trainingDays: [0, 2, 4, 5],
  sessionMinutes: 35,
  equipment: ["boden"],
  limitations: [],
  focus: ["kraft"],
  noJumps: false,
  diet: "alles",
  allergies: [],
  dislikes: [],
  mealsPerDay: 3,
  cookingMinutes: 30,
  onboarded: false,
  updatedAt: 0,
};

const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v));

export function normalizeProfile(raw: Partial<Profile> | null | undefined): Profile {
  const p = { ...DEFAULT_PROFILE, ...(raw ?? {}) };
  const goals: Goal[] = ["abnehmen", "muskeln", "zunehmen", "halten", "fitter"];
  return {
    ...p,
    name: String(p.name ?? "").slice(0, 40),
    sex: p.sex === "w" ? "w" : "m",
    birthYear: clamp(Math.round(Number(p.birthYear) || 1998), 1930, new Date().getFullYear() - 12),
    heightCm: clamp(Number(p.heightCm) || 178, 120, 230),
    weightKg: clamp(Number(p.weightKg) || 78, 35, 250),
    targetWeightKg: p.targetWeightKg ? clamp(Number(p.targetWeightKg), 35, 250) : null,
    goal: goals.includes(p.goal) ? p.goal : "muskeln",
    level: clamp(Math.round(Number(p.level) || 2), 1, 5),
    activity: clamp(Math.round(Number(p.activity) || 1), 1, 4),
    trainingDays: [
      ...new Set((p.trainingDays ?? []).map(Number).filter((d) => d >= 0 && d <= 6)),
    ].sort(),
    sessionMinutes: clamp(Math.round(Number(p.sessionMinutes) || 35), 10, 90),
    equipment: [...new Set(["boden", ...(p.equipment ?? []).map(String)])],
    limitations: [...new Set((p.limitations ?? []).map(String))],
    focus: [...new Set((p.focus ?? []).map(String))],
    noJumps: !!p.noJumps,
    diet: (["alles", "vegetarisch", "vegan", "pescetarisch"] as const).includes(p.diet)
      ? p.diet
      : "alles",
    allergies: (p.allergies ?? []).map(String).slice(0, 20),
    dislikes: (p.dislikes ?? []).map(String).slice(0, 40),
    mealsPerDay: clamp(Math.round(Number(p.mealsPerDay) || 3), 2, 6),
    cookingMinutes: clamp(Math.round(Number(p.cookingMinutes) || 30), 10, 120),
    onboarded: !!p.onboarded,
  };
}

export function getProfile(db: FitDb): Profile {
  return normalizeProfile(kvGet<Profile>(db, "profile"));
}

export function saveProfile(db: FitDb, patch: Partial<Profile>): Profile {
  const next = normalizeProfile({ ...getProfile(db), ...patch, updatedAt: Date.now() });
  next.updatedAt = Date.now();
  kvSet(db, "profile", next);
  return next;
}

// ── Nährstoffe ───────────────────────────────────────────────────────────────

/** Alle Nährstoffe, die der Bereich kennt (Schlüssel = Feld in den Nährwert-Objekten). */
export const NUTRIENTS = [
  { k: "kcal", name: "Energie", unit: "kcal", group: "makro" },
  { k: "protein", name: "Eiweiß", unit: "g", group: "makro" },
  { k: "carbs", name: "Kohlenhydrate", unit: "g", group: "makro" },
  { k: "fat", name: "Fett", unit: "g", group: "makro" },
  { k: "fiber", name: "Ballaststoffe", unit: "g", group: "makro" },
  { k: "sugar", name: "Zucker", unit: "g", group: "grenze" },
  { k: "satfat", name: "Gesättigte Fette", unit: "g", group: "grenze" },
  { k: "salt", name: "Salz", unit: "g", group: "grenze" },
  { k: "omega3", name: "Omega-3", unit: "g", group: "fett" },
  { k: "calcium", name: "Calcium", unit: "mg", group: "mineral" },
  { k: "magnesium", name: "Magnesium", unit: "mg", group: "mineral" },
  { k: "iron", name: "Eisen", unit: "mg", group: "mineral" },
  { k: "zinc", name: "Zink", unit: "mg", group: "mineral" },
  { k: "potassium", name: "Kalium", unit: "mg", group: "mineral" },
  { k: "iodine", name: "Jod", unit: "µg", group: "mineral" },
  { k: "vitA", name: "Vitamin A", unit: "µg", group: "vitamin" },
  { k: "vitC", name: "Vitamin C", unit: "mg", group: "vitamin" },
  { k: "vitD", name: "Vitamin D", unit: "µg", group: "vitamin" },
  { k: "vitE", name: "Vitamin E", unit: "mg", group: "vitamin" },
  { k: "vitK", name: "Vitamin K", unit: "µg", group: "vitamin" },
  { k: "vitB1", name: "Vitamin B1", unit: "mg", group: "vitamin" },
  { k: "vitB2", name: "Vitamin B2", unit: "mg", group: "vitamin" },
  { k: "vitB6", name: "Vitamin B6", unit: "mg", group: "vitamin" },
  { k: "vitB12", name: "Vitamin B12", unit: "µg", group: "vitamin" },
  { k: "folate", name: "Folat", unit: "µg", group: "vitamin" },
] as const;

export type NutrientKey = (typeof NUTRIENTS)[number]["k"];
export type Nutrients = Partial<Record<NutrientKey, number>>;

export interface Targets {
  kcal: number;
  protein: number;
  carbs: number;
  fat: number;
  water: number;
  /** Ziel (min) bzw. Obergrenze (max) je Nährstoff */
  goals: Record<string, { min?: number; max?: number }>;
  explain: { bmr: number; pal: number; training: number; delta: number; proteinPerKg: number; refWeight: number };
}

export function age(p: Profile, now = new Date()): number {
  return now.getFullYear() - p.birthYear;
}

export function computeTargets(p: Profile, now = new Date()): Targets {
  const a = age(p, now);
  const bmr = Math.round(10 * p.weightKg + 6.25 * p.heightCm - 5 * a + (p.sex === "m" ? 5 : -161));
  const pal = [1.35, 1.45, 1.6, 1.8][p.activity - 1] ?? 1.45;
  // Training im Wochenmittel: je Einheit ~6,5 kcal/min (Körpergewichts-Zirkel), auf 7 Tage verteilt.
  const training = Math.round(
    (p.trainingDays.length * p.sessionMinutes * 6.5 * (p.weightKg / 75)) / 7,
  );
  const tdee = bmr * pal + training;
  const delta =
    p.goal === "abnehmen"
      ? -Math.min(550, Math.round(tdee * 0.2))
      : p.goal === "zunehmen"
        ? Math.round(tdee * 0.12)
        : p.goal === "muskeln"
          ? Math.round(tdee * 0.07)
          : 0;
  const kcal = Math.round((tdee + delta) / 10) * 10;
  const proteinPerKg = { abnehmen: 2.0, muskeln: 2.0, zunehmen: 1.8, halten: 1.6, fitter: 1.6 }[
    p.goal
  ];
  // Beim Abnehmen mit viel Übergewicht: Eiweiß an der fettfreien Masse grob begrenzen.
  const refWeight = Math.min(p.weightKg, (p.heightCm - 100) * 1.15);
  const protein = Math.round(refWeight * proteinPerKg);
  const fat = Math.round(Math.max(0.8 * p.weightKg, (kcal * 0.28) / 9));
  const carbs = Math.max(50, Math.round((kcal - protein * 4 - fat * 9) / 4));
  const m = p.sex === "m";
  return {
    kcal,
    protein,
    carbs,
    fat,
    water: Math.round((p.weightKg * 35 + p.sessionMinutes * 8) / 100) / 10,
    goals: {
      kcal: { min: kcal },
      protein: { min: protein },
      carbs: { min: carbs },
      fat: { min: fat },
      fiber: { min: 30 },
      sugar: { max: Math.round((kcal * 0.1) / 4) },
      satfat: { max: Math.round((kcal * 0.1) / 9) },
      salt: { max: 6 },
      omega3: { min: 1.6 },
      calcium: { min: 1000 },
      magnesium: { min: m ? 350 : 300 },
      iron: { min: m ? 11 : 16 },
      zinc: { min: m ? 14 : 8 },
      potassium: { min: 4000 },
      iodine: { min: 200 },
      vitA: { min: m ? 850 : 700 },
      vitC: { min: m ? 110 : 95 },
      vitD: { min: 20 },
      vitE: { min: m ? 14 : 12 },
      vitK: { min: m ? 70 : 60 },
      vitB1: { min: m ? 1.2 : 1.0 },
      vitB2: { min: m ? 1.4 : 1.1 },
      vitB6: { min: m ? 1.6 : 1.4 },
      vitB12: { min: 4 },
      folate: { min: 300 },
    },
    explain: { bmr, pal, training, delta, proteinPerKg, refWeight: Math.round(refWeight) },
  };
}

export function sumNutrients(list: Nutrients[]): Nutrients {
  const out: Nutrients = {};
  for (const n of list)
    for (const [k, v] of Object.entries(n)) {
      if (typeof v !== "number" || !Number.isFinite(v)) continue;
      out[k as NutrientKey] = (out[k as NutrientKey] ?? 0) + v;
    }
  return out;
}

export function scaleNutrients(per100: Nutrients, grams: number): Nutrients {
  const out: Nutrients = {};
  for (const [k, v] of Object.entries(per100))
    if (typeof v === "number") out[k as NutrientKey] = (v * grams) / 100;
  return out;
}

export function roundNutrients(n: Nutrients): Nutrients {
  const out: Nutrients = {};
  for (const [k, v] of Object.entries(n)) {
    if (typeof v !== "number") continue;
    out[k as NutrientKey] =
      Math.abs(v) >= 100
        ? Math.round(v)
        : Math.abs(v) >= 10
          ? Math.round(v * 10) / 10
          : Math.round(v * 100) / 100;
  }
  return out;
}
