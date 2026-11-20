// Start, Rahmen, Reiter und Routen des Fitness-Bereichs.
import { html, render } from '../vendor/preact-htm.js';
import { isNative, exitArea } from './platform.js';
import { AREA, TABS, back, nav, route, useRoute, useToast, useData } from './app.js';
import { Icon, cls } from './ui.js';
import { Today } from './screens/today.js';
import { Training, History, Library } from './screens/training.js';
import { SessionPreview, Player } from './screens/session.js';
import { Feedback, CoachWork } from './screens/feedback.js';
import { ExerciseDetail } from './screens/exercise.js';
import { Skills, SkillDetail, SkillPractice } from './screens/skills.js';
import { NutrientDetail } from './screens/stats.js';
import { Food, PhotoFlow, AddMeal, EntryDetail, DayReport, Shopping, BarcodeFlow } from './screens/food.js';
import { RecipeDetail, RecipeList } from './screens/recipes.js';
import { Me, ProfileEdit, Onboarding } from './screens/me.js';
import { MindHome, MindYoga, MindMeditation, MindKnowledge, MindList, Breath, Guided, YogaPath, YogaLevel, YogaFlow, Poses, AsanaDetail, Deep, Affirmations } from './screens/mind.js';

const TAB_META = AREA === 'mind' ? {
  heute: ['Heute', 'wb_twilight', MindHome, 'c-mind'],
  yoga: ['Yoga', 'self_improvement', MindYoga, 'c-mind'],
  meditation: ['Meditation', 'spa', MindMeditation, 'c-mind'],
  wissen: ['Wissen', 'auto_stories', MindKnowledge, 'c-mind'],
} : {
  heute: ['Heute', 'today', Today, 'c-train'],
  training: ['Training', 'fitness_center', Training, 'c-train'],
  skills: ['Skills', 'sports_gymnastics', Skills, 'c-skill'],
  ernaehrung: ['Ernährung', 'restaurant', Food, 'c-food'],
};

const ROUTES = [
  [/^einheit\/(.+)$/, (m) => html`<${SessionPreview} id=${m[1]} />`],
  [/^los\/(.+)$/, (m) => html`<${Player} id=${m[1]} />`],
  [/^feedback\/(.+)$/, (m) => html`<${Feedback} id=${m[1]} />`],
  [/^coach\/(.+)$/, (m) => html`<${CoachWork} id=${m[1]} />`],
  [/^uebung\/(.+)$/, (m) => html`<${ExerciseDetail} id=${m[1]} />`],
  [/^bibliothek$/, () => html`<${Library} />`],
  [/^verlauf$/, () => html`<${History} />`],
  [/^skill\/([^/]+)\/ueben$/, (m) => html`<${SkillPractice} id=${m[1]} />`],
  [/^skill\/([^/]+)$/, (m) => html`<${SkillDetail} id=${m[1]} />`],
  [/^foto(?:\/(\w+))?$/, (m) => html`<${PhotoFlow} slot=${m[1]} />`],
  [/^barcode(?:\/(\w+))?$/, (m) => html`<${BarcodeFlow} slot=${m[1]} />`],
  [/^mahlzeit\/neu(?:\/(\w+))?$/, (m) => html`<${AddMeal} slot=${m[1]} />`],
  [/^eintrag\/(.+)$/, (m) => html`<${EntryDetail} id=${m[1]} />`],
  [/^ernaehrung\/coach(?:\/(.+))?$/, (m) => html`<${DayReport} day=${m[1]} />`],
  [/^ernaehrung\/naehrstoff\/(\w+)$/, (m) => html`<${NutrientDetail} k=${m[1]} />`],
  [/^einkauf$/, () => html`<${Shopping} />`],
  [/^rezepte(?:\/(\w+))?$/, (m) => html`<${RecipeList} filter=${m[1]} />`],
  [/^rezept\/(.+)$/, (m) => html`<${RecipeDetail} id=${m[1]} />`],
  [/^profil$/, () => html`<${ProfileEdit} />`],
  [/^ich$/, () => html`<${Me} />`],
  [/^achtsam\/(thema|zeit|fuer)\/(\w+)$/, (m) => html`<${MindList} by=${m[1]} k=${m[2]} key=${m[0]} />`],
  [/^achtsam\/atem\/(.+)$/, (m) => html`<${Breath} id=${m[1]} />`],
  [/^achtsam\/gefuehrt\/(.+)$/, (m) => html`<${Guided} id=${m[1]} />`],
  [/^achtsam\/yoga$/, () => html`<${YogaPath} />`],
  [/^achtsam\/yoga\/(\d+)$/, (m) => html`<${YogaLevel} n=${m[1]} key=${m[1]} />`],
  [/^achtsam\/flow\/(\d+)$/, (m) => html`<${YogaFlow} n=${m[1]} />`],
  [/^achtsam\/haltungen$/, () => html`<${Poses} />`],
  [/^achtsam\/haltung\/(.+)$/, (m) => html`<${AsanaDetail} id=${m[1]} key=${m[1]} />`],
  [/^achtsam\/wissen\/(.+)$/, (m) => html`<${Deep} id=${m[1]} />`],
  [/^achtsam\/affirmationen(?:\/(\w+))?$/, (m) => html`<${Affirmations} theme=${m[1]} />`],
  [/^start$/, () => html`<${Onboarding} />`],
];

function TabBar({ active }) {
  return html`<nav class="tabbar">${TABS.map((t) => html`<button class=${cls(active === t && 'on', TAB_META[t][3])} onClick=${() => nav(t, { replace: true })}>
    <span class="pill"><${Icon} n=${TAB_META[t][1]} /></span>${TAB_META[t][0]}
  </button>`)}</nav>`;
}

function Screen({ r }) {
  if (TABS.includes(r)) { const C = TAB_META[r][2]; return html`<${C} key=${r} />`; }
  for (const [re, make] of ROUTES) { const m = re.exec(r); if (m) return make(m); }
  return html`<div class="screen"><div class="empty" style="margin-top:120px"><${Icon} n="explore_off" /><b>Diese Seite gibt es nicht.</b>
    <button class="btn sm ghost mt12" onClick=${() => nav('heute', { replace: true })}>Zur Übersicht</button></div></div>`;
}

const FRAME = isNative || (typeof matchMedia !== 'undefined' && matchMedia('(pointer: coarse)').matches) ? 'full' : '';
if (FRAME === 'full') document.documentElement.classList.add('full');

function App() {
  const r = useRoute();
  const t = useToast();
  const [state] = useData('/fitness/state');
  const tab = TABS.includes(r);
  // Erster Start: Einrichtung (Ziel, Körper, Ausrüstung, Tage).
  const needsOnboarding = AREA === 'fit' && state && !state.profile?.onboarded && r !== 'start';
  return html`<div class=${cls('desk', FRAME)}>
    <div class="phone"><div class="app">
      <div style="position:absolute;inset:0;display:flex;flex-direction:column">
        <div style="position:relative;flex:1;min-height:0">${needsOnboarding ? html`<${Onboarding} />` : html`<${Screen} r=${r} key=${tab ? 'tabs' : r} />`}</div>
        ${tab && !needsOnboarding && html`<${TabBar} active=${r} />`}
      </div>
      ${t && html`<div class="toast" key=${t.at}>${t.text}</div>`}
    </div></div>
  </div>`;
}

// Android-Zurück: erst offene Blätter schließen, dann eine Ebene zurück; auf »Heute« schließt die App den Bereich.
window.__hkBack = () => {
  if (window.__fitSheetClose) { window.__fitSheetClose(); return true; }
  if (window.__fitBackGuard && window.__fitBackGuard()) return true;
  const r = route();
  if (r === 'heute') return false;
  if (TABS.includes(r)) nav('heute', { replace: true }); else back('heute');
  return true;
};
window.__fitExit = exitArea;

if (!isNative) {
  const l = document.createElement('link');
  l.rel = 'stylesheet';
  l.href = 'https://fonts.googleapis.com/css2?family=Roboto:wght@400;500;600;700&display=swap';
  document.head.appendChild(l);
}

const root = document.getElementById('root');
if (!location.hash) history.replaceState(null, '', '#/heute');
root.textContent = '';
render(html`<${App} />`, root);
