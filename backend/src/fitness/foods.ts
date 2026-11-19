/**
 * Nährwert-Datenbank: Bundeslebensmittelschlüssel 4.0 (Max Rubner-Institut, CC BY 4.0), je 100 g,
 * deutsch + englisch, komplett im Speicher (7.140 Einträge). Suche über einfache Token-Wertung —
 * deutsche Komposita werden über Teilwort-Treffer gefunden (»hähnchenbrust« in »Hähnchenbrustfilet«).
 */
import { readFileSync } from "node:fs";
import { gunzipSync } from "node:zlib";
import type { FoodHit, NutrientDb } from "./meal-ai.js";
import type { Nutrients } from "./profile.js";

interface BlsFood {
  id: string;
  de: string;
  en: string;
  n: Nutrients;
}

export function norm(s: string): string {
  return s
    .toLowerCase()
    .replace(/ä/g, "ae")
    .replace(/ö/g, "oe")
    .replace(/ü/g, "ue")
    .replace(/ß/g, "ss")
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .replace(/[^a-z0-9 ]+/g, " ")
    .trim();
}

const STOP = new Set([
  "und",
  "mit",
  "in",
  "im",
  "der",
  "die",
  "das",
  "of",
  "and",
  "with",
  "the",
  "a",
  "g",
  "frisch",
  "fresh",
]);

/** Gleichbedeutende Zubereitungswörter auf eine Form bringen. */
const SYN: Record<string, string> = {
  boiled: "cooked",
  steamed: "cooked",
  gedaempft: "gekocht",
  gedunstet: "gekocht",
  roasted: "fried",
  sauteed: "fried",
  angebraten: "gebraten",
  ei: "huehnerei",
  eier: "huehnerei",
  eggs: "egg",
  haehnchen: "haehnchen",
};

function tokens(s: string): string[] {
  return norm(s)
    .split(/\s+/)
    .map((t) => SYN[t] ?? t)
    .filter((t) => t.length > 1 && !STOP.has(t));
}

/** Zubereitungs-Wörter: bevorzugt, wenn die Anfrage sie nennt, sonst leicht bestraft (außer roh). */
const PREP = [
  "gekocht",
  "gebraten",
  "gegart",
  "geduenstet",
  "gegrillt",
  "frittiert",
  "paniert",
  "konserve",
  "cooked",
  "fried",
  "boiled",
  "roasted",
  "grilled",
  "canned",
  "getrocknet",
  "dried",
];

export class BlsDb implements NutrientDb {
  private foods: BlsFood[];
  private toks: { de: string[]; en: string[]; deFlat: string; enFlat: string; glued: string }[];
  private byId: Map<string, BlsFood>;

  constructor(file: string) {
    const data = JSON.parse(gunzipSync(readFileSync(file)).toString("utf8")) as {
      foods: BlsFood[];
    };
    this.foods = data.foods;
    this.byId = new Map(this.foods.map((f) => [f.id, f]));
    this.toks = this.foods.map((f) => ({
      de: tokens(f.de),
      en: tokens(f.en),
      deFlat: norm(f.de),
      enFlat: norm(f.en),
      glued: norm(f.de).replace(/ /g, ""),
    }));
  }

  get size() {
    return this.foods.length;
  }

  private hit(f: BlsFood): FoodHit {
    return { id: `bls:${f.id}`, name: f.en || f.de, de: f.de, source: "BLS 4.0", per100: f.n };
  }

  get(id: string): FoodHit | null {
    const f = this.byId.get(id.replace(/^bls:/, ""));
    return f ? this.hit(f) : null;
  }

  search(query: string, limit = 8): FoodHit[] {
    const q = tokens(query);
    if (!q.length) return [];
    const qFlat = norm(query);
    const qGlued = qFlat.replace(/ /g, "");
    const wantsPrep = q.some((t) => PREP.some((p) => t.startsWith(p.slice(0, 5))));
    const scored: { i: number; s: number }[] = [];
    for (let i = 0; i < this.foods.length; i++) {
      const t = this.toks[i] as (typeof this.toks)[number];
      let s = 0;
      let matched = 0;
      for (const w of q) {
        let best = 0;
        // Kompositum-Treffer zählen weniger, je länger das restliche Wort ist (»Haferflockenplätzchen«).
        const part = (x: string, base: number) => base - Math.min(1, (x.length - w.length) * 0.06);
        for (const x of t.de)
          best = Math.max(
            best,
            x === w
              ? 3
              : x.startsWith(w)
                ? part(x, 2.4)
                : w.length >= 4 && x.includes(w)
                  ? part(x, 2)
                  : 0,
          );
        for (const x of t.en)
          best = Math.max(
            best,
            x === w
              ? 3
              : x.startsWith(w)
                ? part(x, 2.2)
                : w.length >= 4 && x.includes(w)
                  ? part(x, 1.4)
                  : 0,
          );
        if (best > 0) matched++;
        s += best;
      }
      // Zusammengeschrieben gefunden (»Haferflocken« ↔ »Hafer Flocken«).
      if (qGlued.length >= 5 && t.glued.startsWith(qGlued)) {
        // Genau dieses Lebensmittel (nur anders geschrieben) schlägt jedes längere Kompositum.
        s += t.glued === qGlued ? 7 : 3;
        matched = q.length;
      }
      if (!matched) continue;
      // Alle Suchwörter gefunden → deutlich besser.
      if (matched === q.length) s += 2;
      if (t.deFlat === qFlat || t.enFlat === qFlat) s += 4;
      if (t.deFlat.startsWith(q[0] as string) || t.enFlat.startsWith(q[0] as string)) s += 1.2;
      // Kürzere, einfachere Einträge bevorzugen (»Banane roh« vor »Bananen-Quark-Dessert«).
      s -= Math.max(0, t.de.length - q.length) * 0.35;
      if (!wantsPrep && PREP.some((p) => t.deFlat.includes(p) || t.enFlat.includes(p))) s -= 0.6;
      if (/\broh\b/.test(t.deFlat) || /\braw\b/.test(t.enFlat)) s += wantsPrep ? -0.4 : 0.9;
      // Fertiggerichte (BLS-Gruppen X/Y) nur, wenn nach einem Gericht gefragt wird.
      const group = (this.foods[i] as BlsFood).id[0];
      if ((group === "X" || group === "Y") && q.length <= 2) s -= 0.8;
      scored.push({ i, s });
    }
    scored.sort((a, b) => b.s - a.s);
    return scored.slice(0, limit).map(({ i }) => this.hit(this.foods[i] as BlsFood));
  }
}
