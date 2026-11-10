// Gemeinsamer Zustand der App: Kursdaten, Lernstand, Navigation.
import { useEffect, useState } from '../vendor/preact-htm.js';
import { storage, audio } from './platform.js';
import * as Gl from './domain/glossary.js';
import * as P from './domain/progress.js';
import { CourseIndex, resolveItem } from './domain/course.js';
import { asPhrase } from './domain/content.js';

// ── Kursdaten ──────────────────────────────────────────────────────────────

export const D = {
  course: null, podcast: null, index: null, podIndex: null,
  themes: [], themeOf: {}, deepdive: new Map(), patterns: [], sounds: [],
};

export async function loadData() {
  const get = (n) => fetch(`/data/${n}.json`).then((r) => { if (!r.ok) throw new Error(`${n}: ${r.status}`); return r.json(); });
  const [course, podcast, phrasebook, deepdive, patterns, sounds, glosses, clips] = await Promise.all([
    get('course'), get('podcast'), get('phrasebook'), get('deepdive'), get('patterns'), get('sounds'), get('glosses'),
    fetch('/clips.json').then((r) => r.json()),
  ]);
  Gl.install(glosses);
  audio.setClips(clips);
  D.course = course; D.podcast = podcast;
  D.index = new CourseIndex(course); D.podIndex = new CourseIndex(podcast);
  D.themes = phrasebook.themes ?? []; D.themeOf = phrasebook.items ?? {};
  D.deepdive = new Map((deepdive.entries ?? []).map((e) => [e.id, e]));
  D.patterns = patterns.patterns ?? [];
  D.sounds = sounds.groups ?? [];
}

// ── Lernstand ──────────────────────────────────────────────────────────────

const KEY = 'progress_v1';
let state = P.emptyProgress();
const subs = new Set();
const notify = () => { for (const fn of [...subs]) fn(state); };

export const progress = {
  load() {
    state = P.normalizeProgress(storage.getJSON(KEY));
    notify();
    // In der App: Änderungen von außen (z. B. »Folge gehört« aus dem Podcast-Player) übernehmen.
    if (!this._bound) { this._bound = true; storage.onExternal((key) => { if (key === KEY) { state = P.normalizeProgress(storage.getJSON(KEY)); notify(); } }); }
  },
  get: () => state,
  update(fn) { state = fn(state); storage.set(KEY, state); notify(); },
  replace(p) { state = P.normalizeProgress(p); storage.set(KEY, state); notify(); },
  subscribe(fn) { subs.add(fn); return () => subs.delete(fn); },
  today: () => P.todayEpochDay(),
  settings: () => P.settingsOf(state),

  /** Sitzungsergebnis einrechnen (Karten, XP, Serie, Sterne). */
  apply(result) { this.update((p) => P.applyResult(p, result, P.todayEpochDay())); },

  /** Bewertet eine einzelne Karte aus einem freien Modus. Eigene Sätze ("dd:") werden nicht als Karte geführt. */
  gradeCard(id, correct, xp = 0, spokenOk = 0) {
    if (!id || id.startsWith('dd:')) return;
    this.apply({ xp, graded: { [id]: correct }, spokenOk });
  },

  /** XP ohne Karten- und Serien-Wirkung (Spiel-Abschluss). */
  addXp(xp) {
    const today = P.todayEpochDay();
    this.update((p) => ({ ...p, xp: p.xp + xp, xpByDay: { ...p.xpByDay, [today]: P.xpOn(p, today) + xp } }));
  },

  saveMySentence(s) {
    if (!s.ru?.trim() || !s.de?.trim()) return;
    const norm = (x) => x.replaceAll('+', '').replaceAll('́', '').trim().toLowerCase();
    this.update((p) => ({ ...p, mySentences: [...p.mySentences.filter((m) => norm(m.ru) !== norm(s.ru)), { patternId: '', note: '', ...s }].slice(-200) }));
  },
  removeMySentence(ru) { this.update((p) => ({ ...p, mySentences: p.mySentences.filter((m) => m.ru !== ru) })); },
  toggleFavorite(id) {
    this.update((p) => ({ ...p, favorites: p.favorites.includes(id) ? p.favorites.filter((f) => f !== id) : [...p.favorites, id] }));
  },
};

export function useProgress() {
  const [p, setP] = useState(state);
  useEffect(() => { setP(state); return progress.subscribe(setP); }, []);
  return p;
}

/** Satz zu einer ID: Kurs, sonst Podcast, sonst eigener Satz ("dd:…"). */
export function phraseById(id, settings = progress.settings()) {
  const hit = D.index.phrase(id, settings) ?? D.podIndex.phrase(id, settings);
  if (hit) return hit;
  if (id.startsWith('dd:')) {
    const mine = state.mySentences.map((m) => asPhrase(m.ru, m.de, m.note || 'Eigener Satz')).find((p) => p.id === id);
    if (mine) return mine;
  }
  return null;
}

/**
 * Die Form des anderen Geschlechts zu einem Satz (я уста́л ↔ я уста́ла) — du sprichst deine
 * eigene, sollst die andere aber erkennen. null, wenn der Satz für alle gleich lautet.
 */
export function otherForm(id) {
  const item = D.index.itemById.get(id);
  if (!item?.ruF) return null;
  const other = progress.settings().gender === 'FEMALE' ? 'MALE' : 'FEMALE';
  return { label: other === 'FEMALE' ? 'So sagt es eine Frau' : 'So sagt es ein Mann', phrase: resolveItem(item, D.index.dayOfItem.get(id), { gender: other, voice: other }) };
}

export const myPhrases = () => state.mySentences.map((m) => asPhrase(m.ru, m.de, m.note || 'Eigener Satz'));

/** Alle Sätze, die schon »dran waren«: Karten + freigeschaltete Kurstage (Pool für Spiele). */
export function learnedPool() {
  const s = progress.settings();
  const today = progress.today();
  const ids = [...P.dueIds(state.cards, today), ...Object.keys(state.cards)];
  const seen = new Set();
  const out = [];
  const add = (p) => { if (p && !seen.has(p.id)) { seen.add(p.id); out.push(p); } };
  for (const id of ids) add(D.index.phrase(id, s) ?? D.podIndex.phrase(id, s));
  for (const p of D.index.phrasesUpTo(P.unlockedDay(state, D.index.days.length), s)) add(p);
  return out;
}

/** Übergabe Satzbuch → Vokabeltest/Reflex/Kombi (wird vom Ziel verbraucht). */
export const testSelection = { ids: [] };
/** Sprung ins Satzbuch mit vorgewähltem Thema (wird beim Öffnen verbraucht). */
export const phrasebookPreset = { theme: null };
export const takeSelection = () => { const ids = testSelection.ids; testSelection.ids = []; return ids; };

// ── Navigation (Hash-Routen) ───────────────────────────────────────────────

export const TABS = ['heute', 'kurse', 'ueben', 'satzbuch', 'ich'];
export const route = () => (location.hash.replace(/^#\/?/, '') || 'heute');
// Tiefe mitzählen, damit »Zurück« am Anfang nicht aus der App herausführt.
let depth = 0;
let ownPop = false;
export function nav(path, { replace = false } = {}) {
  audio.stop();
  if (route() === path) return;
  if (replace) location.replace(`#/${path}`);
  else { depth++; location.hash = `#/${path}`; }
}
export function back(fallback = 'heute') {
  audio.stop();
  if (depth > 0) { depth--; ownPop = true; history.back(); } else nav(fallback, { replace: true });
}
export function useRoute() {
  const [r, setR] = useState(route());
  useEffect(() => {
    const on = () => setR(route());
    const pop = () => { if (ownPop) ownPop = false; else depth = Math.max(0, depth - 1); };
    window.addEventListener('popstate', pop);
    window.addEventListener('hashchange', on);
    return () => { window.removeEventListener('hashchange', on); window.removeEventListener('popstate', pop); };
  }, []);
  return r;
}

/**
 * Zurück zur übrigen Hikari-App (Profil). In der App schließt das den Russisch-Bereich;
 * im Browser gibt es dahinter nichts — dort erklärt eine kurze Meldung, was passieren wird.
 */
export function exitApp() {
  audio.stop();
  if (window.HikariNative?.exit) window.HikariNative.exit();
  else toast('In der App führt dieser Pfeil zurück zu deinem Hikari-Profil');
}

// ── Kurzmeldung ────────────────────────────────────────────────────────────

const toastSubs = new Set();
export function toast(text) { for (const fn of toastSubs) fn({ text, at: Date.now() }); }
export function useToast() {
  const [t, setT] = useState(null);
  useEffect(() => {
    let timer;
    const fn = (v) => { setT(v); clearTimeout(timer); timer = setTimeout(() => setT(null), 2400); };
    toastSubs.add(fn);
    return () => { toastSubs.delete(fn); clearTimeout(timer); };
  }, []);
  return t;
}

// ── Oberflächen-Einstellungen (nur diese Oberfläche, nicht Teil des Lernstands) ──

export const prefs = {
  dailyGoal: () => Number(storage.get('ui_daily_goal') ?? 60),
  setDailyGoal: (n) => storage.set('ui_daily_goal', String(n)),
  dailyMinutes: () => Number(storage.get('ui_daily_minutes') ?? 30),
  setDailyMinutes: (n) => storage.set('ui_daily_minutes', String(n)),
  sounds: () => storage.get('ui_sounds') !== '0',
  setSounds: (on) => storage.set('ui_sounds', on ? '1' : '0'),
};

export { P };
