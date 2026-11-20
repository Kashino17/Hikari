// Programme über mehrere Wochen, Wochenvorschau, Muskelbalance und der Wochenrückblick des Coachs.
// Server: /fitness/programs, /fitness/program, /fitness/week, /fitness/week/review (program.ts, week.ts).
import { html, useEffect, useState } from '../../vendor/preact-htm.js';
import { api } from '../platform.js';
import { nav, useData, refresh, relDay, today, parseDay, toast } from '../app.js';
import { Icon, Top, Sheet, cls, Loop, Spinner } from '../ui.js';

const WD = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'];
const TYPE_ICON = { ganzkoerper: 'accessibility_new', druck: 'front_hand', zug: 'rowing', beine: 'directions_walk', core: 'center_focus_strong', ausdauer: 'bolt', mobilitaet: 'self_improvement' };
const GROUPS = [['druck', 'Drücken'], ['zug', 'Ziehen'], ['beine', 'Beine'], ['core', 'Rumpf'], ['ausdauer', 'Ausdauer']];

/** Programm-Karte (oder Einladung) + Wochenleiste — oben unter »Training · Starten«. */
export function WeekPlan() {
  const [w] = useData('/fitness/week');
  if (!w) return html`<div class="skel" style="height:170px" />`;
  const ph = w.program && !w.program.done ? w.program : null;
  const t = today();
  return html`<div class="weekPlan">
    ${ph ? html`<button class="progCard" onClick=${() => nav('programme')}>
        <div class="row" style="justify-content:space-between;align-items:flex-start">
          <div><span class="pc-k">${ph.name} · Woche ${ph.week} von ${ph.weeks}</span><b class="pc-t">${ph.phase}</b><span class="pc-n">${ph.note}</span></div>
          ${ph.deload && html`<span class="pc-tag">Entlastung</span>`}
        </div>
        <div class="pc-weeks">${Array.from({ length: ph.weeks }, (_, i) => html`<i class=${cls(i + 1 < ph.week && 'done', i + 1 === ph.week && 'cur')} />`)}</div>
      </button>`
      : html`<button class="progInvite" onClick=${() => nav('programme')}><${Icon} n="flag" /><div class="grow"><b>${w.program?.done ? 'Programm geschafft — wie geht es weiter?' : 'Programm wählen'}</b><span>Mehrere Wochen mit Ziel, Steigerung und Entlastungswoche</span></div><${Icon} n="chevron_right" /></button>`}
    <div class="wkStrip">${w.days.map((d, i) => {
      const s = d.session;
      const type = s?.dayType ?? d.predicted?.dayType;
      const state = s?.status === 'done' ? 'done' : s ? 'planned' : d.predicted ? 'pred' : d.training ? 'miss' : 'rest';
      return html`<div class=${cls('wkDay', state, d.day === t && 'today')}>
        <span class="wd">${WD[i]}</span>
        <span class="wi">${state === 'done' ? html`<${Icon} n="check" />` : type ? html`<${Icon} n=${TYPE_ICON[type] ?? 'fitness_center'} />` : state === 'miss' ? html`<${Icon} n="remove" />` : ''}</span>
        <span class="wt">${s?.title ? (s.status === 'done' ? `${s.minutes ?? ''}′` : 'geplant') : d.predicted ? d.predicted.title.split(' ')[0] : d.training ? '' : 'frei'}</span>
      </div>`;
    })}</div>
  </div>`;
}

/** Programme zur Auswahl — Bildkacheln, Details mit Phasen-Zeitstrahl im Blatt. */
export function Programs() {
  const [d] = useData('/fitness/programs');
  const [open, setOpen] = useState(null);
  const [busy, setBusy] = useState(false);
  if (!d) return html`<div class="screen"><${Top} title="Programme" /><div class="pad"><div class="skel" style="height:420px" /></div></div>`;
  const cur = d.current && !d.current.done ? d.current : null;
  const start = async (id) => {
    setBusy(true);
    try { await api.post('/fitness/program', { id }); refresh('/fitness/week'); refresh('/fitness/programs'); refresh('/fitness/today'); toast(id ? 'Programm gestartet — dein Coach plant ab heute danach' : 'Programm beendet'); setOpen(null); nav('training', { replace: true }); } catch { toast('Server nicht erreichbar'); }
    setBusy(false);
  };
  const p = open && d.programs.find((x) => x.id === open);
  return html`<div class="screen c-train"><${Top} title="Programme" sub="Mehrere Wochen, ein klares Ziel" />
    <div class="scroll"><div class="pad">
      <div class="progGrid">${d.programs.map((x, i) => html`<button class=${cls('progTile', i === 0 && 'wide', cur?.id === x.id && 'on', !x.machbar && 'off')} onClick=${() => setOpen(x.id)}>
        <div class="pt-img">${x.exercise && html`<${Loop} media=${x.exercise.media} still />`}${cur?.id === x.id && html`<span class="pt-cur">Läuft · Woche ${cur.week}</span>`}</div>
        <div class="pt-tx"><b>${x.name}</b><span>${x.wochen} Wochen · ${x.fuer}</span></div>
      </button>`)}</div>
      <div class="end" />
    </div></div>
    ${p && html`<${Sheet} title=${p.name} onClose=${() => setOpen(null)}><div class="pad progDetail">
      <p class="pd-goal">${p.ziel}</p>
      <div class="pd-time">${p.phasen.map((ph) => html`<div class=${cls('pd-ph', ph.volumen <= 0.7 && 'deload')} style=${{ flex: ph.wochen.length }}>
        <i /><b>${ph.name}</b><span>${ph.wochen.length > 1 ? `Woche ${ph.wochen[0]}–${ph.wochen[ph.wochen.length - 1]}` : `Woche ${ph.wochen[0]}`}</span></div>`)}</div>
      <div class="pd-list mt12">${p.phasen.map((ph) => html`<div><b>${ph.name}</b><span>${ph.notiz}</span></div>`)}</div>
      ${!p.machbar && html`<p class="small mt12" style="color:#F2B847">Dafür brauchst du ${p.braucht.join(' oder ')} — in »Ich« bei der Ausrüstung eintragen.</p>`}
      ${cur?.id === p.id ? html`<button class="btn ghost mt16" disabled=${busy} onClick=${() => start(null)}>Programm beenden</button>`
        : html`<button class="btn mt16" disabled=${busy || !p.machbar} onClick=${() => start(p.id)}>${cur ? 'Wechseln und starten' : 'Programm starten'}</button>`}
      <p class="tiny muted mt8">Woche 1 ist diese Woche. Am Ende steht der Fitness-Test.</p>
    </div><//>`}
  </div>`;
}

/** Muskelbalance: Arbeitssätze diese Woche je Gruppe, Strich = Schnitt der 4 Wochen davor. */
export function BalanceCard() {
  const [w] = useData('/fitness/week');
  if (!w) return null;
  const b = w.balance.week;
  const a = w.balance.avg4;
  const max = Math.max(4, ...GROUPS.map(([k]) => Math.max(b[k] ?? 0, a[k] ?? 0)));
  const total = GROUPS.reduce((s, [k]) => s + (b[k] ?? 0), 0);
  const low = total >= 6 ? GROUPS.filter(([k]) => k !== 'ausdauer' && (b[k] ?? 0) < total * 0.12).map(([, l]) => l) : [];
  return html`<div class="sec"><h2 class="h2">Muskelbalance</h2><span class="small muted">Sätze diese Woche</span></div>
    <div class="card pad16 balCard">
      ${GROUPS.map(([k, l]) => html`<div class="balRow"><span class="bl">${l}</span>
        <div class="bb"><i style=${{ width: `${((b[k] ?? 0) / max) * 100}%` }} />${(a[k] ?? 0) > 0 && html`<em style=${{ left: `${((a[k] ?? 0) / max) * 100}%` }} />`}</div>
        <span class="bn num">${b[k] ?? 0}</span></div>`)}
      <p class="tiny muted mt8">${total === 0 ? 'Noch keine Sätze diese Woche.' : low.length ? `Wenig ${low.join(' und ')} — dein Coach gleicht das in den nächsten Einheiten aus.` : 'Ausgewogen verteilt.'} Strich: Schnitt der letzten vier Wochen.</p>
    </div>`;
}

/** Karte auf »Heute«: Wochenrückblick der letzten Woche (Mo–Mi), wenn dort trainiert wurde. */
export function WeekReviewCard() {
  const [w] = useData('/fitness/week');
  if (!w || !w.review.sessions) return null;
  const wd = (parseDay(today()).getDay() + 6) % 7;
  if (wd > 2 && w.review.headline) return null;
  return html`<button class="reviewCard mt12" onClick=${() => nav(`woche/${w.review.monday}`)}>
    <${Icon} n="insights" /><div class="grow"><b>Dein Wochenrückblick</b><span>${w.review.headline ?? `${w.review.sessions} Einheiten letzte Woche — was dein Coach daraus macht`}</span></div><${Icon} n="chevron_right" /></button>`;
}

export function WeekReview({ monday }) {
  const [r, setR] = useState(null);
  const [err, setErr] = useState(null);
  const load = (force) => { setR(null); setErr(null); api.get(`/fitness/week/review?monday=${monday}${force ? '&force=1' : ''}`).then((x) => setR(x.review)).catch((e) => setErr(e.status === 503 ? 'Kein Gemini-Schlüssel auf dem Server.' : 'Der Coach ist gerade nicht erreichbar.')); };
  useEffect(() => load(false), [monday]);
  const end = new Date(parseDay(monday)); end.setDate(end.getDate() + 6);
  const label = `${parseDay(monday).getDate()}.–${end.getDate()}.${end.getMonth() + 1}.`;
  return html`<div class="screen c-train"><${Top} title="Wochenrückblick" sub=${`Woche ${label}`} actions=${html`<button class="ib" onClick=${() => load(true)} aria-label="Neu"><${Icon} n="refresh" /></button>`} />
    <div class="scroll"><div class="pad">
      ${err ? html`<div class="card pad16 small muted">${err}</div>` : !r ? html`<div class="row gap12 mt12"><${Spinner} /><div class="muted">Dein Coach schaut auf deine Woche …</div></div><div class="skel mt16" style="height:300px" />` : html`
        <div class="wrHero"><b>${r.headline}</b>${r.zahlen && html`<span>${r.zahlen}</span>`}</div>
        ${r.gut?.length > 0 && html`<div class="wrBlock good"><span class="eyebrow">Gut gelaufen</span>${r.gut.map((t) => html`<p>${t}</p>`)}</div>`}
        ${r.achten?.length > 0 && html`<div class="wrBlock watch"><span class="eyebrow">Darauf achten</span>${r.achten.map((t) => html`<p>${t}</p>`)}</div>`}
        ${r.naechste_woche?.length > 0 && html`<div class="wrBlock next"><span class="eyebrow">Nächste Woche</span>${r.naechste_woche.map((t) => html`<p>${t}</p>`)}</div>`}
        ${r.fokus && html`<div class="wrFocus"><${Icon} n="flag" /><b>${r.fokus}</b></div>`}`}
      <div class="end" />
    </div></div></div>`;
}
