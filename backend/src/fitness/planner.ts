/**
 * Regel-Planer: baut den Trainingstag ohne KI — als Grundgerüst für den KI-Coach und als
 * Rückfall, wenn kein Modell erreichbar ist. Alles hier ist deterministisch (Zufall über den Tag
 * gesät), damit derselbe Tag beim Neuladen gleich aussieht.
 */
import type { Catalog, Exercise, Pattern } from "./catalog.js";
import { hasEquipment } from "./catalog.js";
import type { Profile } from "./profile.js";

export type DayType =
  | "ganzkoerper"
  | "druck"
  | "zug"
  | "beine"
  | "core"
  | "ausdauer"
  | "mobilitaet";

export const DAY_TYPES: Record<DayType, { title: string; groups: string[]; patterns: Pattern[] }> =
  {
    ganzkoerper: {
      title: "Ganzkörper",
      groups: ["druck", "zug", "beine", "core"],
      // Reihenfolge = Wichtigkeit (bei wenig Zeit fallen die hinteren weg): je ein Drücken, Ziehen, Beine,
      // Rumpf; bei mehr Zeit ein zweites Drücken/Ziehen, dann Hüfte und Ausfallschritt.
      patterns: ["push", "pull", "squat", "core", "hinge", "pull", "lunge", "push"],
    },
    druck: {
      title: "Oberkörper · Drücken",
      groups: ["druck", "core"],
      patterns: ["push", "push", "push", "core"],
    },
    zug: {
      title: "Oberkörper · Ziehen",
      groups: ["zug", "core"],
      patterns: ["pull", "pull", "pull", "core", "hinge"],
    },
    beine: {
      title: "Beine & Po",
      groups: ["beine", "core"],
      patterns: ["squat", "lunge", "hinge", "squat", "core"],
    },
    core: {
      title: "Rumpf & Stabilität",
      groups: ["core"],
      patterns: ["core", "core", "core", "hinge", "core"],
    },
    ausdauer: {
      title: "Ausdauer · Intervalle",
      groups: ["ausdauer", "beine"],
      patterns: ["cardio", "squat", "cardio", "push", "cardio", "core"],
    },
    mobilitaet: {
      title: "Beweglichkeit & Erholung",
      groups: [],
      patterns: ["mobility", "mobility", "stretch", "mobility", "stretch"],
    },
  };

export interface PlanItem {
  ex: string;
  amount: number;
  unit: "reps" | "sec";
  perSide?: boolean;
  /** Pause danach in Sekunden */
  rest: number;
  note?: string;
}

export interface PlanBlock {
  kind: "warmup" | "skill" | "main" | "finisher" | "cooldown";
  title: string;
  /** bei kind = skill: welcher Skill und welche Stufe */
  skill?: { id: string; step: number };
  /** flow = nacheinander einmal; sets = Sätze je Übung; rounds = Zirkel in Runden; intervals = Arbeit/Pause nach Zeit */
  format: "flow" | "sets" | "rounds" | "intervals";
  rounds: number;
  restBetweenRounds: number;
  items: PlanItem[];
}

export interface SessionPlan {
  title: string;
  dayType: DayType;
  intensity: "leicht" | "mittel" | "hart";
  minutes: number;
  kcal: number;
  coach: { headline: string; message: string; why: string[] };
  blocks: PlanBlock[];
  skillFocus?: string;
}

export interface ExerciseState {
  dose: number;
  /** Startmenge-Faktor beim ersten Mal (Level zu dem Zeitpunkt) — danach zählt nur noch das Feedback. */
  seed?: number | null;
  avoidUntil?: string | null;
  disliked?: boolean;
  technique?: number | null;
  times?: number;
}

export interface PlanContext {
  day: string;
  profile: Profile;
  catalog: Catalog;
  state: Map<string, ExerciseState>;
  /** Höchste schon überholte Schwierigkeit je Muster (progression.masteredLevels) */
  mastered?: Record<string, number> | undefined;
  /** Kürzlich abgestiegene Übungen (nicht direkt einplanen) */
  demoted?: Set<string> | undefined;
  /** Tage seit dem letzten erledigten Training (null = noch nie) */
  daysOff?: number | null | undefined;
  /** Belastete Gruppen der letzten Tage: [Tage her, Gruppen] */
  recent: { daysAgo: number; groups: string[]; dayType: DayType; hard: boolean; overall?: number }[];
  /** Regionen mit aktuellen Beschwerden (letzte 7 Tage) */
  painRegions: string[];
  checkin?: { energy?: number; minutes?: number; sore?: string[]; wish?: string; sleep?: number } | undefined;
  /** Health Connect: Schlaf letzte Nacht (min), Ruhepuls heute und Schnitt der 7 Tage davor */
  health?: { sleepMin?: number | null; restingHr?: number | null; restingHrAvg?: number | null; steps?: number | null } | null | undefined;
  forceType?: DayType | undefined;
  /** Laufendes Programm: Phase mit Volumen, Intensität, Fokus (program.ts) */
  program?: { volume: number; intensity: "leicht" | "mittel" | "hart"; focus: Partial<Record<DayType, number>>; deload: boolean } | null | undefined;
  /** Erledigte Arbeitssätze je Gruppe in dieser Woche (druck, zug, beine, core, ausdauer) */
  balance?: Record<string, number> | undefined;
}

// ── Zufall, über den Tag gesät ──────────────────────────────────────────────

function seeded(seed: string) {
  let h = 2166136261;
  for (const c of seed) h = Math.imul(h ^ c.charCodeAt(0), 16777619);
  return () => {
    h += 0x6d2b79f5;
    let t = h;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

// ── Auswahl ────────────────────────────────────────────────────────────────

export function usable(ctx: PlanContext, e: Exercise): boolean {
  const p = ctx.profile;
  if (!hasEquipment(e, p.equipment)) return false;
  if (p.noJumps && e.jump) return false;
  const st = ctx.state.get(e.id);
  if (st?.disliked) return false;
  if (st?.avoidUntil && st.avoidUntil >= ctx.day) return false;
  const blocked = new Set([...p.limitations, ...ctx.painRegions]);
  if (e.stress.some((r) => blocked.has(r))) return false;
  return true;
}

/** Ziel-Schwierigkeit für das Hauptprogramm: Level 1 → 2–3, Level 5 → 8–9. */
function targetDifficulty(level: number, intensity: SessionPlan["intensity"]) {
  const base = 0.5 + level * 1.6;
  return intensity === "leicht" ? base - 1 : intensity === "hart" ? base + 0.6 : base;
}

/**
 * Tagesform: Check-in-Energie (1–5), sonst 3 — eine Stufe weniger bei kurzer Nacht (< 6 h laut Uhr
 * oder »schlecht geschlafen«) oder deutlich erhöhtem Ruhepuls (≥ 7 Schläge über dem Schnitt).
 */
export function effectiveEnergy(ctx: PlanContext): number {
  let e = ctx.checkin?.energy ?? 3;
  const h = ctx.health;
  const shortNight = (h?.sleepMin != null && h.sleepMin > 0 && h.sleepMin < 360) || ctx.checkin?.sleep === 1;
  const highHr = h?.restingHr != null && h.restingHrAvg != null && h.restingHr >= h.restingHrAvg + 7;
  if (shortNight) e -= 1;
  if (highHr) e -= 1;
  return Math.max(1, Math.min(5, e));
}

export function chooseDayType(ctx: PlanContext): DayType {
  if (ctx.forceType) return ctx.forceType;
  const energy = effectiveEnergy(ctx);
  if (energy <= 1) return "mobilitaet";
  const fatigue: Record<string, number> = {};
  for (const r of ctx.recent) {
    const w = r.daysAgo <= 1 ? (r.hard ? 1.6 : 1) : r.daysAgo === 2 ? 0.5 : 0.15;
    for (const g of r.groups) fatigue[g] = (fatigue[g] ?? 0) + w;
  }
  for (const s of ctx.checkin?.sore ?? []) {
    const g =
      s === "beine"
        ? "beine"
        : s === "brust" || s === "arme" || s === "schulter"
          ? "druck"
          : s === "ruecken"
            ? "zug"
            : "core";
    fatigue[g] = (fatigue[g] ?? 0) + 1.2;
  }
  const p = ctx.profile;
  const canPull = p.equipment.some((q) => ["klimmzugstange", "ringe", "band"].includes(q));
  const last = ctx.recent[0]?.dayType;
  const trainedLast3 = ctx.recent.filter((r) => r.daysAgo <= 3).length;
  // Tage am Stück trainiert (Beweglichkeit zählt als Erholung) — ab 3 kommt ein Erholungstag.
  let streak = 0;
  for (let d = 1; d <= 6; d++) {
    if (ctx.recent.some((r) => r.daysAgo === d && r.dayType !== "mobilitaet")) streak++;
    else break;
  }
  // Wie oft jede Hauptgruppe in den letzten 6 Tagen dran war — Ziel: jede mindestens 2× pro Woche.
  const hits: Record<string, number> = {};
  for (const r of ctx.recent) if (r.daysAgo >= 1) for (const g of HITS[r.dayType] ?? []) hits[g] = (hits[g] ?? 0) + 1;
  // Auch die Satzbilanz der Woche zählt (rund 4 Sätze ≈ eine Einheit für die Gruppe).
  for (const g of ["druck", "zug", "beine"]) hits[g] = Math.max(hits[g] ?? 0, Math.floor((ctx.balance?.[g] ?? 0) / 4));
  // Nur Tagestypen, für die es mit Ausrüstung und Beschwerden auch Hauptübungen gibt
  // (sonst bestünde z. B. ein »Drücken«-Tag nur aus einem Seitstütz).
  const doable = (t: DayType) => {
    if (t === "mobilitaet") return ctx.catalog.exercises.some((e) => MOBILITY.includes(e.pattern) && usable(ctx, e));
    const pats: string[] = DAY_TYPES[t].patterns.filter((x) => x !== "core");
    return !pats.length || ctx.catalog.exercises.some((e) => pats.includes(e.pattern) && e.use.includes("main") && usable(ctx, e));
  };
  const scores: [DayType, number][] = (Object.keys(DAY_TYPES) as DayType[])
    .filter((t) => t !== "mobilitaet" || trainedLast3 >= 3 || energy <= 2 || streak >= 4)
    .filter(doable)
    .map((t) => {
      // Rumpf ist an fast jedem Tag dabei — seine Ermüdung unterscheidet die Tagestypen nicht und zählt
      // nur beim reinen Rumpftag (sonst verliert z. B. »Beine & Rumpf« gegen Ausdauer).
      const groups = DAY_TYPES[t].groups.filter((g) => g !== "core" || DAY_TYPES[t].groups.length === 1);
      let s = groups.length
        ? -groups.reduce((a, g) => a + (fatigue[g] ?? 0), 0) / groups.length
        : -0.4;
      if (t === last) s -= 1;
      // Wenige Tage: Ganzkörper (die Häufigkeit oben zieht ohnehin in diese Richtung).
      // Bis 4 Trainingstage: Krafttage als Ganzkörper (jede Gruppe 2× pro Woche, wie Freeletics Mo/Mi/Fr).
      if (t === "ganzkoerper" && p.trainingDays.length <= 4) s += 0.8;
      if (t === "ausdauer") s += p.goal === "abnehmen" || (p.focus.includes("ausdauer") && p.goal !== "muskeln") ? 0.5 : -0.3;
      // Muskelaufbau: Ausdauer höchstens einmal pro Woche — Kraftsätze haben Vorrang.
      if (t === "ausdauer" && (p.goal === "muskeln" || p.goal === "zunehmen") && (hits.ausdauer ?? 0) >= 1) s -= 2;
      if (
        (t === "druck" || t === "zug" || t === "beine") &&
        (p.goal === "muskeln" || p.focus.includes("kraft"))
      )
        s += 0.35;
      if (t === "core") s -= 0.35;
      if (t === "mobilitaet") s += p.focus.includes("beweglichkeit") ? 0.2 : -0.6;
      // Erholung nach mehreren Trainingstagen am Stück (aktive Regeneration wie bei Freeletics).
      // (4 Tage am Stück, dann Erholung: so bleibt für Drücken/Ziehen/Beine je 2× pro Woche Platz.)
      if (t === "mobilitaet" && streak >= 4) s += streak >= 6 ? 4 : 2.2;
      // Abnehmen: Ausdauer zweimal pro Woche, mit Ausdauer-Schwerpunkt einmal.
      const cardioWant = p.goal === "abnehmen" ? 2 : p.focus.includes("ausdauer") && p.goal !== "muskeln" ? 1 : 0;
      if (t === "ausdauer" && cardioWant) s += 0.6 * Math.max(0, cardioWant - (hits.ausdauer ?? 0));
      // Frequenz: jede Hauptgruppe (Drücken, Ziehen, Beine) mindestens 2× pro Woche.
      const tr = TRAINS[t];
      // Summe statt Schnitt: ein Ganzkörpertag holt drei fehlende Gruppen auf einmal nach.
      // Beine wiegen mehr: sie fallen bei Push/Pull-Rhythmen sonst als erstes hinten runter.
      if (tr.length) s += tr.reduce((a, g) => a + (g === "beine" ? 0.45 : 0.3) * Math.max(0, 2 - (hits[g] ?? 0)), 0);
      if (energy === 2 && (t === "ausdauer" || t === "beine")) s -= 0.4;
      // Programm-Fokus (z. B. »Erster Klimmzug« → Ziehen)
      // (doppelt gewichtet: ein gewähltes Programm ist ein klarer Wunsch und schlägt die Grundregeln)
      s += 2.5 * (ctx.program?.focus?.[t] ?? 0);
      // Muskelbalance der Woche: vernachlässigte Gruppen nachziehen
      const bal = ctx.balance;
      if (bal) {
        const b = { druck: bal.druck ?? 0, zug: bal.zug ?? 0, beine: bal.beine ?? 0 };
        if (t === "zug" && b.druck >= 4 && b.zug < 0.6 * b.druck) s += 0.5;
        if (t === "druck" && b.zug >= 4 && b.druck < 0.6 * b.zug) s += 0.4;
        const upper = (b.druck + b.zug) / 2;
        if (t === "beine" && upper >= 4 && b.beine < 0.5 * upper) s += 0.45;
      }
      const wish = ctx.checkin?.wish ?? "";
      if (wish && (DAY_TYPES[t].title.toLowerCase().includes(wish.toLowerCase()) || t === wish))
        s += 3;
      return [t, s];
    });
  scores.sort((a, b) => b[1] - a[1]);
  return scores[0]?.[0] ?? "ganzkoerper";
}

function niceAmount(v: number, unit: "reps" | "sec"): number {
  if (unit === "sec") return Math.max(10, Math.round(v / 5) * 5);
  return Math.max(3, Math.round(v));
}

/** Menge für eine Übung: Grundmenge × Level × Dosis aus dem Feedback × Tagesform. */
export function doseFor(
  ctx: PlanContext,
  e: Exercise,
  intensity: SessionPlan["intensity"],
  block: PlanBlock["kind"],
): number {
  const st = ctx.state.get(e.id);
  // Startmenge nach Level nur beim ersten Mal; danach trägt die gelernte Dosis. (Sonst verdoppelte ein
  // Level-Aufstieg die Wiederholungen bekannter Übungen → Scheitern → Level wieder runter.)
  // Nie gemachte Übung: 15 % vorsichtiger einsteigen (das Feedback holt den Rest schnell auf).
  const seed = st?.seed ?? seedFor(ctx.profile.level, e) * (st ? 1 : 0.85);
  const inten = intensity === "leicht" ? 0.8 : intensity === "hart" ? 1.15 : 1;
  const blockScale = block === "warmup" || block === "cooldown" ? 0.75 : 1;
  const programScale = block === "main" || block === "finisher" ? (ctx.program?.volume ?? 1) : 1;
  const raw =
    e.base *
    (block === "main" || block === "finisher" ? seed : 1) *
    (st?.dose ?? 1) *
    inten *
    blockScale *
    programScale;
  // Obergrenze: darüber steht ohnehin der Aufstieg zur schwereren Variante an.
  return Math.min(niceAmount(raw, e.unit), e.unit === "sec" ? 90 : e.perSide ? 20 : 30);
}

/** Startfaktor einer Übung für ein Level (schwere Übungen skalieren schwächer). */
export function seedFor(level: number, e: Exercise): number {
  const levelScale = [0.6, 1, 1.3, 1.6, 1.9][level - 1] ?? 1;
  const relative = Math.max(0.55, 1 - (e.level - level * 2) * 0.12);
  return Math.round(levelScale * relative * 1000) / 1000;
}

/** Bewegungsmuster für Erholungstage. */
export const MOBILITY = ["mobility", "stretch"];
/** Welche Hauptgruppen ein Tagestyp wirklich trainiert (für die Frequenz). */
const TRAINS: Record<DayType, string[]> = {
  ganzkoerper: ["druck", "zug", "beine"], druck: ["druck"], zug: ["zug"], beine: ["beine"], core: [], ausdauer: [], mobilitaet: [],
};
/** Für die Zählung »wie oft diese Woche«: Ausdauertage zählen als Ausdauer. */
const HITS: Record<DayType, string[]> = {
  ...TRAINS, ausdauer: ["ausdauer"],
};

/** Zeitrahmen: kurze Einheiten bekommen kurzes Auf- und Abwärmen — der Rest ist Arbeit. */
export function frameFor(minutes: number) {
  return minutes <= 15 ? { warm: 3, cool: 1, coolSec: 30 } : minutes <= 25 ? { warm: 4, cool: 3, coolSec: 35 } : { warm: 5, cool: 4, coolSec: 40 };
}

function pickFrom(
  pool: Exercise[],
  n: number,
  rand: () => number,
  target: number,
  avoid = new Set<string>(),
  noise = 1.8,
): Exercise[] {
  const scored = pool
    .filter((e) => !avoid.has(e.id))
    .map((e) => ({ e, s: -Math.abs(e.level - target) + rand() * noise }))
    .sort((a, b) => b.s - a.s);
  const out: Exercise[] = [];
  for (const { e } of scored) {
    if (out.length >= n) break;
    out.push(e);
  }
  return out;
}

const WARMUP_FOR: Record<DayType, string[]> = {
  ganzkoerper: ["schulter", "huefte", "ganz"],
  druck: ["schulter", "handgelenk", "ganz"],
  zug: ["schulter", "ruecken", "ganz"],
  beine: ["huefte", "sprunggelenk", "ganz"],
  core: ["ruecken", "huefte", "ganz"],
  ausdauer: ["ganz", "sprunggelenk", "huefte"],
  mobilitaet: ["ganz", "ruecken", "huefte"],
};

/** Sekunden, die ein Block dauert. */
export function blockSeconds(b: PlanBlock, catalog: Catalog): number {
  let one = 0;
  for (const it of b.items) {
    const e = catalog.byId.get(it.ex);
    const work = it.unit === "sec" ? it.amount : it.amount * (e?.secPerRep ?? 3);
    one += work * (it.perSide ? 2 : 1) + it.rest;
  }
  const rounds = b.format === "flow" ? 1 : b.rounds;
  return one * rounds + b.restBetweenRounds * Math.max(0, rounds - 1);
}

export function planSeconds(p: Pick<SessionPlan, "blocks">, catalog: Catalog): number {
  return p.blocks.reduce((a, b) => a + blockSeconds(b, catalog), 0);
}

export function estimateKcal(
  p: Pick<SessionPlan, "blocks">,
  catalog: Catalog,
  weightKg: number,
): number {
  let kcal = 0;
  for (const b of p.blocks) {
    const met =
      b.kind === "warmup"
        ? 3.5
        : b.kind === "cooldown"
          ? 2.3
          : b.format === "intervals"
            ? 8
            : b.format === "rounds"
              ? 7
              : 5.5;
    kcal += (met * 3.5 * weightKg * (blockSeconds(b, catalog) / 60)) / 200;
  }
  return Math.round(kcal);
}

export function buildRulesPlan(ctx: PlanContext): SessionPlan {
  const rand = seeded(
    `${ctx.day}:${ctx.forceType ?? ""}:${ctx.checkin?.wish ?? ""}:${ctx.recent.filter((r) => r.daysAgo === 0).length}`,
  );
  const p = ctx.profile;
  const dayType = chooseDayType(ctx);
  const energy = effectiveEnergy(ctx);
  // Zweites Training am selben Tag: eine Stufe ruhiger.
  const second = ctx.recent.some((r) => r.daysAgo === 0);
  let intensity: SessionPlan["intensity"] =
    dayType === "mobilitaet" || energy <= 2
      ? "leicht"
      : second
        ? energy >= 4
          ? "mittel"
          : "leicht"
        : energy >= 4
          ? "hart"
          : "mittel";
  // Reihenfolge zählt: erst die Programmphase, dann »zweimal zu leicht → fordernder«, zuletzt die
  // Schutzregeln (Wiedereinstieg nach Pause, Entlastungswoche) — die gewinnen immer.
  if (ctx.program && energy >= 3 && !second && dayType !== "mobilitaet") intensity = ctx.program.intensity;
  const lastTwo = ctx.recent.filter((r) => r.daysAgo >= 1 && r.dayType !== "mobilitaet").slice(0, 2);
  if (intensity === "mittel" && energy >= 3 && lastTwo.length === 2 && lastTwo.every((r) => (r.overall ?? 3) <= 2)) intensity = "hart";
  if ((ctx.daysOff ?? 0) >= 8 && dayType !== "mobilitaet") intensity = "leicht";
  if (ctx.program?.deload && dayType !== "mobilitaet") intensity = "leicht";
  const minutes = ctx.checkin?.minutes ?? p.sessionMinutes;
  const all = ctx.catalog.exercises.filter((e) => usable(ctx, e));
  const used = new Set<string>();
  const frame = frameFor(minutes);

  // Aufwärmen: 3–5 Übungen à 30 s (je nach Länge der Einheit), passend zum Tag.
  const warmPool = all.filter((e) => e.use.includes("warmup"));
  const warm: Exercise[] = [];
  for (const focus of WARMUP_FOR[dayType]) {
    const cand = warmPool.filter(
      (e) =>
        !used.has(e.id) &&
        (e.stress.includes(focus) ||
          e.muscles.join(" ").toLowerCase().includes(focus) ||
          focus === "ganz"),
    );
    for (const e of pickFrom(cand, focus === "ganz" ? 2 : 1, rand, 2)) {
      warm.push(e);
      used.add(e.id);
    }
  }
  for (const e of pickFrom(warmPool, Math.max(0, frame.warm - warm.length), rand, 2, used)) {
    warm.push(e);
    used.add(e.id);
  }
  warm.splice(frame.warm);

  // Hauptteil
  const levelTarget = targetDifficulty(p.level, intensity);
  // Ziel-Schwierigkeit je Muster am echten Stand: was schon sicher sitzt (Dosis ≥ 1,3, mind. 2×),
  // hebt das Ziel eine Stufe darüber — das Profil-Level hinkt Wochen hinterher.
  const patTarget = (pat: string) => {
    const mastered = Math.max(
      ctx.mastered?.[pat] ?? 0,
      ctx.catalog.exercises
        .filter((e) => e.pattern === pat && (ctx.state.get(e.id)?.times ?? 0) >= 2 && (ctx.state.get(e.id)?.dose ?? 0) >= 1.3)
        .reduce((m, e) => Math.max(m, e.level), 0),
    );
    const t = Math.max(levelTarget, mastered ? mastered + 1 : 0);
    return intensity === "leicht" ? t - 1 : t;
  };
  const target = levelTarget;
  const mobil = dayType === "mobilitaet";
  // Erholungstag: Beweglichkeit und Dehnen als Hauptteil (diese Übungen sind sonst nur fürs Auf-/Abwärmen).
  const mainPool = mobil ? all.filter((e) => MOBILITY.includes(e.pattern)) : all.filter((e) => e.use.includes("main"));
  const main: Exercise[] = [];
  const seenPat = new Set<string>();
  for (const pat of DAY_TYPES[dayType].patterns) {
    let cand = mainPool.filter((e) => e.pattern === pat && !ctx.demoted?.has(e.id));
    const tp = mobil ? target : patTarget(pat);
    // Nur Übungen nah am eigenen Niveau — sonst entstehen »Ramsch-Sätze« (Wand-Liegestütz für jemanden,
    // der 25 Liegestütze schafft). Gleiches Muster ein zweites Mal nur, wenn eine solche frei ist.
    if (!mobil && dayType !== "ausdauer") {
      // Nach oben höchstens eine Stufe über dem Ziel (sonst Varianten, von denen keine 3 Wiederholungen gehen),
      // nach unten bis 2,5 Stufen.
      const near = cand.filter((e) => e.level - tp <= 1 && tp - e.level <= 2.5);
      cand = near.length || seenPat.has(pat) ? near : cand;
      // Keine Variante, von der nur die Mindestmenge (3 Wiederholungen) übrig bliebe — lieber eine passendere.
      const doable = cand.filter((e) => e.unit === "sec" || doseFor(ctx, e, intensity, "main") > 3);
      if (doable.length) cand = doable;
    }
    const [e] = pickFrom(cand, 1, rand, tp, used, mobil ? 1.8 : 0.8);
    if (e) {
      main.push(e);
      used.add(e.id);
      seenPat.add(pat);
    }
  }
  const strengthy = p.goal === "muskeln" || p.focus.includes("kraft");
  const format: PlanBlock["format"] =
    dayType === "mobilitaet"
      ? "flow"
      : dayType === "ausdauer"
        ? "intervals"
        : // Lange Krafteinheiten: klassische Sätze. Kurze: Zirkel (Supersatz) — die Muskeln erholen sich,
          // während die anderen arbeiten, so passen mehrere Sätze je Übung in 15 Minuten.
          strengthy && intensity !== "leicht" && minutes > 30
          ? "sets"
          : "rounds";
  const mainBlock: PlanBlock = {
    kind: "main",
    title:
      format === "sets"
        ? "Kraftteil"
        : format === "intervals"
          ? "Intervalle · 40 s Arbeit, 20 s Pause"
          : format === "flow"
            ? "Beweglichkeit"
            : "Zirkel",
    format,
    rounds: format === "flow" ? 1 : 3,
    restBetweenRounds: format === "rounds" ? 60 : format === "intervals" ? 45 : 0,
    items: main.map((e) => {
      if (format === "intervals")
        return { ex: e.id, amount: 40, unit: "sec" as const, perSide: false, rest: 20 };
      const amount = dayType === "mobilitaet" ? 45 : doseFor(ctx, e, intensity, "main");
      return {
        ex: e.id,
        amount,
        unit: dayType === "mobilitaet" ? ("sec" as const) : e.unit,
        ...(e.perSide ? { perSide: true } : {}),
        rest: format === "sets" ? (e.level >= 6 ? 90 : 60) : format === "flow" ? 10 : 20,
      };
    }),
  };

  // Abkühlen: 4 Dehnungen à 40 s
  // Kurze Einheiten: lieber beidseitige Dehnungen (einseitige dauern doppelt).
  const coolAll = all.filter((e) => e.use.includes("cooldown"));
  const coolPool = minutes <= 20 && coolAll.filter((e) => !e.perSide).length >= frame.cool ? coolAll.filter((e) => !e.perSide) : coolAll;
  const cool = pickFrom(coolPool, frame.cool, rand, 2, used);

  const plan: SessionPlan = {
    title: DAY_TYPES[dayType].title,
    dayType,
    intensity,
    minutes,
    kcal: 0,
    coach: { headline: "", message: "", why: [] },
    blocks: [
      {
        kind: "warmup",
        title: "Aufwärmen",
        format: "flow",
        rounds: 1,
        restBetweenRounds: 0,
        items: warm.map((e) => ({
          ex: e.id,
          amount: e.unit === "sec" ? 30 : Math.max(6, Math.round(e.base * 0.7)),
          unit: e.unit,
          ...(e.perSide ? { perSide: true } : {}),
          rest: 5,
        })),
      },
      mainBlock,
      {
        kind: "cooldown",
        title: "Abkühlen & Dehnen",
        format: "flow",
        rounds: 1,
        restBetweenRounds: 0,
        items: cool.map((e) => ({
          ex: e.id,
          amount: frame.coolSec,
          unit: "sec" as const,
          ...(e.perSide ? { perSide: true } : {}),
          rest: 5,
        })),
      },
    ],
  };
  fitToTime(plan, ctx.catalog, minutes, (e) => usable(ctx, e), ctx.program?.deload ? ctx.program.volume : 1);
  plan.kcal = estimateKcal(plan, ctx.catalog, p.weightKg);
  plan.minutes = Math.round(planSeconds(plan, ctx.catalog) / 60);
  plan.coach = rulesCoachText(plan, ctx);
  return plan;
}

/** Runden/Sätze des Hauptteils so anpassen, dass die Einheit in die gewünschte Zeit passt. */
export function fitToTime(
  plan: SessionPlan,
  catalog: Catalog,
  minutes: number,
  ok: (e: Exercise) => boolean = () => true,
  /** Entlastungswoche: weniger Zeit für den Hauptteil, sonst füllen zusätzliche Runden das gesenkte Volumen wieder auf. */
  mainScale = 1,
) {
  const main = plan.blocks.find((b) => b.kind === "main");
  if (!main) return;
  if (main.format === "flow") {
    // Erholungstag: so viele ruhige Durchgänge, wie in die Zeit passen (1–3).
    const rest = plan.blocks.filter((b) => b !== main).reduce((a, b) => a + blockSeconds(b, catalog), 0);
    main.rounds = 1;
    const one = blockSeconds(main, catalog);
    main.rounds = Math.max(1, Math.min(3, Math.floor((minutes * 60 - rest) / Math.max(1, one))));
    return;
  }
  const others = plan.blocks
    .filter((b) => b !== main)
    .reduce((a, b) => a + blockSeconds(b, catalog), 0);
  const budget = (minutes * 60 - others) * mainScale;
  const fit = () => {
    let best = 1;
    for (let r = 1; r <= (minutes >= 60 ? 8 : 6); r++) {
      main.rounds = r;
      if (blockSeconds(main, catalog) <= budget * 1.08) best = r;
    }
    return best;
  };
  // Beste Aufteilung der Zeit: mindestens 2 Runden je Übung (ein Satz bringt kaum Reiz), sonst so viel
  // Arbeit wie in die Zeit passt — lieber 3 Runden à 4 Übungen als 2 à 5, wenn das die Zeit besser füllt.
  const all = [...main.items];
  let pick = { k: all.length, r: 0, sec: -1 };
  for (let k = all.length; k >= Math.min(2, all.length); k--) {
    main.items = all.slice(0, k);
    const r = fit();
    main.rounds = r;
    const sec = blockSeconds(main, catalog);
    // Zirkel: lieber eine Übung mehr als die 5. und 6. Runde (Abwechslung), solange ≥ 2 Runden passen.
    const okR = (r >= 2 || k <= 2) && (main.format !== "rounds" || r <= 4 || k === all.length);
    if (okR && sec <= budget * 1.08 && sec > pick.sec + 20) pick = { k, r, sec };
  }
  if (pick.sec < 0) pick = { k: Math.min(2, all.length), r: 1, sec: 0 };
  main.items = all.slice(0, pick.k);
  let best = pick.r || fit();
  main.rounds = best;
  // Viel Restzeit übrig? Ein Finisher (kurzes Intervall) füllt sie sinnvoll.
  const left = budget - blockSeconds(main, catalog);
  if (
    left > 240 &&
    mainScale >= 1 &&
    plan.dayType !== "mobilitaet" &&
    !plan.blocks.some((b) => b.kind === "finisher")
  ) {
    const core = main.items.find((i) => catalog.byId.get(i.ex)?.pattern === "core");
    const cardio = catalog.exercises.find(
      (e) =>
        e.pattern === "cardio" && !main.items.some((i) => i.ex === e.id) && e.use.includes("main") && ok(e),
    );
    const items = [cardio, core ? catalog.byId.get(core.ex) : undefined].filter(
      (e): e is Exercise => !!e,
    );
    if (items.length) {
      plan.blocks.splice(plan.blocks.indexOf(main) + 1, 0, {
        kind: "finisher",
        title: "Finisher",
        format: "intervals",
        rounds: Math.min(4, Math.max(2, Math.floor(left / 120))),
        restBetweenRounds: 30,
        items: items.map((e) => ({ ex: e.id, amount: 30, unit: "sec" as const, rest: 15 })),
      });
    }
  }
}

function rulesCoachText(plan: SessionPlan, ctx: PlanContext): SessionPlan["coach"] {
  const why: string[] = [];
  const done = ctx.recent.find((r) => r.daysAgo === 0);
  if (done)
    why.push(
      `Heute schon erledigt: ${DAY_TYPES[done.dayType].title} — jetzt andere Schwerpunkte und etwas leichter.`,
    );
  const recent = ctx.recent.find((r) => r.daysAgo === 1);
  if (recent) why.push(`Gestern: ${DAY_TYPES[recent.dayType].title} — heute andere Muskeln.`);
  if (ctx.painRegions.length)
    why.push(
      `Wegen Beschwerden (${ctx.painRegions.join(", ")}) ohne Übungen, die diese Stellen belasten.`,
    );
  if ((ctx.daysOff ?? 0) >= 8) why.push(`Wiedereinstieg nach ${ctx.daysOff} Tagen Pause — heute bewusst leichter, ab der nächsten Einheit wieder normal.`);
  if (ctx.profile.noJumps) why.push("Ohne Sprünge, wie eingestellt.");
  if (ctx.checkin?.energy && ctx.checkin.energy <= 2)
    why.push("Wenig Energie heute — leichter dosiert.");
  return {
    headline: plan.title,
    message:
      plan.intensity === "leicht"
        ? "Heute ruhig und sauber. Qualität vor Tempo."
        : plan.intensity === "hart"
          ? "Du bist fit heute — wir gehen an die Grenze, aber mit sauberer Technik."
          : "Solider Trainingstag. Saubere Wiederholungen, kurze Pausen.",
    why,
  };
}
