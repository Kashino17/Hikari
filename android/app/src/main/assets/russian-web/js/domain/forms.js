// Wortformen offline: zu jedem russischen Wort alle Formen mit Betonung.
//
// Daten: assets/russian/forms/ (gebaut von tools/russian_forms/build.mjs aus OpenRussian,
// Lücken aus Wiktionary/kaikki.org). In der App und in der Vorschau unter /data/forms/… .
//
//   meta.json      Aufbau, Grenzen der Verzeichnis-Stücke
//   i/<n>.json     Verzeichnis: Wortform (Schlüssel wie Ph.wordKeys) → Einträge
//   e/<n>.json     Einträge (nach Häufigkeit nummeriert, je CHUNK Stück)
//
// Alles wird erst beim ersten Nachschlagen geladen, und nur die Stücke, die gebraucht werden.
//
// ── Form eines Eintrags (stabil) ────────────────────────────────────────────
/**
 * @typedef {string[]} Alts  Eine Form mit Varianten, jede im Betonungsformat der App
 *   ("+" vor dem betonten Vokal, ё ohne Zeichen, einsilbige Wörter ohne Zeichen).
 *   Fehlt die Betonung in der Quelle, steht die Form ohne Zeichen da (nie geraten).
 *
 * @typedef {{nom?:Alts, gen?:Alts, dat?:Alts, acc?:Alts, inst?:Alts, prep?:Alts,
 *            loc?:Alts, part?:Alts}} Cases
 *   loc = zweiter Präpositiv (в лес+у), part = Partitiv (ч+аю) — nur wo es sie gibt.
 *
 * @typedef {Object} Entry
 * @property {number}  id
 * @property {string}  lemma     Grundform, betont ("люб+ить")
 * @property {string}  key       Grundform als Schlüssel (klein, ohne Betonung, ё→е; Wendungen mit Leerzeichen)
 * @property {'verb'|'noun'|'adj'|'pron'|'num'|'adv'|'prep'|'conj'|'particle'|'intj'|'name'|'other'} pos
 * @property {'verb'|'noun'|'adj'|'pron'|'none'} table  welche Tabelle `forms` hat
 * @property {'m'|'f'|'n'|'pl'|'mf'} [gender]   Substantive
 * @property {boolean} [animate]                 Substantive
 * @property {boolean} [indeclinable]
 * @property {boolean} [sgOnly]  nur Einzahl
 * @property {boolean} [plOnly]  nur Mehrzahl
 * @property {'impf'|'pf'|'both'} [aspect]       Verben
 * @property {string[]} [partner]  Aspektpartner (Verben) bzw. Gegenstück m/w (Substantive), betont
 * @property {string[]} [variants] Nebenformen laut Wiktionary, die auf diesen Eintrag verweisen (со → с)
 * @property {string[]} de        deutsche Bedeutungen (kann leer sein)
 * @property {string[]} [en]      englische Bedeutungen — nur wenn es keine deutschen gibt
 * @property {number}  rank       Häufigkeitsrang (kleiner = häufiger; gleich geschriebene Wörter teilen
 *                               sich einen Rang, dann zählt die id; Wiktionary-Wörter ab 100000)
 * @property {'or'|'wikt'|'or+wikt'} src  Quelle (or = OpenRussian, wikt = Wiktionary)
 * @property {Object}  forms
 *   table 'verb': { inf, present?:{sg1,sg2,sg3,pl1,pl2,pl3}, future?:{…gleich}, past:{m,f,n,pl},
 *                   imperative:{sg,pl}, participles?:{presAct,pastAct,presPass,pastPass},
 *                   gerunds?:{pres,past} }  — present bei uv. Verben, future bei v. (beides bei zweiaspektigen)
 *   table 'noun': { sg?: Cases, pl?: Cases }
 *   table 'adj':  { m: Cases, f: Cases, n: Cases, pl: Cases, short?:{m,f,n,pl}, comp?: Alts, sup?: Alts }
 *                 (acc: erst unbelebt, dann belebt — wie im Wörterbuch)
 *   table 'pron': Cases (Personalpronomen, кто, что …)
 *   table 'none': {}
 *   Fehlende Formen fehlen als Schlüssel.
 */
import * as Ph from './phonetics.js';

const CASES = ['nom', 'gen', 'dat', 'acc', 'inst', 'prep'];
const PERSONS = ['sg1', 'sg2', 'sg3', 'pl1', 'pl2', 'pl3'];
const GENDERS = ['m', 'f', 'n', 'pl'];

/** Reihenfolge der gespeicherten Felder je Tabelle — Build und Modul teilen sie. */
export const LAYOUT = {
  verb: [
    'inf',
    ...PERSONS.map((p) => `present.${p}`), ...PERSONS.map((p) => `future.${p}`),
    ...GENDERS.map((g) => `past.${g}`), 'imperative.sg', 'imperative.pl',
    'participles.presAct', 'participles.pastAct', 'participles.presPass', 'participles.pastPass',
    'gerunds.pres', 'gerunds.past',
  ],
  noun: [...CASES.map((c) => `sg.${c}`), ...CASES.map((c) => `pl.${c}`), 'sg.loc', 'sg.part'],
  adj: [...GENDERS.flatMap((g) => CASES.map((c) => `${g}.${c}`)), ...GENDERS.map((g) => `short.${g}`), 'comp', 'sup'],
  pron: [...CASES],
  none: [],
};

const POS = ['verb', 'noun', 'adj', 'pron', 'num', 'adv', 'prep', 'conj', 'particle', 'intj', 'name', 'other'];
const TABLES = ['verb', 'noun', 'adj', 'pron', 'none'];
export const CODES = { POS, TABLES };

/**
 * Schlüssel einer Wortform — dieselbe Normalisierung wie Ph.wordKeys (klein, ohne Betonung, ё→е).
 * Akzente in Anzeige-Schreibweise (во́ду) werden vorher in "+" umgesetzt, sonst zerfiele das Wort.
 */
export function keyOf(word) {
  return Ph.wordKeys(Ph.fromDisplay(String(word ?? '')))[0] ?? '';
}

/** Schlüssel eines Ausdrucks aus mehreren Wörtern ("с тех пор"); bei einem Wort gleich keyOf. */
export function phraseKey(text) {
  return Ph.wordKeys(Ph.fromDisplay(String(text ?? ''))).join(' ');
}

/** Einfacher Vergleich für die binäre Suche (Code-Einheiten, wie Array.prototype.sort). */
const cmp = (a, b) => (a < b ? -1 : a > b ? 1 : 0);

// ── Kodierung ───────────────────────────────────────────────────────────────
// Eine Form wird relativ zur Grundform gespeichert: "8а" = erste 8 Zeichen der Grundform + "а".
// Varianten sind mit "|" getrennt, Felder (Reihenfolge wie LAYOUT) mit ";", leer = Form fehlt.

export function decodeValue(v, lemma) {
  if (!v) return [];
  return v.split('|').map((a) => {
    const m = /^(\d+)/.exec(a);
    return m ? lemma.slice(0, Number(m[1])) + a.slice(m[1].length) : a;
  });
}

export function encodeValue(alts, lemma) {
  return alts.map((a) => {
    let n = 0;
    while (n < a.length && n < lemma.length && a[n] === lemma[n]) n++;
    return n >= 2 && String(n).length < n ? `${n}${a.slice(n)}` : a;
  }).join('|');
}

/** Gespeicherter Eintrag [lemma, pos, table, meta, de, "wert;wert;…", en?] → Entry. */
export function expand(id, raw) {
  const [lemma, posCode, tableCode, meta, de, packed, en] = raw;
  const values = packed ? packed.split(';') : [];
  const table = TABLES[tableCode];
  const forms = {};
  (LAYOUT[table] ?? []).forEach((path, i) => {
    const alts = decodeValue(values[i], lemma);
    if (!alts.length) return;
    const dot = path.indexOf('.');
    if (dot < 0) forms[path] = alts;
    else (forms[path.slice(0, dot)] ??= {})[path.slice(dot + 1)] = alts;
  });
  const e = { id, lemma, key: phraseKey(lemma), pos: POS[posCode], table };
  if (meta.g) e.gender = meta.g;
  if (meta.a !== undefined) e.animate = !!meta.a;
  if (meta.i) e.indeclinable = true;
  if (meta.so) e.sgOnly = true;
  if (meta.po) e.plOnly = true;
  if (meta.asp) e.aspect = meta.asp;
  if (meta.p) e.partner = meta.p;
  if (meta.v) e.variants = meta.v;
  e.de = de;
  if (en?.length) e.en = en;
  e.rank = meta.r;
  e.src = meta.s === 1 ? 'wikt' : meta.s === 2 ? 'or+wikt' : 'or';
  // Zweiaspektige Verben: eine Formenreihe, gilt als Gegenwart und Zukunft.
  if (table === 'verb' && e.aspect === 'both') {
    if (forms.present && !forms.future) forms.future = forms.present;
    else if (forms.future && !forms.present) forms.present = forms.future;
  }
  e.forms = forms;
  return e;
}

/** Ein Stück des Verzeichnisses: "<gemeinsamer Anfang><Rest> <ids base36>" je Zeile, sortiert. */
export function decodeIndex(text) {
  const map = new Map();
  let prev = '';
  for (const line of text.split('\n')) {
    if (!line) continue;
    const sp = line.lastIndexOf(' ');
    const m = /^(\d+)/.exec(line);
    const n = m ? Number(m[1]) : 0;
    const key = prev.slice(0, n) + line.slice(m ? m[1].length : 0, sp);
    map.set(key, line.slice(sp + 1).split(',').map((x) => parseInt(x, 36)));
    prev = key;
  }
  return map;
}

// ── Laden ───────────────────────────────────────────────────────────────────

let loader = (path) => fetch(`/data/forms/${path}`).then((r) => {
  if (!r.ok) throw new Error(`forms/${path}: ${r.status}`);
  return r.json();
});

/** Für Tests und Werkzeuge: eigener Lader (path → Promise<JSON>). Leert den Speicher. */
export function setLoader(fn) {
  loader = fn;
  meta = null;
  idxChunks.clear();
  entChunks.clear();
  entries.clear();
}

let meta = null;
const idxChunks = new Map(); // n → Promise<Map<key, ids>>
const entChunks = new Map(); // n → Promise<Object>
const entries = new Map();   // id → Entry

function getMeta() {
  if (!meta) meta = loader('meta.json').catch((e) => { meta = null; throw e; });
  return meta;
}

function load(cache, n, path, decode) {
  let p = cache.get(n);
  if (!p) {
    p = loader(path).then(decode).catch((e) => { cache.delete(n); throw e; });
    cache.set(n, p);
  }
  return p;
}

async function idsFor(key) {
  if (!key) return [];
  const m = await getMeta();
  // Stück finden: letzter Anfang ≤ key
  let lo = 0;
  let hi = m.idx.length - 1;
  while (lo < hi) {
    const mid = (lo + hi + 1) >> 1;
    if (cmp(m.idx[mid], key) <= 0) lo = mid; else hi = mid - 1;
  }
  const chunk = await load(idxChunks, lo, `i/${lo}.json`, (j) => decodeIndex(j.v));
  return chunk.get(key) ?? [];
}

async function byId(id) {
  if (entries.has(id)) return entries.get(id);
  const m = await getMeta();
  const n = Math.floor(id / m.chunk);
  const chunk = await load(entChunks, n, `e/${n}.json`, (j) => j);
  const raw = chunk[id - n * m.chunk];
  if (!raw) return null;
  const e = expand(id, raw);
  entries.set(id, e);
  return e;
}

/** Bindestrich-Partikeln, die an ein Wort angehängt werden (скаж+и-ка, я-то). */
const GLUED = ['ка', 'таки', 'то'];

/**
 * Alle Einträge, zu denen `word` eine Form ist (Homographen → mehrere Einträge),
 * häufigste zuerst. Betonungszeichen (+ oder Akut), Groß-/Kleinschreibung und ё/е sind egal.
 * Mehrere Wörter ("с тех пор") suchen eine Wendung als Ganzes.
 * @param {string} word
 * @returns {Promise<Entry[]>}
 */
export async function lookup(word) {
  const key = phraseKey(word);
  let ids = await idsFor(key);
  if (!ids.length && key.includes('-')) {
    const cut = key.lastIndexOf('-');
    if (GLUED.includes(key.slice(cut + 1))) ids = await idsFor(key.slice(0, cut));
  }
  const out = (await Promise.all(ids.map(byId))).filter(Boolean);
  return out.sort((a, b) => a.rank - b.rank || a.id - b.id);
}

/**
 * Volle Formentabelle zu einer Grundform. Mit Betonung ("з+амок") nur der passende Eintrag,
 * ohne Betonung alle Einträge mit dieser Grundform.
 * @param {string} lemma
 * @returns {Promise<Entry[]>}
 */
export async function entry(lemma) {
  const key = phraseKey(lemma);
  const all = (await Promise.all((await idsFor(key)).map(byId))).filter((e) => e && e.key === key);
  const want = String(lemma).trim().toLowerCase();
  if (want.includes('+')) {
    const exact = all.filter((e) => e.lemma.toLowerCase() === want);
    if (exact.length) return exact.sort((a, b) => a.rank - b.rank || a.id - b.id);
  }
  return all.sort((a, b) => a.rank - b.rank || a.id - b.id);
}

/** Alle Formen eines Eintrags als flache Liste (z. B. zum Hervorheben im Satz). */
export function allForms(e) {
  const out = [];
  const walk = (v) => {
    if (Array.isArray(v)) out.push(...v);
    else if (v && typeof v === 'object') Object.values(v).forEach(walk);
  };
  walk(e.forms);
  return [...new Set(out)];
}
