// Entwürfe für den großen Ausbau (Bauplan 18.11.2026): Anja als Lehrerin, Grammatik, Offline.
// Mit Beispieldaten, damit Aufbau und Bedienung vor dem Bauen beurteilt werden können —
// zugeklappt und aufgeklappt auf dem Fold. Lieder & Szenen »Entdecken« liegt in erleben.js.
import { html, cls, Icon, TopBar, IL, Latin, Sheet, HiddenRu, useState, useEffect, fx } from '../ui.js';
import { nav, back, toast } from '../app.js';
import * as Ph from '../domain/phonetics.js';

const cyr = (ru) => Ph.display(ru);
/** Aufgeklappt (Fold) zeigt der Bereich zwei Spalten — dann öffnet ein Tipp rechts statt einer neuen Seite. */
const isWide = () => (document.querySelector('.app')?.clientWidth ?? 0) >= 720;

// ── Übersicht der Entwürfe ─────────────────────────────────────────────────

const DRAFTS = [
  ['entwurf/anja', 'c-speak', 'school', 'Anja als Lehrerin', 'Korrektur sofort nach dem Satz, Erklärung auf Deutsch, noch mal sagen, Stolpersteine'],
  ['entwurf/grammatik', 'c-words', 'account_tree', 'Grammatik', 'Lernpfad vom Deutschen her, deine Stolpersteine, Formen nachschlagen'],
  ['entwurf/lektion', 'c-words', 'menu_book', 'Grammatik-Lektion', 'Eine Idee pro Bildschirm, gleich danach üben — am Beispiel Wen-Fall'],
  ['entwurf/wort', 'c-words', 'table_view', 'Formen eines Wortes', 'Was beim Antippen eines Wortes aufklappt: alle Formen mit Betonung'],
  ['entwurf/entdecken', 'c-live', 'explore', 'Lieder & Szenen entdecken', 'Heute neu, Reihen je Thema, Filter, Bewerten — mit deinen echten Stücken'],
  ['entwurf/offline', 'c-review', 'cloud_off', 'Offline', 'Was gerade geht und was nicht, Anja ohne Netz, Laden für unterwegs'],
];

export function DraftIndex() {
  return html`<div class="screen">
    <${TopBar} title="Entwürfe" sub="Zum Anschauen auf dem Fold — zu- und aufgeklappt" onBack=${() => back('heute')} />
    <div class="scroll"><div class="sec" style="padding-top:8px">
      <div class="sub" style="margin:0 4px 14px;line-height:1.5">Alles hier ist ein Entwurf mit Beispieldaten. Tippen, ausprobieren, und mir dann sagen, was passt und was nicht.</div>
      <div class="dr-grid">${DRAFTS.map(([to, c, icon, t, d]) => html`<button class=${cls('dr-card', c)} onClick=${() => nav(to)}>
        <span class="ic solid"><${Icon} n=${icon} /></span><b>${t}</b><span>${d}</span></button>`)}</div>
    </div><div class="end"></div></div></div>`;
}

// ── Anja als Lehrerin ──────────────────────────────────────────────────────

const STONES = [
  { t: 'Wen-Fall nach »wollen«', ex: ['вод+а', 'в+оду'], n: 4, last: 'heute' },
  { t: 'Endung bei »du«', ex: ['ты л+юбит', 'ты л+юбишь'], n: 3, last: 'gestern' },
  { t: 'Wo-Fall nach »w« (in)', ex: ['в Берл+ин', 'в Берл+ине'], n: 2, last: 'vor 3 Tagen' },
];

const VODA = [
  ['Wer, was?', 'вод+а', 'в+оды'], ['Wessen? kein …', 'вод+ы', 'вод'], ['Wem?', 'вод+е', 'в+одам'],
  ['Wen, was?', 'в+оду', 'в+оды'], ['Womit?', 'вод+ой', 'в+одами'], ['Wo? worüber?', 'о вод+е', 'о в+одах'],
];

function Bars({ on }) {
  return html`<span class=${cls('t-bars', on && 'on')}><i></i><i></i><i></i></span>`;
}

/** Fehlerhaftes Wort im eigenen Satz markieren. */
function MySentence({ words, wrong }) {
  return html`<div class="t-mine">${words.map((w, i) => html`${i ? ' ' : ''}<span class=${cls(i === wrong && 'bad')}><${Latin} ru=${w} /></span>`)}</div>`;
}

function FormTable({ rows, head = ['', 'Einzahl', 'Mehrzahl'], hl }) {
  return html`<div class="g-table" role="table">
    <div class="g-tr g-th">${head.map((h) => html`<span>${h}</span>`)}</div>
    ${rows.map((r, i) => html`<div class=${cls('g-tr', i === hl && 'hl')}><span class="gq">${r[0]}</span>${r.slice(1).map((f) => html`<span><b><${Latin} ru=${f} /></b><em lang="ru">${cyr(f)}</em></span>`)}</div>`)}
  </div>`;
}

function Stones({ compact }) {
  return html`<div class="t-stones">${STONES.map((s) => html`<div class="t-stone">
    <span class="n num">${s.n}×</span>
    <span class="grow"><b>${s.t}</b><span class="ex"><s><${Latin} ru=${s.ex[0]} /></s> → <${Latin} ru=${s.ex[1]} /></span></span>
    ${!compact && html`<span class="when">${s.last}</span>`}</div>`)}</div>`;
}

/** Noch einmal sagen: nur Deutsch, Spicker, Versuche. */
function SayAgain({ onDone }) {
  const [level, setLevel] = useState(0);
  const [tries, setTries] = useState(0);
  const [ok, setOk] = useState(false);
  const attempt = () => { const t = tries + 1; setTries(t); if (t >= 2) { setOk(true); fx.right(); onDone?.(); } else { fx.wrong(); } };
  return html`<div class=${cls('t-again', ok && 'ok')}>
    <div class="rowx" style="justify-content:space-between"><span class="over c">Jetzt du — noch einmal</span>
      <span class="t-tries">${[0, 1, 2].map((i) => html`<i class=${cls(i < tries && (ok && i === tries - 1 ? 'ok' : 'used'))}></i>`)}</span></div>
    <div class="scard-ask" style="font-size:18px">Ich will Wasser.</div>
    ${ok ? html`<${IL} ru="Я хоч+у в+оду." size=${19} gloss=${false} />` : html`<${HiddenRu} ru="Я хоч+у в+оду." level=${level} size=${19} center=${false} />`}
    ${!ok && html`<div class="rowx g8 wrap">
      ${level < 3 && html`<button class="chip sm spick" onClick=${() => setLevel(level + 1)}><${Icon} n="visibility" />Spicker · ${['1. Wort', 'Wortanfänge', 'Ganzer Satz'][level]}</button>`}
      <button class="t-mic" onClick=${attempt} aria-label="Sprechen"><${Icon} n="mic" fill /></button></div>
      ${tries === 1 && html`<div class="t-note">Fast — achte auf das Ende von <b><${Latin} ru="в+оду" /></b>. Noch zwei Versuche.</div>`}`}
    ${ok && html`<div class="t-note good"><${Icon} n="check_circle" fill size="16" />Sitzt. Der Satz kommt in deine Wiederholung.</div>`}
  </div>`;
}

/** Was Anja nach einem Fehler macht — in der Reihenfolge, wie sie spricht. */
function TeacherCard({ speaking, onTable }) {
  return html`<div class="t-card">
    <div class="t-head"><span class="ic sm solid"><${Icon} n="school" /></span>
      <span class="grow"><b>Anja erklärt</b><span>auf Deutsch, weil du gerade bei A1 bist</span></span><${Bars} on=${speaking} /></div>
    <div class="t-praise"><${Icon} n="thumb_up" fill size="16" /><span>Der Anfang ist genau richtig: <b><${Latin} ru="Я хоч+у" /></b> heißt »ich will«.</span></div>
    <div class="t-fix"><div class="k">So heißt es</div>
      <div class="t-swap"><s><${Latin} ru="вод+а" /></s><${Icon} n="arrow_forward" size="18" /><b><${Latin} ru="в+оду" /></b></div>
      <${IL} ru="Я хоч+у в+оду." size=${19} gloss=${false} /></div>
    <div class="t-why"><div class="k">Warum</div>
      <p>Was du willst, bekommt etwas ab — im Deutschen sagst du auch »ich will <u>den</u> Saft«. Im Russischen ändert sich dafür das Wort selbst: Endet es auf <b>-a</b>, wird daraus <b>-u</b>.</p>
      <p class="term">Das ist der <b>Wen-Fall</b> (Akkusativ).</p></div>
    <div class="t-ex"><div class="k">Noch ein Beispiel</div>
      <div class="rowx g10"><span class="grow"><${IL} ru="Я хоч+у п+иццу." size=${16} gloss=${false} /><span class="sub">Ich will Pizza.</span></span>
        <button class="iconbtn" aria-label="Anhören"><${Icon} n="volume_up" /></button></div></div>
    <div class="rowx g8 wrap"><button class="chip sm c-words" onClick=${onTable}><${Icon} n="table_view" /><span>Alle Formen von <${Latin} ru="вод+а" /></span></button>
      <button class="chip sm"><${Icon} n="replay" />Nochmal hören</button></div>
  </div>`;
}

function AnjaSide({ onLesson }) {
  return html`<div class="t-side">
    <div class="sec" style="padding-top:14px"><div class="h2">Formen von <${Latin} ru="вод+а" /></div>
      <div class="sub">Wasser · weiblich. Gerade gebraucht: Wen, was?</div>
      <${FormTable} rows=${VODA} hl=${3} /></div>
    <div class="sec"><div class="hd"><div class="h2">Deine Stolpersteine</div></div>
      <div class="sub">Anja merkt sich, was dir öfter passiert, und baut es in die nächsten Gespräche ein.</div>
      <${Stones} />
      <button class="btn tonal sm mt12 c-words" style="width:100%" onClick=${onLesson}><${Icon} n="menu_book" />Kurze Lektion: der Wen-Fall</button></div>
  </div>`;
}

export function DraftAnja() {
  // Ablauf zum Ansehen: dein Satz → »Moment« → Anja erklärt → du sagst es noch einmal → weiter auf Russisch.
  const [phase, setPhase] = useState(0);
  const [sheet, setSheet] = useState(null);
  useEffect(() => {
    if (phase === 0) { const t = setTimeout(() => setPhase(1), 900); return () => clearTimeout(t); }
    if (phase === 1) { const t = setTimeout(() => setPhase(2), 2600); return () => clearTimeout(t); }
    return undefined;
  }, [phase]);
  const chat = html`<div class="t-chat">
    <div class="t-level"><${Icon} n="signal_cellular_alt" size="16" />Dein Niveau: A1 · Anja hilft dir noch viel auf Deutsch</div>
    <div class="bub al-hers"><div class="who">ANJA</div><${IL} ru="Что ты х+очешь пить?" size=${19} /><div class="de">Was möchtest du trinken?</div></div>
    <div class="bub me"><div class="who">DU</div><${MySentence} words=${['Я', 'хоч+у', 'вод+а.']} wrong=${phase >= 2 ? 2 : -1} /></div>
    ${phase === 1 && html`<div class="t-wait"><${Icon} n="progress_activity" c="spin" size="18" />Moment, ich schau mir das an …</div>`}
    ${phase >= 2 && html`<${TeacherCard} speaking=${phase === 2} onTable=${() => setSheet('table')} />`}
    ${phase >= 2 && html`<${SayAgain} onDone=${() => setTimeout(() => setPhase(3), 900)} />`}
    ${phase >= 3 && html`<div class="bub al-hers"><div class="who">ANJA · zurück auf Russisch</div>
      <${IL} ru="Отл+ично! А как+ую в+оду ты л+юбишь?" size=${19} /><div class="de">Super! Und welches Wasser magst du?</div></div>`}
  </div>`;
  return html`<div class="screen c-speak">
    <${TopBar} title="Anja" sub="Gesprächspartnerin und Lehrerin" onBack=${() => back('entwurf')}
      right=${html`<button class="iconbtn" onClick=${() => setPhase(0)} aria-label="Ablauf neu abspielen"><${Icon} n="replay" /></button>
        <button class="iconbtn duo-hide" onClick=${() => setSheet('stones')} aria-label="Stolpersteine"><${Icon} n="psychology_alt" /></button>`} />
    <div class="duo">
      <div class="pane scroll">${chat}<div class="end"></div></div>
      <div class="pane scroll duo-only"><${AnjaSide} onLesson=${() => nav('entwurf/lektion')} /><div class="end"></div></div>
    </div>
    <div class="bottom t-bottom"><div class="rowx g10">
      <button class="btn ghost" style="width:auto;flex:none;padding:0 16px"><${Icon} n="keyboard" /></button>
      <button class="btn c-speak area"><${Icon} n="mic" fill />Gedrückt halten und sprechen</button></div>
      <button class="t-help" onClick=${() => toast('Anja hilft dann auf Deutsch weiter')}><${Icon} n="help" size="17" />Ich komme nicht weiter</button></div>
    ${sheet === 'table' && html`<${Sheet} title="Formen von вода" onClose=${() => setSheet(null)}><${FormTable} rows=${VODA} hl=${3} /><//>`}
    ${sheet === 'stones' && html`<${Sheet} title="Deine Stolpersteine" onClose=${() => setSheet(null)}><${Stones} />
      <button class="btn tonal sm mt16 c-words" style="width:100%" onClick=${() => nav('entwurf/lektion')}><${Icon} n="menu_book" />Kurze Lektion: der Wen-Fall</button><//>`}
  </div>`;
}

// ── Grammatik ──────────────────────────────────────────────────────────────

const PATH = [
  { q: 'Wer macht was?', t: 'Verben je Person', term: 'Gegenwart', st: 'done' },
  { q: 'Wen oder was?', t: 'Das, was etwas abbekommt', term: 'Akkusativ', st: 'now', part: 3, of: 6 },
  { q: 'Wo?', t: 'Orte und Themen', term: 'Präpositiv' },
  { q: 'Wessen? Von wem? Kein …', t: 'Besitz, Mengen, Verneinung', term: 'Genitiv' },
  { q: 'Was war?', t: 'Über gestern reden', term: 'Vergangenheit' },
  { q: 'Wem?', t: 'Jemandem etwas geben', term: 'Dativ' },
  { q: 'Womit? Mit wem?', t: 'Werkzeug und Begleitung', term: 'Instrumental' },
  { q: 'Einmal oder immer wieder?', t: 'Verbpaare', term: 'Aspekt' },
  { q: 'Wie ist es?', t: 'Eigenschaftswörter', term: 'Adjektive' },
];

function PathList({ sel, onSel }) {
  return html`<ol class="g-path">${PATH.map((s, i) => html`<li>
    <button class=${cls('g-step', s.st, sel === i && 'sel')} onClick=${() => onSel(i)}>
      <span class="dot">${s.st === 'done' ? html`<${Icon} n="check" size="18" />` : i + 1}</span>
      <span class="grow"><b>${s.q}</b><span>${s.t} · <em>${s.term}</em></span>
        ${s.st === 'now' && html`<span class="bar"><i style=${`width:${(s.part / s.of) * 100}%`}></i></span>`}</span>
      ${s.st === 'now' && html`<span class="go">Weiter</span>`}</button></li>`)}</ol>`;
}

function Lookup() {
  return html`<div class="g-look">
    <div class="g-search"><${Icon} n="search" /><span>Wort suchen, deutsch oder russisch</span></div>
    <div class="sub mt8">Zuletzt nachgeschlagen</div>
    <div class="rowx g8 wrap mt8">${['люб+ить', 'вод+а', 'хот+еть', 'д+ом'].map((w) => html`<button class="chip sm" onClick=${() => nav('entwurf/wort')}><span><${Latin} ru=${w} /></span></button>`)}</div></div>`;
}

export function DraftGrammar() {
  const [sel, setSel] = useState(1);
  const s = PATH[sel];
  return html`<div class="screen c-words">
    <${TopBar} title="Grammatik" sub="Verstehen, warum Wörter sich ändern" onBack=${() => back('entwurf')} />
    <div class="duo">
      <div class="pane scroll">
        <div class="sec" style="padding-top:6px"><button class="g-hero" onClick=${() => nav('entwurf/lektion')}>
          <span class="k">Weiter mit Schritt 2 von 9</span><b>Wen oder was?</b>
          <span class="d">Warum aus <${Latin} ru="вод+а" /> plötzlich <${Latin} ru="в+оду" /> wird. Noch etwa 4 Minuten.</span>
          <span class="bar"><i style="width:50%"></i></span>
          <span class="go"><${Icon} n="play_arrow" fill />Weiterlernen</span></button></div>
        <div class="sec"><div class="h2">Dein Weg</div>
          <div class="sub">Jeder Schritt beginnt bei einer Frage, die du aus dem Deutschen kennst. Das Fachwort kommt erst danach.</div>
          <${PathList} sel=${sel} onSel=${(i) => (isWide() ? setSel(i) : nav('entwurf/lektion'))} /></div>
        <div class="sec duo-hide"><div class="h2">Deine Stolpersteine</div><div class="sub">Aus deinen Gesprächen mit Anja.</div><${Stones} compact /></div>
        <div class="sec duo-hide"><div class="h2">Formen nachschlagen</div><${Lookup} /></div>
        <div class="end"></div>
      </div>
      <div class="pane scroll duo-only">
        <div class="sec" style="padding-top:14px"><div class="over c">Schritt ${sel + 1} · ${s.term}</div><div class="title mt4">${s.q}</div>
          <p class="g-lead">${s.t}. Vier kurze Bildschirme: was du aus dem Deutschen kennst, was im Russischen anders ist, eine Eselsbrücke, dann üben.</p>
          <button class="btn area mt12" onClick=${() => nav('entwurf/lektion')}><${Icon} n="play_arrow" fill />${s.st === 'done' ? 'Wiederholen' : 'Starten'}</button></div>
        <div class="sec"><div class="h2">Deine Stolpersteine</div><div class="sub">Aus deinen Gesprächen mit Anja.</div><${Stones} compact /></div>
        <div class="sec"><div class="h2">Formen nachschlagen</div><${Lookup} /></div>
        <div class="end"></div>
      </div>
    </div></div>`;
}

// ── Grammatik-Lektion: eine Idee pro Bildschirm ───────────────────────────

const LESSON = [
  { k: 'Kennst du schon', title: 'Wen oder was?', body: html`
      <div class="g-de"><span>D<u>er</u> Hund schläft.</span><span class="ar"><${Icon} n="south" size="18" /></span><span>Ich sehe d<u>en</u> Hund.</span></div>
      <p>Im Deutschen ändert sich nur das kleine Wort davor: <b>der</b> wird zu <b>den</b>. Es zeigt: Der Hund tut nichts, er wird gesehen.</p>
      <p class="muted">Frag dich einfach: <b>Wen oder was</b> sehe ich? Die Antwort ist das Wort, das sich ändert.</p>` },
  { k: 'Im Russischen', title: 'Das Wort selbst ändert sich', body: html`
      <div class="g-pairs">${[['Э+то вод+а.', 'Я пь+ю в+оду.', 'Ich trinke Wasser.'], ['Э+то м+узыка.', 'Я любл+ю м+узыку.', 'Ich liebe Musik.'], ['Э+то с+ок.', 'Я пь+ю с+ок.', 'Ich trinke Saft.']]
        .map(([a, b, de]) => html`<div class="g-pair"><span><${Latin} ru=${a} /></span><${Icon} n="arrow_forward" size="16" /><span><b><${Latin} ru=${b} /></b><em>${de}</em></span></div>`)}</div>
      <div class="g-rule"><b>Regel:</b> Endet das Wort auf <b>-a</b>, wird daraus <b>-u</b>. Auf <b>-ja</b> wird <b>-ju</b>. Männliche Dinge wie <${Latin} ru="с+ок" /> bleiben, wie sie sind.</div>` },
  { k: 'Eselsbrücke', title: 'Was du nimmst, kippt zum U', body: html`
      <div class="g-memo"><b>a → u</b><span>Wenn du etwas nimmst, willst, siehst oder liebst, kippt das a am Ende zum u.</span></div>
      <p class="muted">Und das Fachwort dafür: <b>Akkusativ</b>. Merk dir einfach <b>Akkusativ = Wen-Fall</b>.</p>` },
  { k: 'Üben', title: 'Welche Form passt?', quiz: { de: 'Ich liebe Musik.', stem: ['Я любл+ю', '.'], opts: ['м+узыка', 'м+узыку', 'м+узыке'], ok: 1, why: 'Was liebst du? Die Musik — sie bekommt die Liebe ab, also Wen-Fall: a → u.' } },
  { k: 'Sprechen', title: 'Sag es auf Russisch', say: { de: 'Ich trinke Wasser.', ru: 'Я пь+ю в+оду.' } },
  { k: 'Aus deinem Gespräch', title: 'Jetzt weißt du, warum', body: html`
      <div class="g-own"><span class="k">Mit Anja, heute</span><div class="t-swap"><s><${Latin} ru="Я хоч+у вод+а." /></s></div><div class="t-swap"><b><${Latin} ru="Я хоч+у в+оду." /></b></div>
      <p>Was willst du? Das Wasser — es bekommt etwas ab. Also Wen-Fall: a → u.</p></div>` },
];

function Quiz({ q }) {
  const [pick, setPick] = useState(null);
  return html`<div class="g-quiz">
    <div class="sub">${q.de}</div>
    <div class="g-stem"><${Latin} ru=${q.stem[0]} /> <span class=${cls('hole', pick != null && 'filled')}>${pick != null ? html`<${Latin} ru=${q.opts[pick]} />` : ''}</span>${q.stem[1]}</div>
    <div class="stack g8 mt12">${q.opts.map((o, i) => html`<button class=${cls('opt', pick != null && (i === q.ok ? 'ok' : i === pick ? 'bad' : 'dim'))} onClick=${() => { if (pick == null) { setPick(i); (i === q.ok ? fx.right : fx.wrong)(); } }}><${Latin} ru=${o} /></button>`)}</div>
    ${pick != null && html`<div class=${cls('g-why', pick === q.ok ? 'ok' : 'warn')}>${pick === q.ok ? 'Genau.' : 'Fast.'} ${q.why}</div>`}
  </div>`;
}

function SayStep({ s }) {
  const [level, setLevel] = useState(0);
  return html`<div class="g-say"><div class="scard-ask" style="font-size:20px">${s.de}</div>
    <${HiddenRu} ru=${s.ru} level=${level} size=${22} />
    <div class="rowx g8 wrap jc">${level < 3 && html`<button class="chip sm spick" onClick=${() => setLevel(level + 1)}><${Icon} n="visibility" />Spicker · ${['1. Wort', 'Wortanfänge', 'Ganzer Satz'][level]}</button>`}</div>
    <button class="t-mic big" onClick=${() => { setLevel(3); fx.right(); }} aria-label="Sprechen"><${Icon} n="mic" fill /></button></div>`;
}

function AskAnja({ onClose }) {
  return html`<${Sheet} title="Anja fragen" onClose=${onClose}>
    <div class="t-qa"><div class="bub me"><div class="who">DU</div>Warum heißt es bei Kaffee nicht anders? Ich trinke Kaffee ist doch auch Wen-Fall.</div>
      <div class="bub al-hers"><div class="who">ANJA <${Bars} on /></div>Gute Frage! <${Latin} ru="к+офе" /> ist ein Wort aus einer anderen Sprache. Solche Wörter ändern sich im Russischen nie, egal in welchem Fall. Also: <b><${Latin} ru="Я пь+ю к+офе." /></b>
        <div class="t-checked"><${Icon} n="verified" size="14" />Erklärung geprüft</div></div></div>
    <div class="rowx g8 mt16"><div class="g-search grow"><${Icon} n="edit" /><span>Frage tippen …</span></div><button class="t-mic"><${Icon} n="mic" fill /></button></div><//>`;
}

export function DraftLesson() {
  const [i, setI] = useState(0);
  const [ask, setAsk] = useState(false);
  const s = LESSON[i];
  const last = i === LESSON.length - 1;
  const content = html`<div class="g-screen" key=${i}>
    <div class="over c">${s.k}</div><div class="title mt4">${s.title}</div>
    ${s.body && html`<div class="g-body">${s.body}</div>`}
    ${s.quiz && html`<${Quiz} q=${s.quiz} />`}
    ${s.say && html`<${SayStep} s=${s.say} />`}</div>`;
  return html`<div class="screen c-words">
    <div class="g-top"><button class="iconbtn" onClick=${() => back('entwurf/grammatik')} aria-label="Schließen"><${Icon} n="close" /></button>
      <div class="g-dots">${LESSON.map((_, k) => html`<i class=${cls(k < i && 'done', k === i && 'on')}></i>`)}</div>
      <button class="t-askbtn" onClick=${() => setAsk(true)}><${Icon} n="forum" size="18" />Anja fragen</button></div>
    <div class="duo">
      <div class="pane scroll"><div class="pad">${content}</div><div class="end"></div></div>
      <div class="pane scroll duo-only"><div class="sec" style="padding-top:14px"><div class="h2">Die ganze Tafel</div>
        <div class="sub">So sieht der Wen-Fall bei allen Wörtern aus.</div>
        <${FormTable} head=${['', 'Wer, was?', 'Wen, was?']} rows=${[['-a', 'вод+а', 'в+оду'], ['-ja', 'Т+аня', 'Т+аню'], ['Ding, männl.', 'с+ок', 'с+ок'], ['Person, männl.', 'бр+ат', 'бр+ата']]} hl=${0} /></div></div>
    </div>
    <div class="bottom"><div class="rowx g8">
      ${i > 0 && html`<button class="btn ghost" style="width:54px;flex:none;padding:0" onClick=${() => setI(i - 1)} aria-label="Zurück"><${Icon} n="arrow_back" /></button>`}
      <button class="btn" onClick=${() => (last ? back('entwurf/grammatik') : setI(i + 1))}>${last ? 'Fertig' : 'Weiter'}</button></div></div>
    ${ask && html`<${AskAnja} onClose=${() => setAsk(false)} />`}
  </div>`;
}

// ── Formen eines Wortes ────────────────────────────────────────────────────

const LUBIT = {
  now: [['я', 'любл+ю'], ['ты', 'л+юбишь'], ['он, он+а', 'л+юбит'], ['мы', 'л+юбим'], ['вы', 'л+юбите'], ['он+и', 'л+юбят']],
  past: [['er', 'люб+ил'], ['sie', 'люб+ила'], ['es', 'люб+ило'], ['mehrere', 'люб+или']],
  fut: [['я', 'б+уду люб+ить'], ['ты', 'б+удешь люб+ить'], ['он, он+а', 'б+удет люб+ить'], ['мы', 'б+удем люб+ить'], ['вы', 'б+удете люб+ить'], ['он+и', 'б+удут люб+ить']],
  imp: [['zu einer Person', 'люб+и!'], ['zu mehreren / höflich', 'люб+ите!']],
};
const TENSES = [['now', 'Jetzt'], ['past', 'Gestern'], ['fut', 'Morgen'], ['imp', 'Aufforderung']];
const RULE = {
  now: 'Bei ich kommt ein l dazu (lju-blju), danach bleibt der Stamm. Die Endung zeigt, wer es tut — deshalb kann man ich, du, er im Russischen auch mal weglassen.',
  past: 'Gestern ändert sich das Verb nicht nach Person, sondern danach, wer es ist: Mann -l, Frau -la, mehrere -li.',
  fut: 'Morgen: »werden« (буду, будешь …) plus die Grundform — fast wie im Deutschen.',
  imp: 'Zu einer Person -i, zu mehreren oder höflich -ite.',
};

export function DraftWord() {
  const [t, setT] = useState('now');
  const rows = LUBIT[t].map(([p, f]) => [p, f]);
  return html`<div class="screen c-words">
    <${TopBar} title="Formen" onBack=${() => back('entwurf')} />
    <div class="duo">
      <div class="pane scroll"><div class="pad">
        <div class="w-head"><div class="w-word"><${Latin} ru="люб+ить" /></div><div class="cyr" lang="ru">любить</div>
          <div class="w-mean">lieben, mögen</div>
          <div class="rowx g8 wrap mt8"><span class="tag c-words">Verb</span><span class="tag">macht man immer wieder</span>
            <button class="chip sm"><${Icon} n="swap_horiz" /><span>Gegenstück: <${Latin} ru="полюб+ить" /></span></button></div></div>
        <div class="seg mt16">${TENSES.map(([k, l]) => html`<button class=${cls(t === k && 'on')} onClick=${() => setT(k)}>${l}</button>`)}</div>
        <div class="w-rows mt12">${rows.map(([p, f]) => html`<button class="w-row"><span class="p" lang="ru">${p}</span>
          <span class="f"><b><${Latin} ru=${f} /></b><em lang="ru">${cyr(f)}</em></span><${Icon} n="volume_up" c="faint" /></button>`)}</div>
        <div class="g-rule mt12">${RULE[t]}</div>
      </div><div class="end"></div></div>
      <div class="pane scroll duo-only"><div class="sec" style="padding-top:14px"><div class="h2">In deinen Sätzen</div>
        <div class="list mt12">${[['Я любл+ю к+офе.', 'Ich mag Kaffee.'], ['Ты л+юбишь м+узыку?', 'Magst du Musik?'], ['Он+а л+юбит чит+ать.', 'Sie liest gern.']].map(([ru, de]) => html`<div class="row" style="min-height:0"><div class="tx"><${IL} ru=${ru} size=${16} gloss=${false} /><span>${de}</span></div></div>`)}</div></div>
        <div class="sec"><button class="btn tonal c-words"><${Icon} n="quiz" />Formen üben</button></div></div>
    </div>
    <div class="bottom duo-hide"><div class="rowx g8"><button class="btn ghost"><${Icon} n="forum" />Anja fragen</button><button class="btn area"><${Icon} n="quiz" />Formen üben</button></div></div>
  </div>`;
}

// ── Offline ────────────────────────────────────────────────────────────────

const WORKS = [
  ['school', 'Jahreskurs und Wiederholung', 'mit Kursstimme'],
  ['mic', 'KI-Prüfung beim Sprechen', 'russisches Sprachpaket ist da'],
  ['movie', 'Lieder & Szenen', '12 Stücke auf dem Handy'],
  ['account_tree', 'Grammatik und alle Wortformen', 'liegt auf dem Gerät'],
  ['menu_book', 'Satzbuch, Muster, Spiele', ''],
];
const NEEDS = [
  ['forum', 'Mit Anja sprechen', 'Sie braucht Internet. Deine letzten Korrekturen kannst du trotzdem üben.'],
  ['add_circle', 'Neue Stücke für Lieder & Szenen', 'Kommen heute Nacht im WLAN.'],
];

export function DraftOffline() {
  const [open, setOpen] = useState(true);
  const [gb, setGb] = useState(20);
  return html`<div class="screen c-review">
    <${TopBar} title="Russisch" sub="Entwurf: so sieht die Übersicht ohne Internet aus" onBack=${() => back('entwurf')} />
    <div class="duo">
      <div class="pane scroll">
        <div class="sec" style="padding-top:4px"><div class="o-strip">
          <button class="o-head" onClick=${() => setOpen(!open)}><span class="ic sm"><${Icon} n="cloud_off" /></span>
            <span class="grow"><b>Du bist offline</b><span>Fast alles geht weiter. Nur Anja braucht Internet.</span></span><${Icon} n=${open ? 'expand_less' : 'expand_more'} c="faint" /></button>
          ${open && html`<div class="o-body"><div class="o-k good">Geht gerade</div>
            ${WORKS.map(([i, t, d]) => html`<div class="o-item"><${Icon} n=${i} size="19" /><span class="grow"><b>${t}</b>${d && html`<span>${d}</span>`}</span><${Icon} n="check" size="18" c="good" /></div>`)}
            <div class="o-k">Braucht Internet</div>
            ${NEEDS.map(([i, t, d]) => html`<div class="o-item dim"><${Icon} n=${i} size="19" /><span class="grow"><b>${t}</b><span>${d}</span></span></div>`)}</div>`}
        </div></div>
        <div class="sec"><div class="h2">Anja ohne Netz</div><div class="sub">Was du beim Öffnen von Anja siehst, statt einer Sperre.</div>
          <div class="card p o-anja"><div class="rowx g10"><span class="ic sm"><${Icon} n="wifi_off" /></span><span class="grow"><b>Anja braucht Internet</b><span class="sub">Sobald du wieder online bist, geht es weiter, wo ihr aufgehört habt.</span></span></div>
            <div class="over mt16">Bis dahin: deine letzten Korrekturen</div>
            <div class="list mt8">${[['Я хоч+у в+оду.', 'Ich will Wasser.'], ['Ты л+юбишь чит+ать?', 'Liest du gern?'], ['Я жив+у в Берл+ине.', 'Ich wohne in Berlin.']].map(([ru, de]) => html`<div class="row" style="min-height:0"><div class="tx"><b>${de}</b><span>auf Russisch sagen</span></div><${Icon} n="mic" c="cc" /></div>`)}</div></div></div>
        <div class="end"></div>
      </div>
      <div class="pane scroll">
        <div class="sec" style="padding-top:4px"><div class="h2">Für unterwegs</div><div class="sub">In den Einstellungen, Bereich Russisch.</div>
          <div class="card p o-store">
            <div class="rowx" style="justify-content:space-between"><b>Speicher für Russisch</b><b class="num">${gb} GB</b></div>
            <input type="range" min="2" max="50" step="1" value=${gb} onInput=${(e) => setGb(+e.target.value)} aria-label="Speichergrenze" />
            <div class="o-meter"><i style=${`width:${Math.min(100, (1.8 / gb) * 100)}%`}></i></div>
            <div class="sub">1,8 GB belegt · 12 Stücke · Wörterbuch 24 MB · Kursstimme für 340 eigene Sätze</div>
            <div class="o-last"><${Icon} n="nightlight" size="18" /><span class="grow">Heute Nacht um 3:12 geladen: <b>12 neue Stücke</b></span></div>
            <div class="o-opt"><span class="grow"><b>Nur im WLAN</b><span>Kein mobiles Datenvolumen</span></span><span class="sw on"><i></i></span></div>
            <div class="o-opt"><span class="grow"><b>Nur beim Laden</b><span>Wenn das Handy am Strom hängt</span></span><span class="sw on"><i></i></span></div>
            <div class="o-opt"><span class="grow"><b>Gesehenes nach 7 Tagen vom Handy</b><span>Gemerkte Stücke bleiben</span></span><span class="sw on"><i></i></span></div>
          </div></div>
        <div class="end"></div>
      </div>
    </div></div>`;
}
