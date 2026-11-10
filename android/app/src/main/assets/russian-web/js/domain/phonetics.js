// Betonte Kurs-Texte ("Прив+ет") → Anzeige-Kyrillisch und Aussprache-Umschrift
// für deutsche Muttersprachler. 1:1-Portierung von RussianPhonetics.kt — jede
// Regel hier muss dieselben Ergebnisse liefern wie die Android-App
// (test/parity.test.mjs prüft das gegen alle Kurstexte).

const ACUTE = '́';
const VOWELS = 'аеёиоуыэюя';

const CLITICS = new Set(['в', 'во', 'на', 'по', 'за', 'из', 'с', 'со', 'к', 'ко', 'у', 'о', 'об', 'от',
  'до', 'без', 'при', 'и', 'а', 'но', 'не', 'ни', 'же', 'ли', 'бы']);
const PROCLITICS = new Set(['в', 'во', 'на', 'по', 'за', 'из', 'с', 'со', 'к', 'ко', 'у', 'о', 'об', 'от',
  'до', 'без', 'при', 'не', 'ни']);
const SPOKEN = {
  'что': 'што', 'чтобы': 'шт+обы', 'конечно': 'кон+ешно', 'скучно': 'ск+ушно', 'сегодня': 'сев+одня',
  'сейчас': 'сич+ас', 'легко': 'лехк+о', 'здравствуйте': 'здр+аствуйте', 'здравствуй': 'здр+аствуй',
  'счастливо': 'щасл+иво', 'пожалуйста': 'пож+алуйста', 'кафе': 'каф+э', 'аниме': 'аним+э',
};
const OGO_EXCEPTIONS = new Set(['много', 'немного', 'дорого', 'недорого', 'строго', 'убого']);

const isUpper = (c) => c !== c.toLowerCase() && c === c.toUpperCase();
const upFirst = (s) => (s ? s[0].toUpperCase() + s.slice(1) : s);
const lowFirst = (s) => (s ? s[0].toLowerCase() + s.slice(1) : s);

/** Anzeige: "+" vor dem Vokal → Akut nach dem Vokal, wie in Lehrbüchern. */
export function display(src) {
  let sb = '';
  let i = 0;
  while (i < src.length) {
    const c = src[i];
    if (c === '+' && i + 1 < src.length) {
      const v = src[i + 1];
      sb += v;
      if (v.toLowerCase() !== 'ё') sb += ACUTE;
      i += 2;
    } else {
      sb += c;
      i++;
    }
  }
  return sb;
}

/** Umkehrung von display: Akut nach dem Vokal → "+" davor. */
export function fromDisplay(text) {
  if (!text.includes(ACUTE)) return text;
  let sb = '';
  for (const c of text) {
    if (c === ACUTE && sb.length > 0) sb = sb.slice(0, -1) + '+' + sb.slice(-1);
    else sb += c;
  }
  return sb;
}

/** Ohne Betonungszeichen — für Spracherkennung und Vergleiche. */
export function plain(src) {
  return src.replaceAll('+', '').replaceAll(ACUTE, '');
}

const ACCENTED = { a: 'á', e: 'é', i: 'í', o: 'ó', u: 'ú', y: 'ý', A: 'Á', E: 'É', I: 'Í', O: 'Ó', U: 'Ú', Y: 'Ý' };
function accentFirstVowel(s) {
  for (let i = 0; i < s.length; i++) {
    if ('aeiouy'.includes(s[i].toLowerCase())) return s.slice(0, i) + (ACCENTED[s[i]] ?? s[i]) + s.slice(i + 1);
  }
  return s;
}

/** Umschrift als einfacher String, betonter Vokal mit Akut. */
export function transliterateFlat(src) {
  return transliterate(src).map((s) => (s.stressed ? accentFirstVowel(s.text) : s.text)).join('');
}

// ── Tokenisierung ──────────────────────────────────────────────────────────

function isCyr(c) {
  const l = c.toLowerCase();
  return (l >= 'а' && l <= 'я') || l === 'ё';
}
const isWordChar = (c) => c === '+' || c === '-' || isCyr(c);
const startsWord = (c) => isWordChar(c) && c !== '-';

/** → [{word}|{sep}] */
function tokenize(src) {
  const out = [];
  let i = 0;
  while (i < src.length) {
    const start = i;
    if (startsWord(src[i])) {
      while (i < src.length && isWordChar(src[i])) i++;
      let end = i;
      while (end > start && src[end - 1] === '-') end--;
      out.push({ word: src.substring(start, end) });
      if (end < i) out.push({ sep: src.substring(end, i) });
    } else {
      while (i < src.length && !startsWord(src[i])) i++;
      out.push({ sep: src.substring(start, i) });
    }
  }
  return out;
}
const isWordTok = (t) => t.word !== undefined;

// ── Laute ──────────────────────────────────────────────────────────────────

const cons = (latin, voiced = false, obstruent = true) => ({ latin, voiced, obstruent });
const Cons = {
  P: cons('p'), F: cons('f'), K: cons('k'), T: cons('t'), SH: cons('sch'), S: cons('s'),
  B: cons('b', true), V: cons('w', true), G: cons('g', true), D: cons('d', true), ZH: cons('zh', true), Z: cons('s', true),
  H: cons('ch'), TS: cons('z'), CH: cons('tsch'), SHCH: cons('sch'),
  L: cons('l', false, false), M: cons('m', false, false), N: cons('n', false, false),
  R: cons('r', false, false), J: cons('j', false, false),
};
const DEVOICE = new Map([[Cons.B, Cons.P], [Cons.V, Cons.F], [Cons.G, Cons.K], [Cons.D, Cons.T], [Cons.ZH, Cons.SH], [Cons.Z, Cons.S]]);
const VOICE = new Map([[Cons.P, Cons.B], [Cons.F, Cons.V], [Cons.K, Cons.G], [Cons.T, Cons.D], [Cons.SH, Cons.ZH], [Cons.S, Cons.Z]]);
const CONS_OF = {
  'б': Cons.B, 'в': Cons.V, 'г': Cons.G, 'д': Cons.D, 'ж': Cons.ZH, 'з': Cons.Z, 'к': Cons.K, 'л': Cons.L,
  'м': Cons.M, 'н': Cons.N, 'п': Cons.P, 'р': Cons.R, 'с': Cons.S, 'т': Cons.T, 'ф': Cons.F, 'х': Cons.H,
  'ц': Cons.TS, 'ч': Cons.CH, 'ш': Cons.SH, 'щ': Cons.SHCH, 'й': Cons.J,
};
const HARD_ONLY = new Set([Cons.ZH, Cons.SH, Cons.TS]);
const HISSING = new Set([Cons.ZH, Cons.SH, Cons.CH, Cons.SHCH]);
const SOFT_ONLY = new Set([Cons.CH, Cons.SHCH]);

/** Wort mit Betonungsinfo: Kleinbuchstaben ohne "+", Index des betonten Vokals. */
function parseWord(raw) {
  let letters = '';
  const upperMask = [];
  let stress = null;
  for (const c of raw) {
    if (c === '+') { stress = letters.length; continue; }
    letters += c.toLowerCase();
    upperMask.push(isUpper(c));
  }
  const explicit = stress !== null;
  if (stress === null) {
    const yo = letters.indexOf('ё');
    if (yo >= 0) stress = yo;
  }
  return { letters, upperMask, stressAt: stress, explicit };
}

const vowelCount = (s) => { let n = 0; for (const c of s) if (VOWELS.includes(c)) n++; return n; };
const firstVowelIndex = (s) => { for (let i = 0; i < s.length; i++) if (VOWELS.includes(s[i])) return i; return -1; };

function vowel(ch, stressed, prev, iotated, final) {
  const hard = HARD_ONLY.has(prev);
  const soft = SOFT_ONLY.has(prev);
  const afterJ = prev === Cons.J;
  switch (ch) {
    case 'а': return !stressed && soft ? 'i' : 'a';
    case 'о': return stressed ? 'o' : 'a';
    case 'у': return 'u';
    case 'ы': return 'y';
    case 'э': return 'e';
    case 'и': return hard ? 'y' : 'i';
    case 'е':
      if (stressed) return hard || soft || afterJ ? 'e' : 'je';
      if (final) return iotated && !afterJ ? 'je' : 'e';
      if (hard) return 'y';
      if (iotated && !afterJ) return 'ji';
      return 'i';
    case 'ё': return hard || soft || afterJ ? 'o' : 'jo';
    case 'ю': return hard || soft || afterJ ? 'u' : 'ju';
    case 'я':
      if (stressed || final) return soft || hard || afterJ ? 'a' : 'ja';
      if (iotated && !afterJ) return 'ji';
      return 'i';
    default: return ch;
  }
}

/** Einheiten: {k:'c',cons,upper} | {k:'v',text,stressed,upper} | {k:'m',text} */
function buildUnits(word, stressAt, upperFirst, highlight) {
  const units = [];
  let prevCons = null;
  let prevWasVowelOrStart = true;
  let i = 0;
  const n = word.length;
  const multi = vowelCount(word) > 1;
  const isFinal = (pos) => multi && pos === n - 1;
  while (i < n) {
    const ch = word[i];
    const upper = upperFirst && units.length === 0;
    // -тся / -ться am Wortende → ц + а
    if (ch === 'т' && ((word.endsWith('тся') && i === n - 3) || (word.endsWith('ться') && i === n - 4))) {
      units.push({ k: 'c', cons: Cons.TS, upper });
      prevCons = Cons.TS;
      prevWasVowelOrStart = false;
      i = n - 1;
      continue;
    }
    // сч / зч → щ
    if ((ch === 'с' || ch === 'з') && i + 1 < n && word[i + 1] === 'ч') {
      units.push({ k: 'c', cons: Cons.SHCH, upper });
      prevCons = Cons.SHCH;
      prevWasVowelOrStart = false;
      i += 2;
      continue;
    }
    const cs = CONS_OF[ch];
    if (cs) {
      const next = word[i + 1];
      let c = cs;
      if ((cs === Cons.Z || cs === Cons.S) && next === 'ж') c = Cons.ZH;
      else if ((cs === Cons.Z || cs === Cons.S) && next === 'ш') c = Cons.SH;
      units.push({ k: 'c', cons: c, upper });
      prevCons = cs;
      prevWasVowelOrStart = cs === Cons.J;
      i++;
      continue;
    }
    if (ch === 'ь') {
      if (!HISSING.has(prevCons)) units.push({ k: 'm', text: "'" });
      prevWasVowelOrStart = true;
      prevCons = null;
    } else if (ch === 'ъ') {
      prevWasVowelOrStart = true;
      prevCons = null;
    } else if (ch === '-') {
      units.push({ k: 'm', text: '-' });
    } else if (VOWELS.includes(ch)) {
      const stressed = stressAt === i;
      units.push({ k: 'v', text: vowel(ch, stressed, prevCons, prevWasVowelOrStart, isFinal(i)), stressed: stressed && highlight, upper });
      prevCons = null;
      prevWasVowelOrStart = true;
    } else {
      units.push({ k: 'm', text: ch });
    }
    i++;
  }
  return units;
}

/** Stimmangleichung von rechts nach links + Auslautverhärtung. */
function assimilate(units) {
  const seq = units.filter((u) => u.k === 'c' || u.k === 'v');
  for (let i = seq.length - 1; i >= 0; i--) {
    const u = seq[i];
    if (u.k !== 'c' || !u.cons.obstruent) continue;
    const next = seq[i + 1];
    if (!next) u.cons = DEVOICE.get(u.cons) ?? u.cons;
    else if (next.k === 'c' && next.cons.obstruent && !next.cons.voiced) u.cons = DEVOICE.get(u.cons) ?? u.cons;
    else if (next.k === 'c' && next.cons.obstruent && next.cons.voiced && next.cons !== Cons.V) u.cons = VOICE.get(u.cons) ?? u.cons;
  }
}

function render(units, out) {
  let prev = null;
  units.forEach((u, idx) => {
    if (u.k === 'c') {
      const next = units[idx + 1];
      let t = u.cons.latin;
      if (u.cons === Cons.S && next && next.k === 'v' && (prev === null || prev.k === 'v')) t = 'ss';
      if (u.upper) t = upFirst(t);
      out.push({ text: t, stressed: false });
    } else if (u.k === 'v') {
      out.push({ text: u.upper ? upFirst(u.text) : u.text, stressed: u.stressed });
    } else {
      out.push({ text: u.text, stressed: false });
    }
    prev = u;
  });
}

function merge(spans) {
  const out = [];
  for (const s of spans) {
    if (!s.text) continue;
    const last = out[out.length - 1];
    if (last && !last.stressed && !s.stressed) out[out.length - 1] = { text: last.text + s.text, stressed: false };
    else out.push(s);
  }
  return out;
}

function analyze(src) {
  const tokens = tokenize(src);
  // 1) Betonung je Wort bestimmen.
  const words = []; // {idx, parsed, stressAt, lower, highlight}
  let afterStressedNe = false;
  const single = tokens.filter(isWordTok).length === 1;
  tokens.forEach((t, idx) => {
    if (!isWordTok(t)) {
      if (/[.,!?;:…]/.test(t.sep)) afterStressedNe = false;
      return;
    }
    const p = parseWord(t.word);
    const lower = p.letters;
    const vc = vowelCount(lower.replaceAll('-', ''));
    let stress = p.stressAt;
    const prevWord = words.length ? words[words.length - 1].lower : null;
    const prevTok = tokens[idx - 1];
    const prevSep = prevTok && !isWordTok(prevTok) ? prevTok.sep : '';
    const conjunction = lower === 'что' && (prevWord === 'потому' || prevSep.includes(','));
    if (stress === null && vc === 1 && (single || !CLITICS.has(lower)) && !afterStressedNe && !conjunction) {
      stress = firstVowelIndex(lower);
    }
    if (p.explicit) afterStressedNe = lower === 'не';
    words.push({ idx, parsed: p, stressAt: stress, lower, highlight: p.explicit || vowelCount(lower) >= 2 });
  });

  // 2) Gesprochene Schreibung einsetzen (Ausnahmen + -ого/-его).
  const spoken = words.map((w) => {
    const key = w.lower;
    const special = SPOKEN[key];
    const firstUpper = w.parsed.upperMask[0] === true;
    if (special !== undefined) {
      const p = parseWord(special);
      const st = p.stressAt !== null ? (w.stressAt === null ? null : p.stressAt) : w.stressAt;
      return { letters: p.letters, stressAt: st, upper: firstUpper, highlight: w.highlight };
    }
    if ((key.endsWith('ого') || key.endsWith('его')) && key.length >= 3 && !OGO_EXCEPTIONS.has(key)) {
      const chars = [...key];
      chars[chars.length - 2] = 'в';
      return { letters: chars.join(''), stressAt: w.stressAt, upper: firstUpper, highlight: w.highlight };
    }
    return { letters: key, stressAt: w.stressAt, upper: firstUpper, highlight: w.highlight };
  });

  // 3) Laute erzeugen; Proklitika verschmelzen mit dem nächsten Wort.
  const unitsPerWord = spoken.map((s) => buildUnits(s.letters, s.stressAt, s.upper, s.highlight));
  const groups = [];
  let current = [];
  for (let i = 0; i < spoken.length; i++) {
    current.push(i);
    const next = tokens[words[i].idx + 1];
    const nextIsSpace = !!next && !isWordTok(next) && next.sep.length > 0 && /^ +$/.test(next.sep);
    const attach = PROCLITICS.has(words[i].lower) && i + 1 < spoken.length && nextIsSpace;
    if (!attach) { groups.push(current); current = []; }
  }
  if (current.length) groups.push(current);
  for (const g of groups) assimilate(g.flatMap((i) => unitsPerWord[i]));

  const wordAt = new Map(words.map((w, i) => [w.idx, i]));
  return { tokens, unitsPerWord, wordAt };
}

/** → [{text, stressed}] — die betonte Silbe als eigener Abschnitt. */
export function transliterate(src) {
  const a = analyze(src);
  const out = [];
  a.tokens.forEach((t, idx) => {
    if (isWordTok(t)) render(a.unitsPerWord[a.wordAt.get(idx)], out);
    else out.push({ text: t.sep, stressed: false });
  });
  return merge(out);
}

const OPENERS = '«„“"\'([';
const isSpace = (c) => /\s/.test(c);

/** Satz in Wörter zerlegt: [{cyrillic, plain, latin:[span], before, after}] */
export function words(src) {
  const a = analyze(src);
  const wordTokens = [];
  a.tokens.forEach((t, index) => { if (isWordTok(t)) wordTokens.push({ index, t }); });
  if (!wordTokens.length) return [];
  const before = wordTokens.map(() => '');
  const after = wordTokens.map(() => '');
  a.tokens.forEach((t, idx) => {
    if (isWordTok(t)) return;
    let prev = -1;
    for (let k = wordTokens.length - 1; k >= 0; k--) if (wordTokens[k].index < idx) { prev = k; break; }
    const next = wordTokens.findIndex((w) => w.index > idx);
    const text = t.sep;
    if (prev < 0) {
      const lead = text.trim();
      before[next] += lead === '' || OPENERS.includes(lead[lead.length - 1]) ? lead : lead + ' ';
    } else if (next < 0) {
      after[prev] += text.trimEnd();
    } else {
      let g = 0;
      while (g < text.length && !isSpace(text[g])) g++;
      const glued = text.slice(0, g);
      const rest = text.slice(g).trim();
      after[prev] += glued;
      if (rest) before[next] += OPENERS.includes(rest[rest.length - 1]) ? rest : rest + ' ';
    }
  });
  return wordTokens.map(({ index, t }, i) => {
    const spans = [];
    render(a.unitsPerWord[a.wordAt.get(index)], spans);
    return { cyrillic: display(t.word), plain: plain(t.word), latin: merge(spans), before: before[i], after: after[i] };
  });
}

/** Nur die Wörter (ohne Betonung, klein, ё→е). */
export function wordKeys(src) {
  return tokenize(src).filter(isWordTok).map((t) => plain(t.word).toLowerCase().replaceAll('ё', 'е'));
}

export const LETTER_SOUNDS = {
  'а': 'a', 'б': 'b', 'в': 'w', 'г': 'g', 'д': 'd', 'е': 'je', 'ё': 'jo', 'ж': 'zh', 'з': 's', 'и': 'i', 'й': 'j',
  'к': 'k', 'л': 'l', 'м': 'm', 'н': 'n', 'о': 'o', 'п': 'p', 'р': 'r', 'с': 'ss', 'т': 't', 'у': 'u', 'ф': 'f',
  'х': 'ch', 'ц': 'z', 'ч': 'tsch', 'ш': 'sch', 'щ': "sch'", 'ъ': '–', 'ы': 'y', 'ь': "'", 'э': 'e', 'ю': 'ju', 'я': 'ja',
};

/** Buchstabenpaare eines Textes, Wortgrenzen als null. */
export function letterPairs(src) {
  const out = [];
  for (const c of plain(src)) {
    const sound = LETTER_SOUNDS[c.toLowerCase()];
    if (sound !== undefined) out.push([c, sound]);
    else if (c === ' ' || c === '-') { if (out.length && out[out.length - 1] !== null) out.push(null); }
  }
  while (out.length && out[out.length - 1] === null) out.pop();
  return out;
}

export { VOWELS, upFirst, lowFirst, isUpper };
