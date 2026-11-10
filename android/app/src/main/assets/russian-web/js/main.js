// Start, Rahmen und Routen.
import { html, render, useEffect, useState } from '../vendor/preact-htm.js';
import { storage, isNative } from './platform.js';
import { loadData, progress, useRoute, useToast, useProgress, nav, back, route, TABS, P } from './app.js';
import { Icon, cls } from './ui.js';
import { Home } from './screens/home.js';
import { Courses, Plan } from './screens/courses.js';
import { Practice } from './screens/practice.js';
import { DevTools, demoProgress } from './screens/me.js';
import { Stats } from './screens/stats.js';
import { ROUTES } from './routes.js';
import { startTimeTracking } from './timetrack.js';

const TAB_META = {
  heute: ['Heute', 'today', Home],
  kurse: ['Kurse', 'school', Courses],
  ueben: ['Üben', 'exercise', Practice],
  satzbuch: ['Satzbuch', 'menu_book', null],
  ich: ['Fortschritt', 'insights', Stats],
};

function TabBar({ active }) {
  const p = useProgress();
  const due = P.dueIds(p.cards, progress.today()).length;
  return html`<nav class="tabbar">${TABS.map((t) => html`<button class=${cls(active === t && 'on')} onClick=${() => nav(t, { replace: true })}>
    <span class="pill"><${Icon} n=${TAB_META[t][1]} /></span>${TAB_META[t][0]}
    ${t === 'ueben' && due > 0 && html`<span class="dot">${due > 99 ? '99+' : due}</span>`}
  </button>`)}</nav>`;
}

function Screen({ r }) {
  if (TABS.includes(r)) {
    const C = TAB_META[r][2] ?? ROUTES.tab[r];
    return html`<${C} key=${r} />`;
  }
  if (r === 'plan') return html`<${Plan} />`;
  for (const [re, make] of ROUTES.list) {
    const m = re.exec(r);
    if (m) return make(m);
  }
  return html`<div class="screen"><div class="empty" style="margin-top:120px"><${Icon} n="explore_off" /><b>Diese Seite gibt es nicht.</b>
    <div class="mt20"><button class="btn" onClick=${() => nav('heute', { replace: true })}>Zur Übersicht</button></div></div></div>`;
}

function App() {
  const r = useRoute();
  const t = useToast();
  const tab = TABS.includes(r);
  return html`<div class="desk">
    <div class="phone"><div class="app">
      <div style="position:absolute;inset:0;display:flex;flex-direction:column">
        <div style="position:relative;flex:1;min-height:0"><${Screen} r=${r} key=${tab ? 'tabs' : r} /></div>
        ${tab && html`<${TabBar} active=${r} />`}
      </div>
      ${t && html`<div class="toast" key=${t.at}>${t.text}</div>`}
    </div></div>
    <aside class="side">
      <h1>Hikari · Russisch</h1>
      <p>Die neue Oberfläche — mit echten Kursinhalten, echtem Audio und deinem Lernstand. Was du hier lernst, zählt.</p>
      <h2>Am Rechner</h2>
      <p><span class="kbd">1</span>–<span class="kbd">4</span> Antwort wählen · <span class="kbd">Enter</span> weiter · <span class="kbd">Leertaste</span> anhören</p>
      <h2>Zum Testen</h2>
      <${DevTools} />
    </aside>
  </div>`;
}

// Android-Zurück: innerhalb der Oberfläche eine Ebene zurück; auf »Heute« schließt die App den Bereich.
window.__hkBack = () => {
  const r = route();
  if (r === 'heute') return false;
  if (TABS.includes(r)) nav('heute', { replace: true }); else back('heute');
  return true;
};

// Im Browser (Vorschau) Roboto nachladen — in der App ist es die Systemschrift, und dort darf nichts vom Netz abhängen.
if (!isNative) {
  const l = document.createElement('link');
  l.rel = 'stylesheet';
  l.href = 'https://fonts.googleapis.com/css2?family=Roboto:wght@400;500;600;700&display=swap';
  document.head.appendChild(l);
}

async function boot() {
  const root = document.getElementById('root');
  try {
    await Promise.all([storage.init(), loadData()]);
    progress.load();
    startTimeTracking();
    window.__hk = { progress, nav, storage, demo: (k) => progress.replace(demoProgress(k)) };
    if (!location.hash) history.replaceState(null, '', '#/heute');
    root.textContent = '';
    render(html`<${App} />`, root);
  } catch (e) {
    console.error(e);
    root.innerHTML = `<div class="boot" style="flex-direction:column;gap:12px;padding:32px;text-align:center"><b>Die Kursdaten konnten nicht geladen werden.</b><span style="color:rgba(255,255,255,.55);font-size:13px">${String(e.message ?? e)}</span><button class="btn" style="max-width:220px" onclick="location.reload()">Nochmal versuchen</button></div>`;
  }
}
boot();
