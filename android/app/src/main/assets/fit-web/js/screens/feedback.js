// Nach dem Training: geführtes Feedback (eine Frage pro Bildschirm) → Seite »Coach arbeitet« mit echtem
// Fortschritt (läuft auf dem Server weiter, die App darf zu) → Auswertung.
import { html, useEffect, useMemo, useRef, useState } from '../../vendor/preact-htm.js';
import { api, local } from '../platform.js';
import { back, nav, useData, refresh, amountText, mmss, toast, useCatalog, relDay, today } from '../app.js';
import { Icon, Loop, cls, Spinner } from '../ui.js';

const OVERALL = [
  ['Viel zu leicht', 'Ich hätte deutlich mehr geschafft'],
  ['Zu leicht', 'Etwas mehr wäre gegangen'],
  ['Genau richtig', 'Gefordert, aber sauber geschafft'],
  ['Zu schwer', 'Nicht alles sauber geschafft'],
  ['Viel zu schwer', 'Musste abbrechen oder viel pausieren'],
];
const FUN = ['Gar nicht', 'Eher nicht', 'Okay', 'Gut', 'Sehr'];
const NOTE_IDEAS = ['Mehr Bauch', 'Pausen zu kurz', 'Pausen zu lang', 'Weniger Sprünge', 'Mehr Skill-Training', 'Kürzere Einheiten'];

export function Feedback({ id }) {
  const [s] = useData(`/fitness/session/${id}`);
  const catalog = useCatalog();
  if (!s || !catalog) return html`<div class="screen"><div class="wizCenter"><${Spinner} /></div></div>`;
  return html`<${Wizard} s=${s} regions=${catalog.regions} />`;
}

function Wizard({ s, regions }) {
  const fin = useMemo(() => local.get(`fin:${s.id}`, null) ?? { elapsed: s.result?.seconds ?? 0, result: { done: {}, skipped: [] } }, [s.id]);
  // Jede Übung des Hauptteils genau einmal (in Trainingsreihenfolge).
  const mains = useMemo(() => [...new Map(s.plan.blocks.filter((b) => b.kind === 'main' || b.kind === 'finisher' || b.kind === 'skill').flatMap((b) => b.items.map((i) => [i.ex, i]))).entries()], [s.id]);
  const steps = ['overall', ...mains.map((_, i) => `ex${i}`), 'pain', 'fun', 'note'];
  const [i, setI] = useState(0);
  const [dir, setDir] = useState(1);
  const [overall, setOverall] = useState(null);
  const [exfb, setExfb] = useState({});
  const [hurt, setHurt] = useState(null);
  const [pain, setPain] = useState({});
  const [fun, setFun] = useState(0);
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const step = steps[i];
  const go = (n) => { setDir(n > i ? 1 : -1); setI(Math.max(0, Math.min(steps.length - 1, n))); };
  const next = () => go(i + 1);
  const auto = (fn) => { fn(); setTimeout(next, 260); };
  useEffect(() => { window.__fitBackGuard = () => { if (i > 0) { go(i - 1); return true; } return false; }; return () => { window.__fitBackGuard = null; }; }, [i]);

  const submit = async () => {
    setBusy(true);
    const feedback = {
      overall: overall ?? 3,
      ...(fun ? { enjoyment: fun } : {}),
      pain: hurt ? Object.entries(pain).map(([region, level]) => ({ region, level })) : [],
      exercises: Object.entries(exfb).map(([ex, f]) => ({ ex, ...f })),
      ...(note.trim() ? { note: note.trim() } : {}),
    };
    try {
      await api.post(`/fitness/session/${s.id}/finish`, { result: { seconds: fin.elapsed, ...fin.result }, feedback });
      local.set(`fin:${s.id}`, null);
      local.set(`coachfin:${s.id}`, { elapsed: fin.elapsed, result: fin.result });
      refresh('/fitness/state'); refresh('/fitness/today');
      nav(`coach/${s.id}`, { replace: true });
    } catch {
      toast('Server nicht erreichbar — bitte gleich nochmal');
      setBusy(false);
    }
  };

  let body;
  if (step === 'overall') {
    body = html`<div class="wizQ"><div class="eyebrow accent">Training geschafft · ${mmss(fin.elapsed)}</div><h1>Wie anstrengend war es insgesamt?</h1></div>
      <div class="bigOpts">${OVERALL.map(([l, d], k) => html`<button class=${cls('bigOpt', overall === k + 1 && 'on')} onClick=${() => auto(() => setOverall(k + 1))}>
        <span class="lvl">${k + 1}</span><span class="grow"><b>${l}</b><span>${d}</span></span></button>`)}</div>`;
  } else if (step.startsWith('ex')) {
    const k = Number(step.slice(2));
    const [exId, it] = mains[k];
    const ex = s.exercises[exId];
    const f = exfb[exId] ?? {};
    const setEx = (patch) => setExfb({ ...exfb, [exId]: { ...f, ...patch } });
    body = html`<div class="wizEx">
      <div class="eyebrow">Übung ${k + 1} von ${mains.length}</div>
      <div class="wizVid"><${Loop} media=${ex?.media} eager /></div>
      <h2>${ex?.name}</h2><div class="small muted">${amountText(it)}</div>
    </div>
    <div class="wizQ small"><h1>Wie war diese Übung?</h1></div>
    <div class="flags">
      <button class=${cls('chip c-amber', f.technique === 1 && 'on')} onClick=${() => setEx({ technique: f.technique === 1 ? undefined : 1 })}><${Icon} n="help" />Technik unsicher</button>
      <button class=${cls('chip c-bad', f.pain && 'on')} onClick=${() => setEx({ pain: !f.pain })}><${Icon} n="healing" />Schmerz</button>
      <button class=${cls('chip c-bad', f.dislike && 'on')} onClick=${() => setEx({ dislike: !f.dislike })}><${Icon} n="block" />Nicht mehr</button>
    </div>
    <div class="triOpts">${[[-1, 'Zu leicht', 'south'], [0, 'Genau richtig', 'check'], [1, 'Zu schwer', 'north']].map(([v, l, ic]) => html`<button class=${cls('triOpt', f.difficulty === v && 'on')} onClick=${() => auto(() => setEx({ difficulty: v }))}><${Icon} n=${ic} />${l}</button>`)}</div>`;
  } else if (step === 'pain') {
    body = hurt !== true
      ? html`<div class="wizQ"><h1>Hattest du Schmerzen oder Beschwerden?</h1><p>Der Coach lässt Übungen, die die Stelle belasten, ein paar Tage weg.</p></div>
        <div class="bigOpts two">
          <button class=${cls('bigOpt', hurt === false && 'on')} onClick=${() => auto(() => { setHurt(false); setPain({}); })}><${Icon} n="sentiment_satisfied" /><span class="grow"><b>Nein, alles gut</b></span></button>
          <button class="bigOpt c-bad" onClick=${() => setHurt(true)}><${Icon} n="healing" /><span class="grow"><b>Ja</b></span></button>
        </div>`
      : html`<div class="wizQ small"><h1>Wo genau — und wie stark?</h1></div>
        <div class="regionGrid c-bad">${Object.entries(regions).map(([r, l]) => html`<button class=${cls('region', pain[r] && 'on')} onClick=${() => { const p = { ...pain }; if (p[r]) delete p[r]; else p[r] = 4; setPain(p); }}>${l}</button>`)}</div>
        ${Object.keys(pain).map((r) => html`<div class="painRow"><div class="small">${regions[r]} <b class="accent c-bad" style="color:rgb(var(--bad))">${pain[r]}/10</b></div>
          <input type="range" min="1" max="10" value=${pain[r]} onInput=${(e) => setPain({ ...pain, [r]: Number(e.target.value) })} class="painSlider" /></div>`)}
        <p class="tiny muted mt12">Bei starken oder anhaltenden Schmerzen bitte ärztlich abklären lassen.</p>`;
  } else if (step === 'fun') {
    body = html`<div class="wizQ"><h1>Hat es dir Spaß gemacht?</h1></div>
      <div class="bigOpts">${FUN.map((l, k) => html`<button class=${cls('bigOpt', fun === k + 1 && 'on')} onClick=${() => auto(() => setFun(k + 1))}><span class="lvl">${k + 1}</span><span class="grow"><b>${l}</b></span></button>`)}</div>`;
  } else {
    body = html`<div class="wizQ small"><h1>Möchtest du dem Coach noch etwas sagen?</h1><p>Freiwillig — alles hilft ihm, dich besser zu planen.</p></div>
      <textarea class="input" style="min-height:120px" placeholder="z. B. »Knie zwickt bei Sprüngen«" value=${note} onInput=${(e) => setNote(e.target.value)} />
      <div class="chips wrap mt12">${NOTE_IDEAS.map((t) => html`<button class="chip" onClick=${() => setNote(note.trim() ? `${note.trim()}, ${t}` : t)}>${t}</button>`)}</div>`;
  }

  const optional = step.startsWith('ex') || step === 'fun' || step === 'note';
  const needsNext = (step === 'pain' && hurt === true) || step === 'note';
  return html`<div class="screen c-train wiz">
    <div class="wizTop">
      <button class="ib" onClick=${() => (i > 0 ? go(i - 1) : null)} style=${i === 0 ? 'visibility:hidden' : ''} aria-label="Zurück"><${Icon} n="arrow_back" /></button>
      <div class="wizProg"><i style=${{ width: `${((i + 1) / steps.length) * 100}%` }} /></div>
      ${optional && step !== 'note' ? html`<button class="skip" onClick=${next}>Weiter</button>` : html`<span style="width:56px" />`}
    </div>
    <div class="scroll"><div class=${cls('wizBody', dir > 0 ? 'fwd' : 'bwd')} key=${step + (hurt ? 'h' : '')}>${body}</div></div>
    ${needsNext && html`<div class="bottombar">
      ${step === 'note'
        ? html`<button class="btn" disabled=${busy} onClick=${submit}>${busy ? html`<${Spinner} />` : note.trim() ? 'Senden' : 'Fertig — ohne Nachricht'}</button>`
        : html`<button class="btn" disabled=${!Object.keys(pain).length} onClick=${next}>Weiter</button>`}
    </div>`}
  </div>`;
}

// ── Coach arbeitet ─────────────────────────────────────────────────────────
const EXPECT = { review: 25000, plan: 75000 };
const LINES = ['Schaue mir jede Übung einzeln an …', 'Vergleiche mit deinen letzten Einheiten …', 'Passe die Mengen an dein Feedback an …', 'Prüfe Erholung und Beschwerden …', 'Stelle dein nächstes Training zusammen …'];

export function CoachWork({ id }) {
  const [job, setJob] = useState(null);
  const [err, setErr] = useState(false);
  const [tick, setTick] = useState(0);
  const [showResult, setShowResult] = useState(false);
  const fin = useMemo(() => local.get(`coachfin:${id}`, null), [id]);
  const [s] = useData(`/fitness/session/${id}`);
  useEffect(() => {
    let live = true;
    const poll = async () => {
      try { const j = await api.get(`/fitness/session/${id}/coach`); if (live) { setJob(j); setErr(false); } } catch { if (live) setErr(true); }
    };
    poll();
    const t = setInterval(poll, 1500);
    const t2 = setInterval(() => setTick((x) => x + 1), 500);
    return () => { live = false; clearInterval(t); clearInterval(t2); };
  }, [id]);
  const reviewDone = job && job.review.state !== 'running';
  const planDone = job && job.plan.state !== 'running';
  useEffect(() => {
    if (reviewDone && planDone) { const t = setTimeout(() => setShowResult(true), 900); return () => clearTimeout(t); }
    return undefined;
  }, [reviewDone, planDone]);
  if (showResult && job) return html`<${Result} job=${job} s=${s} fin=${fin} />`;

  const elapsed = job ? job.elapsed + tick * 0 : 0;
  const frac = (state, exp) => (state && state !== 'running' ? 1 : Math.min(0.92, 1 - Math.exp(-(elapsed / exp) * 1.6)));
  const pct = job ? Math.round(((1 + frac(job.review.state, EXPECT.review) + frac(job.plan.state, EXPECT.plan)) / 3) * 100) : 4;
  const line = LINES[Math.floor((Date.now() / 3200) % LINES.length)];
  const slow = job && elapsed > 120000;
  const rows = [
    ['Feedback gespeichert', 'done'],
    ['Training auswerten', job?.review.state ?? 'running'],
    ['Nächstes Training planen', job?.plan.state ?? 'running'],
  ];
  return html`<div class="screen c-train coachWork">
    <div class="scroll"><div class="pad" style="padding-top:calc(36px + var(--sat))">
      <div class="coachArt" aria-hidden="true">
        <svg viewBox="0 0 200 200" width="200" height="200">
          <circle class="ring r1" cx="100" cy="100" r="46" />
          <circle class="ring r2" cx="100" cy="100" r="64" />
          <circle class="ring r3" cx="100" cy="100" r="82" />
          <g class="orbit o1"><circle cx="100" cy="18" r="5" /></g>
          <g class="orbit o2"><circle cx="100" cy="36" r="4" /></g>
          <g class="orbit o3"><circle cx="100" cy="54" r="3.5" /></g>
          <circle cx="100" cy="100" r="34" class="core" />
        </svg>
        <span class="ms fill coreIcon">psychology</span>
      </div>
      <h1 class="cwTitle">Dein Coach arbeitet</h1>
      <p class="cwLine" key=${line}>${err ? 'Verbindung zum Mac unterbrochen — er arbeitet trotzdem weiter.' : line}</p>
      <div class="cwBar"><i style=${{ width: `${pct}%` }} /></div>
      <div class="cwPct num">${pct} %</div>
      <div class="cwSteps">${rows.map(([l, st]) => html`<div class=${cls('cwStep', st)}>
        <span class="dot">${st === 'running' ? html`<${Spinner} />` : st === 'failed' ? html`<${Icon} n="remove" />` : html`<${Icon} n="check" />`}</span>
        <span class="grow">${l}${st === 'none' ? html`<span class="muted"> — diese Woche kein weiterer Trainingstag</span>` : ''}${st === 'failed' ? html`<span class="muted"> — übernimmt der Regelplan</span>` : ''}</span></div>`)}</div>
      <div class="card pad16 cwInfo row" style="align-items:flex-start">
        <${Icon} n="cloud_done" style="color:rgb(var(--train))" />
        <div class="small" style="line-height:1.5">Das läuft auf deinem Mac. <b>Du kannst die App jetzt schließen</b> — die Auswertung findest du später unter »Heute«, dein nächstes Training ist dann schon geplant.${slow ? ' Die freien KI-Modelle sind gerade ausgelastet, es dauert etwas länger.' : ''}</div>
      </div>
      ${reviewDone && !planDone && html`<button class="btn ghost mt16" onClick=${() => setShowResult(true)}>Auswertung schon ansehen</button>`}
      <button class="btn ghost mt12" onClick=${() => nav('heute', { replace: true })}>Später ansehen</button>
      <div class="end" />
    </div></div>
  </div>`;
}

function Result({ job, s, fin }) {
  const review = job.review.data;
  const done = Object.values(fin?.result?.done ?? s?.result?.done ?? {}).flat().filter((x) => x != null);
  const reps = done.reduce((a, b) => a + (Number(b) || 0), 0);
  const secs = fin?.elapsed ?? s?.result?.seconds ?? 0;
  const p = job.plan;
  return html`<div class="screen c-train">
    <div class="scroll"><div class="pad" style="padding-top:calc(28px + var(--sat))">
      <div class="celebrate" style="padding:8px 0 4px"><${Icon} n="emoji_events" fill /><h1 style="font-size:26px;font-weight:700">Training geschafft</h1><div class="muted">${s?.plan?.title ?? ''}</div></div>
      <div class="stats3 mt16">
        <div class="stat"><b>${mmss(secs)}</b><span>Zeit</span></div>
        <div class="stat"><b>${s?.plan?.kcal ?? '–'}</b><span>kcal</span></div>
        <div class="stat"><b>${reps}</b><span>Wdh./Sek.</span></div>
      </div>
      ${(job.skills ?? []).filter((k) => k.advanced || k.met).map((k) => html`<div class="card pad16 mt16 row c-amber" style="border-color:rgba(var(--amber),.35)"><${Icon} n=${k.advanced ? 'workspace_premium' : 'check_circle'} fill style="color:rgb(var(--amber));font-size:28px" /><div class="grow"><div class="h3">${k.advanced ? `${k.skill}: neue Stufe frei` : `${k.skill}: Ziel erreicht`}</div><div class="small muted">${k.advanced ? 'Zwei Tage geschafft — ab jetzt die nächste Stufe.' : 'Noch einmal an einem anderen Tag, dann ist die Stufe frei.'}</div></div></div>`)}
      ${review ? html`<div class="review mt20">
        <div class="row gap8"><div class="iconTile soft" style="width:32px;height:32px;border-radius:9px"><${Icon} n="psychology" style="font-size:18px" /></div><div class="eyebrow" style="color:rgb(var(--train))">Dein Coach</div></div>
        <h3 class="mt12">${review.headline}</h3>
        ${review.good?.length > 0 && html`<div class="grp c-good"><div class="gl">Gut gelaufen</div><ul>${review.good.map((t) => html`<li>${t}</li>`)}</ul></div>`}
        ${review.watch?.length > 0 && html`<div class="grp c-amber"><div class="gl">Aufgefallen</div><ul>${review.watch.map((t) => html`<li>${t}</li>`)}</ul></div>`}
        ${review.next?.length > 0 && html`<div class="grp c-train"><div class="gl">Das ändere ich für dich</div><ul>${review.next.map((t) => html`<li>${t}</li>`)}</ul></div>`}
      </div>` : html`<div class="card pad16 mt20 small muted">Die KI war gerade nicht erreichbar — dein Feedback ist gespeichert und fließt trotzdem in den nächsten Plan ein.</div>`}
      ${p.state === 'done' && html`<button class="card pad16 mt16 row" style="width:100%;text-align:left" onClick=${() => nav('training', { replace: true })}>
        <div class="iconTile"><${Icon} n="event_available" /></div><div class="grow"><div class="eyebrow">Nächstes Training · ${relDay(p.day, today())}</div><div class="h3 mt4">${p.title}</div><div class="small muted">${p.minutes} min${p.source === 'regeln' ? ' · Regelplan' : ' · vom Coach geplant'}</div></div></button>`}
      ${p.state === 'running' && html`<div class="card pad16 mt16 row"><${Spinner} /><div class="small muted">Dein nächstes Training wird noch geplant — es steht gleich unter »Heute«.</div></div>`}
      <button class="btn mt24" onClick=${() => nav('heute', { replace: true })}>Fertig</button>
      <div class="end" />
    </div></div>
  </div>`;
}
