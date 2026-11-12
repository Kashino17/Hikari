// Sprech-Modi: Sprech-Reflex, Kombi-Meister, Blind-Coach, Sprich frei.
// Regeln und Wertung wie in RussianReflex.kt / RussianTalk.kt.
import { html, cls, Icon, IL, PhraseBlock, PlayRow, Latin, Mic, ScoreCard, Summary, Speaker, useListen, useKeys, playPhrase, useState, useEffect, useRef, useMemo, fx, audio, tts, MIC_WAIT } from '../ui.js';
import { D, P, progress, back, learnedPool, myPhrases, phraseById, takeSelection } from '../app.js';
import { speech as recog, serverVoice } from '../platform.js';
import * as Co from '../domain/course.js';
import * as Sp from '../domain/speech.js';
import * as Ct from '../domain/content.js';
import * as Ph from '../domain/phonetics.js';
import * as Gl from '../domain/glossary.js';
import { Intro, GameBar } from './games.js';
import { weightedOrder } from '../learner.js';

const uniq = (list) => { const seen = new Set(); return list.filter((p) => p && !seen.has(p.id) && seen.add(p.id)); };
const box = (id) => progress.get().cards[id]?.box ?? 0;

/** Sätze für Reflex und Blind-Coach: fällige/wackelige zuerst, dazu sichere, eigene und frische. */
function buildReflexQueue(count = 7) {
  const rng = Co.defaultRng();
  const p = progress.get();
  const s = progress.settings();
  const selection = takeSelection();
  if (selection.length) {
    const resolved = rng.shuffled(selection.map((id) => phraseById(id, s)).filter(Boolean));
    if (resolved.length) return rng.shuffled(uniq([...resolved.filter((x) => box(x.id) <= 2).slice(0, 6), ...resolved.filter((x) => box(x.id) >= 3).slice(0, 4), ...resolved])).slice(0, 10);
  }
  const today = progress.today();
  const resolve = (ids) => ids.map((id) => D.index.phrase(id, s) ?? D.podIndex.phrase(id, s)).filter(Boolean);
  const weakIds = Object.entries(p.cards).filter(([, c]) => c.box <= 2).map(([id]) => id);
  const dueAndWeak = resolve([...new Set([...rng.shuffled(P.dueIds(p.cards, today)), ...rng.shuffled(weakIds)])]);
  const mastered = resolve(rng.shuffled(Object.entries(p.cards).filter(([, c]) => c.box >= 3).map(([id]) => id)));
  const fresh = [...rng.shuffled(myPhrases()).slice(0, 2), ...weightedOrder(D.index.phrasesUpTo(P.unlockedDay(p, D.index.days.length), s), rng)];
  return rng.shuffled(uniq([...dueAndWeak.slice(0, Math.max(Math.floor(count * 0.45), 2)), ...mastered.slice(0, Math.max(Math.floor(count * 0.35), 2)), ...fresh]).slice(0, count));
}

function FirstWord({ ru }) {
  const first = useMemo(() => Gl.chunks(ru)[0], [ru]);
  if (!first) return null;
  return html`<div class="mt12"><div class="over">Starthilfe</div><div class="il mt4 center" style="--fs:22px"><div class="il-l">${first.words.map((w, i) => html`${i ? ' ' : ''}${w.latin.map((x) => (x.stressed ? html`<u>${x.text}</u>` : x.text))}`)} …</div></div>
    ${first.gloss && html`<div class="sub">${first.gloss}</div>`}</div>`;
}

const micText = (L, idle) => (L.listening ? (L.partial ? `»${L.partial}«` : (L.opening ? MIC_WAIT : 'Ich höre zu … tippe nochmal, wenn du fertig bist')) : L.evaluating ? 'Wird ausgewertet …' : L.errorText ?? idle);

// ── Sprech-Reflex ──────────────────────────────────────────────────────────

const WINDOW = 4500;

function ReflexRound({ phrase, repeat, streak, onDone }) {
  const [left, setLeft] = useState(WINDOW);
  const [fast, setFast] = useState(false);
  const [hint, setHint] = useState(0);
  const [flash, setFlash] = useState(false);
  const [passed, setPassed] = useState(false);
  const [score, setScore] = useState(null);
  const leftRef = useRef(WINDOW);
  const L = useListen((hyps) => {
    const sc = Sp.speech(phrase.ru, hyps);
    setScore(sc);
    if (sc.passed) { setPassed(true); fx.right(); playPhrase(phrase); } else fx.wrong();
  });
  useEffect(() => { if (recog.available()) L.start(phrase.ru); }, []);
  useEffect(() => {
    if (passed) return undefined;
    const t = setInterval(() => { leftRef.current = Math.max(0, leftRef.current - 100); setLeft(leftRef.current); }, 100);
    return () => clearInterval(t);
  }, [passed]);
  useEffect(() => { if (L.partial && leftRef.current > 0) setFast(true); }, [L.partial]);
  const peek = async () => {
    setHint(2); setFlash(true); L.cancel();
    setTimeout(() => setFlash(false), 2600);
    await playPhrase(phrase);
    if (recog.available()) L.start(phrase.ru);
  };
  const said = () => { L.cancel(); setPassed(true); playPhrase(phrase); };
  const mic = () => { if (L.listening) L.stop(); else { audio.stop(); setScore(null); L.start(phrase.ru); } };
  const blind = hint === 0;
  const next = () => onDone(blind, fast && blind);
  useKeys({ Enter: () => passed && next(), m: () => !passed && mic() }, [passed, L.listening, hint, fast]);

  return html`<div class="scroll"><div class="stepc center g12">
      <div class="rowx" style="width:100%;justify-content:space-between;min-height:26px"><div class=${cls('over', repeat ? 'amber' : 'c')}>${repeat ? 'Nochmal — diesmal ohne Hilfe' : 'Sag es frei auf Russisch'}</div>
        ${streak >= 2 && html`<span class="combo"><${Icon} n="local_fire_department" fill />${streak}× blind</span>`}</div>
      <div class=${cls('timer', left <= 1500 && 'low')} style="width:100%;flex:none"><i style=${`width:${(left / WINDOW) * 100}%`}></i></div>
      <div class="sub" style="min-height:20px">${passed ? (blind && fast ? 'Blitz-Reflex — unter 4,5 Sekunden abgerufen' : blind ? 'Stark — komplett frei gesprochen' : 'Gesprochen. Der Satz kommt gleich noch einmal blind.')
        : left > 0 ? `Reflex-Zeit: ${(left / 1000).toFixed(1).replace('.', ',')} s — sprich sofort los` : 'Kein Stress — überleg kurz oder nimm die Starthilfe'}</div>
      <div class="card p" style=${`width:100%;border-color:${passed ? 'var(--good)' : 'rgba(var(--amber-rgb),.45)'}`}>
        <div class="sub">Wie sagst du auf Russisch:</div><div class="title mt8">${phrase.de}</div>
        ${hint === 1 && !passed && !flash && html`<${FirstWord} ru=${phrase.ru} />`}
        ${(flash || passed) && html`<div class="mt16">${!passed && html`<div class="over amber" style="margin-bottom:8px">Einprägen — verschwindet gleich wieder</div>`}
          <${PhraseBlock} phrase=${phrase} size=${22} showDe=${false} gloss />${passed && html`<div class="mt12"><${PlayRow} phrase=${phrase} size=${48} /></div>`}</div>`}
      </div>
      ${!passed && html`<div class="rowx g8 wrap" style="justify-content:center">
          ${hint < 1 && html`<button class="chip" onClick=${() => setHint(1)}><${Icon} n="lightbulb" />1. Wort zeigen</button>`}
          <button class="chip" onClick=${peek}><${Icon} n="visibility" />Hören und spicken</button></div>
        ${recog.available() && html`<div class="micwrap mt8"><${Mic} L=${L} onClick=${mic} /><div class=${cls('mictxt', L.listening && 'on')}>${micText(L, 'Tippe aufs Mikrofon und sprich aus dem Kopf')}</div></div>`}`}
      ${score && html`<${ScoreCard} sc=${score} targetRu=${phrase.ru} />`}
    </div></div>
    <div class="bottom">${passed
      ? html`<button class=${cls('btn', blind && 'good')} onClick=${next}>${blind ? 'Stark! Nächster Satz' : 'Verstanden — kommt gleich nochmal'}<span class="kb">↵</span></button>`
      : html`<button class="btn ghost" onClick=${said}>Ich hab's laut gesagt</button>`}</div>`;
}

export function Reflex() {
  const [queue, setQueue] = useState(() => buildReflexQueue(7));
  const [phase, setPhase] = useState('intro');
  const [idx, setIdx] = useState(0);
  const [score, setScore] = useState(0);
  const [streak, setStreak] = useState(0);
  const [requeued, setRequeued] = useState([]);
  const res = useRef({ newBest: false, blind: 0 });
  const close = () => { audio.stop(); back('ueben'); };
  const start = () => { res.current = { newBest: false, blind: 0 }; setIdx(0); setScore(0); setStreak(0); setRequeued([]); setPhase('run'); };
  const done = (blind, fastReflex) => {
    const ph = queue[idx];
    progress.apply({ xp: blind ? 12 : 6, graded: ph.id.startsWith('dd:') ? {} : { [ph.id]: blind }, spokenOk: 1 });
    let total = score;
    let q = queue;
    if (blind) { const st = streak + 1; setStreak(st); res.current.blind++; total += (fastReflex ? 25 : 15) + Math.min(st, 5) * 3; }
    else { setStreak(0); total += 5; q = [...queue]; q.splice(Math.min(idx + 3, q.length), 0, ph); setQueue(q); setRequeued([...requeued, ph.id]); }
    setScore(total);
    if (idx + 1 < q.length) { setIdx(idx + 1); return; }
    const old = progress.get().bestReflex;
    res.current.newBest = total > old;
    progress.update((p) => ({ ...p, bestReflex: Math.max(p.bestReflex, total) }));
    setPhase('over');
  };
  if (!queue.length) return html`<${Intro} c="c-speak" icon="bolt" over="Sprechen" title="Sprech-Reflex" text="Starte zuerst Tag 1 — danach trainierst du hier deinen freien Sprech-Reflex." cta="Zurück" onStart=${close} onClose=${close} />`;
  if (phase === 'intro') {
    return html`<${Intro} c="c-speak" icon="bolt" over="Gegen den Sprech-Blackout" title="Sprech-Reflex"
      text="Im echten Gespräch gibt es keine Untertitel. Du siehst nur den deutschen Gedanken und sagst ihn frei auf Russisch."
      points=${[['timer', '4,5 Sekunden Reflex-Zeit — schnell gibt Bonuspunkte'], ['lightbulb', 'Starthilfe erlaubt — der Satz kommt dann noch einmal blind'], ['local_fire_department', 'Jeder Satz ohne Hilfe verlängert deine Serie']]}
      record=${progress.get().bestReflex > 0 ? `Dein Rekord: ${progress.get().bestReflex}` : null} cta="Training starten" onStart=${start} onClose=${close} />`;
  }
  if (phase === 'over') {
    return html`<${Summary} celebrate=${res.current.newBest} over="Sprech-Reflex abgeschlossen" big=${score} title="Punkte"
      note=${res.current.newBest ? 'Neuer persönlicher Rekord!' : `Rekord: ${progress.get().bestReflex}`}
      tiles=${[{ v: res.current.blind, l: 'ohne Hilfe', c: 'var(--good)' }, { v: queue.length, l: 'Runden' }, { v: progress.get().spokenOk, l: 'gesprochen insgesamt' }]}
      actions=${html`<div class="rowx g8"><button class="btn ghost" onClick=${close}>Fertig</button><button class="btn" onClick=${() => { setQueue(buildReflexQueue(7)); start(); }}>Neue Runde</button></div>`} />`;
  }
  const ph = queue[idx];
  return html`<div class="screen c-speak">
    <${GameBar} onClose=${close}><div class="pbar" style="flex:1;height:8px;background:var(--high)"><i style=${`width:${(idx / queue.length) * 100}%`}></i></div><div class="cnt hot" style="min-width:60px">${score} Pkt</div><//>
    <${ReflexRound} key=${`${idx}-${ph.id}`} phrase=${ph} repeat=${requeued.includes(ph.id)} streak=${streak} onDone=${done} />
  </div>`;
}

// ── Kombi-Meister ──────────────────────────────────────────────────────────

const CONNECTORS = [['Ч+естно говор+я,', 'Ehrlich gesagt:'], ['На с+амом д+еле,', 'In Wirklichkeit:'], ['Я д+умаю, что', 'Ich denke, dass'], ['Для мен+я гл+авное —', 'Für mich ist das Wichtigste:']];
const lower = (s) => (s ? s.replace(/^(\+?)(.)/, (m, a, b) => a + b.toLowerCase()) : s);
const noDot = (s) => s.replace(/[.!]+$/, '');

function buildKombi(count = 8) {
  const rng = Co.defaultRng();
  const p = progress.get();
  const s = progress.settings();
  const selection = takeSelection();
  const ids = selection.length ? selection : [...new Set([...rng.shuffled(Object.entries(p.cards).filter(([, c]) => c.box >= 3).map(([id]) => id)), ...rng.shuffled(Object.keys(p.cards))])];
  const base = uniq([...ids.map((id) => phraseById(id, s)), ...rng.shuffled(D.index.phrasesUpTo(P.unlockedDay(p, D.index.days.length), s))]);
  const statements = base.filter((x) => x.wordCount >= 2 && x.wordCount <= 7 && !/[?!]$/.test(x.plain.trim()));
  const questions = base.filter((x) => x.wordCount >= 2 && x.wordCount <= 7 && x.plain.trim().endsWith('?'));
  const reactions = base.filter((x) => x.wordCount <= 2 && !x.plain.trim().endsWith('?'));
  const out = [];
  rng.shuffled(statements).slice(0, 3).forEach((st, i) => {
    const [cRu, cDe] = CONNECTORS[(i + rng.int(4)) % 4];
    out.push({ title: 'Gelerntes neu einbetten', de: `${cDe} ${st.de}`, ru: `${cRu} ${lower(st.ru)}`, blocksDe: [cDe.replace(/:$/, ''), st.de], blocksRu: [cRu.replace(/,$/, ''), st.ru], sourceIds: [st.id] });
  });
  rng.shuffled(statements).slice(0, 2).forEach((st) => {
    const q = rng.pick(questions.filter((x) => x.id !== st.id)) ?? null;
    const [fRu, fDe] = q ? [q.ru, q.de] : ['А к+ак у теб+я?', 'Und wie ist es bei dir?'];
    out.push({ title: 'Erklären und zurückfragen', de: `${st.de} — ${fDe}`, ru: `${st.ru} ${fRu}`, blocksDe: [st.de, fDe], blocksRu: [st.ru, fRu], sourceIds: [st.id, q?.id].filter(Boolean) });
  });
  if (statements.length >= 4) {
    const [a, b] = rng.shuffled(statements);
    out.push({ title: 'Zwei Gedanken mit »но« verbinden', de: `${noDot(a.de)}, aber ${lower(b.de)}`, ru: `${noDot(a.ru)}, но ${lower(b.ru)}`, blocksDe: [a.de, 'aber', b.de], blocksRu: [a.ru, 'но', b.ru], sourceIds: [a.id, b.id] });
  }
  rng.shuffled(D.patterns).slice(0, 4).forEach((pat) => {
    const choices = pat.slots.map((sl) => rng.int(sl.options.length));
    const built = Ct.buildPattern(pat, choices, s.gender);
    const opt = pat.slots.map((sl, i) => sl.options[choices[i]]);
    const f = s.gender === 'FEMALE';
    out.push({ title: pat.title, de: built.de, ru: built.ru, blocksDe: opt.map((o) => (f && o.deF) || o.de), blocksRu: opt.map((o) => (f && o.ruF) || o.ru), sourceIds: [] });
  });
  for (let i = 0; i < 2; i++) {
    const r = rng.pick(reactions);
    const st = rng.pick(statements);
    if (!r || !st || r.id === st.id) continue;
    out.push({ title: 'Reaktion und Gedanke verbinden', de: `${noDot(r.de)} — ${st.de}`, ru: `${noDot(r.ru)}, ${lower(st.ru)}`, blocksDe: [r.de, st.de], blocksRu: [r.ru, st.ru], sourceIds: [] });
  }
  return rng.shuffled(out).slice(0, count);
}

function KombiRound({ item, onDone }) {
  const [showRu, setShowRu] = useState(false);
  const [solved, setSolved] = useState(false);
  const [score, setScore] = useState(null);
  const phrase = useMemo(() => Ct.asPhrase(item.ru, item.de), [item]);
  const say = () => playPhrase(phrase);
  const L = useListen((hyps) => {
    const sc = Sp.speech(item.ru, hyps);
    setScore(sc);
    if (sc.passed) {
      setSolved(true); fx.right();
      for (const id of item.sourceIds) progress.gradeCard(id, true, 2, 0);
      progress.saveMySentence({ ru: item.ru, de: item.de, patternId: 'kombi', note: `Kombi-Meister · ${item.title}` });
      say();
    } else fx.wrong();
  });
  useEffect(() => { if (recog.available()) L.start(item.ru); }, []);
  const reveal = () => { L.cancel(); setShowRu(true); setSolved(true); say(); };
  const mic = () => { if (L.listening) L.stop(); else { audio.stop(); setScore(null); L.start(item.ru); } };
  useKeys({ Enter: () => solved && onDone(showRu ? 10 : 25) }, [solved, showRu]);
  return html`<div class="scroll"><div class="stepc center g12">
      <div><div class="over c">Bausteine kombinieren</div><div class="sub mt4">${item.title}</div></div>
      <div class="card p" style=${`width:100%;border-color:${solved ? 'var(--good)' : 'rgba(var(--c),.4)'}`}>
        <div class="sub">Kombiniere auf Russisch:</div><div class="title mt8">${item.de}</div>
        <div class="stack g8 mt16" style="text-align:left">${item.blocksDe.map((b, i) => html`<div class="rowx g10" style="padding:9px 12px;border-radius:12px;background:var(--high)">
          <b class="cc num">${i + 1}</b>${showRu || solved ? html`<span style="font-weight:600"><${Latin} ru=${item.blocksRu[i]} /></span>` : html`<span class="muted">${b}</span>`}</div>`)}</div>
        ${solved && html`<div class="mt16"><${IL} ru=${item.ru} size=${21} center /></div>`}
      </div>
      ${!solved && html`${!showRu && html`<button class="chip" onClick=${() => setShowRu(true)}><${Icon} n="lightbulb" />Russische Bausteine zeigen</button>`}
        ${recog.available() && html`<div class="micwrap mt8"><${Mic} L=${L} onClick=${mic} /><div class=${cls('mictxt', L.listening && 'on')}>${micText(L, 'Tippe aufs Mikrofon und sprich den kombinierten Satz')}</div></div>`}`}
      ${score && html`<${ScoreCard} sc=${score} targetRu=${item.ru} />`}
    </div></div>
    <div class="bottom">${solved
      ? html`<div class="rowx g8"><button class="btn ghost" onClick=${say}><${Icon} n="volume_up" />Anhören</button><button class="btn" onClick=${() => onDone(showRu ? 10 : 25)}>Weiter · +${showRu ? 10 : 25}<span class="kb">↵</span></button></div>`
      : html`<button class="btn ghost" onClick=${reveal}>Lösung hören und vergleichen</button>`}</div>`;
}

export function Kombi() {
  const [items, setItems] = useState(() => buildKombi(8));
  useEffect(() => { serverVoice.warm(); }, []); // gebaute Sätze spricht die Kursstimme vom Mac
  const [phase, setPhase] = useState('intro');
  const [round, setRound] = useState(0);
  const [score, setScore] = useState(0);
  const res = useRef(false);
  const close = () => { audio.stop(); back('ueben'); };
  const done = (pts) => {
    const total = score + pts;
    setScore(total);
    if (round + 1 < items.length) { setRound(round + 1); return; }
    const old = progress.get().bestKombi;
    res.current = total > old;
    progress.apply({ xp: Math.max(Math.floor(total / 4), 10), graded: {}, spokenOk: 1 });
    progress.update((p) => ({ ...p, bestKombi: Math.max(p.bestKombi, total) }));
    setPhase('over');
  };
  const start = () => { setRound(0); setScore(0); setPhase('run'); };
  if (!items.length) return html`<${Intro} c="c-words" icon="extension" over="Grammatik" title="Kombi-Meister" text="Schließ zuerst Tag 1 ab — dann gibt es genug Bausteine zum Kombinieren." cta="Zurück" onStart=${close} onClose=${close} />`;
  if (phase === 'intro') {
    return html`<${Intro} c="c-words" icon="extension" over="Bausteine live kombinieren" title="Kombi-Meister"
      text="Im echten Gespräch sagst du selten einen auswendig gelernten Satz auf. Hier würfelt Hikari deine Sätze und Muster neu zusammen — und du sprichst die Kombination frei."
      points=${[['mic', '25 Punkte, wenn du ohne russische Bausteine sprichst'], ['lightbulb', '10 Punkte mit Hilfe'], ['bookmark', 'Gesprochene Kombis landen in »Meine Sätze«']]}
      record=${progress.get().bestKombi > 0 ? `Dein Rekord: ${progress.get().bestKombi}` : null} cta="Kombi-Training starten" onStart=${start} onClose=${close} />`;
  }
  if (phase === 'over') {
    return html`<${Summary} celebrate=${res.current} over="Kombi-Training geschafft" big=${score} title="Punkte"
      note=${res.current ? 'Neuer Kombi-Rekord!' : 'Je öfter du Bausteine frei mischst, desto schneller reagierst du im Gespräch.'}
      tiles=${[{ v: items.length, l: 'Kombis' }, { v: `+${Math.max(Math.floor(score / 4), 10)}`, l: 'XP', c: 'var(--xp)' }, { v: progress.get().bestKombi, l: 'Rekord', c: 'var(--amber)' }]}
      actions=${html`<div class="rowx g8"><button class="btn ghost" onClick=${close}>Fertig</button><button class="btn" onClick=${() => { setItems(buildKombi(8)); start(); }}>Neue Kombis</button></div>`} />`;
  }
  return html`<div class="screen c-words">
    <${GameBar} onClose=${close}><div class="pbar" style="flex:1;height:8px;background:var(--high)"><i style=${`width:${(round / items.length) * 100}%`}></i></div><div class="cnt hot" style="min-width:60px">${score} Pkt</div><//>
    <${KombiRound} key=${round} item=${items[round]} onDone=${done} />
  </div>`;
}

// ── Blind-Coach ────────────────────────────────────────────────────────────

const BLIND_TEXT = { prompt: 'Hör zu …', listen: 'Jetzt frei auf Russisch sprechen', master: 'Hör die Musterlösung und sprich sie laut mit', ok: 'Perfekt abgerufen — der nächste Satz folgt', again: 'Gemerkt — kommt gleich noch einmal dran' };

export function Blind() {
  const [queue, setQueue] = useState(() => buildReflexQueue(7));
  const [phase, setPhase] = useState('intro');
  const [round, setRound] = useState(0);
  const [step, setStep] = useState('prompt');
  const [passed, setPassed] = useState(0);
  const [show, setShow] = useState(false);
  const [partial, setPartial] = useState('');
  const ctl = useRef({ token: 0, handle: null, skip: null });
  const qRef = useRef(queue);
  qRef.current = queue;
  const close = () => { ctl.current.token++; ctl.current.handle?.abort?.(); audio.stop(); tts.stop(); back('ueben'); };
  useEffect(() => () => { ctl.current.token++; ctl.current.handle?.abort?.(); audio.stop(); tts.stop(); }, []);

  useEffect(() => {
    if (phase !== 'run') return undefined;
    const token = ++ctl.current.token;
    const alive = () => ctl.current.token === token;
    const ph = qRef.current[round];
    setShow(false); setPartial('');
    (async () => {
      setStep('prompt');
      // Ansage in Rems Stimme (vorab erzeugt); nur ohne Aufnahme die Gerätestimme.
      await Promise.race([audio.coach(ph.de), new Promise((r) => { ctl.current.skip = r; })]);
      audio.stop(); tts.stop();
      if (!alive()) return;
      setStep('listen');
      const h = recog.listen({ expect: ph.ru, onPartial: (t) => alive() && setPartial(t) });
      ctl.current.handle = h;
      const r = await h.promise;
      ctl.current.handle = null;
      if (!alive()) return;
      const ok = r.ok && Sp.speech(ph.ru, r.hyps).passed;
      if (ok) { setPassed((x) => x + 1); fx.right(); progress.apply({ xp: 12, graded: ph.id.startsWith('dd:') ? {} : { [ph.id]: true }, spokenOk: 1 }); }
      else if (qRef.current.length < 12) { const q = [...qRef.current]; q.splice(Math.min(round + 3, q.length), 0, ph); setQueue(q); qRef.current = q; }
      setStep('master');
      await playPhrase(ph);
      if (!alive()) return;
      setStep(ok ? 'ok' : 'again');
      await new Promise((res) => setTimeout(res, 2200));
      if (!alive()) return;
      if (round + 1 < qRef.current.length) setRound(round + 1); else setPhase('over');
    })();
    return () => { ctl.current.token++; ctl.current.handle?.abort?.(); };
  }, [phase, round]);

  const orb = () => { if (step === 'listen') ctl.current.handle?.stop(); else if (step === 'prompt') ctl.current.skip?.(); };
  const skip = () => { ctl.current.token++; ctl.current.handle?.abort?.(); audio.stop(); tts.stop(); if (round + 1 < queue.length) setRound(round + 1); else setPhase('over'); };
  const start = () => { setRound(0); setPassed(0); setPhase('run'); };
  if (!queue.length) return html`<${Intro} c="c-speak" icon="headphones" over="Sprechen" title="Blind-Coach" text="Starte zuerst Tag 1." cta="Zurück" onStart=${close} onClose=${close} />`;
  if (phase === 'intro') {
    return html`<${Intro} c="c-speak" icon="headphones" over="Augen zu, nur Audio" title="Blind-Coach"
      text="Leg das Handy weg oder schließ die Augen. Auf dem Bildschirm steht kein russischer Text."
      points=${[['record_voice_over', 'Der Coach sagt dir auf Deutsch, was du ausdrücken sollst'], ['mic', 'Das Mikrofon öffnet sich von selbst — du sprichst frei'], ['volume_up', 'Danach hörst du die Musterlösung zum Nachsprechen']]}
      cta="Audio-Coach starten" disabled=${!recog.available()} note=${recog.available() ? null : 'Dieser Modus braucht die Spracherkennung des Browsers.'} onStart=${start} onClose=${close} />`;
  }
  if (phase === 'over') {
    return html`<${Summary} celebrate=${passed >= queue.length - 1} over="Audio-Coach beendet" big=${`${passed}/${queue.length}`} title="Sätze blind abgerufen"
      tiles=${[{ v: `+${passed * 12}`, l: 'XP', c: 'var(--xp)' }, { v: progress.get().spokenOk, l: 'gesprochen insgesamt' }]}
      actions=${html`<div class="rowx g8"><button class="btn ghost" onClick=${close}>Fertig</button><button class="btn" onClick=${() => { const q = buildReflexQueue(7); setQueue(q); qRef.current = q; start(); }}>Noch eine Runde</button></div>`} />`;
  }
  const ph = queue[round];
  const done = step === 'master' || step === 'ok' || step === 'again';
  return html`<div class="screen c-speak">
    <${GameBar} onClose=${close}><div class="pbar" style="flex:1;height:8px;background:var(--high)"><i style=${`width:${(round / queue.length) * 100}%`}></i></div><div class="cnt">${round + 1}/${queue.length}</div><//>
    <div class="scroll"><div class="stepc center g20" style="padding-top:20px">
      <div class="title">${ph.de}</div>
      <button class=${cls('orb', step === 'listen' && 'listen', step === 'prompt' && 'say', done && 'ok')} onClick=${orb} aria-label="Weiter">
        <${Icon} n=${step === 'listen' ? 'mic' : step === 'prompt' ? 'headphones' : 'check'} fill /></button>
      <div class=${cls('mictxt', step === 'listen' && 'on')} style="font-size:15.5px">${step === 'listen' && partial ? `»${partial}«` : BLIND_TEXT[step]}</div>
      ${show && html`<${PhraseBlock} phrase=${ph} size=${22} />`}
    </div></div>
    <div class="bottom">${!show && html`<button class="btn text" style="align-self:center" onClick=${() => setShow(true)}><${Icon} n="visibility" />Ausnahmsweise Text einblenden</button>`}
      <div class="rowx g8"><button class="btn ghost" onClick=${() => { ctl.current.handle?.abort?.(); playPhrase(ph); }}><${Icon} n="volume_up" />Lösung hören</button><button class="btn ghost" onClick=${skip}>Überspringen</button></div></div>
  </div>`;
}

// ── Sprich frei ────────────────────────────────────────────────────────────

function buildTalk() {
  const rng = Co.defaultRng();
  const p = progress.get();
  const s = progress.settings();
  const unlocked = P.unlockedDay(p, D.index.days.length);
  const universal = [...Co.UNIVERSAL_REACTIONS].map((id) => D.index.phrase(id, s)).filter(Boolean);
  const items = D.index.days.filter((d) => d.day <= unlocked).flatMap((d) => d.items.filter((i) => i.replies?.length));
  return weightedOrder(items, rng).map((it) => {
    const replies = it.replies.map((id) => D.index.phrase(id, s)).filter(Boolean);
    return replies.length ? { question: D.index.phrase(it.id, s), replies, accepted: [...replies, ...universal] } : null;
  }).filter(Boolean).slice(0, 6);
}

function TalkRound({ r, onDone }) {
  const [score, setScore] = useState(null);
  const [matched, setMatched] = useState(null);
  const [help, setHelp] = useState(false);
  const [attempts, setAttempts] = useState(0);
  const [asking, setAsking] = useState(true);
  const L = useListen((hyps) => {
    setAttempts((a) => a + 1);
    const scored = r.accepted.map((p) => ({ p, sc: Sp.speech(p.ru, hyps) })).sort((a, b) => b.sc.score - a.sc.score);
    const hit = scored.find((x) => x.sc.passed);
    if (hit) {
      setMatched(hit.p); fx.right();
      progress.apply({ xp: 10, graded: { [r.question.id]: true, [hit.p.id]: true }, spokenOk: 1 });
    } else { setScore(scored[0]?.sc ?? null); fx.wrong(); }
  });
  const ask = async (slow) => { L.cancel(); setAsking(true); await playPhrase(r.question, { slow }); setAsking(false); if (!matched && recog.available()) L.start(r.replies[0].ru); };
  useEffect(() => { ask(false); }, []);
  const mic = () => { if (L.listening) L.stop(); else { audio.stop(); setScore(null); L.start(r.replies[0].ru); } };
  const showHelp = !matched && (help || attempts >= 2);
  useKeys({ Enter: () => matched && onDone(true) }, [matched]);
  const status = matched ? 'Passt — das war eine gute Antwort.' : L.evaluating ? 'Wird ausgewertet …' : L.errorText ?? (L.listening ? (L.partial ? `»${L.partial}«` : (L.opening ? MIC_WAIT : 'Ich höre zu … sprich jetzt'))
    : asking ? 'Hör zu …' : score ? 'Das hat noch nicht gepasst — nochmal versuchen oder Hilfe ansehen.' : 'Tippe aufs Mikrofon und antworte');
  return html`<div class="scroll"><div class="stepc center g16">
      <div><div class="over c">Anja fragt</div><div class="title mt4">Antworte auf Russisch</div></div>
      <div class="play" style="--s:64px">
        <button class=${cls('pbtn', asking && 'on')} onClick=${() => ask(false)} aria-label="Frage anhören"><${Icon} n="volume_up" fill /></button>
        <button class="pbtn slow" onClick=${() => ask(true)} aria-label="Langsam anhören"><${Icon} n="slow_motion_video" /></button></div>
      <div class="muted" style="font-size:16px">${r.question.de}</div>
      ${!matched && html`<div class="micwrap"><${Mic} L=${L} onClick=${mic} disabled=${asking} /></div>`}
      <div class=${cls('mictxt', L.listening && 'on', matched && 'good')}>${status}</div>
      ${matched && html`<div class="card p tint c-words" style="width:100%"><${IL} ru=${matched.ru} size=${22} center /><div class="de mt8">${matched.de}</div></div>`}
      ${!matched && score && html`<${ScoreCard} sc=${score} />`}
      ${showHelp && html`<div style="width:100%;text-align:left"><div class="over">So könntest du antworten</div>
        <div class="list mt8">${r.replies.slice(0, 3).map((p) => html`<button class="row" onClick=${() => { L.cancel(); playPhrase(p); }}><div class="tx"><${IL} ru=${p.ru} size=${17} /><span style="margin-top:4px">${p.de}</span></div><${Speaker} phrase=${p} /></button>`)}</div></div>`}
    </div></div>
    <div class="bottom">${matched ? html`<button class="btn good" onClick=${() => onDone(true)}>Weiter<span class="kb">↵</span></button>`
      : html`<div class="rowx g8">${!help && attempts < 2 && html`<button class="btn ghost" onClick=${() => { L.cancel(); audio.stop(); setHelp(true); }}>Mögliche Antworten</button>`}
        <button class="btn ghost" onClick=${() => { L.cancel(); onDone(false); }}>Überspringen</button></div>`}</div>`;
}

export function Talk() {
  const rounds = useMemo(() => buildTalk(), []);
  const [phase, setPhase] = useState('intro');
  const [round, setRound] = useState(0);
  const [correct, setCorrect] = useState(0);
  const close = () => { audio.stop(); back('ueben'); };
  const done = (ok) => { const c = correct + (ok ? 1 : 0); setCorrect(c); if (round + 1 < rounds.length) setRound(round + 1); else setPhase('over'); };
  if (!rounds.length) return html`<${Intro} c="c-speak" icon="record_voice_over" over="Sprechen" title="Sprich frei" text="Noch zu wenig Stoff. Schließ zuerst ein paar Tage ab, dann kann Anja dir Fragen stellen." cta="Zurück" onStart=${close} onClose=${close} />`;
  if (phase === 'intro') {
    return html`<${Intro} c="c-speak" icon="record_voice_over" over="Frage und Antwort" title="Sprich frei"
      text="Du hörst eine Frage und antwortest laut auf Russisch — ohne Auswahl, ohne Lesen."
      points=${[['forum', 'Mehrere Antworten sind richtig'], ['mic', 'Das Mikrofon startet von selbst nach der Frage'], ['lightbulb', 'Nach zwei Versuchen gibt es Antwort-Ideen']]}
      disabled=${!recog.available()} note=${recog.available() ? null : 'Dieser Modus braucht die Spracherkennung des Browsers.'} onStart=${() => setPhase('run')} onClose=${close} />`;
  }
  if (phase === 'over') {
    return html`<${Summary} celebrate=${correct >= rounds.length - 1} over="Gespräch beendet" big=${`${correct}/${rounds.length}`} title="Antworten passten"
      tiles=${[{ v: `+${correct * 10}`, l: 'XP', c: 'var(--xp)' }, { v: progress.get().spokenOk, l: 'gesprochen insgesamt' }]}
      actions=${html`<div class="rowx g8"><button class="btn ghost" onClick=${close}>Fertig</button><button class="btn" onClick=${() => { setRound(0); setCorrect(0); setPhase('run'); }}>Nochmal</button></div>`} />`;
  }
  return html`<div class="screen c-speak">
    <${GameBar} onClose=${close}><div class="pbar" style="flex:1;height:8px;background:var(--high)"><i style=${`width:${(round / rounds.length) * 100}%`}></i></div><div class="cnt">${round + 1}/${rounds.length}</div><//>
    <${TalkRound} key=${round} r=${rounds[round]} onDone=${done} />
  </div>`;
}
