// Training: freie Einheiten, Wochenbilanz, Verlauf mit Coach-Auswertungen, Übungsbibliothek, Bestwerte.
import { html, useState } from '../../vendor/preact-htm.js';
import { api } from '../platform.js';
import { nav, useData, useCatalog, setData, refresh, relDay, today, toast, parseDay, INTENSITY } from '../app.js';
import { Icon, Loop, Top, Columns, cls, Spinner, Empty } from '../ui.js';
import { PATTERN } from './exercise.js';

const FREE = [
  ['ganzkoerper', 'Ganzkörper', 'accessibility_new'],
  ['druck', 'Drücken', 'front_hand'],
  ['zug', 'Ziehen', 'rowing'],
  ['beine', 'Beine & Po', 'directions_walk'],
  ['core', 'Rumpf', 'self_improvement'],
  ['ausdauer', 'Ausdauer', 'favorite'],
  ['mobilitaet', 'Beweglichkeit', 'airline_seat_flat_angled'],
];

export function Training() {
  const [hist] = useData('/fitness/history?days=90');
  const [state] = useData('/fitness/state');
  const [busy, setBusy] = useState(null);
  const [minutes, setMinutes] = useState(null);
  const mins = minutes ?? state?.profile?.sessionMinutes ?? 30;
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
  // Minuten je Woche (letzte 8 Wochen)
  const weeks = [];
  if (hist) {
    const t = parseDay(today());
    const monday = new Date(t); monday.setDate(t.getDate() - ((t.getDay() + 6) % 7));
    for (let w = 7; w >= 0; w--) {
      const from = new Date(monday); from.setDate(monday.getDate() - w * 7);
      const to = new Date(from); to.setDate(from.getDate() + 7);
      const v = hist.sessions.filter((s) => { const d = parseDay(s.day); return d >= from && d < to; }).reduce((a, s) => a + Math.round(s.seconds / 60), 0);
      weeks.push({ v, l: w === 0 ? 'jetzt' : `${from.getDate()}.${from.getMonth() + 1}.`, dim: w !== 0 });
    }
  }
  const total = state?.totals;
  return html`<div class="screen tab c-train">
    <div class="scroll">
      <header class="top big"><div class="tt"><h1>Training</h1><div class="t2">${total ? `${total.sessions} Einheiten · ${Math.round(total.seconds / 3600)} Stunden insgesamt` : ' '}</div></div>
        <button class="ib" onClick=${() => nav('bibliothek')} aria-label="Übungen"><${Icon} n="search" /></button></header>
      <div class="pad">
        <div class="card pad16 tinted">
          <div class="row"><div class="grow"><div class="eyebrow">Diese Woche</div><div style="font-size:26px;font-weight:700;letter-spacing:-.4px;margin-top:2px">${weeks.at(-1)?.v ?? 0} <span class="small muted" style="font-weight:500">Minuten</span></div></div>
            <div style="text-align:right"><div class="eyebrow">Serie</div><div class="row gap6" style="justify-content:flex-end;font-size:22px;font-weight:700;color:rgb(var(--amber))"><${Icon} n="local_fire_department" fill />${state?.streak ?? 0}</div></div></div>
          <div class="mt16">${hist ? html`<${Columns} items=${weeks} height=${110} />` : html`<div class="skel" style="height:110px" />`}</div>
        </div>

        <div class="sec"><h2 class="h2">Freies Training</h2><span class="small muted">plant der Coach</span></div>
        <div class="chips">${[15, 20, 30, 45, 60].map((m) => html`<button class=${cls('chip', mins === m && 'on')} onClick=${() => setMinutes(m)}>${m} min</button>`)}</div>
        <div class="optGrid mt12">${FREE.map(([k, l, ic]) => html`<button class="opt" disabled=${!!busy} onClick=${() => free(k)}>
          ${busy === k ? html`<${Spinner} />` : html`<${Icon} n=${ic} />`}<b>${l}</b><span>${mins} min</span></button>`)}</div>

        <div class="sec"><h2 class="h2">Verlauf</h2>${hist?.sessions?.length > 5 && html`<button class="more" onClick=${() => nav('verlauf')}>Alle<${Icon} n="chevron_right" style="font-size:18px" /></button>`}</div>
        ${!hist ? html`<div class="skel" style="height:180px" />` : hist.sessions.length === 0 ? html`<div class="card pad16 small muted">Noch keine Einheit — dein erstes Training steht unter »Heute«.</div>`
          : html`<div class="list">${hist.sessions.slice(0, 5).map((s) => html`<${HistRow} s=${s} />`)}</div>`}

        ${hist?.bests?.length > 0 && html`<div class="sec"><h2 class="h2">Bestwerte</h2></div>
          <div class="list">${hist.bests.slice(0, 6).map((b) => html`<button class="li" onClick=${() => nav(`uebung/${b.exercise_id}`)}>
            <div class="iconTile soft c-amber"><${Icon} n="military_tech" /></div><div class="tx"><div class="t1">${b.name}</div><div class="t2">${b.times}× trainiert</div></div>
            <b class="num">${b.best}${b.unit === 'sec' ? ' s' : ''}</b></button>`)}</div>`}

        <button class="card pad16 mt20 row" style="width:100%;text-align:left" onClick=${() => nav('bibliothek')}>
          <div class="iconTile"><${Icon} n="video_library" /></div><div class="grow"><div class="h3">Übungsbibliothek</div><div class="small muted">Videos, Ausführung, häufige Fehler</div></div><${Icon} n="chevron_right" cls="faint" />
        </button>
        <div class="end" />
      </div>
    </div>
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
