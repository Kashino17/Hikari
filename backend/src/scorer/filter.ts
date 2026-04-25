/**
 * Single source of truth for the user's curation filter. The Tuning UI edits
 * `FilterConfig`; the scoring pipeline calls `buildPrompt(filter)` to derive
 * the system prompt right before each LLM call. An optional `promptOverride`
 * lets the user paste a hand-tuned prompt when the form isn't enough.
 */

export interface FilterConfig {
  likeTags: string[];
  dislikeTags: string[];
  moodTags: string[];
  depthTags: string[];
  languages: string[];
  minDurationSec: number;
  maxDurationSec: number;
  examples: string;
  scoreThreshold: number; // 0–100, gates feed visibility
}

export const DEFAULT_FILTER: FilterConfig = {
  likeTags: ["Mathematik", "Philosophie", "Wissenschaft"],
  dislikeTags: ["Clickbait", "Drama", "Reaction"],
  moodTags: ["ruhig", "durchdacht"],
  depthTags: ["lehrreich", "tiefgründig"],
  languages: ["de", "en"],
  minDurationSec: 180,
  maxDurationSec: 1800,
  examples: "",
  scoreThreshold: 70,
};

function fmtMin(s: number): string {
  return `${Math.round(s / 60)}min`;
}

export function buildPrompt(f: FilterConfig): string {
  const parts: string[] = [];

  parts.push(
    "Du bist Hikaris KI-Bewerter für Kurzvideos. Bewerte jeden Clip mit einem Score von 0–100 basierend auf den folgenden Kriterien.",
  );

  parts.push("");
  parts.push("## Was der Nutzer mag");
  if (f.likeTags.length) parts.push(`Themen: ${f.likeTags.join(", ")}`);
  if (f.moodTags.length) parts.push(`Stimmung: ${f.moodTags.join(", ")}`);
  if (f.depthTags.length) parts.push(`Stil: ${f.depthTags.join(", ")}`);

  parts.push("");
  parts.push("## Was der Nutzer NICHT mag");
  if (f.dislikeTags.length) {
    parts.push(f.dislikeTags.map((t) => `– ${t}`).join("\n"));
  } else {
    parts.push("– keine speziellen Ausschlüsse");
  }

  parts.push("");
  parts.push("## Harte Filter");
  parts.push(`– Sprachen: ${f.languages.join(", ").toUpperCase()}`);
  parts.push(`– Dauer: ${fmtMin(f.minDurationSec)}–${fmtMin(f.maxDurationSec)}`);
  parts.push(`– Mindest-Score zur Anzeige: ${f.scoreThreshold}/100`);

  if (f.examples.trim()) {
    parts.push("");
    parts.push("## Beispiele für Videos die der Nutzer geliebt hat");
    parts.push(f.examples.trim());
  }

  parts.push("");
  parts.push("## Bewertungsachsen");
  parts.push(
    "Bewerte vier Achsen: overallScore (0–100), clickbaitRisk (0–10), educationalValue (0–10), emotionalManipulation (0–10).",
  );
  parts.push(
    "Hoher overallScore (80–100): Trifft die Kriterien oben präzise, respektiert die Intelligenz des Zuschauers, kein Clickbait, klare Aussage, gute Informationsdichte.",
  );
  parts.push(
    "Niedriger overallScore (0–40): Reine Unterhaltung ohne Lernwert, irreführende Titel, emotionale Manipulation, Padding, oder verstößt gegen die NICHT-Liste.",
  );

  parts.push("");
  parts.push("## Ausgabe");
  parts.push(
    "Strikt JSON gemäß Schema. category ist eines von: science|tech|philosophy|history|math|art|language|society|other. reasoning: 1–2 deutsche Sätze.",
  );

  return parts.join("\n");
}

/**
 * Defensive: shape-validate parsed JSON before persisting. Returns null on
 * malformed input so the caller can 4xx instead of corrupting the row.
 */
export function validateFilter(input: unknown): FilterConfig | null {
  if (!input || typeof input !== "object") return null;
  const f = input as Record<string, unknown>;
  const isStringArray = (v: unknown): v is string[] =>
    Array.isArray(v) && v.every((x) => typeof x === "string");
  if (
    !isStringArray(f.likeTags) ||
    !isStringArray(f.dislikeTags) ||
    !isStringArray(f.moodTags) ||
    !isStringArray(f.depthTags) ||
    !isStringArray(f.languages) ||
    typeof f.minDurationSec !== "number" ||
    typeof f.maxDurationSec !== "number" ||
    typeof f.examples !== "string" ||
    typeof f.scoreThreshold !== "number"
  ) {
    return null;
  }
  if (f.minDurationSec < 0 || f.maxDurationSec < f.minDurationSec) return null;
  if (f.scoreThreshold < 0 || f.scoreThreshold > 100) return null;
  return {
    likeTags: f.likeTags,
    dislikeTags: f.dislikeTags,
    moodTags: f.moodTags,
    depthTags: f.depthTags,
    languages: f.languages,
    minDurationSec: f.minDurationSec,
    maxDurationSec: f.maxDurationSec,
    examples: f.examples,
    scoreThreshold: f.scoreThreshold,
  };
}
