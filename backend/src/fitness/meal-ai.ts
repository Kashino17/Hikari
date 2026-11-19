/**
 * KI-Foto-Tracker und Ernährungs-Coach.
 *
 * Foto → Gemini erkennt Gericht, Zutaten und Gramm (Vision) → jede Zutat wird in der lokalen
 * Nährwert-Datenbank nachgeschlagen (Kandidaten per Volltextsuche, das Modell wählt den passenden
 * Eintrag in derselben Antwort aus einer zweiten, kurzen Anfrage) → Nährwerte = Datenbank × Gramm.
 * Nur wenn die Datenbank nichts Passendes hat, schätzt das Modell die Werte je 100 g selbst
 * (gekennzeichnet als »geschätzt«).
 */
import type { FitnessAi } from "./ai.js";
import type { FitDb } from "./db.js";
import {
  NUTRIENTS,
  type Nutrients,
  type Targets,
  roundNutrients,
  scaleNutrients,
  sumNutrients,
} from "./profile.js";

export interface FoodHit {
  id: string;
  name: string;
  /** deutscher Name, falls die Quelle einen hat */
  de?: string;
  source: string;
  per100: Nutrients;
}

export interface NutrientDb {
  search(query: string, limit?: number): FoodHit[];
  get(id: string): FoodHit | null;
}

export interface MealItem {
  name: string;
  grams: number;
  /** Datenbank-Eintrag oder "ki" (geschätzt) */
  food: { id: string; name: string; source: string } | null;
  per100: Nutrients;
  n: Nutrients;
  confidence?: number;
}

const VISION_SYSTEM = `Du bist Ernährungswissenschaftler und erkennst Mahlzeiten auf Fotos sehr genau.
Bestimme das Gericht und JEDE erkennbare Komponente (auch Öl/Butter zum Braten, Soßen, Dressing, Getränke) mit realistischer Menge in Gramm.
Schätze Mengen über Tellergröße (~26 cm), Besteck, Hände, Verpackungen. Bei verdeckten Zutaten typische Rezepturen annehmen.
Für jede Komponente: deutscher Name, englischer Suchbegriff wie in der USDA-Datenbank (z. B. "rice white cooked", "chicken breast roasted", "olive oil"), Zubereitung, Gramm, Sicherheit 0–1.
Falls es kein Essen ist: {"food":false}.
Antworte NUR mit JSON: {"food":true,"dish":"Name auf Deutsch","items":[{"name":"…","query":"…","grams":0,"confidence":0.0}],"portion_note":"kurzer Hinweis zur Schätzung"}`;

const PICK_SYSTEM = `Ordne jeder Zutat den passenden Eintrag aus ihren Kandidaten zu (Zubereitung beachten: roh/gekocht/gebraten).
Wenn kein Kandidat passt, gib -1 und schätze die Nährwerte je 100 g selbst (Felder: ${NUTRIENTS.map((n) => n.k).join(", ")}; Einheiten: kcal, g, mg, µg wie üblich; salt in g).
Antworte NUR mit JSON: {"picks":[{"i":0,"pick":2,"estimate":null}]}`;

interface VisionRaw {
  food?: boolean;
  dish?: string;
  items?: { name?: string; query?: string; grams?: number; confidence?: number }[];
  portion_note?: string;
}

export function cleanNutrients(raw: unknown): Nutrients {
  const out: Nutrients = {};
  if (!raw || typeof raw !== "object") return out;
  for (const { k } of NUTRIENTS) {
    const v = Number((raw as Record<string, unknown>)[k]);
    if (Number.isFinite(v) && v >= 0) out[k] = v;
  }
  return out;
}

/** Zutatenliste (Name, Suchbegriff, Gramm) → Nährwerte über die Datenbank, Rest geschätzt. */
export async function resolveItems(
  ai: FitnessAi | null,
  ndb: NutrientDb,
  items: { name: string; query: string; grams: number; confidence?: number }[],
  task: "text" | "bulk" = "text",
): Promise<MealItem[]> {
  const cands = items.map((it) => {
    const seen = new Set<string>();
    const hits: FoodHit[] = [];
    for (const q of [it.query, it.name]) {
      for (const h of ndb.search(q, 6)) {
        if (seen.has(h.id)) continue;
        seen.add(h.id);
        hits.push(h);
      }
    }
    return hits.slice(0, 8);
  });
  let picks: { pick: number; estimate: Nutrients | null }[] = items.map((_, i) => ({
    pick: cands[i]?.length ? 0 : -1,
    estimate: null,
  }));
  if (ai?.available()) {
    try {
      const input = items.map((it, i) => ({
        i,
        zutat: `${it.name} (${it.query})`,
        kandidaten: (cands[i] ?? []).map((c, j) => `${j}: ${c.de ? `${c.de} / ` : ""}${c.name}`),
      }));
      const { value } = await ai.ask(
        { task, system: PICK_SYSTEM, input, maxTokens: 3000, temperature: 0.1 },
        (raw) => {
          const r = raw as { picks?: { i?: number; pick?: number; estimate?: unknown }[] };
          if (!Array.isArray(r.picks)) throw new Error("keine_picks");
          return r.picks;
        },
      );
      picks = items.map((_, i) => {
        const p = value.find((x) => Number(x.i) === i);
        const pick = Number(p?.pick);
        const valid = Number.isInteger(pick) && pick >= 0 && pick < (cands[i]?.length ?? 0);
        return { pick: valid ? pick : -1, estimate: valid ? null : cleanNutrients(p?.estimate) };
      });
    } catch {
      /* erster Kandidat bleibt */
    }
  }
  return items.map((it, i) => {
    const p = picks[i] ?? { pick: -1, estimate: null };
    const hit = p.pick >= 0 ? cands[i]?.[p.pick] : undefined;
    const per100 = hit ? hit.per100 : (p.estimate ?? {});
    const grams = Math.max(1, Math.round(it.grams));
    return {
      name: it.name,
      grams,
      food: hit
        ? { id: hit.id, name: hit.de ?? hit.name, source: hit.source }
        : Object.keys(per100).length
          ? { id: "ki", name: "KI-Schätzung", source: "ki" }
          : null,
      per100,
      n: roundNutrients(scaleNutrients(per100, grams)),
      ...(it.confidence !== undefined ? { confidence: it.confidence } : {}),
    };
  });
}

export async function analyzePhoto(
  ai: FitnessAi,
  ndb: NutrientDb,
  image: { mime: string; base64: string },
  hint?: string,
) {
  const { value, model } = await ai.ask(
    {
      task: "vision",
      system: VISION_SYSTEM,
      input: hint ? `Hinweis vom Nutzer: ${hint}` : "Analysiere dieses Foto.",
      image,
      maxTokens: 2500,
      temperature: 0.2,
      timeoutMs: 60_000,
    },
    (raw) => {
      const r = raw as VisionRaw;
      if (r.food === false) return { food: false as const };
      const items = (r.items ?? [])
        .filter((i) => i?.name && Number(i.grams) > 0)
        .map((i) => ({
          name: String(i.name).slice(0, 60),
          query: String(i.query || i.name).slice(0, 80),
          grams: Math.min(2000, Number(i.grams)),
          confidence: Math.min(1, Math.max(0, Number(i.confidence) || 0.6)),
        }));
      if (!items.length) throw new Error("keine_zutaten");
      return {
        food: true as const,
        dish: String(r.dish || "Mahlzeit").slice(0, 80),
        items,
        note: String(r.portion_note ?? "").slice(0, 200),
      };
    },
  );
  if (!value.food) return { food: false as const, model };
  const items = await resolveItems(ai, ndb, value.items);
  return {
    food: true as const,
    dish: value.dish,
    note: value.note,
    items,
    totals: roundNutrients(sumNutrients(items.map((i) => i.n))),
    model,
  };
}

// ── Ernährungs-Coach (Tag + Woche) ──────────────────────────────────────────

const DAY_SYSTEM = `Du bist ein sehr guter, ehrlicher Ernährungscoach (Sporternährung). Du siehst das Ernährungstagebuch des Nutzers (Mahlzeiten mit Zutaten), seine Ziele und den Nährstoffstand.
Gib eine präzise Einschätzung auf Deutsch, du-Form, ohne Emojis, ohne Moralpredigt:
- was gut läuft (konkret),
- was nicht so gut ist (konkret, mit Zahlen: z. B. "Eiweiß 62 von 150 g"),
- Mikronährstoff-Lücken mit Lebensmitteln, die sie schließen,
- ob die Ernährung einseitig ist (Vielfalt, Gemüse/Obst, verarbeitete Lebensmittel),
- 1–3 konkrete nächste Schritte für heute/morgen (z. B. ein Abendessen-Vorschlag mit Mengen).
WICHTIG: Beachte "uhrzeit" und "tag_vorbei". Ist der Tag noch nicht vorbei, bewerte den bisherigen Verlauf fair (fehlende Mahlzeiten kommen noch) und formuliere Lücken als "bis heute Abend noch …" mit konkretem Vorschlag, wie der Rest des Tages die Ziele erreicht. Werte, die durch Brat-/Kochfett entstehen, nicht als Qualitätsmerkmal loben.
Bewertung 1–10 für den bisherigen Tag. Antworte NUR mit JSON:
{"score":7,"headline":"eine Zeile","good":["…"],"bad":["…"],"gaps":[{"nutrient":"Vitamin D","why":"…","foods":["Lachs 150 g","…"]}],"variety":"ein Satz zur Vielfalt","next":["…"]}`;

export interface DayReport {
  score: number;
  headline: string;
  good: string[];
  bad: string[];
  gaps: { nutrient: string; why: string; foods: string[] }[];
  variety: string;
  next: string[];
}

export function nutrientStatus(totals: Nutrients, targets: Targets) {
  return NUTRIENTS.map((n) => {
    const g = targets.goals[n.k] ?? {};
    const v = totals[n.k] ?? 0;
    const ref = g.min ?? g.max ?? 0;
    return {
      k: n.k,
      name: n.name,
      unit: n.unit,
      value: Math.round(v * 10) / 10,
      min: g.min ?? null,
      max: g.max ?? null,
      pct: ref ? Math.round((v / ref) * 100) : null,
    };
  });
}

export async function dayReport(
  ai: FitnessAi,
  input: {
    day: string;
    time: string;
    dayOver: boolean;
    profile: unknown;
    targets: Targets;
    meals: { meal: string; title: string; items: { name: string; grams: number }[] }[];
    totals: Nutrients;
    training: unknown;
    last7: unknown;
  },
): Promise<DayReport & { model: string }> {
  const status = nutrientStatus(input.totals, input.targets)
    .filter((s) => s.pct !== null)
    .map(
      (s) =>
        `${s.name}: ${s.value} ${s.unit} (${s.max ? `max ${s.max}` : `Ziel ${s.min}`}, ${s.pct} %)`,
    );
  const { value, model } = await ai.ask(
    {
      task: "text",
      system: DAY_SYSTEM,
      input: {
        tag: input.day,
        uhrzeit: input.time,
        tag_vorbei: input.dayOver,
        profil: input.profile,
        mahlzeiten: input.meals,
        naehrstoffe: status,
        training_heute: input.training,
        letzte_7_tage: input.last7,
      },
      maxTokens: 2500,
      temperature: 0.4,
    },
    (raw) => {
      const r = raw as Partial<DayReport>;
      if (!r.headline) throw new Error("leer");
      const list = (a: unknown, n = 5) =>
        Array.isArray(a) ? a.map((s) => String(s).slice(0, 240)).slice(0, n) : [];
      return {
        score: Math.min(10, Math.max(1, Math.round(Number(r.score) || 5))),
        headline: String(r.headline).slice(0, 140),
        good: list(r.good),
        bad: list(r.bad),
        gaps: (Array.isArray(r.gaps) ? r.gaps : []).slice(0, 5).map((g) => ({
          nutrient: String(g?.nutrient ?? "").slice(0, 40),
          why: String(g?.why ?? "").slice(0, 200),
          foods: list(g?.foods, 4),
        })),
        variety: String(r.variety ?? "").slice(0, 240),
        next: list(r.next, 3),
      };
    },
  );
  return { ...value, model };
}

export function foodLogDay(db: FitDb, day: string) {
  return (
    db.prepare("SELECT * FROM food_log WHERE day = ? ORDER BY created_at").all(day) as Record<
      string,
      unknown
    >[]
  ).map((r) => ({
    id: String(r.id),
    day: String(r.day),
    meal: String(r.meal),
    title: String(r.title),
    source: String(r.source),
    photo: (r.photo as string) ?? null,
    recipeId: (r.recipe_id as string) ?? null,
    items: JSON.parse(String(r.items)) as MealItem[],
    totals: JSON.parse(String(r.totals)) as Nutrients,
    aiNote: (r.ai_note as string) ?? null,
    createdAt: Number(r.created_at),
  }));
}
