import { type ChildProcess, spawn } from "node:child_process";
import {
  createReadStream,
  existsSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  renameSync,
  rmSync,
  statSync,
  writeFileSync,
} from "node:fs";
import { join } from "node:path";
import type { FastifyInstance } from "fastify";
import {
  type DownReason,
  type PieceKind,
  type PlanSlot,
  type Taste,
  type TasteObs,
  type TopicSuggestion,
  computeTaste,
  normalizeReasons,
  planRun,
  seededRng,
  suggestTopics,
  suggestionId,
} from "../russian/erleben-taste.js";
import { resolveUv } from "../voice/whisper.js";

/**
 * »Lieder & Szenen« im Russisch-Bereich: echte russische Videos und Lieder zu den Themen des
 * Lernenden, vom Server gesucht, aufbereitet und doppelt geprüft (backend/erleben/prepare.py).
 *
 * Dieser Teil verwaltet Sammlung, Feed und den täglichen Lauf:
 *  - fertige Stücke liegen als JSON in <dir>/auto, ihre Videos in <dir>/media
 *  - EIN Lauf am Tag, kurz nachdem sich Googles Kontingent erneuert (Mitternacht in Kalifornien,
 *    unabhängig von der Zeitzone des Macs oder Handys). Er bereitet nacheinander so viele Stücke
 *    auf, wie das Kontingent hergibt, und hört auf, wenn es leer ist oder nichts mehr besteht.
 *  - war der Server über die Erneuerung aus, holt er beim Start GENAU EINEN Lauf nach — nie
 *    einen je verpassten Tag
 *  - zwischen den Läufen wird nichts nachgefüllt; »Neue« zeigt nur schon Aufbereitetes
 *  - alles Aufbereitete bleibt für immer in der Sammlung; gelöscht wird nur auf Wunsch
 *    (»nicht gut« = löschen und Ähnliches meiden, »löschen« = ohne Bewertung)
 *  - Rückmeldungen (gut, gemerkt, nicht gut, weggeklickt, angesehen, durchgearbeitet,
 *    Selbsteinschätzung) steuern den nächsten Lauf (siehe russian/erleben-taste.ts)
 *
 * In den Feed kommt nur, was die ganze Prüfkette durchlaufen hat (`checked`), und nur im
 * aktuellen Stand der Aufbereitung (`v`); ältere Stücke bereitet prepare.py von selbst neu auf.
 */

const FEED_SIZE = 3;
/** Tageskontingente der Prüfmodelle (siehe backend/erleben/gemini.py). */
const REVIEW_MODELS = ["gemini-4-hydrogen", "gemini-3.6-flash", "gemini-3.8-flash"];
const REVIEW_PER_DAY = 20;
const HEAR_MODEL = "gemini-3.5-transcribe";
const HEAR_PER_DAY = 25;
const QUOTA_ZONE = "America/Los_Angeles";
/** Der Lauf startet ein paar Minuten nach der Erneuerung — Googles Zähler springen nicht auf die Sekunde. */
export const RUN_DELAY_MS = 5 * 60 * 1000;
/** Später als das nach der Startzeit = der Server war aus, der Lauf ist ein Nachholer. */
const CATCH_UP_AFTER_MS = 15 * 60 * 1000;
/** So oft schaut der Server spätestens nach, ob ein Lauf fällig ist (übersteht Ruhezustand und Uhrwechsel). */
const CHECK_MS = 10 * 60 * 1000;
/** Je Aufruf von prepare.py so viele Stücke; ein Abschnitt ohne einziges fertiges Stück beendet den Lauf. */
const BATCH = 5;
const MAX_BATCHES = 12;
/**
 * Prüfmodelle bei Google überlastet (503), Kontingent aber noch da: warten und denselben Abschnitt
 * noch einmal — solange der Kontingent-Tag läuft, höchstens BUSY_TRIES-mal (gut drei Stunden).
 */
export const BUSY_WAIT_MS = 20 * 60 * 1000;
export const BUSY_TRIES = 10;
/** Ein vom Neustart unterbrochener Lauf geht am selben Kontingent-Tag weiter — höchstens so oft. */
const MAX_RESUMES = 3;
const PLAN_SLOTS = 20;
const ID_RE = /^[a-z0-9_]{1,60}$/;

export interface ErlebenPiece {
  id: string;
  checked?: boolean;
  [key: string]: unknown;
}

export interface Interests {
  video: string[];
  music: string[];
}

/** Ein Stück der Sammlung in Kurzform, mit allem, was der Lernende daran gemacht hat. */
export interface CollectionItem {
  id: string;
  yt: string | null;
  kind: PieceKind;
  topic: string;
  title: string;
  titleDe: string;
  by: string;
  channel: string;
  level: string;
  dur: number;
  poster: string | null;
  media: string | null;
  /** Wann das Stück fertig wurde (ms). */
  addedAt: number;
  /** Vom letzten Lauf neu aufbereitet. */
  isNew: boolean;
  saved: boolean;
  rating: "up" | null;
  watched: number;
  worked: number;
  skips: number;
  understood: 1 | 2 | 3 | null;
}

export interface RunInfo {
  startedAt: number;
  /** null = läuft noch (oder wurde vom Neustart unterbrochen). */
  finishedAt: number | null;
  /** Nachgeholt, weil der Server zur Zeit der Erneuerung aus war. */
  catchUp: boolean;
  prepared: number;
  ids: string[];
  /** Grund, falls der Lauf nichts versucht hat. */
  skipped?: "no-topics" | "no-quota";
  /** Wie oft der Lauf nach einem Neustart fortgesetzt wurde. */
  resumed?: number;
  /** Wartet gerade, weil Googles Prüfmodelle überlastet sind (bis zu diesem Zeitpunkt). */
  waitingUntil?: number | null;
}

/** Was ein Aufruf von prepare.py meldet. Eine bloße Zahl = so viele fertig (ältere Tests). */
export type PrepareResult = number | { prepared: number; busy?: boolean };

export interface ErlebenFeedState {
  shown: string[];
  /** Bibliothek: behaltene Stücke, die neuesten zuerst. */
  saved: string[];
  ready: number;
  busy: boolean;
  /** Alle Stücke der Sammlung, vollständig (älteste zuerst) — so liest es die App bisher. */
  pieces: ErlebenPiece[];
  interests: Interests;
  leftToday: number;
  /** Nächste Erneuerung des Kontingents. */
  resetAt: number;
  // ── seit dem Tageslauf ──
  /** Sammlung in Kurzform mit Bewertung/Merken/Fortschritt, die neuesten zuerst. */
  collection: CollectionItem[];
  /** Was der letzte (oder gerade laufende) Lauf neu aufbereitet hat. */
  todayIds: string[];
  todayCount: number;
  /** Start des letzten Laufs (0 = noch nie). */
  lastRunAt: number;
  /** Wann der nächste Lauf startet (ms). */
  nextRunAt: number;
  lastRun: RunInfo | null;
  suggestions: TopicSuggestion[];
  taste: { levelTarget: number; blockedChannels: string[]; topics: Taste["topics"] };
}

/** Was ein Abschnitt des Laufs suchen soll — liegt als plan.json für prepare.py bereit. */
export interface RunPlan {
  order: PlanSlot[];
  blocked: string[];
  avoid: string[];
  easyWeight: number;
  levelTarget: number;
}

export interface ErlebenDeps {
  /** Arbeitsordner, üblicherweise ~/.hikari/erleben. */
  dir: string;
  /** Pfad zu backend/erleben/prepare.py. */
  script: string;
  now?: () => number;
  /** Für Tests: eigener Starter des Aufbereiters. Löst mit der Zahl fertiger Stücke auf. */
  runPrepare?: (count: number, plan: RunPlan) => Promise<PrepareResult>;
  /** Für Tests: Warten ohne echte Zeit. */
  sleep?: (ms: number) => Promise<void>;
  /** Für Tests: Zufall der Plan-Ziehung (Standard: Startzeit des Laufs + Abschnitt). */
  seed?: (runStart: number, batch: number) => number;
  log?: (msg: string) => void;
  /** false = kein Zeitplan (Tests rufen `runDaily` selbst auf). */
  schedule?: boolean;
  /**
   * Neue Themen vorschlagen (Sprachmodell, höchstens einmal je Tageslauf). Ohne diese Abhängigkeit
   * kommen Vorschläge nur aus Interpreten und Kanälen — gemessen 18.11.2026 unbrauchbar als Thema
   * (»Песни под гитару от lliillililDetroit (аккорды, табы)«).
   */
  suggest?: (input: {
    liked: { kind: PieceKind; title: string; topic: string }[];
    current: Interests;
    declined: string[];
  }) => Promise<{ kind: PieceKind; topic: string; why: string }[]>;
}

interface Mark {
  rating?: "up" | "down" | undefined;
  reasons?: DownReason[] | undefined;
  ratedAt?: number;
  skips?: number;
  watched?: number;
  worked?: number;
  understood?: 1 | 2 | 3;
  seenAt?: number;
  /** Was über das Stück bekannt war — bleibt, auch wenn es gelöscht ist. */
  info?: {
    yt?: string;
    kind: PieceKind;
    topic: string;
    channel: string;
    by: string;
    level: string;
  };
}

interface SignalsFile {
  marks: Record<string, Mark>;
  /** Gelöschte Stücke: das Video wird nicht noch einmal aufbereitet. */
  gone: Record<string, { yt?: string; at: number; why: "down" | "delete" }>;
  topics: { declined: string[]; accepted: string[]; proposed?: TopicSuggestion[] };
}

function readJson<T>(file: string, fallback: T): T {
  try {
    return JSON.parse(readFileSync(file, "utf8")) as T;
  } catch {
    return fallback;
  }
}

function writeAtomic(file: string, data: string): void {
  const tmp = `${file}.tmp`;
  writeFileSync(tmp, data);
  renameSync(tmp, file);
}

/** Themen säubern: Text, höchstens 60 Zeichen, ohne Doppelte, höchstens zwölf je Art. */
export function cleanInterests(raw: unknown): Interests {
  const list = (a: unknown) =>
    Array.isArray(a)
      ? [...new Set(a.map((x) => String(x).trim().slice(0, 60)).filter(Boolean))].slice(0, 12)
      : [];
  const r = (raw ?? {}) as { video?: unknown; music?: unknown };
  return { video: list(r.video), music: list(r.music) };
}

// ── Zeit: alles in Pazifikzeit gerechnet, nie in der Ortszeit des Rechners ──────────────────

const zoneParts = new Intl.DateTimeFormat("en-US", {
  timeZone: QUOTA_ZONE,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  hourCycle: "h23",
});

function inZone(ms: number): { y: number; m: number; d: number; h: number; mi: number; s: number } {
  const p = Object.fromEntries(
    zoneParts.formatToParts(new Date(ms)).map((x) => [x.type, Number(x.value)]),
  );
  return {
    y: p.year ?? 1970,
    m: p.month ?? 1,
    d: p.day ?? 1,
    h: (p.hour ?? 0) % 24,
    mi: p.minute ?? 0,
    s: p.second ?? 0,
  };
}

/** Abstand der Pazifikzeit zu UTC in diesem Moment (−7 h Sommer, −8 h Winter). */
function zoneOffset(ms: number): number {
  const z = inZone(ms);
  return Date.UTC(z.y, z.m - 1, z.d, z.h, z.mi, z.s) - Math.floor(ms / 1000) * 1000;
}

/** Mitternacht eines Kalendertags in Kalifornien als Zeitpunkt (zweimal gerechnet: Sommerzeit). */
function zoneMidnight(y: number, m: number, d: number): number {
  const guess = Date.UTC(y, m - 1, d);
  const first = guess - zoneOffset(guess);
  return guess - zoneOffset(first);
}

/** Der Tag, nach dem Google die Tageskontingente zählt (Mitternacht Pazifikzeit). */
export function quotaDay(nowMs: number): string {
  const z = inZone(nowMs);
  return `${z.y}-${String(z.m).padStart(2, "0")}-${String(z.d).padStart(2, "0")}`;
}

/** Die letzte Erneuerung des Kontingents (≤ jetzt). */
export function lastQuotaReset(nowMs: number): number {
  const z = inZone(nowMs);
  return zoneMidnight(z.y, z.m, z.d);
}

/**
 * Wann das Tageskontingent sich erneuert — als Zeitpunkt, damit die App ihn in Ortszeit zeigen
 * kann. Rechnet über den Kalendertag, nicht »jetzt + Stunden bis Mitternacht«: an den Tagen der
 * Zeitumstellung hat der Tag in Kalifornien 23 oder 25 Stunden.
 */
export function quotaResetAt(nowMs: number): number {
  const z = inZone(nowMs);
  return zoneMidnight(z.y, z.m, z.d + 1); // Date.UTC rechnet den 32. in den nächsten Monat um
}

/**
 * Fällig ist ein Lauf, wenn seit der letzten Erneuerung keiner gestartet ist und deren
 * Startzeit (Erneuerung + RUN_DELAY_MS) erreicht ist. Wie viele Erneuerungen dazwischen lagen,
 * spielt keine Rolle — deshalb gibt es nach mehreren verpassten Tagen genau einen Lauf.
 */
export function runDue(nowMs: number, lastRunAt: number): boolean {
  const reset = lastQuotaReset(nowMs);
  return lastRunAt < reset && nowMs >= reset + RUN_DELAY_MS;
}

/** Wann der nächste Lauf startet: der fällige sofort, sonst nach der nächsten Erneuerung. */
export function nextRunAt(nowMs: number, lastRunAt: number): number {
  const reset = lastQuotaReset(nowMs);
  if (lastRunAt < reset) return Math.max(nowMs, reset + RUN_DELAY_MS);
  return quotaResetAt(nowMs) + RUN_DELAY_MS;
}

const str = (v: unknown) => (typeof v === "string" ? v : "");
const kindOf = (p: ErlebenPiece): PieceKind => (p.kind === "song" ? "song" : "scene");

export class ErlebenFeed {
  private readonly autoDir: string;
  private readonly mediaDir: string;
  private readonly feedFile: string;
  private readonly interestsFile: string;
  private readonly candidatesFile: string;
  private readonly usageFile: string;
  private readonly signalsFile: string;
  private readonly runsFile: string;
  private readonly planFile: string;
  private readonly now: () => number;
  private feed: { shown: string[]; dismissed: string[]; saved: string[] };
  private signals: SignalsFile;
  private lastRun: RunInfo | null;
  private running: Promise<void> | null = null;
  /** Stücke vor Beginn des laufenden Laufs — was danach dazukommt, ist »heute neu«. */
  private before: Set<string> | null = null;
  private timer: NodeJS.Timeout | null = null;

  constructor(private readonly deps: ErlebenDeps) {
    this.autoDir = join(deps.dir, "auto");
    this.mediaDir = join(deps.dir, "media");
    this.feedFile = join(deps.dir, "feed.json");
    this.interestsFile = join(deps.dir, "interests.json");
    this.candidatesFile = join(deps.dir, "candidates.json");
    this.usageFile = join(deps.dir, "usage.json");
    this.signalsFile = join(deps.dir, "signals.json");
    this.runsFile = join(deps.dir, "runs.json");
    this.planFile = join(deps.dir, "plan.json");
    this.now = deps.now ?? Date.now;
    mkdirSync(this.autoDir, { recursive: true });
    mkdirSync(this.mediaDir, { recursive: true });
    this.feed = { shown: [], dismissed: [], saved: [], ...readJson(this.feedFile, {}) };
    const sig = readJson<Partial<SignalsFile>>(this.signalsFile, {});
    this.signals = {
      marks: sig.marks ?? {},
      gone: sig.gone ?? {},
      topics: { declined: [], accepted: [], ...sig.topics },
    };
    this.lastRun = readJson<{ last?: RunInfo }>(this.runsFile, {}).last ?? null;
  }

  // ── Zeitplan ──────────────────────────────────────────────────────────────

  /** Beim Serverstart: ein fälliger (auch verpasster) Lauf sofort, danach zur nächsten Erneuerung. */
  start(): void {
    this.check();
  }

  stop(): void {
    if (this.timer) clearTimeout(this.timer);
    this.timer = null;
  }

  get lastRunAt(): number {
    return this.lastRun?.startedAt ?? 0;
  }

  get busy(): boolean {
    return this.running !== null;
  }

  private check(): void {
    this.stop();
    const now = this.now();
    const lr = this.lastRun;
    if (!this.running && runDue(now, this.lastRunAt))
      void this.runDaily(now > lastQuotaReset(now) + RUN_DELAY_MS + CATCH_UP_AFTER_MS);
    // Vom Neustart unterbrochen, am selben Kontingent-Tag: weitermachen statt den Rest zu verschenken.
    else if (
      !this.running &&
      lr &&
      lr.finishedAt === null &&
      lr.startedAt >= lastQuotaReset(now) &&
      (lr.resumed ?? 0) < MAX_RESUMES
    )
      void this.runDaily(lr.catchUp, lr);
    // Genau zur nächsten Startzeit wecken, aber spätestens alle zehn Minuten nachsehen.
    const wait = Math.min(CHECK_MS, Math.max(1000, nextRunAt(now, this.lastRunAt) - now));
    this.timer = setTimeout(() => this.check(), wait);
    this.timer.unref?.();
  }

  /** Wartet, bis ein gerade laufender Tageslauf fertig ist. */
  idle(): Promise<void> {
    return this.running ?? Promise.resolve();
  }

  /** Der Tageslauf (startet sofort). Läuft schon einer, wird kein zweiter gestartet. */
  runDaily(catchUp = false, resume?: RunInfo): Promise<void> {
    if (this.running) return this.running;
    this.running = this.doRun(catchUp, resume).finally(() => {
      this.running = null;
      this.before = null;
    });
    return this.running;
  }

  private async doRun(catchUp: boolean, resume?: RunInfo): Promise<void> {
    const info: RunInfo = resume
      ? { ...resume, ids: [...resume.ids], finishedAt: null, resumed: (resume.resumed ?? 0) + 1, waitingUntil: null }
      : { startedAt: this.now(), finishedAt: null, catchUp, prepared: 0, ids: [] };
    if (resume) this.deps.log?.(`erleben: unterbrochener Tageslauf geht weiter (${info.ids.length} schon fertig)`);
    const dayEnd = quotaResetAt(info.startedAt);
    const sleep = this.deps.sleep ?? ((ms: number) => new Promise<void>((r) => setTimeout(r, ms)));
    let waits = 0;
    // Der Start wird sofort festgehalten: auch ein abgebrochener Lauf ist der Lauf des Tages.
    this.saveRun(info);
    const wishes = this.interests();
    if (wishes.video.length + wishes.music.length === 0) {
      info.skipped = "no-topics";
    } else if (this.leftToday() === 0) {
      info.skipped = "no-quota";
    } else {
      // Schon fertige Stücke eines fortgesetzten Laufs zählen weiter als »heute neu«.
      this.before = new Set(this.pieces().map((p) => p.id).filter((id) => !info.ids.includes(id)));
      const run = this.deps.runPrepare ?? ((count) => this.spawnPrepare(count));
      for (let batch = 0; batch < MAX_BATCHES; batch++) {
        const left = this.leftToday();
        if (left <= 0) break;
        const plan = this.plan(this.deps.seed?.(info.startedAt, batch) ?? info.startedAt + batch);
        writeAtomic(this.planFile, JSON.stringify(plan));
        const count = Math.min(BATCH, left);
        const res = await run(count, plan).catch(() => 0 as PrepareResult);
        const prepared = typeof res === "number" ? res : res.prepared;
        const busy = typeof res === "number" ? false : !!res.busy;
        info.prepared += prepared;
        info.ids = this.freshIds();
        this.saveRun(info);
        this.deps.log?.(`erleben: Abschnitt ${batch + 1}: ${prepared} von ${count} fertig`);
        if (busy) {
          // Überlastet heißt nicht leer: warten und denselben Abschnitt noch einmal (Kadirs Wunsch:
          // so viel, wie das Kontingent hergibt) — aber nie über die Erneuerung hinaus.
          if (waits >= BUSY_TRIES || this.now() + BUSY_WAIT_MS >= dayEnd || this.leftToday() <= 0) break;
          waits++;
          info.waitingUntil = this.now() + BUSY_WAIT_MS;
          this.saveRun(info);
          this.deps.log?.(
            `erleben: Prüfmodelle überlastet — neuer Versuch in ${BUSY_WAIT_MS / 60_000} min (${waits}/${BUSY_TRIES})`,
          );
          await sleep(BUSY_WAIT_MS);
          info.waitingUntil = null;
          batch--;
          continue;
        }
        // Nichts bestanden, oder kein Kontingent verbraucht (keine Kandidaten mehr) → Schluss.
        if (prepared === 0 || this.leftToday() >= left) break;
      }
      info.ids = this.freshIds();
    }
    await this.proposeTopics();
    info.finishedAt = this.now();
    this.saveRun(info);
    this.deps.log?.(
      `erleben: Tageslauf fertig — ${info.ids.length} neu${info.skipped ? ` (${info.skipped})` : ""}`,
    );
  }

  private freshIds(): string[] {
    const before = this.before;
    if (!before) return this.lastRun?.ids ?? [];
    return this.pieces()
      .map((p) => p.id)
      .filter((id) => !before.has(id));
  }

  private saveRun(info: RunInfo): void {
    this.lastRun = { ...info, ids: [...info.ids] };
    writeAtomic(this.runsFile, JSON.stringify({ last: this.lastRun }));
  }

  /** Was der nächste Abschnitt eines Laufs suchen soll (geht als plan.json an prepare.py). */
  plan(seed: number): RunPlan {
    const taste = this.taste();
    return {
      order: planRun(taste, PLAN_SLOTS, seededRng(seed)),
      blocked: taste.blockedChannels,
      avoid: Object.values(this.signals.gone)
        .map((g) => g.yt)
        .filter((x): x is string => !!x),
      easyWeight: taste.easyWeight,
      levelTarget: taste.levelTarget,
    };
  }

  // ── Dateien ───────────────────────────────────────────────────────────────

  mediaFile(name: string): string {
    return join(this.mediaDir, name);
  }

  interests(): Interests {
    return cleanInterests(readJson(this.interestsFile, {}));
  }

  setInterests(raw: unknown): void {
    const next = cleanInterests(raw);
    writeAtomic(this.interestsFile, JSON.stringify(next));
    // Neue Themen, neue Suche: gemerkte Trefferlisten verfallen, der Vorrat bleibt.
    const old = readJson<Record<string, unknown> | null>(this.candidatesFile, null);
    if (old) writeAtomic(this.candidatesFile, JSON.stringify({ ...old, pools: {}, turn: 0 }));
    // Gesucht wird erst im nächsten Tageslauf. Einzige Ausnahme: der heutige Lauf hat mangels
    // Themen nichts getan — dann wird er jetzt zum Lauf des Tages (sonst wartete ein neuer
    // Lernender bis zu 24 Stunden auf das erste Stück).
    const last = this.lastRun;
    if (
      last?.skipped === "no-topics" &&
      last.startedAt >= lastQuotaReset(this.now()) &&
      next.video.length + next.music.length > 0
    )
      void this.runDaily(false);
  }

  /** Wie viele Stücke heute noch geprüft werden können. */
  leftToday(): number {
    const u = readJson<{ day?: string; count?: Record<string, number> }>(this.usageFile, {});
    const today = quotaDay(this.now());
    const used = (model: string) => (u.day === today ? (u.count?.[model] ?? 0) : 0);
    const reviews = REVIEW_MODELS.reduce((n, m) => n + Math.max(0, REVIEW_PER_DAY - used(m)), 0);
    return Math.min(reviews, Math.max(0, HEAR_PER_DAY - used(HEAR_MODEL)));
  }

  private saveFeed(): void {
    writeAtomic(this.feedFile, JSON.stringify(this.feed));
  }

  private saveSignals(): void {
    writeAtomic(this.signalsFile, JSON.stringify(this.signals));
  }

  /** Geprüfte Stücke, die ältesten zuerst. */
  pieces(): ErlebenPiece[] {
    return this.files().map(({ p }) => p);
  }

  private files(): { p: ErlebenPiece; at: number }[] {
    const out: { p: ErlebenPiece; at: number }[] = [];
    const list = readdirSync(this.autoDir)
      .filter((f) => f.endsWith(".json"))
      .map((f) => ({ f, at: statSync(join(this.autoDir, f)).mtimeMs }))
      .sort((a, b) => a.at - b.at);
    for (const { f, at } of list) {
      const p = readJson<ErlebenPiece | null>(join(this.autoDir, f), null);
      if (p && typeof p.id === "string" && p.checked === true) out.push({ p, at });
    }
    return out;
  }

  private piece(id: string): ErlebenPiece | null {
    if (!ID_RE.test(id)) return null;
    const p = readJson<ErlebenPiece | null>(join(this.autoDir, `${id}.json`), null);
    return p && p.id === id && p.checked === true ? p : null;
  }

  private infoOf(p: ErlebenPiece): NonNullable<Mark["info"]> {
    return {
      ...(typeof p.yt === "string" ? { yt: p.yt } : {}),
      kind: kindOf(p),
      topic: str(p.topic),
      // Ältere Stücke kennen ihren Kanal nicht; dann steht der Urheber dafür.
      channel: str(p.channel) || str(p.by),
      by: str(p.by),
      level: str(p.level),
    };
  }

  private mark(id: string, p: ErlebenPiece): Mark {
    const m = this.signals.marks[id] ?? {};
    m.info = this.infoOf(p);
    this.signals.marks[id] = m;
    return m;
  }

  /** Stück samt Video und Standbild entfernen und aus Feed und Bibliothek nehmen. */
  private erase(id: string, p: ErlebenPiece, why: "down" | "delete"): void {
    for (const f of [
      join(this.autoDir, `${id}.json`),
      this.mediaFile(`${id}.mp4`),
      this.mediaFile(`${id}.jpg`),
    ])
      rmSync(f, { force: true });
    this.feed.shown = this.feed.shown.filter((x) => x !== id);
    this.feed.saved = this.feed.saved.filter((x) => x !== id);
    this.feed.dismissed = this.feed.dismissed.filter((x) => x !== id);
    this.saveFeed();
    this.signals.gone[id] = {
      ...(typeof p.yt === "string" ? { yt: p.yt } : {}),
      at: this.now(),
      why,
    };
  }

  // ── Rückmeldungen ─────────────────────────────────────────────────────────

  /** Stück in die Bibliothek legen oder wieder herausnehmen (»Merken« = besonders gut). */
  setSaved(id: string, on: boolean): void {
    if (!ID_RE.test(id)) return;
    const rest = this.feed.saved.filter((x) => x !== id);
    this.feed.saved = on ? [id, ...rest] : rest;
    this.saveFeed();
  }

  /**
   * Bewerten: »up« = gut, null = Bewertung zurücknehmen, »down« = nicht gut — dann wird das
   * Stück samt Video gelöscht, und der nächste Lauf meidet Ähnliches (Gründe optional).
   */
  rate(id: string, rating: unknown, reasons: unknown): "ok" | "not_found" | "bad_request" {
    if (rating !== "up" && rating !== "down" && rating !== null) return "bad_request";
    const p = this.piece(id);
    if (!p) return "not_found";
    const m = this.mark(id, p);
    if (rating === null) {
      m.rating = undefined;
      m.reasons = undefined;
    } else {
      m.rating = rating;
      m.ratedAt = this.now();
      if (rating === "down") m.reasons = normalizeReasons(reasons);
      else m.reasons = undefined;
    }
    if (rating === "down") this.erase(id, p, "down");
    this.saveSignals();
    return "ok";
  }

  /** Löschen ohne Bewertung: Stück und Video weg; als Geschmackssignal zählt das nicht. */
  remove(id: string): "ok" | "not_found" {
    const p = this.piece(id);
    if (!p) return "not_found";
    this.erase(id, p, "delete");
    this.saveSignals();
    return "ok";
  }

  /** Verhalten: weggeklickt (unter 20 s), angesehen, durchgearbeitet; dazu die Selbsteinschätzung. */
  signal(id: string, kind: unknown, understood: unknown): "ok" | "not_found" | "bad_request" {
    if (kind !== "skip" && kind !== "watched" && kind !== "worked") return "bad_request";
    if (
      understood !== undefined &&
      understood !== null &&
      ![1, 2, 3].includes(understood as number)
    )
      return "bad_request";
    const p = this.piece(id);
    if (!p) return "not_found";
    const m = this.mark(id, p);
    if (kind === "skip") m.skips = (m.skips ?? 0) + 1;
    if (kind === "watched") m.watched = (m.watched ?? 0) + 1;
    if (kind === "worked") m.worked = (m.worked ?? 0) + 1;
    if (understood === 1 || understood === 2 || understood === 3) m.understood = understood;
    m.seenAt = this.now();
    this.saveSignals();
    return "ok";
  }

  /** Themenvorschlag annehmen (gesucht ab dem nächsten Lauf) oder ablehnen (kommt nicht wieder). */
  answerSuggestion(id: string, accept: boolean): "ok" | "not_found" | "full" {
    const s = this.suggestions().find((x) => x.id === id);
    if (!s) return "not_found";
    if (accept) {
      const wishes = this.interests();
      const list = s.kind === "song" ? wishes.music : wishes.video;
      if (list.length >= 12) return "full";
      list.push(s.topic);
      this.setInterests(wishes);
      this.signals.topics.accepted = [...new Set([...this.signals.topics.accepted, id])];
    } else {
      this.signals.topics.declined = [...new Set([...this.signals.topics.declined, id])];
    }
    this.saveSignals();
    return "ok";
  }

  // ── Geschmack ─────────────────────────────────────────────────────────────

  /** Alle Beobachtungen: jedes vorhandene Stück, dazu gelöschte, zu denen es Signale gab. */
  observations(pieces: ErlebenPiece[] = this.pieces()): TasteObs[] {
    const saved = new Set(this.feed.saved);
    const out: TasteObs[] = [];
    const seen = new Set<string>();
    const obs = (id: string, info: NonNullable<Mark["info"]>, m: Mark | undefined): TasteObs => ({
      kind: info.kind,
      topic: info.topic,
      channel: info.channel,
      by: info.by,
      level: info.level,
      rating: m?.rating ?? null,
      reasons: m?.reasons ?? [],
      saved: saved.has(id),
      skips: m?.skips ?? 0,
      watched: m?.watched ?? 0,
      worked: m?.worked ?? 0,
      ...(m?.understood ? { understood: m.understood } : {}),
    });
    for (const p of pieces) {
      seen.add(p.id);
      out.push(obs(p.id, this.infoOf(p), this.signals.marks[p.id]));
    }
    for (const [id, m] of Object.entries(this.signals.marks))
      if (!seen.has(id) && m.info) out.push(obs(id, m.info, m));
    return out;
  }

  taste(obs: TasteObs[] = this.observations()): Taste {
    return computeTaste(obs, this.interests());
  }

  suggestions(obs: TasteObs[] = this.observations()): TopicSuggestion[] {
    if (!this.deps.suggest) return suggestTopics(obs, this.interests(), this.signals.topics.declined);
    const wishes = this.interests();
    const have = new Set([
      ...wishes.video.map((t) => suggestionId("scene", t)),
      ...wishes.music.map((t) => suggestionId("song", t)),
    ]);
    const done = new Set([...this.signals.topics.declined, ...this.signals.topics.accepted]);
    return (this.signals.topics.proposed ?? []).filter((x) => !done.has(x.id) && !have.has(x.id)).slice(0, 3);
  }

  /** Einmal je Lauf: neue Themen aus dem, was gefallen hat (mindestens zwei Stücke mit Pluspunkten). */
  private async proposeTopics(): Promise<void> {
    if (!this.deps.suggest) return;
    const pieces = new Map(this.pieces().map((p) => [p.id, p]));
    const liked = Object.entries(this.signals.marks)
      .filter(([id, m]) => m.rating !== "down" && (this.feed.saved.includes(id) || m.rating === "up" || (m.worked ?? 0) > 0))
      .map(([id, m]) => {
        const p = pieces.get(id) as (ErlebenPiece & { titleDe?: string }) | undefined;
        const info = m.info;
        return { kind: (info?.kind ?? p?.kind ?? "scene") as PieceKind, title: String(p?.titleDe ?? p?.title ?? info?.topic ?? "").slice(0, 120), topic: String(info?.topic ?? p?.topic ?? "") };
      });
    for (const id of this.feed.saved)
      if (!this.signals.marks[id] && pieces.get(id)) {
        const p = pieces.get(id) as ErlebenPiece & { titleDe?: string };
        liked.push({ kind: p.kind as PieceKind, title: String(p.titleDe ?? p.title ?? "").slice(0, 120), topic: String(p.topic ?? "") });
      }
    if (liked.length < 2) return;
    try {
      const ideas = await this.deps.suggest({ liked: liked.slice(0, 20), current: this.interests(), declined: this.signals.topics.declined });
      this.signals.topics.proposed = ideas
        .filter((x) => x.topic.trim())
        .slice(0, 3)
        .map((x) => ({ id: suggestionId(x.kind, x.topic.trim()), kind: x.kind, topic: x.topic.trim().slice(0, 60), why: x.why.trim().slice(0, 120) }));
      this.saveSignals();
    } catch (e) {
      this.deps.log?.(`erleben: Themenvorschläge nicht möglich: ${String(e).slice(0, 80)}`);
    }
  }

  // ── Feed ──────────────────────────────────────────────────────────────────

  /**
   * Der Feed, wie die App ihn sieht. Mit `keep` werden die gezeigten Stücke ausgetauscht —
   * bis auf die genannten (angefangene Stücke bleiben stehen). Aufbereitet wird dabei nie.
   */
  state(keep: string[] | null = null): ErlebenFeedState {
    const files = this.files();
    const all = files.map(({ p }) => p);
    const ids = new Set(all.map((p) => p.id));
    if (keep) {
      const gone = this.feed.shown.filter((id) => !keep.includes(id));
      this.feed.dismissed = [...new Set([...this.feed.dismissed, ...gone])];
      this.feed.shown = this.feed.shown.filter((id) => keep.includes(id));
    }
    this.feed.shown = this.feed.shown.filter((id) => ids.has(id));
    const ready = () =>
      all.filter((p) => !this.feed.shown.includes(p.id) && !this.feed.dismissed.includes(p.id));
    for (const p of ready()) {
      if (this.feed.shown.length >= FEED_SIZE) break;
      this.feed.shown.push(p.id);
    }
    this.saveFeed();
    const now = this.now();
    const todayIds = this.freshIds().filter((id) => ids.has(id));
    const fresh = new Set(todayIds);
    const saved = new Set(this.feed.saved);
    const collection: CollectionItem[] = files
      .map(({ p, at }) => {
        const m = this.signals.marks[p.id];
        const info = this.infoOf(p);
        return {
          id: p.id,
          yt: info.yt ?? null,
          kind: info.kind,
          topic: info.topic,
          title: str(p.title),
          titleDe: str(p.titleDe),
          by: info.by,
          channel: str(p.channel),
          level: info.level,
          dur: typeof p.dur === "number" ? p.dur : 0,
          poster: str(p.poster) || null,
          media: str(p.media) || null,
          addedAt: Math.round(at),
          isNew: fresh.has(p.id),
          saved: saved.has(p.id),
          rating: m?.rating === "up" ? ("up" as const) : null,
          watched: m?.watched ?? 0,
          worked: m?.worked ?? 0,
          skips: m?.skips ?? 0,
          understood: m?.understood ?? null,
        };
      })
      .reverse();
    // Einmal alle Stücke lesen, nicht je Auswertung neu — die Sammlung wächst täglich.
    const obs = this.observations(all);
    const taste = this.taste(obs);
    return {
      shown: this.feed.shown,
      saved: this.feed.saved,
      ready: ready().length,
      busy: this.busy,
      pieces: all,
      interests: this.interests(),
      leftToday: this.leftToday(),
      resetAt: quotaResetAt(now),
      collection,
      todayIds,
      todayCount: todayIds.length,
      lastRunAt: this.lastRunAt,
      nextRunAt: nextRunAt(now, this.lastRunAt),
      lastRun: this.lastRun,
      suggestions: this.suggestions(obs),
      taste: {
        levelTarget: taste.levelTarget,
        blockedChannels: taste.blockedChannels,
        topics: taste.topics,
      },
    };
  }

  private spawnPrepare(count: number): Promise<PrepareResult> {
    return new Promise((resolve) => {
      let child: ChildProcess;
      try {
        child = spawn(
          resolveUv(),
          // --plan: Themenfolge, gesperrte Kanäle und Schwierigkeit aus dem Geschmack.
          [
            "run",
            "--script",
            this.deps.script,
            "next",
            "--count",
            String(count),
            "--plan",
            this.planFile,
          ],
          {
            stdio: ["ignore", "pipe", "pipe"],
            env: { ...process.env, PATH: `/opt/homebrew/bin:${process.env.PATH ?? ""}` },
          },
        );
      } catch (e) {
        this.deps.log?.(`erleben: Aufbereitung startet nicht: ${String(e)}`);
        resolve(0);
        return;
      }
      let out = "";
      child.stdout?.on("data", (d) => {
        out += String(d);
      });
      child.stderr?.on("data", (d) => {
        for (const line of String(d).split("\n"))
          if (/^(\[|Thema|Kein Kontingent|Prüfmodelle|Tageskontingent|Plan| {6}|gemini-)/.test(line))
            this.deps.log?.(`erleben ${line}`);
      });
      child.on("error", (e) => {
        this.deps.log?.(`erleben: Aufbereitung startet nicht: ${e.message}`);
        resolve(0);
      });
      child.on("exit", () => {
        try {
          const last = out.trim().split("\n").pop() ?? "{}";
          const r = JSON.parse(last) as { prepared?: number; busy?: boolean };
          resolve({ prepared: Number(r.prepared ?? 0), busy: !!r.busy });
        } catch {
          resolve(0);
        }
      });
    });
  }
}

const MEDIA_TYPES: Record<string, string> = { mp4: "video/mp4", jpg: "image/jpeg" };
const STATUS = { not_found: 404, bad_request: 400, full: 409 } as const;

export async function registerRussianErlebenRoutes(
  app: FastifyInstance,
  deps: ErlebenDeps,
): Promise<ErlebenFeed> {
  const feed = new ErlebenFeed(deps);
  if (deps.schedule !== false) {
    feed.start();
    app.addHook("onClose", async () => feed.stop());
  }

  app.get("/russian/erleben/feed", async () => feed.state());

  /** Für ältere App-Versionen: tauscht die gezeigten Stücke nur gegen schon aufbereitete aus. */
  app.post<{ Body: { keep?: unknown } }>("/russian/erleben/refresh", async (req) => {
    const keep = Array.isArray(req.body?.keep) ? req.body.keep.map(String) : [];
    return feed.state(keep);
  });

  app.post<{ Body: { id?: unknown; on?: unknown } }>("/russian/erleben/save", async (req) => {
    feed.setSaved(String(req.body?.id ?? ""), req.body?.on !== false);
    return feed.state();
  });

  app.post<{ Body: unknown }>("/russian/erleben/interests", async (req) => {
    feed.setInterests(req.body);
    return feed.state();
  });

  /** {id, rating: 'up'|'down'|null, reasons?} — »down« löscht das Stück und meidet Ähnliches. */
  app.post<{ Body: { id?: unknown; rating?: unknown; reasons?: unknown } }>(
    "/russian/erleben/rate",
    async (req, reply) => {
      const r = feed.rate(String(req.body?.id ?? ""), req.body?.rating ?? null, req.body?.reasons);
      if (r !== "ok") return reply.code(STATUS[r]).send({ error: r });
      return feed.state();
    },
  );

  /** {id} — löschen ohne Bewertung. */
  app.post<{ Body: { id?: unknown } }>("/russian/erleben/delete", async (req, reply) => {
    const r = feed.remove(String(req.body?.id ?? ""));
    if (r !== "ok") return reply.code(STATUS[r]).send({ error: r });
    return feed.state();
  });

  /** {id, kind: 'skip'|'watched'|'worked', understood?: 1|2|3} — antwortet knapp, kommt oft. */
  app.post<{ Body: { id?: unknown; kind?: unknown; understood?: unknown } }>(
    "/russian/erleben/signal",
    async (req, reply) => {
      const r = feed.signal(String(req.body?.id ?? ""), req.body?.kind, req.body?.understood);
      if (r !== "ok") return reply.code(STATUS[r]).send({ error: r });
      return { ok: true };
    },
  );

  /** {id, accept: boolean} — Themenvorschlag annehmen oder ablehnen. */
  app.post<{ Body: { id?: unknown; accept?: unknown } }>(
    "/russian/erleben/suggestion",
    async (req, reply) => {
      const r = feed.answerSuggestion(String(req.body?.id ?? ""), req.body?.accept === true);
      if (r !== "ok") return reply.code(STATUS[r]).send({ error: r });
      return feed.state();
    },
  );

  /** Video oder Standbild eines Stücks — mit Teilabruf, damit die App zeilengenau springen kann. */
  app.get<{ Params: { file: string } }>("/russian/erleben/media/:file", async (req, reply) => {
    const m = /^([a-z0-9_]{1,60})\.(mp4|jpg)$/.exec(req.params.file);
    if (!m) return reply.code(400).send({ error: "file" });
    const file = feed.mediaFile(req.params.file);
    if (!existsSync(file)) return reply.code(404).send({ error: "not_found" });
    const size = statSync(file).size;
    const type = MEDIA_TYPES[m[2] ?? ""] ?? "application/octet-stream";
    reply.header("accept-ranges", "bytes").header("cache-control", "public, max-age=3600");
    const range = /^bytes=(\d*)-(\d*)$/.exec(req.headers.range ?? "");
    if (range && (range[1] || range[2])) {
      const start = range[1] ? Number(range[1]) : Math.max(0, size - Number(range[2]));
      const end = range[1] && range[2] ? Math.min(Number(range[2]), size - 1) : size - 1;
      if (start > end || start >= size)
        return reply.code(416).header("content-range", `bytes */${size}`).send();
      return reply
        .code(206)
        .type(type)
        .header("content-range", `bytes ${start}-${end}/${size}`)
        .header("content-length", end - start + 1)
        .send(createReadStream(file, { start, end }));
    }
    return reply.type(type).header("content-length", size).send(createReadStream(file));
  });
  return feed;
}
