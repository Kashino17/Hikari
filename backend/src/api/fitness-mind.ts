/**
 * Achtsamkeit (Meditation, Atem, Yoga-Pfad, Affirmationen, Wissen) — Teil des Fitness-Bereichs.
 */
import type { FastifyInstance } from "fastify";
import { type FitDb, localDay } from "../fitness/db.js";
import {
  daySeed,
  loadMind,
  type MindCatalog,
  mindStats,
  setManualUnlock,
  yogaProgress,
} from "../fitness/mind.js";

const KINDS = new Set(["meditation", "atem", "yoga", "video"]);

export function registerMindRoutes(app: FastifyInstance, db: FitDb, dir: string) {
  let mind: MindCatalog = loadMind(dir);
  const fresh = () => {
    // Inhalte dürfen nachgelegt werden, ohne den Server neu zu starten.
    mind = loadMind(dir);
    return mind;
  };
  const asanaLite = (a: MindCatalog["asanas"][number]) => ({
    id: a.id, api: a.api, de: a.de, sanskrit: a.sanskrit, stufe: a.stufe, art: a.art, kurz: a.kurz, seiten: a.seiten,
  });

  app.get("/fitness/mind", async () => {
    const m = fresh();
    const day = localDay();
    const aff = m.content.affirmationen;
    const imp = m.content.impulse;
    const recent = db.prepare("SELECT * FROM mind_log ORDER BY at DESC LIMIT 6").all();
    return {
      day,
      stats: mindStats(db, day),
      path: yogaProgress(db, m.levels),
      levels: m.levels.map((l) => ({
        stufe: l.stufe, name: l.name, untertitel: l.untertitel, minuten: l.minuten, intention: l.intention,
        neu: m.asanas.filter((a) => a.stufe === l.stufe).map((a) => a.id),
      })),
      asanas: m.asanas.map(asanaLite),
      videos: m.videos,
      atem: m.content.atem.map(({ id, name, kurz, wofuer, phasen, runden_standard }) => ({ id, name, kurz, wofuer, phasen, runden_standard })),
      gefuehrt: m.content.gefuehrt.map(({ id, titel, untertitel, minuten, themen }) => ({ id, titel, untertitel, minuten, themen })),
      deepdives: m.content.deepdives.map(({ id, titel, untertitel, minuten, farbe }) => ({ id, titel, untertitel, minuten, farbe })),
      affirmation: aff.length ? aff[daySeed(day, "aff") % aff.length] : null,
      impuls: imp.length ? imp[daySeed(day, "imp") % imp.length] : null,
      recent,
    };
  });

  app.get<{ Params: { id: string } }>("/fitness/mind/asana/:id", async (req, reply) => {
    const m = fresh();
    const a = m.asanas.find((x) => x.id === req.params.id);
    if (!a) return reply.code(404).send({ error: "not_found" });
    const next = m.asanas.filter((x) => x.vorstufe === a.id).map(asanaLite);
    const pre = a.vorstufe ? m.asanas.find((x) => x.id === a.vorstufe) : null;
    return { ...a, vorstufeInfo: pre ? asanaLite(pre) : null, weiter: next };
  });

  app.get<{ Params: { n: string } }>("/fitness/mind/level/:n", async (req, reply) => {
    const m = fresh();
    const l = m.levels.find((x) => x.stufe === Number(req.params.n));
    if (!l) return reply.code(404).send({ error: "not_found" });
    const prog = yogaProgress(db, m.levels);
    const ids = new Set(l.flow.map((f) => f.asana).filter(Boolean) as string[]);
    return {
      ...l,
      progress: prog.levels.find((p) => p.stufe === l.stufe),
      open: prog.open,
      asanas: m.asanas.filter((a) => ids.has(a.id) || a.stufe === l.stufe).map(asanaLite),
      videos: m.videos.filter((v) => v.kind === "yoga" && v.level === l.stufe),
    };
  });

  app.get<{ Params: { id: string } }>("/fitness/mind/guided/:id", async (req, reply) =>
    fresh().content.gefuehrt.find((g) => g.id === req.params.id) ?? reply.code(404).send({ error: "not_found" }),
  );
  app.get<{ Params: { id: string } }>("/fitness/mind/atem/:id", async (req, reply) =>
    fresh().content.atem.find((g) => g.id === req.params.id) ?? reply.code(404).send({ error: "not_found" }),
  );
  app.get<{ Params: { id: string } }>("/fitness/mind/deep/:id", async (req, reply) =>
    fresh().content.deepdives.find((g) => g.id === req.params.id) ?? reply.code(404).send({ error: "not_found" }),
  );
  app.get("/fitness/mind/affirmations", async () => fresh().content.affirmationen);
  app.get("/fitness/mind/impulse", async () => fresh().content.impulse);

  /** Eine Übung ist geschafft. Yoga-Stufen werden dabei ggf. freigeschaltet (Antwort: unlocked). */
  app.post<{
    Body: { kind?: string; ref?: string; seconds?: number; moodBefore?: number; moodAfter?: number; note?: string };
  }>("/fitness/mind/log", async (req, reply) => {
    const b = req.body ?? {};
    if (!b.kind || !KINDS.has(b.kind)) return reply.code(400).send({ error: "kind" });
    const seconds = Math.max(0, Math.min(4 * 3600, Math.round(Number(b.seconds) || 0)));
    if (seconds < 20) return { ok: false, reason: "zu_kurz", stats: mindStats(db) };
    const mood = (v: unknown) => (Number.isInteger(v) && (v as number) >= 1 && (v as number) <= 5 ? (v as number) : null);
    const m = fresh();
    const before = yogaProgress(db, m.levels).open;
    db.prepare(
      "INSERT INTO mind_log(day, kind, ref, seconds, mood_before, mood_after, note, at) VALUES(?, ?, ?, ?, ?, ?, ?, ?)",
    ).run(localDay(), b.kind, b.ref ?? null, seconds, mood(b.moodBefore), mood(b.moodAfter), b.note?.slice(0, 500) ?? null, Date.now());
    const after = yogaProgress(db, m.levels);
    return { ok: true, stats: mindStats(db), path: after, unlocked: after.open > before ? after.open : null };
  });

  /** »Kann ich schon«: die nächste Stufe sofort öffnen (ehrlich bleiben ist deine Sache). */
  app.post<{ Body: { stufe?: number } }>("/fitness/mind/unlock", async (req, reply) => {
    const m = fresh();
    const stufe = Number(req.body?.stufe);
    if (!m.levels.some((l) => l.stufe === stufe)) return reply.code(400).send({ error: "stufe" });
    setManualUnlock(db, stufe);
    return { ok: true, path: yogaProgress(db, m.levels) };
  });

  /** Kachel im Hikari-Profil: Stand des Yoga-Pfads, Minuten heute, Serie. */
  app.get("/fitness/mind/tile", async () => {
    const m = fresh();
    const st = mindStats(db);
    const path = yogaProgress(db, m.levels);
    const level = m.levels.find((l) => l.stufe === path.open);
    return {
      title: st.todayMinutes ? `Heute ${st.todayMinutes} min für dich` : "Ein Moment für dich",
      detail: level ? `Yoga · Stufe ${level.stufe} ${level.name}` : "Yoga, Meditation, Atmen",
      streak: st.streak,
      done: st.todayMinutes > 0,
    };
  });

  /** Für Tageskacheln und den Kalender: Achtsamkeit an einem Tag. */
  app.get<{ Querystring: { day?: string } }>("/fitness/mind/day", async (req) => {
    const day = /^\d{4}-\d{2}-\d{2}$/.test(req.query.day ?? "") ? (req.query.day as string) : localDay();
    const rows = db.prepare("SELECT * FROM mind_log WHERE day = ? ORDER BY at").all(day);
    return { day, rows };
  });
}
