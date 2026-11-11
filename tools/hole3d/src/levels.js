// Level aus Bausteinen: Reihen, Ringe, Spiralen, Türme, Pyramiden.
import { CLASSES } from './sim.js';
import heights from './foods.json' with { type: 'json' };

/** Größenklasse je Modell. Stapelbar ist, was oben flach genug ist. */
export const TYPES = {
  cherries: 'tiny', strawberry: 'tiny', cookie: 'tiny', 'cookie-chocolate': 'tiny',
  'maki-salmon': 'tiny', 'sushi-salmon': 'tiny', egg: 'tiny', grapes: 'tiny',
  donut: 'small', 'donut-chocolate': 'small', 'donut-sprinkles': 'small', apple: 'small',
  orange: 'small', lemon: 'small', cupcake: 'small', muffin: 'small', 'soda-can': 'small',
  croissant: 'small', 'ice-cream': 'small', lollypop: 'small', popsicle: 'small', tomato: 'small',
  burger: 'medium', 'burger-cheese-double': 'medium', 'hot-dog': 'medium', fries: 'medium',
  pineapple: 'medium', 'soda-bottle': 'medium', cake: 'medium', pancakes: 'medium',
  pudding: 'medium', sandwich: 'medium', taco: 'medium', sundae: 'medium', waffle: 'medium',
  watermelon: 'large', pumpkin: 'large', pizza: 'large', 'cake-birthday': 'large', pie: 'large',
  turkey: 'large', 'loaf-round': 'large',
  barrel: 'huge',
};

/** Höchstes Verhältnis von Höhe zu Standfläche, mit dem ein Ding gezeigt wird. */
const MAX_TALL = 1.7;

/** Tischdecken: zwei Karofarben und die Tischfläche drumherum. */
export const CLOTHS = {
  wiese: { a: '#9ccc3d', b: '#8fc034', table: '#5e8f2b' },
  himbeer: { a: '#f29bb8', b: '#ec86a8', table: '#b9577a' },
  himmel: { a: '#8ecdf2', b: '#7cc0ea', table: '#4b8fb8' },
  vanille: { a: '#f6dd8a', b: '#f0d172', table: '#c39a3c' },
  minze: { a: '#8fe0c4', b: '#7dd5b6', table: '#3f9f82' },
  flieder: { a: '#c5b0f3', b: '#b79fee', table: '#7d63c2' },
};

/** Sammelt die Dinge eines Levels; jede Methode gibt die Indizes der neuen Dinge zurück. */
export class Builder {
  constructor() {
    this.items = [];
  }

  /** Ein Ding. `scale` vergrößert über die Klasse hinaus (Riesen-Donut), `cls` überschreibt sie. */
  add(type, x, z, { y = 0, supports = [], cls, scale = 1, yaw } = {}) {
    const klass = CLASSES[cls ?? TYPES[type]];
    if (!klass) throw new Error(`Unbekanntes Modell: ${type}`);
    const r = klass.r * scale;
    const index = this.items.length;
    // Hohe, schmale Dinge (Flasche, Eis am Stiel) würden sonst zu Türmen: Sie werden so
    // weit verkleinert, dass sie höchstens 1,7-mal so hoch sind wie ihre Standfläche breit.
    const vis = 2 * r * Math.min(1, MAX_TALL / heights[type].height);
    this.items.push({
      type,
      x,
      z,
      y,
      r,
      vis,
      h: heights[type].height * vis,
      mass: Math.max(1, Math.round(klass.mass * scale * scale)),
      // Ruhige, feste Drehung je Ding — kein Zufall, damit Level immer gleich aussehen.
      yaw: yaw ?? ((index * 0.61803) % 1) * Math.PI * 2,
      supports,
    });
    return index;
  }

  /** Gerade Reihe von (x0,z0) in Schritten (dx,dz). */
  row(type, n, x0, z0, dx, dz, opts) {
    return Array.from({ length: n }, (_, i) => this.add(type, x0 + dx * i, z0 + dz * i, opts));
  }

  /** Rechteckiges Feld um die Mitte (cx,cz). */
  grid(type, cols, rows, cx, cz, gap, opts) {
    const out = [];
    for (let j = 0; j < rows; j++) {
      for (let i = 0; i < cols; i++) {
        out.push(this.add(type, cx + (i - (cols - 1) / 2) * gap, cz + (j - (rows - 1) / 2) * gap, opts));
      }
    }
    return out;
  }

  /** Kreis aus n Dingen. */
  ring(type, n, cx, cz, radius, opts = {}) {
    const phase = opts.phase ?? 0;
    return Array.from({ length: n }, (_, i) => {
      const a = phase + (i / n) * Math.PI * 2;
      return this.add(type, cx + Math.cos(a) * radius, cz + Math.sin(a) * radius, { ...opts, yaw: -a });
    });
  }

  /** Spirale von innen nach außen. */
  spiral(type, n, cx, cz, r0, r1, turns, opts = {}) {
    const phase = opts.phase ?? 0;
    return Array.from({ length: n }, (_, i) => {
      const t = i / (n - 1);
      const a = phase + t * turns * Math.PI * 2;
      const r = r0 + (r1 - r0) * t;
      return this.add(type, cx + Math.cos(a) * r, cz + Math.sin(a) * r, { ...opts, yaw: -a });
    });
  }

  /** Turm: n Dinge übereinander, jedes ruht auf dem darunter. */
  tower(type, n, x, z, opts = {}) {
    const out = [];
    let y = 0;
    let below = null;
    for (let i = 0; i < n; i++) {
      const index = this.add(type, x, z, { ...opts, y, supports: below === null ? [] : [below], yaw: i * 0.35 });
      out.push(index);
      y += this.items[index].h * 0.97;
      below = index;
    }
    return out;
  }

  /** Quadratische Pyramide: jede Lage eins kleiner, jedes Ding ruht auf den vieren darunter. */
  pyramid(type, base, cx, cz, opts = {}) {
    const probe = CLASSES[opts.cls ?? TYPES[type]].r * (opts.scale ?? 1);
    const gap = probe * 2.02;
    const rise = heights[type].height * 2 * probe * Math.min(1, MAX_TALL / heights[type].height) * 0.92;
    const out = [];
    let lower = null;
    for (let layer = 0; layer < base; layer++) {
      const n = base - layer;
      const current = [];
      for (let j = 0; j < n; j++) {
        for (let i = 0; i < n; i++) {
          const supports = lower
            ? [lower[j * (n + 1) + i], lower[j * (n + 1) + i + 1], lower[(j + 1) * (n + 1) + i], lower[(j + 1) * (n + 1) + i + 1]]
            : [];
          current.push(
            this.add(type, cx + (i - (n - 1) / 2) * gap, cz + (j - (n - 1) / 2) * gap, { ...opts, y: layer * rise, supports, yaw: 0 }),
          );
        }
      }
      out.push(...current);
      lower = current;
    }
    return out;
  }
}

const level = (name, time, cloth, arena, build) => ({ name, time, cloth, arena, build });

/**
 * Die Level-Reise. Jedes Level beginnt mit genug Kleinem zum Wachsen und endet mit einem
 * dicken Brocken in der Mitte — geprüft in levels.test.js (lösbar, nichts überlappt den Start).
 */
export const LEVELS = [
  level('Erste Happen', 60, 'wiese', 13, (b) => {
    b.ring('cherries', 12, 0, -5, 3);
    b.ring('donut', 10, 0, -5, 5.2);
    b.row('strawberry', 7, -6, 4, 2, 0);
    b.row('cookie', 7, -6, 6, 2, 0);
    b.add('burger', 0, -5);
  }),
  level('Donut-Allee', 75, 'himbeer', 14, (b) => {
    b.row('donut-sprinkles', 9, -8, -7, 2, 0);
    b.row('donut-chocolate', 9, -8, -4.6, 2, 0);
    b.row('donut', 9, -8, -2.2, 2, 0);
    b.grid('cookie-chocolate', 8, 2, 0, 4, 1.4);
    b.tower('donut-chocolate', 6, -6, 8);
    b.tower('donut-sprinkles', 6, 6, 8);
    b.add('cake', 0, 8);
  }),
  level('Obstkorb', 80, 'vanille', 15, (b) => {
    b.spiral('grapes', 26, 0, 0, 2.2, 7, 2.2);
    b.ring('apple', 12, 0, 0, 9);
    b.ring('orange', 12, 0, 0, 11, { phase: 0.26 });
    b.row('lemon', 3, -12, 12.6, 2, 0);
    b.row('lemon', 3, 8, 12.6, 2, 0);
    b.add('pineapple', -12.4, -4);
    b.add('pineapple', 12.4, -4);
    b.add('watermelon', 0, -12);
  }),
  level('Dosenpyramide', 85, 'himmel', 15, (b) => {
    b.pyramid('soda-can', 4, 0, -4);
    b.ring('egg', 16, 0, -4, 5.4);
    b.grid('maki-salmon', 6, 3, -8, 7, 1.3);
    b.grid('sushi-salmon', 6, 3, 8, 7, 1.3);
    b.row('soda-bottle', 5, -4.4, -12.6, 2.2, 0);
    b.add('pizza', 0, 5.5);
  }),
  level('Imbissbude', 90, 'minze', 16, (b) => {
    b.grid('fries', 4, 2, -8.5, -9, 2.3);
    b.grid('hot-dog', 4, 2, 8.5, -9, 2.3);
    b.ring('tomato', 14, 0, 0, 4.6);
    b.ring('cherries', 20, 0, 0, 6.6);
    b.tower('burger', 4, -9, 4);
    b.tower('burger-cheese-double', 4, 9, 4);
    b.grid('cookie', 9, 2, 0, 11, 1.3);
    b.add('turkey', 0, 0);
  }),
  level('Tortenschlacht', 95, 'flieder', 16, (b) => {
    b.pyramid('cupcake', 4, -8, -6);
    b.pyramid('muffin', 4, 8, -6);
    b.ring('strawberry', 22, 0, 4, 7.5);
    b.ring('cake', 8, 0, 4, 4.6);
    b.row('lollypop', 4, -10.6, 13.4, 2.4, 0);
    b.row('lollypop', 4, 3.4, 13.4, 2.4, 0);
    b.add('cake-birthday', 0, 4);
  }),
  level('Frühstück', 100, 'vanille', 17, (b) => {
    b.tower('pancakes', 5, -9, -9);
    b.tower('pancakes', 5, 9, -9);
    b.tower('waffle', 5, 0, -11);
    b.grid('egg', 8, 3, 0, -3, 1.25);
    b.ring('croissant', 12, 0, 6, 6);
    b.ring('orange', 8, 0, 6, 3.4);
    b.row('grapes', 6, -12, 13.5, 1.8, 0);
    b.row('grapes', 6, 3, 13.5, 1.8, 0);
    b.add('loaf-round', -11, 4);
    b.add('loaf-round', 11, 4);
    b.add('pie', 0, 6);
  }),
  level('Eisdiele', 100, 'himbeer', 17, (b) => {
    b.spiral('ice-cream', 22, 0, 0, 3, 9.5, 1.7);
    b.spiral('popsicle', 22, 0, 0, 3, 9.5, 1.7, { phase: Math.PI });
    b.ring('cherries', 24, 0, 0, 12);
    b.grid('sundae', 3, 1, 0, -14.6, 2.6);
    b.ring('pudding', 6, 0, 0, 13.6);
    b.add('cake-birthday', 0, 0, { scale: 1.25 });
  }),
  level('Kürbisfeld', 110, 'wiese', 18, (b) => {
    b.grid('tomato', 7, 4, -9, -12, 1.7);
    b.grid('apple', 7, 4, 9, -12, 1.7);
    b.ring('pumpkin', 5, 0, 2, 6.2);
    b.ring('strawberry', 26, 0, 2, 9.4);
    b.row('pineapple', 3, -13, 15, 2.6, 0);
    b.row('pineapple', 3, 7.8, 15, 2.6, 0);
    b.grid('cherries', 5, 2, 0, -12, 1.2);
    b.add('watermelon', 0, 2, { scale: 1.3 });
  }),
  level('Getränkelager', 110, 'himmel', 18, (b) => {
    b.pyramid('soda-can', 5, -8, -7);
    b.pyramid('soda-can', 5, 8, -7);
    b.grid('soda-bottle', 6, 2, 0, 4.4, 2.3);
    b.ring('lemon', 18, 0, 4.4, 8.6);
    b.grid('cookie', 6, 2, -9, 15.4, 1.3);
    b.grid('cookie', 6, 2, 9, 15.4, 1.3);
    b.add('barrel', -11, 9);
    b.add('barrel', 11, 9);
    b.add('barrel', 0, -15);
  }),
  level('Sushi-Garten', 115, 'minze', 19, (b) => {
    for (let k = 0; k < 4; k++) {
      const a = (k / 4) * Math.PI * 2 + Math.PI / 4;
      const x = Math.cos(a) * 10;
      const z = Math.sin(a) * 10;
      b.ring('maki-salmon', 10, x, z, 2.2);
      b.ring('sushi-salmon', 16, x, z, 3.8);
      b.add('taco', x, z);
    }
    b.spiral('egg', 30, 0, 0, 3, 5.8, 2.4);
    b.row('sandwich', 2, -7.8, 16.6, 2.6, 0);
    b.row('sandwich', 2, 5.2, 16.6, 2.6, 0);
    b.row('sandwich', 5, -5.2, -16.6, 2.6, 0);
    b.add('pizza', -16, 0);
    b.add('pizza', 16, 0);
    b.add('turkey', 0, 0, { scale: 1.2 });
  }),
  level('Wolkenkratzer', 120, 'flieder', 19, (b) => {
    for (let i = 0; i < 5; i++) {
      b.tower('donut', 8, -10 + i * 5, -10);
      b.tower('cookie-chocolate', 10, -10 + i * 5, -6.6);
    }
    b.tower('burger', 6, -7, 3);
    b.tower('burger', 6, 7, 3);
    b.tower('cake', 5, 0, 3);
    b.ring('cupcake', 16, 0, 3, 11);
    b.grid('strawberry', 6, 2, -8, 15.6, 1.3);
    b.grid('strawberry', 6, 2, 8, 15.6, 1.3);
    b.tower('pizza', 4, 0, 11);
  }),
  level('Picknick', 125, 'wiese', 20, (b) => {
    b.pyramid('apple', 4, -11, -11);
    b.pyramid('orange', 4, 11, -11);
    b.grid('sandwich', 4, 2, 0, -12, 2.6);
    b.spiral('grapes', 34, 0, 2, 2.4, 9, 2.6);
    b.ring('hot-dog', 8, 0, 2, 11.4);
    b.grid('cherries', 7, 2, -7, 16.4, 1.2);
    b.grid('cherries', 7, 2, 7, 16.4, 1.2);
    b.add('pie', -13, 8);
    b.add('pie', 13, 8);
    b.add('watermelon', 0, 2, { scale: 1.35 });
    b.add('barrel', -15, 16);
    b.add('barrel', 15, 16);
  }),
  level('Süßes Labyrinth', 130, 'himbeer', 21, (b) => {
    for (let k = 0; k < 4; k++) {
      const z = -15 + k * 10;
      b.row('donut-sprinkles', 13, k % 2 ? -8 : -16, z, 2, 0);
      if (k < 3) b.row('lollypop', 7, k % 2 ? -17 : 5, z + 5, 2, 0);
    }
    b.pyramid('cupcake', 4, 0, 0);
    b.ring('cookie', 16, 0, 0, 3.7);
    b.tower('pancakes', 7, -15, 3);
    b.tower('pancakes', 7, 15, -7);
    b.add('cake-birthday', -13, 18.2, { scale: 1.3 });
    b.add('cake-birthday', 13, 18.2, { scale: 1.3 });
  }),
  level('Das große Fressen', 150, 'vanille', 23, (b) => {
    b.spiral('cherries', 40, 0, 0, 3.4, 10.5, 3);
    b.ring('burger', 12, 0, 0, 13);
    b.ring('donut-chocolate', 30, 0, 0, 15.6);
    b.pyramid('soda-can', 5, -15, -15);
    b.pyramid('muffin', 5, 15, -15);
    b.pyramid('apple', 5, -15, 15);
    b.pyramid('cupcake', 5, 15, 15);
    b.tower('pizza', 5, 0, -19);
    b.tower('cake', 6, 0, 17.6);
    b.add('pumpkin', -19.5, 0);
    b.add('watermelon', 19.5, 0);
    b.add('barrel', 0, 0, { scale: 1.3 });
  }),
];

/** Baut ein Level der Reise (0-basiert) zu den Daten, die die Simulation braucht. */
export function buildLevel(index) {
  const def = LEVELS[index];
  const b = new Builder();
  def.build(b);
  return { name: def.name, time: def.time, cloth: def.cloth, arena: def.arena, start: startFor(def), items: b.items };
}

// Das Loch startet unten in der Mitte, wo kein Level etwas hinstellt.
function startFor(def) {
  return { x: 0, z: def.arena - 2.5 };
}

/** Kleiner, fester Zufall (mulberry32) — dieselbe Zahl ergibt dieselbe Zeitjagd-Karte. */
function rng(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const RUSH_PATCHES = [
  (b, x, z, pick) => b.ring(pick('tiny'), 10, x, z, 2.4),
  (b, x, z, pick) => {
    b.ring(pick('small'), 9, x, z, 3.2);
    b.add(pick('medium'), x, z);
  },
  (b, x, z, pick) => b.grid(pick('tiny'), 5, 4, x, z, 1.25),
  (b, x, z, pick) => b.grid(pick('small'), 3, 3, x, z, 1.9),
  (b, x, z, pick) => b.pyramid(pick('stack'), 3, x, z),
  (b, x, z, pick) => {
    b.tower(pick('stack'), 6, x - 1.6, z);
    b.tower(pick('stack'), 6, x + 1.6, z);
  },
  (b, x, z, pick) => {
    b.ring(pick('tiny'), 12, x, z, 3.4);
    b.add(pick('large'), x, z);
  },
  (b, x, z, pick) => b.spiral(pick('tiny'), 16, x, z, 0.8, 3.4, 1.6),
];

const POOLS = {
  tiny: ['cherries', 'strawberry', 'cookie', 'cookie-chocolate', 'maki-salmon', 'sushi-salmon', 'egg', 'grapes'],
  small: ['donut', 'donut-chocolate', 'donut-sprinkles', 'apple', 'orange', 'lemon', 'cupcake', 'muffin', 'croissant', 'tomato'],
  medium: ['burger', 'hot-dog', 'fries', 'pineapple', 'cake', 'pudding', 'sandwich', 'taco', 'sundae'],
  large: ['watermelon', 'pumpkin', 'pizza', 'cake-birthday', 'pie', 'turkey'],
  stack: ['donut', 'donut-chocolate', 'donut-sprinkles', 'soda-can', 'cookie', 'muffin'],
};

/** Zeitjagd: große Karte aus zufällig verteilten Feldern, zwei Minuten, es zählen die Punkte. */
export function buildRush(seed) {
  const random = rng(seed);
  const pick = (pool) => POOLS[pool][Math.floor(random() * POOLS[pool].length)];
  const b = new Builder();
  const arena = 24;
  const cell = 8.6;
  for (let gx = -2; gx <= 2; gx++) {
    for (let gz = -2; gz <= 2; gz++) {
      if (gx === 0 && gz === 2) continue; // Startplatz des Lochs
      if (Math.abs(gx) === 2 && gz === -2) {
        b.add('barrel', gx * cell, gz * cell); // die dicken Brocken in den hinteren Ecken
        continue;
      }
      const patch = RUSH_PATCHES[Math.floor(random() * RUSH_PATCHES.length)];
      patch(b, gx * cell + (random() - 0.5) * 0.4, gz * cell + (random() - 0.5) * 0.4, pick);
    }
  }
  const cloths = Object.keys(CLOTHS);
  return {
    name: 'Zeitjagd',
    time: 120,
    cloth: cloths[Math.floor(random() * cloths.length)],
    arena,
    start: { x: 0, z: arena - 4 },
    items: b.items,
  };
}
