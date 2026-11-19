// Rezepte: Entdecken (Regale), Liste mit Filtern, Rezeptseite mit Portionen, Zutatenbildern, Schritten und Video.
import { html, useState } from '../../vendor/preact-htm.js';
import { api, openVideo } from '../platform.js';
import { back, nav, useData, setData, refresh, toast, fmtNum, img, recipeImg, SLOT_NAMES, slotNow, today as todayStr } from '../app.js';
import { Icon, Top, cls, Empty, Stepper, Spinner, Sheet } from '../ui.js';

const DIET = { alles: 'Mit Fleisch', vegetarisch: 'Vegetarisch', vegan: 'Vegan', pescetarisch: 'Mit Fisch' };
const TAGS = { eiweissreich: 'Eiweißreich', leicht: 'Leicht', energiereich: 'Energiereich', ballaststoffreich: 'Ballaststoffreich', lowcarb: 'Low Carb' };

function Card({ r, wide }) {
  const [fav, setFav] = useState(r.favorite);
  const toggle = async (e) => {
    e.stopPropagation();
    setFav(!fav);
    await api.post(`/fitness/recipes/${encodeURIComponent(r.id)}/state`, { favorite: !fav }).catch(() => null);
  };
  return html`<button class="rcard" style=${wide ? '' : null} onClick=${() => nav(`rezept/${r.id}`)}>
    <div class="img"><img src=${recipeImg(r.image, 'medium')} alt="" loading="lazy" />
      ${r.video && html`<span class="play"><${Icon} n="play_arrow" fill />Video</span>`}
      <span class="fav" onClick=${toggle}><${Icon} n="favorite" fill=${fav} style=${fav ? 'color:rgb(var(--food))' : ''} /></span></div>
    <div class="rt">${r.title}</div>
    <div class="rm num">${fmtNum(r.kcal)} kcal · ${fmtNum(r.protein)} g Eiweiß · ${r.minutes} min</div>
  </button>`;
}

const SHELVES = [
  ['Für dein Ziel', ''],
  ['Eiweißreich', 'tag=eiweissreich'],
  ['Frühstück', 'meal=fruehstueck'],
  ['Leicht & satt', 'tag=leicht'],
  ['Vegetarisch', 'tag=vegetarisch'],
  ['Deine Favoriten', 'fav=1'],
];

export function RecipeBrowse() {
  const [q, setQ] = useState('');
  const [all] = useData('/fitness/recipes');
  if (all && all.total === 0) return html`<${Empty} icon="hourglass_top" title="Rezepte werden aufbereitet" text=${`${all.status?.have ?? 0} von ${all.status?.total || 790} Rezepten fertig — sie werden übersetzt und gegen den Bundeslebensmittelschlüssel durchgerechnet.`} />`;
  return html`<div>
    <button class="search" style="width:100%" onClick=${() => nav('rezepte/suche')}><${Icon} n="search" cls="faint" /><span class="muted">Rezept oder Zutat suchen</span></button>
    ${SHELVES.map(([title, qs]) => html`<${Shelf} title=${title} qs=${qs} />`)}
    ${all?.status && all.status.have < all.status.total && html`<p class="tiny faint mt20">${all.status.have} von ${all.status.total} Rezepten aufbereitet — der Rest folgt im Hintergrund.</p>`}
    <p class="tiny faint mt8">Rezepte & Bilder: TheMealDB, LECKER.de · Nährwerte: BLS 4.0 (Max Rubner-Institut)</p>
  </div>`;
}

function Shelf({ title, qs }) {
  const [d] = useData(`/fitness/recipes?${qs}`);
  if (d && d.items.length === 0) return null;
  return html`<div><div class="sec"><h2 class="h2">${title}</h2><button class="more" onClick=${() => nav(`rezepte/${qs ? qs.replace('=', '_') : 'alle'}`)}>Alle<${Icon} n="chevron_right" style="font-size:18px" /></button></div>
    <div class="rcards">${d ? d.items.slice(0, 10).map((r) => html`<${Card} r=${r} />`) : [0, 1].map(() => html`<div class="skel" style="width:228px;height:270px;flex:none" />`)}</div></div>`;
}

export function RecipeList({ filter }) {
  const [q, setQ] = useState('');
  const [chip, setChip] = useState(filter && filter !== 'alle' && filter !== 'suche' ? filter : '');
  const qs = [chip ? chip.replace('_', '=') : '', q.trim() ? `q=${encodeURIComponent(q.trim())}` : ''].filter(Boolean).join('&');
  const [d] = useData(`/fitness/recipes?${qs}`, `/fitness/recipes?${qs}`, [qs]);
  const chips = [['', 'Alle'], ['meal_fruehstueck', 'Frühstück'], ['meal_mittag', 'Mittag'], ['meal_abend', 'Abend'], ['meal_snack', 'Snack'], ['tag_eiweissreich', 'Eiweißreich'], ['tag_leicht', 'Leicht'], ['tag_lowcarb', 'Low Carb'], ['tag_vegetarisch', 'Vegetarisch'], ['tag_vegan', 'Vegan'], ['fav_1', 'Favoriten']];
  return html`<div class="screen c-food"><${Top} title="Rezepte" sub=${d ? `${d.total} passend zu deinem Profil` : ''} />
    <div class="pad" style="flex:none">
      <div class="search"><${Icon} n="search" cls="faint" /><input autofocus=${filter === 'suche'} placeholder="Rezept oder Zutat" value=${q} onInput=${(e) => setQ(e.target.value)} /></div>
      <div class="chips mt12" style="padding-bottom:10px">${chips.map(([k, l]) => html`<button class=${cls('chip', chip === k && 'on')} onClick=${() => setChip(k)}>${l}</button>`)}</div>
    </div>
    <div class="scroll"><div class="pad">
      ${!d ? html`<div class="skel" style="height:400px" />` : d.items.length === 0 ? html`<${Empty} icon="search_off" title="Keine Rezepte" />` : html`<div class="rgrid">${d.items.map((r) => html`<${Card} r=${r} />`)}</div>`}
      <div class="end" /></div></div>
  </div>`;
}

export function RecipeDetail({ id }) {
  const [d] = useData(`/fitness/recipes/${id}`);
  const [portions, setPortions] = useState(null);
  const [eat, setEat] = useState(false);
  const [fav, setFav] = useState(null);
  if (!d) return html`<div class="screen"><${Top} title="Rezept" /><div class="pad"><div class="skel" style="height:300px" /></div></div>`;
  const r = d.recipe;
  const p = portions ?? d.suggestedFactor;
  const isFav = fav ?? d.state?.favorite ?? false;
  const n = (k) => (r.n[k] ?? 0) * p;
  const toggleFav = async () => { setFav(!isFav); await api.post(`/fitness/recipes/${encodeURIComponent(r.id)}/state`, { favorite: !isFav }).catch(() => null); refresh('/fitness/recipes?fav=1'); };
  return html`<div class="screen c-food">
    <div class="scroll">
      <div class="recHero"><img src=${recipeImg(r.image, 'full')} alt="" />
        <button class="ib back" onClick=${() => back('ernaehrung')}><${Icon} n="arrow_back" /></button>
        <button class="ib act" onClick=${toggleFav} aria-label="Favorit"><${Icon} n="favorite" fill=${isFav} style=${isFav ? 'color:rgb(var(--food))' : ''} /></button></div>
      <div class="pad" style="margin-top:-36px;position:relative;z-index:2">
        <div class="row gap6" style="flex-wrap:wrap">${r.tags.map((t) => html`<span class="tag">${TAGS[t] ?? t}</span>`)}<span class="tag plain">${DIET[r.diet]}</span></div>
        <h1 style="font-size:26px;font-weight:700;letter-spacing:-.4px;margin-top:10px;line-height:1.2">${r.title}</h1>
        ${r.summary && html`<p class="muted mt8" style="line-height:1.5">${r.summary}</p>`}
        <div class="meta"><span><${Icon} n="schedule" />${r.minutes} min</span><span><${Icon} n="signal_cellular_alt" />${['', 'Leicht', 'Mittel', 'Anspruchsvoll'][r.difficulty]}</span><span><${Icon} n="public" />${r.cuisine}</span></div>

        <div class="row mt20" style="justify-content:space-between"><div><div class="h3">Deine Portion</div><div class="tiny muted">${p === d.suggestedFactor ? 'passend zu deinem Tagesziel' : `${fmtNum(p, 2)} Portionen`}</div></div>
          <${Stepper} value=${p} step=${0.25} min=${0.25} max=${r.servings * 2} onChange=${setPortions} fmt=${(v) => `${fmtNum(v, 2)}×`} /></div>
        <div class="macroBand mt12">
          <div><b class="num">${fmtNum(n('kcal'))}</b><span>kcal</span></div><div><b class="num">${fmtNum(n('protein'))}</b><span>Eiweiß g</span></div>
          <div><b class="num">${fmtNum(n('carbs'))}</b><span>KH g</span></div><div><b class="num">${fmtNum(n('fat'))}</b><span>Fett g</span></div>
        </div>

        ${r.video && html`<div class="sec"><h2 class="h2">Video</h2></div>
          <button class="videoCard" onClick=${() => openVideo(r.video, r.title)}><img src=${`https://i.ytimg.com/vi/${r.video}/hqdefault.jpg`} alt="" /><div class="pl"><${Icon} n="play_arrow" fill />So wird's gemacht</div></button>`}

        <div class="sec"><h2 class="h2">Zutaten</h2><span class="small muted">für ${fmtNum(p, 2)} ${p === 1 ? 'Portion' : 'Portionen'}</span></div>
        <div class="card" style="padding:4px 14px">${r.ingredients.map((i) => html`<div class="ingr">
          <div class="ii"><img src=${img(i.image)} alt="" loading="lazy" onError=${(e) => { e.target.style.display = 'none'; }} /></div>
          <div class="grow" style="min-width:0"><div style="font-weight:500">${i.name}</div><div class="tiny muted">${i.amount ? `Original: ${i.amount} für ${r.servings}` : ''}</div></div>
          <div class="amt">${i.grams ? `${fmtNum((i.grams / r.servings) * p)} g` : i.amount}</div></div>`)}</div>

        <div class="sec"><h2 class="h2">Zubereitung</h2></div>
        <ol class="steps">${r.steps.map((s) => html`<li>${s}</li>`)}</ol>

        <div class="sec"><h2 class="h2">Nährwerte je Portion</h2></div>
        <div class="card pad16"><div class="nutri">${[['fiber', 'Ballaststoffe', 'g'], ['sugar', 'Zucker', 'g'], ['salt', 'Salz', 'g'], ['iron', 'Eisen', 'mg'], ['magnesium', 'Magnesium', 'mg'], ['calcium', 'Calcium', 'mg'], ['vitC', 'Vitamin C', 'mg'], ['vitB12', 'Vitamin B12', 'µg'], ['folate', 'Folat', 'µg'], ['zinc', 'Zink', 'mg']].map(([k, l, u]) => {
          const goal = d.targets.goals[k];
          return html`<div class="nu"><div class="l"><span>${l}</span><b class="num">${fmtNum(n(k), 1)} ${u}</b></div>${goal?.min ? html`<div class="bar"><i style=${{ width: `${Math.min(100, (n(k) / goal.min) * 100)}%` }} /></div>` : null}</div>`;
        })}</div></div>
        <p class="tiny faint mt12">${r.source === 'lecker' ? html`Quelle: LECKER.de (»${r.original}«)` : html`Quelle: TheMealDB (»${r.original}«), übersetzt`}; Nährwerte berechnet mit BLS 4.0.</p>
        <div style="height:100px" />
      </div>
    </div>
    <div class="bottombar"><button class="btn solid" onClick=${() => setEat(true)}><${Icon} n="restaurant" />Gegessen</button></div>
    ${eat && html`<${EatSheet} r=${r} factor=${p} onClose=${() => setEat(false)} />`}
  </div>`;
}

function EatSheet({ r, factor, onClose }) {
  const [slot, setSlot] = useState(r.meals.find((m) => m !== 'dessert' && m === slotNow()) ?? slotNow());
  const [busy, setBusy] = useState(false);
  const save = async () => {
    setBusy(true);
    const per = factor / r.servings;
    const items = r.ingredients.filter((i) => i.grams > 0).map((i) => ({ name: i.name, grams: Math.round(i.grams * per), food: i.food ? { id: '', name: i.food, source: 'BLS 4.0' } : null }));
    // Nährwerte je Zutat kennt nur der Server-Datensatz → Gesamtwerte des Rezepts anteilig übernehmen.
    const n = Object.fromEntries(Object.entries(r.n).map(([k, v]) => [k, v * factor]));
    try {
      const d = await api.post('/fitness/food/log', { day: todayStr(), meal: slot, title: r.title, source: 'rezept', recipeId: r.id, items: [{ name: `${r.title} (${String(Math.round(factor * 100) / 100).replace('.', ',')} Portion)`, grams: items.reduce((a, i) => a + i.grams, 0), n }] });
      setData('/fitness/food/day', d);
      refresh(`/fitness/food/day?day=${todayStr()}`);
      toast('Im Tagebuch eingetragen');
      onClose();
    } catch { toast('Server nicht erreichbar'); setBusy(false); }
  };
  return html`<${Sheet} title="Als gegessen eintragen" onClose=${onClose}><div class="c-food">
    <div class="chips wrap">${['fruehstueck', 'mittag', 'abend', 'snack'].map((s) => html`<button class=${cls('chip', slot === s && 'on')} onClick=${() => setSlot(s)}>${SLOT_NAMES[s]}</button>`)}</div>
    <p class="small muted mt12">${fmtNum(factor, 2)} Portion · ${fmtNum((r.n.kcal ?? 0) * factor)} kcal · ${fmtNum((r.n.protein ?? 0) * factor)} g Eiweiß</p>
    <button class="btn solid mt16" disabled=${busy} onClick=${save}>${busy ? html`<${Spinner} />` : 'Eintragen'}</button>
  </div><//>`;
}
