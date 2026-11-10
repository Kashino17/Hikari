// Einstellungen, Geschlechts-Abfrage, XP-Stufen und Test-Werkzeuge (die Statistik selbst liegt in stats.js).
import { html, cls, Icon, Sheet, useState } from '../ui.js';
import { D, P, useProgress, progress, prefs, toast } from '../app.js';
import { storage } from '../platform.js';
import { GenderIntro } from './home.js';
import { timeTrack } from '../timetrack.js';

export function GenderGate() {
  const p = useProgress();
  return p.gender == null ? html`<div style="margin-bottom:16px"><${GenderIntro} /></div>` : null;
}

/** Stufe aus XP: jede Stufe braucht etwas mehr als die davor. */
export function levelOf(xp) {
  const lvl = Math.floor(Math.sqrt(xp / 40)) + 1;
  const base = (lvl - 1) ** 2 * 40;
  const next = lvl ** 2 * 40;
  return { lvl, into: xp - base, need: next - base, next };
}

function Seg({ value, options, onChange }) {
  return html`<div class="seg">${options.map(([v, label]) => html`<button class=${value === v ? 'on' : ''} onClick=${() => onChange(v)}>${label}</button>`)}</div>`;
}

export function SettingsSheet({ onClose }) {
  const p = useProgress();
  const [, bump] = useState(0);
  const s = P.settingsOf(p);
  return html`<${Sheet} onClose=${onClose} title="Einstellungen">
    <div class="q">Ich bin … (bestimmt, welche Ich-Form du sprichst — die andere lernst du zu erkennen)</div>
    <${Seg} value=${p.gender} options=${[['MALE', 'ein Mann'], ['FEMALE', 'eine Frau']]} onChange=${(g) => progress.update((x) => ({ ...x, gender: g, voice: x.voice ?? g }))} />
    <div class="q">Stimme für Vokabeln</div>
    <${Seg} value=${s.voice} options=${[['MALE', 'Männlich'], ['FEMALE', 'Weiblich']]} onChange=${(v) => progress.update((x) => ({ ...x, voice: v }))} />
    <p class="faint mt8" style="font-size:12px">Sätze mit Geschlechtsform klingen immer in der passenden Stimme.</p>
    <div class="q">Tagesziel</div>
    <${Seg} value=${prefs.dailyGoal()} options=${[[30, 'Locker · 30'], [60, 'Normal · 60'], [120, 'Ehrgeizig · 120']]} onChange=${(n) => { prefs.setDailyGoal(n); bump((x) => x + 1); }} />
    <div class="q">Lernzeit pro Tag</div>
    <${Seg} value=${prefs.dailyMinutes()} options=${[[15, '15 Min.'], [30, '30 Min.'], [60, '1 Std.']]} onChange=${(n) => { prefs.setDailyMinutes(n); bump((x) => x + 1); }} />
    <div class="q">Töne bei richtig und falsch</div>
    <${Seg} value=${prefs.sounds()} options=${[[true, 'An'], [false, 'Aus']]} onChange=${(on) => { prefs.setSounds(on); bump((x) => x + 1); }} />
    <button class="btn mt24" onClick=${onClose}>Fertig</button>
  <//>`;
}

// ── Test-Werkzeuge: Beispiel-Lernstände zum Durchklicken ───────────────────

/** Beispiel-Lernstände für spätere Niveaus: so sieht die App aus, wenn du dort angekommen bist. */
const LEVEL_DEMOS = {
  a2: { name: 'A2', pod: 24, sit: 0.72, spoken: 430, patterns: 14, talk: 6, stage: 1, streak: 41, best: 41, xp: 9800, hours: 95, blitz: 520, pairs: 24, reflex: 260, kombi: 175, lab: 5 },
  b1: { name: 'B1', pod: 62, sit: 0.76, spoken: 980, patterns: 25, talk: 64, stage: 2, streak: 96, best: 112, xp: 31500, hours: 310, blitz: 690, pairs: 19, reflex: 340, kombi: 200, lab: 8 },
  b2: { name: 'B2', pod: 100, sit: 0.92, spoken: 1840, patterns: 25, talk: 230, stage: 3, streak: 187, best: 187, xp: 78400, hours: 720, blitz: 840, pairs: 16, reflex: 395, kombi: 200, lab: 8 },
};
const DEMO_LABEL = { new: 'Leerer Lernstand geladen', mid: 'Beispiel: Tag 6, erste Woche', adv: 'Beispiel: Tag 17, Niveau A1', a2: 'Beispiel: Niveau A2', b1: 'Beispiel: Niveau B1', b2: 'Beispiel: Niveau B2' };

function levelDemo(cfg) {
  const today = progress.today();
  const p = { ...P.emptyProgress(), gender: 'MALE', voice: 'MALE' };
  let n = 0;
  const addCards = (items) => {
    for (const it of items) {
      n++;
      const r = ((n * 61) % 100) / 100;
      const box = r < cfg.sit - 0.12 ? 4 + (n % 3) : r < cfg.sit ? 3 : 1 + (n % 2);
      const due = n % 17 === 0 ? today : today + 1 + (n % Math.max(2, P.INTERVALS[box]));
      p.cards[it.id] = { box, due, seen: 3 + box, wrong: box <= 2 ? 2 : n % 5 === 0 ? 1 : 0 };
    }
  };
  for (const d of D.index.days) { p.dayStars[d.day] = d.day % 4 === 0 ? 2 : 3; addCards(d.items); }
  for (const d of D.podIndex.days.filter((x) => x.day <= cfg.pod)) { p.podcastDayStars[d.day] = d.day % 5 === 0 ? 2 : 3; p.podcastListened.push(d.day); addCards(d.items); }
  if (cfg.pod < D.podIndex.days.length) p.podcastListened.push(cfg.pod + 1);
  for (let i = 1; i <= 29; i++) p.xpByDay[today - i] = 60 + ((i * 37) % 120);
  Object.assign(p, {
    streak: cfg.streak, bestStreak: cfg.best, lastActiveDay: today - 1, xp: cfg.xp, spokenOk: cfg.spoken,
    bestBlitz: cfg.blitz, bestPairsSeconds: cfg.pairs, bestReflex: cfg.reflex, bestKombi: cfg.kombi,
    labBest: Object.fromEntries(D.sounds.slice(0, cfg.lab).map((g, i) => [g.id, 100 - ((i * 7) % 22)])),
    favorites: ['d01_spasibo_bolshoe', 'd03_kak_dela', 'd02_ochen_priyatno', 'd12_ya_soglasen'],
    patternsDone: D.patterns.slice(0, cfg.patterns).map((x) => x.id),
    mySentences: [{ ru: 'Я хоч+у домой.', de: 'Ich will nach Hause.', patternId: 'p01_hochu', note: 'meiner Mama' }],
    courseLastCompletedDay: today - 3, podcastLastCompletedDay: today - 1,
  });
  return p;
}

export function demoProgress(kind) {
  const today = progress.today();
  if (kind === 'new') return P.emptyProgress();
  if (LEVEL_DEMOS[kind]) return levelDemo(LEVEL_DEMOS[kind]);
  const upto = kind === 'mid' ? 5 : 16;
  const p = { ...P.emptyProgress(), gender: 'MALE', voice: 'MALE' };
  let n = 0;
  for (const d of D.index.days.filter((x) => x.day <= upto)) {
    p.dayStars[d.day] = d.day % 3 === 0 ? 2 : 3;
    for (const it of d.items) {
      n++;
      const age = upto - d.day;
      const box = Math.min(6, Math.max(1, 1 + Math.floor(age / 1.5) + (n % 3 === 0 ? -1 : 0)));
      const dueIn = n % 7 === 0 ? -1 : n % 5 === 0 ? 0 : P.INTERVALS[box] - (n % 3);
      p.cards[it.id] = { box: n % 11 === 0 ? 1 : box, due: today + dueIn, seen: 2 + age, wrong: n % 4 === 0 ? 1 : 0 };
    }
  }
  const podUpto = kind === 'mid' ? 2 : 12;
  for (let d = 1; d <= podUpto; d++) { p.podcastDayStars[d] = 3; p.podcastListened.push(d); }
  p.podcastListened.push(podUpto + 1);
  const days = kind === 'mid' ? 6 : 23;
  for (let i = 1; i <= Math.min(days, 29); i++) p.xpByDay[today - i] = 40 + ((i * 37) % 90);
  p.streak = days; p.bestStreak = days + (kind === 'mid' ? 0 : 4); p.lastActiveDay = today - 1;
  p.xp = Object.values(p.xpByDay).reduce((a, b) => a + b, 0) + (kind === 'mid' ? 240 : 2900);
  p.spokenOk = kind === 'mid' ? 38 : 412;
  p.bestBlitz = kind === 'mid' ? 180 : 440; p.bestPairsSeconds = kind === 'mid' ? 41 : 27;
  p.bestReflex = kind === 'mid' ? 96 : 215; p.bestKombi = kind === 'mid' ? 0 : 150;
  p.labBest = kind === 'mid' ? { yi: 86 } : { yi: 100, soft: 86, rl: 78, stress: 92 };
  p.favorites = ['d01_spasibo_bolshoe', 'd03_kak_dela', 'd02_ochen_priyatno'];
  p.patternsDone = D.patterns.slice(0, kind === 'mid' ? 1 : 9).map((x) => x.id);
  p.mySentences = [{ ru: 'Я хоч+у домой.', de: 'Ich will nach Hause.', patternId: 'p01_hochu', note: 'meiner Mama' }];
  p.courseLastCompletedDay = today - 1; p.podcastLastCompletedDay = today - 1;
  return p;
}

/** Beispiel-Lernzeiten passend zum Beispiel-Lernstand. */
function demoTime(kind) {
  if (kind === 'new') return {};
  const today = progress.today();
  const cfg = LEVEL_DEMOS[kind];
  const days = kind === 'mid' ? 6 : cfg ? 28 : 23;
  const pick = kind === 'mid' ? ['lesson', 'review', 'podcast', 'blitz'] : ['lesson', 'review', 'podcast', 'podcastPractice', 'reflex', 'kombi', 'talk', 'patterns', 'satzbuch', 'blitz', 'anja'];
  const out = {};
  let recent = 0;
  for (let i = 1; i <= days; i++) {
    const d = {};
    pick.forEach((m, k) => { if ((i + k) % 3 !== 0) { d[m] = 60 * (3 + ((i * 7 + k * 5) % 13)); recent += d[m]; } });
    out[today - i] = d;
  }
  if (cfg) {
    // Die Zeit davor als ein Block — die Diagramme zeigen nur die letzten Wochen, die Summen alles.
    const rest = Math.max(0, cfg.hours * 3600 - recent);
    const share = { lesson: 0.16, podcast: 0.2, podcastPractice: 0.1, review: 0.12, reflex: 0.08, talk: 0.07, anja: 0.1, patterns: 0.05, kombi: 0.04, satzbuch: 0.03, blitz: 0.03, sounds: 0.02 };
    out[today - 45] = Object.fromEntries(Object.entries(share).map(([m, f]) => [m, Math.round(rest * f)]));
  }
  return out;
}

/** Gesprächsreise passend zum Beispiel: alle Mini-Gespräche geschafft, freie Antworten bei Anja. */
function demoChat(kind) {
  const cfg = LEVEL_DEMOS[kind];
  if (!cfg) return null;
  const today = progress.today();
  return {
    stage: cfg.stage,
    foundation: { introduced: ['hello', 'tea', 'yes', 'thanks', 'coffee', 'no', 'please', 'or', 'i', 'like', 'you', 'and', 'how', 'things', 'good', 'okay'], completed: ['hello', 'choice', 'likes', 'feeling'], run: null },
    observations: Array.from({ length: Math.min(cfg.talk, 500) }, (_, i) => ({ id: `demo${i}`, day: today - (i % 60), stage: cfg.stage, scenario: ['cafe', 'friend', 'weekend', 'memory'][i % 4], answer: `Beispiel ${i}`, clear: true, independent: true, spoken: i % 5 !== 0 })),
    cards: [],
  };
}

export function DevTools() {
  const [, bump] = useState(0);
  const load = (kind) => {
    if (!storage.get('progress_backup_real')) {
      storage.set('progress_backup_real', progress.get());
      storage.set('study_time_backup_real', timeTrack.raw());
      storage.set('conv_progress_backup_real', storage.get('conv_progress') ?? '');
    }
    timeTrack.replace(demoTime(kind));
    for (const k of ['lesson_session_v1', 'podcast_session_v1', 'conv_session']) storage.remove(k);
    const chat = demoChat(kind);
    if (chat) storage.set('conv_progress', chat); else storage.remove('conv_progress');
    progress.replace(demoProgress(kind));
    toast(DEMO_LABEL[kind]);
    bump((x) => x + 1);
  };
  const restore = () => {
    const real = storage.getJSON('progress_backup_real');
    if (!real) return;
    progress.replace(real);
    timeTrack.replace(storage.getJSON('study_time_backup_real', {}) ?? {});
    const chat = storage.get('conv_progress_backup_real');
    if (chat) storage.set('conv_progress', chat); else storage.remove('conv_progress');
    for (const k of ['progress_backup_real', 'study_time_backup_real', 'conv_progress_backup_real']) storage.remove(k);
    toast('Dein eigener Lernstand ist zurück');
    bump((x) => x + 1);
  };
  const sim = storage.get('dev_speech') ?? '';
  const setSim = (v) => { if (v) storage.set('dev_speech', v); else storage.remove('dev_speech'); bump((x) => x + 1); };
  return html`<div class="stack g12">
    <div><div class="sub" style="margin-bottom:8px">Beispiel-Lernstand laden — dein eigener wird vorher gesichert.</div>
      <div class="rowx g8 wrap">${[['new', 'Ganz neu'], ['mid', 'Tag 6'], ['adv', 'Tag 17 · A1']].map(([k, l]) => html`<button class="chip" onClick=${() => load(k)}>${l}</button>`)}</div>
      <div class="sub" style="margin:12px 0 8px">So sieht es auf späteren Niveaus aus:</div>
      <div class="rowx g8 wrap">${Object.entries(LEVEL_DEMOS).map(([k, c]) => html`<button class="chip" onClick=${() => load(k)}>Niveau ${c.name}</button>`)}
        ${storage.get('progress_backup_real') && html`<button class="chip on" onClick=${restore}><${Icon} n="undo" />Mein Stand</button>`}</div></div>
    <div><div class="sub" style="margin-bottom:8px">Spracherkennung — zum Durchklicken ohne Mikrofon.</div>
      <div class="rowx g8 wrap">${[['', 'Echtes Mikrofon'], ['ok', 'Immer richtig'], ['bad', 'Immer falsch']].map(([v, l]) => html`<button class=${cls('chip', sim === v && 'on')} onClick=${() => setSim(v)}>${l}</button>`)}</div></div>
  </div>`;
}

