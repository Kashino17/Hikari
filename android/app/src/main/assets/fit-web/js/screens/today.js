// »Heute«: Woche, Trainingstag vom Coach, Tagesform, Ernährung und Skill auf einen Blick.
import { html, useEffect, useState } from '../../vendor/preact-htm.js';
import { api, health, prefetch, openArea, isNative, local } from '../platform.js';
import { useEffect as useEffectT } from '../../vendor/preact-htm.js';
import { WeekReviewCard } from './plan.js';
import { nav, useData, setData, refresh, longDate, wdShort, today as todayStr, relDay, parseDay, INTENSITY, toast, useCatalog, fmtNum, SLOT_NAMES } from '../app.js';
import { Icon, Loop, Ring, Sheet, Bar, cls, Spinner, ScoreBadge } from '../ui.js';
import { openFoodDay } from './food.js';

const isoD = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const plusDays = (day, n) => { const x = parseDay(day); x.setDate(x.getDate() + n); return isoD(x); };
const mondayOf = (day) => plusDays(day, -((parseDay(day).getDay() + 6) % 7));
const FEEL = ['', 'sehr leicht', 'leicht', 'passend', 'schwer', 'sehr schwer'];
let selDay = null; // bleibt beim Zurückkommen erhalten

export function Today() {
  const [state] = useData('/fitness/state');
  const [session, , reload] = useData('/fitness/today');
  const [food] = useData('/fitness/food/day');
  const [skills] = useData('/fitness/skills');
  const [prog] = useData('/fitness/progress');
  const [hl] = useData('/fitness/health');
  // Gesundheitsdaten im Hintergrund abgleichen (höchstens alle 30 min) — der Coach nutzt Schlaf/Ruhepuls.
  useEffectT(() => { health.sync().then((d) => d && refresh('/fitness/health')).catch(() => {}); }, []);
  // Offline-Training: heutigen Plan und seine Übungsvideos aufs Handy holen.
  useEffectT(() => {
    if (!session?.id) return;
    const media = Object.values(session.exercises ?? {}).flatMap((e) => [e?.media?.image, e?.media?.video, e?.media?.video2])
      .filter((u) => typeof u === 'string').map((u) => u.replace(/^\/api/, '')).filter((u) => u.startsWith('/fitness/media/'));
    prefetch([`/fitness/session/${session.id}`, '/fitness/catalog', ...media]);
  }, [session?.id]);
  const catalog = useCatalog();
  const [adapt, setAdapt] = useState(false);
  const day = state?.day ?? todayStr();
  const [sel, setSel0] = useState(selDay ?? day);
  const [cal, setCal] = useState(false);
  const setSel = (d) => { selDay = d === day ? null : d; setSel0(d); };
  const other = sel !== day;

  // Plan wird im Hintergrund vom Coach verfeinert → alle paar Sekunden nachsehen.
  useEffect(() => {
    if (!session?.upgrading) return undefined;
    const t = setInterval(() => reload(), 4000);
    return () => clearInterval(t);
  }, [session?.upgrading, session?.id]);

  const isTrainingDay = state?.profile?.trainingDays?.includes((new Date().getDay() + 6) % 7);
  // Offline abgeschlossen, Server weiß es noch nicht → trotzdem als erledigt zeigen.
  const off = session && session.status !== 'done' ? local.get(`offdone:${session.id}`, null) : null;
  if (session?.status === 'done' && local.get(`offdone:${session.id}`, null)) local.set(`offdone:${session.id}`, null);
  const done = session?.status === 'done' || !!off;
  const shown = off ? { ...session, status: 'done', result: { seconds: off.seconds }, feedback: { overall: off.overall }, pending: true } : session;
  return html`<div class="screen tab c-train">
    <div class="scroll">
      <header class="top big">
        <div class="tt"><h1>${other ? relDay(sel, day) : 'Heute'}</h1><button class="t2 dateBtn" onClick=${() => setCal(true)}>${longDate(sel)}<${Icon} n="calendar_month" style="font-size:17px" /></button></div>
        ${other ? html`<button class="tag c-train" style="height:32px;padding:0 12px;font-size:13.5px" onClick=${() => setSel(day)}><${Icon} n="today" style="font-size:17px" />Heute</button>`
          : state?.streak > 0 && html`<div class="streakTag"><${Icon} n="local_fire_department" fill />${state.streak}</div>`}
        <button class="avatarBtn" onClick=${() => nav('ich')} aria-label="Ich"><${Icon} n="person" fill /></button>
      </header>
      <div class="pad">
        <${Week} today=${day} sel=${sel} onSel=${setSel} />
        ${other ? html`<${DayView} day=${sel} today=${day} />` : html`<div class="mt16">
          ${!session ? html`<div class="skel" style="height:380px" />`
            : done ? html`<${DoneCard} s=${shown} />`
            : html`<${Hero} s=${session} restDay=${!isTrainingDay} second=${state?.week?.find((w) => w.day === day)?.done} onAdapt=${() => setAdapt(true)} />`}
        </div>
        <${HealthRow} days=${hl?.days} />
        ${state && !other && !isTrainingDay && !done && isNative && html`<button class="restCard mt12" onClick=${() => openArea('mind')}>
          <span class="rc-moon" /><div class="grow"><b>Ruhetag — gönn dir 15 Minuten</b><span>Sanftes Yoga oder eine Atemübung lösen Muskelkater und helfen der Erholung.</span></div><${Icon} n="arrow_forward" /></button>`}
        <${WeekReviewCard} />
        ${!other && html`<${WeighCard} today=${day} />`}
        ${prog && !prog.test.last && html`<button class="assessCard mt12" onClick=${() => nav('test')}>
          <${Icon} n="assignment_turned_in" /><div class="grow"><b>Einstufung: 5 Minuten</b><span>${['', 'Ein kurzer Test', 'Zwei kurze Tests', 'Drei kurze Tests', 'Vier kurze Tests'][prog?.test?.tests?.length ?? 4] ?? 'Kurze Tests'} — danach plant dein Coach genau auf deinem Level.</span></div><${Icon} n="chevron_right" /></button>`}
        <div class="sec"><h2 class="h2">Dein Tag</h2></div>
        <div class="tiles">
          <${FoodTile} food=${food} />
          <${SkillTile} skills=${skills} catalog=${catalog} />
        </div>
        `}
        ${state && !state.ai?.hasKey && html`<div class="card pad16 mt16 small muted">Kein Gemini-Schlüssel gefunden — der Coach plant mit festen Regeln, Foto-Tracker und Ernährungs-Coach brauchen den Schlüssel von »Lieder & Szenen«.</div>`}
        <div class="end" />
      </div>
    </div>
    ${adapt && html`<${AdaptSheet} onClose=${() => setAdapt(false)} />`}
    ${cal && html`<${CalendarSheet} today=${day} sel=${sel} onPick=${(d) => { setSel(d); setCal(false); }} onClose=${() => setCal(false)} />`}
  </div>`;
}

/** Woche zum Blättern: jeder Tag antippbar, auch vergangene Wochen und die kommende. */
function Week({ today, sel, onSel }) {
  const start = mondayOf(sel);
  const end = plusDays(start, 6);
  const [d] = useData(`/fitness/days?from=${start}&to=${end}`, `/fitness/days?from=${start}&to=${end}`, [start]);
  const days = d?.days ?? Array.from({ length: 7 }, (_, i) => ({ day: plusDays(start, i) }));
  return html`<div class="weekNav">
    <button class="ib sm" onClick=${() => onSel(plusDays(sel, -7))} aria-label="Woche zurück"><${Icon} n="chevron_left" /></button>
    <div class="week grow">${days.map((w) => {
      const n = Number(w.day.slice(8));
      const done = w.done > 0;
      return html`<button class=${cls('wd', w.day === today && 'today', w.day === sel && 'sel', w.day > today && 'future')} onClick=${() => onSel(w.day)}>
        ${wdShort(w.day)}
        <div class=${cls('dot', done ? 'done' : w.plan || (w.trainingDay && w.day >= today) ? 'plan' : 'off')}>${done ? html`<${Icon} n="check" style="font-size:19px" />` : n}</div>
        <i class=${cls('meal', w.meals > 0 && 'on')} />
      </button>`;
    })}</div>
    <button class="ib sm" onClick=${() => onSel(plusDays(sel, 7))} aria-label="Woche vor"><${Icon} n="chevron_right" /></button>
  </div>`;
}

const MONTHS_L = ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'];

/** Monatskalender zum Springen — beliebig weit zurück. */
function CalendarSheet({ today, sel, onPick, onClose }) {
  const [m, setM] = useState(() => { const x = parseDay(sel); return new Date(x.getFullYear(), x.getMonth(), 1, 12); });
  const first = isoD(m);
  const last = isoD(new Date(m.getFullYear(), m.getMonth() + 1, 0, 12));
  const [d] = useData(`/fitness/days?from=${first}&to=${last}`, `/fitness/days?from=${first}&to=${last}`, [first]);
  const by = Object.fromEntries((d?.days ?? []).map((x) => [x.day, x]));
  const lead = (m.getDay() + 6) % 7;
  const n = new Date(m.getFullYear(), m.getMonth() + 1, 0).getDate();
  const cells = [...Array(lead).fill(null), ...Array.from({ length: n }, (_, i) => isoD(new Date(m.getFullYear(), m.getMonth(), i + 1, 12)))];
  const go = (k) => setM(new Date(m.getFullYear(), m.getMonth() + k, 1, 12));
  const trained = (d?.days ?? []).filter((x) => x.done > 0).length;
  return html`<${Sheet} title="Kalender" onClose=${onClose}>
    <div class="c-train">
      <div class="pnav"><button class="ib sm" onClick=${() => go(-1)} aria-label="Monat zurück"><${Icon} n="chevron_left" /></button>
        <div class="plabel">${MONTHS_L[m.getMonth()]} ${m.getFullYear()}</div>
        <button class="ib sm" onClick=${() => go(1)} aria-label="Monat vor"><${Icon} n="chevron_right" /></button></div>
      <div class="calGrid mt12">${['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'].map((w) => html`<span class="cw">${w}</span>`)}
        ${cells.map((c) => {
          if (!c) return html`<span />`;
          const x = by[c] ?? {};
          return html`<button class=${cls('cd', c === today && 'today', c === sel && 'sel', x.done > 0 && 'done', !x.done && (x.plan || (x.trainingDay && c >= today)) && 'plan')} onClick=${() => onPick(c)}>
            ${Number(c.slice(8))}<i class=${cls('meal', x.meals > 0 && 'on')} /></button>`;
        })}</div>
      <div class="calLegend mt16"><span><i class="lg done" />Trainiert</span><span><i class="lg plan" />Trainingstag</span><span><i class="lg meal" />Essen eingetragen</span></div>
      <p class="small muted mt8">${trained ? `${trained} Trainingstage in diesem Monat.` : 'In diesem Monat noch kein Training.'}</p>
      ${sel !== today && html`<button class="btn tint mt16" onClick=${() => onPick(today)}><${Icon} n="today" />Zu heute</button>`}
    </div>
  <//>`;
}

/** Ein anderer Tag: was trainiert wurde samt gespeicherter Coach-Auswertung, Ernährung + Auswertung, Skills. */
function DayView({ day, today }) {
  const [d] = useData(`/fitness/day/${day}`, `/fitness/day/${day}`, [day]);
  if (!d) return html`<div class="skel mt16" style="height:320px" />`;
  const past = day < today;
  const done = d.sessions.filter((s) => s.status === 'done');
  const planned = d.sessions.filter((s) => s.status !== 'done');
  const f = d.food;
  const kcal = f.totals.kcal ?? 0;
  return html`<div>
    ${done.map((s) => html`<${PastSession} s=${s} />`)}
    ${planned.map((s) => html`<button class="card pad16 mt16 row" style="width:100%;text-align:left" onClick=${() => nav(`einheit/${s.id}`)}>
      <div class="iconTile"><${Icon} n="event_available" /></div>
      <div class="grow"><div class="eyebrow" style="color:rgb(var(--train))">Geplant</div><div class="h3 mt4">${s.title}</div>
        <div class="small muted">${s.minutes} min · ~${s.kcal} kcal · ${INTENSITY[s.intensity]} · ${s.exercises} Übungen</div>
        ${s.coach && html`<p class="small mt8" style="line-height:1.45">${s.coach}</p>`}</div>
      <${Icon} n="chevron_right" style="color:var(--faint)" /></button>`)}
    ${!d.sessions.length && html`<div class="card pad16 mt16 row">
      <div class="iconTile soft"><${Icon} n=${past ? 'bedtime' : d.trainingDay ? 'event' : 'weekend'} /></div>
      <div class="grow"><div class="h3">${past ? (d.trainingDay ? 'Kein Training' : 'Ruhetag') : d.trainingDay ? 'Trainingstag' : 'Ruhetag'}</div>
        <div class="small muted mt4">${past ? (d.trainingDay ? 'An diesem Tag wurde nicht trainiert.' : 'Erholung gehört dazu.') : d.trainingDay ? 'Der Coach plant dieses Training nach deinem letzten Training — mit deinem Feedback.' : 'Kein Training geplant. Du kannst an dem Tag trotzdem ein freies Training machen.'}</div></div></div>`}

    ${(past || f.meals.length > 0) && html`<div class="sec"><h2 class="h2">Ernährung</h2><button class="small" style="color:rgb(var(--food))" onClick=${() => openFoodDay(day)}>Tagebuch</button></div>
      <div class="card pad16 c-food">
        ${f.meals.length ? html`<div class="row gap12">
            <${Ring} size=${62} stroke=${7} value=${kcal} max=${f.targets.kcal}><span class="tiny" style="font-weight:600">${Math.round((kcal / f.targets.kcal) * 100)}%</span><//>
            <div class="grow"><div class="h3 num">${fmtNum(kcal)} <span class="small muted" style="font-weight:400">/ ${fmtNum(f.targets.kcal)} kcal</span></div>
              <div class="small muted">${fmtNum(f.totals.protein ?? 0)} g Eiweiß · ${f.meals.length} ${f.meals.length === 1 ? 'Mahlzeit' : 'Mahlzeiten'}</div></div></div>
          <div class="mt12">${f.meals.map((m) => html`<div class="row small" style="padding:5px 0"><span class="muted" style="width:86px;flex:none">${SLOT_NAMES[m.meal] ?? m.meal}</span><span class="grow ell">${m.title}</span><span class="num muted">${m.kcal}</span></div>`)}</div>
          ${f.report ? html`<button class="foodRep mt12" onClick=${() => nav(`ernaehrung/coach/${day}`)}>
              <${ScoreBadge} score=${f.report.score} sm />
              <div class="grow"><div class="eyebrow" style="color:rgb(var(--food))">Ernährungs-Coach</div><div class="small mt4" style="line-height:1.4">${f.report.headline}</div></div>
              <${Icon} n="chevron_right" style="color:var(--faint)" /></button>`
            : html`<button class="btn tint mt12" onClick=${() => nav(`ernaehrung/coach/${day}`)}><${Icon} n="psychology" />Auswertung erstellen</button>`}`
          : html`<div class="small muted">Nichts eingetragen.</div>`}
      </div>`}

    ${d.mind?.length > 0 && html`<div class="sec"><h2 class="h2">Achtsamkeit</h2><span class="small muted">${Math.round(d.mind.reduce((a, x) => a + x.seconds, 0) / 60)} min</span></div>
      <div class="card pad16 c-mind">${d.mind.map((x) => html`<div class="row small" style="padding:4px 0"><${Icon} n=${x.kind === 'yoga' ? 'self_improvement' : x.kind === 'atem' ? 'air' : 'spa'} style="font-size:18px;color:rgb(var(--mind))" />
        <span class="grow">${x.kind === 'yoga' ? (x.ref?.startsWith('stufe-') ? `Yoga · Stufe ${x.ref.slice(6)}` : 'Yoga') : x.kind === 'atem' ? 'Atemübung' : 'Meditation'}${x.mood_before && x.mood_after ? ` · Stimmung ${x.mood_before} → ${x.mood_after}` : ''}</span>
        <span class="num muted">${Math.round(x.seconds / 60)} min</span></div>`)}</div>`}
    ${d.skills.length > 0 && html`<div class="sec"><h2 class="h2">Skills</h2></div>
      <div class="card pad16 c-skill">${d.skills.map((k) => html`<div class="row small" style="padding:4px 0"><${Icon} n="sports_gymnastics" style="font-size:18px;color:rgb(var(--skill))" /><span class="grow">${k.skill} · ${k.step}</span><span class="num">${fmtNum(k.value)} ${k.unit}</span></div>`)}</div>`}
    ${d.weight && html`<div class="small muted mt16">Gewicht an diesem Tag: ${fmtNum(d.weight, 1)} kg</div>`}
    <div class="end" />
  </div>`;
}

function PastSession({ s }) {
  const rev = s.review;
  const t = s.doneAt ? new Date(s.doneAt) : null;
  return html`<div class="review mt16">
    <div class="row"><div class="iconTile"><${Icon} n="check" /></div><div class="grow"><div class="eyebrow" style="color:rgb(var(--train))">Trainiert${t ? ` · ${String(t.getHours()).padStart(2, '0')}:${String(t.getMinutes()).padStart(2, '0')} Uhr` : ''}</div><div class="h3">${s.title}</div></div></div>
    <div class="stats3 mt16">
      <div class="stat"><b>${Math.round(s.seconds / 60)}</b><span>Minuten</span></div>
      <div class="stat"><b>${s.kcal}</b><span>kcal</span></div>
      <div class="stat"><b>${FEEL[s.overall ?? 3]}</b><span>Gefühl</span></div>
    </div>
    ${s.pain?.length > 0 && html`<div class="small mt12" style="color:rgb(var(--bad))"><${Icon} n="healing" style="font-size:16px;vertical-align:-3px" /> Schmerzen gemeldet</div>`}
    ${s.note && html`<p class="small muted mt12" style="font-style:italic">»${s.note}«</p>`}
    ${rev ? html`<div class="mt16"><div class="row gap8"><div class="iconTile soft" style="width:30px;height:30px;border-radius:9px"><${Icon} n="psychology" style="font-size:17px" /></div><div class="eyebrow" style="color:rgb(var(--train))">Dein Coach</div></div>
        <h3 class="mt8" style="font-size:16px;line-height:1.35">${rev.headline}</h3>
        ${rev.good?.length > 0 && html`<div class="grp c-good"><div class="gl">Gut gelaufen</div><ul>${rev.good.map((x) => html`<li>${x}</li>`)}</ul></div>`}
        ${rev.watch?.length > 0 && html`<div class="grp c-amber"><div class="gl">Aufgefallen</div><ul>${rev.watch.map((x) => html`<li>${x}</li>`)}</ul></div>`}
        ${rev.next?.length > 0 && html`<div class="grp c-train"><div class="gl">Das hat der Coach geändert</div><ul>${rev.next.map((x) => html`<li>${x}</li>`)}</ul></div>`}</div>`
      : html`<div class="small muted mt12">Zu diesem Training gibt es keine Coach-Auswertung (KI war damals nicht erreichbar).</div>`}
    <button class="btn ghost mt12" onClick=${() => nav(`einheit/${s.id}`)}><${Icon} n="format_list_bulleted" />Übungen ansehen</button>
  </div>`;
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

/** Erinnerung ans Wiegen: ohne 2–3 Messungen pro Woche kann der Coach Kalorien nicht nachstellen. */
function WeighCard({ today }) {
  const [b] = useData('/fitness/body/progress');
  if (!b) return null;
  const last = b.weights?.[b.weights.length - 1]?.day;
  const days = last ? Math.round((parseDay(today) - parseDay(last)) / 86400000) : 99;
  if (days < 4) return null;
  return html`<button class="reviewCard mt12" onClick=${() => nav('koerper')}>
    <${Icon} n="monitor_weight" /><div class="grow"><b>Kurz wiegen</b><span>${last ? `Zuletzt vor ${days} Tagen.` : 'Noch kein Gewicht eingetragen.'} 2–3× pro Woche morgens reicht — dann passt dein Coach die Kalorien an.</span></div><${Icon} n="chevron_right" /></button>`;
}

function DoneCard({ s }) {
  const [hist] = useData('/fitness/history?days=7');
  const [extra, setExtra] = useState(false);
  const rev = hist?.sessions?.find((x) => x.id === s.id)?.review;
  return html`<div class="review">
    <div class="row"><div class="iconTile"><${Icon} n="check" /></div><div class="grow"><div class="eyebrow" style="color:rgb(var(--train))">${s.pending ? 'Erledigt · wird übertragen' : 'Erledigt'}</div><div class="h3">${s.plan.title}</div></div></div>
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

function MindTile() {
  const [m] = useData('/fitness/mind');
  const st = m?.stats;
  const breath = m?.atem?.[0];
  return html`<div class="mindTile c-mind mt12">
    <button class="row gap12" style="width:100%;text-align:left" onClick=${() => nav('achtsam', { replace: true })}>
      <div class="orb sm"><i /></div>
      <div class="grow"><div class="tt" style="color:rgb(var(--mind))">Achtsamkeit</div>
        <div class="small muted mt4">${st?.todayMinutes ? `Heute ${st.todayMinutes} min` : 'Heute noch nichts'}${st?.streak > 1 ? ` · ${st.streak} Tage in Folge` : ''}</div></div>
      <${Icon} n="chevron_right" style="color:var(--faint)" />
    </button>
    ${m?.affirmation && html`<p class="small mt12" style="line-height:1.45">»${m.affirmation.text}«</p>`}
    <div class="row gap6 mt12">
      ${breath && html`<button class="btn sm tint grow" style="height:36px" onClick=${() => nav(`achtsam/atem/${breath.id}`)}><${Icon} n="air" style="font-size:18px" />Atmen</button>`}
      <button class="btn sm tint grow" style="height:36px" onClick=${() => nav('achtsam/zeit/5')}><${Icon} n="self_improvement" style="font-size:18px" />5 min</button>
      <button class="btn sm tint grow" style="height:36px" onClick=${() => nav('achtsam/yoga')}><${Icon} n="route" style="font-size:18px" />Yoga</button>
    </div>
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
  // Nur Skills, die zu deinen Beschwerden und deiner Ausrüstung passen (gesperrte bietet die Kachel nicht an).
  const st = skills?.skills?.find((s) => s.active && !s.mastered && !s.blocked);
  const sk = st && catalog?.skills?.find((s) => s.id === st.id);
  const step = sk?.steps?.[st.step];
  if (!sk) {
    const blockedActive = skills?.skills?.some((s) => s.active && s.blocked);
    return html`<button class="tile c-skill" onClick=${() => nav('skills', { replace: true })}>
      <div class="tt"><${Icon} n="sports_gymnastics" style="font-size:17px;color:rgb(var(--skill))" />Skills</div>
      <div class="h3" style="line-height:1.3">${blockedActive ? 'Dein Skill passt gerade nicht' : 'Handstand, Muscle-up, Front Lever …'}</div>
      <div class="small muted" style="margin-top:auto">${blockedActive ? 'Er belastet deine Beschwerden — wähle einen, der passt (z. B. Pistol Squat).' : 'Wähle einen Skill — er wird Teil deines Trainings.'}</div>
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
  const [sleep, setSleep] = useState(null);
  const [wish, setWish] = useState('');
  const [busy, setBusy] = useState(false);
  const go = async () => {
    setBusy(true);
    try {
      const s = await api.post('/fitness/today/adapt', { checkin: { energy, minutes, sore, ...(sleep ? { sleep } : {}) }, ...(wish ? { dayType: wish } : {}) });
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
      <div class="fbq"><div class="q">Wie hast du geschlafen?</div>
        <div class="chips">${[[1, 'Schlecht'], [2, 'Okay'], [3, 'Gut']].map(([k, l]) => html`<button class=${cls('chip', sleep === k && 'on')} onClick=${() => setSleep(sleep === k ? null : k)}>${l}</button>`)}</div></div>
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

/** Schlaf, Schritte, Ruhepuls von heute (Health Connect) — nur wenn verbunden. */
function HealthRow({ days }) {
  const d = days?.length ? days[days.length - 1] : null;
  if (!d || d.day !== todayStr() || (d.steps == null && d.sleepMin == null && d.restingHr == null)) return null;
  const prev = days.slice(0, -1).map((x) => x.restingHr).filter((x) => x != null);
  const avg = prev.length ? prev.reduce((a, b) => a + b, 0) / prev.length : null;
  const short = d.sleepMin != null && d.sleepMin < 360;
  const high = d.restingHr != null && avg != null && d.restingHr >= avg + 7;
  return html`<div class="healthRow mt12">
    ${d.sleepMin != null && html`<div class=${cls(short && 'warn')}><${Icon} n="bedtime" /><b class="num">${Math.floor(d.sleepMin / 60)}:${String(d.sleepMin % 60).padStart(2, '0')}</b><span>Schlaf</span></div>`}
    ${d.steps != null && html`<div><${Icon} n="directions_walk" /><b class="num">${d.steps.toLocaleString('de-DE')}</b><span>Schritte</span></div>`}
    ${d.restingHr != null && html`<div class=${cls(high && 'warn')}><${Icon} n="favorite" /><b class="num">${Math.round(d.restingHr)}</b><span>Ruhepuls</span></div>`}
    ${(short || high) && html`<p class="hrNote">${short ? 'Kurze Nacht' : 'Ruhepuls erhöht'} — dein Coach plant heute etwas leichter.</p>`}
  </div>`;
}
