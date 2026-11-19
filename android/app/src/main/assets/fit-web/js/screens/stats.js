// Verlauf wie in Health-Apps: Tag / Woche / Monat / Jahr, beliebig weit zurück, Balken antippbar,
// Vergleich mit dem Zeitraum davor. Für Training (Minuten, Einheiten, kcal) und Ernährung (jeder Nährstoff).
import { html, useEffect, useMemo, useState } from '../../vendor/preact-htm.js';
import { api, local } from '../platform.js';
import { fmtNum, parseDay, today as todayStr } from '../app.js';
import { Icon, Seg, cls, Spinner } from '../ui.js';

const MONTHS = ['Jan', 'Feb', 'Mär', 'Apr', 'Mai', 'Jun', 'Jul', 'Aug', 'Sep', 'Okt', 'Nov', 'Dez'];
const MONTHS_LONG = ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'];
const WD = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'];
const iso = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const addDays = (d, n) => { const x = new Date(d); x.setDate(x.getDate() + n); return x; };

/** Zeitraum zu Ansicht + Versatz (0 = aktuell, -1 = davor …) samt Balken-Einteilung. */
export function period(mode, offset) {
  const t = parseDay(todayStr());
  if (mode === 'D') {
    const d = addDays(t, offset);
    const prev = addDays(d, -1);
    const label = offset === 0 ? 'Heute' : offset === -1 ? 'Gestern' : `${WD[(d.getDay() + 6) % 7]}, ${d.getDate()}. ${MONTHS[d.getMonth()]} ${d.getFullYear()}`;
    return { mode, from: iso(d), to: iso(d), prevFrom: iso(prev), prevTo: iso(prev), label, prevLabel: 'dem Vortag', buckets: [{ key: iso(d), days: [iso(d)], label: '' }] };
  }
  if (mode === 'W') {
    const monday = addDays(t, -((t.getDay() + 6) % 7) + offset * 7);
    const days = Array.from({ length: 7 }, (_, i) => iso(addDays(monday, i)));
    const end = addDays(monday, 6);
    const label = offset === 0 ? 'Diese Woche' : offset === -1 ? 'Letzte Woche' : `${monday.getDate()}. ${MONTHS[monday.getMonth()]} – ${end.getDate()}. ${MONTHS[end.getMonth()]} ${end.getFullYear()}`;
    return { mode, from: days[0], to: days[6], prevFrom: iso(addDays(monday, -7)), prevTo: iso(addDays(monday, -1)), label, prevLabel: 'der Woche davor', buckets: days.map((d, i) => ({ key: d, days: [d], label: WD[i] })) };
  }
  if (mode === 'M') {
    const first = new Date(t.getFullYear(), t.getMonth() + offset, 1, 12);
    const n = new Date(first.getFullYear(), first.getMonth() + 1, 0).getDate();
    const days = Array.from({ length: n }, (_, i) => iso(addDays(first, i)));
    const pf = new Date(first.getFullYear(), first.getMonth() - 1, 1, 12);
    const pl = new Date(first.getFullYear(), first.getMonth(), 0, 12);
    return { mode, from: days[0], to: days[n - 1], prevFrom: iso(pf), prevTo: iso(pl), label: `${MONTHS_LONG[first.getMonth()]} ${first.getFullYear()}`, prevLabel: 'dem Vormonat', buckets: days.map((d, i) => ({ key: d, days: [d], label: [0, 7, 14, 21, 28].includes(i) ? String(i + 1) : '' })) };
  }
  const y = t.getFullYear() + offset;
  const buckets = Array.from({ length: 12 }, (_, m) => {
    const n = new Date(y, m + 1, 0).getDate();
    return { key: `${y}-${m + 1}`, days: Array.from({ length: n }, (_, i) => iso(new Date(y, m, i + 1, 12))), label: MONTHS[m][0] };
  });
  return { mode, from: `${y}-01-01`, to: `${y}-12-31`, prevFrom: `${y - 1}-01-01`, prevTo: `${y - 1}-12-31`, label: String(y), prevLabel: 'dem Vorjahr', buckets };
}

/** Laufender Zeitraum: fair vergleichen — nur gleich viele Tage des Vorzeitraums (Mo–Do gegen Mo–Do). */
export function fairPeriod(mode, offset) {
  const p = period(mode, offset);
  if (offset !== 0 || mode === 'D') return p;
  const t = todayStr();
  const elapsed = Math.round((parseDay(t) - parseDay(p.from)) / 86400000);
  const prevTo = iso(addDays(parseDay(p.prevFrom), elapsed));
  const pf = parseDay(p.prevFrom), pt = parseDay(prevTo);
  const prevLabel = mode === 'W' ? `${WD[0]}–${WD[(pt.getDay() + 6) % 7]} letzter Woche` : mode === 'M' ? `${pf.getDate()}.–${pt.getDate()}. ${MONTHS_LONG[pf.getMonth()]}` : `bis ${pt.getDate()}. ${MONTHS[pt.getMonth()]} ${pt.getFullYear()}`;
  return { ...p, to: t, prevTo: prevTo < p.prevTo ? prevTo : p.prevTo, prevLabel, fullTo: p.to };
}

/** Kopf mit Ansicht (T/W/M/J) und Blättern. */
function PeriodNav({ mode, setMode, offset, setOffset, label, canBack }) {
  return html`<div>
    <${Seg} items=${[['D', 'T'], ['W', 'W'], ['M', 'M'], ['Y', 'J']]} value=${mode} onChange=${(m) => { setMode(m); setOffset(0); }} />
    <div class="pnav mt12">
      <button class="ib sm" disabled=${!canBack} onClick=${() => setOffset(offset - 1)} aria-label="Zurück"><${Icon} n="chevron_left" /></button>
      <div class="plabel">${label}</div>
      <button class="ib sm" disabled=${offset >= 0} onClick=${() => setOffset(offset + 1)} aria-label="Vor"><${Icon} n="chevron_right" /></button>
    </div>
  </div>`;
}

/** Balken mit optionaler Ziellinie; Antippen zeigt den Wert. */
function Bars({ items, goal, fmt, unit, height = 170, selected, onSelect }) {
  const vals = items.map((i) => i.v ?? 0);
  const max = Math.max(1, ...vals, goal ?? 0) * 1.08;
  return html`<div class="bars" style=${{ height: `${height}px` }}>
    ${goal != null && goal > 0 && html`<div class="goalLine" style=${{ bottom: `${(goal / max) * (height - 22) + 22}px` }}><span>Ziel ${fmt(goal)} ${unit}</span></div>`}
    ${items.map((it, i) => html`<button class=${cls('bcol', selected === i && 'sel', it.v == null && 'none')} onClick=${() => onSelect(selected === i ? null : i)}>
      <div class="bwrap"><i style=${{ height: `${Math.max(it.v ? 3 : 0, ((it.v ?? 0) / max) * (height - 22))}px`, ...(goal && it.v != null && it.v < goal * 0.6 ? { opacity: 0.55 } : {}) }} /></div>
      <span class="bl">${it.label}</span>
    </button>`)}
  </div>`;
}

const pct = (a, b) => (b ? Math.round(((a - b) / b) * 100) : null);
const Delta = ({ now, before, prevLabel, invert }) => {
  const p = pct(now, before);
  if (p == null || !Number.isFinite(p)) return html`<span class="small muted">Kein Vergleich mit ${prevLabel}</span>`;
  const good = invert ? p <= 0 : p >= 0;
  return html`<span class=${cls('delta', good ? 'up' : 'down')}><${Icon} n=${p >= 0 ? 'trending_up' : 'trending_down'} style="font-size:16px" />${p >= 0 ? '+' : ''}${p} % gegenüber ${prevLabel}</span>`;
};

function useRange(kind, p) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  useEffect(() => {
    let live = true;
    setLoading(true);
    api.get(`/fitness/stats/${kind}?from=${p.prevFrom}&to=${p.to}`).then((d) => { if (live) { setData(d); setLoading(false); } }).catch(() => live && setLoading(false));
    return () => { live = false; };
  }, [kind, p.prevFrom, p.to]);
  return [data, loading];
}

// ── Training ────────────────────────────────────────────────────────────────
const T_METRICS = { minutes: ['Minuten', 'min'], sessions: ['Einheiten', ''], kcal: ['Kalorien', 'kcal'] };

export function TrainingStats() {
  const [mode, setMode] = useState(local.get('tstats:mode', 'W'));
  const [offset, setOffset] = useState(0);
  const [metric, setMetric] = useState(local.get('tstats:metric', 'minutes'));
  const [sel, setSel] = useState(null);
  useEffect(() => { local.set('tstats:mode', mode); local.set('tstats:metric', metric); setSel(null); }, [mode, metric, offset]);
  const p = useMemo(() => fairPeriod(mode, offset), [mode, offset]);
  const [d, loading] = useRange('training', p);
  const days = d?.days ?? {};
  const val = (day) => days[day]?.[metric] ?? 0;
  const sum = (from, to) => Object.entries(days).filter(([k]) => k >= from && k <= to).reduce((a, [, v]) => a + (v[metric] ?? 0), 0);
  let items;
  if (mode === 'D') {
    // Tag: Minuten je Stunde (bei Einheiten/kcal: die Einheiten des Tages mit ihrer Stunde)
    const hours = days[p.from]?.hours ?? {};
    items = Array.from({ length: 24 }, (_, h) => ({ v: metric === 'minutes' ? hours[h] ?? 0 : hours[h] ? (metric === 'sessions' ? 1 : Math.round(((days[p.from]?.kcal ?? 0) * hours[h]) / Math.max(1, days[p.from]?.minutes ?? 1))) : 0, label: h % 6 === 0 ? String(h) : '', key: `${h} Uhr` }));
  } else items = p.buckets.map((b) => ({ v: b.days.reduce((a, day) => a + val(day), 0), label: b.label, key: b.days.length === 1 ? b.days[0] : b.key }));
  const now = sum(p.from, p.to);
  const before = sum(p.prevFrom, p.prevTo);
  const activeDays = Object.entries(days).filter(([k, v]) => k >= p.from && k <= p.to && v.sessions > 0).length;
  const [name, unit] = T_METRICS[metric];
  const selItem = sel != null ? items[sel] : null;
  const firstDay = d?.first;
  const canBack = !firstDay || firstDay < p.from || offset > -2;
  return html`<div class="card pad16 tinted c-train statsCard">
    <${PeriodNav} mode=${mode} setMode=${setMode} offset=${offset} setOffset=${setOffset} label=${p.label} canBack=${canBack} />
    <div class="chips wrap mt12">${Object.entries(T_METRICS).map(([k, [l]]) => html`<button class=${cls('chip', metric === k && 'on')} onClick=${() => setMetric(k)}>${l}</button>`)}</div>
    <div class="row mt16" style="align-items:flex-end">
      <div class="grow">
        <div class="eyebrow">${selItem ? fmtKey(selItem.key, mode) : mode === 'D' ? 'Gesamt' : 'Gesamt im Zeitraum'}</div>
        <div class="bigNum num">${fmtNum(selItem ? selItem.v : now)} <small>${unit || (metric === 'sessions' ? ((selItem ? selItem.v : now) === 1 ? 'Einheit' : 'Einheiten') : '')}</small></div>
      </div>
      ${loading && html`<${Spinner} />`}
    </div>
    ${!selItem && html`<div class="mt4"><${Delta} now=${now} before=${before} prevLabel=${p.prevLabel} /></div>`}
    ${!selItem && mode !== 'D' && activeDays > 0 && html`<div class="small muted mt4">${activeDays} Trainingstage · Ø ${fmtNum(now / activeDays)} ${unit || name} je Trainingstag</div>`}
    <div class="mt16"><${Bars} items=${items} fmt=${fmtNum} unit=${unit} selected=${sel} onSelect=${setSel} /></div>
    ${mode === 'D' && days[p.from]?.titles?.length > 0 && html`<div class="small muted mt8">${days[p.from].titles.join(' · ')}</div>`}
  </div>`;
}

function fmtKey(key, mode) {
  if (/^\d+ Uhr$/.test(key)) return key;
  if (/^\d{4}-\d{2}-\d{2}$/.test(key)) { const d = parseDay(key); return `${WD[(d.getDay() + 6) % 7]}, ${d.getDate()}. ${MONTHS[d.getMonth()]}`; }
  const [y, m] = key.split('-').map(Number);
  return mode === 'Y' ? `${MONTHS_LONG[m - 1]} ${y}` : key;
}

// ── Ernährung ───────────────────────────────────────────────────────────────
const GROUPS = [['makro', 'Makronährstoffe'], ['vitamin', 'Vitamine'], ['mineral', 'Mineralstoffe'], ['fett', 'Fettsäuren'], ['grenze', 'Besser wenig']];
const SLOT_ORDER = [['fruehstueck', 'Frühst.'], ['mittag', 'Mittag'], ['abend', 'Abend'], ['snack', 'Snack']];

export function FoodStats() {
  const [mode, setMode] = useState(local.get('fstats:mode', 'W'));
  const [offset, setOffset] = useState(0);
  const [metric, setMetric] = useState(local.get('fstats:metric', 'kcal'));
  const [sel, setSel] = useState(null);
  useEffect(() => { local.set('fstats:mode', mode); local.set('fstats:metric', metric); setSel(null); }, [mode, metric, offset]);
  const p = useMemo(() => fairPeriod(mode, offset), [mode, offset]);
  const [d, loading] = useRange('food', p);
  if (!d) return html`<div class="skel" style="height:420px" />`;
  const days = d.days ?? {};
  const meta = d.nutrients.find((n) => n.k === metric) ?? d.nutrients[0];
  const goal = d.targets.goals[metric] ?? {};
  const ref = goal.min ?? goal.max ?? null;
  const v = (day, k = metric) => days[day]?.totals?.[k];
  const logged = (from, to) => Object.keys(days).filter((k) => k >= from && k <= to);
  const avg = (from, to, k = metric) => { const ds = logged(from, to); return ds.length ? ds.reduce((a, day) => a + (v(day, k) ?? 0), 0) / ds.length : null; };
  let items;
  if (mode === 'D') items = SLOT_ORDER.map(([s, l]) => ({ v: days[p.from]?.slots?.[s]?.[metric] ?? null, label: l, key: l }));
  else if (mode === 'Y') items = p.buckets.map((b) => { const ds = b.days.filter((x) => days[x]); return { v: ds.length ? ds.reduce((a, x) => a + (v(x) ?? 0), 0) / ds.length : null, label: b.label, key: b.key }; });
  else items = p.buckets.map((b) => ({ v: v(b.days[0]) ?? null, label: b.label, key: b.days[0] }));
  const now = mode === 'D' ? v(p.from) ?? null : avg(p.from, p.to);
  const before = mode === 'D' ? v(p.prevFrom) ?? null : avg(p.prevFrom, p.prevTo);
  const nLogged = logged(p.from, p.to).length;
  const selItem = sel != null ? items[sel] : null;
  const dec = meta.unit === 'g' && (ref ?? 10) < 10 ? 1 : meta.unit === 'mg' && (ref ?? 10) < 10 ? 1 : meta.unit === 'µg' && (ref ?? 10) < 10 ? 1 : 0;
  const f = (x) => fmtNum(x, dec);
  // Tabelle: Ø je Tag mit Einträgen über den Zeitraum, in % vom Ziel
  const table = d.nutrients.map((n) => {
    const g = d.targets.goals[n.k] ?? {};
    const a = mode === 'D' ? v(p.from, n.k) ?? null : avg(p.from, p.to, n.k);
    const r = g.min ?? g.max ?? null;
    return { ...n, avg: a, ref: r, isMax: g.max != null && g.min == null, pct: a != null && r ? Math.round((a / r) * 100) : null };
  });
  return html`<div class="c-food">
    <div class="card pad16 tinted statsCard">
      <${PeriodNav} mode=${mode} setMode=${setMode} offset=${offset} setOffset=${setOffset} label=${p.label} canBack=${true} />
      <button class="metricPick mt12" onClick=${() => document.getElementById('ntable')?.scrollIntoView({ behavior: 'smooth' })}>${meta.name}<${Icon} n="expand_more" /></button>
      <div class="row mt12" style="align-items:flex-end">
        <div class="grow">
          <div class="eyebrow">${selItem ? fmtKey(selItem.key, mode) : mode === 'D' ? 'An diesem Tag' : 'Ø pro Tag mit Einträgen'}</div>
          <div class="bigNum num">${selItem ? (selItem.v == null ? '–' : f(selItem.v)) : now == null ? '–' : f(now)} <small>${meta.unit}</small></div>
          ${ref && (selItem ? selItem.v : now) != null && html`<div class="small muted">${Math.round(((selItem ? selItem.v : now) / ref) * 100)} % ${goal.max != null && goal.min == null ? 'der Obergrenze' : 'vom Ziel'} (${f(ref)} ${meta.unit})</div>`}
        </div>
        ${loading && html`<${Spinner} />`}
      </div>
      ${!selItem && now != null && html`<div class="mt4"><${Delta} now=${now} before=${before} prevLabel=${p.prevLabel} invert=${goal.max != null && goal.min == null} /></div>`}
      <div class="mt16"><${Bars} items=${items} goal=${mode === 'D' ? null : ref} fmt=${f} unit=${meta.unit} selected=${sel} onSelect=${setSel} /></div>
      ${mode !== 'D' && html`<div class="small muted mt8">${nLogged ? `${nLogged} Tage mit Einträgen` : 'In diesem Zeitraum nichts eingetragen'}</div>`}
    </div>

    <div class="sec" id="ntable"><h2 class="h2">Nährstoffe</h2><span class="small muted">${mode === 'D' ? 'an diesem Tag' : 'Ø pro Tag'}</span></div>
    ${GROUPS.map(([g, gl]) => html`<div class="eyebrow mt16">${gl}</div>
      <div class="card mt8" style="padding:4px 14px">${table.filter((n) => n.group === g).map((n) => html`<button class=${cls('nrow', metric === n.k && 'on')} onClick=${() => { setMetric(n.k); window.scrollTo?.(0, 0); document.querySelector('.statsCard')?.scrollIntoView({ behavior: 'smooth' }); }}>
        <div class="grow"><div class="row" style="justify-content:space-between"><span>${n.name}</span><span class="small num">${n.avg == null ? '–' : fmtNum(n.avg, n.unit === 'kcal' ? 0 : 1)} ${n.unit}${n.pct != null ? html` · <b class=${n.isMax ? (n.pct > 100 ? 'bad' : '') : n.pct < 60 ? 'bad' : n.pct >= 100 ? 'ok' : ''}>${n.pct} %</b>` : ''}</span></div>
          <div class="bar mt4"><i style=${{ width: `${Math.min(100, n.pct ?? 0)}%`, background: n.isMax ? (n.pct > 100 ? 'rgb(var(--bad))' : 'rgba(255,255,255,.35)') : n.pct != null && n.pct < 60 ? 'rgb(var(--bad))' : undefined }} /></div></div>
      </button>`)}</div>`)}
    <p class="tiny faint mt12">Ziele: DGE-Referenzwerte für dein Profil. »Besser wenig«: Obergrenzen.</p>
  </div>`;
}
