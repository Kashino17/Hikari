// Übung im Detail: Videoschleife, Muskeln, Ausführung, Fehler, leichter/schwerer, eigene Bestwerte.
import { html, useState } from '../../vendor/preact-htm.js';
import { back, nav, useCatalog, useData, exById } from '../app.js';
import { Icon, Loop, Top } from '../ui.js';
import { ExerciseText } from './session.js';

const PATTERN = { push: 'Drücken', pull: 'Ziehen', squat: 'Kniebeuge', hinge: 'Hüftstreckung', lunge: 'Ausfallschritt', core: 'Rumpf', cardio: 'Ausdauer', mobility: 'Mobilisation', stretch: 'Dehnen', skill: 'Skill' };
export { PATTERN };

export function ExerciseDetail({ id }) {
  const catalog = useCatalog();
  const [hist] = useData('/fitness/history?days=365');
  const ex = exById(catalog, id);
  if (!catalog) return html`<div class="screen"><${Top} title="Übung" /></div>`;
  if (!ex) return html`<div class="screen"><${Top} title="Übung" /><div class="empty"><b>Unbekannte Übung</b></div></div>`;
  const rg = exById(catalog, ex.regress);
  const pg = exById(catalog, ex.progress);
  const best = hist?.bests?.find((b) => b.exercise_id === ex.id);
  const eq = (ex.equipment ?? []).map((q) => catalog.equipment[q] ?? q);
  return html`<div class="screen c-train">
    <div class="scroll">
      <div class="exHero"><${Loop} media=${ex.media} eager credit /><button class="ib back" onClick=${() => back()} aria-label="Zurück"><${Icon} n="arrow_back" /></button></div>
      <div class="pad">
        <div class="row mt20" style="align-items:flex-start">
          <div class="grow"><h1 style="font-size:25px;font-weight:700;letter-spacing:-.3px">${ex.name}</h1>
            <div class="small muted mt4">${PATTERN[ex.pattern]} · Schwierigkeit ${ex.level}/10${ex.perSide ? ' · je Seite' : ''}${ex.jump ? ' · mit Sprüngen' : ''}</div></div>
          ${best && html`<div class="tag c-amber" style="height:30px;padding:0 10px"><${Icon} n="military_tech" fill style="font-size:17px" />${best.best}${best.unit === 'sec' ? ' s' : ''}</div>`}
        </div>
        ${eq.length > 0 && html`<div class="row gap6 mt12 small muted"><${Icon} n="fitness_center" style="font-size:17px" />${eq.join(', ')}</div>`}
        <div class="mt20"><${ExerciseText} ex=${ex} /></div>
        ${(rg || pg) && html`<div class="eyebrow mt24">Stufen</div><div class="ladder mt8">
          ${rg && html`<button onClick=${() => nav(`uebung/${rg.id}`, { replace: true })}><div class="tiny muted row gap6"><${Icon} n="south" style="font-size:15px" />Leichter</div><div class="mt4" style="font-weight:500">${rg.name}</div></button>`}
          ${pg && html`<button onClick=${() => nav(`uebung/${pg.id}`, { replace: true })}><div class="tiny muted row gap6"><${Icon} n="north" style="font-size:15px" />Schwerer</div><div class="mt4" style="font-weight:500">${pg.name}</div></button>`}
        </div>`}
        ${best && html`<div class="card pad16 mt20"><div class="eyebrow">Dein Stand</div><div class="row mt8"><div class="grow small">${best.times}× trainiert · Dosis ${Math.round(best.dose * 100)} %</div><div class="small">Bestwert <b>${best.best}${best.unit === 'sec' ? ' s' : ''}</b></div></div></div>`}
        ${ex.media?.credit && html`<p class="tiny faint mt20">Video: ${ex.media.credit}</p>`}
        <div class="end" />
      </div>
    </div>
  </div>`;
}
