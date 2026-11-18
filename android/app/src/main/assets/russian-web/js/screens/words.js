// Formen eines Wortes: was beim Antippen eines russischen Wortes aufklappt — überall gleich.
// Alle Formen kommen fest aus dem Wörterbuch (js/domain/forms.js, OpenRussian + Wiktionary),
// die Regel in einem Satz wird aus den Formen selbst abgeleitet — nichts davon rät ein Modell.
import { html, cls, Icon, Sheet, TopBar, IL, Latin, mk, useState, useEffect, useMemo, fx, audio } from '../ui.js';
import { progress, nav, back, toast, phraseById, myPhrases } from '../app.js';
import * as F from '../domain/forms.js';
import * as Ph from '../domain/phonetics.js';
import * as Sp from '../domain/speech.js';
import { useListen, Mic } from '../ui.js';
import { speech } from '../platform.js';
import { AskAnja } from './grammar.js';

const PERSONS = [['sg1', 'ich', 'я'], ['sg2', 'du', 'ты'], ['sg3', 'er, sie, es', 'он, он+а, он+о'], ['pl1', 'wir', 'мы'], ['pl2', 'ihr, Sie', 'вы'], ['pl3', 'sie (mehrere)', 'он+и']];
const PAST = [['m', 'er (ein Mann)', 'он'], ['f', 'sie (eine Frau)', 'он+а'], ['n', 'es', 'он+о'], ['pl', 'mehrere', 'он+и']];
/** Die sechs Fälle: zuerst die Frage, die man aus dem Deutschen kennt, das Fachwort klein dahinter. */
export const CASES = [
  ['nom', 'Wer? Was?', 'Nominativ'], ['gen', 'Wessen? Von? Kein …', 'Genitiv'], ['dat', 'Wem?', 'Dativ'],
  ['acc', 'Wen? Was?', 'Akkusativ'], ['inst', 'Womit? Mit wem?', 'Instrumental'], ['prep', 'Wo? Worüber?', 'Präpositiv'],
];
const GENDER = { m: 'männlich', f: 'weiblich', n: 'sächlich', pl: 'nur Mehrzahl', mf: 'männlich und weiblich' };
const POS = { verb: 'Verb', noun: 'Hauptwort', adj: 'Eigenschaftswort', pron: 'Fürwort', num: 'Zahlwort', adv: 'Umstandswort', prep: 'Verhältniswort', conj: 'Bindewort', particle: 'Partikel', intj: 'Ausruf', name: 'Name', other: 'Wort' };
/** Zukunft der unvollendeten Verben: werden + Grundform. */
const BUDU = { sg1: 'б+уду', sg2: 'б+удешь', sg3: 'б+удет', pl1: 'б+удем', pl2: 'б+удете', pl3: 'б+удут' };

const first = (alts) => (alts && alts[0]) || '';
const plain = (ru) => Ph.plain(ru).toLowerCase().replaceAll('ё', 'е');
/** Formen in der Kursstimme (nie die Gerätestimme) — offline vom Handy, wenn schon vertont. */
const say = (ru) => audio.play(null, { text: Ph.plain(ru), ru, key: `wf:${ru}` });

/** Reiter je Wortart. */
function tabsOf(e) {
  if (e.table === 'verb') {
    const t = [];
    if (e.forms.present) t.push(['now', 'Jetzt']);
    t.push(['past', 'Gestern'], ['fut', 'Morgen']);
    if (e.forms.imperative) t.push(['imp', 'Aufforderung']);
    return t;
  }
  if (e.table === 'adj') return [['m', 'männlich'], ['f', 'weiblich'], ['n', 'sächlich'], ['pl', 'Mehrzahl']];
  return [['all', '']];
}

/** Zeilen [Bezeichnung, russische Ansage davor, Form] je Reiter. */
function rowsOf(e, tab) {
  const f = e.forms;
  if (e.table === 'verb') {
    if (tab === 'now') return PERSONS.map(([k, de, ru]) => [de, ru, first(f.present?.[k])]);
    if (tab === 'past') return PAST.map(([k, de, ru]) => [de, ru, first(f.past?.[k])]);
    if (tab === 'imp') return [['zu einer Person', '', first(f.imperative?.sg)], ['zu mehreren, höflich', '', first(f.imperative?.pl)]];
    // Morgen: vollendete Verben haben eine eigene Zukunft, unvollendete »б+уду + Grundform«.
    if (f.future) return PERSONS.map(([k, de, ru]) => [de, ru, first(f.future[k])]);
    return PERSONS.map(([k, de, ru]) => [de, ru, `${BUDU[k]} ${first(f.inf)}`]);
  }
  if (e.table === 'noun') return CASES.map(([k, q, term]) => [q, term, first(f.sg?.[k]), first(f.pl?.[k])]);
  if (e.table === 'adj') return CASES.map(([k, q, term]) => [q, term, (f[tab]?.[k] ?? []).join(' / ')]);
  if (e.table === 'pron') return CASES.map(([k, q, term]) => [q, term, first(f[k])]);
  return [];
}

/** Die Regel in einem Satz — fest aus den Formen abgeleitet (russische Endungen werden zur Umschrift). */
function ruleOf(e, tab) {
  const f = e.forms;
  if (e.table === 'verb') {
    if (tab === 'now') {
      const sg2 = plain(first(f.present?.sg2));
      const sg1 = plain(first(f.present?.sg1));
      const iKind = /ишь$/.test(sg2);
      const base = iKind
        ? 'Die Endung zeigt, wer es tut: я »-ю/-у«, ты »-ишь«, он »-ит«, мы »-им«, вы »-ите«, он+и »-ят/-ат« (i-Beugung).'
        : 'Die Endung zeigt, wer es tut: я »-ю/-у«, ты »-ешь«, он »-ет«, мы »-ем«, вы »-ете«, он+и »-ют/-ут« (e-Beugung).';
      // Ändert sich der Stamm bei »я« (люблю, aber любишь), sagen wir es dazu.
      const stem1 = sg1.slice(0, -1);
      const stem2 = sg2.replace(/(ешь|ёшь|ишь)$/, '');
      return stem1 && stem2 && stem1 !== stem2
        ? `${base} Achtung: Bei »я« ändert sich der Stamm — »${first(f.present.sg1)}«, aber »${first(f.present.sg2)}«.`
        : base;
    }
    if (tab === 'past') return 'Bei »gestern« zählt nicht die Person, sondern wer es ist: ein Mann »-л«, eine Frau »-ла«, es »-ло«, mehrere »-ли«.';
    if (tab === 'imp') return 'Zu einer Person sagst du die kurze Form, zu mehreren oder höflich hängst du »-те« an.';
    return f.future
      ? `Dieses Verb sagt, dass etwas fertig wird. Seine Jetzt-Formen bedeuten deshalb Zukunft: »${first(f.future.sg1)}« heißt »ich werde …«.`
      : 'Morgen: »б+уду, б+удешь …« plus die Grundform — wie »werden« im Deutschen.';
  }
  if (e.table === 'noun') {
    const g = e.gender ? `${e.indeclinable ? '' : ''}Das Wort ist ${GENDER[e.gender] ?? ''}.` : '';
    if (e.indeclinable) return `${g} Es ändert sich nie — in jedem Fall bleibt es »${first(f.sg?.nom) || e.lemma}«.`;
    const sameAcc = plain(first(f.sg?.acc)) === plain(first(f.sg?.nom));
    return `${g} Frag dich, welche Frage das Wort im Satz beantwortet — daran hängt die Endung.${sameAcc && e.gender === 'm' ? ' Bei männlichen Dingen ist die Wen-Form gleich der Grundform.' : ''}`.trim();
  }
  if (e.table === 'adj') return 'Das Eigenschaftswort passt sich an das Wort an, zu dem es gehört: an sein Geschlecht und an die Frage, die es im Satz beantwortet.';
  if (e.table === 'pron') return 'Fürwörter ändern sich ganz — am besten lernst du die Formen als Paare mit ihrer Frage.';
  return '';
}

/** Deine eigenen Sätze, in denen eine Form dieses Wortes vorkommt. */
function ownExamples(e, limit = 3) {
  const keys = new Set(F.allForms(e).map((x) => F.keyOf(x)));
  const p = progress.get();
  const out = [];
  const seen = new Set();
  const consider = (ph) => {
    if (!ph?.ru || seen.has(ph.ru) || out.length >= limit) return;
    if (Ph.wordKeys(ph.ru).some((k) => keys.has(k))) { seen.add(ph.ru); out.push(ph); }
  };
  for (const ph of myPhrases()) consider(ph);
  for (const id of Object.keys(p.cards)) { if (out.length >= limit) break; consider(phraseById(id)); }
  return out;
}

/** Sucht zu einem angetippten Text das passende Wort im Wörterbuch (bei mehreren Wörtern das gehaltvollste). */
export async function lookupText(text) {
  const words = Ph.words(Ph.fromDisplay(text)).map((w) => w.plain).filter(Boolean);
  for (const w of [...words].reverse()) {
    const found = await F.lookup(w).catch(() => []);
    const real = found.filter((x) => x.table !== 'none');
    if (real.length) return { word: w, entries: real };
  }
  const any = words.length ? await F.lookup(words[0]).catch(() => []) : [];
  return { word: words[0] ?? text, entries: any };
}

/** Die Tafel eines Eintrags — in der Formen-Seite und im Blatt beim Antippen. */
export function FormTable({ e, hit }) {
  const tabs = tabsOf(e);
  const [tab, setTab] = useState(tabs[0][0]);
  useEffect(() => setTab(tabsOf(e)[0][0]), [e.id]);
  const rows = rowsOf(e, tab);
  const hitKey = hit ? F.keyOf(hit) : '';
  const isHit = (form) => !!hitKey && form.split(/[ /]+/).some((x) => F.keyOf(x) === hitKey);
  return html`<div class="w-table">
    ${tabs.length > 1 && html`<div class="seg">${tabs.map(([k, l]) => html`<button class=${cls(tab === k && 'on')} onClick=${() => setTab(k)}>${l}</button>`)}</div>`}
    ${e.table === 'noun' && html`<div class="g-tr g-th"><span></span><span>Einzahl</span><span>Mehrzahl</span></div>`}
    <div class=${cls('w-rows', tabs.length > 1 && 'mt12')}>${rows.map((r) => (e.table === 'noun'
      ? html`<div class=${cls('g-tr', (isHit(r[2]) || isHit(r[3])) && 'hl')}><span class="gq">${r[0]}<em>${r[1]}</em></span>
          ${[r[2], r[3]].map((form) => html`<button class="w-cell" disabled=${!form} onClick=${() => say(form)}>${form ? html`<b><${Latin} ru=${form} /></b><em lang="ru">${Ph.display(form)}</em>` : '—'}</button>`)}</div>`
      : html`<button class=${cls('w-row', isHit(r[2]) && 'hl')} disabled=${!r[2]} onClick=${() => say(r[1] && e.table === 'verb' ? `${r[1].split(',')[0]} ${r[2]}` : r[2])}>
          <span class="p">${r[0]}${r[1] && e.table !== 'verb' ? html`<em>${r[1]}</em>` : ''}</span>
          <span class="f">${r[2] ? html`<b>${e.table === 'verb' && r[1] ? html`<span class="faint"><${Latin} ru=${r[1].split(',')[0]} /> </span>` : ''}<${Latin} ru=${r[2]} /></b><em lang="ru">${Ph.display(r[2])}</em>` : '—'}</span>
          <${Icon} n="volume_up" c="faint" size="19" /></button>`))}</div>
    ${e.table === 'adj' && e.forms.short && tab !== 'pl' && html`<div class="sub mt8">Kurzform: <b><${Latin} ru=${first(e.forms.short[tab])} /></b></div>`}
    <div class="g-rule mt12">${mk(ruleOf(e, tab))}</div>
  </div>`;
}

/** Kopf eines Eintrags: Wort, Bedeutung, Wortart, Gegenstück. */
function Head({ e }) {
  return html`<div class="w-head">
    <div class="w-word"><${Latin} ru=${e.lemma} /></div><div class="cyr" lang="ru">${Ph.display(e.lemma)}</div>
    <div class="w-mean">${(e.de.length ? e.de : e.en ?? []).slice(0, 4).join(', ') || '—'}</div>
    <div class="rowx g8 wrap mt8"><span class="tag c-words">${POS[e.pos] ?? 'Wort'}</span>
      ${e.gender && html`<span class="tag">${GENDER[e.gender]}</span>`}
      ${e.aspect && html`<span class="tag">${e.aspect === 'pf' ? 'wird fertig (vollendet)' : e.aspect === 'impf' ? 'läuft, immer wieder (unvollendet)' : 'beides'}</span>`}
      ${e.partner?.length > 0 && html`<span class="tag"><span>Gegenstück: <${Latin} ru=${e.partner[0]} /></span></span>`}</div></div>`;
}

/** Die Tafel als Text — Grundlage für Anjas Antwort auf eine Nachfrage zu diesem Wort. */
function referenceOf(e) {
  const p = (x) => x.replaceAll('+', '');
  const lines = [`Wort: «${p(e.lemma)}» (${POS[e.pos] ?? 'Wort'}${e.gender ? `, ${GENDER[e.gender]}` : ''}${e.aspect ? `, ${e.aspect === 'pf' ? 'vollendet' : 'unvollendet'}` : ''}) — ${(e.de ?? []).slice(0, 3).join(', ')}`];
  for (const [k] of tabsOf(e)) {
    const rows = rowsOf(e, k).map((r) => `${r[0]}: ${r.slice(2).filter(Boolean).map(p).join(' / ')}`).join('; ');
    if (rows) lines.push(rows);
    const rule = ruleOf(e, k);
    if (rule) lines.push(`Regel: ${rule.replaceAll('+', '')}`);
  }
  return lines.join('\n');
}

/** Was beim Antippen eines Wortes aufklappt. */
export function WordSheet({ ru, onClose }) {
  const [found, setFound] = useState(null);
  const [pick, setPick] = useState(0);
  const [ask, setAsk] = useState(false);
  useEffect(() => { let alive = true; lookupText(ru).then((x) => alive && setFound(x)).catch(() => alive && setFound({ word: ru, entries: [] })); return () => { alive = false; }; }, [ru]);
  const e = found?.entries[pick];
  const own = useMemo(() => (e ? ownExamples(e) : []), [e?.id]);
  if (ask && e) return html`<${AskAnja} topic=${`das Wort »${Ph.plain(e.lemma)}« und seine Formen`} reference=${referenceOf(e)} onClose=${() => setAsk(false)} />`;
  return html`<${Sheet} onClose=${onClose}>
    ${!found ? html`<div class="sub" style="padding:24px 0;text-align:center">Formen werden nachgeschlagen …</div>`
      : !e ? html`<div class="empty" style="padding:16px 8px"><${Icon} n="search_off" /><b>Nicht im Wörterbuch</b>»${found.word}« ist vermutlich ein Name oder eine Abkürzung.</div>`
      : html`<div class="w-sheet">
        ${found.entries.length > 1 && html`<div class="chips" style="padding:0 0 10px">${found.entries.map((x, i) => html`<button class=${cls('chip c sm', pick === i && 'on')} onClick=${() => setPick(i)}>${POS[x.pos]} · ${(x.de[0] ?? x.en?.[0] ?? '').slice(0, 18)}</button>`)}</div>`}
        <${Head} e=${e} />
        ${e.table !== 'none' && html`<div class="mt16"><${FormTable} e=${e} hit=${found.word} /></div>`}
        ${own.length > 0 && html`<div class="over mt16">In deinen Sätzen</div><div class="list mt8">${own.map((p) => html`<div class="row" style="min-height:0"><div class="tx"><${IL} ru=${p.ru} size=${15} gloss=${false} /><span>${p.de}</span></div></div>`)}</div>`}
        <div class="rowx g8 mt16">
          <button class="btn ghost" onClick=${() => setAsk(true)}><${Icon} n="forum" />Anja fragen</button>
          ${e.table !== 'none' && html`<button class="btn area c-words" onClick=${() => { onClose(); nav(`formen/${encodeURIComponent(Ph.plain(e.lemma))}`); }}><${Icon} n="quiz" />Formen üben</button>`}</div>
      </div>`}
  <//>`;
}

// ── Formen üben ────────────────────────────────────────────────────────────

const shuffle = (a) => { const x = [...a]; for (let i = x.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [x[i], x[j]] = [x[j], x[i]]; } return x; };

/** Aufgaben aus der Tafel: Form bilden (wählen), Form erkennen, Form sprechen. */
export function drillItems(e, n = 8) {
  const out = [];
  if (e.table === 'verb' && (e.forms.present || e.forms.future)) {
    const set = e.forms.present ?? e.forms.future;
    const cells = PERSONS.map(([k, de, ru]) => ({ de, ru: ru.split(',')[0], form: first(set[k]) })).filter((c) => c.form);
    for (const c of shuffle(cells)) {
      const others = shuffle(cells.filter((o) => plain(o.form) !== plain(c.form))).slice(0, 2).map((o) => o.form);
      out.push({ type: 'build', ask: `${c.de} + ${Ph.plain(e.lemma)}`, lead: c.ru, answer: c.form, options: shuffle([c.form, ...others]) });
      out.push({ type: 'say', de: `Sag die Form: »${c.de}« + »${(e.de[0] ?? Ph.plain(e.lemma)).split(/[,;]/)[0].trim()}«`, ru: `${c.ru} ${c.form}` });
    }
  } else if (e.table === 'noun' || e.table === 'pron') {
    const get = (k) => first(e.table === 'noun' ? e.forms.sg?.[k] : e.forms[k]);
    const cells = CASES.map(([k, q]) => ({ q, form: get(k) })).filter((c) => c.form);
    for (const c of shuffle(cells)) {
      const others = shuffle([...new Set(cells.map((o) => o.form).filter((f) => plain(f) !== plain(c.form)))]).slice(0, 2);
      out.push({ type: 'build', ask: `${c.q} → ${Ph.plain(e.lemma)}`, lead: '', answer: c.form, options: shuffle([c.form, ...others]) });
      const qs = shuffle([...new Set(cells.filter((o) => plain(o.form) === plain(c.form)).map((o) => o.q))]);
      const wrongQ = shuffle(cells.filter((o) => plain(o.form) !== plain(c.form)).map((o) => o.q)).slice(0, 2);
      out.push({ type: 'spot', form: c.form, answer: qs[0], accept: qs, options: shuffle([qs[0], ...wrongQ]) });
    }
  } else if (e.table === 'adj') {
    for (const g of ['m', 'f', 'n', 'pl']) {
      const form = first(e.forms[g]?.nom);
      if (!form) continue;
      const others = ['m', 'f', 'n', 'pl'].filter((x) => x !== g).map((x) => first(e.forms[x]?.nom)).filter(Boolean).slice(0, 2);
      out.push({ type: 'build', ask: `${GENDER[g] === 'nur Mehrzahl' ? 'Mehrzahl' : GENDER[g]} → ${Ph.plain(e.lemma)}`, lead: '', answer: form, options: shuffle([form, ...others]) });
    }
  }
  // Abwechselnd die Aufgabenarten, höchstens n.
  return out.slice(0, n);
}

function SayItem({ item, onDone }) {
  const [level, setLevel] = useState(0);
  const [sc, setSc] = useState(null);
  const L = useListen((hyps) => { const r = Sp.speech(item.ru, hyps); setSc(r); if (r.passed) { fx.right(); setTimeout(() => onDone(true, level > 0), 700); } else fx.wrong(); });
  return html`<div class="g-say"><div class="scard-ask" style="font-size:20px">${item.de}</div>
    ${level >= 1 || sc ? html`<${IL} ru=${item.ru} size=${22} gloss=${false} center />` : html`<div class="hidden-hint sub">Sprich die passende Form mit dem Fürwort.</div>`}
    ${speech.available()
      ? html`<${Mic} L=${L} onClick=${() => (L.listening ? L.stop() : (setSc(null), L.start(item.ru)))} />${sc && !sc.passed && html`<div class="sub">Noch nicht ganz — versuch es noch einmal.</div>`}`
      : html`<button class="btn" onClick=${() => onDone(true, level > 0)}>Laut gesagt</button>`}
    ${level === 0 && !sc?.passed && html`<button class="chip sm spick" onClick=${() => setLevel(1)}><${Icon} n="visibility" />Spicker</button>`}
  </div>`;
}

export function FormDrill({ lemma }) {
  const [e, setE] = useState(null);
  const [items, setItems] = useState(null);
  const [i, setI] = useState(0);
  const [pick, setPick] = useState(null);
  const [right, setRight] = useState(0);
  useEffect(() => { F.entry(lemma).then((list) => { const x = (Array.isArray(list) ? list : [list]).find(Boolean); setE(x ?? null); setItems(x ? drillItems(x) : []); }).catch(() => setItems([])); }, [lemma]);
  const close = () => back('grammatik');
  if (!items) return html`<div class="screen c-words"><${TopBar} title="Formen üben" onBack=${close} /></div>`;
  if (!e || !items.length) return html`<div class="screen c-words"><${TopBar} title="Formen üben" onBack=${close} /><div class="empty"><${Icon} n="quiz" /><b>Zu diesem Wort gibt es nichts zu üben.</b></div></div>`;
  if (i >= items.length) {
    return html`<div class="screen c-words"><${TopBar} title="Formen üben" onBack=${close} />
      <div class="scroll"><div class="stepc center g16" style="justify-content:center;min-height:100%">
        <div class="stagedone"><${Icon} n="check" /></div><div class="title">${right} von ${items.length} richtig</div>
        <p class="sub">Die Formen von <b><${Latin} ru=${e.lemma} /></b> sitzen jetzt besser. Morgen noch einmal — dann bleiben sie.</p></div></div>
      <div class="bottom"><button class="btn" onClick=${close}>Fertig</button></div></div>`;
  }
  const it = items[i];
  const next = (ok) => { if (ok) setRight((r) => r + 1); setPick(null); if (i + 1 >= items.length) progress.apply({ xp: right * 2 + 5, graded: {} }); setI(i + 1); };
  const choose = (o) => { if (pick != null) return; setPick(o); (o === it.answer || it.accept?.includes(o) ? fx.right : fx.wrong)(); };
  const ok = pick != null && (pick === it.answer || it.accept?.includes(pick));
  return html`<div class="screen c-words">
    <div class="g-top"><button class="iconbtn" onClick=${close} aria-label="Schließen"><${Icon} n="close" /></button>
      <div class="g-dots">${items.map((_, k) => html`<i class=${cls(k < i && 'done', k === i && 'on')}></i>`)}</div></div>
    <div class="scroll"><div class="pad"><div class="g-screen" key=${i}>
      ${it.type === 'build' && html`<div class="over c">Bilde die Form</div><div class="title mt4">${it.ask}</div>
        <div class="stack g8 mt16">${it.options.map((o) => html`<button class=${cls('opt', pick != null && (o === it.answer ? 'ok' : o === pick ? 'bad' : 'dim'))} onClick=${() => choose(o)}>
          ${it.lead && html`<span class="faint"><${Latin} ru=${it.lead} /></span>`}<b><${Latin} ru=${o} /></b></button>`)}</div>`}
      ${it.type === 'spot' && html`<div class="over c">Erkenne die Form</div><div class="title mt4"><${Latin} ru=${it.form} /></div>
        <div class="cyr" lang="ru">${Ph.display(it.form)}</div><div class="sub mt8">Auf welche Frage antwortet diese Form?</div>
        <div class="stack g8 mt16">${it.options.map((o) => html`<button class=${cls('opt', pick != null && (it.accept.includes(o) ? 'ok' : o === pick ? 'bad' : 'dim'))} onClick=${() => choose(o)}>${o}</button>`)}</div>`}
      ${it.type === 'say' && html`<div class="over c">Sprich die Form</div><${SayItem} item=${it} onDone=${(good) => next(good)} key=${i} />`}
      ${pick != null && html`<div class=${cls('g-why', ok ? 'ok' : 'warn')}>${ok ? 'Genau.' : html`Richtig ist <b><${Latin} ru=${it.answer} /></b>.`}</div>`}
    </div></div></div>
    ${it.type !== 'say' && html`<div class="bottom"><button class="btn" disabled=${pick == null} onClick=${() => next(ok)}>Weiter</button></div>`}
  </div>`;
}
