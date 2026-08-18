import type { FilterConfig } from "./filter.js";

/**
 * Schriftsysteme, die eine Sprache eindeutig verraten — anders als Latein,
 * das für Dutzende Sprachen steht. Jeder Eintrag nennt die Sprachcodes, bei
 * denen die Schrift erwünscht ist.
 */
const SCRIPTS: { name: string; test: RegExp; languages: string[] }[] = [
  { name: "Devanagari", test: /[ऀ-ॿ]/g, languages: ["hi", "mr", "ne", "sa"] },
  { name: "Thai", test: /[฀-๿]/g, languages: ["th"] },
  { name: "Arabisch", test: /[؀-ۿ]/g, languages: ["ar", "fa", "ur"] },
  { name: "Kyrillisch", test: /[Ѐ-ӿ]/g, languages: ["ru", "uk", "bg", "sr"] },
  { name: "Hangul", test: /[가-힯]/g, languages: ["ko"] },
  { name: "Japanisch/Chinesisch", test: /[぀-ヿ一-鿿]/g, languages: ["ja", "zh"] },
  { name: "Hebräisch", test: /[֐-׿]/g, languages: ["he"] },
  { name: "Griechisch", test: /[Ͱ-Ͽ]/g, languages: ["el"] },
];

/** Ab diesem Anteil fremder Zeichen gilt der Titel als fremdsprachig. */
const FOREIGN_SHARE = 0.25;

/**
 * Billiger Vorfilter vor dem teuren LLM-Aufruf: Videos, deren Titel in einer
 * Schrift steht, die keine der gewünschten Sprachen nutzt, lehnt der Scorer
 * ohnehin ab — das muss kein Sprachmodell entscheiden. Spart bei fremdsprachigen
 * Empfehlungswellen Stunden Bewertungszeit.
 *
 * Gibt den Ablehnungsgrund zurück oder null, wenn regulär bewertet werden soll.
 */
export function prefilterReason(
  title: string,
  description: string,
  filter: FilterConfig,
  videoLanguage?: string | null,
): string | null {
  const allowedLangs = new Set(filter.languages.map((l) => l.toLowerCase()));
  // YouTube nennt für viele Videos die Sprache — die genaueste und billigste
  // Auskunft, die wir bekommen können.
  if (videoLanguage) {
    const base = videoLanguage.toLowerCase().split("-")[0] ?? "";
    if (base && !allowedLangs.has(base)) {
      return `Vorfilter: Sprache ${base} — gewünscht sind ${filter.languages.join(", ")}`;
    }
  }

  const text = `${title} ${description}`.trim();
  if (text.length === 0) return null;
  const letters = text.replace(/[^\p{L}]/gu, "");
  if (letters.length < 8) return null; // zu wenig Text für eine Aussage

  for (const script of SCRIPTS) {
    if (script.languages.some((l) => allowedLangs.has(l))) continue;
    const hits = text.match(script.test)?.length ?? 0;
    if (hits / letters.length >= FOREIGN_SHARE) {
      return `Vorfilter: ${script.name}-Schrift — nicht in den gewünschten Sprachen (${filter.languages.join(", ")})`;
    }
  }
  return null;
}
