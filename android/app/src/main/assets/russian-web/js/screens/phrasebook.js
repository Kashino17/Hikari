// Tab »Satzbuch«: alle Sätze durchsuchen, nach Lernstand filtern, vertiefen, testen.
import { html, cls, Icon, TopBar, IL, Sheet, PhraseDock, SentenceCard, OtherFormLine, Speaker, Empty, usePlaying, playPhrase, mk, useState, useMemo, useEffect, useRef, fx } from '../ui.js';
import { D, P, useProgress, progress, nav, back, testSelection, phrasebookPreset, myPhrases, otherForm, exitApp } from '../app.js';
import * as Ct from '../domain/content.js';

const STATUS = (box) => (box >= 4 ? ['Sitzt', 'good'] : box === 3 ? ['Aktiv', 'review'] : box >= 1 ? ['Wackelig', 'amber'] : ['Neu', 'faint']);
const STATUS_COLOR = { good: 'var(--good)', review: 'rgb(var(--review))', amber: 'var(--amber)', faint: 'var(--faint)' };

/** Karte für einen Satz: Anhören, Langsam, Nachsprechen, KI-Prüfung — überall gleich (Vertiefung, Muster, Meine Sätze). */
export function PhraseCard({ p, highlight, small, explain, onEvaluated, children }) {
  return html`<div class=${cls('pcardx', highlight && 'hl', small && 'small')}>
    <${SentenceCard} phrase=${p} size=${highlight ? 22 : small ? 16 : 19} explain=${explain} notes=${!small} />
    ${children && html`<div class="rowx g8 wrap" style="margin-top:2px">${children}</div>`}
    <${PhraseDock} phrase=${p} size="sm" onEvaluated=${onEvaluated} />
  </div>`;
}

function Row({ p, card, fav, open, onToggle }) {
  const box = card?.box ?? 0;
  const [label, tone] = STATUS(box);
  const themes = (D.themeOf[p.id] ?? []).map((id) => D.themes.find((t) => t.id === id)?.title).filter(Boolean);
  const grade = (passed) => progress.gradeCard(p.id, passed, passed ? 5 : 1, passed ? 1 : 0);
  return html`<div>
    <button class="row" style="align-items:flex-start" onClick=${onToggle}>
      <div class="tx"><${IL} ru=${p.ru} size=${open ? 20 : 16} gloss=${open} /><span style=${`margin-top:${open ? 8 : 3}px;font-size:${open ? 14.5 : 12.5}px`}>${p.de}</span></div>
      <div class="stack" style="align-items:flex-end;gap:6px;flex:none">
        <span style=${`font-size:11.5px;font-weight:600;color:${STATUS_COLOR[tone]}`}>${label}</span>
        <div class="rowx" style="gap:2px">${[1, 2, 3, 4, 5, 6].map((i) => html`<i style=${`width:5px;height:10px;border-radius:2px;background:${i <= box ? STATUS_COLOR[tone] : 'var(--border2)'}`}></i>`)}</div>
      </div>
    </button>
    ${open && html`<div class="rowdetail">
      <${PhraseDock} phrase=${p} size="sm" onEvaluated=${grade} />
      ${(p.lit || p.note || themes.length > 0) && html`<div class="snotes">
        ${p.lit && html`<div class="snote"><${Icon} n="translate" size="17" /><span class="grow"><span class="faint">Wörtlich:</span> ${p.lit}</span></div>`}
        ${p.note && html`<div class="snote"><${Icon} n="lightbulb" size="17" /><span class="grow">${p.note}</span></div>`}
        <${OtherFormLine} id=${p.id} />
        ${themes.length > 0 && html`<div class="snote"><${Icon} n="sell" size="17" /><span class="grow faint">${themes.join(', ')}</span></div>`}</div>`}
      <div class="rowx g8">
        <button class=${cls('chip sm', fav && 'on c c-amber')} onClick=${() => { fx.tap(); progress.toggleFavorite(p.id); }}><${Icon} n="star" fill=${fav} />${fav ? 'Gemerkt' : 'Merken'}</button>
        <button class="chip sm" onClick=${() => nav(`deep/${p.id}`)}><${Icon} n="travel_explore" />${D.deepdive.has(p.id) ? 'Vertiefen' : 'Mehr dazu'}</button></div>
    </div>`}
  </div>`;
}

const FILTERS = [
  ['all', 'Alle', () => true],
  ['seen', 'Geübt', (p, c) => !!c],
  ['weak', 'Wackelig', (p, c) => c && c.box <= 2],
  ['known', 'Sitzt', (p, c) => c && c.box >= 3],
  ['fav', 'Favoriten', (p, c, s) => s.favorites.includes(p.id)],
];

export function Phrasebook() {
  const s = useProgress();
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState('all');
  const [theme, setTheme] = useState(() => { const t = phrasebookPreset.theme; phrasebookPreset.theme = null; return t; });
  const [byTheme, setByTheme] = useState(true);
  const [openId, setOpenId] = useState(null);
  const [themeSheet, setThemeSheet] = useState(false);
  const [limit, setLimit] = useState(40);
  const scroller = useRef(null);
  const settings = P.settingsOf(s);
  const all = useMemo(() => [...D.index.days.flatMap((d) => D.index.dayPhrases(d, settings)), ...D.podIndex.days.flatMap((d) => D.podIndex.dayPhrases(d, settings))], [settings.gender, settings.voice]);
  const mine = filter === 'mine';
  const shown = useMemo(() => {
    if (mine) return myPhrases().filter((p) => Ct.searchMatches(p, query));
    const f = FILTERS.find((x) => x[0] === filter)[2];
    return all.filter((p) => Ct.searchMatches(p, query) && f(p, s.cards[p.id], s) && (!theme || (D.themeOf[p.id] ?? []).includes(theme)));
  }, [all, query, filter, theme, s.cards, s.favorites, s.mySentences, mine]);
  useEffect(() => { setLimit(40); scroller.current?.scrollTo(0, 0); }, [query, filter, theme, byTheme]);

  const groups = useMemo(() => {
    if (mine) return [];
    const map = new Map();
    const key = (p) => {
      if (byTheme) return (D.themeOf[p.id] ?? []).map((id) => D.themes.find((t) => t.id === id)?.title).find(Boolean) ?? 'Podcast-Kurs';
      return D.index.itemById.has(p.id) ? `Tag ${p.day} · ${D.index.day(p.day).title}` : `Podcast · Lektion ${p.day} · ${D.podIndex.day(p.day).title}`;
    };
    for (const p of shown.slice(0, limit)) { const k = key(p); if (!map.has(k)) map.set(k, []); map.get(k).push(p); }
    let list = [...map.entries()];
    if (byTheme) { const order = [...D.themes.map((t) => t.title), 'Podcast-Kurs']; list = list.sort((a, b) => order.indexOf(a[0]) - order.indexOf(b[0])); }
    return list;
  }, [shown, limit, byTheme, mine]);

  const send = (to, ids) => { testSelection.ids = ids; nav(to); };
  const ids = shown.map((p) => p.id);
  const count = (f) => (f === 'mine' ? s.mySentences.length : f === 'all' ? all.length : all.filter((p) => FILTERS.find((x) => x[0] === f)[2](p, s.cards[p.id], s)).length);
  const onScroll = (e) => { const el = e.currentTarget; if (el.scrollTop + el.clientHeight > el.scrollHeight - 600 && limit < shown.length) setLimit((l) => l + 40); };

  return html`<div class="screen tab c-words">
    <${TopBar} title="Satzbuch" big onBack=${exitApp} sub=${`${Object.keys(s.cards).length} von ${all.length} geübt · ${P.knownCount(s.cards)} sitzen`}
      right=${html`<button class="chip sm" style="margin-right:10px" onClick=${() => setByTheme(!byTheme)}><${Icon} n="swap_vert" />${byTheme ? 'Nach Thema' : 'Nach Tag'}</button>`} />
    <div class="pad"><label class="search"><${Icon} n="search" size="22" />
      <input value=${query} onInput=${(e) => setQuery(e.target.value)} placeholder="Deutsch, Russisch oder »spasibo«" enterkeyhint="search" />
      ${query && html`<button class="iconbtn" onClick=${() => setQuery('')} aria-label="Löschen"><${Icon} n="close" /></button>`}</label></div>
    <div class="chips mt12">
      ${[...FILTERS.map((f) => f.slice(0, 2)), ['mine', 'Meine Sätze']].map(([id, label]) => html`<button class=${cls('chip', filter === id && 'on')} onClick=${() => setFilter(id)}>${label}<span style="opacity:.55">${count(id)}</span></button>`)}
      ${!mine && html`<button class=${cls('chip', theme && 'on c')} onClick=${() => setThemeSheet(true)}><${Icon} n="sell" />${theme ? D.themes.find((t) => t.id === theme).title : 'Thema'}</button>`}
    </div>
    <div class="scroll mt12" ref=${scroller} onScroll=${onScroll}>
      ${shown.length > 0 && html`<div class="pad rowx g8">
        <button class="btn sm tonal c-speak grow" onClick=${() => send('reflex', ids)}><${Icon} n="bolt" />Reflex · ${Math.min(ids.length, 10)}</button>
        ${!mine && html`<button class="btn sm tonal c-words grow" onClick=${() => send('kombi', ids)}><${Icon} n="extension" />Kombi</button>
        <button class="btn sm tonal c-review grow" onClick=${() => send('test', ids)}><${Icon} n="quiz" />Test · ${Math.min(ids.length, 15)}</button>`}
      </div>`}
      ${shown.length === 0 && html`<${Empty} icon=${mine ? 'edit_note' : 'search_off'} title=${query ? `Nichts gefunden für »${query}«` : mine ? 'Noch keine eigenen Sätze' : 'Hier ist noch nichts'}
        text=${mine && !query ? 'Im Kurs »Satzbau« und im Kombi-Meister baust und speicherst du eigene Sätze.' : 'Probier einen anderen Filter oder ein anderes Wort.'} />`}
      ${mine && html`<div class="pad stack g10 mt12 c-words">${shown.map((p) => html`<${PhraseCard} p=${p} key=${p.id}>
        <button class="chip sm" onClick=${() => progress.removeMySentence(p.ru)}><${Icon} n="delete" />Entfernen</button><//>`)}</div>`}
      ${groups.map(([title, list]) => html`<div class="sec" style="padding-top:18px" key=${title}><div class="over" style="margin:0 4px 8px">${title}</div>
        <div class="list">${list.map((p) => html`<${Row} key=${p.id} p=${p} card=${s.cards[p.id]} fav=${s.favorites.includes(p.id)} open=${openId === p.id}
          onToggle=${() => { const o = openId === p.id ? null : p.id; setOpenId(o); if (o) playPhrase(p); }} />`)}</div></div>`)}
      ${!mine && limit < shown.length && html`<div class="center sub" style="padding:18px">${shown.length - limit} weitere — einfach weiterscrollen</div>`}
      <div class="end"></div>
    </div>
    ${themeSheet && html`<${Sheet} onClose=${() => setThemeSheet(false)} title="Thema wählen">
      <div class="rowx g8 wrap mt12"><button class=${cls('chip', !theme && 'on')} onClick=${() => { setTheme(null); setThemeSheet(false); }}>Alle Themen</button>
        ${D.themes.map((t) => html`<button class=${cls('chip', theme === t.id && 'on c')} onClick=${() => { setTheme(t.id); setThemeSheet(false); }}>${t.title}</button>`)}</div>
      <p class="faint mt16" style="font-size:12px">Themen gibt es für die 300 Sätze des Sprechkurses. Podcast-Sätze findest du über »Nach Tag« oder die Suche.</p><//>`}
  </div>`;
}

// ── Vertiefung ─────────────────────────────────────────────────────────────

function Sec({ title, hint, children }) {
  return html`<div class="sec"><div class="h2">${title}</div><div class="sub">${hint}</div><div class="stack g10">${children}</div></div>`;
}

export function Deepdive({ id }) {
  const s = useProgress();
  const settings = P.settingsOf(s);
  const p = D.index.phrase(id, settings) ?? D.podIndex.phrase(id, settings);
  const e = D.deepdive.get(id);
  const related = useMemo(() => (p ? Ct.related(p, D.index.phrasesUpTo(P.unlockedDay(s, D.index.days.length), settings)) : []), [id]);
  if (!p) return html`<div class="screen"><${TopBar} title="Vertiefen" onBack=${() => back('satzbuch')} /><${Empty} title="Dieser Satz wurde nicht gefunden." /></div>`;
  const fav = s.favorites.includes(id);
  return html`<div class="screen c-words">
    <${TopBar} title="Vertiefen" onBack=${() => back('satzbuch')} right=${html`<button class=${cls('iconbtn', fav && 'on')} onClick=${() => progress.toggleFavorite(id)} aria-label="Favorit"><${Icon} n="star" fill=${fav} /></button>`} />
    <div class="scroll"><div class="pad stack g12">
      <${PhraseCard} p=${p} highlight onEvaluated=${(ok) => progress.gradeCard(p.id, ok, ok ? 5 : 1, ok ? 1 : 0)} />
      ${e ? html`<div><span class="tag c-amber">${mk(e.register)}</span><p class="mt12" style="line-height:1.55;color:rgba(255,255,255,.85)">${mk(e.summary)}</p></div>`
        : html`<p class="sub">Für diesen Satz gibt es noch keine ausführliche Vertiefung. Unten siehst du, wo seine Wörter noch vorkommen.</p>
          ${p.lit && html`<p class="sub">Wörtlich: »${p.lit}«</p>`}${p.note && html`<p class="sub">${mk(p.note)}</p>`}`}
    </div>
    ${e?.variants?.length > 0 && html`<${Sec} title="Synonyme und Varianten" hint="Wann sagt man was? Locker, höflich, förmlich.">
      ${e.variants.map((v) => html`<${PhraseCard} p=${Ct.asPhrase(v.ru, v.de)} explain=${v.use ? `Wann: ${mk(v.use)}` : null} />`)}<//>`}
    ${e?.examples?.length > 0 && html`<${Sec} title="Beispielsätze mit Erklärung" hint="So sitzt das Wort im Satz — und warum es grammatisch so steht.">
      ${e.examples.map((v) => html`<${PhraseCard} p=${Ct.asPhrase(v.ru, v.de)} explain=${v.why ? `Warum so: ${mk(v.why)}` : null} />`)}<//>`}
    ${e?.gender && html`<${Sec} title="Männlich / weiblich" hint=${e.gender.title}><p style="line-height:1.55;font-size:14.5px;color:rgba(255,255,255,.85)">${mk(e.gender.text)}</p>
      ${(e.gender.forms ?? []).map((v) => html`<${PhraseCard} p=${Ct.asPhrase(v.ru, v.de)} explain=${v.use ? mk(v.use) : null} />`)}<//>`}
    ${e?.grammar && html`<${Sec} title="Grammatik dahinter" hint="Die Regel, mit der du eigene Sätze baust."><div class="hint"><p>${mk(e.grammar)}</p></div><//>`}
    ${related.length > 0 && html`<${Sec} title="Wo es noch vorkommt" hint="Dieselben Wörter in anderen Sätzen deines Kurses.">
      ${related.map((r) => html`<${PhraseCard} p=${r} small><button class="chip sm" onClick=${() => nav(`deep/${r.id}`)}><${Icon} n="north_east" />Öffnen</button><//>`)}<//>`}
    <div class="end"></div></div>
  </div>`;
}
