// Gesprächsreise: vier geführte Mini-Gespräche mit Anja, danach das freie Gespräch
// mit der KI (Hikari-Server → lokales Sprachmodell), eigener Wortschatz, Lernplan.
// Regeln wie in RussianConversationViewModel.kt / RussianJourney.kt.
import { html, cls, Icon, TopBar, IL, Sheet, Mic, useListen, useState, useEffect, useRef, useMemo, fx, tts, audio } from '../ui.js';
import { progress, back, toast } from '../app.js';
import { storage, api, speech as recog, serverVoice } from '../platform.js';
import * as Ph from '../domain/phonetics.js';
import * as Sp from '../domain/speech.js';
import * as Gl from '../domain/glossary.js';

// ── Inhalt der vier Mini-Gespräche ─────────────────────────────────────────
const W = (id, ru, de, note, art) => ({ id, ru, de, note, art });
const Ln = (ru, de, situation, instruction, answers, answerDe, success) => ({ ru, de, situation, instruction, answers, answerDe, success });
export const LESSONS = [
  { id: 'hello', title: 'Dein erstes Hallo', sub: 'Ankommen. Einen Tee annehmen. Danke sagen.', place: 'Ein Platz im Café', scenario: 'cafe',
    done: 'Du hast Anja begrüßt, einen Tee angenommen und Danke gesagt. Mit vier Wörtern.',
    words: [W('hello', 'Прив+ет', 'Hallo', 'So begrüßt du Anja. Locker, wie unter Freunden.', 'waving_hand'), W('tea', 'чай', 'Tee', 'Ein Wort für eine warme Tasse. Mehr brauchst du gerade nicht.', 'local_cafe'),
      W('yes', 'да', 'Ja', 'Damit nimmst du Anjas Einladung zum Tee an.', 'check'), W('thanks', 'Спас+ибо', 'Danke', 'Ein kleines Wort, das ein Gespräch wärmer macht.', 'favorite')],
    lines: [Ln('Привет!', 'Hallo!', 'Anja winkt dir zu.', 'Begrüße Anja zurück.', ['Привет!'], 'Hallo!', 'Du hast Anja begrüßt.'),
      Ln('Чай?', 'Tee?', 'Anja bietet dir einen Tee an.', 'Nimm den Tee mit einem Ja an.', ['Да.', 'Да, спасибо.'], 'Ja.', 'Anja weiß: Du möchtest einen Tee.'),
      Ln('Чай.', 'Tee.', 'Anja stellt die Tasse vor dich.', 'Bedanke dich bei Anja.', ['Спасибо!'], 'Danke!', 'Du hast dich auf Russisch bedankt.')] },
  { id: 'choice', title: 'Lieber einen Kaffee', sub: 'Du entscheidest, was in deine Tasse kommt.', place: 'Dein Lieblingsplatz', scenario: 'cafe',
    done: 'Du hast abgelehnt, selbst gewählt und bestellt. Deine Worte machen einen Unterschied.',
    words: [W('coffee', 'к+офе', 'Kaffee', 'Heute darf es auch ein Kaffee sein.', 'local_cafe'), W('no', 'нет', 'Nein', 'Du darfst freundlich ablehnen. Ein Wort reicht.', 'remove'),
      W('please', 'Пож+алуйста', 'Bitte · gern geschehen', 'Beim Bestellen heißt es bitte. Nach einem Danke: gern geschehen.', 'favorite'), W('or', '+или', 'oder', 'Чай или кофе? bedeutet: Tee oder Kaffee? Die Getränke kennst du schon.', 'swap_horiz')],
    lines: [Ln('Чай?', 'Tee?', 'Anja erinnert sich an deinen Tee.', 'Heute möchtest du keinen Tee. Sage Nein.', ['Нет, спасибо.', 'Нет.'], 'Nein, danke.', 'Du hast freundlich abgelehnt.'),
      Ln('Чай или кофе?', 'Tee oder Kaffee?', 'Anja lässt dir die Wahl.', 'Bestelle einen Kaffee. Bitte kennst du jetzt auch.', ['Кофе, пожалуйста.', 'Кофе.'], 'Kaffee, bitte.', 'Du hast selbst einen Kaffee bestellt.'),
      Ln('Кофе.', 'Kaffee.', 'Dein Kaffee ist da.', 'Bedanke dich wie im ersten Gespräch.', ['Спасибо!'], 'Danke!', 'Deine ersten Wörter kommen wieder zum Einsatz.')] },
  { id: 'likes', title: 'Das mag ich', sub: 'Aus bekannten Wörtern wird dein erster eigener Satz.', place: 'Ein Gespräch mit Anja', scenario: 'friend',
    done: 'Du kannst eine Vorliebe ausdrücken. Aus einzelnen Wörtern werden deine Sätze.',
    words: [W('i', 'я', 'ich', 'Mit diesem Wort beginnt ein Satz über dich.', 'waving_hand'), W('like', 'любл+ю', 'ich mag · ich liebe', 'Я люблю чай. heißt: Ich mag Tee. Keine Regel auswendig lernen — erst benutzen.', 'favorite'),
      W('you', 'ты', 'du', 'So sprichst du Anja an. Ihr seid per du.', 'waving_hand'), W('and', 'а', 'und · aber', 'А ты? heißt hier: Und du? Ein kleines Wort zum Zurückfragen.', 'swap_horiz')],
    lines: [Ln('Чай или кофе?', 'Tee oder Kaffee?', 'Ihr sucht euch etwas zu trinken aus.', 'Wähle Tee oder Kaffee — beides passt.', ['Чай.', 'Кофе.', 'Чай, пожалуйста.', 'Кофе, пожалуйста.'], 'Tee.', 'Du hast deine eigene Wahl getroffen.'),
      Ln('Я люблю чай. А ты?', 'Ich mag Tee. Und du?', 'Anja erzählt von ihrer Vorliebe.', 'Sage mit я люблю, welches Getränk du magst.', ['Я люблю чай.', 'Я люблю кофе.'], 'Ich mag Tee.', 'Das war ein eigener russischer Satz.'),
      Ln('Спасибо!', 'Danke!', 'Anja bedankt sich für eure kleine Pause.', 'Antworte mit Gern geschehen.', ['Пожалуйста!'], 'Gern geschehen!', 'Du kannst nicht nur antworten, sondern ein Gespräch abrunden.')] },
  { id: 'feeling', title: 'Wie geht es dir?', sub: 'Ein bisschen mehr über dich. Ganz ohne Druck.', place: 'Wiedersehen mit Anja', scenario: 'friend',
    done: 'Du hast von dir erzählt und zurückgefragt. So entsteht ein Gespräch.',
    words: [W('how', 'как', 'wie', 'Dieses kleine Fragewort hörst du gleich wieder.', 'waving_hand'), W('things', 'дел+а', 'Dinge · Angelegenheiten', 'Zusammen: Как дела? — Wie geht es dir? Lerne es als ganzen Ausdruck.', 'swap_horiz'),
      W('good', 'Хорош+о', 'Gut', 'Eine vollständige Antwort auf Wie geht es dir?.', 'check'), W('okay', 'Норм+ально', 'Ganz okay', 'Du musst nicht immer gut drauf sein. Ganz okay ist genauso richtig.', 'favorite')],
    lines: [Ln('Как дела?', 'Wie geht es dir?', 'Anja freut sich, dich wiederzusehen.', 'Sage Gut oder Ganz okay — wie es für dich passt.', ['Хорошо.', 'Нормально.', 'Хорошо, спасибо.'], 'Gut.', 'Du hast gesagt, wie es dir geht.'),
      Ln('Чай.', 'Tee.', 'Anja bringt dir einen Tee. Jetzt möchtest du wissen, wie es ihr geht.', 'Frage Anja: Wie geht es dir? Du kennst как und дела.', ['Как дела?', 'А ты?'], 'Wie geht es dir?', 'Du hast selbst eine Frage gestellt.'),
      Ln('Хорошо, спасибо!', 'Gut, danke!', 'Anja freut sich über deine Frage.', 'Antworte mit Gern geschehen.', ['Пожалуйста!'], 'Gern geschehen!', 'Ihr habt euch gegenseitig zugehört.')] },
];
const SCENARIOS = [
  ['cafe', 'Ein Café, zwei Menschen', 'Du triffst Anja in einem kleinen Café.', 'Etwas bestellen und eine Vorliebe nennen', 'local_cafe'],
  ['friend', 'Ein neuer Kontakt', 'Ihr lernt euch kennen, ganz ohne Zeitdruck.', 'Dich vorstellen und etwas zurückfragen', 'waving_hand'],
  ['weekend', 'Ein schöner freier Tag', 'Ihr plant etwas, das euch beiden Freude macht.', 'Einen Vorschlag machen und euch einigen', 'wb_sunny'],
  ['journey', 'Unterwegs', 'Ihr findet gemeinsam den richtigen Weg.', 'Nach einem Ort fragen und Hilfe verstehen', 'directions_walk'],
  ['memory', 'Eine kleine Geschichte', 'Eine schöne Erinnerung — echt oder erfunden.', 'Ein Erlebnis und ein Gefühl ausdrücken', 'auto_stories'],
  ['film', 'Nach dem Film', 'Ihr sprecht über eine erfundene kurze Filmszene.', 'Sagen, was passiert und wie du es findest', 'movie'],
  ['music', 'Dein Soundtrack', 'Ihr sprecht über Musik und ihre Stimmung, ohne Liedtexte zu zitieren.', 'Musikgeschmack und Gefühle beschreiben', 'music_note'],
  ['choice', 'Was ist dir wichtig?', 'Ihr trefft eine kleine Entscheidung mit zwei guten Möglichkeiten.', 'Eine Meinung sagen und einen Grund nennen', 'balance'],
].map(([id, title, scene, goal, icon]) => ({ id, title, scene, goal, icon }));
const ACTIONS = [['simplify', 'Einfacher fragen'], ['deeper', 'Tiefer nachfragen'], ['twist', 'Eine kleine Überraschung'], ['roles', 'Jetzt frage ich'], ['retell', 'Aus dem Kopf erzählen'], ['listen', 'Eine Szene verstehen']];

// ── Zustand ────────────────────────────────────────────────────────────────
const DEFAULT_SETTINGS = { scenario: 'cafe', interest: '', mood: 'calm', goal: 'everyday', turns: 12, speed: 0.85, targetDate: '', daysPerWeek: 5, minutesPerDay: 30, blockMinutes: 10 };
const emptyChat = () => ({ stage: 0, observations: [], cards: [], settings: { ...DEFAULT_SETTINGS }, activeSecondsByDay: {}, foundation: { introduced: [], completed: [], run: null }, conversationVocabulary: [] });
const loadChat = () => { const p = storage.getJSON('conv_progress'); return p ? { ...emptyChat(), ...p, settings: { ...DEFAULT_SETTINGS, ...p.settings }, foundation: { introduced: [], completed: [], run: null, ...p.foundation } } : emptyChat(); };
const normalize = (s) => Ph.plain(s).toLowerCase().replaceAll('ё', 'е').replace(/[^а-яa-z ]/g, ' ').trim().replace(/ +/g, ' ');
const uid = () => Math.random().toString(36).slice(2) + Date.now().toString(36);
const INTERVALS = [1, 1, 2, 4, 7, 14, 30];
// Anja spricht mit ihrer Kursstimme vom Mac; ist der nicht erreichbar, mit der Gerätestimme.
const say = (ru, speed) => audio.play(null, { text: Ph.plain(ru), ru, voice: 'p', rate: Math.min(1.1, Math.max(0.6, speed)), key: 'anja' });

function gradeCard(card, correct, today) {
  const box = correct ? Math.min(card.box + 1, 6) : 0;
  return { ...card, box, due: today + INTERVALS[box], attempts: card.attempts + 1, delayedCorrect: card.delayedCorrect + (correct && today >= card.due && today > card.created ? 1 : 0) };
}
function addCards(cp, phrases, today) {
  const have = new Set(cp.cards.map((c) => normalize(c.phrase.ru)));
  const fresh = phrases.filter((p) => !have.has(normalize(p.ru)) && have.add(normalize(p.ru))).map((p) => ({ phrase: { ru: p.ru, de: p.de }, created: today, due: today + 1, box: 0, attempts: 0, delayedCorrect: 0 }));
  return { ...cp, cards: [...cp.cards, ...fresh].slice(-300) };
}
function record(cp, obs) {
  if (cp.observations.some((o) => o.id === obs.id)) return cp;
  const observations = [...cp.observations, obs].slice(-500);
  const recent = observations.filter((o) => o.stage === cp.stage).slice(-20);
  const strong = recent.filter((o) => o.clear && o.independent && o.spoken);
  const ready = recent.length >= 15 && strong.length >= 12 && new Set(strong.map((o) => o.day)).size >= 3 && new Set(strong.map((o) => o.scenario)).size >= 2 &&
    new Set(strong.map((o) => normalize(o.answer))).size >= 8 && cp.cards.filter((c) => c.delayedCorrect > 0).length >= (cp.stage + 1) * 2;
  return { ...cp, observations, stage: ready ? Math.min(4, cp.stage + 1) : cp.stage };
}
const supportStage = (cp) => { const last = cp.observations.slice(-3); return last.length === 3 && last.every((o) => !o.clear || !o.independent) ? Math.max(0, cp.stage - 1) : cp.stage; };

// Warum Anja nicht erreichbar ist — der Server nennt den Grund (`reason`), `server` heißt: der Mac selbst antwortet nicht.
const WHY = {
  server: () => 'Der Hikari-Server auf deinem Mac antwortet nicht. Ist der Mac an und im selben Netz (oder über Tailscale verbunden)?',
  offline: () => 'LM Studio auf dem Mac antwortet nicht. Öffne LM Studio und schalte dort den lokalen Server ein.',
  model_missing: (st) => `Anjas Sprachmodell »${st.wanted}« gibt es in LM Studio nicht mehr. Lade es dort wieder oder trage in der Server-Einstellung RUSSIAN_CHAT_MODEL ein vorhandenes ein.`,
  no_model: () => 'In LM Studio ist kein Sprachmodell vorhanden. Lade dort eines herunter.',
};
const whyText = (st) => (WHY[st?.reason] ?? (() => 'Für das freie Gespräch muss dein KI-Server (LM Studio auf dem Mac) laufen.'))(st);

/** Wort → Bedeutung aus allem, was in der Gesprächsreise schon vorkam — ergänzt das Kurs-Wörterbuch unter deinen eigenen Sätzen. */
function ownWords(cp, session) {
  const m = new Map();
  const add = (w) => { const k = Gl.wordKey(w?.ru ?? ''); if (k && w.de && !/\s/.test(Ph.plain(w.ru).trim()) && !m.has(k)) m.set(k, w.de.split(' · ')[0]); };
  for (const l of LESSONS) l.words.forEach(add);
  cp.conversationVocabulary.forEach(add);
  cp.cards.forEach((c) => add(c.phrase));
  for (const t of session?.turns ?? []) (t.reply?.words ?? []).forEach(add);
  return m;
}

// ── Bausteine ──────────────────────────────────────────────────────────────
const Over = ({ children }) => html`<div class="over amber">${children}</div>`;
const TextBtn = ({ onClick, children, disabled }) => html`<button class="btn text" style="align-self:center" disabled=${disabled} onClick=${onClick}>${children}</button>`;
const Card = ({ children, tint }) => html`<div class=${cls('card p', tint && 'tint')}>${children}</div>`;
function Listen({ ru, speed, label = 'Anhören' }) {
  const [on, setOn] = useState(false);
  return html`<button class=${cls('chip sm', on && 'on')} onClick=${async () => { setOn(true); await say(ru, speed); setOn(false); }}><${Icon} n="volume_up" />${label}</button>`;
}
function Choice({ value, options, onChange, cols = 2 }) {
  return html`<div class="grid" style=${`grid-template-columns:repeat(${cols},1fr)`}>${options.map(([v, label, sub]) => html`<button class="card p" style=${`padding:12px;text-align:left;${value === v ? 'border-color:var(--amber);background:rgba(var(--amber-rgb),.1)' : ''}`} onClick=${() => onChange(v)}>
    <b style="font-size:14px;font-weight:500">${label}</b>${sub && html`<div class="sub" style="font-size:12px">${sub}</div>`}</button>`)}</div>`;
}

/** Antwort sprechen oder tippen: erkannter Text wird zum Entwurf, den du selbst bestätigst. */
function AnswerInput({ draft, spoken, onDraft, expect, sendLabel, onSend, micLabel = 'Ich antworte', busy, extra }) {
  const [typing, setTyping] = useState(false);
  const [note, setNote] = useState(null);
  const L = useListen((hyps) => { const t = (hyps[0] ?? '').trim(); if (t) { onDraft(t, true); setNote(null); } else setNote('Nichts sicher erkannt. Versuch es noch einmal oder tippe.'); });
  const canSpeak = recog.available();
  return html`<div class="stack g10">
    ${L.listening && html`<${Card}><b>${L.opening ? 'Moment — das Mikrofon öffnet …' : 'Ich höre zu …'}</b><div class="sub mt4">${L.partial || 'Ein Wort reicht. Du kannst die Aufnahme jederzeit beenden.'}</div><//>`}
    ${(typing || draft || !canSpeak) && html`<div><div class="over" style="margin-bottom:6px">${spoken ? 'So wurde es verstanden' : 'Deine russische Antwort'}</div>
      ${draft.trim() && /[а-яё]/i.test(draft) && html`<div class="card p" style="padding:12px 14px;margin-bottom:8px"><${IL} ru=${draft} size=${19} extra=${extra} /></div>`}
      <textarea class="field" rows="2" maxlength="1500" lang="ru" value=${draft} onInput=${(e) => onDraft(e.target.value, false)} placeholder="Russisch tippen …"></textarea></div>`}
    ${(note || L.errorText) && html`<div class="sub bad">${note ?? (L.error === 'denied' ? 'Mikrofon nicht freigegeben. Du kannst die Antwort auch tippen.' : L.errorText)}</div>`}
    ${draft && !L.listening && html`<button class="btn" disabled=${busy} onClick=${onSend}>${sendLabel}</button>`}
    ${canSpeak && html`<button class=${cls('btn', (draft || L.listening) && 'ghost')} disabled=${busy || L.evaluating} onClick=${() => { setNote(null); L.toggle(expect); }}>
      <${Icon} n=${L.listening ? 'stop_circle' : 'mic'} />${L.listening ? 'Aufnahme beenden' : draft ? 'Neu aufnehmen' : micLabel}</button>`}
    ${canSpeak && !typing && !draft && html`<${TextBtn} onClick=${() => setTyping(true)}><${Icon} n="keyboard" />Heute lieber tippen<//>`}
  </div>`;
}

// ── Seite ──────────────────────────────────────────────────────────────────
const TITLES = { review: 'Dein Wortschatz', plan: 'Dein Lernplan', evidence: 'Warum wir so üben', chat: 'Mit Anja sprechen', settings: 'Sprechtempo und Hilfe' };

export function Journey() {
  const [cp, setCpState] = useState(loadChat);
  const [session, setSessionState] = useState(() => storage.getJSON('conv_session'));
  const [page, setPageState] = useState('home');
  const [menu, setMenu] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [available, setAvailable] = useState(null);
  const [status, setStatus] = useState(null); // {reason, wanted, at} der letzten Prüfung
  const [checking, setChecking] = useState(false);
  const cpRef = useRef(cp);
  const setCp = (fn) => { const next = typeof fn === 'function' ? fn(cpRef.current) : fn; cpRef.current = next; storage.set('conv_progress', next); setCpState(next); };
  const setSession = (s) => { if (s) storage.set('conv_session', s); else storage.remove('conv_session'); setSessionState(s); };
  const setPage = (p) => { audio.stop(); tts.stop(); setError(null); setMenu(false); setPageState(p); };
  const today = progress.today();
  const f = cp.foundation;
  const run = f.run;
  const lesson = run ? LESSONS.find((l) => l.id === run.lessonId) : null;
  const nextLesson = LESSONS.find((l) => !f.completed.includes(l.id)) ?? LESSONS[LESSONS.length - 1];
  const freeReady = LESSONS.slice(0, 3).every((l) => f.completed.includes(l.id));
  const speed = cp.settings.speed;
  const setRun = (patch) => setCp((c) => ({ ...c, foundation: { ...c.foundation, run: patch ? { ...c.foundation.run, ...patch } : null } }));
  const setSettings = (patch) => setCp((c) => ({ ...c, settings: { ...c.settings, ...patch } }));
  // `shown` = vom Lernenden angestoßen: Die Prüfung dauert oft nur Millisekunden — ohne kurze
  // sichtbare Phase sähe ein Tippen mit gleichem Ergebnis aus, als wäre nichts passiert.
  const checkStatus = async (shown = false) => {
    if (shown) setChecking(true);
    const [r] = await Promise.all([api.get('/russian/conversation/status').catch(() => ({ available: false, reason: 'server' })), shown ? new Promise((ok) => setTimeout(ok, 600)) : null]);
    setAvailable(!!r.available);
    setStatus({ reason: r.reason, wanted: r.wanted, at: new Date().toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit', second: '2-digit' }) });
    setChecking(false);
  };
  useEffect(() => { checkStatus(); serverVoice.warm(); return () => { audio.stop(); tts.stop(); }; }, []);
  // Beim Öffnen der Gesprächs-Auswahl neu prüfen: Wer LM Studio inzwischen gestartet hat, soll nicht tippen müssen.
  useEffect(() => { if (page === 'free') checkStatus(); }, [page]);

  // Aktive Übungszeit (ohne Warten auf die KI).
  useEffect(() => {
    const active = (page === 'lesson' && run && run.phase !== 'complete') || (page === 'chat' && session && !session.finished);
    if (!active) return undefined;
    const t = setInterval(() => { if (!document.hidden && !busy) setCp((c) => ({ ...c, activeSecondsByDay: { ...c.activeSecondsByDay, [today]: (c.activeSecondsByDay[today] ?? 0) + 15 } })); }, 15000);
    return () => clearInterval(t);
  }, [page, run?.phase, session?.finished, busy]);

  // ── Mini-Gespräch ──
  const beginLesson = (id) => { setRun(null); setCp((c) => ({ ...c, foundation: { ...c.foundation, run: { id: uid(), lessonId: id, phase: 'learn', index: 0, draft: '', spoken: false, helped: false, result: '', recalled: 0, spokenAnswers: 0 } } })); setPage('lesson'); };
  const lineOf = (r, l) => (r.phase === 'recall' && l.lines.length > 1 ? l.lines[(r.index + 1) % l.lines.length] : l.lines[r.index]);
  const check = () => {
    const line = lineOf(run, lesson);
    const ok = line.answers.some((a) => normalize(a) === normalize(run.draft));
    (ok ? fx.right : fx.wrong)();
    setRun({ result: ok ? 'correct' : 'retry', recalled: run.recalled + (ok && run.phase === 'recall' && !run.helped ? 1 : 0), spokenAnswers: run.spokenAnswers + (ok && run.spoken ? 1 : 0) });
    if (ok && run.phase === 'recall') setCp((c) => record(c, { id: `${run.id}:${run.phase}:${run.index}`, day: today, stage: 0, scenario: lesson.scenario, answer: run.draft, clear: true, independent: !run.helped, spoken: run.spoken }));
  };
  const nextStep = () => {
    audio.stop(); tts.stop();
    const clean = { draft: '', spoken: false, helped: false, result: '' };
    if (run.phase === 'learn') {
      setCp((c) => ({ ...c, foundation: { ...c.foundation, introduced: [...new Set([...c.foundation.introduced, lesson.words[run.index].id])] } }));
      setRun(run.index + 1 < lesson.words.length ? { index: run.index + 1 } : { phase: 'guided', index: 0, ...clean });
    } else if (run.index + 1 < lesson.lines.length) setRun({ index: run.index + 1, ...clean });
    else if (run.phase === 'guided') setRun({ phase: 'recall', index: 0, ...clean });
    else {
      fx.win();
      setCp((c) => addCards({ ...c, foundation: { ...c.foundation, completed: [...new Set([...c.foundation.completed, lesson.id])], run: { ...c.foundation.run, phase: 'complete', ...clean } } }, lesson.words, today));
      for (const w of lesson.words) progress.saveMySentence({ ru: w.ru, de: w.de, patternId: `journey_${lesson.id}`, note: `Gesprächsreise · ${lesson.title}` });
      progress.apply({ xp: 20, graded: {}, spokenOk: run.spokenAnswers });
    }
  };

  // ── Freies Gespräch ──
  const scenario = SCENARIOS.find((s) => s.id === (session?.settings.scenario ?? cp.settings.scenario)) ?? SCENARIOS[0];
  const send = async (action, s = session) => {
    if (busy || !s || s.finished) return;
    const message = action === 'reply' || action === 'repair' ? s.draft.trim() : '';
    if ((action === 'reply' || action === 'repair') && !message) return;
    const history = action === 'repair' ? s.history.slice(0, -2) : s.history;
    if (history.length > 77) { setSession({ ...s, finished: true }); return; }
    const sc = SCENARIOS.find((x) => x.id === s.settings.scenario) ?? SCENARIOS[0];
    const c = cpRef.current;
    const known = [...LESSONS.flatMap((l) => l.words).filter((w) => c.foundation.introduced.includes(w.id)).map((w) => ({ ru: Ph.plain(w.ru), de: w.de })), ...c.conversationVocabulary];
    const seen = new Set();
    setBusy(true); setError(null);
    try {
      const reply = await api.post('/russian/conversation/turn', {
        stage: s.stage, scenario: `${sc.title}: ${sc.scene} ${sc.goal}`, interest: s.settings.interest, mood: s.settings.mood, goal: s.settings.goal, action, message, history,
        review: c.cards.filter((x) => x.due <= today).sort((a, b) => a.due - b.due).slice(0, 3).map((x) => x.phrase.ru),
        knownWords: known.filter((w) => !seen.has(normalize(w.ru)) && seen.add(normalize(w.ru))).slice(0, 120),
      });
      const turn = { id: uid(), reply, answer: message, spoken: s.draftSpoken, helped: s.helped || action === 'repair', action, ready: false };
      const turns = action === 'repair' ? [...s.turns.slice(0, -1), turn] : [...s.turns, turn];
      setSession({ ...s, turns, history: [...history, { role: 'user', content: message || `Gesprächsaktion: ${action}` }, { role: 'assistant', content: reply.partnerRu }], draft: '', draftSpoken: false, hint: 0, helped: action === 'simplify', repair: false });
      if (action === 'reply' && reply.feedback) {
        setCp((x) => record(x, { id: turn.id, day: today, stage: s.stage, scenario: sc.id, answer: message, clear: reply.feedback.meaning === 'clear', independent: !turn.helped, spoken: turn.spoken }));
        progress.apply({ xp: 8, graded: {}, spokenOk: turn.spoken ? 1 : 0 });
      }
      setAvailable(true);
    } catch (e) {
      setError(e.status === 429 ? 'Anja ist gerade beschäftigt. Deine Antwort bleibt erhalten — versuch es gleich erneut.'
        : 'Anja konnte gerade keine passende Antwort vorbereiten. Dein Text bleibt erhalten. Versuche es erneut — falls es wieder nicht klappt, prüfe, ob dein KI-Server (LM Studio) läuft.');
      checkStatus(); // hält den Grund aktuell, falls die Verbindung mitten im Gespräch wegfiel
    } finally { setBusy(false); }
  };
  const startChat = () => {
    if (!freeReady) { setPage('home'); return; }
    const s = { id: uid(), settings: { ...cp.settings }, stage: supportStage(cp), turns: [], history: [], draft: '', draftSpoken: false, hint: 0, helped: false, finished: false, repair: false, confidenceAfter: null };
    setSession(s); setPage('chat'); send('start', s);
  };
  const savePhrase = (ph) => { setCp((c) => addCards(c, [ph], today)); progress.saveMySentence({ ru: ph.ru, de: ph.de, patternId: 'conversation', note: 'Gesprächsreise' }); toast('Für später gemerkt'); };
  const learnTurnWords = (turn) => {
    const words = turn.reply.words ?? [];
    setCp((c) => { const seen = new Set(c.conversationVocabulary.map((w) => normalize(w.ru))); return addCards({ ...c, conversationVocabulary: [...c.conversationVocabulary, ...words.filter((w) => !seen.has(normalize(w.ru)))].slice(-100) }, words, today); });
    for (const w of words) progress.saveMySentence({ ru: w.ru, de: w.de, patternId: 'conversation', note: 'Gesprächsreise · KI-Dialog' });
    setSession({ ...session, turns: session.turns.map((t) => (t.id === turn.id ? { ...t, ready: true } : t)) });
  };

  const P_ = { cp, setCp, setPage, speed, today, session, setSession, busy, send, scenario, savePhrase, learnTurnWords, freeReady, available, status, checking, checkStatus, startChat, setSettings, nextLesson, beginLesson, run, lesson, setRun, check, nextStep, lineOf };
  const title = page === 'lesson' ? lesson?.title ?? 'Dein Einstieg' : TITLES[page] ?? 'Gesprächsreise';
  const sub = page === 'lesson' ? lesson?.place : page === 'chat' ? 'Dein Tempo. Deine Geschichten.' : 'Russisch · Schritt für Schritt';
  return html`<div class="screen c-amber">
    <${TopBar} title=${title} sub=${sub} onBack=${() => (page === 'home' ? back('kurse') : setPage('home'))}
      right=${html`<button class="iconbtn" onClick=${() => setMenu(true)} aria-label="Weitere Möglichkeiten"><${Icon} n="more_horiz" /></button>`} />
    ${page === 'home' && html`<${HomePage} ...${P_} />`}
    ${page === 'lesson' && lesson && html`<${LessonPage} ...${P_} />`}
    ${page === 'free' && html`<${FreePage} ...${P_} />`}
    ${page === 'chat' && html`<${ChatPage} ...${P_} error=${error} />`}
    ${page === 'review' && html`<${ReviewPage} ...${P_} />`}
    ${page === 'plan' && html`<${PlanPage} ...${P_} />`}
    ${page === 'settings' && html`<${SettingsPage} ...${P_} />`}
    ${page === 'evidence' && html`<${EvidencePage} />`}
    ${menu && html`<${Sheet} onClose=${() => setMenu(false)} title="Dein Platz zum Lernen"><div class="list mt12">
      ${[['home', 'Meine Reise', 'route'], ['review', 'Mein Wortschatz', 'style'], ['plan', 'Mein Lernplan', 'calendar_month'], ['settings', 'Sprechtempo und Hilfe', 'speed'], ['evidence', 'Was die Forschung sagt', 'science']]
        .map(([p, t, ic]) => html`<button class="row" onClick=${() => setPage(p)}><${Icon} n=${ic} c="amber" /><div class="tx"><b>${t}</b></div><${Icon} n="chevron_right" c="faint" /></button>`)}</div><//>`}
  </div>`;
}

function HomePage({ cp, setPage, today, nextLesson, beginLesson, run, lesson, freeReady }) {
  const f = cp.foundation;
  const started = f.completed.length > 0;
  const resumable = run && ['learn', 'guided', 'recall'].includes(run.phase);
  const allDone = f.completed.length >= LESSONS.length;
  const due = cp.cards.filter((c) => c.due <= today).length;
  const known = LESSONS.flatMap((l) => l.words).filter((w) => f.introduced.includes(w.id)).length;
  return html`<div class="scroll"><div class="stepc g16">
    <div><${Over}>${started ? 'Schön, dass du wieder da bist' : 'Dein erster Russisch-Moment'}<//><div class="big mt4">${started ? 'Deine Gesprächsreise' : 'Dein erstes Gespräch'}</div>
      <p class="muted mt8" style="line-height:1.5">${started ? 'Deine Wörter reisen mit. Heute kommt ein kleiner neuer Gesprächsmoment dazu.' : 'Du musst noch nichts können. Wir lernen die Wörter zuerst — und benutzen sie dann zusammen.'}</p></div>
    <div class="card p tint"><div class="rowx g12"><div class="ic lg solid round"><${Icon} n=${allDone && !resumable ? 'forum' : 'local_cafe'} /></div>
        <div class="grow"><b style="font-size:17px">${allDone && !resumable ? 'Bereit für deine Geschichten' : (resumable ? lesson : nextLesson).title}</b>
          <div class="sub">${allDone && !resumable ? 'Mit deinen Wörtern frei sprechen' : `${(resumable ? lesson : nextLesson).words.length} Wörter · ein kleines Gespräch`}</div></div></div>
      <button class="btn mt16" onClick=${() => (resumable ? setPage('lesson') : allDone ? setPage('free') : beginLesson(nextLesson.id))}>
        ${resumable ? 'Hier weitermachen' : allDone ? 'Mein eigenes Gespräch' : started ? 'Nächsten Moment entdecken' : 'Meine ersten 4 Wörter'}<${Icon} n="arrow_forward" /></button>
      <div class="sub mt12 center">Du startest den Ton selbst. Alles wird auf Deutsch erklärt.</div></div>
    ${due > 0 && html`<button class="row card" style="border-radius:16px" onClick=${() => setPage('review')}><${Icon} n="style" c="amber" /><div class="tx"><b>${due} ${due === 1 ? 'Wort wartet' : 'Wörter warten'} auf ein Wiedersehen</b></div><${Icon} n="chevron_right" c="faint" /></button>`}
    <div><div class="over" style="margin-bottom:8px">Deine kleine Reise</div><div class="list">${LESSONS.map((l, i) => {
      const done = f.completed.includes(l.id);
      const open = LESSONS.slice(0, i).every((x) => f.completed.includes(x.id));
      return html`<button class=${cls('row', done && 'done', !open && 'locked')} disabled=${!open} onClick=${() => beginLesson(l.id)}>
        <div class="dn">${done ? html`<${Icon} n="check" />` : open ? i + 1 : html`<${Icon} n="lock" />`}</div>
        <div class="tx"><b>${l.title}</b><span>${done ? 'Noch einmal erleben' : open ? 'Bereit, wenn du es bist' : 'Nach dem vorherigen Gespräch'}</span></div></button>`;
    })}</div></div>
    ${freeReady ? html`<div class="card p"><${Over}>Deine Wörter. Deine Geschichten.<//><div class="h2 mt4">Worüber reden wir?</div>
        <p class="sub mt4">Mit Anja frei sprechen. Neue Formulierungen schaust du dir vorher in Ruhe an.</p><button class="btn ghost mt12" onClick=${() => setPage('free')}><${Icon} n="forum" />Mein eigenes Gespräch</button></div>`
      : html`<p class="sub center">Freie Gespräche kommen nach deinen ersten drei Mini-Gesprächen. Bis dahin üben wir alles gemeinsam.</p>`}
    <div class="rowx g8 wrap" style="justify-content:center"><button class="chip" onClick=${() => setPage('review')}>Mein Wortschatz · ${known} kennengelernt</button><button class="chip" onClick=${() => setPage('plan')}>Mein Lernplan</button></div>
  </div></div>`;
}

function LessonPage({ run, lesson, setRun, check, nextStep, lineOf, speed, setPage, beginLesson, cp }) {
  const extra = useMemo(() => ownWords(cp, null), [cp.conversationVocabulary, cp.cards]);
  const phaseIdx = run.phase === 'learn' ? 0 : run.phase === 'guided' ? 1 : 2;
  const bar = html`<div class="rowx g8" style="padding:0 16px 10px">${['Kennenlernen', 'Gemeinsam', 'Du selbst'].map((t, i) => html`<div class="grow"><div class="pbar" style="height:5px"><i style=${`width:${i <= phaseIdx ? 100 : 0}%`}></i></div>
    <div class="mt4" style=${`font-size:12px;font-weight:${i === phaseIdx ? 600 : 400};color:${i === phaseIdx ? 'var(--text)' : 'var(--faint)'}`}>${t}</div></div>`)}</div>`;
  if (run.phase === 'complete') {
    const more = LESSONS.find((l) => !cp.foundation.completed.includes(l.id));
    return html`<div class="scroll"><div class="stepc g16"><div><${Over}>Ein kleiner Moment. Ein echter Anfang.<//><div class="big mt4">${lesson.id === 'hello' ? 'Dein erstes Gespräch.' : 'Das hast du selbst gesagt.'}</div><p class="muted mt8" style="line-height:1.5">${lesson.done}</p></div>
        <div class="stat3"><div><b>${lesson.words.length}</b><span>Wörter kennengelernt</span></div><div><b>${run.recalled}</b><span>Antworten ohne Hilfe</span></div></div>
        <div class="card p"><${Over}>Das nimmst du mit<//><div class="rowx g8 wrap mt12">${lesson.words.map((w) => html`<button class="chip" onClick=${() => say(w.ru, speed)}><${Icon} n="volume_up" />${Ph.transliterateFlat(w.ru)} · ${w.de}</button>`)}</div>
          <p class="sub mt12">Automatisch in deinem Wortschatz. Morgen begegnen dir die Wörter wieder — dann zeigt sich, was geblieben ist.</p></div>
      </div></div><div class="bottom"><button class="btn" onClick=${() => (more ? beginLesson(more.id) : setPage('home'))}>${more ? 'Nächsten Moment entdecken' : 'Zurück zu meiner Reise'}</button>
        ${more && html`<${TextBtn} onClick=${() => setPage('home')}>Für heute ein guter Anfang<//>`}</div>`;
  }
  if (run.phase === 'learn') {
    const w = lesson.words[run.index];
    return html`${bar}<div class="scroll"><div class="stepc g16" key=${run.index}><div><${Over}>Wort ${run.index + 1} von ${lesson.words.length}<//><div class="big mt4">${run.index === 0 ? 'Lernen wir uns kennen.' : 'Noch ein kleines Stück Russisch.'}</div></div>
        <div class="card p tint center"><div class="ic lg solid round" style="margin:0 auto 14px"><${Icon} n=${w.art} /></div>
          <${IL} ru=${w.ru} size=${34} center gloss=${false} /><div class="title mt8">${w.de}</div><div class="mt16"><${Listen} ru=${w.ru} speed=${speed} label="So klingt das Wort" /></div></div>
        <p style="line-height:1.55">${w.note}</p>
        <p class="sub">Erst anschauen und anhören, wenn du möchtest. Antworten kommt danach. Die Umschrift ist eine Annäherung an den Klang.</p>
      </div></div><div class="bottom"><button class="btn" onClick=${nextStep}>${run.index + 1 < lesson.words.length ? 'Nächstes Wort' : 'Jetzt gemeinsam benutzen'}<${Icon} n="arrow_forward" /></button></div>`;
  }
  const line = lineOf(run, lesson);
  const guided = run.phase === 'guided';
  const ok = run.result === 'correct';
  const showAnswer = guided || run.helped;
  return html`${bar}<div class="scroll"><div class="stepc g12" key=${`${run.phase}${run.index}`}>
      <div><${Over}>${guided ? 'Zusammen' : 'Dein Versuch'} · ${run.index + 1} von ${lesson.lines.length}<//><p class="muted mt4">${line.situation}</p></div>
      <div class="bub" style="max-width:92%"><div class="who">ANJA</div><${IL} ru=${line.ru} size=${22} /><div class="de">${line.de}</div><div class="mt8"><${Listen} ru=${line.ru} speed=${speed} /></div></div>
      ${!ok && html`<p style="font-size:16px;font-weight:500">${line.instruction}</p>
        ${run.result === 'retry' && html`<div class="hint" style="border-color:rgba(var(--bad-rgb),.4)"><p><b>Das passt noch nicht ganz zur Aufgabe.</b> Vielleicht hat die Spracherkennung etwas anderes verstanden. Du kannst den Text ändern, noch einmal sprechen oder die Starthilfe öffnen.</p></div>`}
        ${showAnswer ? html`<div class="card p" style="background:var(--high)"><div class="over">${guided ? 'Deine Antwort' : 'Deine Starthilfe'}</div><div class="mt8"><${IL} ru=${line.answers[0]} size=${20} /></div><div class="de mt4">${line.answerDe}</div>
            <div class="rowx g8 wrap mt12"><${Listen} ru=${line.answers[0]} speed=${speed} label="Antwort anhören" /><button class="chip sm" onClick=${() => setRun({ draft: line.answers[0], helped: true, spoken: false })}>Als Starthilfe einsetzen</button></div></div>`
          : html`<${TextBtn} onClick=${() => setRun({ helped: true })}><${Icon} n="lightbulb" />Ein kleiner Tipp<//>`}`}
      ${ok && html`<div class="card p" style="border-color:var(--good);background:rgba(var(--good-rgb),.08)"><div class="rowx g8 good" style="font-weight:600"><${Icon} n="check_circle" fill />${line.success}</div>
        <div class="sub mt8" style="margin-bottom:4px">Deine Antwort</div><${IL} ru=${run.draft} size=${19} extra=${extra} /><div class="sub mt4">${guided || run.helped ? 'Mit Unterstützung geübt. Genau dafür ist sie da.' : 'Aus dem Kopf — diesen Versuch merken wir uns für deinen Fortschritt.'}</div></div>`}
    </div></div>
    <div class="bottom">${ok ? html`<button class="btn good" onClick=${nextStep}>${guided && run.index + 1 >= lesson.lines.length ? 'Jetzt versuche ich es selbst' : 'Weiter im Gespräch'}</button>`
      : html`<${AnswerInput} draft=${run.draft} spoken=${run.spoken} expect=${line.answers[0]} onDraft=${(t, sp) => setRun({ draft: t.slice(0, 180), spoken: sp, result: '' })} sendLabel="Antwort prüfen" onSend=${check} micLabel=${guided ? 'Ich spreche es nach' : 'Ich antworte'} extra=${extra} />`}</div>`;
}

function FreePage({ cp, setSettings, session, setPage, startChat, freeReady, available, status, checking, checkStatus, busy }) {
  const [more, setMore] = useState(false);
  const s = cp.settings;
  const sc = (list) => list.map((x) => [x.id, x.title, x.goal]);
  return html`<div class="scroll"><div class="stepc g16">
      <div><${Over}>Deine Wörter. Dein Gespräch.<//><div class="big mt4">Was bewegt dich heute?</div><p class="muted mt8" style="line-height:1.5">Anja knüpft an das an, was du schon gelernt hast. Neue Wörter schauen wir uns vor deiner Antwort gemeinsam an.</p></div>
      ${!freeReady && html`<div class="hint"><p>Freie Gespräche kommen nach deinen ersten drei Mini-Gesprächen.</p></div>`}
      ${session && !session.finished && session.turns.length > 0 && html`<div class="card p tint"><b>Euer letztes Gespräch wartet noch auf dich.</b><button class="btn mt12" onClick=${() => setPage('chat')}>Gespräch fortsetzen</button></div>`}
      ${available === false && html`<div class="card p" style="border-color:rgba(var(--bad-rgb),.4)"><b>Anja ist gerade nicht verbunden.</b><p class="sub mt4">${whyText(status)} Deine Mini-Gespräche und dein Wortschatz funktionieren weiterhin.</p>
        <div class="rowx g8 wrap mt12"><button class="chip" disabled=${checking} onClick=${() => checkStatus(true)}><${Icon} n=${checking ? 'progress_activity' : 'refresh'} c=${checking && 'spin'} />${checking ? 'Prüfe …' : 'Verbindung erneut prüfen'}</button>
          ${status?.at && !checking && html`<span class="sub">Zuletzt geprüft um ${status.at} — noch nicht erreichbar.</span>`}</div></div>`}
      <div><div class="h3" style="margin-bottom:8px">Wo möchtest du Anja treffen?</div><${Choice} value=${s.scenario} options=${sc(more ? SCENARIOS : SCENARIOS.slice(0, 4))} onChange=${(v) => setSettings({ scenario: v })} /></div>
      <div><div class="h3" style="margin-bottom:8px">Dein Thema, wenn du magst</div><input class="field" maxlength="300" value=${s.interest} onInput=${(e) => setSettings({ interest: e.target.value })} placeholder="Meine Katze, eine Reise, mein Lieblingsfilm …" /></div>
      ${more && html`<div><div class="h3" style="margin-bottom:8px">Was möchtest du üben?</div><${Choice} cols=${3} value=${s.goal} options=${[['everyday', 'Alltag'], ['stories', 'Geschichten'], ['media', 'Film & Musik']]} onChange=${(v) => setSettings({ goal: v })} /></div>
        <div><div class="h3" style="margin-bottom:8px">Deine Stimmung</div><${Choice} cols=${3} value=${s.mood} options=${[['calm', 'Entspannt'], ['playful', 'Verspielt'], ['curious', 'Neugierig']]} onChange=${(v) => setSettings({ mood: v })} /></div>
        <div><div class="h3" style="margin-bottom:8px">Zeit für …</div><${Choice} cols=${3} value=${s.turns} options=${[[6, '6 Antworten'], [12, '12 Antworten'], [24, '24 Antworten']]} onChange=${(v) => setSettings({ turns: v })} /></div>`}
      <${TextBtn} onClick=${() => setMore(!more)}>${more ? 'Weniger anzeigen' : 'Gespräch nach meinem Geschmack'}<//>
      <p class="sub">Du kannst eigene Antworten geben oder etwas erfinden. Du entscheidest, wann das Mikrofon startet und welcher erkannte Text gesendet wird.</p>
    </div></div><div class="bottom"><button class="btn" disabled=${busy || !freeReady || available === false} onClick=${startChat}><${Icon} n="forum" />Ein neues Gespräch beginnen</button></div>`;
}

function ChatPage({ cp, session, setSession, busy, send, scenario, savePhrase, learnTurnWords, setPage, speed, error }) {
  const [history, setHistory] = useState(false);
  const [shape, setShape] = useState(false);
  const [example, setExample] = useState(false);
  const scroller = useRef(null);
  const turn = session?.turns[session.turns.length - 1];
  const extra = useMemo(() => ownWords(cp, session), [cp.conversationVocabulary, cp.cards, session?.turns]);
  useEffect(() => { setExample(false); setShape(false); scroller.current?.scrollTo({ top: 0 }); }, [turn?.id]);
  if (!session) return html`<div class="empty">Kein Gespräch geöffnet.<div class="mt20"><button class="btn" onClick=${() => setPage('free')}>Gespräch einrichten</button></div></div>`;
  const answers = session.turns.filter((t) => t.answer);
  const patch = (p) => setSession({ ...session, ...p });
  if (session.finished) {
    const chunks = [...new Map(session.turns.map((t) => t.reply.chunk).filter((c) => c?.ru).map((c) => [c.ru, c])).values()].slice(-3);
    return html`<div class="scroll"><div class="stepc g16"><div><${Over}>Dein Gesprächsmoment<//><div class="big mt4">Du hast etwas zu sagen.</div>
        <p class="muted mt8" style="line-height:1.5">${answers.length} eigene Antworten. ${answers.filter((t) => t.spoken).length} davon gesprochen. ${answers.filter((t) => !t.helped).length} ohne aufgedeckte Hilfe.</p>
        <p class="sub mt8">Dein Fortschritt wächst über mehrere Tage. Eine einzelne gute Runde ist ein Anfang, noch kein Beleg für sicheres Beherrschen.</p></div>
        <div><div class="h3" style="margin-bottom:8px">Wie fühlst du dich jetzt?</div><${Choice} cols=${3} value=${session.confidenceAfter} options=${[[1, 'Unsicher'], [2, 'Okay'], [3, 'Mutig']]} onChange=${(v) => patch({ confidenceAfter: v })} /></div>
        ${chunks.map((c) => { const has = cp.cards.some((x) => normalize(x.phrase.ru) === normalize(c.ru)); return html`<div class="card p"><${IL} ru=${c.ru} size=${18} /><div class="de mt4">${c.de}</div>
          <button class="chip sm mt12" disabled=${has} onClick=${() => savePhrase(c)}><${Icon} n=${has ? 'bookmark_added' : 'bookmark_add'} />${has ? 'Für später gemerkt' : 'In meinen Wortschatz'}</button></div>`; })}
      </div></div><div class="bottom"><button class="btn" onClick=${() => setPage('home')}>Zurück zu meiner Reise</button><${TextBtn} onClick=${() => { setSession(null); setPage('free'); }}>Ein anderes Gespräch<//></div>`;
  }
  const r = turn?.reply;
  const maxHint = r?.starter ? 4 : 3;
  const saved = r && cp.cards.some((x) => normalize(x.phrase.ru) === normalize(r.chunk?.ru ?? ''));
  return html`<div class="scroll" ref=${scroller}><div class="stepc g12">
      <${Over}>${scenario.title} · ${answers.length} eigene Antworten<//>
      ${!turn && html`<div class="title">Anja ist gleich da.</div><p class="muted">Du startest mit einer kurzen Frage. Erst anschauen, dann antworten.</p>${!busy && html`<button class="btn ghost" onClick=${() => send('start')}>Noch einmal verbinden</button>`}`}
      ${turn?.answer && html`<div class="bub me" style="max-width:92%"><div class="who">DU</div><${IL} ru=${turn.answer} size=${19} extra=${extra} />
        ${r.feedback && html`<div class="nt" style="color:var(--good)">${r.feedback.praise}</div>
          ${!r.feedback.focus && r.feedback.explanationDe && html`<div class="de"><b>Genau so:</b> ${r.feedback.explanationDe}</div>`}
          ${r.feedback.focus && html`<div class="de"><b>Lernimpuls:</b> ${r.feedback.focus}</div><div class="sub mt4">${r.feedback.repairCue}</div>
            <div class="rowx g8 wrap mt8">${!session.repair && html`<button class="chip sm" onClick=${() => patch({ repair: true, draft: turn.answer, helped: true })}>Selbst verbessern</button>`}
              <button class=${cls('chip sm', example && 'on')} onClick=${() => { setExample(!example); patch({ helped: true }); }}>Ein Beispiel hilft mir</button></div>
            ${example && html`<div class="mt8"><${IL} ru=${r.feedback.correctedRu} size=${17} /><div class="sub mt4">${r.feedback.explanationDe}</div><div class="mt8"><${Listen} ru=${r.feedback.correctedRu} speed=${speed} /></div></div>`}`}`}</div>`}
      ${r && html`<div class="bub" style="max-width:92%"><div class="who">ANJA</div><${IL} ru=${r.partnerRu} size=${19} /><div class="de">${r.partnerDe}</div><div class="mt8"><${Listen} ru=${r.partnerRu} speed=${speed} /></div></div>`}
      ${busy && html`<div class="typing"><i></i><i></i><i></i></div><div class="sub">Anja denkt über ihre Antwort nach …</div>`}
      ${error && html`<div class="hint" style="border-color:rgba(var(--bad-rgb),.4)"><p>${error}</p></div>`}
      ${r && !busy && !turn.ready && !session.repair && html`<div class="card p tint"><${Over}>Erst in Ruhe anschauen<//><p class="mt8">${r.cue}</p>
        ${(r.words ?? []).map((w) => html`<div class="rowx g8 mt8"><div class="grow"><b>${Ph.transliterateFlat(w.ru)}</b> <span class="cyr" lang="ru">${w.ru}</span><div class="sub">${w.de}</div></div><${Listen} ru=${w.ru} speed=${speed} label="" /></div>`)}
        <p class="sub mt12">${(r.words ?? []).length ? 'Diese Wörter und Ausdrücke können dir helfen. Die Antwort darf ganz deine eigene sein.' : 'Diese Wörter kennst du schon. Eine kurze Antwort reicht.'}</p>
        <button class="btn mt12" onClick=${() => learnTurnWords(turn)}>Bereit für meine Antwort</button></div>`}
      ${r && !busy && (turn.ready || session.repair) && html`
        ${session.repair && html`<div class="rowx g8"><p class="grow" style="font-weight:500">Dein neuer Versuch — ändere nur die eine Stelle.</p><button class="chip sm" onClick=${() => patch({ repair: false, draft: '' })}>Lieber weiterreden</button></div>`}
        ${session.hint > 0 && html`<div class="card p"><${Over}>Ein bisschen Rückenwind<//><p class="mt8">${r.cue}</p>
          ${session.hint >= 2 && (r.words ?? []).map((w) => html`<div class="sub mt4"><b style="color:var(--text)">${Ph.transliterateFlat(w.ru)}</b> — ${w.de}</div>`)}
          ${session.hint >= 3 && r.starter && html`<div class="mt8"><${IL} ru=${r.starter} size=${17} gloss=${false} /><button class="chip sm mt8" onClick=${() => patch({ draft: r.starter, draftSpoken: false })}>Diesen Anfang benutzen</button></div>`}
          ${session.hint >= maxHint && (r.examples ?? []).map((ex) => html`<div class="mt12"><${IL} ru=${ex.ru} size=${16} /><div class="sub">${ex.de}</div><div class="rowx g8 mt8"><${Listen} ru=${ex.ru} speed=${speed} /><button class="chip sm" onClick=${() => patch({ draft: ex.ru, draftSpoken: false })}>Damit anfangen</button></div></div>`)}</div>`}
        <${AnswerInput} draft=${session.draft} spoken=${session.draftSpoken} expect=${r.examples?.[0]?.ru ?? 'Да.'} busy=${busy} onDraft=${(t, sp) => patch({ draft: t.slice(0, 1500), draftSpoken: sp })} sendLabel="Meine Antwort senden" onSend=${() => send(session.repair ? 'repair' : 'reply')} extra=${extra} />
        <${TextBtn} disabled=${session.hint >= maxHint} onClick=${() => patch({ hint: Math.min(session.hint + 1, 4), helped: true })}><${Icon} n="lightbulb" />${session.hint === 0 ? 'Ein kleiner Tipp' : 'Mehr Starthilfe'}<//>
        <p class="faint center" style="font-size:12px">KI-Rückmeldung zu deinem bestätigten Text. Kein Aussprachetest. Andere Formulierungen sind willkommen.</p>
        ${r.chunk?.ru && html`<${TextBtn} disabled=${saved} onClick=${() => savePhrase(r.chunk)}><${Icon} n=${saved ? 'bookmark_added' : 'bookmark_add'} />${saved ? 'Ausdruck gemerkt' : 'Diesen Ausdruck für später merken'}<//>`}
        ${answers.length >= session.settings.turns && html`<div class="card p tint"><p>Dein Gesprächsziel für heute ist erreicht. Ein guter Moment für eine Pause.</p><button class="btn mt12" onClick=${() => patch({ finished: true })}>Mein Rückblick</button></div>`}
        <${TextBtn} onClick=${() => setShape(!shape)}><${Icon} n="tune" />Das Gespräch gestalten<//>
        ${shape && html`<div class="rowx g8 wrap" style="justify-content:center">${ACTIONS.map(([a, t]) => html`<button class="chip" disabled=${!!session.draft} onClick=${() => send(a)}>${t}</button>`)}</div>`}`}
      ${session.turns.length >= 2 && html`<${TextBtn} onClick=${() => setHistory(!history)}>${history ? 'Verlauf schließen' : 'Unser bisheriges Gespräch'}<//>
        ${history && session.turns.slice(0, -1).reverse().map((t) => html`<div class="card p">${t.answer && html`<div class="sub" style="margin-bottom:4px">Du</div><${IL} ru=${t.answer} size=${15} extra=${extra} /><div class="sub mt12" style="margin-bottom:4px">Anja</div>`}<div class="mt4"><${IL} ru=${t.reply.partnerRu} size=${15} gloss=${false} /></div><div class="sub">${t.reply.partnerDe}</div></div>`)}`}
      ${turn && !busy && html`<${TextBtn} onClick=${() => patch({ finished: true })}>Für heute beenden<//>`}
    </div></div>`;
}

function ReviewPage({ cp, setCp, today, speed }) {
  const due = cp.cards.filter((c) => c.due <= today).sort((a, b) => a.due - b.due);
  const card = due[0];
  const [text, setText] = useState('');
  const [spoken, setSpoken] = useState(false);
  const [revealed, setRevealed] = useState(false);
  const [checked, setChecked] = useState(null);
  const [archive, setArchive] = useState(false);
  const L = useListen((hyps) => { if (hyps[0]) { setText(hyps[0]); setSpoken(true); } });
  const next = () => { setCp((c) => ({ ...c, cards: c.cards.map((x) => (x === card ? gradeCard(x, checked && spoken, today) : x)) })); setText(''); setSpoken(false); setRevealed(false); setChecked(null); };
  const date = (d) => new Date(d * 86400000).toISOString().slice(0, 10);
  return html`<div class="scroll"><div class="stepc g16"><div><div class="big">Wörter, die bleiben.</div><p class="muted mt4">${due.length} fällig · ${cp.cards.length} persönliche Ausdrücke</p></div>
    ${!card ? html`<div class="hint"><p>${cp.cards.length ? 'Heute ist nichts fällig. Neue Wörter kommen frühestens morgen wieder. Du kannst deinen Wortschatz unten jederzeit nachschlagen.' : 'Nach deinem ersten Mini-Gespräch kommen deine Wörter automatisch hierher. Morgen erinnern wir uns gemeinsam daran.'}</p></div>`
      : html`<div class="card p tint"><div class="sub">Wie sagst du das auf Russisch?</div><div class="title mt8">${card.phrase.de}</div>
          ${(revealed || checked != null) && html`<div class="mt12"><${IL} ru=${card.phrase.ru} size=${22} gloss=${false} /><div class="mt8"><${Listen} ru=${card.phrase.ru} speed=${speed} label="Ausdruck anhören" /></div></div>`}</div>
        ${checked == null ? html`<textarea class="field" rows="2" lang="ru" value=${text} onInput=${(e) => { setText(e.target.value); setSpoken(false); }} placeholder="Erinnern, sprechen, dann prüfen"></textarea>
            ${recog.available() && html`<button class="btn ghost" onClick=${() => L.toggle(card.phrase.ru)}><${Icon} n=${L.listening ? 'stop_circle' : 'mic'} />${L.listening ? 'Aufnahme beenden' : 'Aus dem Kopf sagen'}</button>`}
            <button class="btn" disabled=${!text.trim() || L.listening} onClick=${() => { const ok = Sp.speech(card.phrase.ru, [text]).passed && !revealed && !archive; setChecked(ok); (ok ? fx.right : fx.wrong)(); }}>Erinnerung prüfen</button>
            ${!revealed && html`<${TextBtn} onClick=${() => setRevealed(true)}>Ich brauche die Lösung<//>`}`
          : html`<div class=${cls('hint')} style=${`border-color:${checked ? 'var(--good)' : 'rgba(var(--bad-rgb),.5)'}`}><p><b>${checked ? 'Aus dem Kopf erinnert!' : 'Noch nicht sicher. Vergleiche den Ausdruck und probiere ihn morgen wieder.'}</b></p>
              <p class="sub mt8">Für den Aufstieg zählt hier selbstständiges Sprechen; getippte Antworten bleiben Übung. Es ist kein Aussprachetest.</p></div><button class="btn" onClick=${next}>Weiter</button>`}`}
    ${cp.cards.length > 0 && html`<${TextBtn} onClick=${() => { setArchive(!archive); if (!archive) setRevealed(true); }}>${archive ? 'Wortschatz verbergen' : 'Wortschatz nachschlagen (zeigt Lösungen)'}<//>
      ${archive && html`<div class="list">${cp.cards.slice(-10).reverse().map((c) => html`<div class="row" style="min-height:0"><div class="tx"><b>${Ph.transliterateFlat(c.phrase.ru)} <span class="cyr" lang="ru">${c.phrase.ru}</span></b><span>${c.phrase.de} · wieder am ${date(c.due)}</span></div><${Listen} ru=${c.phrase.ru} speed=${speed} label="" /></div>`)}</div>`}`}
  </div></div>`;
}

function PlanPage({ cp, setSettings, today }) {
  const s = cp.settings;
  const iso = (d) => new Date(d * 86400000).toISOString().slice(0, 10);
  const plus = (m) => { const d = new Date(); d.setMonth(d.getMonth() + m); return d.toISOString().slice(0, 10); };
  const target = /^\d{4}-\d{2}-\d{2}$/.test(s.targetDate) ? Math.floor(Date.parse(s.targetDate) / 86400000) : NaN;
  const days = target - today;
  const valid = days >= 1 && days <= 3650;
  const studyDays = valid ? Math.floor(days / 7) * s.daysPerWeek + Math.min(days % 7, s.daysPerWeek) : 0;
  const hours = (studyDays * s.minutesPerDay) / 60;
  const secs = cp.activeSecondsByDay[today] ?? 0;
  const de1 = (n) => n.toFixed(1).replace('.', ',');
  const Row = ({ label, value, options, k }) => html`<div><div class="over" style="margin-bottom:6px">${label}</div><div class="rowx g8 wrap">${options.map(([v, t]) => html`<button class=${cls('chip', value === v && 'on')} onClick=${() => setSettings({ [k]: v })}>${t}</button>`)}</div></div>`;
  return html`<div class="scroll"><div class="stepc g16"><div><${Over}>Kleine Schritte, die in deinen Tag passen<//><div class="big mt4">Ein guter Rhythmus für dich.</div><p class="muted mt8" style="line-height:1.5">Wann möchtest du sicherer sprechen? Wir planen deine verfügbare Zeit und schauen, was du später wirklich erinnerst.</p></div>
    <div class="card p stack g16">
      <div><div class="over" style="margin-bottom:6px">Mein Wunschzeitraum</div><div class="rowx g8 wrap">${[[3, '3 Monate'], [6, '6 Monate'], [12, '1 Jahr']].map(([m, t]) => html`<button class=${cls('chip', s.targetDate === plus(m) && 'on')} onClick=${() => setSettings({ targetDate: plus(m) })}>${t}</button>`)}
        <input class="field" style="width:auto;padding:6px 10px;flex:1;min-width:140px" type="date" value=${s.targetDate} onInput=${(e) => setSettings({ targetDate: e.target.value })} /></div>
        ${s.targetDate && !valid && html`<div class="sub bad mt4">Bitte ein zukünftiges Datum wählen, z. B. ${plus(6)}.</div>`}</div>
      <${Row} label="Zeit pro Lerntag" value=${s.minutesPerDay} k="minutesPerDay" options=${[[15, '15 Min'], [30, '30 Min'], [45, '45 Min'], [60, '1 Stunde']]} />
      <${Row} label="Lerntage pro Woche" value=${s.daysPerWeek} k="daysPerWeek" options=${[[3, '3 Tage'], [5, '5 Tage'], [6, '6 Tage'], [7, 'Täglich']]} />
      <${Row} label="Eine kleine Übungsrunde" value=${s.blockMinutes} k="blockMinutes" options=${[[5, '5 Min'], [10, '10 Min'], [20, '20 Min'], [30, '30 Min']]} /></div>
    ${valid && studyDays > 0 && html`<div class="card p tint"><${Over}>Dein Zeitbudget bis zum Wunschdatum<//><div class="big mt4">${de1(hours)} Stunden</div>
      <p class="mt8">${studyDays} Lerntage mit je ${s.minutesPerDay} Minuten. Verteile deine ${Math.ceil(s.minutesPerDay / s.blockMinutes)} Übungsblöcke nach Möglichkeit über den Tag.</p>
      <p class="sub mt8">Diese Rechnung ist dein Kalender, keine Vorhersage, wann du fließend sprichst.</p></div>`}
    <div class="card p"><${Over}>Dein tatsächlicher Fortschritt<//><p class="mt8"><b>Heute ${Math.floor(secs / 60)} Minuten aktiv geübt</b></p><div class="pbar mt8"><i style=${`width:${Math.min(100, (secs / (s.minutesPerDay * 60)) * 100)}%`}></i></div>
      <p class="sub mt8">${cp.cards.filter((c) => c.delayedCorrect > 0).length} Ausdrücke an späteren Tagen erinnert. ${cp.foundation.completed.length} Mini-Gespräche abgeschlossen.</p>
      <p class="sub mt4">Letzte 7 Tage: ${Array.from({ length: 7 }, (_, i) => `${Math.floor((cp.activeSecondsByDay[today - 6 + i] ?? 0) / 60)}m`).join(' · ')}</p></div>
    <div class="hint"><span class="over">Warum hier kein Fließend-Countdown steht</span><p>Studien stützen verteiltes Üben und späteres Abrufen. Sie liefern keine verlässliche Formel für deine persönlichen Stunden bis zur Sprachbeherrschung.${valid && studyDays > 0 ? ` Zur Einordnung: Der FSI-Richtwert von etwa 1.100 Unterrichtsstunden entspräche ${de1(1100 / studyDays)} Stunden pro geplantem Lerntag — das ist kein Lernvorschlag.` : ''}</p></div>
  </div></div>`;
}

function SettingsPage({ cp, setSettings }) {
  return html`<div class="scroll"><div class="stepc g16"><div class="big">So, wie es für dich passt.</div>
    <div><div class="h3" style="margin-bottom:8px">Sprechtempo</div><${Choice} cols=${3} value=${cp.settings.speed} options=${[[0.7, 'Langsam'], [0.85, 'Ruhig'], [1, 'Normal']]} onChange=${(v) => setSettings({ speed: v })} /></div>
    <div class="hint"><span class="over">Du bestimmst, wann du hörst</span><p>Wörter und Fragen werden erst vorgelesen, wenn du auf Anhören tippst. Beim Verlassen der Seite wird der Ton gestoppt.</p></div>
    <div class="hint"><span class="over">Sprechen oder tippen — beides geht</span><p>Die Spracherkennung deines Geräts kann Audio online verarbeiten. Hikari schickt nur den von dir bestätigten Text an die Gesprächs-KI auf deinem Mac. Die ersten Mini-Gespräche benötigen keine KI-Verbindung.</p></div>
  </div></div>`;
}

const EVIDENCE = [
  ['Erst ein Beispiel, dann selbst antworten', 'Du lernst zuerst die Wörter, übst mit einer sichtbaren Vorlage und versuchst es danach selbst. Forschung zu schrittweise reduzierter Beispielhilfe stützt diesen Ansatz beim Fertigkeitserwerb.', 'https://doi.org/10.1037/0022-0663.95.4.774'],
  ['Selbst abrufen und später wiederholen', 'Im gemeinsamen Versuch siehst du die Antwort, danach rufst du sie selbst ab. Gemerkte Ausdrücke kommen an späteren Tagen wieder. Der konkrete Wiederholungsplan ist eine App-Entscheidung, kein erwiesenes Optimum.', 'https://doi.org/10.1111/lang.12479'],
  ['Eine hilfreiche Korrektur, dann selbst versuchen', 'Rückmeldung kann Sprachlernen verbessern. Deshalb gibt es einen gezielten Lernimpuls und einen eigenen Verbesserungsversuch.', 'https://doi.org/10.1017/S0272263109990520'],
  ['Gleiche Szene, neuer Versuch', 'Wiederholte Sprechaufgaben können Ausdruck und Genauigkeit stärken. Der Transfer auf völlig neue Gespräche muss zusätzlich geübt werden.', 'https://doi.org/10.1016/j.system.2025.103868'],
  ['Hören mit dosierter Textstütze', 'Text kann Hörverstehen und Wortlernen unterstützen. Im Einstieg stehen Russisch und Deutsch immer nebeneinander.', 'https://doi.org/10.1016/j.system.2013.07.013'],
  ['Als Anfänger hören und mitlesen', 'Auch eine Studie mit Russischlernenden spricht für Textstützen beim Hörverstehen. Erst danach wird die Antwort verborgen.', 'https://www.lltjournal.org/item/10125-44203/'],
  ['Freude, Interesse und wenig Druck', 'Lernfreude hängt positiv mit Lernleistung zusammen. Das beweist nicht, dass Emotion allein am schnellsten zur Sprachbeherrschung führt.', 'https://doi.org/10.14746/ssllt.2022.12.2.3'],
  ['KI als Übungspartner', 'Aktuelle Forschung zeigt Potenzial, aber auch uneinheitliche Studien und fehlerhafte Rückmeldungen. Beurteile Aussagen kritisch und übe auch mit Menschen.', 'https://doi.org/10.1007/s10791-026-10015-1'],
  ['Spracherkennung ist kein Ausspracheprüfer', 'Ein korrekt erkanntes Wort belegt keine perfekte Aussprache. Dieser Modus bewertet den bestätigten Text; für Laute gibt es das Lautlabor.', 'https://doi.org/10.1017/S0958344023000113'],
  ['Verteilen statt nur anhäufen', 'Eine Metaanalyse von 2025 unterstützt verteilte Übung. Daraus folgt keine universelle Tagesobergrenze und keine Formel für eine persönliche Fließend-Frist.', 'https://doi.org/10.3390/bs15060771'],
];
function EvidencePage() {
  return html`<div class="scroll"><div class="stepc g12"><div class="big">Weniger raten. Mehr erinnern.</div>
    <p class="muted" style="line-height:1.5">Die Lernprinzipien sind erforscht. Genau diese App, ihre KI und ihre Stufenschwellen sind noch nicht durch eine Wirksamkeitsstudie validiert.</p>
    ${EVIDENCE.map(([t, d, url]) => html`<div class="card p"><b class="amber">${t}</b><p class="sub mt8" style="line-height:1.5;color:rgba(255,255,255,.8)">${d}</p>
      <a class="sub" style="display:inline-flex;gap:6px;align-items:center;margin-top:10px;color:var(--amber)" href=${url} target="_blank" rel="noopener"><${Icon} n="open_in_new" size="16" />Studie öffnen</a></div>`)}
    <p class="faint" style="font-size:12px">Recherche: 26.10.2026. Überwiegend Zweitsprachenforschung, oft Englisch; die Übertragung auf Russisch ist begründet, aber nicht direkt bewiesen.</p>
  </div></div>`;
}
