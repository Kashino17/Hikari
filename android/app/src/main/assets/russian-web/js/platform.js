// Geräte-Anbindung: Speicher, Audio, Sprachausgabe, Spracherkennung, Aufnahme.
// Alles, was im Browser anders läuft als später in der Android-App, steckt hier
// hinter einer kleinen Schnittstelle. Beim Live-Schalten wird nur diese Datei
// um die native Brücke (window.HikariNative) ergänzt — die Oberfläche bleibt gleich.

const native = typeof window !== 'undefined' ? window.HikariNative ?? null : null;
export const isNative = !!native;

// Antworten und Ereignisse der App kommen über zwei globale Funktionen zurück.
const nativeCbs = new Map();
const nativeEvents = new Map();
let nativeSeq = 0;
const nextId = (p) => `${p}${++nativeSeq}`;
if (typeof window !== 'undefined') {
  window.__hkCb = (id, payload) => nativeCbs.get(id)?.(payload);
  window.__hkEvent = (name, payload) => { for (const fn of [...(nativeEvents.get(name) ?? [])]) fn(payload); };
}
/** Auf ein Ereignis der App hören (z. B. 'podcast', 'store'). */
export function nativeOn(name, fn) {
  if (!nativeEvents.has(name)) nativeEvents.set(name, new Set());
  nativeEvents.get(name).add(fn);
  return () => nativeEvents.get(name).delete(fn);
}
/** Einmalige Antwort der App abwarten. */
const nativeCall = (prefix, start) => new Promise((resolve) => { const id = nextId(prefix); nativeCbs.set(id, (m) => { nativeCbs.delete(id); resolve(m); }); start(id); });

// ── Speicher (Schlüssel/Wert wie SharedPreferences "hikari_russian") ────────

const LS_PREFIX = 'hikari_russian:';
const values = new Map();
let pendingSet = {};
let pendingRemove = new Set();
let flushTimer = null;
let serverOk = false;

async function flush() {
  flushTimer = null;
  if (!serverOk) return;
  const body = JSON.stringify({ set: pendingSet, remove: [...pendingRemove] });
  pendingSet = {};
  pendingRemove = new Set();
  try { await fetch('/store', { method: 'POST', headers: { 'content-type': 'application/json' }, body, keepalive: body.length < 60000 }); } catch { /* lokal liegt es trotzdem */ }
}
const scheduleFlush = () => { if (!flushTimer) flushTimer = setTimeout(flush, 250); };

export const storage = {
  /** Lädt den Lernstand: Server (geräteübergreifend) → sonst localStorage. */
  async init() {
    if (native?.storeAll) {
      for (const [k, v] of Object.entries(JSON.parse(native.storeAll()))) values.set(k, v);
      return;
    }
    try {
      const r = await fetch('/store', { cache: 'no-store' });
      if (!r.ok) throw new Error(String(r.status));
      const data = await r.json();
      for (const [k, v] of Object.entries(data.values ?? {})) values.set(k, v);
      serverOk = true;
    } catch {
      for (let i = 0; i < localStorage.length; i++) {
        const k = localStorage.key(i);
        if (k.startsWith(LS_PREFIX)) values.set(k.slice(LS_PREFIX.length), localStorage.getItem(k));
      }
    }
  },
  get: (key) => values.get(key) ?? null,
  getJSON(key, fallback = null) {
    const v = values.get(key);
    if (v == null) return fallback;
    try { return JSON.parse(v); } catch { return fallback; }
  },
  set(key, value) {
    const v = typeof value === 'string' ? value : JSON.stringify(value);
    values.set(key, v);
    if (native?.storeSet) { native.storeSet(key, v); return; }
    try { localStorage.setItem(LS_PREFIX + key, v); } catch { /* voll */ }
    pendingSet[key] = v; pendingRemove.delete(key); scheduleFlush();
  },
  remove(key) {
    values.delete(key);
    if (native?.storeRemove) { native.storeRemove(key); return; }
    try { localStorage.removeItem(LS_PREFIX + key); } catch { /* egal */ }
    delete pendingSet[key]; pendingRemove.add(key); scheduleFlush();
  },
  keys: () => [...values.keys()],
  /** Nur den Wert im Arbeitsspeicher setzen (kommt aus der App, wird nicht zurückgeschrieben). */
  setLocal(key, value) { values.set(key, typeof value === 'string' ? value : JSON.stringify(value)); },
  /** Wird aufgerufen, wenn die App einen Wert von sich aus geändert hat. */
  onExternal(fn) { return nativeOn('store', (m) => { if (m.value == null) values.delete(m.key); else values.set(m.key, m.value); fn(m.key); }); },
  flushNow: () => { if (flushTimer) { clearTimeout(flushTimer); return flush(); } return Promise.resolve(); },
};
if (typeof window !== 'undefined') {
  window.addEventListener('pagehide', () => { if (flushTimer) { clearTimeout(flushTimer); flush(); } });
}

// ── Kleine Ereignis-Hilfe ──────────────────────────────────────────────────

function emitter() {
  const subs = new Set();
  return { on(fn) { subs.add(fn); return () => subs.delete(fn); }, emit(v) { for (const fn of [...subs]) fn(v); } };
}

// ── Sprachausgabe des Geräts (Rückfall, wenn weder Clip noch Kursstimme vom Mac da sind) ──

let ruVoice = null;
function pickVoice() {
  if (typeof speechSynthesis === 'undefined') return null;
  const voices = speechSynthesis.getVoices().filter((v) => v.lang?.toLowerCase().startsWith('ru'));
  // Natürlich klingende Stimmen zuerst (Google/Enhanced), sonst die erste russische.
  ruVoice = voices.find((v) => /google|enhanced|premium|natural/i.test(v.name)) ?? voices[0] ?? null;
  return ruVoice;
}
if (typeof speechSynthesis !== 'undefined') {
  pickVoice();
  speechSynthesis.addEventListener?.('voiceschanged', pickVoice);
}

export const tts = {
  available: () => !!native?.ttsSpeak || typeof speechSynthesis !== 'undefined',
  hasRussianVoice: () => !!native?.ttsSpeak || !!(ruVoice ?? pickVoice()),
  speak(text, { rate = 1, lang = 'ru-RU' } = {}) {
    if (native?.ttsSpeak) {
      if (!text) return Promise.resolve(false);
      return nativeCall('t', (id) => native.ttsSpeak(id, text, lang, rate)).then((m) => !!m.ok);
    }
    return new Promise((resolve) => {
      if (typeof speechSynthesis === 'undefined' || !text) { resolve(false); return; }
      speechSynthesis.cancel();
      const u = new SpeechSynthesisUtterance(text);
      u.lang = lang;
      const v = lang.startsWith('ru') ? ruVoice ?? pickVoice() : speechSynthesis.getVoices().find((x) => x.lang?.toLowerCase().startsWith(lang.slice(0, 2).toLowerCase()));
      if (v) u.voice = v;
      u.rate = rate;
      let done = false;
      const fin = (ok) => { if (!done) { done = true; resolve(ok); } };
      u.onend = () => fin(true);
      u.onerror = () => fin(false);
      // Manche Browser melden das Ende nie — Sicherheitsnetz nach geschätzter Sprechzeit.
      setTimeout(() => fin(true), 2500 + text.length * 140 / rate);
      speechSynthesis.speak(u);
    });
  },
  stop() { if (native?.ttsStop) native.ttsStop(); else if (typeof speechSynthesis !== 'undefined') speechSynthesis.cancel(); },
};

// ── Kursstimme vom Mac ─────────────────────────────────────────────────────
// Für Sätze ohne fertigen Clip (Kombi-Meister, Gespräch mit Anja, eigene Sätze) spricht
// der Hikari-Server in denselben Stimmen wie der Kurs. Ist er nicht erreichbar oder lädt
// sein Sprachmodell noch, übernimmt sofort die Gerätestimme.

const voiceUrls = new Map(); // "stimme|tempo|text" → Objekt-URL
let voicePausedUntil = 0;
let defaultVoice = 'm';
const timeoutSignal = (ms) => {
  if (typeof AbortSignal !== 'undefined' && AbortSignal.timeout) return AbortSignal.timeout(ms);
  const c = new AbortController();
  setTimeout(() => c.abort(), ms);
  return c.signal;
};

export const serverVoice = {
  /** Stimme für eigene Sätze: 'm' oder 'f' (aus den Einstellungen); Anja spricht mit 'p'. */
  setDefault(v) { defaultVoice = v; },
  defaultVoice: () => defaultVoice,
  /** Dienst vorwärmen: das Sprachmodell lädt rund eine halbe Minute. */
  warm() {
    if (Date.now() < voicePausedUntil) return;
    fetch('/api/russian/voice/status?warm=1', { cache: 'no-store', signal: timeoutSignal(4000) }).catch(() => { voicePausedUntil = Date.now() + 60000; });
  },
  /** Russischen Text (möglichst mit "+") sprechen lassen. → Objekt-URL oder null (dann Gerätestimme). */
  async fetch(ru, { voice = defaultVoice, rate = 1 } = {}) {
    const key = `${voice}|${rate}|${ru}`;
    if (voiceUrls.has(key)) return voiceUrls.get(key);
    if (Date.now() < voicePausedUntil) return null;
    try {
      const r = await fetch(`/api/russian/voice?voice=${voice}&rate=${rate}&text=${encodeURIComponent(ru)}`, { signal: timeoutSignal(5000) });
      // 503 = Modell lädt noch (gleich wieder versuchen) oder nicht eingerichtet; alles andere = länger Pause.
      if (!r.ok) { voicePausedUntil = Date.now() + (r.status === 503 ? 8000 : 60000); return null; }
      const url = URL.createObjectURL(await r.blob());
      voiceUrls.set(key, url);
      if (voiceUrls.size > 80) { const [k, old] = voiceUrls.entries().next().value; voiceUrls.delete(k); URL.revokeObjectURL(old); }
      return url;
    } catch { voicePausedUntil = Date.now() + 60000; return null; }
  },
};

// ── Clips ──────────────────────────────────────────────────────────────────

let clipSet = new Set();
const el = typeof Audio !== 'undefined' ? new Audio() : null;
if (el) { el.preload = 'auto'; el.preservesPitch = true; }
let playToken = 0;
const audioState = emitter();
let current = null; // {key, slow}

const setCurrent = (c) => { current = c; audioState.emit(c); };

export const audio = {
  setClips(names) { clipSet = new Set(names); },
  has: (clip) => clipSet.has(clip),
  url: (clip) => `/audio/${clip}.ogg`,
  onState: audioState.on,
  current: () => current,

  /**
   * Spielt einen Clip. `slow` nimmt die langsam gesprochene Aufnahme (falls vorhanden),
   * sonst wird die normale gebremst. Ohne Clip spricht die Kursstimme vom Mac (`ru` = Text
   * mit Betonung, `voice` = 'm' | 'f' | 'p'); ist sie nicht erreichbar, die Gerätestimme (`text`).
   * `key` kennzeichnet, welcher Knopf gerade »spielt« (Standard: der Clip-Name).
   * Löst auf, wenn die Wiedergabe endet: true = zu Ende gespielt, false = abgebrochen/Fehler.
   */
  async play(clip, { slow = false, text = null, ru = null, voice = undefined, rate = 1, key = null } = {}) {
    const token = ++playToken;
    this._halt();
    const k = key ?? clip;
    setCurrent({ key: k, slow });
    const done = (ok) => { if (token === playToken) setCurrent(null); return ok; };
    let name = clip;
    let playbackRate = rate;
    if (clip && slow) {
      if (clipSet.has(`${clip}_slow`)) name = `${clip}_slow`;
      else playbackRate = rate * 0.75;
    }
    if (name && clipSet.has(name)) return done(await this._element(this.url(name), playbackRate));
    const spoken = ru ?? text;
    if (spoken) {
      // Langsam heißt hier wirklich langsam gesprochen, nicht gebremst abgespielt.
      const src = await serverVoice.fetch(spoken, { voice, rate: slow ? 0.7 : 1 });
      if (token !== playToken) return false; // inzwischen wurde etwas anderes gestartet
      if (src) return done(await this._element(src, rate));
    }
    if (text) return done(await tts.speak(text, { rate: (slow ? 0.6 : 0.92) * rate }));
    return done(false);
  },

  _element(src, playbackRate) {
    return new Promise((resolve) => {
      const fin = (ok) => { el.onended = el.onerror = el.onpause = null; resolve(ok); };
      el.onended = () => fin(true);
      el.onerror = () => fin(false);
      el.onpause = () => { if (!el.ended) fin(false); };
      el.src = src;
      el.playbackRate = playbackRate;
      el.play().catch(() => fin(false));
    });
  },

  /** Eigene Aufnahme (Blob) abspielen. */
  async playBlob(blob, { key = 'take' } = {}) {
    const token = ++playToken;
    this._halt();
    setCurrent({ key, slow: false });
    const url = URL.createObjectURL(blob);
    const ok = await new Promise((resolve) => {
      el.onended = () => resolve(true);
      el.onerror = () => resolve(false);
      el.onpause = () => { if (!el.ended) resolve(false); };
      el.src = url;
      el.playbackRate = 1;
      el.play().catch(() => resolve(false));
    });
    el.onended = el.onerror = el.onpause = null;
    URL.revokeObjectURL(url);
    if (token === playToken) setCurrent(null);
    return ok;
  },

  _halt() {
    tts.stop();
    if (el && !el.paused) { el.onpause = null; el.pause(); }
  },
  stop() { playToken++; this._halt(); setCurrent(null); },

  /** Clips vorab laden, damit der nächste Satz ohne Verzögerung startet. */
  preload(clips) {
    for (const c of clips) if (c && clipSet.has(c)) fetch(this.url(c)).catch(() => {});
  },
};

// ── Rückmeldungs-Töne & Vibration ──────────────────────────────────────────

let ctx = null;
export function audioContext() {
  if (!ctx) { const AC = window.AudioContext ?? window.webkitAudioContext; if (AC) ctx = new AC(); }
  if (ctx?.state === 'suspended') ctx.resume().catch(() => {});
  return ctx;
}
function tone(freqs, { dur = 0.11, gap = 0.085, type = 'sine', vol = 0.07 } = {}) {
  if (storage.get('ui_sounds') === '0') return;
  const ac = audioContext();
  if (!ac) return;
  freqs.forEach((f, i) => {
    const o = ac.createOscillator();
    const g = ac.createGain();
    o.type = type; o.frequency.value = f;
    const t0 = ac.currentTime + i * gap;
    g.gain.setValueAtTime(0, t0);
    g.gain.linearRampToValueAtTime(vol, t0 + 0.012);
    g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
    o.connect(g).connect(ac.destination);
    o.start(t0); o.stop(t0 + dur + 0.02);
  });
}
export const fx = {
  right: () => { tone([660, 990]); navigator.vibrate?.(12); },
  wrong: () => { tone([220, 196], { type: 'triangle', dur: 0.16, vol: 0.09 }); navigator.vibrate?.([18, 40, 18]); },
  tap: () => navigator.vibrate?.(6),
  win: () => { tone([523, 659, 784, 1047], { dur: 0.16, gap: 0.1 }); navigator.vibrate?.([12, 50, 12, 50, 24]); },
  tick: () => tone([880], { dur: 0.05, vol: 0.04 }),
};

// ── Spracherkennung ────────────────────────────────────────────────────────

const SR = typeof window !== 'undefined' ? window.SpeechRecognition ?? window.webkitSpeechRecognition : null;

export const speech = {
  available: () => !!((native?.speechAvailable && native.speechAvailable()) || SR || storage.get('dev_speech')),

  /**
   * Hört einmal zu. → {promise, stop()}.
   * promise → {ok:true, hyps:[bis zu 5 Lesarten]} | {ok:false, error:'unavailable'|'denied'|'nospeech'|'network'|'aborted'}
   */
  listen({ lang = 'ru-RU', onPartial = null, onStart = null, maxMs = 20000, waitMs = 10000, delayMs = 250, expect = '' } = {}) {
    audio.stop();
    // Test-Werkzeug: Spracherkennung simulieren (ohne Mikrofon durchklickbar).
    const sim = storage.get('dev_speech');
    if (sim === 'ok' || sim === 'bad') {
      const text = sim === 'ok' ? expect.replaceAll('+', '').replaceAll('́', '') : 'я не знаю что сказать';
      let done = null;
      let timer = null;
      const promise = new Promise((resolve) => {
        done = () => { clearTimeout(timer); resolve(text ? { ok: true, hyps: [text] } : { ok: false, error: 'nospeech' }); };
        setTimeout(() => onPartial?.(text.split(' ').slice(0, 2).join(' ')), 350);
        timer = setTimeout(done, 1100);
      });
      return { promise, stop: () => done(), abort() { clearTimeout(timer); } };
    }
    // In der App: die Android-Spracherkennung (wartet selbst auf den Einsatz und das Satzende).
    if (native?.listenStart) {
      const id = nextId('l');
      let last = '';
      let resolveFn = null;
      const promise = new Promise((resolve) => {
        resolveFn = resolve;
        nativeCbs.set(id, (m) => {
          if (m.t === 'partial') { last = m.text; onPartial?.(m.text); return; }
          nativeCbs.delete(id);
          if (m.t === 'done') resolve(m.hyps?.length ? { ok: true, hyps: m.hyps } : last ? { ok: true, hyps: [last] } : { ok: false, error: 'nospeech' });
          else resolve({ ok: false, error: m.code ?? 'unavailable' });
        });
        setTimeout(() => { if (nativeCbs.has(id)) { onStart?.(); native.listenStart(id, lang); } }, delayMs);
      });
      return {
        promise,
        stop() { native.listenStop(id); },
        abort() { if (nativeCbs.has(id)) { nativeCbs.delete(id); native.listenAbort(id); resolveFn({ ok: false, error: 'aborted' }); } },
      };
    }
    if (!SR) return { promise: Promise.resolve({ ok: false, error: 'unavailable' }), stop() {}, abort() {} };

    // Chrome (vor allem auf Android) beendet eine Erkennung, sobald am Anfang kurz Stille ist —
    // oft nach unter einer Sekunde. Darum: bis `waitMs` auf den Einsatz warten und die Erkennung
    // bei Stille still neu starten. Erst wenn du sprichst, entscheidet die Satzende-Erkennung.
    const started = Date.now();
    let settled = false;
    let userStopped = false;
    let heardAnything = false;
    let finalHyps = null;
    let lastPartial = '';
    let rec = null;
    let resolveFn = null;
    let hardTimer = null;
    const finish = (r) => {
      if (settled) return;
      settled = true;
      clearTimeout(hardTimer);
      try { rec?.abort(); } catch { /* läuft nicht */ }
      resolveFn(r);
    };
    const result = () => (finalHyps?.length ? { ok: true, hyps: finalHyps } : lastPartial ? { ok: true, hyps: [lastPartial] } : { ok: false, error: 'nospeech' });
    const MAP = { 'not-allowed': 'denied', 'service-not-allowed': 'denied', 'audio-capture': 'denied', network: 'network', 'language-not-supported': 'unavailable' };
    let restarts = 0;
    const run = () => {
      if (settled) return;
      const r = new SR();
      rec = r;
      r.lang = lang;
      r.interimResults = true;
      r.maxAlternatives = 5;
      r.continuous = false;
      let fatal = null;
      r.onstart = () => { if (restarts === 0) onStart?.(); };
      r.onresult = (e) => {
        const res = e.results[e.results.length - 1];
        const hyps = [...res].map((a) => a.transcript.trim()).filter(Boolean);
        if (!hyps.length) return;
        heardAnything = true;
        if (res.isFinal) finalHyps = hyps;
        else { lastPartial = hyps[0]; onPartial?.(hyps[0]); }
      };
      r.onerror = (e) => { if (MAP[e.error]) fatal = MAP[e.error]; };
      r.onend = () => {
        if (settled || rec !== r) return;
        if (fatal) { finish({ ok: false, error: fatal }); return; }
        if (finalHyps?.length || userStopped || heardAnything) { finish(result()); return; }
        // Noch nichts gehört: weiter warten, solange das Zeitfenster offen ist.
        if (Date.now() - started < waitMs && restarts < 25) { restarts++; setTimeout(run, 120); return; }
        finish(result());
      };
      try { r.start(); } catch { finish({ ok: false, error: 'unavailable' }); }
    };
    const promise = new Promise((resolve) => {
      resolveFn = resolve;
      hardTimer = setTimeout(() => { userStopped = true; try { rec?.stop(); } catch { finish(result()); } }, maxMs);
      // Kurz warten, damit das Mikrofon nicht noch den Lautsprecher-Ausklang mitnimmt.
      setTimeout(run, delayMs);
    });
    return {
      promise,
      stop() {
        userStopped = true;
        try { rec?.stop(); } catch { finish(result()); }
        // Kommt vom System nichts mehr zurück, mit dem bisher Gehörten abschließen.
        setTimeout(() => finish(result()), 2500);
      },
      abort() { finish({ ok: false, error: 'aborted' }); },
    };
  },
};

export const SPEECH_ERRORS = {
  unavailable: native ? 'Auf diesem Gerät wurde keine Spracherkennung gefunden. Vergleiche selbst mit dem Original.' : 'Dieser Browser hat keine Spracherkennung. Vergleiche selbst mit dem Original.',
  denied: native ? 'Kein Zugriff aufs Mikrofon. Erlaube es in den Android-Einstellungen für Hikari.' : 'Kein Zugriff aufs Mikrofon. Erlaube es in den Browser-Einstellungen für diese Seite.',
  nospeech: 'Ich habe nichts gehört. Tippe aufs Mikrofon, wenn du so weit bist.',
  network: native ? 'Die Spracherkennung braucht Internet oder das russische Offline-Sprachpaket.' : 'Die Spracherkennung braucht gerade Internet und hat keine Verbindung.',
  busy: 'Die Spracherkennung ist gerade nicht erreichbar — bitte gleich nochmal versuchen.',
  aborted: 'Abgebrochen.',
};

// ── Aufnahme der eigenen Stimme ────────────────────────────────────────────

export const recorder = {
  available: () => !!(navigator.mediaDevices?.getUserMedia && window.MediaRecorder),

  /** → {stop(): Promise<Blob>, cancel(), level(): 0..1} */
  async start() {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true, channelCount: 1 } });
    const mime = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4'].find((m) => MediaRecorder.isTypeSupported(m)) ?? '';
    const rec = new MediaRecorder(stream, mime ? { mimeType: mime } : undefined);
    const chunks = [];
    rec.ondataavailable = (e) => { if (e.data.size) chunks.push(e.data); };
    let analyser = null;
    let buf = null;
    const ac = audioContext();
    let srcNode = null;
    if (ac) {
      srcNode = ac.createMediaStreamSource(stream);
      analyser = ac.createAnalyser();
      analyser.fftSize = 512;
      buf = new Float32Array(analyser.fftSize);
      srcNode.connect(analyser);
    }
    const release = () => { stream.getTracks().forEach((t) => t.stop()); try { srcNode?.disconnect(); } catch { /* egal */ } };
    rec.start();
    return {
      level() {
        if (!analyser) return 0;
        analyser.getFloatTimeDomainData(buf);
        let s = 0;
        for (const v of buf) s += v * v;
        return Math.min(1, Math.sqrt(s / buf.length) * 4);
      },
      stop: () => new Promise((resolve) => {
        rec.onstop = () => { release(); resolve(new Blob(chunks, { type: rec.mimeType || 'audio/webm' })); };
        try { rec.stop(); } catch { release(); resolve(new Blob(chunks)); }
      }),
      cancel() { try { rec.stop(); } catch { /* egal */ } release(); },
    };
  },
};

/** Audio (Blob/ArrayBuffer/URL) → Float32Array, mono, 16 kHz — für den Melodie-Vergleich. */
export async function decodePcm16k(source) {
  let data;
  if (typeof source === 'string') data = await (await fetch(source)).arrayBuffer();
  else if (source instanceof Blob) data = await source.arrayBuffer();
  else data = source;
  const ac = audioContext();
  const decoded = await ac.decodeAudioData(data.slice(0));
  const len = Math.max(1, Math.ceil(decoded.duration * 16000));
  const off = new OfflineAudioContext(1, len, 16000);
  const src = off.createBufferSource();
  src.buffer = decoded;
  src.connect(off.destination);
  src.start();
  const rendered = await off.startRendering();
  return rendered.getChannelData(0);
}

// ── Hikari-Server ──────────────────────────────────────────────────────────

const blobToBase64 = (blob) => new Promise((resolve, reject) => {
  const r = new FileReader();
  r.onload = () => resolve(String(r.result).split(',')[1] ?? '');
  r.onerror = reject;
  r.readAsDataURL(blob);
});
function nativeResult(m) {
  let data = {};
  try { data = JSON.parse(m.body || '{}'); } catch { /* kein JSON */ }
  if (m.status >= 200 && m.status < 300) return data;
  throw Object.assign(new Error(data.error ?? `HTTP ${m.status}`), { status: m.status || undefined, data });
}

export const api = {
  async get(path) {
    const r = await fetch(`/api${path}`, { cache: 'no-store' });
    if (!r.ok) throw Object.assign(new Error(`HTTP ${r.status}`), { status: r.status });
    return r.json();
  },
  async post(path, body) {
    if (native?.httpPost) return nativeResult(await nativeCall('h', (id) => native.httpPost(id, path, JSON.stringify(body ?? {}))));
    const r = await fetch(`/api${path}`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body ?? {}) });
    const data = await r.json().catch(() => ({}));
    if (!r.ok) throw Object.assign(new Error(data.error ?? `HTTP ${r.status}`), { status: r.status, data });
    return data;
  },
  /** Aussprache-Prüfung: eigene Aufnahme gegen die Kursstimme (Clip-Name oder "text:<Stimme>:<Satz>"). */
  async pronunciation(clip, blob) {
    if (native?.pronounce) {
      const b64 = await blobToBase64(blob);
      return nativeResult(await nativeCall('p', (id) => native.pronounce(id, clip, b64, blob.type || 'audio/webm')));
    }
    const fd = new FormData();
    fd.append('clip', clip); // Feld MUSS vor der Datei stehen (Server liest es aus part.fields)
    fd.append('file', blob, 'take.webm');
    const r = await fetch('/api/russian/pronunciation', { method: 'POST', body: fd });
    const data = await r.json().catch(() => ({}));
    if (!r.ok) throw Object.assign(new Error(data.error ?? `HTTP ${r.status}`), { status: r.status, data });
    return data;
  },
  mediaUrl: (path) => `/api${path}`,
};

/** Die App selbst (nur dort vorhanden): Podcast-Player, Erinnerung, Schließen. */
export const app = native;
