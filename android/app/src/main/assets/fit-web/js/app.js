// Routen, Datenhaltung, Toast — klein und ohne Abhängigkeiten außer Preact.
import { useEffect, useState, useRef } from '../vendor/preact-htm.js';
import { api, local } from './platform.js';

// ── Routen (#/heute, #/einheit/<id>, …) ──
/** Zwei Bereiche aus einer App: Fitness (Training, Skills, Ernährung) und Achtsamkeit (eigener Bereich im Profil). */
export const AREA = new URLSearchParams(location.search).get('area') === 'mind' ? 'mind' : 'fit';
if (typeof document !== 'undefined') document.documentElement.classList.add(`area-${AREA}`);
export const TABS = AREA === 'mind' ? ['heute', 'yoga', 'meditation', 'wissen'] : ['heute', 'training', 'skills', 'ernaehrung'];
export const route = () => decodeURIComponent(location.hash.replace(/^#\/?/, '')) || 'heute';
let depth = 0;
export function nav(r, { replace = false } = {}) {
  const url = `#/${r}`;
  if (replace) { history.replaceState(null, '', url); window.dispatchEvent(new HashChangeEvent('hashchange')); return; }
  depth++;
  location.hash = `/${r}`;
}
export function back(fallback = 'heute') {
  if (depth > 0) { depth--; history.back(); } else nav(fallback, { replace: true });
}
export function useRoute() {
  const [r, set] = useState(route());
  useEffect(() => { const f = () => set(route()); window.addEventListener('hashchange', f); return () => window.removeEventListener('hashchange', f); }, []);
  return r;
}

// ── Daten: einfacher Zwischenspeicher mit Abonnenten ──
const cache = new Map();
const subs = new Map();
const inflight = new Map();
function emit(key) { for (const fn of subs.get(key) ?? []) fn(cache.get(key)); }
export function setData(key, value) { cache.set(key, value); emit(key); }
export function getData(key) { return cache.get(key); }
export async function load(key, path = key, { force = false } = {}) {
  if (!force && inflight.has(key)) return inflight.get(key);
  const p = api.get(path).then((v) => { setData(key, v); local.set(`c:${key}`, v); return v; }).finally(() => inflight.delete(key));
  inflight.set(key, p);
  return p;
}
/** Daten mit Abo: liefert zuerst den letzten Stand (auch offline), lädt im Hintergrund neu. */
export function useData(key, path = key, deps = []) {
  const [v, set] = useState(() => cache.get(key) ?? local.get(`c:${key}`, null));
  const [err, setErr] = useState(null);
  useEffect(() => {
    if (!key) return undefined;
    const fn = (x) => set(x);
    if (!subs.has(key)) subs.set(key, new Set());
    subs.get(key).add(fn);
    if (cache.has(key)) set(cache.get(key));
    else { const l = local.get(`c:${key}`, null); if (l) set(l); }
    load(key, path).then(() => setErr(null)).catch((e) => setErr(e));
    return () => subs.get(key).delete(fn);
  }, [key, ...deps]);
  return [v, err, () => load(key, path, { force: true })];
}
export const refresh = (key, path = key) => load(key, path, { force: true }).catch(() => null);

// ── Toast ──
let toastFn = null;
export function toast(text) { toastFn?.({ text, at: Date.now() }); }
export function useToast() {
  const [t, set] = useState(null);
  const timer = useRef(null);
  useEffect(() => { toastFn = (x) => { set(x); clearTimeout(timer.current); timer.current = setTimeout(() => set(null), 3200); }; return () => { toastFn = null; }; }, []);
  return t;
}

// ── Gemeinsame Kataloge ──
export function useCatalog() { return useData('/fitness/catalog')[0]; }
export function exById(catalog, id) { return catalog?.exercises?.find((e) => e.id === id) ?? null; }

// ── Formate ──
const WD = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'];
const MONTHS = ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'];
export const parseDay = (d) => { const [y, m, dd] = d.split('-').map(Number); return new Date(y, m - 1, dd, 12); };
export const wdShort = (d) => WD[(parseDay(d).getDay() + 6) % 7];
export function longDate(d) {
  const x = parseDay(d);
  const names = ['Sonntag', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag'];
  return `${names[x.getDay()]}, ${x.getDate()}. ${MONTHS[x.getMonth()]}`;
}
export function relDay(d, today) {
  if (d === today) return 'Heute';
  const diff = Math.round((parseDay(today) - parseDay(d)) / 86400000);
  if (diff === 1) return 'Gestern';
  if (diff === -1) return 'Morgen';
  const x = parseDay(d);
  return `${wdShort(d)}, ${x.getDate()}. ${MONTHS[x.getMonth()].slice(0, 3)}.`;
}
export const fmtNum = (n, d = 0) => (n == null || Number.isNaN(n) ? '–' : Number(n).toLocaleString('de-DE', { maximumFractionDigits: d, minimumFractionDigits: 0 }));
export const mmss = (s) => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, '0')}`;
export const amountText = (it) => (it.unit === 'sec' ? `${it.amount} s` : `${it.amount}×`) + (it.perSide ? ' je Seite' : '');
export const today = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; };
export const img = (u) => (u ? `/api/fitness/img?u=${encodeURIComponent(u)}` : null);
/** Rezeptbild in passender Größe: TheMealDB über /small|/medium, lecker.de über den Breiten-Parameter. */
export function recipeImg(u, size = 'medium') {
  if (!u) return null;
  if (u.includes('themealdb.com')) return img(size === 'full' ? u : `${u}/${size}`);
  if (u.includes('lecker.de')) return img(u.replace(/w=\d+/, `w=${size === 'small' ? 240 : size === 'medium' ? 640 : 1200}`));
  return img(u);
}
export const GOALS = { abnehmen: 'Abnehmen', muskeln: 'Muskeln aufbauen', zunehmen: 'Zunehmen', halten: 'Gewicht halten', fitter: 'Fitter werden' };
export const INTENSITY = { leicht: 'Leicht', mittel: 'Mittel', hart: 'Hart' };
export const SLOT_NAMES = { fruehstueck: 'Frühstück', mittag: 'Mittagessen', abend: 'Abendessen', snack: 'Snack' };
export const SLOT_ICONS = { fruehstueck: 'egg_alt', mittag: 'lunch_dining', abend: 'dinner_dining', snack: 'nutrition' };
export function slotNow() { const h = new Date().getHours(); return h < 11 ? 'fruehstueck' : h < 15 ? 'mittag' : h < 17 ? 'snack' : 'abend'; }
