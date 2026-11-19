/**
 * Bilder für Zutaten.
 *
 * 1. TheMealDB-Zutatenbilder (freigestellt, einheitlicher Stil) — 992 bekannte Namen. Die KI schreibt
 *    oft eine Variante (»carrot«, »arugula«, »vegetable broth«), deshalb: Mehrzahl/Einzahl, Synonyme,
 *    und notfalls der Grundbegriff (»salmon fillet« → »Salmon«).
 * 2. Sonst das Zutatenbild von Spoonacular (gleicher Stil: freigestellt auf Weiß) — einmal im
 *    Hintergrund nachgeschlagen (Varianten des englischen Namens, .jpg/.png) und gemerkt.
 *    Wikimedia/Wikidata wurde verworfen: liefert oft die Pflanze statt des Lebensmittels
 *    (»Kresse« → Blüte, »Hirse« → Sorghum).
 * 3. Gibt es beides nicht, zeigt die Oberfläche ein Symbol.
 */
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { type FitDb, kvGet, kvSet } from "./db.js";

const UA = "Hikari-Fitness/1.0 (private app; https://github.com/Kashino17/Hikari)";
const MEALDB = "https://www.themealdb.com/images/ingredients/";

const SYN: Record<string, string> = {
  arugula: "rocket",
  "clarified butter": "ghee",
  "vegetable broth": "vegetable stock",
  "chicken broth": "chicken stock",
  "beef broth": "beef stock",
  "tomato paste": "tomato puree",
  "bell pepper": "red pepper",
  "red bell pepper": "red pepper",
  "yellow bell pepper": "yellow pepper",
  "green bell pepper": "green pepper",
  "paprika powder": "paprika",
  cilantro: "coriander",
  zucchini: "courgettes",
  eggplant: "aubergine",
  "powdered sugar": "icing sugar",
  "ground beef": "minced beef",
  "minced meat": "minced beef",
  scallion: "spring onions",
  "spring onion": "spring onions",
  chili: "chilli",
  "chili flakes": "chilli flakes",
  "chili powder": "chilli powder",
  "toast bread": "bread",
  "whole wheat bread": "bread",
  "mixed wheat bread": "bread",
  "agave syrup": "maple syrup",
  "low-fat yogurt": "greek yogurt",
  "whole milk yogurt": "greek yogurt",
  "soy yogurt": "greek yogurt",
  "natural yogurt": "greek yogurt",
  yogurt: "greek yogurt",
  cress: "watercress",
  "garden cress": "watercress",
  "lime zest": "lime",
  "lemon zest": "lemon",
  "orange zest": "orange",
  "egg yolk": "egg yolks",
  "baby spinach": "spinach",
  "salmon fillet": "salmon",
  "cod fillet": "cod",
  "pointed cabbage": "cabbage",
  "white cabbage": "cabbage",
  quark: "cream cheese",
  skyr: "greek yogurt",
  "coconut chips": "desiccated coconut",
  "rolled oats": "oats",
  "oat flakes": "oats",
  "oat meal": "oats",
  "sesame seeds": "sesame seed",
  hazelnuts: "hazlenuts",
  edamame: "peas",
  "sweet potato": "sweet potatoes",
  "white pepper": "black pepper",
  "sesame oil": "sesame seed oil",
  cherries: "cherry",
  "crème fraîche": "creme fraiche",
  "cooked penne": "penne rigate",
  pasta: "spaghetti",
  corn: "sweetcorn",
  "sweet corn": "sweetcorn",
  "canned corn": "sweetcorn",
  "baby corn": "sweetcorn",
};

/** Zusätzliche Zuordnungen nur für Spoonacular-Dateinamen. */
const SPOON: Record<string, string> = {
  flaxseeds: "flax seeds",
  flaxseed: "flax seeds",
  linseed: "flax seeds",
  "almond flakes": "almonds",
  "sliced almonds": "almonds",
  "hazelnut flakes": "hazelnuts",
  "mixed nuts": "walnuts",
  "mixed berries": "blueberries",
  laugenpretzel: "pretzels",
  "pretzel roll": "pretzels",
  pretzel: "pretzels",
  sucuk: "chorizo",
  salsiccia: "chorizo",
  cabanossi: "chorizo",
  "crunchy muesli": "granola",
  "bircher muesli": "granola",
  "chocolate granola": "granola",
  muesli: "granola",
  "creme fraiche": "sour cream",
  "crème fraîche": "sour cream",
  pickles: "dill pickles",
  "vegan margarine": "butter",
  margarine: "butter",
  "vanilla bean": "vanilla",
  "apricot halves": "dried apricots",
  plums: "plum",
  "popped amaranth": "quinoa",
  "wild rice mix": "wild rice",
};

function variants(n: string): string[] {
  const out = [n, SYN[n] ?? ""];
  for (const v of [n, SYN[n] ?? ""]) {
    if (!v) continue;
    out.push(
      `${v}s`,
      `${v}es`,
      v.replace(/ies$/, "y"),
      v.replace(/es$/, ""),
      v.replace(/s$/, ""),
      v.replace("chili", "chilli"),
    );
  }
  const w = n.split(" ");
  for (let k = 1; k < w.length; k++) {
    const t = w.slice(k).join(" ");
    out.push(t, SYN[t] ?? "", `${t}s`, t.replace(/s$/, ""));
  }
  return out.filter(Boolean);
}

export class IngredientImages {
  private known = new Map<string, string>();
  private queue: { key: string; de: string; en: string }[] = [];
  private queued = new Set<string>();
  private busy = false;

  constructor(
    private db: FitDb,
    private cacheFile: string,
    private log: (m: string) => void,
    private request: typeof fetch = fetch,
  ) {}

  /** Liste der TheMealDB-Zutaten (einmal geladen, auf der Platte gemerkt). */
  async init() {
    try {
      let list: { strIngredient?: string }[];
      if (existsSync(this.cacheFile)) list = JSON.parse(readFileSync(this.cacheFile, "utf8"));
      else {
        const r = await this.request("https://www.themealdb.com/api/json/v1/1/list.php?i=list", {
          headers: { "user-agent": UA },
        });
        list = ((await r.json()) as { meals: { strIngredient?: string }[] }).meals ?? [];
        writeFileSync(this.cacheFile, JSON.stringify(list));
      }
      for (const m of list)
        if (m.strIngredient?.trim())
          this.known.set(m.strIngredient.trim().toLowerCase(), m.strIngredient.trim());
    } catch (e) {
      this.log(`[fitness] Zutatenliste: ${e instanceof Error ? e.message : e}`);
    }
  }

  mealDb(en: string): string | null {
    const n = en
      .toLowerCase()
      .replace(/\(.*?\)/g, "")
      .trim();
    for (const c of variants(n)) {
      const hit = this.known.get(c);
      if (hit) return `${MEALDB}${encodeURIComponent(hit)}-small.png`;
    }
    return null;
  }

  /** Bild für eine Zutat oder null (dann ist sie für die Hintergrund-Suche vorgemerkt). */
  image(en: string, de: string): string | null {
    const m = this.mealDb(en);
    if (m) return m;
    const key = `ingimg:${de.toLowerCase().trim()}`;
    const cached = kvGet<{ url: string | null }>(this.db, key);
    if (cached) return cached.url;
    if (!this.queued.has(key)) {
      this.queued.add(key);
      this.queue.push({ key, de, en });
      void this.work();
    }
    return null;
  }

  private async work() {
    if (this.busy) return;
    this.busy = true;
    try {
      while (this.queue.length) {
        const job = this.queue.shift() as { key: string; de: string; en: string };
        let url: string | null = null;
        try {
          url = await this.spoonacular(job.en);
        } catch (e) {
          this.log(`[fitness] Zutatenbild ${job.de}: ${e instanceof Error ? e.message : e}`);
          // Netzfehler: nicht merken, später nochmal versuchen.
          this.queued.delete(job.key);
          await new Promise((r) => setTimeout(r, 30_000));
          continue;
        }
        kvSet(this.db, job.key, { url });
        await new Promise((r) => setTimeout(r, 400));
      }
    } finally {
      this.busy = false;
    }
  }

  /** Spoonacular-Zutatenbild: Namensvarianten als Dateinamen probieren (HEAD), erstes Treffer-Bild. */
  private async spoonacular(en: string): Promise<string | null> {
    const base = en
      .toLowerCase()
      .replace(/\(.*?\)/g, "")
      .replace(/,.*$/, "")
      .replace(/[^a-z\s-]/g, "")
      .trim();
    if (base.length < 2) return null;
    const names = [
      SPOON[base] ?? "",
      ...variants(base),
      ...variants(SPOON[base] ?? "").filter(Boolean),
    ].filter(Boolean);
    const slugs = [...new Set(names.map((v) => v.trim().replace(/\s+/g, "-")))].slice(0, 12);
    for (const slug of slugs) {
      for (const ext of ["jpg", "png"]) {
        const url = `https://img.spoonacular.com/ingredients_100x100/${slug}.${ext}`;
        const r = await this.request(url, {
          method: "HEAD",
          headers: { "user-agent": UA },
          signal: AbortSignal.timeout(8000),
        });
        if (r.ok) return url.replace("ingredients_100x100", "ingredients_250x250");
      }
    }
    return null;
  }

  /** Alle Zutaten der Rezepte einmal durchgehen, damit Bilder schon da sind, bevor jemand das Rezept öffnet. */
  prefetch(items: { en: string; name: string }[]) {
    for (const i of items) this.image(i.en, i.name);
  }
}
