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
  WICHTIG für Smart-Crop auf 9:16:
  • Bei Personen/Talking-Heads: setze (x,y) auf das GESICHT der gerade sprechenden Person
  • Bei zwei Personen im Frame: das Gesicht der dominanten/sprechenden Person
  • Bei Diagrammen/Slides/Code: das ZENTRUM des Inhalts, NICHT der leeren Wand drumherum
  • Bei Live-Demo: die Hand/das UI-Element, das gerade interagiert
  Vermeide stumpfes (0.5, 0.5) wenn die Action seitlich ist!
- focus.w, focus.h = Größe des wichtigen Bereichs, normalisiert 0.0–1.0
  (nur als Hinweis — der Crop wird automatisch auf 9:16 gerechnet)
- reason (kurze prägnante Beschreibung — wird als Clip-Titel angezeigt, max ~80 Zeichen)
- display_mode = "smart-crop" oder "fit"
  ENTSCHEIDUNGSREGEL nach VERHÄLTNIS Gesicht-zu-Frame:

  ╔═══════════════════════════════════════════════════════════════════╗
  ║  TALKING-HEAD (Gesicht ist der Hauptinhalt, füllt großen Teil    ║
  ║  des Frames, kein konkurrierender Bildschirm/Visualisierung)     ║
  ║  → "smart-crop". focus.x/y = Mitte des sprechenden Gesichts.     ║
  ║  Beispiel: Solo-Interview, Podcast-Setup, Vlog mit Speaker-Cam   ║
  ║  als Hauptmotiv.                                                  ║
  ╠═══════════════════════════════════════════════════════════════════╣
  ║  REACTION / EXPLAINER (kleine Reactor-Cam + großer Bildschirm   ║
  ║  oder Content im Frame, Sprecher ist eine Picture-in-Picture-    ║
  ║  Box in der Ecke <25% Frame-Fläche)                               ║
  ║  → "fit". Der BILDSCHIRM/CONTENT ist der Punkt — Gesicht-Cam ist ║
  ║  nur Beigabe. Frame komplett sichtbar mit Blur-BG.                ║
  ║  Beispiel: User reagiert auf Tweet, Streamer mit Game-Footage,   ║
  ║  Erklärvideo mit Slide + kleiner Webcam unten rechts.             ║
  ╠═══════════════════════════════════════════════════════════════════╣
  ║  PURE SLIDES / UI / CODE / DIAGRAMS (keine Menschen sichtbar)    ║
  ║  → "fit". Komplettes Frame sichtbar mit Blur-BG.                  ║
  ╚═══════════════════════════════════════════════════════════════════╝

  Faustregel: Wie groß ist das Gesicht im Frame?
  • Gesicht > ~30% Frame-Fläche → smart-crop (Face IS the content)
  • Gesicht < ~25% Frame-Fläche → fit (Face ist Begleiter, Content ist
                                        woanders)
  • Dazwischen (25-30%): nimm den Hinweis vom Audio/Transkript — wenn
    der Sprecher auf etwas zeigt/erklärt/reagiert → fit, wenn er
    primär selbst Aussagen trifft → smart-crop.

OUTPUT: ausschließlich gültiges JSON-Array, sortiert nach start_sec ASC. Keine Markdown-Code-Blocks, keine Erklärungen außerhalb des JSON.

Beispiele für korrekt gesetzten focus.{x,y}:

Zwei-Personen-Interview, Sprecher links:
  {"start_sec": ..., "focus": {"x": 0.3, "y": 0.4, "w": 0.3, "h": 0.6},
   "reason": "Kernaussage zum Thema X"}

Talking-Head zentral, große Wand drumherum:
  {"start_sec": ..., "focus": {"x": 0.5, "y": 0.45, "w": 0.3, "h": 0.7},
   "reason": "Pointierte These über Y"}

Slide/Diagramm-Demo, Inhalt rechts neben Sprecher:
  {"start_sec": ..., "focus": {"x": 0.65, "y": 0.5, "w": 0.4, "h": 0.6},
   "reason": "Benchmark-Vergleich GPT-5 vs Claude"}

Volles JSON-Output-Beispiel:
[
  {"start_sec": 142.5, "end_sec": 198.0,
   "focus": {"x": 0.5, "y": 0.4, "w": 0.5, "h": 0.7},
   "reason": "Klare Erklärung der Kernidee mit Diagramm",
   "display_mode": "smart-crop"},
  {"start_sec": 305.0, "end_sec": 360.0,
   "focus": {"x": 0.5, "y": 0.5, "w": 1.0, "h": 1.0},
   "reason": "GPT-5.5 vs Claude Benchmark-Tabelle (textlastig)",
   "display_mode": "fit"},
  {"start_sec": 612.0, "end_sec": 668.5,
   "focus": {"x": 0.3, "y": 0.45, "w": 0.4, "h": 0.6},
   "reason": "Punchy Quote über praktische Anwendung",
   "display_mode": "smart-crop"}
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
