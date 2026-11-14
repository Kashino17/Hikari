// »Lieder & Szenen«: echtes Russisch (Lied, Filmszene) in drei Schritten —
// Fühlen (ansehen), Verstehen (Zeile für Zeile, jedes Wort antippbar), Mitnehmen
// (Sätze ins Satzbuch, kurze Übung). Von Hand aufbereitete Stücke: assets/russian/erleben.json.
// Der Feed zu den eigenen Themen, seine Stücke und alle Videos kommen vom Hikari-Server
// (/russian/erleben/…, siehe backend/src/api/russian-erleben.ts).
import { html, cls, Icon, Sheet, Opt, TopBar, mk, fx, audio, useState, useEffect, useRef, useMemo, useListen, ScoreCard, MIC_WAIT, SvgCompare, SvgAiCheck } from '../ui.js';
import { progress, useProgress, phraseById, nav, back, toast } from '../app.js';
import { storage, speech, recorder, SPEECH_ERRORS, api } from '../platform.js';
import * as Sp from '../domain/speech.js';
import * as Gl from '../domain/glossary.js';
import { asPhrase } from '../domain/content.js';
import * as Ph from '../domain/phonetics.js';

// ── Inhalte ────────────────────────────────────────────────────────────────

let DATA = null;
let loading = null;
// Nachschub: Stücke, die der Server selbst aufbereitet hat. `shown` = was gerade im Feed steht.
let FEED = { shown: [], saved: [], ready: 0, busy: false, interests: { video: [], music: [] }, leftToday: 0, resetAt: 0 };
const feedSubs = new Set();
const getFeed = () => api.get('/russian/erleben/feed').catch(() => null);
/** Adresse eines Videos oder Standbilds auf dem Hikari-Server. */
const media = (file) => api.mediaUrl(`/russian/erleben/media/${file}`);
function takeFeed(f) {
  if (!f) return;
  const hand = (DATA ?? []).filter((p) => !p.auto);
  const have = new Map((DATA ?? []).filter((p) => p.auto).map((p) => [p.id, p]));
  DATA = [...hand, ...f.pieces.map((p) => have.get(p.id) ?? prepare(p))];
  FEED = { shown: f.shown, saved: f.saved ?? [], ready: f.ready, busy: f.busy, interests: f.interests ?? { video: [], music: [] }, leftToday: f.leftToday ?? 0, resetAt: f.resetAt ?? 0 };
  for (const fn of [...feedSubs]) fn(FEED);
}
const loadPieces = () => (loading ??= Promise.all([fetch('/data/erleben.json').then((r) => r.json()), getFeed()])
  .then(([d, f]) => { DATA = d.pieces.map(prepare); takeFeed(f); return DATA; }));
const feed = {
  async reload() { takeFeed(await getFeed()); },
  /** Neue Stücke holen. Angefangene bleiben stehen, der Rest macht Platz. */
  async refresh(keep) {
    takeFeed(await api.post('/russian/erleben/refresh', { keep }).catch(() => null));
  },
  /** Stück in die Bibliothek legen oder herausnehmen — dort bleibt es dauerhaft. */
  async save(id, on) {
    const f = await api.post('/russian/erleben/save', { id, on }).catch(() => null);
    takeFeed(f);
    return !!f;
  },
  async setInterests(video, music) {
    const f = await api.post('/russian/erleben/interests', { video, music }).catch(() => null);
    takeFeed(f);
    return !!f;
  },
};
function useFeed() {
  const [f, setF] = useState(FEED);
  useEffect(() => { setF(FEED); feedSubs.add(setF); return () => feedSubs.delete(setF); }, []);
  // Solange der Server Nachschub aufbereitet, regelmäßig nachsehen.
  useEffect(() => { if (!f.busy) return undefined; const t = setInterval(() => feed.reload(), 6000); return () => clearInterval(t); }, [f.busy]);
  return f;
}

/** Zeile → Abschnitte für die Wort-für-Wort-Anzeige ("=" hängt ein Wort an die Bedeutung davor). */
function chunksOf(line) {
  const out = [];
  Ph.words(line.ru).forEach((w, i) => {
    const item = { ...w, i, key: Ph.wordKeys(w.plain)[0] ?? '', t: line.t[i] };
    if (line.g[i] === '=' && out.length) out[out.length - 1].words.push(item);
    else out.push({ words: [item], gloss: line.g[i] });
  });
  return out;
}

/**
 * Welche Sätze lohnen sich zum Mitnehmen? Sie werden zu Karten in der Wiederholung, also zählen
 * ganze Sätze (kein Fetzen, der mit einem Komma endet), ohne Eigennamen, rund vier Wörter lang.
 */
export function suggestTake(uniq, count = 3) {
  const score = (l) => {
    const ru = Ph.plain(l.ru).trim();
    const n = l.g.length;
    return (/[.!?…]$/.test(ru) ? 3 : /[,:;–—-]$/.test(ru) ? -3 : 0)
      + (/^\p{Lu}/u.test(ru) ? 1 : 0)
      - (l.g.includes('Name') ? 4 : 0)
      - (n < 2 ? 4 : Math.abs(n - 4));
  };
  return uniq.map((l) => [score(l), l.i]).sort((a, b) => b[0] - a[0] || a[1] - b[1]).slice(0, count).map(([, i]) => i).sort((a, b) => a - b);
}

function prepare(p) {
  const lines = p.lines.map((l, i) => ({ ...l, i, chunks: chunksOf(l), keys: Ph.wordKeys(l.ru) }));
  // Zeilen, die die Prüfung nicht bestanden haben (ok === false), bleiben hörbar, werden aber weder gezeigt noch geübt.
  const uniq = lines.filter((l) => l.rep == null && l.ok !== false);
  // Welche Wörter tauchen in dieser Zeile zum ersten Mal auf?
  const seen = new Set();
  for (const l of uniq) { l.fresh = new Set(l.keys.filter((k) => !seen.has(k))); for (const k of l.keys) seen.add(k); }
  for (const l of lines) l.times = l.ok === false ? 0 : lines.filter((o) => o.ok !== false && (o.rep ?? o.i) === (l.rep ?? l.i)).length;
  const latin = Ph.transliterateFlat(p.title);
  // Gefundene Videos tragen oft lange Reißer-Titel — dort steht der deutsche Titel vorn, nicht die Umschrift.
  // Bei gefundenen Stücken wählt die App den Vorschlag selbst — die Aufbereitung nahm nur die Länge.
  const take = p.auto ? suggestTake(uniq) : p.take;
  return { ...p, take, lines, uniq, keys: [...seen], latin, head: p.auto ? p.titleDe : latin, under: p.auto ? p.by : p.titleDe, hidden: lines.filter((l) => l.ok === false).length };
}

export function usePieces() {
  const [d, setD] = useState(DATA);
  useEffect(() => {
    if (!DATA) loadPieces().then(setD).catch(() => setD([]));
    const on = () => setD(DATA);
    feedSubs.add(on);
    return () => feedSubs.delete(on);
  }, []);
  return d;
}

// ── Was du aus dem Kurs schon kennst ───────────────────────────────────────

/** Wort → Beispielsatz aus deinen Karten. Daraus entsteht »kennst du schon«. */
function useKnown() {
  const p = useProgress();
  return useMemo(() => {
    const map = new Map();
    for (const id of Object.keys(p.cards)) {
      const ph = phraseById(id);
      if (!ph) continue;
      for (const k of Ph.wordKeys(ph.ru)) if (!map.has(k)) map.set(k, ph);
    }
    return map;
  }, [p.cards]);
}
const knownCount = (piece, known) => piece.keys.filter((k) => known.has(k)).length;

// ── Eigener Stand dieses Bereichs ──────────────────────────────────────────

const KEY = 'erleben_v1';
const EMPTY = { pieces: {}, words: {}, taken: {}, subs: { lat: true, cyr: false, de: true } };
const subs = new Set();
const read = () => ({ ...EMPTY, ...storage.getJSON(KEY, {}) });
const store = {
  get: read,
  update(fn) { const next = fn(read()); storage.set(KEY, next); for (const f of [...subs]) f(next); },
  piece(id, patch) { this.update((s) => ({ ...s, pieces: { ...s.pieces, [id]: { stage: 0, line: 0, marks: [], ...s.pieces[id], ...patch } } })); },
};
function useStore() {
  const [s, setS] = useState(read);
  useEffect(() => { subs.add(setS); return () => subs.delete(setS); }, []);
  return s;
}
const pieceState = (s, id) => ({ stage: 0, line: 0, marks: [], started: false, done: false, ...s.pieces[id] });

// ── Was du hier lernst, zählt überall ──────────────────────────────────────
// Mitgenommene Sätze und gemerkte Wörter landen im Satzbuch (»Meine Sätze«) UND als Karte im
// Lernstand: Sie kommen in die Wiederholung, werden in den Übungen bewertet und zählen zum
// Wortschatz, sobald sie sitzen. Die Bedeutung je Wort wandert mit, damit sie auch dort unter
// jedem Wort steht — sie liegt in diesem Speicher (`taken`), weil das Stück selbst später
// gelöscht sein kann.

const sentenceKey = (ru) => Ph.plain(ru).trim();
/** Für die Erfolge: so viele Stücke sind durchgearbeitet. */
export const piecesDone = () => Object.values(read().pieces).filter((x) => x.done).length;

/** Beim Start: die Wortbedeutungen aller mitgenommenen Sätze beim Glossar anmelden. */
export function registerTaken() { Gl.addSentences(read().taken); }

/** Satz oder Wort aus einem Stück lernen. → Karten-ID */
function learn(piece, ru, de, glosses, word = false) {
  const key = sentenceKey(ru);
  store.update((x) => ({ ...x, taken: { ...x.taken, [key]: glosses } }));
  Gl.addSentences({ [key]: glosses });
  return progress.learnSentence({ ru, de, note: `${word ? 'Wort aus' : 'Aus'} »${piece.titleDe}«` });
}
function unlearn(ru) {
  const key = sentenceKey(ru);
  store.update((x) => { const taken = { ...x.taken }; delete taken[key]; return { ...x, taken }; });
  progress.removeMySentence(ru);
}

// ── Wiedersehen ────────────────────────────────────────────────────────────
// Nach dem Ansehen ohne Deutsch schätzt du ein, wie viel du verstanden hast. Daraus ergibt sich,
// wann die Bibliothek das Stück wieder vorschlägt: wenig verstanden = morgen, fast alles = in zehn Tagen.

const RATINGS = [[1, 'Wenig'], [2, 'Etwa die Hälfte'], [3, 'Fast alles']];
/** Dieselbe Einschätzung als Satzteil: »zuletzt … verstanden«. */
const UNDERSTOOD = { 1: 'wenig', 2: 'etwa die Hälfte', 3: 'fast alles' };
const REWATCH_DAYS = { 1: 1, 2: 3, 3: 10 };
const rewatchDue = (ps, today) => !!(ps.done && ps.rating && today - (ps.ratedAt ?? 0) >= REWATCH_DAYS[ps.rating]);

/** Was nach einem fertigen Stück sinnvoll ist: erst Neues aus dem Feed, dann was wieder dran ist. */
function pickNext(pieces, s, f, currentId, today) {
  const st = (x) => pieceState(s, x.id);
  const others = pieces.filter((x) => x.id !== currentId);
  const fresh = f.shown.map((id) => others.find((x) => x.id === id)).filter((x) => x && !st(x).done);
  if (fresh.length) return { piece: fresh[0], why: 'Neu für dich' };
  const due = others.filter((x) => rewatchDue(st(x), today) && (!x.auto || f.saved.includes(x.id)));
  if (due.length) return { piece: due[0], why: 'Wieder dran' };
  const hand = others.filter((x) => !x.auto && !st(x).done);
  return hand.length ? { piece: hand[0], why: 'Ausgewähltes Stück' } : null;
}

const fmt = (sec) => `${Math.floor(sec / 60)}:${String(Math.round(sec % 60)).padStart(2, '0')}`;
const KIND = { song: ['Lied', 'music_note'], scene: ['Szene', 'movie'] };
/** Standbild eines Stücks. Ist der Server nicht erreichbar, bleibt die Fläche dunkel statt ein kaputtes Bild zu zeigen. */
const Pic = ({ file }) => html`<img src=${media(file)} alt="" onError=${(e) => { e.target.style.visibility = 'hidden'; }} />`;
const latin = (spans) => spans.map((s) => (s.stressed ? html`<u>${s.text}</u>` : s.text));
const shuffled = (a) => { const x = [...a]; for (let i = x.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [x[i], x[j]] = [x[j], x[i]]; } return x; };
const bare = (de) => de.replace(/[»«]/g, '');

function Meter({ piece, known, onDark }) {
  const k = knownCount(piece, known);
  return html`<div class=${cls('ls-meter', onDark && 'dark')}>
    <i><i style=${`width:${Math.round((k / piece.keys.length) * 100)}%`}></i></i>
    <span>${k} von ${piece.keys.length} Wörtern kennst du</span></div>`;
}

function statusOf(ps, piece) {
  if (rewatchDue(ps, progress.today())) return { text: 'Wieder dran', icon: 'replay_circle_filled', c: 'cc' };
  if (ps.done) return { text: ps.rating ? `Fertig, ${UNDERSTOOD[ps.rating]} verstanden` : 'Durchgearbeitet', icon: 'check_circle', c: 'amber' };
  if (ps.started) return { text: ps.stage === 0 ? 'Angesehen' : ps.stage === 1 ? `Zeile ${Math.min(ps.line + 1, piece.uniq.length)} von ${piece.uniq.length}` : 'Fast fertig', icon: 'play_circle', c: 'cc' };
  return null;
}

// ── Übersicht ──────────────────────────────────────────────────────────────

/** Was als Nächstes dran ist: Angefangenes zuerst, sonst das Stück, von dem du am meisten kennst. */
function pickHero(pieces, known, s) {
  const open = pieces.filter((p) => !pieceState(s, p.id).done);
  const pool = open.length ? open : pieces;
  const started = pool.find((p) => pieceState(s, p.id).started);
  return started ?? [...pool].sort((a, b) => knownCount(b, known) / b.keys.length - knownCount(a, known) / a.keys.length)[0];
}

function HeroCard({ piece, known, ps }) {
  const st = statusOf(ps, piece);
  return html`<button class="ls-hero c-live" onClick=${() => nav(`erleben/${piece.id}`)}>
    <${Pic} file=${piece.poster} />
    <div class="in">
      <div class="kind"><${Icon} n=${KIND[piece.kind][1]} size="15" />${KIND[piece.kind][0]}${piece.mood ? `, ${piece.mood.toLowerCase()}` : ''}</div>
      <div class="t">${piece.head}</div>
      <div class="d">${piece.under}, ${piece.part}, ${fmt(piece.dur)}</div>
      <div class="foot"><${Meter} piece=${piece} known=${known} onDark />
        <span class="go"><${Icon} n="play_arrow" fill /></span></div>
      ${st && html`<div class="resume">${ps.done ? 'Nochmal ansehen' : `Weitermachen: ${st.text}`}</div>`}
    </div></button>`;
}

function PieceRow({ piece, known, ps }) {
  const st = statusOf(ps, piece);
  return html`<button class="ls-row c-live" onClick=${() => nav(`erleben/${piece.id}`)}>
    <span class="th"><${Pic} file=${piece.poster} /><em>${fmt(piece.dur)}</em></span>
    <span class="tx"><span class="hd1"><b class="ell">${piece.head}</b>${!piece.auto && html`<em class=${cls(piece.level !== 'Einsteiger' && 'hard')}>${piece.level}</em>`}</span>
      <span class="d ell">${piece.auto ? `${KIND[piece.kind][0]}${piece.topic ? ` zu »${piece.topic}«` : ''}, ${piece.level}` : `${piece.titleDe}, ${KIND[piece.kind][0]}`}</span>
      ${st ? html`<span class=${cls('s', st.c)}><${Icon} n=${st.icon} fill size="15" />${st.text}</span>` : html`<${Meter} piece=${piece} known=${known} />`}
    </span></button>`;
}

const HOW = [
  ['Fühlen', 'Erst nur ansehen, mit deutschem Text. Worum geht es, wie klingt es?'],
  ['Verstehen', 'Zeile für Zeile. Jedes Wort lässt sich antippen, jede Zeile wiederholen.'],
  ['Mitnehmen', 'Zwei, drei Sätze wandern in dein Satzbuch. Danach verstehst du sie beim Hören.'],
];

const IDEAS = {
  video: ['Anime', 'Filme und Serien', 'Lustige Videos', 'True Crime', 'Nachrichten', 'Reisen', 'Kochen', 'Gaming', 'Trickfilme', 'Wissenschaft'],
  music: ['Rock', 'Pop', 'Rap', 'Lieder zur Gitarre', 'Filmmusik', 'Volkslieder', 'Elektronisch', 'Balladen'],
};

function TopicList({ label, hint, list, ideas, onChange }) {
  const [text, setText] = useState('');
  const add = (t) => { const v = t.trim(); if (v && !list.some((x) => x.toLowerCase() === v.toLowerCase()) && list.length < 12) onChange([...list, v]); setText(''); };
  const open = ideas.filter((i) => !list.some((x) => x.toLowerCase() === i.toLowerCase()));
  return html`<div class="ls-topics">
    <div class="q">${label}</div>
    <div class="have">${list.map((t) => html`<button onClick=${() => onChange(list.filter((x) => x !== t))} aria-label=${`${t} entfernen`}>${t}<${Icon} n="close" size="15" /></button>`)}
      ${list.length === 0 && html`<span class="none">Noch nichts gewählt</span>`}</div>
    <form class="add" onSubmit=${(e) => { e.preventDefault(); add(text); }}>
      <input value=${text} onInput=${(e) => setText(e.target.value)} placeholder=${hint} maxlength="60" enterkeyhint="done" />
      <button type="submit" disabled=${!text.trim()} aria-label="Hinzufügen"><${Icon} n="add" /></button></form>
    ${open.length > 0 && html`<div class="ideas">${open.map((i) => html`<button onClick=${() => add(i)}>${i}</button>`)}</div>`}
  </div>`;
}

/** Was interessiert dich? Videos und Musik getrennt, frei eintippbar. Danach sucht der Server passend dazu. */
function InterestsSheet({ f, onClose }) {
  const [video, setVideo] = useState(f.interests.video);
  const [music, setMusic] = useState(f.interests.music);
  const [busy, setBusy] = useState(false);
  const save = async () => {
    setBusy(true);
    const ok = await feed.setInterests(video, music);
    setBusy(false);
    if (ok) onClose(); else toast('Der Hikari-Server ist gerade nicht erreichbar');
  };
  return html`<${Sheet} onClose=${onClose} title="Was interessiert dich?">
    <p class="sub mt4" style="line-height:1.45">Schreib rein, was du wirklich sehen und hören willst. Danach sucht der Server russische Videos und Lieder genau dazu.</p>
    <${TopicList} label="Videos" hint="z. B. One Piece, True Crime, Memes" list=${video} ideas=${IDEAS.video} onChange=${setVideo} />
    <${TopicList} label="Musik" hint="z. B. Rock, ein Interpret, Filmmusik" list=${music} ideas=${IDEAS.music} onChange=${setMusic} />
    <button class="btn mt20" disabled=${busy || video.length + music.length === 0} onClick=${save}>${busy ? 'Speichere …' : 'Speichern und suchen'}</button>
  <//>`;
}

/** Nachschub: was der Server zu deinen Themen gefunden, aufbereitet und geprüft hat. */
function FeedSection({ pieces, known, s, f }) {
  const [busy, setBusy] = useState(false);
  const [topics, setTopics] = useState(false);
  const shown = f.shown.map((id) => pieces.find((p) => p.id === id)).filter(Boolean);
  const has = f.interests.video.length + f.interests.music.length > 0;
  const more = async () => {
    setBusy(true);
    const keep = shown.filter((p) => { const ps = pieceState(s, p.id); return ps.started && !ps.done; }).map((p) => p.id);
    await feed.refresh(keep);
    setBusy(false);
  };
  const waiting = busy || (f.busy && f.ready === 0);
  // Das Prüfkontingent erneuert sich einmal am Tag (Mitternacht in Kalifornien) — hier in deiner Uhrzeit.
  const again = f.resetAt ? `ab ${new Date(f.resetAt).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })} Uhr` : 'morgen';
  const status = f.ready > 0 ? `${f.ready === 1 ? 'Ein weiteres liegt' : `${f.ready} weitere liegen`} bereit.`
    : f.busy ? 'Nachschub wird gerade gesucht und geprüft.'
    : f.leftToday === 0 ? `Das Prüfkontingent ist aufgebraucht, ${again} geht es weiter.`
    : `Bis ${again.replace('ab ', '')} sind noch bis zu ${f.leftToday} Stücke möglich.`;
  return html`<div class="sec"><div class="hd"><div class="h2">Frisch für dich</div>
      ${has && html`<span class="rowx g8"><button class="ls-more" onClick=${() => setTopics(true)}><${Icon} n="tune" size="18" />Themen</button>
        <button class="ls-more" disabled=${waiting || (f.ready === 0 && shown.length === 0)} onClick=${more}><${Icon} n=${waiting ? 'progress_activity' : 'refresh'} c=${waiting ? 'spin' : ''} size="18" />Neue</button></span>`}</div>
    ${!has ? html`<div class="sub">Videos und Lieder zu dem, was dich wirklich interessiert.</div>
        <button class="ls-start" onClick=${() => setTopics(true)}>
          <span class="ic solid"><${Icon} n="interests" /></span>
          <span class="grow"><b>Sag, was dich interessiert</b><span>Anime, True Crime, Memes, deine Musik: Du gibst die Richtung vor, der Server sucht, bereitet auf und prüft.</span></span>
          <${Icon} n="chevron_right" c="faint" /></button>`
      : html`<div class="sub">${[...f.interests.video, ...f.interests.music].join(', ')}. ${status}</div>
        ${shown.length > 0 ? html`<div class="list">${shown.map((x) => html`<${PieceRow} piece=${x} known=${known} ps=${pieceState(s, x.id)} />`)}</div>`
          : html`<div class="ls-wait"><${Icon} n=${f.busy ? 'progress_activity' : 'hourglass_empty'} c=${f.busy ? 'spin' : ''} /><span>${f.busy
            ? 'Die ersten Stücke werden gesucht, aufbereitet und doppelt geprüft. Das dauert ein bis zwei Minuten je Stück; nicht jedes Video besteht die Prüfung.'
            : f.leftToday === 0 ? `Gerade kann nichts mehr geprüft werden. ${again[0].toUpperCase()}${again.slice(1)} geht es weiter.` : 'Gleich geht es los.'}</span></div>`}`}
    ${topics && html`<${InterestsSheet} f=${f} onClose=${() => setTopics(false)} />`}
  </div>`;
}

/** Eine Kachel der Bibliothek: Bild, Titel, Stand. */
function LibTile({ piece, ps }) {
  const st = statusOf(ps, piece);
  return html`<button class="ls-tile" onClick=${() => nav(`erleben/${piece.id}`)}>
    <span class="th"><${Pic} file=${piece.poster} /><em>${fmt(piece.dur)}</em></span>
    <b>${piece.head}</b>
    <span class=${cls('s', st?.c)}>${st ? html`<${Icon} n=${st.icon} fill size="14" />${st.text}` : KIND[piece.kind][0]}</span></button>`;
}

/** Behaltene Stücke: was wieder dran ist zuerst, dann die zuletzt hinzugefügten. */
function libraryPieces(pieces, s, f) {
  const today = progress.today();
  const list = f.saved.map((id) => pieces.find((p) => p.id === id)).filter(Boolean);
  const due = (p) => (rewatchDue(pieceState(s, p.id), today) ? 0 : 1);
  return list.map((p, i) => [p, i]).sort((a, b) => due(a[0]) - due(b[0]) || a[1] - b[1]).map(([p]) => p);
}

function LibrarySection({ pieces, s, f }) {
  const list = libraryPieces(pieces, s, f);
  if (!list.length) return null;
  const today = progress.today();
  const due = list.filter((p) => rewatchDue(pieceState(s, p.id), today)).length;
  return html`<div class="sec"><div class="hd"><div class="h2">Bibliothek</div>${list.length > 4 && html`<button onClick=${() => nav('erleben/bibliothek')}>Alle ${list.length}</button>`}</div>
    <div class="sub">${due > 0 ? `${due === 1 ? 'Ein Stück ist' : `${due} Stücke sind`} wieder dran: noch einmal ansehen festigt, was du daraus gelernt hast.` : 'Was du durchgearbeitet oder behalten hast. Diese Stücke bleiben.'}</div>
    <div class="ls-tiles">${list.slice(0, 4).map((x) => html`<${LibTile} piece=${x} ps=${pieceState(s, x.id)} />`)}</div>
  </div>`;
}

const LIB_FILTERS = [['all', 'Alle'], ['due', 'Wieder dran'], ['song', 'Lieder'], ['scene', 'Szenen']];

export function ErlebenLibrary() {
  const pieces = usePieces();
  const s = useStore();
  const f = useFeed();
  const [filter, setFilter] = useState('all');
  if (!pieces) return html`<div class="screen"><${TopBar} title="Bibliothek" onBack=${() => back('erleben')} /></div>`;
  const today = progress.today();
  const all = libraryPieces(pieces, s, f);
  const list = all.filter((p) => filter === 'all' || (filter === 'due' ? rewatchDue(pieceState(s, p.id), today) : p.kind === filter));
  return html`<div class="screen c-live">
    <${TopBar} title="Bibliothek" sub=${all.length === 1 ? 'Ein Stück, das bleibt' : `${all.length} Stücke, die bleiben`} onBack=${() => back('erleben')} />
    <div class="chips" style="padding-top:6px;padding-bottom:10px">${LIB_FILTERS.map(([id, label]) => html`<button class=${cls('chip c sm', filter === id && 'on')} onClick=${() => setFilter(id)}>${label}</button>`)}</div>
    <div class="scroll"><div style="padding:6px 16px 0">
      ${list.length ? html`<div class="ls-tiles">${list.map((x) => html`<${LibTile} piece=${x} ps=${pieceState(s, x.id)} />`)}</div>`
        : html`<div class="empty"><${Icon} n="video_library" /><b>${all.length ? 'Hier ist gerade nichts.' : 'Noch nichts in der Bibliothek.'}</b>${all.length ? 'Wähle oben einen anderen Filter.' : 'Arbeite ein Stück durch oder tippe im Stück oben auf das Lesezeichen.'}</div>`}
    </div><div class="end"></div></div></div>`;
}

export function ErlebenHub() {
  const pieces = usePieces();
  const known = useKnown();
  const s = useStore();
  const p = useProgress();
  const f = useFeed();
  if (!pieces) return html`<div class="screen"><${TopBar} title="Lieder & Szenen" onBack=${() => back('heute')} /></div>`;
  const hand = pieces.filter((x) => !x.auto);
  const hero = pickHero(hand, known, s);
  const rest = hand.filter((x) => x !== hero);
  const taken = p.mySentences.filter((m) => (m.note ?? '').startsWith('Aus »')).length;
  const done = pieces.filter((x) => pieceState(s, x.id).done).length;
  return html`<div class="screen c-live">
    <${TopBar} title="Lieder & Szenen" sub="Russisch, wie es wirklich klingt" onBack=${() => back('heute')} />
    <div class="scroll">
      <${HeroCard} piece=${hero} known=${known} ps=${pieceState(s, hero.id)} />

      <${FeedSection} pieces=${pieces} known=${known} s=${s} f=${f} />

      <${LibrarySection} pieces=${pieces} s=${s} f=${f} />

      <div class="sec"><div class="h2">Ausgewählte Stücke</div>
        <div class="sub">Von Hand aufbereitet, mit Geschichte und Tipps.</div>
        <div class="list">${[...rest].sort((a, b) => knownCount(b, known) / b.keys.length - knownCount(a, known) / a.keys.length)
          .map((x) => html`<${PieceRow} piece=${x} known=${known} ps=${pieceState(s, x.id)} />`)}</div>
      </div>

      <div class="sec"><div class="h2">So arbeitest du mit einem Stück</div>
        <div class="ls-how">${HOW.map(([t, d], i) => html`<div><i>${i + 1}</i><span><b>${t}</b>${d}</span></div>`)}</div>
      </div>

      <div class="sec"><div class="hd"><div class="h2">Mitgenommen</div>${taken > 0 && html`<button onClick=${() => nav('satzbuch')}>Im Satzbuch</button>`}</div>
        <div class="stat3 mt12">
          <div><b><${Icon} n="bookmark" fill c="cc" />${taken}</b><span>Sätze im Satzbuch</span></div>
          <div><b><${Icon} n="translate" c="cc" />${Object.keys(s.words).length}</b><span>Wörter gemerkt</span></div>
          <div><b><${Icon} n="check_circle" fill c="amber" />${done}</b><span>${done === 1 ? 'Stück' : 'Stücke'} fertig</span></div>
        </div>
      </div>

      <div class="sec"><button class="ls-add" onClick=${() => toast('Kommt mit dem Ausbau: Lieder aus deiner Musik, Videos aus der Bibliothek')}>
        <span class="ic sm"><${Icon} n="add" /></span>
        <span class="grow"><b>Eigenes Lied oder Video</b><span>Später auch aus deiner Musik und deiner Bibliothek. In dieser Vorschau noch nicht.</span></span></button></div>
      <div class="end"></div>
    </div></div>`;
}

/** Einstieg auf »Heute«. */
export function ErlebenTeaser() {
  const pieces = usePieces();
  const known = useKnown();
  const s = useStore();
  if (!pieces?.length) return null;
  const hero = pickHero(pieces.filter((x) => !x.auto || FEED.shown.includes(x.id)), known, s);
  const ps = pieceState(s, hero.id);
  return html`<div class="sec c-live"><div class="hd"><div class="h2">Lieder & Szenen</div><button onClick=${() => nav('erleben')}>Alle Stücke</button></div>
    <div class="sub" style="margin:3px 4px 12px">Echtes Russisch: ansehen, verstehen, Sätze mitnehmen.</div>
    <button class="ls-teaser" onClick=${() => nav(`erleben/${hero.id}`)}>
      <span class="th"><${Pic} file=${hero.poster} /></span>
      <span class="tx"><span class="k">${KIND[hero.kind][0]}, ${fmt(hero.dur)}</span><b class="ell">${hero.head}</b>
        <span class="d ell">${ps.started && !ps.done ? `Weitermachen: ${statusOf(ps, hero).text}` : hero.under}</span></span>
      <span class="go"><${Icon} n="play_arrow" fill /></span></button></div>`;
}

// ── Wiedergabe ─────────────────────────────────────────────────────────────

/**
 * Steuert das Video eines Stücks. `pos` = {line, word}: was gerade zu hören ist.
 * `playSeg(a, b)` spielt einen Abschnitt (eine Zeile, ein Wort) und hält danach an.
 */
// Geladene Ausschnitte bleiben im Speicher, solange der Bereich offen ist — die letzten vier.
const CLIPS = new Map();
function keepClip(file, url) {
  CLIPS.set(file, url);
  while (CLIPS.size > 4) { const [old, oldUrl] = CLIPS.entries().next().value; CLIPS.delete(old); URL.revokeObjectURL(oldUrl); }
}

/**
 * Lädt den Ausschnitt einmal vollständig und spielt ihn dann aus dem Speicher.
 *
 * Warum nicht einfach die Server-Adresse als Quelle: In der App laufen Abrufe über die Brücke der
 * WebView (shouldInterceptRequest). Einen Teilabruf »ab Byte N« — den der Player nach einem Sprung
 * an eine noch nicht geladene Stelle braucht — kann sie nicht weitergeben; das Video blieb dann in
 * »sucht« hängen und fragte den Server in Schleife (v0.89.112, gemessen: 13 Anfragen in 10 s).
 * Direkt vom Server (http) darf die https-Oberfläche kein Video laden. Die Ausschnitte sind klein
 * (5 bis 10 MB), also: einmal am Stück holen, danach geht jeder Sprung sofort und ohne Netz.
 */
function useClip(file) {
  const [st, setSt] = useState(() => ({ url: CLIPS.get(file) ?? null, part: 0, failed: false }));
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    if (CLIPS.has(file)) { setSt({ url: CLIPS.get(file), part: 1, failed: false }); return undefined; }
    const ctrl = new AbortController();
    setSt({ url: null, part: 0, failed: false });
    (async () => {
      try {
        const r = await fetch(media(file), { signal: ctrl.signal });
        if (!r.ok) throw new Error(`HTTP ${r.status}`);
        const total = Number(r.headers.get('content-length')) || 0;
        const reader = r.body?.getReader?.();
        let blob;
        if (reader) {
          const chunks = [];
          let got = 0;
          let shown = 0;
          for (;;) {
            const { done, value } = await reader.read();
            if (done) break;
            chunks.push(value);
            got += value.length;
            const part = total ? Math.min(0.99, got / total) : 0;
            if (part - shown >= 0.03) { shown = part; setSt((x) => ({ ...x, part })); }
          }
          blob = new Blob(chunks, { type: 'video/mp4' });
        } else blob = await r.blob();
        if (ctrl.signal.aborted) return;
        if (blob.size < 1000) throw new Error('leer');
        const url = URL.createObjectURL(blob);
        keepClip(file, url);
        setSt({ url, part: 1, failed: false });
      } catch {
        if (!ctrl.signal.aborted) setSt({ url: null, part: 0, failed: true });
      }
    })();
    return () => ctrl.abort();
  }, [file, attempt]);
  return { ...st, ready: !!st.url, retry: () => setAttempt((x) => x + 1) };
}

function usePlayer(piece) {
  const clip = useClip(piece.media);
  const ref = useRef(null);
  const bar = useRef(null);
  const seg = useRef(null);
  const [playing, setPlaying] = useState(false);
  const [pos, setPos] = useState({ line: -1, word: -1 });
  const [ended, setEnded] = useState(false);
  const [segKey, setSegKey] = useState(null);
  useEffect(() => {
    let raf = 0;
    let last = '';
    const tick = () => {
      raf = requestAnimationFrame(tick);
      const v = ref.current;
      if (!v) return;
      const t = v.currentTime;
      const sg = seg.current;
      if (sg && t >= sg.b) {
        if (sg.loop) v.currentTime = sg.a;
        else { v.pause(); seg.current = null; setSegKey(null); }
      }
      if (bar.current) bar.current.style.width = `${Math.min(100, (t / piece.dur) * 100)}%`;
      let line = -1;
      for (const l of piece.lines) if (t >= l.a && t < l.b) { line = l.i; break; }
      let word = -1;
      if (line >= 0) { const ts = piece.lines[line].t; for (let k = 0; k < ts.length; k++) if (t >= ts[k] - 0.08) word = k; }
      const sig = `${line}:${word}`;
      if (sig !== last) { last = sig; setPos({ line, word }); }
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [piece]);
  const start = (rate = 1) => { const v = ref.current; v.playbackRate = rate; setEnded(false); return v.play().catch(() => {}); };
  // Solange der Ausschnitt lädt, gibt es nichts abzuspielen — die Knöpfe zeigen dann den Ladestand.
  const ready = clip.ready;
  return {
    ref, bar, playing, pos, ended, segKey, clip, ready,
    bind: { onPlay: () => setPlaying(true), onPause: () => setPlaying(false), onEnded: () => { setPlaying(false); setEnded(true); seg.current = null; setSegKey(null); } },
    play() { if (!ready) return; seg.current = null; setSegKey(null); const v = ref.current; if (v.ended || v.currentTime >= piece.dur - 0.3) v.currentTime = 0; start(1); },
    pause() { ref.current?.pause(); },
    toggle() { if (ref.current.paused) this.play(); else this.pause(); },
    seek(t) { if (!ready) return; seg.current = null; setSegKey(null); ref.current.currentTime = t; start(1); },
    playSeg(a, b, { rate = 1, loop = false, key = 'line' } = {}) {
      if (!ready) return;
      audio.stop();
      const v = ref.current;
      seg.current = { a, b, loop };
      setSegKey(key);
      v.currentTime = a;
      start(rate);
    },
    stopSeg() { seg.current = null; setSegKey(null); ref.current?.pause(); },
  };
}

/** Ladestand des Ausschnitts als Text: »Lädt 43 %«, oder der Hinweis nach einem Fehlschlag. */
const clipLabel = (clip) => (clip.failed ? 'Video nicht geladen. Nochmal versuchen' : clip.part > 0 ? `Video lädt, ${Math.round(clip.part * 100)} %` : 'Video lädt …');

function Stage({ piece, pl, onTap, quiet }) {
  const tap = !pl.ready ? () => pl.clip.failed && pl.clip.retry() : (onTap ?? (() => pl.toggle()));
  const ticks = piece.lines.map((l) => html`<s style=${`left:${((l.a / piece.dur) * 100).toFixed(2)}%`}></s>`);
  const wait = !pl.ready && html`<${Icon} n=${pl.clip.failed ? 'refresh' : 'progress_activity'} c=${pl.clip.failed ? '' : 'spin'} />`;
  if (piece.still) {
    return html`<div class="ls-audio">
      <video ref=${pl.ref} src=${pl.clip.url} poster=${media(piece.poster)} playsinline preload="auto" ...${pl.bind}></video>
      <div class="tx"><b class="ell">${piece.head}</b><span class="ell">${pl.ready ? piece.by : clipLabel(pl.clip)}</span>
        <div class="ls-track"><i ref=${pl.bar}></i>${ticks}</div></div>
      <button class="pp" onClick=${tap} aria-label=${pl.ready ? (pl.playing ? 'Pause' : 'Abspielen') : clipLabel(pl.clip)}>${wait || html`<${Icon} n=${pl.playing ? 'pause' : 'play_arrow'} fill />`}</button>
    </div>`;
  }
  return html`<div class="ls-video" style=${`--r:${piece.ratio}`}>
    <video ref=${pl.ref} src=${pl.clip.url} poster=${media(piece.poster)} playsinline preload="auto" ...${pl.bind}></video>
    <button class=${cls('tap', pl.playing && 'on', quiet && pl.ready && 'quiet')} onClick=${tap} aria-label=${pl.ready ? (pl.playing ? 'Pause' : 'Abspielen') : clipLabel(pl.clip)}>
      ${pl.ready ? html`<span class="pp"><${Icon} n="play_arrow" fill /></span>` : html`<span class="ls-load">${wait}<em>${clipLabel(pl.clip)}</em></span>`}</button>
    <div class="ls-track"><i ref=${pl.bar}></i>${ticks}</div>
  </div>`;
}

// ── Schritt 1: Fühlen ──────────────────────────────────────────────────────

const SUBS = [['lat', 'Umschrift'], ['cyr', 'Kyrillisch'], ['de', 'Deutsch']];

function SubToggles({ s }) {
  const flip = (k) => store.update((x) => ({ ...x, subs: { ...x.subs, [k]: !x.subs[k] } }));
  return html`<div class="ls-subs"><span>Untertitel</span>${SUBS.map(([k, label]) => html`<button class=${cls(s.subs[k] && 'on')} onClick=${() => flip(k)}>${label}</button>`)}</div>`;
}

function LiveLine({ line, word, s, dim }) {
  if (!line) return null;
  if (line.ok === false) return html`<div class=${cls('ls-live', 'dim')}><div class="de">Diese Stelle ist nicht sicher verstanden. Hör einfach zu.</div></div>`;
  const none = !s.subs.lat && !s.subs.cyr && !s.subs.de;
  return html`<div class=${cls('ls-live', dim && 'dim')}>
    ${line.who && html`<div class="who">${line.who}</div>`}
    ${s.subs.lat && html`<div class="lat">${line.chunks.flatMap((c) => c.words).map((w) => html`<span class=${cls(!dim && w.i < word && 'past', !dim && w.i === word && 'now')}>${w.before}${latin(w.latin)}${w.after} </span>`)}</div>`}
    ${s.subs.cyr && html`<div class=${cls('cy', !s.subs.lat && 'main')} lang="ru">${Ph.display(line.ru)}</div>`}
    ${s.subs.de && html`<div class="de">${line.de}</div>`}
    ${none && !dim && html`<div class="de faint">Ohne Untertitel. Nur zuhören.</div>`}
  </div>`;
}

function Feel({ piece, pl, ps, s, ending }) {
  const started = ps.started || pl.playing;
  const cur = pl.pos.line >= 0 ? piece.lines[pl.pos.line] : null;
  // Zwischen zwei Zeilen: die nächste schon zeigen, gedimmt.
  const next = cur ? null : piece.lines.find((l) => l.a > (pl.ref.current?.currentTime ?? 0));
  return html`<div class="scroll"><div class="ls-body">
    ${started && html`<${SubToggles} s=${s} />`}
    ${started && html`<div class="ls-livebox">${cur ? html`<${LiveLine} line=${cur} word=${pl.pos.word} s=${s} />` : pl.ended
      // Am Ende: beim ersten Mal der Weg zum Verstehen, bei einem fertigen Stück Einschätzung und nächster Schritt.
      ? (ending ?? (ps.done ? null : html`<div class="ls-after"><b>Worum ging es?</b><span>Du musst noch nichts verstanden haben. Jetzt nehmt ihr es gemeinsam auseinander, Zeile für Zeile.</span></div>`))
      : html`<${LiveLine} line=${next} word=${-1} s=${s} dim />`}</div>`}
    <div class=${cls('ls-story', started && 'small')}>
      <div class="hd2"><${Icon} n=${KIND[piece.kind][1]} size="17" />${piece.auto ? 'Worum es geht' : piece.titleDe}<em>${piece.auto ? fmt(piece.dur) : piece.by}</em></div>
      ${piece.story && html`<p>${piece.story}</p>`}
      ${piece.listen && html`<div class="lst"><${Icon} n="hearing" size="17" /><span>${piece.listen}</span></div>`}
      ${piece.auto && html`<p class="autonote"><${Icon} n="verified_user" size="16" />Doppelt geprüft: Jede gezeigte Zeile haben zwei Spracherkenner gleich gehört, Übersetzung und Betonung sind von zwei Quellen bestätigt.${piece.hidden > 0 ? ` ${piece.hidden === 1 ? 'Eine Stelle blieb' : `${piece.hidden} Stellen blieben`} unbestätigt und ${piece.hidden === 1 ? 'ist' : 'sind'} ausgeblendet.` : ''}</p>`}
    </div>
  </div></div>`;
}

// ── Schritt 2: Verstehen ───────────────────────────────────────────────────

function wordState(key, line, known) {
  if (known.has(key)) return 'known';
  return line.fresh?.has(key) ? 'new' : 'seen';
}

function Words({ line, known, word, playing, onWord, kept }) {
  return html`<div class="ls-words">${line.chunks.map((c) => {
    const st = c.words.some((w) => wordState(w.key, line, known) === 'new') ? 'new' : c.words.every((w) => known.has(w.key)) ? 'known' : 'seen';
    const now = playing && c.words.some((w) => w.i === word);
    return html`<button class=${cls('lw', st, now && 'now')} onClick=${() => onWord(c)}>
      <span class="l">${c.words.map((w, k) => html`${k ? ' ' : ''}<i>${w.before}</i>${latin(w.latin)}<i>${w.after}</i>`)}</span>
      <span class="g">${c.words.some((w) => kept[w.key]) && html`<${Icon} n="bookmark" fill size="12" />`}${c.gloss || '–'}</span></button>`;
  })}</div>`;
}

function DockBtn({ icon, node, label, on, primary, onClick, fill, rec, disabled }) {
  return html`<button class=${cls('dk', primary && 'primary', on && 'on', rec && 'rec')} onClick=${onClick} disabled=${disabled} aria-label=${label}>
    <span class="dk-c">${node ?? html`<${Icon} n=${icon} fill=${fill} />`}</span><span class="dk-l">${label}</span></button>`;
}

function Understand({ piece, pl, ps, s, known, onWord, toLine, onTake }) {
  const idx = Math.min(ps.line, piece.uniq.length - 1);
  const line = piece.uniq[idx];
  const marked = ps.marks.includes(line.i);
  const said = (ps.spoken ?? []).includes(line.i);
  const here = pl.pos.line === line.i || (pl.pos.line >= 0 && piece.lines[pl.pos.line].rep === line.i);
  const [lens, setLens] = useState(false);
  // Sprechen: KI-Prüfung (Erkennung) und Nachsprechen (eigene Aufnahme gegen das Original).
  const [sc, setSc] = useState(null);
  const [rec, setRec] = useState(null);
  const [take, setTake] = useState(null);
  const [err, setErr] = useState(null);
  const L = useListen((hyps) => {
    const r = Sp.speech(line.ru, hyps);
    setSc(r);
    (r.passed ? fx.right : fx.wrong)();
    if (r.passed && !said) {
      store.piece(piece.id, { spoken: [...(ps.spoken ?? []), line.i] });
      // Zählt als gesprochener Satz; ist die Zeile schon eine Karte (mitgenommen), auch für diese.
      const card = asPhrase(line.ru, bare(line.de)).id;
      progress.apply({ xp: 3, spokenOk: 1, graded: progress.gradable(card) ? { [card]: true } : {} });
    }
  });
  useEffect(() => { setLens(false); setSc(null); setTake(null); setErr(null); L.cancel(); }, [idx]);
  useEffect(() => () => rec?.cancel(), [rec]);
  const hear = (opts) => { L.cancel(); pl.playSeg(line.a, line.b, opts); };
  const mark = () => store.piece(piece.id, { marks: marked ? ps.marks.filter((m) => m !== line.i) : [...ps.marks, line.i] });
  const check = () => {
    if (L.listening) { L.stop(); return; }
    if (rec) { rec.cancel(); setRec(null); }
    pl.stopSeg(); setSc(null); setErr(null);
    L.start(line.ru);
  };
  const record = async () => {
    if (rec) {
      const blob = await rec.stop();
      setRec(null); setTake(blob);
      // Erst du, dann das Original — so hörst du den Unterschied.
      if (await audio.playBlob(blob, { key: 'take' })) pl.playSeg(line.a, line.b, { key: 'line' });
      return;
    }
    L.cancel(); pl.stopSeg(); setErr(null);
    try { setRec(await recorder.start()); } catch { setErr(SPEECH_ERRORS.denied); }
  };
  const fresh = line.chunks.flatMap((c) => c.words).filter((w) => wordState(w.key, line, known) === 'new').length;
  const hint = rec ? 'Sprich die Zeile nach und tippe auf Stopp. Dann hörst du dich und das Original.'
    : L.listening ? (L.partial ? `»${L.partial}«` : L.opening ? MIC_WAIT : 'Ich höre zu … sprich die Zeile und tippe auf Fertig.')
    : err ?? L.errorText;
  return html`<div class="scroll"><div class="ls-body">
    <div class="ls-linehd"><span class="n">Zeile ${idx + 1} von ${piece.uniq.length}</span>
      ${line.who && html`<span class="who">${line.who}</span>`}
      ${said && html`<span class="said"><${Icon} n="check_circle" fill size="14" />gesprochen</span>`}
      <span class="grow"></span>
      ${line.unsure && html`<span class="rep"><${Icon} n="help" size="14" />unsicher erkannt</span>`}
      ${line.times > 1 && html`<span class="rep"><${Icon} n="repeat" size="14" />${line.times}-mal</span>`}
      <button class=${cls('keep', marked && 'on')} onClick=${mark} aria-label=${marked ? 'Nicht mehr merken' : 'Zeile merken'}><${Icon} n="bookmark" fill=${marked} size="20" />${marked ? 'Gemerkt' : 'Merken'}</button></div>
    <div class="ls-card" key=${line.i}>
      <${Words} line=${line} known=${known} word=${pl.pos.word} playing=${here && pl.playing} onWord=${(c) => onWord(c, line)} kept=${s.words} />
      <button class=${cls('cyr-line', lens && 'open')} onClick=${() => setLens(!lens)}><span class="cyr" lang="ru">${Ph.display(line.ru)}</span><span class="abc">${lens ? 'ausblenden' : 'Buchstaben'}</span></button>
      ${lens && html`<div class="lens" style="justify-content:flex-start">${Ph.letterPairs(line.ru).map((p) => (p ? html`<span lang="ru">${p[0]}<small>${p[1]}</small></span>` : html`<i class="sp"></i>`))}</div>`}
      ${line.de && html`<div class="de">${line.de}</div>`}
      ${line.tip && html`<div class="tip"><${Icon} n="lightbulb" size="17" /><span>${mk(line.tip)}</span></div>`}
    </div>
    ${hint && html`<div class="ls-hint">${hint}</div>`}
    ${sc && !L.listening && html`<div class="mt12"><${ScoreCard} sc=${sc} targetRu=${line.ru} /></div>`}
    ${take && !rec && html`<div class="res mt12"><div class="res-row" style="justify-content:space-between"><b class="res-title">Deine Aufnahme</b>
      <div class="rowx g8"><button class="chip sm" onClick=${() => { pl.pause(); audio.playBlob(take); }}><${Icon} n="play_arrow" />Ich</button>
        <button class="chip sm" onClick=${() => hear({ key: 'line' })}><${Icon} n="play_arrow" />Original</button></div></div></div>`}
    ${!hint && !sc && !take && html`<div class="ls-legend">${fresh > 0 ? html`<i class="new"></i>${fresh === 1 ? 'Ein Wort ist neu für dich' : `${fresh} Wörter sind neu für dich`}` : html`<i></i>Alle Wörter dieser Zeile hattest du schon`}<span>Tippe ein Wort an</span></div>`}
  </div></div>
  <div class="bottom">
    <div class="dock ls-dock">
      <${DockBtn} primary fill disabled=${!pl.ready && !pl.clip.failed} node=${!pl.ready ? html`<${Icon} n=${pl.clip.failed ? 'refresh' : 'progress_activity'} c=${pl.clip.failed ? '' : 'spin'} />` : null}
        icon=${pl.playing && pl.segKey === 'line' ? 'graphic_eq' : 'play_arrow'} label=${pl.ready ? 'Hören' : pl.clip.failed ? 'Nochmal' : pl.clip.part > 0 ? `${Math.round(pl.clip.part * 100)} %` : 'Lädt …'}
        onClick=${() => (pl.ready ? hear({ key: 'line' }) : pl.clip.retry())} />
      <${DockBtn} icon="slow_motion_video" label="Langsam" on=${pl.playing && pl.segKey === 'slow'} onClick=${() => hear({ rate: 0.7, key: 'slow' })} />
      <${DockBtn} icon="repeat" label="Schleife" on=${pl.segKey === 'loop'} onClick=${() => (pl.segKey === 'loop' ? pl.stopSeg() : hear({ loop: true, key: 'loop' }))} />
      ${recorder.available() && html`<${DockBtn} rec=${!!rec} node=${rec ? html`<${Icon} n="stop" fill />` : html`<${SvgCompare} s=${22} />`} label=${rec ? 'Stopp' : 'Nachsprechen'} onClick=${record} />`}
      ${speech.available() && html`<${DockBtn} on=${L.ready} disabled=${L.evaluating}
        node=${L.ready ? html`<${Icon} n="graphic_eq" />` : L.evaluating || L.opening ? html`<${Icon} n="progress_activity" c="spin" />` : html`<${SvgAiCheck} s=${22} />`}
        label=${L.opening ? 'Moment …' : L.listening ? 'Fertig' : 'KI-Prüfung'} onClick=${check} />`}
    </div>
    <div class="rowx g8">
      <button class="btn ghost" style="width:54px;flex:none;padding:0;min-height:54px" disabled=${idx === 0} onClick=${() => toLine(idx - 1)} aria-label="Vorige Zeile"><${Icon} n="arrow_back" /></button>
      ${idx + 1 < piece.uniq.length
        ? html`<button class="btn" onClick=${() => toLine(idx + 1)}>Nächste Zeile<${Icon} n="arrow_forward" /></button>`
        : html`<button class="btn area" onClick=${onTake}>${ps.done ? 'Zum Abschluss' : 'Sätze mitnehmen'}<${Icon} n="arrow_forward" /></button>`}
    </div>
  </div>`;
}

function WordSheet({ chunk, line, piece, known, s, pl, onClose }) {
  const w = chunk.words[chunk.words.length - 1];
  const main = chunk.words.find((x) => piece.words[x.key]?.n) ?? chunk.words.find((x) => !known.has(x.key)) ?? w;
  const info = piece.words[main.key] ?? {};
  const example = known.get(main.key);
  const kept = !!s.words[main.key];
  const state = wordState(main.key, line, known);
  const from = chunk.words[0].t;
  const nextT = line.t[w.i + 1] ?? line.b;
  // Gemerkt wird das Wort (oder die Wendung) so, wie es in der Zeile steht — mit seiner Bedeutung dort.
  const ruForm = chunk.words.map((x) => Ph.fromDisplay(x.cyrillic)).join(' ');
  const keep = () => {
    if (!chunk.gloss || chunk.gloss === 'Name') { toast('Für dieses Wort gibt es keine Bedeutung zum Merken'); return; }
    if (kept) unlearn(s.words[main.key].ru ?? ruForm);
    else learn(piece, ruForm, chunk.gloss, [chunk.gloss, ...chunk.words.slice(1).map(() => '=')], true);
    store.update((x) => {
      const words = { ...x.words };
      if (kept) delete words[main.key]; else words[main.key] = { ru: ruForm, de: chunk.gloss, piece: piece.id };
      return { ...x, words };
    });
    toast(kept ? 'Aus dem Satzbuch entfernt' : 'Im Satzbuch und in der Wiederholung');
  };
  const baseDiffers = info.b && Ph.plain(info.b).toLowerCase() !== main.plain.toLowerCase();
  return html`<${Sheet} onClose=${onClose}>
    <div class="ls-word c-live">
      <div class="big"><span>${chunk.words.map((x, k) => html`${k ? ' ' : ''}${latin(x.latin)}`)}</span><span class="cyr" lang="ru">${chunk.words.map((x) => x.cyrillic).join(' ')}</span></div>
      <div class="here">${chunk.gloss ? html`Hier: <b>${chunk.gloss}</b>` : 'Keine der Wörterbuch-Bedeutungen passt hier sicher. Die Grundform steht unten.'}</div>
      <div class="rows">
        ${baseDiffers && html`<div><${Icon} n="account_tree" size="18" /><span><span class="faint">Grundform:</span> <b>${Ph.transliterateFlat(info.b)}</b> <span class="cyr" lang="ru">${Ph.display(info.b)}</span> = ${info.de}</span></div>`}
        ${info.n && html`<div><${Icon} n="lightbulb" size="18" /><span>${mk(info.n)}</span></div>`}
        ${state === 'known' && example && html`<div class="ok"><${Icon} n="check_circle" fill size="18" /><span><span class="faint">Kennst du aus dem Kurs:</span><br /><b>${Ph.transliterateFlat(example.ru)}</b><br /><span class="muted">${example.de}</span></span></div>`}
        ${state === 'new' && html`<div class="nw"><${Icon} n="fiber_new" size="18" /><span>Neu für dich. Im Kurs kam dieses Wort noch nicht vor.</span></div>`}
        ${state === 'seen' && html`<div><${Icon} n="history" size="18" /><span>Dieses Wort hattest du weiter oben in diesem Stück schon.</span></div>`}
      </div>
      <div class="dock" style="margin-top:20px">
        <${DockBtn} primary fill icon="play_arrow" label="Im Original" onClick=${() => pl.playSeg(Math.max(line.a, from - 0.12), Math.min(line.b, nextT + 0.1), { key: 'word' })} />
        <${DockBtn} icon="record_voice_over" label="Deutlich" onClick=${() => { pl.pause(); audio.play(null, { ru: chunk.words.map((x) => x.cyrillic).join(' '), text: chunk.words.map((x) => x.plain).join(' '), slow: true, key: 'word' }); }} />
        <${DockBtn} icon="bookmark" fill=${kept} on=${kept} label=${kept ? 'Gemerkt' : 'Wort merken'} onClick=${keep} />
      </div>
    </div><//>`;
}

// ── Schritt 3: Mitnehmen ───────────────────────────────────────────────────

function buildQuiz(piece, picked) {
  const qs = [];
  for (const i of picked) {
    const l = piece.lines[i];
    const others = shuffled(piece.uniq.filter((o) => o.i !== i && o.de && o.de !== l.de)).slice(0, 2).map((o) => o.de);
    if (l.de && others.length === 2) qs.push({ type: 'hear', line: l, options: shuffled([l.de, ...others]), answer: l.de });
  }
  const all = piece.uniq.flatMap((l) => l.chunks.filter((c) => c.words.length === 1 && c.words[0].plain.length >= 3));
  for (const i of picked) {
    const l = piece.lines[i];
    const cand = l.chunks.filter((c) => c.words.length === 1 && c.words[0].plain.length >= 3);
    if (!cand.length) continue;
    const gap = [...cand].sort((a, b) => b.words[0].plain.length - a.words[0].plain.length)[0];
    const seen = new Set([gap.words[0].key]);
    const wrong = [];
    for (const c of shuffled(all)) { if (!seen.has(c.words[0].key) && c.gloss !== gap.gloss) { seen.add(c.words[0].key); wrong.push(c); } if (wrong.length === 2) break; }
    qs.push({ type: 'gap', line: l, gap, options: shuffled([gap, ...wrong]), answer: gap });
  }
  return qs;
}

function Quiz({ piece, picked, cards, pl, onDone }) {
  // Einmal bauen: sonst würfelt jede Neuzeichnung die Antworten neu.
  const qs = useMemo(() => buildQuiz(piece, picked), [piece, picked.join(',')]);
  const [n, setN] = useState(0);
  const [pick, setPick] = useState(null);
  const [right, setRight] = useState(0);
  const q = qs[n];
  const hear = () => pl.playSeg(q.line.a, q.line.b, { key: 'line' });
  const choose = (o) => {
    if (pick != null) return;
    const ok = o === q.answer;
    setPick(o);
    if (ok) { setRight(right + 1); fx.right(); } else fx.wrong();
    // Die Antwort zählt für die Karte dieses Satzes — wie in jeder anderen Übung.
    const card = cards?.[q.line.i];
    if (card) progress.apply({ xp: 0, graded: { [card]: ok } });
    if (q.type === 'gap') hear();
  };
  const next = () => { if (n + 1 >= qs.length) onDone(right, qs.length); else { setN(n + 1); setPick(null); } };
  const answered = pick != null;
  return html`<div class="scroll"><div class="ls-body">
    <div class="ls-linehd"><span class="n">Übung ${n + 1} von ${qs.length}</span></div>
    ${q.type === 'hear' ? html`<div class="ls-q"><b>Was bedeutet diese Zeile?</b>
        <button class="btn sm tonal" onClick=${hear}><${Icon} n="volume_up" fill />Zeile hören</button></div>
      <div class="stack g8 mt12">${q.options.map((o) => html`<${Opt} state=${answered ? (o === q.answer ? 'ok' : o === pick ? 'bad' : 'dim') : null} onClick=${() => choose(o)}>${bare(o)}<//>`)}</div>
      ${answered && html`<div class="ls-reveal"><div class="lat">${q.line.chunks.flatMap((c) => c.words).map((w) => html`${w.before}${latin(w.latin)}${w.after} `)}</div></div>`}`
    : html`<div class="ls-q"><b>Welches Wort fehlt?</b><span class="sub">${bare(q.line.de)}</span></div>
      <div class="ls-gap">${q.line.chunks.map((c) => (c === q.gap
        ? html`<span class=${cls('hole', answered && 'filled')}>${answered ? latin(c.words[0].latin) : ''}</span>${c.words[0].after} `
        : html`${c.words.map((w) => html`${w.before}${latin(w.latin)}${w.after} `)}`))}</div>
      <div class="stack g8 mt12">${q.options.map((o) => html`<${Opt} state=${answered ? (o === q.answer ? 'ok' : o === pick ? 'bad' : 'dim') : null} onClick=${() => choose(o)}>
        <b>${latin(o.words[0].latin)}</b>${answered && html`<span class="sub" style="margin-left:8px">${o.gloss}</span>`}<//>`)}</div>`}
  </div></div>
  <div class="bottom"><button class="btn" disabled=${!answered} onClick=${next}>${n + 1 >= qs.length ? 'Fertig' : 'Weiter'}</button></div>`;
}

function NextRow({ next }) {
  if (!next) return null;
  return html`<button class="ls-next" onClick=${() => nav(`erleben/${next.piece.id}`, { replace: true })}>
    <span class="th"><${Pic} file=${next.piece.poster} /></span>
    <span class="tx"><span class="k">${next.why}</span><b class="ell">${next.piece.head}</b>
      <span class="d ell">${KIND[next.piece.kind][0]}, ${fmt(next.piece.dur)}</span></span>
    <${Icon} n="chevron_right" c="faint" /></button>`;
}

function Take({ piece, pl, ps, s, next, onReplay }) {
  const [phase, setPhase] = useState(ps.done ? 'done' : 'pick');
  const [sel, setSel] = useState(() => (ps.marks.length ? ps.marks : piece.take).slice().sort((a, b) => a - b));
  const [result, setResult] = useState(null);
  const [cards, setCards] = useState({});
  const suggested = ps.marks.length === 0;
  const flip = (i) => setSel(sel.includes(i) ? sel.filter((x) => x !== i) : [...sel, i].sort((a, b) => a - b));
  const start = () => {
    // Jeder gewählte Satz kommt ins Satzbuch und als Karte in die Wiederholung.
    const ids = {};
    for (const i of sel) { const l = piece.lines[i]; ids[i] = learn(piece, l.ru, bare(l.de), l.g); }
    setCards(ids);
    store.piece(piece.id, { marks: sel });
    setPhase('quiz');
  };
  const finish = (right, total) => {
    setResult({ right, total });
    // Über apply statt addXp: ein durchgearbeitetes Stück zählt als Lerntag für die Serie.
    if (!ps.done) progress.apply({ xp: 10 + right * 5 });
    store.piece(piece.id, { done: true, stage: 2, doneAt: progress.today() });
    // Woran du gearbeitet hast, bleibt: ab in die Bibliothek.
    if (!FEED.saved.includes(piece.id)) feed.save(piece.id, true);
    fx.win();
    setPhase('done');
  };
  if (phase === 'quiz') return html`<${Quiz} piece=${piece} picked=${sel.slice(0, 4)} cards=${cards} pl=${pl} onDone=${finish} />`;
  if (phase === 'done') {
    const words = Object.values(s.words).filter((w) => w.piece === piece.id).length;
    const seen = !!ps.rating;   // schon einmal ohne Deutsch angesehen und eingeschätzt
    return html`<div class="scroll"><div class="ls-body">
      <div class="ls-donehd"><${Icon} n="verified" fill c="amber" /><div><b>Durchgearbeitet</b><span>${ps.marks.length === 1 ? 'Ein Satz liegt' : `${ps.marks.length} Sätze liegen`} in deinem Satzbuch und ${ps.marks.length === 1 ? 'kommt' : 'kommen'} in die Wiederholung.</span></div></div>
      <div class="stat3 mt16">
        <div><b><${Icon} n="bookmark" fill c="cc" />${ps.marks.length}</b><span>Sätze mitgenommen</span></div>
        <div><b><${Icon} n="translate" c="cc" />${words}</b><span>Wörter gemerkt</span></div>
        ${result ? html`<div><b><${Icon} n="task_alt" c="good" />${result.right}/${result.total}</b><span>richtig geübt</span></div>` : html`<div><b><${Icon} n="replay" c="cc" />${piece.uniq.length}</b><span>Zeilen verstanden</span></div>`}
      </div>
      ${seen
        ? html`<div class="ls-after mt20"><b>Das Stück liegt in deiner Bibliothek</b><span>Zuletzt hast du ${UNDERSTOOD[ps.rating]} verstanden. Die Bibliothek schlägt es dir ${REWATCH_DAYS[ps.rating] === 1 ? 'morgen' : `in ${REWATCH_DAYS[ps.rating]} Tagen`} wieder vor.</span></div>
          ${next && html`<div class="ls-nexthd">Wie es weitergeht</div><${NextRow} next=${next} />`}`
        : html`<div class="ls-after mt20"><b>Jetzt der beste Teil</b><span>Sieh es dir noch einmal an, diesmal ohne Deutsch. Du wirst mehr verstehen als beim ersten Mal.</span></div>`}
    </div></div>
    <div class="bottom">${seen
      ? html`${next ? html`<button class="btn" onClick=${() => nav(`erleben/${next.piece.id}`, { replace: true })}>Nächstes Stück<${Icon} n="arrow_forward" /></button>`
          : html`<button class="btn" onClick=${() => back('erleben')}>Zur Übersicht</button>`}
        <button class="btn ghost" onClick=${onReplay}>Nochmal ohne Deutsch ansehen</button>`
      : html`<button class="btn" onClick=${onReplay}><${Icon} n="play_arrow" fill />Ohne Deutsch ansehen</button>
        <button class="btn ghost" onClick=${() => back('erleben')}>Zur Übersicht</button>`}</div>`;
  }
  return html`<div class="scroll"><div class="ls-body">
    <div class="ls-q"><b>Was nimmst du mit?</b><span class="sub">${suggested ? 'Mein Vorschlag ist angehakt. Zwei, drei Sätze reichen.' : 'Deine gemerkten Zeilen sind angehakt. Zwei, drei Sätze reichen.'}</span></div>
    <div class="list mt12">${piece.uniq.map((l) => html`<div class=${cls('ls-pick', sel.includes(l.i) && 'on')}>
      <button class="chk" onClick=${() => flip(l.i)} aria-label="Auswählen"><${Icon} n=${sel.includes(l.i) ? 'check_circle' : 'radio_button_unchecked'} fill=${sel.includes(l.i)} /></button>
      <button class="tx" onClick=${() => flip(l.i)}><b>${l.chunks.flatMap((c) => c.words).map((w) => html`${w.before}${latin(w.latin)}${w.after} `)}</b><span>${bare(l.de)}</span></button>
      <button class="iconbtn" onClick=${() => pl.playSeg(l.a, l.b, { key: `p${l.i}` })} aria-label="Zeile hören"><${Icon} n=${pl.playing && pl.segKey === `p${l.i}` ? 'graphic_eq' : 'volume_up'} /></button>
    </div>`)}</div>
  </div></div>
  <div class="bottom"><button class="btn" disabled=${sel.length === 0} onClick=${start}>
    ${sel.length === 0 ? 'Wähle mindestens einen Satz' : sel.length === 1 ? 'Einen Satz mitnehmen und üben' : `${sel.length} Sätze mitnehmen und üben`}</button></div>`;
}

// ── Ein Stück ──────────────────────────────────────────────────────────────

const STAGES = ['Fühlen', 'Verstehen', 'Mitnehmen'];

export function ErlebenPiece({ id }) {
  const pieces = usePieces();
  const piece = pieces?.find((p) => p.id === id);
  if (!pieces) return html`<div class="screen"></div>`;
  if (!piece) return html`<div class="screen"><${TopBar} title="Lieder & Szenen" onBack=${() => back('erleben')} /><div class="empty"><b>Dieses Stück gibt es nicht.</b></div></div>`;
  return html`<${PieceView} piece=${piece} key=${id} />`;
}

function PieceView({ piece }) {
  const s = useStore();
  const ps = pieceState(s, piece.id);
  const known = useKnown();
  const pieces = usePieces() ?? [];
  const f = useFeed();
  const pl = usePlayer(piece);
  const today = progress.today();
  const [stage, setStage] = useState(ps.done ? 0 : ps.stage);
  const [sheet, setSheet] = useState(null);
  // Nur für diese Sitzung: Hast du das fertige Stück gerade noch einmal angesehen und eingeschätzt?
  const [watched, setWatched] = useState(false);
  const [rated, setRated] = useState(false);
  useEffect(() => () => audio.stop(), []);
  const saved = f.saved.includes(piece.id);
  const next = pickNext(pieces, s, f, piece.id, today);
  const go = (n) => { pl.stopSeg(); setSheet(null); setStage(n); store.piece(piece.id, { stage: Math.max(n, ps.stage), started: true }); };
  const watch = () => { store.piece(piece.id, { started: true }); setWatched(true); setRated(false); pl.play(); };
  const toLine = (i) => {
    store.piece(piece.id, { line: i, started: true });
    const l = piece.uniq[i];
    pl.playSeg(l.a, l.b, { key: 'line' });
  };
  const replayPlain = () => {
    store.update((x) => ({ ...x, subs: { ...x.subs, lat: true, de: false } }));
    setStage(0); setWatched(true); setRated(false);
    pl.seek(0);
  };
  const rate = (n) => {
    store.piece(piece.id, { rating: n, ratedAt: today });
    // Das nächste neue Stück beginnt wieder mit deutschem Text.
    store.update((x) => ({ ...x, subs: { ...x.subs, de: true } }));
    setRated(true);
  };
  const toggleSaved = async () => {
    const ok = await feed.save(piece.id, !saved);
    toast(!ok ? 'Der Hikari-Server ist gerade nicht erreichbar' : saved ? 'Aus der Bibliothek entfernt' : 'In der Bibliothek: Dieses Stück bleibt');
  };
  const idx = Math.min(ps.line, piece.uniq.length - 1);
  // In »Verstehen« und »Mitnehmen« spielt ein Tipp aufs Video die aktuelle Zeile.
  const stageTap = stage === 1 ? () => (pl.playing ? pl.pause() : pl.playSeg(piece.uniq[idx].a, piece.uniq[idx].b, { key: 'line' })) : stage === 0 ? () => (pl.playing ? pl.pause() : watch()) : null;
  // Fertiges Stück, gerade zu Ende angesehen: erst einschätzen, dann weiter — kein Kreislauf durch die Schritte.
  const closing = ps.done && watched && pl.ended;
  const ending = !closing ? null : !rated
    ? html`<div class="ls-rate"><b>Wie viel hast du ohne Deutsch verstanden?</b>
        <div>${RATINGS.map(([n, label]) => html`<button onClick=${() => rate(n)}>${label}</button>`)}</div></div>`
    : html`<div class="ls-after"><b>${ps.rating === 3 ? 'Stark. Das sitzt.' : ps.rating === 2 ? 'Gut. Es wird.' : 'Notiert. Das kommt mit dem Wiedersehen.'}</b>
        <span>Die Bibliothek schlägt dir das Stück ${REWATCH_DAYS[ps.rating] === 1 ? 'morgen' : `in ${REWATCH_DAYS[ps.rating]} Tagen`} wieder vor.</span></div>
      ${next && html`<div class="ls-nexthd">Wie es weitergeht</div><${NextRow} next=${next} />`}`;
  const fresh = !(ps.done ? watched : ps.started) && !pl.playing && !pl.ended;
  return html`<div class="screen c-live ls-piece">
    <${TopBar} title=${piece.head} sub=${piece.auto ? `${KIND[piece.kind][0]}${piece.topic ? ` zu »${piece.topic}«` : ''}, ${piece.by}` : `${piece.titleDe}, ${piece.part}`} onBack=${() => back('erleben')}
      right=${html`<button class=${cls('iconbtn', saved && 'tint')} onClick=${toggleSaved} aria-label=${saved ? 'Aus der Bibliothek entfernen' : 'In die Bibliothek'}><${Icon} n="bookmark" fill=${saved} /></button>`} />
    <${Stage} piece=${piece} pl=${pl} onTap=${stageTap} quiet=${stage > 0} />
    <div class="ls-stages">${STAGES.map((t, i) => html`<button class=${cls(stage === i && 'on', (ps.done || i < ps.stage) && stage !== i && 'done')} onClick=${() => go(i)}>
      <i>${(ps.done || i < ps.stage) && stage !== i ? html`<${Icon} n="check" size="13" />` : i + 1}</i>${t}</button>`)}</div>

    ${stage === 0 && html`<${Feel} piece=${piece} pl=${pl} ps=${ps} s=${s} ending=${ending} />
      <div class="bottom">${fresh
        ? (pl.ready ? html`<button class="btn" onClick=${watch}><${Icon} n="play_arrow" fill />${ps.done ? 'Nochmal ansehen' : 'Ansehen'}</button>`
          : html`<button class="btn" disabled=${!pl.clip.failed} onClick=${pl.clip.retry}>${clipLabel(pl.clip)}</button>`)
        : !ps.done ? html`<button class=${cls('btn', !pl.ended && 'ghost')} onClick=${() => { go(1); toLine(ps.line < piece.uniq.length ? idx : 0); }}>Zeile für Zeile verstehen<${Icon} n="arrow_forward" /></button>`
        : closing && rated && next ? html`<button class="btn" onClick=${() => nav(`erleben/${next.piece.id}`, { replace: true })}>Nächstes Stück<${Icon} n="arrow_forward" /></button>
            <button class="btn ghost" onClick=${() => back('erleben')}>Zur Übersicht</button>`
        : html`<button class=${cls('btn', !(closing && rated) && 'ghost')} onClick=${() => back('erleben')}>Zur Übersicht</button>`}</div>`}

    ${stage === 1 && html`<${Understand} piece=${piece} pl=${pl} ps=${ps} s=${s} known=${known} onWord=${(chunk, line) => setSheet({ chunk, line })} toLine=${toLine} onTake=${() => go(2)} />`}

    ${stage === 2 && html`<${Take} piece=${piece} pl=${pl} ps=${ps} s=${s} next=${next} onReplay=${replayPlain} />`}

    ${sheet && html`<${WordSheet} chunk=${sheet.chunk} line=${sheet.line} piece=${piece} known=${known} s=${s} pl=${pl} onClose=${() => setSheet(null)} />`}
  </div>`;
}
