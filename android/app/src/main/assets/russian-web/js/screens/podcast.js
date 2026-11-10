// Podcast-Kurs: Lektionsliste, Lektion mit Player, Erinnerung. Der Player lebt
// außerhalb der Seiten weiter (wie RussianPodcastPlayer in der App): Audio vom
// Hikari-Server, Hörstand alle 3 s gesichert, 90 % gehört = Übungen frei.
import { html, cls, Icon, TopBar, IL, Sheet, Stars, Ring, Speaker, useState, useEffect, useRef, audio } from '../ui.js';
import { D, P, useProgress, progress, nav, back } from '../app.js';
import { storage, api, app, nativeOn } from '../platform.js';
import { podcastResumeDay, podcastSpot } from './home.js';
import { timeTrack } from '../timetrack.js';

// ── Player ─────────────────────────────────────────────────────────────────

const el = new Audio();
el.preload = 'auto';
let st = { day: null, title: '', videoId: '', playing: false, loading: false, pos: 0, dur: 0, speed: 1, resumedFrom: null, error: false };
const subs = new Set();
let lastSave = 0;
let retries = 0;
const set = (patch) => { st = { ...st, ...patch }; timeTrack.setPodcastPlaying(st.playing); for (const fn of [...subs]) fn(st); };
const cover = (id) => `https://i.ytimg.com/vi/${id}/hqdefault.jpg`;
const fin = (n) => (Number.isFinite(n) ? n : 0);

function save() {
  if (st.day == null || (st.pos < 1000 && st.dur <= 0)) return;
  storage.set(`podcast_pos_${st.day}`, String(Math.round(st.pos)));
  if (st.dur > 0) storage.set(`podcast_dur_${st.day}`, String(Math.round(st.dur)));
  if (P.isListened(st.pos, st.dur)) player.markListened(st.day);
}

const webPlayer = {
  get: () => st,
  subscribe(fn) { subs.add(fn); return () => subs.delete(fn); },
  load(day, { autoplay = false } = {}) {
    const lesson = D.podIndex.day(day);
    const videoId = lesson?.media?.videoId;
    if (!videoId) return;
    if (st.day === day && !st.error) { if (autoplay) this.play(); return; }
    save();
    const spot = podcastSpot(day);
    const start = P.resumeStartMs(spot.pos, spot.dur);
    storage.set('podcast_last_day', String(day));
    retries = 0;
    set({ day, videoId, title: `Lektion ${day} · ${lesson.title}`, playing: false, loading: true, pos: start, dur: spot.dur, resumedFrom: start > 0 ? start : null, error: false,
      speed: Number(storage.get('podcast_speed') ?? 1) });
    el.src = api.mediaUrl(`/music/audio/${videoId}`);
    el.playbackRate = st.speed;
    if (start > 0) el.currentTime = start / 1000;
    // Der Server lädt diese und die nächste Folge auf Platte — danach hängt nichts mehr an YouTube.
    const nextId = D.podIndex.day(day + 1)?.media?.videoId;
    for (const id of [videoId, nextId]) if (id) fetch(api.mediaUrl(`/music/download/${id}`), { method: 'POST', headers: { 'content-type': 'application/json' }, body: '{}' }).catch(() => {});
    if ('mediaSession' in navigator) {
      navigator.mediaSession.metadata = new MediaMetadata({ title: st.title, artist: 'RusslandJournal', album: 'Hikari · Podcast-Kurs', artwork: [{ src: cover(videoId), sizes: '480x360', type: 'image/jpeg' }] });
    }
    if (autoplay) this.play();
  },
  play() {
    if (st.day == null) return;
    audio.stop();
    if (st.error) { const d = st.day; set({ day: null }); this.load(d, { autoplay: true }); return; }
    if (st.dur > 0 && st.pos >= st.dur - 500) el.currentTime = 0;
    el.play().catch(() => {});
  },
  pause() { el.pause(); },
  toggle() { if (st.playing) this.pause(); else this.play(); },
  seekTo(ms) {
    const to = Math.max(0, st.dur > 0 ? Math.min(ms, st.dur - 500) : ms);
    el.currentTime = to / 1000;
    set({ pos: to, resumedFrom: null });
    save();
  },
  skipBy(ms) { this.seekTo(st.pos + ms); },
  cycleSpeed() { const speed = P.nextSpeed(st.speed); el.playbackRate = speed; storage.set('podcast_speed', String(speed)); set({ speed }); },
  markListened(day) { if (!progress.get().podcastListened.includes(day)) progress.update((p) => ({ ...p, podcastListened: [...p.podcastListened, day] })); },
  close() { save(); el.pause(); el.removeAttribute('src'); el.load(); set({ day: null, playing: false, loading: false, pos: 0, dur: 0, error: false, resumedFrom: null }); },
};

el.addEventListener('playing', () => { retries = 0; set({ playing: true, loading: false, error: false }); });
el.addEventListener('pause', () => { set({ playing: false }); save(); });
el.addEventListener('waiting', () => set({ loading: true }));
el.addEventListener('canplay', () => set({ loading: false }));
el.addEventListener('durationchange', () => { if (fin(el.duration) > 0) set({ dur: el.duration * 1000 }); });
el.addEventListener('timeupdate', () => {
  if (st.day == null) return;
  set({ pos: el.currentTime * 1000 });
  if (Date.now() - lastSave > 3000) { lastSave = Date.now(); save(); }
});
el.addEventListener('ended', () => { set({ playing: false }); if (st.day != null) player.markListened(st.day); save(); });
el.addEventListener('error', () => {
  if (st.day == null || !el.getAttribute('src')) return;
  if (retries < 4) {
    retries++;
    const at = st.pos;
    const wasPlaying = st.playing || st.loading;
    set({ loading: true });
    setTimeout(() => { if (st.day == null) return; el.src = api.mediaUrl(`/music/audio/${st.videoId}`); el.playbackRate = st.speed; el.currentTime = at / 1000; if (wasPlaying) el.play().catch(() => {}); }, 2000 * retries);
  } else set({ error: true, loading: false, playing: false });
});
audio.onState((c) => { if (c && st.playing) player.pause(); }); // Lernphrase anhören pausiert die Folge
if ('mediaSession' in navigator) {
  const ms = navigator.mediaSession;
  try {
    ms.setActionHandler('play', () => player.play()); ms.setActionHandler('pause', () => player.pause());
    ms.setActionHandler('seekbackward', () => player.skipBy(-5000)); ms.setActionHandler('seekforward', () => player.skipBy(10000));
    ms.setActionHandler('seekto', (e) => player.seekTo(e.seekTime * 1000));
  } catch { /* ältere Browser */ }
}
window.addEventListener('pagehide', save);

// ── In der App: der eingebaute Player (läuft im Hintergrund, Sperrbildschirm, Kopfhörertasten) ──
const N = app?.podcastLoad ? app : null;
let skipDelta = false;
const fromNative = (n) => ({ day: n.day ?? null, title: n.day != null ? `Lektion ${n.day} · ${n.title}` : '', videoId: n.videoId ?? '', playing: !!n.playing, loading: !!n.loading,
  pos: n.positionMs ?? 0, dur: n.durationMs ?? 0, speed: n.speed ?? 1, resumedFrom: n.resumedFromMs ?? null, error: !!n.error });
const nativePlayer = {
  get: () => st,
  subscribe(fn) { subs.add(fn); return () => subs.delete(fn); },
  load(day, { autoplay = false } = {}) {
    const lesson = D.podIndex.day(day);
    if (!lesson?.media?.videoId) return;
    skipDelta = true;
    N.podcastLoad(day, lesson.media.videoId, lesson.title, autoplay);
  },
  play() { audio.stop(); N.podcastPlay(); },
  pause() { N.podcastPause(); },
  toggle() { if (!st.playing) audio.stop(); N.podcastToggle(); },
  seekTo(ms) { skipDelta = true; N.podcastSeek(Math.max(0, Math.round(ms))); },
  skipBy(ms) { this.seekTo(st.pos + ms); },
  cycleSpeed() { N.podcastCycleSpeed(); },
  markListened(day) { N.podcastMarkListened(day); },
  close() { N.podcastClose(); },
};
if (N) {
  try { st = fromNative(JSON.parse(N.podcastState())); } catch { /* Player noch leer */ }
  nativeOn('podcast', (n) => {
    const next = fromNative(n);
    // Gehörte Zeit aus dem Fortschritt der Position: bei 2× Tempo zählt die halbe Strecke.
    if (next.day != null && st.day === next.day && !skipDelta) {
      const d = next.pos - st.pos;
      if (d > 0 && d < 3 * 3600 * 1000 && (st.playing || next.playing)) timeTrack.addPodcast(d / 1000 / (st.speed || 1));
    }
    skipDelta = false;
    if (next.day != null) {
      storage.setLocal('podcast_last_day', String(next.day));
      if (next.pos >= 1000 || next.dur > 0) { storage.setLocal(`podcast_pos_${next.day}`, String(Math.round(next.pos))); storage.setLocal(`podcast_dur_${next.day}`, String(Math.round(next.dur))); }
    }
    st = next;
    for (const fn of [...subs]) fn(st);
  });
}
export const player = N ? nativePlayer : webPlayer;

export function usePlayer() {
  const [s, setS] = useState(st);
  useEffect(() => { setS(st); return player.subscribe(setS); }, []);
  return s;
}

// ── Player-Oberfläche ──────────────────────────────────────────────────────

function Seek({ s }) {
  const [drag, setDrag] = useState(null);
  const tr = useRef(null);
  const ratio = (e) => { const r = tr.current.getBoundingClientRect(); return Math.min(1, Math.max(0, (e.clientX - r.left) / r.width)); };
  const down = (e) => { if (!(s.dur > 0)) return; e.currentTarget.setPointerCapture(e.pointerId); setDrag(ratio(e)); };
  const move = (e) => { if (drag != null) setDrag(ratio(e)); };
  const up = () => { if (drag != null) { player.seekTo(drag * s.dur); setDrag(null); } };
  const frac = drag ?? (s.dur > 0 ? s.pos / s.dur : 0);
  const diff = drag != null ? drag * s.dur - s.pos : 0;
  return html`<div class="seek" onPointerDown=${down} onPointerMove=${move} onPointerUp=${up} onPointerCancel=${() => setDrag(null)}>
    <div class="tr" ref=${tr}><i style=${`width:${frac * 100}%`}></i><b style=${`left:${frac * 100}%`}></b>
      ${drag != null && html`<span style=${`position:absolute;bottom:18px;left:${frac * 100}%;transform:translateX(-50%);background:#fff;color:#111;border-radius:9px;padding:4px 9px;font-size:12.5px;font-weight:600;white-space:nowrap`}>
        ${P.formatTime(drag * s.dur)} <span style="opacity:.55">${diff >= 0 ? '+' : '−'}${P.formatTime(Math.abs(diff))}</span></span>`}</div></div>`;
}

function PlayerFull({ s, onPhrases }) {
  const [remaining, setRemaining] = useState(false);
  const [flash, setFlash] = useState(null);
  const last = useRef(0);
  const [hideResume, setHideResume] = useState(false);
  useEffect(() => { setHideResume(false); const t = setTimeout(() => setHideResume(true), 6000); return () => clearTimeout(t); }, [s.day, s.resumedFrom]);
  const tap = (e) => {
    if (s.error) { player.play(); return; }
    const r = e.currentTarget.getBoundingClientRect();
    const x = (e.clientX - r.left) / r.width;
    const dbl = Date.now() - last.current < 300;
    last.current = Date.now();
    if (dbl && x < 0.38) { player.skipBy(-10000); setFlash('replay_10'); } else if (dbl && x > 0.62) { player.skipBy(10000); setFlash('forward_10'); } else { player.toggle(); setFlash(s.playing ? 'pause' : 'play_arrow'); }
    setTimeout(() => setFlash(null), 450);
  };
  return html`<div class="pad c-listen">
    <button class="cover" style=${`background-image:url(${cover(s.videoId)})`} onClick=${tap} aria-label="Abspielen oder Pause">
      <div style="position:absolute;inset:0;background:linear-gradient(transparent 55%,rgba(0,0,0,.6));display:flex;align-items:center;justify-content:center">
        ${s.loading && !s.error && html`<${Icon} n="progress_activity" size="44" c="spin" />`}
        ${flash && html`<div style="width:72px;height:72px;border-radius:50%;background:rgba(0,0,0,.55);display:flex;align-items:center;justify-content:center;animation:pop .3s"><${Icon} n=${flash} fill size="40" /></div>`}
        ${s.error && html`<div style="background:rgba(0,0,0,.7);padding:14px 18px;border-radius:14px;text-align:center"><b>Folge lädt gerade nicht</b><div class="sub">Tippen für einen neuen Versuch</div></div>`}
      </div>
      ${s.resumedFrom && !hideResume && html`<div style="position:absolute;left:10px;bottom:10px;display:flex;gap:8px;align-items:center">
        <span class="tag solid c-listen">Weiter bei ${P.formatTime(s.resumedFrom)}</span>
        <span class="tag" style="background:rgba(0,0,0,.6);color:#fff" onClick=${(e) => { e.stopPropagation(); player.seekTo(0); player.play(); }}>Von vorn</span></div>`}
    </button>
    <${Seek} s=${s} />
    <div class="times"><span>${P.formatTime(s.pos)}</span><button class="num" onClick=${() => setRemaining(!remaining)}>${s.dur > 0 ? (remaining ? `−${P.formatTime(s.dur - s.pos)}` : P.formatTime(s.dur)) : '–:––'}</button></div>
    <div class="ctrl">
      <button class=${cls('chip sm', s.speed !== 1 && 'on c c-amber')} style="min-width:62px;justify-content:center" onClick=${() => player.cycleSpeed()}>${P.speedLabel(s.speed)}</button>
      <button class="iconbtn" onClick=${() => player.skipBy(-5000)} aria-label="5 Sekunden zurück"><${Icon} n="replay_5" size="30" /></button>
      <button class="pp" onClick=${() => player.toggle()} aria-label=${s.playing ? 'Pause' : 'Abspielen'}><${Icon} n=${s.playing ? 'pause' : 'play_arrow'} fill /></button>
      <button class="iconbtn" onClick=${() => player.skipBy(10000)} aria-label="10 Sekunden vor"><${Icon} n="forward_10" size="30" /></button>
      <button class="iconbtn" style="width:62px" onClick=${onPhrases} aria-label="Zu den Lernphrasen"><${Icon} n="translate" /></button>
    </div>
  </div>`;
}

export function MiniPlayer({ onOpen }) {
  const s = usePlayer();
  if (s.day == null) return null;
  return html`<div class="mini c-listen">
    <button class="rowx g12 grow" onClick=${onOpen ?? (() => nav(`podcast/${s.day}`))}>
      <div class="cov" style=${`width:54px;height:34px;border-radius:7px;background:#000 center/cover url(${cover(s.videoId)})`}></div>
      <div class="grow"><b class="ell" style="display:block;font-size:13.5px;font-weight:500">${s.title}</b><span class="sub num" style="font-size:12px">${P.formatTime(s.pos)} / ${s.dur > 0 ? P.formatTime(s.dur) : '–:––'}</span></div></button>
    <button class="iconbtn" onClick=${() => player.toggle()} aria-label=${s.playing ? 'Pause' : 'Abspielen'}><${Icon} n=${s.playing ? 'pause' : 'play_arrow'} fill /></button>
    <button class="iconbtn" onClick=${() => player.close()} aria-label="Schließen"><${Icon} n="close" /></button>
    <div class="mp" style=${`width:${s.dur > 0 ? (s.pos / s.dur) * 100 : 0}%`}></div>
  </div>`;
}

// ── Seiten ─────────────────────────────────────────────────────────────────

function ReminderSheet({ onClose }) {
  const initial = app?.reminderGet ? JSON.parse(app.reminderGet()) : { enabled: storage.get('reminder_enabled') === '1', minutes: Number(storage.get('reminder_minutes') ?? 1080) };
  const [on, setOn] = useState(!!initial.enabled);
  const [min, setMin] = useState(initial.minutes ?? 1080);
  const hhmm = `${String(Math.floor(min / 60)).padStart(2, '0')}:${String(min % 60).padStart(2, '0')}`;
  const save = () => {
    if (app?.reminderSet) app.reminderSet(on, min);
    else { storage.set('reminder_enabled', on ? '1' : '0'); storage.set('reminder_minutes', String(min)); }
    onClose();
  };
  return html`<${Sheet} onClose=${onClose} title="Tägliche Erinnerung">
    <p class="sub">An die nächste Lektion erinnern. Die Erinnerung entfällt, wenn du deine Lektion heute schon abgeschlossen hast.</p>
    <div class="q">Erinnerung</div>
    <div class="seg"><button class=${on ? 'on' : ''} onClick=${() => setOn(true)}>An</button><button class=${!on ? 'on' : ''} onClick=${() => setOn(false)}>Aus</button></div>
    <div class="q">Uhrzeit</div>
    <input class="field" type="time" value=${hhmm} onInput=${(e) => { const [h, m] = e.target.value.split(':').map(Number); if (Number.isFinite(h)) setMin(h * 60 + (m || 0)); }} />
    ${!app?.reminderSet && html`<p class="faint mt12" style="font-size:12px">Die Benachrichtigung kommt von der Hikari-App auf dein Handy.</p>`}
    <button class="btn mt20" onClick=${save}>Speichern</button><//>`;
}

export function PodcastHome() {
  const p = useProgress();
  const s = usePlayer();
  const [reminder, setReminder] = useState(false);
  const total = D.podIndex.days.length;
  const unlocked = P.unlockedPodcastDay(p, total);
  const done = Object.keys(p.podcastDayStars).length;
  const resume = podcastResumeDay(p);
  const heroDay = resume ?? unlocked;
  const hero = D.podIndex.day(heroDay);
  const allDone = done >= total && resume == null;
  const spot = s.day === heroDay ? { pos: s.pos, dur: s.dur } : podcastSpot(heroDay);
  const live = s.day === heroDay && s.playing;
  const start = () => { if (!live) player.load(heroDay, { autoplay: true }); nav(`podcast/${heroDay}`); };

  return html`<div class="screen c-listen">
    <${TopBar} title="Podcast-Kurs" sub=${`${total} Lektionen · RusslandJournal`} onBack=${() => back('kurse')}
      right=${html`<button class="iconbtn" onClick=${() => setReminder(true)} aria-label="Erinnerung"><${Icon} n="notifications" /></button>`} />
    <div class="scroll">
      <div class="pad"><div class="card tint" style="overflow:hidden;border-radius:22px">
        ${hero?.media?.videoId && html`<div class="cover" style=${`border-radius:0;background-image:url(${cover(hero.media.videoId)})`}><div style="position:absolute;inset:0;background:linear-gradient(transparent 55%,var(--card))"></div></div>`}
        <div style="padding:14px 16px 16px;position:relative">
          <div class="over c">${allDone ? 'Kurs abgeschlossen' : resume ? 'Weiterhören' : 'Deine nächste Lektion'}</div>
          <div class="title mt4">${allDone ? `Alle ${total} Lektionen geschafft` : `Lektion ${heroDay} · ${hero.title}`}</div>
          <div class="sub mt4">Hören · verstehen · direkt anwenden</div>
          ${resume && spot.dur > 0 && html`<div class="rowx g10 mt12 num" style="font-size:12px"><span>${P.formatTime(spot.pos)}</span><div class="pbar grow"><i style=${`width:${(spot.pos / spot.dur) * 100}%`}></i></div><span class="muted">${P.formatTime(spot.dur)}</span></div>`}
          ${!allDone && html`<button class="btn mt16" onClick=${start}><${Icon} n=${live ? 'graphic_eq' : 'play_arrow'} fill />${live ? 'Zur laufenden Folge' : resume ? `Weiterhören ab ${P.formatTime(P.resumeStartMs(spot.pos, spot.dur))}` : p.podcastListened.includes(heroDay) ? `Lektion ${heroDay} öffnen` : `Lektion ${heroDay} starten`}</button>`}
          <div class="rowx mt16" style="justify-content:space-between;font-size:12.5px"><span class="muted">Fortschritt</span><b class="num">${done}/${total}</b></div>
          <div class="pbar mt4"><i style=${`width:${(done / total) * 100}%`}></i></div>
        </div></div></div>

      <div class="sec"><div class="h2">Dein Plan</div><div class="sub">Die nächste Lektion wird frei, sobald du die Übungen zur aktuellen abschließt.</div>
        <div class="list">${D.podIndex.days.map((d) => {
          const stars = p.podcastDayStars[d.day] ?? 0;
          const locked = d.day > unlocked;
          const listened = p.podcastListened.includes(d.day);
          const sp = s.day === d.day ? { pos: s.pos, dur: s.dur } : podcastSpot(d.day);
          const frac = sp.dur > 0 && !listened ? sp.pos / sp.dur : 0;
          return html`<button class=${cls('row', stars > 0 && 'done', d.day === heroDay && !allDone && 'now', locked && 'locked')} disabled=${locked} onClick=${() => nav(`podcast/${d.day}`)}>
            ${frac > 0 && !stars ? html`<${Ring} value=${frac} size=${34} stroke=${3} color="#fff"><span style="font-size:12px;font-weight:600">${d.day}</span><//>`
              : html`<div class="dn">${stars > 0 ? html`<${Icon} n="check" />` : locked ? html`<${Icon} n="lock" />` : d.day}</div>`}
            <div class="tx"><b class="ell">${d.title}</b>
              <span>${stars > 0 ? 'Abgeschlossen' : listened ? 'Gehört · Übungen offen' : sp.pos > 5000 && sp.dur > 0 ? `${P.formatTime(sp.pos)} von ${P.formatTime(sp.dur)}` : locked ? 'Noch gesperrt' : `${d.items.length} Lernphrasen`}</span></div>
            ${stars > 0 ? html`<${Stars} n=${stars} />` : d.day === heroDay && !allDone ? html`<${Icon} n="headphones" c="cc" />` : null}
          </button>`;
        })}</div>
        <p class="faint mt16" style="font-size:12px;padding:0 4px">Audio und Begleittexte: RusslandJournal.de. Die Lernphrasen und Übungen gehören zu Hikari.</p>
      </div>
      <div class="end"></div>
    </div>
    <${MiniPlayer} />
    ${reminder && html`<${ReminderSheet} onClose=${() => setReminder(false)} />`}
  </div>`;
}

export function PodcastLesson({ day }) {
  const p = useProgress();
  const s = usePlayer();
  const lesson = D.podIndex.day(day);
  const phrasesRef = useRef(null);
  useEffect(() => { if (lesson) player.load(day); }, [day]);
  if (!lesson) return html`<div class="screen"><${TopBar} title="Podcast-Kurs" onBack=${() => back('podcast')} /><div class="empty">Diese Lektion gibt es nicht.</div></div>`;
  const listened = p.podcastListened.includes(day);
  const stars = p.podcastDayStars[day] ?? 0;
  const phrases = D.podIndex.dayPhrases(lesson, P.settingsOf(p));
  const mine = s.day === day ? s : { ...s, day, videoId: lesson.media.videoId, pos: podcastSpot(day).pos, dur: podcastSpot(day).dur, playing: false };
  const practice = () => nav(`podcastPractice/${day}`);

  return html`<div class="screen c-listen">
    <${TopBar} title=${`Lektion ${day}`} sub="Podcast-Kurs" onBack=${() => back('podcast')} />
    <div class="scroll">
      <${PlayerFull} s=${mine} onPhrases=${() => phrasesRef.current?.scrollIntoView({ behavior: 'smooth' })} />
      <div class="pad mt20">
        <div class="title">${lesson.title}</div>
        <div class="rowx g8 mt8 wrap"><span class="sub">${mine.dur > 0 ? `${P.formatTime(mine.dur)} · ` : ''}RusslandJournal</span>
          ${listened && html`<span class="tag c-words"><${Icon} n="check" />Gehört</span>`}${stars > 0 && html`<${Stars} n=${stars} />`}</div>
        <p class="mt12" style="line-height:1.55;color:rgba(255,255,255,.82)">${lesson.media.summary}</p>
        ${listened ? html`<button class="btn area mt16" onClick=${practice}><${Icon} n="fitness_center" />${stars > 0 ? 'Übungen wiederholen' : 'Direkt zu den Übungen'}</button>`
          : html`<button class="btn mt16" disabled><${Icon} n="lock" />Übungen nach der Folge</button>
            <div class="sub center mt8">Schon gehört? <button style="color:rgb(var(--c));font-weight:500" onClick=${() => player.markListened(day)}>Als gehört markieren</button></div>`}
      </div>
      <div class="sec"><div class="over" style="margin:0 4px 8px">Wissen für diese Lektion</div>
        <div class="stack g8">${lesson.media.notes.map((n) => html`<div class="hint"><p>${n}</p></div>`)}</div>
        <a class="sub" style="display:inline-flex;gap:6px;align-items:center;margin:12px 4px 0;color:rgb(var(--c))" href=${lesson.media.sourceUrl} target="_blank" rel="noopener">
          <${Icon} n="open_in_new" size="16" />Originaltext auf RusslandJournal.de</a>
      </div>
      <div class="sec" ref=${phrasesRef}><div class="over" style="margin:0 4px 8px">Lernphrasen · ${phrases.length}</div>
        <div class="list">${phrases.map((ph) => html`<div class="row" style="align-items:flex-start"><div class="tx"><${IL} ru=${ph.ru} size=${17} /><span style="margin-top:4px">${ph.de}</span></div><${Speaker} phrase=${ph} /></div>`)}</div>
        ${listened && html`<button class="btn area mt16" onClick=${practice}>Übungen zu Lektion ${day}</button>`}
      </div>
      <div class="end"></div>
    </div>
  </div>`;
}
