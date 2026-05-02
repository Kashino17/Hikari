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
- reason (kurze prägnante Beschreibung — wird als Clip-Titel angezeigt, max ~80 Zeichen)

OUTPUT: ausschließlich gültiges JSON-Array, sortiert nach start_sec ASC. Keine Markdown-Code-Blocks, keine Erklärungen außerhalb des JSON.

Hinweis: Konzentrier dich nur darauf die wertvollsten Highlight-Momente
zu finden und ihren Sinn in einem prägnanten reason-Satz zu beschreiben.
Das Rendering ist deterministisch (16:9 Original mit Blur-Hintergrund) —
kein Layout-Hinweis von dir nötig.

Beispiel:
[
  {"start_sec": 142.5, "end_sec": 198.0,
   "reason": "Klare Erklärung der Kernidee mit Diagramm"},
  {"start_sec": 305.0, "end_sec": 360.0,
   "reason": "GPT-5.5 vs Claude Benchmark-Tabelle"},
  {"start_sec": 612.0, "end_sec": 668.5,
   "reason": "Punchy Quote über praktische Anwendung"}
]`;

export function buildClipperPrompt(filter: FilterConfig, _meta: VideoMeta): string {
  return `Du bist ein Video-Highlight-Analyst für die App "Hikari" (kuratierte Kurzvideos, positiv und lehrreich, Anti-Doom-Scroll). Du siehst das Video direkt — Audio + Bild. Identifiziere die wertvollsten Highlight-Momente.

USER-KRITERIEN (was IST gut für diesen User):
- Bevorzugt: ${filter.likeTags.join(", ") || "(keine spezifischen Vorlieben)"}
- Vermeidet: ${filter.dislikeTags.join(", ") || "(keine expliziten Abneigungen)"}
- Stimmung: ${filter.moodTags.join(", ") || "(beliebig)"} | Tiefe: ${filter.depthTags.join(", ") || "(beliebig)"}
- Sprachen: ${filter.languages.join(", ") || "(beliebig)"}
- Beispiele bevorzugter Inhalte: ${filter.examples || "(keine)"}

${OPERATIONAL_RULES}

${OUTPUT_INSTRUCTIONS}`;
}
