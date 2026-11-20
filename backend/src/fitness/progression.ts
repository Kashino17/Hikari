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
import { seedFor } from "./planner.js";
import { getPrograms, phaseFor } from "./program.js";
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
  // Zwischenstufen auf dem Weg Wurzel → aktuelle Stufe sind auch »erledigt« (Wand → Knie → Fäuste:
  // wählt der Planer Knie direkt, gilt ebenfalls Fäuste).
  const passed = new Map<string, string>();
  for (const [root, cur] of map) {
    const path: string[] = [];
    let e = catalog.byId.get(root);
    for (let i = 0; e && e.id !== cur && i < 8; i++) {
      path.push(e.id);
      e = e.progress ? catalog.byId.get(e.progress) : undefined;
    }
    if (e?.id === cur) for (const id of path.slice(1)) if (!map.has(id)) passed.set(id, cur);
  }
  return (id: string): string => {
    const via = passed.get(id);
    const viaOk = via ? catalog.byId.get(via) : undefined;
    let cur = map.has(id) ? id : via && viaOk && ok(viaOk) ? via : id;
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

/**
 * Höchste beherrschte Schwierigkeit je Bewegungsmuster: alle Stufen, die auf dem Weg einer Zuordnung
 * (Ausgangsübung → aktuelle Stufe) überholt wurden. Bleibt nach einem Aufstieg erhalten — die Dosis der
 * alten Übung wird dabei zurückgesetzt und taugt nicht als Merkmal.
 */
export function masteredLevels(db: FitDb, catalog: Catalog): Record<string, number> {
  const out: Record<string, number> = {};
  for (const [root, cur] of variantMap(db)) {
    let e = catalog.byId.get(root);
    const path: Exercise[] = [];
    for (let i = 0; e && e.id !== cur && i < 8; i++) {
      path.push(e);
      e = e.progress ? catalog.byId.get(e.progress) : undefined;
    }
    // Nur ein echter Aufstiegsweg zählt (Abstieg: kein Weg nach oben) — andere Ketten bleiben unberührt.
    if (e?.id === cur) for (const x of path) out[x.pattern] = Math.max(out[x.pattern] ?? 0, x.level);
  }
  return out;
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
  /** Entlastungswoche: kein Aufstieg, kein Level (leicht ist dort Absicht). */
  deload?: boolean | undefined;
  /** Anteil der Vorgabe im ersten Satz */
  first?: number | undefined;
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
  // Übergabe der Menge: die neue, schwerere Stufe beginnt bei ~60 % der Wiederholungen der alten (wie bei
  // Freeletics), die leichtere nach einem Abstieg bei ~140 % — statt bei einer Standardmenge, die oft zu viel war.
  const lvl0 = getProfile(db).level;
  const seedRow = db.prepare("SELECT seed FROM exercise_state WHERE exercise_id = ?");
  const seedOf = (x: Exercise) => (seedRow.get(x.id) as { seed: number | null } | undefined)?.seed ?? seedFor(lvl0, x);
  const handState = db.prepare(
    "INSERT INTO exercise_state(exercise_id, dose, times, updated_at, seed) VALUES(?, ?, 0, ?, ?) ON CONFLICT(exercise_id) DO UPDATE SET dose = excluded.dose, seed = COALESCE(exercise_state.seed, excluded.seed), updated_at = excluded.updated_at",
  );
  // Faktor je nach Abstand: pro Schwierigkeitsstufe ×0,78; von beidseitig auf einseitig (je Seite) ×0,7.
  const gapFactor = (from: Exercise, to: Exercise) => {
    const gap = Math.max(1, Math.abs(to.level - from.level));
    let f = 0.78 ** gap;
    if (to.perSide && !from.perSide) f *= 0.7;
    if (!to.perSide && from.perSide) f /= 0.7;
    return to.level >= from.level ? f : 1 / f;
  };
  const capOf = (x: Exercise) => (x.unit === "sec" ? 90 : x.perSide ? 20 : 30);
  const handoff = (from: Exercise, fromDose: number, to: Exercise, factor = gapFactor(from, to)) => {
    const sdTo = seedOf(to);
    const dose =
      from.unit === to.unit
        ? Math.min(2, Math.max(0.45, (Math.min(capOf(from), from.base * seedOf(from) * fromDose) * factor) / (to.base * sdTo)))
        : factor < 1 ? 0.85 : 1.1;
    handState.run(to.id, Math.round(dose * 1000) / 1000, now, sdTo);
  };
  const map = variantMap(db);
  const passedRes = makeResolver(db, catalog, ok);
  // Jede Übung gehört zu genau einer Wurzel (der Übung, die der Planer ursprünglich wählt). Auf- und
  // Abstiege biegen immer die Wurzel um — so entstehen keine Kreise (Liegestütz → Knie → Liegestütz).
  const rootsOf = (id: string) => {
    const r = [...map.entries()].filter(([, cur]) => cur === id).map(([root]) => root);
    return r.length ? r : [id];
  };
  const rootOf = (id: string) => rootsOf(id)[0] as string;
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
      // Erster Satz voll geschafft und im Schnitt ≥ 85 % — dass späte Runden mit Ermüdung kürzer werden, ist normal.
      !s.deload && s.dose >= 1.45 && s.times >= 2 && (s.first ?? s.reached) >= 1 && s.reached >= 0.85 && s.difficulty !== 1 && s.technique !== 1 && !s.pain;
    // Abstieg: dauerhaft niedrige Menge, oder »zu schwer« und deutlich verfehlt (mit unsicherer Technik
    // sofort, sonst ab dem dritten Mal — nicht jeder tippt den Technik-Chip an).
    const down =
      (s.dose <= 0.62 && s.times >= 2) || (s.difficulty === 1 && s.reached < 0.7 && (s.technique === 1 || s.times >= 3));
    if (up && e.progress) {
      // Nächste machbare Stufe — nicht machbare (Ausrüstung, Beschwerden) werden übersprungen.
      let n = catalog.byId.get(e.progress);
      for (let hop = 0; n && !ok(n) && hop < 3; hop++) n = n.progress ? catalog.byId.get(n.progress) : undefined;
      if (!n || !ok(n)) continue;
      // Schon umgestellt (die Übung stand trotzdem im Plan) → kein zweiter »Aufstieg«.
      if (map.get(rootOf(e.id)) === n.id || passedRes(e.id) !== e.id) continue;
      // Zeigen mehrere Ausgangsübungen auf diese Stufe, ziehen alle mit (sonst bleibt eine zurück).
      for (const r of rootsOf(e.id)) point(r, n.id);
      // Neue Variante etwas vorsichtiger beginnen; die alte fällt auf die Grundmenge zurück.
      handoff(e, s.dose, n);
      initState.run(e.id, 1, now);
      log.run(day, "auf", e.id, n.id, null, now);
      events.push({ kind: "auf", from: e.id, to: n.id, fromName: e.name, toName: n.name });
    } else if (down) {
      // Eine Stufe leichter (nicht machbare Stufen überspringen).
      let easier = easierOf(catalog, e.id);
      for (let hop = 0; easier && !ok(easier) && hop < 3; hop++) easier = easierOf(catalog, easier.id);
      if (!easier || !ok(easier)) continue;
      for (const r of rootsOf(e.id)) point(r, easier.id);
      // Leichtere Stufe übernimmt die Menge passend zum Abstand. Die gelernte Dosis der schweren Übung bleibt —
      // kommt sie später wieder dran, startet sie dort, wo sie zu schwer war, statt wieder zu hoch.
      handoff(e, s.dose, easier, Math.min(1.8, gapFactor(e, easier)));
      log.run(day, "ab", e.id, easier.id, null, now);
      events.push({ kind: "ab", from: e.id, to: easier.id, fromName: name(e.id), toName: easier.name });
    }
  }
  const deloadNow = signals.some((s) => s.deload) || !!phaseFor(db, getPrograms(), day)?.deload;
  const lvl = deloadNow ? null : adjustLevel(db, day);
  if (lvl) events.push(lvl);
  return events;
}

/** Level 1–5 aus den Auf-/Abstiegen seit der letzten Änderung (oder dem letzten Test). */
export function adjustLevel(db: FitDb, day: string): ProgressEvent | null {
  const since = (kvGet<number>(db, "level_changed_at") ?? 0) as number;
  const rows = db
    // Verschiedene Übungen zählen, nicht Wiederholungen derselben Stufe.
    .prepare("SELECT kind, COUNT(DISTINCT ex_from || '>' || ex_to) AS n FROM progress_events WHERE at > ? AND kind IN ('auf','ab') GROUP BY kind")
    .all(since) as { kind: string; n: number }[];
  const ups = rows.find((r) => r.kind === "auf")?.n ?? 0;
  const downs = rows.find((r) => r.kind === "ab")?.n ?? 0;
  const p = getProfile(db);
  let next = p.level;
  // Nicht zappeln: frühestens 21 Tage nach der letzten Änderung hoch, 14 Tage runter.
  const sinceDay = kvGet<string>(db, "level_changed_day");
  const days = sinceDay ? Math.round((Date.parse(`${day}T12:00:00`) - Date.parse(`${sinceDay}T12:00:00`)) / 86_400_000) : Infinity;
  if (ups - downs >= 4 && p.level < 5 && days >= 21) next = p.level + 1;
  else if (downs - ups >= 3 && p.level > 1 && days >= 14) next = p.level - 1;
  if (next === p.level) return null;
  saveProfile(db, { level: next });
  const now = Date.now();
  kvSet(db, "level_changed_at", now);
  kvSet(db, "level_changed_day", day);
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
