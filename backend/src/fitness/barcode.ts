/**
 * Barcode → Produkt über Open Food Facts (kostenlos, ohne Schlüssel, weltweit > 4 Mio. Produkte).
 * Antworten werden in der Tabelle `foods` gemerkt (gefundene für immer, nicht gefundene einen Tag),
 * damit dasselbe Produkt nie zweimal abgefragt wird. Höflich: höchstens 10 Abrufe pro Minute.
 *
 * Open Food Facts speichert Mineralstoffe und Vitamine in Gramm je 100 g — hier in mg/µg umgerechnet.
 */
import type { FitDb } from "./db.js";
import type { Nutrients } from "./profile.js";

const UA = "Hikari-Fitness/1.0 (private app; https://github.com/Kashino17/Hikari)";
const FIELDS =
  "product_name,product_name_de,product_name_en,generic_name,brands,quantity,serving_size,serving_quantity,product_quantity,image_front_small_url,image_front_url,nutriments,nutriscore_grade,nova_group";

export interface BarcodeProduct {
  ean: string;
  name: string;
  brand: string | null;
  quantity: string | null;
  /** Gramm bzw. ml einer Portion laut Packung */
  serving: number | null;
  servingLabel: string | null;
  /** ganze Packung in g/ml */
  pack: number | null;
  image: string | null;
  per100: Nutrients;
  liquid: boolean;
  nutriscore: string | null;
  /** Woher: Open Food Facts, UPCitemdb (nur Name) oder selbst erfasst (Nährwerttabelle fotografiert) */
  source?: "openfoodfacts" | "upcitemdb" | "eigen";
}

// [unser Schlüssel, Feld bei Open Food Facts, Faktor]
const MAP: [keyof Nutrients, string, number][] = [
  ["kcal", "energy-kcal_100g", 1],
  ["protein", "proteins_100g", 1],
  ["carbs", "carbohydrates_100g", 1],
  ["fat", "fat_100g", 1],
  ["fiber", "fiber_100g", 1],
  ["sugar", "sugars_100g", 1],
  ["satfat", "saturated-fat_100g", 1],
  ["salt", "salt_100g", 1],
  ["omega3", "omega-3-fat_100g", 1],
  ["calcium", "calcium_100g", 1000],
  ["magnesium", "magnesium_100g", 1000],
  ["iron", "iron_100g", 1000],
  ["zinc", "zinc_100g", 1000],
  ["potassium", "potassium_100g", 1000],
  ["iodine", "iodine_100g", 1e6],
  ["vitA", "vitamin-a_100g", 1e6],
  ["vitC", "vitamin-c_100g", 1000],
  ["vitD", "vitamin-d_100g", 1e6],
  ["vitE", "vitamin-e_100g", 1000],
  ["vitK", "vitamin-k_100g", 1e6],
  ["vitB1", "vitamin-b1_100g", 1000],
  ["vitB2", "vitamin-b2_100g", 1000],
  ["vitB6", "vitamin-b6_100g", 1000],
  ["vitB12", "vitamin-b12_100g", 1e6],
  ["folate", "vitamin-b9_100g", 1e6],
];

export function mapProduct(ean: string, p: Record<string, unknown>): BarcodeProduct | null {
  const n = (p.nutriments ?? {}) as Record<string, unknown>;
  const per100: Nutrients = {};
  for (const [k, f, factor] of MAP) {
    const v = Number(n[f]);
    if (Number.isFinite(v) && v >= 0) per100[k] = Math.round(v * factor * 1000) / 1000;
  }
  // Energie nur in kJ angegeben → umrechnen.
  if (per100.kcal === undefined && Number.isFinite(Number(n["energy_100g"])))
    per100.kcal = Math.round(Number(n["energy_100g"]) / 4.184);
  const name = String(
    p.product_name_de || p.product_name || p.product_name_en || p.generic_name || "",
  ).trim();
  if (!name && per100.kcal === undefined) return null;
  const quantity = p.quantity ? String(p.quantity) : null;
  const num = (v: unknown) => (Number.isFinite(Number(v)) && Number(v) > 0 ? Number(v) : null);
  return {
    ean,
    name: name || `Produkt ${ean}`,
    brand: p.brands ? (String(p.brands).split(",")[0]?.trim() ?? null) : null,
    quantity,
    serving: num(p.serving_quantity),
    servingLabel: p.serving_size ? String(p.serving_size) : null,
    pack: num(p.product_quantity),
    image: (p.image_front_url as string) || (p.image_front_small_url as string) || null,
    per100,
    liquid: /\d\s*(ml|cl|l)\b/i.test(quantity ?? "") || /ml/i.test(String(p.serving_size ?? "")),
    nutriscore: p.nutriscore_grade ? String(p.nutriscore_grade) : null,
  };
}

export class BarcodeLookup {
  private recent: number[] = [];
  constructor(
    private db: FitDb,
    private request: typeof fetch = fetch,
  ) {}

  /** Selbst erfasstes Produkt (Nährwerttabelle fotografiert) — gilt ab jetzt für diese Nummer. */
  save(product: BarcodeProduct) {
    this.db
      .prepare(
        "INSERT INTO foods(key, name, source, per100, updated_at) VALUES(?, ?, 'eigen', ?, ?) ON CONFLICT(key) DO UPDATE SET name = excluded.name, source = excluded.source, per100 = excluded.per100, updated_at = excluded.updated_at",
      )
      .run(`off:${product.ean}`, product.name, JSON.stringify(product), Date.now());
  }

  async find(ean: string): Promise<BarcodeProduct | null> {
    const key = `off:${ean}`;
    const row = this.db.prepare("SELECT per100, updated_at FROM foods WHERE key = ?").get(key) as
      | { per100: string; updated_at: number }
      | undefined;
    if (row) {
      const cached = JSON.parse(row.per100) as BarcodeProduct | null;
      if (cached || Date.now() - row.updated_at < 86_400_000) return cached;
    }
    // Höflichkeit: höchstens 10 Abrufe pro Minute.
    const now = Date.now();
    this.recent = this.recent.filter((t) => now - t < 60_000);
    if (this.recent.length >= 10) throw new Error("rate");
    this.recent.push(now);
    let product: BarcodeProduct | null = null;
    for (const host of ["world.openfoodfacts.org", "world.openfoodfacts.net"]) {
      try {
        const r = await this.request(
          `https://${host}/api/v2/product/${ean}.json?fields=${FIELDS}`,
          {
            headers: { "user-agent": UA },
            signal: AbortSignal.timeout(12_000),
          },
        );
        if (r.status === 404) break;
        if (!r.ok) continue;
        const d = (await r.json()) as { status?: number; product?: Record<string, unknown> };
        product = d.status === 1 && d.product ? mapProduct(ean, d.product) : null;
        if (product) product.source = "openfoodfacts";
        break;
      } catch {
        /* Ausweich-Host versuchen */
      }
    }
    // Nicht bei Open Food Facts: Name über UPCitemdb (frei, 100 am Tag), dann Websuche der KI.
    let hint: { name: string; brand: string | null; image: string | null } | null = null;
    if (!product?.per100.kcal) hint = await this.upcItemDb(ean).catch(() => null);
    // Websuche über Gemini (google_search) geht nur mit Abrechnung — im kostenlosen Kontingent
    // antwortet Google sofort mit 429 (gemessen 19.11.2026). Deshalb: Nährwerttabelle fotografieren.
    if (!product && hint)
      product = {
        ean,
        name: hint.name,
        brand: hint.brand,
        quantity: null,
        serving: null,
        servingLabel: null,
        pack: null,
        image: hint.image,
        per100: {},
        liquid: false,
        nutriscore: null,
        source: "upcitemdb",
      };
    // Nichts gefunden nur einen Tag merken (vielleicht trägt es jemand bei Open Food Facts ein).
    this.db
      .prepare(
        "INSERT INTO foods(key, name, source, per100, updated_at) VALUES(?, ?, ?, ?, ?) ON CONFLICT(key) DO UPDATE SET name = excluded.name, source = excluded.source, per100 = excluded.per100, updated_at = excluded.updated_at",
      )
      .run(
        key,
        product?.name ?? "",
        product?.source ?? "openfoodfacts",
        JSON.stringify(product),
        Date.now(),
      );
    return product;
  }

  private async upcItemDb(ean: string) {
    const r = await this.request(`https://api.upcitemdb.com/prod/trial/lookup?upc=${ean}`, {
      headers: { "user-agent": UA },
      signal: AbortSignal.timeout(10_000),
    });
    if (!r.ok) return null;
    const d = (await r.json()) as {
      items?: { title?: string; brand?: string; images?: string[] }[];
    };
    const it = d.items?.[0];
    return it?.title
      ? { name: it.title, brand: it.brand || null, image: it.images?.[0] ?? null }
      : null;
  }
}
