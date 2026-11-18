// Nachsprechen nur auf Deutsch (Bauplan 18.11.2026): Wer spickt, bekommt den Satz früher wieder.
// 'hint' = nur 1. Wort oder Wortanfänge gebraucht → Abstand wächst halb so stark, Box bleibt.
// 'peek' = ganzer Satz gesehen oder angehört → morgen wieder, Box bleibt.
import test from 'node:test';
import assert from 'node:assert/strict';
import * as P from '../../../android/app/src/main/assets/russian-web/js/domain/progress.js';

const T = 1000;

test('richtig ohne Hilfe: eine Box höher wie bisher', () => {
  const c = P.grade({ box: 3, due: T, seen: 4, wrong: 0 }, true, T);
  assert.equal(c.box, 4);
  assert.equal(c.due, T + P.INTERVALS[4]);
});

test('hint: Box bleibt, Abstand liegt halb zwischen jetziger und nächster Stufe', () => {
  const c = P.grade({ box: 3, due: T, seen: 4, wrong: 0 }, 'hint', T);
  assert.equal(c.box, 3);
  // Box 3 → 4 Tage, Box 4 → 7 Tage: halb so starkes Wachstum = 5,5 → 6 Tage
  assert.equal(c.due, T + 6);
  assert.equal(c.seen, 5);
  assert.equal(c.wrong, 0);
});

test('hint ganz oben: höchste Box bleibt, Abstand wie die Box', () => {
  const c = P.grade({ box: P.MAX_BOX, due: T }, 'hint', T);
  assert.equal(c.box, P.MAX_BOX);
  assert.equal(c.due, T + P.INTERVALS[P.MAX_BOX]);
});

test('peek: Box bleibt, morgen wieder fällig, zählt nicht als Fehler', () => {
  const c = P.grade({ box: 5, due: T, seen: 9, wrong: 1 }, 'peek', T);
  assert.equal(c.box, 5);
  assert.equal(c.due, T + 1);
  assert.equal(c.wrong, 1);
});

test('applyResult nimmt hint und peek aus einer Sitzung an', () => {
  const p = P.applyResult(P.normalizeProgress({ cards: { a: { box: 2, due: T }, b: { box: 4, due: T } } }), { xp: 5, graded: { a: 'hint', b: 'peek' } }, T);
  assert.equal(p.cards.a.box, 2);
  assert.equal(p.cards.a.due, T + 3); // Box 2 → 2 Tage, Box 3 → 4 Tage: 3
  assert.equal(p.cards.b.box, 4);
  assert.equal(p.cards.b.due, T + 1);
  // Nur Zahlen im Lernstand — die App (Kotlin) liest das JSON mit.
  for (const c of Object.values(JSON.parse(JSON.stringify(p.cards)))) for (const v of Object.values(c)) assert.equal(typeof v, 'number');
});

test('stärkste Hilfe gewinnt', () => {
  assert.equal(P.strongerPeek(null, 'hint'), 'hint');
  assert.equal(P.strongerPeek('hint', 'peek'), 'peek');
  assert.equal(P.strongerPeek('peek', 'hint'), 'peek');
  assert.equal(P.strongerPeek(null, null), null);
});
