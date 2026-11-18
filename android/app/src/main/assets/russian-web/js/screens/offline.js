// Russisch für unterwegs: klar sagen, was ohne Netz (oder ohne Mac) geht und was nicht — statt einer
// Sperre. Dazu Anja ohne Netz (deine letzten Korrekturen zum Üben) und die Einstellungen zum Laden.
import { html, cls, Icon, useState, useEffect } from '../ui.js';
import { useLink, progress, nav } from '../app.js';
import { speech, offlineStore, anjaKeys, isNative } from '../platform.js';
import { PhraseCard, gradeSpoken } from './phrasebook.js';
import { asPhrase } from '../domain/content.js';

const ANJA_NOTE = 'Aus dem Gespräch mit Anja';

/** Was geht und was nicht — je nachdem, ob nur der Mac fehlt oder gar kein Netz da ist. */
function lists(link) {
  const pack = speech.pack();
  const st = offlineStore.state();
  const direct = link === 'noserver' && !!anjaKeys.info()?.ready;
  const works = [
    direct ? ['forum', 'Mit Anja sprechen', 'direkt über Google, mit dem Schlüssel auf deinem Handy'] : null,
    ['school', 'Jahreskurs und Wiederholung', 'mit Kursstimme'],
    pack === 'installed' ? ['mic', 'KI-Prüfung beim Sprechen', 'russisches Sprachpaket ist da'] : null,
    ['movie', 'Lieder & Szenen', st ? `${st.pieces} ${st.pieces === 1 ? 'Stück' : 'Stücke'} auf dem Handy` : 'geladene Stücke'],
    ['account_tree', 'Grammatik und alle Wortformen', 'liegt auf dem Gerät'],
    ['menu_book', 'Satzbuch, Muster, Spiele', ''],
  ].filter(Boolean);
  const needs = [
    direct ? null : ['forum', 'Mit Anja sprechen', link === 'offline' ? 'Sie braucht Internet. Deine letzten Korrekturen kannst du trotzdem üben.' : 'Ohne Mac nur mit Schlüssel auf dem Handy — er kommt von selbst, sobald dein Mac einmal erreichbar ist.'],
    pack !== 'installed' ? ['mic_off', 'KI-Prüfung beim Sprechen', pack === 'unsupported' ? 'Auf diesem Gerät nur mit Internet.' : 'Ohne russisches Sprachpaket nur mit Internet.'] : null,
    ['add_circle', 'Neue Stücke für Lieder & Szenen', 'Kommen nachts im WLAN beim Laden.'],
  ].filter(Boolean);
  return { works, needs, pack, direct };
}

/** Der Streifen oben auf »Heute«, solange Netz oder Mac fehlen. */
export function OfflineStrip() {
  const link = useLink();
  const [open, setOpen] = useState(false);
  if (link === 'online') return null;
  const { works, needs, pack, direct } = lists(link);
  return html`<div class="sec c-review" style="padding-top:4px"><div class="o-strip">
    <button class="o-head" onClick=${() => setOpen(!open)}><span class="ic sm"><${Icon} n=${link === 'offline' ? 'cloud_off' : 'dns'} /></span>
      <span class="grow"><b>${link === 'offline' ? 'Du bist offline' : 'Dein Mac ist gerade nicht erreichbar'}</b>
        <span>${link === 'offline' ? 'Fast alles geht weiter. Nur Anja braucht Internet.' : direct ? 'Alles geht weiter, Anja direkt über Google. Nur neue Stücke brauchen ihn.' : 'Fast alles geht weiter. Nur Anja und neue Stücke brauchen ihn.'}</span></span>
      <${Icon} n=${open ? 'expand_less' : 'expand_more'} c="faint" /></button>
    ${open && html`<div class="o-body"><div class="o-k good">Geht gerade</div>
      ${works.map(([i, t, d]) => html`<div class="o-item"><${Icon} n=${i} size="19" /><span class="grow"><b>${t}</b>${d && html`<span>${d}</span>`}</span><${Icon} n="check" size="18" c="good" /></div>`)}
      <div class="o-k">${link === 'offline' ? 'Braucht Internet' : 'Braucht deinen Mac oder Internet'}</div>
      ${needs.map(([i, t, d]) => html`<div class="o-item dim"><${Icon} n=${i} size="19" /><span class="grow"><b>${t}</b><span>${d}</span></span></div>`)}
      ${pack === 'missing' && link !== 'offline' && html`<button class="btn tonal sm mt8 c-review" style="width:100%" onClick=${() => speech.downloadPack()}><${Icon} n="download" />Sprachpaket laden — dann geht die Prüfung auch offline</button>`}
    </div>`}
  </div></div>`;
}

/** Deine letzten Korrekturen von Anja — ohne Netz zum Üben, nur Deutsch, mit Spicker. */
export function recentCorrections(n = 5) {
  return progress.get().mySentences.filter((m) => (m.note ?? '') === ANJA_NOTE).slice(-n).reverse()
    .map((m) => asPhrase(m.ru, m.de, m.note));
}

/** Was Anja ohne Verbindung zeigt — statt einer Sperre. */
export function AnjaOffline({ link }) {
  const list = recentCorrections();
  return html`<div class="card p o-anja">
    <div class="rowx g10"><span class="ic sm"><${Icon} n=${link === 'offline' ? 'wifi_off' : 'dns'} /></span>
      <span class="grow"><b>${link === 'offline' ? 'Anja braucht Internet' : 'Anja braucht deinen Mac'}</b>
        <span class="sub" style="display:block">${link === 'offline' ? 'Sobald du wieder online bist, geht es weiter, wo ihr aufgehört habt.' : 'Ohne Mac spricht sie direkt mit Google — dafür fehlt auf dem Handy noch der Schlüssel. Er kommt von selbst, sobald dein Mac einmal erreichbar ist, oder du trägst in den Einstellungen einen eigenen ein.'}</span></span></div>
    ${list.length > 0 ? html`<div class="over mt16">Bis dahin: deine letzten Korrekturen</div>
      <div class="stack g10 mt8">${list.map((p) => html`<${PhraseCard} p=${p} small onEvaluated=${gradeSpoken(p.id)} key=${p.id} />`)}</div>`
      : html`<p class="sub mt12">Noch keine Korrekturen gespeichert. Übe solange im Satzbuch oder in der Grammatik.</p>
        <div class="rowx g8 mt12"><button class="btn ghost sm" onClick=${() => nav('satzbuch')}>Satzbuch</button><button class="btn ghost sm" onClick=${() => nav('grammatik')}>Grammatik</button></div>`}
  </div>`;
}

const mb = (bytes) => (bytes >= 1024 ** 3 ? `${(bytes / 1024 ** 3).toFixed(1).replace('.', ',')} GB` : `${Math.round(bytes / 1024 ** 2)} MB`);

/** Einstellungen »Für unterwegs« — nur in der App (dort lädt sie nachts). */
export function OfflineSettings() {
  const [st, setSt] = useState(() => offlineStore.state());
  useEffect(() => { const t = setInterval(() => setSt(offlineStore.state()), 4000); return () => clearInterval(t); }, []);
  if (!st) return null;
  const set = (patch) => { offlineStore.set(patch); setSt({ ...st, ...patch }); };
  const gb = Math.round(st.limitMb / 1024 * 10) / 10;
  const last = st.lastRun ? new Date(st.lastRun) : null;
  const Toggle = ({ k, t, d }) => html`<button class="o-opt" onClick=${() => set({ [k]: !st[k] })} aria-pressed=${st[k]}>
    <span class="grow"><b>${t}</b><span>${d}</span></span><span class=${cls('sw', st[k] && 'on')}><i></i></span></button>`;
  return html`<div class="q">Für unterwegs</div>
    <div class="card p o-store c-review">
      <div class="rowx" style="justify-content:space-between"><b>Speicher für Russisch</b><b class="num">${String(gb).replace('.', ',')} GB</b></div>
      <input type="range" min="0.5" max="20" step="0.5" value=${gb} onChange=${(e) => set({ limitMb: Math.round(Number(e.target.value) * 1024) })} onInput=${(e) => setSt({ ...st, limitMb: Math.round(Number(e.target.value) * 1024) })} aria-label="Speichergrenze" />
      <div class="o-meter"><i style=${`width:${Math.min(100, (st.usedBytes / (st.limitMb * 1024 * 1024)) * 100)}%`}></i></div>
      <div class="sub">${mb(st.usedBytes)} belegt · ${st.pieces} ${st.pieces === 1 ? 'Stück' : 'Stücke'} · Kursstimme für ${st.voices} Sätze</div>
      <div class="o-last"><${Icon} n="nightlight" size="18" /><span class="grow">${last ? `Zuletzt geladen ${last.toLocaleDateString('de-DE', { weekday: 'short' })} ${last.toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })}: ${st.lastNew === 1 ? 'ein neues Stück' : `${st.lastNew} neue Stücke`}` : 'Noch nicht geladen — das passiert nachts von selbst.'}</span>
        <button class="chip sm" onClick=${() => { offlineStore.runNow(); }}>Jetzt laden</button></div>
      <${Toggle} k="wifiOnly" t="Nur im WLAN" d="Kein mobiles Datenvolumen" />
      <${Toggle} k="chargingOnly" t="Nur beim Laden" d="Wenn das Handy am Strom hängt" />
      <${Toggle} k="deleteSeen" t="Gesehenes nach 7 Tagen vom Handy" d="Gemerkte Stücke bleiben; auf dem Mac bleibt alles" />
    </div>`;
}

/**
 * Einstellungen »Anja ohne Mac«: unser Schlüssel (vom Mac übernommen) oder ein eigener. Beide liegen
 * verschlüsselt auf dem Handy und werden nur genutzt, wenn der Mac nicht erreichbar ist.
 */
export function AnjaKeySettings() {
  const [info, setInfo] = useState(() => anjaKeys.info());
  const [draft, setDraft] = useState('');
  const [edit, setEdit] = useState(false);
  if (!info) return null;
  const refresh = () => setInfo(anjaKeys.info());
  const pick = (own) => { anjaKeys.useOwn(own); refresh(); if (own && !info.own) setEdit(true); };
  const save = () => { const k = draft.trim(); if (k.length < 20) return; anjaKeys.save('own', k); anjaKeys.useOwn(true); setDraft(''); setEdit(false); refresh(); };
  const drop = () => { anjaKeys.save('own', ''); anjaKeys.useOwn(false); setEdit(false); refresh(); };
  const own = info.useOwn && info.own;
  return html`<div class="q">Anja ohne Mac</div>
    <div class="card p o-key c-amber">
      <p class="sub" style="margin:0 0 12px;line-height:1.5">Ist dein Mac aus, spricht Anja direkt mit Google. Der Schlüssel dafür liegt verschlüsselt nur auf diesem Handy${isNative ? '' : ' (in der Vorschau: im Browser)'} und wird nur ohne Mac benutzt.</p>
      <div class="o-seg" role="radiogroup">
        <button class=${cls(!own && 'on')} role="radio" aria-checked=${!own} onClick=${() => pick(false)}><b>Unser Schlüssel</b><span>${info.mac ? 'liegt auf dem Handy' : 'kommt vom Mac'}</span></button>
        <button class=${cls(own && 'on')} role="radio" aria-checked=${!!own} onClick=${() => pick(true)}><b>Eigener Schlüssel</b><span>${info.own ? `endet auf …${info.ownEnd}` : 'noch keiner'}</span></button>
      </div>
      ${!own && !info.mac && html`<div class="o-last mt12"><${Icon} n="sync" size="18" /><span class="grow">Noch nicht übernommen — passiert von selbst, sobald dein Mac einmal erreichbar ist.</span></div>`}
      ${(edit || (info.useOwn && !info.own)) && html`<div class="o-keyin mt12">
        <input class="field" type="password" autocomplete="off" spellcheck="false" placeholder="Gemini-Schlüssel (AIza…)" value=${draft} onInput=${(e) => setDraft(e.target.value)} />
        <button class="btn area sm" disabled=${draft.trim().length < 20} onClick=${save}>Speichern</button></div>
        <p class="faint mt8" style="font-size:12px">Kostenlos bei Google AI Studio unter »Get API key«.</p>`}
      ${info.own && !edit && html`<div class="rowx g8 mt12"><button class="btn ghost sm" onClick=${() => setEdit(true)}>Eigenen ändern</button><button class="btn ghost sm" onClick=${drop}>Eigenen löschen</button></div>`}
    </div>`;
}
