/**
 * Progression wie bei Freeletics: Wer eine Übung beherrscht, steigt auf die nächste Variante um
 * (Liegestütz → Diamant → Pseudo-Planche); wer dauerhaft zu kämpfen hat, geht eine Stufe zurück.
 *
 * Grundlage: exercises.json »progress« (nächste, schwerere Variante). Die leichtere ist die Umkehrung.
 * Gespeichert wird nur »statt X nimm Y« (variant_map); Planer und KI-Plan lösen jede Übung darüber auf.
 * Das Profil-Level (1–5) folgt: genug Aufstiege → Level hoch, gehäufte Abstiege → runter.
 */
import type { Catalog, Exercise } from "./catalog.js";
import { type FitDb, kvGet, kvSet } from "./db.js";
import { getProfile, saveProfile } from "./profile.js";


export interface ProgressEvent {
  kind: "auf" | "ab" | "level_auf" | "level_ab";
  from?: string | undefined;
  to?: string | undefined;
  fromName?: string | undefined;
  toName?: string | undefined;
  level?: number | undefined;
}

/** Leichtere Variante: die Übung, deren »progress« auf diese zeigt (bei mehreren die schwerste). */
export function easierOf(catalog: Catalog, id: string): Exercise | null {
  const cands = catalog.exercises.filter((e) => e.progress === id);
  return cands.sort((a, b) => b.level - a.level)[0] ?? null;
}

function variantMap(db: FitDb): Map<string, string> {
  return new Map(
    (db.prepare("SELECT base, current FROM variant_map").all() as { base: string; current: string }[]).map((r) => [
      r.base,
      r.current,
    ]),
  );
}

/**
 * Welche Übung wirklich dran ist: folgt »statt X nimm Y« (auch über mehrere Stufen), solange die
 * Ziel-Übung erlaubt ist (Ausrüstung, Beschwerden). Kreise werden abgefangen.
 */
export function makeResolver(db: FitDb, catalog: Catalog, ok: (e: Exercise) => boolean) {
  const map = variantMap(db);
  return (id: string): string => {
    let cur = id;
    const seen = new Set([cur]);
    for (let i = 0; i < 8; i++) {
      const next = map.get(cur);
      if (!next || seen.has(next)) break;
      const e = catalog.byId.get(next);
      if (!e || !ok(e)) break;
      seen.add(next);
      cur = next;
    }
    return cur;
  };
}

/** Familien für die Fortschrittsseite: jede Kette vom leichtesten Glied bis zum schwersten. */
export function chains(catalog: Catalog): Exercise[][] {
  const hasEasier = new Set(catalog.exercises.map((e) => e.progress).filter(Boolean) as string[]);
  const out: Exercise[][] = [];
  for (const e of catalog.exercises) {
    if (hasEasier.has(e.id) || !e.progress) continue; // Start = nichts Leichteres zeigt hierher
    const chain = [e];
    const seen = new Set([e.id]);
    let cur = e;
    while (cur.progress && !seen.has(cur.progress)) {
      const n = catalog.byId.get(cur.progress);
      if (!n) break;
      chain.push(n);
      seen.add(n.id);
      cur = n;
    }
    if (chain.length >= 3) out.push(chain);
  }
  return out;
}

interface Signal {
  ex: string;
  dose: number;
  times: number;
  difficulty?: number | undefined;
  technique?: number | undefined;
  reached: number;
  pain?: boolean | undefined;
}

/**
 * Nach dem Feedback einer Einheit: Auf-/Abstiege prüfen und Level anpassen.
 * Aufstieg: Dosis ≥ 1,45 (deutlich mehr als die Grundmenge), mindestens 2× gemacht, Ziel erreicht,
 *   nicht »zu schwer«, Technik nicht »unsicher«, kein Schmerz.
 * Abstieg: Dosis ≤ 0,62 nach mindestens 2×, oder heute »zu schwer« + unter 70 % geschafft + Technik unsicher.
 */
export function checkProgression(
  db: FitDb,
  catalog: Catalog,
  day: string,
  signals: Signal[],
  ok: (e: Exercise) => boolean,
): ProgressEvent[] {
  const events: ProgressEvent[] = [];
  const now = Date.now();
  const name = (id?: string) => (id ? (catalog.byId.get(id)?.name ?? id) : undefined);
  const setMap = db.prepare(
    "INSERT INTO variant_map(base, current, since) VALUES(?, ?, ?) ON CONFLICT(base) DO UPDATE SET current = excluded.current, since = excluded.since",
  );
  const delMap = db.prepare("DELETE FROM variant_map WHERE base = ?");
  const log = db.prepare("INSERT INTO progress_events(day, kind, ex_from, ex_to, level, at) VALUES(?, ?, ?, ?, ?, ?)");
  const initState = db.prepare(
    "INSERT INTO exercise_state(exercise_id, dose, times, updated_at) VALUES(?, ?, 0, ?) ON CONFLICT(exercise_id) DO UPDATE SET dose = excluded.dose, updated_at = excluded.updated_at",
  );
  const map = variantMap(db);
  // Jede Übung gehört zu genau einer Wurzel (der Übung, die der Planer ursprünglich wählt). Auf- und
  // Abstiege biegen immer die Wurzel um — so entstehen keine Kreise (Liegestütz → Knie → Liegestütz).
  const rootOf = (id: string) => [...map.entries()].find(([, cur]) => cur === id)?.[0] ?? id;
  const point = (root: string, to: string) => {
    if (to === root) {
      delMap.run(root);
      map.delete(root);
    } else {
      setMap.run(root, to, day);
      map.set(root, to);
    }
  };
  for (const s of signals) {
    const e = catalog.byId.get(s.ex);
    if (!e) continue;
    const up =
      s.dose >= 1.45 && s.times >= 2 && s.reached >= 1 && s.difficulty !== 1 && s.technique !== 1 && !s.pain;
    const down = (s.dose <= 0.62 && s.times >= 2) || (s.difficulty === 1 && s.reached < 0.7 && s.technique === 1);
    if (up && e.progress) {
      // Nächste machbare Stufe — nicht machbare (Ausrüstung, Beschwerden) werden übersprungen.
      let n = catalog.byId.get(e.progress);
      for (let hop = 0; n && !ok(n) && hop < 3; hop++) n = n.progress ? catalog.byId.get(n.progress) : undefined;
      if (!n || !ok(n)) continue;
      point(rootOf(e.id), n.id);
      // Neue Variante etwas vorsichtiger beginnen; die alte fällt auf die Grundmenge zurück.
      initState.run(n.id, 0.85, now);
      initState.run(e.id, 1, now);
      log.run(day, "auf", e.id, n.id, null, now);
      events.push({ kind: "auf", from: e.id, to: n.id, fromName: e.name, toName: n.name });
    } else if (down) {
      // Eine Stufe leichter (nicht machbare Stufen überspringen).
      let easier = easierOf(catalog, e.id);
      for (let hop = 0; easier && !ok(easier) && hop < 3; hop++) easier = easierOf(catalog, easier.id);
      if (!easier || !ok(easier)) continue;
      point(rootOf(e.id), easier.id);
      initState.run(easier.id, 1.1, now);
      initState.run(e.id, 1, now);
      log.run(day, "ab", e.id, easier.id, null, now);
      events.push({ kind: "ab", from: e.id, to: easier.id, fromName: name(e.id), toName: easier.name });
    }
  }
  const lvl = adjustLevel(db, day);
  if (lvl) events.push(lvl);
  return events;
}

/** Level 1–5 aus den Auf-/Abstiegen seit der letzten Änderung (oder dem letzten Test). */
export function adjustLevel(db: FitDb, day: string): ProgressEvent | null {
  const since = (kvGet<number>(db, "level_changed_at") ?? 0) as number;
  const rows = db
    .prepare("SELECT kind, COUNT(*) AS n FROM progress_events WHERE at > ? AND kind IN ('auf','ab') GROUP BY kind")
    .all(since) as { kind: string; n: number }[];
  const ups = rows.find((r) => r.kind === "auf")?.n ?? 0;
  const downs = rows.find((r) => r.kind === "ab")?.n ?? 0;
  const p = getProfile(db);
  let next = p.level;
  if (ups - downs >= 4 && p.level < 5) next = p.level + 1;
  else if (downs - ups >= 3 && p.level > 1) next = p.level - 1;
  if (next === p.level) return null;
  saveProfile(db, { level: next });
  const now = Date.now();
  kvSet(db, "level_changed_at", now);
  db.prepare("INSERT INTO progress_events(day, kind, ex_from, ex_to, level, at) VALUES(?, ?, NULL, NULL, ?, ?)").run(
    day,
    next > p.level ? "level_auf" : "level_ab",
    next,
    now,
  );
  return { kind: next > p.level ? "level_auf" : "level_ab", level: next };
}

/** Für die Fortschrittsseite: Ketten mit aktuellem Stand, letzte Ereignisse, Level. */
export function progressionView(db: FitDb, catalog: Catalog, ok: (e: Exercise) => boolean) {
  const resolve = makeResolver(db, catalog, ok);
  const states = new Map(
    (db.prepare("SELECT exercise_id, dose, times, best FROM exercise_state").all() as {
      exercise_id: string;
      dose: number;
      times: number;
      best: number | null;
    }[]).map((r) => [r.exercise_id, r]),
  );
  // Zuletzt geänderte Zuordnung zuerst — sie bestimmt, auf welcher Stufe die Kette gerade steht.
  const maps = db.prepare("SELECT base FROM variant_map ORDER BY since DESC, rowid DESC").all() as { base: string }[];
  const fam = chains(catalog)
    .filter((c) => c.some(ok))
    .map((c) => {
      const usable = c.filter(ok);
      const ids = new Set(c.map((e) => e.id));
      const m = maps.find((r) => ids.has(r.base));
      // Ohne Auf-/Abstieg: die Stufe, die am häufigsten trainiert wurde, sonst die leichteste machbare.
      const trained = usable.filter((e) => states.has(e.id)).sort((a, b) => (states.get(b.id)?.times ?? 0) - (states.get(a.id)?.times ?? 0))[0];
      let cur: string | null = m ? resolve(m.base) : (trained ?? usable[0])?.id ?? null;
      if (cur && !ids.has(cur)) cur = m?.base ?? null;
      const idx = Math.max(0, c.findIndex((e) => e.id === cur));
      const st = cur ? states.get(cur) : undefined;
      return {
        pattern: c[0]?.pattern,
        steps: c.map((e) => ({ id: e.id, name: e.name, level: e.level, usable: ok(e) })),
        current: idx,
        // Nähe zum nächsten Aufstieg: Dosis 1 → 0 %, 1,45 → 100 %
        toNext: st ? Math.max(0, Math.min(100, Math.round(((st.dose - 1) / 0.45) * 100))) : 0,
        times: st?.times ?? 0,
        best: st?.best ?? null,
      };
    });
  const events = db
    .prepare("SELECT day, kind, ex_from, ex_to, level FROM progress_events ORDER BY at DESC LIMIT 12")
    .all() as { day: string; kind: string; ex_from: string | null; ex_to: string | null; level: number | null }[];
  return {
    level: getProfile(db).level,
    families: fam,
    events: events.map((e) => ({
      ...e,
      fromName: e.ex_from ? catalog.byId.get(e.ex_from)?.name : null,
      toName: e.ex_to ? catalog.byId.get(e.ex_to)?.name : null,
    })),
  };
}
