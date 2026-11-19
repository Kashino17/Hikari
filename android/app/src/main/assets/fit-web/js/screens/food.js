// Ernährung: Tagebuch mit Foto-Tracker, Nährstoff-Check, Ernährungs-Coach, Tagesplan, Einkaufsliste.
import { html, useEffect, useState } from '../../vendor/preact-htm.js';
import { api, pickPhoto, scanBarcode } from '../platform.js';
import { back, nav, useData, setData, refresh, today as todayStr, relDay, toast, fmtNum, SLOT_NAMES, SLOT_ICONS, slotNow, img, recipeImg, parseDay } from '../app.js';
import { Icon, Ring, Bar, Sheet, Top, Seg, cls, Spinner, Empty } from '../ui.js';
import { RecipeBrowse, IngImg } from './recipes.js';
import { FoodStats } from './stats.js';

const SLOTS = ['fruehstueck', 'mittag', 'abend', 'snack'];
const shift = (d, n) => { const x = parseDay(d); x.setDate(x.getDate() + n); return `${x.getFullYear()}-${String(x.getMonth() + 1).padStart(2, '0')}-${String(x.getDate()).padStart(2, '0')}`; };
let foodTab = 'tagebuch';
let foodDay = null;
/** Von außen: Tagebuch an einem bestimmten Tag öffnen. */
export function openFoodDay(day) { foodTab = 'tagebuch'; foodDay = day; nav('ernaehrung', { replace: true }); }

export function Food() {
  const [tab, setTab] = useState(foodTab);
  const [day, setDay] = useState(foodDay ?? todayStr());
  useEffect(() => { foodTab = tab; foodDay = day; }, [tab, day]);
  const isToday = day === todayStr();
  return html`<div class="screen tab c-food">
    <div class="scroll">
      <header class="top big"><div class="tt"><h1>Ernährung</h1><div class="t2">${tab === 'rezepte' ? 'Rezepte passend zu deinem Ziel' : tab === 'verlauf' ? 'Nährstoffe über die Zeit' : relDay(day, todayStr())}</div></div>
        ${tab !== 'rezepte' && tab !== 'verlauf' && html`<button class="ib" onClick=${() => setDay(shift(day, -1))} aria-label="Vortag"><${Icon} n="chevron_left" /></button>
        <button class="ib" disabled=${isToday} onClick=${() => setDay(shift(day, 1))} aria-label="Folgetag"><${Icon} n="chevron_right" /></button>`}</header>
      <div class="pad"><${Seg} items=${[['tagebuch', 'Tagebuch'], ['plan', 'Plan'], ['verlauf', 'Verlauf'], ['rezepte', 'Rezepte']]} value=${tab} onChange=${setTab} /></div>
      <div class="pad mt16">
        ${tab === 'tagebuch' && html`<${Diary} day=${day} />`}
        ${tab === 'plan' && html`<${DayPlan} day=${day} />`}
        ${tab === 'rezepte' && html`<${RecipeBrowse} />`}
        ${tab === 'verlauf' && html`<${FoodStats} />`}
      </div>
      <div style="height:90px" />
    </div>

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
    ${day === todayStr() && html`<div class="actionBar mt12">
      <button class="act main" onClick=${() => nav(`foto/${slotNow()}`)}><${Icon} n="photo_camera" /><span>Foto</span></button>
      <button class="act" onClick=${() => nav(`barcode/${slotNow()}`)}><${Icon} n="barcode_scanner" /><span>Barcode</span></button>
      <button class="act" onClick=${() => nav(`mahlzeit/neu/${slotNow()}`)}><${Icon} n="edit_note" /><span>Eintragen</span></button>
    </div>`}

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
      <button class="li" onClick=${() => go(`barcode/${slot}`)}><div class="iconTile"><${Icon} n="barcode_scanner" /></div><div class="tx"><div class="t1">Barcode scannen</div><div class="t2">Verpackte Produkte weltweit — Cola, Riegel, Joghurt …</div></div></button>
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
  // Erst die Auswahl (Kamera oder Galerie) — die Kamera öffnet nie von selbst. Abbrechen führt hierher zurück.
  const take = async (source) => {
    setErr(null);
    const p = await pickPhoto(source);
    if (!p) return;
    setPhoto(p); setRes(null);
    analyze(p, hint);
  };
  const analyze = async (p, h) => {
    setRes(null); setErr(null);
    try {
      const r = await api.post('/fitness/food/photo', { base64: p.base64, mime: p.mime, ...(h ? { hint: h } : {}) });
      if (!r.food) setErr('Auf dem Foto ist kein Essen zu erkennen.');
      else setRes(r);
    } catch (e) { setErr(e.status === 503 ? 'Kein Gemini-Schlüssel auf dem Server.' : 'Die Erkennung hat nicht geklappt — nochmal versuchen?'); }
  };
  const reset = () => { setPhoto(null); setRes(null); setErr(null); };
  if (res) return html`<${MealEditor} initial=${{ title: res.dish, items: res.items, photo: res.photo, source: 'foto', note: res.note }} slot=${slot} photoSrc=${`data:image/jpeg;base64,${photo.base64}`} onRetake=${reset} />`;
  if (photo) {
    return html`<div class="screen c-food"><${Top} title="Mahlzeit erkennen" onBack=${reset} />
      <div class="photoBox"><img src=${`data:${photo.mime};base64,${photo.base64}`} alt="" />${!err && html`<div class="scan" />`}</div>
      <div class="pad mt20">${err ? html`<div class="card pad16"><div class="h3">${err}</div>
          <div class="field mt12"><label>Hinweis für die KI (optional)</label><input class="input" placeholder="z. B. »Linsen-Dal mit Reis«" value=${hint} onInput=${(e) => setHint(e.target.value)} /></div>
          <div class="row mt12"><button class="btn" onClick=${() => analyze(photo, hint)}>Nochmal erkennen</button><button class="btn ghost" onClick=${reset}>Anderes Foto</button></div></div>`
        : html`<div class="row gap12"><${Spinner} /><div><div class="h3">Erkenne Gericht und Mengen …</div><div class="small muted">Zutaten werden im Bundeslebensmittelschlüssel nachgeschlagen</div></div></div>`}</div>
    </div>`;
  }
  return html`<div class="screen c-food"><${Top} title="Mahlzeit erkennen" onBack=${() => back('ernaehrung')} />
    <div class="scroll"><div class="pad">
      <div class="pickGrid">
        <button class="pick main" onClick=${() => take('camera')}><${Icon} n="photo_camera" /><b>Foto machen</b><span>Teller jetzt fotografieren</span></button>
        <button class="pick" onClick=${() => take('gallery')}><${Icon} n="photo_library" /><b>Aus der Galerie</b><span>Ein Foto, das du schon gemacht hast</span></button>
      </div>
      <div class="field mt20"><label>Hinweis für die KI (optional)</label><input class="input" placeholder="z. B. »Protein-Pancakes mit Skyr«" value=${hint} onInput=${(e) => setHint(e.target.value)} /></div>
      <div class="card pad16 mt20">
        <div class="eyebrow">So wird es genau</div>
        <ul class="errs tips mt8">
          <li><${Icon} n="check_circle" />Schräg von oben, der ganze Teller im Bild</li>
          <li><${Icon} n="check_circle" />Besteck oder Hand daneben — daran erkennt die KI die Mengen</li>
          <li><${Icon} n="check_circle" />Soßen, Öl und Getränke mit aufs Bild</li>
        </ul>
      </div>
      <button class="card pad16 mt12 row" style="width:100%;text-align:left" onClick=${() => nav(`barcode/${slot ?? slotNow()}`, { replace: true })}>
        <div class="iconTile soft"><${Icon} n="barcode_scanner" /></div><div class="grow"><div class="h3">Verpackt?</div><div class="small muted">Barcode scannen ist genauer als ein Foto</div></div><${Icon} n="chevron_right" cls="faint" /></button>
      <div class="end" />
    </div></div>
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
          <div class="ingr" style="padding:0;flex:none"><${IngImg} src=${it.image} size=${38} /></div>
          <div class="grow" style="min-width:0"><div class="sn" style="font-weight:500">${it.name}</div><div class="tiny muted ell">${it.recipes.join(', ')}</div></div>
          <span class="small num">${it.grams >= 1000 ? `${fmtNum(it.grams / 1000, 1)} kg` : `${it.grams} g`}</span></button>`)}</div>`}
      <div class="end" />
    </div></div></div>`;
}

// ── Barcode ─────────────────────────────────────────────────────────────────
export function BarcodeFlow({ slot }) {
  const [state, setState] = useState('scan'); // scan | load | product | missing | manual
  const [code, setCode] = useState('');
  const [p, setP] = useState(null);
  const [err, setErr] = useState('');
  const lookup = async (ean) => {
    setState('load'); setCode(ean);
    try { const r = await api.get(`/fitness/food/barcode/${ean}`); setP(r.product); setState('product'); }
    catch (e) { setState(e.status === 404 ? 'missing' : 'manual'); setErr(e.status === 404 ? '' : 'Die Produktdatenbank ist gerade nicht erreichbar.'); }
  };
  const scan = async () => {
    setErr(''); setState('scan');
    const r = await scanBarcode();
    if (r?.code) lookup(r.code);
    else if (r?.error) { setState('manual'); setErr(r.error === 'browser' ? '' : 'Der Scanner ließ sich nicht starten — beim ersten Mal lädt Android ihn kurz nach. Gleich nochmal versuchen oder die Nummer eintippen.'); }
    else back('ernaehrung');
  };
  useEffect(() => { scan(); }, []);
  if (state === 'product' && p) return html`<${ProductAdd} p=${p} slot=${slot} onRescan=${scan} />`;
  return html`<div class="screen c-food"><${Top} title="Barcode" onBack=${() => back('ernaehrung')} />
    <div class="scroll"><div class="pad">
      ${state === 'load' || state === 'scan' ? html`<div class="row gap12 mt24"><${Spinner} /><div class="muted">${state === 'load' ? `Suche Produkt ${code} …` : 'Scanner öffnet …'}</div></div>` : null}
      ${state === 'missing' && html`<${LabelCapture} ean=${code} onDone=${(prod) => { setP(prod); setState('product'); }} onRescan=${scan} slot=${slot} />`}
      ${(state === 'manual' || state === 'missing') && html`<div class="mt20">
        ${err && html`<p class="small muted" style="line-height:1.5">${err}</p>`}
        <div class="field mt12"><label>Barcode-Nummer eintippen</label>
          <div class="row"><input class="input" inputmode="numeric" placeholder="z. B. 5449000000996" value=${code} onInput=${(e) => setCode(e.target.value.replace(/\D/g, ''))} /><button class="btn solid sm" style="height:48px" disabled=${code.length < 8} onClick=${() => lookup(code)}>Suchen</button></div></div>
        ${state === 'manual' && html`<button class="btn ghost mt12" onClick=${scan}><${Icon} n="barcode_scanner" />Scanner öffnen</button>`}</div>`}
      <p class="tiny faint mt24">Produktdaten: Open Food Facts (weltweit, frei)</p>
    </div></div></div>`;
}

function ProductAdd({ p: p0, slot: slot0, onRescan }) {
  const [p, setP] = useState(p0);
  const unit = p.liquid ? 'ml' : 'g';
  const opts = [p.serving && [p.serving, `1 Portion · ${fmtNum(p.serving)} ${unit}`], p.pack && p.pack !== p.serving && [p.pack, `Ganze Packung · ${fmtNum(p.pack)} ${unit}`], [100, `100 ${unit}`]].filter(Boolean);
  const [g, setG] = useState(opts[0][0]);
  const [slot, setSlot] = useState(slot0 && SLOTS.includes(slot0) ? slot0 : slotNow());
  const [busy, setBusy] = useState(false);
  const n = (k) => ((p.per100[k] ?? 0) * g) / 100;
  const save = async () => {
    setBusy(true);
    try {
      const d = await api.post('/fitness/food/log', { day: todayStr(), meal: slot, title: p.brand ? `${p.name} (${p.brand})` : p.name, source: 'barcode', items: [{ name: p.name, grams: g, per100: p.per100, food: { id: `off:${p.ean}`, name: p.name, source: 'Open Food Facts' } }] });
      setData('/fitness/food/day', d); setData(`/fitness/food/day?day=${todayStr()}`, d);
      toast('Eingetragen'); nav('ernaehrung', { replace: true });
    } catch { toast('Server nicht erreichbar'); setBusy(false); }
  };
  const missing = p.per100.kcal == null;
  return html`<div class="screen c-food"><${Top} title="Produkt" onBack=${() => back('ernaehrung')} actions=${html`<button class="ib" onClick=${onRescan} aria-label="Nochmal scannen"><${Icon} n="barcode_scanner" /></button>`} />
    <div class="scroll"><div class="pad">
      <div class="row gap12" style="align-items:flex-start">
        <div class="prodImg">${p.image ? html`<img src=${img(p.image)} alt="" />` : html`<${Icon} n="inventory_2" />`}</div>
        <div class="grow"><div class="h2" style="line-height:1.25">${p.name}</div><div class="small muted mt4">${[p.brand, p.quantity].filter(Boolean).join(' · ')}</div>
          ${p.nutriscore && /^[a-e]$/.test(p.nutriscore) && html`<span class=${`nutri ns-${p.nutriscore}`}>Nutri-Score ${p.nutriscore.toUpperCase()}</span>`}</div>
      </div>
      ${p.source === 'eigen' && html`<div class="tag mt12" style="display:inline-flex"><${Icon} n="verified" style="font-size:15px" />Von dir erfasst</div>`}
      ${missing ? html`<div class="mt16"><${LabelCapture} ean=${p.ean} name=${p.name} onDone=${(prod) => setP(prod)} compact /></div>` : html`
      <div class="eyebrow mt24">Wie viel?</div>
      <div class="bigOpts mt8">${opts.map(([v, l]) => html`<button class=${cls('bigOpt', g === v && 'on')} style="min-height:54px" onClick=${() => setG(v)}><span class="grow"><b style="font-size:15px">${l}</b></span>${g === v && html`<${Icon} n="check" style="color:rgb(var(--food))" />`}</button>`)}</div>
      <div class="row mt12" style="justify-content:space-between"><span class="small muted">Eigene Menge</span><div class="row gap6"><input class="gramInput" type="number" inputmode="numeric" value=${g} onInput=${(e) => setG(Math.max(0, Number(e.target.value) || 0))} /><span class="small muted">${unit}</span></div></div>
      <div class="macroBand mt16"><div><b class="num">${fmtNum(n('kcal'))}</b><span>kcal</span></div><div><b class="num">${fmtNum(n('protein'), 1)}</b><span>Eiweiß g</span></div><div><b class="num">${fmtNum(n('carbs'), 1)}</b><span>KH g</span></div><div><b class="num">${fmtNum(n('fat'), 1)}</b><span>Fett g</span></div></div>
      ${n('sugar') > 0 && html`<p class="small muted mt8">davon Zucker ${fmtNum(n('sugar'), 1)} g${n('salt') ? ` · Salz ${fmtNum(n('salt'), 2)} g` : ''}</p>`}
      <div class="chips mt20">${SLOTS.map((s) => html`<button class=${cls('chip', slot === s && 'on')} onClick=${() => setSlot(s)}>${SLOT_NAMES[s]}</button>`)}</div>`}
      <div style="height:100px" />
    </div></div>
    ${!missing && html`<div class="bottombar"><button class="btn solid" disabled=${busy || !g} onClick=${save}>${busy ? html`<${Spinner} />` : html`<${Icon} n="check" />Eintragen`}</button></div>`}
  </div>`;
}

/**
 * Produkt unbekannt (oder ohne Nährwerte): Nährwerttabelle fotografieren — die KI liest sie ab
 * (jede Sprache), und das Produkt gilt ab dann für diese Nummer.
 */
function LabelCapture({ ean, name: name0, onDone, onRescan, slot, compact }) {
  const [name, setName] = useState(name0 ?? '');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const take = async (source) => {
    const ph = await pickPhoto(source);
    if (!ph) return;
    setBusy(true); setErr('');
    try {
      const r = await api.post('/fitness/food/label', { ean, base64: ph.base64, mime: ph.mime, ...(name.trim() ? { name: name.trim() } : {}) });
      onDone(r.product);
    } catch (e) {
      setErr(e.status === 422 ? 'Die Tabelle war nicht lesbar — näher ran, gerade von vorn, ohne Spiegelung.' : 'Das hat nicht geklappt — nochmal versuchen?');
    }
    setBusy(false);
  };
  return html`<div class="card pad16 mt12">
    ${!compact && html`<div class="h3">Produkt ${ean} ist noch in keiner Datenbank</div>
      <p class="small muted mt8" style="line-height:1.5">Gesucht in Open Food Facts und UPCitemdb. Fotografier die <b style="color:var(--text)">Nährwerttabelle</b> auf der Packung — die KI liest sie ab (jede Sprache), und beim nächsten Scan ist das Produkt sofort da.</p>`}
    ${compact && html`<div class="h3">Nährwerte fehlen</div><p class="small muted mt8">Fotografier die Nährwerttabelle — dann ist das Produkt vollständig, auch für die nächsten Scans.</p>`}
    <div class="field mt12"><label>Produktname (optional)</label><input class="input" placeholder="z. B. Milo Kotak 200 ml" value=${name} onInput=${(e) => setName(e.target.value)} /></div>
    ${busy ? html`<div class="row gap12 mt16"><${Spinner} /><div class="muted">Lese die Nährwerttabelle …</div></div>`
      : html`<div class="row mt12"><button class="btn solid" onClick=${() => take('camera')}><${Icon} n="photo_camera" />Tabelle fotografieren</button><button class="btn ghost" style="width:auto;padding:0 14px" onClick=${() => take('gallery')} aria-label="Aus der Galerie"><${Icon} n="photo_library" /></button></div>`}
    ${err && html`<p class="small mt8" style="color:rgb(var(--bad))">${err}</p>`}
    ${!compact && html`<div class="row mt12"><button class="btn ghost sm" onClick=${() => nav(`foto/${slot ?? slotNow()}`, { replace: true })}><${Icon} n="restaurant" />Ganzes Essen fotografieren</button><button class="btn ghost sm" onClick=${onRescan}>Nochmal scannen</button></div>`}
  </div>`;
}
