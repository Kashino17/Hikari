// Ablauf des Spiels: Menü → Level → Ergebnis. Verbindet Simulation, Bild, Ton und die App.
import { LEVELS, buildLevel, buildRush } from './levels.js';
import { MAX_SIZE, createSim, growthNeed, starsFor, step } from './sim.js';
import { Stage, loadFoods } from './stage.js';

const $ = (id) => document.getElementById(id);
const native = window.HoleNative ?? null;
const fmt = (n) => n.toLocaleString('de-DE');

// ── Gespeicherter Stand ──────────────────────────────────────────────────────

function loadSave() {
  let raw = null;
  try {
    raw = native ? native.load() : localStorage.getItem('hole3d');
  } catch {
    /* kein Speicher (privates Fenster) — dann eben ohne */
  }
  let data = {};
  try {
    data = raw ? JSON.parse(raw) : {};
  } catch {
    data = {};
  }
  return {
    stars: Array.from({ length: LEVELS.length }, (_, i) => Number(data.stars?.[i]) || 0),
    best: Number(data.best) || 0,
    sound: data.sound !== false,
    haptics: data.haptics !== false,
  };
}
function persist() {
  const json = JSON.stringify(save);
  try {
    if (native) native.save(json);
    else localStorage.setItem('hole3d', json);
  } catch {
    /* siehe oben */
  }
}
const save = loadSave();

/** Das erste Level ohne Stern ist dran; alles davor ist frei. */
function currentLevel() {
  const open = save.stars.findIndex((s) => s === 0);
  return open === -1 ? LEVELS.length - 1 : open;
}

// ── Ton und Vibration ────────────────────────────────────────────────────────

let audio = null;
function tone(freq, length, gain = 0.1, type = 'sine', delay = 0) {
  if (!save.sound) return;
  try {
    audio ??= new (window.AudioContext || window.webkitAudioContext)();
    if (audio.state === 'suspended') audio.resume();
    const t = audio.currentTime + delay;
    const osc = audio.createOscillator();
    const amp = audio.createGain();
    osc.type = type;
    osc.frequency.setValueAtTime(freq, t);
    amp.gain.setValueAtTime(gain, t);
    amp.gain.exponentialRampToValueAtTime(0.0008, t + length);
    osc.connect(amp).connect(audio.destination);
    osc.start(t);
    osc.stop(t + length + 0.02);
  } catch {
    /* ohne Ton spielbar */
  }
}
let combo = 0;
let comboAt = 0;
let lastBuzz = 0;
function buzz(kind) {
  if (!save.haptics || !native) return;
  const now = performance.now();
  if (kind === 'tick' && now - lastBuzz < 45) return;
  lastBuzz = now;
  native.haptic(kind);
}

// ── Zustand ──────────────────────────────────────────────────────────────────

let stage = null;
let sim = null;
let run = null; // { kind: 'level' | 'rush', index, name }
let mode = 'loading'; // loading | menu | play | pause | result
let preview = null;
const pending = { x: 0, y: 0 };

function setMode(next) {
  mode = next;
  $('menu').hidden = next !== 'menu';
  $('hud').hidden = !(next === 'play' || next === 'pause');
  $('pausesheet').hidden = next !== 'pause';
  $('result').hidden = next !== 'result';
}

function starIcons(count, extra = '') {
  return [0, 1, 2].map((i) => `<svg class="fill ${i < count ? 'on' : ''} ${extra}"><use href="#i-star"/></svg>`).join('');
}

function showMenu() {
  const now = currentLevel();
  const def = LEVELS[now];
  const done = save.stars.every((s) => s > 0);
  $('next').querySelector('.k').textContent = done ? 'Alles geschafft' : save.stars.some((s) => s > 0) ? 'Weiter' : 'Los geht’s';
  $('next').querySelector('.t').textContent = `Level ${now + 1}: ${def.name}`;
  $('next').querySelector('.s').textContent = `${Math.floor(def.time / 60)}:${String(def.time % 60).padStart(2, '0')} Minuten`;
  $('next').onclick = () => startLevel(now);

  $('levels').innerHTML = LEVELS.map((_, i) => {
    const locked = i > now;
    const cls = `lvl${i === now ? ' current' : ''}${locked ? ' locked' : ''}`;
    const inner = locked ? '<svg><use href="#i-lock"/></svg>' : `${i + 1}<div class="stars">${starIcons(save.stars[i])}</div>`;
    return `<button class="${cls}" data-level="${i}" ${locked ? 'disabled' : ''} aria-label="Level ${i + 1}">${inner}</button>`;
  }).join('');
  $('best').textContent = fmt(save.best);
  $('sound').classList.toggle('off', !save.sound);
  $('buzz').classList.toggle('off', !save.haptics);

  // Hinter dem Menü dreht sich das nächste Level.
  preview = createSim(buildLevel(now));
  stage.setLevel(preview, def.cloth);
  setMode('menu');
}

function begin(level, info) {
  sim = createSim(level);
  run = info;
  stage.setLevel(sim, level.cloth);
  pending.x = pending.y = 0;
  combo = 0;
  lastHint = -1e9;
  $('pausename').textContent = info.kind === 'rush' ? 'Zeitjagd' : `Level ${info.index + 1}: ${info.name}`;
  updateHud(true);
  setMode('play');
}
function startLevel(index) {
  begin(buildLevel(index), { kind: 'level', index, name: LEVELS[index].name });
}
function startRush() {
  begin(buildRush((Math.random() * 2 ** 31) | 0), { kind: 'rush', name: 'Zeitjagd' });
}
function restart() {
  if (run.kind === 'rush') startRush();
  else startLevel(run.index);
}

// ── Anzeige ──────────────────────────────────────────────────────────────────

let shownSecond = -1;
let shownEaten = -1;
function updateHud(force) {
  const second = Math.ceil(sim.time);
  if (force || second !== shownSecond) {
    shownSecond = second;
    $('time').querySelector('span').textContent = `${Math.floor(second / 60)}:${String(second % 60).padStart(2, '0')}`;
    $('time').classList.toggle('low', second <= 10);
  }
  if (force || sim.eaten !== shownEaten) {
    shownEaten = sim.eaten;
    $('count').firstElementChild.textContent = run.kind === 'rush' ? fmt(sim.score) : sim.eaten;
    $('count').lastElementChild.textContent = run.kind === 'rush' ? 'Punkte' : `von ${sim.total}`;
    const hole = sim.hole;
    const full = hole.size >= MAX_SIZE;
    $('size').querySelector('b').textContent = full ? 'Größte Größe' : `Größe ${hole.size}`;
    $('size').querySelector('i').style.width = `${full ? 100 : Math.round((hole.progress / growthNeed(hole.size)) * 100)}%`;
  }
}

let lastHint = -1e9;
let hintTimer = 0;
function hint(text) {
  const now = performance.now();
  if (now - lastHint < 5000) return;
  lastHint = now;
  $('hint').textContent = text;
  $('hint').classList.add('show');
  clearTimeout(hintTimer);
  hintTimer = setTimeout(() => $('hint').classList.remove('show'), 1800);
}

function showGrow(size) {
  const el = $('grow');
  el.textContent = `Größe ${size}`;
  el.classList.remove('show');
  void el.offsetWidth; // Animation neu starten
  el.classList.add('show');
  $('size').classList.remove('grew');
  void $('size').offsetWidth;
  $('size').classList.add('grew');
}

function handleEvents() {
  for (const event of sim.events) {
    if (event.type === 'eat') {
      const now = performance.now();
      combo = now - comboAt < 420 ? Math.min(combo + 1, 16) : 0;
      comboAt = now;
      tone(330 * Math.pow(2, combo / 12), 0.09, 0.09 + Math.min(event.mass, 12) * 0.006);
      buzz('tick');
    } else if (event.type === 'grow') {
      stage.pulseRim();
      showGrow(event.size);
      tone(392, 0.16, 0.12, 'triangle');
      tone(523, 0.16, 0.12, 'triangle', 0.09);
      tone(659, 0.26, 0.12, 'triangle', 0.18);
      buzz('grow');
    } else if (event.type === 'tooBig') {
      hint('Noch zu groß. Friss erst Kleineres.');
    } else if (event.type === 'land') {
      tone(140, 0.07, 0.05, 'triangle');
    } else if (event.type === 'finish') {
      setTimeout(showResult, event.cleared ? 900 : 500);
    }
  }
  sim.events.length = 0;
}

function showResult() {
  if (mode !== 'play' && mode !== 'pause') return;
  const rush = run.kind === 'rush';
  const stars = starsFor(sim.eaten, sim.total);
  let record = false;
  if (rush) {
    record = sim.score > save.best;
    if (record) save.best = sim.score;
  } else if (stars > save.stars[run.index]) {
    save.stars[run.index] = stars;
  }
  persist();

  const passed = !rush && stars > 0;
  const hasNext = passed && run.index + 1 < LEVELS.length;
  $('r-title').textContent = rush ? (record ? 'Neuer Rekord' : 'Zeit abgelaufen') : sim.cleared ? 'Alles verschluckt' : passed ? 'Level geschafft' : 'Zeit abgelaufen';
  $('r-sub').textContent = rush
    ? 'Zeitjagd'
    : passed
      ? `Level ${run.index + 1}: ${run.name}`
      : 'Mindestens die Hälfte muss weg, dann geht es weiter.';
  $('r-stars').hidden = rush;
  $('r-stars').innerHTML = starIcons(stars);
  $('r-eaten').textContent = `${sim.eaten} von ${sim.total}`;
  $('r-score').textContent = fmt(sim.score);
  $('r-scorelabel').textContent = rush ? (record ? 'Punkte, neuer Rekord' : `Punkte, Rekord ${fmt(save.best)}`) : 'Punkte';
  $('r-scorebox').classList.toggle('rec', record);
  $('r-next').hidden = !hasNext;
  $('r-again').className = hasNext ? 'btn quiet' : 'btn primary';
  setMode('result');

  if (rush ? record : passed) {
    [523, 659, 784, 1047].forEach((f, i) => tone(f, 0.22, 0.11, 'triangle', i * 0.11));
    buzz('grow');
  } else {
    tone(220, 0.3, 0.1, 'triangle');
    tone(175, 0.4, 0.1, 'triangle', 0.16);
  }
}

// ── Steuerung ────────────────────────────────────────────────────────────────

function wire(canvas) {
  let last = null;
  canvas.addEventListener('pointerdown', (e) => {
    last = { id: e.pointerId, x: e.clientX, y: e.clientY };
    canvas.setPointerCapture?.(e.pointerId);
  });
  canvas.addEventListener('pointermove', (e) => {
    if (!last || e.pointerId !== last.id) return;
    pending.x += e.clientX - last.x;
    pending.y += e.clientY - last.y;
    last.x = e.clientX;
    last.y = e.clientY;
  });
  const end = (e) => {
    if (last && e.pointerId === last.id) last = null;
  };
  canvas.addEventListener('pointerup', end);
  canvas.addEventListener('pointercancel', end);

  $('pause').onclick = () => mode === 'play' && setMode('pause');
  $('resume').onclick = () => setMode('play');
  $('restart').onclick = restart;
  $('quit').onclick = showMenu;
  $('r-next').onclick = () => startLevel(run.index + 1);
  $('r-again').onclick = restart;
  $('r-menu').onclick = showMenu;
  $('rush').onclick = startRush;
  $('exit').onclick = () => native?.exit();
  $('levels').onclick = (e) => {
    const button = e.target.closest('[data-level]');
    if (button && !button.disabled) startLevel(Number(button.dataset.level));
  };
  $('sound').onclick = () => {
    save.sound = !save.sound;
    persist();
    $('sound').classList.toggle('off', !save.sound);
    tone(523, 0.12);
  };
  $('buzz').onclick = () => {
    save.haptics = !save.haptics;
    persist();
    $('buzz').classList.toggle('off', !save.haptics);
    buzz('grow');
  };

  document.addEventListener('visibilitychange', () => {
    if (document.hidden && mode === 'play') setMode('pause');
  });
  window.addEventListener('resize', () => stage.resize(window.innerWidth, window.innerHeight));
}

/** Zurück-Taste des Handys: Spiel → Pause → Übersicht → raus. */
window.hikariBack = () => {
  if (mode === 'play') setMode('pause');
  else if (mode === 'pause' || mode === 'result') showMenu();
  else native?.exit();
};

// ── Schleife ─────────────────────────────────────────────────────────────────

let before = 0;
function frame(now) {
  requestAnimationFrame(frame);
  const dt = Math.min(0.05, (now - before) / 1000 || 0);
  before = now;
  const time = now / 1000;

  if (mode === 'play') {
    const per = stage.worldPerPixel() * 1.12;
    const move = { x: pending.x * per, z: pending.y * per * stage.depthStretch };
    pending.x = pending.y = 0;
    // Zwei halbe Schritte: schnelle Wischer überspringen so keine kleinen Dinge.
    step(sim, dt / 2, { x: move.x / 2, z: move.z / 2 });
    step(sim, dt / 2, { x: move.x / 2, z: move.z / 2 });
    handleEvents();
    updateHud(false);
    stage.sync(sim, dt, time);
  } else if (mode === 'result') {
    step(sim, dt, null); // was fällt, fällt zu Ende
    sim.events.length = 0;
    stage.sync(sim, dt, time);
  } else if (mode === 'menu' && preview) {
    stage.idle(preview, time);
  }
  if (mode !== 'loading') stage.render();
}

async function start() {
  const canvas = $('stage');
  const foods = await loadFoods('foods.glb');
  stage = new Stage(canvas, foods);
  stage.resize(window.innerWidth, window.innerHeight);
  wire(canvas);
  showMenu();
  requestAnimationFrame(frame);
}

start().catch((error) => {
  document.body.insertAdjacentHTML(
    'beforeend',
    `<div style="position:fixed;inset:0;display:grid;place-items:center;padding:24px;text-align:center;color:#fff">Das Spiel konnte nicht geladen werden.<br><small style="opacity:.6">${String(error.message || error)}</small></div>`,
  );
  console.error(error);
});

// Für Tests von außen (Entwicklung): Zustand lesen und das Loch führen.
window.__hole = { get sim() { return sim; }, get mode() { return mode; }, startLevel, startRush, pending };
