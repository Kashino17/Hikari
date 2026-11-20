// »Ich«: Profil, Ziele, Gewichtsverlauf, Tagesziele (Energie/Makros), Einstellungen — und die Einrichtung beim ersten Start.
import { html, useEffect, useState } from '../../vendor/preact-htm.js';
import { api, voice, exitArea, isNative, reminders, health } from '../platform.js';
import { back, nav, useData, setData, refresh, toast, fmtNum, GOALS, relDay, today } from '../app.js';
import { Icon, Top, Spark, cls, Spinner, Sheet } from '../ui.js';

const GOAL_META = {
  abnehmen: ['trending_down', 'Fett verlieren, Muskeln halten'],
  muskeln: ['fitness_center', 'Kraft und Muskelmasse'],
  zunehmen: ['trending_up', 'Gewicht gezielt aufbauen'],
  halten: ['balance', 'Form und Gewicht halten'],
  fitter: ['bolt', 'Ausdauer, Beweglichkeit, Energie'],
};
const LEVELS = [['Einsteiger', 'Kaum Training in letzter Zeit'], ['Grundfit', 'Ab und zu aktiv'], ['Fit', 'Regelmäßig, z. B. 15 Liegestütze'], ['Sehr fit', 'Klimmzüge, 30+ Liegestütze'], ['Athlet', 'Calisthenics-Erfahrung']];
const ACTIVITY = [['Sitzend', 'Büro, wenig Bewegung'], ['Leicht aktiv', 'Viel zu Fuß'], ['Aktiv', 'Stehender Beruf'], ['Körperlich hart', 'Handwerk, Lager']];
const WD = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'];
const LIMITS = [['knie', 'Knie'], ['schulter', 'Schulter'], ['ruecken', 'Unterer Rücken'], ['handgelenk', 'Handgelenke'], ['huefte', 'Hüfte'], ['nacken', 'Nacken'], ['ellbogen', 'Ellbogen'], ['sprunggelenk', 'Sprunggelenk']];
const FOCUS = [['kraft', 'Kraft'], ['ausdauer', 'Ausdauer'], ['beweglichkeit', 'Beweglichkeit'], ['skills', 'Calisthenics-Skills']];
const DIETS = [['alles', 'Alles'], ['vegetarisch', 'Vegetarisch'], ['vegan', 'Vegan'], ['pescetarisch', 'Pescetarisch']];
const ALLERG = ['Gluten', 'Laktose', 'Ei', 'Nüsse', 'Erdnüsse', 'Soja', 'Fisch', 'Schalentiere', 'Sellerie', 'Senf', 'Sesam'];

export function Me() {
  const [rem, setRem] = useState(() => reminders.get());
  const [bk] = useData('/fitness/backup');
  const [hs, setHs] = useState(null);
  useEffect(() => { if (health.available()) health.status().then(setHs).catch(() => setHs('unavailable')); }, []);
  const [state] = useData('/fitness/state');
  const [hist] = useData('/fitness/history?days=180');
  const [, rerender] = useState(0);
  const [info, setInfo] = useState(null);
  const p = state?.profile;
  const t = state?.targets;
  if (!p) return html`<div class="screen tab"><div class="pad"><div class="skel mt24" style="height:300px" /></div></div>`;
  const pts = (hist?.body ?? []).map((b) => ({ v: b.weight }));
  return html`<div class="screen c-train">
    <div class="scroll">
      <header class="top big"><button class="ib backBig" onClick=${() => back('heute')} aria-label="Zurück"><${Icon} n="arrow_back" /></button><div class="tt"><h1>Ich</h1><div class="t2">${GOALS[p.goal]} · ${LEVELS[p.level - 1][0]}</div></div>
        <button class="ib" onClick=${() => nav('profil')} aria-label="Bearbeiten"><${Icon} n="edit" /></button></header>
      <div class="pad">
        <div class="stats3">
          <div class="stat"><b>${state.totals.sessions}</b><span>Einheiten</span></div>
          <div class="stat"><b>${Math.round(state.totals.seconds / 3600)}</b><span>Stunden</span></div>
          <div class="stat"><b style="color:rgb(var(--amber))">${state.streak}</b><span>Serie</span></div>
        </div>

        <div class="bento mt12">
          <button class="bt wide" onClick=${() => nav('koerper')}>
            <span class="bt-l">Gewicht</span>
            <div class="row" style="align-items:flex-end;gap:14px"><b class="bt-big num">${fmtNum(p.weightKg, 1)}<small> kg</small></b>
              ${pts.length > 1 && html`<div class="grow"><${Spark} points=${pts} goal=${p.targetWeightKg} /></div>`}</div>
            <span class="bt-s">${state.weight ? `zuletzt ${relDay(state.weight.day, today())}` : 'Tippen zum Eintragen'}${p.targetWeightKg ? ` · Ziel ${fmtNum(p.targetWeightKg, 1)} kg` : ''}</span>
          </button>
          <button class="bt c-food" onClick=${() => setInfo('ziele')}>
            <span class="bt-l">Tagesziel</span><b class="bt-big num" style="color:rgb(var(--food))">${fmtNum(t.kcal)}</b><span class="bt-s">kcal · ${t.protein} g Eiweiß</span></button>
          <button class="bt" onClick=${() => nav('profil')}>
            <span class="bt-l">Trainingstage</span>
            <div class="wdots">${WD.map((d, i) => html`<i class=${cls(p.trainingDays.includes(i) && 'on')}>${d[0]}</i>`)}</div>
            <span class="bt-s">${p.sessionMinutes} min je Einheit</span></button>
          <button class="bt" onClick=${() => nav('profil')}>
            <span class="bt-l">Ausrüstung</span><${Icon} n="fitness_center" cls="bt-ic" />
            <span class="bt-s">${p.equipment.length > 1 ? `${p.equipment.filter((e) => e !== 'boden').length} Geräte` : 'Nur Körpergewicht'}</span></button>
          <button class="bt" onClick=${() => nav('profil')}>
            <span class="bt-l">Beschwerden</span><${Icon} n="healing" cls="bt-ic" style=${p.limitations.length ? 'color:rgb(var(--bad))' : ''} />
            <span class="bt-s ell">${p.limitations.length ? p.limitations.map((l) => LIMITS.find((x) => x[0] === l)?.[1] ?? l).join(', ') : 'Keine'}${p.noJumps ? ' · ohne Sprünge' : ''}</span></button>
          <button class="bt c-food" onClick=${() => nav('profil')}>
            <span class="bt-l">Ernährung</span><${Icon} n="eco" cls="bt-ic" style="color:rgb(var(--food))" />
            <span class="bt-s ell">${DIETS.find((d) => d[0] === p.diet)?.[1]}${p.allergies.length ? ` · ohne ${p.allergies.join(', ')}` : ''}</span></button>
          <button class=${cls('bt', voice.on && 'on')} onClick=${() => { voice.set(!voice.on); rerender((x) => x + 1); }}>
            <span class="bt-l">Rems Ansagen</span><${Icon} n=${voice.on ? 'record_voice_over' : 'voice_over_off'} cls="bt-ic" />
            <span class="bt-s">${voice.on ? 'An' : 'Aus'}</span></button>
          <button class="bt c-food" onClick=${() => nav('einkauf')}>
            <span class="bt-l">Einkaufsliste</span><${Icon} n="shopping_cart" cls="bt-ic" style="color:rgb(var(--food))" />
            <span class="bt-s">für deinen Plan</span></button>
          ${reminders.available() && html`<button class=${cls('bt', rem?.enabled && 'on')} onClick=${() => setInfo('erinnerung')}>
            <span class="bt-l">Erinnerungen</span><${Icon} n=${rem?.enabled ? 'notifications_active' : 'notifications_off'} cls="bt-ic" />
            <span class="bt-s">${rem?.enabled ? `Trainingstage ${String(Math.floor(rem.minutes / 60)).padStart(2, '0')}:${String(rem.minutes % 60).padStart(2, '0')}` : 'Aus'}</span></button>`}
          ${health.available() && html`<button class=${cls('bt', hs === 'granted' && 'on')} onClick=${() => setInfo('health')}>
            <span class="bt-l">Gesundheitsdaten</span><${Icon} n="monitor_heart" cls="bt-ic" />
            <span class="bt-s">${hs === 'granted' ? 'Verbunden' : hs === 'partial' ? 'Teilweise' : hs === 'unavailable' ? 'Nicht verfügbar' : 'Health Connect'}</span></button>`}
        </div>
        <button class="link small mt20" onClick=${() => setInfo('ueber')}>Über Daten und Quellen</button>
        ${isNative && html`<button class="btn ghost mt16" onClick=${exitArea}><${Icon} n="logout" />Zurück zu Hikari</button>`}
        <div class="end" />
      </div>
    </div>
    ${info === 'erinnerung' && html`<${ReminderSheet} p=${p} rem=${rem} onSave=${(c) => { reminders.set(c); setRem(c); }} onClose=${() => setInfo(null)} />`}
    ${info === 'health' && html`<${HealthSheet} status=${hs} onStatus=${setHs} onClose=${() => setInfo(null)} />`}
    ${info === 'ziele' && html`<${Sheet} title="Deine Tagesziele" onClose=${() => setInfo(null)}><div class="c-food">
      <div class="row"><div class="grow"><div style="font-size:30px;font-weight:800;font-family:var(--display)" class="num">${fmtNum(t.kcal)} kcal</div></div><div class="small muted" style="text-align:right">Grundumsatz ${fmtNum(t.explain.bmr)}<br />Alltag ×${t.explain.pal} · Training +${t.explain.training}<br />Ziel ${t.explain.delta >= 0 ? '+' : ''}${t.explain.delta}${t.explain.adjust ? html`<br />nachgestellt ${t.explain.adjust > 0 ? '+' : '−'}${Math.abs(t.explain.adjust)}` : ''}${t.day?.delta ? html`<br />heute ${t.day.delta > 0 ? '+' : '−'}${Math.abs(t.day.delta)} (${{ done: 'Training erledigt', planned: 'Trainingstag', missed: 'ausgelassen', rest: 'Ruhetag' }[t.day.kind] ?? 'Tag'})` : ''}</div></div>
      <div class="macroBand mt16"><div><b>${t.protein}</b><span>Eiweiß g</span></div><div><b>${t.carbs}</b><span>KH g</span></div><div><b>${t.fat}</b><span>Fett g</span></div><div><b>${fmtNum(t.water, 1)}</b><span>Wasser l</span></div></div>
      <div class="supp mt16"><div class="eyebrow">Sinnvoll ergänzen (freiwillig)</div>
        ${(p.goal === 'muskeln' || p.goal === 'zunehmen') && html`<p><b>Kreatin-Monohydrat 3–5 g täglich</b> — gut untersucht, mehr Kraft und Muskelzuwachs beim Training. Zeitpunkt egal, jeden Tag.</p>`}
        <p><b>Vitamin D 20 µg (800 IE) von Oktober bis März</b> — in Deutschland bildet die Haut im Winter kaum welches, über Essen kommt wenig.</p>
        <p class="tiny muted">Bei Vorerkrankungen oder Medikamenten vorher ärztlich abklären.</p></div></div><//>`}
    ${info === 'ueber' && html`<${Sheet} title="Daten und Quellen" onClose=${() => setInfo(null)}><p class="small muted" style="line-height:1.6">
      <b style="color:var(--text)">KI</b> · ${state.ai.hasKey ? `Gemini (kostenlos) · heute ${Object.values(state.ai.used ?? {}).reduce((x, y) => x + y, 0)} Anfragen` : 'kein Schlüssel'}<br />
      <b style="color:var(--text)">Rezepte</b> · ${state.recipes.have} von ${state.recipes.total || 790} aufbereitet${state.recipes.running ? ' (läuft)' : ''}<br />
      Videos: Sworkit, Freeletics, ExerciseDB (AscendAPI) · Nährwerte: BLS 4.0 (MRI) · Rezepte: TheMealDB<br />Achtsamkeit: Klangschalen-Aufnahmen von »דג בלי מלח«, Wikimedia Commons (CC BY-SA 4.0) · Yoga-Figuren: yoga-api (MIT) · Klangbetten: YouTube-Kanäle, im Klang-Blatt genannt<br />
      <b style="color:var(--text)">Sicherung</b> · ${bk?.last ? `täglich auf deinem Mac, zuletzt ${relDay(bk.last.day, today())} · ${bk.count} Tage aufbewahrt` : 'täglich auf deinem Mac (die erste läuft beim nächsten Serverstart)'}</p><//>`}
  </div>`;
}

// ── Profil bearbeiten (gleiche Felder wie die Einrichtung, auf einer Seite) ──
export function ProfileEdit() {
  const [state] = useData('/fitness/state');
  const [catalog] = useData('/fitness/catalog');
  const [p, setP] = useState(null);
  const [busy, setBusy] = useState(false);
  const cur = p ?? state?.profile;
  if (!cur || !catalog) return html`<div class="screen"><${Top} title="Profil" /></div>`;
  const up = (patch) => setP({ ...cur, ...patch });
  const save = async () => {
    setBusy(true);
    try {
      const r = await api.post('/fitness/profile', cur);
      setData('/fitness/state', { ...state, profile: r.profile, targets: r.targets });
      reminders.followDays(r.profile.trainingDays);
      refresh('/fitness/state'); refresh('/fitness/food/day'); refresh('/fitness/recipes');
      toast('Gespeichert — der Coach plant ab jetzt damit');
      back('ich');
    } catch { toast('Server nicht erreichbar'); setBusy(false); }
  };
  return html`<div class="screen c-train"><${Top} title="Profil & Ziele" />
    <div class="scroll"><div class="pad"><${ProfileFields} p=${cur} up=${up} catalog=${catalog} all /><div style="height:100px" /></div></div>
    <div class="bottombar"><button class="btn" disabled=${busy} onClick=${save}>${busy ? html`<${Spinner} />` : 'Speichern'}</button></div>
  </div>`;
}

function ProfileFields({ p, up, catalog, part = null, all = false }) {
  const show = (k) => all || part === k;
  const toggle = (arr, v) => (arr.includes(v) ? arr.filter((x) => x !== v) : [...arr, v]);
  return html`<div>
    ${show('ziel') && html`<div class="fbq" style="margin-top:8px"><div class="q">Ziel</div><div class="optGrid">${Object.entries(GOALS).map(([k, l]) => html`<button class=${cls('opt', p.goal === k && 'on')} onClick=${() => up({ goal: k })}><${Icon} n=${GOAL_META[k][0]} /><b>${l}</b><span>${GOAL_META[k][1]}</span></button>`)}</div></div>`}
    ${show('koerper') && html`<div class="fbq"><div class="q">Körper</div>
      <div class="seg"><button class=${cls(p.sex === 'm' && 'on')} onClick=${() => up({ sex: 'm' })}>Mann</button><button class=${cls(p.sex === 'w' && 'on')} onClick=${() => up({ sex: 'w' })}>Frau</button></div>
      <div class="optGrid mt12">
        <div class="field"><label>Geburtsjahr</label><input class="input" inputmode="numeric" value=${p.birthYear} onInput=${(e) => up({ birthYear: Number(e.target.value) || p.birthYear })} /></div>
        <div class="field"><label>Größe (cm)</label><input class="input" inputmode="numeric" value=${p.heightCm} onInput=${(e) => up({ heightCm: Number(e.target.value) || p.heightCm })} /></div>
        <div class="field"><label>Gewicht (kg)</label><input class="input" inputmode="decimal" value=${p.weightKg} onInput=${(e) => up({ weightKg: Number(e.target.value.replace(',', '.')) || p.weightKg })} /></div>
        <div class="field"><label>Zielgewicht (optional)</label><input class="input" inputmode="decimal" value=${p.targetWeightKg ?? ''} onInput=${(e) => up({ targetWeightKg: Number(e.target.value.replace(',', '.')) || null })} /></div>
      </div>
      <div class="q mt20">Alltag</div><div class="optGrid">${ACTIVITY.map(([l, s], i) => html`<button class=${cls('opt', p.activity === i + 1 && 'on')} onClick=${() => up({ activity: i + 1 })}><b>${l}</b><span>${s}</span></button>`)}</div></div>`}
    ${show('level') && html`<div class="fbq"><div class="q">Fitnesslevel</div><div class="list">${LEVELS.map(([l, s], i) => html`<button class="li" onClick=${() => up({ level: i + 1 })}>
      <div class=${cls('iconTile', p.level !== i + 1 && 'soft')} style="width:34px;height:34px;border-radius:9px;font-weight:700">${i + 1}</div><div class="tx"><div class="t1">${l}</div><div class="t2">${s}</div></div>${p.level === i + 1 && html`<${Icon} n="check" cls="accent" />`}</button>`)}</div>
      <div class="q mt20">Schwerpunkte</div><div class="chips wrap">${FOCUS.map(([k, l]) => html`<button class=${cls('chip', p.focus.includes(k) && 'on')} onClick=${() => up({ focus: toggle(p.focus, k) })}>${l}</button>`)}</div></div>`}
    ${show('plan') && html`<div class="fbq"><div class="q">Trainingstage</div><div class="row gap6">${WD.map((d, i) => html`<button class=${cls('chip', p.trainingDays.includes(i) && 'on')} style="flex:1;justify-content:center;padding:0" onClick=${() => up({ trainingDays: toggle(p.trainingDays, i).sort() })}>${d}</button>`)}</div>
      <div class="q mt20">Dauer einer Einheit</div><div class="chips wrap">${[15, 20, 30, 35, 45, 60].map((m) => html`<button class=${cls('chip', p.sessionMinutes === m && 'on')} onClick=${() => up({ sessionMinutes: m })}>${m} min</button>`)}</div></div>`}
    ${show('geraete') && html`<div class="fbq"><div class="q">Ausrüstung</div><div class="chips wrap">${Object.entries(catalog.equipment).filter(([k]) => !['boden', 'wand', 'matte'].includes(k)).map(([k, l]) => html`<button class=${cls('chip', p.equipment.includes(k) && 'on')} onClick=${() => up({ equipment: toggle(p.equipment, k) })}>${l}</button>`)}</div>
      <p class="small muted mt8">Ohne Auswahl trainierst du nur mit Körpergewicht, Boden und Wand.</p>
      <div class="q mt20">Beschwerden (dauerhaft)</div><div class="chips wrap c-bad">${LIMITS.map(([k, l]) => html`<button class=${cls('chip', p.limitations.includes(k) && 'on')} onClick=${() => up({ limitations: toggle(p.limitations, k) })}>${l}</button>`)}</div>
      <button class=${cls('li card mt12')} style="border-radius:12px" onClick=${() => up({ noJumps: !p.noJumps })}><div class="tx"><div class="t1">Ohne Sprünge</div><div class="t2">Leise trainieren, gelenkschonend</div></div><span class=${cls('chip', p.noJumps && 'on')}>${p.noJumps ? 'An' : 'Aus'}</span></button></div>`}
    ${show('essen') && html`<div class="fbq c-food"><div class="q">Ernährungsform</div><div class="chips wrap">${DIETS.map(([k, l]) => html`<button class=${cls('chip', p.diet === k && 'on')} onClick=${() => up({ diet: k })}>${l}</button>`)}</div>
      <div class="q mt20">Allergien & Unverträglichkeiten</div><div class="chips wrap">${ALLERG.map((a) => html`<button class=${cls('chip', p.allergies.includes(a) && 'on')} onClick=${() => up({ allergies: toggle(p.allergies, a) })}>${a}</button>`)}</div>
      <div class="field mt20"><label>Mag ich nicht (mit Komma trennen)</label><input class="input" placeholder="z. B. Pilze, Oliven, Koriander" value=${p.dislikes.join(', ')} onInput=${(e) => up({ dislikes: e.target.value.split(',').map((x) => x.trim()).filter(Boolean) })} /></div>
      <div class="q mt20">Mahlzeiten am Tag</div><div class="chips wrap">${[2, 3, 4, 5].map((n) => html`<button class=${cls('chip', p.mealsPerDay === n && 'on')} onClick=${() => up({ mealsPerDay: n })}>${n}</button>`)}</div>
      <div class="q mt20">Zeit zum Kochen</div><div class="chips wrap">${[15, 30, 45, 60].map((n) => html`<button class=${cls('chip', p.cookingMinutes === n && 'on')} onClick=${() => up({ cookingMinutes: n })}>${n === 60 ? '60+ min' : `bis ${n} min`}</button>`)}</div></div>`}
  </div>`;
}

// ── Einrichtung ─────────────────────────────────────────────────────────────
const STEPS = [
  ['ziel', 'Was willst du erreichen?', 'Dein Coach richtet Training und Ernährung danach aus. Du kannst es jederzeit ändern.'],
  ['koerper', 'Ein paar Eckdaten', 'Daraus berechnen wir deinen Energiebedarf und die Nährstoffziele.'],
  ['level', 'Wie fit bist du gerade?', 'Ehrlich ist besser — der Coach passt sich nach jedem Training an.'],
  ['plan', 'Wann trainierst du?', 'Der Coach plant für jeden dieser Tage eine Einheit.'],
  ['geraete', 'Was hast du zur Verfügung?', 'Nur was du auswählst, kommt im Training vor.'],
  ['essen', 'Und beim Essen?', 'Rezepte und Tagesplan halten sich daran.'],
];

export function Onboarding() {
  const [state] = useData('/fitness/state');
  const [catalog] = useData('/fitness/catalog');
  const [i, setI] = useState(0);
  const [p, setP] = useState(null);
  const [busy, setBusy] = useState(false);
  const cur = p ?? state?.profile;
  if (!cur || !catalog) return html`<div class="onb"><div class="boot"><div class="boot-dot" /></div></div>`;
  const [key, title, lead] = STEPS[i];
  const finish = async () => {
    setBusy(true);
    try {
      const r = await api.post('/fitness/profile', { ...cur, onboarded: true });
      setData('/fitness/state', { ...state, profile: r.profile, targets: r.targets });
      reminders.followDays(r.profile.trainingDays);
      refresh('/fitness/state'); refresh('/fitness/today'); refresh('/fitness/food/day');
      nav('heute', { replace: true });
    } catch { toast('Server nicht erreichbar'); setBusy(false); }
  };
  return html`<div class="onb c-train">
    <div class="prog">${STEPS.map((_, k) => html`<i class=${cls(k <= i && 'on')} />`)}</div>
    <div class="scroll"><div class="pad" style="padding-top:28px">
      ${i === 0 && html`<div class="eyebrow" style="color:rgb(var(--train))">Hikari Fitness</div>`}
      <h1 class="mt8">${title}</h1><p class="lead">${lead}</p>
      <${ProfileFields} p=${cur} up=${(patch) => setP({ ...cur, ...patch })} catalog=${catalog} part=${key} />
      <div style="height:100px" />
    </div></div>
    <div class="bottombar">
      ${i > 0 && html`<button class="btn ghost" style="width:auto;padding:0 18px" onClick=${() => setI(i - 1)}><${Icon} n="arrow_back" /></button>`}
      ${i < STEPS.length - 1 ? html`<button class="btn" onClick=${() => setI(i + 1)}>Weiter</button>` : html`<button class="btn" disabled=${busy} onClick=${finish}>${busy ? html`<${Spinner} />` : 'Los geht’s'}</button>`}
    </div>
  </div>`;
}

/** Erinnerungen an Trainingstagen + abends »Serie retten« (Android-Benachrichtigung). */
function ReminderSheet({ p, rem, onSave, onClose }) {
  // Noch nie eingestellt → beim Öffnen an (wer hier speichert, will erinnert werden).
  const [on, setOn] = useState(rem?.configured ? rem.enabled : true);
  const [min, setMin] = useState(rem?.minutes ?? 18 * 60);
  const [streak, setStreak] = useState(rem?.streak ?? true);
  const times = [7 * 60, 12 * 60, 17 * 60, 18 * 60, 19 * 60, 20 * 60];
  const fmt = (m) => `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
  const save = async () => {
    // Ohne Erlaubnis kämen die Erinnerungen nie an — also vorher fragen.
    const ok = on ? await reminders.permit() : true;
    onSave({ enabled: on, minutes: min, streak, days: p.trainingDays });
    toast(!on ? 'Erinnerungen aus' : ok ? 'Erinnerungen an' : 'Gespeichert — aber Benachrichtigungen sind für Hikari aus. In den Android-Einstellungen erlauben.');
    onClose();
  };
  return html`<${Sheet} title="Erinnerungen" onClose=${onClose}><div class="pad">
    <button class=${cls('toggleRow', on && 'on')} onClick=${() => setOn(!on)}><div class="grow"><b>An Trainingstagen erinnern</b><span>${p.trainingDays.length} Tage pro Woche laut deinem Profil</span></div><i class="tg" /></button>
    ${on && html`<div class="lbl mt16">Uhrzeit</div><div class="chips wrap mt8">${times.map((t) => html`<button class=${cls('chip', min === t && 'on')} onClick=${() => setMin(t)}>${fmt(t)}</button>`)}</div>
      <button class=${cls('toggleRow mt16', streak && 'on')} onClick=${() => setStreak(!streak)}><div class="grow"><b>Abends »Serie retten«</b><span>Um 20:30, wenn du an einem Trainingstag noch nicht trainiert hast</span></div><i class="tg" /></button>`}
    <button class="btn mt20" onClick=${save}>Speichern</button>
  </div><//>`;
}

/** Health Connect: Schritte, Schlaf, Ruhepuls, aktive Kalorien — nur lesen. */
function HealthSheet({ status, onStatus, onClose }) {
  const [busy, setBusy] = useState(false);
  const [days, setDays] = useState(null);
  useEffect(() => { if (status === 'granted' || status === 'partial') health.read(7).then(setDays).catch(() => {}); }, [status]);
  const connect = async () => {
    setBusy(true);
    const st = await health.connect().catch(() => 'unavailable');
    onStatus(st);
    if (st === 'granted' || st === 'partial') { await health.sync(true).catch(() => {}); refresh('/fitness/health'); toast('Verbunden — dein Coach sieht jetzt Schlaf und Ruhepuls'); }
    else if (st === 'update') toast('Bitte Health Connect im Play Store aktualisieren');
    else if (st === 'unavailable') toast('Health Connect ist auf diesem Gerät nicht verfügbar');
    setBusy(false);
  };
  const last = days?.[days.length - 1];
  const sleep = days?.filter((d) => d.sleepMin).slice(-1)[0];
  return html`<${Sheet} title="Gesundheitsdaten" onClose=${onClose}><div class="pad">
    <p class="small muted" style="line-height:1.55">Über Health Connect liest Hikari Schritte, Schlaf, Ruhepuls und aktive Kalorien von deinem Handy oder deiner Uhr (z. B. Samsung Health). Nur lesen — nichts wird geschrieben. Dein Coach plant nach einer kurzen Nacht oder bei erhöhtem Ruhepuls automatisch leichter.</p>
    ${(status === 'granted' || status === 'partial') && html`<div class="hsGrid mt16">
      <div><b class="num">${last?.steps != null ? last.steps.toLocaleString('de-DE') : '–'}</b><span>Schritte heute</span></div>
      <div><b class="num">${sleep?.sleepMin ? `${Math.floor(sleep.sleepMin / 60)}:${String(sleep.sleepMin % 60).padStart(2, '0')}` : '–'}</b><span>Schlaf</span></div>
      <div><b class="num">${last?.restingHr ?? '–'}</b><span>Ruhepuls</span></div>
      <div><b class="num">${last?.activeKcal ?? '–'}</b><span>aktive kcal</span></div></div>`}
    ${(status === 'granted' || status === 'partial') && days && !days.some((x) => x.steps != null || x.sleepMin != null || x.restingHr != null) && html`<p class="small mt12" style="color:#F2B847;line-height:1.5">Verbunden, aber noch keine Daten. In Samsung Health: Einstellungen → Health Connect → dort Samsung Health erlauben, Schritte, Schlaf und Herzfrequenz zu teilen. Danach erscheinen die Werte hier.</p>`}
    <button class="btn mt20" disabled=${busy} onClick=${connect}>${busy ? html`<${Spinner} />` : status === 'granted' ? 'Berechtigungen ändern' : 'Health Connect verbinden'}</button>
  </div><//>`;
}
