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
    // Die Reise ist endlos: ein Eintrag je gespieltem Level, in Reihenfolge.
    stars: Array.isArray(data.stars) ? data.stars.map((s) => Number(s) || 0) : [],
    best: Number(data.best) || 0,
    sound: data.sound !== false,
    haptics: data.haptics !== false,
    // Tempo der Steuerung: 0 ruhig, 1 mittel, 2 flink.
    pace: [0, 1, 2].includes(data.pace) ? data.pace : 1,
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

const starsOf = (index) => save.stars[index] ?? 0;

/** Das erste Level ohne Stern ist dran; alles davor ist frei. Nach dem letzten kommt immer ein neues. */
function currentLevel() {
  const open = save.stars.findIndex((s) => s === 0);
  return open === -1 ? save.stars.length : open;
}

const levelTitle = (index) => (index < LEVELS.length ? `Level ${index + 1}: ${LEVELS[index].name}` : `Level ${index + 1}`);

/** Wie viel Fingerweg zu Lochweg wird — und wie zügig das Loch seinem Ziel folgt. */
const PACES = [
  { gain: 0.58, follow: 11 },
  { gain: 0.76, follow: 13 },
  { gain: 0.98, follow: 16 },
];

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
const aim = { x: 0, z: 0 }; // Zielpunkt des Fingers; das Loch gleitet weich dorthin
let page = 0; // Seite der Level-Übersicht (15 je Seite)
const PER_PAGE = 15;

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

function renderLevels() {
  const now = currentLevel();
  const first = page * PER_PAGE;
  $('levels').innerHTML = Array.from({ length: PER_PAGE }, (_, k) => {
    const i = first + k;
    const locked = i > now;
    const cls = `lvl${i === now ? ' current' : ''}${locked ? ' locked' : ''}`;
    const inner = locked ? '<svg><use href="#i-lock"/></svg>' : `${i + 1}<div class="stars">${starIcons(starsOf(i))}</div>`;
    return `<button class="${cls}" data-level="${i}" ${locked ? 'disabled' : ''} aria-label="Level ${i + 1}">${inner}</button>`;
  }).join('');
  $('range').textContent = `Level ${first + 1} bis ${first + PER_PAGE}`;
  $('prev').disabled = page === 0;
  $('later').disabled = first + PER_PAGE > now; // weiter blättern nur bis zur Seite mit dem offenen Level
}

function renderPace() {
  for (const button of document.querySelectorAll('[data-pace]')) {
    button.classList.toggle('on', Number(button.dataset.pace) === save.pace);
  }
}

function showMenu() {
  const now = currentLevel();
  const level = buildLevel(now);
  const bombs = level.items.filter((i) => i.bomb).length;
  $('next').querySelector('.k').textContent = save.stars.some((s) => s > 0) ? 'Weiter' : 'Los geht’s';
  $('next').querySelector('.t').textContent = levelTitle(now);
  const minutes = `${Math.floor(level.time / 60)}:${String(level.time % 60).padStart(2, '0')} Minuten`;
  $('next').querySelector('.s').textContent = bombs === 0 ? minutes : `${minutes}, ${bombs === 1 ? '1 Bombe' : `${bombs} Bomben`}`;
  $('next').onclick = () => startLevel(now);

  page = Math.floor(now / PER_PAGE);
  renderLevels();
  renderPace();
  $('best').textContent = fmt(save.best);
  $('sound').classList.toggle('off', !save.sound);
  $('buzz').classList.toggle('off', !save.haptics);

  // Hinter dem Menü dreht sich das nächste Level.
  preview = createSim(level);
  stage.setLevel(preview, level.cloth);
  setMode('menu');
}

function begin(level, info) {
  sim = createSim(level);
  run = info;
  stage.setLevel(sim, level.cloth);
  pending.x = pending.y = 0;
  aim.x = sim.hole.x;
  aim.z = sim.hole.z;
  combo = 0;
  lastHint = -1e9;
  bombHintShown = false;
  $('pausename').textContent = info.kind === 'rush' ? 'Zeitjagd' : info.name;
  updateHud(true);
  setMode('play');
}
function startLevel(index) {
  begin(buildLevel(index), { kind: 'level', index, name: levelTitle(index) });
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

/** Knall der Bombe: kurzes Rauschen, das dumpf ausklingt. */
function boom() {
  if (!save.sound) return;
  try {
    audio ??= new (window.AudioContext || window.webkitAudioContext)();
    const length = 0.7;
    const buffer = audio.createBuffer(1, Math.floor(audio.sampleRate * length), audio.sampleRate);
    const data = buffer.getChannelData(0);
    for (let i = 0; i < data.length; i++) data[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / data.length, 2.2);
    const source = audio.createBufferSource();
    source.buffer = buffer;
    const filter = audio.createBiquadFilter();
    filter.type = 'lowpass';
    filter.frequency.setValueAtTime(1400, audio.currentTime);
    filter.frequency.exponentialRampToValueAtTime(120, audio.currentTime + length);
    const amp = audio.createGain();
    amp.gain.value = 0.5;
    source.connect(filter).connect(amp).connect(audio.destination);
    source.start();
  } catch {
    /* ohne Ton spielbar */
  }
}

let bombHintShown = false;
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
    } else if (event.type === 'bomb') {
      stage.quake();
      const flash = $('flash');
      flash.classList.remove('show');
      void flash.offsetWidth;
      flash.classList.add('show');
      boom();
      buzz('grow');
    } else if (event.type === 'land') {
      tone(140, 0.07, 0.05, 'triangle');
    } else if (event.type === 'finish') {
      setTimeout(showResult, sim.exploded ? 1100 : event.cleared ? 900 : 500);
    }
  }
  sim.events.length = 0;
}

function showResult() {
  if (mode !== 'play' && mode !== 'pause') return;
  const rush = run.kind === 'rush';
  const boomed = sim.exploded;
  const stars = starsFor(sim.eaten, sim.total, boomed);
  let record = false;
  if (rush) {
    record = sim.score > save.best;
    if (record) save.best = sim.score;
  } else {
    // Lücken auffüllen, damit die Reihenfolge der Level stimmt.
    while (save.stars.length <= run.index) save.stars.push(0);
    if (stars > save.stars[run.index]) save.stars[run.index] = stars;
  }
  persist();

  const passed = !rush && stars > 0;
  const hasNext = passed;
  $('r-title').textContent = boomed
    ? record ? 'Bombe, aber Rekord' : 'Bombe verschluckt'
    : rush ? (record ? 'Neuer Rekord' : 'Zeit abgelaufen') : sim.cleared ? 'Alles verschluckt' : passed ? 'Level geschafft' : 'Zeit abgelaufen';
  $('r-sub').textContent = boomed
    ? 'Um Bomben musst du herumsteuern. Der rote Ring zeigt sie.'
    : rush
      ? 'Zeitjagd'
      : passed
        ? run.name
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
  $('prev').onclick = () => {
    page = Math.max(0, page - 1);
    renderLevels();
  };
  $('later').onclick = () => {
    page += 1;
    renderLevels();
  };
  for (const button of document.querySelectorAll('[data-pace]')) {
    button.onclick = () => {
      save.pace = Number(button.dataset.pace);
      persist();
      renderPace();
    };
  }
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
    // Der Finger verschiebt einen Zielpunkt; das Loch gleitet ihm nach. Das glättet die
    // ruckartigen Schritte der Berührungspunkte, ohne dass sich das Loch träge anfühlt.
    const pace = PACES[save.pace];
    const per = stage.worldPerPixel() * pace.gain;
    const hole = sim.hole;
    const limit = sim.arena - hole.r * 0.6;
    aim.x = Math.max(-limit, Math.min(limit, aim.x + pending.x * per));
    aim.z = Math.max(-limit, Math.min(limit, aim.z + pending.y * per * stage.depthStretch));
    pending.x = pending.y = 0;
    const glide = 1 - Math.exp(-dt * pace.follow);
    const move = { x: (aim.x - hole.x) * glide, z: (aim.z - hole.z) * glide };
    // Zwei halbe Schritte: schnelle Wischer überspringen so keine kleinen Dinge.
    step(sim, dt / 2, { x: move.x / 2, z: move.z / 2 });
    step(sim, dt / 2, { x: move.x / 2, z: move.z / 2 });
    handleEvents();
    if (!bombHintShown) {
      for (const index of sim.touched) {
        if (sim.items[index].danger > 0.15) {
          bombHintShown = true;
          lastHint = -1e9;
          hint('Vorsicht, Bombe. Nicht verschlucken.');
          break;
        }
      }
    }
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
window.__hole = { get sim() { return sim; }, get mode() { return mode; }, startLevel, startRush, pending, aim };
