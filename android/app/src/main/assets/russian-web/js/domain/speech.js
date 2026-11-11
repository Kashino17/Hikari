// Fairer, aber strenger Abgleich »Zielsatz ↔ Spracherkennung« samt Laut-Hinweisen.
// Portierung von RussianSpeechMatch.kt, RuAnswerCheck (RussianExercises.kt) und
// RussianSoundCoach.kt.
import * as Ph from './phonetics.js';
import { withStress } from './glossary.js';

const VOWELS = 'аеиоуыэюя';

// ── Zahlen ─────────────────────────────────────────────────────────────────
const UNITS = ['ноль', 'один', 'два', 'три', 'четыре', 'пять', 'шесть', 'семь', 'восемь', 'девять'];
const TEENS = ['десять', 'одиннадцать', 'двенадцать', 'тринадцать', 'четырнадцать', 'пятнадцать', 'шестнадцать', 'семнадцать', 'восемнадцать', 'девятнадцать'];
const TENS = ['', '', 'двадцать', 'тридцать', 'сорок', 'пятьдесят', 'шестьдесят', 'семьдесят', 'восемьдесят', 'девяносто'];
const HUNDREDS = ['', 'сто', 'двести', 'триста', 'четыреста', 'пятьсот', 'шестьсот', 'семьсот', 'восемьсот', 'девятьсот'];
const ORD_UNITS = ['нулевой', 'первый', 'второй', 'третий', 'четвертый', 'пятый', 'шестой', 'седьмой', 'восьмой', 'девятый'];
const ORD_TEENS = ['десятый', 'одиннадцатый', 'двенадцатый', 'тринадцатый', 'четырнадцатый', 'пятнадцатый', 'шестнадцатый', 'семнадцатый', 'восемнадцатый', 'девятнадцатый'];
const ORD_TENS = ['', '', 'двадцатый', 'тридцатый', 'сороковой', 'пятидесятый', 'шестидесятый', 'семидесятый', 'восьмидесятый', 'девяностый'];
const OBLIQUE = {
  'один': ['одна', 'одно', 'одного', 'одному', 'одной', 'одну', 'одним', 'одном', 'одни', 'одних'],
  'два': ['две', 'двух', 'двум', 'двумя'],
  'три': ['трех', 'трем', 'тремя'],
  'четыре': ['четырех', 'четырем', 'четырьмя'],
  'сто': ['ста'], 'сорок': ['сорока'], 'девяносто': ['девяноста'],
};

export function cardinal(n) {
  if (n < 0 || n > 9999) return [];
  if (n === 0) return [UNITS[0]];
  const out = [];
  const th = Math.floor(n / 1000);
  if (th > 0) {
    if (th === 1) out.push('тысяча');
    else if (th === 2) out.push('две', 'тысячи');
    else if (th === 3 || th === 4) out.push(UNITS[th], 'тысячи');
    else out.push(UNITS[th], 'тысяч');
  }
  const rest = n % 1000;
  if (Math.floor(rest / 100) > 0) out.push(HUNDREDS[Math.floor(rest / 100)]);
  const r = rest % 100;
  if (r >= 10 && r <= 19) out.push(TEENS[r - 10]);
  else {
    if (Math.floor(r / 10) > 0) out.push(TENS[Math.floor(r / 10)]);
    if (r % 10 > 0) out.push(UNITS[r % 10]);
  }
  return out;
}

export function ordinal(n) {
  const c = cardinal(n);
  if (!c.length) return c;
  const r = n % 100;
  let last;
  if (n % 1000 === 0 && n > 0) last = 'тысячный';
  else if (n % 100 === 0 && n % 1000 !== 0) {
    const h = HUNDREDS[Math.floor((n % 1000) / 100)];
    last = h === 'сто' ? 'сотый' : h.slice(0, -1) + 'ый';
  } else if (r >= 10 && r <= 19) last = ORD_TEENS[r - 10];
  else if (r % 10 === 0) last = ORD_TENS[Math.floor(r / 10)];
  else last = ORD_UNITS[r % 10];
  return [...c.slice(0, -1), last];
}

const NUMBER_TOKEN = /^(\d+)(?:[:.](\d+))?(?:-?([а-яё]+))?(%)?$/;
const toInt = (s) => { if (s === undefined || s === '') return null; const n = Number(s); return Number.isSafeInteger(n) && n <= 2147483647 ? n : null; };

function expandNumber(m) {
  const a = toInt(m[1]);
  if (a === null) return [];
  const b = toInt(m[2]);
  const suffix = m[3] ?? '';
  const pct = !!m[4];
  const first = suffix && b === null ? ordinal(a) : cardinal(a);
  const out = first.map((w) => ({ word: w, stress: null, number: a }));
  if (b !== null) out.push(...cardinal(b).map((w) => ({ word: w, stress: null, number: b })));
  if (pct) out.push({ word: 'процент', stress: null, number: null });
  return out;
}

const trimEndChars = (s, chars) => { let e = s.length; while (e > 0 && chars.includes(s[e - 1])) e--; return s.slice(0, e); };

export function sameNumeral(word, numberWord) {
  if (word === numberWord) return true;
  if (OBLIQUE[numberWord]?.includes(word)) return true;
  const stem = trimEndChars(numberWord, 'ьйыоаи');
  return stem.length >= 3 && word.startsWith(stem.slice(0, Math.max(3, stem.length - 1))) && word.length - stem.length <= 3;
}

// ── Zerlegen ───────────────────────────────────────────────────────────────
const TOKEN = /\d+(?:[:.]\d+)?(?:-?[а-яё]+)?%?|[a-zа-яё+]+/g;

/** Text → [{word, stress, number}] ; Ziffern werden Zahlwörter, ё zu е. */
export function tokens(text) {
  const src = Ph.fromDisplay(text).toLowerCase();
  const out = [];
  for (const m of src.matchAll(TOKEN)) {
    const v = m[0];
    if (v[0] >= '0' && v[0] <= '9') {
      const nm = v.match(NUMBER_TOKEN);
      if (nm) out.push(...expandNumber(nm));
      continue;
    }
    let stress = null;
    let w = '';
    for (const c of v) {
      if (c === '+') stress = w.length;
      else if (c === 'ё') { if (stress === null) stress = w.length; w += 'е'; } else w += c;
    }
    if (!w) continue;
    if (stress === null) {
      let cnt = 0; let first = -1;
      for (let i = 0; i < w.length; i++) if (VOWELS.includes(w[i])) { cnt++; if (first < 0) first = i; }
      if (cnt === 1) stress = first;
    }
    out.push({ word: w, stress, number: null });
  }
  return out;
}

export const tok = (word) => ({ word, stress: null, number: null });

// ── Lautbasierter Vergleich ────────────────────────────────────────────────
const VOICING = { 'б': 'п', 'в': 'ф', 'г': 'к', 'д': 'т', 'ж': 'ш', 'з': 'с' };
const VOICELESS = 'пфктшсхцчщ';
const VOICED_OBSTRUENT = 'бгджз';

function soundsSame(target, i, h) {
  const w = target.word;
  const t = w[i];
  if (t === h) return true;
  const tv = VOWELS.includes(t);
  const hv = VOWELS.includes(h);
  if (tv && hv) {
    if (target.stress === i) return false;
    const a = 'оа';
    const i2 = 'еия';
    return (a.includes(t) && a.includes(h)) || (i2.includes(t) && i2.includes(h));
  }
  if (!tv && !hv) {
    const next = w[i + 1];
    const final = i === w.length - 1 || (next === 'ь' && i + 1 === w.length - 1);
    const voiced = VOICING[t] === h || VOICING[h] === t;
    if (!voiced) return false;
    return final || (next !== undefined && (VOICELESS.includes(next) || VOICED_OBSTRUENT.includes(next)));
  }
  return false;
}

/** 1.0 = klingt gleich, 0.0 = nichts gemeinsam. */
export function soundSimilarity(target, heard) {
  const a = target.word.replaceAll('ться', 'тся');
  const t = a === target.word ? target : { ...target, word: a };
  const b = heard.replaceAll('ться', 'тся');
  const n = a.length;
  const m = b.length;
  if (n === 0 && m === 0) return 1;
  const dp = Array.from({ length: n + 1 }, () => new Float64Array(m + 1));
  for (let i = 0; i <= n; i++) dp[i][0] = i;
  for (let j = 0; j <= m; j++) dp[0][j] = j;
  for (let i = 1; i <= n; i++) for (let j = 1; j <= m; j++) {
    const sub = soundsSame(t, i - 1, b[j - 1]) ? 0 : 1;
    dp[i][j] = Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + sub);
  }
  return 1 - dp[n][m] / Math.max(n, m);
}

const LATIN = {
  'а': 'a', 'б': 'b', 'в': 'v', 'г': 'g', 'д': 'd', 'е': 'e', 'ж': 'zh', 'з': 'z', 'и': 'i', 'й': 'y', 'к': 'k',
  'л': 'l', 'м': 'm', 'н': 'n', 'о': 'o', 'п': 'p', 'р': 'r', 'с': 's', 'т': 't', 'у': 'u', 'ф': 'f', 'х': 'kh',
  'ц': 'ts', 'ч': 'ch', 'ш': 'sh', 'щ': 'shch', 'ъ': '', 'ы': 'y', 'ь': '', 'э': 'e', 'ю': 'yu', 'я': 'ya',
};
export const latinOf = (word) => [...word].map((c) => LATIN[c] ?? c).join('');

export const WORD_PASS = 0.75;

const NUMERAL_BASES = [...UNITS, ...TEENS, ...TENS, ...HUNDREDS, ...ORD_UNITS, ...ORD_TEENS, ...ORD_TENS, 'тысяча', 'сотый', 'тысячный']
  .filter(Boolean)
  .map((w, i) => [w, i])
  .sort((x, y) => y[0].length - x[0].length || x[1] - y[1]) // stabil wie Kotlin sortedByDescending
  .map(([w]) => w);

export const numeralBase = (w) => NUMERAL_BASES.find((b) => sameNumeral(w, b)) ?? null;

/** Trifft das gehörte Wort das Zielwort? */
export function matches(target, heard) {
  if (heard.number !== null && heard.number !== undefined) return sameNumeral(target.word, heard.word);
  if (/[a-z]/.test(heard.word)) return similarity(latinOf(target.word), heard.word) >= WORD_PASS;
  const tb = numeralBase(target.word);
  const hb = numeralBase(heard.word);
  if (tb !== null && hb !== null && tb !== hb) return false;
  if (soundSimilarity(target, heard.word) < WORD_PASS) return false;
  const st = target.stress;
  if (st !== null && st !== undefined && target.word.length === heard.word.length && target.word[st] !== heard.word[st]) return false;
  return true;
}

// ── RuAnswerCheck ──────────────────────────────────────────────────────────
export const PASS = 0.75;
const MUST_MATCH = new Set(['не', 'нет', 'да']);
const HYPOTHESES = 3;

function levenshtein(a, b) {
  const dp = Array.from({ length: b.length + 1 }, (_, i) => i);
  for (let i = 1; i <= a.length; i++) {
    let prev = dp[0];
    dp[0] = i;
    for (let j = 1; j <= b.length; j++) {
      const tmp = dp[j];
      dp[j] = Math.min(dp[j] + 1, dp[j - 1] + 1, prev + (a[i - 1] === b[j - 1] ? 0 : 1));
      prev = tmp;
    }
  }
  return dp[b.length];
}

export function similarity(a, b) {
  if (a === b) return 1;
  const max = Math.max(a.length, b.length);
  if (max === 0) return 1;
  return 1 - levenshtein(a, b) / max;
}

/**
 * Vergleicht die Erkennungs-Hypothesen mit dem Zielsatz.
 * → {words:[[wort, getroffen]], score, heard, passed}
 * Bestanden: alle Inhaltswörter (ab 4 Buchstaben) und jede Verneinung stimmen
 * und insgesamt ≥ 75 % erkannt.
 */
export function speech(target, hypotheses) {
  const targetToks = tokens(withStress(Ph.fromDisplay(target)));
  const hyps = hypotheses.filter((h) => h.trim() !== '').slice(0, HYPOTHESES);
  const perHyp = hyps.map((h) => {
    const heard = tokens(h);
    return targetToks.map((tw) => {
      const hit = heard.findIndex((x) => matches(tw, x));
      if (hit >= 0) heard.splice(hit, 1);
      return hit >= 0;
    });
  });
  const words = targetToks.map((tw, i) => [tw.word, perHyp.some((p) => p[i])]);
  const score = words.length ? words.filter((w) => w[1]).length / words.length : 0;
  let bestIdx = -1;
  let bestCount = -1;
  perHyp.forEach((p, k) => { const c = p.filter(Boolean).length; if (c > bestCount) { bestCount = c; bestIdx = k; } });
  const heard = hyps[bestIdx] ?? hypotheses[0] ?? '';
  const passed = words.length > 0 && score >= PASS && words.every(([w, ok]) => ok || (w.length < 4 && !MUST_MATCH.has(w)));
  return { words, score, heard, passed };
}

/**
 * Wie viele Wörter am Satzanfang gar nicht in der Aufnahme sind (0, 1 oder 2).
 *
 * Erkennbar daran, dass nur der Anfang fehlt, alles danach stimmt und im Gehörten auch kein
 * überzähliges Wort steht, das ein missglückter Versuch sein könnte. Dann wurde das Wort nicht
 * falsch gesprochen, sondern nicht aufgenommen — typisch, wenn man spricht, bevor das Mikrofon
 * offen ist. Ändert nichts an Punktzahl und Bestehen, nur daran, wie das Ergebnis erklärt wird.
 */
export function clippedStart(sc) {
  const words = sc?.words ?? [];
  let k = 0;
  while (k < words.length && !words[k][1]) k++;
  if (k === 0 || k > 2 || k >= words.length) return 0;
  if (!words.slice(k).every((w) => w[1])) return 0;
  return tokens(sc.heard ?? '').length <= words.length - k ? k : 0;
}

// ── RuSoundCoach ───────────────────────────────────────────────────────────
const CONFUSIONS = {
  'ы|и': '»ы« klang wie »и«. Zieh die Zunge nach hinten und lass die Lippen locker — kein breites Lächeln.',
  'и|ы': '»и« klang zu dunkel (wie »ы«). Zunge nach vorn, Lippen leicht breit — wie in »Biene«.',
  'ш|с': '»ш« klang wie »с«. Zunge zurück, Lippen leicht gerundet — ein tiefes »sch«.',
  'с|ш': '»с« klang wie »ш«. Zunge bleibt vorn hinter den Zähnen, Lippen flach — glattes »s«.',
  'ш|щ': '»ш« klang zu weich (wie »щ«). Bei »ш« bleibt die Zungenmitte unten, der Laut ist tief und hart.',
  'щ|ш': '»щ« klang zu hart (wie »ш«). »щ« ist ein langes, weiches »sch« mit angehobener Zungenmitte.',
  'ж|ш': '»ж« klang stimmlos (wie »ш«). Lass die Stimmbänder mitschwingen — wie französisch »j« in »Journal«.',
  'ш|ж': '»ш« klang stimmhaft (wie »ж«). Hier bleibt die Stimme aus, nur Luft.',
  'ж|з': '»ж« klang wie »з«. Zunge zurück, Lippen leicht gerundet, stimmhaft zischen.',
  'з|ж': '»з« klang wie »ж«. Zunge bleibt vorn — ein weiches Summen wie »s« in »Sonne«.',
  'ч|ш': '»ч« klang wie »ш«. »ч« beginnt mit einem kurzen »t« und endet weich — ein kompaktes »tsch«.',
  'ш|ч': '»ш« klang wie »ч«. Kein »t« davor — nur ein durchgehendes »sch«.',
  'ч|ц': '»ч« klang wie »ц«. Bei »ч« liegt die Zungenmitte oben, es klingt weicher als »ts«.',
  'ц|ч': '»ц« klang wie »ч«. »ц« ist ein schnelles »ts« wie in »Zahl« — hart und kurz.',
  'ц|с': '»ц« klang wie »с«. Vorn ein kurzes »t« anschlagen: »ts«.',
  'с|ц': '»с« klang wie »ц«. Hier kein »t« davor — nur ein glattes »s«.',
  'р|л': '»р« klang wie »л«. Die Zungenspitze muss am Gaumen schwingen, nicht an den Zähnen stehen.',
  'л|р': '»л« klang wie »р«. Zunge fest gegen die Zähne drücken, nicht rollen.',
  'х|к': '»х« klang wie »к«. Nur Luft durch die Enge am hinteren Gaumen, kein Verschluss — wie »ch« in »Bach«.',
  'г|к': '»г« klang stimmlos (wie »к«). Stimme an, weich ansetzen.',
  'д|т': '»д« klang stimmlos (wie »т«). Stimmbänder schwingen lassen.',
  'б|п': '»б« klang stimmlos (wie »п«). Stimmbänder schwingen lassen.',
  'в|б': '»в« klang wie »б«. Lippen nicht schließen — Oberzähne an die Unterlippe.',
  'я|а': '»я« klang wie »а«. »я« enthält ein kurzes »j« — wie in »ja«.',
  'ю|у': '»ю« klang wie »у«. »ю« enthält ein kurzes »j« — wie in »Jule«.',
  'е|э': '»е« klang wie »э«. »е« beginnt mit einem »j«: »je«.',
  'ё|о': '»ё« klang wie »о«. »ё« beginnt mit einem »j«: »jo«.',
};
const SOFT_TIP = 'Das weiche Zeichen »ь« fehlte. Hebe die Zungenmitte zum Gaumen — der Konsonant davor wird weich, wie ein kleines »j« danach.';

/** Edit-Skript [soll|null, gehört|null] der Unterschiede, in Wortreihenfolge. */
export function diff(a, b) {
  const n = a.length;
  const m = b.length;
  const dp = Array.from({ length: n + 1 }, () => new Int32Array(m + 1));
  for (let i = 0; i <= n; i++) dp[i][0] = i;
  for (let j = 0; j <= m; j++) dp[0][j] = j;
  for (let i = 1; i <= n; i++) for (let j = 1; j <= m; j++) {
    dp[i][j] = Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1));
  }
  const ops = [];
  let i = n;
  let j = m;
  while (i > 0 || j > 0) {
    if (i > 0 && j > 0 && a[i - 1] === b[j - 1] && dp[i][j] === dp[i - 1][j - 1]) { i--; j--; }
    else if (i > 0 && j > 0 && dp[i][j] === dp[i - 1][j - 1] + 1) { ops.unshift([a[i - 1], b[j - 1]]); i--; j--; }
    else if (i > 0 && dp[i][j] === dp[i - 1][j] + 1) { ops.unshift([a[i - 1], null]); i--; }
    else { ops.unshift([null, b[j - 1]]); j--; }
  }
  return ops;
}

export function tipFor(target, heard) {
  for (const [t, h] of diff(target, heard)) {
    if (t === 'ь' && h === null) return SOFT_TIP;
    if (t !== null && h !== null && CONFUSIONS[`${t}|${h}`]) return CONFUSIONS[`${t}|${h}`];
  }
  return null;
}

/** Umschrift mit großgeschriebener betonter Silbe, z. B. »dJEssit'«. */
export function reading(word) {
  return Ph.transliterate(withStress(word)).map((s) => (s.stressed ? s.text.toUpperCase() : s.text)).join('');
}

/** Ein Hinweis je verfehltem Wort (höchstens max): [{target, heard, tip}] */
export function hints(sc, max = 3) {
  if (!sc.heard.trim()) return [];
  const heardWords = tokens(sc.heard);
  for (const [w, ok] of sc.words) {
    if (!ok) continue;
    const t = tok(w);
    const hit = heardWords.findIndex((x) => matches(t, x));
    if (hit >= 0) heardWords.splice(hit, 1);
  }
  const out = [];
  const seen = new Set();
  for (const [word, ok] of sc.words) {
    if (ok || out.length >= max) continue;
    const t = tok(word);
    let cand = null;
    let best = -Infinity;
    for (const h of heardWords) {
      const s = soundSimilarity(t, h.word);
      if (s > best) { best = s; cand = h; }
    }
    const close = cand !== null && best >= 0.4;
    let hint;
    if (close) {
      heardWords.splice(heardWords.indexOf(cand), 1);
      const tip = tipFor(word, cand.word) ?? `Kam als anderes Wort an. Sprich es langsam und deutlich: ${reading(word)} — die GROSSE Silbe ist betont, alle anderen Vokale kurz und schwach.`;
      hint = { target: word, heard: cand.word, tip };
    } else {
      if (word.length < 3 && word !== 'не' && word !== 'да') continue;
      hint = { target: word, heard: null, tip: `Dieses Wort war nicht zu hören. Nicht verschlucken — sprich es mit: ${reading(word)}.` };
    }
    if (!seen.has(hint.tip)) { seen.add(hint.tip); out.push(hint); }
  }
  return out;
}
