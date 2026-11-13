// Lern-Sitzung: Lektion, Wiederholung, Rollenspiel, Vokabeltest, Podcast-Übungen.
// Ablauf und Wertung entsprechen RussianSessionViewModel.kt; die Oberfläche ist neu.
import {
  html, cls, Icon, SessionBar, IL, PhraseBlock, PlayRow, Speaker, Opt, LetterLens, Latin, useKeys, useListen, Mic, ScoreCard,
  Practice, PhraseDock, SentenceCard, RecordCompare, Summary, LeaveSheet, usePlaying, playPhrase, useState, useEffect, useRef, useMemo, fx, audio, speech, MIC_WAIT } from '../ui.js';
import { D, P, Pl, progress, planState, dayNo, back, takeSelection } from '../app.js';
import { storage, SPEECH_ERRORS } from '../platform.js';
import * as Co from '../domain/course.js';
import { weightedOrder, lessonReviewIds, talkDaySelection } from '../learner.js';
import { StageList } from './stages.js';
import { PatternStep } from './patternstep.js';
import * as Sp from '../domain/speech.js';
import * as Gl from '../domain/glossary.js';
import * as Ph from '../domain/phonetics.js';

const MODE = {
  LESSON: { c: 'c-speak', bonus: 30 }, PODCAST: { c: 'c-listen', bonus: 30 }, REVIEW: { c: 'c-review', bonus: 10 },
  TEST: { c: 'c-words', bonus: 10 }, DIALOG: { c: 'c-speak', bonus: 0 }, TALKDAY: { c: 'c-amber', bonus: 40 },
};
const SNAP_KEY = { LESSON: 'lesson_session_v1', PODCAST: 'podcast_session_v1', TALKDAY: 'talk_session_v1' };
/** Format der Zwischenstände. 2 = mit Etappen und gespeicherter Auswahl. */
export const SNAP_VERSION = 2;

// ── Ablauf ─────────────────────────────────────────────────────────────────

class Engine {
  constructor(mode, day, onChange) {
    Object.assign(this, { mode, day, onChange, pos: 0, feedback: null, summary: null, xp: 0, correct: 0, total: 0, spokenOk: 0 });
    this.firstTry = {}; this.retried = new Set(); this.retriedPositions = new Set(); this.inserts = [];
    this.settings = progress.settings();
    this.snapKey = SNAP_KEY[mode] ?? null;
    const saved = this.snapKey ? storage.getJSON(this.snapKey) : null;
    const snap = saved && saved.web === 1 && saved.v === SNAP_VERSION && saved.day === day ? saved : null;
    this.seed = snap?.seed ?? ((Math.random() * 0x7fffffff) | 0);
    // Was aus dem Lernstand gewählt wird (Wiederholungen, Stoff des Gesprächstags), bleibt über eine Pause hinweg gleich.
    this.sel = snap?.sel ?? this.select(Co.makeRng(this.seed ^ 0x5bd1e995));
    this.steps = this.build(Co.makeRng(this.seed));
    this.baseSize = this.steps.length;
    const stageCount = 1 + Math.max(-1, ...this.steps.map((x) => x.stage ?? -1));
    this.stages = Pl.STAGES[mode] && stageCount > 1 ? Pl.STAGES[mode].slice(0, stageCount) : null;
    this.stageEst = this.stages ? Pl.stageSeconds(this.steps, this.stages.length) : [];
    this.active = snap?.active ?? 0;   // aktive Sekunden in der laufenden Etappe
    this.stepAt = Date.now();
    if (snap) {
      if (snap.baseSize === this.steps.length && snap.pos < this.steps.length + snap.inserts.length) {
        const from = [...snap.retried].sort((a, b) => a - b);
        snap.inserts.forEach((at, i) => this.steps.splice(Math.min(at, this.steps.length), 0, this.steps[from[i]]));
        Object.assign(this, { pos: snap.pos, inserts: [...snap.inserts], firstTry: { ...snap.firstTry }, retried: new Set(snap.retried),
          retriedPositions: new Set(snap.retriedPositions), xp: snap.xp, correct: snap.correct, total: snap.total, spokenOk: snap.spokenOk });
        this.resumed = snap.pos > 0;
      } else storage.remove(this.snapKey);
    }
    if (!this.steps.length) this.summary = { empty: true };
  }

  review(ids, limit, rng) {
    const s = this.settings;
    const main = Co.review(D.index, ids.filter((id) => D.index.itemById.has(id)), D.index.days.length, s, rng, limit);
    const pod = Co.review(D.podIndex, ids.filter((id) => D.podIndex.itemById.has(id)), D.podIndex.days.length, s, rng, limit);
    return rng.shuffled([...main, ...pod]).slice(0, limit);
  }

  /** Auswahl aus dem Lernstand — einmal je Sitzung, wird im Zwischenstand gespeichert. */
  select(rng) {
    if (this.mode === 'LESSON') return { reviewIds: lessonReviewIds(this.day, rng) };
    if (this.mode === 'TALKDAY') return talkDaySelection(this.day, rng);
    return {};
  }

  build(rng) {
    const { mode, day, settings } = this;
    const p = progress.get();
    const today = progress.today();
    if (mode === 'LESSON') {
      const pattern = D.patterns.find((x) => x.id === D.plan.ofLesson(day)?.pattern) ?? null;
      return Co.lesson(D.index, day, settings, rng, { reviewIds: this.sel.reviewIds, pattern });
    }
    if (mode === 'PODCAST') return Co.podcastLesson(D.podIndex, day, settings, rng);
    if (mode === 'TALKDAY') return Co.talkDay(D.index, this.sel, settings);
    if (mode === 'DIALOG') { const d = D.index.day(day); return d ? [{ type: 'dialog', day: d, lines: D.index.dialog(d, settings), gradedId: null }] : []; }
    if (mode === 'TEST') {
      const all = rng.shuffled(takeSelection());
      const box = (id) => p.cards[id]?.box ?? 0;
      const ids = [...new Set([...all.filter((id) => box(id) <= 2).slice(0, 9), ...all.filter((id) => box(id) >= 3).slice(0, 6), ...all])].slice(0, 15);
      return this.review(ids, 15, rng);
    }
    const due = P.dueIds(p.cards, today);
    const rest = Object.entries(p.cards).filter(([, c]) => c.due > today);
    const strong = rng.shuffled(rest.filter(([, c]) => c.box >= 3).map(([id]) => id));
    const weak = rng.shuffled(rest.filter(([, c]) => c.box <= 2).map(([id]) => id));
    let ids = [...new Set([...due.slice(0, 12), ...weak.slice(0, 4), ...strong.slice(0, 8)])];
    // Nichts fällig: eine gemischte Runde, in der schwache Themen und wackelige Sätze öfter vorkommen.
    if (!ids.length) ids = weightedOrder(D.index.phrasesUpTo(P.unlockedDay(p, D.index.days.length), settings), rng).slice(0, 12).map((x) => x.id);
    return this.review(ids, 20, rng);
  }

  get current() { return this.steps[this.pos] ?? null; }

  answer(isCorrect) {
    const step = this.current;
    if (!step || this.feedback) return;
    const id = step.gradedId;
    const isRetry = this.retriedPositions.has(this.pos);
    let gained = 0;
    if (id != null) {
      if (!(id in this.firstTry)) this.firstTry[id] = isCorrect;
      this.total++;
      if (isCorrect) { this.correct++; gained = isRetry ? 5 : 10; this.xp += gained; }
    }
    const requeue = !isCorrect && id != null && !this.retried.has(this.pos) && !isRetry;
    if (requeue) {
      this.retried.add(this.pos);
      // Noch in derselben Etappe wiederholen: vor dem nächsten Haltepunkt bzw. vor Muster und Rollenspiel.
      let insertAt = this.steps.length;
      for (let i = this.pos + 1; i < this.steps.length; i++) {
        if (['stageBreak', 'dialog', 'pattern'].includes(this.steps[i].type)) { insertAt = i; break; }
      }
      this.steps.splice(insertAt, 0, step);
      this.inserts.push(insertAt);
      this.retriedPositions = new Set([...this.retriedPositions].map((x) => (x >= insertAt ? x + 1 : x)));
      this.retriedPositions.add(insertAt);
    }
    (isCorrect ? fx.right : fx.wrong)();
    this.feedback = { correct: isCorrect, requeued: requeue, gained };
    this.onChange();
  }

  skip() { if (!this.feedback) { this.feedback = { correct: true, skipped: true, gained: 0 }; this.onChange(); } }
  spokenCorrectly() { this.spokenOk++; }

  dialogDone(right, lines) {
    this.xp += right * 3 + 10; this.correct += right; this.total += lines;
    this.next();
  }

  /** Aktive Zeit des gerade beendeten Schritts mitzählen (Leerlauf zählt höchstens zwei Minuten). */
  tick() {
    const now = Date.now();
    this.active += Math.min(120, (now - this.stepAt) / 1000);
    this.stepAt = now;
  }
  /** Etappe abgeschlossen: gemessene gegen geschätzte Zeit halten, damit die Minuten-Angaben zu dir passen. */
  stageDone(i) {
    const est = this.stageEst[i] ?? 0;
    if (est > 0 && this.active > est * 0.3) planState.update((st) => Pl.addPace(st, est, this.active));
    this.active = 0;
  }
  get stageIndex() { return this.current?.stage ?? (this.stages ? this.stages.length - 1 : 0); }
  /** Abgeschlossene Etappen (ein erreichter Haltepunkt zählt mit). */
  get stagesDone() { return this.steps.slice(0, this.pos + 1).filter((x) => x.type === 'stageBreak').length; }

  next() {
    audio.stop();
    this.tick();
    if (this.pos + 1 >= this.steps.length) { this.finish(); return; }
    this.pos++; this.feedback = null;
    if (this.current.type === 'stageBreak') this.stageDone(this.current.stage);
    if (this.snapKey) {
      storage.set(this.snapKey, { web: 1, v: SNAP_VERSION, day: this.day, seed: this.seed, sel: this.sel, baseSize: this.baseSize, pos: this.pos, inserts: this.inserts,
        firstTry: this.firstTry, retried: [...this.retried], retriedPositions: [...this.retriedPositions], xp: this.xp, correct: this.correct,
        total: this.total, spokenOk: this.spokenOk, active: this.active, done: this.stagesDone, stages: this.stages?.length ?? 0 });
    }
    this.onChange();
  }

  finish() {
    const { mode, day } = this;
    if (this.snapKey) storage.remove(this.snapKey);
    if (this.stages) this.stageDone(this.stages.length - 1);
    const stars = P.stars(this.correct, this.total);
    const xp = this.xp + MODE[mode].bonus;
    const index = mode === 'PODCAST' ? D.podIndex : D.index;
    const dayItems = mode === 'LESSON' || mode === 'PODCAST' ? (index.day(day)?.items ?? []).map((i) => i.id) : [];
    progress.apply({ xp, graded: this.firstTry, introduced: dayItems, completedDay: mode === 'LESSON' ? day : null,
      completedPodcastDay: mode === 'PODCAST' ? day : null, stars, spokenOk: this.spokenOk });
    if (mode === 'LESSON') {
      // Das Satzbau-Muster des Tages gilt mit der Lektion als gelernt.
      const pat = D.plan.ofLesson(day)?.pattern;
      if (pat && !progress.get().patternsDone.includes(pat)) progress.update((x) => ({ ...x, patternsDone: [...x.patternsDone, pat] }));
    }
    if (mode === 'TALKDAY') {
      const today = progress.today();
      planState.update((st) => ({ ...st, talk: { ...st.talk, [day]: Math.max(stars, st.talk[day] ?? 0) }, talkLast: today }));
    }
    const wrong = Object.entries(this.firstTry).filter(([, ok]) => !ok).map(([id]) => D.index.phrase(id, this.settings) ?? D.podIndex.phrase(id, this.settings)).filter(Boolean);
    this.pos = this.steps.length; this.feedback = null;
    this.summary = { xp, correct: this.correct, total: this.total, stars, streak: progress.get().streak, wrong };
    this.onChange();
  }
}

// ── Schritte ───────────────────────────────────────────────────────────────

const useAutoPlay = (phrase) => useEffect(() => { playPhrase(phrase); }, []);
const KEYS = ['1', '2', '3', '4'];

function Head({ over, title, c }) {
  return html`<div class=${cls('over', c && 'c')}>${over}</div>${title && html`<div class="title" style="margin-bottom:18px">${title}</div>`}`;
}
const Hint = ({ title, children }) => html`<div class="hint"><span class="over">${title}</span><p>${children}</p></div>`;

function ListenRow({ p }) {
  const cur = usePlaying();
  return html`<button class="row" style="padding:10px 0;min-height:0;text-align:left" onClick=${() => playPhrase(p)}>
    <div class="tx"><${IL} ru=${p.ru} size=${17} /><span style="margin-top:4px">${p.de}</span></div>
    <${Icon} n="volume_up" c=${cls('spk', cur?.key === p.audio && 'on')} /></button>`;
}

function SoundTipStep({ step, env }) {
  const d = step.day;
  useKeys({ Enter: env.onNext }, []);
  return html`<div class="scroll"><div class="stepc g16">
      <div><${Head} over=${`Tag ${dayNo(d.day)} · ${d.title}`} c /><div class="title">${d.goal}</div></div>
      <div class="card p"><span class="over amber">Aussprache</span><div class="h2 mt4">${d.sound.title}</div>
        <p class="mt8" style="font-size:14.5px;line-height:1.55;color:rgba(255,255,255,.82)">${d.sound.text}</p>
        ${step.examples.length > 0 && html`<div class="stack mt8" style="border-top:1px solid var(--border)">${step.examples.map((p) => html`<${ListenRow} p=${p} />`)}</div>`}</div>
      ${d.grammar && html`<${Hint} title="Satzbau">${d.grammar}<//>`}
    </div></div>
    <div class="bottom"><button class="btn" onClick=${env.onNext}>Los geht's<span class="kb">↵</span></button></div>`;
}

function IntroStep({ step, env }) {
  const p = step.phrase;
  useAutoPlay(p);
  useKeys({ Enter: env.onNext, ' ': () => playPhrase(p) }, []);
  return html`<div class="scroll"><div class="stepc center g16">
      <div class="over c">Neuer Satz</div>
      <${SentenceCard} phrase=${p} size=${p.plain.length > 22 ? 24 : 30} center />
      <${PhraseDock} phrase=${p} size="lg" />
    </div></div>
    <div class="bottom"><button class="btn" onClick=${env.onNext}>Weiter<span class="kb">↵</span></button></div>`;
}

function optState(picked, isRight, isPicked) {
  if (picked == null) return '';
  return isRight ? 'ok' : isPicked ? 'bad' : 'dim';
}

function ListenChooseStep({ step, env }) {
  const p = step.phrase;
  const [picked, setPicked] = useState(null);
  useAutoPlay(p);
  const pick = (o) => { if (picked != null) return; setPicked(o.id); env.onAnswer(o.id === p.id); };
  useKeys({ ...Object.fromEntries(step.options.map((o, i) => [KEYS[i], () => pick(o)])), ' ': () => playPhrase(p) }, [picked]);
  return html`<div class="scroll"><div class="stepc center">
      <${Head} over="Hören" title="Was bedeutet das?" c />
      <${PlayRow} phrase=${p} size=${76} />
      <div class="opts mt24">${step.options.map((o, i) => html`<${Opt} kb=${KEYS[i]} state=${optState(picked, o.id === p.id, o.id === picked)} onClick=${() => pick(o)}>${o.de}<//>`)}</div>
    </div></div>`;
}

function MeaningChooseStep({ step, env }) {
  const p = step.phrase;
  const [picked, setPicked] = useState(null);
  const pick = (o) => { if (picked != null) return; setPicked(o.id); env.onAnswer(o.id === p.id); playPhrase(p); };
  useKeys(Object.fromEntries(step.options.map((o, i) => [KEYS[i], () => pick(o)])), [picked]);
  return html`<div class="scroll"><div class="stepc">
      <div class="center"><div class="over c">Wie sagt man …</div><div class="big" style="margin:6px 0 22px">${p.de}</div></div>
      <div class="opts">${step.options.map((o, i) => html`<${Opt} kb=${KEYS[i]} state=${optState(picked, o.id === p.id, o.id === picked)} onClick=${() => pick(o)}>
        <${IL} ru=${o.ru} size=${18} gloss=${picked != null} /><//>`)}</div>
    </div></div>`;
}

function ReplyStep({ step, env }) {
  const q = step.question;
  const [picked, setPicked] = useState(null);
  const [showDe, setShowDe] = useState(false);
  useAutoPlay(q);
  const pick = (o) => {
    playPhrase(o);
    if (picked != null) return;
    setPicked(o.id); env.onAnswer(step.correctIds.has(o.id));
  };
  useKeys({ ...Object.fromEntries(step.options.map((o, i) => [KEYS[i], () => pick(o)])), ' ': () => playPhrase(q) }, [picked]);
  const open = showDe || picked != null;
  return html`<div class="scroll"><div class="stepc center">
      <${Head} over="Antworten" title="Was passt als Antwort?" c />
      <${PhraseBlock} phrase=${q} size=${24} showDe=${open} />
      <div class="mt12"><${PlayRow} phrase=${q} size=${56} /></div>
      ${!open && html`<button class="btn text" onClick=${() => setShowDe(true)}>Übersetzung zeigen</button>`}
      <div class="opts mt16">${step.options.map((o, i) => html`<${Opt} kb=${KEYS[i]} state=${optState(picked, step.correctIds.has(o.id), o.id === picked)} onClick=${() => pick(o)}>
        <${IL} ru=${o.ru} size=${18} gloss=${picked != null} />${picked != null && html`<div class="sub mt4">${o.de}</div>`}<//>`)}</div>
    </div></div>`;
}

function StressTapStep({ step, env }) {
  const p = step.phrase;
  const [picked, setPicked] = useState(null);
  useAutoPlay(p);
  const pick = (i) => { if (picked != null) return; setPicked(i); env.onAnswer(i === step.stressedIndex); };
  useKeys({ ...Object.fromEntries(step.syllables.map((_, i) => [String(i + 1), () => pick(i)])), ' ': () => playPhrase(p) }, [picked]);
  return html`<div class="scroll"><div class="stepc center">
      <${Head} over="Betonung" title="Welche Silbe ist betont?" c />
      <div class="muted">${p.de}</div>
      ${p.wordCount > 1 && html`<div class="cyr mt4" lang="ru">${p.plain}</div>`}
      <div class="mt20"><${PlayRow} phrase=${p} /></div>
      <div class="syl mt24">${step.syllables.map((s, i) => html`<button lang="ru" class=${picked == null ? '' : i === step.stressedIndex ? 'ok' : i === picked ? 'bad' : ''} onClick=${() => pick(i)}>${s}</button>`)}</div>
      <div class="sub mt20">Hör auf die Silbe, die länger und lauter klingt.</div>
    </div></div>`;
}

function Tile({ word, ghost, onClick, disabled }) {
  return html`<button class=${cls('tile', ghost && 'ghost')} onClick=${onClick} disabled=${disabled}>
    <b><${Latin} ru=${Ph.fromDisplay(word)} /></b><small lang="ru">${word}</small></button>`;
}

function BuildStep({ step, env }) {
  const p = step.phrase;
  const [chosen, setChosen] = useState([]);
  const [result, setResult] = useState(null);
  const check = () => {
    if (result != null || !chosen.length) return;
    const ok = Co.buildCorrect(step, chosen.map((i) => step.tiles[i]));
    setResult(ok); env.onAnswer(ok); playPhrase(p);
  };
  useKeys({ Enter: check, Backspace: () => result == null && setChosen((c) => c.slice(0, -1)) }, [chosen, result]);
  return html`<div class="scroll"><div class="stepc">
      <div class="center"><div class="over c">Satz bauen</div><div class="title" style="margin:6px 0 18px">${p.de}</div></div>
      <div class=${cls('tray', result === true && 'ok', result === false && 'bad')}>
        ${chosen.length === 0 && html`<span class="ph">Tippe die Wörter in der richtigen Reihenfolge an.</span>`}
        ${chosen.map((i, k) => html`<${Tile} word=${step.tiles[i]} key=${i} disabled=${result != null} onClick=${() => setChosen(chosen.filter((_, x) => x !== k))} />`)}</div>
      <div class="tiles mt24">${step.tiles.map((w, i) => html`<${Tile} word=${w} key=${i} ghost=${chosen.includes(i)} disabled=${result != null} onClick=${() => { audio.stop(); fx.tap(); setChosen([...chosen, i]); }} />`)}</div>
    </div></div>
    ${result == null && html`<div class="bottom"><button class="btn" disabled=${!chosen.length} onClick=${check}>Prüfen<span class="kb">↵</span></button></div>`}`;
}

function FirstWordHint({ ru }) {
  const first = useMemo(() => Gl.chunks(ru)[0], [ru]);
  if (!first) return null;
  return html`<div class="mt12"><div class="over">Starthilfe</div>
    <div class="il mt4" style="--fs:22px;align-items:center"><div class="il-l">${first.words.map((w, i) => html`${i ? ' ' : ''}${w.latin.map((x) => (x.stressed ? html`<u>${x.text}</u>` : x.text))}`)} …</div></div>
    ${first.gloss && html`<div class="sub">${first.gloss}</div>`}<div class="cyr" lang="ru">${first.words.map((w) => w.cyrillic).join(' ')}</div></div>`;
}

function SpeakStep({ step, env }) {
  const p = step.phrase;
  const [attempts, setAttempts] = useState(0);
  const [score, setScore] = useState(null);
  const [selfCheck, setSelfCheck] = useState(!speech.available());
  const [masked, setMasked] = useState(false);
  const [peekFirst, setPeekFirst] = useState(false);
  const [usedPeek, setUsedPeek] = useState(false);
  const [peekTick, setPeekTick] = useState(0);
  const [spoke, setSpoke] = useState(false);
  const answered = env.answered;
  useEffect(() => {
    if (answered) return undefined;
    setMasked(false);
    const t = setTimeout(() => setMasked(true), 2600);
    return () => clearTimeout(t);
  }, [peekTick]);
  const L = useListen((hyps) => {
    const sc = Sp.speech(p.ru, hyps);
    setScore(sc); setAttempts((a) => a + 1);
    if (sc.passed && !answered) { setSpoke(true); env.onSpokenOk(); env.onAnswer(true); } else fx.wrong();
  });
  useEffect(() => { if (L.error && L.error !== 'nospeech' && L.error !== 'nomatch') setSelfCheck(true); }, [L.error]);
  const mic = () => { if (L.listening) L.stop(); else { setScore(null); setMasked(true); L.start(p.ru); } };
  useKeys({ ' ': () => playPhrase(p), m: () => !answered && !selfCheck && mic() }, [answered, selfCheck, L.listening]);
  const visible = answered || !masked;

  return html`<div class="scroll"><div class="stepc center g16">
      <div><${Head} over="Sprechen" title=${answered ? null : 'Aus dem Kopf sagen'} c /></div>
      ${!answered && !masked && html`<div class="tag c-amber"><${Icon} n="visibility" />Kurz einprägen — der Text blendet gleich aus</div>`}
      ${answered && spoke && !usedPeek && html`<div class="tag c-words"><${Icon} n="psychology" />Frei aus dem Gedächtnis gesprochen</div>`}
      ${visible ? html`<${PhraseBlock} phrase=${p} size=${26} />` : html`<div class="card p" style="width:100%;border-color:rgba(var(--amber-rgb),.4)">
        <div class="over amber">Ohne Ablesen — sag auf Russisch</div><div class="title mt8">${p.de}</div>
        ${peekFirst && html`<${FirstWordHint} ru=${p.ru} />`}
        <div class="rowx g8 wrap mt16" style="justify-content:center">
          ${!peekFirst && html`<button class="chip sm" onClick=${() => setPeekFirst(true)}><${Icon} n="lightbulb" />1. Wort zeigen</button>`}
          <button class="chip sm" onClick=${() => { setUsedPeek(true); setPeekTick((x) => x + 1); }}><${Icon} n="visibility" />Kurz spicken</button></div></div>`}
      <${PlayRow} phrase=${p} size=${52} />
      ${!selfCheck && html`<div class="micwrap"><${Mic} L=${L} onClick=${mic} disabled=${answered} />
        <div class=${cls('mictxt', L.listening && 'on')}>${L.listening ? (L.partial ? html`»${L.partial}«<br />Tippe nochmal, wenn du fertig bist` : (L.opening ? MIC_WAIT : 'Ich höre zu … Tippe nochmal, wenn du fertig bist'))
          : L.evaluating ? 'Wird ausgewertet …' : answered ? '' : L.errorText ?? (score ? 'Tippen für einen weiteren Versuch' : 'Tippen und auf Russisch sprechen')}</div></div>
        ${score && html`<${ScoreCard} sc=${score} targetRu=${p.ru} />`}`}
      ${selfCheck && !answered && html`${L.errorText && html`<${Hint} title="Spracherkennung">${L.errorText}<//>`}
        <div class="sub">Nimm dich auf und vergleiche selbst mit dem Original.</div><${RecordCompare} phrase=${p} />`}
    </div></div>
    ${!answered && html`<div class="bottom">
      ${selfCheck && html`<button class="btn" onClick=${() => env.onAnswer(true)}>Klingt wie das Original</button>`}
      <div class="rowx g8">${!selfCheck && attempts >= 2 && html`<button class="btn ghost" onClick=${() => { L.cancel(); setSelfCheck(true); }}>Selbst vergleichen</button>`}
        <button class="btn ghost" onClick=${() => { L.cancel(); env.onSkip(); }}>Überspringen</button></div></div>`}`;
}

function Bubble({ line, partner, busy }) {
  const cur = usePlaying();
  const p = line.phrase;
  return html`<button class=${cls('bub', line.isUser && 'me', cur?.key === p.audio && 'on')} disabled=${busy} onClick=${() => playPhrase(p)}>
    <div class="who">${line.isUser ? 'DU' : partner.toUpperCase()}</div><${IL} ru=${p.ru} size=${17} /><div class="de">${p.de}</div>
    ${p.note && html`<div class="nt">${p.note}</div>`}</button>`;
}

function DialogStep({ step, env, onDone }) {
  const { lines } = step;
  const dlg = step.day.dialog;
  const partner = dlg.partner ?? 'Anja';
  const [started, setStarted] = useState(false);
  const [shown, setShown] = useState(0);
  const [busy, setBusy] = useState(false);
  const [wrong, setWrong] = useState([]);
  const [right, setRight] = useState(0);
  const [spoken, setSpoken] = useState(null);
  const scroller = useRef(null);
  const options = useRef(new Map());
  const playing = useRef(0);
  const cur = lines[shown];
  const userLines = lines.filter((l) => l.isUser).length;

  useEffect(() => { scroller.current?.scrollTo({ top: 1e6, behavior: 'smooth' }); }, [shown, started, spoken]);
  useEffect(() => {
    if (!started || !cur || cur.isUser || busy) return undefined;
    let alive = true;
    const t = setTimeout(async () => { await playPhrase(cur.phrase); if (alive) setShown((s) => s + 1); }, 350);
    return () => { alive = false; clearTimeout(t); };
  }, [started, shown, busy]);

  const opts = () => {
    if (!options.current.has(shown)) {
      const seen = new Set([cur.phrase.plain]);
      const others = lines.filter((l) => l.isUser && !seen.has(l.phrase.plain) && seen.add(l.phrase.plain)).sort(() => Math.random() - 0.5).slice(0, 2);
      options.current.set(shown, [...others, cur].sort(() => Math.random() - 0.5));
    }
    return options.current.get(shown);
  };
  const accept = async (viaSpeech) => {
    if (!wrong.length) setRight((r) => r + 1);
    if (viaSpeech) env.onSpokenOk();
    fx.right(); setWrong([]); setSpoken(null); setBusy(true); setShown((s) => s + 1);
    await playPhrase(cur.phrase);
    setBusy(false);
  };
  const pick = (o) => {
    if (busy || wrong.includes(o.phrase.id)) return;
    if (o.phrase.plain === cur.phrase.plain) accept(false); else { fx.wrong(); setWrong([...wrong, o.phrase.id]); }
  };
  const L = useListen((hyps) => { const sc = Sp.speech(cur.phrase.ru, hyps); if (sc.passed) accept(true); else { fx.wrong(); setSpoken(sc); } });
  const playAll = async () => {
    const token = ++playing.current;
    for (const l of lines) { if (playing.current !== token) return; if (!(await playPhrase(l.phrase))) return; }
  };
  const done = shown >= lines.length;
  useKeys({ Enter: () => (!started ? setStarted(true) : done && !busy ? onDone(right, userLines) : null) }, [started, done, busy, right]);

  return html`<div class="scroll" ref=${scroller}><div class="stepc g12">
      <div><div class="over c">Rollenspiel · ${dlg.title}</div><p class="muted mt4">${dlg.scene}</p></div>
      ${lines.slice(0, shown).map((l) => html`<${Bubble} line=${l} partner=${partner} busy=${busy} key=${l.phrase.id} />`)}
      ${started && cur && !cur.isUser && html`<div class="typing"><i></i><i></i><i></i></div>`}
    </div></div>
    ${!started && html`<div class="bottom"><p class="sub center" style="padding:0 12px 6px">${partner} spricht, du antwortest. Wähl deine Antwort oder sprich sie — danach hörst du sie.</p>
      <button class="btn" onClick=${() => setStarted(true)}>Gespräch starten<span class="kb">↵</span></button></div>`}
    ${started && cur?.isUser && !busy && html`<div class="fb" style="gap:10px">
      <div class="rowx g10"><div class="grow"><div class="over">Du willst sagen</div><div style="font-size:16px;font-weight:500" class="mt4">${cur.phrase.de}</div></div>
        ${speech.available() && html`<${Mic} small L=${L} onClick=${() => { setSpoken(null); L.toggle(cur.phrase.ru); }} />`}</div>
      ${L.listening && html`<div class="sub amber">${L.partial ? `»${L.partial}«` : (L.opening ? MIC_WAIT : 'Ich höre zu … sprich deine Antwort')}</div>`}
      ${!L.listening && L.errorText && html`<div class="sub">${L.errorText}</div>`}
      ${spoken && html`<${ScoreCard} sc=${spoken} targetRu=${cur.phrase.ru} />`}
      <div class="opts" style="gap:8px">${opts().map((o) => html`<${Opt} state=${wrong.includes(o.phrase.id) ? 'bad' : ''} onClick=${() => pick(o)}><${IL} ru=${o.phrase.ru} size=${16} gloss=${false} /><//>`)}</div>
      ${cur.line.gloss && html`<div class="faint" style="font-size:12px">${cur.line.gloss}</div>`}</div>`}
    ${done && !busy && html`<div class="bottom"><div class="rowx g8"><button class="btn ghost" onClick=${playAll}><${Icon} n="play_circle" />Ganz anhören</button>
      <button class="btn" onClick=${() => { playing.current++; onDone(right, userLines); }}>Weiter<span class="kb">↵</span></button></div></div>`}`;
}

/** Haltepunkt zwischen zwei Etappen: zeigen, was geschafft ist, was kommt und wie lange es dauert. */
function StageBreakStep({ step, env }) {
  const e = env.engine;
  const done = step.stage + 1;
  const nextStage = e.stages[done];
  const mins = Pl.toMinutes(e.stageEst[done] ?? 0, planState.pace());
  useEffect(() => { fx.win(); }, []);
  useKeys({ Enter: env.onNext }, []);
  return html`<div class="scroll"><div class="stepc center g16" style="justify-content:center;min-height:100%">
      <div class="stagedone"><${Icon} n="check" /></div>
      <div><div class="over c">Etappe ${done} von ${e.stages.length} geschafft</div>
        <div class="title mt4">${e.stages[step.stage].title} ist durch.</div></div>
      <${StageList} stages=${e.stages} seconds=${e.stageEst} done=${done} current=${done} />
      <p class="sub">Guter Punkt für eine Pause: Dein Stand bleibt gespeichert, du machst genau hier weiter.</p>
    </div></div>
    <div class="bottom"><button class="btn" onClick=${env.onNext}>Weiter mit »${nextStage.title}« · ${mins} Min.<span class="kb">↵</span></button>
      <button class="btn ghost" onClick=${env.onPause}><${Icon} n="pause" />Pause machen</button></div>`;
}

/** Gesprächstag: Anja fragt, du antwortest frei. Jede passende Antwort zählt. */
function AnswerStep({ step, env }) {
  // Anja stellt die Frage: mit der weiblichen Stimme, sofern der Satz für beide Geschlechter gleich lautet.
  const q = useMemo(() => (D.index.itemById.get(step.question.id)?.ruF == null ? { ...step.question, audio: `${step.question.id}_f` } : step.question), []);
  const [result, setResult] = useState(null);
  const [attempts, setAttempts] = useState(0);
  const [show, setShow] = useState(false);
  const [selfCheck, setSelfCheck] = useState(!speech.available());
  const answered = env.answered;
  useAutoPlay(q);
  const L = useListen((hyps) => {
    const scored = step.replies.map((r) => ({ r, sc: Sp.speech(r.ru, hyps) })).sort((a, b) => b.sc.score - a.sc.score);
    const hit = scored.find((x) => x.sc.passed);
    setAttempts((a) => a + 1);
    if (hit && !answered) { setResult(hit); env.onSpokenOk(); env.onAnswer(true); playPhrase(hit.r); } else { fx.wrong(); setResult(scored[0]); }
  });
  useEffect(() => { if (L.error && L.error !== 'nospeech' && L.error !== 'nomatch') setSelfCheck(true); }, [L.error]);
  const mic = () => { if (L.listening) L.stop(); else { setResult(null); L.start(step.replies[0].ru); } };
  const open = show || answered;
  useKeys({ ' ': () => playPhrase(q), m: () => !answered && !selfCheck && mic() }, [answered, selfCheck, L.listening]);
  return html`<div class="scroll"><div class="stepc center g16">
      <${Head} over="Frei antworten" title=${answered ? null : 'Anja fragt dich'} c />
      <${PhraseBlock} phrase=${q} size=${24} />
      <${PlayRow} phrase=${q} size=${52} />
      ${!selfCheck && html`<div class="micwrap"><${Mic} L=${L} onClick=${mic} disabled=${answered} />
        <div class=${cls('mictxt', L.listening && 'on')}>${L.listening ? (L.partial ? html`»${L.partial}«<br />Tippe nochmal, wenn du fertig bist` : (L.opening ? MIC_WAIT : 'Ich höre zu … antworte auf Russisch'))
          : L.evaluating ? 'Wird ausgewertet …' : answered ? '' : L.errorText ?? (result ? 'Tippen für einen weiteren Versuch' : 'Tippen und frei antworten')}</div></div>`}
      ${result && html`<${ScoreCard} sc=${result.sc} targetRu=${result.r.ru} />`}
      ${selfCheck && !answered && html`<div class="sub">Antworte laut — dann vergleiche mit den Beispielen unten.</div>`}
      ${open && html`<div style="width:100%;text-align:left"><div class="over">${answered ? 'Passende Antworten' : 'So könntest du antworten'}</div>
        <div class="list mt8">${step.replies.map((r) => html`<div class="row" style="min-height:0"><div class="tx"><${IL} ru=${r.ru} size=${16} gloss=${false} /><span>${r.de}</span></div><${Speaker} phrase=${r} /></div>`)}</div></div>`}
    </div></div>
    ${!answered && html`<div class="bottom">
      ${selfCheck && html`<button class="btn" disabled=${!show} onClick=${() => env.onAnswer(true)}>Ich habe passend geantwortet</button>`}
      <div class="rowx g8">${!show && html`<button class="btn ghost" onClick=${() => setShow(true)}>Antworten zeigen</button>`}
        ${show && !selfCheck && html`<button class="btn ghost" onClick=${() => { L.cancel(); env.onAnswer(false); }}>Wusste ich nicht</button>`}
        <button class="btn ghost" onClick=${() => { L.cancel(); env.onSkip(); }}>Überspringen</button></div></div>`}`;
}

const STEPS = { soundTip: SoundTipStep, intro: IntroStep, listenChoose: ListenChooseStep, meaningChoose: MeaningChooseStep, stressTap: StressTapStep, build: BuildStep, speak: SpeakStep, reply: ReplyStep,
  stageBreak: StageBreakStep, pattern: PatternStep, answer: AnswerStep };

// ── Rückmeldung & Abschluss ────────────────────────────────────────────────

function FeedbackBar({ fb, step, onNext }) {
  useKeys({ Enter: onNext }, [onNext]);
  const solution = ['listenChoose', 'meaningChoose', 'stressTap', 'build'].includes(step.type) ? step.phrase : null;
  if (fb.skipped) return html`<div class="bottom"><button class="btn" onClick=${onNext}>Weiter<span class="kb">↵</span></button></div>`;
  return html`<div class=${cls('fb', fb.correct ? 'ok' : 'bad')}>
    <div class="hdr"><${Icon} n=${fb.correct ? 'check_circle' : 'error'} fill />${fb.correct ? 'Richtig!' : solution ? 'Noch nicht — die Lösung:' : 'Noch nicht ganz.'}
      ${fb.gained > 0 && html`<span class="xp">+${fb.gained} XP</span>`}</div>
    ${solution && html`<div class="rowx g8"><div class="grow"><${PhraseBlock} phrase=${solution} size=${19} center=${false} /></div><${Speaker} phrase=${solution} /></div>`}
    ${fb.requeued && html`<div class="faint" style="font-size:12.5px">Kommt am Ende noch einmal.</div>`}
    <button class=${cls('btn', fb.correct ? 'good' : 'bad')} onClick=${onNext}>Weiter<span class="kb">↵</span></button>
  </div>`;
}

const OVER = { TALKDAY: (d) => `Tag ${d}: Gesprächstag geschafft`, LESSON: (d) => `Tag ${dayNo(d)} geschafft`, PODCAST: (d) => `Podcast-Lektion ${d} geschafft`, REVIEW: () => 'Wiederholung fertig', DIALOG: () => 'Gespräch geschafft', TEST: () => 'Test geschafft' };

function SessionSummary({ mode, day, s, onClose }) {
  useKeys({ Enter: onClose }, []);
  if (s.empty) {
    return html`<${Summary} over="Alles erledigt" title=${mode === 'REVIEW' ? 'Gerade ist nichts zur Wiederholung fällig.' : 'Hier gibt es nichts zu tun.'}
      note=${mode === 'TEST' ? 'Wähle im Satzbuch die Sätze aus, die du testen möchtest.' : null} actions=${html`<button class="btn" onClick=${onClose}>Zurück</button>`} />`;
  }
  const line = s.stars === 3 ? `${Ph.transliterateFlat('Отл+ично!')} Stark gemacht.` : s.stars === 2 ? `${Ph.transliterateFlat('Хорош+о!')} Gut gemacht.` : 'Gut, dass du dranbleibst — Wiederholen hilft.';
  return html`<${Summary} celebrate=${s.stars >= 2} over=${OVER[mode](day)} stars=${s.stars} title=${line}
    tiles=${[{ v: `+${s.xp}`, l: 'XP', c: 'var(--xp)' }, { v: s.total ? `${Math.floor((s.correct * 100) / s.total)} %` : '–', l: 'Treffer' },
      { v: s.streak, l: s.streak === 1 ? 'Tag Serie' : 'Tage Serie', icon: 'local_fire_department', c: 'var(--amber)' }]}
    actions=${html`<button class="btn" onClick=${onClose}>Fertig<span class="kb">↵</span></button>`}>
    ${s.wrong.length > 0 && html`<div style="width:100%;text-align:left" class="mt24"><div class="over">Diese Sätze kommen bald wieder</div>
      <div class="list mt8">${s.wrong.slice(0, 6).map((p) => html`<div class="row" style="min-height:0"><div class="tx"><${IL} ru=${p.ru} size=${16} gloss=${false} /><span>${p.de}</span></div><${Speaker} phrase=${p} /></div>`)}</div></div>`}
  <//>`;
}

export function Session({ mode, day }) {
  const [, force] = useState(0);
  const [leave, setLeave] = useState(false);
  const ref = useRef(null);
  if (!ref.current) ref.current = new Engine(mode, day, () => force((x) => x + 1));
  const e = ref.current;
  useEffect(() => () => audio.stop(), []);
  const close = () => { audio.stop(); back(mode === 'PODCAST' ? `podcast/${day}` : ['LESSON', 'DIALOG', 'TALKDAY'].includes(mode) ? 'plan' : mode === 'TEST' ? 'satzbuch' : 'heute'); };
  if (e.summary) return html`<${SessionSummary} mode=${mode} day=${day} s=${e.summary} onClose=${close} />`;
  const step = e.current;
  const env = { engine: e, answered: e.feedback != null, onAnswer: (ok) => e.answer(ok), onSkip: () => e.skip(), onSpokenOk: () => e.spokenCorrectly(), onNext: () => e.next(), onPause: close };
  // Trennstriche im Balken an den Haltepunkten zwischen den Etappen
  const marks = e.stages ? e.steps.map((x, i) => (x.type === 'stageBreak' ? i / e.steps.length : null)).filter((x) => x != null) : null;
  const C = STEPS[step.type];
  const tryClose = () => ((e.pos === 0 && !e.feedback) || step.type === 'stageBreak' ? close() : setLeave(true));
  return html`<div class=${cls('screen', MODE[mode].c)}>
    <${SessionBar} value=${e.pos / e.steps.length} count=${e.stages ? `Etappe ${e.stageIndex + 1}/${e.stages.length}` : `${e.pos + 1}/${e.steps.length}`} marks=${marks} onClose=${tryClose} />
    ${step.type === 'dialog'
      ? html`<${DialogStep} key=${e.pos} step=${step} env=${env} onDone=${(r, n) => e.dialogDone(r, n)} />`
      : html`<${C} key=${e.pos} step=${step} env=${env} />`}
    ${e.feedback && html`<${FeedbackBar} fb=${e.feedback} step=${step} onNext=${() => e.next()} />`}
    ${leave && html`<${LeaveSheet} saved=${!!e.snapKey} onStay=${() => setLeave(false)} onLeave=${close} />`}
  </div>`;
}
