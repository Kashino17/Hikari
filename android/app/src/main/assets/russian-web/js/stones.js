// Fehler-Gedächtnis (»Stolpersteine«): was dir in Gesprächen mit Anja öfter passiert.
// Anja baut Gelegenheiten dafür ins Gespräch ein, die Grammatik zeigt es als Übungsziel.
// Eigener Schlüssel — nie in `progress_v1` (das liest die App mit und verwirft fremde Felder).
import { storage } from './platform.js';

const KEY = 'anja_stones_v1';
const MAX = 60;

const read = () => storage.getJSON(KEY, {}) ?? {};

export const stones = {
  /** Alle Stolpersteine: [{topic, n, last, wrongRu, rightRu}], die häufigsten und jüngsten zuerst. */
  all() {
    return Object.entries(read()).map(([topic, s]) => ({ topic, n: 0, last: 0, wrongRu: '', rightRu: '', ...s }))
      .sort((a, b) => b.n - a.n || b.last - a.last);
  },
  /** Die wichtigsten für Anjas Auftrag. */
  top(n = 5) { return this.all().slice(0, n); },
  /** Ein bestätigter Fehler aus dem Gespräch. */
  add({ topic, wrongRu = '', rightRu = '', day = 0 }) {
    const t = (topic ?? '').trim().slice(0, 80);
    if (!t) return;
    const m = read();
    const old = m[t] ?? { n: 0 };
    m[t] = { n: old.n + 1, last: day, wrongRu: wrongRu.slice(0, 200), rightRu: rightRu.slice(0, 200) };
    // Nur die häufigsten und jüngsten behalten.
    const keep = Object.entries(m).sort((a, b) => b[1].n - a[1].n || b[1].last - a[1].last).slice(0, MAX);
    storage.set(KEY, Object.fromEntries(keep));
  },
};

/** Wörter eines Satzes für den Vergleich: klein, ohne Satzzeichen, ё wie е. */
export const wordsOf = (s) => (s ?? '').toLowerCase().replaceAll('ё', 'е').split(/[^а-яa-z-]+/i).filter(Boolean);

/** Welche Wörter des gesagten Satzes stehen so nicht im verbesserten? → Set der Positionen */
export function wrongPositions(said, corrected) {
  const right = new Set(wordsOf(corrected));
  const out = new Set();
  (said ?? '').split(/\s+/).forEach((w, i) => { const k = wordsOf(w)[0]; if (k && !right.has(k)) out.add(i); });
  return out;
}
