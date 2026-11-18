// Abdeckung: Wie viele russische Wörter aus den Inhalten der App findet forms.js?
//
// Start: node tools/russian_forms/coverage.mjs [anzahl-ungelöste=50]
// Liest alle Texte (auch Schlüssel) aus assets/russian/{course,podcast,phrasebook,deepdive,patterns,erleben,glosses}.json.
import { readFile } from 'node:fs/promises';
import * as F from '../../android/app/src/main/assets/russian-web/js/domain/forms.js';
import * as Ph from '../../android/app/src/main/assets/russian-web/js/domain/phonetics.js';

const ASSETS = new URL('../../android/app/src/main/assets/russian/', import.meta.url);
const FILES = ['course', 'podcast', 'phrasebook', 'deepdive', 'patterns', 'erleben', 'glosses'];
const TOP = Number(process.argv[2] ?? 50);
F.setLoader(async (path) => JSON.parse(await readFile(new URL(`forms/${path}`, ASSETS), 'utf8')));

const freq = new Map();
const perFile = {};
function walk(v, file) {
  if (typeof v === 'string') {
    for (const k of Ph.wordKeys(Ph.fromDisplay(v))) {
      if (!/[а-яё]/.test(k)) continue;
      freq.set(k, (freq.get(k) ?? 0) + 1);
      perFile[file].add(k);
    }
  } else if (Array.isArray(v)) v.forEach((x) => walk(x, file));
  else if (v && typeof v === 'object') for (const [k, x] of Object.entries(v)) { walk(k, file); walk(x, file); }
}
for (const f of FILES) {
  perFile[f] = new Set();
  walk(JSON.parse(await readFile(new URL(`${f}.json`, ASSETS), 'utf8')), f);
}

const resolved = new Map();
for (const k of freq.keys()) resolved.set(k, (await F.lookup(k)).length > 0);
const tokens = [...freq.values()].reduce((a, b) => a + b, 0);
const tokOk = [...freq].filter(([k]) => resolved.get(k)).reduce((a, [, n]) => a + n, 0);
const ok = [...resolved.values()].filter(Boolean).length;
const pct = (a, b) => `${((100 * a) / b).toFixed(1)} %`;

console.log(`Verschiedene Wörter: ${freq.size}, gefunden: ${ok} (${pct(ok, freq.size)})`);
console.log(`Vorkommen gesamt: ${tokens}, gefunden: ${tokOk} (${pct(tokOk, tokens)})`);
for (const f of FILES) {
  const s = [...perFile[f]];
  const r = s.filter((k) => resolved.get(k)).length;
  console.log(`  ${f.padEnd(11)} ${String(r).padStart(5)} / ${String(s.length).padStart(5)}  ${pct(r, s.length)}`);
}
const missing = [...freq].filter(([k]) => !resolved.get(k)).sort((a, b) => b[1] - a[1]);
console.log(`\nNicht gefunden (${missing.length}), die häufigsten ${TOP}:`);
console.log(missing.slice(0, TOP).map(([k, n]) => `${k} ${n}`).join(', '));
