// Bausteine der Oberfläche — überall gleich, damit sich jeder Modus gleich bedient.
import { html, useState, useEffect, useRef, useMemo, useCallback } from '../vendor/preact-htm.js';
import { audio, speech, recorder, decodePcm16k, api, fx, SPEECH_ERRORS, tts } from './platform.js';
import * as Ph from './domain/phonetics.js';
import * as Gl from './domain/glossary.js';
import * as Sp from './domain/speech.js';
import * as Pr from './domain/prosody.js';
import { phonemeHints } from './domain/tips.js';
import { otherForm } from './app.js';

export { html, useState, useEffect, useRef, useMemo, useCallback };
export const cls = (...a) => a.filter(Boolean).join(' ');
export const AREA = { speak: 'c-speak', listen: 'c-listen', review: 'c-review', words: 'c-words', amber: 'c-amber' };

export const Icon = ({ n, fill, size, c }) => html`<span class=${cls('ms', fill && 'fill', c)} style=${size ? `font-size:${size}px` : null} aria-hidden="true">${n}</span>`;

// ── Kopfzeilen ─────────────────────────────────────────────────────────────

export function TopBar({ title, sub, onBack, right, big }) {
  return html`<div class=${cls('top', big && 'big', big && onBack && 'hasback')}>
    ${onBack && html`<button class="iconbtn" onClick=${onBack} aria-label="Zurück"><${Icon} n="arrow_back" /></button>`}
    <div class="tt"><h1>${title}</h1>${sub && html`<div class="t2">${sub}</div>`}</div>
    ${right}
  </div>`;
}

/** Kopf jeder Übung: Schließen · Fortschritt in Bereichsfarbe · Zähler/Punkte. */
export function SessionBar({ value, count, onClose, right, hot }) {
  return html`<div class="sbar">
    <button class="iconbtn" onClick=${onClose} aria-label="Schließen"><${Icon} n="close" /></button>
    <div class="pbar"><i style=${`width:${Math.round(Math.min(1, Math.max(0, value)) * 100)}%`}></i></div>
    ${right ?? (count != null && html`<div class=${cls('cnt', hot && 'hot')}>${count}</div>`)}
  </div>`;
}

// ── Satzanzeige ────────────────────────────────────────────────────────────

const latin = (spans) => spans.map((s) => (s.stressed ? html`<u>${s.text}</u>` : s.text));

/** Umschrift groß (betonte Silbe amber), darunter Bedeutung je Wort, darunter dezent Kyrillisch. */
export function IL({ ru, size = 24, center = false, gloss = true, cyr = true }) {
  const chunks = useMemo(() => Gl.chunks(ru), [ru]);
  return html`<div class=${cls('il', center && 'center', !gloss && 'nog')} style=${`--fs:${size}px`}>
    <div class="il-ws">${chunks.map((c) => html`<div class="il-w">
      <div class="il-l">${c.words.map((w, i) => html`${i ? ' ' : ''}${w.before}${latin(w.latin)}${w.after}`)}</div>
      ${gloss && html`<div class="il-g">${c.gloss ?? ' '}</div>`}
    </div>`)}</div>
    ${cyr && html`<div class="cyr" lang="ru">${Ph.display(Gl.withStress(ru))}</div>`}
  </div>`;
}

export function PhraseBlock({ phrase, size = 26, showDe = true, gloss = showDe, center = true }) {
  return html`<div class=${cls('pb', center && 'center')}>
    <${IL} ru=${phrase.ru} size=${size} center=${center} gloss=${gloss} />
    ${showDe && html`<div class="de" style=${`font-size:${Math.max(14, Math.round(size * 0.62))}px`}>${phrase.de}</div>`}
  </div>`;
}

/** Nur die Umschrift eines einzelnen Worts/Texts (z. B. auf Kacheln). */
export const Latin = ({ ru }) => html`${latin(Ph.transliterate(Gl.withStress(ru)))}`;

/** Erklärtexte: kyrillische Passagen als Umschrift zeigen (wie mk() in der App). */
const CYR_RUN = /\+?[А-Яа-яЁё][А-Яа-яЁё+\-]*(?: \+?[А-Яа-яЁё][А-Яа-яЁё+\-]*)*/g;
export const mk = (text) => (text ?? '').replace(CYR_RUN, (m) => Ph.transliterateFlat(Gl.withStress(m)));

export function LetterLens({ ru }) {
  return html`<div class="lens">${Ph.letterPairs(ru).map((p) => (p ? html`<span lang="ru">${p[0]}<small>${p[1]}</small></span>` : html`<i class="sp"></i>`))}</div>`;
}

// ── Abspielen ──────────────────────────────────────────────────────────────

export function usePlaying() {
  const [cur, setCur] = useState(audio.current());
  useEffect(() => audio.onState(setCur), []);
  return cur;
}
export const playPhrase = (p, opts = {}) => audio.play(p.audio, { text: p.plain, ...opts });

/** Großer Abspielknopf + kleiner »Langsam«-Knopf. */
export function PlayRow({ phrase, size = 64, onPlay }) {
  const cur = usePlaying();
  const on = cur?.key === phrase.audio;
  const go = (slow) => { onPlay?.(); playPhrase(phrase, { slow }); };
  return html`<div class="play" style=${`--s:${size}px`}>
    <button class=${cls('pbtn', on && !cur.slow && 'on')} onClick=${() => go(false)} aria-label="Anhören"><${Icon} n="volume_up" fill /></button>
    <button class=${cls('pbtn slow', on && cur.slow && 'on')} onClick=${() => go(true)} aria-label="Langsam anhören"><${Icon} n="slow_motion_video" /></button>
  </div>`;
}

export function Speaker({ phrase, slow }) {
  const cur = usePlaying();
  return html`<button class=${cls('iconbtn spk', cur?.key === phrase.audio && 'on')} onClick=${(e) => { e.stopPropagation(); playPhrase(phrase, { slow }); }} aria-label="Anhören"><${Icon} n="volume_up" /></button>`;
}

// ── Auswahl ────────────────────────────────────────────────────────────────

export function Opt({ state, kb, onClick, children, disabled }) {
  return html`<button class=${cls('opt', state)} onClick=${onClick} disabled=${disabled}>
    ${kb && html`<span class="kb">${kb}</span>`}
    <div class="grow">${children}</div>
    ${state === 'ok' && html`<${Icon} n="check_circle" fill c="res" />`}
    ${state === 'bad' && html`<${Icon} n="cancel" fill c="res" />`}
  </button>`;
}

/** Tastatur am Rechner: 1–4 wählen, Enter weiter, Leertaste anhören. */
export function useKeys(map, deps = []) {
  useEffect(() => {
    const on = (e) => {
      if (e.target.matches?.('input,textarea') || e.metaKey || e.ctrlKey || e.altKey) return;
      const fn = map[e.key] ?? map[e.code];
      if (fn) { e.preventDefault(); fn(); }
    };
    window.addEventListener('keydown', on);
    return () => window.removeEventListener('keydown', on);
  }, deps);
}

// ── Blatt von unten ────────────────────────────────────────────────────────

export function Sheet({ onClose, children, title }) {
  return html`<div class="scrim" onClick=${onClose}></div>
    <div class="sheet" role="dialog"><div class="grab"></div>${title && html`<h4>${title}</h4>`}${children}</div>`;
}

export function Ring({ value, size = 64, stroke = 6, color = 'var(--amber)', children }) {
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  return html`<div class="ring" style=${`width:${size}px;height:${size}px`}>
    <svg width=${size} height=${size}><circle cx=${size / 2} cy=${size / 2} r=${r} stroke="rgba(255,255,255,.1)" stroke-width=${stroke} />
      <circle cx=${size / 2} cy=${size / 2} r=${r} stroke=${color} stroke-width=${stroke} stroke-dasharray=${c} stroke-dashoffset=${c * (1 - Math.min(1, Math.max(0, value)))} /></svg>
    <div class="in">${children}</div>
  </div>`;
}

export const Stars = ({ n, of = 3 }) => html`<span class="stars">${Array.from({ length: of }, (_, i) => html`<${Icon} n="star" c=${i < n ? 'on' : ''} />`)}</span>`;

export function Confetti() {
  const bits = useMemo(() => Array.from({ length: 36 }, (_, i) => ({
    left: Math.random() * 100, delay: Math.random() * 0.5, dur: 1.6 + Math.random() * 1.4,
    color: ['#FBBF24', '#F472B6', '#A78BFA', '#38BDF8', '#34D399', '#fff'][i % 6],
  })), []);
  return html`<div class="confetti">${bits.map((b) => html`<i style=${`left:${b.left}%;background:${b.color};animation-duration:${b.dur}s;animation-delay:${b.delay}s`}></i>`)}</div>`;
}

// ── Spracherkennung ────────────────────────────────────────────────────────

/**
 * Einmal zuhören. `onDone(hyps)` bekommt die Lesarten (leer = nichts verstanden).
 * → {listening, evaluating, partial, error, start(expect), stop(), cancel(), toggle(expect)}
 */
export function useListen(onDone) {
  const [st, setSt] = useState({ phase: 'idle', partial: '', error: null });
  const handle = useRef(null);
  const alive = useRef(true);
  const cb = useRef(onDone);
  cb.current = onDone;
  useEffect(() => () => { alive.current = false; handle.current?.abort?.(); }, []);
  const start = useCallback((expect = '') => {
    if (handle.current) return;
    setSt({ phase: 'listening', partial: '', error: null });
    const h = speech.listen({ expect, onPartial: (t) => alive.current && setSt((s) => (s.phase === 'idle' ? s : { ...s, partial: t })) });
    handle.current = h;
    h.promise.then((r) => {
      if (handle.current !== h) return;
      handle.current = null;
      if (!alive.current) return;
      // Nichts gehört ist kein Fehlversuch — nur ein Hinweis, kein Abzug, kein Fehlerton.
      if (r.ok) { setSt({ phase: 'idle', partial: '', error: null }); cb.current(r.hyps); }
      else setSt({ phase: 'idle', partial: '', error: r.error === 'aborted' ? null : r.error });
    });
  }, []);
  const stop = useCallback(() => { if (handle.current) { setSt((s) => ({ ...s, phase: 'evaluating' })); handle.current.stop(); } }, []);
  const cancel = useCallback(() => { const h = handle.current; handle.current = null; h?.abort?.(); setSt({ phase: 'idle', partial: '', error: null }); }, []);
  return {
    ...st, listening: st.phase === 'listening', evaluating: st.phase === 'evaluating',
    errorText: st.error ? SPEECH_ERRORS[st.error] ?? SPEECH_ERRORS.nospeech : null,
    start, stop, cancel, toggle: (expect) => (handle.current ? stop() : start(expect)),
  };
}

export function Mic({ L, onClick, disabled, small }) {
  return html`<button class=${cls('mic', small && 'sm', L.listening && 'on', L.evaluating && 'busy')} onClick=${onClick} disabled=${disabled || L.evaluating} aria-label=${L.listening ? 'Fertig' : 'Sprechen'}>
    <${Icon} n=${L.evaluating ? 'progress_activity' : L.listening ? 'graphic_eq' : 'mic'} fill=${!L.evaluating} />
  </button>`;
}

// ── Ergebnisse: kompakt, ein Blick ─────────────────────────────────────────

const tone = (v) => (v >= 75 ? 'var(--good)' : v >= 50 ? 'var(--amber)' : 'var(--bad)');

function ScorePill({ pct, ok, size = 40 }) {
  return html`<${Ring} value=${pct / 100} size=${size} stroke=${4} color=${ok ? 'var(--good)' : '#F59E0B'}><b class="num" style=${`font-size:${size * 0.3}px`}>${pct}</b><//>`;
}

/**
 * Auswertung der KI-Prüfung in einer Zeile: Prozent, Urteil, Wörter.
 * Nur wenn es nicht geklappt hat, kommen »Gehört« und ein gezielter Tipp dazu.
 */
export function ScoreCard({ sc, targetRu }) {
  const [more, setMore] = useState(false);
  const pct = Math.trunc(sc.score * 100);
  const nothing = !sc.heard.trim();
  const reads = useMemo(() => (targetRu ? Ph.words(Gl.withStress(Ph.fromDisplay(targetRu))) : null), [targetRu]);
  const hints = useMemo(() => (sc.passed ? [] : Sp.hints(sc)), [sc]);
  const wordLatin = (w, i) => {
    const r = reads && reads.length === sc.words.length ? reads[i] : null;
    return r ? r.latin.map((s) => s.text).join('') : Ph.transliterateFlat(Gl.withStress(w));
  };
  if (nothing) return html`<div class="res"><div class="res-row"><${Icon} n="hearing_disabled" c="faint" /><span class="muted">Nichts verstanden — nochmal, etwas lauter.</span></div></div>`;
  return html`<div class=${cls('res', sc.passed ? 'ok' : 'warn')}>
    <div class="res-row"><${ScorePill} pct=${pct} ok=${sc.passed} />
      <div class="grow"><b class="res-title">${sc.passed ? 'Klar verständlich' : 'Fast — einzelne Wörter fehlen'}</b>
        <div class="ws">${sc.words.map(([w, ok], i) => html`<span class=${ok ? 'ok' : ''}>${wordLatin(w, i)}</span>`)}</div></div></div>
    ${!sc.passed && html`<div class="res-more">
      <div class="sub">Gehört: »${Ph.transliterate(Gl.withStress(sc.heard)).map((s) => s.text).join('')}«</div>
      ${(more ? hints : hints.slice(0, 1)).map((h) => html`<div class="res-tip"><b>${h.heard ? `»${Sp.reading(h.target).toLowerCase()}« klang wie »${Ph.transliterateFlat(h.heard)}«` : `»${Sp.reading(h.target).toLowerCase()}« fehlte`}</b> ${h.tip}</div>`)}
      ${hints.length > 1 && html`<button class="res-link" onClick=${() => setMore(!more)}>${more ? 'Weniger' : `${hints.length - 1} weiterer Tipp${hints.length > 2 ? 's' : ''}`}</button>`}
    </div>`}
  </div>`;
}

// ── Eigene Aufnahme: Melodie, Rhythmus, Laute ──────────────────────────────

function prosodyAdvice(c, phon) {
  if (c && c.melody >= 75 && c.rhythm >= 70 && (!phon || phon >= 75)) return 'Sehr nah am Original — Melodie, Betonung und Laute sitzen.';
  if (c && c.melody < 50) return 'Die Melodie weicht ab: Folge der gelben Linie — wo sie steigt, geht deine Stimme hoch, am Satzende meist runter.';
  if (c && c.rhythm < 50) return 'Der Rhythmus weicht ab: Betone die lauten Silben stärker, sprich die schwachen kurz und leise.';
  if (phon != null && phon < 75) return 'Einzelne Laute weichen ab — tippe auf »Laute im Detail«, um zu sehen, welche.';
  if (c) return 'Schon ähnlich. Hör dir den Vergleich an und achte darauf, wo die Linien auseinandergehen.';
  return 'Hör dir den Vergleich an: erst du, dann das Original.';
}

/** Wertet eine Aufnahme aus: Melodie/Rhythmus lokal, Laute über den Hikari-Server. */
function useTake(clip, take) {
  const [pros, setPros] = useState('busy');
  const [phon, setPhon] = useState({ s: 'busy' });
  useEffect(() => {
    let alive = true;
    setPros('busy'); setPhon({ s: 'busy' });
    if (!audio.has(clip)) { setPros('none'); setPhon({ s: 'none' }); return undefined; }
    (async () => {
      try {
        const [a, b] = await Promise.all([decodePcm16k(audio.url(clip)), decodePcm16k(take)]);
        const c = Pr.compare(Pr.analyze(a), Pr.analyze(b));
        if (alive) setPros(c ?? 'fail');
      } catch { if (alive) setPros('fail'); }
    })();
    (async () => {
      for (let attempt = 0; attempt < 3; attempt++) {
        try { const data = await api.pronunciation(clip, take); if (alive) setPhon({ s: 'ok', data }); return; } catch (e) {
          const retry = !e.status || e.status === 500 || e.status === 502;
          if (!retry || attempt === 2) { if (alive) setPhon({ s: 'err', code: e.status ?? 0 }); return; }
          await new Promise((r) => setTimeout(r, 4000));
          if (!alive) return;
        }
      }
    })();
    return () => { alive = false; };
  }, [clip, take]);
  return { pros, phon };
}

function Metric({ label, value, busy, note }) {
  return html`<div class="metric">
    <b class="num" style=${value != null ? `color:${tone(value)}` : ''}>${busy ? html`<span class="dots"><i></i><i></i><i></i></span>` : value != null ? `${value}%` : '–'}</b>
    <span>${label}</span>${note && html`<em>${note}</em>`}
    <i class="mline"><i style=${`width:${value ?? 0}%;background:${value != null ? tone(value) : 'transparent'}`}></i></i></div>`;
}

function Curve({ orig, user }) {
  const all = [...orig, ...user];
  let lo = Math.min(...all);
  let hi = Math.max(...all);
  if (hi - lo < 2) { const m = (hi + lo) / 2; lo = m - 1; hi = m + 1; }
  const W = 320;
  const H = 46;
  const path = (a) => [...a].map((v, i) => `${i ? 'L' : 'M'}${((i / (a.length - 1)) * (W - 6) + 3).toFixed(1)},${(H - 4 - ((v - lo) / (hi - lo)) * (H - 8)).toFixed(1)}`).join(' ');
  return html`<svg class="curve" viewBox="0 0 ${W} ${H}" width="100%" height=${H} preserveAspectRatio="none" aria-label="Satzmelodie: Original gelb, du weiß">
    <path d=${path(orig)} fill="none" stroke="#FBBF24" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" />
    <path d=${path(user)} fill="none" stroke="rgba(255,255,255,.9)" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" /></svg>`;
}

/** Ergebnis einer Aufnahme: drei Werte, flache Kurve, ein Satz Rat — Laute im Detail auf Wunsch. */
export function TakeResult({ phrase, take }) {
  const { pros, phon } = useTake(phrase.audio, take);
  const [detail, setDetail] = useState(false);
  const cur = usePlaying();
  const ab = async () => { if (await audio.playBlob(take, { key: 'take-ab' })) await playPhrase(phrase); };
  if (pros === 'none') {
    return html`<div class="res"><div class="res-row"><span class="grow sub">Für diesen Satz gibt es keine Muttersprachler-Aufnahme zum Vergleichen.</span>
      <button class=${cls('chip sm', cur?.key === 'take' && 'on')} onClick=${() => audio.playBlob(take)}><${Icon} n="play_arrow" />Ich</button></div></div>`;
  }
  const c = pros !== 'busy' && pros !== 'fail' ? pros : null;
  const ph = phon.s === 'ok' ? phon.data : null;
  const phonNote = phon.s === 'err' ? (phon.code === 503 ? 'nicht eingerichtet' : phon.code === 422 ? 'zu leise' : 'Server aus') : null;
  const tips = ph ? phonemeHints(ph.marks) : [];
  return html`<div class="res">
    <div class="res-row" style="justify-content:space-between"><b class="res-title">Deine Aufnahme</b>
      <div class="rowx g6"><button class=${cls('chip sm', cur?.key === 'take' && 'on')} onClick=${() => audio.playBlob(take)}><${Icon} n="play_arrow" />Ich</button>
        <button class=${cls('chip sm', cur?.key === 'take-ab' && 'on')} onClick=${ab}><${Icon} n="compare_arrows" />Ich + Original</button></div></div>
    <div class="metrics">
      <${Metric} label="Melodie" value=${c?.melody ?? null} busy=${pros === 'busy'} note=${pros === 'fail' ? 'zu leise' : null} />
      <${Metric} label="Rhythmus" value=${c?.rhythm ?? null} busy=${pros === 'busy'} note=${pros === 'fail' ? 'zu leise' : null} />
      <${Metric} label="Laute" value=${ph?.score ?? null} busy=${phon.s === 'busy'} note=${phonNote} />
    </div>
    ${c && html`<div class="curvebox"><${Curve} orig=${c.refCurve} user=${c.userCurve} />
      <div class="curve-legend"><span><i style="background:#FBBF24"></i>Original</span><span><i style="background:#fff"></i>Du</span><span class="grow"></span><span>${Pr.tempoText(c.tempoRatio)}</span></div></div>`}
    <p class="res-advice">${prosodyAdvice(c, ph?.score)}</p>
    ${ph && html`<button class="res-link" onClick=${() => setDetail(!detail)}>${detail ? 'Laute ausblenden' : 'Laute im Detail'}</button>
      ${detail && html`<div class="phon">${ph.marks.map((m) => html`<span class=${m.status === 'ok' ? '' : m.status}>${m.status === 'sub' ? `${m.ref}→${m.user}` : m.ref}</span>`)}</div>
        ${tips.map((t) => html`<p class="res-tip">${t}</p>`)}`}`}
  </div>`;
}

/** Nur aufnehmen und vergleichen (Selbstvergleich ohne Erkenner, Betonungspaare im Lautlabor). */
export function RecordCompare({ phrase, onTake }) {
  return html`<${PhraseDock} phrase=${phrase} size="md" listen=${false} ai=${false} onTake=${onTake} />`;
}

// ── Satz-Leiste: Anhören · Langsam · Nachsprechen · KI-Prüfung ─────────────

/** Zwei Tonspuren übereinander — deine Aufnahme gegen das Original. */
const SvgCompare = ({ s = 22 }) => html`<svg viewBox="0 0 24 24" width=${s} height=${s} fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
  <path d="M3 7.5h1.5M7 4.5v6M10.5 6v3M14 3.5v8M17.5 5.5v4M21 7.5h-.5" /><path d="M3 16.5h1.5M7 14v5M10.5 12.5v8M14 15v3M17.5 13.5v6M21 16.5h-.5" opacity=".55" /></svg>`;
/** Sprechblase mit Haken und Funke — die Erkennung prüft, was ankam. */
const SvgAiCheck = ({ s = 22 }) => html`<svg viewBox="0 0 24 24" width=${s} height=${s} fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
  <path d="M4 6.5A2.5 2.5 0 0 1 6.5 4H13M20 11v2.5a2.5 2.5 0 0 1-2.5 2.5H10l-4.5 4v-4h0A1.5 1.5 0 0 1 4 14.5V9" /><path d="m8 10.5 2.3 2.3L14.5 8.5" />
  <path d="M18.5 2.5v4M16.5 4.5h4" stroke-width="1.7" /></svg>`;

function DockBtn({ icon, label, on, primary, onClick, disabled, rec }) {
  return html`<button class=${cls('dk', primary && 'primary', on && 'on', rec && 'rec')} onClick=${onClick} disabled=${disabled} aria-label=${label}>
    <span class="dk-c">${icon}</span><span class="dk-l">${label}</span></button>`;
}

/**
 * Die eine Bedienleiste für jeden Satz — überall gleich (Lektion, Satzbuch,
 * Vertiefung, Muster). Ergebnisse erscheinen darunter, kompakt.
 * size: 'lg' (Lektion) | 'md' | 'sm' (Karten). `onEvaluated(passed, sc)` meldet die KI-Prüfung.
 */
export function PhraseDock({ phrase, size = 'md', onEvaluated, onTake, listen = true, ai = true, record = true }) {
  const canRec = record && recorder.available();
  const canAi = ai && speech.available();
  const [rec, setRec] = useState(null);
  const [secs, setSecs] = useState(0);
  const [take, setTake] = useState(null);
  const [err, setErr] = useState(null);
  const [sc, setSc] = useState(null);
  const cur = usePlaying();
  const L = useListen((hyps) => {
    const s = Sp.speech(phrase.ru, hyps);
    setSc(s);
    (s.passed ? fx.right : fx.wrong)();
    onEvaluated?.(s.passed, s);
  });
  useEffect(() => () => rec?.cancel(), [rec]);
  useEffect(() => {
    if (!rec) return undefined;
    setSecs(0);
    const t = setInterval(() => setSecs((x) => x + 1), 1000);
    return () => clearInterval(t);
  }, [rec]);
  const doRecord = async () => {
    if (rec) {
      const blob = await rec.stop();
      setRec(null); setTake(blob); onTake?.(blob);
      if (await audio.playBlob(blob, { key: 'take-ab' })) playPhrase(phrase);
      return;
    }
    L.cancel(); audio.stop(); setErr(null);
    try { setRec(await recorder.start()); } catch { setErr(SPEECH_ERRORS.denied); }
  };
  const doCheck = () => {
    if (L.listening) { L.stop(); return; }
    if (rec) { rec.cancel(); setRec(null); }
    setSc(null); L.start(phrase.ru);
  };
  const playing = cur?.key === phrase.audio;
  const ic = size === 'lg' ? 26 : size === 'sm' ? 19 : 22;
  return html`<div class=${cls('dockwrap', `dock-${size}`)}>
    <div class="dock">
      ${listen && html`<${DockBtn} primary icon=${html`<${Icon} n="volume_up" fill size=${ic} />`} label="Anhören" on=${playing && !cur.slow} onClick=${() => playPhrase(phrase)} />
        <${DockBtn} icon=${html`<${Icon} n="slow_motion_video" size=${ic} />`} label="Langsam" on=${playing && cur.slow} onClick=${() => playPhrase(phrase, { slow: true })} />`}
      ${canRec && html`<${DockBtn} rec=${!!rec} icon=${rec ? html`<${Icon} n="stop" fill size=${ic} />` : html`<${SvgCompare} s=${ic} />`}
        label=${rec ? `Stopp · ${secs} s` : 'Nachsprechen'} onClick=${doRecord} />`}
      ${canAi && html`<${DockBtn} on=${L.listening} disabled=${L.evaluating} icon=${L.listening ? html`<${Icon} n="graphic_eq" size=${ic} />` : L.evaluating ? html`<${Icon} n="progress_activity" c="spin" size=${ic} />` : html`<${SvgAiCheck} s=${ic} />`}
        label=${L.listening ? 'Fertig' : 'KI-Prüfung'} onClick=${doCheck} />`}
    </div>
    ${(L.listening || rec) && html`<div class="dock-hint">${rec ? 'Sprich den Satz nach — tippe auf Stopp, dann hörst du dich und das Original.' : L.partial ? `»${L.partial}«` : 'Ich höre zu … sprich den Satz und tippe auf Fertig.'}</div>`}
    ${(err || (!L.listening && L.errorText)) && html`<div class="dock-hint">${err ?? L.errorText}</div>`}
    ${(sc || (take && !rec)) && html`<div class="stack g8 dock-res">
      ${sc && html`<${ScoreCard} sc=${sc} targetRu=${phrase.ru} />`}
      ${take && !rec && html`<${TakeResult} phrase=${phrase} take=${take} key=${take.size} />`}</div>`}
  </div>`;
}

/** Älterer Name — überall dieselbe Leiste, nur ohne Anhören (das steht dort schon daneben). */
export function Practice({ phrase, onEvaluated }) {
  return html`<${PhraseDock} phrase=${phrase} size="sm" listen=${false} onEvaluated=${onEvaluated} />`;
}

// ── Satzkarte: alles, was zum Satz gehört, in einer Fläche ────────────────

/** Die Form des anderen Geschlechts als eine Zeile — zum Erkennen, nicht zum Sprechen. */
export function OtherFormLine({ id }) {
  const o = otherForm(id);
  const cur = usePlaying();
  if (!o) return null;
  return html`<button class="snote other" onClick=${() => playPhrase(o.phrase)}>
    <${Icon} n=${cur?.key === o.phrase.audio ? 'graphic_eq' : 'volume_up'} size="17" c=${cur?.key === o.phrase.audio ? 'amber' : ''} />
    <span class="grow"><span class="faint">${o.label}:</span> <b>${Ph.transliterateFlat(Gl.withStress(o.phrase.ru))}</b> <span class="cyr" lang="ru">${o.phrase.display}</span></span></button>`;
}

/**
 * Ein Satz als Karte: Umschrift mit Bedeutung je Wort, die kyrillische Zeile
 * (antippen klappt die Buchstaben genau dort auf), Deutsch — und Tipp,
 * wörtliche Übersetzung und die andere Geschlechtsform als ruhige Zeilen darin.
 */
export function SentenceCard({ phrase, size = 26, center = false, tint = false, notes = true, explain = null, children }) {
  const [lens, setLens] = useState(false);
  const display = Ph.display(Gl.withStress(phrase.ru));
  return html`<div class=${cls('scard', center && 'center', tint && 'tint')}>
    <${IL} ru=${phrase.ru} size=${size} center=${center} cyr=${false} />
    <button class=${cls('cyr-line', lens && 'open')} onClick=${() => setLens(!lens)} aria-label=${lens ? 'Buchstaben ausblenden' : 'Buchstaben zeigen'}>
      <span class="cyr" lang="ru">${display}</span><span class="abc">${lens ? 'ausblenden' : 'Buchstaben'}</span></button>
    ${lens && html`<div class="lenswrap"><${LetterLens} ru=${phrase.ru} /></div>`}
    <div class="scard-de" style=${`font-size:${Math.max(14.5, Math.round(size * 0.62))}px`}>${phrase.de}</div>
    ${explain && html`<p class="scard-explain">${explain}</p>`}
    ${notes && (phrase.note || phrase.lit || otherForm(phrase.id)) && html`<div class="snotes">
      ${phrase.lit && html`<div class="snote"><${Icon} n="translate" size="17" /><span class="grow"><span class="faint">Wörtlich:</span> ${phrase.lit}</span></div>`}
      ${phrase.note && html`<div class="snote"><${Icon} n="lightbulb" size="17" /><span class="grow">${phrase.note}</span></div>`}
      <${OtherFormLine} id=${phrase.id} /></div>`}
    ${children}
  </div>`;
}

// ── Abschluss ──────────────────────────────────────────────────────────────

/** Einheitlicher Abschluss jeder Übung. `tiles`: [{v, l, icon?, c?}] */
export function Summary({ over, title, note, stars, big, tiles = [], children, actions, celebrate }) {
  useEffect(() => { if (celebrate) fx.win(); }, []);
  return html`<div class="screen">
    ${celebrate && html`<${Confetti} />`}
    <div class="scroll"><div class="sum" style="min-height:100%">
      <div class="over amber">${over}</div>
      ${stars != null && html`<div class="bigstars">${[0, 1, 2].map((i) => html`<${Icon} n="star" c=${i < stars ? 'on' : ''} />`)}</div>`}
      ${big != null && html`<div class="bignum mt12">${big}</div>`}
      ${title && html`<div class="title mt8">${title}</div>`}
      ${note && html`<div class="sub" style="max-width:300px">${note}</div>`}
      ${tiles.length > 0 && html`<div class="stat3 mt20" style="width:100%">${tiles.map((t) => html`<div><b style=${t.c ? `color:${t.c}` : ''}>${t.icon && html`<${Icon} n=${t.icon} fill />`}${t.v}</b><span>${t.l}</span></div>`)}</div>`}
      ${children}
    </div></div>
    <div class="bottom">${actions}</div>
  </div>`;
}

export function Empty({ icon = 'inbox', title, text, action }) {
  return html`<div class="empty"><${Icon} n=${icon} /><b>${title}</b>${text}${action && html`<div class="mt20">${action}</div>`}</div>`;
}

/** Sicherheitsfrage beim Schließen einer laufenden Übung. */
export function LeaveSheet({ onStay, onLeave, saved }) {
  return html`<${Sheet} onClose=${onStay} title="Übung verlassen?">
    <p class="sub mt4">${saved ? 'Dein Stand bleibt gespeichert — du machst beim nächsten Mal an derselben Stelle weiter.' : 'Die Punkte dieser Runde gehen verloren. Was du bis hier geübt hast, bleibt in deinem Lernstand.'}</p>
    <div class="stack g8 mt20"><button class="btn" onClick=${onStay}>Weitermachen</button><button class="btn ghost" onClick=${onLeave}>Verlassen</button></div>
  <//>`;
}

export { tts, fx, audio, speech };
