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
import { buildRulesPlan, planSeconds, usable } from "./planner.js";
import { computeTargets, normalizeProfile, saveProfile } from "./profile.js";
import { recipeTags, youtubeId } from "./recipes.js";
import { goalMet, logSkill, setSkill, skillStates } from "./skills.js";

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
    const ctx = buildContext(db, catalog, "2026-11-19");
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
