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
      patterns: ["push", "squat", "pull", "core", "lunge", "hinge"],
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
  /** Belastete Gruppen der letzten Tage: [Tage her, Gruppen] */
  recent: { daysAgo: number; groups: string[]; dayType: DayType; hard: boolean }[];
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
  const scores: [DayType, number][] = (Object.keys(DAY_TYPES) as DayType[])
    .filter((t) => t !== "mobilitaet" || trainedLast3 >= 3 || energy <= 2)
    .filter((t) => t !== "zug" || canPull)
    .map((t) => {
      const groups = DAY_TYPES[t].groups;
      let s = groups.length
        ? -groups.reduce((a, g) => a + (fatigue[g] ?? 0), 0) / groups.length
        : -0.4;
      if (t === last) s -= 1;
      if (t === "ganzkoerper") s += p.trainingDays.length <= 3 ? 0.9 : 0.25;
      if (t === "ausdauer") s += p.focus.includes("ausdauer") || p.goal === "abnehmen" ? 0.5 : -0.3;
      if (
        (t === "druck" || t === "zug" || t === "beine") &&
        (p.goal === "muskeln" || p.focus.includes("kraft"))
      )
        s += 0.35;
      if (t === "core") s -= 0.35;
      if (t === "mobilitaet") s += p.focus.includes("beweglichkeit") ? 0.2 : -0.6;
      if (energy === 2 && (t === "ausdauer" || t === "beine")) s -= 0.4;
      // Programm-Fokus (z. B. »Erster Klimmzug« → Ziehen)
      s += ctx.program?.focus?.[t] ?? 0;
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
  const levelScale = [0.6, 1, 1.3, 1.6, 1.9][ctx.profile.level - 1] ?? 1;
  // Schwere Übungen skalieren schwächer mit dem Level (ein Level-5-Athlet macht nicht 3× so viele Muscle-ups).
  const relative = Math.max(0.55, 1 - (e.level - ctx.profile.level * 2) * 0.12);
  const inten = intensity === "leicht" ? 0.8 : intensity === "hart" ? 1.15 : 1;
  const blockScale = block === "warmup" || block === "cooldown" ? 0.75 : 1;
  const programScale = block === "main" || block === "finisher" ? (ctx.program?.volume ?? 1) : 1;
  const raw =
    e.base *
    (block === "main" || block === "finisher" ? levelScale * relative : 1) *
    (st?.dose ?? 1) *
    inten *
    blockScale *
    programScale;
  return niceAmount(raw, e.unit);
}

function pickFrom(
  pool: Exercise[],
  n: number,
  rand: () => number,
  target: number,
  avoid = new Set<string>(),
): Exercise[] {
  const scored = pool
    .filter((e) => !avoid.has(e.id))
    .map((e) => ({ e, s: -Math.abs(e.level - target) + rand() * 1.8 }))
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
  // Programm: Entlastungswoche immer leicht; sonst die Phasen-Intensität, wenn die Tagesform passt.
  if (ctx.program?.deload && dayType !== "mobilitaet") intensity = "leicht";
  else if (ctx.program && energy >= 3 && !second && dayType !== "mobilitaet") intensity = ctx.program.intensity;
  const minutes = ctx.checkin?.minutes ?? p.sessionMinutes;
  const all = ctx.catalog.exercises.filter((e) => usable(ctx, e));
  const used = new Set<string>();

  // Aufwärmen: 5 Übungen à 30 s, passend zum Tag.
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
  for (const e of pickFrom(warmPool, Math.max(0, 5 - warm.length), rand, 2, used)) {
    warm.push(e);
    used.add(e.id);
  }

  // Hauptteil
  const target = targetDifficulty(p.level, intensity);
  const mainPool = all.filter((e) => e.use.includes("main"));
  const main: Exercise[] = [];
  for (const pat of DAY_TYPES[dayType].patterns) {
    const cand = mainPool.filter((e) => e.pattern === pat);
    const [e] = pickFrom(cand, 1, rand, target, used);
    if (e) {
      main.push(e);
      used.add(e.id);
    }
  }
  const strengthy = p.goal === "muskeln" || p.focus.includes("kraft");
  const format: PlanBlock["format"] =
    dayType === "mobilitaet"
      ? "flow"
      : dayType === "ausdauer"
        ? "intervals"
        : strengthy && intensity !== "leicht"
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
    restBetweenRounds: format === "rounds" ? 90 : format === "intervals" ? 60 : 0,
    items: main.map((e) => {
      if (format === "intervals")
        return { ex: e.id, amount: 40, unit: "sec" as const, perSide: false, rest: 20 };
      const amount = dayType === "mobilitaet" ? 45 : doseFor(ctx, e, intensity, "main");
      return {
        ex: e.id,
        amount,
        unit: dayType === "mobilitaet" ? ("sec" as const) : e.unit,
        ...(e.perSide ? { perSide: true } : {}),
        rest: format === "sets" ? (e.level >= 6 ? 120 : 75) : format === "flow" ? 10 : 15,
      };
    }),
  };

  // Abkühlen: 4 Dehnungen à 40 s
  const coolPool = all.filter((e) => e.use.includes("cooldown"));
  const cool = pickFrom(coolPool, 4, rand, 2, used);

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
          amount: 40,
          unit: "sec" as const,
          ...(e.perSide ? { perSide: true } : {}),
          rest: 5,
        })),
      },
    ],
  };
  fitToTime(plan, ctx.catalog, minutes);
  plan.kcal = estimateKcal(plan, ctx.catalog, p.weightKg);
  plan.minutes = Math.round(planSeconds(plan, ctx.catalog) / 60);
  plan.coach = rulesCoachText(plan, ctx);
  return plan;
}

/** Runden/Sätze des Hauptteils so anpassen, dass die Einheit in die gewünschte Zeit passt. */
export function fitToTime(plan: SessionPlan, catalog: Catalog, minutes: number) {
  const main = plan.blocks.find((b) => b.kind === "main");
  if (!main || main.format === "flow") return;
  const others = plan.blocks
    .filter((b) => b !== main)
    .reduce((a, b) => a + blockSeconds(b, catalog), 0);
  const budget = minutes * 60 - others;
  let best = 1;
  for (let r = 1; r <= 6; r++) {
    main.rounds = r;
    if (blockSeconds(main, catalog) <= budget * 1.08) best = r;
  }
  main.rounds = best;
  // Viel Restzeit übrig? Ein Finisher (kurzes Intervall) füllt sie sinnvoll.
  const left = budget - blockSeconds(main, catalog);
  if (
    left > 240 &&
    plan.dayType !== "mobilitaet" &&
    !plan.blocks.some((b) => b.kind === "finisher")
  ) {
    const core = main.items.find((i) => catalog.byId.get(i.ex)?.pattern === "core");
    const cardio = catalog.exercises.find(
      (e) =>
        e.pattern === "cardio" && !main.items.some((i) => i.ex === e.id) && e.use.includes("main"),
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
