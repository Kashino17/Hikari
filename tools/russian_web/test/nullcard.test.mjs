// Fehler bis v0.89.104: Karten aus der App ohne »box« → richtig bewertet → box/due = NaN → null.
import test from 'node:test';
import assert from 'node:assert/strict';
import * as P from '../../../android/app/src/main/assets/russian-web/js/domain/progress.js';

test('richtig bewertete Karte ohne box bleibt gültig', () => {
  const p = P.applyResult(P.normalizeProgress({ cards: { a: { due: 100, seen: 1 } } }), { xp: 3, graded: { a: true } }, 100);
  const json = JSON.parse(JSON.stringify(p));
  assert.equal(json.cards.a.box, 2);
  assert.equal(json.cards.a.due, 102);
});

test('grade() direkt mit unvollständiger Karte', () => {
  const c = P.grade({ due: 50 }, true, 50);
  assert.ok(Number.isFinite(c.box) && Number.isFinite(c.due));
});

test('kaputte Karte vom Handy (null) wird beim Laden repariert', () => {
  const p = P.normalizeProgress({ cards: { rj001_01: { due: null, seen: 2, box: null } } });
  assert.equal(p.cards.rj001_01.box, 1);
  assert.ok(Number.isFinite(p.cards.rj001_01.due));
  assert.equal(p.cards.rj001_01.seen, 2);
});
