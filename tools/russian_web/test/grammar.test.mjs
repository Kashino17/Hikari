// Grammatik-Lernweg: Jede Betonung in den Lektionen muss mit dem Wörterbuch übereinstimmen,
// und jede Übung braucht genau eine richtige Antwort. (Kadirs Regel: nichts Falsches lernen.)
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import * as F from '../../../android/app/src/main/assets/russian-web/js/domain/forms.js';
import { LESSONS, russianOf } from '../../../android/app/src/main/assets/russian-web/js/domain/grammar.js';

const ROOT = new URL('../../../android/app/src/main/assets/russian/forms/', import.meta.url).pathname;
F.setLoader(async (p) => JSON.parse(readFileSync(ROOT + p.replace(/^.*\/forms\//, ''), 'utf8')));

const VOWELS = /[аеёиоуыэюя]/gi;
/** Wörter ohne Wörterbuch-Eintrag, deren Betonung bekannt ist (Namen). */
const NAMES = new Set(['аня', 'аню', 'ане', 'аней']);

test('jede Betonung in den Lektionen stimmt mit dem Wörterbuch überein', async () => {
  const problems = [];
  for (const lesson of LESSONS) {
    for (const text of russianOf(lesson)) {
      for (const marked of text.split(/[\s.,!?«»—()…:;→]+/).filter((t) => /[а-яё]/i.test(t))) {
        const bare = marked.replaceAll('+', '');
        const key = F.keyOf(bare);
        if (NAMES.has(key)) continue;
        const found = await F.lookup(bare);
        if (!found.length) { problems.push(`${lesson.id}: »${bare}« nicht im Wörterbuch`); continue; }
        const syll = (bare.match(VOWELS) ?? []).length;
        if (syll < 2 || /ё/i.test(bare)) continue;
        if (!marked.includes('+')) { problems.push(`${lesson.id}: »${bare}« ohne Betonung`); continue; }
        const forms = new Set(found.flatMap((e) => [e.lemma, ...F.allForms(e)]).map((f) => f.toLowerCase()));
        if (!forms.has(marked.toLowerCase())) problems.push(`${lesson.id}: »${marked}« — Wörterbuch: ${[...forms].filter((f) => F.keyOf(f) === key).slice(0, 4).join(', ')}`);
      }
    }
  }
  assert.deepEqual(problems, []);
});

test('russische Wörter in den deutschen Erklärungen sind betont und stimmen', async () => {
  const problems = [];
  for (const lesson of LESSONS) {
    const texts = lesson.screens.flatMap((x) => [...(x.p ?? []), x.rule, x.why, x.text, x.big, x.term].filter(Boolean));
    for (const text of texts) {
      for (const marked of text.match(/\+?[А-Яа-яЁё][А-Яа-яЁё+]*/g) ?? []) {
        const bare = marked.replaceAll('+', '');
        if ((bare.match(VOWELS) ?? []).length < 2 || /ё/i.test(bare)) continue;
        // Endungen (»-ая«) sind keine Wörter.
        if (new RegExp(`-${bare}`).test(text) && bare.length <= 3) continue;
        if (!marked.includes('+')) { problems.push(`${lesson.id}: »${bare}« im Text ohne Betonung`); continue; }
        if (NAMES.has(F.keyOf(bare))) continue;
        const found = await F.lookup(bare);
        const forms = new Set(found.flatMap((e) => [e.lemma, ...F.allForms(e)]).map((f) => f.toLowerCase()));
        if (!forms.has(marked.toLowerCase())) problems.push(`${lesson.id}: »${marked}« im Text — Wörterbuch: ${[...forms].filter((f) => F.keyOf(f) === F.keyOf(bare)).slice(0, 3).join(', ')}`);
      }
    }
  }
  assert.deepEqual(problems, []);
});

test('jede Übung hat genau eine richtige, eindeutige Antwort', () => {
  for (const lesson of LESSONS) {
    for (const s of lesson.screens.filter((x) => x.kind === 'quiz')) {
      assert.ok(s.ok >= 0 && s.ok < s.opts.length, `${lesson.id}: Index`);
      assert.equal(new Set(s.opts).size, s.opts.length, `${lesson.id}: doppelte Antwort`);
    }
    assert.ok(lesson.screens.some((x) => x.kind === 'say'), `${lesson.id}: Sprechen fehlt`);
    assert.ok(lesson.screens.some((x) => x.kind === 'memo' && x.term), `${lesson.id}: Fachwort erst nach der Idee`);
  }
});
