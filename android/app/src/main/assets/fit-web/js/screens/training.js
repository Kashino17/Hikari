// Training: freie Einheiten, Wochenbilanz, Verlauf mit Coach-Auswertungen, Übungsbibliothek, Bestwerte.
import { html, useState } from '../../vendor/preact-htm.js';
import { api } from '../platform.js';
import { nav, useData, useCatalog, setData, refresh, relDay, today, toast, parseDay, INTENSITY } from '../app.js';
import { Icon, Loop, Top, Columns, cls, Spinner, Empty } from '../ui.js';
import { PATTERN } from './exercise.js';
import { TrainingStats } from './stats.js';

const FREE = [
  ['ganzkoerper', 'Ganzkörper', 'burpee'],
  ['druck', 'Drücken', 'pushup'],
  ['zug', 'Ziehen', 'pullup'],
  ['beine', 'Beine & Po', 'squat'],
  ['core', 'Rumpf', 'plank'],
  ['ausdauer', 'Ausdauer', 'high_knees'],
  ['mobilitaet', 'Beweglichkeit', 'worlds_greatest'],
];
let trainTab = 'start';

/** Training in zwei Ebenen: »Starten« (freies Training mit Bildern) und »Fortschritt« (Zahlen, Verlauf, Bestwerte). */
export function Training() {
  const [state] = useData('/fitness/state');
  const [tab, setTab0] = useState(trainTab);
  const setTab = (t) => { trainTab = t; setTab0(t); };
  const total = state?.totals;
  return html`<div class="screen tab c-train">
    <div class="scroll">
      <header class="top big"><div class="tt"><h1>Training</h1><div class="t2">${total ? `${total.sessions} Einheiten · ${Math.round(total.seconds / 3600)} Stunden` : ' '}</div></div>
        <button class="ib" onClick=${() => nav('bibliothek')} aria-label="Übungen suchen"><${Icon} n="search" /></button></header>
      <div class="pad">
        <div class="seg"><button class=${cls(tab === 'start' && 'on')} onClick=${() => setTab('start')}>Starten</button><button class=${cls(tab === 'progress' && 'on')} onClick=${() => setTab('progress')}>Fortschritt</button></div>
        ${tab === 'start' ? html`<${StartTraining} state=${state} />` : html`<${Progress} />`}
        <div class="end" />
      </div>
    </div>
  </div>`;
}

function StartTraining({ state }) {
  const catalog = useCatalog();
  const [busy, setBusy] = useState(null);
  const [minutes, setMinutes] = useState(null);
  const mins = minutes ?? state?.profile?.sessionMinutes ?? 30;
  const ex = (id) => catalog?.exercises?.find((e) => e.id === id);
  const free = async (type) => {
    setBusy(type);
    try {
      const s = await api.post('/fitness/custom', { dayType: type, minutes: mins });
      setData('/fitness/today', s);
      refresh('/fitness/state');
      nav(`einheit/${s.id}`);
    } catch { toast('Server nicht erreichbar'); }
    setBusy(null);
  };
  return html`<div>
    <div class="durPick mt16">${[15, 20, 30, 45, 60].map((m) => html`<button class=${cls(mins === m && 'on')} onClick=${() => setMinutes(m)}><b class="num">${m}</b><span>min</span></button>`)}</div>
    <div class="typeGrid mt12">${FREE.map(([k, l, exId], i) => html`<button class=${cls('typeTile', i === 0 && 'wide')} disabled=${!!busy} onClick=${() => free(k)}>
      <div class="tt-img">${ex(exId) && html`<${Loop} media=${ex(exId).media} still />`}</div>
      <div class="tt-tx"><b>${l}</b><span>${busy === k ? 'Coach plant …' : `${mins} min`}</span></div>
      ${busy === k && html`<div class="tt-busy"><${Spinner} /></div>`}
    </button>`)}</div>
    <button class="libTile mt12" onClick=${() => nav('bibliothek')}>
      <div class="lt-imgs">${['pistol_squat', 'muscle_up', 'tuck_planche'].map((id) => ex(id) && html`<${Loop} media=${ex(id).media} still />`)}</div>
      <div class="lt-tx"><b>Übungsbibliothek</b><span>${catalog ? `${catalog.exercises.length} Übungen mit Video und Technik` : ' '}</span></div>
      <${Icon} n="chevron_right" />
    </button>
  </div>`;
}

function Progress() {
  const [hist] = useData('/fitness/history?days=90');
  return html`<div>
    <div class="mt16"><${TrainingStats} /></div>
    <div class="sec"><h2 class="h2">Zuletzt</h2>${hist?.sessions?.length > 3 && html`<button class="more" onClick=${() => nav('verlauf')}>Alle<${Icon} n="chevron_right" style="font-size:18px" /></button>`}</div>
    ${!hist ? html`<div class="skel" style="height:140px" />` : hist.sessions.length === 0 ? html`<div class="card pad16 small muted">Noch keine Einheit — dein erstes Training steht unter »Heute«.</div>`
      : html`<div class="list">${hist.sessions.slice(0, 3).map((s) => html`<${HistRow} s=${s} />`)}</div>`}
    ${hist?.bests?.length > 0 && html`<div class="sec"><h2 class="h2">Bestwerte</h2></div>
      <div class="bestRow">${hist.bests.slice(0, 8).map((b) => html`<button class="bestCard" onClick=${() => nav(`uebung/${b.exercise_id}`)}>
        <b class="num">${b.best}<small>${b.unit === 'sec' ? ' s' : ''}</small></b><span>${b.name}</span></button>`)}</div>`}
  </div>`;
}

function HistRow({ s }) {
  const [open, setOpen] = useState(false);
  return html`<button class="li" style="flex-direction:column;align-items:stretch" onClick=${() => setOpen(!open)}>
    <div class="row"><div class="iconTile soft"><${Icon} n="check" /></div>
      <div class="tx"><div class="t1">${s.title}</div><div class="t2">${relDay(s.day, today())} · ${Math.round(s.seconds / 60)} min · ${INTENSITY[s.intensity] ?? ''}${s.pain?.length ? ' · Beschwerden' : ''}</div></div>
      ${s.review && html`<${Icon} n=${open ? 'expand_less' : 'expand_more'} cls="faint" />`}</div>
    ${open && s.review && html`<div class="small mt8" style="padding-left:52px;line-height:1.5"><div style="font-weight:500">${s.review.headline}</div>
      ${(s.review.next ?? []).map((n) => html`<div class="muted mt4">· ${n}</div>`)}</div>`}
  </button>`;
}

export function History() {
  const [hist] = useData('/fitness/history?days=365');
  return html`<div class="screen c-train"><${Top} title="Verlauf" sub=${hist ? `${hist.sessions.length} Einheiten im letzten Jahr` : ''} />
    <div class="scroll"><div class="pad">${hist ? html`<div class="list">${hist.sessions.map((s) => html`<${HistRow} s=${s} />`)}</div>` : html`<div class="skel" style="height:300px" />`}<div class="end" /></div></div></div>`;
}

export function Library() {
  const catalog = useCatalog();
  const [q, setQ] = useState('');
  const [pat, setPat] = useState('');
  const list = (catalog?.exercises ?? []).filter((e) => (!pat || e.pattern === pat) && (!q || `${e.name} ${e.en} ${e.muscles.join(' ')}`.toLowerCase().includes(q.toLowerCase())));
  return html`<div class="screen c-train"><${Top} title="Übungen" sub=${catalog ? `${catalog.exercises.length} Übungen mit Video` : ''} />
    <div class="pad" style="flex:none">
      <div class="search"><${Icon} n="search" cls="faint" /><input placeholder="Übung oder Muskel suchen" value=${q} onInput=${(e) => setQ(e.target.value)} />${q && html`<button class="ib sm" onClick=${() => setQ('')}><${Icon} n="close" /></button>`}</div>
      <div class="chips mt12" style="padding-bottom:8px"><button class=${cls('chip', !pat && 'on')} onClick=${() => setPat('')}>Alle</button>${Object.entries(PATTERN).map(([k, l]) => html`<button class=${cls('chip', pat === k && 'on')} onClick=${() => setPat(k)}>${l}</button>`)}</div>
    </div>
    <div class="scroll"><div class="pad">
      ${list.length === 0 && catalog ? html`<${Empty} icon="search_off" title="Nichts gefunden" />` : list.map((e) => html`<button class="exRow" onClick=${() => nav(`uebung/${e.id}`)}>
        <${Loop} media=${e.media} cls="thumb" still /><div class="tx"><div class="n">${e.name}</div><div class="a">${e.muscles.slice(0, 2).join(', ')} · Stufe ${e.level}</div></div><${Icon} n="chevron_right" cls="faint" /></button>`)}
      <div class="end" /></div></div>
  </div>`;
}
