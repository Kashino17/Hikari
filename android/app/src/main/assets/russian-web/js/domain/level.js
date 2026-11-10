// Lernstand → Sprachniveau (GER/CEFR), geschätzt aus echten Lerndaten.
//
// Fünf Fähigkeiten, jede mit einem harten Messwert aus dem Lernstand:
//   Wortschatz – Sätze, die im Wiederholungs-System sitzen (Box ≥ 3)
//   Sprechen   – Sätze, die die Spracherkennung akzeptiert hat
//   Hören      – abgeschlossene Podcast-Lektionen
//   Grammatik  – abgeschlossene Satzbau-Muster
//   Gespräch   – freie Antworten im KI-Gespräch, klar und ohne Hilfe (ab B1)
// Ein Niveau ist erreicht, wenn ALLE Fähigkeiten sein Ziel erfüllen — die
// schwächste Fähigkeit ist der Engpass, und auf den reagieren die Übungen.
//
// Ehrlich gesagt: Das ersetzt keinen Sprachtest. Die Zielwerte sind
// Hikari-Richtwerte, an einer Stelle änderbar (TARGETS).

export const DIMS = [
  { id: 'vocab', name: 'Wortschatz', unit: ['sitzender Satz', 'sitzende Sätze'], color: '56,189,248', icon: 'style' },
  { id: 'speak', name: 'Sprechen', unit: ['gesprochener Satz', 'gesprochene Sätze'], color: '244,114,182', icon: 'record_voice_over' },
  { id: 'listen', name: 'Hören', unit: ['Podcast-Lektion', 'Podcast-Lektionen'], color: '167,139,250', icon: 'headphones' },
  { id: 'grammar', name: 'Grammatik', unit: ['Satzbau-Muster', 'Satzbau-Muster'], color: '52,211,153', icon: 'construction' },
  { id: 'talk', name: 'Gespräch', unit: ['freie Antwort mit Anja', 'freie Antworten mit Anja'], color: '251,191,36', icon: 'forum' },
];

/** Zielwerte je Niveau und Fähigkeit (0 = zählt für dieses Niveau noch nicht). */
export const TARGETS = {
  A1: { vocab: 80, speak: 60, listen: 5, grammar: 3, talk: 0 },
  A2: { vocab: 200, speak: 250, listen: 20, grammar: 10, talk: 0 },
  B1: { vocab: 450, speak: 700, listen: 50, grammar: 25, talk: 40 },
  B2: { vocab: 950, speak: 1600, listen: 100, grammar: 25, talk: 200 },
};
const ORDER = ['A0', 'A1', 'A2', 'B1', 'B2'];

export const LEVELS = [
  { id: 'A0', name: 'Einstieg', desc: 'Du lernst gerade die ersten Wörter und Laute. Mit jedem Kurstag wächst dein Grundstock.' },
  { id: 'A1', name: 'Anfänger', desc: 'Du verstehst und benutzt vertraute Alltagsausdrücke und ganz einfache Sätze. Du kannst dich vorstellen und einfache Fragen stellen und beantworten — wenn dein Gegenüber langsam und deutlich spricht.' },
  { id: 'A2', name: 'Grundlagen', desc: 'Du verstehst häufige Ausdrücke zu Familie, Arbeit, Einkaufen und Umgebung. Du kommst in einfachen Routinegesprächen zurecht und kannst über deine Herkunft und deine Bedürfnisse sprechen.' },
  { id: 'B1', name: 'Mittelstufe', desc: 'Du verstehst die Hauptpunkte, wenn klar über Vertrautes gesprochen wird. Du meisterst die meisten Situationen auf Reisen, erzählst von Erfahrungen und Zielen und begründest kurz deine Meinung.' },
  { id: 'B2', name: 'Gute Mittelstufe', desc: 'Du verstehst auch komplexere Gespräche und unterhältst dich spontan und fließend mit Muttersprachlern — ohne größere Anstrengung auf beiden Seiten. Das Niveau für echte, tiefe Gespräche.' },
  { id: 'C1', name: 'Fortgeschritten', desc: 'Du verstehst anspruchsvolle, lange Texte und Gespräche, auch zwischen den Zeilen, und drückst dich spontan und fließend aus.' },
  { id: 'C2', name: 'Annähernd muttersprachlich', desc: 'Du verstehst praktisch alles mühelos und drückst dich sehr flüssig und genau aus — auch in feinen Nuancen.' },
];

/** Messwerte der fünf Fähigkeiten aus Lernstand und Gesprächsreise. */
export function measure(p, chat) {
  return {
    vocab: Object.values(p.cards).filter((c) => c.box >= 3).length,
    speak: p.spokenOk,
    listen: Object.keys(p.podcastDayStars).length,
    grammar: p.patternsDone.length,
    // Nur Antworten aus dem freien KI-Gespräch (die Mini-Gespräche haben IDs mit »:«).
    talk: (chat?.observations ?? []).filter((o) => o.clear && o.independent && !String(o.id).includes(':')).length,
  };
}

/**
 * → { level, next, progress 0..1, dims:[{…DIM, value, target, frac, done, missing}], bottleneck }
 * `dims` enthält nur Fähigkeiten, die für das nächste Niveau zählen.
 */
export function estimate(m) {
  let idx = 0;
  for (let i = 1; i < ORDER.length; i++) {
    const t = TARGETS[ORDER[i]];
    if (DIMS.every((d) => m[d.id] >= t[d.id])) idx = i; else break;
  }
  const level = LEVELS.find((l) => l.id === ORDER[idx]);
  const nextId = ORDER[idx + 1] ?? null;
  const next = nextId ? LEVELS.find((l) => l.id === nextId) : null;
  // Ganz oben gibt es kein nächstes Ziel mehr — dann zeigt das Netz die erfüllten Ziele des erreichten Niveaus.
  const t = TARGETS[nextId ?? ORDER[idx]] ?? null;
  const dims = t ? DIMS.filter((d) => t[d.id] > 0).map((d) => {
    const value = m[d.id];
    const target = t[d.id];
    return { ...d, value, target, frac: Math.min(1, value / target), done: value >= target, missing: Math.max(0, target - value) };
  }) : [];
  const progress = dims.length ? dims.reduce((a, d) => a + d.frac, 0) / dims.length : 1;
  const open = dims.filter((d) => !d.done);
  const bottleneck = open.length ? open.reduce((a, d) => (d.frac < a.frac ? d : a)) : null;
  return { level, next, progress, dims, bottleneck };
}
