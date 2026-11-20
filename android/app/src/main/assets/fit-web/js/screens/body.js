// Körperfortschritt: Gewicht mit Trend und Prognose zum Ziel, Umfänge, Fortschrittsfotos mit
// Vorher/Nachher-Vergleich. Server: /fitness/body/* (body.ts).
import { html, useState } from '../../vendor/preact-htm.js';
import { api, pickPhoto } from '../platform.js';
import { useData, refresh, relDay, today, fmtNum, parseDay, toast, longDate } from '../app.js';
import { Icon, Top, Seg, Sheet, cls, Spark, Spinner } from '../ui.js';

const MEASURES = [['chest', 'Brust'], ['arm', 'Oberarm'], ['waist', 'Taille'], ['hips', 'Hüfte'], ['thigh', 'Oberschenkel']];
const POSES = [['vorn', 'Vorn'], ['seite', 'Seite'], ['hinten', 'Hinten']];

export function BodyPage() {
  const [d] = useData('/fitness/body/progress');
  const [tab, setTab] = useState('gewicht');
  const [sheet, setSheet] = useState(null);
  if (!d) return html`<div class="screen"><${Top} title="Körper" /><div class="pad"><div class="skel" style="height:420px" /></div></div>`;
  return html`<div class="screen c-train"><${Top} title="Körper" sub="Gewicht, Maße und Fotos über die Zeit" />
    <div class="scroll"><div class="pad">
      <${Seg} items=${[['gewicht', 'Gewicht'], ['masse', 'Maße'], ['fotos', 'Fotos']]} value=${tab} onChange=${setTab} />
      <div class="mt16">
        ${tab === 'gewicht' && html`<${WeightTab} d=${d} onAdd=${() => setSheet('gewicht')} />`}
        ${tab === 'masse' && html`<${MeasureTab} d=${d} onAdd=${() => setSheet('masse')} />`}
        ${tab === 'fotos' && html`<${PhotoTab} d=${d} />`}
      </div>
      <div class="end" />
    </div></div>
    ${sheet === 'gewicht' && html`<${WeightAdd} last=${d.weights[d.weights.length - 1]?.weight} onClose=${() => setSheet(null)} />`}
    ${sheet === 'masse' && html`<${MeasureAdd} last=${d.measures[d.measures.length - 1]} onClose=${() => setSheet(null)} />`}
  </div>`;
}

function WeightTab({ d, onAdd }) {
  const w = d.weights;
  const last = w[w.length - 1];
  const first = w[0];
  const pts = w.slice(-60).map((x) => ({ v: x.weight }));
  const dir = d.target && last ? Math.sign(d.target - last.weight) : 0;
  const onTrack = d.trend != null && dir !== 0 && Math.sign(d.trend) === dir;
  return html`<div>
    <div class="bodyHero">
      <div class="row" style="align-items:flex-end;justify-content:space-between">
        <div><span class="lc-k">Aktuell</span><div class="lc-v num" style="font-size:56px">${last ? fmtNum(last.weight, 1) : '–'}<small> kg</small></div>
          <span class="bh-s">${last ? `gewogen ${relDay(last.day, today())}` : 'Noch nichts eingetragen'}</span></div>
        ${d.trend != null && html`<div class="bh-trend ${cls(onTrack ? 'good' : dir ? 'bad' : '')}"><b class="num">${d.trend > 0 ? '+' : ''}${fmtNum(d.trend, 2)}</b><span>kg pro Woche</span></div>`}
      </div>
      ${pts.length > 1 && html`<div class="mt12"><${Spark} points=${pts} goal=${d.target} color="rgb(17,17,17)" goalStroke="rgba(0,0,0,.3)" /></div>`}
      ${d.band && html`<div class="bh-rate"><span>Gesundes Tempo für dein Ziel</span><b class="num">${d.band.map((v) => `${v > 0 ? '+' : ''}${fmtNum(v, 2)}`).join(' bis ')} kg/Woche</b></div>`}
      ${d.adjustNote && html`<div class="bh-rate"><span>Dein Coach</span><b>${d.adjustNote}</b></div>`}
      ${d.target && html`<div class="bh-goal"><${Icon} n="flag" /><span>${d.eta ? `Ziel ${fmtNum(d.target, 1)} kg voraussichtlich ${longDate(d.eta)}` : d.trend == null ? `Ziel ${fmtNum(d.target, 1)} kg — für eine Prognose mindestens dreimal in vier Wochen wiegen` : `Ziel ${fmtNum(d.target, 1)} kg — der Trend zeigt gerade in die andere Richtung`}</span></div>`}
    </div>
    <button class="btn mt16" onClick=${onAdd}><${Icon} n="monitor_weight" />Gewicht eintragen</button>
    ${first && last && first.day !== last.day && html`<div class="statRow mt16"><div><span>Start</span><b class="num">${fmtNum(first.weight, 1)} kg</b><em>${relDay(first.day, today())}</em></div>
      <div><span>Veränderung</span><b class="num">${last.weight - first.weight > 0 ? '+' : ''}${fmtNum(last.weight - first.weight, 1)} kg</b><em>seit Start</em></div></div>`}
    <p class="tiny muted mt12">Am genauesten: morgens, nach dem Aufstehen, vor dem Frühstück.</p>
  </div>`;
}

function MeasureTab({ d, onAdd }) {
  const m = d.measures;
  const last = m[m.length - 1];
  const firstOf = (k) => m.find((x) => x[k] != null)?.[k];
  return html`<div>
    ${!last ? html`<div class="card pad16 small muted">Noch keine Maße. Ein Maßband reicht — einmal im Monat messen zeigt, was die Waage nicht zeigt.</div>`
      : html`<div class="measGrid">${MEASURES.map(([k, l]) => {
        const v = [...m].reverse().find((x) => x[k] != null)?.[k];
        const f = firstOf(k);
        const diff = v != null && f != null ? v - f : null;
        return html`<div class="measTile"><span>${l}</span><b class="num">${v != null ? fmtNum(v, 1) : '–'}<small> cm</small></b>
          ${diff != null && diff !== 0 && html`<em class=${cls(k === 'waist' || k === 'hips' ? (diff < 0 ? 'good' : 'bad') : diff > 0 ? 'good' : '')}>${diff > 0 ? '+' : ''}${fmtNum(diff, 1)} cm</em>`}</div>`;
      })}</div>
      <p class="tiny muted mt8">Zuletzt gemessen ${relDay(last.day, today())} · Veränderung seit der ersten Messung</p>`}
    <button class="btn mt16" onClick=${onAdd}><${Icon} n="straighten" />Maße eintragen</button>
  </div>`;
}

function PhotoTab({ d }) {
  const [busy, setBusy] = useState(null);
  const [cmp, setCmp] = useState(50);
  const [pose, setPose] = useState('vorn');
  const list = d.photos.filter((p) => p.pose === pose);
  const newest = list[0];
  const oldest = list[list.length - 1];
  const add = async (ps) => {
    const ph = await pickPhoto('camera');
    if (!ph) return;
    setBusy(ps);
    try { await api.post('/fitness/body/photo', { base64: ph.base64, mime: ph.mime, pose: ps }); refresh('/fitness/body/progress'); toast('Foto gespeichert — nur auf deinem Server'); } catch { toast('Server nicht erreichbar'); }
    setBusy(null);
  };
  return html`<div>
    <div class="chips">${POSES.map(([k, l]) => html`<button class=${cls('chip', pose === k && 'on')} onClick=${() => setPose(k)}>${l}${d.photos.some((p) => p.pose === k) ? ` · ${d.photos.filter((p) => p.pose === k).length}` : ''}</button>`)}</div>
    ${newest && oldest && newest.id !== oldest.id ? html`<div class="cmpBox mt12">
        <img src=${`/api${oldest.url}`} alt="" />
        <div class="cmpTop" style=${{ clipPath: `inset(0 0 0 ${cmp}%)` }}><img src=${`/api${newest.url}`} alt="" /></div>
        <span class="cmpL">${longDate(oldest.day)}</span><span class="cmpR">${longDate(newest.day)}</span>
        <i class="cmpLine" style=${{ left: `${cmp}%` }} />
      </div>
      <input class="cmpSlider" type="range" min="0" max="100" value=${cmp} onInput=${(e) => setCmp(Number(e.target.value))} aria-label="Vorher/Nachher" />`
      : newest ? html`<div class="cmpBox mt12"><img src=${`/api${newest.url}`} alt="" /><span class="cmpR">${longDate(newest.day)}</span></div>
        <p class="tiny muted mt8">Mach in ein paar Wochen ein zweites ${POSES.find((x) => x[0] === pose)[1].toLowerCase()}-Foto — dann siehst du hier Vorher und Nachher.</p>`
      : html`<div class="card pad16 small muted mt12">Noch kein Foto ${pose === 'vorn' ? 'von vorn' : pose === 'seite' ? 'von der Seite' : 'von hinten'}. Gleiches Licht, gleiche Stelle, gleiche Haltung — dann ist der Vergleich ehrlich.</div>`}
    <button class="btn mt16" disabled=${!!busy} onClick=${() => add(pose)}>${busy ? html`<${Spinner} />` : html`<${Icon} n="photo_camera" />Foto ${POSES.find((x) => x[0] === pose)[1].toLowerCase()} aufnehmen`}</button>
    ${list.length > 0 && html`<div class="photoGrid mt16">${list.map((p) => html`<div><img src=${`/api${p.url}`} alt="" loading="lazy" /><span>${relDay(p.day, today())}</span></div>`)}</div>`}
    <p class="tiny muted mt12">Fotos liegen nur auf deinem Mac, nirgendwo sonst.</p>
  </div>`;
}

function WeightAdd({ last, onClose }) {
  const [v, setV] = useState(String(last ?? '').replace('.', ','));
  const save = async () => {
    const n = Number(v.replace(',', '.'));
    if (!(n > 30 && n < 300)) { toast('Bitte ein gültiges Gewicht'); return; }
    try {
      const r = await api.post('/fitness/body', { weight: n, day: today() });
      if (r?.queued) toast('Offline gespeichert — wird nachgetragen');
      refresh('/fitness/body/progress'); refresh('/fitness/state'); onClose();
    } catch { toast('Server nicht erreichbar'); }
  };
  return html`<${Sheet} title="Gewicht" onClose=${onClose}><div class="pad">
    <div class="bigInput"><input inputmode="decimal" value=${v} onInput=${(e) => setV(e.target.value)} autofocus /><span>kg</span></div>
    <button class="btn mt16" onClick=${save}>Speichern</button></div><//>`;
}

function MeasureAdd({ last, onClose }) {
  const [v, setV] = useState(() => Object.fromEntries(MEASURES.map(([k]) => [k, last?.[k] != null ? String(last[k]).replace('.', ',') : ''])));
  const save = async () => {
    const body = Object.fromEntries(MEASURES.map(([k]) => [k, v[k] ? Number(String(v[k]).replace(',', '.')) : undefined]).filter(([, x]) => x));
    if (!Object.keys(body).length) { toast('Trag mindestens ein Maß ein'); return; }
    try {
      const r = await api.post('/fitness/body/measure', { ...body, day: today() });
      refresh('/fitness/body/progress'); onClose(); toast(r?.queued ? 'Offline gespeichert — wird nachgetragen' : 'Maße gespeichert');
    } catch { toast('Server nicht erreichbar'); }
  };
  return html`<${Sheet} title="Maße in cm" onClose=${onClose}><div class="pad">
    <div class="measForm">${MEASURES.map(([k, l]) => html`<label><span>${l}</span><input inputmode="decimal" value=${v[k]} onInput=${(e) => setV({ ...v, [k]: e.target.value })} placeholder="–" /></label>`)}</div>
    <p class="tiny muted mt8">Brust und Taille auf Höhe der Brustwarzen bzw. des Bauchnabels, Arm am dicksten Punkt, locker.</p>
    <button class="btn mt16" onClick=${save}>Speichern</button></div><//>`;
}
