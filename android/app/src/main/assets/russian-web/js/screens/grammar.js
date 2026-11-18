// Grammatik: Lernweg in neun Schritten, Lektionen mit einer Idee pro Bildschirm, Stolpersteine aus
// den Gesprächen mit Anja, Formen nachschlagen und Nachfragen an Anja (geprüfte Antwort, gesprochen).
import { html, cls, Icon, TopBar, IL, Latin, Sheet, HiddenRu, mk, useListen, Mic, ScoreCard, useState, useEffect, useMemo, useRef, fx } from '../ui.js';
import { progress, nav, back, toast, openWord, useProgress } from '../app.js';
import { storage, api, speech, audioContext } from '../platform.js';
import * as Sp from '../domain/speech.js';
import { LESSONS, lessonById, GRAMMAR_KEY } from '../domain/grammar.js';
import { stones } from '../stones.js';
import { assess } from '../learner.js';
import { playStream, stopPlayback } from '../livevoice.js';
import { lookupText } from './words.js';

// ── Lernstand ──────────────────────────────────────────────────────────────

const readState = () => ({ done: {}, at: {}, ...(storage.getJSON(GRAMMAR_KEY, {}) ?? {}) });
const writeState = (s) => storage.set(GRAMMAR_KEY, s);
/** Der Schritt, der dran ist: der erste nicht abgeschlossene. */
function current(s) { return LESSONS.findIndex((l) => !s.done[l.id]); }

/** {Text} → hervorgehoben; Kyrillisch wird zur Umschrift (mk). */
function Marked({ text }) {
  const parts = text.split(/(\{[^}]+\})/);
  return html`${parts.map((p) => (p.startsWith('{') ? html`<u>${mk(p.slice(1, -1))}</u>` : mk(p)))}`;
}

// ── Anja fragen ────────────────────────────────────────────────────────────

/**
 * Eine Nachfrage an Anja: Die Antwort entsteht als Text, wird geprüft und dann in Anjas Stimme
 * vorgelesen. Ohne Verbindung zum Mac sagt das Blatt klar, dass das gerade nicht geht.
 */
export function AskAnja({ topic, reference = '', onClose }) {
  const [q, setQ] = useState('');
  const [busy, setBusy] = useState(false);
  const [answer, setAnswer] = useState(null);
  const [err, setErr] = useState(null);
  const level = useMemo(() => { try { return assess().level.id; } catch { return 'A1'; } }, []);
  useEffect(() => () => stopPlayback(), []);
  const send = async () => {
    if (!q.trim() || busy) return;
    audioContext();
    setBusy(true); setErr(null); setAnswer(null);
    try {
      // Das geprüfte Lehrmaterial geht mit: Anjas Antwort muss dazu passen, die Prüfung vergleicht damit.
      const r = await api.post('/russian/anja/ask', { question: q.trim(), topic: topic ?? '', reference: reference.slice(0, 2500), level });
      setAnswer(r);
      if (r.id && r.turn) playStream(`/api/russian/anja/live/audio/${r.id}/${r.turn}`, {}).catch(() => {});
    } catch (e) {
      setErr(e?.status ? 'Anja kann gerade nicht antworten. Versuch es gleich noch einmal.' : 'Anja braucht dafür die Verbindung zu deinem Mac. Gerade ist er nicht erreichbar.');
    } finally { setBusy(false); }
  };
  return html`<${Sheet} title="Anja fragen" onClose=${onClose}>
    <p class="sub" style="margin-bottom:10px">${topic ? `Zu ${topic}. ` : ''}Frag, was du nicht verstehst — Anja antwortet auf Deutsch, vorher geprüft.</p>
    ${answer && html`<div class="t-qa"><div class="bub me"><div class="who">DU</div>${q}</div>
      <div class="bub al-hers"><div class="who">ANJA</div><p style="line-height:1.55">${mk(answer.text)}</p>
        <div class="t-checked"><${Icon} n=${answer.verified ? 'verified' : 'info'} size="14" />${answer.verified ? 'Erklärung geprüft' : 'Nicht sicher geprüft — schau es dir in der Lektion an'}</div></div></div>`}
    ${err && html`<div class="hint mt8"><p>${err}</p></div>`}
    <div class="rowx g8 mt12"><input class="field grow" maxlength="300" placeholder=${answer ? 'Noch eine Frage …' : 'Warum heißt es »воду« und nicht »вода«?'} value=${q} onInput=${(e) => setQ(e.target.value)} onKeyDown=${(e) => e.key === 'Enter' && send()} />
      <button class="t-mic" disabled=${busy || !q.trim()} onClick=${send} aria-label="Fragen"><${Icon} n=${busy ? 'progress_activity' : 'send'} c=${busy ? 'spin' : ''} /></button></div>
    ${busy && html`<div class="sub mt8">Anja überlegt und prüft ihre Antwort …</div>`}
  <//>`;
}

// ── Bildschirme einer Lektion ─────────────────────────────────────────────

function Quiz({ s, onAnswer }) {
  const [pick, setPick] = useState(null);
  return html`<div class="g-quiz">
    <div class="sub">${s.de}</div>
    <div class="g-stem">${s.stem[0] && html`<${Latin} ru=${s.stem[0]} /> `}<span class=${cls('hole', pick != null && 'filled')}>${pick != null ? html`<${Latin} ru=${s.opts[pick]} />` : ''}</span>${s.stem[1] && html` <${Latin} ru=${s.stem[1]} />`}</div>
    <div class="stack g8 mt12">${s.opts.map((o, i) => html`<button class=${cls('opt', pick != null && (i === s.ok ? 'ok' : i === pick ? 'bad' : 'dim'))} onClick=${() => { if (pick == null) { setPick(i); (i === s.ok ? fx.right : fx.wrong)(); onAnswer(i === s.ok); } }}><b><${Latin} ru=${o} /></b></button>`)}</div>
    ${pick != null && html`<div class=${cls('g-why', pick === s.ok ? 'ok' : 'warn')}>${pick === s.ok ? 'Genau. ' : 'Fast. '}${mk(s.why)}</div>`}
  </div>`;
}

function Say({ s, onDone }) {
  const [level, setLevel] = useState(0);
  const [sc, setSc] = useState(null);
  const L = useListen((hyps) => { const r = Sp.speech(s.ru, hyps); setSc(r); if (r.passed) { fx.right(); onDone(level); } else fx.wrong(); });
  return html`<div class="g-say"><div class="scard-ask" style="font-size:20px">${s.de}</div>
    <${HiddenRu} ru=${s.ru} level=${sc?.passed ? 3 : level} size=${22} />
    ${!sc?.passed && level < 3 && html`<button class="chip sm spick" onClick=${() => setLevel(level + 1)}><${Icon} n="visibility" />Spicker · ${['1. Wort', 'Wortanfänge', 'Ganzer Satz'][level]}</button>`}
    ${speech.available()
      ? html`<${Mic} L=${L} onClick=${() => (L.listening ? L.stop() : (setSc(null), L.start(s.ru)))} />${sc && html`<${ScoreCard} sc=${sc} targetRu=${s.ru} />`}`
      : html`<button class="btn ghost" onClick=${() => onDone(level)}>Laut gesagt</button>`}
  </div>`;
}

function Screen({ s, onAnswer, onSaid }) {
  return html`<div class="g-screen">
    <div class="over c">${s.k}</div><div class="title mt4">${s.t}</div>
    <div class="g-body">
      ${s.kind === 'de' && html`${s.de.map((row) => html`<div class="g-de">${row.map((x, i) => html`${i > 0 && html`<span class="ar"><${Icon} n="south" size="18" /></span>`}<span><${Marked} text=${x} /></span>`)}</div>`)}
        ${s.p.map((x) => html`<p><${Marked} text=${x} /></p>`)}`}
      ${s.kind === 'pairs' && html`<div class="g-pairs">${s.pairs.map(([a, b, de]) => html`<div class="g-pair">
          <span><${Latin} ru=${a} /></span><${Icon} n="arrow_forward" size="16" /><span><b><${Latin} ru=${b} /></b><em>${de}</em></span></div>`)}</div>
        <div class="g-rule"><b>Regel:</b> ${mk(s.rule)}</div>`}
      ${s.kind === 'table' && html`<div class="w-rows">${s.rows.map(([l, ru, de]) => html`<button class="w-row" onClick=${() => openWord(ru.split('→').pop().trim())}>
          <span class="p">${l}</span><span class="f"><b><${Latin} ru=${ru} /></b>${de && html`<em>${de}</em>`}</span><${Icon} n="table_view" c="faint" size="18" /></button>`)}</div>
        <div class="g-rule">${mk(s.rule)}</div>`}
      ${s.kind === 'memo' && html`<div class="g-memo"><b>${mk(s.big)}</b><span>${mk(s.text)}</span></div><p class="muted">${s.term}</p>`}
      ${s.kind === 'quiz' && html`<${Quiz} s=${s} onAnswer=${onAnswer} />`}
      ${s.kind === 'say' && html`<${Say} s=${s} onDone=${onSaid} />`}
    </div></div>`;
}

/** Das geprüfte Material einer Lektion als Text — Grundlage für Anjas Antwort auf eine Nachfrage. */
function referenceOf(lesson) {
  const plainRu = (x) => x.replaceAll('+', '');
  return lesson.screens.map((s) => [
    s.rule, s.text,
    ...(s.pairs ?? []).map(([a, b, de]) => `«${plainRu(a)}» → «${plainRu(b)}» (${de})`),
    ...(s.rows ?? []).map(([l, ru, de]) => `${l}: «${plainRu(ru)}»${de ? ` (${de})` : ''}`),
  ].filter(Boolean).join('\n')).filter(Boolean).join('\n');
}

export function GrammarLesson({ id }) {
  const lesson = lessonById(id);
  const [st, setSt] = useState(readState);
  const [i, setI] = useState(() => Math.min(readState().at[id] ?? 0, (lesson?.screens.length ?? 1) - 1));
  const [ask, setAsk] = useState(false);
  const [answered, setAnswered] = useState(false);
  const score = useRef({ right: 0, total: 0 });
  if (!lesson) return html`<div class="screen"><${TopBar} title="Grammatik" onBack=${() => back('grammatik')} /><div class="empty"><b>Diese Lektion gibt es nicht.</b></div></div>`;
  const s = lesson.screens[i];
  const last = i === lesson.screens.length - 1;
  const needs = (s.kind === 'quiz' || s.kind === 'say') && !answered;
  const go = (n) => { setAnswered(false); setI(n); const next = { ...st, at: { ...st.at, [id]: n } }; setSt(next); writeState(next); };
  const finish = () => {
    const next = { ...st, done: { ...st.done, [id]: progress.today() }, at: { ...st.at, [id]: 0 } };
    setSt(next); writeState(next);
    // Zählt wie jede Übung (XP, Serie); der Sprechsatz kommt als Karte in die Wiederholung.
    progress.apply({ xp: 10 + score.current.right * 3, graded: {}, spokenOk: 1 });
    const say = lesson.screens.find((x) => x.kind === 'say');
    if (say) progress.learnSentence({ ru: say.ru, de: say.de, note: `Grammatik: ${lesson.q}` });
    toast('Lektion geschafft — der Sprechsatz ist in deiner Wiederholung');
    back('grammatik');
  };
  const table = lesson.screens.find((x) => x.kind === 'table' || x.kind === 'pairs');
  return html`<div class="screen c-words">
    <div class="g-top"><button class="iconbtn" onClick=${() => back('grammatik')} aria-label="Schließen"><${Icon} n="close" /></button>
      <div class="g-dots">${lesson.screens.map((_, k) => html`<i class=${cls(k < i && 'done', k === i && 'on')}></i>`)}</div>
      <button class="t-askbtn" onClick=${() => setAsk(true)}><${Icon} n="forum" size="18" />Anja fragen</button></div>
    <div class="duo">
      <div class="pane scroll"><div class="pad"><${Screen} key=${`${id}${i}`} s=${s}
        onAnswer=${(ok) => { setAnswered(true); score.current.total++; if (ok) score.current.right++; }}
        onSaid=${() => setAnswered(true)} /></div><div class="end"></div></div>
      <div class="pane scroll duo-only"><div class="sec" style="padding-top:14px">
        <div class="over c">Schritt ${LESSONS.indexOf(lesson) + 1} · ${lesson.term}</div><div class="h2 mt4">${lesson.q}</div>
        ${table && html`<div class="mt12">${table.kind === 'table'
          ? html`<div class="w-rows">${table.rows.map(([l, ru, de]) => html`<div class="w-row"><span class="p">${l}</span><span class="f"><b><${Latin} ru=${ru} /></b>${de && html`<em>${de}</em>`}</span></div>`)}</div>`
          : html`<div class="g-pairs">${table.pairs.map(([a, b, de]) => html`<div class="g-pair"><span><${Latin} ru=${a} /></span><${Icon} n="arrow_forward" size="16" /><span><b><${Latin} ru=${b} /></b><em>${de}</em></span></div>`)}</div>`}
          <div class="g-rule mt12">${mk(table.rule)}</div></div>`}
        <p class="sub mt12">Tippe auf ein russisches Wort, um alle seine Formen zu sehen.</p></div></div>
    </div>
    <div class="bottom"><div class="rowx g8">
      ${i > 0 && html`<button class="btn ghost" style="width:54px;flex:none;padding:0" onClick=${() => go(i - 1)} aria-label="Zurück"><${Icon} n="arrow_back" /></button>`}
      <button class="btn" disabled=${needs} onClick=${() => (last ? finish() : go(i + 1))}>${needs ? (s.kind === 'quiz' ? 'Wähle eine Antwort' : 'Sag den Satz') : last ? 'Fertig' : 'Weiter'}</button></div>
      ${needs && s.kind === 'say' && html`<button class="btn text" style="margin:0 auto" onClick=${() => setAnswered(true)}>Überspringen</button>`}</div>
    ${ask && html`<${AskAnja} topic=${`»${lesson.q}« (${lesson.term})`} reference=${referenceOf(lesson)} onClose=${() => setAsk(false)} />`}
  </div>`;
}

// ── Übersicht ──────────────────────────────────────────────────────────────

function Lookup() {
  const [q, setQ] = useState('');
  const [busy, setBusy] = useState(false);
  const recent = storage.getJSON('grammar_recent', []) ?? [];
  const open = async (word) => {
    const w = word.trim();
    if (!w) return;
    setBusy(true);
    const found = await lookupText(w).catch(() => null);
    setBusy(false);
    if (!found?.entries.length) { toast(`»${w}« steht nicht im Wörterbuch`); return; }
    storage.set('grammar_recent', [w, ...recent.filter((x) => x !== w)].slice(0, 6));
    openWord(w);
  };
  return html`<div class="g-look">
    <div class="g-search"><${Icon} n=${busy ? 'progress_activity' : 'search'} c=${busy ? 'spin' : ''} />
      <input class="grow" lang="ru" placeholder="Russisches Wort, in jeder Form" value=${q} onInput=${(e) => setQ(e.target.value)} onKeyDown=${(e) => e.key === 'Enter' && open(q)} /></div>
    ${recent.length > 0 && html`<div class="sub mt8">Zuletzt nachgeschlagen</div>
      <div class="rowx g8 wrap mt8">${recent.map((w) => html`<button class="chip sm" onClick=${() => open(w)}><span><${Latin} ru=${w} /></span></button>`)}</div>`}</div>`;
}

function Stones() {
  const list = stones.all().slice(0, 5);
  if (!list.length) return html`<p class="sub">Noch keine. Sobald Anja dich korrigiert, siehst du hier, worüber du stolperst — und welcher Schritt dazu passt.</p>`;
  return html`<div class="t-stones">${list.map((x) => {
    const lesson = LESSONS.find((l) => l.stones.test(x.topic));
    return html`<button class="t-stone" onClick=${() => (lesson ? nav(`grammatik/${lesson.id}`) : x.rightRu && openWord(x.rightRu))}>
      <span class="n num">${x.n}×</span>
      <span class="grow"><b>${x.topic}</b>${x.wrongRu && x.rightRu && html`<span class="ex"><s><${Latin} ru=${x.wrongRu} /></s> → <${Latin} ru=${x.rightRu} /></span>`}</span>
      ${lesson && html`<span class="when">Schritt ${LESSONS.indexOf(lesson) + 1}</span>`}</button>`;
  })}</div>`;
}

export function Grammar() {
  useProgress();
  const st = readState();
  const now = current(st);
  const [sel, setSel] = useState(now < 0 ? 0 : now);
  const isWide = () => (document.querySelector('.app')?.clientWidth ?? 0) >= 720;
  const hero = LESSONS[now < 0 ? 0 : now];
  const doneCount = Object.keys(st.done).length;
  const s = LESSONS[sel];
  return html`<div class="screen c-words">
    <${TopBar} title="Grammatik" sub="Verstehen, warum Wörter sich ändern" onBack=${() => back('heute')} />
    <div class="duo">
      <div class="pane scroll">
        <div class="sec" style="padding-top:6px"><button class="g-hero" onClick=${() => nav(`grammatik/${hero.id}`)}>
          <span class="k">${now < 0 ? 'Alle 9 Schritte geschafft — zum Wiederholen' : `Weiter mit Schritt ${now + 1} von ${LESSONS.length}`}</span><b>${hero.q}</b>
          <span class="d">${hero.t}. Eine Idee pro Bildschirm, gleich danach üben.</span>
          <span class="bar"><i style=${`width:${(doneCount / LESSONS.length) * 100}%`}></i></span>
          <span class="go"><${Icon} n="play_arrow" fill />${st.at[hero.id] ? 'Weiterlernen' : 'Starten'}</span></button></div>
        <div class="sec"><div class="h2">Dein Weg</div>
          <div class="sub">Jeder Schritt beginnt bei einer Frage, die du aus dem Deutschen kennst. Das Fachwort kommt erst danach.</div>
          <ol class="g-path">${LESSONS.map((l, i) => {
            const state = st.done[l.id] ? 'done' : i === now ? 'now' : '';
            return html`<li><button class=${cls('g-step', state, sel === i && isWide() && 'sel')} onClick=${() => (isWide() ? setSel(i) : nav(`grammatik/${l.id}`))}>
              <span class="dot">${st.done[l.id] ? html`<${Icon} n="check" size="18" />` : i + 1}</span>
              <span class="grow"><b>${l.q}</b><span>${l.t} · <em>${l.term}</em></span></span>
              ${state === 'now' && html`<span class="go">Weiter</span>`}</button></li>`;
          })}</ol></div>
        <div class="sec duo-hide"><div class="h2">Deine Stolpersteine</div><div class="sub">Aus deinen Gesprächen mit Anja.</div><${Stones} /></div>
        <div class="sec duo-hide"><div class="h2">Formen nachschlagen</div><div class="sub">Jedes Wort in jeder Form — oder tippe im Kurs einfach auf ein Wort.</div><${Lookup} /></div>
        <div class="end"></div>
      </div>
      <div class="pane scroll duo-only">
        <div class="sec" style="padding-top:14px"><div class="over c">Schritt ${sel + 1} · ${s.term}</div><div class="title mt4">${s.q}</div>
          <p class="g-lead">${s.t}. ${s.screens.length} kurze Bildschirme: was du aus dem Deutschen kennst, was im Russischen anders ist, eine Eselsbrücke, dann üben und sprechen.</p>
          <button class="btn area mt12" onClick=${() => nav(`grammatik/${s.id}`)}><${Icon} n="play_arrow" fill />${st.done[s.id] ? 'Wiederholen' : 'Starten'}</button></div>
        <div class="sec"><div class="h2">Deine Stolpersteine</div><div class="sub">Aus deinen Gesprächen mit Anja.</div><${Stones} /></div>
        <div class="sec"><div class="h2">Formen nachschlagen</div><${Lookup} /></div>
        <div class="end"></div>
      </div>
    </div></div>`;
}
