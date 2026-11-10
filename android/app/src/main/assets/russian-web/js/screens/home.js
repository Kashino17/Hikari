// Tab »Heute«: Tagesziel, Tagesprogramm, Weitermachen — alles auf einen Blick.
import { html, cls, Icon, Ring, useState, useEffect } from '../ui.js';
import { D, P, useProgress, progress, nav, prefs, exitApp } from '../app.js';
import { storage } from '../platform.js';
import { SettingsSheet } from './me.js';
import { daySeconds, fmtDuration, timeTrack } from '../timetrack.js';
import { focus, quickPicks } from '../learner.js';

const WD = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'];

export function podcastSpot(day) {
  return { pos: Number(storage.get(`podcast_pos_${day}`) ?? 0), dur: Number(storage.get(`podcast_dur_${day}`) ?? 0) };
}
/** Angefangene, noch nicht fertig gehörte Folge geht vor. */
export function podcastResumeDay(p) {
  const d = Number(storage.get('podcast_last_day') ?? 0);
  if (!d || d > P.unlockedPodcastDay(p, D.podIndex.days.length) || p.podcastListened.includes(d)) return null;
  return podcastSpot(d).pos > 5000 ? d : null;
}

const KIND = {
  COURSE: { c: 'c-speak', icon: 'school' },
  REVIEW: { c: 'c-review', icon: 'replay' },
  PODCAST: { c: 'c-listen', icon: 'podcasts' },
};

function todayText(item, leftMin) {
  if (item.kind === 'COURSE') {
    const d = D.index.day(item.day);
    if (item.allDone) return { src: 'Sprechkurs', title: `Alle ${D.index.days.length} Tage geschafft`, desc: 'Wiederhole Tage im Kursplan', meta: 'Erledigt', route: 'plan' };
    if (item.done) return { src: `Sprechkurs, Tag ${item.day}`, title: `Tag ${item.day} geschafft`, desc: d?.title ?? '', meta: 'Erledigt', route: `lesson/${item.day}` };
    const n = d?.items.length ?? 12;
    return { src: `Sprechkurs, Tag ${item.day}`, title: `Lektion: ${d?.title ?? ''}`, desc: `${n} neue Sätze hören und nachsprechen`, meta: `ca. ${Math.max(5, Math.floor((n * 45) / 60))} Min.`, route: `lesson/${item.day}` };
  }
  if (item.kind === 'REVIEW') {
    if (item.done) return { src: 'Wiederholen', title: 'Alles wiederholt', desc: 'Gerade ist kein Satz fällig', meta: 'Erledigt', route: 'review' };
    return { src: 'Wiederholen', title: item.count === 1 ? '1 Satz ist fällig' : `${item.count} Sätze sind fällig`, desc: 'Damit Gelerntes sitzen bleibt', meta: `ca. ${Math.max(1, Math.floor((Math.min(item.count, 20) * 15) / 60))} Min.`, route: 'review' };
  }
  const src = `Podcast-Kurs, Lektion ${item.day}`;
  if (item.allDone) return { src: 'Podcast-Kurs', title: 'Alle Lektionen geschafft', desc: 'Folgen bleiben zum Nachhören da', meta: 'Erledigt', route: 'podcast' };
  if (item.done) return { src, title: 'Heutige Lektion geschafft', desc: 'Morgen geht es weiter', meta: 'Erledigt', route: 'podcast' };
  if (item.practice) return { src, title: 'Übungen zur Folge', desc: 'Folge gehört, jetzt anwenden', meta: '', route: `podcastPractice/${item.day}` };
  if (leftMin != null) return { src, title: 'Folge weiterhören', desc: 'Hören, dann die Übungen dazu', meta: `${leftMin} Min.`, route: `podcast/${item.day}` };
  return { src, title: 'Folge hören', desc: 'Hören, dann die Übungen dazu', meta: '', route: `podcast/${item.day}` };
}

/** Kurze Übungen für »Fünf Minuten Zeit?« — jede mit Bereichsfarbe, Ziel und Rekord-Anzeige. */
const QUICK = {
  reflex: { c: 'c-speak', icon: 'bolt', to: 'reflex', t: 'Sprech-Reflex', d: 'Deutsch lesen, sofort Russisch sagen', badge: (p) => (p.bestReflex > 0 ? `Rekord ${p.bestReflex}` : null) },
  talk: { c: 'c-speak', icon: 'record_voice_over', to: 'talk', t: 'Sprich frei', d: 'Frage hören, frei antworten', badge: () => null },
  blind: { c: 'c-speak', icon: 'headphones', to: 'blind', t: 'Blind-Coach', d: 'Nur Audio, gut für unterwegs', badge: () => null },
  blitz: { c: 'c-listen', icon: 'hearing', to: 'blitz', t: 'Hör-Blitz', d: '60 Sekunden: hören und tippen', badge: (p) => (p.bestBlitz > 0 ? `Rekord ${p.bestBlitz}` : null) },
  sounds: { c: 'c-listen', icon: 'graphic_eq', to: 'sounds', t: 'Lautlabor', d: 'Ähnliche Laute unterscheiden', badge: (p) => (Object.keys(p.labBest).length ? `${Math.max(...Object.values(p.labBest))} %` : null) },
  kombi: { c: 'c-words', icon: 'extension', to: 'kombi', t: 'Kombi-Meister', d: 'Bausteine neu kombinieren', badge: (p) => (p.bestKombi > 0 ? `Rekord ${p.bestKombi}` : null) },
  pairs: { c: 'c-words', icon: 'join_inner', to: 'pairs', t: 'Paare finden', d: 'Russisch und Deutsch zuordnen', badge: (p) => (p.bestPairsSeconds > 0 ? `${p.bestPairsSeconds} s` : null) },
};

export function useToday() {
  const p = useProgress();
  const today = progress.today();
  const plan = P.todayPlan(p, today, D.index.days.length, D.podIndex.days.length, podcastResumeDay(p));
  const spot = podcastSpot(plan[2].day);
  const leftMin = spot.dur > 0 ? Math.max(1, Math.floor((spot.dur - spot.pos) / 60000)) : null;
  const items = plan.map((it) => ({ ...it, ...KIND[it.kind], ...todayText(it, it.kind === 'PODCAST' ? leftMin : null) }));
  return { p, today, items, next: items.findIndex((i) => !i.done) };
}

export function GenderIntro() {
  const pick = (g) => progress.update((p) => ({ ...p, gender: g, voice: p.voice ?? g }));
  return html`<div class="card p mt12" style="margin:12px 16px 0;border-color:rgba(var(--amber-rgb),.4)">
    <div class="over amber">Bevor es losgeht</div>
    <div class="h2 mt8">Wer spricht hier?</div>
    <p class="mt8" style="font-size:14.5px;line-height:1.5">Manche russischen Ich-Sätze hängen davon ab, wer sie sagt: Ein Mann sagt <b>ja ustál</b>, eine Frau <b>ja ustála</b> (»ich bin müde«). Du lernst <b>beide</b> Formen — deine eigene sprichst du, die andere hörst und erkennst du.</p>
    <div class="rowx g8 mt16"><button class="btn" onClick=${() => pick('MALE')}>Ich bin ein Mann</button><button class="btn" onClick=${() => pick('FEMALE')}>Ich bin eine Frau</button></div>
  </div>`;
}

export function Home() {
  const { p, today, items, next } = useToday();
  const [, bump] = useState(0);
  useEffect(() => timeTrack.subscribe(() => bump((x) => x + 1)), []);
  const f = focus(p);
  const [settings, setSettings] = useState(false);
  const ready = p.gender != null;
  const xp = P.xpOn(p, today);
  const goal = prefs.dailyGoal();
  const streak = P.visibleStreak(p, today);
  const monday = today - ((new Date().getDay() + 6) % 7);
  const first = items[0].day === 1 && !items[0].done && Object.keys(p.dayStars).length === 0;
  const lesson = storage.getJSON('lesson_session_v1');
  // Nur Zwischenstände dieser Oberfläche (web: 1) lassen sich fortsetzen — ältere aus der früheren App-Fassung nicht.
  const lessonSteps = lesson && lesson.web === 1 && Number.isFinite(lesson.baseSize) ? lesson : null;
  const open = (r) => ready && nav(r);

  return html`<div class="screen tab">
    <div class="top big hasback"><button class="iconbtn" onClick=${exitApp} aria-label="Zurück zu Hikari"><${Icon} n="arrow_back" /></button><div class="tt"><h1>Russisch</h1></div>
      <button class="chip" style="border-color:transparent;background:var(--surface)" onClick=${() => nav('ich')} aria-label="Serie">
        <${Icon} n="local_fire_department" fill c=${streak > 0 ? 'amber' : 'faint'} /><b class="num">${streak}</b></button>
      <button class="iconbtn" onClick=${() => setSettings(true)} aria-label="Einstellungen"><${Icon} n="tune" /></button>
    </div>
    <div class="scroll">
      <div class="hero">
        <${Ring} value=${xp / goal} size=${84} stroke=${8}><b class="num" style="font-size:20px;line-height:1">${xp}</b><span class="faint" style="font-size:10.5px">von ${goal} XP</span><//>
        <div class="grow">
          <div class="big">${xp >= goal ? 'Tagesziel geschafft' : xp > 0 ? `Noch ${goal - xp} XP bis zum Ziel` : streak > 0 ? `Tag ${streak + (p.lastActiveDay === today ? 0 : 1)} deiner Serie` : 'Bereit für heute?'}</div>
          ${daySeconds(today) >= 60 && html`<div class="sub mt4"><${Icon} n="schedule" size="15" /> ${fmtDuration(daySeconds(today))} heute gelernt</div>`}
          <div class="week">${WD.map((w, i) => {
            const d = monday + i;
            const done = P.xpOn(p, d) > 0;
            return html`<div class=${cls(done && 'd', d === today && 't')}><i>${done && html`<${Icon} n="check" />`}</i>${w}</div>`;
          })}</div>
        </div>
      </div>
      ${!ready && html`<${GenderIntro} />`}

      <div class="sec"><div class="h2">Heute dran</div><div class="sub">Dein Tagesprogramm aus allen Bereichen, in der besten Reihenfolge.</div>
        <div class="list surface" style="background:var(--surface);border-radius:22px">
          ${items.map((it, i) => html`<button class=${cls('step', it.c, it.done && 'done', i === next && 'next')} onClick=${() => open(it.route)}>
            <div class=${cls('ic', i === next && 'solid')}><${Icon} n=${it.done ? 'check' : it.icon} /></div>
            <div class="tx"><div class="src">${it.src}</div><b class="ell">${it.title}</b><span class="d ell">${it.desc}</span></div>
            ${it.meta && html`<div class="st">${it.meta}</div>`}
          </button>`)}
          ${next >= 0 && html`<div style="padding:8px 14px 14px;border:0"><button class="btn" disabled=${!ready} onClick=${() => open(items[next].route)}>
            ${first ? 'Loslegen' : 'Weiterlernen'}<${Icon} n="arrow_forward" /></button></div>`}
          ${next < 0 && html`<div class="rowx g10" style="padding:14px 16px;border-top:1px solid var(--border)"><${Icon} n="verified" fill c="amber" />
            <span style="font-size:14px">Alles für heute erledigt. Lust auf mehr? Unter <b>Üben</b> wartet Nachschub.</span></div>`}
        </div>
      </div>

      ${lessonSteps && lessonSteps.pos > 0 && ready && html`<div class="sec"><div class="h2">Weitermachen</div><div class="sub">Hier hast du zuletzt aufgehört.</div>
        <button class="row card c-speak" style="border-radius:18px" onClick=${() => nav(`lesson/${lessonSteps.day}`)}>
          <div class="ic"><${Icon} n="resume" /></div>
          <div class="tx"><b>Tag ${lessonSteps.day}: ${D.index.day(lessonSteps.day)?.title}</b><span>Schritt ${lessonSteps.pos + 1} von ${lessonSteps.baseSize + (lessonSteps.inserts?.length ?? 0)} · dein Stand ist gespeichert</span></div>
          <${Icon} n="chevron_right" c="faint" /></button></div>`}

      ${ready && f && html`<div class="sec"><div class="h2">Dein nächster Schritt zu ${f.est.next.id}</div><div class="sub">Aus deinem Lernstand: Das bringt dich gerade am meisten voran.</div>
        <button class="nextstep solo" style=${`--c:${f.dim.color}`} onClick=${f.go}>
          <span class="ic solid"><${Icon} n=${f.dim.icon} /></span>
          <span class="grow"><b>${f.title}</b><span>${f.text}</span></span>
          <span class="go">${f.label}<${Icon} n="chevron_right" size="18" /></span></button></div>`}

      <div class="sec"><div class="hd"><div class="h2">Fünf Minuten Zeit?</div><button onClick=${() => nav('ueben')}>Alle Übungen</button></div>
        <div class="sub" style="margin:3px 4px 12px">Passend zu deinem Lernstand ausgesucht.</div>
        <div class="grid">${quickPicks(p).map((id) => { const t = QUICK[id]; const b = t.badge(p); return html`
          <button class=${cls('tool', t.c)} onClick=${() => open(t.to)}><div class="tic"><div class="ic sm solid"><${Icon} n=${t.icon} /></div>
            <span class=${cls('badge', b && 'rec')}>${b ?? 'Neu'}</span></div><b>${t.t}</b><span>${t.d}</span></button>`; })}</div>
      </div>

      <div class="sec"><div class="hd"><div class="h2">Dein Stand</div><button onClick=${() => nav('ich')}>Fortschritt</button></div>
        <div class="stat3 mt12">
          <div><b><${Icon} n="task_alt" c="good" />${P.knownCount(p.cards)}</b><span>Sätze sitzen</span></div>
          <div><b><${Icon} n="record_voice_over" c="cc c-speak" />${p.spokenOk}</b><span>gesprochen</span></div>
          <div><b><${Icon} n="bolt" fill c="" /><span style="color:var(--xp);font-size:18px">${p.xp}</span></b><span>XP gesamt</span></div>
        </div>
      </div>
      <div class="end"></div>
    </div>
    ${settings && html`<${SettingsSheet} onClose=${() => setSettings(false)} />`}
  </div>`;
}
