// Calisthenics-Skills: Übersicht, Leiter je Skill (Stufen mit Freischalt-Ziel), Tutorials, Üben mit Haltezeit-Stoppuhr.
import { html, useEffect, useRef, useState } from '../../vendor/preact-htm.js';
import { api, beep, keepAwake, openVideo, voice } from '../platform.js';
import { back, nav, useCatalog, useData, setData, refresh, exById, toast, relDay, today } from '../app.js';
import { Icon, Loop, Sheet, Top, cls, Spinner } from '../ui.js';

const GROUP = { balance: 'Balance', druck: 'Drücken', zug: 'Ziehen', core: 'Rumpf', beine: 'Beine' };
const goalText = (g) => `${g.sets > 1 ? `${g.sets} × ` : ''}${g.value}${g.type === 'hold' ? ' s' : ' Wdh.'}`;

export function Skills() {
  const catalog = useCatalog();
  const [data] = useData('/fitness/skills');
  const states = new Map((data?.skills ?? []).map((s) => [s.id, s]));
  const skills = catalog?.skills ?? [];
  const active = skills.filter((s) => states.get(s.id)?.active);
  const rest = skills.filter((s) => !states.get(s.id)?.active);
  const locked = (s) => (s.requires ?? []).some((r) => !states.get(r)?.mastered && (states.get(r)?.step ?? 0) < 3);
  const card = (s) => {
    const st = states.get(s.id);
    const step = Math.min(st?.step ?? 0, s.steps.length - 1);
    const ex = exById(catalog, s.steps[step]?.exercise);
    return html`<button class=${cls('skill', st?.active && 'active')} style=${{ '--sc': s.color }} onClick=${() => nav(`skill/${s.id}`)}>
      <div class="sv"><${Loop} media=${ex?.media} still=${!st?.active} />${locked(s) && html`<span class="lock"><${Icon} n="lock" style="font-size:14px" />Später</span>`}</div>
      <div class="sb"><div class="sn">${s.name}</div><div class="ss">${st?.mastered ? 'Gemeistert' : st?.active ? s.steps[step].name : GROUP[s.group]}</div>
        <div class="lvl">${s.steps.map((_, i) => html`<i class=${cls(i < (st?.step ?? 0) && 'on')} />`)}</div></div>
    </button>`;
  };
  return html`<div class="screen tab c-skill">
    <div class="scroll">
      <header class="top big"><div class="tt"><h1>Skills</h1><div class="t2">Calisthenics Stufe für Stufe — mit klaren Zielen</div></div></header>
      <div class="pad">
        ${!catalog ? html`<div class="skel" style="height:300px" />` : html`
          ${active.length > 0 && html`<div class="sec" style="margin-top:8px"><h2 class="h2">Deine Ziele</h2></div><div class="skillGrid">${active.map(card)}</div>`}
          <div class="sec" style=${active.length ? '' : 'margin-top:8px'}><h2 class="h2">${active.length ? 'Weitere Skills' : 'Wähle deinen ersten Skill'}</h2></div>
          <div class="skillGrid">${rest.map(card)}</div>
          <p class="small muted mt20" style="line-height:1.5">Eine Stufe ist geschafft, wenn du ihr Ziel an <b style="color:var(--text)">zwei verschiedenen Tagen</b> erreichst. Aktive Skills baut der Coach als kurzen Skill-Block in dein Training ein.</p>`}
        <div class="end" />
      </div>
    </div>
  </div>`;
}

export function SkillDetail({ id }) {
  const catalog = useCatalog();
  const [data] = useData('/fitness/skills');
  const [view, setView] = useState(null);
  const [busy, setBusy] = useState(false);
  const s = catalog?.skills?.find((x) => x.id === id);
  if (!s) return html`<div class="screen"><${Top} title="Skill" /></div>`;
  const st = data?.skills?.find((x) => x.id === id) ?? { step: 0, hits: 0, active: false, log: [] };
  const stepIdx = Math.min(st.step, s.steps.length - 1);
  const step = s.steps[stepIdx];
  const ex = exById(catalog, step.exercise);
  const setSkill = async (patch) => {
    setBusy(true);
    try { setData('/fitness/skills', await api.post(`/fitness/skills/${id}`, patch)); } catch { toast('Server nicht erreichbar'); }
    setBusy(false);
  };
  return html`<div class="screen c-skill" style=${{ '--sc': s.color }}>
    <div class="scroll">
      <div class="exHero" style="height:270px"><${Loop} media=${ex?.media} eager /><button class="ib back" onClick=${() => back('skills')} aria-label="Zurück"><${Icon} n="arrow_back" /></button></div>
      <div class="pad">
        <div class="eyebrow mt20" style="color:var(--sc)">${GROUP[s.group]} · ${s.steps.length} Stufen</div>
        <h1 style="font-size:28px;font-weight:700;letter-spacing:-.4px;margin-top:4px">${s.name}</h1>
        <p class="muted mt8" style="line-height:1.5">${s.summary}</p>

        ${st.mastered ? html`<div class="celebrate card mt16"><${Icon} n="workspace_premium" fill /><div class="h2">Gemeistert</div><div class="small muted">Alle Stufen geschafft.</div></div>` : html`
        <div class="curStep mt20">
          <div class="pad16" style="padding:16px">
            <div class="row"><div class="grow"><div class="eyebrow">Stufe ${stepIdx + 1} von ${s.steps.length}</div><div class="h2 mt4">${step.name}</div></div>
              <div class="hits" title="Ziel an zwei Tagen erreicht">${[0, 1].map((i) => html`<i class=${cls(st.hits > i && 'on')}>${st.hits > i && html`<${Icon} n="check" />`}</i>`)}</div></div>
            <p class="small muted mt8" style="line-height:1.5">${step.description}</p>
            <div class="goal mt12"><div class="gv">${goalText(step.goal)}</div><div class="small muted" style="line-height:1.35">Ziel — an zwei<br/>verschiedenen Tagen</div></div>
            <ul class="errs tips mt8">${step.cues.map((c) => html`<li><${Icon} n="check_circle" />${c}</li>`)}</ul>
            <div class="row mt12">
              <button class="btn solid" style="--c:var(--skill)" onClick=${() => nav(`skill/${id}/ueben`)}><${Icon} n="timer" />Jetzt üben</button>
              ${ex && html`<button class="ib ring" style="width:52px;height:52px;border-radius:10px" onClick=${() => nav(`uebung/${ex.id}`)} aria-label="Anleitung"><${Icon} n="menu_book" /></button>`}
            </div>
          </div>
        </div>`}
        <button class="btn ghost mt12" disabled=${busy} onClick=${() => setSkill({ active: !st.active })}>${st.active ? html`<${Icon} n="bookmark_remove" />Nicht mehr als Ziel` : html`<${Icon} n="bookmark_add" />Als Ziel setzen`}</button>

        ${s.videos?.length > 0 && html`<div class="sec"><h2 class="h2">Tutorials</h2><span class="small muted">YouTube</span></div>
          <div class="yt">${s.videos.map((v) => html`<button onClick=${() => openVideo(v.id, v.title)}>
            <div class="th"><img src=${`https://i.ytimg.com/vi/${v.id}/mqdefault.jpg`} alt="" loading="lazy" /><${Icon} n="play_arrow" fill /></div>
            <div class="yn">${v.title}</div><div class="yc">${v.channel}</div></button>`)}</div>`}

        <div class="sec"><h2 class="h2">Der Weg</h2></div>
        <div class="path">${s.steps.map((p, i) => html`<button class=${cls('pstep', i < st.step && 'done', i === st.step && 'cur', i > st.step && 'lock')} onClick=${() => setView(i)}>
          <span class="pd">${i < st.step ? html`<${Icon} n="check" style="font-size:17px" />` : i + 1}</span>
          <div class="pn">${p.name}</div><div class="pg">${goalText(p.goal)}</div></button>`)}</div>

        ${s.prep?.length > 0 && html`<div class="sec"><h2 class="h2">Vorbereitung</h2></div>
          ${s.prep.map((pid) => { const e = exById(catalog, pid); return e && html`<button class="exRow" onClick=${() => nav(`uebung/${pid}`)}><${Loop} media=${e.media} cls="thumb s" still /><div class="tx"><div class="n">${e.name}</div><div class="a">${e.muscles.slice(0, 2).join(', ')}</div></div><${Icon} n="chevron_right" cls="faint" /></button>`; })}`}

        <div class="sec"><h2 class="h2">Sicherheit</h2></div>
        <ul class="errs">${s.safety.map((t) => html`<li><${Icon} n="health_and_safety" style="color:rgb(var(--amber))" />${t}</li>`)}</ul>

        ${st.log?.length > 0 && html`<div class="sec"><h2 class="h2">Protokoll</h2></div>
          <div class="list">${st.log.slice(0, 8).map((l) => html`<div class="li"><div class="tx"><div class="t1">${s.steps[l.step]?.name}</div><div class="t2">${relDay(l.day, today())}</div></div><span class="small num">${l.sets.join(' · ')}${s.steps[l.step]?.goal.type === 'hold' ? ' s' : ''}</span></div>`)}</div>`}
        <div class="end" />
      </div>
    </div>
    ${view != null && html`<${StepSheet} s=${s} i=${view} catalog=${catalog} current=${st.step} onClose=${() => setView(null)} onSet=${async () => { await setSkill({ step: view, active: true }); setView(null); }} />`}
  </div>`;
}

function StepSheet({ s, i, catalog, current, onClose, onSet }) {
  const p = s.steps[i];
  const ex = exById(catalog, p.exercise);
  return html`<${Sheet} title=${`Stufe ${i + 1}: ${p.name}`} onClose=${onClose}>
    <div class="c-skill" style=${{ '--sc': s.color }}>
      ${ex && html`<${Loop} media=${ex.media} eager cls="" />`}
      <p class="mt12" style="line-height:1.5">${p.description}</p>
      <div class="goal mt12"><div class="gv">${goalText(p.goal)}</div></div>
      <ul class="errs tips mt8">${p.cues.map((c) => html`<li><${Icon} n="check_circle" />${c}</li>`)}</ul>
      ${i !== current && html`<button class="btn ghost mt16" onClick=${onSet}>${i < current ? 'Zurück auf diese Stufe' : 'Hier einsteigen (kann ich schon)'}</button>`}
    </div>
  <//>`;
}

export function SkillPractice({ id }) {
  const catalog = useCatalog();
  const [data] = useData('/fitness/skills');
  const s = catalog?.skills?.find((x) => x.id === id);
  const st = data?.skills?.find((x) => x.id === id);
  if (!s || !data) return html`<div class="screen"><${Top} title="Üben" /></div>`;
  return html`<${Practice} s=${s} st=${st ?? { step: 0 }} catalog=${catalog} />`;
}

function Practice({ s, st, catalog }) {
  const stepIdx = Math.min(st.step, s.steps.length - 1);
  const step = s.steps[stepIdx];
  const ex = exById(catalog, step.exercise);
  const hold = step.goal.type === 'hold';
  const [sets, setSets] = useState([]);
  const [running, setRunning] = useState(false);
  const [sec, setSec] = useState(0);
  const [reps, setReps] = useState(step.goal.value);
  const [rest, setRest] = useState(0);
  const [feel, setFeel] = useState(2);
  const [busy, setBusy] = useState(false);
  const [res, setRes] = useState(null);
  const started = useRef(0);
  useEffect(() => { keepAwake(true); return () => keepAwake(false); }, []);
  useEffect(() => {
    if (!running) return undefined;
    const t = setInterval(() => {
      const v = Math.floor((Date.now() - started.current) / 100) / 10;
      setSec(v);
      if (Math.floor(v) === step.goal.value && v - Math.floor(v) < 0.1) { beep('go'); voice.say('Ziel erreicht'); }
    }, 100);
    return () => clearInterval(t);
  }, [running]);
  useEffect(() => {
    if (rest <= 0) return undefined;
    const t = setTimeout(() => { if (rest <= 4 && rest > 1) beep('tick'); if (rest === 1) beep('go'); setRest(rest - 1); }, 1000);
    return () => clearTimeout(t);
  }, [rest]);
  const startStop = () => {
    if (!running) { started.current = Date.now(); setSec(0); setRunning(true); beep('go'); setRest(0); return; }
    setRunning(false);
    setSets([...sets, Math.round(sec)]);
    setRest(hold ? 90 : 120);
  };
  const addReps = () => { setSets([...sets, reps]); setRest(120); };
  const save = async () => {
    setBusy(true);
    try {
      const r = await api.post(`/fitness/skills/${s.id}/log`, { step: stepIdx, sets, feel });
      refresh('/fitness/skills');
      setRes(r);
      if (r.advanced) { beep('go'); voice.say('Neue Stufe freigeschaltet'); }
    } catch { toast('Server nicht erreichbar'); }
    setBusy(false);
  };
  if (res) {
    const next = s.steps[stepIdx + 1];
    return html`<div class="screen c-skill" style=${{ '--sc': s.color }}><${Top} title=${s.name} onBack=${() => back(`skill/${s.id}`)} />
      <div class="scroll"><div class="pad">
        ${res.advanced ? html`<div class="celebrate"><${Icon} n="workspace_premium" fill /><h1 style="font-size:26px;font-weight:700">Stufe geschafft</h1>
          <p class="muted">${step.name} sitzt. ${next ? `Weiter mit: ${next.name} (${goalText(next.goal)}).` : 'Skill gemeistert!'}</p></div>`
        : res.met ? html`<div class="celebrate"><${Icon} n="check_circle" fill style="color:var(--sc)" /><h1 style="font-size:24px;font-weight:700">Ziel erreicht</h1>
          <p class="muted">${res.state.hits >= 2 ? 'Stark!' : 'Noch einmal an einem anderen Tag — dann ist die Stufe frei.'}</p></div>`
        : html`<div class="celebrate"><${Icon} n="trending_up" style="color:var(--sc)" /><h1 style="font-size:24px;font-weight:700">Eingetragen</h1>
          <p class="muted">Bester Satz: ${Math.max(...sets)}${hold ? ' s' : ''} — Ziel ${goalText(step.goal)}. Jede Einheit bringt dich näher.</p></div>`}
        <button class="btn mt24" onClick=${() => back(`skill/${s.id}`)}>Fertig</button>
      </div></div></div>`;
  }
  return html`<div class="screen c-skill" style=${{ '--sc': s.color }}>
    <${Top} title=${step.name} sub=${`Ziel ${goalText(step.goal)}`} onBack=${() => back(`skill/${s.id}`)} />
    <div class="scroll"><div class="pad">
      <div class="curStep"><div class="cv"><${Loop} media=${ex?.media} eager /></div></div>
      ${hold ? html`<div class="holdTimer"><div class="t" style=${{ color: sec >= step.goal.value ? 'var(--sc)' : undefined }}>${sec.toFixed(1)}</div><div class="small muted">Sekunden · Ziel ${step.goal.value} s</div></div>
        <button class="btn" style="height:60px;font-size:17px" onClick=${startStop}>${running ? html`<${Icon} n="stop" fill />Stopp` : html`<${Icon} n="play_arrow" fill />${sets.length ? 'Nächster Satz' : 'Start'}`}</button>`
      : html`<div class="holdTimer"><div class="row gap12"><button class="ib ring" onClick=${() => setReps(Math.max(0, reps - 1))}><${Icon} n="remove" /></button><div class="t">${reps}</div><button class="ib ring" onClick=${() => setReps(reps + 1)}><${Icon} n="add" /></button></div><div class="small muted">Wiederholungen · Ziel ${step.goal.value}</div></div>
        <button class="btn" style="height:60px;font-size:17px" onClick=${addReps}><${Icon} n="add" />Satz eintragen</button>`}
      ${rest > 0 && html`<div class="card pad16 mt12 row"><${Icon} n="hourglass_top" style="color:var(--sc)" /><div class="grow">Pause bis zum nächsten Satz</div><b class="num">${rest} s</b><button class="ib sm" onClick=${() => setRest(0)}><${Icon} n="close" /></button></div>`}
      ${sets.length > 0 && html`<div class="eyebrow mt20" style="text-align:center">Sätze</div><div class="setsRow mt8">${sets.map((v) => html`<span class=${cls('sv2', v >= step.goal.value && 'ok')}>${v}${hold ? ' s' : ''}</span>`)}</div>
        <div class="fbq"><div class="q" style="text-align:center">Wie hat es sich angefühlt?</div><div class="scale">${['Schwer', 'Machbar', 'Leicht'].map((l, i) => html`<button class=${cls(feel === i + 1 && 'on')} onClick=${() => setFeel(i + 1)}>${l}</button>`)}</div></div>
        <button class="btn solid mt20" disabled=${busy} onClick=${save}>${busy ? html`<${Spinner} />` : 'Eintragen'}</button>`}
      <ul class="errs tips mt20">${step.cues.map((c) => html`<li><${Icon} n="check_circle" />${c}</li>`)}</ul>
      <div class="end" />
    </div></div>
  </div>`;
}
