// Tab »Üben«: alles, was unabhängig vom Kurs jederzeit geht — nach Fähigkeit sortiert.
import { html, cls, Icon, TopBar, Sheet, useState } from '../ui.js';
import { D, P, useProgress, progress, nav, exitApp } from '../app.js';
import { GenderGate } from './me.js';

const rec = (v) => (v > 0 ? `Rekord ${v}` : 'Neu');

function groups(p) {
  const lab = Object.values(p.labBest);
  return [
    { c: 'c-speak', title: 'Sprechen', hint: 'antworten und frei reden', tools: [
      { t: 'Sprich frei', d: 'Frage hören, frei antworten', icon: 'record_voice_over', to: 'talk', time: '3 Min.' },
      { t: 'Sprech-Reflex', d: 'Deutsch lesen, sofort Russisch sagen', icon: 'bolt', to: 'reflex', badge: rec(p.bestReflex), hot: p.bestReflex > 0 },
      { t: 'Blind-Coach', d: 'Nur Audio, gut für unterwegs', icon: 'headphones', to: 'blind', time: '4 Min.' },
      { t: 'Rollenspiele', d: 'Gespräche aus den Kurstagen', icon: 'forum', to: '@dialog' },
      { t: 'Mit Anja sprechen', d: 'Freies Gespräch mit KI, auf deinem Niveau', icon: 'travel_explore', to: 'anja' },
    ] },
    { c: 'c-listen', title: 'Hören und Aussprache', hint: 'verstehen und richtig klingen', tools: [
      { t: 'Hör-Blitz', d: '60 Sekunden: hören, Bedeutung tippen', icon: 'hearing', to: 'blitz', badge: rec(p.bestBlitz), hot: p.bestBlitz > 0 },
      { t: 'Lautlabor', d: 'Ähnliche Laute unterscheiden und sprechen', icon: 'graphic_eq', to: 'sounds', badge: lab.length ? `${Math.max(...lab)} %` : 'Neu', hot: lab.length > 0 },
    ] },
    { c: 'c-words', title: 'Wörter und Grammatik', hint: 'festigen und Sätze bauen', tools: [
      { t: 'Kombi-Meister', d: 'Bausteine zu neuen Sätzen kombinieren', icon: 'extension', to: 'kombi', badge: rec(p.bestKombi), hot: p.bestKombi > 0 },
      { t: 'Paare finden', d: 'Russisch und Deutsch zuordnen', icon: 'join_inner', to: 'pairs', badge: p.bestPairsSeconds > 0 ? `${p.bestPairsSeconds} s` : 'Neu', hot: p.bestPairsSeconds > 0 },
    ] },
  ];
}

export function Practice() {
  const p = useProgress();
  const [dialogs, setDialogs] = useState(false);
  const ready = p.gender != null;
  const due = P.dueIds(p.cards, progress.today()).length;
  const open = (to) => { if (!ready) return; if (to === '@dialog') setDialogs(true); else nav(to); };
  const unlocked = P.unlockedDay(p, D.index.days.length);

  return html`<div class="screen tab">
    <${TopBar} title="Üben" big onBack=${exitApp} />
    <div class="scroll"><${GenderGate} />
      <div class="pad">
        <button class="course c-review" onClick=${() => open('review')}>
          <div class="rowx g12"><div class="ic lg solid"><${Icon} n="replay" /></div>
            <div class="grow"><div class="kind">Wiederholen</div>
              <b class="n">${due === 0 ? 'Gerade ist nichts fällig' : due === 1 ? '1 Satz ist fällig' : `${due} Sätze sind fällig`}</b>
              <div class="d">${due === 0 ? 'Trotzdem eine gemischte Runde? Schadet nie.' : 'Kurz abfragen, damit Gelerntes sitzen bleibt'}</div></div>
            ${due > 0 ? html`<span class="tag solid">${Math.max(1, Math.floor((Math.min(due, 20) * 15) / 60))} Min.</span>` : html`<${Icon} n="chevron_right" c="cc" />`}</div>
        </button>
      </div>
      <div class="sec">${groups(p).map((g) => html`<div class=${cls('group', g.c)}>
        <h3><i></i>${g.title}<span>${g.hint}</span></h3>
        <div class="grid">${g.tools.map((t, i) => {
          const wide = g.tools.length % 2 === 1 && i === g.tools.length - 1;
          return wide
            ? html`<button class="tool wide" onClick=${() => open(t.to)}><div class="ic sm solid"><${Icon} n=${t.icon} /></div><div class="grow"><b>${t.t}</b><span class="sub" style="font-size:12px">${t.d}</span></div><${Icon} n="chevron_right" c="faint" /></button>`
            : html`<button class="tool" onClick=${() => open(t.to)}><div class="tic"><div class="ic sm solid"><${Icon} n=${t.icon} /></div>
                ${(t.badge || t.time) && html`<span class=${cls('badge', t.hot && 'rec')}>${t.badge ?? t.time}</span>`}</div><b>${t.t}</b><span>${t.d}</span></button>`;
        })}</div></div>`)}
      </div>
      <div class="end"></div>
    </div>
    ${dialogs && html`<${Sheet} onClose=${() => setDialogs(false)} title="Rollenspiele">
      <p class="sub">Jeder Kurstag endet mit einem Gespräch. Such dir eins aus.</p>
      <div class="list mt16 c-speak">${D.index.days.filter((d) => d.day <= unlocked).map((d) => html`<button class="row" onClick=${() => nav(`dialog/${d.day}`)}>
        <div class="dn">${d.day}</div><div class="tx"><b class="ell">${d.dialog.title}</b><span class="ell">${d.dialog.scene}</span></div><${Icon} n="chevron_right" c="faint" /></button>`)}</div>
      ${unlocked < D.index.days.length && html`<p class="sub mt12">Weitere Gespräche kommen mit den nächsten Kurstagen dazu.</p>`}<//>`}
  </div>`;
}
