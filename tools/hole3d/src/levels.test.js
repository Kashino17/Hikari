import assert from 'node:assert/strict';
import { test } from 'node:test';
import { CLOTHS, LEVELS, TYPES, buildLevel, buildRush } from './levels.js';
import heights from './foods.json' with { type: 'json' };
import { unreachableCount } from './sim.js';

function check(level, label) {
  assert.ok(CLOTHS[level.cloth], `${label}: Tischdecke ${level.cloth}`);
  assert.ok(level.items.length >= 20, `${label}: zu wenig Dinge (${level.items.length})`);
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
