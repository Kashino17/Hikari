// Geräte-Anbindung des Fitness-Bereichs. Im Browser (Vorschau) läuft alles über fetch,
// in der App über window.HikariNative (FitWebScreen.kt). Die Oberfläche kennt nur diese Datei.

const native = typeof window !== 'undefined' ? window.HikariNative ?? null : null;
export const isNative = !!native;

const cbs = new Map();
const events = new Map();
let seq = 0;
if (typeof window !== 'undefined') {
  window.__hkCb = (id, payload) => cbs.get(id)?.(payload);
  window.__hkEvent = (name, payload) => { for (const fn of [...(events.get(name) ?? [])]) fn(payload); };
}
export function nativeOn(name, fn) {
  if (!events.has(name)) events.set(name, new Set());
  events.get(name).add(fn);
  return () => events.get(name).delete(fn);
}
const call = (start) => new Promise((resolve) => { const id = `f${++seq}`; cbs.set(id, (m) => { cbs.delete(id); resolve(m); }); start(id); });

// ── Kleiner Speicher (nur Bequemlichkeiten: zuletzt gewählter Reiter, Entwürfe) ──
const LS = 'hikari_fit:';
export const local = {
  get(key, fallback = null) {
    try {
      const v = native?.storeGet ? native.storeGet(key) : localStorage.getItem(LS + key);
      if (v == null || v === '') return fallback;
      // Gelöschte Werte liegen als "null" — dann zählt der Rückfallwert (sonst stürzt der Leser ab).
      const parsed = JSON.parse(v);
      return parsed == null ? fallback : parsed;
    } catch { return fallback; }
  },
  set(key, value) {
    const v = JSON.stringify(value);
    try { if (native?.storeSet) native.storeSet(key, v); else localStorage.setItem(LS + key, v); } catch { /* egal */ }
  },
};

// ── Server ──
export class ApiError extends Error {
  constructor(status, body) { super(body?.error ?? `HTTP ${status}`); this.status = status; this.body = body; }
}
const parse = (status, text) => {
  let body = null;
  try { body = text ? JSON.parse(text) : null; } catch { body = { error: text }; }
  if (status < 200 || status >= 300) throw new ApiError(status, body);
  return body;
};
export const api = {
  async get(path) {
    let r;
    try { r = await fetch(`/api${path}`, { cache: 'no-store' }); } catch { throw new ApiError(0, { error: 'offline' }); }
    return parse(r.status, await r.text());
  },
  async post(path, body) {
    if (native?.httpPost) {
      const m = await call((id) => native.httpPost(id, path, JSON.stringify(body ?? {})));
      if (m.status === 0) throw new ApiError(0, { error: 'offline' });
      return parse(m.status, m.body);
    }
    let r;
    try { r = await fetch(`/api${path}`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body ?? {}) }); } catch { throw new ApiError(0, { error: 'offline' }); }
    return parse(r.status, await r.text());
  },
  url: (path) => `/api${path}`,
};

// ── Medien: Videos einmal per fetch laden und als blob: abspielen ──
// (Die WebView kann Teilabrufe über die Brücke nicht weiterreichen — siehe Lieder & Szenen v0.89.113.)
const blobs = new Map();
const order = [];
export function mediaBlob(path) {
  if (!path) return Promise.resolve(null);
  if (blobs.has(path)) return blobs.get(path);
  const p = fetch(api.url(path)).then((r) => (r.ok ? r.blob() : null)).then((b) => (b ? URL.createObjectURL(b) : null)).catch(() => null);
  blobs.set(path, p);
  order.push(path);
  // Höchstens 40 Videos im Speicher halten.
  while (order.length > 40) {
    const old = order.shift();
    blobs.get(old)?.then((u) => u && URL.revokeObjectURL(u));
    blobs.delete(old);
  }
  p.then((u) => { if (!u) { blobs.delete(path); } });
  return p;
}

// ── Kamera / Foto ──
/** Gibt { base64, mime } zurück (verkleinert auf max. 1280 px) oder null. source: 'camera' | 'gallery' */
export async function pickPhoto(source = 'camera') {
  if (native?.pickPhoto) {
    const m = await call((id) => native.pickPhoto(id, source));
    return m?.base64 ? { base64: m.base64, mime: m.mime ?? 'image/jpeg' } : null;
  }
  return new Promise((resolve) => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = 'image/*';
    if (source === 'camera') input.capture = 'environment';
    input.onchange = async () => {
      const f = input.files?.[0];
      if (!f) return resolve(null);
      resolve(await shrink(f));
    };
    input.click();
  });
}
async function shrink(file) {
  const img = await createImageBitmap(file);
  const s = Math.min(1, 1280 / Math.max(img.width, img.height));
  const c = document.createElement('canvas');
  c.width = Math.round(img.width * s); c.height = Math.round(img.height * s);
  c.getContext('2d').drawImage(img, 0, 0, c.width, c.height);
  const url = c.toDataURL('image/jpeg', 0.86);
  return { base64: url.split(',')[1], mime: 'image/jpeg' };
}

// ── Barcode ──
/** Scannt einen Produkt-Barcode. Gibt { code } zurück, null bei Abbruch, { error } wenn es nicht geht. */
export async function scanBarcode() {
  if (native?.scanBarcode) {
    const m = await call((id) => native.scanBarcode(id));
    if (m?.code) return { code: m.code };
    return m?.error ? { error: m.error } : null;
  }
  return { error: 'browser' };
}

// ── Sprachansagen (Training) in Rems Stimme ──
// Vorab erzeugte Clips (tools/fit_voice) in assets/fit-web/voice, Index: Schlüssel → Datei.
// Ansagen werden aus Bausteinen zusammengesetzt (Übung, Menge, »je Seite«). Fehlt ein Clip, bleibt
// es still — bewusst KEINE Gerätestimme (Kadir mag die Computerstimme nicht).
//
// Abgespielt wird über WebAudio, NICHT über <audio>: Ein Audio-Element holt sich in der WebView den
// vollen Audio-Fokus, und jede Musik im Hintergrund bleibt stehen. WebAudio fordert keinen Fokus an;
// während Rem spricht (oder ein Ton kommt), wird die Musik nur leiser gedreht (duck) und danach
// wieder laut.
let voiceOn = local.get('voice', true);
let voiceIndex = null;
const voiceIndexReady = fetch('/voice/index.json').then((r) => (r.ok ? r.json() : {})).then((j) => { voiceIndex = j; }).catch(() => { voiceIndex = {}; });

function audioCtx() {
  const ctx = beep.ctx ?? (beep.ctx = new (window.AudioContext || window.webkitAudioContext)());
  if (ctx.state === 'suspended') ctx.resume().catch(() => {});
  return ctx;
}

// Musik leiser: gezählt, mit kurzem Nachlauf — zwischen zwei Bausteinen soll sie nicht hochpumpen.
let ducks = 0;
let duckOffTimer = null;
function duckOn() {
  ducks++;
  clearTimeout(duckOffTimer);
  if (ducks === 1) native?.duck?.(true);
}
function duckOff(after = 450) {
  ducks = Math.max(0, ducks - 1);
  if (ducks > 0) return;
  clearTimeout(duckOffTimer);
  duckOffTimer = setTimeout(() => { if (ducks === 0) native?.duck?.(false); }, after);
}

const clipCache = new Map();
function loadClip(id) {
  if (!clipCache.has(id)) {
    clipCache.set(id, fetch(`/voice/${id}.ogg`).then((r) => r.arrayBuffer()).then((b) => audioCtx().decodeAudioData(b)).catch((e) => { clipCache.delete(id); throw e; }));
    if (clipCache.size > 120) clipCache.delete(clipCache.keys().next().value);
  }
  return clipCache.get(id);
}
/** Einen Clip abspielen → Promise (true = zu Ende gespielt). Abbrechbar über voice.stop(). */
let current = null;
let token = 0;
function playClip(id, t) {
  return loadClip(id).then((buf) => new Promise((resolve) => {
    if (t !== token) { resolve(false); return; }
    const ctx = audioCtx();
    const src = ctx.createBufferSource();
    src.buffer = buf;
    src.connect(ctx.destination);
    let settled = false;
    const done = (ok) => { if (settled) return; settled = true; if (current === src) current = null; resolve(ok && t === token); };
    src.onended = () => done(true);
    src.__done = () => { try { src.stop(); } catch { /* */ } done(false); };
    current = src;
    src.start();
  })).catch(() => false);
}

export const voice = {
  get on() { return voiceOn; },
  set(on) { voiceOn = on; local.set('voice', on); if (!on) this.stop(); },
  /** Bausteine nacheinander sprechen; eine neue Ansage ersetzt die laufende. */
  async say(...keys) {
    if (!voiceOn) return;
    const list = keys.flat().filter(Boolean);
    if (!list.length) return;
    if (!voiceIndex) await voiceIndexReady;
    this.stop();
    const t = token;
    const ids = list.map((k) => voiceIndex?.[k]).filter(Boolean);
    if (!ids.length) return;
    ids.forEach((id) => loadClip(id).catch(() => {})); // vorab dekodieren — keine Lücken
    duckOn();
    try {
      for (const id of ids) {
        if (t !== token) break;
        await playClip(id, t);
        await new Promise((r) => setTimeout(r, 90));
      }
    } finally { duckOff(); }
  },
  stop() {
    token++;
    current?.__done?.();
    current = null;
  },
  /** Gibt es diesen Satz in Rems Stimme? (Achtsamkeit: Schlüssel »t:<Satz>«) */
  has(text) { return !!voiceIndex?.[`t:${text}`]; },
  ready: () => voiceIndexReady,
  /**
   * Einen Satz sprechen und warten, bis er zu Ende ist (Meditation, Yoga, Affirmation) — unabhängig
   * vom Ansagen-Schalter des Trainings. → true, wenn zu Ende gesprochen; false, wenn kein Clip da
   * ist oder abgebrochen wurde.
   */
  async speak(text) {
    await voiceIndexReady;
    const id = voiceIndex?.[`t:${text}`];
    this.stop();
    if (!id) return false;
    const t = token;
    duckOn();
    try { return await playClip(id, t); } finally { duckOff(); }
  },
  /** Mengen-Baustein: »12 Wdh« bzw. »45 s« (auf 5 s gerundet, wie die Clips). */
  amount(amount, unit) {
    if (unit === 'sec') return `${Math.min(180, Math.max(5, Math.round(amount / 5) * 5))} s`;
    return `${Math.min(60, Math.max(1, Math.round(amount)))} Wdh`;
  },
};

export function beep(kind = 'tick') {
  if (native?.haptic) native.haptic(kind);
  try {
    const ctx = audioCtx();
    const o = ctx.createOscillator();
    const g = ctx.createGain();
    o.frequency.value = kind === 'go' ? 880 : kind === 'end' ? 660 : 520;
    g.gain.setValueAtTime(0.0001, ctx.currentTime);
    g.gain.exponentialRampToValueAtTime(0.25, ctx.currentTime + 0.01);
    g.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + (kind === 'go' ? 0.35 : 0.14));
    o.connect(g).connect(ctx.destination);
    duckOn();
    o.start(); o.stop(ctx.currentTime + 0.4);
    o.onended = () => duckOff(700);
  } catch { /* stumm */ }
}

/** Klangschale: weicher Gong aus mehreren Teiltönen mit langem Ausklang (Meditation, Yoga). */
export function bell(strength = 1) {
  if (native?.haptic) native.haptic('tick');
  try {
    const ctx = audioCtx();
    const now = ctx.currentTime;
    const out = ctx.createGain();
    out.gain.value = 0.22 * strength;
    out.connect(ctx.destination);
    duckOn();
    setTimeout(() => duckOff(300), 4500);
    for (const [f, g, d] of [[432, 1, 7], [864.5, 0.45, 5], [1296.8, 0.22, 3.5], [1735, 0.12, 2.4], [2160, 0.06, 1.6]]) {
      const o = ctx.createOscillator();
      const v = ctx.createGain();
      o.type = 'sine';
      o.frequency.value = f;
      v.gain.setValueAtTime(0.0001, now);
      v.gain.exponentialRampToValueAtTime(g, now + 0.015);
      v.gain.exponentialRampToValueAtTime(0.0001, now + d);
      o.connect(v).connect(out);
      o.start(now); o.stop(now + d + 0.1);
    }
  } catch { /* stumm */ }
}

export function haptic(kind = 'tick') { if (native?.haptic) native.haptic(kind); else navigator.vibrate?.(kind === 'tick' ? 12 : 30); }

/**
 * Display wach halten, solange irgendeine Übung läuft (Training, Skill, Yoga, Atem, Meditation).
 * Gezählt statt An/Aus: Beim Wechsel zwischen zwei Übungsbildschirmen darf das Aufräumen des alten
 * das »an« des neuen nicht wieder löschen. Nach dem Zurückkehren in die App wird es neu gesetzt.
 */
let awake = 0;
function applyAwake() {
  const on = awake > 0;
  if (native?.keepAwake) { native.keepAwake(on); return; }
  try {
    if (on && navigator.wakeLock && !keepAwake.lock) navigator.wakeLock.request('screen').then((l) => { keepAwake.lock = l; l.addEventListener?.('release', () => { keepAwake.lock = null; }); }).catch(() => {});
    else if (!on) { keepAwake.lock?.release?.(); keepAwake.lock = null; }
  } catch { /* */ }
}
export function keepAwake(on) {
  awake = Math.max(0, awake + (on ? 1 : -1));
  applyAwake();
}
if (typeof document !== 'undefined') document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible' && awake > 0) applyAwake(); });

export function openVideo(id, title = '') {
  if (native?.openVideo) { native.openVideo(id, title); return; }
  window.open(`https://www.youtube.com/watch?v=${id}`, '_blank');
}

export function exitArea() { if (native?.exit) native.exit(); else history.back(); }
