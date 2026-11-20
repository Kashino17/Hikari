// Einheit: Vorschau → Player (Videoschleife, Zähler/Timer, Pausen, Ansagen) → Feedback je Übung → Auswertung des Coachs.
import { html, useEffect, useMemo, useRef, useState } from '../../vendor/preact-htm.js';
import { api, beep, keepAwake, local, voice } from '../platform.js';
import { back, nav, useData, setData, refresh, amountText, mmss, INTENSITY, toast, useCatalog } from '../app.js';
import { Icon, Loop, Ring, Sheet, cls, Spinner, Top } from '../ui.js';

const FORMAT = { flow: 'nacheinander', sets: 'Sätze', rounds: 'Runden', intervals: 'Intervalle' };

function useSession(id) {
  const [s, err, reload] = useData(`/fitness/session/${id}`);
  return [s, err, reload];
}

// ── Vorschau ────────────────────────────────────────────────────────────────
export function SessionPreview({ id }) {
  const [s, err, reload] = useSession(id);
  const [swap, setSwap] = useState(null);
  const [why, setWhy] = useState(false);
  useEffect(() => {
    if (!s?.upgrading) return undefined;
    const t = setInterval(reload, 4000);
    return () => clearInterval(t);
  }, [s?.upgrading]);
  if (!s) return html`<div class="screen"><${Top} title="Training" />${err ? html`<div class="empty"><b>Nicht erreichbar</b></div>` : html`<div class="pad"><div class="skel" style="height:260px" /></div>`}</div>`;
  const plan = s.plan;
  const first = plan.blocks.find((b) => b.kind === 'main')?.items[0]?.ex ?? plan.blocks[0].items[0].ex;
  const saved = local.get(`run:${s.id}`, null);
  const done = s.status === 'done';
  return html`<div class="screen c-train">
    <div class="scroll">
      <div class="sessHero">
        <${Loop} media=${s.exercises[first]?.media} eager />
        <button class="ib back" onClick=${() => back()} aria-label="Zurück"><${Icon} n="arrow_back" /></button>
      </div>
      <div class="pad">
        <div class="kicker mt20" style="display:flex;gap:8px;font-size:12.5px;color:rgb(var(--train));font-weight:600;letter-spacing:.3px">
          <span>${INTENSITY[plan.intensity].toUpperCase()}</span><span class="faint">·</span><span>${s.source === 'regeln' ? 'REGELPLAN' : 'KI-COACH'}</span>
        </div>
        <h1 style="font-size:27px;font-weight:700;letter-spacing:-.4px;margin-top:4px">${plan.title}</h1>
        <div class="meta"><span><${Icon} n="schedule" />${plan.minutes} min</span><span><${Icon} n="local_fire_department" />~${plan.kcal} kcal</span><span><${Icon} n="layers" />${plan.blocks.length} Blöcke</span></div>
        ${plan.coach && html`<button class="card pad16 mt16" style="width:100%;text-align:left" onClick=${() => setWhy(true)}>
          <div class="row" style="align-items:flex-start"><div class="iconTile soft" style="width:34px;height:34px;border-radius:10px"><${Icon} n="psychology" style="font-size:19px" /></div>
            <div class="grow"><div class="h3">${plan.coach.headline}</div><p class="small muted mt4" style="line-height:1.45">${plan.coach.message}</p>
              ${plan.coach.why?.length > 0 && html`<div class="small mt8 accent row gap6">Warum dieser Plan <${Icon} n="chevron_right" style="font-size:18px" /></div>`}
              ${s.upgrading && html`<div class="upgrading"><${Spinner} />Coach stimmt den Plan ab …</div>`}
            </div></div>
        </button>`}
        ${plan.blocks.map((b, bi) => html`<div>
          <div class="blockHead"><span class="bt">${b.title}</span><span class="bm">${b.format === 'flow' ? FORMAT.flow : `${b.rounds} ${b.format === 'sets' ? 'Sätze' : 'Runden'}`}${b.restBetweenRounds ? ` · ${b.restBetweenRounds} s Pause` : ''} · ${Math.round((s.blockSeconds?.[bi] ?? 0) / 60)} min</span></div>
          ${b.items.map((it, ii) => {
            const ex = s.exercises[it.ex];
            return html`<div class="exRow">
              <button class="row grow" style="gap:12px;text-align:left" onClick=${() => nav(`uebung/${it.ex}`)}>
                <${Loop} media=${ex?.media} cls="thumb" still />
                <div class="tx"><div class="n">${ex?.name ?? it.ex}</div><div class="a">${amountText(it)}${b.format === 'sets' ? ` · ${b.rounds} Sätze` : ''}${it.rest ? ` · ${it.rest} s Pause` : ''}</div>
                  ${it.note && html`<div class="note">${it.note}</div>`}</div>
              </button>
              ${!done && (b.kind === 'main' || b.kind === 'finisher') && html`<button class="ib sm swap" onClick=${() => setSwap({ bi, ii, ex: it.ex })} aria-label="Tauschen"><${Icon} n="swap_horiz" /></button>`}
            </div>`;
          })}
        </div>`)}
        <div style="height:100px" />
      </div>
    </div>
    ${!done && html`<div class="bottombar">
      <button class="btn" onClick=${async () => { await api.post(`/fitness/session/${s.id}/start`).catch(() => null); nav(`los/${s.id}`, { replace: true }); }}>
        <${Icon} n="play_arrow" fill />${saved ? 'Fortsetzen' : 'Training starten'}
      </button>
    </div>`}
    ${swap && html`<${SwapSheet} s=${s} swap=${swap} onClose=${() => setSwap(null)} onDone=${(ns) => { setData(`/fitness/session/${s.id}`, ns); refresh('/fitness/today'); setSwap(null); }} />`}
    ${why && html`<${Sheet} title="Warum dieser Plan" onClose=${() => setWhy(false)}>
      <ul class="errs tips">${(plan.coach.why ?? []).map((w) => html`<li><${Icon} n="check_circle" />${w}</li>`)}</ul>
      <p class="small muted mt12">${s.source === 'regeln' ? 'Plan nach festen Regeln (Erholung, Ausrüstung, Beschwerden, gelernte Dosis je Übung).' : `Geplant vom KI-Coach (${s.source}) auf Basis deines Feedbacks; Übungen und Mengen werden fest gegengeprüft.`}</p>
    <//>`}
  </div>`;
}

function SwapSheet({ s, swap, onClose, onDone }) {
  const [alts] = useData(`/fitness/alternatives/${swap.ex}`);
  const [busy, setBusy] = useState(false);
  const cur = s.exercises[swap.ex];
  const pick = async (ex) => {
    setBusy(true);
    try { onDone(await api.post(`/fitness/session/${s.id}/swap`, { block: swap.bi, item: swap.ii, ex })); }
    catch { toast('Tauschen ging nicht'); setBusy(false); }
  };
  const groups = ['leichter', 'gleich', 'schwerer'];
  return html`<${Sheet} title="Übung tauschen" onClose=${onClose}>
    <p class="small muted">Statt <b style="color:var(--text)">${cur?.name}</b> — gleiche Bewegung, passend zu deiner Ausrüstung.</p>
    ${!alts ? html`<div class="skel mt12" style="height:120px" />` : alts.items.length === 0 ? html`<p class="mt12 muted">Keine passende Alternative.</p>`
      : groups.map((g) => {
        const list = alts.items.filter((a) => a.relation === g);
        return list.length > 0 && html`<div class="eyebrow mt16">${g}</div><div class="mt8">${list.map((a) => html`<button class="exRow" disabled=${busy} onClick=${() => pick(a.id)}>
          <${Loop} media=${a.media} cls="thumb s" still /><div class="tx"><div class="n">${a.name}</div><div class="a">${a.muscles.slice(0, 2).join(', ')} · Stufe ${a.level}</div></div><${Icon} n="chevron_right" cls="faint" />
        </button>`)}</div>`;
      })}
  <//>`;
}

// ── Ablauf berechnen ────────────────────────────────────────────────────────
export function buildSteps(plan) {
  const steps = [];
  plan.blocks.forEach((b, bi) => {
    const rounds = b.format === 'flow' ? 1 : b.rounds;
    const work = (it, ii, round) => {
      if (it.unit === 'sec' && it.perSide && b.format !== 'intervals') {
        steps.push({ t: 'work', bi, ii, round, rounds, ex: it.ex, amount: it.amount, unit: 'sec', side: 'Links', block: b });
        steps.push({ t: 'work', bi, ii, round, rounds, ex: it.ex, amount: it.amount, unit: 'sec', side: 'Rechts', block: b, second: true });
      } else steps.push({ t: 'work', bi, ii, round, rounds, ex: it.ex, amount: it.amount, unit: it.unit, perSide: it.perSide, note: it.note, block: b });
    };
    if (b.format === 'sets') {
      b.items.forEach((it, ii) => {
        for (let r = 0; r < rounds; r++) {
          work(it, ii, r);
          if (it.rest) steps.push({ t: 'rest', secs: it.rest, bi });
        }
      });
    } else {
      for (let r = 0; r < rounds; r++) {
        b.items.forEach((it, ii) => {
          work(it, ii, r);
          const last = ii === b.items.length - 1;
          if (!last && it.rest) steps.push({ t: 'rest', secs: it.rest, bi, short: b.format === 'flow' });
        });
        if (r < rounds - 1 && (b.restBetweenRounds || b.items.at(-1)?.rest)) steps.push({ t: 'rest', secs: b.restBetweenRounds || b.items.at(-1).rest, bi, roundBreak: true });
      }
    }
    if (bi < plan.blocks.length - 1) steps.push({ t: 'rest', secs: b.kind === 'warmup' ? 30 : 45, bi, blockBreak: true });
  });
  // Pausen am Ende/doppelt entfernen
  while (steps.length && steps.at(-1).t === 'rest') steps.pop();
  return steps;
}

// ── Player ──────────────────────────────────────────────────────────────────
export function Player({ id }) {
  const [s] = useSession(id);
  if (!s) return html`<div class="player"><div class="empty" style="margin-top:40%"><${Spinner} /></div></div>`;
  return html`<${Run} s=${s} />`;
}

function Run({ s }) {
  const steps = useMemo(() => buildSteps(s.plan), [s.id]);
  const saved = local.get(`run:${s.id}`, null);
  const [idx, setIdx] = useState(saved?.idx ?? 0);
  const [elapsed, setElapsed] = useState(saved?.elapsed ?? 0);
  const [paused, setPaused] = useState(false);
  const [left, setLeft] = useState(null);
  const [reps, setReps] = useState(null);
  const [info, setInfo] = useState(false);
  const [quit, setQuit] = useState(false);
  const result = useRef(saved?.result ?? { done: {}, skipped: [] });
  const step = steps[idx];
  const ex = step && step.t === 'work' ? s.exercises[step.ex] : null;
  const nextWork = steps.slice(idx + 1).find((x) => x.t === 'work');

  const finished = useRef(false);
  // Beim Verlassen verstummen — außer nach dem Ende: Rems »Training geschafft!« darf ausklingen.
  useEffect(() => { keepAwake(true); return () => { keepAwake(false); if (!finished.current) voice.stop(); }; }, []);
  useEffect(() => { window.__fitBackGuard = () => { setQuit(true); return true; }; return () => { window.__fitBackGuard = null; }; }, []);
  // Gesamtuhr
  useEffect(() => {
    if (paused) return undefined;
    const t = setInterval(() => setElapsed((e) => e + 1), 1000);
    return () => clearInterval(t);
  }, [paused]);
  // Zwischenstand sichern (App kann jederzeit beendet werden)
  useEffect(() => { local.set(`run:${s.id}`, { idx, elapsed, result: result.current }); }, [idx, elapsed]);

  // Schrittwechsel: Timer/Zähler setzen, ansagen
  useEffect(() => {
    if (!step) return;
    if (step.t === 'rest') {
      setLeft(step.secs);
      if (step.blockBreak) voice.say(`Block ${steps[idx + 1]?.block?.kind ?? 'main'}`);
      else if (step.roundBreak) voice.say('Rundenpause');
      else if (!step.short) voice.say('Pause');
    } else {
      setReps(step.unit === 'reps' ? step.amount : null);
      setLeft(step.unit === 'sec' ? step.amount : null);
      if (step.side === 'Rechts') voice.say('Seite wechseln');
      else {
        const prev = steps[idx - 1];
        const firstOfRound = step.ii === 0 && step.rounds > 1 && step.block.format !== 'sets';
        voice.say(
          idx === 0 && 'Block warmup',
          firstOfRound && (step.round === step.rounds - 1 ? 'Letzte Runde' : step.round > 0 && `Runde ${step.round + 1}`),
          `ex:${step.ex}`,
          voice.amount(step.amount, step.unit),
          step.perSide && 'je Seite',
        );
        void prev;
      }
      beep('go');
    }
  }, [idx]);

  // Countdown
  useEffect(() => {
    if (paused || left == null) return undefined;
    if (left <= 0) { finishStep(); return undefined; }
    const t = setTimeout(() => {
      if (left <= 4 && left > 1) beep('tick');
      if (step?.t === 'rest' && left === 4 && nextWork) voice.say('Gleich', `ex:${nextWork.ex}`);
      if (step?.t === 'work' && step.unit === 'sec' && step.amount >= 30 && !step.side && ['main', 'finisher', 'skill'].includes(step.block.kind)) {
        if (left === Math.round(step.amount / 2) && step.amount >= 40) voice.say('Halbzeit');
        else if (left === 11) voice.say('Noch zehn');
      }
      setLeft((l) => (l == null ? l : l - 1));
    }, 1000);
    return () => clearTimeout(t);
  }, [left, paused]);

  function record(value) {
    if (!step || step.t !== 'work' || step.second) return;
    const k = `${step.bi}:${step.ii}`;
    const arr = result.current.done[k] ?? [];
    arr[step.round] = value;
    result.current.done[k] = arr;
  }
  function finishStep() {
    // Halten: die wirklich gehaltene Zeit zählt (früher aufgehört → weniger), damit der Coach merkt,
    // wenn eine Vorgabe zu viel war.
    if (step?.t === 'work') { record(step.unit === 'reps' ? reps : Math.max(0, step.amount - Math.max(0, left ?? 0))); beep('end'); }
    next();
  }
  function next() {
    if (idx >= steps.length - 1) { done(); return; }
    setIdx(idx + 1);
  }
  function skip() {
    if (step?.t === 'work') {
      const k = `${step.bi}:${step.ii}`;
      if (!result.current.skipped.includes(k)) result.current.skipped.push(k);
    }
    next();
  }
  function done() {
    local.set(`fin:${s.id}`, { elapsed, result: result.current });
    local.set(`run:${s.id}`, null);
    finished.current = true;
    voice.say('Fertig');
    nav(`feedback/${s.id}`, { replace: true });
  }

  if (!step) return null;
  const blockIdx = step.bi;
  const blocks = s.plan.blocks;
  // Fortschritt im aktuellen Block
  const inBlock = steps.filter((x) => x.bi === blockIdx && x.t === 'work');
  const posInBlock = inBlock.indexOf(step) >= 0 ? inBlock.indexOf(step) : inBlock.filter((x) => steps.indexOf(x) < idx).length;
  const pct = inBlock.length ? (posInBlock / inBlock.length) * 100 : 0;

  return html`<div class="player c-train">
    <div class="segs">${blocks.map((b, i) => html`<i class=${cls(i < blockIdx && 'done', i === blockIdx && 'cur')} style=${i === blockIdx ? { '--p': `${pct}%` } : null} />`)}</div>
    <div class="phead">
      <button class="ib" onClick=${() => setQuit(true)} aria-label="Beenden"><${Icon} n="close" /></button>
      <div class="clock">${blocks[blockIdx].title} · ${mmss(elapsed)}</div>
      <button class="ib" onClick=${() => voice.set(!voice.on)} aria-label="Ansagen"><${Icon} n=${voice.on ? 'volume_up' : 'volume_off'} /></button>
    </div>

    ${step.t === 'rest' ? html`<${RestView} step=${step} left=${left} nextStep=${nextWork} s=${s} onAdd=${() => setLeft((l) => l + 15)} onSkip=${next} paused=${paused} onPause=${() => setPaused(!paused)} />` : html`
      <div class="stage">
        <${Loop} media=${ex?.media} eager />
        ${step.rounds > 1 && html`<span class="round">${step.block.format === 'sets' ? 'Satz' : 'Runde'} ${step.round + 1}/${step.rounds}</span>`}
        ${step.side && html`<span class="side">${step.side}</span>`}
      </div>
      <div class="info">
        <button class="exname" style="display:inline-flex;align-items:center;gap:6px" onClick=${() => setInfo(true)}>${ex?.name}<${Icon} n="info" style="font-size:19px;color:var(--muted)" /></button>
        <div class="cue">${step.note ?? ex?.steps?.[2] ?? ''}</div>
        ${step.unit === 'reps' ? html`
          <div class="amount">${reps}<small>${step.perSide ? 'je Seite' : 'Wdh.'}</small></div>
          <div class="adj"><button class="ib sm ring" onClick=${() => setReps(Math.max(0, reps - 1))} aria-label="weniger"><${Icon} n="remove" /></button><span class="small muted" style="align-self:center">geschafft?</span><button class="ib sm ring" onClick=${() => setReps(reps + 1)} aria-label="mehr"><${Icon} n="add" /></button></div>`
        : html`<div class="timerRing"><${Ring} size=${150} stroke=${9} value=${left ?? 0} max=${step.amount}><div class="num" style="font-size:52px;font-weight:700;letter-spacing:-1.5px">${left}</div><div class="tiny muted">Sekunden</div><//></div>`}
      </div>
      ${nextWork && html`<div class="next"><${Loop} media=${s.exercises[nextWork.ex]?.media} cls="thumb s" still /><div class="grow"><div class="l">Danach</div><div class="small ell" style="font-weight:500">${s.exercises[nextWork.ex]?.name} · ${amountText(nextWork)}</div></div></div>`}
      <div class="controls">
        <button class="ib" onClick=${skip} aria-label="Überspringen"><${Icon} n="skip_next" /></button>
        ${step.unit === 'reps'
          ? html`<button class="done" onClick=${finishStep}><${Icon} n="check" />Fertig</button>`
          : html`<button class="done" onClick=${() => setPaused(!paused)}><${Icon} n=${paused ? 'play_arrow' : 'pause'} fill />${paused ? 'Weiter' : 'Pause'}</button>`}
        ${step.unit === 'sec' ? html`<button class="ib" onClick=${finishStep} aria-label="Fertig"><${Icon} n="check" /></button>` : html`<button class="ib" onClick=${() => setPaused(!paused)} aria-label="Pause"><${Icon} n=${paused ? 'play_arrow' : 'pause'} /></button>`}
      </div>`}
    ${info && ex && html`<${Sheet} title=${ex.name} onClose=${() => setInfo(false)}><${ExerciseText} ex=${ex} /><//>`}
    ${quit && html`<${Sheet} title="Training beenden?" onClose=${() => setQuit(false)}>
      <p class="muted small">Dein Fortschritt ist gespeichert.</p>
      <button class="btn mt16" onClick=${done}>Beenden und Feedback geben</button>
      <button class="btn ghost mt8" onClick=${() => setQuit(false)}>Weitertrainieren</button>
      <button class="btn ghost mt8" onClick=${() => back('heute')}>Später fortsetzen</button>
    <//>`}
  </div>`;
}

function RestView({ step, left, nextStep, s, onAdd, onSkip, paused, onPause }) {
  const n = nextStep ? s.exercises[nextStep.ex] : null;
  return html`<div style="flex:1;display:flex;flex-direction:column;align-items:center;min-height:0">
    <div class="eyebrow mt24">${step.blockBreak ? 'Nächster Block' : step.roundBreak ? 'Rundenpause' : 'Pause'}</div>
    <div class="num" style="font-size:96px;font-weight:700;letter-spacing:-3px;line-height:1.05;margin-top:8px">${left}</div>
    <div class="row gap8 mt8"><button class="btn sm ghost" onClick=${onAdd}>+15 s</button><button class="btn sm ghost" onClick=${onPause}>${paused ? 'Weiter' : 'Anhalten'}</button><button class="btn sm ghost" onClick=${onSkip}>Überspringen</button></div>
    ${n && html`<div class="stage" style="width:calc(100% - 24px);margin-top:20px;flex:1"><${Loop} media=${n.media} eager /><span class="round">Als Nächstes</span></div>
      <div class="info" style="padding-bottom:calc(20px + var(--sab))"><div class="exname">${n.name}</div><div class="cue">${amountText(nextStep)}${nextStep.block?.title && step.blockBreak ? ` · ${nextStep.block.title}` : ''}</div></div>`}
  </div>`;
}

export function ExerciseText({ ex }) {
  return html`<div class="c-train">
    <div class="muscles">${ex.muscles.map((m) => html`<span class="tag">${m}</span>`)}${(ex.secondary ?? []).map((m) => html`<span class="tag plain">${m}</span>`)}</div>
    <div class="eyebrow mt16">Ausführung</div>
    <ol class="steps mt8">${ex.steps.map((t) => html`<li>${t}</li>`)}</ol>
    <div class="eyebrow mt16">Häufige Fehler</div>
    <ul class="errs mt8">${ex.mistakes.map((t) => html`<li><${Icon} n="close" />${t}</li>`)}</ul>
    ${ex.tips?.length > 0 && html`<div class="eyebrow mt16">Tipps</div><ul class="errs tips mt8">${ex.tips.map((t) => html`<li><${Icon} n="lightbulb" />${t}</li>`)}</ul>`}
    ${ex.breathing && html`<div class="eyebrow mt16">Atmung</div><p class="mt8" style="font-size:14.5px">${ex.breathing}</p>`}
  </div>`;
}
