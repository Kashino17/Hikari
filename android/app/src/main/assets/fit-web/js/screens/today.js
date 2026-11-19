// »Heute«: Woche, Trainingstag vom Coach, Tagesform, Ernährung und Skill auf einen Blick.
import { html, useEffect, useState } from '../../vendor/preact-htm.js';
import { api } from '../platform.js';
import { nav, useData, setData, refresh, longDate, wdShort, today as todayStr, INTENSITY, toast, useCatalog, fmtNum } from '../app.js';
import { Icon, Loop, Ring, Sheet, cls, Spinner } from '../ui.js';

export function Today() {
  const [state] = useData('/fitness/state');
  const [session, , reload] = useData('/fitness/today');
  const [food] = useData('/fitness/food/day');
  const [skills] = useData('/fitness/skills');
  const catalog = useCatalog();
  const [adapt, setAdapt] = useState(false);
  const day = state?.day ?? todayStr();

  // Plan wird im Hintergrund vom Coach verfeinert → alle paar Sekunden nachsehen.
  useEffect(() => {
    if (!session?.upgrading) return undefined;
    const t = setInterval(() => reload(), 4000);
    return () => clearInterval(t);
  }, [session?.upgrading, session?.id]);

  const isTrainingDay = state?.profile?.trainingDays?.includes((new Date().getDay() + 6) % 7);
  const done = session?.status === 'done';
  return html`<div class="screen tab c-train">
    <div class="scroll">
      <header class="top big">
        <div class="tt"><h1>Heute</h1><div class="t2">${longDate(day)}</div></div>
        ${state?.streak > 0 && html`<div class="tag c-amber" style="height:30px;padding:0 10px;font-size:13.5px"><${Icon} n="local_fire_department" fill style="font-size:18px" />${state.streak}</div>`}
      </header>
      <div class="pad">
        <${Week} state=${state} />
        <div class="mt16">
          ${!session ? html`<div class="skel" style="height:380px" />`
            : done ? html`<${DoneCard} s=${session} />`
            : html`<${Hero} s=${session} restDay=${!isTrainingDay} second=${state?.week?.find((w) => w.day === day)?.done} onAdapt=${() => setAdapt(true)} />`}
        </div>
        <div class="sec"><h2 class="h2">Dein Tag</h2></div>
        <div class="tiles">
          <${FoodTile} food=${food} />
          <${SkillTile} skills=${skills} catalog=${catalog} />
        </div>
        ${state && !state.ai?.hasKey && html`<div class="card pad16 mt16 small muted">Kein Gemini-Schlüssel gefunden — der Coach plant mit festen Regeln, Foto-Tracker und Ernährungs-Coach brauchen den Schlüssel von »Lieder & Szenen«.</div>`}
        <div class="end" />
      </div>
    </div>
    ${adapt && html`<${AdaptSheet} onClose=${() => setAdapt(false)} />`}
  </div>`;
}

function Week({ state }) {
  if (!state) return html`<div class="skel" style="height:58px" />`;
  return html`<div class="week">${state.week.map((w) => {
    const isToday = w.day === state.day;
    const n = Number(w.day.slice(8));
    return html`<div class=${cls('wd', isToday && 'today')}>
      ${wdShort(w.day)}
      <div class=${cls('dot', w.done ? 'done' : w.planned ? 'plan' : 'off')}>${w.done ? html`<${Icon} n="check" style="font-size:19px" />` : n}</div>
    </div>`;
  })}</div>`;
}

function Hero({ s, restDay, second, onAdapt }) {
  const plan = s.plan;
  const mains = plan.blocks.filter((b) => b.kind === 'main' || b.kind === 'finisher').flatMap((b) => b.items.map((i) => i.ex));
  const picks = [...new Set(mains)].slice(0, 3);
  const nEx = new Set(plan.blocks.flatMap((b) => b.items.map((i) => i.ex))).size;
  return html`<div class="hero">
    <button style="display:block;width:100%" onClick=${() => nav(`einheit/${s.id}`)}>
      <div class="media">${picks.map((id, i) => html`<${Loop} media=${s.exercises[id]?.media} still=${i > 0} eager=${i === 0} />`)}</div>
    </button>
    <div class="body">
      <div class="kicker"><${Icon} n="bolt" fill style="font-size:16px" />${second ? 'ZWEITES TRAINING HEUTE' : restDay ? 'RUHETAG · OPTIONAL' : 'DEIN TRAINING HEUTE'}</div>
      <h2>${plan.title}</h2>
      <div class="meta">
        <span><${Icon} n="schedule" />${plan.minutes} min</span>
        <span><${Icon} n="local_fire_department" />~${plan.kcal} kcal</span>
        <span><${Icon} n="speed" />${INTENSITY[plan.intensity]}</span>
        <span><${Icon} n="format_list_bulleted" />${nEx} Übungen</span>
      </div>
      ${plan.coach?.message && html`<div class="coachline"><div class="av"><${Icon} n="psychology" /></div><div>
        <p>${plan.coach.message}</p>
        ${s.upgrading && html`<div class="upgrading"><${Spinner} />Coach stimmt den Plan auf dein Feedback ab …</div>`}
      </div></div>`}
      <div class="row mt16">
        <button class="btn" onClick=${() => nav(`einheit/${s.id}`)}><${Icon} n="play_arrow" fill />Training ansehen</button>
        <button class="ib ring" style="width:52px;height:52px;border-radius:10px" onClick=${onAdapt} aria-label="Tagesform anpassen"><${Icon} n="tune" /></button>
      </div>
    </div>
  </div>`;
}

function DoneCard({ s }) {
  const [hist] = useData('/fitness/history?days=7');
  const [extra, setExtra] = useState(false);
  const rev = hist?.sessions?.find((x) => x.id === s.id)?.review;
  return html`<div class="review">
    <div class="row"><div class="iconTile"><${Icon} n="check" /></div><div class="grow"><div class="eyebrow" style="color:rgb(var(--train))">Erledigt</div><div class="h3">${s.plan.title}</div></div></div>
    <div class="stats3 mt16">
      <div class="stat"><b>${Math.round((s.result?.seconds ?? 0) / 60)}</b><span>Minuten</span></div>
      <div class="stat"><b>${s.plan.kcal}</b><span>kcal</span></div>
      <div class="stat"><b>${['', 'sehr leicht', 'leicht', 'passend', 'schwer', 'sehr schwer'][s.feedback?.overall ?? 3]}</b><span>Gefühl</span></div>
    </div>
    ${rev && html`<div class="grp"><div class="gl"><${Icon} n="psychology" style="font-size:16px" />Coach</div><p class="mt4" style="font-size:14.5px">${rev.headline}</p>
      ${rev.next?.length > 0 && html`<ul>${rev.next.map((n) => html`<li>${n}</li>`)}</ul>`}</div>`}
    ${!rev && html`<button class="btn tint mt16" onClick=${() => nav(`coach/${s.id}`)}><${Icon} n="psychology" />Auswertung des Coachs</button>`}
    <button class="btn mt12" onClick=${() => setExtra(true)}><${Icon} n="add_circle" />Noch ein Training heute</button>
    <button class="btn ghost mt8" onClick=${() => nav('training', { replace: true })}>Nächstes Training ansehen</button>
    ${extra && html`<${ExtraSheet} onClose=${() => setExtra(false)} />`}
  </div>`;
}

function FoodTile({ food }) {
  const t = food?.targets;
  const eaten = food?.totals?.kcal ?? 0;
  const left = t ? Math.max(0, t.kcal - eaten) : 0;
  return html`<button class="tile c-food" onClick=${() => nav('ernaehrung', { replace: true })}>
    <div class="tt"><${Icon} n="restaurant" style="font-size:17px;color:rgb(var(--food))" />Ernährung</div>
    <div class="row gap12">
      <${Ring} size=${62} stroke=${7} value=${eaten} max=${t?.kcal ?? 1}><span class="tiny" style="font-weight:600">${t ? Math.round((eaten / t.kcal) * 100) : 0}%</span><//>
      <div><div class="big num">${fmtNum(left)}</div><div class="tiny muted">kcal übrig</div></div>
    </div>
    <div class="row gap6" style="margin-top:auto">
      <span class="btn sm solid" style="height:34px;flex:1" onClick=${(e) => { e.stopPropagation(); nav('foto'); }}><${Icon} n="photo_camera" style="font-size:18px" />Foto</span>
      <span class="btn sm tint" style="height:34px;width:40px;padding:0" onClick=${(e) => { e.stopPropagation(); nav('barcode'); }} aria-label="Barcode"><${Icon} n="barcode_scanner" style="font-size:19px" /></span>
    </div>
  </button>`;
}

function SkillTile({ skills, catalog }) {
  const st = skills?.skills?.find((s) => s.active && !s.mastered);
  const sk = st && catalog?.skills?.find((s) => s.id === st.id);
  const step = sk?.steps?.[st.step];
  if (!sk) {
    return html`<button class="tile c-skill" onClick=${() => nav('skills', { replace: true })}>
      <div class="tt"><${Icon} n="sports_gymnastics" style="font-size:17px;color:rgb(var(--skill))" />Skills</div>
      <div class="h3" style="line-height:1.3">Handstand, Muscle-up, Front Lever …</div>
      <div class="small muted" style="margin-top:auto">Wähle einen Skill — er wird Teil deines Trainings.</div>
    </button>`;
  }
  return html`<button class="tile c-skill" style=${{ '--sc': sk.color }} onClick=${() => nav(`skill/${sk.id}`)}>
    <div class="tt"><${Icon} n=${sk.icon} style="font-size:17px;color:rgb(var(--skill))" />${sk.name}</div>
    <div class="h3" style="line-height:1.25">${step.name}</div>
    <div class="small muted">Ziel ${step.goal.sets} × ${step.goal.value}${step.goal.type === 'hold' ? ' s' : ''} · Stufe ${st.step + 1}/${sk.steps.length}</div>
    <div class="row gap6" style="margin-top:auto"><span class="btn sm tint" style="height:34px;flex:1">Üben</span></div>
  </button>`;
}

const SORE = [['brust', 'Brust'], ['ruecken', 'Rücken'], ['schulter', 'Schultern'], ['arme', 'Arme'], ['bauch', 'Bauch'], ['beine', 'Beine']];
const WISH = [['', 'Coach entscheidet'], ['ganzkoerper', 'Ganzkörper'], ['druck', 'Drücken'], ['zug', 'Ziehen'], ['beine', 'Beine'], ['core', 'Rumpf'], ['ausdauer', 'Ausdauer'], ['mobilitaet', 'Beweglichkeit']];

function AdaptSheet({ onClose }) {
  const [state] = useData('/fitness/state');
  const [energy, setEnergy] = useState(3);
  const [minutes, setMinutes] = useState(state?.profile?.sessionMinutes ?? 35);
  const [sore, setSore] = useState([]);
  const [wish, setWish] = useState('');
  const [busy, setBusy] = useState(false);
  const go = async () => {
    setBusy(true);
    try {
      const s = await api.post('/fitness/today/adapt', { checkin: { energy, minutes, sore }, ...(wish ? { dayType: wish } : {}) });
      setData('/fitness/today', s);
      refresh('/fitness/state');
      toast(s.source === 'regeln' ? 'Plan angepasst (ohne KI — Coach gerade nicht erreichbar)' : 'Der Coach hat deinen Tag neu geplant');
      onClose();
    } catch {
      toast('Server nicht erreichbar');
      setBusy(false);
    }
  };
  return html`<${Sheet} title="Tagesform" onClose=${busy ? () => {} : onClose}>
    <div class="c-train">
      <div class="fbq" style="margin-top:4px"><div class="q">Wie viel Energie hast du heute?</div>
        <div class="scale">${['Platt', 'Müde', 'Okay', 'Gut', 'Top'].map((l, i) => html`<button class=${cls(energy === i + 1 && 'on')} onClick=${() => setEnergy(i + 1)}><b>${i + 1}</b>${l}</button>`)}</div></div>
      <div class="fbq"><div class="q">Wie viel Zeit hast du?</div>
        <div class="chips wrap">${[15, 20, 30, 45, 60].map((m) => html`<button class=${cls('chip', minutes === m && 'on')} onClick=${() => setMinutes(m)}>${m} min</button>`)}</div></div>
      <div class="fbq"><div class="q">Muskelkater?</div>
        <div class="chips wrap">${SORE.map(([k, l]) => html`<button class=${cls('chip', sore.includes(k) && 'on')} onClick=${() => setSore(sore.includes(k) ? sore.filter((x) => x !== k) : [...sore, k])}>${l}</button>`)}</div></div>
      <div class="fbq"><div class="q">Worauf hast du Lust?</div>
        <div class="chips wrap">${WISH.map(([k, l]) => html`<button class=${cls('chip', wish === k && 'on')} onClick=${() => setWish(k)}>${l}</button>`)}</div></div>
      <button class="btn mt24" disabled=${busy} onClick=${go}>${busy ? html`<${Spinner} />Coach plant neu …` : 'Plan anpassen'}</button>
      ${busy && html`<p class="small muted mt8" style="text-align:center">Dauert meist 20–60 Sekunden.</p>`}
    </div>
  <//>`;
}

const EXTRA_TYPES = [['', 'Coach entscheidet'], ['ganzkoerper', 'Ganzkörper'], ['druck', 'Drücken'], ['zug', 'Ziehen'], ['beine', 'Beine'], ['core', 'Rumpf'], ['ausdauer', 'Ausdauer'], ['mobilitaet', 'Beweglichkeit']];

/** Noch ein Training am selben Tag — der Coach weiß, was heute schon dran war. */
function ExtraSheet({ onClose }) {
  const [type, setType] = useState('');
  const [minutes, setMinutes] = useState(20);
  const [energy, setEnergy] = useState(3);
  const [busy, setBusy] = useState(false);
  const go = async () => {
    setBusy(true);
    try {
      const s = await api.post('/fitness/extra', { minutes, energy, ...(type ? { dayType: type } : {}) });
      setData('/fitness/today', s);
      refresh('/fitness/state');
      onClose();
      nav(`einheit/${s.id}`);
    } catch {
      toast('Server nicht erreichbar');
      setBusy(false);
    }
  };
  return html`<${Sheet} title="Noch ein Training" onClose=${busy ? () => {} : onClose}>
    <div class="c-train">
      <p class="small muted">Der Coach weiß, was du heute schon trainiert hast, und plant andere Schwerpunkte — eher kürzer und ruhiger. Der Plan steht sofort, der Coach stimmt ihn im Hintergrund noch ab.</p>
      <div class="fbq"><div class="q">Worauf hast du Lust?</div>
        <div class="chips wrap">${EXTRA_TYPES.map(([k, l]) => html`<button class=${cls('chip', type === k && 'on')} onClick=${() => setType(k)}>${l}</button>`)}</div></div>
      <div class="fbq"><div class="q">Wie lange?</div>
        <div class="chips wrap">${[10, 15, 20, 30, 45].map((m) => html`<button class=${cls('chip', minutes === m && 'on')} onClick=${() => setMinutes(m)}>${m} min</button>`)}</div></div>
      <div class="fbq"><div class="q">Wie fit fühlst du dich noch?</div>
        <div class="scale">${['Platt', 'Müde', 'Okay', 'Gut', 'Top'].map((l, i) => html`<button class=${cls(energy === i + 1 && 'on')} onClick=${() => setEnergy(i + 1)}><b>${i + 1}</b>${l}</button>`)}</div></div>
      <button class="btn mt24" disabled=${busy} onClick=${go}>${busy ? html`<${Spinner} />Coach plant …` : 'Training planen lassen'}</button>
    </div>
  <//>`;
}
