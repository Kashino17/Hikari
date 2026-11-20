import { randomUUID } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, renameSync, rmSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import {
  AREAS,
  type Area,
  KINDS,
  type Kind,
  type Project,
  type Task,
  type TaskState,
} from "./plan.js";

export type CaptureStatus = "queued" | "transcribing" | "sorting" | "done" | "failed";

/** Eine Aufnahme oder ein Text, aus dem Hikari Aufgaben macht. */
export interface Capture {
  id: string;
  createdAt: number;
  updatedAt: number;
  status: CaptureStatus;
  source: Task["source"];
  /** Tondatei im Ordner captures/ (null bei Text). */
  file: string | null;
  /** Getippter Text oder das Transkript. */
  text: string | null;
  /** Angelegte Aufgaben. */
  created: string[];
  /** Neu angelegte Vorhaben (für Rückgängig). */
  createdProjects: string[];
  /** true = LM Studio war nicht erreichbar, einfache Regeln haben sortiert. */
  fallback: boolean;
  error: string | null;
}

interface FileShape extends TaskState {
  version: 1;
  captures: Capture[];
}

export interface NewTask {
  title: string;
  area?: Area;
  kind?: Kind;
  minutes?: number;
  due?: string | null;
  stakes?: number;
  note?: string | null;
  project?: string | null;
  wait?: string | null;
  source?: Task["source"];
  captureId?: string | null;
}

const DAY_RX = /^\d{4}-\d{2}-\d{2}$/;

export function cleanTask(x: NewTask, now = Date.now()): Task {
  return {
    id: `t_${randomUUID().slice(0, 12)}`,
    title: String(x.title).trim().slice(0, 200) || "Aufgabe",
    area: AREAS.includes(x.area as Area) ? (x.area as Area) : "haus",
    kind: KINDS.includes(x.kind as Kind) ? (x.kind as Kind) : "zuhause",
    minutes: Math.max(1, Math.min(600, Math.round(Number(x.minutes) || 10))),
    due: x.due && DAY_RX.test(x.due) ? x.due : null,
    stakes: Math.max(0, Math.min(3, Math.round(Number(x.stakes) || 0))),
    note: x.note?.trim() ? x.note.trim().slice(0, 120) : null,
    project: x.project ?? null,
    wait: x.wait?.trim() ? x.wait.trim().slice(0, 120) : null,
    createdAt: now,
    done: false,
    doneAt: null,
    holdUntil: null,
    later: 0,
    source: x.source ?? "app",
    captureId: x.captureId ?? null,
  };
}

/**
 * Alle Aufgaben in einer JSON-Datei (~/.hikari/tasks/tasks.json).
 * Ein Nutzer, wenige hundert Einträge: Datei statt Datenbank, atomar geschrieben.
 */
export class TaskStore {
  private data: FileShape;
  private readonly file: string;
  readonly captureDir: string;

  constructor(private readonly dir: string) {
    mkdirSync(dir, { recursive: true });
    this.captureDir = join(dir, "captures");
    mkdirSync(this.captureDir, { recursive: true });
    this.file = join(dir, "tasks.json");
    this.data = this.load();
  }

  private load(): FileShape {
    if (existsSync(this.file)) {
      try {
        const raw = JSON.parse(readFileSync(this.file, "utf8")) as Partial<FileShape>;
        return {
          version: 1,
          tasks: raw.tasks ?? [],
          projects: raw.projects ?? [],
          captures: raw.captures ?? [],
        };
      } catch {
        renameSync(this.file, `${this.file}.kaputt-${Date.now()}`);
      }
    }
    return { version: 1, tasks: [], projects: [], captures: [] };
  }

  save(): void {
    // Erledigtes älter als 60 Tage und alte Aufnahmen fallen raus.
    const cut = Date.now() - 60 * 86_400_000;
    this.data.tasks = this.data.tasks.filter(
      (t) => !t.done || (t.doneAt ?? 0) > cut || this.inProject(t.id),
    );
    this.data.captures = this.data.captures.slice(-60);
    const tmp = `${this.file}.tmp`;
    writeFileSync(tmp, JSON.stringify(this.data));
    renameSync(tmp, this.file);
  }

  private inProject(id: string) {
    return this.data.projects.some((p) => p.steps.some((s) => "task" in s && s.task === id));
  }

  state(): TaskState {
    return { tasks: this.data.tasks, projects: this.data.projects };
  }

  task(id: string): Task | undefined {
    return this.data.tasks.find((t) => t.id === id);
  }

  project(id: string): Project | undefined {
    return this.data.projects.find((p) => p.id === id);
  }

  addTask(x: NewTask): Task {
    const t = cleanTask(x);
    this.data.tasks.push(t);
    return t;
  }

  /** Neuen Schritt hinter den aktuellen Schritt des Vorhabens einsortieren (vor Termin/Wartendes). */
  addStep(pid: string, taskId: string): void {
    const p = this.project(pid);
    if (!p) return;
    let i = p.steps.findIndex((s) => {
      if ("task" in s) {
        const t = this.task(s.task);
        return !!t && !t.done && !!t.wait;
      }
      return !!s.fixed;
    });
    if (i < 0) i = p.steps.length;
    p.steps.splice(i, 0, { task: taskId });
  }

  addProject(x: Omit<Project, "id" | "createdAt" | "steps">): Project {
    const p: Project = {
      ...x,
      id: `p_${randomUUID().slice(0, 10)}`,
      createdAt: Date.now(),
      steps: [],
    };
    this.data.projects.push(p);
    return p;
  }

  updateTask(id: string, patch: Partial<Task>): Task | undefined {
    const t = this.task(id);
    if (!t) return undefined;
    const allowed: (keyof Task)[] = [
      "title",
      "area",
      "kind",
      "minutes",
      "due",
      "stakes",
      "note",
      "wait",
      "done",
      "doneAt",
      "holdUntil",
      "later",
    ];
    for (const k of allowed)
      if (k in patch) (t as unknown as Record<string, unknown>)[k] = patch[k];
    const c = cleanTask({ ...t });
    Object.assign(t, {
      ...c,
      id: t.id,
      createdAt: t.createdAt,
      done: !!t.done,
      doneAt: t.done ? (t.doneAt ?? Date.now()) : null,
      holdUntil: t.holdUntil && DAY_RX.test(t.holdUntil) ? t.holdUntil : null,
      later: Math.max(0, Number(t.later) || 0),
      source: t.source,
      captureId: t.captureId,
      project: t.project,
    });
    return t;
  }

  /** Aufgabe weg, auch aus ihrem Vorhaben; leere Vorhaben verschwinden mit. */
  deleteTask(id: string): boolean {
    const before = this.data.tasks.length;
    this.data.tasks = this.data.tasks.filter((t) => t.id !== id);
    for (const p of this.data.projects)
      p.steps = p.steps.filter((s) => !("task" in s) || s.task !== id);
    this.data.projects = this.data.projects.filter((p) => p.steps.some((s) => "task" in s));
    return this.data.tasks.length !== before;
  }

  deleteProject(id: string): void {
    const p = this.project(id);
    if (!p) return;
    const ids = new Set(p.steps.flatMap((s) => ("task" in s ? [s.task] : [])));
    this.data.tasks = this.data.tasks.filter((t) => !ids.has(t.id));
    this.data.projects = this.data.projects.filter((x) => x.id !== id);
  }

  // ---- Aufnahmen ----

  captures(): Capture[] {
    return this.data.captures;
  }

  capture(id: string): Capture | undefined {
    return this.data.captures.find((c) => c.id === id);
  }

  newCapture(
    source: Task["source"],
    text: string | null,
    audio: { data: Buffer; ext: string } | null,
  ): Capture {
    const id = `c_${randomUUID().slice(0, 12)}`;
    let file: string | null = null;
    if (audio) {
      file = `${id}${audio.ext}`;
      writeFileSync(join(this.captureDir, file), audio.data);
    }
    const c: Capture = {
      id,
      createdAt: Date.now(),
      updatedAt: Date.now(),
      status: "queued",
      source,
      file,
      text: text?.trim() || null,
      created: [],
      createdProjects: [],
      fallback: false,
      error: null,
    };
    this.data.captures.push(c);
    this.save();
    return c;
  }

  setCapture(c: Capture, patch: Partial<Capture>): void {
    Object.assign(c, patch, { updatedAt: Date.now() });
    if (c.status === "done" && c.file) {
      rmSync(join(this.captureDir, c.file), { force: true });
      c.file = null;
    }
    this.save();
  }
}
