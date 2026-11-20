// Fortschritt wie bei Freeletics: Level (1–5), Übungsstufen mit Auf-/Abstiegen und der Fitness-Test
// (Einstufung + alle 4 Wochen). Server: /fitness/progress, /fitness/test (progression.ts, assessment.ts).
import { html, useEffect, useRef, useState } from '../../vendor/preact-htm.js';
import { api, beep, haptic, keepAwake } from '../platform.js';
import { nav, useData, refresh, relDay, today, mmss } from '../app.js';
import { Icon, Top, cls, Loop, Spinner } from '../ui.js';

const PATTERN = { push: 'Drücken', pull: 'Ziehen', squat: 'Beine', lunge: 'Beine', hinge: 'Hüfte', core: 'Rumpf', skill: 'Skill', cardio: 'Ausdauer' };
const LEVEL_NAME = ['', 'Einsteiger', 'Grundlagen', 'Fortgeschritten', 'Stark', 'Athlet'];

/** Level-Karte mit Test-Stand — oben im Fortschritt. */
export function LevelCard({ d }) {
  const t = d.test;
  return html`<div class="lvlCard">
    <div class="row" style="align-items:flex-end;justify-content:space-between">
      <div><div class="lc-k">Dein Level</div><div class="lc-v num">${d.level}<small>/5</small></div><div class="lc-n">${LEVEL_NAME[d.level] ?? ''}</div></div>
      <div class="lc-bars">${[1, 2, 3, 4, 5].map((i) => html`<i class=${cls(i <= d.level && 'on')} style=${{ height: `${14 + i * 9}px` }} />`)}</div>
    </div>
    <div class="lc-test">
      <div class="grow"><b>${t.last ? `Fitness-Test ${relDay(t.last.day, today())}` : 'Noch kein Fitness-Test'}</b>
        <span>${t.last ? (t.due ? 'Zeit für den nächsten — zeig, was sich getan hat' : `Nächster in ${t.daysUntil} Tagen`) : '5 Minuten, damit dein Coach dich richtig einschätzt'}</span></div>
      <button class=${cls('btn sm', !t.due && 'ghost')} onClick=${() => nav('test')}>${t.due ? 'Test starten' : 'Ansehen'}</button>
    </div>
  </div>`;
}

/** Übungsstufen: je Familie die aktuelle Variante, die Kette und wie nah der nächste Aufstieg ist. */
export function Families({ d, cat }) {
  const ex = (id) => cat?.exercises?.find((e) => e.id === id);
  const fams = d.families.filter((f) => f.steps[f.current]?.usable);
  if (!fams.length) return null;
  // Zuerst die Familien, in denen schon trainiert wurde
  fams.sort((a, b) => (b.times > 0) - (a.times > 0) || b.toNext - a.toNext);
  return html`<div class="sec"><h2 class="h2">Deine Stufen</h2><span class="small muted">Aufstieg, wenn du sie beherrschst</span></div>
    <div class="rail famRail">${fams.map((f) => {
      const cur = f.steps[f.current];
      const next = f.steps.slice(f.current + 1).find((s) => s.usable);
      const e = ex(cur.id);
      return html`<button class="famCard" onClick=${() => nav(`uebung/${cur.id}`)}>
        <div class="fc-img">${e && html`<${Loop} media=${e.media} still />`}<span class="fc-p">${PATTERN[f.pattern] ?? ''}</span></div>
        <div class="fc-tx">
          <b>${cur.name}</b>
          <div class="fc-chain">${f.steps.map((s, i) => html`<i class=${cls(i < f.current && 'done', i === f.current && 'cur', !s.usable && 'off')} />`)}</div>
          ${next ? html`<div class="fc-bar"><i style=${{ width: `${f.toNext}%` }} /></div>
            <span class="fc-next">${f.times ? `${f.toNext} % bis ${next.name}` : `Danach: ${next.name}`}</span>`
            : html`<span class="fc-next">Höchste Stufe erreicht</span>`}
        </div>
      </button>`;
    })}</div>`;
}

export function ProgressEvents({ d }) {
  if (!d.events?.length) return null;
  const text = (e) => e.kind === 'auf' ? `${e.fromName} → ${e.toName}` : e.kind === 'ab' ? `${e.fromName} → ${e.toName}` : e.kind === 'test' ? `Fitness-Test: Level ${e.level}` : `Level ${e.level}`;
  const label = { auf: 'Aufstieg', ab: 'Eine Stufe zurück', level_auf: 'Level hoch', level_ab: 'Level angepasst', test: 'Einstufung' };
  const icon = { auf: 'trending_up', ab: 'trending_down', level_auf: 'military_tech', level_ab: 'tune', test: 'assignment_turned_in' };
  return html`<div class="sec"><h2 class="h2">Entwicklung</h2></div>
    <div class="evList">${d.events.slice(0, 6).map((e) => html`<div class=${cls('evRow', e.kind)}>
      <span class="ev-ic"><${Icon} n=${icon[e.kind] ?? 'flag'} /></span>
      <div class="grow"><b>${label[e.kind] ?? e.kind}</b><span>${text(e)}</span></div><span class="small muted">${relDay(e.day, today())}</span></div>`)}</div>`;
}

/** Abschluss nach dem Training: Aufstieg/Abstieg/Level gleich zeigen. */
export function ProgressNews({ items }) {
  if (!items?.length) return null;
  return html`<div class="newsList mt16">${items.map((e) => html`<div class=${cls('newsCard', e.kind)}>
    <${Icon} n=${e.kind === 'auf' ? 'trending_up' : e.kind === 'ab' ? 'trending_down' : 'military_tech'} />
    <div class="grow">${e.kind === 'auf' ? html`<b>Aufstieg: ${e.toName}</b><span>Du beherrschst ${e.fromName} — ab jetzt die nächste Stufe.</span>`
      : e.kind === 'ab' ? html`<b>Eine Stufe zurück: ${e.toName}</b><span>${e.fromName} war noch zu schwer. Erst sauber aufbauen, dann wieder hoch.</span>`
      : html`<b>Level ${e.level}</b><span>${e.kind === 'level_auf' ? 'Du bist stärker geworden — dein Training wird anspruchsvoller.' : 'Dein Coach passt das Training an deine Form an.'}</span>`}</div>
  </div>`)}</div>`;
}

// ── Fitness-Test ─────────────────────────────────────────────────────────────

export function FitnessTest() {
  const [d] = useData('/fitness/progress');
  const [cat] = useData('/fitness/catalog');
  const [i, setI] = useState(-1); // -1 Übersicht, n = Ergebnis
  const [vals, setVals] = useState({});
  const [res, setRes] = useState(null);
  const [busy, setBusy] = useState(false);
  useEffect(() => { keepAwake(true); return () => keepAwake(false); }, []);
  if (!d) return html`<div class="screen"><${Top} title="Fitness-Test" /><div class="pad"><div class="skel" style="height:400px" /></div></div>`;
  const tests = d.test.tests;
  const last = d.test.last;
  const finish = async (all) => {
    setBusy(true);
    try {
      const r = await api.post('/fitness/test', { results: all });
      // Offline: Ergebnis wartet auf dem Handy, Einstufung folgt beim Nachsenden.
      setRes(r?.queued ? { queued: true, results: all } : r);
      refresh('/fitness/progress'); refresh('/fitness/state');
    } catch { setBusy(false); }
  };
  if (res) return html`<${TestResult} res=${res} tests=${tests} />`;
  if (i < 0) return html`<div class="screen c-train"><${Top} title="Fitness-Test" sub=${`${tests.length} Übungen · etwa 8 Minuten`} />
    <div class="scroll"><div class="pad">
      <p class="lead2">Dein Coach stuft dich danach ein und plant passend — alle vier Wochen siehst du, was sich getan hat. Wärm dich vorher zwei, drei Minuten auf.</p>
      <div class="testList mt16">${tests.map((t, k) => html`<div class="tl-row"><span class="tl-n num">${k + 1}</span>
        <div class="tl-img">${t.exercise && html`<${Loop} media=${t.exercise.media} still />`}</div>
        <div class="grow"><b>${t.name}</b><span>${t.mode === 'timed' ? `${t.seconds} Sekunden` : t.unit === 'sec' ? 'So lange wie möglich' : 'So viele wie möglich'}</span></div>
        ${last?.results?.[t.id] != null && html`<span class="tl-last num">${last.results[t.id]}${t.unit === 'sec' ? ' s' : ''}</span>`}</div>`)}</div>
      <button class="btn mt24" onClick=${() => setI(0)}><${Icon} n="play_arrow" fill />Test starten</button>
      ${d.test.history?.length > 0 && html`<${TestHistory} hist=${d.test.history} tests=${tests} />`}
      <div class="end" />
    </div></div></div>`;
  const t = tests[i];
  const next = (v) => {
    const all = { ...vals, [t.id]: v };
    setVals(all);
    if (i + 1 < tests.length) setI(i + 1); else finish(all);
  };
  return html`<${TestStep} key=${t.id} t=${t} k=${i} n=${tests.length} last=${last?.results?.[t.id]} busy=${busy} onDone=${next} onQuit=${() => nav('training', { replace: true })} />`;
}

function TestStep({ t, k, n, last, busy, onDone, onQuit }) {
  const [phase, setPhase] = useState('info'); // info | count | run | enter
  const [left, setLeft] = useState(t.seconds ?? 0);
  const [el, setEl] = useState(0);
  const [v, setV] = useState(last ?? (t.unit === 'sec' ? 0 : 10));
  const t0 = useRef(0);
  // 3-2-1, dann Timer (Wdh. in 60 s) oder Stoppuhr (Halten)
  useEffect(() => {
    if (phase === 'count') {
      let c = 3; beep('tick'); setLeft(3);
      const iv = setInterval(() => { c--; if (c > 0) { beep('tick'); setLeft(c); } else { clearInterval(iv); beep('go'); t0.current = Date.now(); setPhase('run'); } }, 1000);
      return () => clearInterval(iv);
    }
    if (phase === 'run') {
      const iv = setInterval(() => {
        const s = (Date.now() - t0.current) / 1000;
        setEl(s);
        if (t.mode === 'timed' && s >= t.seconds) { clearInterval(iv); beep('end'); haptic('success'); setPhase('enter'); }
        else if (t.mode === 'timed' && t.seconds - s <= 3.05 && Math.abs((t.seconds - s) % 1) < 0.1) beep('tick');
      }, 100);
      return () => clearInterval(iv);
    }
    return undefined;
  }, [phase]);
  const stopHold = () => { beep('end'); haptic('success'); setV(Math.round((Date.now() - t0.current) / 1000)); setPhase('enter'); };
  const timed = t.mode === 'timed';
  const hold = t.unit === 'sec';
  return html`<div class="screen testStep c-train">
    <div class="brTop"><button class="ib" onClick=${onQuit} aria-label="Abbrechen"><${Icon} n="close" /></button><span>${k + 1} von ${n}</span><span /></div>
    <div class="scroll"><div class="pad">
      <div class="ts-media">${t.exercise && html`<${Loop} media=${t.exercise.media} />`}</div>
      <h1 class="ts-name">${t.name}</h1>
      ${phase === 'info' && html`<p class="ts-how">${t.how}</p>
        ${last != null && html`<p class="small muted mt8">Letztes Mal: ${last}${hold ? ' s' : ''}</p>`}
        <button class="btn mt20" onClick=${() => (timed || hold ? setPhase('count') : setPhase('enter'))}>${timed ? `Start · ${t.seconds} s` : hold ? 'Start · Stoppuhr' : 'Bin fertig — Ergebnis eintragen'}</button>
        ${!timed && !hold && html`<p class="tiny muted mt8" style="text-align:center">Mach erst deine Wiederholungen, dann trag die Zahl ein.</p>`}`}
      ${phase === 'count' && html`<div class="ts-big num">${left}</div><p class="ts-sub">Gleich geht's los</p>`}
      ${phase === 'run' && html`<div class="ts-big num">${timed ? Math.max(0, Math.ceil(t.seconds - el)) : mmss(el)}</div>
        <p class="ts-sub">${timed ? 'Zähl mit — gleich trägst du die Zahl ein' : 'Halten! Tippe, sobald die Form bricht'}</p>
        ${hold && html`<button class="btn mt20" onClick=${stopHold}><${Icon} n="stop" fill />Stopp</button>`}`}
      ${phase === 'enter' && html`<p class="ts-sub">${hold ? 'Gehalten' : 'Wie viele saubere Wiederholungen?'}</p>
        <div class="stepper">
          <button class="ib ring" onClick=${() => setV(Math.max(0, v - (hold ? 5 : 1)))} aria-label="Weniger"><${Icon} n="remove" /></button>
          <div class="ts-big num" style="font-size:72px">${v}<small style="font-size:22px">${hold ? ' s' : ''}</small></div>
          <button class="ib ring" onClick=${() => setV(v + (hold ? 5 : 1))} aria-label="Mehr"><${Icon} n="add" /></button>
        </div>
        <div class="chips mt12" style="justify-content:center">${(hold ? [-10, 10] : [-5, 5]).map((x) => html`<button class="chip" onClick=${() => setV(Math.max(0, v + x))}>${x > 0 ? '+' : ''}${x}</button>`)}</div>
        <button class="btn mt20" disabled=${busy} onClick=${() => onDone(v)}>${busy ? html`<${Spinner} />` : k + 1 < n ? 'Weiter' : 'Auswerten'}</button>`}
      <div class="end" />
    </div></div>
  </div>`;
}

function TestResult({ res, tests }) {
  if (res.queued) return html`<div class="screen c-train"><div class="scroll"><div class="pad" style="padding-top:calc(28px + var(--sat))">
    <div class="tr-hero"><span class="lc-k">Fitness-Test</span><div class="lc-n mt8">Offline gespeichert</div></div>
    <p class="small muted mt16">Deine Werte sind auf dem Handy. Sobald dein Server erreichbar ist, werden sie gesendet und dein Coach stuft dich ein.</p>
    <div class="trList mt16">${tests.map((t) => html`<div class="trRow"><div class="grow"><b>${t.name}</b></div><span class="num tr-v">${res.results[t.id]}${t.unit === 'sec' ? ' s' : ''}</span></div>`)}</div>
    <button class="btn mt20" onClick=${() => nav('training', { replace: true })}>Fertig</button></div></div></div>`;
  const up = res.before != null && res.level > res.before;
  return html`<div class="screen c-train"><div class="scroll"><div class="pad" style="padding-top:calc(28px + var(--sat))">
    <div class="tr-hero"><span class="lc-k">${res.prev ? 'Dein neues Level' : 'Deine Einstufung'}</span><div class="lc-v num">${res.level}<small>/5</small></div>
      <div class="lc-n">${LEVEL_NAME[res.level]}${up ? ' · aufgestiegen' : ''}</div></div>
    <div class="trList mt20">${tests.map((t) => {
      const v = res.results[t.id];
      const p = res.prev?.results?.[t.id];
      const diff = p != null ? v - p : null;
      return html`<div class="trRow"><div class="grow"><b>${t.name}</b><div class="tr-dots">${[1, 2, 3, 4, 5].map((i) => html`<i class=${cls(i <= res.tiers[t.id] && 'on')} />`)}</div></div>
        <div style="text-align:right"><span class="num tr-v">${v}${t.unit === 'sec' ? ' s' : ''}</span>${diff != null && diff !== 0 && html`<span class=${cls('tr-d', diff > 0 ? 'up' : 'down')}>${diff > 0 ? '+' : ''}${diff}</span>`}</div></div>`;
    })}</div>
    <p class="small muted mt16">Dein Coach plant ab jetzt auf diesem Level. In vier Wochen testest du wieder.</p>
    <button class="btn mt20" onClick=${() => nav('training', { replace: true })}>Fertig</button>
    <div class="end" />
  </div></div></div>`;
}

function TestHistory({ hist, tests }) {
  return html`<div class="sec"><h2 class="h2">Bisherige Tests</h2></div>
    <div class="trList">${hist.slice(0, 5).map((h) => html`<div class="trRow"><div class="grow"><b>${relDay(h.day, today())}</b>
      <span class="small muted">${tests.map((t) => `${t.name.split(' ')[0]} ${h.results[t.id] ?? '–'}`).join(' · ')}</span></div><span class="lc-badge num">L${h.level}</span></div>`)}</div>`;
}
