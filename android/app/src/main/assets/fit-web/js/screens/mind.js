// Achtsamkeit: Meditation (geführt in Rems Stimme + Videos), Atem, Yoga-Pfad mit Stufen,
// Affirmationen, Impulse und Wissen. Jede Übung endet mit einer kurzen Einkehr (Stimmung vorher/nachher).
import { html, useEffect, useMemo, useRef, useState } from '../../vendor/preact-htm.js';
import { api, local, voice, bell, haptic, keepAwake, openVideo, ambient } from '../platform.js';
import { nav, back, useData, setData, refresh, toast, img, fmtNum } from '../app.js';
import { Icon, Top, Sheet, cls, Spinner, Empty, Ring, Bar } from '../ui.js';

const M = '/fitness/mind';

export const SITUATIONS = [
  ['stress', 'Gestresst', 'bolt'],
  ['schlaf', 'Kann nicht schlafen', 'bedtime'],
  ['angst', 'Unruhig', 'waves'],
  ['fokus', 'Zerstreut', 'center_focus_strong'],
  ['energie', 'Müde', 'battery_low'],
  ['selbstliebe', 'Niedergeschlagen', 'favorite'],
  ['dankbarkeit', 'Ausgeglichen', 'spa'],
];
const THEMES = [
  ['schlaf', 'Schlafen', 'bedtime', '129,140,248'],
  ['stress', 'Stress lösen', 'self_improvement', '45,212,191'],
  ['angst', 'Angst & Grübeln', 'waves', '56,189,248'],
  ['fokus', 'Fokus', 'center_focus_strong', '251,191,36'],
  ['morgen', 'Morgen', 'wb_twilight', '251,146,60'],
  ['koerper', 'Body-Scan', 'accessibility_new', '52,211,153'],
  ['selbstliebe', 'Selbstmitgefühl', 'favorite', '251,113,133'],
  ['dankbarkeit', 'Dankbarkeit', 'volunteer_activism', '167,139,250'],
  ['anfaenger', 'Meditation lernen', 'school', '163,230,53'],
  ['energie', 'Energie', 'bolt', '250,204,21'],
];
const THEME_NAME = Object.fromEntries([...THEMES.map(([k, l]) => [k, l]), ['atem', 'Atem'], ['abend', 'Abend'], ['ruecken', 'Rücken'], ['huefte', 'Hüfte'], ['yin', 'Yin Yoga'], ['kurz', 'Kurze Pause']]);
const AFF_FOR = { stress: 'ruhe', schlaf: 'schlaf', angst: 'mut', fokus: 'fokus', energie: 'koerper', selbstliebe: 'selbstwert', dankbarkeit: 'dankbarkeit' };
const MOOD = [['sentiment_very_dissatisfied', 'Schlecht'], ['sentiment_dissatisfied', 'Eher schlecht'], ['sentiment_neutral', 'Okay'], ['sentiment_satisfied', 'Gut'], ['sentiment_very_satisfied', 'Sehr gut']];
const ART = { stehend: 'Stehend', sitzend: 'Sitzend', liegend: 'Liegend', rueckbeuge: 'Rückbeuge', vorbeuge: 'Vorbeuge', umkehr: 'Umkehrhaltung', balance: 'Gleichgewicht', armbalance: 'Armbalance', drehung: 'Drehung', hueftoeffner: 'Hüftöffner', entspannung: 'Entspannung' };
const PHASE = { ankommen: 'Ankommen', atem: 'Atem', asana: 'Haltung', stille: 'Stille', savasana: 'Ruhen' };

const min = (s) => Math.max(1, Math.round(s / 60));
const mmss = (s) => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, '0')}`;
const ytThumb = (id) => img(`https://i.ytimg.com/vi/${id}/mqdefault.jpg`);
export const poseImg = (api) => `/yoga/${api}.svg`;
const bucket = (s) => (s <= 8 * 60 ? 5 : s <= 15 * 60 ? 10 : s <= 25 * 60 ? 20 : 30);

// ── Kleine Bausteine ────────────────────────────────────────────────────────

function MoodPick({ value, onChange, label }) {
  return html`<div>
    ${label && html`<div class="small muted mb8">${label}</div>`}
    <div class="mood">${MOOD.map(([icon, l], i) => html`<button class=${cls(value === i + 1 && 'on')} onClick=${() => { haptic(); onChange(value === i + 1 ? null : i + 1); }}>
      <${Icon} n=${icon} fill=${value === i + 1} /><span>${l}</span></button>`)}</div>
  </div>`;
}

function VideoCard({ v, wide }) {
  return html`<button class=${cls('vcard', wide && 'wide')} onClick=${() => startVideo(v)}>
    <div class="th"><img src=${ytThumb(v.id)} loading="lazy" alt="" /><span class="dur">${min(v.seconds)} min</span>${v.lang === 'en' && html`<span class="lang">EN</span>`}
      <span class="play"><${Icon} n="play_arrow" fill /></span></div>
    <div class="tx"><b>${v.title}</b><span>${v.channel}${v.note && wide ? ` · ${v.note}` : ''}</span></div>
  </button>`;
}

function GuidedCard({ g }) {
  return html`<button class="gcard" onClick=${() => nav(`achtsam/gefuehrt/${g.id}`)}>
    <div class="orb sm"><i /></div>
    <div class="tx"><b>${g.titel}</b><span>${g.minuten} min · Rems Stimme</span></div>
  </button>`;
}

function BreathCard({ a }) {
  const cycle = a.phasen.reduce((s, p) => s + p.sekunden, 0);
  return html`<button class="bcard" onClick=${() => nav(`achtsam/atem/${a.id}`)}>
    <div class="bc-ring"><${Icon} n="air" /></div>
    <b>${a.name}</b><span>${a.kurz}</span>
    <em>${a.phasen.map((p) => fmtNum(p.sekunden, 1)).join(' · ')} s · ${min(cycle * a.runden_standard)} min</em>
  </button>`;
}

/** Video starten — beim Zurückkommen fragt die App, wie es war, und trägt die Zeit ein. */
export function startVideo(v) {
  const open = mindCache?.path?.open ?? 1;
  const ref = v.kind === 'yoga' && v.level && v.level <= open ? `stufe-${v.level}` : v.id;
  local.set('mind:pending', { id: v.id, kind: v.kind === 'yoga' ? 'yoga' : 'video', ref, title: v.title, seconds: v.seconds, at: Date.now() });
  openVideo(v.id, v.title);
}
let mindCache = null;

/** Zurück aus einem Video: kurz nachfragen und eintragen. */
function PendingVideo() {
  const [p, setP] = useState(null);
  useEffect(() => {
    const check = () => {
      if (document.visibilityState !== 'visible') return;
      const x = local.get('mind:pending', null);
      if (!x) return;
      const elapsed = (Date.now() - x.at) / 1000;
      if (elapsed < 60) return;
      setP({ ...x, watched: Math.min(elapsed, x.seconds) });
    };
    check();
    document.addEventListener('visibilitychange', check);
    window.addEventListener('focus', check);
    return () => { document.removeEventListener('visibilitychange', check); window.removeEventListener('focus', check); };
  }, []);
  const [mood, setMood] = useState(null);
  if (!p) return null;
  const close = () => { local.set('mind:pending', null); setP(null); };
  const save = async () => {
    const r = await api.post(`${M}/log`, { kind: p.kind, ref: p.ref, seconds: p.watched, moodAfter: mood }).catch(() => null);
    close();
    refresh(M);
    if (r?.unlocked) toast(`Stufe ${r.unlocked} im Yoga-Pfad ist frei`);
    else toast(`${min(p.watched)} Minuten eingetragen`);
  };
  return html`<${Sheet} title="Schön, dass du da warst" onClose=${close}>
    <div class="c-mind">
      <p class="small muted">${p.title} · etwa ${min(p.watched)} Minuten</p>
      <div class="mt16"><${MoodPick} value=${mood} onChange=${setMood} label="Wie fühlst du dich jetzt?" /></div>
      <button class="btn mt20" onClick=${save}><${Icon} n="check" />Eintragen</button>
      <button class="btn ghost mt8" onClick=${close}>Nicht eintragen</button>
    </div>
  <//>`;
}

// ── Bereich Achtsamkeit: vier Reiter, jede Übersicht auf einen Blick ──────

const GUIDED_ART = { ankommen: 'sunrise', 'body-scan': 'body', einschlafen: 'moon', 'notfall-stress': 'wave', 'fokus-arbeit': 'focus' };

/** Kleine Kunst statt Schleier: Nachtblau, ein klarer goldener Mond/Kreis — je Meditation eine Form. */
function Art({ kind = 'moon', big }) {
  return html`<div class=${cls('art', `art-${kind}`, big && 'big')}><i /><b /></div>`;
}

function Rail({ title, more, children }) {
  return html`<section class="railSec">
    <div class="railHead"><h2>${title}</h2>${more && html`<button onClick=${more}>Alle</button>`}</div>
    <div class="rail">${children}</div>
  </section>`;
}

function PosterVideo({ v }) {
  return html`<button class="pv" onClick=${() => startVideo(v)}>
    <div class="pv-th"><img src=${ytThumb(v.id)} loading="lazy" alt="" /><span class="pv-dur">${min(v.seconds)} min</span></div>
    <b>${v.title}</b><span>${v.channel}</span></button>`;
}
function PosterGuided({ g }) {
  return html`<button class="pg" onClick=${() => nav(`achtsam/gefuehrt/${g.id}`)}>
    <${Art} kind=${GUIDED_ART[g.id] ?? 'moon'} /><b>${g.titel}</b><span>${g.minuten} min mit Rem</span></button>`;
}
function PosterBreath({ a }) {
  const cycle = a.phasen.reduce((x, p) => x + p.sekunden, 0);
  return html`<button class="pb" onClick=${() => nav(`achtsam/atem/${a.id}`)}>
    <div class="pb-rhythm">${a.phasen.map((p) => html`<i class=${p.typ} style=${{ flex: p.sekunden }} />`)}</div>
    <b>${a.name}</b><span>${min(cycle * a.runden_standard)} min</span></button>`;
}

function useMind() {
  const [d] = useData(M);
  useEffect(() => { if (d) mindCache = d; }, [d]);
  return d;
}
const hello = () => { const h = new Date().getHours(); return h < 5 ? 'Gute Nacht' : h < 11 ? 'Guten Morgen' : h < 17 ? 'Hallo' : h < 22 ? 'Guten Abend' : 'Gute Nacht'; };

/** Was jetzt passt — nach Tageszeit. */
function pickNow(d) {
  const h = new Date().getHours();
  const g = (id) => d.gefuehrt.find((x) => x.id === id);
  const level = d.levels.find((l) => l.stufe === d.path.open) ?? d.levels[0];
  if (h >= 5 && h < 11 && level) return { kicker: 'Dein Morgen', title: `Yoga · ${level.name}`, meta: `${level.minuten} min · Stufe ${level.stufe}`, go: () => nav(`achtsam/flow/${level.stufe}`), pose: d.asanas.find((a) => level.neu.includes(a.id)) };
  if (h >= 21 || h < 5) { const x = g('einschlafen'); if (x) return { kicker: 'Für die Nacht', title: x.titel, meta: `${x.minuten} min mit Rem`, go: () => nav(`achtsam/gefuehrt/${x.id}`), art: 'moon' }; }
  if (h >= 11 && h < 17) { const x = g('fokus-arbeit'); if (x) return { kicker: 'Für deinen Tag', title: x.titel, meta: `${x.minuten} min mit Rem`, go: () => nav(`achtsam/gefuehrt/${x.id}`), art: 'focus' }; }
  const x = g('ankommen') ?? d.gefuehrt[0];
  return x ? { kicker: 'Für deinen Abend', title: x.titel, meta: `${x.minuten} min mit Rem`, go: () => nav(`achtsam/gefuehrt/${x.id}`), art: 'sunrise' } : null;
}

function MindHead({ title, sub, right }) {
  return html`<header class="mHead"><div><h1>${title}</h1>${sub && html`<p>${sub}</p>`}</div>${right}</header>`;
}

export function MindHome() {
  const d = useMind();
  if (!d) return html`<div class="screen tab"><${MindHead} title=${hello()} /><div class="pad"><div class="skel" style="height:360px" /></div></div>`;
  const now = pickNow(d);
  const st = d.stats;
  const level = d.levels.find((l) => l.stufe === d.path.open) ?? d.levels[0];
  const prog = d.path.levels.find((p) => p.stufe === d.path.open);
  const quick = d.atem.find((a) => a.id === 'kohaerent') ?? d.atem[0];
  return html`<div class="screen tab">
    <div class="scroll">
      <${MindHead} title=${hello()} sub=${st.todayMinutes ? `Heute ${st.todayMinutes} Minuten für dich` : 'Nimm dir einen Moment.'}
        right=${st.streak > 0 && html`<div class="mStreak"><b class="num">${st.streak}</b><span>${st.streak === 1 ? 'Tag' : 'Tage'}</span></div>`} />
      <div class="pad">
        ${now && html`<button class="nowCard" onClick=${now.go}>
          <div class="nc-art">${now.pose ? html`<div class="nc-pose"><img src=${poseImg(now.pose.api)} alt="" /></div>` : html`<${Art} kind=${now.art} big />`}</div>
          <div class="nc-tx"><span>${now.kicker}</span><h2>${now.title}</h2><p>${now.meta}</p></div>
          <div class="nc-play"><${Icon} n="play_arrow" fill /></div>
        </button>`}

        <div class="moodRow mt20"><span>Wie fühlst du dich?</span>
          <div class="mr-chips">${SITUATIONS.map(([k, l, icon]) => html`<button onClick=${() => nav(`achtsam/fuer/${k}`)}><${Icon} n=${icon} />${l}</button>`)}</div></div>

        <div class="twoUp mt20">
          ${level && html`<button class="tu pathTile" onClick=${() => nav(`achtsam/yoga/${level.stufe}`)}>
            <${Ring} size=${54} stroke=${5} value=${Math.min(prog?.sessions ?? 0, prog?.need.einheiten ?? 1)} max=${prog?.need.einheiten ?? 1} color="rgb(var(--mind))" track="rgba(255,255,255,.1)"><b class="num">${level.stufe}</b><//>
            <b>${level.name}</b><span>${prog?.met ? 'Nächste Stufe frei' : `${prog?.sessions ?? 0} von ${prog?.need.einheiten ?? 0} Einheiten`}</span></button>`}
          ${quick && html`<button class="tu breath" onClick=${() => nav(`achtsam/atem/${quick.id}`)}>
            <div class="pb-rhythm">${quick.phasen.map((p) => html`<i class=${p.typ} style=${{ flex: p.sekunden }} />`)}</div>
            <b>Kurz durchatmen</b><span>${quick.name}</span></button>`}
        </div>

        ${d.affirmation && html`<button class="affStrip mt12" onClick=${() => voice.speak(d.affirmation.text)}>
          <p>${d.affirmation.text}</p><${Icon} n="volume_up" /></button>`}
        <div class="end" />
      </div>
    </div>
    <${PendingVideo} />
  </div>`;
}

export function MindYoga() {
  const d = useMind();
  const railRef = useRef(null);
  useEffect(() => {
    // Zum aktuellen Stand scrollen.
    const el = railRef.current?.querySelector('.stage.cur');
    if (el) railRef.current.scrollLeft = el.offsetLeft - 16;
  }, [d?.path?.open]);
  if (!d) return html`<div class="screen tab"><${MindHead} title="Yoga" /><div class="pad"><div class="skel" style="height:360px" /></div></div>`;
  const yogaVideos = d.videos.filter((v) => v.kind === 'yoga' && !v.level);
  return html`<div class="screen tab">
    <div class="scroll">
      <${MindHead} title="Yoga" sub="Dein Pfad in sechs Stufen" right=${html`<button class="ib" onClick=${() => nav('achtsam/haltungen')} aria-label="Haltungen"><${Icon} n="grid_view" /></button>`} />
      <div class="stages" ref=${railRef}>${d.levels.map((l) => {
        const p = d.path.levels.find((x) => x.stufe === l.stufe);
        const lock = !p?.unlocked;
        const done = p?.met && l.stufe < d.path.open;
        const poses = d.asanas.filter((a) => l.neu.includes(a.id));
        return html`<button class=${cls('stage', lock && 'locked', p?.current && 'cur')} onClick=${() => nav(`achtsam/yoga/${l.stufe}`)}>
          <div class="st-img"><img src=${poseImg((poses[1] ?? poses[0])?.api)} alt="" />${lock && html`<span class="st-lock"><${Icon} n="lock" /></span>`}${done && html`<span class="st-done"><${Icon} n="check" /></span>`}</div>
          <div class="st-tx"><span>Stufe ${l.stufe} · ${l.minuten} min</span><b>${l.name}</b><p>${l.untertitel}</p>
            ${p?.current && html`<div class="st-bar"><i style=${{ width: `${Math.min(100, (p.sessions / p.need.einheiten) * 100)}%` }} /></div>`}</div>
        </button>`;
      })}</div>
      <div class="pad">
        <div class="twoUp mt16">
          <button class="tu poses" onClick=${() => nav('achtsam/haltungen')}>
            <div class="tu-poses">${d.asanas.slice(3, 6).map((a) => html`<img src=${poseImg(a.api)} alt="" />`)}</div>
            <b>Haltungen</b><span>${d.asanas.length} mit Anleitung</span></button>
          <button class="tu" onClick=${() => nav('achtsam/thema/yoga')}>
            <div class="tu-thumb">${yogaVideos[0] && html`<img src=${ytThumb(yogaVideos[0].id)} alt="" />`}</div>
            <b>Mit Video</b><span>${d.videos.filter((v) => v.kind === 'yoga').length} Einheiten</span></button>
        </div>
        ${yogaVideos.length > 0 && html`<${Rail} title="Kurz zwischendurch" more=${() => nav('achtsam/thema/yoga')}>${yogaVideos.slice(0, 8).map((v) => html`<${PosterVideo} v=${v} />`)}<//>`}
        <div class="end" />
      </div>
    </div>
    <${PendingVideo} />
  </div>`;
}

export function MindMeditation() {
  const d = useMind();
  const [theme, setTheme] = useState(local.get('mind:theme', ''));
  if (!d) return html`<div class="screen tab"><${MindHead} title="Meditation" /><div class="pad"><div class="skel" style="height:360px" /></div></div>`;
  const pick = (t) => { const v = theme === t ? '' : t; setTheme(v); local.set('mind:theme', v); };
  const match = (list) => (theme ? list.filter((x) => (x.themes ?? x.themen ?? x.wofuer ?? []).includes(theme)) : list);
  const guided = match(d.gefuehrt);
  const breath = match(d.atem.map((a) => ({ ...a, themes: a.wofuer })));
  const videos = match(d.videos.filter((v) => v.kind !== 'yoga')).sort((a, b) => a.seconds - b.seconds);
  return html`<div class="screen tab">
    <div class="scroll">
      <${MindHead} title="Meditation" sub=${theme ? THEME_NAME[theme] : 'Geführt, zum Atmen, für jede Lage'} />
      <div class="themeChips">${THEMES.map(([k, l, icon]) => html`<button class=${cls(theme === k && 'on')} onClick=${() => pick(k)}><${Icon} n=${icon} />${l}</button>`)}</div>
      <div class="pad">
        ${guided.length > 0 && html`<${Rail} title="Mit Rem">${guided.map((g) => html`<${PosterGuided} g=${g} />`)}<//>`}
        ${breath.length > 0 && html`<${Rail} title="Atmen">${breath.map((a) => html`<${PosterBreath} a=${a} />`)}<//>`}
        ${videos.length > 0 && html`<${Rail} title="Videos" more=${() => nav(theme ? `achtsam/thema/${theme}` : 'achtsam/zeit/10')}>${videos.slice(0, 10).map((v) => html`<${PosterVideo} v=${v} />`)}<//>`}
        ${!guided.length && !breath.length && !videos.length && html`<${Empty} icon="spa" title="Dafür gibt es noch nichts" text="Wähle ein anderes Thema." />`}
        <div class="end" />
      </div>
    </div>
    <${PendingVideo} />
  </div>`;
}

const ART_FOR_DEEP = { teal: 'wave', violet: 'moon', amber: 'sunrise', sky: 'focus', rose: 'body' };

export function MindKnowledge() {
  const d = useMind();
  if (!d) return html`<div class="screen tab"><${MindHead} title="Wissen" /><div class="pad"><div class="skel" style="height:360px" /></div></div>`;
  return html`<div class="screen tab">
    <div class="scroll">
      <${MindHead} title="Wissen" sub="Verstehen, was du übst" />
      <div class="pad">
        ${d.affirmation && html`<button class="affHero" onClick=${() => nav('achtsam/affirmationen')}>
          <span>Affirmation des Tages</span><p>${d.affirmation.text}</p>
          <div class="ah-row"><span onClick=${(e) => { e.stopPropagation(); voice.speak(d.affirmation.text); }}><${Icon} n="volume_up" />Anhören</span><span>Alle 90<${Icon} n="chevron_right" /></span></div></button>`}
        <${Rail} title="Lesen">${d.deepdives.map((x) => html`<button class=${cls('book', `t-${x.farbe}`)} onClick=${() => nav(`achtsam/wissen/${x.id}`)}>
          <${Art} kind=${ART_FOR_DEEP[x.farbe] ?? 'moon'} /><b>${x.titel}</b><span>${x.minuten} min</span></button>`)}<//>
        ${d.impuls && html`<div class="impulseCard mt20"><span>Impuls für heute</span><b>${d.impuls.titel}</b><p>${d.impuls.text}</p></div>`}
        <div class="end" />
      </div>
    </div>
  </div>`;
}

// ── Listen: Thema, Zeit, Situation ──────────────────────────────────────────

export function MindList({ by, k }) {
  const [d] = useData(M);
  const [dur, setDur] = useState(by === 'zeit' ? Number(k) : 0);
  if (!d) return html`<div class="screen"><${Top} title="Achtsamkeit" /><div class="pad"><div class="skel" style="height:300px" /></div></div>`;
  const sit = SITUATIONS.find((s) => s[0] === k);
  const title = by === 'zeit' ? `${k}${k === '30' ? '+' : ''} Minuten` : by === 'fuer' ? sit?.[1] ?? 'Für dich' : k === 'yoga' ? 'Yoga mit Video' : THEME_NAME[k] ?? k;
  const fits = (sec) => !dur || bucket(sec) === dur;
  const match = (themes) => by === 'zeit' || (themes ?? []).includes(k);
  const videos = k === 'yoga' ? d.videos.filter((v) => v.kind === 'yoga' && fits(v.seconds)).sort((a, b) => (a.level ?? 9) - (b.level ?? 9) || a.seconds - b.seconds)
    : d.videos.filter((v) => match(v.themes) && fits(v.seconds));
  const guided = d.gefuehrt.filter((g) => match(g.themen) && fits(g.minuten * 60));
  const breath = d.atem.filter((a) => (by === 'zeit' ? dur <= 10 : a.wofuer.includes(k)));
  const aff = by === 'fuer' && AFF_FOR[k];
  const sub = by === 'fuer' ? 'Das passt jetzt zu dir' : `${videos.length + guided.length} Einheiten`;
  const medi = videos.filter((v) => v.kind !== 'yoga');
  const yoga = videos.filter((v) => v.kind === 'yoga');
  return html`<div class="screen c-mind">
    <${Top} title=${title} sub=${sub} />
    <div class="scroll"><div class="pad">
      ${by !== 'zeit' && html`<div class="chips wrap mt4">${[[0, 'Alle'], [5, '≈ 5 min'], [10, '≈ 10 min'], [20, '≈ 20 min'], [30, '30+ min']].map(([m, l]) => html`<button class=${cls('chip', dur === m && 'on')} onClick=${() => setDur(m)}>${l}</button>`)}</div>`}
      ${by === 'fuer' && breath[0] && html`<div class="sec"><h2 class="h2">Sofort</h2><span class="small muted">zwei Minuten reichen</span></div><div class="hscroll">${breath.map((a) => html`<${BreathCard} a=${a} />`)}</div>`}
      ${guided.length > 0 && html`<div class="sec"><h2 class="h2">Geführt von Rem</h2></div><div class="stack8">${guided.map((g) => html`<${GuidedCard} g=${g} />`)}</div>`}
      ${by !== 'fuer' && breath.length > 0 && html`<div class="sec"><h2 class="h2">Atmen</h2></div><div class="hscroll">${breath.map((a) => html`<${BreathCard} a=${a} />`)}</div>`}
      ${medi.length > 0 && html`<div class="sec"><h2 class="h2">Meditationen</h2><span class="small muted">${medi.length}</span></div><div class="stack10">${medi.map((v) => html`<${VideoCard} v=${v} wide />`)}</div>`}
      ${yoga.length > 0 && html`<div class="sec"><h2 class="h2">Yoga</h2><span class="small muted">${yoga.length}</span></div><div class="stack10">${yoga.map((v) => html`<${VideoCard} v=${v} wide />`)}</div>`}
      ${aff && html`<button class="affCard mt20" onClick=${() => nav(`achtsam/affirmationen/${aff}`)}><div class="eyebrow">Affirmationen</div><div class="aff mt8" style="font-size:18px">Worte, die jetzt guttun</div></button>`}
      ${!videos.length && !guided.length && !breath.length && html`<${Empty} icon="self_improvement" title="Hier ist noch nichts" text="Probier eine andere Dauer." />`}
      <div class="end" />
    </div></div>
    <${PendingVideo} />
  </div>`;
}

// ── Abschluss jeder Übung ───────────────────────────────────────────────────

function Done({ kind, ref, seconds, moodBefore, title, onAgain }) {
  const [mood, setMood] = useState(null);
  const [note, setNote] = useState('');
  const [saved, setSaved] = useState(null);
  const save = async () => {
    const r = await api.post(`${M}/log`, { kind, ref, seconds, moodBefore, moodAfter: mood, note: note.trim() || undefined }).catch(() => null);
    setSaved(r ?? { ok: false });
    refresh(M);
    if (r?.unlocked) { bell(1); haptic('success'); }
  };
  if (saved) {
    return html`<div class="doneMind">
      <div class="orb big done"><i /></div>
      ${saved.unlocked ? html`<h2 class="h1 mt20">Stufe ${saved.unlocked} ist frei</h2><p class="muted mt8">Du hast die Übung deiner Stufe verinnerlicht. Neue Haltungen warten — in deinem Tempo.</p>
          <button class="btn mt20" onClick=${() => nav(`achtsam/yoga/${saved.unlocked}`, { replace: true })}><${Icon} n="lock_open" />Zur neuen Stufe</button>`
        : html`<h2 class="h1 mt20">${min(seconds)} Minuten für dich</h2><p class="muted mt8">${saved.stats?.streak > 1 ? `${saved.stats.streak} Tage in Folge. ` : ''}Nimm dieses Gefühl mit in den Tag.</p>`}
      <button class="btn ghost mt12" onClick=${() => back('achtsam')}>Fertig</button>
    </div>`;
  }
  return html`<div class="doneMind">
    <div class="orb big done"><i /></div>
    <h2 class="h1 mt20">Schön gemacht</h2>
    <p class="muted mt8">${title} · ${min(seconds)} min</p>
    <div class="mt20" style="width:100%"><${MoodPick} value=${mood} onChange=${setMood} label="Wie fühlst du dich jetzt?" /></div>
    <textarea class="input mt16" rows="2" placeholder="Was hast du bemerkt? (optional)" value=${note} onInput=${(e) => setNote(e.target.value)} />
    <button class="btn mt16" onClick=${save}><${Icon} n="check" />Eintragen</button>
    ${onAgain && html`<button class="btn ghost mt8" onClick=${onAgain}>Noch einmal</button>`}
  </div>`;
}

// ── Atemübung ───────────────────────────────────────────────────────────────

export function Breath({ id }) {
  const [a] = useData(`${M}/atem/${id}`);
  const [rounds, setRounds] = useState(null);
  const [mood, setMood] = useState(null);
  const [run, setRun] = useState(null); // { start, paused }
  const [doneSec, setDoneSec] = useState(null);
  const bed = useSoundChoice('atem', id, a?.wofuer);
  useBedPreviewOff();
  if (!a) return html`<div class="screen"><${Top} title="Atmen" /><div class="pad"><div class="skel" style="height:300px" /></div></div>`;
  const n = rounds ?? a.runden_standard;
  const cycle = a.phasen.reduce((s, p) => s + p.sekunden, 0);
  if (doneSec != null) return html`<div class="screen c-mind"><${Top} title=${a.name} /><div class="scroll"><div class="pad"><${Done} kind="atem" ref=${a.id} seconds=${doneSec} moodBefore=${mood} title=${a.name} onAgain=${() => { setDoneSec(null); setRun(null); }} /></div></div></div>`;
  if (run) return html`<${BreathRun} a=${a} rounds=${n} bed=${bed} onEnd=${(s) => { setRun(null); setDoneSec(s); }} onQuit=${() => setRun(null)} />`;
  return html`<div class="screen c-mind">
    <${Top} title=${a.name} sub=${a.kurz} />
    <div class="scroll"><div class="pad">
      <div class="breathPreview"><div class="orb"><i /></div><div class="phRow">${a.phasen.map((p) => html`<span><b>${fmtNum(p.sekunden, 1)}</b>${p.sage}</span>`)}</div></div>
      <div class="sec"><h2 class="h2">Runden</h2><span class="small muted">≈ ${min(cycle * n)} min</span></div>
      <div class="chips wrap">${[...new Set([Math.max(3, Math.round(a.runden_standard / 2)), a.runden_standard, a.runden_standard * 2])].map((r) => html`<button class=${cls('chip', n === r && 'on')} onClick=${() => setRounds(r)}>${r} Runden</button>`)}</div>
      <${SoundPick} c=${bed} preview />
      <div class="mt20"><${MoodPick} value=${mood} onChange=${setMood} label="Wie fühlst du dich gerade? (optional)" /></div>
      <button class="btn mt20" onClick=${() => setRun({ start: Date.now() })}><${Icon} n="play_arrow" fill />Beginnen</button>
      <div class="card pad16 mt20"><div class="h3">So geht's</div><ol class="steps mt8">${a.anleitung.map((s) => html`<li>${s}</li>`)}</ol></div>
      <div class="card pad16 mt12"><div class="h3">Warum es wirkt</div><p class="small mt8" style="line-height:1.55">${a.warum}</p></div>
      ${a.vorsicht && html`<div class="card pad16 mt12 warnCard"><${Icon} n="info" /><p class="small">${a.vorsicht}</p></div>`}
      <div class="end" />
    </div></div>
  </div>`;
}

function BreathRun({ a, rounds, bed, onEnd, onQuit }) {
  const cycle = a.phasen.reduce((s, p) => s + p.sekunden, 0);
  const total = cycle * rounds;
  const [t, setT] = useState(0);
  const [paused, setPaused] = useState(false);
  const base = useRef({ start: performance.now(), acc: 0 });
  const last = useRef(-1);
  useBed(bed, paused);
  useEffect(() => { keepAwake(true); bell(0.6); return () => { keepAwake(false); voice.stop(); }; }, []);
  useEffect(() => {
    if (paused) { base.current.acc = t; return undefined; }
    base.current.start = performance.now();
    let raf;
    const tick = () => {
      const now = base.current.acc + (performance.now() - base.current.start) / 1000;
      setT(now);
      if (now >= total) { bell(0.7); onEnd(Math.round(total)); return; }
      raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [paused]);
  const inCycle = t % cycle;
  const round = Math.min(rounds, Math.floor(t / cycle) + 1);
  let acc = 0;
  let pi = 0;
  for (; pi < a.phasen.length; pi++) { if (inCycle < acc + a.phasen[pi].sekunden) break; acc += a.phasen[pi].sekunden; }
  const ph = a.phasen[Math.min(pi, a.phasen.length - 1)];
  const pt = (inCycle - acc) / ph.sekunden;
  // »Kurze Stöße« (Feueratem): pulsieren statt einem langen Ausatmen.
  const bursts = /stö(ß|ss)e/i.test(ph.sage);
  const scale = bursts ? 0.8 + 0.07 * Math.abs(Math.sin((inCycle - acc) * Math.PI))
    : ph.typ === 'ein' ? 0.55 + 0.45 * pt : ph.typ === 'aus' ? 1 - 0.45 * pt : ph.typ === 'halten' ? 1 : 0.55;
  const key = Math.floor(t / cycle) * 10 + pi;
  useEffect(() => {
    if (key === last.current || paused) return;
    last.current = key;
    haptic();
    voice.speak(ph.sage);
  }, [key]);
  return html`<div class="screen breathRun c-mind">
    <div class="brTop"><button class="ib" onClick=${() => { voice.stop(); onQuit(); }} aria-label="Beenden"><${Icon} n="close" /></button><span>Runde ${round} von ${rounds} · ${mmss(Math.max(0, total - t))}</span><${BedButton} c=${bed} /></div>
    <div class="brStage" onClick=${() => setPaused(!paused)}>
      <div class="brOrb" style=${{ transform: `scale(${scale})` }}><i /></div>
      <div class="brLabel"><b>${paused ? 'Pause' : ph.sage}</b><span>${paused ? 'Tippen zum Weitermachen' : Math.ceil(ph.sekunden - (inCycle - acc))}</span></div>
    </div>
    <div class="brBottom"><div class="bar"><i style=${{ width: `${(t / total) * 100}%` }} /></div></div>
  </div>`;
}

// ── Klangbett: Hintergrundmusik unter Meditation, Yoga und Atem ─────────────
// Die Betten kommen vom Server (/fitness/mind/sounds), gespielt wird nativ (platform.js → ambient).
// Vorschlag nach Thema; wer selbst wählt, behält die Wahl für genau diese Einheit.
const SOUND_FOR = { schlaf: 'schlaf', stress: 'tibet', angst: 'hz528', koerper: 'hz432', fokus: 'kristall', ruhe: 'hz432', energie: 'wald' };
function recommendSound(kind, themen = []) {
  if (kind === 'yoga') return 'handpan';
  const t = themen[0];
  if (kind === 'atem' && t === 'schlaf') return 'regen';
  return SOUND_FOR[t] ?? 'kristall';
}
function useSoundChoice(kind, ref, themen) {
  const [sounds] = useData(`${M}/sounds`);
  const key = `mind:klang:${kind}:${ref}`;
  // Läuft schon deine eigene Musik, bleibt sie (Vorschlag »Stille«) — außer du hast hier etwas gewählt.
  const [picked, setPicked] = useState(() => local.get(key, null));
  const [ownMusic] = useState(() => ambient.musicPlaying());
  const [vol, setVol] = useState(() => local.get('mind:klangVol', 0.6));
  // Der Vorschlag folgt den Themen, sobald die Einheit geladen ist (nicht beim ersten Rendern einfrieren).
  const id = picked ?? (ownMusic ? 'aus' : recommendSound(kind, themen));
  const sound = (id !== 'aus' && sounds?.find((x) => x.id === id)) || null;
  return {
    sounds, sound, vol, id: sound ? id : 'aus', rec: recommendSound(kind, themen),
    pick(x) { setPicked(x); local.set(key, x); },
    volume(v) { setVol(v); local.set('mind:klangVol', v); },
  };
}
/** Auswahl als Kachelreihe (Stille + Betten) und Lautstärke. preview: Antippen spielt das Bett an. */
function SoundPick({ c, preview }) {
  const rail = useRef(null);
  // Die gewählte Kachel sichtbar machen (der Vorschlag liegt oft weiter rechts in der Reihe).
  useEffect(() => {
    const r = rail.current;
    const on = r?.querySelector('.kl.on');
    if (r && on) r.scrollLeft = Math.max(0, on.offsetLeft - r.offsetLeft - (r.clientWidth - on.clientWidth) / 2);
  }, [c.sounds?.length, c.id]);
  if (!c.sounds?.length) return null;
  const choose = (x) => {
    c.pick(x);
    if (!preview) return;
    const snd = c.sounds.find((y) => y.id === x);
    if (snd) ambient.play(snd.id, snd.v, c.vol, snd.loop); else ambient.stop(1200);
  };
  return html`<div class="klang">
    <div class="sec"><h2 class="h2">Klang</h2><span class="small muted">${c.sound ? c.sound.kurz : 'Nur Rems Stimme'}</span></div>
    <div class="rail klRail" ref=${rail}>
      <button class=${cls('kl', c.id === 'aus' && 'on')} style=${{ '--kc': '#232a52' }} onClick=${() => choose('aus')}><span class="kl-art"><${Icon} n="volume_off" /></span><b>Stille</b></button>
      ${c.sounds.map((x) => html`<button class=${cls('kl', c.id === x.id && 'on')} style=${{ '--kc': x.farbe }} onClick=${() => choose(x.id)}>
        <span class="kl-art"><${Icon} n=${x.icon} />${x.id === c.rec && html`<i>passt</i>`}</span><b>${x.name}</b></button>`)}
    </div>
    ${c.sound && html`<label class="klVol"><${Icon} n="volume_down" /><input type="range" min="0.1" max="1" step="0.05" value=${c.vol}
      onInput=${(e) => { const v = Number(e.target.value); c.volume(v); if (preview) ambient.volume(v); }} /><${Icon} n="volume_up" /></label>`}
  </div>`;
}
/** Bett während einer Einheit: an, leiser/lauter, Pause, am Ende ausblenden. */
function useBed(c, paused) {
  useEffect(() => { if (c.sound) ambient.play(c.sound.id, c.sound.v, c.vol, c.sound.loop); else ambient.stop(1500); }, [c.sound?.id]);
  useEffect(() => { if (c.sound) ambient.volume(c.vol); }, [c.vol]);
  useEffect(() => { if (c.sound) ambient.pause(paused); }, [paused]);
  useEffect(() => () => ambient.stop(3500), []);
}
/** Vorschauseite verlassen (zurück) → angespieltes Bett aus. */
function useBedPreviewOff() { useEffect(() => () => ambient.stop(1200), []); }
/** Knopf + Blatt, um das Bett während der Einheit zu wechseln. */
function BedButton({ c }) {
  const [open, setOpen] = useState(false);
  if (!c.sounds?.length) return null;
  return html`<button class="ib" onClick=${(e) => { e.stopPropagation(); setOpen(true); }} aria-label="Klang"><${Icon} n=${c.sound ? 'music_note' : 'music_off'} /></button>
    ${open && html`<${Sheet} title="Klang" onClose=${() => setOpen(false)}><div class="pad klSheet" onClick=${(e) => e.stopPropagation()}><${SoundPick} c=${c} />
      <p class="tiny faint mt12">${c.sound?.quelle?.kanal ? `Musik: ${c.sound.quelle.kanal} (YouTube) · ` : ''}Klangschalen: »דג בלי מלח«, Wikimedia Commons, CC BY-SA 4.0</p></div><//>`}`;
}

// ── Geführte Meditation (Rems Stimme, mit Stille dazwischen) ────────────────

export function Guided({ id }) {
  const [g] = useData(`${M}/guided/${id}`);
  const [mood, setMood] = useState(null);
  const [run, setRun] = useState(false);
  const [doneSec, setDoneSec] = useState(null);
  const bed = useSoundChoice('meditation', id, g?.themen);
  useBedPreviewOff();
  if (!g) return html`<div class="screen"><${Top} title="Meditation" /><div class="pad"><div class="skel" style="height:300px" /></div></div>`;
  if (doneSec != null) return html`<div class="screen c-mind"><${Top} title=${g.titel} /><div class="scroll"><div class="pad"><${Done} kind="meditation" ref=${g.id} seconds=${doneSec} moodBefore=${mood} title=${g.titel} /></div></div></div>`;
  if (run) return html`<${GuidedRun} g=${g} bed=${bed} onEnd=${(s) => { setRun(false); setDoneSec(s); }} onQuit=${() => setRun(false)} />`;
  return html`<div class="screen c-mind">
    <${Top} title=${g.titel} sub=${`${g.minuten} min · geführt von Rem`} />
    <div class="scroll"><div class="pad">
      <div class="guidedHero"><div class="orb big"><i /></div><p class="mt20" style="text-align:center;line-height:1.55">${g.untertitel}</p></div>
      <${SoundPick} c=${bed} preview />
      <div class="mt20"><${MoodPick} value=${mood} onChange=${setMood} label="Wie fühlst du dich gerade? (optional)" /></div>
      <button class="btn mt20" onClick=${() => setRun(true)}><${Icon} n="play_arrow" fill />Beginnen</button>
      <p class="tiny faint mt12" style="text-align:center">Kopfhörer und ein ruhiger Platz helfen. Der Bildschirm bleibt an.</p>
      <div class="end" />
    </div></div>
  </div>`;
}

function GuidedRun({ g, bed, onEnd, onQuit }) {
  const steps = g.schritte;
  const [i, setI] = useState(0);
  const [phase, setPhase] = useState('sage'); // sage | stille
  const [paused, setPaused] = useState(false);
  const [showText, setShowText] = useState(local.get('mind:text', true));
  const [elapsed, setElapsed] = useState(0);
  const pausedRef = useRef(false);
  const alive = useRef(true);
  const startAt = useRef(Date.now());
  const pausedFor = useRef(0);
  const est = (s) => s.sage.split(/\s+/).length / 2.3;
  const total = steps.reduce((s, x) => s + est(x) + x.pause, 0);
  useEffect(() => { pausedRef.current = paused; }, [paused]);
  useBed(bed, paused);
  useEffect(() => {
    keepAwake(true);
    alive.current = true;
    const sleep = async (sec) => {
      let left = sec * 1000;
      while (left > 0 && alive.current) {
        await new Promise((r) => setTimeout(r, 200));
        if (!pausedRef.current) left -= 200;
      }
    };
    const waitResume = async () => { while (pausedRef.current && alive.current) await new Promise((r) => setTimeout(r, 200)); };
    (async () => {
      bell(0.8);
      await sleep(3);
      for (let k = 0; k < steps.length && alive.current; k++) {
        setI(k); setPhase('sage');
        await waitResume();
        let ok = await voice.speak(steps[k].sage);
        // Kein Clip (oder abgebrochen durch Pause): Satz lesen lassen bzw. nach der Pause wiederholen.
        while (!ok && alive.current) {
          if (pausedRef.current) { await waitResume(); ok = await voice.speak(steps[k].sage); continue; }
          await sleep(est(steps[k])); ok = true;
        }
        setPhase('stille');
        await sleep(steps[k].pause);
      }
      if (alive.current) { bell(g.themen?.includes('schlaf') ? 0.35 : 0.8); onEnd(Math.round((Date.now() - startAt.current - pausedFor.current) / 1000)); }
    })();
    const iv = setInterval(() => setElapsed((Date.now() - startAt.current - pausedFor.current) / 1000), 500);
    return () => { alive.current = false; clearInterval(iv); voice.stop(); keepAwake(false); };
  }, []);
  const pauseAt = useRef(0);
  const toggle = () => {
    if (!paused) { pauseAt.current = Date.now(); voice.stop(); } else pausedFor.current += Date.now() - pauseAt.current;
    setPaused(!paused);
  };
  return html`<div class="screen guidedRun c-mind">
    <div class="brTop"><button class="ib" onClick=${() => { alive.current = false; voice.stop(); onQuit(); }} aria-label="Beenden"><${Icon} n="close" /></button>
      <span>${g.titel}</span>
      <div class="row"><${BedButton} c=${bed} /><button class="ib" onClick=${() => { setShowText(!showText); local.set('mind:text', !showText); }} aria-label="Text"><${Icon} n=${showText ? 'subtitles' : 'subtitles_off'} /></button></div></div>
    <div class="grStage" onClick=${toggle}>
      <div class=${cls('orb', 'huge', (paused || phase === 'stille') && 'slow')}><i /></div>
      <div class="grText">${paused ? html`<b>Pausiert</b><span>Tippen zum Weitermachen</span>` : showText && phase === 'sage' ? html`<p>${steps[i]?.sage}</p>` : html`<span class="faint">${phase === 'stille' ? 'Stille' : ''}</span>`}</div>
    </div>
    <div class="brBottom"><div class="row" style="justify-content:space-between"><span class="small muted">${mmss(elapsed)}</span><span class="small muted">≈ ${mmss(Math.max(0, total - elapsed))}</span></div>
      <div class="bar mt8"><i style=${{ width: `${Math.min(100, (elapsed / total) * 100)}%` }} /></div></div>
  </div>`;
}

// ── Yoga-Pfad ───────────────────────────────────────────────────────────────

export function YogaPath() {
  const [d] = useData(M);
  if (!d) return html`<div class="screen"><${Top} title="Yoga-Pfad" /><div class="pad"><div class="skel" style="height:400px" /></div></div>`;
  return html`<div class="screen c-mind">
    <${Top} title="Yoga-Pfad" sub="Sechs Stufen — Körper, Atem und Geist wachsen zusammen" />
    <div class="scroll"><div class="pad">
      <p class="small muted" style="line-height:1.55">Jede Stufe bringt neue Haltungen, eine längere Einheit und eine innere Haltung, mit der du übst. Die nächste Stufe öffnet sich, wenn du die aktuelle an mehreren Tagen geübt hast — nicht schneller, denn Beweglichkeit und Ruhe brauchen Wiederholung.</p>
      <div class="path mt16">${d.levels.map((l) => {
        const p = d.path.levels.find((x) => x.stufe === l.stufe);
        const lock = !p?.unlocked;
        const done = p && p.met && l.stufe < d.path.open;
        return html`<button class=${cls('pathStep', lock && 'locked', p?.current && 'cur', done && 'done')} onClick=${() => nav(`achtsam/yoga/${l.stufe}`)}>
          <div class="ps-dot">${done ? html`<${Icon} n="check" />` : lock ? html`<${Icon} n="lock" />` : l.stufe}</div>
          <div class="grow"><div class="row" style="justify-content:space-between"><b>${l.name}</b><span class="small muted">${l.minuten} min</span></div>
            <div class="small muted mt4">${l.untertitel}</div>
            ${p?.current && html`<div class="mt8"><${Bar} value=${Math.min(p.sessions, p.need.einheiten)} max=${p.need.einheiten} /></div><div class="tiny muted mt4">${p.sessions} von ${p.need.einheiten} Einheiten · ${p.days} von ${p.need.tage} Tagen</div>`}
            <div class="ps-imgs">${d.asanas.filter((a) => l.neu.includes(a.id)).slice(0, 6).map((a) => html`<img src=${poseImg(a.api)} alt="" />`)}</div>
          </div></button>`;
      })}</div>
      <div class="end" />
    </div></div>
  </div>`;
}

export function YogaLevel({ n }) {
  const [l, , reload] = useData(`${M}/level/${n}`);
  const [skip, setSkip] = useState(false);
  if (!l) return html`<div class="screen"><${Top} title=${`Stufe ${n}`} /><div class="pad"><div class="skel" style="height:400px" /></div></div>`;
  const p = l.progress;
  const locked = l.stufe > l.open;
  const neu = l.asanas.filter((a) => a.stufe === l.stufe);
  const repeat = l.asanas.filter((a) => a.stufe < l.stufe);
  const doSkip = async () => {
    await api.post(`${M}/unlock`, { stufe: l.stufe + 1 }).catch(() => null);
    setSkip(false); refresh(M); reload(); toast(`Stufe ${l.stufe + 1} ist offen`);
  };
  return html`<div class="screen c-mind">
    <${Top} title=${`Stufe ${l.stufe} · ${l.name}`} sub=${`${l.untertitel} · ${l.minuten} min`} />
    <div class="scroll"><div class="pad">
      <div class="lvlHero"><div class="lh-poses">${neu.slice(0, 4).map((a) => html`<img src=${poseImg(a.api)} alt="" />`)}</div>
        <div class="lh-int"><span class="eyebrow">Intention</span><p>${l.intention}</p></div></div>
      ${locked ? html`<div class="card pad16 mt16 row gap12"><${Icon} n="lock" style="color:rgb(var(--c))" /><div class="small">Diese Stufe öffnet sich, wenn du Stufe ${l.stufe - 1} an genug Tagen geübt hast. Schau dir die Haltungen trotzdem schon an.</div></div>`
        : html`<button class="btn mt16" onClick=${() => nav(`achtsam/flow/${l.stufe}`)}><${Icon} n="play_arrow" fill />Geführte Einheit · ${l.minuten} min</button>
          <p class="tiny faint mt8" style="text-align:center">Rem leitet dich durch Atem, Haltungen und Stille. Zählt für deinen Pfad.</p>`}
      ${p && !locked && html`<div class="card pad16 mt16"><div class="row" style="justify-content:space-between"><div class="h3">Dein Weg in dieser Stufe</div>${p.met && html`<span class="tag c-good">geschafft</span>`}</div>
        <div class="mt12"><${Bar} value=${Math.min(p.sessions, p.need.einheiten)} max=${p.need.einheiten} /></div>
        <div class="small muted mt8">${p.sessions} von ${p.need.einheiten} Einheiten · an ${p.days} von ${p.need.tage} verschiedenen Tagen</div>
        ${p.current && !p.met && l.stufe < 6 && html`<button class="link small mt12" onClick=${() => setSkip(true)}>Ich übe schon länger — nächste Stufe öffnen</button>`}</div>`}
      <div class="sec"><h2 class="h2">Ziel</h2></div><p style="line-height:1.6">${l.ziel}</p>
      <div class="philo mt16"><div class="row gap8"><${Icon} n="auto_stories" /><span class="eyebrow">Yoga-Philosophie</span></div><p class="mt8">${l.philosophie}</p></div>
      ${neu.length > 0 && html`<div class="sec"><h2 class="h2">Neue Haltungen</h2><span class="small muted">${neu.length}</span></div><div class="poseGrid">${neu.map((a) => html`<${PoseTile} a=${a} />`)}</div>`}
      ${repeat.length > 0 && html`<div class="sec"><h2 class="h2">Wiederholung</h2></div><div class="poseGrid small">${repeat.map((a) => html`<${PoseTile} a=${a} />`)}</div>`}
      ${l.videos.length > 0 && html`<div class="sec"><h2 class="h2">Mit Video üben</h2><span class="small muted">zählt auch</span></div><div class="stack10">${l.videos.map((v) => html`<${VideoCard} v=${v} wide />`)}</div>`}
      <div class="end" />
    </div></div>
    ${skip && html`<${Sheet} title="Nächste Stufe öffnen?" onClose=${() => setSkip(false)}><div class="c-mind">
      <p class="small" style="line-height:1.55">Mach das nur, wenn dir die Haltungen dieser Stufe wirklich leicht fallen und du sie sauber hältst. Yoga lebt davon, nichts zu erzwingen.</p>
      <button class="btn mt16" onClick=${doSkip}>Ja, Stufe ${l.stufe + 1} öffnen</button><button class="btn ghost mt8" onClick=${() => setSkip(false)}>Lieber weiter üben</button></div><//>`}
    <${PendingVideo} />
  </div>`;
}

function PoseTile({ a, locked }) {
  return html`<button class=${cls('poseTile', locked && 'locked')} onClick=${() => nav(`achtsam/haltung/${a.id}`)}>
    <div class="pt-img"><img src=${poseImg(a.api)} loading="lazy" alt="" />${locked && html`<span class="pt-lock"><${Icon} n="lock" /></span>`}</div>
    <b>${a.de}</b><span>${a.sanskrit}</span></button>`;
}

export function Poses() {
  const [d] = useData(M);
  const [art, setArt] = useState('');
  if (!d) return html`<div class="screen"><${Top} title="Haltungen" /><div class="pad"><div class="skel" style="height:400px" /></div></div>`;
  const arts = [...new Set(d.asanas.map((a) => a.art))];
  return html`<div class="screen c-mind">
    <${Top} title="Haltungen" sub=${`${d.asanas.length} Asanas · Stufe ${d.path.open} offen`} />
    <div class="scroll"><div class="pad">
      <div class="chips wrap">${[['', 'Alle'], ...arts.map((x) => [x, ART[x] ?? x])].map(([k, l]) => html`<button class=${cls('chip', art === k && 'on')} onClick=${() => setArt(k)}>${l}</button>`)}</div>
      ${d.levels.map((l) => {
        const list = d.asanas.filter((a) => a.stufe === l.stufe && (!art || a.art === art));
        if (!list.length) return null;
        return html`<div class="sec"><h2 class="h2">Stufe ${l.stufe} · ${l.name}</h2>${l.stufe > d.path.open && html`<${Icon} n="lock" style="color:var(--faint)" />`}</div>
          <div class="poseGrid">${list.map((a) => html`<${PoseTile} a=${a} locked=${l.stufe > d.path.open} />`)}</div>`;
      })}
      <p class="tiny faint mt20">Illustrationen: yoga-api (MIT-Lizenz).</p>
      <div class="end" />
    </div></div>
  </div>`;
}

export function AsanaDetail({ id }) {
  const [a] = useData(`${M}/asana/${id}`);
  if (!a) return html`<div class="screen"><${Top} title="Haltung" /><div class="pad"><div class="skel" style="height:400px" /></div></div>`;
  return html`<div class="screen c-mind">
    <${Top} title=${a.de} sub=${`${a.sanskrit} · Stufe ${a.stufe}`} actions=${html`<button class="ib" onClick=${() => voice.speak(a.de)} aria-label="Anhören"><${Icon} n="volume_up" /></button>`} />
    <div class="scroll"><div class="pad">
      <div class="asanaHero"><img src=${poseImg(a.api)} alt="" /></div>
      <div class="row gap6 mt12 wrap"><span class="tag">${ART[a.art] ?? a.art}</span>${a.seiten && html`<span class="tag">beide Seiten</span>`}<span class="tag">${a.halten.start}–${a.halten.ziel} s halten</span></div>
      <p class="mt12" style="line-height:1.6">${a.kurz}</p>
      <div class="sec"><h2 class="h2">So kommst du hinein</h2></div><ol class="steps">${a.schritte.map((s) => html`<li>${s}</li>`)}</ol>
      <div class="card pad16 mt16 breathNote"><${Icon} n="air" /><div><div class="h3">Atem</div><p class="small mt4">${a.atem}</p></div></div>
      <div class="philo mt12"><div class="row gap8"><${Icon} n="self_improvement" /><span class="eyebrow">Achtsamkeit</span></div><p class="mt8">${a.achtsamkeit}</p></div>
      <div class="sec"><h2 class="h2">Typische Fehler</h2></div><ul class="errs">${a.fehler.map((f) => html`<li><${Icon} n="error" />${f}</li>`)}</ul>
      <div class="sec"><h2 class="h2">Wirkung</h2></div><ul class="errs tips">${a.wirkung.map((f) => html`<li><${Icon} n="check_circle" />${f}</li>`)}</ul>
      ${a.erleichterung && html`<div class="card pad16 mt16"><div class="h3">Leichter machen</div><p class="small mt4">${a.erleichterung}</p></div>`}
      ${a.vorsicht && html`<div class="card pad16 mt12 warnCard"><${Icon} n="info" /><p class="small">${a.vorsicht}</p></div>`}
      ${(a.vorstufeInfo || a.weiter?.length > 0) && html`<div class="sec"><h2 class="h2">Weg dorthin und weiter</h2></div>
        <div class="poseGrid small">${a.vorstufeInfo && html`<${PoseTile} a=${a.vorstufeInfo} />`}${a.weiter.map((x) => html`<${PoseTile} a=${x} />`)}</div>`}
      <div class="end" />
    </div></div>
  </div>`;
}

// ── Geführte Yoga-Einheit ───────────────────────────────────────────────────

export function YogaFlow({ n }) {
  const [l] = useData(`${M}/level/${n}`);
  const [d] = useData(M);
  const [mood, setMood] = useState(null);
  const [started, setStarted] = useState(false);
  const [doneSec, setDoneSec] = useState(null);
  const bed = useSoundChoice('yoga', `stufe-${n}`);
  useBedPreviewOff();
  if (!l || !d) return html`<div class="screen"><${Top} title="Yoga" /><div class="pad"><div class="skel" style="height:400px" /></div></div>`;
  if (doneSec != null) return html`<div class="screen c-mind"><${Top} title=${l.name} /><div class="scroll"><div class="pad"><${Done} kind="yoga" ref=${`stufe-${l.stufe}`} seconds=${doneSec} moodBefore=${mood} title=${`Stufe ${l.stufe} · ${l.name}`} /></div></div></div>`;
  if (started) return html`<${FlowRun} l=${l} asanas=${d.asanas} bed=${bed} onEnd=${(s) => { setStarted(false); setDoneSec(s); }} onQuit=${() => setStarted(false)} />`;
  const total = l.flow.reduce((s, f) => s + f.sekunden, 0);
  return html`<div class="screen c-mind">
    <${Top} title=${`Stufe ${l.stufe} · ${l.name}`} sub=${`${min(total)} min · ${l.flow.filter((f) => f.phase === 'asana').length} Haltungen`} />
    <div class="scroll"><div class="pad">
      <div class="lh-int card pad16"><span class="eyebrow">Intention</span><p class="mt4">${l.intention}</p></div>
      <${SoundPick} c=${bed} preview />
      <div class="sec"><h2 class="h2">Ablauf</h2></div>
      <div class="flowList">${l.flow.map((f) => {
        const a = f.asana && d.asanas.find((x) => x.id === f.asana);
        return html`<div class="fl-row">${a ? html`<img src=${poseImg(a.api)} alt="" />` : html`<div class="fl-ph"><${Icon} n=${f.phase === 'savasana' ? 'bedtime' : f.phase === 'stille' ? 'self_improvement' : 'air'} /></div>`}
          <div class="grow"><b>${a ? a.de : PHASE[f.phase]}</b>${f.seite && html`<span class="small muted"> · ${f.seite}</span>`}</div><span class="small muted num">${mmss(f.sekunden)}</span></div>`;
      })}</div>
      <div class="mt20"><${MoodPick} value=${mood} onChange=${setMood} label="Wie fühlst du dich gerade? (optional)" /></div>
      <div class="end" style="height:110px" />
    </div></div>
    <div class="bottombar"><button class="btn" onClick=${() => setStarted(true)}><${Icon} n="play_arrow" fill />Beginnen</button></div>
  </div>`;
}

function FlowRun({ l, asanas, bed, onEnd, onQuit }) {
  const steps = l.flow;
  const [i, setI] = useState(0);
  const [left, setLeft] = useState(steps[0].sekunden);
  const [paused, setPaused] = useState(false);
  const spent = useRef(0);
  useEffect(() => { keepAwake(true); bell(0.8); return () => { keepAwake(false); voice.stop(); }; }, []);
  useBed(bed, paused);
  useEffect(() => {
    // Übergang in Stille oder Endentspannung: eine helle Schale statt nur der Stimme.
    if (i > 0 && (steps[i].phase === 'savasana' || steps[i].phase === 'stille')) bell(0.55, 'hell');
    voice.speak(steps[i].sage);
    if (steps[i].phase === 'asana') haptic();
  }, [i]);
  useEffect(() => {
    if (paused) return undefined;
    const iv = setInterval(() => {
      spent.current += 1;
      setLeft((x) => {
        if (x > 1) return x - 1;
        if (i + 1 >= steps.length) { clearInterval(iv); bell(0.8); onEnd(spent.current); return 0; }
        setI(i + 1);
        return steps[i + 1].sekunden;
      });
    }, 1000);
    return () => clearInterval(iv);
  }, [i, paused]);
  const s = steps[i];
  const a = s.asana && asanas.find((x) => x.id === s.asana);
  const next = steps[i + 1];
  const na = next?.asana && asanas.find((x) => x.id === next.asana);
  const go = (k) => { if (k < 0 || k >= steps.length) return; setI(k); setLeft(steps[k].sekunden); };
  const total = steps.reduce((x, f) => x + f.sekunden, 0);
  const done = steps.slice(0, i).reduce((x, f) => x + f.sekunden, 0) + (s.sekunden - left);
  return html`<div class="screen flowRun c-mind">
    <div class="brTop"><button class="ib" onClick=${() => { voice.stop(); onQuit(); }} aria-label="Beenden"><${Icon} n="close" /></button><span>${PHASE[s.phase]}${s.seite ? ` · ${s.seite}` : ''} · ${mmss(total - done)}</span><${BedButton} c=${bed} /></div>
    <div class="frStage" onClick=${() => { if (!paused) voice.stop(); setPaused(!paused); }}>
      ${a ? html`<div class="frPose"><img src=${poseImg(a.api)} alt="" />${s.seite && html`<span class="frSide">${s.seite}</span>`}</div>` : html`<div class=${cls('orb', 'huge', 'slow')}><i /></div>`}
      <div class="frName"><b>${a ? a.de : PHASE[s.phase]}</b>${a && html`<span>${a.sanskrit}</span>`}</div>
      <p class="frCue">${paused ? 'Pausiert — tippen zum Weitermachen' : s.sage}</p>
    </div>
    <div class="frTimer"><${Ring} size=${86} stroke=${6} value=${s.sekunden - left} max=${s.sekunden}><b class="num" style="font-size:20px">${left}</b><//></div>
    <div class="brBottom">
      <div class="row gap8">
        <button class="ib ring" onClick=${() => go(i - 1)} aria-label="Zurück"><${Icon} n="skip_previous" /></button>
        <div class="grow small muted" style="text-align:center">${next ? html`Danach: <b style="color:var(--text)">${na ? na.de : PHASE[next.phase]}</b>` : 'Gleich geschafft'}</div>
        <button class="ib ring" onClick=${() => go(i + 1)} aria-label="Weiter"><${Icon} n="skip_next" /></button>
      </div>
      <div class="segs mt12">${steps.map((_, k) => html`<i class=${cls(k < i && 'done', k === i && 'cur')} style=${k === i ? { '--p': `${((s.sekunden - left) / s.sekunden) * 100}%` } : null} />`)}</div>
    </div>
  </div>`;
}

// ── Wissen & Affirmationen ──────────────────────────────────────────────────

export function Deep({ id }) {
  const [x] = useData(`${M}/deep/${id}`);
  if (!x) return html`<div class="screen"><${Top} title="Wissen" /><div class="pad"><div class="skel" style="height:400px" /></div></div>`;
  return html`<div class="screen c-mind">
    <${Top} title="" />
    <div class="scroll"><div class="pad article">
      <div class=${cls('artHead', `t-${x.farbe}`)}><span class="eyebrow">${x.minuten} min Lesezeit</span><h1>${x.titel}</h1><p>${x.untertitel}</p></div>
      ${x.abschnitte.map((s) => html`<h2>${s.h}</h2>${s.text.split(/\n\n+/).map((p) => html`<p>${p}</p>`)}`)}
      <div class="end" />
    </div></div>
  </div>`;
}

const AFF_THEMES = [['', 'Alle'], ['selbstwert', 'Selbstwert'], ['ruhe', 'Ruhe'], ['mut', 'Mut'], ['dankbarkeit', 'Dankbarkeit'], ['koerper', 'Körper'], ['fokus', 'Fokus'], ['schlaf', 'Schlaf'], ['heilung', 'Heilung'], ['verbundenheit', 'Verbundenheit']];

export function Affirmations({ theme: t0 }) {
  const [list] = useData(`${M}/affirmations`);
  const [theme, setTheme] = useState(t0 ?? '');
  const [k, setK] = useState(0);
  const shown = useMemo(() => (list ?? []).filter((a) => !theme || a.thema === theme), [list, theme]);
  useEffect(() => setK(0), [theme]);
  if (!list) return html`<div class="screen"><${Top} title="Affirmationen" /><div class="pad"><div class="skel" style="height:300px" /></div></div>`;
  const cur = shown[k % Math.max(1, shown.length)];
  return html`<div class="screen c-mind">
    <${Top} title="Affirmationen" sub="Sätze, die du dir selbst sagen darfst" />
    <div class="scroll"><div class="pad">
      <div class="chips wrap">${AFF_THEMES.map(([v, l]) => html`<button class=${cls('chip', theme === v && 'on')} onClick=${() => setTheme(v)}>${l}</button>`)}</div>
      ${cur && html`<div class="affBig mt16" onClick=${() => voice.speak(cur.text)}>
        <span class="ab-l">Sprich es dir zu</span><div class="aff">${cur.text}</div>
        <div class="row gap8 mt20"><button class="btn sm tint grow" onClick=${(e) => { e.stopPropagation(); voice.speak(cur.text); }}><${Icon} n="volume_up" />Anhören</button>
          <button class="btn sm grow" onClick=${(e) => { e.stopPropagation(); setK(k + 1); }}>Nächste<${Icon} n="arrow_forward" /></button></div></div>`}
      <p class="small muted mt16" style="line-height:1.55">Sprich sie langsam, gern laut, und atme danach einmal bewusst aus. Es geht nicht darum, etwas zu erzwingen, sondern dir freundlich zu begegnen.</p>
      <div class="sec"><h2 class="h2">Alle</h2><span class="small muted">${shown.length}</span></div>
      <div class="affList">${shown.map((a) => html`<button onClick=${() => voice.speak(a.text)}><span>${a.text}</span><${Icon} n="volume_up" /></button>`)}</div>
      <div class="end" />
    </div></div>
  </div>`;
}
