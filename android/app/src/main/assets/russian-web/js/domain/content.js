// Satzbuch-Suche, Vertiefung (Clip-Namen, verwandte Sätze) und Satzbau-Muster.
// Portierung von RussianPhrasebook.kt, RussianDeepdive.kt, RussianPatterns.kt (domain).
import * as Ph from './phonetics.js';
import { makePhrase } from './course.js';

// ── Satzbuch ───────────────────────────────────────────────────────────────

/** Kleinschreibung, ё=е, ohne Betonungs- und Satzzeichen. */
export function searchNorm(s) {
  return [...s.toLowerCase().replaceAll('ё', 'е').replaceAll('ß', 'ss').replaceAll('ä', 'a').replaceAll('ö', 'o').replaceAll('ü', 'u')]
    .filter((c) => /[\p{L}\p{Nd}]/u.test(c) || c === ' ').join('').trim();
}

const TRANSLIT = {
  'а': 'a', 'б': 'b', 'в': 'v', 'г': 'g', 'д': 'd', 'е': 'e', 'ж': 'zh', 'з': 'z', 'и': 'i', 'й': 'j', 'к': 'k',
  'л': 'l', 'м': 'm', 'н': 'n', 'о': 'o', 'п': 'p', 'р': 'r', 'с': 's', 'т': 't', 'у': 'u', 'ф': 'f', 'х': 'h',
  'ц': 'ts', 'ч': 'ch', 'ш': 'sh', 'щ': 'sch', 'ъ': '', 'ы': 'y', 'ь': '', 'э': 'e', 'ю': 'yu', 'я': 'ya',
};
/** Heuristische Umschrift für Lateineingabe: »spasibo« findet »Спасибо«. */
export const searchTranslit = (ru) => [...searchNorm(Ph.plain(ru))].map((c) => TRANSLIT[c] ?? c).join('');

/** Alle Suchbegriffe müssen in Deutsch, Russisch, Umschrift, Notiz oder wörtlicher Übersetzung vorkommen. */
export function searchMatches(p, query) {
  const terms = searchNorm(query).split(' ').filter(Boolean);
  if (!terms.length) return true;
  const hay = p._hay ?? (p._hay = [searchNorm(p.de), searchNorm(p.plain), searchTranslit(p.ru), searchNorm(p.note ?? ''), searchNorm(p.lit ?? '')].join(' '));
  return terms.every((t) => hay.includes(t));
}

// ── SHA-1 (synchron; für Clip-Namen dd_<sha1(ru)[:10]>) ─────────────────────

export function sha1Hex(str) {
  const bytes = new TextEncoder().encode(str);
  const l = bytes.length;
  const words = new Uint32Array(((l + 8) >> 6) + 1 << 4);
  for (let i = 0; i < l; i++) words[i >> 2] |= bytes[i] << (24 - (i % 4) * 8);
  words[l >> 2] |= 0x80 << (24 - (l % 4) * 8);
  words[words.length - 1] = l * 8;
  let h0 = 0x67452301; let h1 = 0xefcdab89; let h2 = 0x98badcfe; let h3 = 0x10325476; let h4 = 0xc3d2e1f0;
  const w = new Uint32Array(80);
  const rotl = (x, n) => (x << n) | (x >>> (32 - n));
  for (let i = 0; i < words.length; i += 16) {
    for (let t = 0; t < 16; t++) w[t] = words[i + t];
    for (let t = 16; t < 80; t++) w[t] = rotl(w[t - 3] ^ w[t - 8] ^ w[t - 14] ^ w[t - 16], 1);
    let a = h0; let b = h1; let c = h2; let d = h3; let e = h4;
    for (let t = 0; t < 80; t++) {
      let f; let k;
      if (t < 20) { f = (b & c) | (~b & d); k = 0x5a827999; } else if (t < 40) { f = b ^ c ^ d; k = 0x6ed9eba1; } else if (t < 60) { f = (b & c) | (b & d) | (c & d); k = 0x8f1bbcdc; } else { f = b ^ c ^ d; k = 0xca62c1d6; }
      const tmp = (rotl(a, 5) + f + e + k + w[t]) | 0;
      e = d; d = c; c = rotl(b, 30); b = a; a = tmp;
    }
    h0 = (h0 + a) | 0; h1 = (h1 + b) | 0; h2 = (h2 + c) | 0; h3 = (h3 + d) | 0; h4 = (h4 + e) | 0;
  }
  return [h0, h1, h2, h3, h4].map((h) => (h >>> 0).toString(16).padStart(8, '0')).join('');
}

// ── Vertiefung ─────────────────────────────────────────────────────────────

const clipTexts = new Map();

/** Clip-Name wie in tools/russian_audio/generate_deepdive.py: dd_<sha1(ru)[:10]>. */
export function clipName(ru) {
  const clip = 'dd_' + sha1Hex(ru).slice(0, 10);
  const clean = Ph.plain(ru.startsWith('p|') ? ru.slice(2) : ru).trim();
  if (clean) clipTexts.set(clip, clean);
  return clip;
}
/** Russischer Klartext zu einem dynamischen Clip (für die Sprachausgabe als Rückfall). */
export const textForClip = (clip) => clipTexts.get(clip.replace(/_slow$/, '')) ?? null;

/** Beliebiger Text als Lernsatz, damit Anhören und Sprechprüfung dieselben Bausteine nutzen. */
export function asPhrase(ru, de, note = null, day = 0) {
  const clip = clipName(ru);
  return makePhrase({ id: `dd:${clip}`, ru, de, note, lit: null, day, audio: clip });
}

const STOP = new Set(['что', 'это', 'как', 'для', 'или', 'при', 'мне', 'тебе', 'тебя', 'меня', 'вас', 'вам', 'вы', 'ты',
  'его', 'её', 'мой', 'моя', 'они', 'она', 'оно', 'там', 'тут', 'нет', 'уже', 'еще', 'ещё', 'все', 'всё']);

export const keyWords = (ru) => searchNorm(Ph.plain(ru)).split(' ').filter((w) => w.length >= 3 && !STOP.has(w));

/** Andere Kurssätze, die ein Wort dieses Satzes enthalten. */
export function related(target, pool, limit = 5) {
  const keys = keyWords(target.ru);
  if (!keys.length) return [];
  const score = (p) => {
    const words = keyWords(p.ru);
    let s = 0;
    for (const k of keys) {
      if (words.includes(k)) s += 3;
      else if (k.length >= 5 && words.some((w) => w.length >= 5 && w.slice(0, 4) === k.slice(0, 4))) s += 1;
    }
    return s;
  };
  return pool.filter((p) => p.id !== target.id).map((p, i) => ({ p, s: score(p), i })).filter((x) => x.s > 0)
    .sort((a, b) => b.s - a.s || a.p.day - b.p.day || a.i - b.i).slice(0, limit).map((x) => x.p);
}

// ── Satzbau-Muster ─────────────────────────────────────────────────────────

/** Setzt die gewählten Bausteine in die Vorlage ein. → {ru, de, patternId, note} */
export function buildPattern(p, choices, gender) {
  let ru = p.template.ru;
  let de = p.template.de;
  const female = gender === 'FEMALE';
  p.slots.forEach((slot, i) => {
    const o = slot.options[choices[i] ?? 0] ?? slot.options[0];
    ru = ru.replaceAll(`{${i}}`, female && o.ruF != null ? o.ruF : o.ru);
    de = de.replaceAll(`{${i}}`, female && o.deF != null ? o.deF : o.de);
  });
  return { ru, de, patternId: p.id, note: '' };
}

export const sceneLine = (l, gender) => (gender === 'FEMALE' && l.ruF != null ? { ru: l.ruF, de: l.deF ?? l.de } : { ru: l.ru, de: l.de });

/** Clip einer Szenenzeile (Partnerin hat eine eigene Stimme: "p|"+Text). */
export function sceneLineClip(l, gender) {
  const { ru } = sceneLine(l, gender);
  return l.who === 'U' ? clipName(ru) : clipName(`p|${ru}`);
}
