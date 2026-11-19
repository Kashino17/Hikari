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
      return v == null || v === '' ? fallback : JSON.parse(v);
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

// ── Sprachansagen (Training) ──
let voiceOn = local.get('voice', true);
export const voice = {
  get on() { return voiceOn; },
  set(on) { voiceOn = on; local.set('voice', on); if (!on) this.stop(); },
  say(text) {
    if (!voiceOn || !text) return;
    if (native?.ttsSpeak) { native.ttsSpeak(`v${++seq}`, text, 'de-DE', 1.0); return; }
    try {
      speechSynthesis.cancel();
      const u = new SpeechSynthesisUtterance(text);
      u.lang = 'de-DE';
      speechSynthesis.speak(u);
    } catch { /* ohne Stimme */ }
  },
  stop() { try { if (native?.ttsStop) native.ttsStop(); else speechSynthesis.cancel(); } catch { /* */ } },
};

export function beep(kind = 'tick') {
  if (native?.haptic) native.haptic(kind);
  try {
    const ctx = beep.ctx ?? (beep.ctx = new (window.AudioContext || window.webkitAudioContext)());
    const o = ctx.createOscillator();
    const g = ctx.createGain();
    o.frequency.value = kind === 'go' ? 880 : kind === 'end' ? 660 : 520;
    g.gain.setValueAtTime(0.0001, ctx.currentTime);
    g.gain.exponentialRampToValueAtTime(0.25, ctx.currentTime + 0.01);
    g.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + (kind === 'go' ? 0.35 : 0.14));
    o.connect(g).connect(ctx.destination);
    o.start(); o.stop(ctx.currentTime + 0.4);
  } catch { /* stumm */ }
}

export function keepAwake(on) {
  if (native?.keepAwake) { native.keepAwake(!!on); return; }
  try {
    if (on && navigator.wakeLock) navigator.wakeLock.request('screen').then((l) => { keepAwake.lock = l; }).catch(() => {});
    else keepAwake.lock?.release?.();
  } catch { /* */ }
}

/** YouTube-Video im Player der App öffnen (im Browser: neuer Tab). */
export function openVideo(id, title = '') {
  if (native?.openVideo) { native.openVideo(id, title); return; }
  window.open(`https://www.youtube.com/watch?v=${id}`, '_blank');
}

export function exitArea() { if (native?.exit) native.exit(); else history.back(); }
