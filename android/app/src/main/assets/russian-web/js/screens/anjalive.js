// Anja live: ein gesprochenes Gespräch. Du sprichst (Sprechtaste), Anja antwortet sofort mit ihrer
// eigenen Stimme. Der Hikari-Server hält dafür die Verbindung zu Gemini Live; nach jedem Zug liefert
// er, was hier zu lesen ist: was verstanden wurde, die strenge Satzprüfung, Übersetzung, Beispiele.
//
// Grundsatz: Was Anja SAGT, ist Gespräch. Ob dein Satz richtig war, entscheidet allein die Prüfung —
// ein richtiger Satz bekommt eine Bestätigung, nie eine Korrektur-Optik.
import { html, cls, Icon, TopBar, IL, Sheet, useState, useEffect, useRef, useMemo, fx, audio } from '../ui.js';
import { progress, back, toast, phraseById } from '../app.js';
import { storage, api, audioContext } from '../platform.js';
import * as Ph from '../domain/phonetics.js';
import * as Gl from '../domain/glossary.js';
import { SCENARIOS, loadChat, supportStage, record, knownWords, ownWords } from './journey.js';
import { mic, playStream, playPcm, stopPlayback, MAX_RECORD_S } from '../livevoice.js';

const WHY = {
  no_key: 'Auf dem Mac ist kein Gemini-Schlüssel hinterlegt.',
  server: 'Der Hikari-Server auf deinem Mac antwortet nicht. Ist der Mac an und erreichbar?',
  live_unavailable: 'Googles Sprachmodell antwortet gerade nicht. Versuch es gleich noch einmal.',
  mic: 'Ohne Mikrofon geht es nicht. Erlaube den Zugriff und versuch es noch einmal.',
  timeout: 'Anja hat nicht rechtzeitig geantwortet. Sag deinen Satz einfach noch einmal.',
  connection_lost: 'Die Verbindung zu Anja ist abgerissen. Sag deinen Satz noch einmal.',
};
const why = (code) => WHY[code] ?? WHY.live_unavailable;

/** Was Anja voraussetzen darf: Wörter aus den Mini-Gesprächen und kurze Sätze, die im Kurs schon sitzen. */
function knownFor(cp) {
  const out = knownWords(cp).map((w) => w.ru);
  const p = progress.get();
  const solid = Object.entries(p.cards).filter(([, c]) => c.box >= 2).sort((a, b) => b[1].box - a[1].box);
  for (const [id] of solid) {
    if (out.length >= 140) break;
    const ph = phraseById(id);
    if (ph && ph.wordCount <= 3) out.push(Ph.plain(ph.ru));
  }
  return [...new Set(out.map((w) => w.trim().toLowerCase()).filter((w) => w && w.length <= 80))].slice(0, 150);
}

const say = (ru) => audio.play(null, { text: Ph.plain(ru), ru, voice: 'p', rate: 0.9, key: `al:${ru}` });

// ── Bausteine ──────────────────────────────────────────────────────────────

function Bars({ on }) {
  return html`<span class=${cls('al-bars', on && 'on')} aria-hidden="true"><i></i><i></i><i></i><i></i></span>`;
}

/** Deine Zeile: was verstanden wurde, darunter das Urteil der Satzprüfung. */
function Mine({ t, extra, onKeep, kept }) {
  const [open, setOpen] = useState(false);
  const c = t.helps?.check;
  return html`<div class=${cls('bub me al-mine', c && !c.ok && 'fix')}>
    <div class="who">DU${!t.spoken ? ' · getippt' : ''}</div>
    ${t.heard ? html`<${IL} ru=${t.heard} size=${18} extra=${extra} />` : html`<div class="sub">${t.done ? 'Ich habe nichts verstanden. Sag es noch einmal, etwas lauter.' : 'Anja hört zu …'}</div>`}
    ${t.heard && t.helpsState === 'pending' && html`<div class="al-verdict wait"><${Icon} n="progress_activity" c="spin" size="15" />Dein Satz wird geprüft …</div>`}
    ${c?.ok && html`<div class="al-verdict ok"><${Icon} n="check_circle" fill size="16" />Genau so.</div>`}
    ${c && !c.ok && html`<div class="al-fix">
      <div class="al-verdict tip"><${Icon} n="lightbulb" fill size="16" />${c.focus}</div>
      ${!open && html`<button class="chip sm mt8" onClick=${() => setOpen(true)}>So wäre es richtig</button>`}
      ${open && html`<div class="al-right"><${IL} ru=${c.correctedRu} size=${17} extra=${extra} />
        ${c.correctedDe && html`<div class="de">${c.correctedDe}</div>`}
        <div class="sub mt4">${c.explanationDe}</div>
        <div class="rowx g8 wrap mt8"><button class="chip sm" onClick=${() => say(c.correctedRu)}><${Icon} n="volume_up" />Anhören</button>
          ${c.correctedDe && html`<button class="chip sm" disabled=${kept} onClick=${() => onKeep({ ru: c.correctedRu, de: c.correctedDe })}><${Icon} n=${kept ? 'bookmark_added' : 'bookmark_add'} />${kept ? 'Im Satzbuch' : 'Merken'}</button>`}</div></div>`}
    </div>`}
    ${t.heard && t.helpsState === 'failed' && html`<div class="al-verdict wait">Die Prüfung war gerade nicht erreichbar.</div>`}
  </div>`;
}

/** Anjas Zeile: erst hören, dann lesen; Deutsch und Wortbedeutungen auf Tippen. */
function Hers({ t, extra, speaking, onReplay, replaying, hideText, onKeep, kept, latest }) {
  const [de, setDe] = useState(false);
  const [shown, setShown] = useState(false);
  const words = useMemo(() => { const m = new Map(extra); for (const w of t.helps?.words ?? []) { const k = Gl.wordKey(w.ru); if (k && !m.has(k)) m.set(k, w.de); } return m; }, [extra, t.helps]);
  const veiled = hideText && latest && !shown && !!t.said;
  return html`<div class=${cls('bub al-hers', speaking && 'on')}>
    <div class="who">ANJA <${Bars} on=${speaking || replaying} /></div>
    ${!t.said && html`<div class="sub">${speaking ? 'Hör zu …' : t.error ? 'Hier fehlt Anjas Antwort.' : 'Anja überlegt …'}</div>`}
    ${veiled && html`<button class="al-veil" onClick=${() => setShown(true)}><${Icon} n="visibility" size="18" />Text zeigen</button>`}
    ${t.said && !veiled && html`<${IL} ru=${t.said} size=${19} gloss=${de} extra=${words} />`}
    ${t.said && !veiled && de && t.helps?.replyDe && html`<div class="de">${t.helps.replyDe}</div>`}
    ${t.said && html`<div class="rowx g8 wrap mt8">
      <button class=${cls('chip sm', replaying && 'on')} disabled=${!onReplay} onClick=${onReplay}><${Icon} n=${replaying ? 'graphic_eq' : 'replay'} />Nochmal hören</button>
      ${!veiled && html`<button class=${cls('chip sm', de && 'on')} disabled=${!t.helps} onClick=${() => setDe(!de)}><${Icon} n="translate" />${t.helpsState === 'pending' ? 'Deutsch kommt …' : 'Deutsch'}</button>`}
      ${de && t.helps?.replyDe && html`<button class="chip sm" disabled=${kept} onClick=${() => onKeep({ ru: t.said, de: t.helps.replyDe })}><${Icon} n=${kept ? 'bookmark_added' : 'bookmark_add'} />${kept ? 'Im Satzbuch' : 'Merken'}</button>`}
    </div>`}
  </div>`;
}

function HelpSheet({ turn, extra, onClose }) {
  const ex = turn?.helps?.examples ?? [];
  return html`<${Sheet} onClose=${onClose} title="Was kann ich sagen?">
    <p class="sub" style="margin-bottom:12px">${ex.length ? 'Zwei Möglichkeiten. Du darfst auch etwas ganz anderes sagen.' : turn?.helpsState === 'pending' ? 'Die Beispiele kommen gleich …' : 'Gerade gibt es keine Beispiele. Sag einfach, was dir einfällt — ein Wort reicht.'}</p>
    ${ex.map((e) => html`<div class="al-ex"><div class="grow"><${IL} ru=${e.ru} size=${18} extra=${extra} /><div class="de">${e.de}</div></div>
      <button class="iconbtn" onClick=${() => say(e.ru)} aria-label="Anhören"><${Icon} n="volume_up" /></button></div>`)}
  <//>`;
}

function TypeSheet({ onSend, onClose }) {
  const [text, setText] = useState('');
  const ok = /[а-яё]/i.test(text);
  return html`<${Sheet} onClose=${onClose} title="Antwort tippen">
    <p class="sub" style="margin-bottom:10px">Wenn du gerade nicht laut sprechen kannst. Anja antwortet trotzdem gesprochen.</p>
    <input class="field" lang="ru" maxlength="300" placeholder="Напиши по-русски …" value=${text} onInput=${(e) => setText(e.target.value)} />
    <button class="btn mt12" disabled=${!ok} onClick=${() => onSend(text.trim())}>Senden</button>
  <//>`;
}

// ── Der Bildschirm ─────────────────────────────────────────────────────────

export function AnjaLive() {
  const [cp, setCpState] = useState(loadChat);
  const [phase, setPhase] = useState('intro');
  const [status, setStatus] = useState('idle'); // idle | connecting | anja | you | rec | sending
  const [turns, setTurnsState] = useState([]);
  const [error, setError] = useState(null);
  const [sheet, setSheet] = useState(null);
  const [avail, setAvail] = useState(null);
  const [replay, setReplay] = useState(null);
  const [kept, setKept] = useState(() => new Set());
  const [hideText, setHideText] = useState(() => storage.get('anja_live_hide') === '1');
  const [more, setMore] = useState(false);
  const cpRef = useRef(cp);
  const turnsRef = useRef([]);
  const sid = useRef(null);
  const seq = useRef(0);
  const pcm = useRef(new Map());
  const rec = useRef(null);
  const micBtn = useRef(null);
  const recLabel = useRef(null);
  const scroller = useRef(null);
  const alive = useRef(true);
  const helped = useRef(false);
  const opening = useRef(false);
  const today = progress.today();

  const setCp = (fn) => { const next = fn(cpRef.current); cpRef.current = next; storage.set('conv_progress', next); setCpState(next); };
  const setTurns = (list) => { turnsRef.current = list; if (alive.current) setTurnsState(list); };
  const patch = (k, p) => setTurns(turnsRef.current.map((t) => (t.k === k ? { ...t, ...p } : t)));
  const scenario = SCENARIOS.find((x) => x.id === cp.settings.scenario) ?? SCENARIOS[0];
  const stage = supportStage(cp);
  const extra = useMemo(() => ownWords(cp, null), [cp.conversationVocabulary, cp.cards]);

  useEffect(() => {
    api.get('/russian/anja/live/status').then((s) => setAvail(s)).catch(() => setAvail({ available: false, reason: 'server' }));
    return () => {
      alive.current = false;
      stopPlayback();
      rec.current?.cancel();
      if (sid.current) api.post('/russian/anja/live/end', { id: sid.current }).catch(() => {});
    };
  }, []);
  useEffect(() => { scroller.current?.scrollTo({ top: scroller.current.scrollHeight, behavior: 'smooth' }); }, [turns.length, status, turns[turns.length - 1]?.said, turns[turns.length - 1]?.helpsState]);

  // Pegel und Dauer der Aufnahme, ohne den Bildschirm neu zu zeichnen.
  useEffect(() => {
    if (status !== 'rec') return undefined;
    let raf = 0;
    const tick = () => {
      const r = rec.current;
      if (r) {
        micBtn.current?.style.setProperty('--lv', r.level().toFixed(2));
        const s = r.seconds();
        if (recLabel.current) recLabel.current.textContent = `${Math.floor(s)} s`;
        if (s >= MAX_RECORD_S - 0.2) { sendRecording(); return; }
      }
      raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [status]);

  const startBody = (lines = []) => ({ stage, scenario: `${scenario.title}: ${scenario.scene} ${scenario.goal}`, interest: cp.settings.interest ?? '', knownWords: knownFor(cpRef.current), lines });
  const linesSoFar = () => turnsRef.current.flatMap((t) => [t.heard && (t.kind === 'audio' || t.kind === 'text') ? { who: 'kadir', ru: t.heard } : null, t.said ? { who: 'anja', ru: t.said } : null]).filter(Boolean).slice(-40);

  /** Belohnt einen beantworteten Zug — einmal, sobald das Urteil der Prüfung da ist. */
  const score = (k) => {
    const t = turnsRef.current.find((x) => x.k === k);
    if (!t || t.scored || !t.heard || (t.kind !== 'audio' && t.kind !== 'text')) return;
    const c = t.helps?.check;
    const clear = c?.ok === true;
    patch(k, { scored: true });
    progress.apply({ xp: clear ? 6 : 3, graded: {}, spokenOk: clear && t.spoken ? 1 : 0 });
    if (c) setCp((x) => record(x, { id: `live${t.uid}`, day: today, stage, scenario: scenario.id, answer: t.heard, clear, independent: !t.helped, spoken: t.spoken }));
    if (clear) fx.right();
  };

  /** Ein Zug von Anja: Ton abspielen, sobald er kommt; Mitschrift und Hilfen nachtragen. */
  const runTurn = async (n, base) => {
    const k = ++seq.current;
    const id = sid.current;
    const uid = `${Date.now().toString(36)}${k}`;
    setTurns([...turnsRef.current, { k, n, uid, kind: base.kind, heard: base.heard ?? '', said: '', helps: null, helpsState: 'none', done: false, error: null, spoken: base.kind === 'audio', helped: !!base.helped }]);
    setStatus('anja');
    let ended = false;
    let done = false;
    let settled = false;
    // Genau einmal und nur aus »Anja spricht« heraus: Ein später Rückruf darf eine laufende Aufnahme nie zurücksetzen.
    const settle = () => {
      if (settled || !ended || !done) return;
      settled = true;
      if (alive.current && seq.current === k) setStatus((s) => (s === 'anja' ? 'you' : s));
    };
    playStream(`/api/russian/anja/live/audio/${id}/${n}`, { onEnd: () => { ended = true; settle(); } })
      .then((all) => {
        if (all.length) pcm.current.set(k, all);
        // Meldet der Browser das Ende der Wiedergabe nicht (Ton gesperrt, Gerät stumm), geht es trotzdem weiter.
        setTimeout(() => { ended = true; settle(); }, (all.length / 24000) * 1000 + 1500);
      })
      .catch(() => { ended = true; settle(); });
    try {
      let t = await api.get(`/russian/anja/live/turn/${id}/${n}?wait=done`);
      if (!t.done && !t.error) t = await api.get(`/russian/anja/live/turn/${id}/${n}?wait=done`);
      if (t.error || !t.done) throw Object.assign(new Error(t.error ?? 'timeout'), { code: t.error ?? 'timeout' });
      patch(k, { heard: t.heard || base.heard || '', said: t.said, done: true, helpsState: t.helpsState, helps: t.helps });
      done = true; settle();
      if (t.helpsState === 'pending') {
        const h = await api.get(`/russian/anja/live/turn/${id}/${n}?wait=helps`);
        patch(k, { helps: h.helps, helpsState: h.helpsState === 'pending' ? 'failed' : h.helpsState });
      }
      score(k);
    } catch (e) {
      stopPlayback();
      patch(k, { done: true, error: e.code ?? 'failed' });
      if (alive.current) { setError(why(e.code ?? e.data?.error)); if (seq.current === k) setStatus((s) => (s === 'anja' ? 'you' : s)); }
    }
  };

  const fail = (e) => {
    const code = e?.data?.error ?? (e?.status ? 'live_unavailable' : 'server');
    setError(why(code));
    setStatus(sid.current ? 'you' : 'idle');
    if (!sid.current) setPhase('intro');
  };

  const begin = async (lines = []) => {
    audioContext(); // Ton im Tipp freischalten — sonst bleibt Anjas erste Antwort auf dem Handy stumm
    setError(null); setPhase('talk'); setStatus('connecting');
    try {
      const s = await api.post('/russian/anja/live/start', startBody(lines));
      sid.current = s.id;
      await runTurn(s.turn, { kind: 'start' });
    } catch (e) { fail(e); }
  };

  /** Schickt einen Zug; war das Gespräch auf dem Server schon weg, wird es mit dem Verlauf neu geöffnet. */
  const sendTurn = async (body, base) => {
    setError(null); setStatus('sending'); stopPlayback();
    const mark = { ...base, helped: helped.current };
    helped.current = false;
    try {
      const r = await api.post('/russian/anja/live/turn', { id: sid.current, ...body });
      await runTurn(r.turn, mark);
    } catch (e) {
      if (e?.data?.error === 'unknown_session' || e?.status === 404) {
        try {
          const s = await api.post('/russian/anja/live/start', startBody(linesSoFar()));
          sid.current = s.id;
          toast('Die Verbindung war kurz weg — Anja macht weiter.');
          await runTurn(s.turn, { kind: 'start' });
        } catch (e2) { fail(e2); }
      } else fail(e);
    }
  };

  const startRecording = async () => {
    if (status !== 'you' || rec.current || opening.current) return;
    opening.current = true;
    setError(null); stopPlayback(); audio.stop();
    try { rec.current = await mic.start(); setStatus('rec'); } catch { setError(WHY.mic); } finally { opening.current = false; }
  };
  const sendRecording = async () => {
    const r = rec.current;
    rec.current = null;
    if (!r) return;
    audioContext();
    const take = await r.stop();
    if (take.seconds < 0.4) { setStatus('you'); toast('Das war sehr kurz. Halte ruhig einen Moment länger.'); return; }
    await sendTurn({ kind: 'audio', audio: take.audio, rate: take.rate }, { kind: 'audio' });
  };
  const sendText = (text) => { setSheet(null); sendTurn({ kind: 'text', text }, { kind: 'text', heard: text }); };
  const ask = (kind) => { if (status === 'you') sendTurn({ kind }, { kind }); };

  const keep = (s) => {
    progress.learnSentence({ ru: s.ru, de: s.de, note: 'Aus dem Gespräch mit Anja' });
    setKept(new Set([...kept, s.ru]));
    toast('Im Satzbuch und in der Wiederholung');
  };
  const playAgain = (t) => {
    const data = pcm.current.get(t.k);
    if (!data) return;
    audio.stop(); setReplay(t.k);
    playPcm(data, { onEnd: () => alive.current && setReplay((x) => (x === t.k ? null : x)) });
  };
  const finish = () => {
    stopPlayback(); rec.current?.cancel(); rec.current = null;
    if (sid.current) api.post('/russian/anja/live/end', { id: sid.current }).catch(() => {});
    sid.current = null;
    setStatus('idle'); setPhase('done');
  };
  const leave = () => (phase === 'talk' && turns.some((t) => t.heard) ? finish() : back('anja'));
  const setSettings = (p) => setCp((x) => ({ ...x, settings: { ...x.settings, ...p } }));

  const answered = turns.filter((t) => t.heard && (t.kind === 'audio' || t.kind === 'text'));
  const lastAnja = [...turns].reverse().find((t) => t.said);

  // ── Einstieg ─────────────────────────────────────────────────────────────
  if (phase === 'intro') {
    const off = avail && !avail.available;
    return html`<div class="screen c-amber">
      <${TopBar} title="Anja live" sub="Sprechen statt tippen" onBack=${() => back('anja')} />
      <div class="scroll"><div class="stepc g16">
        <div><div class="over amber">Ein echtes Gespräch</div><div class="big mt4">Du sprichst, Anja antwortet.</div>
          <p class="muted mt8" style="line-height:1.5">Sofort und mit ihrer eigenen Stimme. Sie bleibt bei den Wörtern, die du kennst, und sagt dir einen Satz richtig vor, wenn er nicht stimmte.</p></div>
        ${off && html`<div class="card p" style="border-color:rgba(var(--bad-rgb),.4)"><b>Anja ist gerade nicht erreichbar.</b><p class="sub mt4">${why(avail.reason ?? 'no_key')}</p></div>`}
        ${error && html`<div class="hint" style="border-color:rgba(var(--bad-rgb),.4)"><p>${error}</p></div>`}
        <div><div class="h3" style="margin-bottom:8px">Wo trefft ihr euch?</div>
          <div class="al-scenes">${(more ? SCENARIOS : SCENARIOS.slice(0, 4)).map((x) => html`<button class=${cls('al-scene', cp.settings.scenario === x.id && 'on')} onClick=${() => setSettings({ scenario: x.id })}>
            <${Icon} n=${x.icon} /><b>${x.title}</b><span>${x.goal}</span></button>`)}</div>
          ${!more && html`<button class="btn text" style="margin:4px auto 0" onClick=${() => setMore(true)}>Mehr Situationen</button>`}</div>
        <div><div class="h3" style="margin-bottom:8px">Dein Thema, wenn du magst</div>
          <input class="field" maxlength="300" value=${cp.settings.interest} onInput=${(e) => setSettings({ interest: e.target.value })} placeholder="Meine Katze, eine Reise, mein Lieblingsfilm …" /></div>
        <button class="al-switch" onClick=${() => { const v = !hideText; setHideText(v); storage.set('anja_live_hide', v ? '1' : '0'); }}>
          <span class="tx"><b>Erst hören, dann lesen</b><span>Anjas Text erscheint erst, wenn du ihn antippst.</span></span>
          <span class=${cls('sw', hideText && 'on')}><i></i></span></button>
        <div class="al-steps">
          <div><${Icon} n="hearing" />Anja spricht. Hör zu.</div>
          <div><${Icon} n="mic" />Tippe aufs Mikrofon, sprich, tippe noch einmal.</div>
          <div><${Icon} n="fact_check" />Dein Satz wird geprüft: richtig oder ein Hinweis.</div>
        </div>
        <p class="faint" style="font-size:12px;line-height:1.5">Dein Ton geht zur Verarbeitung an Google (kostenlose Stufe; Google darf solche Eingaben zur Verbesserung seiner Dienste nutzen).</p>
      </div></div>
      <div class="bottom"><button class="btn area" disabled=${!avail || off || !mic.available()} onClick=${() => begin()}><${Icon} n="graphic_eq" />Gespräch beginnen</button>
        ${avail && !off && !mic.available() && html`<div class="sub center">Dieser Browser gibt kein Mikrofon frei.</div>`}</div>
    </div>`;
  }

  // ── Rückblick ────────────────────────────────────────────────────────────
  if (phase === 'done') {
    const clear = answered.filter((t) => t.helps?.check?.ok === true).length;
    const fixes = answered.filter((t) => t.helps?.check && !t.helps.check.ok);
    return html`<div class="screen c-amber">
      <${TopBar} title="Anja live" sub="Dein Gespräch" onBack=${() => back('anja')} />
      <div class="scroll"><div class="stepc g16">
        <div><div class="over amber">Geschafft</div><div class="big mt4">${answered.length === 0 ? 'Bis zum nächsten Mal.' : 'Du hast Russisch gesprochen.'}</div></div>
        <div class="stat3">
          <div><b><${Icon} n="record_voice_over" c="amber" />${answered.length}</b><span>${answered.length === 1 ? 'Antwort' : 'Antworten'}</span></div>
          <div><b><${Icon} n="check_circle" fill c="good" />${clear}</b><span>auf Anhieb richtig</span></div>
          <div><b><${Icon} n="lightbulb" fill c="amber" />${fixes.length}</b><span>${fixes.length === 1 ? 'Hinweis' : 'Hinweise'}</span></div>
        </div>
        ${fixes.length > 0 && html`<div><div class="h3" style="margin-bottom:8px">Daraus lernst du am meisten</div>
          ${fixes.map((t) => { const c = t.helps.check; const has = kept.has(c.correctedRu); return html`<div class="card p al-review">
            <div class="sub">Du hast gesagt</div><div class="was">${t.heard}</div>
            <div class="sub mt8">Richtig</div><${IL} ru=${c.correctedRu} size=${18} extra=${extra} />${c.correctedDe && html`<div class="de">${c.correctedDe}</div>`}
            <div class="sub mt8">${c.explanationDe}</div>
            <div class="rowx g8 wrap mt12"><button class="chip sm" onClick=${() => say(c.correctedRu)}><${Icon} n="volume_up" />Anhören</button>
              ${c.correctedDe && html`<button class="chip sm" disabled=${has} onClick=${() => keep({ ru: c.correctedRu, de: c.correctedDe })}><${Icon} n=${has ? 'bookmark_added' : 'bookmark_add'} />${has ? 'Im Satzbuch' : 'Merken'}</button>`}</div></div>`; })}</div>`}
        ${answered.length > 0 && fixes.length === 0 && html`<div class="hint"><p>Kein einziger Hinweis. Beim nächsten Mal darf Anja ruhig etwas mehr fragen.</p></div>`}
      </div></div>
      <div class="bottom"><button class="btn area" onClick=${() => { setTurns([]); pcm.current.clear(); setPhase('intro'); }}>Noch ein Gespräch</button>
        <button class="btn ghost" onClick=${() => back('anja')}>Zurück zur Gesprächsreise</button></div>
    </div>`;
  }

  // ── Gespräch ─────────────────────────────────────────────────────────────
  const speaking = status === 'anja';
  const can = status === 'you';
  const label = status === 'connecting' ? 'Anja kommt …' : status === 'sending' ? 'Anja hört zu …' : speaking ? 'Anja spricht' : 'Tippen und sprechen';
  return html`<div class="screen c-amber al">
    <${TopBar} title="Anja live" sub=${`${scenario.title} · ${answered.length} ${answered.length === 1 ? 'Antwort' : 'Antworten'}`} onBack=${leave}
      right=${html`<button class="btn text" style="min-height:36px;padding:0 4px" onClick=${finish}>Beenden</button>`} />
    <div class="scroll" ref=${scroller}><div class="stepc g12 al-talk">
      ${turns.length === 0 && html`<div class="al-wait"><${Bars} on /><b>Anja kommt gleich.</b><span>Sie begrüßt dich und stellt die erste Frage.</span></div>`}
      ${turns.map((t, i) => html`
        ${(t.kind === 'audio' || t.kind === 'text') && html`<${Mine} key=${`m${t.k}`} t=${t} extra=${extra} onKeep=${keep} kept=${kept.has(t.helps?.check?.correctedRu)} />`}
        ${!(t.error && !t.said) && html`<${Hers} key=${`a${t.k}`} t=${t} extra=${extra} speaking=${speaking && i === turns.length - 1} latest=${i === turns.length - 1} hideText=${hideText}
          onReplay=${pcm.current.has(t.k) && !speaking && status !== 'rec' ? () => playAgain(t) : null} replaying=${replay === t.k} onKeep=${keep} kept=${kept.has(t.said)} />`}`)}
      ${status === 'sending' && html`<div class="typing" style="align-self:flex-end"><i></i><i></i><i></i></div>`}
      ${error && html`<div class="hint" style="border-color:rgba(var(--bad-rgb),.4)"><p>${error}</p></div>`}
    </div></div>
    <div class="al-dock">
      <button class="al-side" disabled=${!can || !lastAnja} onClick=${() => ask('again')}><${Icon} n="replay" /><span>Nicht verstanden</span></button>
      <div class="al-center">
        <button ref=${micBtn} class=${cls('al-mic', status === 'rec' && 'rec', !can && status !== 'rec' && 'off')} disabled=${!can && status !== 'rec'}
          onClick=${() => (rec.current ? sendRecording() : startRecording())} aria-label=${status === 'rec' ? 'Fertig gesprochen' : 'Sprechen'}>
          <${Icon} n=${status === 'rec' ? 'stop' : speaking ? 'graphic_eq' : 'mic'} fill size="34" /></button>
        <div class="al-label">${status === 'rec' ? html`<b class="al-time" ref=${recLabel}>0 s</b> Tippe, wenn du fertig bist` : label}</div>
      </div>
      <button class="al-side" disabled=${!can || !lastAnja} onClick=${() => { helped.current = true; setSheet('help'); }}><${Icon} n="lightbulb" /><span>Was kann ich sagen?</span></button>
    </div>
    <div class="al-under">
      <button disabled=${!can || !lastAnja} onClick=${() => ask('easier')}>Einfacher fragen</button>
      <button disabled=${!can} onClick=${() => setSheet('type')}>Lieber tippen</button>
    </div>
    ${sheet === 'help' && html`<${HelpSheet} turn=${lastAnja} extra=${extra} onClose=${() => setSheet(null)} />`}
    ${sheet === 'type' && html`<${TypeSheet} onSend=${sendText} onClose=${() => setSheet(null)} />`}
  </div>`;
}
