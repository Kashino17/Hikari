import assert from 'node:assert/strict';
import { test } from 'node:test';
import { CLOTHS, LEVELS, TYPES, buildEndless, buildLevel, buildRush } from './levels.js';
import heights from './foods.json' with { type: 'json' };
import { unreachableCount } from './sim.js';

function check(level, label) {
  assert.ok(CLOTHS[level.cloth], `${label}: Tischdecke ${level.cloth}`);
  assert.ok(level.items.filter((i) => !i.bomb).length >= 20, `${label}: zu wenig Dinge (${level.items.length})`);
  for (const bomb of level.items.filter((i) => i.bomb)) assert.equal(bomb.mass, 0, `${label}: Bombe mit Masse`);
  for (const item of level.items) {
    assert.ok(TYPES[item.type] && heights[item.type], `${label}: Modell ${item.type}`);
    const edge = Math.max(Math.abs(item.x), Math.abs(item.z)) + item.r;
    assert.ok(edge <= level.arena, `${label}: ${item.type} ragt über den Rand (${edge.toFixed(1)} > ${level.arena})`);
    const toStart = Math.hypot(item.x - level.start.x, item.z - level.start.z);
    assert.ok(toStart > item.r + 1.3, `${label}: ${item.type} steht auf dem Startplatz`);
  }
  assert.equal(unreachableCount(level.items), 0, `${label}: nicht alles ist erreichbar`);

  // Am Boden darf sich nichts ineinanderschieben.
  const ground = level.items.filter((i) => i.y === 0);
  for (let a = 0; a < ground.length; a++) {
    for (let b = a + 1; b < ground.length; b++) {
      const d = Math.hypot(ground[a].x - ground[b].x, ground[a].z - ground[b].z);
      const min = (ground[a].r + ground[b].r) * 0.72;
      assert.ok(d >= min, `${label}: ${ground[a].type} und ${ground[b].type} überlappen (${d.toFixed(2)} < ${min.toFixed(2)}) bei ${ground[a].x.toFixed(1)}/${ground[a].z.toFixed(1)}`);
    }
  }
}

test('jedes Level der Reise ist lösbar und sauber gestellt', () => {
  assert.equal(LEVELS.length, 15);
  LEVELS.forEach((_, i) => check(buildLevel(i), `Level ${i + 1} »${LEVELS[i].name}«`));
});

test('die Level werden größer', () => {
  const counts = LEVELS.map((_, i) => buildLevel(i).items.length);
  assert.ok(counts[0] < 60, `Level 1 soll klein anfangen (${counts[0]})`);
  assert.ok(counts.at(-1) > 250, `das Finale soll voll sein (${counts.at(-1)})`);
});

test('Zeitjagd: gleiche Zahl, gleiche Karte — und immer lösbar', () => {
  assert.deepEqual(buildRush(7).items, buildRush(7).items);
  assert.notDeepEqual(buildRush(7).items.map((i) => i.type), buildRush(8).items.map((i) => i.type));
  for (const seed of [1, 2, 3, 42, 1234, 99999]) check(buildRush(seed), `Zeitjagd ${seed}`);
});

test('die Reise geht nach den festen Leveln endlos weiter', () => {
  const first = buildLevel(LEVELS.length); // Level 16
  assert.equal(first.name, `Level ${LEVELS.length + 1}`);
  assert.deepEqual(buildLevel(LEVELS.length).items, first.items, 'dieselbe Nummer, dasselbe Level');
  assert.notDeepEqual(buildEndless(17).items.map((i) => i.type), buildEndless(18).items.map((i) => i.type));
});

test('hundert erzeugte Level: alle lösbar, nichts überlappt, Startplatz frei', () => {
  for (let n = LEVELS.length + 1; n <= LEVELS.length + 100; n++) check(buildEndless(n), `Level ${n}`);
  for (const n of [250, 500, 1000, 5000]) check(buildEndless(n), `Level ${n}`);
});

test('mit der Nummer wird es schwerer: mehr Bomben, größere Tafel, weniger Zeit je Ding', () => {
  const stats = (n) => {
    const level = buildEndless(n);
    const bombs = level.items.filter((i) => i.bomb).length;
    const food = level.items.length - bombs;
    return { bombs, arena: level.arena, perItem: level.time / food };
  };
  const average = (from, to, key) => {
    let sum = 0;
    for (let n = from; n <= to; n++) sum += stats(n)[key];
    return sum / (to - from + 1);
  };
  assert.ok(average(60, 80, 'bombs') > average(16, 22, 'bombs') + 2, 'mehr Bomben');
  assert.ok(stats(70).arena > stats(16).arena, 'größere Tafel');
  assert.ok(average(100, 120, 'perItem') < average(16, 36, 'perItem'), 'weniger Zeit je Ding');
  assert.ok(stats(16).bombs >= 1, 'schon das erste erzeugte Level hat eine Bombe');
});

test('ab Level 5 stehen Bomben in den festen Leveln', () => {
  const bombs = LEVELS.map((_, i) => buildLevel(i).items.filter((item) => item.bomb).length);
  assert.deepEqual(bombs.slice(0, 4), [0, 0, 0, 0]);
  assert.ok(bombs.slice(4).every((n) => n >= 2), `Bomben je Level: ${bombs}`);
});
