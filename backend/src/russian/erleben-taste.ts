/**
 * Geschmack für »Lieder & Szenen«: was der tägliche Lauf aus den Rückmeldungen des Lernenden lernt.
 *
 * Reine Rechenlogik ohne Dateien und ohne Uhr — der Feed (api/russian-erleben.ts) sammelt die
 * Beobachtungen, dieses Modul macht daraus
 *  - Gewichte je Thema (gut/gemerkt/durchgearbeitet heben, »langweilig« und Wegklicken senken),
 *  - gesperrte Kanäle (zweimal »nicht gut« vom selben Kanal oder einmal »kaum Russisch«),
 *  - ein Schwierigkeitsziel (»zu schwer«/»zu leicht« und die Selbsteinschätzung verschieben es),
 *  - den Plan eines Laufs: etwa vier von fünf Plätzen folgen dem Geschmack, jeder fünfte
 *    probiert bewusst etwas, das bisher kaum vorkam,
 *  - Themenvorschläge aus dem, was gefällt (ohne Modell: Interpreten und Kanäle gemochter Stücke).
 */

export type PieceKind = "scene" | "song";
export type Rating = "up" | "down";

/** Gründe für »nicht gut« — in der App großgeschrieben, hier einheitlich klein. */
export const DOWN_REASONS = [
  "zu schwer",
  "zu leicht",
  "langweilig",
  "schlechter ton",
  "kaum russisch",
] as const;
export type DownReason = (typeof DOWN_REASONS)[number];

/** Was über ein Stück bekannt ist — auch über gelöschte, solange es ein Signal gab. */
export interface TasteObs {
  kind: PieceKind;
  topic: string;
  /** YouTube-Kanal; bei alten Stücken ohne Kanal der Interpret/Urheber (`by`). */
  channel: string;
  /** Interpret bzw. Urheber, wie er dem Lernenden gezeigt wird. */
  by?: string;
  level?: string;
  rating?: Rating | null;
  reasons?: DownReason[];
  saved?: boolean;
  skips?: number;
  watched?: number;
  worked?: number;
  /** Selbsteinschätzung: 1 = wenig, 2 = die Hälfte, 3 = fast alles verstanden. */
  understood?: 1 | 2 | 3;
}

export interface TopicWishes {
  video: string[];
  music: string[];
}

export interface TopicWeight {
  kind: PieceKind;
  topic: string;
  weight: number;
  /** Wie viele Stücke es zu dem Thema schon gab — wenig = noch unerforscht. */
  exposure: number;
}

export interface Taste {
  topics: TopicWeight[];
  blockedChannels: string[];
  /** 0 = Einsteiger, 1 = Mittel, 2 = Anspruchsvoll. */
  levelTarget: number;
  /** Ob es überhaupt Hinweise zur Schwierigkeit gab (sonst bleibt das Ziel neutral bei 1). */
  levelKnown: boolean;
  /** Gewicht leichter Wörter bei der Wahl des Ausschnitts in prepare.py (1 = wie bisher). */
  easyWeight: number;
}

export interface PlanSlot {
  kind: PieceKind;
  topic: string;
  explore: boolean;
}

export interface TopicSuggestion {
  /** Feste Kennung, mit der die App den Vorschlag annimmt oder ablehnt. */
  id: string;
  kind: PieceKind;
  topic: string;
  why: string;
}

const LEVELS: Record<string, number> = { Einsteiger: 0, Mittel: 1, Anspruchsvoll: 2 };
/** Jeder wievielte Platz eines Laufs bewusst etwas Neues probiert. */
export const EXPLORE_EVERY = 5;
const MAX_WEIGHT = 6;
const MIN_WEIGHT = 0.1;

export function levelScore(level: unknown): number | null {
  return typeof level === "string" && level in LEVELS ? (LEVELS[level] ?? null) : null;
}

/** Kanalnamen vergleichbar machen: klein, ohne Rand und doppelte Leerzeichen. */
export function channelKey(name: unknown): string {
  return String(name ?? "")
    .trim()
    .toLowerCase()
    .replace(/\s+/g, " ");
}

/** Gründe säubern: nur bekannte, ohne Doppelte, Groß-/Kleinschreibung egal. */
export function normalizeReasons(raw: unknown): DownReason[] {
  if (!Array.isArray(raw)) return [];
  const out = new Set<DownReason>();
  for (const r of raw) {
    const k = channelKey(r) as DownReason;
    if ((DOWN_REASONS as readonly string[]).includes(k)) out.add(k);
  }
  return [...out];
}

const topicKey = (kind: PieceKind, topic: string) => `${kind}:${topic.trim().toLowerCase()}`;

/** Wie sehr ein einzelnes Stück für sein Thema spricht (positiv) oder dagegen (negativ). */
export function pieceSignal(o: TasteObs): number {
  let s = 0;
  if (o.saved)
    s += 2; // gemerkt = besonders gut, allein oder zusammen mit »gut«
  else if (o.rating === "up") s += 1;
  if (o.rating === "down") s -= o.reasons?.includes("langweilig") ? 1.5 : 0.5;
  if ((o.worked ?? 0) > 0) s += 0.5;
  else if ((o.watched ?? 0) > 0) s += 0.15;
  // Wegklicken zählt nur, wenn das Stück nie angesehen wurde.
  if (!o.worked && !o.watched) s -= 0.3 * Math.min(3, o.skips ?? 0);
  return s;
}

/** Schwierigkeitsziel aus »zu schwer/zu leicht« und Selbsteinschätzung; neutral 1 ohne Hinweise. */
function levelTarget(obs: TasteObs[]): { target: number; known: boolean } {
  let sum = 0;
  let weight = 0;
  for (const o of obs) {
    const l = levelScore(o.level);
    if (l === null) continue;
    const add = (wish: number, w: number) => {
      sum += wish * w;
      weight += w;
    };
    if (o.reasons?.includes("zu schwer")) add(l - 1, 2);
    if (o.reasons?.includes("zu leicht")) add(l + 1, 2);
    if (o.understood === 1) add(l - 0.75, 1);
    else if (o.understood === 2) add(l, 1);
    else if (o.understood === 3) add(l + 0.5, 1);
    if (o.rating === "up" || o.saved) add(l, 0.5);
  }
  if (weight === 0) return { target: 1, known: false };
  return { target: Math.min(2, Math.max(0, sum / weight)), known: true };
}

/** Kanäle, die der Lauf nicht mehr nimmt. */
export function blockedChannels(obs: TasteObs[]): string[] {
  const downs = new Map<string, number>();
  const blocked = new Set<string>();
  for (const o of obs) {
    const ch = channelKey(o.channel);
    if (!ch || o.rating !== "down") continue;
    downs.set(ch, (downs.get(ch) ?? 0) + 1);
    if (o.reasons?.includes("kaum russisch") || (downs.get(ch) ?? 0) >= 2) blocked.add(ch);
  }
  return [...blocked].sort();
}

export function computeTaste(obs: TasteObs[], wishes: TopicWishes): Taste {
  const { target, known } = levelTarget(obs);
  const byTopic = new Map<string, TasteObs[]>();
  for (const o of obs) {
    if (!o.topic) continue;
    const k = topicKey(o.kind, o.topic);
    byTopic.set(k, [...(byTopic.get(k) ?? []), o]);
  }
  const topics: TopicWeight[] = [];
  const add = (kind: PieceKind, topic: string) => {
    const list = byTopic.get(topicKey(kind, topic)) ?? [];
    let weight = 1 + list.reduce((n, o) => n + pieceSignal(o), 0) * 0.5;
    // Themen, deren Stücke nah am Schwierigkeitsziel lagen, kommen etwas öfter dran.
    const levels = list.map((o) => levelScore(o.level)).filter((l): l is number => l !== null);
    if (known && levels.length) {
      const avg = levels.reduce((a, b) => a + b, 0) / levels.length;
      weight *= 1 / (1 + 0.5 * Math.abs(avg - target));
    }
    weight = Math.min(MAX_WEIGHT, Math.max(MIN_WEIGHT, weight));
    topics.push({ kind, topic, weight: Math.round(weight * 1000) / 1000, exposure: list.length });
  };
  for (const t of wishes.video) add("scene", t);
  for (const t of wishes.music) add("song", t);
  return {
    topics,
    blockedChannels: blockedChannels(obs),
    levelTarget: Math.round(target * 100) / 100,
    levelKnown: known,
    // Ziel leicht → leichte Wörter zählen bei der Wahl des Ausschnitts doppelt, Ziel schwer → halb.
    easyWeight: Math.round((1 + (1 - target) * (target < 1 ? 1 : 0.5)) * 100) / 100,
  };
}

/** Kleiner, fester Zufall (mulberry32) — damit ein Plan nachvollziehbar und testbar bleibt. */
export function seededRng(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/**
 * Plan eines Laufs: Reihenfolge der Themen, aus der prepare.py Kandidaten sucht. Platz 5, 10, 15 …
 * ist ein Erkundungsplatz — dort kommt das am wenigsten erprobte Thema dran (ganz frisch
 * angenommene Vorschläge zuerst); die übrigen Plätze ziehen nach Gewicht.
 */
export function planRun(taste: Taste, slots: number, rng: () => number): PlanSlot[] {
  const topics = taste.topics;
  if (topics.length === 0) return [];
  const total = topics.reduce((n, t) => n + t.weight, 0);
  const draw = (): TopicWeight => {
    let r = rng() * total;
    for (const t of topics) {
      r -= t.weight;
      if (r < 0) return t;
    }
    return topics[topics.length - 1] as TopicWeight;
  };
  // Erkunden: nur Themen, die nicht schon klar abgelehnt sind; das Lieblingsthema nur, wenn es
  // das einzige ist.
  const best = Math.max(...topics.map((t) => t.weight));
  let pool = topics.filter((t) => t.weight >= 0.5 && (t.weight < best || topics.length === 1));
  if (pool.length === 0) pool = topics;
  const explore = (): TopicWeight => {
    const least = Math.min(...pool.map((t) => t.exposure));
    const fresh = pool.filter((t) => t.exposure === least);
    const pick = fresh[Math.floor(rng() * fresh.length)] as TopicWeight;
    pick.exposure += 1; // im selben Lauf beim nächsten Mal ein anderes Thema
    return pick;
  };
  pool = pool.map((t) => ({ ...t }));
  const out: PlanSlot[] = [];
  for (let i = 0; i < slots; i++) {
    const isExplore = i % EXPLORE_EVERY === EXPLORE_EVERY - 1;
    const t = isExplore ? explore() : draw();
    out.push({ kind: t.kind, topic: t.topic, explore: isExplore });
  }
  return out;
}

/**
 * Neue Themen vorschlagen — ohne Modell: Interpreten (Lieder) und Kanäle (Videos), von denen
 * mindestens zwei Pluspunkte kamen (einmal gemerkt oder zweimal »gut«/durchgearbeitet).
 * Was schon Thema ist, abgelehnt wurde oder gesperrt ist, wird nicht vorgeschlagen.
 */
export function suggestTopics(
  obs: TasteObs[],
  wishes: TopicWishes,
  declined: string[],
  limit = 3,
): TopicSuggestion[] {
  const have = new Set([
    ...wishes.video.map((t) => topicKey("scene", t)),
    ...wishes.music.map((t) => topicKey("song", t)),
  ]);
  const no = new Set(declined);
  const blocked = new Set(blockedChannels(obs));
  const score = new Map<string, { kind: PieceKind; topic: string; n: number; pieces: number }>();
  for (const o of obs) {
    if (o.rating === "down") continue;
    const plus = (o.saved ? 2 : o.rating === "up" ? 1 : 0) + ((o.worked ?? 0) > 0 ? 1 : 0);
    if (plus === 0) continue;
    const name = (o.kind === "song" ? o.by || o.channel : o.channel || o.by || "").trim();
    if (!name || blocked.has(channelKey(o.channel)) || blocked.has(channelKey(name))) continue;
    const id = topicKey(o.kind, name);
    const cur = score.get(id) ?? { kind: o.kind, topic: name.slice(0, 60), n: 0, pieces: 0 };
    cur.n += plus;
    cur.pieces += 1;
    score.set(id, cur);
  }
  return [...score.entries()]
    .filter(([id, s]) => s.n >= 2 && !have.has(id) && !no.has(id))
    .sort((a, b) => b[1].n - a[1].n || a[0].localeCompare(b[0]))
    .slice(0, limit)
    .map(([id, s]) => ({
      id,
      kind: s.kind,
      topic: s.topic,
      why:
        s.kind === "song"
          ? `${s.pieces === 1 ? "Ein Lied" : `${s.pieces} Lieder`} von ${s.topic} ${s.pieces === 1 ? "hat" : "haben"} dir gefallen`
          : `${s.pieces === 1 ? "Ein Video" : `${s.pieces} Videos`} von ${s.topic} ${s.pieces === 1 ? "hat" : "haben"} dir gefallen`,
    }));
}

export function suggestionId(kind: PieceKind, topic: string): string {
  return topicKey(kind, topic);
}
