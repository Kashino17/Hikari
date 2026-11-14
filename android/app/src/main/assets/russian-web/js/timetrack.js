// Echte Lernzeit je Tag und Modus. Gezählt wird nur aktive Zeit:
// - Podcast: solange die Folge wirklich läuft (auch bei ausgeschaltetem Bildschirm).
//   Bei 2× Tempo zählt also die halbe Folgenlänge — so lange hast du zugehört.
// - Alle anderen Modi: solange die Seite offen und sichtbar ist und du in der
//   letzten Minute etwas getan hast (Tippen, Taste, Ton läuft, Mikrofon an).
// Gespeichert unter "study_time": { epochDay: { modus: Sekunden } }.
import { storage, audio } from './platform.js';

const KEY = 'study_time';
const TICK = 5;
const IDLE_MS = 60000;

/** Modus → Anzeigename und Bereichsfarbe. Reihenfolge = Reihenfolge in der Statistik. */
export const MODES = {
  lesson: ['Jahreskurs: Lektionen', 'speak'],
  talkday: ['Jahreskurs: Gesprächstage', 'speak'],
  dialog: ['Rollenspiele', 'speak'],
  talk: ['Sprich frei', 'speak'],
  reflex: ['Sprech-Reflex', 'speak'],
  blind: ['Blind-Coach', 'speak'],
  anja: ['Gespräch mit Anja', 'amber'],
  podcast: ['Podcast hören', 'listen'],
  podcastPractice: ['Podcast-Übungen', 'listen'],
  erleben: ['Lieder & Szenen', 'listen'],
  blitz: ['Hör-Blitz', 'listen'],
  sounds: ['Lautlabor', 'listen'],
  review: ['Wiederholen und Tests', 'review'],
  patterns: ['Satzbau', 'words'],
  kombi: ['Kombi-Meister', 'words'],
  pairs: ['Paare finden', 'words'],
  satzbuch: ['Satzbuch und Vertiefung', 'words'],
  // Der Feed der App bucht diese drei selbst (RussianStudyTime.kt) — hier stehen nur Name und Bereich.
  feedPractice: ['Wiederholen und Üben', 'feed'],
  feedPhrases: ['Neue Sätze und Nachsprechen', 'feed'],
  feedAlphabet: ['Kyrillisch lesen', 'feed'],
};
export const AREA_COLOR = { speak: 'rgb(244,114,182)', listen: 'rgb(167,139,250)', review: 'rgb(56,189,248)', words: 'rgb(52,211,153)', amber: '#FBBF24', feed: 'rgb(251,146,60)' };
export const AREA_NAME = { speak: 'Sprechen', listen: 'Hören', review: 'Wiederholen', words: 'Wörter & Grammatik', amber: 'Gespräch', feed: 'Feed' };

const ROUTE_MODE = [
  [/^lesson\//, 'lesson'], [/^talkday\//, 'talkday'], [/^dialog\//, 'dialog'], [/^review$/, 'review'], [/^test$/, 'review'],
  [/^podcastPractice\//, 'podcastPractice'], [/^podcast/, 'podcast'], [/^pattern/, 'patterns'], [/^anja$/, 'anja'],
  [/^reflex$/, 'reflex'], [/^kombi$/, 'kombi'], [/^blind$/, 'blind'], [/^talk$/, 'talk'], [/^blitz$/, 'blitz'],
  [/^pairs$/, 'pairs'], [/^sounds/, 'sounds'], [/^satzbuch$/, 'satzbuch'], [/^deep\//, 'satzbuch'], [/^erleben/, 'erleben'],
];
const modeOf = (r) => ROUTE_MODE.find(([re]) => re.test(r))?.[1] ?? null;

let data = null;
let dirty = false;
let lastInteract = Date.now();
let podcastPlaying = false;
let podcastCarry = 0;
const subs = new Set();

const load = () => { if (!data) data = storage.getJSON(KEY, {}) ?? {}; return data; };
const today = () => { const d = new Date(); return Math.floor(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) / 86400000); };

function add(mode, secs) {
  const d = load();
  const day = today();
  d[day] = { ...(d[day] ?? {}), [mode]: (d[day]?.[mode] ?? 0) + secs };
  dirty = true;
  for (const fn of [...subs]) fn();
}
function persist() { if (dirty) { storage.set(KEY, data); dirty = false; } }

export const timeTrack = {
  /** Gehörte Sekunden direkt gutschreiben (in der App misst der Player selbst). */
  addPodcast(secs) {
    // Der Player meldet viermal pro Sekunde — Bruchteile sammeln und nur ganze Sekunden buchen.
    if (!(secs > 0)) return;
    podcastCarry += secs;
    const whole = Math.floor(podcastCarry);
    if (whole >= 1) { podcastCarry -= whole; add('podcast', whole); }
  },
  /** Vom Podcast-Player: läuft die Folge gerade? */
  setPodcastPlaying(on) { podcastPlaying = on; },
  /** Sekunden je Modus an einem Tag. */
  day(epochDay) { return load()[epochDay] ?? {}; },
  /** Sekunden je Modus über alles. */
  totals() {
    const out = {};
    for (const day of Object.values(load())) for (const [m, s] of Object.entries(day)) out[m] = (out[m] ?? 0) + s;
    return out;
  },
  subscribe(fn) { subs.add(fn); return () => subs.delete(fn); },
  raw: () => load(),
  reset() { data = {}; storage.remove(KEY); for (const fn of [...subs]) fn(); },
  /** Nur für Beispiel-Lernstände. */
  replace(d) { data = d; dirty = true; persist(); for (const fn of [...subs]) fn(); },
};
export const daySeconds = (epochDay) => Object.values(timeTrack.day(epochDay)).reduce((a, b) => a + b, 0);

export function startTimeTracking() {
  const touch = () => { lastInteract = Date.now(); };
  for (const ev of ['pointerdown', 'keydown', 'touchstart', 'wheel']) window.addEventListener(ev, touch, { passive: true });
  setInterval(() => {
    if (podcastPlaying) { add('podcast', TICK); return; }
    if (document.hidden) return;
    const mode = modeOf(location.hash.replace(/^#\/?/, ''));
    if (!mode) return;
    // Ein laufendes Video (»Lieder & Szenen«) ist Lernzeit, auch wenn eine Minute lang nichts getippt wurde.
    const watching = [...document.querySelectorAll('video')].some((v) => !v.paused && !v.ended);
    if (Date.now() - lastInteract < IDLE_MS || audio.current() || watching) add(mode, TICK);
  }, TICK * 1000);
  setInterval(persist, 15000);
  window.addEventListener('pagehide', persist);
  document.addEventListener('visibilitychange', () => { if (document.hidden) persist(); });
}

export function fmtDuration(secs, { short = false } = {}) {
  const m = Math.round(secs / 60);
  if (m < 60) return `${m} Min.`;
  const h = Math.floor(m / 60);
  const r = m % 60;
  return short || r === 0 ? `${h} Std.` : `${h} Std. ${r} Min.`;
}
