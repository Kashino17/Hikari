// Betonung und Zellen der Quellen → Betonungsformat der App.
//
// OpenRussian: Apostroph NACH dem betonten Vokal (сказа'ть), Wiktionary: Akut (сказа́ть).
// App: "+" VOR dem betonten Vokal (сказ+ать), ё ohne Zeichen (von sich aus betont),
// einsilbige Wörter ohne Zeichen. Nebenbetonungen (Gravis, ѐ, ѝ, `) fallen weg.
// Mehrere Hauptbetonungen in einem Wort (beides ist richtig, z. B. гу'ме'н) bleiben erhalten.

const VOWELS = 'аеёиоуыэюяАЕЁИОУЫЭЮЯ';
// Lateinische Buchstaben, die in den Quellen versehentlich statt kyrillischer stehen
const HOMOGLYPH = { a: 'а', e: 'е', o: 'о', p: 'р', c: 'с', x: 'х', y: 'у', A: 'А', E: 'Е', O: 'О', P: 'Р', C: 'С', X: 'Х', K: 'К', M: 'М', H: 'Н', T: 'Т', B: 'В' };
const VALID = /^[а-яёА-ЯЁ+\- ]+$/;

/** Eine Form aus der Quelle → "+"-Format, oder null wenn unbrauchbar. */
export function normForm(raw) {
  let s = String(raw ?? '').trim().replace(/\s+/g, ' ');
  if (!s || s === '-' || s === '—') return null;
  s = s.normalize('NFD');
  if (/[а-яё]/i.test(s)) s = s.replace(/[aeopcxyAEOPCXKMHTB]/g, (c) => HOMOGLYPH[c]);
  s = s.replace(/([иИ])̆/g, (_, c) => (c === 'и' ? 'й' : 'Й')).replace(/([еЕ])̈/g, (_, c) => (c === 'е' ? 'ё' : 'Ё'));
  s = s.replace(/́/g, "'").replace(/’/g, "'").replace(/̇/g, '');
  if (s.includes("'")) s = s.replace(/[̀`]/g, '');
  else s = s.replace(/̀/g, "'").replace(/`/g, ''); // nur Gravis vorhanden → er ist die Hauptbetonung
  let out = '';
  for (const c of s) {
    if (c === "'") {
      const last = out[out.length - 1];
      if (last && VOWELS.includes(last)) out = `${out.slice(0, -1)}+${last}`;
      continue; // Apostroph nach Konsonant: kein Vokal zum Betonen → weglassen
    }
    out += c;
  }
  out = out.replace(/\+([ёЁ])/g, '$1').replace(/\+\+/g, '+');
  out = out.split(' ').map((w) => (vowelCount(w) <= 1 ? w.replaceAll('+', '') : w)).join(' ');
  out = out.normalize('NFC').trim();
  if (!out || !VALID.test(out) || !/[а-яё]/i.test(out)) return null;
  return out;
}

const vowelCount = (w) => [...w].filter((c) => VOWELS.includes(c)).length;

/**
 * Zelle mit Varianten → einzelne Rohformen (noch mit Quell-Betonung).
 * spaces: auch Leerzeichen trennen Varianten (му'дры мудры') — nicht bei Wendungen wie »с+амый …«.
 */
export function splitCell(v, spaces = false) {
  let s = String(v ?? '').replace(/\r/g, '').trim();
  if (!s || s === '-' || s === '—') return [];
  s = s.replace(/\$.*$/, '').replace(/=>/g, '').replace(/\[\[|\]\]/g, '');
  s = s.replace(/_[^_]*_/g, '//'); // "_устар._" (veraltet) trennt nur Varianten
  return s.split(spaces ? /\/\/|\/|;|,|\s+/ : /\/\/|\/|;|,/).map((x) => x.replace(/[()*]/g, '').trim()).filter((x) => x && x !== '-' && x !== '—');
}

/** Bedeutungen "a, b; c (d, e)" → ["a", "b", "c (d, e)"] — Kommas in Klammern bleiben. */
export function splitMeanings(v) {
  const s = String(v ?? '').replace(/\r/g, '').trim();
  if (!s) return [];
  const out = [];
  let depth = 0;
  let cur = '';
  for (const c of s) {
    if (c === '(') depth++;
    if (c === ')') depth = Math.max(0, depth - 1);
    if ((c === ',' || c === ';') && depth === 0) { out.push(cur); cur = ''; } else cur += c;
  }
  out.push(cur);
  return [...new Set(out.map((x) => x.trim()).filter(Boolean))];
}

/** Zählt Formen ohne Betonungszeichen (mehrsilbig, kein ё, kein "+"). */
export function stressStats(list) {
  const r = { forms: 0, unmarkedPolysyllabic: 0, bySrc: {} };
  for (const { src, forms } of list) {
    for (const f of forms) {
      r.forms++;
      const unmarked = f.split(/[ -]/).some((w) => vowelCount(w) >= 2 && !w.includes('+') && !/[ёЁ]/.test(w));
      if (unmarked) {
        r.unmarkedPolysyllabic++;
        r.bySrc[src] = (r.bySrc[src] ?? 0) + 1;
      }
    }
  }
  return r;
}
