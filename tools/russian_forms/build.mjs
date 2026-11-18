// Baut die Wortformen-Daten für den Russisch-Bereich (assets/russian/forms).
//
// Quellen:
//   ~/.hikari/erleben/dict/{verbs,nouns,adjectives,others}.csv   OpenRussian (Tab-getrennt)
//   ~/.hikari/forms-src/kaikki-ru.jsonl.gz                        Wiktionary-Auszug von kaikki.org
//     (nur für Lücken: Wörter, die OpenRussian nicht hat, und fehlende Formen wie
//      Pronomen-Deklination, Partizipien, Lokativ, Zukunft von быть). Fehlt die Datei,
//      wird ohne Wiktionary gebaut.
//
// Start: node tools/russian_forms/build.mjs   (System-Node; schreibt nach assets/russian/forms)
// Erneut ausführbar — der Ausgabeordner wird jedes Mal neu geschrieben.
//
// Regeln: keine Form wird erfunden. Jede Form stammt aus den Quellen; fehlt dort die Betonung,
// bleibt die Form ohne Zeichen (gezählt in der Statistik).
import { createReadStream, existsSync, mkdirSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';
import readline from 'node:readline';
import { createGunzip } from 'node:zlib';
import * as Ph from '../../android/app/src/main/assets/russian-web/js/domain/phonetics.js';
import { CODES, LAYOUT, encodeValue, keyOf, phraseKey } from '../../android/app/src/main/assets/russian-web/js/domain/forms.js';
import { normForm, splitCell, splitMeanings, stressStats } from './stress.mjs';

const DICT = process.env.HIKARI_DICT ?? join(homedir(), '.hikari', 'erleben', 'dict');
const SRC = process.env.HIKARI_FORMS_SRC ?? join(homedir(), '.hikari', 'forms-src');
const KAIKKI = join(SRC, 'kaikki-ru.jsonl.gz');
const KAIKKI_CACHE = join(SRC, 'kaikki-ru.slim.json');
const KAIKKI_VERSION = 8;
const OUT = new URL('../../android/app/src/main/assets/russian/forms/', import.meta.url).pathname;

const ENTRY_CHUNK = 400;   // Einträge je e/<n>.json
const INDEX_CHUNK = 2500;  // Schlüssel je i/<n>.json

const stats = { wiktStressMismatchMatched: 0, wiktExtraVariants: 0, wiktAliases: 0, droppedForms: 0, wiktFilledSlots: {}, wiktOnlyEntries: 0, wiktOnlyByPos: {}, wiktUpgradedOthers: 0, wiktRetagged: 0 };
const bump = (o, k, n = 1) => { o[k] = (o[k] ?? 0) + n; };

// ── OpenRussian ─────────────────────────────────────────────────────────────

function readCsv(name) {
  const lines = readFileSync(join(DICT, `${name}.csv`), 'utf8').split('\n').map((l) => l.replace(/\r$/, '')).filter(Boolean);
  const head = lines[0].split('\t');
  return lines.slice(1).map((l, i) => {
    const c = l.split('\t');
    const row = { _rank: i + 1 };
    head.forEach((h, j) => { row[h] = c[j] ?? ''; });
    return row;
  });
}

/** Zelle → betonte Formen (+-Format). Unbrauchbares wird gezählt und verworfen. */
function cell(v, phrases = false) {
  const out = [];
  for (const raw of splitCell(v, !phrases)) {
    const f = normForm(raw);
    if (f) out.push(f); else stats.droppedForms++;
  }
  return [...new Set(out)];
}

/** Grundform: accented, sonst die erste Tabellenform mit Betonung, sonst bare. */
function lemmaOf(row, fallbackCol) {
  const a = cell(row.accented, true)[0];
  if (a && (a.includes('+') || vowels(a) < 2 || a.includes('ё'))) return a;
  const f = fallbackCol ? cell(row[fallbackCol]).find((x) => keyOf(x) === keyOf(row.bare)) : null;
  return f ?? a ?? normForm(row.bare);
}
const vowels = (s) => [...s.toLowerCase()].filter((c) => 'аеёиоуыэюя'.includes(c)).length;

const ASPECT = { imperfective: 'impf', perfective: 'pf', both: 'both' };

function orEntries() {
  const out = [];
  const accentedBy = new Map(); // "verb:сказать" → "сказ+ать"
  const verbs = readCsv('verbs');
  const nouns = readCsv('nouns');
  const adjs = readCsv('adjectives');
  const others = readCsv('others');
  for (const r of verbs) accentedBy.set(`verb:${keyOf(r.bare)}`, lemmaOf(r));
  for (const r of nouns) accentedBy.set(`noun:${keyOf(r.bare)}`, lemmaOf(r, 'sg_nom'));

  const partners = (v, kind) => [...new Set(String(v ?? '').split(/[;,/]/).map((p) => p.trim())
    .filter((p) => p && /^[а-яё́̀' -]+$/i.test(p))
    .map((p) => accentedBy.get(`${kind}:${keyOf(p)}`) ?? normForm(p)).filter(Boolean))];

  for (const r of verbs) {
    const aspect = ASPECT[r.aspect];
    const slots = { inf: [lemmaOf(r)] };
    const tense = aspect === 'pf' ? 'future' : 'present';
    for (const p of ['sg1', 'sg2', 'sg3', 'pl1', 'pl2', 'pl3']) slots[`${tense}.${p}`] = cell(r[`presfut_${p}`]);
    for (const g of ['m', 'f', 'n', 'pl']) slots[`past.${g}`] = cell(r[`past_${g}`]);
    slots['imperative.sg'] = cell(r.imperative_sg);
    slots['imperative.pl'] = cell(r.imperative_pl);
    out.push({ src: 'or', file: 'verbs', bare: r.bare, lemma: slots.inf[0], pos: 'verb', table: 'verb', rank: r._rank,
      aspect, partner: partners(r.partner, 'verb'), de: splitMeanings(r.translations_de), en: splitMeanings(r.translations_en), slots });
  }
  for (const r of nouns) {
    const slots = {};
    for (const n of ['sg', 'pl']) for (const c of ['nom', 'gen', 'dat', 'acc', 'inst', 'prep']) slots[`${n}.${c}`] = cell(r[`${n}_${c}`]);
    const gender = { m: 'm', f: 'f', n: 'n', pl: 'pl', both: 'mf' }[r.gender];
    out.push({ src: 'or', file: 'nouns', bare: r.bare, lemma: lemmaOf(r, 'sg_nom'), pos: 'noun', table: 'noun', rank: r._rank,
      gender, animate: r.animate === '1', indeclinable: r.indeclinable === '1', sgOnly: r.sg_only === '1', plOnly: r.pl_only === '1',
      partner: partners(r.partner, 'noun'), de: splitMeanings(r.translations_de), en: splitMeanings(r.translations_en), slots });
  }
  for (const r of adjs) {
    const slots = {};
    for (const g of ['m', 'f', 'n', 'pl']) {
      for (const c of ['nom', 'gen', 'dat', 'acc', 'inst', 'prep']) slots[`${g}.${c}`] = cell(r[`decl_${g}_${c}`]);
      slots[`short.${g}`] = cell(r[`short_${g}`]);
    }
    slots.comp = cell(r.comparative);
    slots.sup = cell(r.superlative, true); // »с+амый ленивый« ist eine Form aus zwei Wörtern
    out.push({ src: 'or', file: 'adjectives', bare: r.bare, lemma: lemmaOf(r, 'decl_m_nom'), pos: 'adj', table: 'adj', rank: r._rank,
      de: splitMeanings(r.translations_de), en: splitMeanings(r.translations_en), slots });
  }
  for (const r of others) {
    out.push({ src: 'or', file: 'others', bare: r.bare, lemma: lemmaOf(r), pos: 'other', table: 'none', rank: r._rank,
      de: splitMeanings(r.translations_de), en: splitMeanings(r.translations_en), slots: {} });
  }
  return out.filter((e) => e.lemma);
}

// ── Wiktionary (kaikki.org) ─────────────────────────────────────────────────

const SKIP_TAGS = new Set(['dated', 'archaic', 'obsolete', 'romanization', 'table-tags', 'inflection-template', 'class',
  'error-unrecognized-form', 'proscribed', 'nonstandard', 'dialectal', 'regional', 'vocative', 'count-form', 'paucal']);
const CASE_OF = { nominative: 'nom', genitive: 'gen', dative: 'dat', accusative: 'acc', instrumental: 'inst', prepositional: 'prep', locative: 'loc', partitive: 'part' };
const GENDER_OF = { masculine: 'm', feminine: 'f', neuter: 'n' };
const PERSON_OF = { 'first-person': '1', 'second-person': '2', 'third-person': '3' };
const TABLE_OF = {
  'ru-conj': 'verb', 'ru-noun-table': 'noun', 'ru-decl-noun-irreg': 'noun', 'ru-decl-noun-irreg-unc': 'noun', 'ru-ndecl': 'noun',
  'ru-decl-adj': 'adj', 'ru-decl-adj-irreg': 'adj', g: 'pers',
};
const KEEP_POS = new Set(['noun', 'verb', 'adj', 'pron', 'det', 'num', 'adv', 'prep', 'conj', 'particle', 'intj', 'name']);
const PERSONAL = { 'я': '1s', 'ты': '2s', 'он': '3m', 'оно': '3n', 'она': '3f', 'мы': '1p', 'вы': '2p', 'они': '3p', 'себя': 'r' };

const OLD_SPELLING = /[ѣіѳѵ]|[бвгджзклмнпрстфхцчшщ]ъ(\s|$)/i;
function kForm(f) {
  if (!f || f === '-' || f === '—' || OLD_SPELLING.test(f)) return null;
  const clean = f.replace(/[\s]*[¹²³⁴⁵⁶⁷⁸⁹⁰*]+$/u, '').replace(/\s+[0-9]+$/, '').trim();
  return normForm(clean);
}

/** Zeile aus der Tabelle → Feld(er) im Layout. */
function slotsFor(table, tags) {
  const t = new Set(tags);
  const kase = Object.keys(CASE_OF).find((c) => t.has(c));
  if (table === 'verb') {
    if (t.has('infinitive')) return ['inf'];
    if (t.has('participle')) {
      if (t.has('adverbial')) return [t.has('present') ? 'gerunds.pres' : 'gerunds.past'];
      const voice = t.has('passive') ? 'Pass' : 'Act';
      return [`participles.${t.has('present') ? 'pres' : 'past'}${voice}`];
    }
    if (t.has('imperative')) return t.has('second-person') ? [`imperative.${t.has('plural') ? 'pl' : 'sg'}`] : [];
    if (t.has('past')) {
      if (t.has('plural')) return ['past.pl'];
      const g = Object.keys(GENDER_OF).find((x) => t.has(x));
      return g ? [`past.${GENDER_OF[g]}`] : [];
    }
    for (const tense of ['present', 'future']) {
      if (!t.has(tense)) continue;
      const p = Object.keys(PERSON_OF).find((x) => t.has(x));
      return p ? [`${tense}.${t.has('plural') ? 'pl' : 'sg'}${PERSON_OF[p]}`] : [];
    }
    return [];
  }
  if (table === 'noun') {
    if (!kase) return [];
    return [`${t.has('plural') ? 'pl' : 'sg'}.${CASE_OF[kase]}`];
  }
  if (table === 'adj') {
    if (t.has('short-form')) {
      if (t.has('plural')) return ['short.pl'];
      const g = Object.keys(GENDER_OF).find((x) => t.has(x));
      return g ? [`short.${GENDER_OF[g]}`] : [];
    }
    if (!kase || CASE_OF[kase].length > 4) return [];
    const c = CASE_OF[kase];
    const gs = Object.keys(GENDER_OF).filter((x) => t.has(x)).map((x) => GENDER_OF[x]);
    if (t.has('plural')) return gs.length ? [] : [`pl.${c}`];
    return (gs.length ? gs : ['m', 'f', 'n']).map((g) => `${g}.${c}`);
  }
  return [];
}

/** Personalpronomen-Tabelle (ein gemeinsamer Block für я … они, себя) → Spalte des Worts. */
function personalSlots(forms, word) {
  const want = PERSONAL[word];
  if (!want) return null;
  const slots = {};
  let kase = null;
  let seenThird = false;
  for (const f of forms) {
    const t = new Set(f.tags ?? []);
    const k = Object.keys(CASE_OF).find((c) => t.has(c));
    if (!k || ![...t].some((x) => x === 'personal' || x === 'reflexive')) continue;
    if (k !== kase) { kase = k; seenThird = false; }
    if (![...f.form].some((c) => /[а-яё]/i.test(c)) && f.form !== '-') continue; // Umschrift
    let col;
    if (t.has('reflexive')) col = 'r';
    else if (t.has('third-person')) {
      seenThird = true;
      if (t.has('singular')) col = Object.keys(GENDER_OF).filter((g) => t.has(g)).map((g) => `3${GENDER_OF[g]}`);
      else col = '3p';
    } else if (t.has('first-person')) col = seenThird ? '1p' : '1s';
    else if (t.has('second-person')) col = seenThird ? '2p' : '2s';
    const cols = Array.isArray(col) ? col : [col];
    if (!cols.includes(want)) continue;
    const v = kForm(f.form);
    if (v) (slots[CASE_OF[k]] ??= []).includes(v) || slots[CASE_OF[k]].push(v);
  }
  return Object.keys(slots).length ? slots : null;
}

function slimKaikki(e) {
  // Wendungen aus mehreren Wörtern (»весь день«) sind keine Wortformen
  if (!KEEP_POS.has(e.pos) || !e.word || !/[а-яё]/i.test(e.word) || /\s/.test(e.word.trim())) return null;
  const senses = e.senses ?? [];
  const isFormOf = senses.length > 0 && senses.every((s) => s.form_of || s.alt_of || (s.tags ?? []).includes('form-of'));
  if (isFormOf) {
    // Verweis: »со« ist Nebenform von »с«, »годах« eine Form von »год«
    const alt = senses.flatMap((s) => s.alt_of ?? []).map((x) => x.word).filter(Boolean);
    const of = senses.flatMap((s) => s.form_of ?? []).map((x) => x.word).filter(Boolean);
    const to = [...new Set([...alt, ...of])].filter((w) => /[а-яё]/i.test(w));
    return to.length ? { alias: e.word, pos: e.pos, to, alt: alt.length > 0 } : null;
  }
  const forms = e.forms ?? [];
  const templates = forms.filter((f) => (f.tags ?? []).includes('inflection-template') && !(f.tags ?? []).includes('dated')).map((f) => f.form);
  let table = templates.map((x) => TABLE_OF[x]).find(Boolean) ?? null;
  // Tabellenart muss zur Wortart passen (Partizip-Einträge als Verb mit Adjektivtabelle u. ä. überspringen).
  if (table === 'verb' && e.pos !== 'verb') table = null;
  if (e.pos === 'verb' && table !== 'verb') table = null;
  let slots = {};
  if (table === 'pers') {
    slots = personalSlots(forms, e.word) ?? {};
    table = Object.keys(slots).length ? 'pron' : null;
  } else if (table) {
    const acc = {};
    for (const f of forms) {
      const tags = f.tags ?? [];
      if (!['declension', 'conjugation', 'inflection'].includes(f.source)) continue;
      if (tags.some((x) => SKIP_TAGS.has(x))) continue;
      let v = kForm(f.form);
      if (!v) continue;
      if (tags.includes('locative')) v = v.replace(/^(в|во|на) /, ''); // »в году́« → году́
      if ((table === 'verb' || tags.includes('locative')) && v.includes(' ')) continue; // zusammengesetzte Zukunft (буду говорить) ist keine Form
      for (const s of slotsFor(table, tags)) {
        // Akkusativ: erst unbelebt, dann belebt (wie OpenRussian)
        if (s.endsWith('.acc') && table === 'adj') {
          const kind = tags.includes('animate') ? 'an' : 'in';
          ((acc[s] ??= { in: [], an: [] })[kind]).push(v);
          continue;
        }
        (slots[s] ??= []).includes(v) || slots[s].push(v);
      }
    }
    for (const [s, { in: i, an }] of Object.entries(acc)) slots[s] = [...new Set([...i, ...an])];
    // Pronomen mit Substantivtabelle nur im Singular (кто, что) → flache Fälle
    if (table === 'noun' && ['pron', 'det'].includes(e.pos) && !Object.keys(slots).some((s) => s.startsWith('pl.'))) {
      const flat = {};
      for (const [s, v] of Object.entries(slots)) if (!s.endsWith('.loc') && !s.endsWith('.part')) flat[s.slice(3)] = v;
      slots = flat;
      table = 'pron';
    }
    if (!Object.keys(slots).length) table = null;
  }
  const canonF = forms.find((f) => (f.tags ?? []).includes('canonical'));
  let canon = canonF ? kForm(canonF.form) : null;
  if (!canon || keyOf(canon) !== keyOf(e.word)) canon = (slots.inf ?? slots['sg.nom'] ?? slots['m.nom'] ?? slots.nom ?? slots['pl.nom'] ?? [])
    .find((x) => keyOf(x) === keyOf(e.word)) ?? normForm(e.word);
  if (!canon) return null;
  const head = forms.filter((f) => !f.source);
  const tagsOf = (x) => new Set(x?.tags ?? []);
  const ct = tagsOf(canonF);
  const inf = forms.find((f) => f.source === 'conjugation' && (f.tags ?? []).includes('infinitive'));
  const it = tagsOf(inf);
  // »(по)мудре́е« = мудре́е und помудре́е
  const opt = (f) => { const m = /^\(([^)]+)\)(.+)$/.exec(f.form); return m ? [m[2], m[1] + m[2]] : [f.form]; };
  const comp = head.filter((f) => (f.tags ?? []).includes('comparative')).flatMap(opt).map(kForm).filter((x) => x && !x.includes(' '));
  const sup = head.filter((f) => (f.tags ?? []).includes('superlative')).flatMap(opt).map(kForm).filter(Boolean);
  if (table === 'adj') {
    if (comp.length) slots.comp = [...new Set(comp)];
    if (sup.length) slots.sup = [...new Set(sup)];
  }
  const en = [];
  for (const s of senses) {
    if (s.form_of || s.alt_of) continue;
    const g = s.glosses?.[s.glosses.length - 1];
    if (g && !en.includes(g)) en.push(g.length > 90 ? `${g.slice(0, 87)}…` : g);
    if (en.length >= 4) break;
  }
  return {
    w: e.word, pos: e.pos, canon, table, slots, en,
    g: ct.has('masculine') ? 'm' : ct.has('feminine') ? 'f' : ct.has('neuter') ? 'n' : (ct.has('plural') ? 'pl' : undefined),
    anim: ct.has('animate') ? true : ct.has('inanimate') ? false : undefined,
    asp: it.has('imperfective') && it.has('perfective') ? 'both' : it.has('imperfective') ? 'impf' : it.has('perfective') ? 'pf' : undefined,
  };
}

async function kaikki() {
  if (!existsSync(KAIKKI)) {
    console.warn(`Wiktionary-Auszug fehlt (${KAIKKI}) — baue nur aus OpenRussian.`);
    return null;
  }
  const mtime = statSync(KAIKKI).mtimeMs;
  if (existsSync(KAIKKI_CACHE)) {
    const c = JSON.parse(readFileSync(KAIKKI_CACHE, 'utf8'));
    if (c.version === KAIKKI_VERSION && c.mtime === mtime) return c.records;
  }
  console.log('Lese Wiktionary-Auszug (einmalig, ~1–2 min) …');
  const records = [];
  const rl = readline.createInterface({ input: createReadStream(KAIKKI).pipe(createGunzip()), crlfDelay: Infinity });
  for await (const line of rl) {
    const r = slimKaikki(JSON.parse(line));
    if (r) records.push(r);
  }
  writeFileSync(KAIKKI_CACHE, JSON.stringify({ version: KAIKKI_VERSION, mtime, records }));
  return records;
}

// ── Zusammenführen ──────────────────────────────────────────────────────────

const KPOS = { det: 'pron', pron: 'pron', num: 'num', adv: 'adv', prep: 'prep', conj: 'conj', particle: 'particle', intj: 'intj', name: 'name', noun: 'noun', verb: 'verb', adj: 'adj' };
// Tabellenart → OpenRussian-Datei, die dieselbe Tabelle hätte
const TABLE_FILE = { verb: 'verbs', noun: 'nouns', adj: 'adjectives', pron: 'others' };
// Welche Wiktionary-Wortarten zu welcher OpenRussian-Datei passen (und umgekehrt)
const GROUP_OR = { verbs: ['verb'], nouns: ['noun', 'name', 'num'], adjectives: ['adj', 'pron', 'det', 'num'], others: ['pron', 'det', 'num', 'adv', 'prep', 'conj', 'particle', 'intj', 'adj', 'noun'] };
const GROUP_K = { verb: ['verbs'], noun: ['nouns', 'others'], name: ['nouns'], adj: ['adjectives', 'others'], pron: ['adjectives', 'others'], det: ['adjectives', 'others'], num: ['adjectives', 'others', 'nouns'],
  adv: ['others'], prep: ['others'], conj: ['others'], particle: ['others'], intj: ['others'] };
/** Vergleich der Betonung: klein, ё→е bleibt unterscheidbar über "+". */
const stressKey = (s) => s.toLowerCase();

function merge(or, krecs) {
  const byKey = new Map();
  for (const r of krecs ?? []) {
    if (r.alias) continue;
    const k = keyOf(r.w);
    if (!k) continue;
    (byKey.get(k) ?? byKey.set(k, []).get(k)).push(r);
  }
  const orKeys = new Map(); // key → Set(file)
  for (const e of or) (orKeys.get(phraseKey(e.lemma)) ?? orKeys.set(phraseKey(e.lemma), new Set()).get(phraseKey(e.lemma))).add(e.file);

  const orCount = new Map();
  for (const e of or) orCount.set(`${e.file}|${phraseKey(e.lemma)}`, (orCount.get(`${e.file}|${phraseKey(e.lemma)}`) ?? 0) + 1);

  for (const e of or) {
    const cands = (byKey.get(phraseKey(e.lemma)) ?? []).filter((r) => GROUP_OR[e.file].includes(r.pos));
    if (!cands.length) continue;
    // Wortart genauer: Pronomen/Zahlwörter, die OpenRussian unter Adjektiven/Sonstigen führt
    if (e.file === 'adjectives' && !cands.some((r) => r.pos === 'adj')) {
      const p = cands.find((r) => ['pron', 'det', 'num'].includes(r.pos));
      if (p) { e.pos = KPOS[p.pos]; stats.wiktRetagged++; }
    }
    if (e.file === 'others') {
      // OpenRussian führt hier Pronomen, Zahlwörter, Adverbien … und ein paar falsch einsortierte
      // Substantive/Adjektive. Formen nur übernehmen, wenn das Wort sicher dasselbe ist.
      const stressOk = (r) => stressKey(r.canon) === stressKey(e.lemma);
      const prn = cands.filter((r) => r.table && ['pron', 'det', 'num'].includes(r.pos));
      const adj = cands.filter((r) => r.table === 'adj' && r.pos === 'adj' && stressOk(r));
      // deutsche Substantive beginnen groß — so fällt »ляп« (Interjektion: peng) nicht auf das Substantiv ляп
      const noun = cands.filter((r) => r.table === 'noun' && r.pos === 'noun' && stressOk(r) && /^[A-ZÄÖÜ]/.test(e.de[0] ?? ''));
      const pick = prn.find(stressOk) ?? (prn.length === 1 ? prn[0] : null) ?? adj[0] ?? noun[0] ?? null;
      if (pick) {
        e.pos = KPOS[pick.pos];
        e.table = pick.table;
        e.slots = { ...pick.slots };
        if (pick.g && pick.table === 'noun') e.gender = pick.g;
        if (pick.table === 'noun' && pick.anim !== undefined) e.animate = pick.anim;
        e.src = 'or+wikt';
        stats.wiktUpgradedOthers++;
        bump(stats.wiktFilledSlots, `${pick.table}:alle (Sonstige)`, Object.keys(pick.slots).length);
      } else {
        const p = cands.find((r) => !['noun', 'adj', 'name'].includes(r.pos));
        if (p) e.pos = KPOS[p.pos];
      }
      continue;
    }
    // Zahlwort, das OpenRussian als Substantiv mit kaputter Tabelle führt (пять: »пя«)
    if (e.file === 'nouns') {
      const num = cands.find((r) => r.pos === 'num' && r.table && stressKey(r.canon) === stressKey(e.lemma));
      if (num && !cands.some((r) => r.pos === 'noun')) {
        Object.assign(e, { pos: 'num', table: num.table, slots: { ...num.slots }, gender: undefined, animate: undefined, src: 'or+wikt' });
        stats.wiktNumerals = (stats.wiktNumerals ?? 0) + 1;
        continue;
      }
    }
    const want = e.table;
    const same = cands.filter((r) => r.table === want);
    const hasMark = e.lemma.includes('+') || vowels(e.lemma) < 2 || e.lemma.includes('ё');
    // Gleiche Betonung = gleiches Wort. Weicht nur die Betonung ab (форм+ировать / формиров+ать), gilt es als
    // dasselbe Wort, wenn es auf beiden Seiten das einzige dieser Schreibung und Wortart ist.
    let k = same.find((r) => stressKey(r.canon) === stressKey(e.lemma)) ?? (!hasMark && same.length === 1 ? same[0] : null);
    if (!k && same.length === 1 && orCount.get(`${e.file}|${phraseKey(e.lemma)}`) === 1) {
      k = same[0];
      stats.wiktStressMismatchMatched++;
    }
    if (!k) continue;
    let filled = false;
    for (const path of LAYOUT[want]) {
      if (!k.slots[path]?.length) continue;
      if (want === 'verb' && path.startsWith('future.') && e.aspect === 'both') continue; // eine Reihe gilt für beides
      // Präsens bei vollendeten / Zukunft bei unvollendeten Verben nur, wenn es echte Einzelformen sind
      if (want === 'verb' && path.startsWith('present.') && (e.aspect === 'pf' || k.asp === 'pf')) continue;
      // Zukunft bei unvollendeten Verben nur, wenn Wiktionary eine eigene einfache Zukunft kennt (быть → б+уду),
      // nicht wenn es das Verb dort als zweiaspektig führt (Zukunft = Präsensformen).
      if (want === 'verb' && path.startsWith('future.') && e.aspect === 'impf'
        && (k.asp !== 'impf' || JSON.stringify(k.slots[path]) === JSON.stringify(k.slots[path.replace('future', 'present')]))) continue;
      if (want === 'noun' && e.sgOnly && path.startsWith('pl.')) continue;
      if (want === 'noun' && e.plOnly && path.startsWith('sg.')) continue;
      const cur = e.slots[path] ?? [];
      if (!cur.length) {
        e.slots[path] = k.slots[path];
        bump(stats.wiktFilledSlots, `${want}:${path.replace(/\.(sg|pl)\d$/, '.*').replace(/^(m|f|n|pl)\./, '*.')}`);
        filled = true;
      } else {
        // Nebenformen, die OpenRussian nicht hat (год: Mehrzahl г+оды/год+а neben лет+а)
        const have = new Set(cur.map(keyOf));
        const add = k.slots[path].filter((x) => !have.has(keyOf(x)));
        if (add.length) {
          e.slots[path] = [...cur, ...add];
          stats.wiktExtraVariants += add.length;
          filled = true;
        }
      }
    }
    if (filled) e.src = 'or+wikt';
  }

  // Wörter, die OpenRussian gar nicht hat
  const extra = [];
  const seen = new Map();
  let order = 0;
  for (const [k, recs] of byKey) {
    for (const r of recs) {
      const files = [...(GROUP_K[r.pos] ?? []), TABLE_FILE[r.table]].filter(Boolean);
      const orHas = orKeys.get(k);
      if (orHas && files.some((f) => orHas.has(f))) continue;
      // Wörter ohne Tabelle bringen nur eine Bedeutung — nicht, wenn OpenRussian das Wort schon hat
      if (orHas && !r.table) continue;
      const pos = KPOS[r.pos];
      const sig = `${pos}|${r.canon}|${JSON.stringify(r.slots)}`;
      const prev = seen.get(sig);
      if (prev) { for (const g of r.en) if (!prev.en.includes(g)) prev.en.push(g); continue; }
      const e = { src: 'wikt', file: 'wikt', lemma: r.canon, pos, table: r.table ?? 'none', rank: 100000 + order++,
        gender: r.table === 'noun' ? r.g : undefined, animate: r.table === 'noun' ? r.anim : undefined, aspect: r.asp,
        de: [], en: r.en.slice(0, 4), slots: { ...r.slots } };
      if (e.table === 'verb' && !e.slots.inf) e.slots.inf = [r.canon];
      seen.set(sig, e);
      extra.push(e);
      stats.wiktOnlyEntries++;
      bump(stats.wiktOnlyByPos, pos);
    }
  }
  return [...or, ...extra];
}

// ── Schreiben ───────────────────────────────────────────────────────────────

function pack(e) {
  const meta = { r: e.rank };
  if (e.gender) meta.g = e.gender;
  if (e.table === 'noun' && e.animate !== undefined) meta.a = e.animate ? 1 : 0;
  if (e.indeclinable) meta.i = 1;
  if (e.sgOnly) meta.so = 1;
  if (e.plOnly) meta.po = 1;
  if (e.aspect) meta.asp = e.aspect;
  if (e.partner?.length) meta.p = e.partner;
  if (e.variants?.length) meta.v = e.variants;
  if (e.src === 'wikt') meta.s = 1; else if (e.src === 'or+wikt') meta.s = 2;
  const values = LAYOUT[e.table].map((p) => encodeValue(e.slots[p] ?? [], e.lemma));
  while (values.length && values[values.length - 1] === '') values.pop();
  const packed = values.join(';');
  const raw = [e.lemma, CODES.POS.indexOf(e.pos), CODES.TABLES.indexOf(e.table), meta, e.de];
  if (e.de.length) raw.push(packed); else raw.push(packed, e.en ?? []);
  return raw;
}

async function main() {
  const t0 = Date.now();
  const or = orEntries();
  console.log(`OpenRussian: ${or.length} Einträge`);
  const krecs = await kaikki();
  if (krecs) console.log(`Wiktionary: ${krecs.length} Grundformen gelesen`);
  const all = merge(or, krecs);

  // Gleiche Einträge (gleiche Datei, Grundform und Formen) zusammenlegen
  const uniq = new Map();
  for (const e of all) {
    const sig = `${e.pos}|${e.table}|${e.lemma}|${JSON.stringify(LAYOUT[e.table].map((p) => e.slots[p] ?? []))}`;
    const prev = uniq.get(sig);
    if (prev) { for (const d of e.de) if (!prev.de.includes(d)) prev.de.push(d); prev.rank = Math.min(prev.rank, e.rank); continue; }
    uniq.set(sig, e);
  }
  // OpenRussian zählt die Häufigkeit je Schreibung: von gleich geschriebenen Wörtern bekommt nur eines
  // den Rang, das andere landet unsortiert am Dateiende (стать: Substantiv Rang 5, Verb Rang 14.828).
  // Gleich geschriebene Wörter teilen sich deshalb den besten Rang; bei Gleichstand Verb vor Pronomen … vor Substantiv.
  const best = new Map();
  for (const e of uniq.values()) if (e.src !== 'wikt') best.set(phraseKey(e.lemma), Math.min(best.get(phraseKey(e.lemma)) ?? Infinity, e.rank));
  for (const e of uniq.values()) if (e.src !== 'wikt') e.rank = best.get(phraseKey(e.lemma));
  const TIE = ['verb', 'pron', 'num', 'adj', 'noun', 'adv', 'prep', 'conj', 'particle', 'intj', 'other', 'name'];
  const entries = [...uniq.values()].sort((a, b) => a.rank - b.rank || TIE.indexOf(a.pos) - TIE.indexOf(b.pos));

  // Verzeichnis: Schlüssel jeder Einzelwort-Form → Einträge
  const index = new Map();
  entries.forEach((e, id) => {
    e.id = id;
    const forms = new Set([e.lemma, ...LAYOUT[e.table].flatMap((p) => e.slots[p] ?? [])]);
    // Grundformen aus mehreren Wörtern (»с тех пор«) unter dem ganzen Ausdruck — für entry()/lookup() mit Wendung
    if (Ph.wordKeys(e.lemma).length > 1) (index.get(phraseKey(e.lemma)) ?? index.set(phraseKey(e.lemma), new Set()).get(phraseKey(e.lemma))).add(id);
    for (const f of forms) {
      const ks = Ph.wordKeys(f);
      if (ks.length !== 1) continue;
      const set = index.get(ks[0]) ?? index.set(ks[0], new Set()).get(ks[0]);
      set.add(id);
    }
  });

  // Verweise aus Wiktionary für Schreibungen, die sonst nichts finden (со → с, здрасьте → здрасте)
  const byLemmaKey = new Map();
  for (const e of entries) (byLemmaKey.get(phraseKey(e.lemma)) ?? byLemmaKey.set(phraseKey(e.lemma), []).get(phraseKey(e.lemma))).push(e);
  for (const a of (krecs ?? []).filter((r) => r.alias)) {
    const k = keyOf(a.alias);
    if (!k || index.has(k) || Ph.wordKeys(a.alias).length !== 1 || OLD_SPELLING.test(a.alias)) continue;
    const pos = KPOS[a.pos];
    const targets = a.to.flatMap((t) => byLemmaKey.get(phraseKey(t)) ?? []);
    const fit = targets.filter((e) => e.pos === pos);
    const use = fit.length ? fit : targets;
    if (!use.length) continue;
    index.set(k, new Set(use.map((e) => e.id)));
    if (a.alt) for (const e of use) {
      const v = kForm(a.alias);
      if (v) (e.variants ??= []).includes(v) || e.variants.push(v);
    }
    stats.wiktAliases++;
  }

  rmSync(OUT, { recursive: true, force: true });
  mkdirSync(join(OUT, 'e'), { recursive: true });
  mkdirSync(join(OUT, 'i'), { recursive: true });
  let bytes = 0;
  const write = (rel, obj) => { const s = JSON.stringify(obj); writeFileSync(join(OUT, rel), s); bytes += Buffer.byteLength(s); return Buffer.byteLength(s); };

  let entBytes = 0;
  for (let n = 0; n * ENTRY_CHUNK < entries.length; n++) {
    entBytes += write(`e/${n}.json`, entries.slice(n * ENTRY_CHUNK, (n + 1) * ENTRY_CHUNK).map(pack));
  }
  const keys = [...index.keys()].sort();
  const firsts = [];
  let idxBytes = 0;
  let maxIdx = 0;
  for (let n = 0; n * INDEX_CHUNK < keys.length; n++) {
    const part = keys.slice(n * INDEX_CHUNK, (n + 1) * INDEX_CHUNK);
    firsts.push(part[0]);
    let prev = '';
    const lines = part.map((k) => {
      let p = 0;
      while (p < k.length && p < prev.length && k[p] === prev[p]) p++;
      prev = k;
      const ids = [...index.get(k)].sort((a, b) => a - b).map((x) => x.toString(36)).join(',');
      return `${p}${k.slice(p)} ${ids}`;
    });
    const b = write(`i/${n}.json`, { v: lines.join('\n') });
    idxBytes += b;
    maxIdx = Math.max(maxIdx, b);
  }
  const metaObj = { version: 1, built: new Date().toISOString().slice(0, 10), chunk: ENTRY_CHUNK, entries: entries.length, keys: keys.length, idx: firsts };
  const metaBytes = write('meta.json', metaObj);

  const st = stressStats(entries.map((e) => ({ src: e.src, forms: [e.lemma, ...LAYOUT[e.table].flatMap((p) => e.slots[p] ?? [])] })));
  const report = {
    entries: entries.length,
    bySrc: entries.reduce((o, e) => (bump(o, e.src), o), {}),
    byPos: entries.reduce((o, e) => (bump(o, e.pos), o), {}),
    keys: keys.length,
    bytes: { total: bytes, entries: entBytes, index: idxBytes, meta: metaBytes, entryFiles: Math.ceil(entries.length / ENTRY_CHUNK), indexFiles: firsts.length, largestIndexFile: maxIdx },
    stress: st,
    droppedForms: stats.droppedForms,
    wiktionary: krecs ? {
      onlyEntries: stats.wiktOnlyEntries, onlyByPos: stats.wiktOnlyByPos, othersWithTable: stats.wiktUpgradedOthers,
      retaggedPronouns: stats.wiktRetagged, aliases: stats.wiktAliases, extraVariants: stats.wiktExtraVariants,
      stressMismatchMatched: stats.wiktStressMismatchMatched, numeralsReplaced: stats.wiktNumerals ?? 0, filledSlots: stats.wiktFilledSlots,
    } : 'nicht vorhanden',
    seconds: Math.round((Date.now() - t0) / 1000),
  };
  writeFileSync(new URL('./last-build.json', import.meta.url), `${JSON.stringify(report, null, 2)}\n`);
  console.log(JSON.stringify(report, null, 2));
}

await main();
