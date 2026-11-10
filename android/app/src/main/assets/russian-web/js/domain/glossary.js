// Wort-für-Wort-Bedeutungen aus glosses.json — Portierung von RussianGlossary.kt.
// `s`: Satz (ohne Betonung) → Glosse je Wort ("=" = gehört zur Glosse davor),
// `w`: Rückfall je Wortform, `st`: Wortform → betonte Schreibung.
import * as Ph from './phonetics.js';

export const JOIN = '=';
const VOWELS = 'аеёиоуыэюя';

let data = { s: {}, w: {}, st: {} };
let index = null;

export function install(d) {
  data = { s: d.s ?? {}, w: d.w ?? {}, st: d.st ?? {} };
  index = null;
}

const norm = (w) => w.toLowerCase().replaceAll('ё', 'е');

function sentenceIndex() {
  if (index) return index;
  index = new Map();
  for (const [sentence, glosses] of Object.entries(data.s)) {
    const key = Ph.wordKeys(sentence).join(' ');
    if (key.split(' ').length === glosses.length && !index.has(key)) index.set(key, glosses);
  }
  return index;
}

/** Glosse je Wort von ru (Betonungszeichen egal), null = unbekannt. */
export function glossesFor(ru) {
  const words = Ph.words(ru);
  const exact = data.s[Ph.plain(ru).trim()];
  if (exact && exact.length === words.length) return exact;
  const idx = sentenceIndex();
  const keys = words.map((w) => norm(w.plain));
  const out = words.map(() => null);
  let i = 0;
  while (i < words.length) {
    let matched = 0;
    for (let j = words.length; j >= i + 2; j--) {
      const g = idx.get(keys.slice(i, j).join(' '));
      if (!g) continue;
      for (let k = 0; k < g.length; k++) out[i + k] = k === 0 && g[k] === JOIN ? null : g[k];
      matched = j - i;
      break;
    }
    if (matched === 0) {
      out[i] = data.w[words[i].plain.toLowerCase()] ?? data.w[keys[i]] ?? null;
      matched = 1;
    }
    i += matched;
  }
  return out;
}

const WORD = /[А-Яа-яЁё+][А-Яа-яЁё+\-]*/g;

/** Setzt fehlende Betonungen aus dem Kurs ein (Привет → Прив+ет). */
export function withStress(text) {
  if (!Object.keys(data.st).length) return text;
  return text.replace(WORD, (w) => {
    const lower = w.toLowerCase();
    let vc = 0;
    for (const c of lower) if (VOWELS.includes(c)) vc++;
    if (w.includes('+') || lower.includes('ё') || vc < 2) return w;
    const form = data.st[lower.replaceAll('ё', 'е').replace(/-+$/, '')];
    if (!form) return w;
    let out = '';
    let i = 0;
    for (const c of form) {
      if (c === '+') { out += c; continue; }
      const orig = w[i];
      out += orig !== undefined && Ph.isUpper(orig) ? c.toUpperCase() : c;
      i++;
    }
    return Ph.plain(out).toLowerCase() === w.toLowerCase() ? out : w;
  });
}

/** Satz → Abschnitte für die Wort-für-Wort-Zeile: [{words:[WordReading], gloss}] */
export function chunks(ru) {
  const words = Ph.words(withStress(ru));
  const glosses = glossesFor(ru);
  const out = [];
  words.forEach((w, i) => {
    const g = glosses[i] ?? null;
    if (g === JOIN && out.length) out[out.length - 1].words.push(w);
    else out.push({ words: [w], gloss: g === JOIN ? null : g });
  });
  return out;
}
