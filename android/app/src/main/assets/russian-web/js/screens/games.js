// Spiele: Hör-Blitz (60 Sekunden) und Paare finden (auf Zeit).
import { html, cls, Icon, IL, PlayRow, Opt, Speaker, Summary, useKeys, playPhrase, useState, useEffect, useRef, useMemo, fx, audio } from '../ui.js';
import { progress, back, learnedPool } from '../app.js';
import * as Co from '../domain/course.js';
import { weightedOrder } from '../learner.js';

/** Einheitlicher Einstieg in jedes Spiel und jeden Sprech-Modus. */
export function Intro({ c, icon, over, title, text, points = [], cta = 'Start', onStart, onClose, record, disabled, note }) {
  useKeys({ Enter: () => !disabled && onStart() }, [disabled]);
  return html`<div class=${cls('screen', c)}>
    <div class="top"><button class="iconbtn" onClick=${onClose} aria-label="Schließen"><${Icon} n="close" /></button></div>
    <div class="scroll"><div class="stepc center g16" style="padding-top:28px">
      <div class="ic lg solid" style="width:76px;height:76px;border-radius:24px"><${Icon} n=${icon} size="40" /></div>
      <div><div class="over c">${over}</div><div class="big mt4">${title}</div></div>
      <p class="muted" style="max-width:320px;line-height:1.5">${text}</p>
      ${points.length > 0 && html`<div class="list" style="width:100%;text-align:left">${points.map(([ic, t]) => html`<div class="row" style="min-height:52px"><${Icon} n=${ic} c="cc" /><div class="tx"><span style="color:var(--text);font-size:14px;margin:0">${t}</span></div></div>`)}</div>`}
      ${record && html`<span class="tag c-amber"><${Icon} n="trophy" />${record}</span>`}
      ${note && html`<p class="sub">${note}</p>`}
    </div></div>
    <div class="bottom"><button class="btn" disabled=${disabled} onClick=${onStart}>${cta}<span class="kb">↵</span></button></div>
  </div>`;
}

export const GameBar = ({ onClose, children }) => html`<div class="sbar"><button class="iconbtn" onClick=${onClose} aria-label="Schließen"><${Icon} n="close" /></button>${children}</div>`;

// ── Hör-Blitz ──────────────────────────────────────────────────────────────

const BLITZ_MS = 60000;
const mult = (combo) => Math.min(1 + Math.floor(combo / 3), 4);

export function Blitz() {
  const pool = useMemo(() => learnedPool(), []);
  const rng = useMemo(() => Co.defaultRng(), []);
  const [phase, setPhase] = useState('intro');
  const [left, setLeft] = useState(BLITZ_MS);
  const [score, setScore] = useState(0);
  const [combo, setCombo] = useState(0);
  const [round, setRound] = useState(0);
  const [picked, setPicked] = useState(null);
  const [gain, setGain] = useState(null);
  const heard = useRef(new Map());
  const res = useRef({ newBest: false, best: 0 });
  const lastId = useRef(null);
  // Fällige und wackelige Sätze kommen häufiger dran als solche, die schon sitzen.
  const q = useMemo(() => {
    const target = weightedOrder(pool.filter((x) => x.id !== lastId.current), rng)[0] ?? rng.pick(pool);
    lastId.current = target.id;
    return { target, options: rng.shuffled([...Co.distractors(target, pool, rng), target]) };
  }, [round]);
  const close = () => { audio.stop(); back('ueben'); };

  const note = (p, ok) => { const rank = (v) => (v === false ? 0 : v == null ? 1 : 2); const old = heard.current.get(p.id); if (!old || rank(ok) < rank(old.ok)) heard.current.set(p.id, { p, ok }); };
  useEffect(() => { if (phase === 'run') playPhrase(q.target); }, [round, phase]);
  useEffect(() => {
    if (phase !== 'run') return undefined;
    const t = setInterval(() => setLeft((l) => l - 100), 100);
    return () => clearInterval(t);
  }, [phase]);
  useEffect(() => {
    if (phase !== 'run' || left > 0) return;
    if (picked == null) note(q.target, null);
    audio.stop();
    const old = progress.get().bestBlitz;
    res.current = { newBest: score > old, best: Math.max(old, score) };
    progress.update((p) => ({ ...p, bestBlitz: Math.max(p.bestBlitz, score) }));
    progress.addXp(Math.floor(score / 10));
    setPhase('over');
  }, [left, phase]);

  const pick = (o) => {
    if (picked != null || phase !== 'run') return;
    const ok = o.id === q.target.id;
    setPicked(o.id); note(q.target, ok);
    progress.gradeCard(q.target.id, ok);
    if (ok) { const c = combo + 1; const g = 10 * mult(c); setCombo(c); setScore((s) => s + g); setGain(g); fx.right(); } else { setCombo(0); setLeft((l) => Math.max(0, l - 3000)); setGain(-3); fx.wrong(); }
    setTimeout(() => { setPicked(null); setGain(null); setRound((r) => r + 1); }, ok ? 450 : 1100);
  };
  useKeys({ ...Object.fromEntries(q.options.map((o, i) => [String(i + 1), () => pick(o)])), ' ': () => playPhrase(q.target) }, [picked, phase, round]);
  const start = () => { heard.current = new Map(); setScore(0); setCombo(0); setLeft(BLITZ_MS); setPicked(null); setRound((r) => r + 1); setPhase('run'); };

  if (pool.length < 4) return html`<${Intro} c="c-listen" icon="hearing" over="Hören" title="Hör-Blitz" text="Schließ zuerst Tag 1 ab — dann gibt es genug Sätze für das Spiel." cta="Zurück" onStart=${close} onClose=${close} />`;
  if (phase === 'intro') {
    return html`<${Intro} c="c-listen" icon="hearing" over="60 Sekunden" title="Hör-Blitz" text="Du hörst einen Satz und tippst so schnell wie möglich die richtige Bedeutung."
      points=${[['bolt', 'Serien geben Bonus: ab 3 Treffern doppelte Punkte'], ['timer', 'Ein Fehler kostet 3 Sekunden'], ['replay', 'Jede Antwort zählt für deinen Lernstand']]}
      record=${progress.get().bestBlitz > 0 ? `Dein Rekord: ${progress.get().bestBlitz}` : null} onStart=${start} onClose=${close} />`;
  }
  if (phase === 'over') {
    const list = [...heard.current.values()].sort((a, b) => (a.ok === false ? 0 : a.ok == null ? 1 : 2) - (b.ok === false ? 0 : b.ok == null ? 1 : 2));
    const right = list.filter((x) => x.ok === true).length;
    const wrong = list.filter((x) => x.ok === false).length;
    return html`<${Summary} celebrate=${res.current.newBest} over="Zeit um" big=${score} title="Punkte" note=${res.current.newBest ? 'Neuer Rekord!' : `Rekord: ${res.current.best}`}
      tiles=${[{ v: right, l: 'richtig', c: 'var(--good)' }, { v: wrong, l: 'falsch', c: wrong ? 'var(--bad)' : null }, { v: `+${Math.floor(score / 10)}`, l: 'XP', c: 'var(--xp)' }]}
      actions=${html`<div class="rowx g8"><button class="btn ghost" onClick=${close}>Fertig</button><button class="btn" onClick=${start}>Nochmal</button></div>`}>
      ${list.length > 0 && html`<div style="width:100%;text-align:left" class="mt24"><div class="over">Gehörte Sätze · ${list.length}</div>
        <div class="list mt8">${list.map(({ p, ok }) => html`<div class=${cls('dotline', ok === true && 'ok', ok === false && 'bad')}><i></i>
          <div class="grow"><${IL} ru=${p.ru} size=${16} /><div class="sub mt4">${p.de}</div></div><${Speaker} phrase=${p} /></div>`)}</div></div>`}
    <//>`;
  }
  return html`<div class="screen c-listen">
    <${GameBar} onClose=${close}><div class=${cls('timer', left < 10000 && 'low')}><i style=${`width:${(left / BLITZ_MS) * 100}%`}></i></div>
      <div class="cnt hot" style="min-width:54px">${score}</div><//>
    <div class="scroll"><div class="stepc center">
      <div style="height:30px">${combo >= 3 && html`<span class="combo" key=${mult(combo)}><${Icon} n="local_fire_department" fill />Serie ×${mult(combo)}</span>`}</div>
      <${PlayRow} phrase=${q.target} size=${84} />
      <div style="min-height:74px;display:flex;align-items:center" class="mt12">${picked != null && html`<${IL} ru=${q.target.ru} size=${20} center />`}</div>
      <div class="opts mt8">${q.options.map((o, i) => html`<${Opt} kb=${i + 1} key=${`${round}-${o.id}`} state=${picked == null ? '' : o.id === q.target.id ? 'ok' : o.id === picked ? 'bad' : 'dim'} onClick=${() => pick(o)}>${o.de}<//>`)}</div>
      <div style="height:26px" class="mt12">${gain != null && html`<b class=${gain > 0 ? 'good' : 'bad'} style="animation:pop .3s">${gain > 0 ? `+${gain}` : '−3 Sekunden'}</b>`}</div>
    </div></div>
  </div>`;
}

// ── Paare finden ───────────────────────────────────────────────────────────

function dealPairs() {
  const rng = Co.defaultRng();
  const chosen = [];
  for (const p of weightedOrder(learnedPool(), rng)) {
    if (chosen.length === 6) break;
    if (chosen.some((c) => Co.confusable(c.de, p.de) || c.plain === p.plain)) continue;
    chosen.push(p);
  }
  return rng.shuffled(chosen.flatMap((p) => [{ p, ru: true }, { p, ru: false }]));
}

export function Pairs() {
  const [cards, setCards] = useState(dealPairs);
  const [phase, setPhase] = useState('intro');
  const [matched, setMatched] = useState([]);
  const [sel, setSel] = useState(null);
  const [wrong, setWrong] = useState(null);
  const [elapsed, setElapsed] = useState(0);
  const [penalty, setPenalty] = useState(0);
  const res = useRef({ newBest: false, best: 0 });
  const seconds = Math.floor(elapsed / 1000) + penalty;
  const close = () => { audio.stop(); back('ueben'); };

  useEffect(() => {
    if (phase !== 'run') return undefined;
    const t = setInterval(() => setElapsed((e) => e + 100), 100);
    return () => clearInterval(t);
  }, [phase]);
  useEffect(() => {
    if (phase !== 'run' || !cards.length || matched.length * 2 !== cards.length) return;
    const old = progress.get().bestPairsSeconds;
    res.current = { newBest: old === 0 || seconds < old, best: old === 0 ? seconds : Math.min(old, seconds) };
    progress.update((p) => ({ ...p, bestPairsSeconds: res.current.best }));
    progress.addXp(15);
    setPhase('done');
  }, [matched]);

  const tap = (i) => {
    if (wrong || matched.includes(cards[i].p.id)) return;
    const c = cards[i];
    if (c.ru) playPhrase(c.p);
    if (sel == null) { setSel(i); return; }
    if (sel === i) { setSel(null); return; }
    const a = cards[sel];
    if (a.ru === c.ru) { setSel(i); return; }
    if (a.p.id === c.p.id) { fx.right(); setMatched([...matched, c.p.id]); progress.gradeCard(c.p.id, true); setSel(null); return; }
    fx.wrong(); setWrong([sel, i]); progress.gradeCard(a.p.id, false); setPenalty((x) => x + 3); setSel(null);
    setTimeout(() => setWrong(null), 650);
  };
  const again = () => { setCards(dealPairs()); setMatched([]); setSel(null); setWrong(null); setElapsed(0); setPenalty(0); setPhase('run'); };

  if (cards.length < 8) return html`<${Intro} c="c-words" icon="join_inner" over="Wörter" title="Paare finden" text="Schließ zuerst Tag 1 ab — dann gibt es genug Sätze für das Spiel." cta="Zurück" onStart=${close} onClose=${close} />`;
  if (phase === 'intro') {
    return html`<${Intro} c="c-words" icon="join_inner" over="Auf Zeit" title="Paare finden" text="Verbinde jeden russischen Satz mit seiner Bedeutung — so schnell du kannst."
      points=${[['volume_up', 'Russische Karten sprechen beim Antippen'], ['timer', 'Ein Fehler kostet 3 Sekunden'], ['replay', 'Jedes Paar zählt für deinen Lernstand']]}
      record=${progress.get().bestPairsSeconds > 0 ? `Deine Bestzeit: ${progress.get().bestPairsSeconds} s` : null} onStart=${() => setPhase('run')} onClose=${close} />`;
  }
  if (phase === 'done') {
    return html`<${Summary} celebrate=${res.current.newBest} over="Alle Paare" big=${seconds} title="Sekunden" note=${res.current.newBest ? 'Neue Bestzeit!' : `Bestzeit: ${res.current.best} s`}
      tiles=${[{ v: 6, l: 'Paare', c: 'var(--good)' }, { v: penalty / 3, l: penalty === 3 ? 'Fehler' : 'Fehler' }, { v: '+15', l: 'XP', c: 'var(--xp)' }]}
      actions=${html`<div class="rowx g8"><button class="btn ghost" onClick=${close}>Fertig</button><button class="btn" onClick=${again}>Nochmal</button></div>`} />`;
  }
  return html`<div class="screen c-words">
    <${GameBar} onClose=${close}><div class="pbar" style="flex:1;height:8px;background:var(--high)"><i style=${`width:${((matched.length * 2) / cards.length) * 100}%`}></i></div>
      <div class="cnt hot" style="min-width:54px">${seconds} s</div><//>
    <div class="scroll"><div class="stepc"><div class="pairs">${cards.map((c, i) => html`<button class=${cls('pcard', sel === i && 'sel', wrong?.includes(i) && 'bad', matched.includes(c.p.id) && 'gone')} onClick=${() => tap(i)}>
      ${c.ru ? html`<${IL} ru=${c.p.ru} size=${15} gloss=${false} />` : c.p.de}</button>`)}</div></div></div>
  </div>`;
}
