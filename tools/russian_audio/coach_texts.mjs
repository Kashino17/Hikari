// Sammelt alle deutschen Ansagen des Blind-Coachs (»Sag auf Russisch: …«) aus Sprech- und
// Podcastkurs, für beide Ich-Formen. Ausgabe: JSON [{ de, spoken }] auf stdout.
//
//   node tools/russian_audio/coach_texts.mjs > /tmp/coach_texts.json
//
// `de` ist der Text, wie ihn die Oberfläche kennt (Schlüssel zum Nachschlagen),
// `spoken` das, was Rem tatsächlich sagt: Klammer-Hinweise wandern nach vorn
// (»Hallo! (locker)« → »Sag auf Russisch, locker: Hallo!«), » / « wird »oder«.
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ASSETS = join(dirname(fileURLToPath(import.meta.url)), '../../android/app/src/main/assets');
const { CourseIndex } = await import(join(ASSETS, 'russian-web/js/domain/course.js'));

export function spokenPrompt(de) {
  let text = de.trim();
  const hints = [];
  // Hinweise in Klammern am Ende (auch mehrere): »… (höflich)«
  for (let m; (m = /\s*\(([^()]+)\)\s*$/.exec(text)); ) {
    hints.unshift(m[1].trim());
    text = text.slice(0, m.index).trim();
  }
  // Klammern mitten im Satz werden zum Einschub: »Sie (höflich) haben« → »Sie, höflich, haben«.
  // Am Satzanfang ist es meist ein weglassbares Wort: »(Ich) gratuliere« → »Ich gratuliere«.
  text = text.replace(/^\(([^()]+)\)\s*/, '$1 ');
  text = text.replace(/\s*\(([^()]+)\)\s*/g, ', $1, ').replace(/([.!?]),\s*/g, '$1 ').replace(/,\s*([.,!?;:])/g, '$1').replace(/^,\s*/, '').replace(/[()]/g, '').replace(/\s+\/\s+/g, ' oder ').replace(/\s*\/\s*/g, ' oder ').replace(/\s+/g, ' ');
  const lead = hints.length ? `Sag auf Russisch, ${hints.join(', ')}:` : 'Sag auf Russisch:';
  return `${lead} ${text}`;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const seen = new Set();
  const out = [];
  for (const f of ['course', 'podcast']) {
    const idx = new CourseIndex(JSON.parse(readFileSync(join(ASSETS, `russian/${f}.json`), 'utf8')));
    for (const g of ['MALE', 'FEMALE']) {
      for (const p of idx.phrasesUpTo(9999, { gender: g, voice: g })) {
        const de = p.de?.trim();
        if (!de || seen.has(de)) continue;
        seen.add(de);
        out.push({ de, spoken: spokenPrompt(de) });
      }
    }
  }
  process.stdout.write(JSON.stringify(out, null, 0));
}
