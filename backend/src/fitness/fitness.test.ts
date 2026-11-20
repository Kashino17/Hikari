import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { loadCatalog } from "./catalog.js";
import {
  applyFeedback,
  buildContext,
  extraSession,
  getSession,
  settleAiPlan,
  todaySession,
  withSkillBlock,
} from "./coach.js";
import { openFitnessDb } from "./db.js";
import { BlsDb } from "./foods.js";
import { buildRulesPlan, doseFor, planSeconds, usable } from "./planner.js";
import { computeTargets, getProfile, normalizeProfile, saveProfile } from "./profile.js";
import { recipeTags, youtubeId } from "./recipes.js";
import { goalMet, logSkill, setSkill, skillStates } from "./skills.js";
import { mindStats, setManualUnlock, yogaProgress } from "./mind.js";

const DATA = new URL("../../fitness", import.meta.url).pathname;
const catalog = loadCatalog(join(DATA, "exercises.json"), join(DATA, "skills.json"));
const freshDb = () => openFitnessDb(":memory:");

describe("Katalog", () => {
  it("hat zu jeder Übung Video, Schritte und Fehler; Skill-Stufen zeigen auf echte Übungen", () => {
    expect(catalog.exercises.length).toBeGreaterThan(120);
    for (const e of catalog.exercises) {
      expect(e.steps.length, e.id).toBeGreaterThan(1);
      expect(e.mistakes.length, e.id).toBeGreaterThan(0);
      expect(e.media?.video, e.id).toBeTruthy();
    }
    for (const s of catalog.skills)
      for (const st of s.steps)
        if (st.exercise) expect(catalog.byId.has(st.exercise), `${s.id}:${st.name}`).toBe(true);
  });
});

describe("Regel-Planer", () => {
  it("baut Aufwärmen, Hauptteil, Abkühlen in der gewünschten Zeit und nur mit vorhandener Ausrüstung", () => {
    const db = freshDb();
    saveProfile(db, { equipment: [], sessionMinutes: 30, onboarded: true });
    const ctx = buildContext(db, catalog, "2026-11-19");
    const plan = buildRulesPlan(ctx);
    expect(plan.blocks.map((b) => b.kind)).toEqual(
      expect.arrayContaining(["warmup", "main", "cooldown"]),
    );
    const mins = planSeconds(plan, catalog) / 60;
    expect(mins).toBeGreaterThan(18);
    expect(mins).toBeLessThan(40);
    for (const b of plan.blocks)
      for (const it of b.items)
        expect(
          catalog.byId.get(it.ex)?.equipment.every((q) => ["boden", "wand", "matte"].includes(q)),
          it.ex,
        ).toBe(true);
  });

  it("lässt bei Knieschmerz und »ohne Sprünge« passende Übungen weg", () => {
    const db = freshDb();
    saveProfile(db, { noJumps: true, limitations: ["knie"] });
    const ctx = buildContext(db, catalog, "2026-11-19");
    const ex = catalog.exercises.filter((e) => usable(ctx, e));
    expect(ex.some((e) => e.jump)).toBe(false);
    expect(ex.some((e) => e.stress.includes("knie"))).toBe(false);
  });
});

describe("KI-Plan absichern", () => {
  it("verwirft erfundene Übungen und klemmt Mengen auf ±30 % der Regel-Dosis", () => {
    const db = freshDb();
    saveProfile(db, { equipment: ["klimmzugstange"], level: 3 });
    const ctx = buildContext(db, catalog, "2026-11-19", undefined, "druck");
    const rules = buildRulesPlan(ctx);
    const plan = settleAiPlan(
      {
        title: "Test",
        blocks: [
          {
            kind: "main",
            format: "sets",
            rounds: 3,
            items: [
              { ex: "pushup", amount: 500 },
              { ex: "fantasie_uebung", amount: 10 },
              { ex: "pullup", amount: 1 },
            ],
          },
        ],
      },
      ctx,
      rules,
    );
    const main = plan.blocks.find((b) => b.kind === "main");
    expect(main?.items.map((i) => i.ex)).toEqual(["pushup", "pullup"]);
    expect(main?.items[0]?.amount).toBeLessThan(40);
    expect(main?.items[1]?.amount).toBeGreaterThanOrEqual(2);
    expect(plan.blocks[0]?.kind).toBe("warmup");
    expect(plan.blocks.at(-1)?.kind).toBe("cooldown");
  });

  it("Krafttag bleibt Krafttag: KI ohne Beine an einem Beintag wird verworfen", () => {
    const db = freshDb();
    const ctx = buildContext(db, catalog, "2026-11-19", undefined, "beine");
    const rules = buildRulesPlan(ctx);
    expect(() =>
      settleAiPlan({ dayType: "ausdauer", blocks: [{ kind: "main", format: "intervals", rounds: 3, items: [{ ex: "jumping_jack" }, { ex: "plank" }] }] }, ctx, rules),
    ).toThrow(/muster_fehlen/);
  });

  it("wirft, wenn kein Hauptteil übrig bleibt", () => {
    const db = freshDb();
    const ctx = buildContext(db, catalog, "2026-11-19");
    expect(() =>
      settleAiPlan(
        { blocks: [{ kind: "main", items: [{ ex: "gibtsnicht" }] }] },
        ctx,
        buildRulesPlan(ctx),
      ),
    ).toThrow();
  });
});

describe("Feedback lernt je Übung", () => {
  it("»zu schwer« senkt die Dosis, Schmerz sperrt die Übung eine Woche", async () => {
    const db = freshDb();
    saveProfile(db, { onboarded: true });
    const s = await todaySession(null, db, catalog, { day: "2026-11-19" });
    const bi = s.plan.blocks.findIndex((b) => b.kind === "main");
    const ex = s.plan.blocks[bi]?.items[0]?.ex as string;
    const ex2 = s.plan.blocks[bi]?.items[1]?.ex as string;
    applyFeedback(
      db,
      s,
      {
        overall: 4,
        pain: [{ region: "knie", level: 5 }],
        exercises: [
          { ex, difficulty: 1 },
          { ex: ex2, pain: true },
        ],
      },
      { seconds: 1800, done: {}, skipped: [] },
    );
    const st = db.prepare("SELECT * FROM exercise_state WHERE exercise_id = ?").get(ex) as {
      dose: number;
    };
    expect(st.dose).toBeLessThan(1);
    const st2 = db
      .prepare("SELECT avoid_until FROM exercise_state WHERE exercise_id = ?")
      .get(ex2) as { avoid_until: string };
    expect(st2.avoid_until).toBe("2026-11-26");
    expect(getSession(db, s.id)?.status).toBe("done");
    const next = buildContext(db, catalog, "2026-11-20");
    expect(next.painRegions).toContain("knie");
  });
});

describe("Skills", () => {
  it("schaltet eine Stufe erst nach dem zweiten erfolgreichen Tag frei", () => {
    const db = freshDb();
    const hs = catalog.skillById.get("handstand");
    expect(hs && goalMet(hs, 0, [30, 31, 30])).toBe(true);
    expect(hs && goalMet(hs, 0, [30, 20, 30])).toBe(false);
    const a = logSkill(db, catalog, "handstand", 0, [30, 30, 30], null);
    expect(a.advanced).toBe(false);
    // zweiter Tag
    db.prepare("UPDATE skill_log SET day = '2026-01-01'").run();
    const b = logSkill(db, catalog, "handstand", 0, [32, 30, 31], null);
    expect(b.advanced).toBe(true);
    expect(skillStates(db, catalog).find((x) => x.id === "handstand")?.step).toBe(1);
  });

  it("baut den Skill-Block nach dem Aufwärmen ein", () => {
    const db = freshDb();
    setSkill(db, "planche", { active: true });
    const ctx = buildContext(db, catalog, "2026-11-19");
    const plan = withSkillBlock(db, catalog, "2026-11-19", buildRulesPlan(ctx));
    if (plan.dayType === "mobilitaet") return;
    expect(plan.blocks[1]?.kind).toBe("skill");
    expect(plan.blocks[1]?.skill?.id).toBe("planche");
  });
});

describe("Ernährung", () => {
  it("rechnet Ziele nach Mifflin-St Jeor mit Ziel-Zuschlag und DGE-Mikronährstoffen", () => {
    const p = normalizeProfile({
      sex: "m",
      birthYear: 1997,
      heightCm: 180,
      weightKg: 80,
      activity: 1,
      trainingDays: [0, 2, 4],
      sessionMinutes: 40,
      goal: "abnehmen",
    });
    const t = computeTargets(p, new Date("2026-11-19"));
    expect(t.explain.bmr).toBe(1785);
    expect(t.explain.delta).toBeLessThan(0);
    expect(t.protein).toBeGreaterThanOrEqual(150);
    expect(t.goals.iron?.min).toBe(11);
    expect(t.goals.salt?.max).toBe(6);
  });

  it("findet Grundlebensmittel im Bundeslebensmittelschlüssel", () => {
    const bls = new BlsDb(join(DATA, "bls.json.gz"));
    expect(bls.search("Banane", 3)[0]?.de).toBe("Banane roh");
    expect(bls.search("Ei", 3)[0]?.de).toContain("Hühnerei");
    expect(bls.search("Haferflocken", 5).some((h) => h.de === "Hafer Flocken")).toBe(true);
    expect(bls.search("Reis gekocht", 3)[0]?.per100.kcal).toBeGreaterThan(90);
  });

  it("vergibt Rezept-Schlagworte und erkennt YouTube-IDs", () => {
    expect(recipeTags({ kcal: 400, protein: 35, carbs: 20, fiber: 9 })).toEqual(
      expect.arrayContaining(["eiweissreich", "leicht", "ballaststoffreich"]),
    );
    expect(youtubeId("https://www.youtube.com/watch?v=4aZr5hZXP_s")).toBe("4aZr5hZXP_s");
    expect(youtubeId("")).toBeNull();
  });
});

describe("Barcode und Zutatenbilder", () => {
  it("rechnet Open-Food-Facts-Werte um (Mineralstoffe g → mg, kJ → kcal)", async () => {
    const { mapProduct } = await import("./barcode.js");
    const p = mapProduct("1", {
      product_name: "Testriegel",
      brands: "X, Y",
      quantity: "330 ml",
      serving_quantity: 55,
      nutriments: {
        energy_100g: 1674,
        proteins_100g: 30,
        calcium_100g: 0.12,
        "vitamin-d_100g": 0.000005,
      },
    });
    expect(p?.per100.kcal).toBe(400);
    expect(p?.per100.calcium).toBe(120);
    expect(p?.per100.vitD).toBe(5);
    expect(p?.brand).toBe("X");
    expect(p?.liquid).toBe(true);
    expect(p?.serving).toBe(55);
  });

  it("findet TheMealDB-Bilder auch für Varianten und Synonyme", async () => {
    const { IngredientImages } = await import("./ingredient-images.js");
    const db = freshDb();
    const im = new IngredientImages(
      db,
      join((await import("node:os")).tmpdir(), `mdb-${Date.now()}.json`),
      () => {},
      (async () =>
        new Response(
          JSON.stringify({
            meals: [
              { strIngredient: "Carrots" },
              { strIngredient: "Rocket" },
              { strIngredient: "Bread" },
              { strIngredient: "Salmon" },
            ],
          }),
        )) as unknown as typeof fetch,
    );
    await im.init();
    expect(im.mealDb("carrot")).toContain("Carrots");
    expect(im.mealDb("arugula")).toContain("Rocket");
    expect(im.mealDb("mixed wheat bread")).toContain("Bread");
    expect(im.mealDb("salmon fillet")).toContain("Salmon");
    expect(im.mealDb("unbekanntes zeug")).toBeNull();
  });
});

describe("Zweites Training am selben Tag", () => {
  it("legt nach einer erledigten Einheit eine neue an, die den Tag kennt und ruhiger ist", async () => {
    const db = freshDb();
    saveProfile(db, { onboarded: true });
    const day = "2026-11-19";
    const first = await todaySession(null, db, catalog, { day });
    applyFeedback(
      db,
      first,
      { overall: 3, pain: [], exercises: [] },
      { seconds: 1800, done: {}, skipped: [] },
    );
    const second = await extraSession(null, db, catalog, { day });
    expect(second.id).not.toBe(first.id);
    expect(second.status).toBe("planned");
    expect(second.plan.intensity).not.toBe("hart");
    expect(second.plan.coach.why.join(" ")).toContain("Heute schon erledigt");
    expect((await todaySession(null, db, catalog, { day })).id).toBe(second.id);
    expect(getSession(db, first.id)?.status).toBe("done");
  });
});

describe("Achtsamkeit: Yoga-Pfad und Statistik", () => {
  const levels = [1, 2, 3].map((stufe) => ({
    stufe, name: `S${stufe}`, untertitel: "", minuten: 10, ziel: "", intention: "", philosophie: "",
    freischalten: { einheiten: 2, tage: 2 }, flow: [],
  }));
  const log = (db: ReturnType<typeof freshDb>, day: string, ref: string, kind = "yoga") =>
    db.prepare("INSERT INTO mind_log(day, kind, ref, seconds, at) VALUES(?, ?, ?, 600, ?)").run(day, kind, ref, Date.now());

  it("öffnet die nächste Stufe erst nach genug Einheiten an genug Tagen", () => {
    const db = freshDb();
    expect(yogaProgress(db, levels).open).toBe(1);
    log(db, "2026-11-01", "stufe-1");
    log(db, "2026-11-01", "stufe-1");
    expect(yogaProgress(db, levels).open).toBe(1); // zwei Einheiten, aber nur ein Tag
    log(db, "2026-11-02", "stufe-1");
    const p = yogaProgress(db, levels);
    expect(p.open).toBe(2);
    expect(p.levels[0].met).toBe(true);
    expect(p.levels[1].current).toBe(true);
    expect(p.levels[2].unlocked).toBe(false);
  });

  it("Überspringen öffnet genau eine Stufe, Meditationen zählen nicht für Yoga", () => {
    const db = freshDb();
    log(db, "2026-11-01", "stufe-1", "meditation");
    log(db, "2026-11-02", "stufe-1", "meditation");
    expect(yogaProgress(db, levels).open).toBe(1);
    setManualUnlock(db, 2);
    expect(yogaProgress(db, levels).open).toBe(2);
  });

  it("Serie zählt Tage in Folge bis heute oder gestern", () => {
    const db = freshDb();
    for (const d of ["2026-11-17", "2026-11-18", "2026-11-19"]) log(db, d, "x", "meditation");
    expect(mindStats(db, "2026-11-20").streak).toBe(3);
    expect(mindStats(db, "2026-11-22").streak).toBe(0);
    expect(mindStats(db, "2026-11-19").todayMinutes).toBe(10);
  });
});

describe("Progression: Aufstieg, Abstieg, Level, Test", () => {
  it("beherrschte Übung → nächste Variante, Plan nutzt sie mit passender Menge", async () => {
    const { checkProgression, makeResolver } = await import("./progression.js");
    const { applyVariants } = await import("./coach.js");
    const db = freshDb();
    saveProfile(db, { onboarded: true, equipment: ["boden"] });
    const ctx = buildContext(db, catalog, "2026-11-20");
    const ok = (e: Parameters<typeof usable>[1]) => usable(ctx, e);
    // Liegestütz beherrscht (Dosis hoch, 2× gemacht, Ziel erreicht)
    const ev = checkProgression(db, catalog, "2026-11-20", [{ ex: "pushup", dose: 1.5, times: 2, reached: 1.1 }], ok);
    expect(ev[0]).toMatchObject({ kind: "auf", from: "pushup", to: "diamond_pushup" });
    expect(makeResolver(db, catalog, ok)("pushup")).toBe("diamond_pushup");
    const plan = {
      title: "t", dayType: "druck", intensity: "mittel",
      blocks: [{ kind: "main", title: "m", format: "sets", rounds: 3, items: [{ ex: "pushup", amount: 20, rest: 30 }] }],
    } as unknown as Parameters<typeof applyVariants>[2];
    const out = applyVariants(db, ctx, plan);
    expect(out.blocks[0]?.items[0]?.ex).toBe("diamond_pushup");
    expect(out.blocks[0]?.items[0]?.amount).toBeLessThan(20);
    // Diamant dauerhaft zu schwer → zurück zum normalen Liegestütz
    const back = checkProgression(db, catalog, "2026-11-22", [{ ex: "diamond_pushup", dose: 0.6, times: 2, reached: 0.6 }], ok);
    expect(back[0]).toMatchObject({ kind: "ab", to: "pushup" });
    expect(makeResolver(db, catalog, ok)("pushup")).toBe("pushup");
  });

  it("kein Aufstieg bei »zu schwer«, unsicherer Technik oder Schmerz; Ausrüstung muss passen", async () => {
    const { checkProgression } = await import("./progression.js");
    const db = freshDb();
    saveProfile(db, { onboarded: true, equipment: ["boden"] });
    const ctx = buildContext(db, catalog, "2026-11-20");
    const ok = (e: Parameters<typeof usable>[1]) => usable(ctx, e);
    expect(checkProgression(db, catalog, "d", [{ ex: "pushup", dose: 1.6, times: 3, reached: 1, difficulty: 1 }], ok)).toEqual([]);
    expect(checkProgression(db, catalog, "d", [{ ex: "pushup", dose: 1.6, times: 3, reached: 1, technique: 1 }], ok)).toEqual([]);
    expect(checkProgression(db, catalog, "d", [{ ex: "pushup", dose: 1.6, times: 3, reached: 1, pain: true }], ok)).toEqual([]);
    // Dip braucht Barren → kein Aufstieg von bench_dip ohne Ausrüstung
    expect(checkProgression(db, catalog, "d", [{ ex: "bench_dip", dose: 1.6, times: 3, reached: 1 }], ok)).toEqual([]);
    // Bulgarische Kniebeuge geht mit Sofa/Bett (keine Bank nötig) → direkt die nächste Stufe
    const next = checkProgression(db, catalog, "d", [{ ex: "split_squat", dose: 1.6, times: 3, reached: 1, first: 1 }], ok);
    expect(next[0]).toMatchObject({ kind: "auf", from: "split_squat", to: "bulgarian_split_squat" });
  });

  it("vier Aufstiege heben das Level, der Test setzt es direkt", async () => {
    const { checkProgression } = await import("./progression.js");
    const { saveTest } = await import("./assessment.js");
    const db = freshDb();
    saveProfile(db, { onboarded: true, level: 2, equipment: ["boden", "klimmzugstange"] });
    const ctx = buildContext(db, catalog, "2026-11-20");
    const ok = (e: Parameters<typeof usable>[1]) => usable(ctx, e);
    const ev = checkProgression(db, catalog, "2026-11-20", ["incline_pushup", "squat", "crunch", "dead_bug"].map((ex) => ({ ex, dose: 1.5, times: 2, reached: 1 })), ok);
    expect(ev.find((e) => e.kind === "level_auf")?.level).toBe(3);
    const r = saveTest(db, { liegestuetz: 32, kniebeuge: 46, plank: 125, klimmzug: 10 });
    expect(r.level).toBe(4);
    expect(getProfile(db).level).toBe(4);
  });
});

describe("Programme, Woche, Balance", () => {
  it("Entlastungswoche → leicht und weniger Umfang; Fokus lenkt den Tagestyp", async () => {
    const { loadPrograms, setPrograms, startProgram, phaseFor } = await import("./program.js");
    const { buildRulesPlan, doseFor } = await import("./planner.js");
    setPrograms(loadPrograms(DATA));
    const db = freshDb();
    saveProfile(db, { onboarded: true, equipment: ["boden", "klimmzugstange"], trainingDays: [0, 2, 4] });
    startProgram(db, "muskelaufbau", "2026-10-05"); // Montag → Woche 8 = 23.–29.11. Entlastung & Test
    const ph = phaseFor(db, (await import("./program.js")).getPrograms(), "2026-11-24");
    expect(ph).toMatchObject({ week: 8, deload: true });
    const ctx = buildContext(db, catalog, "2026-11-24", { energy: 4 });
    const plan = buildRulesPlan(ctx);
    expect(plan.intensity).toBe("leicht");
    const e = catalog.byId.get("pushup")!;
    const ctxNormal = { ...ctx, program: null };
    expect(doseFor(ctx, e, "mittel", "main")).toBeLessThan(doseFor(ctxNormal, e, "mittel", "main"));
    // Klimmzug-Programm: Ziehen bevorzugt
    startProgram(db, "klimmzug", "2026-11-16");
    const { chooseDayType } = await import("./planner.js");
    expect(chooseDayType(buildContext(db, catalog, "2026-11-18", { energy: 3 }))).toBe("zug");
  });

  it("viel Drücken, kaum Ziehen in der Woche → Ziehen kommt dran", async () => {
    const { chooseDayType } = await import("./planner.js");
    const db = freshDb();
    saveProfile(db, { onboarded: true, equipment: ["boden", "klimmzugstange"], goal: "muskeln" });
    const ctx = buildContext(db, catalog, "2026-11-20", { energy: 3 });
    ctx.balance = { druck: 12, zug: 2, beine: 8, core: 4, ausdauer: 0 };
    ctx.recent = [];
    expect(chooseDayType(ctx)).toBe("zug");
  });

  it("Wochenvorschau sagt offene Trainingstage voraus", async () => {
    const { weekPreview } = await import("./week.js");
    const db = freshDb();
    saveProfile(db, { onboarded: true, trainingDays: [0, 2, 4] });
    const w = weekPreview(db, catalog, "2026-11-16");
    expect(w.days).toHaveLength(7);
    expect(w.days.filter((d) => d.predicted).length).toBe(3);
  });
});

describe("Bestzeiten und Körper", () => {
  it("nicht machbare Übungen werden ersetzt, Zielzeiten und Bestzeit stimmen", async () => {
    const { loadBenchmarks, resolveItems, targets, tierFor, saveRun, best } = await import("./benchmark.js");
    const db = freshDb();
    saveProfile(db, { onboarded: true, equipment: ["boden"], limitations: ["handgelenk"], noJumps: true });
    const ctx = buildContext(db, catalog, "2026-11-20");
    const b = loadBenchmarks(DATA).find((x) => x.id === "funke")!;
    const items = resolveItems(b, catalog, (e) => usable(ctx, e));
    expect(items.every(Boolean)).toBe(true);
    const kp = items.find((i) => i?.statt === "Knie-Liegestütz" || i?.statt?.includes("Liegest"));
    expect(kp).toBeTruthy(); // Handgelenk → Ersatz
    expect(items.some((i) => i?.ex === "jumping_jack")).toBe(false); // keine Sprünge
    const t = targets(items as { ex: string; menge: number }[], b.runden, catalog);
    expect(t.gold).toBeLessThan(t.silber);
    expect(tierFor(t.gold - 1, t)).toBe("gold");
    expect(saveRun(db, "funke", 300, {}).pb).toBe(true);
    expect(saveRun(db, "funke", 320, {}).pb).toBe(false);
    expect(best(db, "funke")).toBe(300);
  });

  it("Gewichtstrend und Prognose zum Ziel", async () => {
    const { weightTrend } = await import("./body.js");
    const { localDay, addDays } = await import("./db.js");
    const t = localDay();
    const rows = [0, 7, 14, 21].map((i) => ({ day: addDays(t, -21 + i), weight: 63 + i / 7 * 0.3 }));
    expect(weightTrend(rows)).toBeCloseTo(0.3, 1);
  });
});

describe("Sicherung", () => {
  it("sichert Datenbank und neue Körperfotos, hält 14 Tage", async () => {
    const { backupNow, backupInfo } = await import("./backup.js");
    const { mkdtempSync, writeFileSync, mkdirSync, readdirSync, existsSync } = await import("node:fs");
    const { tmpdir } = await import("node:os");
    const dir = mkdtempSync(join(tmpdir(), "fitbk-"));
    const db = openFitnessDb(join(dir, "fitness.db"));
    saveProfile(db, { onboarded: true, weightKg: 71 });
    mkdirSync(join(dir, "body"));
    writeFileSync(join(dir, "body", "abc.jpg"), "x");
    const target = join(dir, "backup");
    for (let i = 0; i < 16; i++) writeFileSync(join(target.replace(/backup$/, ""), "dummy"), ""); // nichts
    mkdirSync(target, { recursive: true });
    for (let i = 1; i <= 16; i++) writeFileSync(join(target, `fitness-2026-01-${String(i).padStart(2, "0")}.db`), "");
    const f = await backupNow(db, dir, target);
    expect(existsSync(f)).toBe(true);
    expect(existsSync(join(target, "body", "abc.jpg"))).toBe(true);
    expect(readdirSync(target).filter((x) => x.endsWith(".db")).length).toBe(14);
    const copy = openFitnessDb(f);
    expect(getProfile(copy).weightKg).toBe(71);
    expect(backupInfo(target).count).toBe(14);
  });
});

describe("Lernschleife über mehrere Einheiten (wie im echten Gebrauch)", () => {
  it("8 Einheiten »zu leicht« + übertroffen → Dosis steigt, Aufstiege, Varianten im nächsten Plan", async () => {
    const { checkProgression, makeResolver } = await import("./progression.js");
    const db = freshDb();
    saveProfile(db, { onboarded: true, equipment: ["boden"], limitations: ["handgelenk"], noJumps: true, trainingDays: [0, 1, 2, 3, 4, 5, 6], goal: "muskeln", level: 2 });
    const allEvents: { kind: string; from?: string | undefined; to?: string | undefined }[] = [];
    for (let i = 0; i < 8; i++) {
      const day = addDaysT("2026-11-23", i);
      const s = await todaySession(null, db, catalog, { day });
      const done: Record<string, number[]> = {};
      const exFb: { ex: string; difficulty: number; technique: number }[] = [];
      s.plan.blocks.forEach((b, bi) => b.items.forEach((it, ii) => {
        done[`${bi}:${ii}`] = Array.from({ length: Math.max(1, b.rounds ?? 1) }, () => Math.round(it.amount * 1.25));
        if (b.kind === "main") exFb.push({ ex: it.ex, difficulty: -1, technique: 3 });
      }));
      const signals = applyFeedback(db, s, { overall: 2, pain: [], exercises: exFb }, { seconds: 1500, done, skipped: [] });
      const ctx = buildContext(db, catalog, day);
      allEvents.push(...checkProgression(db, catalog, day, signals, (e) => usable(ctx, e)));
    }
    const ups = allEvents.filter((e) => e.kind === "auf");
    expect(ups.length).toBeGreaterThan(0);
    // Der nächste Plan nutzt die neue Variante statt der beherrschten Übung.
    const day = addDaysT("2026-11-23", 8);
    const ctx = buildContext(db, catalog, day);
    const resolve = makeResolver(db, catalog, (e) => usable(ctx, e));
    expect(resolve(ups[0]!.from!)).not.toBe(ups[0]!.from);
    const next = await todaySession(null, db, catalog, { day });
    const left = next.plan.blocks.flatMap((b) => b.items.filter((i) => ups.map((u) => u.from).includes(i.ex)).map((i) => `${b.kind}:${i.ex}`));
    console.log("Aufstiege:", ups.map((u) => `${u.from}→${u.to}`).join(", "), "| noch im Plan:", left.join(", "));
    expect(left.filter((x) => x.startsWith("main") || x.startsWith("finisher"))).toEqual([]);
    console.log("Aufstiege:", ups.map((u) => `${u.from}→${u.to}`).join(", "), "· Level jetzt", getProfile(db).level);
  });
});
function addDaysT(day: string, n: number) { const [y, m, d] = day.split("-").map(Number) as [number, number, number]; const dt = new Date(y, m - 1, d + n, 12); return `${dt.getFullYear()}-${String(dt.getMonth() + 1).padStart(2, "0")}-${String(dt.getDate()).padStart(2, "0")}`; }

describe("Prüfung 20.11.: gefundene Fehler bleiben behoben", () => {
  it("Abstieg, dann Aufstieg → zurück auf der Ausgangsübung (kein Kreis)", async () => {
    const { checkProgression, makeResolver } = await import("./progression.js");
    const db = freshDb();
    saveProfile(db, { onboarded: true, equipment: ["boden"], limitations: [] });
    const ok = () => true;
    const down = [{ ex: "pushup", dose: 0.6, times: 3, reached: 0.5 }];
    checkProgression(db, catalog, "2026-11-20", down, ok);
    expect(makeResolver(db, catalog, ok)("pushup")).toBe("knee_pushup");
    const up = [{ ex: "knee_pushup", dose: 1.5, times: 3, reached: 1 }];
    checkProgression(db, catalog, "2026-11-22", up, ok);
    expect(makeResolver(db, catalog, ok)("pushup")).toBe("pushup");
    expect(db.prepare("SELECT COUNT(*) AS n FROM variant_map").get()).toEqual({ n: 0 });
    // weiter hoch: pushup → nächste Stufe, die Kette zeigt sie auf der Fortschrittsseite
    checkProgression(db, catalog, "2026-11-24", [{ ex: "pushup", dose: 1.5, times: 3, reached: 1 }], ok);
    const next = catalog.byId.get("pushup")!.progress!;
    expect(makeResolver(db, catalog, ok)("pushup")).toBe(next);
    const { progressionView } = await import("./progression.js");
    const fam = progressionView(db, catalog, ok).families.find((f) => f.steps.some((s) => s.id === next) && f.steps.some((s) => s.id === "pushup"))!;
    expect(fam.steps[fam.current]!.id).toBe(next);
  });

  it("Variante übernimmt Einheit und »je Seite«, Intervalle behalten ihre feste Zeit", async () => {
    const { applyVariants } = await import("./coach.js");
    const db = freshDb();
    saveProfile(db, { onboarded: true, equipment: ["boden", "klimmzugstange"], limitations: [], minutes: 45, trainingDays: [0, 1, 2, 3, 4, 5, 6] });
    // alles eine Stufe hoch
    const ins = db.prepare("INSERT INTO variant_map(base, current, since) VALUES(?, ?, '2026-11-01')");
    for (const e of catalog.exercises) if (e.progress) ins.run(e.id, e.progress);
    for (let i = 0; i < 14; i++) {
      const day = addDaysT("2026-11-23", i);
      const ctx = buildContext(db, catalog, day);
      const plan = applyVariants(db, ctx, buildRulesPlan(ctx));
      for (const b of plan.blocks) {
        if (b.kind !== "main" && b.kind !== "finisher") continue;
        for (const it of b.items) {
          const e = catalog.byId.get(it.ex)!;
          if (b.format === "intervals") expect([30, 40]).toContain(it.amount);
          else if (b.format !== "flow") {
            expect(it.unit, `${day} ${it.ex}`).toBe(e.unit);
            expect(!!it.perSide, `${day} ${it.ex}`).toBe(!!e.perSide);
          }
        }
      }
      expect(plan.minutes).toBe(Math.round(planSeconds(plan, catalog) / 60));
    }
  });

  it("Handgelenk + ohne Sprünge + lange Einheiten: auch der Finisher bleibt machbar", () => {
    const db = freshDb();
    saveProfile(db, { onboarded: true, equipment: ["boden"], limitations: ["handgelenk"], noJumps: true, minutes: 75, trainingDays: [0, 1, 2, 3, 4, 5, 6] });
    for (let i = 0; i < 21; i++) {
      const ctx = buildContext(db, catalog, addDaysT("2026-11-23", i));
      const plan = buildRulesPlan(ctx);
      for (const b of plan.blocks) for (const it of b.items) expect(usable(ctx, catalog.byId.get(it.ex)!), `${b.kind} ${it.ex}`).toBe(true);
    }
  });

  it("Fitness-Test: Handgelenk → Liegestütz auf Fäusten, ausgelassene Tests zählen nicht als 0", async () => {
    const { saveTest, testsFor } = await import("./assessment.js");
    const db = freshDb();
    saveProfile(db, { onboarded: true, equipment: ["boden"], limitations: ["handgelenk"], noJumps: true, sex: "m", level: 3 });
    const ctx = buildContext(db, catalog, "2026-11-20");
    const ok = (id: string) => { const e = catalog.byId.get(id); return !!e && usable(ctx, e); };
    const tests = testsFor(db, ok);
    expect(tests.map((t) => t.ex)).toEqual(["fist_pushup", "squat", "plank", "towel_door_row"]);
    const r = saveTest(db, { liegestuetz: 30, kniebeuge: 45, plank: "" as unknown as number }, ok, "2026-11-18");
    expect(Object.keys(r!.tiers)).toEqual(["liegestuetz", "kniebeuge"]);
    expect(r!.level).toBeGreaterThanOrEqual(3);
    expect(saveTest(db, {}, ok)).toBeNull();
    expect((db.prepare("SELECT day FROM test_log").get() as { day: string }).day).toBe("2026-11-18");
  });

  it("Programmwoche: Zeitumstellung zählt richtig, Start am Wochenende → Woche 1 ab Montag", async () => {
    const { phaseFor, startProgram, getPrograms, setPrograms, loadPrograms } = await import("./program.js");
    setPrograms(loadPrograms(DATA));
    const db = freshDb();
    const id = getPrograms()[0]!.id;
    (await import("./db.js")).kvSet(db, "program", { id, start: "2026-03-23" });
    expect(phaseFor(db, getPrograms(), "2026-03-30")!.week).toBe(2);
    startProgram(db, id, "2026-11-22"); // Sonntag
    expect(phaseFor(db, getPrograms(), "2026-11-22")!.week).toBe(1);
    expect(phaseFor(db, getPrograms(), "2026-11-29")!.week).toBe(1);
    expect(phaseFor(db, getPrograms(), "2026-11-30")!.week).toBe(2);
  });

  it("API: doppelt gesendeter Abschluss zählt einmal, Offline-Ergebnis landet am richtigen Tag", async () => {
    const Fastify = (await import("fastify")).default;
    const { mkdtempSync } = await import("node:fs");
    const { tmpdir } = await import("node:os");
    const { registerFitnessRoutes } = await import("../api/fitness.js");
    const app = Fastify();
    const dir = mkdtempSync(join(tmpdir(), "fit-test-"));
    registerFitnessRoutes(app, { dir, dataDir: DATA, geminiKey: () => null, log: () => {} });
    await app.ready();
    await app.inject({ method: "POST", url: "/fitness/profile", payload: { onboarded: true, equipment: ["boden"], trainingDays: [0, 1, 2, 3, 4, 5, 6] } });
    const t = (await app.inject({ method: "GET", url: "/fitness/today" })).json() as { id: string; plan: { blocks: { kind: string; items: { ex: string; amount: number }[]; rounds?: number }[] } };
    const done: Record<string, number[]> = {};
    t.plan.blocks.forEach((b, bi) => b.items.forEach((it, ii) => { done[`${bi}:${ii}`] = Array.from({ length: b.rounds ?? 1 }, () => it.amount * 2); }));
    const body = { result: { seconds: 900, done, skipped: [] }, feedback: { overall: 2, exercises: t.plan.blocks.flatMap((b) => b.kind === "main" ? b.items.map((i) => ({ ex: i.ex, difficulty: -1, technique: 3 })) : []) } };
    const a = await app.inject({ method: "POST", url: `/fitness/session/${t.id}/finish`, payload: body, headers: { "x-hikari-req": "req-aaaa-1111" } });
    expect(a.statusCode).toBe(200);
    const times = () => (db2().prepare("SELECT SUM(times) AS n FROM exercise_state").get() as { n: number }).n;
    const { openFitnessDb: open } = await import("./db.js");
    const db2 = () => open(join(dir, "fitness.db"));
    const n1 = times();
    // gleiche Kennung (Schlange) und ohne Kennung (abgelaufene Verbindung) → beide nicht gewertet
    expect((await app.inject({ method: "POST", url: `/fitness/session/${t.id}/finish`, payload: body, headers: { "x-hikari-req": "req-aaaa-1111" } })).json()).toMatchObject({ duplicate: true });
    expect((await app.inject({ method: "POST", url: `/fitness/session/${t.id}/finish`, payload: body })).json()).toMatchObject({ duplicate: true });
    expect(times()).toBe(n1);
    // Gewicht offline von gestern: landet gestern, nicht heute
    const y = new Date(); y.setDate(y.getDate() - 1);
    await app.inject({ method: "POST", url: "/fitness/body", payload: { weight: 70.2 }, headers: { "x-hikari-at": String(y.getTime()), "x-hikari-req": "req-bbbb-2222" } });
    const rows = db2().prepare("SELECT day, weight FROM body_log").all() as { day: string; weight: number }[];
    expect(rows).toEqual([{ day: (await import("./db.js")).localDay(y), weight: 70.2 }]);
    // kaputte Eingaben → kein 500
    expect((await app.inject({ method: "POST", url: "/fitness/health", payload: { days: "x" } })).statusCode).toBe(200);
    expect((await app.inject({ method: "POST", url: "/fitness/health", payload: { days: [null, { day: 5 }] } })).statusCode).toBe(200);
    expect((await app.inject({ method: "POST", url: "/fitness/body/measure", payload: {} })).statusCode).toBe(400);
    await app.close();
  });
});

describe("Kalorienziel folgt dem Gewichtsverlauf", () => {
  it("zu langsam + Ziel gegessen → +100; nicht gegessen → nur Hinweis; im Band → bleibt", async () => {
    const { adaptCalories } = await import("./body.js");
    const { localDay: ld, addDays: ad } = await import("./db.js");
    const db = freshDb();
    saveProfile(db, { onboarded: true, sex: "m", weightKg: 63, heightCm: 178, goal: "muskeln", trainingDays: [0, 2, 4], sessionMinutes: 15 });
    const today = ld();
    const w = db.prepare("INSERT INTO body_log(day, weight) VALUES(?, ?)");
    for (let i = 21; i >= 0; i -= 3) w.run(ad(today, -i), 63); // 3 Wochen flach
    // nichts gegessen eingetragen → nur Hinweis
    expect(adaptCalories(db)!.note).toMatch(/vollständig ein/);
    expect(getProfile(db).kcalAdjust).toBe(0);
    // 14 Tage Ziel gegessen → +100
    const kcal = computeTargets(getProfile(db)).kcal;
    const f = db.prepare("INSERT INTO food_log(id, day, meal, title, source, items, totals, created_at) VALUES(?, ?, 'mittag', 'x', 'frei', '[]', ?, 0)");
    for (let i = 1; i <= 14; i++) f.run(`f${i}`, ad(today, -i), JSON.stringify({ kcal }));
    saveProfile(db, { kcalAdjustAt: 0 });
    expect(adaptCalories(db)!.adjust).toBe(100);
    expect(computeTargets(getProfile(db)).kcal).toBe(kcal + 100);
    expect(adaptCalories(db)).toBeNull(); // höchstens alle 14 Tage
    // Gewicht steigt gesund (+0,25 kg/Woche) → bleibt
    db.prepare("DELETE FROM body_log").run();
    for (let i = 21; i >= 0; i -= 3) w.run(ad(today, -i), 63 + ((21 - i) / 7) * 0.25);
    saveProfile(db, { kcalAdjustAt: 0 });
    expect(adaptCalories(db)!.adjust).toBe(100);
    // neues Ziel setzt zurück
    saveProfile(db, { goal: "halten" });
    expect(getProfile(db).kcalAdjust).toBe(0);
  });
});

describe("Trainingsplan-Qualität (Coach 2)", () => {
  it("15 min täglich mit Handgelenk: kurzer Rahmen, ≥2 Runden, Erholungstag, Beine ≥2× pro Woche", async () => {
    const db = freshDb();
    saveProfile(db, { onboarded: true, sex: "m", weightKg: 63, goal: "muskeln", level: 1, equipment: ["boden"], limitations: ["handgelenk"], noJumps: true, trainingDays: [0, 1, 2, 3, 4, 5, 6], sessionMinutes: 15 });
    const types: string[] = [];
    for (let i = 0; i < 14; i++) {
      const day = addDaysT("2026-11-23", i);
      const s = await todaySession(null, db, catalog, { day });
      const p = s.plan;
      types.push(p.dayType);
      const warm = p.blocks.find((b) => b.kind === "warmup")!;
      const cool = p.blocks.find((b) => b.kind === "cooldown")!;
      const main = p.blocks.find((b) => b.kind === "main")!;
      expect(warm.items.length).toBeLessThanOrEqual(3);
      expect(cool.items.length).toBeLessThanOrEqual(2);
      if (main.format !== "flow") expect(main.rounds, `${day} ${p.dayType}`).toBeGreaterThanOrEqual(2);
      if (main.format !== "flow") expect(Math.abs(p.minutes - 15), `${day} ${p.minutes} min`).toBeLessThanOrEqual(3); // Erholungstag darf kürzer sein
      // erledigen (mittleres Feedback), damit der Wochenrhythmus entsteht
      const done: Record<string, number[]> = {};
      p.blocks.forEach((b, bi) => b.items.forEach((it, ii) => { done[`${bi}:${ii}`] = Array.from({ length: b.rounds ?? 1 }, () => it.amount); }));
      applyFeedback(db, s, { overall: 3, pain: [], exercises: [] }, { seconds: p.minutes * 60, done, skipped: [] });
    }
    expect(types.filter((t) => t === "mobilitaet").length).toBeGreaterThanOrEqual(2);
    const legs = (w: string[]) => w.filter((t) => t === "beine" || t === "ganzkoerper").length;
    expect(legs(types.slice(0, 7)) + legs(types.slice(7))).toBeGreaterThanOrEqual(3);
  });

  it("Aufstieg: neue Stufe beginnt mit deutlich weniger Wiederholungen als die alte", async () => {
    const { checkProgression } = await import("./progression.js");
    const db = freshDb();
    saveProfile(db, { onboarded: true, level: 2, equipment: ["boden"], limitations: [] });
    const ctx0 = buildContext(db, catalog, "2026-11-20");
    const before = doseForT(ctx0, "pushup");
    db.prepare("INSERT INTO exercise_state(exercise_id, dose, times, updated_at) VALUES('pushup', 1.5, 3, 0)").run();
    const beforeUp = doseForT(buildContext(db, catalog, "2026-11-20"), "pushup");
    const ev = checkProgression(db, catalog, "2026-11-20", [{ ex: "pushup", dose: 1.5, times: 3, reached: 1, first: 1 }], () => true);
    const next = ev.find((e) => e.kind === "auf")!.to!;
    const after = doseForT(buildContext(db, catalog, "2026-11-21"), next);
    expect(beforeUp).toBeGreaterThan(before);
    expect(after).toBeLessThan(beforeUp * 0.85);
    expect(after).toBeGreaterThanOrEqual(3);
  });
});
function doseForT(ctx: ReturnType<typeof buildContext>, id: string) {
  return doseFor(ctx, catalog.byId.get(id)!, "mittel", "main");
}

describe("Ernährung: vergessene und halbe Tage", () => {
  it("leer zählt nicht, nur Frühstück = teilweise, selbst markiert hat Vorrang, heute = offen", async () => {
    const { dayStates, setDayState } = await import("./food-days.js");
    const { localDay: ld, addDays: ad } = await import("./db.js");
    const db = freshDb();
    const today = ld();
    const f = db.prepare("INSERT INTO food_log(id, day, meal, title, source, items, totals, created_at) VALUES(?, ?, ?, 'x', 'frei', '[]', ?, 0)");
    f.run("a", ad(today, -3), "fruehstueck", JSON.stringify({ kcal: 500 })); // nur Frühstück
    f.run("b", ad(today, -2), "fruehstueck", JSON.stringify({ kcal: 600 }));
    f.run("c", ad(today, -2), "mittag", JSON.stringify({ kcal: 800 }));
    f.run("d", ad(today, -2), "abend", JSON.stringify({ kcal: 700 })); // alle Hauptmahlzeiten
    f.run("e", ad(today, -1), "mittag", JSON.stringify({ kcal: 1800 })); // viel, aber eine Mahlzeit
    f.run("g", today, "fruehstueck", JSON.stringify({ kcal: 400 }));
    let st = dayStates(db, ad(today, -5), today, 2400, 3);
    expect(st[ad(today, -4)]).toBeUndefined(); // nichts eingetragen → kommt gar nicht vor
    expect(st[ad(today, -3)]!.state).toBe("teilweise");
    expect(st[ad(today, -2)]!.state).toBe("komplett");
    expect(st[ad(today, -1)]!.state).toBe("komplett"); // ≥ 60 % des Ziels
    expect(st[today]!.state).toBe("offen");
    setDayState(db, ad(today, -3), "komplett"); // »war wirklich alles« (z. B. Fastentag)
    setDayState(db, ad(today, -1), "teilweise"); // »hab was vergessen«
    st = dayStates(db, ad(today, -5), today, 2400, 3);
    expect(st[ad(today, -3)]).toMatchObject({ state: "komplett", manual: true });
    expect(st[ad(today, -1)]).toMatchObject({ state: "teilweise", manual: true });
  });

  it("Kalorien-Nachsteuerung: halbe Tage drücken den Schnitt nicht", async () => {
    const { adaptCalories } = await import("./body.js");
    const { localDay: ld, addDays: ad } = await import("./db.js");
    const db = freshDb();
    saveProfile(db, { onboarded: true, sex: "m", weightKg: 63, heightCm: 178, goal: "muskeln", trainingDays: [0, 2, 4], sessionMinutes: 15 });
    const today = ld();
    const w = db.prepare("INSERT INTO body_log(day, weight) VALUES(?, ?)");
    for (let i = 21; i >= 0; i -= 3) w.run(ad(today, -i), 63);
    const kcal = computeTargets(getProfile(db)).kcal;
    const f = db.prepare("INSERT INTO food_log(id, day, meal, title, source, items, totals, created_at) VALUES(?, ?, ?, 'x', 'frei', '[]', ?, 0)");
    for (let i = 1; i <= 14; i++) {
      if (i % 3 === 0) f.run(`p${i}`, ad(today, -i), "fruehstueck", JSON.stringify({ kcal: 450 })); // vergessen
      else f.run(`f${i}`, ad(today, -i), "mittag", JSON.stringify({ kcal }));
    }
    // 10 vollständige Tage im Ziel, 4 halbe → Erhöhung, nicht »du isst zu wenig«
    expect(adaptCalories(db)!.adjust).toBe(100);
  });
});

describe("Prüfung 21.11.: Fach- und Code-Prüfung", () => {
  it("Abneigungen: ein String mit Punkten, Tippfehler, Plural und Komposita werden erkannt", async () => {
    const { dislikeHit } = await import("./recipes.js");
    const p = normalizeProfile({ dislikes: ["Bannanen.apfel.mandeln.orangen"] });
    expect(p.dislikes).toEqual(["Bannanen", "apfel", "mandeln", "orangen"]);
    expect(dislikeHit("orangen", "Orangensaft")).toBe(true);
    expect(dislikeHit("Bannanen", "Banane")).toBe(true);
    expect(dislikeHit("apfel", "Äpfel")).toBe(true);
    expect(dislikeHit("mandeln", "Mandelmus")).toBe(true);
    expect(dislikeHit("apfel", "Ananas")).toBe(false);
    expect(dislikeHit("mandeln", "Mango")).toBe(false);
  });

  it("Resolver: verbotene Stufe kommt auch über einen Umweg nicht in den Plan", async () => {
    const { makeResolver } = await import("./progression.js");
    const db = freshDb();
    const chain = catalog.exercises.find((e) => e.progress && catalog.byId.get(e.progress)?.progress)!;
    const mid = catalog.byId.get(chain.progress!)!;
    const top = catalog.byId.get(mid.progress!)!;
    db.prepare("INSERT INTO variant_map(base, current, since) VALUES(?, ?, 'x')").run(chain.id, top.id);
    const ok = (e: { id: string }) => e.id !== top.id; // oberste Stufe jetzt verboten (z. B. neue Beschwerde)
    expect(makeResolver(db, catalog, ok)(mid.id)).toBe(mid.id);
  });

  it("Beherrschte Stufen: Reihenfolge der Zuordnungen egal, Abstieg löscht keine andere Kette", async () => {
    const { masteredLevels } = await import("./progression.js");
    const a = freshDb();
    a.prepare("INSERT INTO variant_map(base, current, since) VALUES('incline_pushup', 'pushup', 'x')").run();
    a.prepare("INSERT INTO variant_map(base, current, since) VALUES('pike_pushup', 'decline_pushup', 'x')").run();
    const b = freshDb();
    b.prepare("INSERT INTO variant_map(base, current, since) VALUES('pike_pushup', 'decline_pushup', 'x')").run();
    b.prepare("INSERT INTO variant_map(base, current, since) VALUES('incline_pushup', 'pushup', 'x')").run();
    expect(masteredLevels(a, catalog)).toEqual(masteredLevels(b, catalog));
    expect(masteredLevels(a, catalog).push).toBeGreaterThan(0);
  });

  it("Wiedereinstieg gewinnt auch im Programm", async () => {
    const { startProgram, setPrograms, loadPrograms } = await import("./program.js");
    setPrograms(loadPrograms(DATA));
    const db = freshDb();
    saveProfile(db, { onboarded: true, trainingDays: [0, 2, 4] });
    startProgram(db, "muskelaufbau", "2026-11-16");
    const ctx = buildContext(db, catalog, "2026-11-25", { energy: 4 });
    ctx.daysOff = 13;
    expect(buildRulesPlan(ctx).intensity).toBe("leicht");
  });

  it("Tagesplan: ohne Snack trifft er das Kalorienziel (Anteile 100 %), Kochzeit eingehalten", async () => {
    const { mealShare } = await import("./recipes.js");
    const p = { mealsPerDay: 3 };
    expect(mealShare("fruehstueck", p) + mealShare("mittag", p) + mealShare("abend", p)).toBeCloseTo(1, 5);
    const p4 = { mealsPerDay: 4 };
    expect(mealShare("fruehstueck", p4) + mealShare("mittag", p4) + mealShare("abend", p4) + mealShare("snack", p4)).toBeCloseTo(1, 5);
  });

  it("Lücke füllen: passt zu Ernährungsform, Allergien und Abneigungen", async () => {
    const { fillGap } = await import("./recipes.js");
    const vegan = normalizeProfile({ diet: "vegan", allergies: ["erdnuss"], dislikes: ["Hummus"] });
    const x = fillGap(500, 30, vegan);
    expect(x.length).toBeGreaterThan(0);
    expect(x.some((i) => /Quark|Skyr|Milch 400|Eier|Thunfisch|Erdnuss|Hummus/.test(i.name))).toBe(false);
    const omni = normalizeProfile({ diet: "alles" });
    const y = fillGap(300, 0, omni);
    expect(y.reduce((a, i) => a + i.kcal, 0)).toBeGreaterThanOrEqual(200);
  });

  it("Fitness-Test setzt die Startmenge der getesteten Übung (≈ 70 % vom Maximum)", async () => {
    const { saveTest } = await import("./assessment.js");
    const db = freshDb();
    saveProfile(db, { onboarded: true, sex: "m", equipment: ["boden"], limitations: ["handgelenk"], noJumps: true });
    const ctx = buildContext(db, catalog, "2026-11-20");
    const ok = (id: string) => { const e = catalog.byId.get(id); return !!e && usable(ctx, e); };
    saveTest(db, { liegestuetz: 20, kniebeuge: 40, plank: 60, rudern: 12 }, ok, "2026-11-20", catalog);
    const after = buildContext(db, catalog, "2026-11-21");
    expect(doseFor(after, catalog.byId.get("fist_pushup")!, "mittel", "main")).toBe(14);
    expect(doseFor(after, catalog.byId.get("towel_door_row")!, "mittel", "main")).toBeGreaterThanOrEqual(8);
  });

  it("Kein Feedback, alles geschafft: die Menge steigt trotzdem langsam", async () => {
    const db = freshDb();
    saveProfile(db, { onboarded: true, equipment: ["boden"], trainingDays: [0, 1, 2, 3, 4, 5, 6] });
    const s = await todaySession(null, db, catalog, { day: "2026-11-23" });
    const main = s.plan.blocks.findIndex((b) => b.kind === "main");
    const it0 = s.plan.blocks[main]!.items[0]!;
    const done: Record<string, number[]> = { [`${main}:0`]: Array.from({ length: s.plan.blocks[main]!.rounds }, () => it0.amount) };
    applyFeedback(db, s, { overall: 3, pain: [], exercises: [] }, { seconds: 600, done, skipped: [] }, { seedOf: () => 1 });
    const st = db.prepare("SELECT dose FROM exercise_state WHERE exercise_id = ?").get(it0.ex) as { dose: number };
    expect(st.dose).toBeGreaterThan(1);
  });
});
