// Gemeinsamer Zustand der App: Kursdaten, Lernstand, Navigation.
import { useEffect, useState } from '../vendor/preact-htm.js';
import { storage, audio, serverVoice } from './platform.js';
import * as Gl from './domain/glossary.js';
import * as P from './domain/progress.js';
import { CourseIndex, resolveItem } from './domain/course.js';
import { asPhrase } from './domain/content.js';
import * as Pl from './domain/plan.js';

// ── Kursdaten ──────────────────────────────────────────────────────────────

export const D = {
  course: null, podcast: null, index: null, podIndex: null,
  themes: [], themeOf: {}, deepdive: new Map(), patterns: [], sounds: [], plan: null,
};

export async function loadData() {
  const get = (n) => fetch(`/data/${n}.json`).then((r) => { if (!r.ok) throw new Error(`${n}: ${r.status}`); return r.json(); });
  const [course, podcast, phrasebook, deepdive, patterns, sounds, glosses, plan, clips] = await Promise.all([
    get('course'), get('podcast'), get('phrasebook'), get('deepdive'), get('patterns'), get('sounds'), get('glosses'), get('plan'),
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
  D.plan = new Pl.Plan(plan);
}

// ── Lernstand ──────────────────────────────────────────────────────────────

const KEY = 'progress_v1';
let state = P.emptyProgress();
const subs = new Set();
const notify = () => {
  serverVoice.setDefault((state.voice ?? state.gender) === 'FEMALE' ? 'f' : 'm');
  for (const fn of [...subs]) fn(state);
};

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

  /**
   * Bewertet eine einzelne Karte aus einem freien Modus. Eigene Sätze ("dd:") zählen nur, wenn sie
   * als Karte geführt werden — das sind die aus »Lieder & Szenen« mitgenommenen (siehe learnSentence).
   */
  gradeCard(id, correct, xp = 0, spokenOk = 0) {
    if (!id || !this.gradable(id)) return;
    this.apply({ xp, graded: { [id]: correct }, spokenOk });
  },

  /** XP ohne Karten- und Serien-Wirkung (Spiel-Abschluss). */
  addXp(xp) {
    const today = P.todayEpochDay();
    this.update((p) => ({ ...p, xp: p.xp + xp, xpByDay: { ...p.xpByDay, [today]: P.xpOn(p, today) + xp } }));
  },

  /** Wird diese Karte bewertet? Kurssätze immer, eigene Sätze nur mit Karte. */
  gradable: (id) => !!id && (!id.startsWith('dd:') || !!state.cards[id]),

  saveMySentence(s) {
    if (!s.ru?.trim() || !s.de?.trim()) return;
    const norm = (x) => x.replaceAll('+', '').replaceAll('́', '').trim().toLowerCase();
    this.update((p) => withoutOrphanCards({ ...p, mySentences: [...p.mySentences.filter((m) => norm(m.ru) !== norm(s.ru)), { patternId: '', note: '', ...s }].slice(-MY_SENTENCES_MAX) }));
  },
  removeMySentence(ru) { this.update((p) => withoutOrphanCards({ ...p, mySentences: p.mySentences.filter((m) => m.ru !== ru) })); },

  /**
   * Eigenen Satz ins Satzbuch legen UND als Karte führen: Er kommt in die Wiederholung, wird in den
   * Übungen bewertet und zählt zum Wortschatz, sobald er sitzt. → Karten-ID
   */
  learnSentence(s) {
    this.saveMySentence(s);
    const id = asPhrase(s.ru, s.de).id;
    if (!state.cards[id]) this.apply({ xp: 0, introduced: [id] });
    return id;
  },
  toggleFavorite(id) {
    this.update((p) => ({ ...p, favorites: p.favorites.includes(id) ? p.favorites.filter((f) => f !== id) : [...p.favorites, id] }));
  },
};

/**
 * Sprechen nur auf Deutsch: Ein Satz steht nur beim allerersten Mal offen auf Russisch da.
 * Kurssätze gelten als bekannt, sobald es eine Karte gibt; alle anderen (Vertiefung, Muster,
 * Zeilen aus Lieder & Szenen), sobald sie einmal offen gezeigt wurden. Eigener Schlüssel —
 * nie in `progress_v1`, das liest die App (Kotlin) mit und verwirft fremde Felder.
 */
const SEEN_KEY = 'speak_seen_v1';
const SEEN_MAX = 6000;
let seenIds = null;
const seenSet = () => (seenIds ??= new Set(storage.getJSON(SEEN_KEY, [])));
export const speakSeen = {
  isFirst(phrase) {
    const id = phrase?.id;
    if (!id) return true;
    return !state.cards[id] && !seenSet().has(id);
  },
  mark(phrase) {
    const id = phrase?.id;
    if (!id || state.cards[id] || seenSet().has(id)) return;
    seenIds.add(id);
    storage.set(SEEN_KEY, [...seenIds].slice(-SEEN_MAX));
  },
};

/** So viele eigene Sätze fasst das Satzbuch; die ältesten fallen danach heraus. */
const MY_SENTENCES_MAX = 1000;
/** Karten eigener Sätze verschwinden mit ihrem Satz — sonst blieben sie für immer »fällig«. */
function withoutOrphanCards(p) {
  const own = Object.keys(p.cards).filter((id) => id.startsWith('dd:'));
  if (!own.length) return p;
  const keep = new Set(p.mySentences.map((m) => asPhrase(m.ru, m.de).id));
  if (own.every((id) => keep.has(id))) return p;
  return { ...p, cards: Object.fromEntries(Object.entries(p.cards).filter(([id]) => !id.startsWith('dd:') || keep.has(id))) };
}

export function useProgress() {
  const [p, setP] = useState(state);
  useEffect(() => { setP(state); return progress.subscribe(setP); }, []);
  return p;
}

// ── Jahresplan: Gesprächstage und Lerntempo (eigener Speicher, nicht Teil des Lernstands) ──

const PLAN_KEY = 'plan_v1';
let planSt = Pl.emptyState();
const planSubs = new Set();
export const planState = {
  load() {
    planSt = Pl.normalizeState(storage.getJSON(PLAN_KEY));
    if (!this._bound) { this._bound = true; storage.onExternal((key) => { if (key === PLAN_KEY) { planSt = Pl.normalizeState(storage.getJSON(PLAN_KEY)); for (const fn of [...planSubs]) fn(planSt); } }); }
  },
  get: () => planSt,
  update(fn) { planSt = Pl.normalizeState(fn(planSt)); storage.set(PLAN_KEY, planSt); for (const f of [...planSubs]) f(planSt); },
  subscribe(fn) { planSubs.add(fn); return () => planSubs.delete(fn); },
  /** Persönlicher Zeitfaktor für alle Minuten-Angaben. */
  pace: () => Pl.paceFactor(planSt),
};
export function usePlanState() {
  const [s, setS] = useState(planSt);
  useEffect(() => { setS(planSt); return planState.subscribe(setS); }, []);
  return s;
}
/** »Tag 7« zu einer Lektion: im Kurs zählen Plan-Tage (jeder fünfte ist ein Gesprächstag). */
export const dayNo = (lesson) => Pl.dayOfLesson(lesson);
/** Höchste Lektion, die gerade offen ist (Gesprächstage halten die nächste Lektion zurück). */
export function openLesson(p = state) {
  const cur = D.plan.currentDay(p, planSt);
  if (cur == null) return D.plan.builtLessons;
  return Pl.lessonOfDay(cur) ?? Pl.lessonOfDay(cur - 1);
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

// Angebot, das russische Sprachpaket zu laden — einmal je App-Start, wenn die Erkennung übers Internet hakt.
const packSubs = new Set();
let packOffered = false;
export function offerPack(force = false) {
  if (packOffered && !force) return;
  packOffered = true;
  for (const fn of packSubs) fn(true);
}
export function usePackOffer() {
  const [open, setOpen] = useState(false);
  useEffect(() => { packSubs.add(setOpen); return () => packSubs.delete(setOpen); }, []);
  return [open, () => setOpen(false)];
}

// ── Verbindung: Netz und Mac ───────────────────────────────────────────────
// 'online' | 'noserver' (Netz da, Mac nicht erreichbar) | 'offline' (kein Netz). Für den Offline-Streifen
// und für Anja: klar sagen, was gerade geht — statt einer stummen Sperre.
let LINK = 'online';
const linkSubs = new Set();
const setLink = (v) => { if (v !== LINK) { LINK = v; for (const fn of [...linkSubs]) fn(v); } };
async function checkLink() {
  if (typeof navigator !== 'undefined' && navigator.onLine === false) { setLink('offline'); return; }
  try {
    const ctl = new AbortController();
    const t = setTimeout(() => ctl.abort(), 3500);
    const r = await fetch('/api/russian/anja/live/status', { cache: 'no-store', signal: ctl.signal });
    clearTimeout(t);
    setLink(r.status === 502 || r.status === 504 ? (navigator.onLine === false ? 'offline' : 'noserver') : 'online');
  } catch { setLink(navigator.onLine === false ? 'offline' : 'noserver'); }
}
let linkTimer = null;
export function useLink() {
  const [v, setV] = useState(LINK);
  useEffect(() => {
    linkSubs.add(setV);
    if (!linkTimer) {
      checkLink();
      linkTimer = setInterval(() => { if (!document.hidden) checkLink(); }, 30000);
      window.addEventListener('online', checkLink);
      window.addEventListener('offline', checkLink);
    }
    return () => linkSubs.delete(setV);
  }, []);
  return v;
}

// ── Formen eines Wortes: überall per Tippen ────────────────────────────────
const wordSubs = new Set();
/** Öffnet das Blatt mit allen Formen eines russischen Wortes (oder einer Wortgruppe). */
export function openWord(ru) { if (ru) for (const fn of wordSubs) fn(String(ru)); }
export function useWordSheet() {
  const [w, setW] = useState(null);
  useEffect(() => { wordSubs.add(setW); return () => wordSubs.delete(setW); }, []);
  return [w, () => setW(null)];
}

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

export { P, Pl };
