// Workouts auf Zeit mit Bestzeiten — feste Runden, so schnell wie sauber möglich.
// Server: /fitness/benchmarks, /fitness/benchmark/:id (benchmark.ts).
import { html, useEffect, useRef, useState } from '../../vendor/preact-htm.js';
import { api, beep, haptic, keepAwake, voice } from '../platform.js';
import { nav, useData, refresh, relDay, today, mmss } from '../app.js';
import { Icon, Top, cls, Loop, Spinner } from '../ui.js';

const MEDAL = { gold: ['Gold', '#F2C14E'], silber: ['Silber', '#C9CDD6'], bronze: ['Bronze', '#D08B57'] };
const Medal = ({ tier, sm }) => (tier ? html`<span class=${cls('medal', sm && 'sm')} style=${{ '--m': MEDAL[tier][1] }}>${MEDAL[tier][0]}</span>` : null);

/** Kartenreihe »Auf Zeit« unter Training · Starten. */
export function BenchRail() {
  const [d] = useData('/fitness/benchmarks');
  if (!d?.benchmarks?.length) return null;
  return html`<div class="sec"><h2 class="h2">Auf Zeit</h2><span class="small muted">Bestzeit schlagen</span></div>
    <div class="rail benchRail">${d.benchmarks.map((b) => html`<button class=${cls('benchCard', !b.machbar && 'off')} onClick=${() => nav(`bestzeit/${b.id}`)}>
      <div class="bc-img">${b.items.slice(0, 3).map((it) => html`<div>${it.exercise && html`<${Loop} media=${it.exercise.media} still />`}</div>`)}</div>
      <div class="bc-tx"><b>${b.name}</b><span>${b.runden} Runden · ${b.items.length} Übungen</span>
        <div class="row" style="justify-content:space-between;align-items:center;margin-top:8px">
          <span class="bc-pb num">${b.best != null ? mmss(b.best) : '–:–'}</span><${Medal} tier=${b.tier} sm /></div></div>
    </button>`)}</div>`;
}

export function BenchDetail({ id }) {
  const [b] = useData(`/fitness/benchmark/${id}`);
  const [run, setRun] = useState(false);
  if (!b) return html`<div class="screen"><${Top} title="Auf Zeit" /><div class="pad"><div class="skel" style="height:420px" /></div></div>`;
  if (run) return html`<${BenchRun} b=${b} onQuit=${() => setRun(false)} />`;
  const t = b.targets;
  return html`<div class="screen c-train"><${Top} title=${b.name} sub=${b.kurz} />
    <div class="scroll"><div class="pad">
      <div class="bdHero">
        <div><span class="lc-k">Bestzeit</span><div class="bd-pb num">${b.best != null ? mmss(b.best) : '–:–'}</div><${Medal} tier=${b.tier} /></div>
        <div class="bd-goals">${['gold', 'silber', 'bronze'].map((k) => html`<div><i style=${{ background: MEDAL[k][1] }} /><span>${MEDAL[k][0]}</span><b class="num">${mmss(t[k])}</b></div>`)}</div>
      </div>
      <div class="sec"><h2 class="h2">${b.runden} Runden</h2><span class="small muted">so schnell wie sauber möglich</span></div>
      <div class="bdList">${b.items.map((it) => html`<button class="bdItem" onClick=${() => nav(`uebung/${it.ex}`)}>
        <div class="bd-img">${it.exercise && html`<${Loop} media=${it.exercise.media} still />`}</div>
        <div class="grow"><b>${it.menge}× ${it.exercise?.name ?? it.ex}${it.exercise?.perSide ? ' je Seite' : ''}</b>${it.statt && html`<span>statt ${it.statt} — passend zu deinen Beschwerden</span>`}</div></button>`)}</div>
      ${b.history?.length > 0 && html`<div class="sec"><h2 class="h2">Deine Zeiten</h2></div>
        <div class="bdHist">${b.history.slice(0, 8).map((h) => html`<div><span>${relDay(h.day, today())}</span><b class="num">${mmss(h.seconds)}</b>${h.seconds === b.best && html`<em>Bestzeit</em>`}</div>`)}</div>`}
      <div class="end" style="height:110px" />
    </div></div>
    <div class="bottombar"><button class="btn" disabled=${!b.machbar} onClick=${() => setRun(true)}><${Icon} n="timer" />${b.best != null ? 'Bestzeit angreifen' : 'Los geht\'s'}</button></div>
  </div>`;
}

function BenchRun({ b, onQuit }) {
  const steps = [];
  for (let r = 0; r < b.runden; r++) b.items.forEach((it, i) => steps.push({ r, i, it }));
  const [phase, setPhase] = useState('count'); // count | run | done
  const [k, setK] = useState(0);
  const [c, setC] = useState(3);
  const [el, setEl] = useState(0);
  const [res, setRes] = useState(null);
  const t0 = useRef(0);
  useEffect(() => { keepAwake(true); return () => keepAwake(false); }, []);
  useEffect(() => {
    if (phase === 'count') {
      let n = 3; beep('tick');
      const iv = setInterval(() => { n--; if (n > 0) { setC(n); beep('tick'); } else { clearInterval(iv); beep('go'); t0.current = Date.now(); setPhase('run'); } }, 1000);
      return () => clearInterval(iv);
    }
    if (phase === 'run') { const iv = setInterval(() => setEl((Date.now() - t0.current) / 1000), 100); return () => clearInterval(iv); }
    return undefined;
  }, [phase]);
  useEffect(() => { if (phase === 'run') voice.say(`ex:${steps[k].it.ex}`, voice.amount(steps[k].it.menge, 'reps')); }, [k, phase]);
  const next = async () => {
    haptic('tick');
    if (k + 1 < steps.length) { if (steps[k + 1].r !== steps[k].r) beep('go'); setK(k + 1); return; }
    const sec = Math.round((Date.now() - t0.current) / 1000);
    beep('end'); haptic('success'); setPhase('done'); setEl(sec);
    try { setRes(await api.post(`/fitness/benchmark/${b.id}`, { seconds: sec })); refresh('/fitness/benchmarks'); refresh(`/fitness/benchmark/${b.id}`); }
    catch (e) { setRes({ seconds: sec, error: e.status === 400 ? 'kurz' : 'offline' }); }
  };
  if (phase === 'done') return html`<div class="screen c-train"><div class="scroll"><div class="pad" style="padding-top:calc(28px + var(--sat))">
    <div class="tr-hero"><span class="lc-k">${b.name}</span><div class="lc-v num" style="font-size:76px">${mmss(el)}</div>
      ${res?.pb && html`<div class="pbBadge">Neue Bestzeit${res.before != null ? ` · ${mmss(res.before - el)} schneller` : ''}</div>`}
      ${res?.tier && html`<div class="mt8"><${Medal} tier=${res.tier} /></div>`}</div>
    ${!res ? html`<div class="row gap12 mt16"><${Spinner} /><span class="muted">Speichere …</span></div>`
      : res.queued ? html`<p class="small muted mt16">Offline gespeichert — die Zeit wird gewertet, sobald dein Server erreichbar ist.</p>`
      : res.error === 'kurz' ? html`<p class="small muted mt16">Zu kurz für eine Wertung — unter 20 Sekunden zählt nicht.</p>`
      : res.error ? html`<p class="small muted mt16">Gerade keine Verbindung — die Zeit ist nicht gespeichert.</p>` : null}
    <button class="btn mt24" onClick=${onQuit}>Fertig</button>
  </div></div></div>`;
  const s = steps[k];
  const nxt = steps[k + 1];
  return html`<div class="screen benchRun c-train">
    <div class="brTop"><button class="ib" onClick=${onQuit} aria-label="Abbrechen"><${Icon} n="close" /></button><span>Runde ${s.r + 1} von ${b.runden}</span><span /></div>
    ${phase === 'count' ? html`<div class="ts-big num" style="margin-top:30vh">${c}</div>` : html`
      <div class="br-clock num">${mmss(el)}</div>
      <div class="br-ex"><div class="ts-media" style="height:200px">${s.it.exercise && html`<${Loop} media=${s.it.exercise.media} />`}</div>
        <div class="br-amt num">${s.it.menge}×</div><b class="br-name">${s.it.exercise?.name ?? s.it.ex}${s.it.exercise?.perSide ? ' je Seite' : ''}</b></div>
      <div class="br-segs">${steps.map((_, i) => html`<i class=${cls(i < k && 'done', i === k && 'cur')} />`)}</div>
      <div class="brBottom"><button class="btn big" onClick=${next}><${Icon} n="check" />${nxt ? 'Geschafft' : 'Ziel!'}</button>
        <p class="tiny muted mt8" style="text-align:center">${nxt ? `Danach: ${nxt.it.menge}× ${nxt.it.exercise?.name ?? ''}` : 'Letzte Übung'}</p></div>`}
  </div>`;
}
