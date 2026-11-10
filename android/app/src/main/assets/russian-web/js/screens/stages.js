// Etappen einer Lektion als kleine Liste: was geschafft ist, was läuft, was noch kommt — mit Minuten.
import { html, cls, Icon } from '../ui.js';
import { Pl, planState } from '../app.js';

/**
 * stages: [{title, hint, icon}], seconds: geschätzte Sekunden je Etappe,
 * done: Anzahl geschaffter Etappen, current: Index der laufenden (oder -1).
 */
export function StageList({ stages, seconds, done = 0, current = -1 }) {
  const f = planState.pace();
  return html`<div class="stages">${stages.map((s, i) => html`<div class=${cls('stg', i < done && 'done', i === current && 'now')}>
    <span class="si"><${Icon} n=${i < done ? 'check' : s.icon} /></span>
    <span class="grow"><b>${s.title}</b><span>${s.hint}</span></span>
    <span class="sm num">${i < done ? 'geschafft' : `${Pl.toMinutes(seconds[i] ?? 0, f)} Min.`}</span></div>`)}</div>`;
}

/** Gesamtdauer als »ca. 24 Min.« */
export const totalLabel = (seconds) => Pl.minutesLabel(seconds.reduce((a, b) => a + b, 0), planState.pace());
