import assert from 'node:assert/strict';
import { test } from 'node:test';
import { Builder } from './levels.js';
import { DROP, FALL, GONE, HOLE_RADII, REST, createSim, fits, growthNeed, starsFor, step, unreachableCount } from './sim.js';

const DT = 1 / 60;
function sim(build, { time = 60, arena = 20, start = { x: 0, z: 10 } } = {}) {
  const b = new Builder();
  build(b);
  return createSim({ arena, time, start, items: b.items });
}
/** Führt das Loch geradlinig zum Punkt (x,z) und lässt danach `rest` Sekunden verstreichen. */
function driveTo(s, x, z, rest = 0.6) {
  for (let i = 0; i < 2000; i++) {
    const dx = x - s.hole.x;
    const dz = z - s.hole.z;
    const d = Math.hypot(dx, dz);
    if (d < 0.01) break;
    const stepLen = Math.min(d, 8 * DT);
    step(s, DT, { x: (dx / d) * stepLen, z: (dz / d) * stepLen });
  }
  for (let t = 0; t < rest; t += DT) step(s, DT, { x: 0, z: 0 });
}
const kinds = (s) => s.events.map((e) => e.type);

test('ein kleines Ding fällt hinein, sobald das Loch darunter ist', () => {
  const s = sim((b) => {
    b.add('cherries', 0, 0);
    b.add('barrel', 15, 15); // damit das Level nicht sofort endet
  });
  driveTo(s, 0, 0, 0.1);
  assert.equal(s.items[0].state, FALL);
  assert.equal(s.eaten, 1);
  assert.equal(s.score, 10);
  assert.ok(kinds(s).includes('eat'));
  for (let t = 0; t < 2; t += DT) step(s, DT);
  assert.equal(s.items[0].state, GONE);
});

test('was größer ist als das Loch, bleibt stehen und meldet sich', () => {
  const s = sim((b) => {
    b.add('watermelon', 0, 0);
    b.add('cherries', 9, 9); // damit das Level nicht sofort endet
  });
  driveTo(s, 0, 0);
  assert.equal(s.items[0].state, REST);
  assert.equal(s.eaten, 0);
  assert.ok(kinds(s).includes('tooBig'));
  assert.equal(fits(s.items[0].r, HOLE_RADII[0]), false);
});

test('über der Kante kippt ein Ding und rutscht von selbst hinein', () => {
  const s = sim((b) => b.add('donut', 0, 0));
  // Lochrand knapp neben dem Donut: Mitte liegt noch nicht »drin«.
  driveTo(s, 0, 1.2, 0);
  assert.equal(s.items[0].state, REST);
  assert.ok(s.items[0].tilt > 0, 'kippt');
  for (let t = 0; t < 3 && s.items[0].state === REST; t += DT) step(s, DT);
  assert.equal(s.items[0].state, FALL, 'rutscht hinein');
});

test('das Loch wächst mit der verschluckten Masse', () => {
  const need = growthNeed(1);
  const s = sim((b) => {
    b.grid('cherries', need, 1, 0, 0, 0.9);
    b.add('barrel', 15, 15);
  });
  for (let x = -need / 2; x <= need / 2; x += 0.5) driveTo(s, x, 0, 0.05);
  assert.equal(s.hole.size, 2);
  assert.ok(kinds(s).includes('grow'));
  for (let t = 0; t < 2; t += DT) step(s, DT);
  assert.ok(Math.abs(s.hole.r - HOLE_RADII[1]) < 0.02, 'Radius folgt der Größe');
});

test('ein Turm sinkt ins Loch: jedes Stockwerk folgt dem unteren', () => {
  const s = sim((b) => b.tower('donut', 6, 0, 0));
  assert.ok(s.items[5].y > s.items[1].y);
  driveTo(s, 0, 0, 3);
  assert.equal(s.eaten, 6);
  assert.ok(s.items.every((i) => i.state === GONE));
  assert.equal(s.cleared, true);
});

test('zieht das Loch weiter, setzt sich der Rest des Turms wieder ab', () => {
  const s = sim((b) => {
    b.tower('donut', 4, 0, 0);
    b.add('barrel', 15, 15);
  });
  const h = s.items[0].h * 0.97;
  driveTo(s, 0, 0, 0); // unterstes Stockwerk fällt
  assert.equal(s.items[0].state, FALL);
  assert.equal(s.items[1].state, DROP);
  // Sofort weg, bevor das zweite Stockwerk unten ankommt.
  s.hole.x = 12;
  for (let t = 0; t < 2; t += DT) step(s, DT);
  assert.deepEqual(s.items.slice(1, 4).map((i) => i.state), [REST, REST, REST]);
  assert.ok(Math.abs(s.items[1].y) < 1e-6, 'zweites steht jetzt am Boden');
  assert.ok(Math.abs(s.items[2].y - s.items[1].h) < 0.05, 'drittes ruht darauf');
  assert.ok(s.items[3].y > h, 'viertes obenauf');
});

test('in der Pyramide hält ein Ding, solange genug darunter steht', () => {
  const s = sim((b) => {
    b.pyramid('soda-can', 2, 0, 0); // 4 unten, 1 oben
    b.add('barrel', 15, 15);
  });
  const top = s.items[4];
  assert.equal(top.supports.length, 4);
  // Eine Ecke wegfressen: 3 von 4 tragen noch.
  driveTo(s, s.items[0].x - 0.5, s.items[0].z - 0.5, 0.5);
  assert.equal(s.items[0].state === FALL || s.items[0].state === GONE, true);
  if (s.items.slice(1, 4).every((i) => i.state === REST)) assert.equal(top.state, REST);
});

test('Level endet mit allem verschluckt (Restzeit zählt) oder mit der Zeit', () => {
  const cleared = sim((b) => b.add('cherries', 0, 0), { time: 30 });
  driveTo(cleared, 0, 0, 0.2);
  assert.equal(cleared.finished, true);
  assert.equal(cleared.cleared, true);
  assert.ok(cleared.score > 10, 'Zeitbonus');

  const late = sim((b) => b.add('barrel', 0, 0), { time: 0.5 });
  for (let t = 0; t < 1; t += DT) step(late, DT);
  assert.equal(late.finished, true);
  assert.equal(late.cleared, false);
  assert.equal(late.score, 0);
});

test('das Loch bleibt in der Arena', () => {
  const s = sim((b) => b.add('barrel', 0, 0), { arena: 10 });
  for (let i = 0; i < 600; i++) step(s, DT, { x: 1, z: -1 });
  assert.ok(s.hole.x <= 10 && s.hole.z >= -10);
});

test('Sterne nach Anteil', () => {
  assert.equal(starsFor(0, 10), 0);
  assert.equal(starsFor(4, 10), 0);
  assert.equal(starsFor(5, 10), 1);
  assert.equal(starsFor(8, 10), 2);
  assert.equal(starsFor(10, 10), 3);
});

test('unlösbare Level werden erkannt', () => {
  assert.equal(unreachableCount([{ r: 0.38, mass: 1 }, { r: 0.62, mass: 2 }]), 0);
  // Ein Fass, aber viel zu wenig Kleines zum Wachsen.
  assert.equal(unreachableCount([{ r: 0.38, mass: 1 }, { r: 2.5, mass: 30 }]), 1);
});
