// Wort-für-Wort-Zeile unter eigenen Sätzen: Kurs-Wörterbuch zuerst, danach die eigene Wortliste.
// Ausführen: node --test tools/russian_web/test/ownwords.test.mjs
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import * as Gl from '../../../android/app/src/main/assets/russian-web/js/domain/glossary.js';

Gl.install(JSON.parse(readFileSync(new URL('../../../android/app/src/main/assets/russian/glosses.json', import.meta.url), 'utf8')));
const glosses = (ru, extra) => Gl.chunks(ru, extra).map((c) => c.gloss);

test('erkannter Satz bekommt Bedeutungen aus dem Kurs-Wörterbuch', () => {
  // Kadirs Satz vom 13.11.: stand nur kyrillisch da, ohne Umschrift und Bedeutung.
  // »я буду чай« kennt der Kurs als Wendung — deshalb »werde nehmen« statt des nackten »werde«.
  assert.deepEqual(glosses('чай я буду чай'), ['Tee', 'ich', 'werde nehmen', 'Tee']);
  assert.deepEqual(glosses('Да спасибо я чай'), ['ja', 'danke', 'ich', 'Tee']);
});

test('unbekannte Wörter kommen aus der eigenen Wortliste, Satzzeichen und Großschreibung stören nicht', () => {
  const extra = new Map([[Gl.wordKey('Капуч+ино'), 'Cappuccino']]);
  assert.equal(glosses('Капучино, пожалуйста!')[0], null);
  assert.equal(glosses('Капучино, пожалуйста!', extra)[0], 'Cappuccino');
});

test('das Kurs-Wörterbuch geht vor, ganz Unbekanntes bleibt leer', () => {
  const extra = new Map([[Gl.wordKey('чай'), 'FALSCH']]);
  assert.deepEqual(glosses('чай абракадабра', extra), ['Tee', null]);
});
