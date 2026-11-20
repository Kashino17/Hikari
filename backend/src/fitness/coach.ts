/**
 * KI-Coach: macht aus dem Regel-Grundgerüst den persönlichen Trainingstag und verarbeitet das
 * Feedback nach jeder Einheit.
 *
 * Grundsatz (wie überall in Hikari): Das stärkste freie Modell entscheidet, fester Code sichert ab.
 * Das Modell darf nur Übungen aus der erlaubten Liste nehmen (Ausrüstung, Beschwerden, Abneigungen
 * sind dort schon herausgefiltert), Mengen werden auf ein sinnvolles Band um die Regel-Dosis
 * geklemmt, und die Dauer muss zur gewünschten Zeit passen. Fällt die KI aus, gilt der Regelplan.
 */
import { makeResolver } from "./progression.js";
import { balance, getPrograms, mondayOf, phaseFor } from "./program.js";
import type { FitnessAi } from "./ai.js";
import type { Catalog, Exercise } from "./catalog.js";
import { type FitDb, addDays, localDay } from "./db.js";
import {
  DAY_TYPES,
  type DayType,
  type ExerciseState,
  type PlanBlock,
  type PlanContext,
  type SessionPlan,
  buildRulesPlan,
  doseFor,
  estimateKcal,
  planSeconds,
  usable,
} from "./planner.js";
import { type Profile, getProfile } from "./profile.js";

export interface SessionRow {
  id: string;
  day: string;
  kind: string;
  status: string;
  plan: SessionPlan;
  result: SessionResult | null;
  feedback: SessionFeedback | null;
  source: string | null;
  created_at: number;
  done_at: number | null;
}

export interface SessionResult {
  seconds: number;
  /** je Übung (Blockindex:Itemindex) die wirklich geschafften Mengen je Runde */
  done: Record<string, number[]>;
  skipped: string[];
  swapped?: Record<string, string>;
}

export interface ExerciseFeedback {
  ex: string;
  /** -1 zu leicht, 0 passt, 1 zu schwer */
  difficulty?: number;
  /** 1 unsicher, 2 okay, 3 sauber */
  technique?: number;
  pain?: boolean;
  dislike?: boolean;
}

export interface SessionFeedback {
  /** 1 viel zu leicht … 5 viel zu schwer */
  overall: number;
  /** 1–5 */
  energy?: number;
  enjoyment?: number;
  pain: { region: string; level: number }[];
  exercises: ExerciseFeedback[];
  note?: string;
}

function rowToSession(r: Record<string, unknown>): SessionRow {
  return {
    id: String(r.id),
    day: String(r.day),
    kind: String(r.kind),
    status: String(r.status),
    plan: JSON.parse(String(r.plan)),
    result: r.result ? JSON.parse(String(r.result)) : null,
    feedback: r.feedback ? JSON.parse(String(r.feedback)) : null,
    source: (r.source as string) ?? null,
    created_at: Number(r.created_at),
    done_at: r.done_at ? Number(r.done_at) : null,
  };
}

export function getSession(db: FitDb, id: string): SessionRow | null {
  const r = db.prepare("SELECT * FROM sessions WHERE id = ?").get(id) as
    | Record<string, unknown>
    | undefined;
  return r ? rowToSession(r) : null;
}

export function sessionsBetween(db: FitDb, from: string, to: string): SessionRow[] {
  return (
    db
      .prepare("SELECT * FROM sessions WHERE day >= ? AND day <= ? ORDER BY day, created_at")
      .all(from, to) as Record<string, unknown>[]
  ).map(rowToSession);
}

export function exerciseStates(db: FitDb): Map<string, ExerciseState> {
  const rows = db.prepare("SELECT * FROM exercise_state").all() as Record<string, unknown>[];
  return new Map(
    rows.map((r) => [
      String(r.exercise_id),
      {
        dose: Number(r.dose) || 1,
        avoidUntil: (r.avoid_until as string) ?? null,
        disliked: !!r.disliked,
        technique: (r.technique as number) ?? null,
        times: Number(r.times) || 0,
      },
    ]),
  );
}

export function painRegions(db: FitDb, day: string): string[] {
  const rows = db
    .prepare(
      "SELECT region, MAX(level) AS lvl, MAX(day) AS last FROM pain_log WHERE day >= ? GROUP BY region",
    )
    .all(addDays(day, -7)) as {
    region: string;
    lvl: number;
    last: string;
  }[];
  // Stärkere Beschwerden länger schonen: Stufe ≥ 6 eine Woche, sonst 4 Tage.
  return rows.filter((r) => r.lvl >= 6 || r.last >= addDays(day, -4)).map((r) => r.region);
}

export function buildContext(
  db: FitDb,
  catalog: Catalog,
  day: string,
  checkin?: PlanContext["checkin"],
  forceType?: DayType,
): PlanContext {
  const profile = getProfile(db);
  // Heute schon erledigte Einheiten zählen mit (daysAgo 0) — für ein zweites Training am selben Tag.
  const recent = sessionsBetween(db, addDays(day, -6), day)
    .filter((s) => s.status === "done")
    .map((s) => {
      const daysAgo = Math.round(
        (Date.parse(`${day}T12:00:00`) - Date.parse(`${s.day}T12:00:00`)) / 86_400_000,
      );
      return {
        daysAgo,
        groups: DAY_TYPES[s.plan.dayType]?.groups ?? [],
        dayType: s.plan.dayType,
        hard: (s.feedback?.overall ?? 3) >= 4,
      };
    })
    .sort((a, b) => a.daysAgo - b.daysAgo);
  const phase = phaseFor(db, getPrograms(), day);
  return {
    day,
    profile,
    catalog,
    state: exerciseStates(db),
    recent,
    painRegions: painRegions(db, day),
    checkin,
    forceType,
    program: phase && !phase.done ? phase : null,
    balance: balance(db, catalog, mondayOf(day), day),
  };
}

// ── KI-Plan ─────────────────────────────────────────────────────────────────

const COACH_SYSTEM = `Du bist ein erstklassiger Personal Trainer für Körpergewichtstraining und Calisthenics (Niveau: Freeletics-Coach, aber persönlicher).
Du bekommst: Profil, Tagesform, die letzten Einheiten mit Feedback, Beschwerden, den Regel-Vorschlag für heute und die Liste ERLAUBTER Übungen (id, Name, Muster, Schwierigkeit 1–10, Einheit, Regel-Menge).
Baue daraus den besten Trainingstag. Regeln:
- Nimm NUR ids aus "erlaubt". Erfinde keine Übungen.
- Blöcke: warmup (format flow), main (format sets | rounds | intervals | flow), optional finisher, cooldown (flow). Aufwärmen ~4–6 min, Abkühlen ~3–5 min.
- Die Gesamtdauer soll ungefähr "minuten" treffen.
- Mengen orientieren sich an "regel" (das ist die vom Feedback gelernte Dosis). Weiche höchstens ±30 % ab, und nur mit Grund.
- Wo das Feedback "zu schwer" oder "Technik unsicher" sagt: leichtere Variante oder weniger. "zu leicht": mehr oder schwerere Variante.
- Abwechslung zu den letzten Tagen, Erholung der Muskelgruppen beachten.
- Läuft ein "programm": halte dich an Phase, Intensität und Fokus. In einer Entlastungswoche bewusst leichter und weniger Umfang (das ist Absicht, sag es dem Nutzer). Erwähne die Programmwoche in der Nachricht.
- "muskelbalance_diese_woche_saetze": vernachlässigte Gruppen (z. B. viel Drücken, wenig Ziehen) gezielt ausgleichen.
- Steht im Verlauf schon eine Einheit von HEUTE, ist das ein zweites Training am selben Tag: andere Muskelgruppen, eher kürzer und moderater, und sag das in der Nachricht.
- Sprich den Nutzer mit "du" an, Deutsch, motivierend aber sachlich, ohne Emojis.
- Erwähne keine internen Werte (Level-Zahlen, Schwierigkeitsstufen, "Regel", Dosis, ids) — sprich über Übungen, Muskeln, Ziele und sein Feedback.
Antworte NUR mit JSON:
{"title":"kurzer Name des Tages","dayType":"ganzkoerper|druck|zug|beine|core|ausdauer|mobilitaet","intensity":"leicht|mittel|hart",
 "coach":{"headline":"eine Zeile","message":"2–3 Sätze: worum es heute geht","why":["kurze Begründungen, bezogen auf Feedback/Verlauf"]},
 "blocks":[{"kind":"warmup|main|finisher|cooldown","title":"…","format":"flow|sets|rounds|intervals","rounds":1,"restBetweenRounds":0,
   "items":[{"ex":"id","amount":10,"rest":15,"note":"optionaler Technik-Fokus für heute (max 8 Wörter)"}]}]}`;

interface AiPlanRaw {
  title?: string;
  dayType?: string;
  intensity?: string;
  coach?: { headline?: string; message?: string; why?: string[] };
  blocks?: {
    kind?: string;
    title?: string;
    format?: string;
    rounds?: number;
    restBetweenRounds?: number;
    items?: { ex?: string; amount?: number; rest?: number; note?: string }[];
  }[];
}

/** Prüft und repariert den KI-Plan. Wirft, wenn er unbrauchbar ist. */
export function settleAiPlan(raw: AiPlanRaw, ctx: PlanContext, rules: SessionPlan): SessionPlan {
  const allowed = new Map(
    ctx.catalog.exercises.filter((e) => usable(ctx, e)).map((e) => [e.id, e]),
  );
  const kinds = new Set(["warmup", "main", "finisher", "cooldown"]);
  const formats = new Set(["flow", "sets", "rounds", "intervals"]);
  const blocks: PlanBlock[] = [];
  for (const b of raw.blocks ?? []) {
    if (!b?.kind || !kinds.has(b.kind)) continue;
    const kind = b.kind as PlanBlock["kind"];
    const format = (
      formats.has(b.format ?? "") ? b.format : kind === "main" ? "rounds" : "flow"
    ) as PlanBlock["format"];
    const items = (b.items ?? [])
      .map((it) => {
        const e = it?.ex ? allowed.get(it.ex) : undefined;
        if (!e) return null;
        const blockKind = kind === "finisher" ? "main" : kind;
        if (!e.use.includes(blockKind as "warmup" | "main" | "cooldown")) return null;
        const unit =
          format === "intervals"
            ? "sec"
            : kind === "cooldown" || kind === "warmup"
              ? e.unit
              : e.unit;
        const ruleAmount =
          format === "intervals"
            ? 40
            : kind === "main" || kind === "finisher"
              ? doseFor(ctx, e, rules.intensity, "main")
              : unit === "sec"
                ? 30
                : Math.max(6, Math.round(e.base * 0.7));
        const asked = Number(it.amount) || ruleAmount;
        const lo = ruleAmount * (format === "intervals" ? 0.5 : 0.7);
        const hi = ruleAmount * (format === "intervals" ? 1.5 : 1.3);
        const amount =
          unit === "sec"
            ? Math.max(10, Math.round(Math.min(hi, Math.max(lo, asked)) / 5) * 5)
            : Math.max(2, Math.round(Math.min(hi, Math.max(lo, asked))));
        return {
          ex: e.id,
          amount,
          unit: unit as "reps" | "sec",
          ...(e.perSide && format !== "intervals" ? { perSide: true } : {}),
          rest: Math.min(
            180,
            Math.max(0, Math.round(Number(it.rest) || (format === "sets" ? 75 : 10))),
          ),
          ...(it.note ? { note: String(it.note).slice(0, 80) } : {}),
        };
      })
      .filter((x): x is NonNullable<typeof x> => !!x);
    if (!items.length) continue;
    blocks.push({
      kind,
      title: String(
        b.title ||
          (kind === "warmup"
            ? "Aufwärmen"
            : kind === "cooldown"
              ? "Abkühlen & Dehnen"
              : "Hauptteil"),
      ).slice(0, 60),
      format: kind === "warmup" || kind === "cooldown" ? "flow" : format,
      rounds: format === "flow" ? 1 : Math.min(8, Math.max(1, Math.round(Number(b.rounds) || 3))),
      restBetweenRounds: Math.min(240, Math.max(0, Math.round(Number(b.restBetweenRounds) || 0))),
      items,
    });
  }
  if (!blocks.some((b) => b.kind === "main")) throw new Error("kein_hauptteil");
  if (!blocks.some((b) => b.kind === "warmup"))
    blocks.unshift(rules.blocks.find((b) => b.kind === "warmup") as PlanBlock);
  if (!blocks.some((b) => b.kind === "cooldown"))
    blocks.push(rules.blocks.find((b) => b.kind === "cooldown") as PlanBlock);
  const order = { warmup: 0, skill: 1, main: 2, finisher: 3, cooldown: 4 };
  blocks.sort((a, b) => order[a.kind] - order[b.kind]);
  const dayType = (
    raw.dayType && raw.dayType in DAY_TYPES ? raw.dayType : rules.dayType
  ) as DayType;
  const plan: SessionPlan = {
    title: String(raw.title || DAY_TYPES[dayType].title).slice(0, 50),
    dayType,
    intensity: (["leicht", "mittel", "hart"].includes(raw.intensity ?? "")
      ? raw.intensity
      : rules.intensity) as SessionPlan["intensity"],
    minutes: 0,
    kcal: 0,
    coach: {
      headline: String(raw.coach?.headline || rules.coach.headline).slice(0, 90),
      message: String(raw.coach?.message || rules.coach.message).slice(0, 400),
      why: (raw.coach?.why ?? []).map((w) => String(w).slice(0, 160)).slice(0, 4),
    },
    blocks,
  };
  // Dauer: zu lang → Runden kürzen, zu kurz → Runden ergänzen.
  const want = (ctx.checkin?.minutes ?? ctx.profile.sessionMinutes) * 60;
  const main = plan.blocks.find((b) => b.kind === "main") as PlanBlock;
  for (let i = 0; i < 6 && planSeconds(plan, ctx.catalog) > want * 1.25 && main.rounds > 1; i++)
    main.rounds--;
  for (
    let i = 0;
    i < 6 &&
    planSeconds(plan, ctx.catalog) < want * 0.7 &&
    main.rounds < 6 &&
    main.format !== "flow";
    i++
  )
    main.rounds++;
  plan.minutes = Math.round(planSeconds(plan, ctx.catalog) / 60);
  plan.kcal = estimateKcal(plan, ctx.catalog, ctx.profile.weightKg);
  return plan;
}

function historyForAi(db: FitDb, catalog: Catalog, day: string) {
  const name = (id: string) => catalog.byId.get(id)?.name ?? id;
  return sessionsBetween(db, addDays(day, -14), day)
    .filter((s) => s.status === "done" || s.status === "skipped")
    .map((s) => ({
      tag: s.day,
      status: s.status,
      typ: s.plan.dayType,
      intensitaet: s.plan.intensity,
      dauer_min: s.result ? Math.round(s.result.seconds / 60) : null,
      gesamt: s.feedback
        ? ["", "viel zu leicht", "zu leicht", "passend", "zu schwer", "viel zu schwer"][
            s.feedback.overall
          ]
        : null,
      schmerz: s.feedback?.pain ?? [],
      uebungen: (s.feedback?.exercises ?? [])
        .filter((f) => f.difficulty || f.technique === 1 || f.pain || f.dislike)
        .map((f) => ({
          uebung: name(f.ex),
          ...(f.difficulty ? { gefuehl: f.difficulty < 0 ? "zu leicht" : "zu schwer" } : {}),
          ...(f.technique === 1 ? { technik: "unsicher" } : {}),
          ...(f.pain ? { schmerz: true } : {}),
        })),
      notiz: s.feedback?.note ?? null,
    }));
}

export async function aiPlan(
  ai: FitnessAi,
  db: FitDb,
  ctx: PlanContext,
  rules: SessionPlan,
): Promise<{ plan: SessionPlan; model: string }> {
  const p: Profile = ctx.profile;
  // Beherrschte Grundübungen nicht anbieten — dort ist die nächste Variante dran (progression.ts).
  const resolveV = makeResolver(db, ctx.catalog, (e) => usable(ctx, e));
  const allowed = ctx.catalog.exercises.filter((e) => usable(ctx, e) && resolveV(e.id) === e.id);
  // Kompakt: eine Zeile je Übung (spart Eingabe und damit Zeit).
  const line = (e: Exercise) =>
    [
      e.id,
      e.name,
      e.pattern,
      `schwierigkeit ${e.level}/10`,
      e.unit === "sec" ? "s" : "wdh",
      e.perSide ? "je Seite" : "",
      e.use.map((u) => u[0]).join(""),
      e.use.includes("main") ? `regel ${doseFor(ctx, e, rules.intensity, "main")}` : "",
    ]
      .filter(Boolean)
      .join(" | ");
  const input = {
    heute: ctx.day,
    minuten: ctx.checkin?.minutes ?? p.sessionMinutes,
    profil: {
      geschlecht: p.sex,
      alter: new Date().getFullYear() - p.birthYear,
      gewicht: p.weightKg,
      ziel: p.goal,
      level: p.level,
      schwerpunkte: p.focus,
      ausruestung: p.equipment,
      dauerhafte_beschwerden: p.limitations,
      keine_spruenge: p.noJumps,
      trainingstage_pro_woche: p.trainingDays.length,
    },
    tagesform: ctx.checkin ?? null,
    programm: ctx.program
      ? (() => { const ph = phaseFor(db, getPrograms(), ctx.day); return ph ? { name: ph.name, woche: `${ph.week} von ${ph.weeks}`, phase: ph.phase, notiz: ph.note, intensitaet: ph.intensity, volumen: ph.volume, entlastungswoche: ph.deload, fokus: ph.focus } : null; })()
      : null,
    muskelbalance_diese_woche_saetze: ctx.balance ?? null,
    aktuelle_beschwerden: ctx.painRegions,
    verlauf: historyForAi(db, ctx.catalog, ctx.day),
    regel_vorschlag: {
      typ: rules.dayType,
      intensitaet: rules.intensity,
      bloecke: rules.blocks.map((b) => ({
        kind: b.kind,
        format: b.format,
        rounds: b.rounds,
        items: b.items.map((i) => `${i.ex} ×${i.amount}`),
      })),
    },
    erlaubt_format:
      "id | Name | Muster | Schwierigkeit | Einheit | (je Seite) | Blöcke w=warmup m=main c=cooldown | Regel-Menge",
    erlaubt: allowed.map(line),
  };
  const { value, model } = await ai.ask(
    {
      task: "coach",
      system: COACH_SYSTEM,
      input,
      maxTokens: 6000,
      temperature: 0.5,
      timeoutMs: 70_000,
    },
    (raw) => settleAiPlan(raw as AiPlanRaw, ctx, rules),
  );
  return { plan: value, model };
}

// ── Tagesplan holen/erzeugen ────────────────────────────────────────────────

/** Sitzungen, deren KI-Plan gerade entsteht (die Oberfläche zeigt »Coach plant …«). */
export const upgrading = new Set<string>();

/**
 * Aktive Calisthenics-Skills: ein kurzer Skill-Block direkt nach dem Aufwärmen (frische Nerven,
 * saubere Technik). Bei mehreren aktiven Skills wechselt er täglich. Ziel der aktuellen Stufe
 * als Sätze; die Zeit dafür geht vom Hauptteil ab.
 */
export function withSkillBlock(
  db: FitDb,
  catalog: Catalog,
  day: string,
  plan: SessionPlan,
): SessionPlan {
  if (plan.blocks.some((b) => b.kind === "skill") || plan.dayType === "mobilitaet") return plan;
  const active = db
    .prepare("SELECT skill_id, step FROM skill_progress WHERE active = 1 ORDER BY updated_at DESC")
    .all() as { skill_id: string; step: number }[];
  const open = active.filter((a) => {
    const sk = catalog.skillById.get(a.skill_id);
    return sk && a.step < sk.steps.length;
  });
  if (!open.length) return plan;
  const pick = open[Number(day.replace(/-/g, "")) % open.length] as {
    skill_id: string;
    step: number;
  };
  const skill = catalog.skillById.get(pick.skill_id) as NonNullable<
    ReturnType<typeof catalog.skillById.get>
  >;
  const step = skill.steps[pick.step];
  const ex = step?.exercise ? catalog.byId.get(step.exercise) : undefined;
  if (!step || !ex) return plan;
  const hold = step.goal.type === "hold";
  const block: PlanBlock = {
    kind: "skill",
    title: `Skill · ${skill.name}`,
    format: "sets",
    rounds: Math.min(4, Math.max(3, step.goal.sets)),
    restBetweenRounds: 0,
    skill: { id: skill.id, step: pick.step },
    items: [
      {
        ex: ex.id,
        amount: step.goal.value,
        unit: hold ? "sec" : "reps",
        rest: hold ? 75 : 100,
        note: `Stufe ${pick.step + 1}: ${step.name}`,
      },
    ],
  };
  const next = { ...plan, blocks: [...plan.blocks] };
  const warm = next.blocks.findIndex((b) => b.kind === "warmup");
  next.blocks.splice(warm + 1, 0, block);
  // Zeit ausgleichen: eine Runde weniger im Hauptteil, falls möglich.
  const main = next.blocks.find((b) => b.kind === "main");
  if (main && main.format !== "flow" && main.rounds > 2)
    next.blocks[next.blocks.indexOf(main)] = { ...main, rounds: main.rounds - 1 };
  next.minutes = Math.round(planSeconds(next, catalog) / 60);
  next.coach = {
    ...next.coach,
    why: [
      ...next.coach.why,
      `Skill-Block ${skill.name}: ${step.name} (${step.goal.sets} × ${step.goal.value}${hold ? " s" : ""}).`,
    ].slice(0, 5),
  };
  return next;
}

/**
 * Beherrschte Übungen durch die nächste Variante ersetzen (progression.ts) — mit der passenden Menge
 * für die neue Übung. Gilt für Regel- und KI-Pläne gleichermaßen.
 */
export function applyVariants(db: FitDb, ctx: PlanContext, plan: SessionPlan): SessionPlan {
  const resolve = makeResolver(db, ctx.catalog, (e) => usable(ctx, e));
  const blocks = plan.blocks.map((b) => {
    if (b.kind !== "main" && b.kind !== "finisher") return b;
    const used = new Set<string>();
    const items = b.items.map((it) => {
      const id = resolve(it.ex);
      if (id === it.ex || used.has(id)) { used.add(it.ex); return it; }
      const e = ctx.catalog.byId.get(id);
      if (!e) return it;
      used.add(id);
      return { ...it, ex: id, amount: doseFor(ctx, e, plan.intensity, b.kind) };
    });
    return { ...b, items };
  });
  return { ...plan, blocks };
}

function insertSession(
  db: FitDb,
  day: string,
  plan: SessionPlan,
  source: string,
  replaceId?: string,
): SessionRow {
  const id = `${day}-${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;
  db.transaction(() => {
    if (replaceId)
      db.prepare("DELETE FROM sessions WHERE id = ? AND status != 'done'").run(replaceId);
    db.prepare(
      "INSERT INTO sessions(id, day, kind, status, plan, source, created_at) VALUES(?, ?, 'coach', 'planned', ?, ?, ?)",
    ).run(id, day, JSON.stringify(plan), source, Date.now());
  })();
  return getSession(db, id) as SessionRow;
}

/** Ersetzt den Regelplan durch den KI-Plan — aber nur, solange die Einheit noch nicht begonnen hat. */
async function upgrade(
  ai: FitnessAi,
  db: FitDb,
  catalog: Catalog,
  s: SessionRow,
  checkin: PlanContext["checkin"],
  forceType: DayType | undefined,
  log?: (m: string) => void,
) {
  if (upgrading.has(s.id)) return;
  upgrading.add(s.id);
  try {
    const ctx = buildContext(db, catalog, s.day, checkin, forceType);
    const rulesOnly = { ...s.plan, blocks: s.plan.blocks.filter((b) => b.kind !== "skill") };
    const r = await aiPlan(ai, db, ctx, rulesOnly);
    const plan = withSkillBlock(db, catalog, s.day, applyVariants(db, ctx, r.plan));
    const res = db
      .prepare("UPDATE sessions SET plan = ?, source = ? WHERE id = ? AND status = 'planned'")
      .run(JSON.stringify(plan), r.model, s.id);
    if (!res.changes) log?.("[fitness] KI-Plan kam zu spät — Einheit läuft schon");
  } catch (e) {
    log?.(
      `[fitness] KI-Plan fehlgeschlagen, Regelplan bleibt: ${e instanceof Error ? e.message : e}`,
    );
  } finally {
    upgrading.delete(s.id);
  }
}

/**
 * Plan für einen Tag. Gibt es noch keinen, steht sofort der Regelplan da und der KI-Coach verfeinert
 * ihn im Hintergrund (freie Gemini-Modelle brauchen 20–90 s und sind zeitweise überlastet).
 * Mit [wait] (Tagesform angepasst) wird auf die KI gewartet — der Nutzer hat ja ausdrücklich gefragt.
 */
export async function todaySession(
  ai: FitnessAi | null,
  db: FitDb,
  catalog: Catalog,
  opts: {
    day?: string;
    regenerate?: boolean;
    wait?: boolean;
    checkin?: PlanContext["checkin"];
    forceType?: DayType | undefined;
    log?: ((m: string) => void) | undefined;
  } = {},
): Promise<SessionRow> {
  const day = opts.day ?? localDay();
  const existingRow = db
    .prepare(
      "SELECT * FROM sessions WHERE day = ? AND kind = 'coach' ORDER BY created_at DESC, rowid DESC LIMIT 1",
    )
    .get(day) as Record<string, unknown> | undefined;
  const existing = existingRow ? rowToSession(existingRow) : null;
  if (existing && (!opts.regenerate || existing.status === "done")) {
    // Regelplan ohne laufende Verfeinerung (z. B. nach Neustart) → jetzt nachholen.
    if (
      existing.status === "planned" &&
      existing.source === "regeln" &&
      ai?.available() &&
      !upgrading.has(existing.id) &&
      !failedRecently.has(existing.id)
    ) {
      failedRecently.add(existing.id);
      void upgrade(ai, db, catalog, existing, undefined, undefined, opts.log);
    }
    return existing;
  }
  const ctx = buildContext(db, catalog, day, opts.checkin, opts.forceType);
  const rules = withSkillBlock(db, catalog, day, applyVariants(db, ctx, buildRulesPlan(ctx)));
  const row = insertSession(db, day, rules, "regeln", existing?.id);
  if (ai?.available()) {
    const job = upgrade(ai, db, catalog, row, opts.checkin, opts.forceType, opts.log);
    if (opts.wait) {
      await job;
      return getSession(db, row.id) as SessionRow;
    }
  }
  return row;
}

/**
 * Noch ein Training am selben Tag (wenn das geplante schon erledigt ist): neue Einheit neben der
 * erledigten, der Coach weiß, was heute schon trainiert wurde. Wartet auf die KI.
 */
export async function extraSession(
  ai: FitnessAi | null,
  db: FitDb,
  catalog: Catalog,
  opts: {
    day?: string;
    wait?: boolean;
    checkin?: PlanContext["checkin"];
    forceType?: DayType | undefined;
    log?: ((m: string) => void) | undefined;
  } = {},
): Promise<SessionRow> {
  const day = opts.day ?? localDay();
  // Noch nicht begonnene Zusatz-Einheit von vorhin ersetzen statt stapeln.
  db.prepare("DELETE FROM sessions WHERE day = ? AND kind = 'coach' AND status = 'planned'").run(
    day,
  );
  const ctx = buildContext(db, catalog, day, opts.checkin, opts.forceType);
  const rules = withSkillBlock(db, catalog, day, applyVariants(db, ctx, buildRulesPlan(ctx)));
  const row = insertSession(db, day, rules, "regeln");
  if (ai?.available()) {
    // Regelplan sofort; der Coach verfeinert im Hintergrund (freie Modelle brauchen teils Minuten).
    const job = upgrade(ai, db, catalog, row, opts.checkin, opts.forceType, opts.log);
    if (opts.wait) await job;
  }
  return getSession(db, row.id) as SessionRow;
}

/** Einmal je Sitzung im Hintergrund nachbessern, nicht bei jedem Abruf erneut. */
const failedRecently = new Set<string>();

/** Nach dem Training: Plan für den nächsten Trainingstag schon vorbereiten (mit dem frischen Feedback). */
export async function prepareNext(
  ai: FitnessAi | null,
  db: FitDb,
  catalog: Catalog,
  fromDay: string,
  log?: (m: string) => void,
): Promise<{ day: string; session: SessionRow } | null> {
  const p = getProfile(db);
  for (let i = 1; i <= 7; i++) {
    const d = addDays(fromDay, i);
    if (!p.trainingDays.includes(weekdayOf(d))) continue;
    // Ein schon vorbereiteter, noch nicht begonnener Plan entstand ohne dieses Feedback → neu planen.
    const has = db
      .prepare(
        "SELECT status FROM sessions WHERE day = ? AND kind = 'coach' ORDER BY created_at DESC, rowid DESC LIMIT 1",
      )
      .get(d) as { status: string } | undefined;
    if (has && has.status !== "planned") return null;
    const session = await todaySession(ai, db, catalog, {
      day: d,
      regenerate: !!has,
      wait: true,
      log,
    });
    return { day: d, session };
  }
  return null;
}

function weekdayOf(day: string): number {
  const [y, m, d] = day.split("-").map(Number) as [number, number, number];
  return (new Date(y, m - 1, d, 12).getDay() + 6) % 7;
}

// ── Feedback verarbeiten ────────────────────────────────────────────────────

/** Feste Lernregeln je Übung — die KI liest das Ergebnis beim nächsten Plan mit. */
/** Signal je Hauptübung für Auf-/Abstieg (progression.checkProgression). */
export interface ExerciseSignal { ex: string; dose: number; times: number; difficulty?: number | undefined; technique?: number | undefined; reached: number; pain?: boolean | undefined }

export function applyFeedback(
  db: FitDb,
  session: SessionRow,
  fb: SessionFeedback,
  result: SessionResult,
): ExerciseSignal[] {
  const signals: ExerciseSignal[] = [];
  const now = Date.now();
  const day = session.day;
  const upsert =
    db.prepare(`INSERT INTO exercise_state(exercise_id, dose, best, last, times, technique, avoid_until, disliked, updated_at)
    VALUES(@id, @dose, @best, @last, 1, @technique, @avoid, @disliked, @now)
    ON CONFLICT(exercise_id) DO UPDATE SET dose = @dose, best = MAX(COALESCE(best, 0), COALESCE(@best, 0)), last = COALESCE(@last, last),
      times = times + 1, technique = COALESCE(@technique, technique), avoid_until = COALESCE(@avoid, avoid_until), disliked = MAX(disliked, @disliked), updated_at = @now`);
  const states = exerciseStates(db);
  // Gesamtgefühl wirkt leicht auf alle Übungen der Einheit.
  const overallShift = { 1: 0.1, 2: 0.05, 3: 0, 4: -0.05, 5: -0.1 }[fb.overall] ?? 0;
  const perEx = new Map(fb.exercises.map((f) => [f.ex, f]));
  const seen = new Set<string>();
  db.transaction(() => {
    session.plan.blocks.forEach((b, bi) => {
      if (b.kind !== "main" && b.kind !== "finisher") return;
      b.items.forEach((it, ii) => {
        if (seen.has(it.ex)) return;
        seen.add(it.ex);
        const f = perEx.get(it.ex);
        const st = states.get(it.ex);
        let dose = st?.dose ?? 1;
        const done = result.done[`${bi}:${ii}`] ?? [];
        const skipped = result.skipped.includes(`${bi}:${ii}`);
        // Nicht geschafft (unter 80 % der Vorgabe) zählt wie »zu schwer«.
        const reached = done.length ? Math.min(...done) / it.amount : 1;
        let shift = overallShift;
        if (f?.difficulty === -1) shift += 0.12;
        if (f?.difficulty === 1) shift -= 0.12;
        if (reached < 0.8) shift -= 0.1;
        else if (reached > 1.15) shift += 0.08;
        if (f?.technique === 1) shift -= 0.05;
        dose = Math.min(2.2, Math.max(0.45, dose * (1 + shift)));
        if (!skipped && done.length)
          signals.push({ ex: it.ex, dose, times: (st?.times ?? 0) + 1, difficulty: f?.difficulty, technique: f?.technique, reached, pain: f?.pain });
        upsert.run({
          id: it.ex,
          dose: skipped ? (st?.dose ?? 1) : dose,
          best: done.length ? Math.max(...done) : null,
          last: done.length ? done[done.length - 1] : null,
          technique: f?.technique ?? null,
          avoid: f?.pain ? addDays(day, 7) : null,
          disliked: f?.dislike ? 1 : 0,
          now,
        });
      });
    });
    // Auch Aufwärm-/Dehnübungen kann man »nicht mögen«.
    for (const f of fb.exercises)
      if (f.dislike && !seen.has(f.ex))
        upsert.run({
          id: f.ex,
          dose: 1,
          best: null,
          last: null,
          technique: null,
          avoid: null,
          disliked: 1,
          now,
        });
    for (const p of fb.pain)
      db.prepare(
        "INSERT INTO pain_log(day, region, level, note, session_id) VALUES(?, ?, ?, ?, ?)",
      ).run(day, p.region, Math.min(10, Math.max(1, p.level)), null, session.id);
    db.prepare(
      "UPDATE sessions SET status = 'done', result = ?, feedback = ?, done_at = ? WHERE id = ?",
    ).run(JSON.stringify(result), JSON.stringify(fb), now, session.id);
  })();
  return signals;
}

const REVIEW_SYSTEM = `Du bist der Personal Coach des Nutzers. Er hat gerade eine Einheit beendet und Feedback gegeben.
Schreibe eine kurze, persönliche Auswertung auf Deutsch (du-Form, keine Emojis): was gut lief, was auffällt, und konkret, was du für die nächsten Einheiten änderst (nenne Übungen).
Wenn Schmerzen angegeben wurden: ernst nehmen, Schonung erklären, bei starken oder anhaltenden Schmerzen ärztliche Abklärung empfehlen.
Antworte NUR mit JSON: {"headline":"eine Zeile","good":["…"],"watch":["…"],"next":["konkrete Anpassung …"]}`;

export async function reviewSession(
  ai: FitnessAi,
  catalog: Catalog,
  session: SessionRow,
  fb: SessionFeedback,
  result: SessionResult,
) {
  const name = (id: string) => catalog.byId.get(id)?.name ?? id;
  const input = {
    einheit: session.plan.title,
    intensitaet: session.plan.intensity,
    geplant_min: session.plan.minutes,
    gebraucht_min: Math.round(result.seconds / 60),
    uebungen: session.plan.blocks.flatMap((b, bi) =>
      b.kind === "main" || b.kind === "finisher"
        ? b.items.map((it, ii) => ({
            uebung: name(it.ex),
            vorgabe: `${it.amount} ${it.unit === "sec" ? "s" : "Wdh"}${it.perSide ? " je Seite" : ""}`,
            geschafft: result.done[`${bi}:${ii}`] ?? [],
            uebersprungen: result.skipped.includes(`${bi}:${ii}`),
          }))
        : [],
    ),
    feedback: {
      gesamt: ["", "viel zu leicht", "zu leicht", "passend", "zu schwer", "viel zu schwer"][
        fb.overall
      ],
      energie: fb.energy,
      spass: fb.enjoyment,
      schmerz: fb.pain,
      je_uebung: fb.exercises.map((f) => ({ uebung: name(f.ex), ...f })),
      notiz: fb.note,
    },
  };
  const { value, model } = await ai.ask(
    { task: "text", system: REVIEW_SYSTEM, input, maxTokens: 1500, temperature: 0.5 },
    (raw) => {
      const r = raw as { headline?: string; good?: string[]; watch?: string[]; next?: string[] };
      if (!r.headline) throw new Error("leer");
      const list = (a: unknown) =>
        Array.isArray(a) ? a.map((s) => String(s).slice(0, 220)).slice(0, 4) : [];
      return {
        headline: String(r.headline).slice(0, 120),
        good: list(r.good),
        watch: list(r.watch),
        next: list(r.next),
      };
    },
  );
  return { ...value, model };
}
