/**
 * Fitness-Bereich: Training mit KI-Coach, Calisthenics-Skills, Ernährung (Foto-Tracker, Rezepte,
 * Ernährungs-Coach). Alle Daten in ~/.hikari/fitness (eigene Datenbank, Medien, Fotos).
 */
import { registerMindRoutes } from "./fitness-mind.js";
import { createHash, randomUUID } from "node:crypto";
import {
  createReadStream,
  existsSync,
  mkdirSync,
  readFileSync,
  statSync,
  writeFileSync,
} from "node:fs";
import { dirname, join } from "node:path";
import type { FastifyInstance, FastifyReply } from "fastify";
import { FitnessAi } from "../fitness/ai.js";
import { BarcodeLookup } from "../fitness/barcode.js";
import { EQUIPMENT, loadCatalog } from "../fitness/catalog.js";
import {
  type SessionFeedback,
  type SessionResult,
  applyFeedback,
  buildContext,
  extraSession,
  getSession,
  prepareNext,
  reviewSession,
  sessionsBetween,
  todaySession,
  upgrading,
} from "../fitness/coach.js";
import {
  type FitDb,
  addDays,
  kvGet,
  kvSet,
  localDay,
  openFitnessDb,
  weekday,
} from "../fitness/db.js";
import { BlsDb } from "../fitness/foods.js";
import { IngredientImages } from "../fitness/ingredient-images.js";
import {
  type MealItem,
  analyzePhoto,
  cleanNutrients,
  dayReport,
  foodLogDay,
  nutrientStatus,
  resolveItems,
} from "../fitness/meal-ai.js";
import { DAY_TYPES, type DayType, blockSeconds, usable } from "../fitness/planner.js";
import { checkProgression, progressionView } from "../fitness/progression.js";
import { saveTest, testState } from "../fitness/assessment.js";
import { loadPrograms, phaseFor, setPrograms, startProgram, stopProgram, getPrograms } from "../fitness/program.js";
import { weekPreview, weekReview } from "../fitness/week.js";
import {
  NUTRIENTS,
  type Nutrients,
  computeTargets,
  getProfile,
  roundNutrients,
  saveProfile,
  scaleNutrients,
  sumNutrients,
} from "../fitness/profile.js";
import {
  type MealDbMeal,
  type MealSlot,
  RecipeImporter,
  allRecipes,
  fitsProfile,
  getRecipe,
  leckerToMeal,
  portionFactor,
  recipeStates,
  scoreRecipe,
} from "../fitness/recipes.js";
import { logSkill, setSkill, skillStates } from "../fitness/skills.js";

export interface FitnessDeps {
  dir: string;
  /** backend/fitness (Katalog, Skills, BLS) */
  dataDir: string;
  geminiKey: () => string | null;
  log: (msg: string) => void;
}

const SLOTS: MealSlot[] = ["fruehstueck", "mittag", "abend", "snack"];
const SLOT_NAMES: Record<string, string> = {
  fruehstueck: "Frühstück",
  mittag: "Mittagessen",
  abend: "Abendessen",
  snack: "Snack",
};
const REGIONS: Record<string, string> = {
  nacken: "Nacken",
  schulter: "Schulter",
  ellbogen: "Ellbogen",
  handgelenk: "Handgelenk",
  ruecken: "Unterer Rücken",
  huefte: "Hüfte",
  knie: "Knie",
  sprunggelenk: "Sprunggelenk",
};

function sendFile(reply: FastifyReply, file: string, type: string, range?: string) {
  const size = statSync(file).size;
  reply.header("accept-ranges", "bytes").header("cache-control", "public, max-age=86400");
  const m = /^bytes=(\d*)-(\d*)$/.exec(range ?? "");
  if (m && (m[1] || m[2])) {
    const start = m[1] ? Number(m[1]) : Math.max(0, size - Number(m[2]));
    const end = m[1] && m[2] ? Math.min(Number(m[2]), size - 1) : size - 1;
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
}

export function registerFitnessRoutes(app: FastifyInstance, deps: FitnessDeps) {
  mkdirSync(deps.dir, { recursive: true });
  const db: FitDb = openFitnessDb(join(deps.dir, "fitness.db"));
  const mediaDir = join(deps.dir, "media");
  const photoDir = join(deps.dir, "photos");
  const imgDir = join(deps.dir, "img");
  for (const d of [mediaDir, photoDir, imgDir]) mkdirSync(d, { recursive: true });
  const catalog = loadCatalog(
    join(deps.dataDir, "exercises.json"),
    join(deps.dataDir, "skills.json"),
  );
  const ndb = new BlsDb(join(deps.dataDir, "bls.json.gz"));
  // Klangbetten liegen einmal für alle (auch fitness-dev) unter ~/.hikari/fitness/mind-sounds.
  registerMindRoutes(app, db, join(deps.dataDir, "mind"), join(dirname(deps.dir), "fitness", "mind-sounds"));
  const ai = new FitnessAi({
    key: deps.geminiKey,
    usageFile: join(deps.dir, "ai-usage.json"),
    log: deps.log,
  });
  const mealDbFile = join(deps.dir, "themealdb.json");
  const importer = new RecipeImporter(
    db,
    ai,
    ndb,
    async () => {
      let meals: MealDbMeal[] = [];
      if (existsSync(mealDbFile)) meals = JSON.parse(readFileSync(mealDbFile, "utf8"));
      else {
        for (const c of "abcdefghijklmnopqrstuvwxyz") {
          const r = await fetch(`https://www.themealdb.com/api/json/v1/1/search.php?f=${c}`);
          const j = (await r.json()) as { meals: MealDbMeal[] | null };
          meals.push(...(j.meals ?? []));
          await new Promise((res) => setTimeout(res, 400));
        }
        writeFileSync(mealDbFile, JSON.stringify(meals));
      }
      // Deutsche Frühstücke, Snacks und Fitness-Gerichte (backend/fitness/tools/fetch_lecker.py).
      const leckerFile = join(deps.dir, "lecker.json");
      if (existsSync(leckerFile)) {
        const entries = JSON.parse(readFileSync(leckerFile, "utf8")) as {
          url: string;
          group: string;
          ld: Record<string, unknown>;
        }[];
        meals = [...entries.map(leckerToMeal).filter((m): m is MealDbMeal => !!m), ...meals];
      }
      return meals;
    },
    deps.log,
  );
  // Rezepte im Hintergrund aufbereiten (setzt nach Neustart fort).
  setTimeout(() => void importer.run(), 20_000);
  setInterval(() => void importer.run(), 6 * 60 * 60 * 1000).unref();

  // Zutatenbilder: TheMealDB-Namen zuordnen, Rest einmal über Wikimedia nachschlagen (im Hintergrund).
  const images = new IngredientImages(db, join(deps.dir, "mealdb-ingredients.json"), deps.log);
  const prefetchImages = () => {
    for (const r of allRecipes(db)) images.prefetch(r.ingredients);
  };
  void images.init().then(() => setTimeout(prefetchImages, 60_000));
  setInterval(prefetchImages, 30 * 60 * 1000).unref();
  const withImages = (r: ReturnType<typeof allRecipes>[number]) => ({
    ...r,
    ingredients: r.ingredients.map((i) => ({ ...i, image: images.image(i.en, i.name) })),
  });

  const exerciseView = (id: string) => {
    const e = catalog.byId.get(id);
    if (!e) return null;
    return {
      ...e,
      media: e.media?.video
        ? {
            video: `/fitness/media/${e.media.video}`,
            image: `/fitness/media/${e.media.image}`,
            credit: e.media.credit,
          }
        : null,
    };
  };

  // ── Auswertung nach dem Training (Hintergrund) ─────────────────────────
  interface CoachJob {
    session: string;
    startedAt: number;
    review: { state: "running" | "done" | "failed"; doneAt?: number; data?: unknown };
    plan: {
      state: "running" | "done" | "failed" | "none";
      doneAt?: number;
      day?: string;
      title?: string;
      minutes?: number;
      source?: string;
    };
    skills: unknown[];
  }
  const jobs = new Map<string, CoachJob>();
  const startCoachJob = (
    s: NonNullable<ReturnType<typeof getSession>>,
    fb: SessionFeedback,
    result: SessionResult,
    skills: unknown[],
  ) => {
    const job: CoachJob = {
      session: s.id,
      startedAt: Date.now(),
      review: { state: "running" },
      plan: { state: "running" },
      skills,
    };
    jobs.set(s.id, job);
    void (async () => {
      if (!ai.available()) {
        job.review = { state: "failed", doneAt: Date.now() };
        return;
      }
      try {
        const review = await reviewSession(ai, catalog, s, fb, result);
        db.prepare(
          "INSERT INTO coach_notes(kind, day, data, model, at) VALUES('training', ?, ?, ?, ?)",
        ).run(s.day, JSON.stringify({ session: s.id, ...review }), review.model, Date.now());
        job.review = { state: "done", doneAt: Date.now(), data: review };
      } catch (e) {
        deps.log(`[fitness] Auswertung: ${e instanceof Error ? e.message : e}`);
        job.review = { state: "failed", doneAt: Date.now() };
      }
    })();
    void (async () => {
      try {
        const next = await prepareNext(ai, db, catalog, s.day, deps.log);
        job.plan = next
          ? {
              state: "done",
              doneAt: Date.now(),
              day: next.day,
              title: next.session.plan.title,
              minutes: next.session.plan.minutes,
              source: next.session.source ?? "regeln",
            }
          : { state: "none", doneAt: Date.now() };
      } catch (e) {
        deps.log(`[fitness] Nächster Plan: ${e instanceof Error ? e.message : e}`);
        job.plan = { state: "failed", doneAt: Date.now() };
      }
    })();
    // Alte Aufträge nach einem Tag vergessen.
    for (const [k, j] of jobs) if (Date.now() - j.startedAt > 86_400_000) jobs.delete(k);
  };
  /** Ohne laufenden Auftrag (z. B. nach Neustart) aus der Datenbank: Auswertung vorhanden → fertig. */
  const coachJobView = (sessionId: string) => {
    const j = jobs.get(sessionId);
    if (j) return { ...j, elapsed: Date.now() - j.startedAt };
    const s = getSession(db, sessionId);
    if (!s || s.status !== "done") return null;
    const note = (
      db
        .prepare(
          "SELECT data FROM coach_notes WHERE kind = 'training' AND day = ? ORDER BY at DESC",
        )
        .all(s.day) as { data: string }[]
    )
      .map((n) => JSON.parse(n.data))
      .find((n: { session?: string }) => n.session === sessionId);
    return {
      session: sessionId,
      startedAt: s.done_at ?? Date.now(),
      elapsed: 0,
      review: note ? { state: "done", data: note } : { state: "failed" },
      plan: { state: "none" },
      skills: [],
    };
  };

  // ── Grundlagen ──────────────────────────────────────────────────────────
  app.get("/fitness/catalog", async () => ({
    exercises: catalog.exercises.map((e) => exerciseView(e.id)),
    skills: catalog.skills,
    equipment: EQUIPMENT,
    regions: REGIONS,
    dayTypes: Object.fromEntries(Object.entries(DAY_TYPES).map(([k, v]) => [k, v.title])),
    nutrients: NUTRIENTS,
  }));

  // ── Fortschritt: Level, Übungsstufen (Auf-/Abstiege), Fitness-Test ──
  app.get("/fitness/progress", async () => {
    const ctx = buildContext(db, catalog, localDay());
    const t = testState(db, catalog);
    return {
      ...progressionView(db, catalog, (e) => usable(ctx, e)),
      test: { ...t, tests: t.tests.map((x) => ({ ...x, exercise: exerciseView(x.ex) })) },
    };
  });
  app.post<{ Body: { results?: Record<string, number> } }>("/fitness/test", async (req) => saveTest(db, req.body?.results ?? {}));

  // ── Programme (mehrere Wochen) und Woche ──
  setPrograms(loadPrograms(deps.dataDir));
  app.get("/fitness/programs", async () => {
    const p = getProfile(db);
    return {
      programs: getPrograms().map((x) => ({
        ...x,
        machbar: !x.braucht || x.braucht.some((q) => p.equipment.includes(q)),
        exercise: exerciseView(x.bild),
      })),
      current: phaseFor(db, getPrograms(), localDay()),
    };
  });
  app.post<{ Body: { id?: string | null } }>("/fitness/program", async (req, reply) => {
    const id = req.body?.id;
    if (!id) { stopProgram(db); return { ok: true, current: null }; }
    if (!getPrograms().some((x) => x.id === id)) return reply.code(400).send({ error: "program" });
    startProgram(db, id, localDay());
    // Heutigen, noch nicht begonnenen Plan an die neue Phase anpassen.
    const today = sessionsBetween(db, localDay(), localDay()).find((s) => s.status === "planned");
    if (today) void todaySession(ai, db, catalog, { regenerate: true, log: deps.log }).catch(() => {});
    return { ok: true, current: phaseFor(db, getPrograms(), localDay()) };
  });
  app.get("/fitness/week", async () => weekPreview(db, catalog));
  app.get<{ Querystring: { monday?: string; force?: string } }>("/fitness/week/review", async (req, reply) => {
    const monday = /^\d{4}-\d{2}-\d{2}$/.test(req.query.monday ?? "") ? (req.query.monday as string) : weekPreview(db, catalog).review.monday;
    const c = db.prepare("SELECT data FROM coach_notes WHERE kind = 'woche' AND day = ? ORDER BY at DESC LIMIT 1").get(monday) as { data: string } | undefined;
    if (c && !req.query.force) return { monday, review: JSON.parse(c.data) };
    if (!ai.available()) return reply.code(503).send({ error: "no_key" });
    try {
      return { monday, review: await weekReview(ai, db, catalog, monday) };
    } catch (e) {
      deps.log(`[fitness] Wochenrückblick fehlgeschlagen: ${e instanceof Error ? e.message : e}`);
      return reply.code(502).send({ error: "ai" });
    }
  });

  app.get<{ Params: { file: string } }>("/fitness/media/:file", async (req, reply) => {
    const m = /^([a-z0-9_]{1,60})\.(mp4|jpg)$/.exec(req.params.file);
    if (!m) return reply.code(400).send({ error: "file" });
    const file = join(mediaDir, req.params.file);
    if (!existsSync(file)) return reply.code(404).send({ error: "not_found" });
    return sendFile(reply, file, m[2] === "mp4" ? "video/mp4" : "image/jpeg", req.headers.range);
  });

  app.get("/fitness/state", async () => {
    const profile = getProfile(db);
    const day = localDay();
    const weekStart = addDays(day, -weekday(day));
    const week = sessionsBetween(db, weekStart, addDays(weekStart, 6));
    const today =
      (db
        .prepare(
          "SELECT id, status FROM sessions WHERE day = ? AND kind = 'coach' ORDER BY created_at DESC, rowid DESC LIMIT 1",
        )
        .get(day) as { id: string; status: string } | undefined) ?? null;
    const streak = trainingStreak(db, day, profile.trainingDays);
    const weight = db
      .prepare(
        "SELECT day, weight FROM body_log WHERE weight IS NOT NULL ORDER BY day DESC LIMIT 1",
      )
      .get() as { day: string; weight: number } | undefined;
    return {
      day,
      profile,
      targets: computeTargets(profile),
      ai: ai.status(),
      today,
      week: Array.from({ length: 7 }, (_, i) => {
        const d = addDays(weekStart, i);
        const s = week.filter((w) => w.day === d);
        return {
          day: d,
          planned: profile.trainingDays.includes(i),
          done: s.some((x) => x.status === "done"),
          minutes: s
            .filter((x) => x.status === "done")
            .reduce((a, x) => a + Math.round((x.result?.seconds ?? 0) / 60), 0),
        };
      }),
      streak,
      totals: db
        .prepare(
          "SELECT COUNT(*) AS sessions, COALESCE(SUM(json_extract(result, '$.seconds')), 0) AS seconds FROM sessions WHERE status = 'done'",
        )
        .get(),
      weight: weight ?? null,
      recipes: importer.status(),
    };
  });

  /** Kurzfassung für die Kachel im Profil der App. */
  app.get("/fitness/tile", async () => {
    const profile = getProfile(db);
    const day = localDay();
    if (!profile.onboarded)
      return {
        title: "Fitness einrichten",
        detail: "Ziel, Körper, Ausrüstung — 1 Minute",
        done: false,
        streak: 0,
      };
    const s = db
      .prepare(
        "SELECT plan, status, result FROM sessions WHERE day = ? AND kind = 'coach' ORDER BY created_at DESC, rowid DESC LIMIT 1",
      )
      .get(day) as { plan: string; status: string; result: string | null } | undefined;
    const food = foodLogDay(db, day);
    const kcal = Math.round(sumNutrients(food.map((f) => f.totals)).kcal ?? 0);
    const left = computeTargets(profile).kcal - kcal;
    const eat = left >= 0 ? `noch ${left} kcal` : `${-left} kcal über Ziel`;
    const streak = trainingStreak(db, day, profile.trainingDays);
    // Bild für die Profil-Kachel: erste Hauptübung des Tages (sonst ein Klimmzug).
    const imageOf = (planJson?: string) => {
      let id = "pullup";
      try {
        const blocks = (JSON.parse(planJson ?? "{}").blocks ?? []) as { kind: string; items: { ex: string }[] }[];
        id = blocks.find((b) => b.kind === "main")?.items?.[0]?.ex ?? id;
      } catch { /* Standardbild */ }
      return (exerciseView(id) as { media?: { image?: string } } | null)?.media?.image ?? null;
    };
    if (s) {
      const plan = JSON.parse(s.plan) as { title: string; minutes: number };
      if (s.status === "done")
        return {
          title: `${plan.title} geschafft`,
          detail: `${Math.round((JSON.parse(s.result ?? "{}").seconds ?? 0) / 60)} min · ${eat}`,
          done: true,
          streak,
          image: imageOf(s.plan),
        };
      return {
        title: plan.title,
        detail: `${plan.minutes} min Training · ${eat}`,
        done: false,
        streak,
        image: imageOf(s.plan),
      };
    }
    const rest = !profile.trainingDays.includes(weekday(day));
    return {
      title: rest ? "Ruhetag" : "Training wartet",
      detail: rest ? `Erholung · ${eat}` : `Dein Coach plant beim Öffnen · ${eat}`,
      done: false,
      streak,
      image: imageOf(),
    };
  });

  app.post<{ Body: Record<string, unknown> }>("/fitness/profile", async (req) => {
    const p = saveProfile(db, req.body as never);
    if (typeof req.body.weightKg === "number")
      db.prepare(
        "INSERT INTO body_log(day, weight) VALUES(?, ?) ON CONFLICT(day) DO UPDATE SET weight = excluded.weight",
      ).run(localDay(), p.weightKg);
    return { profile: p, targets: computeTargets(p) };
  });

  // ── Training ────────────────────────────────────────────────────────────
  const sessionView = (s: NonNullable<ReturnType<typeof getSession>>) => ({
    ...s,
    upgrading: upgrading.has(s.id),
    exercises: Object.fromEntries(
      [...new Set(s.plan.blocks.flatMap((b) => b.items.map((i) => i.ex)))].map((id) => [
        id,
        exerciseView(id),
      ]),
    ),
    blockSeconds: s.plan.blocks.map((b) => blockSeconds(b, catalog)),
  });

  app.get<{ Querystring: { regen?: string } }>("/fitness/today", async () =>
    sessionView(await todaySession(ai, db, catalog, { log: deps.log })),
  );

  app.post<{
    Body: {
      checkin?: { energy?: number; minutes?: number; sore?: string[]; wish?: string };
      dayType?: DayType;
    };
  }>("/fitness/today/adapt", async (req) => {
    const s = await todaySession(ai, db, catalog, {
      regenerate: true,
      wait: true,
      checkin: req.body.checkin,
      forceType: req.body.dayType,
      log: deps.log,
    });
    return sessionView(s);
  });

  app.get<{ Params: { id: string } }>("/fitness/session/:id", async (req, reply) => {
    const s = getSession(db, req.params.id);
    return s ? sessionView(s) : reply.code(404).send({ error: "not_found" });
  });

  /** Übung tauschen: gleiche Bewegung, leichter / gleich / schwerer. */
  app.get<{ Params: { ex: string } }>("/fitness/alternatives/:ex", async (req) => {
    const e = catalog.byId.get(req.params.ex);
    if (!e) return { items: [] };
    const p = getProfile(db);
    const owned = new Set([...p.equipment, "boden", "wand", "matte"]);
    return {
      items: catalog.exercises
        .filter(
          (x) =>
            x.id !== e.id &&
            x.pattern === e.pattern &&
            x.equipment.every((q) => owned.has(q)) &&
            x.use.some((u) => e.use.includes(u)),
        )
        .sort((a, b) => Math.abs(a.level - e.level) - Math.abs(b.level - e.level))
        .slice(0, 8)
        .map((x) => ({
          ...exerciseView(x.id),
          relation: x.level < e.level ? "leichter" : x.level > e.level ? "schwerer" : "gleich",
        })),
    };
  });

  app.post<{ Params: { id: string }; Body: { block: number; item: number; ex: string } }>(
    "/fitness/session/:id/swap",
    async (req, reply) => {
      const s = getSession(db, req.params.id);
      const e = catalog.byId.get(req.body.ex);
      const it = s?.plan.blocks[req.body.block]?.items[req.body.item];
      if (!s || !e || !it) return reply.code(400).send({ error: "swap" });
      const old = catalog.byId.get(it.ex);
      it.ex = e.id;
      if (it.unit !== e.unit) {
        it.unit = e.unit;
        it.amount = e.unit === "sec" ? 30 : Math.max(5, Math.round(e.base));
      } else if (old && old.level !== e.level && e.unit === "reps") {
        it.amount = Math.max(3, Math.round(it.amount * (1 + (old.level - e.level) * 0.12)));
      }
      if (e.perSide) it.perSide = true;
      // biome-ignore lint/performance/noDelete: Feld soll im JSON ganz fehlen
      else delete it.perSide;
      db.prepare("UPDATE sessions SET plan = ? WHERE id = ?").run(JSON.stringify(s.plan), s.id);
      return sessionView(getSession(db, s.id) as NonNullable<ReturnType<typeof getSession>>);
    },
  );

  app.post<{ Params: { id: string } }>("/fitness/session/:id/start", async (req) => {
    db.prepare("UPDATE sessions SET status = 'active' WHERE id = ? AND status = 'planned'").run(
      req.params.id,
    );
    return { ok: true };
  });

  app.post<{ Params: { id: string }; Body: { result: SessionResult; feedback: SessionFeedback } }>(
    "/fitness/session/:id/finish",
    async (req, reply) => {
      const s = getSession(db, req.params.id);
      if (!s) return reply.code(404).send({ error: "not_found" });
      const result: SessionResult = {
        seconds: Math.max(0, Math.round(Number(req.body.result?.seconds) || 0)),
        done: req.body.result?.done ?? {},
        skipped: req.body.result?.skipped ?? [],
      };
      const fb: SessionFeedback = {
        overall: Math.min(5, Math.max(1, Math.round(Number(req.body.feedback?.overall) || 3))),
        ...(req.body.feedback?.energy ? { energy: req.body.feedback.energy } : {}),
        ...(req.body.feedback?.enjoyment ? { enjoyment: req.body.feedback.enjoyment } : {}),
        pain: (req.body.feedback?.pain ?? []).filter((p) => p.region in REGIONS),
        exercises: req.body.feedback?.exercises ?? [],
        ...(req.body.feedback?.note ? { note: String(req.body.feedback.note).slice(0, 600) } : {}),
      };
      const signals = applyFeedback(db, s, fb, result);
      // Auf-/Abstiege und Level (progression.ts) — gleich zurückgeben, die App zeigt sie im Abschluss.
      const pctx = buildContext(db, catalog, s.day);
      const progress = checkProgression(db, catalog, s.day, signals, (e) => usable(pctx, e));
      // Skill-Block im Training zählt auch im Skill-Protokoll (zwei erfolgreiche Tage → nächste Stufe).
      const skillNews: { skill: string; advanced: boolean; met: boolean }[] = [];
      s.plan.blocks.forEach((b, bi) => {
        if (b.kind !== "skill" || !b.skill) return;
        const sets = (result.done[`${bi}:0`] ?? []).filter((v) => typeof v === "number");
        if (!sets.length) return;
        try {
          const r = logSkill(db, catalog, b.skill.id, b.skill.step, sets, null);
          skillNews.push({
            skill: catalog.skillById.get(b.skill.id)?.name ?? b.skill.id,
            advanced: r.advanced,
            met: r.met,
          });
        } catch {
          /* Skill unbekannt */
        }
      });
      // Auswertung und nächster Plan laufen auf dem Server weiter — die App darf zu sein.
      startCoachJob(s, fb, result, skillNews);
      return { ok: true, job: coachJobView(s.id), skills: skillNews, progress };
    },
  );

  /** Stand der Auswertung nach dem Training (für die Fortschrittsseite). */
  app.get<{ Params: { id: string } }>("/fitness/session/:id/coach", async (req, reply) => {
    const v = coachJobView(req.params.id);
    return v ?? reply.code(404).send({ error: "not_found" });
  });

  /** Noch ein Training heute — wenn das geplante schon erledigt ist (der Coach berücksichtigt es). */
  app.post<{ Body: { dayType?: DayType; minutes?: number; energy?: number } }>(
    "/fitness/extra",
    async (req) => {
      const s = await extraSession(ai, db, catalog, {
        forceType: req.body.dayType,
        checkin: {
          ...(req.body.minutes ? { minutes: req.body.minutes } : {}),
          ...(req.body.energy ? { energy: req.body.energy } : {}),
        },
        log: deps.log,
      });
      return sessionView(s);
    },
  );

  /** Freies Training: Tagestyp und Dauer selbst gewählt (zählt wie eine Coach-Einheit). */
  app.post<{ Body: { dayType: DayType; minutes?: number } }>("/fitness/custom", async (req) => {
    const latest = db
      .prepare(
        "SELECT status FROM sessions WHERE day = ? AND kind = 'coach' ORDER BY created_at DESC, rowid DESC LIMIT 1",
      )
      .get(localDay()) as { status: string } | undefined;
    // Heute schon trainiert → zusätzliche Einheit statt die erledigte zurückzugeben.
    if (latest?.status === "done" || latest?.status === "active") {
      const extra = await extraSession(ai, db, catalog, {
        forceType: req.body.dayType,
        checkin: req.body.minutes ? { minutes: req.body.minutes } : undefined,
        log: deps.log,
      });
      return sessionView(extra);
    }
    const s = await todaySession(ai, db, catalog, {
      regenerate: true,
      wait: true,
      forceType: req.body.dayType,
      checkin: req.body.minutes ? { minutes: req.body.minutes } : undefined,
      log: deps.log,
    });
    return sessionView(s);
  });

  app.get<{ Querystring: { days?: string } }>("/fitness/history", async (req) => {
    const days = Math.min(365, Math.max(7, Number(req.query.days) || 90));
    const day = localDay();
    const rows = sessionsBetween(db, addDays(day, -days), day).filter((s) => s.status === "done");
    const notes = db
      .prepare("SELECT data FROM coach_notes WHERE kind = 'training' AND day >= ? ORDER BY at DESC")
      .all(addDays(day, -days)) as { data: string }[];
    const reviews = new Map(
      notes.map((n) => JSON.parse(n.data)).map((n: { session: string }) => [n.session, n]),
    );
    const bests = db
      .prepare(
        "SELECT exercise_id, best, times, dose FROM exercise_state WHERE best IS NOT NULL ORDER BY times DESC LIMIT 40",
      )
      .all() as { exercise_id: string; best: number; times: number; dose: number }[];
    const body = db
      .prepare(
        "SELECT day, weight FROM body_log WHERE weight IS NOT NULL AND day >= ? ORDER BY day",
      )
      .all(addDays(day, -days));
    return {
      sessions: rows.reverse().map((s) => ({
        id: s.id,
        day: s.day,
        title: s.plan.title,
        dayType: s.plan.dayType,
        intensity: s.plan.intensity,
        seconds: s.result?.seconds ?? 0,
        kcal: s.plan.kcal,
        overall: s.feedback?.overall ?? null,
        pain: s.feedback?.pain ?? [],
        review: reviews.get(s.id) ?? null,
      })),
      bests: bests.map((b) => ({
        ...b,
        name: catalog.byId.get(b.exercise_id)?.name ?? b.exercise_id,
        unit: catalog.byId.get(b.exercise_id)?.unit ?? "reps",
      })),
      body,
    };
  });

  // ── Kalender: jeder Tag antippbar, vergangene mit gespeicherten Auswertungen ──
  const reviewsFor = (day: string) =>
    new Map(
      (db.prepare("SELECT data FROM coach_notes WHERE kind = 'training' AND day = ? ORDER BY at").all(day) as { data: string }[])
        .map((n) => JSON.parse(n.data) as { session?: string })
        .filter((n) => n.session)
        .map((n) => [n.session as string, n]),
    );

  /** Markierungen je Tag (Woche oder Monat): trainiert, geplant, Trainingstag, gegessen. */
  app.get<{ Querystring: { from?: string; to?: string } }>("/fitness/days", async (req) => {
    const today = localDay();
    const to = dayParam(req.query.to, addDays(today, 6));
    let from = dayParam(req.query.from, addDays(to, -6));
    if (from > to || addDays(from, 62) < to) from = addDays(to, -6);
    const profile = getProfile(db);
    const sessions = sessionsBetween(db, from, to);
    const food = new Map(
      (db.prepare("SELECT day, COUNT(*) AS n FROM food_log WHERE day >= ? AND day <= ? GROUP BY day").all(from, to) as { day: string; n: number }[]).map((r) => [r.day, r.n]),
    );
    const mindMin = new Map(
      (db.prepare("SELECT day, SUM(seconds) AS s FROM mind_log WHERE day >= ? AND day <= ? GROUP BY day").all(from, to) as { day: string; s: number }[]).map((r) => [r.day, Math.round(r.s / 60)]),
    );
    const out = [];
    for (let d = from; d <= to; d = addDays(d, 1)) {
      const s = sessions.filter((x) => x.day === d);
      out.push({
        day: d,
        trainingDay: profile.trainingDays.includes(weekday(d)),
        done: s.filter((x) => x.status === "done").length,
        plan: s.some((x) => x.status === "planned" || x.status === "active"),
        minutes: s.filter((x) => x.status === "done").reduce((a, x) => a + Math.round((x.result?.seconds ?? 0) / 60), 0),
        meals: food.get(d) ?? 0,
        mind: mindMin.get(d) ?? 0,
      });
    }
    const first = db.prepare("SELECT MIN(day) AS d FROM (SELECT day FROM sessions WHERE status = 'done' UNION SELECT day FROM food_log)").get() as { d: string | null };
    return { today, days: out, first: first.d };
  });

  /** Alles zu einem Tag: Trainings mit gespeicherter Coach-Auswertung, Plan, Ernährung + gespeicherter Ernährungs-Auswertung. */
  app.get<{ Params: { day: string } }>("/fitness/day/:day", async (req, reply) => {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(req.params.day)) return reply.code(400).send({ error: "day" });
    const day = req.params.day;
    const today = localDay();
    const profile = getProfile(db);
    const reviews = reviewsFor(day);
    const sessions = sessionsBetween(db, day, day)
      .filter((s) => s.status !== "skipped" && (s.status === "done" || day >= today))
      .map((s) => ({
        id: s.id,
        status: s.status,
        kind: s.kind,
        title: s.plan.title,
        minutes: s.plan.minutes,
        kcal: s.plan.kcal,
        intensity: s.plan.intensity,
        coach: s.plan.coach?.message ?? null,
        seconds: s.result?.seconds ?? 0,
        overall: s.feedback?.overall ?? null,
        pain: s.feedback?.pain ?? [],
        note: (s.feedback as { note?: string } | null)?.note ?? null,
        doneAt: s.done_at ?? null,
        exercises: [...new Set(s.plan.blocks.flatMap((b) => b.items.map((i) => i.ex)))].length,
        review: reviews.get(s.id) ?? null,
      }));
    const entries = foodLogDay(db, day);
    const totals = roundNutrients(sumNutrients(entries.map((e) => e.totals)));
    const targets = computeTargets(profile);
    const rep = db.prepare("SELECT data FROM coach_notes WHERE kind = 'ernaehrung' AND day = ? ORDER BY at DESC LIMIT 1").get(day) as { data: string } | undefined;
    const skills = (db.prepare("SELECT skill_id, step, value, feel FROM skill_log WHERE day = ? ORDER BY at").all(day) as { skill_id: string; step: number; value: number; feel: number | null }[]).map((r) => {
      const sk = catalog.skills.find((x) => x.id === r.skill_id);
      return { skill: sk?.name ?? r.skill_id, step: sk?.steps[r.step]?.name ?? `Stufe ${r.step + 1}`, value: r.value, unit: sk?.steps[r.step]?.goal.type === "hold" ? "s" : "Wdh" };
    });
    const body = db.prepare("SELECT weight FROM body_log WHERE day = ?").get(day) as { weight: number | null } | undefined;
    const mind = db.prepare("SELECT kind, ref, seconds, mood_before, mood_after, note, at FROM mind_log WHERE day = ? ORDER BY at").all(day);
    return {
      day,
      today,
      mind,
      trainingDay: profile.trainingDays.includes(weekday(day)),
      sessions,
      food: {
        meals: entries.map((e) => ({ id: e.id, meal: e.meal, title: e.title, kcal: Math.round(e.totals.kcal ?? 0), photo: e.photo ?? null })),
        totals,
        targets: { kcal: targets.kcal, protein: targets.protein, carbs: targets.carbs, fat: targets.fat },
        report: rep ? JSON.parse(rep.data) : null,
      },
      skills,
      weight: body?.weight ?? null,
    };
  });

  // ── Verlauf (Tag/Woche/Monat/Jahr wie in Health-Apps) ───────────────────
  const dayParam = (v: unknown, fallback: string) => (typeof v === "string" && /^\d{4}-\d{2}-\d{2}$/.test(v) ? v : fallback);

  /** Training je Tag im Zeitraum: Minuten, Einheiten, kcal, Wiederholungen, Minuten je Stunde. */
  app.get<{ Querystring: { from?: string; to?: string } }>("/fitness/stats/training", async (req) => {
    const to = dayParam(req.query.to, localDay());
    const from = dayParam(req.query.from, addDays(to, -6));
    const days: Record<string, { minutes: number; sessions: number; kcal: number; reps: number; hours: Record<number, number>; titles: string[] }> = {};
    for (const s of sessionsBetween(db, from, to)) {
      if (s.status !== "done") continue;
      const d = (days[s.day] ??= { minutes: 0, sessions: 0, kcal: 0, reps: 0, hours: {}, titles: [] });
      const min = Math.round((s.result?.seconds ?? 0) / 60);
      d.minutes += min;
      d.sessions += 1;
      d.kcal += s.plan.kcal ?? 0;
      d.reps += Object.values(s.result?.done ?? {}).flat().reduce((a: number, v) => a + (Number(v) || 0), 0);
      const h = new Date(s.done_at ?? s.created_at).getHours();
      d.hours[h] = (d.hours[h] ?? 0) + min;
      d.titles.push(s.plan.title);
    }
    const first = db.prepare("SELECT MIN(day) AS d FROM sessions WHERE status = 'done'").get() as { d: string | null };
    return { from, to, days, first: first.d };
  });

  /** Ernährung je Tag im Zeitraum: alle Nährstoffe gesamt und je Mahlzeit, dazu die Ziele. */
  app.get<{ Querystring: { from?: string; to?: string } }>("/fitness/stats/food", async (req) => {
    const to = dayParam(req.query.to, localDay());
    const from = dayParam(req.query.from, addDays(to, -6));
    const rows = db.prepare("SELECT day, meal, totals FROM food_log WHERE day >= ? AND day <= ?").all(from, to) as { day: string; meal: string; totals: string }[];
    const days: Record<string, { totals: Nutrients; slots: Record<string, Nutrients>; meals: number }> = {};
    for (const r of rows) {
      const d = (days[r.day] ??= { totals: {}, slots: {}, meals: 0 });
      const t = JSON.parse(r.totals) as Nutrients;
      d.totals = sumNutrients([d.totals, t]);
      d.slots[r.meal] = sumNutrients([d.slots[r.meal] ?? {}, t]);
      d.meals += 1;
    }
    for (const d of Object.values(days)) d.totals = roundNutrients(d.totals);
    const first = db.prepare("SELECT MIN(day) AS d FROM food_log").get() as { d: string | null };
    // Noten des Ernährungs-Coachs je Tag (die jüngste Bewertung zählt) — für Diagramm und Kalender.
    const scores: Record<string, { score: number; headline: string }> = {};
    const notes = db
      .prepare("SELECT day, data FROM coach_notes WHERE kind = 'ernaehrung' AND day >= ? AND day <= ? ORDER BY at")
      .all(from, to) as { day: string; data: string }[];
    for (const n of notes) {
      try {
        const r = JSON.parse(n.data);
        if (typeof r.score === "number") scores[n.day] = { score: r.score, headline: r.headline ?? "" };
      } catch {
        /* kaputte Notiz überspringen */
      }
    }
    return { from, to, days, scores, first: first.d, targets: computeTargets(getProfile(db)), nutrients: NUTRIENTS };
  });

  /**
   * Ein Nährstoff im Detail: Wissen (backend/fitness/naehrstoffe.json), persönliche Rechnung für dein Ziel
   * und deine besten Quellen im Zeitraum (aus den Zutaten deines Tagebuchs).
   */
  const NUTRI_INFO = join(deps.dataDir, "naehrstoffe.json");
  app.get<{ Params: { k: string }; Querystring: { from?: string; to?: string } }>(
    "/fitness/food/nutrient/:k",
    async (req, reply) => {
      const meta = NUTRIENTS.find((n) => n.k === req.params.k);
      if (!meta) return reply.code(404).send({ error: "not_found" });
      const k = meta.k;
      const to = dayParam(req.query.to, localDay());
      const from = dayParam(req.query.from, addDays(to, -6));
      const info = existsSync(NUTRI_INFO) ? (JSON.parse(readFileSync(NUTRI_INFO, "utf8"))[k] ?? null) : null;
      const p = getProfile(db);
      const t = computeTargets(p);
      const g = t.goals[k] ?? {};
      const x = t.explain;
      const fmt = (v: number) => v.toLocaleString("de-DE", { maximumFractionDigits: 1 });
      const GOAL: Record<string, string> = { abnehmen: "Abnehmen", muskeln: "Muskelaufbau", zunehmen: "Zunehmen", halten: "Gewicht halten", fitter: "Fitter werden" };
      const rechnung: Record<string, string> = {
        kcal: `Grundumsatz ${fmt(x.bmr)} kcal × Alltag ${fmt(x.pal)} + Training im Schnitt ${fmt(x.training)} kcal am Tag ${x.delta >= 0 ? "+" : "−"} ${fmt(Math.abs(x.delta))} kcal für »${GOAL[p.goal] ?? p.goal}« = ${fmt(t.kcal)} kcal`,
        protein: `${fmt(x.proteinPerKg)} g je Kilo × ${fmt(x.refWeight)} kg Bezugsgewicht = ${fmt(t.protein)} g — für »${GOAL[p.goal] ?? p.goal}«`,
        fat: `28 % deiner Energie, mindestens 0,8 g je Kilo Körpergewicht = ${fmt(t.fat)} g`,
        carbs: `Was nach Eiweiß und Fett von deinen ${fmt(t.kcal)} kcal übrig bleibt = ${fmt(t.carbs)} g`,
        sugar: `Höchstens 10 % deiner ${fmt(t.kcal)} kcal als freier Zucker (WHO, DGE) = ${fmt(g.max ?? 0)} g`,
        satfat: `Höchstens 10 % deiner ${fmt(t.kcal)} kcal = ${fmt(g.max ?? 0)} g`,
      };
      const ref = g.min ?? g.max ?? null;
      const who = p.sex === "m" ? "Männer" : "Frauen";
      // Beste Quellen: Zutaten im Zeitraum, nach Beitrag zu diesem Nährstoff.
      const rows = db.prepare("SELECT items FROM food_log WHERE day >= ? AND day <= ?").all(from, to) as { items: string }[];
      const by = new Map<string, number>();
      let total = 0;
      for (const r of rows) {
        for (const it of JSON.parse(r.items) as { name: string; n?: Nutrients }[]) {
          const v = Number(it.n?.[k] ?? 0);
          if (!v) continue;
          by.set(it.name, (by.get(it.name) ?? 0) + v);
          total += v;
        }
      }
      const top = [...by.entries()].sort((a, b) => b[1] - a[1]).slice(0, 5)
        .map(([name, v]) => ({ name, value: Math.round(v * 10) / 10, share: total ? Math.round((v / total) * 100) : 0 }));
      return {
        ...meta,
        target: ref,
        isMax: g.max != null && g.min == null,
        rechnung: rechnung[k] ?? (ref != null ? `DGE-Referenzwert für ${who}: ${fmt(ref)} ${meta.unit} am Tag` : null),
        info,
        top,
        from,
        to,
      };
    },
  );

  app.post<{ Body: { weight?: number; waist?: number; day?: string } }>(
    "/fitness/body",
    async (req) => {
      const day = req.body.day ?? localDay();
      db.prepare(
        "INSERT INTO body_log(day, weight, waist) VALUES(?, ?, ?) ON CONFLICT(day) DO UPDATE SET weight = COALESCE(excluded.weight, weight), waist = COALESCE(excluded.waist, waist)",
      ).run(day, req.body.weight ?? null, req.body.waist ?? null);
      if (req.body.weight && day === localDay()) saveProfile(db, { weightKg: req.body.weight });
      return { ok: true };
    },
  );

  // ── Skills ──────────────────────────────────────────────────────────────
  app.get("/fitness/skills", async () => ({ skills: skillStates(db, catalog) }));
  app.post<{ Params: { id: string }; Body: { active?: boolean; step?: number } }>(
    "/fitness/skills/:id",
    async (req, reply) => {
      if (!catalog.skillById.has(req.params.id)) return reply.code(404).send({ error: "skill" });
      setSkill(db, req.params.id, req.body);
      return { skills: skillStates(db, catalog) };
    },
  );
  app.post<{ Params: { id: string }; Body: { step: number; sets: number[]; feel?: number } }>(
    "/fitness/skills/:id/log",
    async (req, reply) => {
      try {
        return logSkill(
          db,
          catalog,
          req.params.id,
          Number(req.body.step) || 0,
          req.body.sets ?? [],
          req.body.feel ?? null,
        );
      } catch (e) {
        return reply.code(400).send({ error: e instanceof Error ? e.message : "log" });
      }
    },
  );

  // ── Ernährung ───────────────────────────────────────────────────────────
  const dayData = (day: string) => {
    const entries = foodLogDay(db, day);
    const totals = roundNutrients(sumNutrients(entries.map((e) => e.totals)));
    const profile = getProfile(db);
    const targets = computeTargets(profile);
    return { day, entries, totals, targets, status: nutrientStatus(totals, targets) };
  };

  app.get<{ Querystring: { day?: string } }>("/fitness/food/day", async (req) =>
    dayData(req.query.day ?? localDay()),
  );

  app.get<{ Querystring: { q?: string } }>("/fitness/food/search", async (req) => ({
    items: ndb
      .search(String(req.query.q ?? ""), 20)
      .map((h) => ({ id: h.id, name: h.de ?? h.name, en: h.name, per100: h.per100 })),
  }));

  app.post<{ Body: { base64: string; mime?: string; hint?: string } }>(
    "/fitness/food/photo",
    { bodyLimit: 12 * 1024 * 1024 },
    async (req, reply) => {
      if (!ai.available()) return reply.code(503).send({ error: "no_key" });
      const base64 = String(req.body.base64 ?? "");
      if (base64.length < 1000) return reply.code(400).send({ error: "photo" });
      try {
        const res = await analyzePhoto(
          ai,
          ndb,
          { mime: req.body.mime ?? "image/jpeg", base64 },
          req.body.hint,
        );
        // Foto gleich ablegen — wird beim Speichern der Mahlzeit verknüpft.
        const photo = `${randomUUID().slice(0, 12)}.jpg`;
        writeFileSync(join(photoDir, photo), Buffer.from(base64, "base64"));
        return { ...res, photo };
      } catch (e) {
        deps.log(`[fitness] Foto: ${e instanceof Error ? e.message : e}`);
        return reply.code(502).send({ error: e instanceof Error ? e.message : "photo_failed" });
      }
    },
  );

  // Barcode → Produkt (Open Food Facts, weltweit, kostenlos).
  const barcodes = new BarcodeLookup(db);
  app.get<{ Params: { ean: string } }>("/fitness/food/barcode/:ean", async (req, reply) => {
    const ean = req.params.ean.replace(/\D/g, "");
    if (ean.length < 6 || ean.length > 14) return reply.code(400).send({ error: "ean" });
    try {
      const product = await barcodes.find(ean);
      return product ? { product } : reply.code(404).send({ error: "not_found", ean });
    } catch (e) {
      return reply
        .code(e instanceof Error && e.message === "rate" ? 429 : 502)
        .send({ error: "lookup_failed" });
    }
  });

  /**
   * Produkt ohne Datenbank-Eintrag: Nährwerttabelle (und gern die Vorderseite) fotografieren — die KI
   * liest die Werte je 100 g/ml ab; das Produkt gilt ab dann für diese Nummer (auch auf Reisen).
   */
  app.post<{ Body: { ean: string; base64: string; mime?: string; name?: string } }>(
    "/fitness/food/label",
    { bodyLimit: 12 * 1024 * 1024 },
    async (req, reply) => {
      const ean = String(req.body.ean ?? "").replace(/\D/g, "");
      if (ean.length < 6) return reply.code(400).send({ error: "ean" });
      if (!ai.available()) return reply.code(503).send({ error: "no_key" });
      try {
        const { value } = await ai.ask(
          {
            task: "vision",
            system:
              'Lies die Nährwerttabelle auf dem Foto einer Lebensmittelverpackung exakt ab (Sprache egal: Englisch, Malaiisch, Chinesisch, Thai …). Gib die Werte JE 100 g bzw. 100 ml an — steht nur "pro Portion" da, rechne mit der angegebenen Portionsgröße auf 100 um. Energie in kcal (kJ ÷ 4,184). Natrium in Salz umrechnen (Salz = Natrium × 2,5). Lies auch Produktname, Marke, Packungsgröße und Portionsgröße, falls sichtbar. Fehlende Werte = null, nichts erfinden. Antworte NUR mit JSON: {"readable":true,"name":"…","brand":"…","quantity":"…","serving_g":null,"per":"100g|100ml","per100":{"kcal":0,"protein":0,"carbs":0,"sugar":0,"fat":0,"satfat":0,"fiber":0,"salt":0,"calcium_mg":null,"iron_mg":null}}',
            input: req.body.name
              ? `Produktname laut Nutzer: ${req.body.name}`
              : "Lies die Nährwerttabelle.",
            image: { mime: req.body.mime ?? "image/jpeg", base64: String(req.body.base64 ?? "") },
            maxTokens: 1500,
            temperature: 0.1,
            timeoutMs: 60_000,
          },
          (raw) => raw as Record<string, unknown>,
        );
        if (!value.readable) return reply.code(422).send({ error: "unreadable" });
        const p100 = (value.per100 ?? {}) as Record<string, unknown>;
        const per100: Nutrients = {};
        for (const k of [
          "kcal",
          "protein",
          "carbs",
          "sugar",
          "fat",
          "satfat",
          "fiber",
          "salt",
        ] as const) {
          const v = Number(p100[k]);
          if (p100[k] !== null && Number.isFinite(v) && v >= 0) per100[k] = v;
        }
        if (Number(p100.calcium_mg) > 0) per100.calcium = Number(p100.calcium_mg);
        if (Number(p100.iron_mg) > 0) per100.iron = Number(p100.iron_mg);
        if (per100.kcal === undefined) return reply.code(422).send({ error: "unreadable" });
        const quantity = value.quantity ? String(value.quantity) : null;
        const product = {
          ean,
          name: String(req.body.name || value.name || `Produkt ${ean}`).slice(0, 100),
          brand: value.brand ? String(value.brand).slice(0, 60) : null,
          quantity,
          serving: Number(value.serving_g) > 0 ? Number(value.serving_g) : null,
          servingLabel: null,
          pack: null,
          image: null,
          per100,
          liquid: value.per === "100ml",
          nutriscore: null,
          source: "eigen" as const,
        };
        barcodes.save(product);
        return { product };
      } catch (e) {
        return reply.code(502).send({ error: e instanceof Error ? e.message : "label_failed" });
      }
    },
  );

  /** Freitext (»2 Eier, 1 Scheibe Vollkornbrot mit Butter«) → Zutaten mit Gramm + Nährwerte. */
  app.post<{ Body: { text: string } }>("/fitness/food/text", async (req, reply) => {
    const text = String(req.body.text ?? "").slice(0, 600);
    if (!text.trim()) return reply.code(400).send({ error: "text" });
    if (!ai.available()) return reply.code(503).send({ error: "no_key" });
    try {
      const { value } = await ai.ask(
        {
          task: "text",
          system:
            'Zerlege die Beschreibung einer Mahlzeit in Zutaten mit realistischen Gramm-Mengen (Standardportionen, wenn nichts angegeben). Für jede Zutat: deutscher Name und Suchbegriff wie im Bundeslebensmittelschlüssel (mit Zustand, z. B. "Hühnerei gekocht"). Antworte NUR mit JSON: {"title":"kurzer Name der Mahlzeit","items":[{"name":"…","query":"…","grams":0}]}',
          input: text,
          maxTokens: 1500,
          temperature: 0.2,
        },
        (raw) => {
          const r = raw as {
            title?: string;
            items?: { name?: string; query?: string; grams?: number }[];
          };
          const items = (r.items ?? [])
            .filter((i) => i.name && Number(i.grams) > 0)
            .map((i) => ({
              name: String(i.name),
              query: String(i.query || i.name),
              grams: Number(i.grams),
            }));
          if (!items.length) throw new Error("leer");
          return { title: String(r.title || text).slice(0, 80), items };
        },
      );
      const items = await resolveItems(ai, ndb, value.items);
      return {
        title: value.title,
        items,
        totals: roundNutrients(sumNutrients(items.map((i) => i.n))),
      };
    } catch (e) {
      return reply.code(502).send({ error: e instanceof Error ? e.message : "text_failed" });
    }
  });

  /** Zutat neu berechnen (andere Grammzahl oder anderer Datenbank-Eintrag). */
  app.post<{ Body: { food?: string; grams: number; per100?: Nutrients } }>(
    "/fitness/food/item",
    async (req) => {
      const hit = req.body.food ? ndb.get(req.body.food) : null;
      const per100 = hit?.per100 ?? cleanNutrients(req.body.per100);
      const grams = Math.max(1, Math.round(Number(req.body.grams) || 1));
      return {
        grams,
        per100,
        n: roundNutrients(scaleNutrients(per100, grams)),
        food: hit ? { id: hit.id, name: hit.de ?? hit.name, source: hit.source } : null,
      };
    },
  );

  app.post<{
    Body: {
      id?: string;
      day?: string;
      meal: string;
      title: string;
      source: string;
      photo?: string;
      recipeId?: string;
      items: MealItem[];
    };
  }>("/fitness/food/log", async (req, reply) => {
    const b = req.body;
    if (!SLOTS.includes(b.meal as MealSlot)) return reply.code(400).send({ error: "meal" });
    const items = (b.items ?? []).slice(0, 40).map((i) => ({
      ...i,
      grams: Math.max(0, Math.round(Number(i.grams) || 0)),
      n: roundNutrients(
        i.per100 ? scaleNutrients(i.per100, Number(i.grams) || 0) : cleanNutrients(i.n),
      ),
    }));
    const totals = roundNutrients(sumNutrients(items.map((i) => i.n)));
    const id = b.id ?? randomUUID();
    const photo = b.photo && /^[a-f0-9-]{6,40}\.jpg$/.test(b.photo) ? b.photo : null;
    db.prepare(
      `INSERT INTO food_log(id, day, meal, title, source, photo, recipe_id, items, totals, created_at) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
       ON CONFLICT(id) DO UPDATE SET meal = excluded.meal, title = excluded.title, items = excluded.items, totals = excluded.totals`,
    ).run(
      id,
      b.day ?? localDay(),
      b.meal,
      String(b.title ?? "Mahlzeit").slice(0, 80),
      String(b.source ?? "frei"),
      photo,
      b.recipeId ?? null,
      JSON.stringify(items),
      JSON.stringify(totals),
      Date.now(),
    );
    if (b.recipeId)
      db.prepare(
        "INSERT INTO recipe_state(recipe_id, cooked, updated_at) VALUES(?, 1, ?) ON CONFLICT(recipe_id) DO UPDATE SET cooked = cooked + 1, updated_at = excluded.updated_at",
      ).run(b.recipeId, Date.now());
    return dayData(b.day ?? localDay());
  });

  app.post<{ Params: { id: string } }>("/fitness/food/log/:id/delete", async (req) => {
    const row = db.prepare("SELECT day FROM food_log WHERE id = ?").get(req.params.id) as
      | { day: string }
      | undefined;
    db.prepare("DELETE FROM food_log WHERE id = ?").run(req.params.id);
    return dayData(row?.day ?? localDay());
  });

  app.get<{ Params: { file: string } }>("/fitness/photo/:file", async (req, reply) => {
    if (!/^[a-f0-9-]{6,40}\.jpg$/.test(req.params.file))
      return reply.code(400).send({ error: "file" });
    const file = join(photoDir, req.params.file);
    if (!existsSync(file)) return reply.code(404).send({ error: "not_found" });
    return sendFile(reply, file, "image/jpeg");
  });

  /** Ernährungs-Coach für einen Tag — zwischengespeichert, solange sich das Tagebuch nicht ändert. */
  app.get<{ Querystring: { day?: string; force?: string; cached?: string } }>(
    "/fitness/food/report",
    async (req, reply) => {
      const day = req.query.day ?? localDay();
      // Nur lesen (Verlauf): gespeicherte Bewertung zurückgeben, nie neu rechnen — das kostet Gemini-Anfragen.
      if (req.query.cached) {
        const c = db
          .prepare("SELECT data FROM coach_notes WHERE kind = 'ernaehrung' AND day = ? ORDER BY at DESC LIMIT 1")
          .get(day) as { data: string } | undefined;
        return { report: c ? JSON.parse(c.data) : null };
      }
      const d = dayData(day);
      if (!d.entries.length) return { report: null, reason: "leer" };
      const hash = createHash("sha1")
        .update(JSON.stringify(d.entries.map((e) => [e.id, e.items.map((i) => [i.name, i.grams])])))
        .digest("hex")
        .slice(0, 12);
      const cached = db
        .prepare(
          "SELECT data FROM coach_notes WHERE kind = 'ernaehrung' AND day = ? ORDER BY at DESC LIMIT 1",
        )
        .get(day) as { data: string } | undefined;
      if (cached && !req.query.force) {
        const c = JSON.parse(cached.data);
        if (c.hash === hash && (day < localDay() || Date.now() - (c.at ?? 0) < 3 * 3600_000))
          return { report: c };
      }
      if (!ai.available()) return reply.code(503).send({ error: "no_key" });
      const profile = getProfile(db);
      const training = sessionsBetween(db, day, day).find((s) => s.status === "done");
      const last7 = Array.from({ length: 7 }, (_, i) => addDays(day, -i - 1))
        .map((dd) => {
          const e = foodLogDay(db, dd);
          if (!e.length) return null;
          const t = sumNutrients(e.map((x) => x.totals));
          return {
            tag: dd,
            kcal: Math.round(t.kcal ?? 0),
            eiweiss: Math.round(t.protein ?? 0),
            mahlzeiten: e.map((x) => x.title),
          };
        })
        .filter(Boolean);
      try {
        const now = new Date();
        const r = await dayReport(ai, {
          day,
          time: `${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}`,
          dayOver: day < localDay() || now.getHours() >= 21,
          profile: {
            ziel: profile.goal,
            gewicht: profile.weightKg,
            ernaehrung: profile.diet,
            allergien: profile.allergies,
          },
          targets: d.targets,
          meals: d.entries.map((e) => ({
            meal: SLOT_NAMES[e.meal] ?? e.meal,
            title: e.title,
            items: e.items.map((i) => ({ name: i.name, grams: i.grams })),
          })),
          totals: d.totals,
          training: training ? { titel: training.plan.title, kcal: training.plan.kcal } : null,
          last7,
        });
        const data = { ...r, hash, at: Date.now() };
        db.prepare(
          "INSERT INTO coach_notes(kind, day, data, model, at) VALUES('ernaehrung', ?, ?, ?, ?)",
        ).run(day, JSON.stringify(data), r.model, Date.now());
        return { report: data };
      } catch (e) {
        return reply.code(502).send({ error: e instanceof Error ? e.message : "report_failed" });
      }
    },
  );

  app.get("/fitness/food/week", async () => {
    const day = localDay();
    const targets = computeTargets(getProfile(db));
    return {
      targets,
      days: Array.from({ length: 14 }, (_, i) => addDays(day, -13 + i)).map((d) => {
        const e = foodLogDay(db, d);
        const t = roundNutrients(sumNutrients(e.map((x) => x.totals)));
        return { day: d, meals: e.length, totals: t };
      }),
    };
  });

  // ── Rezepte ─────────────────────────────────────────────────────────────
  const recipeCard = (
    r: ReturnType<typeof allRecipes>[number],
    st?: { favorite: boolean; rating: number | null; cooked: number },
  ) => ({
    id: r.id,
    title: r.title,
    image: r.image,
    minutes: r.minutes,
    difficulty: r.difficulty,
    kcal: r.n.kcal ?? 0,
    protein: r.n.protein ?? 0,
    carbs: r.n.carbs ?? 0,
    fat: r.n.fat ?? 0,
    meals: r.meals,
    diet: r.diet,
    tags: r.tags,
    video: !!r.video,
    favorite: !!st?.favorite,
    cuisine: r.cuisine,
  });

  app.get<{ Querystring: { q?: string; meal?: string; tag?: string; fav?: string; all?: string } }>(
    "/fitness/recipes",
    async (req) => {
      const p = getProfile(db);
      const targets = computeTargets(p);
      const states = recipeStates(db);
      const q = String(req.query.q ?? "")
        .toLowerCase()
        .trim();
      let list = allRecipes(db).filter(
        (r) => !states.get(r.id)?.hidden && (req.query.all || fitsProfile(r, p)),
      );
      if (q)
        list = list.filter(
          (r) =>
            r.title.toLowerCase().includes(q) ||
            r.ingredients.some((i) => i.name.toLowerCase().includes(q)) ||
            r.cuisine.toLowerCase().includes(q),
        );
      if (req.query.meal) list = list.filter((r) => r.meals.includes(req.query.meal as MealSlot));
      if (req.query.tag)
        list = list.filter(
          (r) => r.tags.includes(String(req.query.tag)) || r.diet === req.query.tag,
        );
      if (req.query.fav) list = list.filter((r) => states.get(r.id)?.favorite);
      const slot = (
        SLOTS.includes(req.query.meal as MealSlot) ? req.query.meal : "abend"
      ) as MealSlot;
      list.sort(
        (a, b) =>
          scoreRecipe(b, slot, targets, p, states.get(b.id)) -
          scoreRecipe(a, slot, targets, p, states.get(a.id)),
      );
      return {
        total: list.length,
        status: importer.status(),
        items: list.slice(0, 200).map((r) => recipeCard(r, states.get(r.id))),
      };
    },
  );

  app.get<{ Params: { id: string } }>("/fitness/recipes/:id", async (req, reply) => {
    const r = getRecipe(db, req.params.id);
    if (!r) return reply.code(404).send({ error: "not_found" });
    const targets = computeTargets(getProfile(db));
    const slot = (r.meals.find((m) => m !== "dessert") ?? "abend") as MealSlot;
    return {
      recipe: withImages(r),
      state: recipeStates(db).get(r.id) ?? null,
      suggestedFactor: portionFactor(r, slot, targets),
      targets,
    };
  });

  app.post<{
    Params: { id: string };
    Body: { favorite?: boolean; rating?: number; hidden?: boolean };
  }>("/fitness/recipes/:id/state", async (req) => {
    const b = req.body;
    db.prepare(
      "INSERT INTO recipe_state(recipe_id, updated_at) VALUES(?, ?) ON CONFLICT(recipe_id) DO NOTHING",
    ).run(req.params.id, Date.now());
    if (b.favorite !== undefined)
      db.prepare("UPDATE recipe_state SET favorite = ?, updated_at = ? WHERE recipe_id = ?").run(
        b.favorite ? 1 : 0,
        Date.now(),
        req.params.id,
      );
    if (b.rating !== undefined)
      db.prepare("UPDATE recipe_state SET rating = ?, updated_at = ? WHERE recipe_id = ?").run(
        Math.min(5, Math.max(1, Math.round(b.rating))),
        Date.now(),
        req.params.id,
      );
    if (b.hidden !== undefined)
      db.prepare("UPDATE recipe_state SET hidden = ?, updated_at = ? WHERE recipe_id = ?").run(
        b.hidden ? 1 : 0,
        Date.now(),
        req.params.id,
      );
    return { state: recipeStates(db).get(req.params.id) ?? null };
  });

  /**
   * Tagesplan: je Mahlzeit der beste Vorschlag (passend zu Zielen, Vorlieben, Restbedarf) plus zwei
   * Alternativen. Getauschte Vorschläge merken wir uns pro Tag.
   */
  app.get<{ Querystring: { day?: string } }>("/fitness/mealplan", async (req) => {
    const day = req.query.day ?? localDay();
    const p = getProfile(db);
    const targets = computeTargets(p);
    const states = recipeStates(db);
    const pool = allRecipes(db).filter((r) => fitsProfile(r, p) && !states.get(r.id)?.hidden);
    const chosen = kvGet<Record<string, string>>(db, `mealplan:${day}`) ?? {};
    // In den letzten 5 Tagen schon vorgeschlagene/gegessene Rezepte nach hinten.
    const recent = new Set<string>();
    for (let i = 1; i <= 5; i++)
      for (const v of Object.values(
        kvGet<Record<string, string>>(db, `mealplan:${addDays(day, -i)}`) ?? {},
      ))
        recent.add(v);
    const used = new Set<string>();
    const slots = SLOTS.filter((s) => s !== "snack" || p.mealsPerDay > 3).map((slot) => {
      // Frühstück und Snack nur aus Rezepten, die dafür gedacht sind (keine Paella zum Frühstück).
      const ranked = pool
        .filter((r) =>
          slot === "fruehstueck" || slot === "snack"
            ? r.meals.includes(slot)
            : !r.meals.includes("dessert") || r.meals.length > 1,
        )
        .map((r) => ({
          r,
          s:
            scoreRecipe(r, slot, targets, p, states.get(r.id)) -
            (recent.has(r.id) ? 2 : 0) +
            seededJitter(`${day}:${slot}:${r.id}`),
        }))
        .sort((a, b) => b.s - a.s)
        .filter((x) => !used.has(x.r.id))
        .slice(0, 12);
      const pick = (chosen[slot] && pool.find((r) => r.id === chosen[slot])) || ranked[0]?.r;
      if (pick) used.add(pick.id);
      return {
        slot,
        name: SLOT_NAMES[slot],
        recipe: pick
          ? { ...recipeCard(pick, states.get(pick.id)), factor: portionFactor(pick, slot, targets) }
          : null,
        alternatives: ranked
          .filter((x) => x.r.id !== pick?.id)
          .slice(0, 6)
          .map((x) => ({
            ...recipeCard(x.r, states.get(x.r.id)),
            factor: portionFactor(x.r, slot, targets),
          })),
      };
    });
    const sum = sumNutrients(
      slots
        .filter((s) => s.recipe)
        .map((s) => ({
          kcal: (s.recipe?.kcal ?? 0) * (s.recipe?.factor ?? 1),
          protein: (s.recipe?.protein ?? 0) * (s.recipe?.factor ?? 1),
        })),
    );
    return { day, targets, slots, planned: roundNutrients(sum), status: importer.status() };
  });

  app.post<{ Body: { day?: string; slot: string; recipeId: string } }>(
    "/fitness/mealplan/choose",
    async (req) => {
      const day = req.body.day ?? localDay();
      const cur = kvGet<Record<string, string>>(db, `mealplan:${day}`) ?? {};
      cur[req.body.slot] = req.body.recipeId;
      kvSet(db, `mealplan:${day}`, cur);
      return { ok: true };
    },
  );

  /** Einkaufsliste für die nächsten N Tage aus dem Tagesplan (Mengen zusammengezählt). */
  app.get<{ Querystring: { days?: string } }>("/fitness/shopping", async (req) => {
    const days = Math.min(7, Math.max(1, Number(req.query.days) || 3));
    const p = getProfile(db);
    const targets = computeTargets(p);
    const items = new Map<
      string,
      { name: string; grams: number; image: string | null; recipes: string[] }
    >();
    const checked = kvGet<string[]>(db, "shopping:checked") ?? [];
    for (let i = 0; i < days; i++) {
      const day = addDays(localDay(), i);
      const chosen = kvGet<Record<string, string>>(db, `mealplan:${day}`) ?? {};
      for (const [slot, id] of Object.entries(chosen)) {
        const r = getRecipe(db, id);
        if (!r) continue;
        const f = portionFactor(r, slot as MealSlot, targets) / r.servings;
        for (const ing of r.ingredients) {
          const k = ing.name.toLowerCase();
          const cur = items.get(k) ?? {
            name: ing.name,
            grams: 0,
            image: images.image(ing.en, ing.name),
            recipes: [] as string[],
          };
          cur.grams += ing.grams * f;
          if (!cur.recipes.includes(r.title)) cur.recipes.push(r.title);
          items.set(k, cur);
        }
      }
    }
    return {
      days,
      items: [...items.values()]
        .map((x) => ({
          ...x,
          grams: Math.ceil(x.grams / 5) * 5,
          checked: checked.includes(x.name.toLowerCase()),
        }))
        .sort((a, b) => a.name.localeCompare(b.name, "de")),
    };
  });

  app.post<{ Body: { name: string; checked: boolean } }>("/fitness/shopping/check", async (req) => {
    const cur = new Set(kvGet<string[]>(db, "shopping:checked") ?? []);
    if (req.body.checked) cur.add(req.body.name.toLowerCase());
    else cur.delete(req.body.name.toLowerCase());
    kvSet(db, "shopping:checked", [...cur]);
    return { ok: true };
  });

  /** Bilder von TheMealDB einmal laden und lokal ausliefern (schneller, offline-fähig). */
  app.get<{ Querystring: { u: string } }>("/fitness/img", async (req, reply) => {
    const u = String(req.query.u ?? "");
    const mealdb =
      /^https:\/\/www\.themealdb\.com\/images\/[\w./%()-]+\.(jpg|png)(\/(small|medium|large|preview))?$/i.test(
        u,
      );
    const lecker = /^https:\/\/(images|cdn)\.lecker\.de\/[^\s"'<>]+$/i.test(u);
    const spoon =
      /^https:\/\/img\.spoonacular\.com\/ingredients_\d+x\d+\/[a-z0-9-]+\.(jpg|png)$/i.test(u);
    const off =
      /^https:\/\/images\.openfoodfacts\.(org|net)\/images\/products\/[\w./-]+\.(jpg|png|webp)$/i.test(
        u,
      );
    // Vorschaubilder der Achtsamkeits-Videos (YouTube).
    const yt = /^https:\/\/i\.ytimg\.com\/vi\/[\w-]{11}\/(mq|hq|sd|maxres)default\.jpg$/i.test(u);
    if (!mealdb && !lecker && !spoon && !off && !yt) return reply.code(400).send({ error: "url" });
    const base = join(imgDir, createHash("sha1").update(u).digest("hex"));
    const TYPES: Record<string, string> = {
      jpg: "image/jpeg",
      png: "image/png",
      webp: "image/webp",
      avif: "image/avif",
    };
    let ext = Object.keys(TYPES).find((e) => existsSync(`${base}.${e}`));
    if (!ext) {
      try {
        const r = await fetch(u, {
          headers: { "user-agent": "Hikari-Fitness/1.0 (private)" },
          signal: AbortSignal.timeout(15_000),
        });
        if (!r.ok) return reply.code(r.status === 404 ? 404 : 502).send({ error: "upstream" });
        const type = r.headers.get("content-type") ?? "";
        ext = type.includes("png")
          ? "png"
          : type.includes("webp")
            ? "webp"
            : type.includes("avif")
              ? "avif"
              : "jpg";
        writeFileSync(`${base}.${ext}`, Buffer.from(await r.arrayBuffer()));
      } catch {
        return reply.code(502).send({ error: "upstream" });
      }
    }
    return sendFile(reply, `${base}.${ext}`, TYPES[ext] ?? "image/jpeg");
  });

  app.post("/fitness/recipes/import", async () => {
    void importer.run();
    return importer.status();
  });

  return { db, catalog, ai, importer };
}

function seededJitter(s: string): number {
  let h = 0;
  for (const c of s) h = (Math.imul(h, 31) + c.charCodeAt(0)) | 0;
  return ((h >>> 0) % 1000) / 1000;
}

/** Serie: aufeinanderfolgende geplante Trainingstage (Ruhetage zählen nicht dagegen), an denen trainiert wurde. */
function trainingStreak(db: FitDb, day: string, trainingDays: number[]): number {
  const done = new Set(
    (
      db
        .prepare("SELECT DISTINCT day FROM sessions WHERE status = 'done' AND day >= ?")
        .all(addDays(day, -400)) as { day: string }[]
    ).map((r) => r.day),
  );
  let streak = 0;
  for (let i = 0; i < 400; i++) {
    const d = addDays(day, -i);
    const planned = trainingDays.includes(weekday(d));
    if (done.has(d)) streak++;
    else if (planned && i > 0) break;
  }
  return streak;
}
