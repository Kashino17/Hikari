// Prüft erleben.json: je Zeile so viele Bedeutungen und Zeitmarken wie Wörter, Zeiten in
// der Zeile, jedes Wort im Wörterverzeichnis des Stücks.   node tools/russian_web/check_erleben.mjs
import { readFileSync } from 'node:fs';
import * as Ph from '../../android/app/src/main/assets/russian-web/js/domain/phonetics.js';

const data = JSON.parse(readFileSync(new URL('../../android/app/src/main/assets/russian/erleben.json', import.meta.url)));
let bad = 0;
const fail = (m) => { bad++; console.log('FEHLER', m); };
for (const p of data.pieces) {
  let prev = 0;
  p.lines.forEach((l, i) => {
    const ws = Ph.words(l.ru);
    const at = `${p.id} Zeile ${i + 1}`;
    if (ws.length !== l.g.length) fail(`${at}: ${ws.length} Wörter, ${l.g.length} Bedeutungen`);
    if (ws.length !== l.t.length) fail(`${at}: ${ws.length} Wörter, ${l.t.length} Zeitmarken`);
    if (l.g[0] === '=') fail(`${at}: beginnt mit Fortsetzung`);
    if (!(l.a < l.b) || l.a < prev - 0.01 || l.b > p.dur + 0.05) fail(`${at}: Zeitfenster ${l.a}–${l.b}`);
    l.t.forEach((t, k) => { if (t < l.a - 0.01 || t > l.b || (k && t < l.t[k - 1])) fail(`${at}: Wortzeit ${t}`); });
    for (const k of Ph.wordKeys(l.ru)) if (!p.words[k]) fail(`${at}: »${k}« fehlt im Wörterverzeichnis`);
    if (l.rep != null && Ph.plain(p.lines[l.rep].ru) !== Ph.plain(l.ru)) fail(`${at}: rep zeigt auf anderen Text`);
    prev = l.b;
  });
  for (const i of p.take) if (p.lines[i].rep != null) fail(`${p.id}: Vorschlag ${i} ist eine Wiederholung`);
  console.log(p.id, p.lines.length, 'Zeilen,', new Set(p.lines.flatMap((l) => Ph.wordKeys(l.ru))).size, 'Wörter');
}
console.log(bad ? `${bad} Fehler` : 'alles in Ordnung');
process.exit(bad ? 1 : 0);
