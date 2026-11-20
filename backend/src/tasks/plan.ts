/**
 * Aufgaben: Datenmodell und die Regeln, nach denen Hikari die Reihenfolge setzt.
 * Kadir trägt nur ein; Priorität, „was passt in 15 Minuten“ und das
 * Morgen-Briefing kommen von hier. Die Web-Oberfläche (assets/tasks-web)
 * rechnet dieselben Regeln für sofortige Rückmeldung noch einmal nach.
 */

export const AREAS = ["amt", "geld", "job", "leute", "haus", "ich"] as const;
export type Area = (typeof AREAS)[number];
export const KINDS = ["rechner", "telefon", "unterwegs", "zuhause"] as const;
export type Kind = (typeof KINDS)[number];

export interface Task {
  id: string;
  title: string;
  area: Area;
  kind: Kind;
  minutes: number;
  /** Frist als lokales Datum JJJJ-MM-TT. */
  due: string | null;
  /** 0 = egal, 3 = Behörde/Bank/Geld hängt dran. */
  stakes: number;
  /** Warum es wichtig ist („Rücksendefrist“). */
  note: string | null;
  project: string | null;
  /** Gesetzt = wartet auf etwas Äußeres („erst wenn das Visum da ist“). */
  wait: string | null;
  createdAt: number;
  done: boolean;
  doneAt: number | null;
  /** Geschoben bis einschließlich zu diesem Tag (JJJJ-MM-TT). */
  holdUntil: string | null;
  /** Wie oft geschoben. */
  later: number;
  source: "voice" | "text" | "share" | "app";
  captureId: string | null;
}

export type Step = { task: string } | { title: string; done?: boolean; fixed?: string };

export interface Project {
  id: string;
  title: string;
  area: Area;
  /** Termin, auf den alles hinläuft. */
  deadline: string | null;
  /** Was am Termin passiert („Termin im Konsulat“). */
  goal: string;
  /** Der Satz, aus dem das Vorhaben entstand. */
  origin: string;
  steps: Step[];
  createdAt: number;
}

export interface TaskState {
  tasks: Task[];
  projects: Project[];
}

const DAY = 86_400_000;

/** Lokales Datum als JJJJ-MM-TT. */
export function isoDay(d: Date = new Date()): string {
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

function parseDay(s: string): number {
  const [y = 1970, m = 1, d = 1] = s.split("-").map(Number);
  return new Date(y, m - 1, d).getTime();
}

/** Tage von `today` bis `date` (negativ = vorbei). */
export function dayDiff(today: string, date: string): number {
  return Math.round((parseDay(date) - parseDay(today)) / DAY);
}

export function addDays(today: string, n: number): string {
  const d = new Date(parseDay(today));
  d.setDate(d.getDate() + n);
  return isoDay(d);
}

const byId = (s: TaskState, id: string) => s.tasks.find((t) => t.id === id);

/** Der eine Schritt eines Vorhabens, der gerade dran ist. */
export function projectNext(s: TaskState, pid: string): Task | null {
  const p = s.projects.find((x) => x.id === pid);
  if (!p) return null;
  for (const st of p.steps) {
    if ("task" in st) {
      const t = byId(s, st.task);
      if (!t) continue;
      if (!t.done && !t.wait) return t;
      if (!t.done) return null;
    } else if (!st.done && !st.fixed) return null;
  }
  return null;
}

/** Wartet auf etwas Äußeres oder auf einen früheren Schritt. */
export function blocked(s: TaskState, t: Task): boolean {
  if (t.wait) return true;
  return !!t.project && projectNext(s, t.project)?.id !== t.id;
}

/** Frist in Tagen; Vorhaben-Schritte erben eine Frist vom Termin des Vorhabens. */
export function effDue(s: TaskState, t: Task, today: string): number | null {
  if (t.due) return dayDiff(today, t.due);
  if (!t.project) return null;
  const p = s.projects.find((x) => x.id === t.project);
  if (!p?.deadline) return null;
  const left = p.steps.filter((st) => {
    if (!("task" in st)) return false;
    const x = byId(s, st.task);
    return !!x && !x.done && !x.wait;
  }).length;
  return Math.max(0, dayDiff(today, p.deadline) - (left - 1) * 2 - 1);
}

/** Frist ×60, Folgen ×24, Vorhaben-Schritt ×14, Wartezeit ×12. */
export function score(s: TaskState, t: Task, today: string, now = Date.now()): number {
  const d = effDue(s, t, today);
  const frist = d == null ? 0 : d <= 0 ? 1 : d === 1 ? 0.75 : d <= 3 ? 0.55 : d <= 7 ? 0.32 : 0.15;
  const ageDays = Math.max(0, (now - t.createdAt) / DAY);
  return (
    frist * 60 +
    (Math.min(3, t.stakes) / 3) * 24 +
    (t.project ? 14 : 0) +
    Math.min(1, ageDays / 21) * 12
  );
}

export function isHeld(t: Task, today: string): boolean {
  return !!t.holdUntil && dayDiff(today, t.holdUntil) >= 0;
}

/** Alles, was jetzt machbar ist, wichtigstes zuerst. */
export function openTasks(s: TaskState, today: string, now = Date.now()): Task[] {
  return s.tasks
    .filter((t) => !t.done && !blocked(s, t) && !isHeld(t, today))
    .map((t) => ({ t, sc: score(s, t, today, now) }))
    .sort((a, b) => b.sc - a.sc)
    .map((x) => x.t);
}

/** Bis zu `max` Aufgaben, die zusammen in `minutes` passen. */
export function fitFor(
  s: TaskState,
  today: string,
  minutes: number,
  max = 3,
  now = Date.now(),
): Task[] {
  const pick: Task[] = [];
  let left = minutes;
  for (const t of openTasks(s, today, now)) {
    if (pick.length >= max) break;
    if (t.minutes <= left) {
      pick.push(t);
      left -= t.minutes;
    }
  }
  return pick;
}

/** Morgen-Briefing: die wichtigsten Dinge des Tages, zusammen höchstens 90 Minuten. */
export function briefing(s: TaskState, today: string, now = Date.now()) {
  const items = fitFor(s, today, 90, 3, now);
  return {
    items: items.map((t) => ({ id: t.id, title: t.title, minutes: t.minutes, area: t.area })),
    minutes: items.reduce((n, t) => n + t.minutes, 0),
    open: openTasks(s, today, now).length,
  };
}
