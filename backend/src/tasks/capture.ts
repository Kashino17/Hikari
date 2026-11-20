import { join } from "node:path";
import type { ParseContext, ParsedTask } from "./parse.js";
import { isoDay } from "./plan.js";

/**
 * Was Whisper in Stille gern erfindet (Abspann-Floskeln aus Untertitel-Trainingsdaten).
 * Am 21.11. wurde aus einer stillen Aufnahme »Transcribed by ESO · Translated by —« — zwei Scheinaufgaben.
 */
const SILENCE =
  /(transcribed|translated|subtitles?|captions?) by|untertitel|amara\.org|copyright|vielen dank (fürs|für das|für's) zu(schauen|hören)|thanks? for watching|bis zum nächsten mal|^[\s.,!?…—-]*$/i;

/** Erfundene Sätze raus; was übrig bleibt, ist das Gesagte (kann leer sein). */
export function cleanSpoken(text: string): string {
  return text
    .split(/(?<=[.!?…])\s+|\s+[·—-]\s+/)
    .map((x) => x.trim())
    .filter((x) => x.length > 0 && !SILENCE.test(x))
    .join(" ")
    .trim();
}
import type { Capture, TaskStore } from "./store.js";

export interface CaptureDeps {
  store: TaskStore;
  transcribe: (path: string) => Promise<{ text: string }>;
  parse: (text: string, ctx: ParseContext) => Promise<ParsedTask[]>;
  fallback: (text: string, ctx: ParseContext) => ParsedTask[];
  log?: (msg: string, extra?: Record<string, unknown>) => void;
  today?: () => string;
}

/** Legt die erkannten Aufgaben an; große Sachen mit Schritten werden Vorhaben. */
export function applyParsed(store: TaskStore, parsed: ParsedTask[], c: Capture) {
  const created: string[] = [];
  const createdProjects: string[] = [];
  for (const p of parsed) {
    const base = { area: p.area, stakes: p.stakes, source: c.source, captureId: c.id };
    if (p.steps.length >= 2) {
      const proj = store.addProject({
        title: p.project && !store.project(p.project) ? p.project : p.title,
        area: p.area,
        deadline: p.due,
        goal: p.note ?? "Frist",
        origin: c.text ?? p.title,
      });
      createdProjects.push(proj.id);
      for (const s of p.steps) {
        const t = store.addTask({
          ...base,
          title: s.title,
          kind: s.kind,
          minutes: s.minutes,
          project: proj.id,
        });
        proj.steps.push({ task: t.id });
        created.push(t.id);
      }
      continue;
    }
    const existing = p.project ? store.project(p.project) : undefined;
    const t = store.addTask({
      ...base,
      title: p.title,
      kind: p.kind,
      minutes: p.minutes,
      due: existing ? null : p.due,
      note: p.note,
      project: existing?.id ?? null,
    });
    if (existing) store.addStep(existing.id, t.id);
    created.push(t.id);
  }
  store.save();
  return { created, createdProjects };
}

/**
 * Arbeitet Aufnahmen nacheinander ab: Whisper schreibt mit, Qwen zerlegt.
 * Ist LM Studio nicht erreichbar, sortieren einfache Regeln — die Aufgabe
 * soll nie verloren gehen, nur weil das Modell gerade fehlt.
 */
export class CaptureProcessor {
  private queue: string[] = [];
  private running = false;

  constructor(private readonly deps: CaptureDeps) {}

  get busy() {
    return this.running;
  }

  enqueue(id: string) {
    if (!this.queue.includes(id)) this.queue.push(id);
    void this.pump();
  }

  /** Nach einem Neustart liegengebliebene Aufnahmen weiterverarbeiten. */
  recover(): number {
    const open = this.deps.store
      .captures()
      .filter((c) => c.status !== "done" && c.status !== "failed");
    for (const c of open) this.enqueue(c.id);
    return open.length;
  }

  private async pump() {
    if (this.running) return;
    this.running = true;
    try {
      while (this.queue.length) {
        const id = this.queue.shift();
        if (!id) break;
        const c = this.deps.store.capture(id);
        if (c) await this.run(c);
      }
    } finally {
      this.running = false;
    }
  }

  private async run(c: Capture) {
    const { store } = this.deps;
    const started = Date.now();
    try {
      let text = c.text ?? "";
      if (c.file) {
        store.setCapture(c, { status: "transcribing" });
        text = cleanSpoken((await this.deps.transcribe(join(store.captureDir, c.file))).text);
        store.setCapture(c, { text });
      }
      if (!text) {
        store.setCapture(c, { status: "failed", error: "nichts_verstanden" });
        return;
      }
      store.setCapture(c, { status: "sorting" });
      const ctx: ParseContext = {
        today: this.deps.today?.() ?? isoDay(),
        projects: store
          .state()
          .projects.filter((p) => p.steps.some((s) => "task" in s && !store.task(s.task)?.done))
          .map((p) => ({ id: p.id, title: p.title })),
      };
      let parsed: ParsedTask[];
      let fallback = false;
      try {
        parsed = await this.deps.parse(text, ctx);
      } catch (err) {
        this.deps.log?.("tasks: Qwen nicht verfügbar, einfache Regeln", { err: String(err) });
        parsed = this.deps.fallback(text, ctx);
        fallback = true;
      }
      // Leer heißt: Qwen hat keine Aufgabe gefunden — dann auch keine anlegen.
      const res = applyParsed(store, parsed, c);
      store.setCapture(c, { status: "done", fallback, ...res });
      this.deps.log?.("tasks: Aufnahme sortiert", {
        id: c.id,
        tasks: res.created.length,
        ms: Date.now() - started,
      });
    } catch (err) {
      store.setCapture(c, {
        status: "failed",
        error: String((err as Error).message ?? err).slice(0, 200),
      });
      this.deps.log?.("tasks: Aufnahme fehlgeschlagen", { id: c.id, err: String(err) });
    }
  }
}
