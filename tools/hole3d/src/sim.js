// Spiellogik ohne Darstellung: Loch, Verschlucken, Wachsen, einstürzende Stapel.
// Bewusst ohne three.js — so läuft sie auch in den Tests (node --test).

export const REST = 0; // steht auf dem Boden oder auf anderen
export const DROP = 1; // hat seinen Halt verloren und fällt herunter
export const FALL = 2; // fällt ins Loch
export const GONE = 3; // verschluckt

/** Größenklassen: Radius der Standfläche in Welt-Einheiten und »Masse« (Punkte, Wachstum). */
export const CLASSES = {
  tiny: { r: 0.38, mass: 1 },
  small: { r: 0.62, mass: 2 },
  medium: { r: 1.0, mass: 5 },
  large: { r: 1.6, mass: 12 },
  huge: { r: 2.5, mass: 30 },
};

/** Lochradius je Größe 1…7. */
export const HOLE_RADII = [1.0, 1.35, 1.8, 2.4, 3.1, 4.0, 5.0];
export const MAX_SIZE = HOLE_RADII.length;

/** Ein Ding passt, wenn seine Standfläche etwas kleiner ist als das Loch. */
export const FIT = 0.92;
const GRAVITY = 34;
const GRID = 4; // Kantenlänge einer Suchzelle

/** Masse, die das Loch von Größe `size` zur nächsten braucht. */
export function growthNeed(size) {
  return Math.round(10 * Math.pow(1.7, size - 1));
}

export function fits(itemRadius, holeRadius) {
  return itemRadius <= holeRadius * FIT;
}

/**
 * @param level  { arena, time, items: [{ type, cls, x, z, y, r, h, mass, supports: number[] }] }
 */
export function createSim(level) {
  const items = level.items.map((src, index) => ({
    index,
    type: src.type,
    x: src.x,
    y: src.y,
    z: src.z,
    r: src.r,
    vis: src.vis ?? src.r * 2, // Darstellungsgröße (bei hohen Dingen kleiner als die Standfläche)
    h: src.h,
    mass: src.mass,
    yaw: src.yaw ?? 0,
    supports: src.supports ?? [],
    dependents: [],
    state: REST,
    vy: 0,
    tilt: 0, // 0…1: wie weit das Ding über die Kante kippt
    tiltDir: 0, // Richtung zum Loch (Winkel in der Ebene)
    spin: 0, // Taumeln beim Fallen
    spinAxis: index * 2.399, // gleichmäßig verteilte Achsen ohne Zufall
    wobble: 0, // Zittern, wenn das Loch noch zu klein ist
  }));
  for (const item of items) for (const s of item.supports) items[s].dependents.push(item.index);

  const grid = new Map();
  for (const item of items) if (item.y < 0.05) gridAdd(grid, item);

  return {
    arena: level.arena,
    items,
    grid,
    moving: new Set(), // alles, was gerade fällt (DROP/FALL)
    touched: new Set(), // Dinge mit Kippen/Zittern, die wieder zur Ruhe kommen müssen
    hole: { x: level.start?.x ?? 0, z: level.start?.z ?? 0, r: HOLE_RADII[0], size: 1, progress: 0 },
    time: level.time,
    timeTotal: level.time,
    total: items.length,
    eaten: 0,
    score: 0,
    finished: false,
    cleared: false,
    events: [],
  };
}

function cellKey(x, z) {
  return Math.floor(x / GRID) * 4096 + Math.floor(z / GRID);
}
function gridAdd(grid, item) {
  const key = cellKey(item.x, item.z);
  item.cell = key;
  let cell = grid.get(key);
  if (!cell) grid.set(key, (cell = new Set()));
  cell.add(item.index);
}
function gridRemove(grid, item) {
  if (item.cell === undefined) return;
  grid.get(item.cell)?.delete(item.index);
  item.cell = undefined;
}

/** Alle ruhenden Boden-Dinge im Umkreis. */
function nearby(sim, x, z, reach, out) {
  out.length = 0;
  const lo = Math.floor((x - reach) / GRID);
  const hi = Math.floor((x + reach) / GRID);
  const loZ = Math.floor((z - reach) / GRID);
  const hiZ = Math.floor((z + reach) / GRID);
  for (let cx = lo; cx <= hi; cx++) {
    for (let cz = loZ; cz <= hiZ; cz++) {
      const cell = sim.grid.get(cx * 4096 + cz);
      if (cell) for (const index of cell) out.push(sim.items[index]);
    }
  }
  return out;
}

const scratch = [];

/**
 * Ein Zeitschritt. `move` ist die gewünschte Verschiebung des Lochs in Welt-Einheiten.
 * Ereignisse landen in `sim.events` (vom Aufrufer zu leeren): eat, grow, tooBig, land, finish.
 */
export function step(sim, dt, move) {
  const hole = sim.hole;
  if (sim.finished) {
    // Nach dem Ende bewegt sich das Loch nicht mehr — aber was schon fällt, fällt zu Ende.
    advanceMoving(sim, dt);
    return;
  }

  // 1. Loch bewegen, in der Arena halten, Radius weich zum Ziel führen.
  const limit = sim.arena - hole.r * 0.6;
  hole.x = Math.max(-limit, Math.min(limit, hole.x + (move?.x ?? 0)));
  hole.z = Math.max(-limit, Math.min(limit, hole.z + (move?.z ?? 0)));
  const target = HOLE_RADII[hole.size - 1];
  hole.r += (target - hole.r) * Math.min(1, dt * 5);

  // 2. Ruhende Dinge am Loch: hineinfallen, kippen oder zittern.
  const R = hole.r;
  const seen = nearby(sim, hole.x, hole.z, R + 3.2, scratch);
  for (const item of seen) {
    if (item.state !== REST) continue;
    const dx = hole.x - item.x;
    const dz = hole.z - item.z;
    const d = Math.hypot(dx, dz);
    const ok = fits(item.r, R);
    const inside = R - 0.25 * item.r;
    if (ok && d < inside) {
      startFall(sim, item);
    } else if (ok && d < R + 0.55 * item.r) {
      // Über der Kante: kippt zum Loch und rutscht hinein.
      const t = 1 - (d - inside) / (0.8 * item.r);
      item.tilt = Math.max(0, Math.min(1, t));
      item.tiltDir = Math.atan2(dz, dx);
      const pull = 1.8 * item.tilt * dt;
      if (d > 1e-4) {
        gridRemove(sim.grid, item);
        item.x += (dx / d) * pull;
        item.z += (dz / d) * pull;
        gridAdd(sim.grid, item);
      }
      sim.touched.add(item.index);
    } else if (!ok && d < R + item.r * 0.3) {
      item.wobble = 1;
      sim.touched.add(item.index);
      sim.events.push({ type: 'tooBig', index: item.index });
    }
  }

  // 3. Kippen und Zittern klingen ab, sobald das Loch weg ist.
  for (const index of sim.touched) {
    const item = sim.items[index];
    if (item.state !== REST) {
      sim.touched.delete(index);
      continue;
    }
    const d = Math.hypot(hole.x - item.x, hole.z - item.z);
    if (d >= R + 0.55 * item.r) item.tilt = Math.max(0, item.tilt - dt * 4);
    item.wobble = Math.max(0, item.wobble - dt * 3);
    if (item.tilt === 0 && item.wobble === 0) sim.touched.delete(index);
  }

  // 4. Was fällt, fällt weiter.
  advanceMoving(sim, dt);

  // 5. Zeit und Ende.
  sim.time -= dt;
  if (sim.eaten >= sim.total) finish(sim, true);
  else if (sim.time <= 0) {
    sim.time = 0;
    finish(sim, false);
  }
}

function advanceMoving(sim, dt) {
  const hole = sim.hole;
  const R = hole.r;
  for (const index of sim.moving) {
    const item = sim.items[index];
    item.vy -= GRAVITY * dt;
    item.y += item.vy * dt;
    if (item.state === FALL) {
      // Zur Lochmitte ziehen, taumeln, in der Tiefe verschwinden.
      const k = Math.min(1, dt * 5);
      item.x += (hole.x - item.x) * k;
      item.z += (hole.z - item.z) * k;
      item.spin += dt * 7;
      if (item.y < -(2.6 * R + item.h + 1)) {
        item.state = GONE;
        sim.moving.delete(index);
      }
    } else if (item.state === DROP) {
      const floor = landingHeight(sim, item);
      if (item.y <= floor) {
        const d = Math.hypot(hole.x - item.x, hole.z - item.z);
        if (floor === 0 && fits(item.r, R) && d < R - 0.25 * item.r) {
          // Direkt über dem Loch: ohne Halt weiter hinein — so sinkt ein ganzer Turm ein.
          item.y = 0;
          startFall(sim, item, item.vy);
        } else {
          item.y = floor;
          item.vy = 0;
          item.state = REST;
          sim.moving.delete(index);
          if (floor === 0) {
            item.supports = [];
            gridAdd(sim.grid, item);
          }
          sim.events.push({ type: 'land', index });
        }
      }
    }
  }
}

/** Höhe, auf der ein fallendes Ding landet: auf einem ruhenden Träger direkt darunter, sonst am Boden. */
function landingHeight(sim, item) {
  let floor = 0;
  for (const s of item.supports) {
    const below = sim.items[s];
    if (below.state !== REST) continue;
    if (Math.hypot(below.x - item.x, below.z - item.z) > below.r * 0.5) continue;
    floor = Math.max(floor, below.y + below.h);
  }
  return floor;
}

function startFall(sim, item, vy = 0) {
  item.state = FALL;
  item.vy = Math.min(vy, -2);
  item.tilt = 0;
  item.wobble = 0;
  gridRemove(sim.grid, item);
  sim.moving.add(item.index);
  sim.touched.delete(item.index);

  sim.eaten += 1;
  sim.score += item.mass * 10;
  sim.events.push({ type: 'eat', index: item.index, mass: item.mass });
  grow(sim, item.mass);
  releaseDependents(sim, item);
}

/** Was auf einem Ding stand, verliert seinen Halt — und alles darüber mit. */
function releaseDependents(sim, item) {
  const queue = [...item.dependents];
  while (queue.length) {
    const upper = sim.items[queue.pop()];
    if (upper.state !== REST) continue;
    const resting = upper.supports.filter((s) => sim.items[s].state === REST).length;
    const needed = Math.ceil(upper.supports.length * 0.6);
    if (resting >= needed) continue;
    upper.state = DROP;
    upper.vy = 0;
    sim.moving.add(upper.index);
    for (const d of upper.dependents) queue.push(d);
  }
}

function grow(sim, mass) {
  const hole = sim.hole;
  if (hole.size >= MAX_SIZE) return;
  hole.progress += mass;
  while (hole.size < MAX_SIZE && hole.progress >= growthNeed(hole.size)) {
    hole.progress -= growthNeed(hole.size);
    hole.size += 1;
    sim.events.push({ type: 'grow', size: hole.size });
  }
  if (hole.size >= MAX_SIZE) hole.progress = 0;
}

function finish(sim, cleared) {
  if (sim.finished) return;
  sim.finished = true;
  sim.cleared = cleared;
  // Restzeit zählt nur, wenn alles weg ist.
  if (cleared) sim.score += Math.ceil(sim.time) * 5;
  sim.events.push({ type: 'finish', cleared });
}

/** Sterne nach verschlucktem Anteil: ab der Hälfte einer, ab 80 % zwei, alles = drei. */
export function starsFor(eaten, total) {
  if (total === 0) return 0;
  const share = eaten / total;
  if (share >= 1) return 3;
  if (share >= 0.8) return 2;
  if (share >= 0.5) return 1;
  return 0;
}

/**
 * Lässt sich das Level überhaupt leeren? Frisst gierig alles, was gerade passt, und prüft,
 * ob das Loch damit groß genug für den Rest wird. Gibt die Zahl der unerreichbaren Dinge zurück.
 */
export function unreachableCount(items) {
  let size = 1;
  let progress = 0;
  let left = items.map((i) => ({ r: i.r, mass: i.mass }));
  for (;;) {
    const radius = HOLE_RADII[size - 1];
    const edible = left.filter((i) => fits(i.r, radius));
    if (edible.length === 0) return left.length;
    left = left.filter((i) => !fits(i.r, radius));
    for (const i of edible) {
      progress += i.mass;
      while (size < MAX_SIZE && progress >= growthNeed(size)) {
        progress -= growthNeed(size);
        size += 1;
      }
    }
    if (left.length === 0) return 0;
  }
}
