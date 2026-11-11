// Satzanfang nicht aufgenommen vs. falsch gesprochen — reine Logik, ohne Kursdaten.
// Start: node --test tools/russian_web/test/clipped.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as Sp from '../../../android/app/src/main/assets/russian-web/js/domain/speech.js';

const sc = (marks, heard) => ({ words: marks, heard });
const SENTENCE = ['как', 'ты', 'себя', 'чувствуешь', 'на', 'самом', 'деле'];
const marks = (...missing) => SENTENCE.map((w, i) => [w, !missing.includes(i)]);

test('erstes Wort fehlt in der Aufnahme, der Rest stimmt', () => {
  assert.equal(Sp.clippedStart(sc(marks(0), 'ты себя чувствуешь на самом деле')), 1);
});

test('die ersten zwei Wörter fehlen', () => {
  assert.equal(Sp.clippedStart(sc(marks(0, 1), 'себя чувствуешь на самом деле')), 2);
});

test('steht an der Stelle ein anderes Wort, war es ein Fehlversuch und kein fehlender Anfang', () => {
  assert.equal(Sp.clippedStart(sc(marks(0), 'кок ты себя чувствуешь на самом деле')), 0);
});

test('fehlt auch später etwas, ist es kein reiner Aufnahme-Anfang', () => {
  assert.equal(Sp.clippedStart(sc(marks(0, 4), 'ты себя чувствуешь самом деле')), 0);
});

test('alles erkannt, nichts erkannt, mehr als zwei Wörter: keine Aussage', () => {
  assert.equal(Sp.clippedStart(sc(marks(), 'как ты себя чувствуешь на самом деле')), 0);
  assert.equal(Sp.clippedStart(sc(SENTENCE.map((w) => [w, false]), '')), 0);
  assert.equal(Sp.clippedStart(sc(marks(0, 1, 2), 'чувствуешь на самом деле')), 0);
});

test('fehlt ein Wort in der Mitte oder am Ende, ist das kein Anfang', () => {
  assert.equal(Sp.clippedStart(sc(marks(3), 'как ты себя на самом деле')), 0);
  assert.equal(Sp.clippedStart(sc(marks(6), 'как ты себя чувствуешь на самом')), 0);
});
