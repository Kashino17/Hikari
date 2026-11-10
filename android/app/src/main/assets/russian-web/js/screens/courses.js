// Tab »Kurse«: die vier Lernwege mit festem Aufbau — und der Kursplan des Sprechkurses.
import { html, cls, Icon, TopBar, Stars, Sheet, useState } from '../ui.js';
import { D, P, useProgress, nav, back, exitApp } from '../app.js';
import { storage } from '../platform.js';
import { podcastResumeDay } from './home.js';
import { GenderGate } from './me.js';

const DEEP_TALK_FROM = 15;
export const journeyDone = () => (storage.getJSON('conv_progress')?.foundation?.completed ?? []).length;

export function Courses() {
  const p = useProgress();
  const days = D.index.days;
  const unlocked = P.unlockedDay(p, days.length);
  const doneDays = Object.keys(p.dayStars).length;
  const podTotal = D.podIndex.days.length;
  const podDone = Object.keys(p.podcastDayStars).length;
  const podDay = podcastResumeDay(p) ?? P.unlockedPodcastDay(p, podTotal);
  const podLesson = D.podIndex.day(podDay);
  const patTotal = D.patterns.length;
  const patNext = D.patterns.find((x) => !p.patternsDone.includes(x.id));
  const jDone = journeyDone();

  return html`<div class="screen tab">
    <${TopBar} title="Kurse" big onBack=${exitApp} />
    <div class="scroll"><${GenderGate} />
      <p class="sub" style="padding:0 20px 14px">Vier Wege mit festem Aufbau. Jeder hat seinen eigenen Fortschritt — du kannst sie mischen, wie es dir passt.</p>
      <div class="pad stack g10">
        <button class="course c-speak" onClick=${() => nav('plan')}>
          <div class="rowx g12"><div class="ic lg solid"><${Icon} n="school" /></div>
            <div class="grow"><div class="kind">Sprechen · ${days.length} Tage</div><b class="n">Sprechkurs</b><div class="d">Vom Smalltalk bis zu tiefen Gesprächen</div></div>
            <${Icon} n="chevron_right" c="cc" /></div>
          <div class="ticks mt16">${days.map((d) => html`<i class=${cls(p.dayStars[d.day] && 'd', !p.dayStars[d.day] && d.day === unlocked && 'n', d.day === DEEP_TALK_FROM && 'gap')}></i>`)}</div>
          <div class="cfoot"><span class="ell">Jetzt: <b>${doneDays >= days.length ? 'alle Tage geschafft' : `Tag ${unlocked}, ${D.index.day(unlocked).title}`}</b></span><span>${doneDays} von ${days.length}</span></div>
        </button>

        <button class="course c-listen" onClick=${() => nav('podcast')}>
          <div class="rowx g12"><div class="cov" style=${podLesson?.media?.videoId ? `background-image:url(https://i.ytimg.com/vi/${podLesson.media.videoId}/hqdefault.jpg)` : ''}></div>
            <div class="grow"><div class="kind">Hören · ${podTotal} Lektionen</div><b class="n">Podcast-Kurs</b><div class="d">RusslandJournal: hören, verstehen, anwenden</div></div>
            <${Icon} n="chevron_right" c="cc" /></div>
          <div class="pbar mt16"><i style=${`width:${Math.min(100, Math.max(1.5, (podDone / podTotal) * 100))}%`}></i></div>
          <div class="cfoot"><span class="ell">Jetzt: <b>Lektion ${podDay}, ${podLesson?.title ?? ''}</b></span><span>${podDone} von ${podTotal}</span></div>
        </button>

        <button class="course c-words" onClick=${() => nav('patterns')}>
          <div class="rowx g12"><div class="ic lg solid"><${Icon} n="construction" /></div>
            <div class="grow"><div class="kind">Grammatik · ${patTotal} Muster</div><b class="n">Satzbau</b><div class="d">Muster lernen, eigene Sätze bauen</div></div>
            <${Icon} n="chevron_right" c="cc" /></div>
          <div class="pbar mt16"><i style=${`width:${Math.min(100, Math.max(1.5, (p.patternsDone.length / patTotal) * 100))}%`}></i></div>
          <div class="cfoot"><span class="ell">Jetzt: <b>${patNext ? patNext.title : 'alle Muster geschafft'}</b></span><span>${p.patternsDone.length} von ${patTotal}</span></div>
        </button>

        <button class="course c-amber" onClick=${() => nav('anja')}>
          <div class="rowx g12"><div class="ic lg solid round"><${Icon} n="forum" /></div>
            <div class="grow"><div class="kind">Gespräch · mit Anja</div><b class="n">Gesprächsreise</b><div class="d">Vier Mini-Gespräche, danach frei mit KI reden</div></div>
            <${Icon} n="chevron_right" c="cc" /></div>
          <div class="ticks mt16">${[0, 1, 2, 3].map((i) => html`<i class=${cls(i < jDone && 'd', i === jDone && 'n')}></i>`)}</div>
          <div class="cfoot"><span class="ell">Jetzt: <b>${jDone >= 4 ? 'frei sprechen' : jDone >= 3 ? 'freies Gespräch ist offen' : `Mini-Gespräch ${jDone + 1}`}</b></span><span>${Math.min(jDone, 4)} von 4</span></div>
        </button>
      </div>
      <div class="end"></div>
    </div>
  </div>`;
}

const WEEKS = [
  { title: 'Woche 1', hint: 'Small Talk, die Basis', from: 1, to: 7 },
  { title: 'Woche 2', hint: 'Small Talk im Alltag', from: 8, to: 14 },
  { title: 'Woche 3', hint: 'Tiefe Gespräche', from: 15, to: 99 },
];

/** Kursplan Sprechkurs: Tage nach Wochen; Tap öffnet ein Blatt mit Lektion und Rollenspiel. */
export function Plan() {
  const p = useProgress();
  const [sel, setSel] = useState(null);
  const days = D.index.days;
  const unlocked = P.unlockedDay(p, days.length);
  const allDone = Object.keys(p.dayStars).length >= days.length;
  return html`<div class="screen c-speak">
    <${TopBar} title="Sprechkurs" sub=${`${days.length} Tage, vom Smalltalk bis zu tiefen Gesprächen`} onBack=${() => back('kurse')} />
    <div class="scroll"><${GenderGate} />
      <p class="sub" style="padding:0 20px">Jeder Tag hat eine Lektion mit neuen Sätzen und ein Rollenspiel. Der nächste Tag wird frei, sobald du den aktuellen abschließt.</p>
      ${WEEKS.map((w) => {
        const list = days.filter((d) => d.day >= w.from && d.day <= w.to);
        const done = list.filter((d) => p.dayStars[d.day]).length;
        return html`<div class="sec"><div class="hd"><div class="h3">${w.title} <span class="muted" style="font-weight:400;font-size:13px;margin-left:4px">${w.hint}</span></div><span class="sub num">${done}/${list.length}</span></div>
          <div class="list mt8">${list.map((d) => {
            const stars = p.dayStars[d.day] ?? 0;
            const current = d.day === unlocked && !allDone && !stars;
            const locked = d.day > unlocked;
            return html`<button class=${cls('row', stars > 0 && 'done', current && 'now', locked && 'locked')} disabled=${locked} onClick=${() => setSel(d)}>
              <div class="dn">${stars > 0 ? html`<${Icon} n="check" />` : locked ? html`<${Icon} n="lock" />` : d.day}</div>
              <div class="tx"><b class="ell">${d.title}</b><span class="ell">${d.goal}</span></div>
              ${stars > 0 && html`<${Stars} n=${stars} />`}
              ${current && html`<span class="tag solid">Heute</span>`}
            </button>`;
          })}</div></div>`;
      })}
      <div class="end"></div>
    </div>
    ${sel && html`<${Sheet} onClose=${() => setSel(null)} title=${`Tag ${sel.day}: ${sel.title}`}>
      <p class="sub">${sel.goal}</p>
      <div class="rowx g8 wrap mt12"><span class="tag"><${Icon} n="chat_bubble" />${sel.items.length} Sätze</span><span class="tag"><${Icon} n="graphic_eq" />${sel.sound.title}</span>
        ${p.dayStars[sel.day] && html`<${Stars} n=${p.dayStars[sel.day]} />`}</div>
      <div class="stack g8 mt20">
        <button class="btn" onClick=${() => nav(`lesson/${sel.day}`)}><${Icon} n="play_arrow" fill />${p.dayStars[sel.day] ? 'Lektion wiederholen' : 'Lektion starten'}</button>
        <button class="btn ghost" onClick=${() => nav(`dialog/${sel.day}`)}><${Icon} n="forum" />Nur das Rollenspiel: ${sel.dialog.title}</button>
      </div><//>`}
  </div>`;
}
