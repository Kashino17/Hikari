// Verlauf wie in Health-Apps: Tag / Woche / Monat / Jahr, beliebig weit zurück, Balken antippbar,
// Vergleich mit dem Zeitraum davor. Für Training (Minuten, Einheiten, kcal) und Ernährung (jeder Nährstoff).
import { html, useEffect, useMemo, useState } from '../../vendor/preact-htm.js';
import { api, local } from '../platform.js';
import { fmtNum, parseDay, today as todayStr, nav } from '../app.js';
import { Icon, Seg, cls, Spinner, Ring, Sheet, Top, ScoreBadge, scoreTone } from '../ui.js';

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
function Bars({ items, goal, fmt, unit, height = 170, selected, onSelect, marks }) {
  const vals = items.map((i) => i.v ?? 0);
  const max = Math.max(1, ...vals, goal ?? 0) * 1.08;
  return html`<div class="bars" style=${{ height: `${height}px` }}>
    ${goal != null && goal > 0 && html`<div class="goalLine" style=${{ bottom: `${(goal / max) * (height - 22) + 22}px` }}><span>Ziel ${fmt(goal)} ${unit}</span></div>`}
    ${items.map((it, i) => html`<button class=${cls('bcol', selected === i && 'sel', it.v == null && 'none')} onClick=${() => onSelect(selected === i ? null : i)}>
      <div class="bwrap"><i style=${{ height: `${Math.max(it.v ? 3 : 0, ((it.v ?? 0) / max) * (height - 22))}px`, ...(goal && it.v != null && it.v < goal * 0.6 ? { opacity: 0.55 } : {}) }} /></div>
      <span class="bl">${it.label}</span>
      ${marks && html`<span class=${cls('bmark', marks[i] != null && scoreTone(marks[i]))}>${marks[i] != null ? Math.round(marks[i]) : ''}</span>`}
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

function useRange(kind, p, bump = 0) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  useEffect(() => {
    let live = true;
    setLoading(true);
    api.get(`/fitness/stats/${kind}?from=${p.prevFrom}&to=${p.to}`).then((d) => { if (live) { setData(d); setLoading(false); } }).catch(() => live && setLoading(false));
    return () => { live = false; };
  }, [kind, p.prevFrom, p.to, bump]);
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
// Übersicht auf einen Blick: Diagramm mit Noten je Tag (Monat: Notenkalender), Makros als Ringe,
// Gruppen als Kacheln. Tiefer: Tag → Coach-Fazit / nachträglich analysieren; Gruppe → Nährstoffe;
// Nährstoff → eigene Seite mit Verlauf, Wissen und persönlicher Rechnung.
const GROUPS = [['vitamin', 'Vitamine', 'nutrition'], ['mineral', 'Mineralstoffe', 'diamond'], ['fett', 'Fettsäuren', 'water_drop'], ['grenze', 'Besser wenig', 'do_not_disturb_on']];
const SLOT_ORDER = [['fruehstueck', 'Frühst.'], ['mittag', 'Mittag'], ['abend', 'Abend'], ['snack', 'Snack']];
/** Wo steht ein Wert gegenüber seinem Ziel? good | fair | low (bei Obergrenzen: good | low). */
const nutriTone = (pct, isMax) => (pct == null ? '' : isMax ? (pct <= 100 ? 'good' : 'low') : pct >= 100 ? 'good' : pct >= 60 ? 'fair' : 'low');
// Zuletzt gewählter Zeitraum — die Nährstoff-Seite zeigt denselben.
let lastPeriod = { mode: 'W', offset: 0 };

export function FoodStats() {
  const [mode, setMode] = useState(local.get('fstats:mode', 'W'));
  const [offset, setOffset] = useState(0);
  const [metric, setMetric] = useState(local.get('fstats:metric', 'kcal'));
  const [sel, setSel] = useState(null);
  const [daySheet, setDaySheet] = useState(null);
  const [group, setGroup] = useState(null);
  const [bump, setBump] = useState(0);
  useEffect(() => { local.set('fstats:mode', mode); local.set('fstats:metric', metric); setSel(null); lastPeriod = { mode, offset }; }, [mode, metric, offset]);
  const p = useMemo(() => fairPeriod(mode, offset), [mode, offset]);
  const [d, loading] = useRange('food', p, bump);
  if (!d) return html`<div class="skel" style="height:420px" />`;
  const days = d.days ?? {};
  const scores = d.scores ?? {};
  const meta = d.nutrients.find((n) => n.k === metric) ?? d.nutrients[0];
  const goal = d.targets.goals[metric] ?? {};
  const ref = goal.min ?? goal.max ?? null;
  const v = (day, k = metric) => days[day]?.totals?.[k];
  const logged = (from, to) => Object.keys(days).filter((k) => k >= from && k <= to);
  const avg = (from, to, k = metric) => { const ds = logged(from, to); return ds.length ? ds.reduce((a, day) => a + (v(day, k) ?? 0), 0) / ds.length : null; };
  const avgScore = (ds) => { const s = ds.map((x) => scores[x]?.score).filter((x) => x != null); return s.length ? s.reduce((a, b) => a + b, 0) / s.length : null; };
  let items;
  let marks = null;
  if (mode === 'D') items = SLOT_ORDER.map(([s, l]) => ({ v: days[p.from]?.slots?.[s]?.[metric] ?? null, label: l, key: l }));
  else if (mode === 'Y') { items = p.buckets.map((b) => { const ds = b.days.filter((x) => days[x]); return { v: ds.length ? ds.reduce((a, x) => a + (v(x) ?? 0), 0) / ds.length : null, label: b.label, key: b.key }; }); marks = p.buckets.map((b) => avgScore(b.days)); }
  else { items = p.buckets.map((b) => ({ v: v(b.days[0]) ?? null, label: b.label, key: b.days[0] })); if (mode === 'W') marks = p.buckets.map((b) => scores[b.days[0]]?.score ?? null); }
  const now = mode === 'D' ? v(p.from) ?? null : avg(p.from, p.to);
  const before = mode === 'D' ? v(p.prevFrom) ?? null : avg(p.prevFrom, p.prevTo);
  const nLogged = logged(p.from, p.to).length;
  const selItem = sel != null ? items[sel] : null;
  const dec = (meta.unit === 'g' || meta.unit === 'mg' || meta.unit === 'µg') && (ref ?? 10) < 10 ? 1 : 0;
  const f = (x) => fmtNum(x, dec);
  // Ø je Tag mit Einträgen über den Zeitraum, in % vom Ziel
  const table = d.nutrients.map((n) => {
    const g = d.targets.goals[n.k] ?? {};
    const a = mode === 'D' ? v(p.from, n.k) ?? null : avg(p.from, p.to, n.k);
    const r = g.min ?? g.max ?? null;
    const isMax = g.max != null && g.min == null;
    const pc = a != null && r ? Math.round((a / r) * 100) : null;
    return { ...n, avg: a, ref: r, isMax, pct: pc, tone: nutriTone(pc, isMax) };
  });
  const pickDay = (i) => { setSel(i); const k = items[i]?.key; if (mode === 'W' && /^\d{4}-/.test(k ?? '') && (days[k] || scores[k])) setDaySheet(k); };
  const periodScore = mode === 'D' ? scores[p.from]?.score ?? null : avgScore(logged(p.from, p.to));
  const openNutrient = (k) => nav(`ernaehrung/naehrstoff/${k}`);
  return html`<div class="c-food fstats">
    <div class="card pad16 statsCard">
      <${PeriodNav} mode=${mode} setMode=${setMode} offset=${offset} setOffset=${setOffset} label=${p.label} canBack=${true} />
      <div class="row mt12" style="justify-content:space-between;align-items:center">
        <button class="metricPick" onClick=${() => setGroup('alle')}>${meta.name}<${Icon} n="expand_more" /></button>
        ${periodScore != null && html`<button class="scoreLine" onClick=${() => mode === 'D' && setDaySheet(p.from)}><span class="small muted">${mode === 'D' ? 'Coach' : 'Ø Coach'}</span><${ScoreBadge} score=${Math.round(periodScore * 10) / 10} sm /></button>`}
      </div>
      <div class="row mt12" style="align-items:flex-end">
        <div class="grow">
          <div class="eyebrow">${selItem ? fmtKey(selItem.key, mode) : mode === 'D' ? 'An diesem Tag' : 'Ø pro Tag mit Einträgen'}</div>
          <div class="bigNum num">${selItem ? (selItem.v == null ? '–' : f(selItem.v)) : now == null ? '–' : f(now)} <small>${meta.unit}</small></div>
          ${ref && (selItem ? selItem.v : now) != null && html`<div class="small muted">${Math.round(((selItem ? selItem.v : now) / ref) * 100)} % ${goal.max != null && goal.min == null ? 'der Obergrenze' : 'vom Ziel'} (${f(ref)} ${meta.unit})</div>`}
        </div>
        ${loading && html`<${Spinner} />`}
      </div>
      ${!selItem && now != null && html`<div class="mt4"><${Delta} now=${now} before=${before} prevLabel=${p.prevLabel} invert=${goal.max != null && goal.min == null} /></div>`}
      <div class="mt16"><${Bars} items=${items} goal=${mode === 'D' ? null : ref} fmt=${f} unit=${meta.unit} selected=${sel} onSelect=${(i) => (i == null ? setSel(null) : pickDay(i))} marks=${marks} /></div>
      ${mode === 'W' && html`<div class="tiny muted mt8">Tag antippen: Coach-Bewertung lesen oder nachträglich analysieren</div>`}
      ${mode === 'D' && (days[p.from] || scores[p.from]) && html`<button class="btn ghost sm mt12" onClick=${() => setDaySheet(p.from)}><${Icon} n="psychology" />Coach zu diesem Tag</button>`}
      ${mode !== 'D' && mode !== 'W' && html`<div class="small muted mt8">${nLogged ? `${nLogged} Tage mit Einträgen` : 'In diesem Zeitraum nichts eingetragen'}</div>`}
    </div>

    ${mode === 'M' && html`<${ScoreCalendar} p=${p} days=${days} scores=${scores} onPick=${setDaySheet} />`}

    <div class="sec"><h2 class="h2">Makros</h2><span class="small muted">${mode === 'D' ? 'an diesem Tag' : 'Ø pro Tag'}</span></div>
    <div class="macroGrid">${table.filter((n) => n.group === 'makro').map((n) => html`<button class=${cls('macroTile', n.tone)} onClick=${() => openNutrient(n.k)}>
      <${Ring} size=${50} stroke=${5} value=${Math.min(n.pct ?? 0, 100)} max=${100} color=${`var(--tone-${n.tone || 'none'})`}><b class="num">${n.pct ?? '–'}${n.pct != null ? html`<small>%</small>` : ''}</b><//>
      <span class="mtName">${n.name}</span><span class="mtVal num">${n.avg == null ? '–' : fmtNum(n.avg, 0)} ${n.unit}</span></button>`)}</div>

    <div class="sec"><h2 class="h2">Mikronährstoffe</h2></div>
    <div class="groupGrid">${GROUPS.map(([g, gl, ic]) => {
      const list = table.filter((n) => n.group === g);
      const ok = list.filter((n) => n.tone === 'good').length;
      const hasData = list.some((n) => n.pct != null);
      return html`<button class="groupTile" onClick=${() => (list.length === 1 ? openNutrient(list[0].k) : setGroup(g))}>
        <div class="row" style="justify-content:space-between"><${Icon} n=${ic} /><span class="gtCount num">${hasData ? html`${ok}<small>/${list.length}</small>` : '–'}</span></div>
        <b class="gtName">${gl}</b>
        <span class="gtSub">${!hasData ? 'Keine Einträge' : g === 'grenze' ? (ok === list.length ? 'Alles im Rahmen' : `${list.length - ok} drüber`) : ok === list.length ? 'Alles im Ziel' : `${list.length - ok} unter Ziel`}</span>
        <div class="gtDots">${list.map((n) => html`<i class=${n.tone} />`)}</div>
      </button>`;
    })}</div>
    <p class="tiny faint mt12">Ziele: DGE-Referenzwerte für dein Profil. Antippen für Details, Wirkung und deine Quellen.</p>

    ${group && html`<${Sheet} title=${group === 'alle' ? 'Was zeigt das Diagramm?' : GROUPS.find((x) => x[0] === group)?.[1]} onClose=${() => setGroup(null)}>
      <div class="pad c-food">${(group === 'alle' ? table : table.filter((n) => n.group === group)).map((n) => html`<div class=${cls('nrow2', n.tone)}>
        <button class="nr-main" onClick=${() => { setGroup(null); openNutrient(n.k); }}>
          <div class="row" style="justify-content:space-between"><span>${n.name}</span><span class="small num">${n.avg == null ? '–' : fmtNum(n.avg, n.unit === 'kcal' ? 0 : 1)} ${n.unit}${n.pct != null ? html` · <b>${n.pct} %</b>` : ''}</span></div>
          <div class="bar mt4"><i style=${{ width: `${Math.min(100, n.pct ?? 0)}%` }} /></div>
        </button>
        ${group === 'alle' && html`<button class=${cls('ib sm', metric === n.k && 'on')} onClick=${() => { setMetric(n.k); setGroup(null); }} aria-label="Im Diagramm zeigen"><${Icon} n="bar_chart" /></button>`}
        ${group !== 'alle' && html`<${Icon} n="chevron_right" />`}
      </div>`)}</div><//>`}

    ${daySheet && html`<${DayCoachSheet} day=${daySheet} totals=${days[daySheet]?.totals} meals=${days[daySheet]?.meals} targets=${d.targets} score=${scores[daySheet]} onClose=${() => setDaySheet(null)} onAnalyzed=${() => setBump((x) => x + 1)} />`}
  </div>`;
}

/** Monat: Kalender, jeder Tag in der Farbe seiner Coach-Note; Tage mit Einträgen ohne Note umrandet. */
function ScoreCalendar({ p, days, scores, onPick }) {
  const first = parseDay(p.from);
  const lead = (first.getDay() + 6) % 7;
  const all = p.buckets.map((b) => b.days[0]);
  const t = todayStr();
  return html`<div class="card pad16 mt12 scoreCal">
    <div class="row" style="justify-content:space-between"><b>Bewertungen</b><span class="tiny muted">Tag antippen</span></div>
    <div class="calHead mt12">${WD.map((w) => html`<span>${w}</span>`)}</div>
    <div class="calGrid">${Array.from({ length: lead }, () => html`<span />`)}${all.map((day) => {
      const s = scores[day]?.score;
      const has = !!days[day];
      return html`<button class=${cls('calDay', s != null && scoreTone(s), s == null && has && 'logged', day > t && 'future')} disabled=${!has && s == null} onClick=${() => onPick(day)}>
        <span class="cd">${parseDay(day).getDate()}</span>${s != null && html`<span class="cs num">${s}</span>`}</button>`;
    })}</div>
    <div class="calLegend mt12"><span><i class="sc-low" />1–3</span><span><i class="sc-mid" />4–5</span><span><i class="sc-fair" />6–7</span><span><i class="sc-good" />8–10</span><span><i class="logged" />ohne Bewertung</span></div>
  </div>`;
}

/** Ein Tag: Note und Fazit des Coachs — oder nachträglich analysieren lassen. */
function DayCoachSheet({ day, totals, meals, targets, score, onClose, onAnalyzed }) {
  const [r, setR] = useState(null);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState(null);
  const label = fmtKey(day, 'W');
  // Gespeicherte Bewertung nur lesen — neu analysiert wird ausschließlich über den Knopf.
  useEffect(() => { if (score) api.get(`/fitness/food/report?day=${day}&cached=1`).then((x) => setR(x.report)).catch(() => {}); }, [day]);
  const analyze = () => {
    setBusy(true); setErr(null);
    api.get(`/fitness/food/report?day=${day}`).then((x) => { setBusy(false); if (x.report) { setR(x.report); onAnalyzed(); } else setErr('An diesem Tag ist nichts eingetragen.'); })
      .catch((e) => { setBusy(false); setErr(e.status === 503 ? 'Kein Gemini-Schlüssel auf dem Server.' : 'Der Coach ist gerade nicht erreichbar.'); });
  };
  const k = totals?.kcal;
  const pr = totals?.protein;
  const rep = r ?? (score ? { score: score.score, headline: score.headline } : null);
  return html`<${Sheet} title=${label} onClose=${onClose}><div class="pad c-food dayCoach">
    <div class="dcStats">
      <div><span class="num">${k != null ? fmtNum(k, 0) : '–'}</span><small>kcal · ${targets?.kcal ? Math.round(((k ?? 0) / targets.kcal) * 100) : '–'} %</small></div>
      <div><span class="num">${pr != null ? fmtNum(pr, 0) : '–'}</span><small>g Eiweiß</small></div>
      <div><span class="num">${meals ?? 0}</span><small>${meals === 1 ? 'Mahlzeit' : 'Mahlzeiten'}</small></div>
    </div>
    ${rep ? html`<div class="dcReport mt16">
        <div class="row" style="align-items:flex-start;gap:14px"><${ScoreBadge} score=${rep.score} /><b class="grow" style="font-size:17px;line-height:1.35">${rep.headline}</b></div>
        ${r?.good?.length > 0 && html`<div class="dcList good mt12"><span>Läuft gut</span>${r.good.slice(0, 2).map((t) => html`<p>${t}</p>`)}</div>`}
        ${r?.bad?.length > 0 && html`<div class="dcList bad mt8"><span>Nicht so gut</span>${r.bad.slice(0, 2).map((t) => html`<p>${t}</p>`)}</div>`}
        <button class="btn mt16" onClick=${() => { onClose(); nav(`ernaehrung/coach/${day}`); }}><${Icon} n="article" />Ganze Analyse lesen</button>
      </div>`
      : html`<div class="dcEmpty mt16">
        <${Icon} n="psychology" />
        <b>Für diesen Tag gibt es noch keine Bewertung</b>
        <span class="small muted">Der Coach liest dein Tagebuch von ${label} und bewertet den Tag — mit Note, Lücken und Tipps.</span>
        ${err && html`<span class="small" style="color:#EE7B6F">${err}</span>`}
        <button class="btn mt12" disabled=${busy || !totals} onClick=${analyze}>${busy ? html`<${Spinner} /> Coach liest …` : html`<${Icon} n="auto_awesome" />Jetzt analysieren`}</button>
      </div>`}
  </div><//>`;
}

/** Ein Nährstoff im Detail: Verlauf, Wissen, persönliche Rechnung, deine Quellen. */
export function NutrientDetail({ k }) {
  const p = useMemo(() => fairPeriod(lastPeriod.mode === 'D' ? 'W' : lastPeriod.mode, lastPeriod.offset), [k]);
  const [d] = useRange('food', p);
  const [x, setX] = useState(null);
  const [sel, setSel] = useState(null);
  useEffect(() => { api.get(`/fitness/food/nutrient/${k}?from=${p.from}&to=${p.to}`).then(setX).catch(() => setX({ error: true })); }, [k, p.from, p.to]);
  const meta = d?.nutrients.find((n) => n.k === k);
  const name = x?.name ?? meta?.name ?? 'Nährstoff';
  if (!x || !d) return html`<div class="screen c-food"><${Top} title=${name} /><div class="pad"><div class="skel" style="height:420px" /></div></div>`;
  const days = d.days ?? {};
  const unit = x.unit;
  const ref = x.target;
  const ds = Object.keys(days).filter((dd) => dd >= p.from && dd <= p.to);
  const avg = ds.length ? ds.reduce((a, dd) => a + (days[dd].totals?.[k] ?? 0), 0) / ds.length : null;
  const pc = avg != null && ref ? Math.round((avg / ref) * 100) : null;
  const tone = nutriTone(pc, x.isMax);
  const dec = (unit !== 'kcal' && (ref ?? 10) < 10) ? 1 : 0;
  let items;
  if (p.mode === 'Y') items = p.buckets.map((b) => { const bd = b.days.filter((z) => days[z]); return { v: bd.length ? bd.reduce((a, z) => a + (days[z].totals?.[k] ?? 0), 0) / bd.length : null, label: b.label, key: b.key }; });
  else items = p.buckets.map((b) => ({ v: days[b.days[0]]?.totals?.[k] ?? null, label: b.label, key: b.days[0] }));
  const selItem = sel != null ? items[sel] : null;
  const i = x.info;
  const verdict = pc == null ? 'Noch keine Einträge in diesem Zeitraum'
    : x.isMax ? (pc <= 100 ? 'Im Rahmen — gut so' : 'Über der Obergrenze')
    : pc >= 100 ? 'Ziel erreicht' : pc >= 60 ? 'Fast da' : 'Deutlich unter dem Ziel';
  return html`<div class="screen c-food nutriPage">
    <${Top} title=${name} sub=${i?.kurz ?? ''} />
    <div class="scroll"><div class="pad">
      <div class=${cls('card pad16 npHero', tone)}>
        <div class="row" style="gap:16px;align-items:center">
          <${Ring} size=${96} stroke=${9} value=${Math.min(pc ?? 0, 100)} max=${100} color=${`var(--tone-${tone || 'none'})`}><b class="num" style="font-size:24px">${pc ?? '–'}${pc != null ? html`<small style="font-size:13px">%</small>` : ''}</b><//>
          <div class="grow">
            <div class="eyebrow">${selItem ? fmtKey(selItem.key, p.mode) : `Ø ${p.label}`}</div>
            <div class="bigNum num" style="font-size:30px">${selItem ? (selItem.v == null ? '–' : fmtNum(selItem.v, dec)) : avg == null ? '–' : fmtNum(avg, dec)} <small>${unit}</small></div>
            <div class="small muted">${x.isMax ? 'Obergrenze' : 'Dein Ziel'} ${ref != null ? `${fmtNum(ref, dec)} ${unit}` : '–'} · <b class=${cls('npVerdict', tone)}>${verdict}</b></div>
          </div>
        </div>
        <div class="mt16"><${Bars} items=${items} goal=${ref} fmt=${(v) => fmtNum(v, dec)} unit=${unit} height=${130} selected=${sel} onSelect=${setSel} /></div>
      </div>

      ${x.rechnung && html`<div class="npCalc mt12"><span class="eyebrow">Warum genau deine Menge</span><p class="npFormula">${x.rechnung}</p>${i?.menge && html`<p class="small muted mt8">${i.menge}</p>`}</div>`}

      ${i && html`
        <div class="sec"><h2 class="h2">Wofür dein Körper das braucht</h2></div>
        <div class="npFor">${i.wofuer.map((t) => html`<div><${Icon} n="check_circle" /><span>${t}</span></div>`)}</div>
        <div class="card pad16 mt12"><span class="eyebrow">Warum es für dich zählt</span><p class="mt4" style="line-height:1.55">${i.warum}</p></div>`}

      ${x.top?.length > 0 && html`<div class="sec"><h2 class="h2">Deine besten Quellen</h2><span class="small muted">${p.label}</span></div>
        <div class="npTop">${x.top.map((t) => html`<div><span class="grow">${t.name}</span><span class="small num muted">${fmtNum(t.value, dec || 1)} ${unit}</span><span class="npShare num">${t.share} %</span></div>`)}</div>`}

      ${i && html`
        <div class="sec"><h2 class="h2">Steckt viel drin in</h2></div>
        <div class="chips wrap">${i.quellen.map((q) => html`<span class="chip">${q}</span>`)}</div>
        <div class="npTwo mt16">
          <div class="npSide low"><span class="eyebrow">Zu wenig</span><p>${i.zuwenig}</p></div>
          ${i.zuviel && html`<div class="npSide high"><span class="eyebrow">Zu viel</span><p>${i.zuviel}</p></div>`}
        </div>
        ${i.tipp && html`<div class="npTip mt12"><${Icon} n="lightbulb" /><p>${i.tipp}</p></div>`}`}
      <p class="tiny faint mt16">Richtwerte: DGE, EFSA, WHO. Ersetzt keine ärztliche Beratung.</p>
      <div class="end" />
    </div></div>
  </div>`;
}
