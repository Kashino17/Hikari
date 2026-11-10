// Tab »Kurse«: die Lernwege mit festem Aufbau — und der Jahresplan des Sprechkurses.
import { html, cls, Icon, TopBar, Stars, Sheet, useState } from '../ui.js';
import { StageList } from './stages.js';
import { D, P, Pl, useProgress, usePlanState, planState, nav, back, exitApp } from '../app.js';
import { storage } from '../platform.js';
import { podcastResumeDay } from './home.js';
import { GenderGate } from './me.js';

export const journeyDone = () => (storage.getJSON('conv_progress')?.foundation?.completed ?? []).length;

export function Courses() {
  const p = useProgress();
  const st = usePlanState();
  const plan = D.plan;
  const cur = plan.currentDay(p, st);
  const now = cur != null ? dayView(plan.day(cur), p, st) : null;
  const doneDays = plan.doneCount(p, st);
  const podTotal = D.podIndex.days.length;
  const podDone = Object.keys(p.podcastDayStars).length;
  const podDay = podcastResumeDay(p) ?? P.unlockedPodcastDay(p, podTotal);
  const podLesson = D.podIndex.day(podDay);
  const jDone = journeyDone();

  return html`<div class="screen tab">
    <${TopBar} title="Kurse" big onBack=${exitApp} />
    <div class="scroll"><${GenderGate} />
      <p class="sub" style="padding:0 20px 14px">Drei Wege mit festem Aufbau. Jeder hat seinen eigenen Fortschritt, du kannst sie mischen, wie es dir passt.</p>
      <div class="pad stack g10">
        <button class="course c-speak" onClick=${() => nav('plan')}>
          <div class="rowx g12"><div class="ic lg solid"><${Icon} n="school" /></div>
            <div class="grow"><div class="kind">Sprechen · 365 Tage</div><b class="n">Jahreskurs</b><div class="d">Vom ersten Hallo bis zum freien Gespräch</div></div>
            <${Icon} n="chevron_right" c="cc" /></div>
          <div class="pbar year mt16"><i style=${`width:${Math.max(0.6, (doneDays / 365) * 100)}%`}></i><s style=${`left:${(plan.builtDays / 365) * 100}%`}></s></div>
          <div class="cfoot"><span class="ell">Jetzt: <b>${now ? `Tag ${now.day}, ${now.title}` : `alle ${plan.builtDays} gebauten Tage geschafft`}</b></span><span>${now ? now.total : `${doneDays} von 365`}</span></div>
        </button>

        <button class="course c-listen" onClick=${() => nav('podcast')}>
          <div class="rowx g12"><div class="cov" style=${podLesson?.media?.videoId ? `background-image:url(https://i.ytimg.com/vi/${podLesson.media.videoId}/hqdefault.jpg)` : ''}></div>
            <div class="grow"><div class="kind">Hören · ${podTotal} Lektionen</div><b class="n">Podcast-Kurs</b><div class="d">RusslandJournal: hören, verstehen, anwenden</div></div>
            <${Icon} n="chevron_right" c="cc" /></div>
          <div class="pbar mt16"><i style=${`width:${Math.min(100, Math.max(1.5, (podDone / podTotal) * 100))}%`}></i></div>
          <div class="cfoot"><span class="ell">Jetzt: <b>Lektion ${podDay}, ${podLesson?.title ?? ''}</b></span><span>${podDone} von ${podTotal}</span></div>
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

/**
 * Ein Plan-Tag mit allem, was die Oberfläche braucht:
 * state  done | now | open (offen gebliebener Gesprächstag) | locked | future (noch nicht gebaut)
 */
export function dayView(d, p, st) {
  const plan = D.plan;
  const talk = d.kind === 'talk';
  const built = plan.isBuilt(d.day);
  const cur = plan.currentDay(p, st);
  const stars = built ? plan.stars(d.day, p, st) : 0;
  const state = !built ? 'future' : stars > 0 ? 'done' : d.day === cur ? 'now' : cur == null || d.day < cur ? 'open' : 'locked';
  const lessonDay = talk ? null : D.index.day(d.lesson);
  const secs = !built ? [] : talk ? Pl.talkEstimate() : Pl.lessonEstimate(lessonDay, !!d.pattern);
  const stages = talk ? Pl.STAGES.TALKDAY : Pl.STAGES.LESSON;
  const snap = storage.getJSON(talk ? 'talk_session_v1' : 'lesson_session_v1');
  const resume = snap && snap.web === 1 && snap.v === 2 && snap.day === (talk ? d.day : d.lesson) && snap.pos > 0 ? snap : null;
  const stagesDone = resume ? Math.min(resume.done ?? 0, stages.length - 1) : 0;
  const f = Pl.paceFactor(st);
  const left = secs.slice(stagesDone).reduce((a, b) => a + b, 0);
  return {
    ...d, talk, built, stars, state, lessonDay, secs, stages, resume, stagesDone,
    total: built ? Pl.minutesLabel(secs.reduce((a, b) => a + b, 0), f) : '',
    left: Pl.minutesLabel(left, f),
    route: talk ? `talkday/${d.day}` : `lesson/${d.lesson}`,
  };
}

const startLabel = (v) => (v.resume ? `Weitermachen · noch ${v.left}` : v.state === 'done' ? (v.talk ? 'Gesprächstag wiederholen' : 'Lektion wiederholen') : `${v.talk ? 'Gesprächstag' : 'Lektion'} starten · ${v.total}`);

/** Die Karte für den Tag, der dran ist: Titel, Ziel, Etappen mit Minuten, ein Knopf. */
function TodayCard({ v }) {
  return html`<div class=${cls('daycard', v.talk ? 'c-amber' : 'c-speak')}>
    <div class="rowx g8"><span class="over c grow">Tag ${v.day} von 365 · ${v.talk ? 'Gesprächstag' : 'Lektion'}</span>${v.resume && html`<span class="tag">angefangen</span>`}</div>
    <div class="title mt8">${v.title}</div>
    <p class="sub mt4" style="font-size:14px;line-height:1.45">${v.talk ? v.scene : v.goal}</p>
    <${StageList} stages=${v.stages} seconds=${v.secs} done=${v.stagesDone} current=${v.resume ? v.stagesDone : -1} />
    <button class="btn area mt16" onClick=${() => nav(v.route)}><${Icon} n="play_arrow" fill />${startLabel(v)}</button>
  </div>`;
}

function DayRow({ v, onClick }) {
  const muted = v.state === 'locked' || v.state === 'future';
  return html`<button class=${cls('row', v.talk && 'c-amber', v.state === 'done' && 'done', v.state === 'now' && 'now', muted && 'locked')} disabled=${v.state === 'locked'} onClick=${onClick}>
    <div class="dn">${v.state === 'done' ? html`<${Icon} n="check" />` : muted ? html`<${Icon} n="lock" />` : v.talk ? html`<${Icon} n="forum" />` : v.day}</div>
    <div class="tx"><b class="ell">${v.talk ? `Gesprächstag: ${v.title}` : v.title}</b><span class="ell">${muted || v.state === 'done' ? `Tag ${v.day}` : `Tag ${v.day} · ${v.total}`}${v.state === 'open' ? ' · noch offen' : ''}</span></div>
    ${v.state === 'done' && html`<${Stars} n=${v.stars} />`}
    ${v.state === 'now' && html`<span class="tag solid">Heute</span>`}
  </button>`;
}

/** Jahresplan: oben der Tag, der dran ist; darunter der Weg durch das Jahr in Phasen und Fünf-Tage-Blöcken. */
export function Plan() {
  const p = useProgress();
  const st = usePlanState();
  const plan = D.plan;
  const cur = plan.currentDay(p, st);
  const curPhase = plan.phaseOf(cur ?? plan.builtDays);
  const curBlock = plan.day(cur ?? plan.builtDays).block;
  const [sel, setSel] = useState(null);
  const [openPhase, setOpenPhase] = useState(curPhase.id);
  const [openBlock, setOpenBlock] = useState(curBlock);
  const view = (d) => dayView(d, p, st);
  const now = cur != null ? view(plan.day(cur)) : null;
  const done = plan.doneCount(p, st);

  return html`<div class="screen c-speak">
    <${TopBar} title="Jahreskurs" sub=${`${done} von 365 Tagen · jeder fünfte Tag ist ein Gesprächstag`} onBack=${() => back('kurse')} />
    <div class="scroll"><${GenderGate} />
      ${now ? html`<${TodayCard} v=${now} />` : html`<div class="daycard c-amber"><div class="over c">Stand heute</div>
        <div class="title mt8">Alle ${plan.builtDays} gebauten Tage geschafft</div>
        <p class="sub mt4" style="font-size:14px;line-height:1.45">Die nächsten Tage stehen im Plan und werden als Nächstes gebaut. Bis dahin: wiederholen, frei sprechen, Podcast hören.</p></div>`}

      <div class="sec"><div class="h2">Dein Weg durchs Jahr</div><div class="sub">Sechs Phasen. Jede endet mit einem großen Gespräch.</div>
        <div class="stack g10">${plan.phases.map((ph) => {
          const open = openPhase === ph.id;
          const total = ph.to - ph.from + 1;
          let phDone = 0;
          for (let d = ph.from; d <= Math.min(ph.to, plan.builtDays); d++) if (plan.stars(d, p, st) > 0) phDone++;
          const future = ph.from > plan.builtDays;
          return html`<div class=${cls('phase', open && 'open', future && 'future')}>
            <button class="ph-h" onClick=${() => setOpenPhase(open ? null : ph.id)}>
              <span class="lv">${ph.level}</span>
              <span class="grow"><b>${ph.title}</b><span>Tag ${ph.from}–${ph.to}${future ? ' · in Vorbereitung' : ''}</span></span>
              <span class="sub num">${phDone}/${total}</span><${Icon} n=${open ? 'expand_less' : 'expand_more'} c="faint" /></button>
            <div class="pbar"><i style=${`width:${(phDone / total) * 100}%`}></i></div>
            ${open && html`<p class="sub ph-goal">${ph.goal}</p>
              ${plan.blocks.filter((b) => b.phase === ph.id).map((b) => {
                const days = plan.days.slice(b.from - 1, b.to).map(view);
                const bOpen = openBlock === b.n;
                const built = plan.isBuilt(b.from);
                return html`<div class=${cls('blk', bOpen && 'open')}>
                  <button class="blk-h" onClick=${() => setOpenBlock(bOpen ? null : b.n)}>
                    <span class="grow"><b>${b.title}</b><span>${b.focus}</span></span>
                    <span class="ticks five">${days.map((v) => html`<i class=${cls(v.state === 'done' && 'd', v.state === 'now' && 'n', v.talk && 't')}></i>`)}</span>
                    ${!built && html`<${Icon} n="lock" c="faint" size="16" />`}</button>
                  ${bOpen && html`<div class="list">${days.map((v) => html`<${DayRow} v=${v} onClick=${() => setSel(v)} />`)}</div>`}
                </div>`;
              })}`}
          </div>`;
        })}</div>
      </div>
      <div class="end"></div>
    </div>
    ${sel && html`<${Sheet} onClose=${() => setSel(null)} title=${`Tag ${sel.day}: ${sel.title}`}>
      <p class="sub">${sel.talk ? sel.scene : sel.goal}</p>
      ${sel.state === 'future' && html`<div class="hint mt16"><span class="over">In Vorbereitung</span><p>Dieser Tag steht fest im Plan, ist aber noch nicht gebaut. Er wird freigeschaltet, sobald du in seine Nähe kommst.</p></div>`}
      ${sel.built && html`
        <div class="rowx g8 wrap mt12">
          ${!sel.talk && html`<span class="tag"><${Icon} n="chat_bubble" />${sel.lessonDay.items.length} neue Sätze</span>`}
          ${sel.pattern && html`<span class="tag c-words"><${Icon} n="construction" />Satzbau-Muster</span>`}
          ${sel.talk && html`<span class="tag"><${Icon} n="replay" />kein neuer Stoff</span>`}
          ${sel.stars > 0 && html`<${Stars} n=${sel.stars} />`}</div>
        <${StageList} stages=${sel.stages} seconds=${sel.secs} done=${sel.stagesDone} current=${sel.resume ? sel.stagesDone : -1} />
        <div class="stack g8 mt16">
          <button class="btn" onClick=${() => nav(sel.route)}><${Icon} n="play_arrow" fill />${startLabel(sel)}</button>
          ${!sel.talk && html`<button class="btn ghost" onClick=${() => nav(`dialog/${sel.lesson}`)}><${Icon} n="forum" />Nur das Rollenspiel: ${sel.lessonDay.dialog.title}</button>`}
        </div>`}
    <//>`}
  </div>`;
}
