// Prüft die JS-Portierung der Lernlogik gegen die Ergebnisse der Android-App.
// parity.json enthält für alle Kurstexte, was die Kotlin-Logik liefert. Es ist nicht
// eingecheckt (6 MB) und wird so erzeugt: ZzWebParityDumpTest.kt.txt nach
// android/app/src/test/java/com/hikari/app/domain/russian/ZzWebParityDumpTest.kt kopieren,
// `./gradlew :app:testDebugUnitTest --tests '*ZzWebParityDumpTest'`, dann
// android/app/build/web-parity.json hierher als parity.json kopieren und den Test wieder löschen.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import * as Ph from '../../../android/app/src/main/assets/russian-web/js/domain/phonetics.js';
import * as Gl from '../../../android/app/src/main/assets/russian-web/js/domain/glossary.js';
import * as Sp from '../../../android/app/src/main/assets/russian-web/js/domain/speech.js';
import * as Co from '../../../android/app/src/main/assets/russian-web/js/domain/course.js';
import * as Ct from '../../../android/app/src/main/assets/russian-web/js/domain/content.js';

const ASSETS = process.env.HIKARI_ASSETS ?? new URL('../../../android/app/src/main/assets/russian', import.meta.url).pathname;
const parity = JSON.parse(readFileSync(new URL('./parity.json', import.meta.url), 'utf8'));
Gl.install(JSON.parse(readFileSync(`${ASSETS}/glosses.json`, 'utf8')));

const spans = (l) => l.map((s) => [s.text, s.stressed]);

function compareAll(name, fn) {
  test(name, () => {
    const bad = [];
    for (const t of parity.texts) {
      try {
        const [actual, expected] = fn(t);
        assert.deepEqual(actual, expected);
      } catch (e) {
        bad.push(`${t.src}\n   ${String(e.message).split('\n').slice(0, 12).join('\n   ')}`);
      }
    }
    assert.equal(bad.length, 0, `${bad.length} von ${parity.texts.length} weichen ab:\n${bad.slice(0, 6).join('\n')}`);
  });
}

compareAll('display', (t) => [Ph.display(t.src), t.display]);
compareAll('transliterateFlat', (t) => [Ph.transliterateFlat(t.src), t.flat]);
compareAll('transliterate spans', (t) => [spans(Ph.transliterate(t.src)), t.spans]);
compareAll('words', (t) => [
  Ph.words(t.src).map((w) => ({ c: w.cyrillic, p: w.plain, l: spans(w.latin), b: w.before, a: w.after })),
  t.words,
]);
compareAll('withStress', (t) => [Gl.withStress(Ph.plain(t.src)), t.withStress]);
compareAll('gloss chunks', (t) => [
  Gl.chunks(Ph.plain(t.src)).map((c) => ({ n: c.words.length, g: c.gloss, l: c.words.map((w) => w.latin.map((s) => s.text).join('')).join(' ') })),
  t.chunks,
]);
compareAll('syllables', (t) => [
  Co.ruWords(t.src).map((w) => { const s = Co.syllables(w); return { w, s: s.syllables, i: s.stressed }; }),
  t.syll,
]);
compareAll('norm', (t) => [Co.norm(t.src), t.norm]);
compareAll('search translit', (t) => [Ct.searchTranslit(t.src), t.search]);
compareAll('clip name (sha1)', (t) => [Ct.clipName(t.src), t.clip]);

compareAll('speech score + hints', (t) => {
  const plain = Ph.plain(t.src);
  const lower = plain.toLowerCase();
  const ws = lower.split(' ');
  const hyps = [plain, ws.slice(1).join(' '), lower.replaceAll('о', 'а'), lower.replaceAll('и', 'ы'),
    lower.replaceAll('ш', 'с').replaceAll('ж', 'з'), [...ws].reverse().join(' ')];
  const res = (sc) => ({
    passed: sc.passed, score: Math.round(sc.score * 1000), heard: sc.heard, words: sc.words,
    hints: Sp.hints(sc).map((h) => [h.target, h.heard, h.tip]),
  });
  const actual = hyps.map((h) => ({ h, r: res(Sp.speech(t.src, [h])) }));
  actual.push({ h: 'multi', r: res(Sp.speech(Ph.display(t.src), [hyps[3], hyps[1], hyps[0]])) });
  return [actual, t.speech];
});

test('Zahlwörter', () => {
  for (const n of parity.numbers) {
    assert.deepEqual(Sp.cardinal(n.n), n.c, `cardinal ${n.n}`);
    assert.deepEqual(Sp.ordinal(n.n), n.o, `ordinal ${n.n}`);
  }
});

// Die Fälle aus RussianSpeechMatchTest.kt — fair, aber streng.
test('Spracherkennung: Regeln aus der App', () => {
  const ok = (target, ...heard) => Sp.speech(target, heard).passed;
  assert.ok(ok('д+есять', '10'));
  assert.ok(ok('Мне дв+адцать од+ин год.', 'мне 21 год'));
  assert.ok(ok('Сег+одня п+ятое м+ая.', 'сегодня 5-е мая'));
  assert.ok(ok('в в+осемь тр+идцать', 'в 8:30'));
  assert.ok(!ok('тр+идцать', '13'));
  assert.ok(!ok('тр+идцать', 'тринадцать'));
  assert.ok(!ok('Мне дв+адцать од+ин год.', 'мне 22 года'));
  assert.ok(Sp.sameNumeral('пяти', 'пять') && Sp.sameNumeral('двух', 'два') && !Sp.sameNumeral('пятница', 'пять'));
  assert.ok(ok('+Это интер+есно.', 'эта интересно'));
  assert.ok(ok('Мой г+ород.', 'мой горот'));
  assert.ok(!ok('м+ама', 'мома'));
  assert.ok(!ok('д+ом', 'дам'));
  assert.ok(ok('Мен+я зов+ут Кад+ир.', 'меня зовут Kadir'));
  assert.ok(ok('Я не зн+аю.', 'я не знал', 'я не знаю'));
  assert.ok(!ok('Спас+ибо!', 'пожалуйста'));
  assert.ok(!ok('Я не поним+аю.', 'я понимаю'));
  assert.ok(!ok('Мы б+ыли там.', 'ми были там'));
  assert.ok(!ok('Да.', ''));
  assert.equal(Sp.reading('д+есять'), "dJEssit'");
});

test('Phonetik: Beispiele aus der App', () => {
  const tr = Ph.transliterateFlat;
  assert.equal(tr('Прив+ет!'), 'Priwjét!');
  assert.equal(tr('Здр+авствуйте!'), 'Sdrástwujte!');
  assert.equal(tr('Что н+ового?'), 'Schto nówawa?');
  assert.equal(tr('Пойдём в каф+е?'), 'Pajdjóm f kafé?');
  assert.equal(tr('Н+е за что.'), 'Njé sa schta.');
  assert.equal(tr('нр+авится'), 'nráwiza');
  assert.deepEqual(Ph.letterPairs('Да, нет'), [['Д', 'd'], ['а', 'a'], null, ['н', 'n'], ['е', 'je'], ['т', 't']]);
});
