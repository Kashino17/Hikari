// Tab »Fortschritt«. Eine ruhige Hauptseite mit drei Dingen:
//   1. Dein Profil  – Niveau als Netz der Fähigkeiten, darunter der nächste Schritt
//   2. Aktivität    – 14 Tage, Regler Lernzeit/XP, Tage antippbar
//   3. Drei Türen   – Wortschatz · Lernzeit · Erfolge (jede öffnet ihre eigene Seite)
// Alles Weitere liegt eine Ebene tiefer, damit die Hauptseite auf einen Blick lesbar bleibt.
import { html, cls, Icon, TopBar, Sheet, Ring, useState, useEffect, useMemo } from '../ui.js';
import { piecesDone } from './erleben.js';
import { D, P, useProgress, progress, planState, prefs, exitApp, nav, back, testSelection, phrasebookPreset } from '../app.js';
import { storage, isNative } from '../platform.js';
import { journeyDone } from './courses.js';
import { SettingsSheet, DevTools, levelOf } from './me.js';
import { timeTrack, daySeconds, fmtDuration, MODES, AREA_COLOR, AREA_NAME } from '../timetrack.js';
import { LEVELS, TARGETS, DIMS } from '../domain/level.js';
import { assess, focus, themeStats } from '../learner.js';

const FSI_HOURS = 1100;
const WD_SHORT = ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'];
const MONTHS = ['Jan.', 'Feb.', 'März', 'Apr.', 'Mai', 'Juni', 'Juli', 'Aug.', 'Sep.', 'Okt.', 'Nov.', 'Dez.'];
const dateOf = (d) => new Date(d * 86400000);
const wd = (d) => WD_SHORT[dateOf(d).getUTCDay()];
const dayLabel = (d, today) => (d === today ? 'Heute' : d === today - 1 ? 'Gestern' : `${wd(d)}, ${dateOf(d).getUTCDate()}. ${MONTHS[dateOf(d).getUTCMonth()]}`);
const clock = (secs) => { const m = Math.round(secs / 60); return m < 60 ? `${m} Min.` : `${Math.floor(m / 60)}:${String(m % 60).padStart(2, '0')} Std.`; };
/** Stunden mit einer Nachkommastelle, ab 100 Stunden ohne. */
const hNum = (secs) => { const h = secs / 3600; return h >= 100 ? String(Math.round(h)) : h.toFixed(1).replace('.', ','); };
const hours = (secs) => (secs >= 3600 ? `${hNum(secs)} Std.` : fmtDuration(secs));
const MASTERY = [
  { key: 'sits', label: 'Sitzt', color: 'var(--good)', test: (b) => b >= 4 },
  { key: 'active', label: 'Aktiv', color: 'rgb(var(--review))', test: (b) => b === 3 },
  { key: 'shaky', label: 'Wackelig', color: 'var(--amber)', test: (b) => b >= 1 && b <= 2 },
];
function countMastery(ids, cards) {
  const out = { sits: 0, active: 0, shaky: 0, fresh: 0 };
  for (const id of ids) { const m = MASTERY.find((x) => x.test(cards[id]?.box ?? 0)); out[m ? m.key : 'fresh']++; }
  return out;
}
function useTicker() {
  const [, bump] = useState(0);
  useEffect(() => timeTrack.subscribe(() => bump((x) => x + 1)), []);
}
const totalSeconds = () => Object.values(timeTrack.totals()).reduce((a, b) => a + b, 0);
function areaTotals(totals) {
  const out = {};
  for (const [k, s] of Object.entries(totals)) { const a = MODES[k]?.[1] ?? 'words'; out[a] = (out[a] ?? 0) + s; }
  return out;
}

// ── Profil: Niveau als Netz ────────────────────────────────────────────────

/** Netz der Fähigkeiten: außen liegt das Ziel des nächsten Niveaus, die Fläche bist du. */
function Radar({ dims, levelId }) {
  const W = 320, H = 236, cx = 160, cy = 118, R = 80, HUB = 30;
  const n = dims.length;
  const ang = (i) => -Math.PI / 2 + (i * 2 * Math.PI) / n;
  const at = (i, r) => [cx + Math.cos(ang(i)) * r, cy + Math.sin(ang(i)) * r];
  const poly = (f) => dims.map((_, i) => at(i, HUB + (R - HUB) * f).map((v) => v.toFixed(1)).join(',')).join(' ');
  const mine = dims.map((d, i) => at(i, HUB + (R - HUB) * d.frac));
  return html`<svg class="radar" viewBox="0 0 ${W} ${H}" width="100%" role="img" aria-label="Deine Fähigkeiten im Vergleich zum nächsten Niveau">
    ${[0.5, 1].map((f) => html`<polygon points=${poly(f)} fill="none" stroke="rgba(255,255,255,.09)" stroke-width="1" stroke-dasharray=${f === 1 ? '0' : '3 4'} />`)}
    ${dims.map((_, i) => { const [x, y] = at(i, R); return html`<line x1=${cx} y1=${cy} x2=${x} y2=${y} stroke="rgba(255,255,255,.07)" />`; })}
    <polygon points=${mine.map((p) => p.map((v) => v.toFixed(1)).join(',')).join(' ')} fill="rgba(251,191,36,.2)" stroke="#FBBF24" stroke-width="2" stroke-linejoin="round" />
    ${dims.map((d, i) => html`<circle cx=${mine[i][0]} cy=${mine[i][1]} r="4.5" fill=${`rgb(${d.color})`} stroke="#262629" stroke-width="2" />`)}
    <circle cx=${cx} cy=${cy} r=${HUB - 3} fill="#262629" stroke="rgba(251,191,36,.55)" stroke-width="1.5" />
    <text x=${cx} y=${cy + 8} text-anchor="middle" font-size="23" font-weight="800" fill="#fff" letter-spacing="-.5">${levelId}</text>
    ${dims.map((d, i) => {
      const [x, y] = at(i, R + 12);
      const c = Math.cos(ang(i));
      const s = Math.sin(ang(i));
      const anchor = Math.abs(c) < 0.3 ? 'middle' : c > 0 ? 'start' : 'end';
      const dy = s < -0.5 ? -14 : s > 0.5 ? 12 : -1;
      return html`<g><text x=${x} y=${y + dy} text-anchor=${anchor} font-size="12.5" font-weight="600" fill=${`rgb(${d.color})`}>${d.name}</text>
        <text x=${x} y=${y + dy + 14} text-anchor=${anchor} font-size="11" fill=${d.done ? '#34D399' : 'rgba(255,255,255,.55)'}>${d.done ? `${d.target} ✓` : `${d.value} / ${d.target}`}</text></g>`;
    })}
  </svg>`;
}

function Profile({ est, f, onInfo }) {
  return html`<section class="profile">
    <button class="profile-head" onClick=${onInfo} aria-label="Was die Niveaus bedeuten">
      <div class="grow"><h2>${est.level.name}</h2>
        <div class="sub">${est.next ? `${Math.round(est.progress * 100)} % des Wegs zu ${est.next.id} geschafft` : 'Mehr misst Hikari nicht — jetzt zählen echte Gespräche.'}</div></div>
      <${Icon} n="info" c="faint" /></button>
    ${est.dims.length >= 3 && html`<${Radar} dims=${est.dims} levelId=${est.level.id} />`}
    ${f && html`<button class="nextstep" style=${`--c:${f.dim.color}`} onClick=${f.go}>
      <span class="ic sm solid"><${Icon} n=${f.dim.icon} /></span>
      <span class="grow"><b>${f.title}</b><span>${f.text}</span></span>
      <span class="go">${f.label}<${Icon} n="chevron_right" size="18" /></span></button>`}
  </section>`;
}

// ── Aktivität ──────────────────────────────────────────────────────────────

function Activity({ p, today }) {
  const [mode, setMode] = useState(() => storage.get('ui_chart') ?? 'time');
  const [sel, setSel] = useState(today);
  const days = Array.from({ length: 14 }, (_, i) => today - 13 + i);
  const isXp = mode === 'xp';
  const vals = days.map((d) => (isXp ? P.xpOn(p, d) : daySeconds(d) / 60));
  const goal = isXp ? prefs.dailyGoal() : prefs.dailyMinutes();
  const max = Math.max(goal * 1.15, ...vals, 1);
  const H = 104;
  const pick = (m) => { setMode(m); storage.set('ui_chart', m); };
  const byArea = areaTotals(timeTrack.day(sel));
  const selVal = isXp ? `${P.xpOn(p, sel)} XP` : fmtDuration(daySeconds(sel));
  return html`<section class="block">
    <div class="block-head"><h2>Aktivität</h2>
      <div class="seg mini">${[['time', 'Lernzeit'], ['xp', 'XP']].map(([k, l]) => html`<button class=${mode === k ? 'on' : ''} onClick=${() => pick(k)}>${l}</button>`)}</div></div>
    <div class="card p">
      <div class="act-sel"><div class="sub">${dayLabel(sel, today)}</div><div class="act-val num">${selVal}</div></div>
      <div class="bars" style=${`height:${H}px`}>
        <div class="goal" style=${`bottom:${(goal / max) * H}px`}><span>${isXp ? `${goal} XP` : `${goal} Min.`}</span></div>
        ${days.map((d, i) => {
          let inner;
          if (isXp) inner = html`<i style=${`height:${Math.max(vals[i] ? 4 : 2, (vals[i] / max) * H)}px;background:${vals[i] ? 'var(--amber)' : 'var(--border2)'}`}></i>`;
          else {
            const ar = areaTotals(timeTrack.day(d));
            const tot = Object.values(ar).reduce((a, b) => a + b, 0);
            inner = tot ? html`<i class="stack" style=${`height:${Math.max(4, (tot / 60 / max) * H)}px;flex-direction:column-reverse`}>${Object.keys(AREA_COLOR).filter((a) => ar[a]).map((a) => html`<b style=${`flex:${ar[a]};background:${AREA_COLOR[a]}`}></b>`)}</i>`
              : html`<i style="height:2px;background:var(--border2)"></i>`;
          }
          return html`<button class=${cls('bar', d === sel && 'on')} onClick=${() => setSel(d)} aria-label=${dayLabel(d, today)}>${inner}</button>`;
        })}
      </div>
      <div class="bar-days">${days.map((d) => html`<span class=${cls(d === sel && 'on')}>${wd(d)}</span>`)}</div>
      ${!isXp && Object.keys(byArea).length > 0 && html`<div class="act-areas">${Object.keys(AREA_COLOR).filter((a) => byArea[a]).map((a) => html`<span><i style=${`background:${AREA_COLOR[a]}`}></i>${AREA_NAME[a]} <b>${clock(byArea[a])}</b></span>`)}</div>`}
    </div>
  </section>`;
}

// ── Drei Türen ─────────────────────────────────────────────────────────────

function Doors({ p, today }) {
  const allIds = useMemo(() => [...D.index.itemById.keys(), ...D.podIndex.itemById.keys()], []);
  const m = countMastery(allIds, p.cards);
  const practiced = allIds.length - m.fresh;
  const totals = timeTrack.totals();
  const total = Object.values(totals).reduce((a, b) => a + b, 0);
  const areas = areaTotals(totals);
  const streak = P.visibleStreak(p, today);
  const monday = today - ((dateOf(today).getUTCDay() + 6) % 7);
  const records = [p.bestBlitz, p.bestReflex, p.bestKombi, p.bestPairsSeconds, Object.keys(p.labBest).length].filter(Boolean).length;
  return html`<section class="doors">
    <button class="door wide" onClick=${() => nav('ich/wortschatz')}>
      <div class="door-head"><span>Wortschatz</span><${Icon} n="chevron_right" c="faint" size="20" /></div>
      <div class="door-main"><b class="num">${m.sits + m.active}</b><span>Sätze sitzen sicher</span></div>
      <div class="mbar" style="height:8px">${practiced ? MASTERY.map((x) => (m[x.key] ? html`<i style=${`flex:${m[x.key]};background:${x.color}`}></i>` : null)) : html`<i style="flex:1;background:var(--border2)"></i>`}</div>
      <div class="door-foot">${m.shaky} wackelig · ${practiced} von ${allIds.length} geübt</div>
    </button>
    <button class="door" onClick=${() => nav('ich/lernzeit')}>
      <div class="door-head"><span>Lernzeit</span><${Icon} n="chevron_right" c="faint" size="20" /></div>
      <div class="door-main"><b class="num">${total >= 3600 ? hNum(total) : Math.round(total / 60)}</b><span>${total >= 3600 ? 'Stunden' : 'Minuten'}</span></div>
      <div class="mbar" style="height:8px">${total ? Object.keys(AREA_COLOR).filter((a) => areas[a]).map((a) => html`<i style=${`flex:${areas[a]};background:${AREA_COLOR[a]}`}></i>`) : html`<i style="flex:1;background:var(--border2)"></i>`}</div>
    </button>
    <button class="door" onClick=${() => nav('ich/erfolge')}>
      <div class="door-head"><span>Erfolge</span><${Icon} n="chevron_right" c="faint" size="20" /></div>
      <div class="door-main"><b class="num"><${Icon} n="local_fire_department" fill c=${streak ? 'amber' : 'faint'} size="26" />${streak}</b><span>${streak === 1 ? 'Tag Serie' : 'Tage Serie'}</span></div>
      <div class="weekdots">${Array.from({ length: 7 }, (_, i) => monday + i).map((d) => html`<i class=${cls(P.xpOn(p, d) > 0 || daySeconds(d) > 0 ? 'on' : '', d === today && 'today')}></i>`)}</div>
    </button>
  </section>`;
}

// ── Niveau-Erklärung ───────────────────────────────────────────────────────

function LevelSheet({ est, onClose }) {
  const idx = LEVELS.findIndex((l) => l.id === est.level.id);
  return html`<${Sheet} onClose=${onClose} title="So wird dein Niveau geschätzt">
    <p class="sub">Hikari misst fünf Fähigkeiten an dem, was du wirklich getan hast. Ein Niveau gilt als erreicht, wenn alle Fähigkeiten sein Ziel erfüllen — die schwächste bestimmt also, wo du stehst. Das ist eine Schätzung, kein offizieller Test.</p>
    <div class="stack g8 mt16">${LEVELS.map((l, i) => {
      const here = i === idx;
      const next = est.next?.id === l.id;
      const t = TARGETS[l.id];
      return html`<div class=${cls('lvrow', here && 'here', i < idx && 'past')}>
        <div class="lvbadge">${l.id}</div>
        <div class="grow"><div class="rowx g8 wrap"><b>${l.name}</b>${here && html`<span class="tag solid c-amber">Du bist hier</span>`}${next && html`<span class="tag c-amber">Als Nächstes</span>`}</div>
          <p class="sub mt4" style="line-height:1.45">${l.desc}</p>
          ${next && html`<div class="stack g8 mt12">${est.dims.map((d) => html`<div>
            <div class="rowx g8" style="font-size:13px"><span class="grow" style=${`color:rgb(${d.color});font-weight:600`}>${d.name}</span><span class="muted">${d.target} ${d.unit[1]}</span><b class="num" style=${`min-width:64px;text-align:right;color:${d.done ? 'var(--good)' : 'var(--text)'}`}>${Math.min(d.value, d.target)}/${d.target}</b></div>
            <div class="pbar mt4" style="height:4px"><i style=${`width:${d.frac * 100}%;background:rgb(${d.color})`}></i></div></div>`)}</div>`}
          ${!next && !here && t && i > idx && html`<p class="faint mt4" style="font-size:12px">${DIMS.filter((d) => t[d.id] > 0).map((d) => `${d.name} ${t[d.id]}`).join(' · ')}</p>`}
          ${!t && l.id !== 'A0' && html`<p class="faint mt4" style="font-size:12px">Misst Hikari nicht — dafür braucht es viel echtes Gespräch, Lesen und Schreiben.</p>`}
        </div></div>`;
    })}</div>
    <p class="faint mt16" style="font-size:12px;line-height:1.5">Wortschatz zählt Sätze, die im Wiederholungs-System mindestens vier Tage Abstand richtig überstanden haben. Sprechen zählt Sätze, die die Spracherkennung akzeptiert hat. Die Zielwerte sind Hikari-Richtwerte.</p>
    <button class="btn mt16" onClick=${onClose}>Verstanden</button><//>`;
}

// ── Hauptseite ─────────────────────────────────────────────────────────────

export function Stats() {
  const p = useProgress();
  useTicker();
  const [settings, setSettings] = useState(false);
  const [levelOpen, setLevelOpen] = useState(false);
  const [dev, setDev] = useState(false);
  const today = progress.today();
  const est = assess(p);
  const f = focus(p);
  return html`<div class="screen tab">
    <${TopBar} title="Fortschritt" big onBack=${exitApp} right=${html`<button class="iconbtn" onClick=${() => setSettings(true)} aria-label="Einstellungen"><${Icon} n="tune" /></button>`} />
    <div class="scroll"><div class="stats">
      <${Profile} est=${est} f=${f} onInfo=${() => setLevelOpen(true)} />
      <${Activity} p=${p} today=${today} />
      <${Doors} p=${p} today=${today} />
      ${!isNative && html`<button class="btn text" style="align-self:center" onClick=${() => setDev(true)}><${Icon} n="science" size="18" />Test-Werkzeuge</button>`}
      <div class="end"></div>
    </div></div>
    ${settings && html`<${SettingsSheet} onClose=${() => setSettings(false)} />`}
    ${levelOpen && html`<${LevelSheet} est=${est} onClose=${() => setLevelOpen(false)} />`}
    ${dev && html`<${Sheet} onClose=${() => setDev(false)} title="Test-Werkzeuge"><div class="mt12"><${DevTools} /></div><button class="btn mt24" onClick=${() => setDev(false)}>Fertig</button><//>`}
  </div>`;
}

// ── Unterseite: Wortschatz ─────────────────────────────────────────────────

/** Ring aus Segmenten. segments: [{v, color}] */
function Donut({ segments, size = 148, stroke = 16, children }) {
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const total = segments.reduce((a, s) => a + s.v, 0) || 1;
  const gap = segments.filter((s) => s.v > 0).length > 1 ? 3 : 0;
  let off = 0;
  return html`<div class="ring" style=${`width:${size}px;height:${size}px`}>
    <svg width=${size} height=${size}><circle cx=${size / 2} cy=${size / 2} r=${r} stroke="rgba(255,255,255,.07)" stroke-width=${stroke} fill="none" />
      ${segments.filter((s) => s.v > 0).map((s) => {
        const len = (s.v / total) * c;
        const el = html`<circle cx=${size / 2} cy=${size / 2} r=${r} fill="none" stroke=${s.color} stroke-width=${stroke} stroke-dasharray=${`${Math.max(0, len - gap)} ${c}`} stroke-dashoffset=${-off} />`;
        off += len;
        return el;
      })}</svg>
    <div class="in">${children}</div></div>`;
}

export function StatsVocab() {
  const p = useProgress();
  const cards = p.cards;
  const [col, setCol] = useState(0);
  const [theme, setTheme] = useState(null);
  const allIds = useMemo(() => [...D.index.itemById.keys(), ...D.podIndex.itemById.keys()], []);
  const all = countMastery(allIds, cards);
  const ofDays = (idx, a, b) => idx.days.filter((d) => d.day >= a && d.day <= b).flatMap((d) => d.items.map((i) => i.id));
  const cols = useMemo(() => [
    ['Basis', 'Jahreskurs: Small Talk, die Basis', 'speak', ofDays(D.index, 1, 7)],
    ['Alltag', 'Jahreskurs: Small Talk im Alltag', 'speak', ofDays(D.index, 8, 14)],
    ['Tief', 'Jahreskurs: tiefe Gespräche', 'speak', ofDays(D.index, 15, 20)],
    ['Handeln', 'Jahreskurs: handeln, erzählen, Nähe', 'speak', ofDays(D.index, 21, 99)],
    ['Einstieg', 'Podcast, Lektionen 1–33', 'listen', ofDays(D.podIndex, 1, 33)],
    ['Aufbau', 'Podcast, Lektionen 34–66', 'listen', ofDays(D.podIndex, 34, 66)],
    ['Profi', 'Podcast, Lektionen 67–100', 'listen', ofDays(D.podIndex, 67, 100)],
  ], []);
  const themes = useMemo(() => themeStats(p).map((t) => ({ ...t, m: countMastery(t.ids, cards), seenShare: t.ids.length ? t.seen / t.ids.length : 0 }))
    .sort((a, b) => a.share - b.share || a.seenShare - b.seenShare), [cards]);
  const selCol = cols[col];
  const sm = countMastery(selCol[3], cards);
  return html`<div class="screen">
    <${TopBar} title="Wortschatz" sub=${`${allIds.length - all.fresh} von ${allIds.length} Sätzen geübt`} onBack=${() => back('ich')} />
    <div class="scroll"><div class="stats">
      <section class="card p vocab-top">
        <${Donut} size=${140} stroke=${15} segments=${[...MASTERY.map((x) => ({ v: all[x.key], color: x.color })), { v: all.fresh, color: 'transparent' }]}>
          <b class="num" style="font-size:28px;line-height:1">${all.sits + all.active}</b><span class="sub" style="font-size:11.5px">sitzen sicher</span><//>
        <div class="stack g10 grow">${MASTERY.map((x) => html`<div class="legend-row"><i style=${`background:${x.color}`}></i><span class="grow">${x.label}</span><b class="num">${all[x.key]}</b></div>`)}
          <div class="legend-row"><i style="background:var(--border2)"></i><span class="grow">Noch neu</span><b class="num">${all.fresh}</b></div></div>
      </section>

      <section class="block"><div class="block-head"><h2>Nach Kursabschnitt</h2></div>
        <div class="card p">
          <div class="ladder">${cols.map(([name, , a, ids], i) => {
            const m = countMastery(ids, cards);
            const n = ids.length || 1;
            return html`${i === 3 ? html`<span class="ladder-gap"></span>` : null}<button class=${cls('lcol', i === col && 'on')} onClick=${() => setCol(i)}>
              <span class="lbar">${MASTERY.slice().reverse().map((x) => html`<i style=${`height:${(m[x.key] / n) * 100}%;background:${x.color}`}></i>`)}</span>
              <span class="lpct num">${Math.round(((m.sits + m.active) / n) * 100)}%</span>
              <span class="lname" style=${`color:${AREA_COLOR[a]}`}>${name}</span></button>`;
          })}</div>
          <div class="ladder-detail"><b>${selCol[1]}</b>
            <div class="sub mt4">${sm.sits + sm.active} von ${selCol[3].length} sitzen · ${sm.shaky} wackelig · ${sm.fresh} noch neu</div></div>
        </div></section>

      <section class="block"><div class="block-head"><h2>Nach Thema</h2><span class="sub">schwächste zuerst</span></div>
        <div class="tgrid">${themes.map((t) => html`<button class="tcell" onClick=${() => setTheme(t)}>
          <span class="tfill" style=${`width:${t.share * 100}%`}></span>
          <b>${t.title}</b><span class="num">${t.sits} von ${t.ids.length}</span></button>`)}</div></section>
      <div class="end"></div>
    </div></div>
    ${theme && html`<${Sheet} onClose=${() => setTheme(null)} title=${theme.title}>
      <div class="mbar mt12" style="height:10px">${MASTERY.map((x) => (theme.m[x.key] ? html`<i style=${`flex:${theme.m[x.key]};background:${x.color}`}></i>` : null))}${theme.m.fresh ? html`<i style=${`flex:${theme.m.fresh};background:var(--border2)`}></i>` : null}</div>
      <div class="rowx mt12" style="justify-content:space-between">${[...MASTERY, { key: 'fresh', label: 'Neu', color: 'var(--border2)' }].map((x) => html`<div><b class="num" style="font-size:18px">${theme.m[x.key]}</b><div class="sub"><i style=${`display:inline-block;width:8px;height:8px;border-radius:50%;margin-right:5px;background:${x.color}`}></i>${x.label}</div></div>`)}</div>
      <div class="stack g8 mt24">
        <button class="btn" onClick=${() => { testSelection.ids = theme.ids; nav('test'); }}><${Icon} n="quiz" />${Math.min(15, theme.ids.length)} Sätze testen</button>
        <button class="btn ghost" onClick=${() => { phrasebookPreset.theme = theme.id; nav('satzbuch', { replace: true }); }}><${Icon} n="menu_book" />Im Satzbuch ansehen</button></div><//>`}
  </div>`;
}

// ── Unterseite: Lernzeit ───────────────────────────────────────────────────

export function StatsTime() {
  useTicker();
  const today = progress.today();
  const totals = timeTrack.totals();
  const total = Object.values(totals).reduce((a, b) => a + b, 0);
  const byArea = areaTotals(totals);
  const perDay = Array.from({ length: 14 }, (_, i) => daySeconds(today - i)).reduce((a, b) => a + b, 0) / 14;
  const years = perDay > 0 ? (FSI_HOURS * 3600 - total) / perDay / 365 : null;
  const pace = years == null ? null : years > 1.5 ? `rund ${Math.round(years)} Jahre` : `rund ${Math.max(1, Math.round(years * 12))} Monate`;
  const groups = Object.keys(AREA_COLOR).map((a) => ({ a, rows: Object.entries(MODES).filter(([, [, ar]]) => ar === a).map(([k, [name]]) => ({ k, name, s: totals[k] ?? 0 })).filter((r) => r.s > 0).sort((x, y) => y.s - x.s) })).filter((g) => g.rows.length);
  const maxRow = Math.max(1, ...groups.flatMap((g) => g.rows.map((r) => r.s)));
  return html`<div class="screen">
    <${TopBar} title="Lernzeit" sub="Nur aktive Zeit, ohne Pausen" onBack=${() => back('ich')} />
    <div class="scroll"><div class="stats">
      ${!total ? html`<div class="card p center" style="padding:36px 20px"><${Icon} n="schedule" size="44" c="faint" />
          <div class="h2 mt12">Ab jetzt zählt jede Minute</div><p class="sub mt8">Starte eine Lektion, eine Übung oder eine Podcast-Folge. Hier siehst du dann, wohin deine Lernzeit geht.</p>
          <button class="btn mt20" onClick=${() => nav('heute', { replace: true })}>Zum Tagesprogramm</button></div>`
        : html`<section class="card p vocab-top">
          <${Donut} size=${140} stroke=${15} segments=${Object.keys(AREA_COLOR).map((a) => ({ v: byArea[a] ?? 0, color: AREA_COLOR[a] }))}>
            <b class="num" style="font-size:22px;line-height:1.05">${hours(total)}</b><span class="sub" style="font-size:11.5px">gelernt</span><//>
          <div class="stack g10 grow">${Object.keys(AREA_COLOR).filter((a) => byArea[a]).sort((x, y) => byArea[y] - byArea[x]).map((a) => html`<div class="legend-row"><i style=${`background:${AREA_COLOR[a]}`}></i><span class="grow">${AREA_NAME[a]}</span><b class="num">${Math.round((byArea[a] / total) * 100)}%</b></div>`)}</div>
        </section>
        <section class="block"><div class="block-head"><h2>Der lange Weg</h2></div>
          <div class="card p">
            <div class="mtrack"><i style=${`width:${Math.max(0.8, Math.min(100, (total / 3600 / FSI_HOURS) * 100))}%`}></i></div>
            <div class="rowx mt8" style="justify-content:space-between"><b class="num">${hours(total)}</b><span class="sub num">ca. ${FSI_HOURS.toLocaleString('de-DE')} Std.</span></div>
            ${pace && html`<p class="mt12" style="font-size:15px;line-height:1.5">In deinem Tempo der letzten zwei Wochen, <b>${fmtDuration(perDay)} pro Tag</b>, wären es noch <b>${pace}</b>.</p>`}
            <p class="faint mt8" style="font-size:12px;line-height:1.5">Das US-Außenministerium rechnet für Russisch mit etwa ${FSI_HOURS.toLocaleString('de-DE')} Unterrichtsstunden, bis man im Beruf sicher spricht. Ein grober Richtwert, keine Vorhersage für dich.</p>
          </div></section>
        <section class="block"><div class="block-head"><h2>Nach Modus</h2></div>
          <div class="card p stack g20">${groups.map((g) => html`<div>
            <div class="rowx g8" style="margin-bottom:10px"><i style=${`width:10px;height:10px;border-radius:50%;background:${AREA_COLOR[g.a]}`}></i><b style="font-size:14px">${AREA_NAME[g.a]}</b><span class="grow"></span><span class="sub num">${clock(byArea[g.a])}</span></div>
            <div class="stack g8">${g.rows.map((r) => html`<div class="mrow"><span class="grow">${r.name}</span><span class="mbarline"><i style=${`width:${(r.s / maxRow) * 100}%;background:${AREA_COLOR[g.a]}`}></i></span><span class="num mtime">${clock(r.s)}</span></div>`)}</div></div>`)}</div></section>`}
      <div class="end"></div>
    </div></div>
  </div>`;
}

// ── Unterseite: Erfolge ────────────────────────────────────────────────────

export function StatsWins() {
  const p = useProgress();
  useTicker();
  const today = progress.today();
  const lv = levelOf(p.xp);
  const streak = P.visibleStreak(p, today);
  const monday = today - ((dateOf(today).getUTCDay() + 6) % 7);
  const cells = Array.from({ length: 35 }, (_, i) => monday - 28 + i);
  const heat = (d) => { const x = P.xpOn(p, d); const t = daySeconds(d); if (!x && !t) return 0; const r = Math.max(x / prefs.dailyGoal(), t / 60 / prefs.dailyMinutes()); return r >= 1 ? 3 : r >= 0.5 ? 2 : 1; };
  const lab = Object.values(p.labBest);
  const liveAnswers = storage.getJSON('conv_progress')?.live?.answers ?? 0;
  const courses = [
    ['speak', 'Jahreskurs', D.plan.doneCount(p, planState.get()), 365, 'plan'],
    ['listen', 'Podcast', Object.keys(p.podcastDayStars).length, D.podIndex.days.length, 'podcast'],
    ['words', 'Satzbau', p.patternsDone.length, D.patterns.length, 'patterns'],
    ['amber', 'Mit Anja', Math.min(4, journeyDone()), 4, 'anja'],
  ];
  const records = [
    ['hearing', 'listen', 'Hör-Blitz', p.bestBlitz, 'Punkte', 'blitz'],
    ['bolt', 'speak', 'Sprech-Reflex', p.bestReflex, 'Punkte', 'reflex'],
    ['extension', 'words', 'Kombi-Meister', p.bestKombi, 'Punkte', 'kombi'],
    ['join_inner', 'words', 'Paare finden', p.bestPairsSeconds, 'Sekunden', 'pairs'],
    ['graphic_eq', 'listen', 'Lautlabor', lab.length ? Math.max(...lab) : 0, 'Prozent', 'sounds'],
    ['graphic_eq', 'amber', 'Anja live', liveAnswers, liveAnswers === 1 ? 'gesprochene Antwort' : 'gesprochene Antworten', 'anja/live'],
    ['video_library', 'listen', 'Lieder & Szenen', piecesDone(), piecesDone() === 1 ? 'Stück durchgearbeitet' : 'Stücke durchgearbeitet', 'erleben'],
  ];
  return html`<div class="screen">
    <${TopBar} title="Erfolge" onBack=${() => back('ich')} />
    <div class="scroll"><div class="stats">
      <section class="card p rowx g16">
        <${Ring} value=${lv.into / lv.need} size=${76} stroke=${7}><span class="faint" style="font-size:10px">Stufe</span><b class="num" style="font-size:22px;line-height:1">${lv.lvl}</b><//>
        <div class="grow"><b class="num" style="font-size:22px">${p.xp} XP</b><div class="sub">Noch ${lv.next - p.xp} XP bis Stufe ${lv.lvl + 1}</div></div>
      </section>
      <section class="block"><div class="block-head"><h2>Serie</h2><span class="sub">Rekord: ${p.bestStreak} Tage</span></div>
        <div class="card p rowx g20" style="align-items:center">
          <div class="center" style="flex:none"><${Icon} n="local_fire_department" fill c=${streak ? 'amber' : 'faint'} size="34" /><div class="num" style="font-size:26px;font-weight:700;line-height:1.1">${streak}</div><div class="sub">${streak === 1 ? 'Tag' : 'Tage'}</div></div>
          <div class="grow"><div class="heat">${['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'].map((w) => html`<span class="hd">${w}</span>`)}
            ${cells.map((d) => html`<span class=${cls('cell', `l${d > today ? 'f' : heat(d)}`, d === today && 'today')}></span>`)}</div></div>
        </div></section>
      <section class="block"><div class="block-head"><h2>Kurse</h2></div>
        <div class="crings">${courses.map(([a, name, done, tot, to]) => html`<button onClick=${() => nav(to)}>
          <${Ring} value=${done / tot} size=${60} stroke=${6} color=${AREA_COLOR[a]}><b class="num" style="font-size:13px">${Math.round((done / tot) * 100)}%</b><//>
          <b>${name}</b><span class="num">${done} von ${tot}</span></button>`)}</div></section>
      <section class="block"><div class="block-head"><h2>Rekorde</h2></div>
        <div class="trophies">${records.map(([icon, a, name, v, unit, to]) => html`<button class=${cls('trophy', !v && 'empty')} onClick=${() => nav(to)}>
          <span class="ti" style=${`color:${AREA_COLOR[a]};background:color-mix(in srgb, ${AREA_COLOR[a]} 16%, transparent)`}><${Icon} n=${icon} /></span>
          <span class="grow"><b>${name}</b><span>${v ? `${v} ${unit}` : 'Noch kein Rekord'}</span></span>
          ${v ? html`<${Icon} n="trophy" fill c="amber" size="20" />` : html`<${Icon} n="play_arrow" c="faint" size="20" />`}</button>`)}</div></section>
      <div class="end"></div>
    </div></div>
  </div>`;
}
