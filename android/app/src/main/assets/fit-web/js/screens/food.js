// Ernährung: Tagebuch mit Foto-Tracker, Nährstoff-Check, Ernährungs-Coach, Tagesplan, Einkaufsliste.
import { html, useEffect, useState } from '../../vendor/preact-htm.js';
import { api, pickPhoto } from '../platform.js';
import { back, nav, useData, setData, refresh, today as todayStr, relDay, toast, fmtNum, SLOT_NAMES, SLOT_ICONS, slotNow, img, recipeImg, parseDay } from '../app.js';
import { Icon, Ring, Bar, Sheet, Top, Seg, cls, Spinner, Empty } from '../ui.js';
import { RecipeBrowse } from './recipes.js';

const SLOTS = ['fruehstueck', 'mittag', 'abend', 'snack'];
const shift = (d, n) => { const x = parseDay(d); x.setDate(x.getDate() + n); return `${x.getFullYear()}-${String(x.getMonth() + 1).padStart(2, '0')}-${String(x.getDate()).padStart(2, '0')}`; };
let foodTab = 'tagebuch';
let foodDay = null;

export function Food() {
  const [tab, setTab] = useState(foodTab);
  const [day, setDay] = useState(foodDay ?? todayStr());
  useEffect(() => { foodTab = tab; foodDay = day; }, [tab, day]);
  const isToday = day === todayStr();
  return html`<div class="screen tab c-food">
    <div class="scroll">
      <header class="top big"><div class="tt"><h1>Ernährung</h1><div class="t2">${tab === 'rezepte' ? 'Rezepte passend zu deinem Ziel' : relDay(day, todayStr())}</div></div>
        ${tab !== 'rezepte' && html`<button class="ib" onClick=${() => setDay(shift(day, -1))} aria-label="Vortag"><${Icon} n="chevron_left" /></button>
        <button class="ib" disabled=${isToday} onClick=${() => setDay(shift(day, 1))} aria-label="Folgetag"><${Icon} n="chevron_right" /></button>`}</header>
      <div class="pad"><${Seg} items=${[['tagebuch', 'Tagebuch'], ['plan', 'Tagesplan'], ['rezepte', 'Rezepte']]} value=${tab} onChange=${setTab} /></div>
      <div class="pad mt16">
        ${tab === 'tagebuch' && html`<${Diary} day=${day} />`}
        ${tab === 'plan' && html`<${DayPlan} day=${day} />`}
        ${tab === 'rezepte' && html`<${RecipeBrowse} />`}
      </div>
      <div style="height:90px" />
    </div>
    ${tab === 'tagebuch' && isToday && html`<button class="fab" onClick=${() => nav(`foto/${slotNow()}`)}><${Icon} n="photo_camera" />Foto</button>`}
  </div>`;
}

function Diary({ day }) {
  const [d] = useData(`/fitness/food/day?day=${day}`, `/fitness/food/day?day=${day}`, [day]);
  const [add, setAdd] = useState(null);
  const [allN, setAllN] = useState(false);
  if (!d) return html`<div class="skel" style="height:420px" />`;
  const t = d.targets;
  const k = d.totals.kcal ?? 0;
  const left = Math.round(t.kcal - k);
  const mac = [['protein', 'Eiweiß', t.protein], ['carbs', 'Kohlenhydrate', t.carbs], ['fat', 'Fett', t.fat]];
  const st = d.status.filter((s) => !['kcal', 'protein', 'carbs', 'fat'].includes(s.k));
  const lows = st.filter((s) => s.min && s.pct != null).sort((a, b) => a.pct - b.pct).slice(0, 4);
  const overs = st.filter((s) => s.max && s.value > s.max);
  return html`<div>
    <div class="card pad16 tinted">
      <div class="kcalHero">
        <${Ring} size=${128} stroke=${11} value=${k} max=${t.kcal} color=${k > t.kcal * 1.05 ? 'rgb(var(--bad))' : 'rgb(var(--food))'}>
          <div class="num" style="font-size:27px;font-weight:700;letter-spacing:-.5px">${fmtNum(Math.abs(left))}</div><div class="tiny muted">${left >= 0 ? 'kcal übrig' : 'kcal drüber'}</div><//>
        <div class="side">
          <div class="kv"><span class="muted">Gegessen</span><b class="num">${fmtNum(k)} kcal</b></div>
          <div class="kv"><span class="muted">Ziel</span><b class="num">${fmtNum(t.kcal)} kcal</b></div>
          <div class="kv"><span class="muted">Mahlzeiten</span><b>${d.entries.length}</b></div>
        </div>
      </div>
      <div class="macros mt16">${mac.map(([key, l, goal]) => html`<div class="macro"><div class="l"><span>${l}</span></div><div class="v num">${fmtNum(d.totals[key] ?? 0)}<span class="muted" style="font-weight:400"> / ${goal} g</span></div><${Bar} value=${d.totals[key] ?? 0} max=${goal} /></div>`)}</div>
    </div>

    ${SLOTS.map((slot) => {
      const es = d.entries.filter((e) => e.meal === slot);
      const kc = es.reduce((a, e) => a + (e.totals.kcal ?? 0), 0);
      return html`<div class="meal">
        <div class="mh"><span class="mt row gap8"><${Icon} n=${SLOT_ICONS[slot]} style="font-size:20px;color:rgb(var(--food))" />${SLOT_NAMES[slot]}</span><span class="mk">${kc ? `${fmtNum(kc)} kcal` : ''}</span></div>
        ${es.map((e) => html`<button class="entry" onClick=${() => nav(`eintrag/${e.id}`)}>
          <div class="pic">${e.photo ? html`<img src=${`/api/fitness/photo/${e.photo}`} alt="" />` : html`<${Icon} n=${e.source === 'rezept' ? 'menu_book' : 'restaurant'} />`}</div>
          <div class="grow"><div style="font-weight:500" class="ell">${e.title}</div><div class="small muted">${fmtNum(e.totals.kcal)} kcal · ${fmtNum(e.totals.protein)} g Eiweiß</div></div>
          <${Icon} n="chevron_right" cls="faint" /></button>`)}
        <button class="addMeal mt8" onClick=${() => setAdd(slot)}><${Icon} n="add" />Hinzufügen</button>
      </div>`;
    })}

    ${d.entries.length > 0 && html`<div class="sec"><h2 class="h2">Nährstoff-Check</h2><button class="more" onClick=${() => setAllN(!allN)}>${allN ? 'Weniger' : 'Alle'}<${Icon} n=${allN ? 'expand_less' : 'expand_more'} style="font-size:18px" /></button></div>
      <div class="card pad16">
        <div class="nutri">${(allN ? st : [...lows, ...overs]).map((s) => html`<${Nu} s=${s} />`)}</div>
        <button class="btn tint mt16" onClick=${() => nav(`ernaehrung/coach/${day}`)}><${Icon} n="psychology" />Einschätzung vom Ernährungs-Coach</button>
      </div>`}
    ${add && html`<${AddSheet} slot=${add} day=${day} onClose=${() => setAdd(null)} />`}
  </div>`;
}

function Nu({ s }) {
  const low = s.min && s.pct != null && s.pct < 60;
  const over = s.max && s.value > s.max;
  return html`<div class=${cls('nu', low && 'low', over && 'over')}>
    <div class="l"><span>${s.name}</span><b class="num">${s.min ? `${s.pct ?? 0} %` : `${fmtNum(s.value, 1)} / ${s.max} ${s.unit}`}</b></div>
    <${Bar} value=${s.value} max=${s.min ?? s.max} color=${low ? 'rgb(var(--bad))' : undefined} />
  </div>`;
}

function AddSheet({ slot, day, onClose }) {
  const go = (r) => { onClose(); nav(r); };
  return html`<${Sheet} title=${`${SLOT_NAMES[slot]} hinzufügen`} onClose=${onClose}>
    <div class="list c-food">
      <button class="li" onClick=${() => go(`foto/${slot}`)}><div class="iconTile"><${Icon} n="photo_camera" /></div><div class="tx"><div class="t1">Foto machen</div><div class="t2">KI erkennt Gericht, Zutaten und Mengen</div></div></button>
      <button class="li" onClick=${() => go(`mahlzeit/neu/${slot}`)}><div class="iconTile soft"><${Icon} n="edit_note" /></div><div class="tx"><div class="t1">Beschreiben oder suchen</div><div class="t2">»2 Eier, Vollkornbrot mit Butter«</div></div></button>
      <button class="li" onClick=${() => go('rezepte')}><div class="iconTile soft"><${Icon} n="menu_book" /></div><div class="tx"><div class="t1">Aus Rezepten</div><div class="t2">Gekochtes Rezept eintragen</div></div></button>
    </div>
    ${day !== todayStr() && html`<p class="small muted mt12">Wird für ${relDay(day, todayStr())} eingetragen.</p>`}
  <//>`;
}

// ── Foto-Tracker ────────────────────────────────────────────────────────────
export function PhotoFlow({ slot }) {
  const [photo, setPhoto] = useState(null);
  const [res, setRes] = useState(null);
  const [err, setErr] = useState(null);
  const [hint, setHint] = useState('');
  const take = async (source) => {
    setErr(null);
    const p = await pickPhoto(source);
    if (!p) { if (!photo) back('ernaehrung'); return; }
    setPhoto(p); setRes(null);
    analyze(p, '');
  };
  const analyze = async (p, h) => {
    setRes(null); setErr(null);
    try {
      const r = await api.post('/fitness/food/photo', { base64: p.base64, mime: p.mime, ...(h ? { hint: h } : {}) });
      if (!r.food) setErr('Auf dem Foto ist kein Essen zu erkennen.');
      else setRes(r);
    } catch (e) { setErr(e.status === 503 ? 'Kein Gemini-Schlüssel auf dem Server.' : 'Die Erkennung hat nicht geklappt — nochmal versuchen?'); }
  };
  useEffect(() => { take('camera'); }, []);
  if (res) return html`<${MealEditor} initial=${{ title: res.dish, items: res.items, photo: res.photo, source: 'foto', note: res.note }} slot=${slot} photoSrc=${`data:image/jpeg;base64,${photo.base64}`} onRetake=${() => take('camera')} />`;
  return html`<div class="screen c-food">
    <${Top} title="Mahlzeit erkennen" onBack=${() => back('ernaehrung')} />
    ${photo ? html`<div class="photoBox"><img src=${`data:${photo.mime};base64,${photo.base64}`} alt="" />${!err && html`<div class="scan" />`}</div>
      <div class="pad mt20">${err ? html`<div class="card pad16"><div class="h3">${err}</div>
          <div class="field mt12"><label>Hinweis für die KI (optional)</label><input class="input" placeholder="z. B. »Linsen-Dal mit Reis«" value=${hint} onInput=${(e) => setHint(e.target.value)} /></div>
          <div class="row mt12"><button class="btn" onClick=${() => analyze(photo, hint)}>Nochmal erkennen</button><button class="btn ghost" onClick=${() => take('camera')}>Neues Foto</button></div></div>`
        : html`<div class="row gap12"><${Spinner} /><div><div class="h3">Erkenne Gericht und Mengen …</div><div class="small muted">Zutaten werden im Bundeslebensmittelschlüssel nachgeschlagen</div></div></div>`}</div>`
    : html`<div class="pad"><${Empty} icon="photo_camera" title="Foto aufnehmen" text="Fotografiere deinen Teller von schräg oben — mit Besteck im Bild werden die Mengen genauer.">
        <div class="row mt12"><button class="btn solid" onClick=${() => take('camera')}><${Icon} n="photo_camera" />Kamera</button><button class="btn ghost" onClick=${() => take('gallery')}><${Icon} n="image" />Galerie</button></div><//></div>`}
  </div>`;
}

// ── Mahlzeit bearbeiten (Foto, Text, Rezept, Eintrag) ───────────────────────
const scale = (per100, g) => Object.fromEntries(Object.entries(per100 ?? {}).map(([k, v]) => [k, (v * g) / 100]));
const sum = (items) => items.reduce((a, i) => { for (const [k, v] of Object.entries(i.n ?? {})) a[k] = (a[k] ?? 0) + v; return a; }, {});

export function MealEditor({ initial, slot: slot0, photoSrc, onRetake, entryId, day }) {
  const [title, setTitle] = useState(initial.title ?? 'Mahlzeit');
  const [slot, setSlot] = useState(slot0 && SLOTS.includes(slot0) ? slot0 : slotNow());
  const [items, setItems] = useState(initial.items.map((i) => ({ ...i, n: i.per100 && Object.keys(i.per100).length ? scale(i.per100, i.grams) : i.n })));
  const [busy, setBusy] = useState(false);
  const [search, setSearch] = useState(false);
  const totals = sum(items);
  const setGrams = (i, g) => setItems(items.map((it, k) => (k === i ? { ...it, grams: g, n: it.per100 && Object.keys(it.per100).length ? scale(it.per100, g) : Object.fromEntries(Object.entries(it.n ?? {}).map(([kk, v]) => [kk, it.grams ? (v * g) / it.grams : v])) } : it)));
  const save = async () => {
    setBusy(true);
    const dd = day ?? todayStr();
    try {
      const d = await api.post('/fitness/food/log', { ...(entryId ? { id: entryId } : {}), day: dd, meal: slot, title, source: initial.source ?? 'frei', photo: initial.photo, recipeId: initial.recipeId, items });
      setData(`/fitness/food/day?day=${dd}`, d);
      if (dd === todayStr()) setData('/fitness/food/day', d);
      toast('Eingetragen');
      nav('ernaehrung', { replace: true });
    } catch { toast('Server nicht erreichbar'); setBusy(false); }
  };
  return html`<div class="screen c-food">
    ${photoSrc ? html`<div class="photoBox" style="height:230px"><img src=${photoSrc} alt="" /><button class="ib" style="position:absolute;top:calc(8px + var(--sat));left:8px;background:rgba(17,17,17,.6);color:#fff" onClick=${() => back('ernaehrung')}><${Icon} n="arrow_back" /></button></div>`
      : html`<${Top} title=${entryId ? 'Eintrag' : 'Mahlzeit'} onBack=${() => back('ernaehrung')} />`}
    <div class="scroll"><div class="pad">
      <input class="input mt16" style="font-size:19px;font-weight:600;height:52px" value=${title} onInput=${(e) => setTitle(e.target.value)} />
      ${initial.note && html`<p class="small muted mt8">${initial.note}</p>`}
      <div class="chips mt12">${SLOTS.map((s) => html`<button class=${cls('chip', slot === s && 'on')} onClick=${() => setSlot(s)}>${SLOT_NAMES[s]}</button>`)}</div>
      <div class="macroBand mt16">
        <div><b class="num">${fmtNum(totals.kcal)}</b><span>kcal</span></div>
        <div><b class="num">${fmtNum(totals.protein)}</b><span>Eiweiß g</span></div>
        <div><b class="num">${fmtNum(totals.carbs)}</b><span>KH g</span></div>
        <div><b class="num">${fmtNum(totals.fat)}</b><span>Fett g</span></div>
      </div>
      <div class="sec"><h2 class="h3">Zutaten</h2><button class="more" onClick=${() => setSearch(true)}><${Icon} n="add" style="font-size:18px" />Zutat</button></div>
      <div class="card" style="padding:4px 14px">${items.map((it, i) => html`<div class="item">
        <div class="tx"><div style="font-weight:500">${it.name}</div><div class="src">${it.food ? (it.food.source === 'ki' ? 'KI-Schätzung' : it.food.name) : 'ohne Nährwerte'} · ${fmtNum(it.n?.kcal)} kcal${it.confidence != null && it.confidence < 0.6 ? ' · unsicher' : ''}</div></div>
        <input class="gramInput" type="number" inputmode="numeric" value=${it.grams} onInput=${(e) => setGrams(i, Math.max(0, Number(e.target.value) || 0))} /><span class="small muted">g</span>
        <button class="ib sm" onClick=${() => setItems(items.filter((_, k) => k !== i))} aria-label="Entfernen"><${Icon} n="close" /></button>
      </div>`)}${items.length === 0 && html`<p class="small muted" style="padding:12px 0">Keine Zutaten.</p>`}</div>
      ${onRetake && html`<button class="btn ghost mt12" onClick=${onRetake}><${Icon} n="photo_camera" />Neues Foto</button>`}
      ${entryId && html`<button class="btn ghost mt12 c-bad" style="color:rgb(var(--bad))" onClick=${async () => { await api.post(`/fitness/food/log/${entryId}/delete`); refresh('/fitness/food/day'); refresh(`/fitness/food/day?day=${day}`); back('ernaehrung'); }}><${Icon} n="delete" />Eintrag löschen</button>`}
      <div style="height:100px" />
    </div></div>
    <div class="bottombar"><button class="btn solid" disabled=${busy || !items.length} onClick=${save}>${busy ? html`<${Spinner} />` : html`<${Icon} n="check" />Speichern`}</button></div>
    ${search && html`<${FoodSearch} onClose=${() => setSearch(false)} onPick=${(it) => { setItems([...items, it]); setSearch(false); }} />`}
  </div>`;
}

function FoodSearch({ onClose, onPick }) {
  const [q, setQ] = useState('');
  const [res, setRes] = useState(null);
  const [sel, setSel] = useState(null);
  const [g, setG] = useState(100);
  useEffect(() => {
    if (q.trim().length < 2) { setRes(null); return undefined; }
    const t = setTimeout(() => api.get(`/fitness/food/search?q=${encodeURIComponent(q)}`).then((r) => setRes(r.items)).catch(() => setRes([])), 250);
    return () => clearTimeout(t);
  }, [q]);
  return html`<${Sheet} title=${sel ? sel.name : 'Lebensmittel suchen'} onClose=${onClose}>
    <div class="c-food">
    ${sel ? html`<div class="row" style="justify-content:space-between"><span class="muted small">Menge</span><input class="gramInput" type="number" value=${g} onInput=${(e) => setG(Number(e.target.value) || 0)} /></div>
      <div class="chips wrap mt12">${[30, 50, 100, 150, 200, 250].map((x) => html`<button class=${cls('chip', g === x && 'on')} onClick=${() => setG(x)}>${x} g</button>`)}</div>
      <div class="macroBand mt16"><div><b>${fmtNum((sel.per100.kcal ?? 0) * g / 100)}</b><span>kcal</span></div><div><b>${fmtNum((sel.per100.protein ?? 0) * g / 100, 1)}</b><span>Eiweiß</span></div><div><b>${fmtNum((sel.per100.carbs ?? 0) * g / 100, 1)}</b><span>KH</span></div><div><b>${fmtNum((sel.per100.fat ?? 0) * g / 100, 1)}</b><span>Fett</span></div></div>
      <button class="btn solid mt16" onClick=${() => onPick({ name: sel.name.split(',')[0], grams: g, per100: sel.per100, n: scale(sel.per100, g), food: { id: sel.id, name: sel.name, source: 'BLS 4.0' } })}>Hinzufügen</button>`
    : html`<div class="search"><${Icon} n="search" cls="faint" /><input autofocus placeholder="z. B. Haferflocken, Skyr, Banane" value=${q} onInput=${(e) => setQ(e.target.value)} /></div>
      <div class="mt8">${(res ?? []).map((r) => html`<button class="li" style="padding:10px 0" onClick=${() => setSel(r)}><div class="tx"><div class="t1" style="font-size:14.5px">${r.name}</div><div class="t2">${fmtNum(r.per100.kcal)} kcal · ${fmtNum(r.per100.protein, 1)} g Eiweiß je 100 g</div></div></button>`)}
        ${res && res.length === 0 && html`<p class="small muted mt12">Nichts gefunden.</p>`}</div>
      <p class="tiny faint mt12">Nährwerte: Bundeslebensmittelschlüssel 4.0, Max Rubner-Institut (CC BY 4.0)</p>`}
    </div>
  <//>`;
}

export function AddMeal({ slot }) {
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const [res, setRes] = useState(null);
  const [search, setSearch] = useState(false);
  if (res) return html`<${MealEditor} initial=${{ title: res.title, items: res.items, source: res.source ?? 'frei' }} slot=${slot} />`;
  const go = async () => {
    setBusy(true);
    try { setRes(await api.post('/fitness/food/text', { text })); } catch { toast('Das hat nicht geklappt — Server oder KI nicht erreichbar'); }
    setBusy(false);
  };
  return html`<div class="screen c-food"><${Top} title=${`${SLOT_NAMES[slot] ?? 'Mahlzeit'} eintragen`} onBack=${() => back('ernaehrung')} />
    <div class="scroll"><div class="pad">
      <div class="field"><label>Was hast du gegessen?</label><textarea class="input" placeholder="z. B. 250 g Magerquark mit Heidelbeeren und 2 EL Haferflocken" value=${text} onInput=${(e) => setText(e.target.value)} /></div>
      <button class="btn solid mt12" disabled=${busy || !text.trim()} onClick=${go}>${busy ? html`<${Spinner} />Rechne …` : html`<${Icon} n="auto_awesome" />Nährwerte berechnen`}</button>
      <div class="row mt24 gap8"><div style="flex:1;height:1px;background:var(--border)" /><span class="tiny muted">ODER</span><div style="flex:1;height:1px;background:var(--border)" /></div>
      <button class="btn ghost mt16" onClick=${() => setSearch(true)}><${Icon} n="search" />Einzelnes Lebensmittel suchen</button>
    </div></div>
    ${search && html`<${FoodSearch} onClose=${() => setSearch(false)} onPick=${(it) => { setSearch(false); setRes({ title: it.name, items: [it], source: 'suche' }); }} />`}
  </div>`;
}

export function EntryDetail({ id }) {
  const [day] = useState(foodDay ?? todayStr());
  const [d] = useData(`/fitness/food/day?day=${day}`);
  const e = d?.entries?.find((x) => x.id === id);
  if (!d) return html`<div class="screen"><${Top} title="Eintrag" /></div>`;
  if (!e) return html`<div class="screen"><${Top} title="Eintrag" /><${Empty} title="Eintrag nicht gefunden" /></div>`;
  return html`<${MealEditor} initial=${e} slot=${e.meal} entryId=${e.id} day=${e.day} photoSrc=${e.photo ? `/api/fitness/photo/${e.photo}` : null} />`;
}

// ── Ernährungs-Coach ────────────────────────────────────────────────────────
export function DayReport({ day: day0 }) {
  const day = day0 ?? todayStr();
  const [r, setR] = useState(null);
  const [err, setErr] = useState(null);
  const [d] = useData(`/fitness/food/day?day=${day}`);
  const load = (force) => { setR(null); setErr(null); api.get(`/fitness/food/report?day=${day}${force ? '&force=1' : ''}`).then((x) => (x.report ? setR(x.report) : setErr('Noch nichts eingetragen.'))).catch((e) => setErr(e.status === 503 ? 'Kein Gemini-Schlüssel auf dem Server.' : 'Der Coach ist gerade nicht erreichbar.')); };
  useEffect(() => load(false), [day]);
  return html`<div class="screen c-food"><${Top} title="Ernährungs-Coach" sub=${relDay(day, todayStr())} actions=${html`<button class="ib" onClick=${() => load(true)} aria-label="Neu"><${Icon} n="refresh" /></button>`} />
    <div class="scroll"><div class="pad">
      ${err ? html`<${Empty} icon="cloud_off" title=${err} />` : !r ? html`<div class="row gap12 mt12"><${Spinner} /><div class="muted">Coach liest dein Tagebuch …</div></div><div class="skel mt16" style="height:300px" />` : html`
        <div class="review c-food" style="background:linear-gradient(180deg,rgba(var(--food),.14),rgba(var(--food),.03)),var(--surface);border-color:rgba(var(--food),.22)">
          <div class="row" style="align-items:flex-start"><div class="scoreBadge"><b>${r.score}</b><span>von 10</span></div><h3 class="grow">${r.headline}</h3></div>
          ${r.good?.length > 0 && html`<div class="grp c-good"><div class="gl">Läuft gut</div><ul>${r.good.map((t) => html`<li>${t}</li>`)}</ul></div>`}
          ${r.bad?.length > 0 && html`<div class="grp c-bad"><div class="gl">Nicht so gut</div><ul>${r.bad.map((t) => html`<li>${t}</li>`)}</ul></div>`}
          ${r.variety && html`<div class="grp c-sky"><div class="gl">Vielfalt</div><p class="mt4" style="font-size:14.5px;line-height:1.45">${r.variety}</p></div>`}
          ${r.next?.length > 0 && html`<div class="grp c-food"><div class="gl">Als Nächstes</div><ul>${r.next.map((t) => html`<li>${t}</li>`)}</ul></div>`}
        </div>
        ${r.gaps?.length > 0 && html`<div class="sec"><h2 class="h2">Lücken schließen</h2></div>${r.gaps.map((g) => html`<div class="gap"><div class="gn">${g.nutrient}</div><div class="small mt4" style="line-height:1.45">${g.why}</div>${g.foods?.length > 0 && html`<div class="gf">${g.foods.join(' · ')}</div>`}</div>`)}`}
      `}
      ${d && html`<div class="sec"><h2 class="h2">Alle Nährstoffe</h2></div><div class="card pad16"><div class="nutri">${d.status.map((s) => html`<${Nu} s=${s} />`)}</div>
        <p class="tiny faint mt12">Ziele: DGE-Referenzwerte. Nährwerte: BLS 4.0 (Max Rubner-Institut).</p></div>`}
      <div class="end" />
    </div></div>
  </div>`;
}

// ── Tagesplan ───────────────────────────────────────────────────────────────
function DayPlan({ day }) {
  const [p] = useData(`/fitness/mealplan?day=${day}`, `/fitness/mealplan?day=${day}`, [day]);
  const [alt, setAlt] = useState(null);
  if (!p) return html`<div class="skel" style="height:400px" />`;
  if (!p.slots.some((s) => s.recipe)) return html`<${Empty} icon="hourglass_top" title="Rezepte werden aufbereitet" text=${`${p.status?.have ?? 0} von ${p.status?.total || 790} Rezepten sind übersetzt und durchgerechnet. Das läuft im Hintergrund.`} />`;
  const choose = async (slot, recipeId) => {
    await api.post('/fitness/mealplan/choose', { day, slot, recipeId }).catch(() => null);
    refresh(`/fitness/mealplan?day=${day}`);
    setAlt(null);
  };
  return html`<div>
    <div class="card pad16 tinted"><div class="row"><div class="grow"><div class="eyebrow">Vorschlag für den Tag</div><div style="font-size:22px;font-weight:700;margin-top:2px" class="num">${fmtNum(p.planned.kcal)} <span class="small muted" style="font-weight:500">von ${fmtNum(p.targets.kcal)} kcal</span></div></div>
      <div style="text-align:right"><div class="eyebrow">Eiweiß</div><div style="font-size:18px;font-weight:700" class="num">${fmtNum(p.planned.protein)} g</div></div></div>
      <p class="small muted mt8">Portionen sind auf dein Ziel skaliert. Tippe auf ein Gericht für Rezept und Video.</p></div>
    <div class="mt16">${p.slots.map((s) => s.recipe && html`<div class="planSlot">
      <button class="pi" onClick=${() => nav(`rezept/${s.recipe.id}`)}><img src=${recipeImg(s.recipe.image, 'medium')} alt="" loading="lazy" /></button>
      <button class="grow" style="text-align:left;min-width:0" onClick=${() => nav(`rezept/${s.recipe.id}`)}><div class="sl">${s.name}</div><div style="font-weight:600;margin-top:3px;line-height:1.25" class="ell">${s.recipe.title}</div>
        <div class="small muted mt4">${fmtNum(s.recipe.kcal * s.recipe.factor)} kcal · ${fmtNum(s.recipe.protein * s.recipe.factor)} g Eiweiß · ${s.recipe.minutes} min</div></button>
      <button class="ib sm" onClick=${() => setAlt(s)} aria-label="Tauschen"><${Icon} n="swap_horiz" /></button>
    </div>`)}</div>
    <button class="btn ghost mt16" onClick=${() => nav('einkauf')}><${Icon} n="shopping_cart" />Einkaufsliste</button>
    ${alt && html`<${Sheet} title=${`${alt.name} tauschen`} onClose=${() => setAlt(null)}>
      ${alt.alternatives.map((r) => html`<button class="planSlot" onClick=${() => choose(alt.slot, r.id)}><div class="pi" style="width:64px;height:64px"><img src=${recipeImg(r.image, 'small')} alt="" /></div>
        <div class="grow" style="min-width:0"><div style="font-weight:600" class="ell">${r.title}</div><div class="small muted mt4">${fmtNum(r.kcal * r.factor)} kcal · ${fmtNum(r.protein * r.factor)} g Eiweiß · ${r.minutes} min</div></div></button>`)}
    <//>`}
  </div>`;
}

export function Shopping() {
  const [days, setDays] = useState(3);
  const [l, , reload] = useData(`/fitness/shopping?days=${days}`, `/fitness/shopping?days=${days}`, [days]);
  const toggle = async (it) => { await api.post('/fitness/shopping/check', { name: it.name, checked: !it.checked }).catch(() => null); reload(); };
  return html`<div class="screen c-food"><${Top} title="Einkaufsliste" sub="aus deinem Tagesplan" />
    <div class="scroll"><div class="pad">
      <div class="chips">${[1, 3, 5, 7].map((d) => html`<button class=${cls('chip', days === d && 'on')} onClick=${() => setDays(d)}>${d === 1 ? 'Heute' : `${d} Tage`}</button>`)}</div>
      ${!l ? html`<div class="skel mt16" style="height:300px" />` : l.items.length === 0 ? html`<${Empty} icon="shopping_cart" title="Noch nichts geplant" text="Öffne den Tagesplan für die kommenden Tage — dann füllt sich die Liste." />`
        : html`<div class="card mt16" style="padding:4px 14px">${l.items.map((it) => html`<button class=${cls('shop', it.checked && 'on')} onClick=${() => toggle(it)}>
          <span class="ck">${it.checked && html`<${Icon} n="check" style="font-size:17px" />`}</span>
          <div class="ingr" style="padding:0;flex:none"><div class="ii" style="width:38px;height:38px"><img src=${img(it.image)} alt="" style="width:32px;height:32px" loading="lazy" /></div></div>
          <div class="grow" style="min-width:0"><div class="sn" style="font-weight:500">${it.name}</div><div class="tiny muted ell">${it.recipes.join(', ')}</div></div>
          <span class="small num">${it.grams >= 1000 ? `${fmtNum(it.grams / 1000, 1)} kg` : `${it.grams} g`}</span></button>`)}</div>`}
      <div class="end" />
    </div></div></div>`;
}
