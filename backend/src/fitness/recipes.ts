/**
 * Rezepte: TheMealDB (790 Rezepte, Fotos, Zutatenbilder, YouTube-Videos) — einmal übersetzt und
 * je Portion gegen den Bundeslebensmittelschlüssel durchgerechnet. Der Import läuft im Hintergrund
 * (Gemma, großer Topf) und setzt nach einem Neustart fort; fertige Rezepte sind sofort nutzbar.
 */
import type { FitnessAi } from "./ai.js";
import type { FitDb } from "./db.js";
import { type MealItem, type NutrientDb, resolveItems } from "./meal-ai.js";
import {
  type Nutrients,
  type Profile,
  type Targets,
  roundNutrients,
  sumNutrients,
} from "./profile.js";

export type MealSlot = "fruehstueck" | "mittag" | "abend" | "snack";

export interface Recipe {
  id: string;
  source: "themealdb" | "lecker";
  url?: string;
  title: string;
  original: string;
  summary: string;
  image: string;
  video: string | null;
  category: string;
  cuisine: string;
  minutes: number;
  difficulty: number;
  servings: number;
  meals: (MealSlot | "dessert")[];
  diet: "alles" | "vegetarisch" | "vegan" | "pescetarisch";
  allergens: string[];
  ingredients: {
    name: string;
    en: string;
    amount: string;
    grams: number;
    image: string;
    food: string | null;
  }[];
  steps: string[];
  /** je Portion */
  n: Nutrients;
  tags: string[];
}

export interface MealDbMeal {
  idMeal: string;
  strMeal: string;
  strCategory: string;
  strArea: string;
  strInstructions: string;
  strMealThumb: string;
  strYoutube?: string | null;
  /** lecker = deutsches Rezept (nicht übersetzen); Gruppe gibt die Mahlzeit vor */
  _source?: "lecker";
  _url?: string;
  _group?: string;
  [k: string]: unknown;
}

const TRANSLATE_SYSTEM = `Du bist Koch und Ernährungswissenschaftler. Bereite ein Rezept strukturiert auf Deutsch auf (englische Rezepte übersetzen, deutsche nur sauber aufbereiten).
- Rechne JEDE Zutatenmenge in Gramm für das ganze Rezept um (US-Maße: 1 cup Mehl ≈ 125 g, 1 cup Reis roh ≈ 185 g, 1 tbsp Öl ≈ 13 g, 1 tsp Salz ≈ 6 g, 1 Ei ≈ 55 g, 1 Zwiebel ≈ 110 g, 1 Knoblauchzehe ≈ 4 g; "to taste"/"pinch" ≈ 1–2 g).
- amount: die Menge auf Deutsch in Küchenmaßen (z. B. "2 EL", "1 Dose (400 g)", "200 g").
- query: Suchbegriff für den Bundeslebensmittelschlüssel, deutsch, mit Zustand (z. B. "Hähnchenbrustfilet roh", "Reis poliert roh", "Olivenöl", "Tomate Konserve").
- servings: realistische Portionszahl. minutes: Gesamtzeit. difficulty: 1 leicht, 2 mittel, 3 anspruchsvoll.
- meals: passende Mahlzeiten aus fruehstueck, mittag, abend, snack, dessert (Hinweis "gruppe" beachten: fruehstueck → enthält fruehstueck, snack → enthält snack).
- diet: alles | vegetarisch | vegan | pescetarisch. allergens: z. B. Gluten, Laktose, Ei, Nüsse, Erdnüsse, Soja, Fisch, Schalentiere, Sellerie, Senf, Sesam.
- steps: klare, nummerierbare Arbeitsschritte (deutsch, du-Form, je 1–2 Sätze), Temperaturen in °C.
Antworte NUR mit JSON: {"title":"…","summary":"ein appetitlicher Satz","servings":4,"minutes":40,"difficulty":2,"meals":["abend"],"diet":"alles","allergens":[],"ingredients":[{"en":"englischer Zutatenname wie bei TheMealDB, z. B. Chicken Breast, Rolled Oats, Greek Yogurt","name":"deutscher Name","amount":"…","grams":0,"query":"…"}],"steps":["…"]}`;

export function mealDbIngredients(m: MealDbMeal): { name: string; measure: string }[] {
  const out: { name: string; measure: string }[] = [];
  for (let i = 1; i <= 20; i++) {
    const name = String(m[`strIngredient${i}`] ?? "").trim();
    if (!name) continue;
    out.push({ name, measure: String(m[`strMeasure${i}`] ?? "").trim() });
  }
  return out;
}

export function youtubeId(url: string | null | undefined): string | null {
  const m = /(?:v=|youtu\.be\/|embed\/)([\w-]{11})/.exec(url ?? "");
  return m ? (m[1] as string) : null;
}

export function recipeTags(n: Nutrients): string[] {
  const kcal = n.kcal ?? 0;
  const p = n.protein ?? 0;
  const tags: string[] = [];
  if (kcal > 0 && (p * 4) / kcal >= 0.28) tags.push("eiweissreich");
  if (kcal > 0 && kcal <= 450) tags.push("leicht");
  if (kcal >= 750) tags.push("energiereich");
  if ((n.fiber ?? 0) >= 8) tags.push("ballaststoffreich");
  if (kcal > 0 && ((n.carbs ?? 0) * 4) / kcal <= 0.2) tags.push("lowcarb");
  return tags;
}

export async function importRecipe(ai: FitnessAi, ndb: NutrientDb, m: MealDbMeal): Promise<Recipe> {
  const ingredients = mealDbIngredients(m);
  const { value: t } = await ai.ask(
    {
      task: "bulk",
      system: TRANSLATE_SYSTEM,
      input: {
        title: m.strMeal,
        category: m.strCategory,
        cuisine: m.strArea,
        ingredients,
        instructions: m.strInstructions,
      },
      maxTokens: 5000,
      temperature: 0.2,
      timeoutMs: 120_000,
    },
    (raw) => {
      const r = raw as {
        title?: string;
        summary?: string;
        servings?: number;
        minutes?: number;
        difficulty?: number;
        meals?: string[];
        diet?: string;
        allergens?: string[];
        ingredients?: {
          en?: string;
          name?: string;
          amount?: string;
          grams?: number;
          query?: string;
        }[];
        steps?: string[];
      };
      if (
        !r.title ||
        !Array.isArray(r.ingredients) ||
        !r.ingredients.length ||
        !Array.isArray(r.steps) ||
        !r.steps.length
      )
        throw new Error("unvollständig");
      return r;
    },
  );
  const ing = (t.ingredients ?? []).filter((i) => i.name && Number(i.grams) >= 0);
  const resolved: MealItem[] = await resolveItems(
    ai,
    ndb,
    ing.map((i) => ({
      name: String(i.name),
      query: String(i.query || i.name),
      grams: Math.max(1, Number(i.grams) || 1),
    })),
    "bulk",
  );
  const servings = Math.min(12, Math.max(1, Math.round(Number(t.servings) || 4)));
  const total = sumNutrients(resolved.map((r) => r.n));
  const per: Nutrients = {};
  for (const [k, v] of Object.entries(total)) per[k as keyof Nutrients] = (v as number) / servings;
  const n = roundNutrients(per);
  const slots = new Set(["fruehstueck", "mittag", "abend", "snack", "dessert"]);
  const diets = ["alles", "vegetarisch", "vegan", "pescetarisch"] as const;
  return {
    id: m._source === "lecker" ? `lecker:${m.idMeal}` : `mdb:${m.idMeal}`,
    source: m._source === "lecker" ? "lecker" : "themealdb",
    ...(m._url ? { url: m._url } : {}),
    title: String(t.title).slice(0, 90),
    original: m.strMeal,
    summary: String(t.summary ?? "").slice(0, 200),
    image: m.strMealThumb,
    video: youtubeId(m.strYoutube),
    category: m.strCategory,
    cuisine: m.strArea,
    minutes: Math.min(600, Math.max(5, Math.round(Number(t.minutes) || 40))),
    difficulty: Math.min(3, Math.max(1, Math.round(Number(t.difficulty) || 2))),
    servings,
    meals: (t.meals ?? []).filter((x): x is Recipe["meals"][number] => slots.has(x)),
    diet: (diets as readonly string[]).includes(t.diet ?? "")
      ? (t.diet as Recipe["diet"])
      : "alles",
    allergens: (t.allergens ?? []).map(String).slice(0, 10),
    ingredients: ing.map((i, k) => ({
      name: String(i.name).slice(0, 60),
      en: String(i.en || ingredients[k]?.name || ""),
      amount: String(i.amount ?? "").slice(0, 40),
      grams: Math.round(Number(i.grams) || 0),
      image: `https://www.themealdb.com/images/ingredients/${encodeURIComponent(String(i.en || ingredients[k]?.name || i.name))}-small.png`,
      food: resolved[k]?.food?.name ?? null,
    })),
    steps: (t.steps ?? []).map((s) => String(s).slice(0, 500)).slice(0, 25),
    n,
    tags: recipeTags(n),
  };
}

export function saveRecipe(db: FitDb, r: Recipe) {
  db.prepare(
    "INSERT INTO recipes(id, source, data, kcal, protein, tags, updated_at) VALUES(?, ?, ?, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET data = excluded.data, kcal = excluded.kcal, protein = excluded.protein, tags = excluded.tags, updated_at = excluded.updated_at",
  ).run(
    r.id,
    r.source,
    JSON.stringify(r),
    r.n.kcal ?? null,
    r.n.protein ?? null,
    r.tags.join(","),
    Date.now(),
  );
}

export function allRecipes(db: FitDb): Recipe[] {
  return (db.prepare("SELECT data FROM recipes").all() as { data: string }[]).map(
    (r) => JSON.parse(r.data) as Recipe,
  );
}

export function getRecipe(db: FitDb, id: string): Recipe | null {
  const r = db.prepare("SELECT data FROM recipes WHERE id = ?").get(id) as
    | { data: string }
    | undefined;
  return r ? (JSON.parse(r.data) as Recipe) : null;
}

export function recipeStates(db: FitDb) {
  return new Map(
    (
      db.prepare("SELECT * FROM recipe_state").all() as {
        recipe_id: string;
        favorite: number;
        rating: number | null;
        cooked: number;
        hidden: number;
      }[]
    ).map((r) => [
      r.recipe_id,
      { favorite: !!r.favorite, rating: r.rating, cooked: r.cooked, hidden: !!r.hidden },
    ]),
  );
}

/** Passt das Rezept zur Ernährungsform und zu Allergien/Abneigungen? */
export function fitsProfile(r: Recipe, p: Profile): boolean {
  const order = { vegan: 0, vegetarisch: 1, pescetarisch: 2, alles: 3 } as const;
  if (p.diet === "vegan" && r.diet !== "vegan") return false;
  if (p.diet === "vegetarisch" && order[r.diet] > 1) return false;
  if (p.diet === "pescetarisch" && r.diet === "alles") return false;
  const lower = (s: string) => s.toLowerCase();
  for (const a of p.allergies)
    if (r.allergens.some((x) => lower(x).includes(lower(a)))) return false;
  for (const d of p.dislikes) {
    const w = lower(d).trim();
    if (
      w.length >= 3 &&
      r.ingredients.some((i) => lower(i.name).includes(w) || lower(i.en).includes(w))
    )
      return false;
  }
  return true;
}

/**
 * Wie gut passt ein Rezept als [slot] zu den Zielen? Energieanteil der Mahlzeit am Tagesziel,
 * Eiweißdichte je nach Ziel, Ballaststoffe, Vorlieben (Favorit, Bewertung), Kochzeit.
 */
export function scoreRecipe(
  r: Recipe,
  slot: MealSlot,
  targets: Targets,
  p: Profile,
  state?: { favorite: boolean; rating: number | null; cooked: number },
): number {
  const share = { fruehstueck: 0.25, mittag: 0.33, abend: 0.32, snack: 0.1 }[slot];
  const want = targets.kcal * share;
  const kcal = r.n.kcal ?? 0;
  if (!kcal) return -99;
  // Portion lässt sich skalieren (0,6–1,6) — bewertet wird die Abweichung nach der Skalierung.
  const factor = Math.min(1.6, Math.max(0.6, want / kcal));
  const off = Math.abs(kcal * factor - want) / want;
  let s = 3 - off * 4;
  const pShare = ((r.n.protein ?? 0) * 4) / kcal;
  const wantP = (targets.protein * 4) / targets.kcal;
  s += Math.min(2, (pShare / wantP) * 1.5) - (pShare < wantP * 0.5 ? 1 : 0);
  if (p.goal === "abnehmen") s += (r.n.fiber ?? 0) / 8 - ((r.n.sugar ?? 0) > 25 ? 1 : 0);
  if (r.minutes > p.cookingMinutes * 1.5) s -= 1.5;
  if (state?.favorite) s += 1.2;
  if (state?.rating) s += (state.rating - 3) * 0.6;
  if (r.meals.includes(slot)) s += 1.5;
  else if (slot === "fruehstueck" || slot === "snack") s -= 2.5;
  if (r.meals.includes("dessert") && slot !== "snack") s -= 3;
  return s;
}

export function portionFactor(r: Recipe, slot: MealSlot, targets: Targets): number {
  const share = { fruehstueck: 0.25, mittag: 0.33, abend: 0.32, snack: 0.1 }[slot];
  const kcal = r.n.kcal ?? 0;
  if (!kcal) return 1;
  return Math.round(Math.min(1.6, Math.max(0.6, (targets.kcal * share) / kcal)) * 4) / 4;
}

/** Läuft im Hintergrund: übersetzt und berechnet fehlende Rezepte, höflich nacheinander. */
export class RecipeImporter {
  running = false;
  done = 0;
  failed = 0;
  total = 0;
  lastError = "";
  constructor(
    private db: FitDb,
    private ai: FitnessAi,
    private ndb: NutrientDb,
    private loadSource: () => Promise<MealDbMeal[]>,
    private log: (m: string) => void,
  ) {}

  status() {
    const have = (this.db.prepare("SELECT COUNT(*) AS n FROM recipes").get() as { n: number }).n;
    return {
      running: this.running,
      have,
      total: this.total,
      failed: this.failed,
      lastError: this.lastError,
    };
  }

  async run(limit = Number.POSITIVE_INFINITY) {
    if (this.running || !this.ai.available()) return;
    this.running = true;
    try {
      const meals = await this.loadSource();
      this.total = meals.length;
      const have = new Set(
        (this.db.prepare("SELECT id FROM recipes").all() as { id: string }[]).map((r) => r.id),
      );
      // Erst die herzhaften Hauptgerichte, Desserts zuletzt.
      const order = (m: MealDbMeal) =>
        m.strCategory === "Dessert"
          ? 2
          : m.strCategory === "Side" || m.strCategory === "Starter"
            ? 1
            : 0;
      const todo = meals
        .filter((m) => !have.has(`mdb:${m.idMeal}`))
        .sort((a, b) => order(a) - order(b));
      // Gemma braucht 30–60 s je Antwort, erlaubt aber 30 Anfragen/Minute → mehrere Arbeiter,
      // Starts mindestens 4 s auseinander (je Rezept zwei Anfragen).
      const queue = todo.slice(0, Number.isFinite(limit) ? limit : todo.length);
      let stop = false;
      let lastStart = 0;
      let busyStreak = 0;
      const worker = async () => {
        while (!stop && queue.length) {
          const wait = lastStart + 4000 - Date.now();
          lastStart = Math.max(Date.now(), lastStart + 4000);
          if (wait > 0) await new Promise((r) => setTimeout(r, wait));
          const m = queue.shift();
          if (!m) break;
          try {
            saveRecipe(this.db, await importRecipe(this.ai, this.ndb, m));
            this.done++;
          } catch (e) {
            this.lastError = e instanceof Error ? e.message : String(e);
            if (this.lastError === "no_key") stop = true;
            else if (
              ["exhausted", "quota", "rate"].includes(this.lastError) ||
              /^http_5/.test(this.lastError)
            ) {
              // Modelle überlastet oder gedrosselt → Rezept zurücklegen und warten.
              queue.push(m);
              if (++busyStreak > 40) stop = true;
              await new Promise((r) => setTimeout(r, 90_000));
              continue;
            } else {
              this.failed++;
              this.log(`[fitness] Rezept ${m.strMeal}: ${this.lastError}`);
            }
          }
          busyStreak = 0;
        }
      };
      await Promise.all(Array.from({ length: 6 }, worker));
    } finally {
      this.running = false;
    }
  }
}

export function recipeDayTotals(items: { n: Nutrients }[]) {
  return roundNutrients(sumNutrients(items.map((i) => i.n)));
}

/** schema.org-Recipe von lecker.de → gleiche Form wie TheMealDB (Zutaten als ganze Zeilen). */
export function leckerToMeal(entry: {
  url: string;
  group: string;
  ld: Record<string, unknown>;
}): MealDbMeal | null {
  const ld = entry.ld;
  const num = /-(\d+)\.html$/.exec(entry.url)?.[1];
  if (!num) return null;
  const imgs = ([] as unknown[])
    .concat(ld.image ?? [])
    .map((i) => (typeof i === "string" ? i : String((i as { url?: string }).url ?? "")))
    .filter(Boolean);
  const image = imgs.find((u) => u.includes("1x1")) ?? imgs[0];
  if (!image) return null;
  const steps = ([] as unknown[])
    .concat(ld.recipeInstructions ?? [])
    .map((x) => (typeof x === "string" ? x : String((x as { text?: string }).text ?? "")))
    .filter(Boolean);
  const meal: MealDbMeal = {
    idMeal: num,
    strMeal: String(ld.name ?? "").replace(/\s+Rezept$/, ""),
    strCategory: String(ld.recipeCategory ?? entry.group),
    strArea: "Deutschland",
    strInstructions: steps.join("\n"),
    strMealThumb: image,
    strYoutube: null,
    _source: "lecker",
    _url: entry.url,
    _group: entry.group,
  };
  ([] as unknown[])
    .concat(ld.recipeIngredient ?? [])
    .slice(0, 20)
    .forEach((ing, i) => {
      meal[`strIngredient${i + 1}`] = String(ing);
      meal[`strMeasure${i + 1}`] = "";
    });
  meal.strCategory = `${meal.strCategory} · ${String(ld.recipeYield ?? "")}`;
  return meal;
}
