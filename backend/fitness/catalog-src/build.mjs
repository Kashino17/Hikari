// Baut backend/fitness/exercises.json aus den handgeschriebenen Quellen (kurze Schreibweise).
// Aufruf: node backend/fitness/catalog-src/build.mjs
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { exercises as a } from './ex-kraft.mjs';
import { exercises as b } from './ex-rumpf-cardio.mjs';
import { exercises as c } from './ex-mobil-skill.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const all = [...a, ...b, ...c];
const mediaFile = join(here, '..', 'media.json');
const media = existsSync(mediaFile) ? JSON.parse(readFileSync(mediaFile, 'utf8')).media ?? {} : {};

const ids = new Set();
const problems = [];
for (const e of all) {
  if (ids.has(e.id)) problems.push(`doppelt: ${e.id}`);
  ids.add(e.id);
}
for (const e of all) {
  for (const k of ['regress', 'progress']) if (e[k] && !ids.has(e[k])) problems.push(`${e.id}.${k} → ${e[k]} fehlt`);
  if (!e.steps?.length) problems.push(`${e.id}: keine Schritte`);
  if (!e.mistakes?.length) problems.push(`${e.id}: keine Fehler`);
  if (media[e.id]) e.media = media[e.id];
}
if (problems.length) { console.error(problems.join('\n')); process.exit(1); }
writeFileSync(join(here, '..', 'exercises.json'), `${JSON.stringify({ version: 1, exercises: all }, null, 1)}\n`);
const withVideo = all.filter((e) => e.media?.video).length;
console.log(`${all.length} Übungen, ${withVideo} mit Video`);
