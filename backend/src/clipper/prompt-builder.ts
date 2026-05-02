import type { FilterConfig } from "../scorer/filter.js";

export interface VideoMeta {
  aspectRatio: string | null;
}

const OPERATIONAL_RULES = `OPERATIONELLE REGELN (fest):
- Pro Clip: zwischen 20s und 60s, Toleranz bis 90s wenn der Highlight-Moment unteilbar ist
- Anzahl Clips: ungefähr 1 pro 5 Min Original-Dauer (5 Min→1, 15 Min→3, 30 Min→6, 60 Min→12), aber NUR wenn Qualität es trägt
- Lieber WENIGER Clips von hoher Qualität als das ganze Video zerstückeln
- Wenn das Video keine highlight-würdigen Momente hat: leere Liste []`;

const OUTPUT_INSTRUCTIONS = `PRO CLIP gibst du an:
- start_sec, end_sec (Float)
- focus.x, focus.y = ZENTRUM des wichtigen Bildbereichs, normalisiert 0.0–1.0
  (z.B. ein zentriertes Talking-Head-Gesicht: x=0.5, y=0.4)
- focus.w, focus.h = Größe des wichtigen Bereichs, normalisiert 0.0–1.0
  (nur als Hinweis — der Crop wird automatisch auf 9:16 gerechnet)
- reason (kurze Begründung warum dieser Part)

OUTPUT: ausschließlich gültiges JSON-Array, sortiert nach start_sec ASC. Keine Markdown-Code-Blocks, keine Erklärungen außerhalb des JSON.

Beispiel (Talking-Head-Video, Action zentriert):
[
  {"start_sec": 142.5, "end_sec": 198.0,
   "focus": {"x": 0.5, "y": 0.4, "w": 0.5, "h": 0.7},
   "reason": "Klare Erklärung der Kernidee mit Diagramm"},
  {"start_sec": 612.0, "end_sec": 668.5,
   "focus": {"x": 0.5, "y": 0.5, "w": 0.4, "h": 0.6},
   "reason": "Punchy Quote über praktische Anwendung"}
]`;

export function buildClipperPrompt(filter: FilterConfig, meta: VideoMeta): string {
  const portraitHint =
    meta.aspectRatio === "9:16"
      ? "\n  HINWEIS: Das Original ist schon hochkant (9:16). Setze focus immer auf x=0, y=0, w=1, h=1 — kein Crop nötig, nur Cut."
      : "";

  return `Du bist ein Video-Highlight-Analyst für die App "Hikari" (kuratierte Kurzvideos, positiv und lehrreich, Anti-Doom-Scroll). Du siehst das Video direkt — Audio + Bild. Identifiziere die wertvollsten Highlight-Momente.

USER-KRITERIEN (was IST gut für diesen User):
- Bevorzugt: ${filter.likeTags.join(", ") || "(keine spezifischen Vorlieben)"}
- Vermeidet: ${filter.dislikeTags.join(", ") || "(keine expliziten Abneigungen)"}
- Stimmung: ${filter.moodTags.join(", ") || "(beliebig)"} | Tiefe: ${filter.depthTags.join(", ") || "(beliebig)"}
- Sprachen: ${filter.languages.join(", ") || "(beliebig)"}
- Beispiele bevorzugter Inhalte: ${filter.examples || "(keine)"}

${OPERATIONAL_RULES}${portraitHint}

${OUTPUT_INSTRUCTIONS}`;
}
