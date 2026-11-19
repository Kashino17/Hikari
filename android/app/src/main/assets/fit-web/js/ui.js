// Bausteine der Oberfläche.
import { html, useEffect, useRef, useState } from '../vendor/preact-htm.js';
import { mediaBlob } from './platform.js';
import { back } from './app.js';

export const cls = (...a) => a.filter(Boolean).join(' ');
export const Icon = ({ n, fill, style, cls: c }) => html`<span class=${cls('ms', fill && 'fill', c)} style=${style} aria-hidden="true">${n}</span>`;

/**
 * Übungsvideo in Schleife. Lädt erst, wenn es sichtbar wird (spart Speicher in langen Listen),
 * und zeigt bis dahin das Standbild.
 */
export function Loop({ media, cls: c, badge, still = false, eager = false, credit = false }) {
  const ref = useRef(null);
  const [visible, setVisible] = useState(eager);
  const [src, setSrc] = useState(null);
  useEffect(() => {
    if (eager || !ref.current) return undefined;
    const io = new IntersectionObserver(([e]) => setVisible(e.isIntersecting), { rootMargin: '120px' });
    io.observe(ref.current);
    return () => io.disconnect();
  }, [eager]);
  useEffect(() => {
    let live = true;
    if (visible && !still && media?.video) mediaBlob(media.video).then((u) => { if (live) setSrc(u); });
    return () => { live = false; };
  }, [visible, still, media?.video]);
  const poster = media?.image ? `/api${media.image}` : null;
  return html`<div class=${cls('vid', c)} ref=${ref}>
    ${src && visible && !still
      ? html`<video src=${src} autoplay loop muted playsinline poster=${poster} />`
      : poster ? html`<img src=${poster} alt="" loading="lazy" />` : html`<${Icon} n="fitness_center" cls="ph" />`}
    ${badge && html`<span class="badge">${badge}</span>`}
    ${credit && media?.credit && html`<span class="credit">${media.credit}</span>`}
  </div>`;
}

export function Ring({ size = 120, stroke = 10, value = 0, max = 1, color = 'rgb(var(--c))', track = 'rgba(255,255,255,.08)', children }) {
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const p = Math.max(0, Math.min(1, max ? value / max : 0));
  return html`<div class="ring" style=${{ width: `${size}px`, height: `${size}px` }}>
    <svg width=${size} height=${size}>
      <circle cx=${size / 2} cy=${size / 2} r=${r} fill="none" stroke=${track} stroke-width=${stroke} />
      <circle cx=${size / 2} cy=${size / 2} r=${r} fill="none" stroke=${color} stroke-width=${stroke} stroke-linecap="round"
        stroke-dasharray=${c} stroke-dashoffset=${c * (1 - p)} style="transition:stroke-dashoffset .6s cubic-bezier(.2,.8,.2,1)" />
    </svg>
    <div class="in">${children}</div>
  </div>`;
}

export const Bar = ({ value, max, thick, color }) => html`<div class=${cls('bar', thick && 'thick')}><i style=${{ width: `${Math.max(0, Math.min(100, max ? (value / max) * 100 : 0))}%`, ...(color ? { background: color } : {}) }} /></div>`;

export function Top({ title, sub, onBack, actions, big }) {
  return html`<header class=${cls('top', big && 'big', onBack !== false && !big && 'hasback')}>
    ${!big && onBack !== false && html`<button class="ib" onClick=${onBack ?? (() => back())} aria-label="Zurück"><${Icon} n="arrow_back" /></button>`}
    <div class="tt"><h1>${title}</h1>${sub && html`<div class="t2">${sub}</div>`}</div>
    ${actions}
  </header>`;
}

export function Sheet({ title, onClose, children, actions }) {
  useEffect(() => {
    const prev = window.__fitSheetClose;
    window.__fitSheetClose = onClose;
    return () => { window.__fitSheetClose = prev; };
  }, [onClose]);
  return html`<div class="veil" onClick=${onClose} /><div class="sheet" role="dialog">
    <div class="grab" />
    ${title && html`<div class="sh"><h2>${title}</h2>${actions}<button class="ib sm" onClick=${onClose} aria-label="Schließen"><${Icon} n="close" /></button></div>`}
    <div class="sb">${children}</div>
  </div>`;
}

export const Seg = ({ items, value, onChange }) => html`<div class="seg">${items.map(([k, label, icon]) => html`<button class=${cls(value === k && 'on')} onClick=${() => onChange(k)}>${icon && html`<${Icon} n=${icon} />`}${label}</button>`)}</div>`;

export function Stepper({ value, onChange, step = 1, min = 0, max = 9999, fmt = (v) => v }) {
  return html`<div class="stepper">
    <button onClick=${() => onChange(Math.max(min, Math.round((value - step) * 100) / 100))} aria-label="weniger"><${Icon} n="remove" /></button>
    <span class="v">${fmt(value)}</span>
    <button onClick=${() => onChange(Math.min(max, Math.round((value + step) * 100) / 100))} aria-label="mehr"><${Icon} n="add" /></button>
  </div>`;
}

export const Empty = ({ icon = 'info', title, text, children }) => html`<div class="empty"><${Icon} n=${icon} /><b>${title}</b>${text && html`<div>${text}</div>`}${children}</div>`;
export const Spinner = () => html`<div class="spinner" />`;

/** Einfache Liniengrafik (Gewicht, Kalorien). */
export function Spark({ points, height = 150, color = 'rgb(var(--c))', goal }) {
  if (!points?.length) return null;
  const w = 340;
  const vals = points.map((p) => p.v).concat(goal != null ? [goal] : []);
  const lo = Math.min(...vals);
  const hi = Math.max(...vals);
  const span = hi - lo || 1;
  const x = (i) => (points.length === 1 ? w / 2 : (i / (points.length - 1)) * (w - 16) + 8);
  const y = (v) => height - 22 - ((v - lo) / span) * (height - 44);
  const d = points.map((p, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(p.v).toFixed(1)}`).join(' ');
  return html`<svg class="chart" viewBox="0 0 ${w} ${height}" preserveAspectRatio="none">
    ${goal != null && html`<line x1="0" x2=${w} y1=${y(goal)} y2=${y(goal)} stroke="rgba(255,255,255,.25)" stroke-dasharray="4 4" />`}
    <path d=${`${d} L${x(points.length - 1)},${height} L${x(0)},${height} Z`} fill=${color} opacity=".12" />
    <path d=${d} fill="none" stroke=${color} stroke-width="2.5" stroke-linejoin="round" stroke-linecap="round" />
    ${points.map((p, i) => html`<circle cx=${x(i)} cy=${y(p.v)} r=${i === points.length - 1 ? 4.5 : 2.5} fill=${color} />`)}
  </svg>`;
}

/** Säulen (z. B. Trainingsminuten je Woche). */
export function Columns({ items, height = 120, color = 'rgb(var(--c))' }) {
  const max = Math.max(1, ...items.map((i) => i.v));
  return html`<div style=${{ display: 'flex', alignItems: 'flex-end', gap: '6px', height: `${height}px` }}>
    ${items.map((i) => html`<div style=${{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '6px', height: '100%', justifyContent: 'flex-end' }}>
      <div style=${{ width: '100%', maxWidth: '26px', height: `${Math.max(3, (i.v / max) * (height - 22))}px`, borderRadius: '6px', background: i.v ? color : 'rgba(255,255,255,.08)', opacity: i.dim ? 0.45 : 1 }} />
      <span class="tiny faint">${i.l}</span>
    </div>`)}
  </div>`;
}
