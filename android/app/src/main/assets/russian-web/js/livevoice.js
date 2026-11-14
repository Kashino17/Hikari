// Ton für »Anja live«: das Mikrofon als roher 16-kHz-Ton (so will ihn das Sprachmodell) und die
// Wiedergabe von Anjas Antwort, während sie noch vom Server hereinströmt (24 kHz, 16 Bit, mono).
import { audioContext } from './platform.js';

const TARGET_RATE = 16000;
const OUT_RATE = 24000;
/** Länger als das spricht ein Anfänger nicht am Stück — und der Server nimmt nicht mehr an. */
export const MAX_RECORD_S = 30;

/** Mittelt auf die Zielrate herunter (linear dazwischen): reicht für Sprache und kostet nichts. */
export function downsample(input, fromRate, toRate = TARGET_RATE) {
  if (fromRate === toRate) return input;
  const ratio = fromRate / toRate;
  const out = new Float32Array(Math.floor(input.length / ratio));
  for (let i = 0; i < out.length; i++) {
    const a = Math.floor(i * ratio);
    const b = Math.min(input.length, Math.max(a + 1, Math.floor((i + 1) * ratio)));
    let sum = 0;
    for (let k = a; k < b; k++) sum += input[k];
    out[i] = sum / (b - a);
  }
  return out;
}

export function toPcm16(float32) {
  const out = new Int16Array(float32.length);
  for (let i = 0; i < float32.length; i++) { const v = Math.max(-1, Math.min(1, float32[i])); out[i] = v < 0 ? v * 0x8000 : v * 0x7fff; }
  return out;
}

function base64(bytes) {
  let s = '';
  for (let i = 0; i < bytes.length; i += 0x8000) s += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000));
  return btoa(s);
}

export const mic = {
  available: () => !!(navigator.mediaDevices?.getUserMedia && audioContext()),

  /** → { level(): 0..1, seconds(), stop(): Promise<{audio, rate, seconds}>, cancel() } */
  async start() {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true, channelCount: 1 } });
    const ac = audioContext();
    const src = ac.createMediaStreamSource(stream);
    // ScriptProcessor ist alt, läuft aber überall gleich — auch in der WebView der App.
    const tap = ac.createScriptProcessor(4096, 1, 1);
    const mute = ac.createGain();
    mute.gain.value = 0;
    const parts = [];
    let samples = 0;
    let peak = 0;
    tap.onaudioprocess = (e) => {
      const data = e.inputBuffer.getChannelData(0);
      if (samples / ac.sampleRate >= MAX_RECORD_S) return;
      parts.push(new Float32Array(data));
      samples += data.length;
      let s = 0;
      for (const v of data) s += v * v;
      peak = Math.min(1, Math.sqrt(s / data.length) * 4);
    };
    src.connect(tap); tap.connect(mute); mute.connect(ac.destination);
    const release = () => { try { src.disconnect(); tap.disconnect(); mute.disconnect(); } catch { /* egal */ } stream.getTracks().forEach((t) => t.stop()); };
    return {
      level: () => peak,
      seconds: () => samples / ac.sampleRate,
      cancel: release,
      async stop() {
        release();
        const all = new Float32Array(samples);
        let at = 0;
        for (const p of parts) { all.set(p, at); at += p.length; }
        const pcm = toPcm16(downsample(all, ac.sampleRate));
        return { audio: base64(new Uint8Array(pcm.buffer)), rate: TARGET_RATE, seconds: pcm.length / TARGET_RATE };
      },
    };
  },
};

// ── Wiedergabe ─────────────────────────────────────────────────────────────

let playing = null;

/** Hält an, was gerade läuft (Anjas Antwort oder eine Wiederholung). */
export function stopPlayback() {
  playing?.stop();
  playing = null;
}

function player(onEnd) {
  const ac = audioContext();
  const nodes = new Set();
  let at = ac.currentTime + 0.06;
  let closed = false;
  let stopped = false;
  const finish = () => { if (!stopped && closed && nodes.size === 0) { stopped = true; onEnd?.(); } };
  return {
    push(int16) {
      if (stopped || !int16.length) return;
      const buf = ac.createBuffer(1, int16.length, OUT_RATE);
      const ch = buf.getChannelData(0);
      for (let i = 0; i < int16.length; i++) ch[i] = int16[i] / 0x8000;
      const node = ac.createBufferSource();
      node.buffer = buf;
      node.connect(ac.destination);
      at = Math.max(at, ac.currentTime + 0.02);
      node.start(at);
      at += buf.duration;
      nodes.add(node);
      node.onended = () => { nodes.delete(node); finish(); };
    },
    /** Es kommt nichts mehr: `onEnd` meldet sich, wenn der letzte Ton verklungen ist. */
    close() { closed = true; finish(); },
    stop() { if (stopped) return; stopped = true; for (const n of nodes) { try { n.onended = null; n.stop(); } catch { /* schon aus */ } } nodes.clear(); onEnd?.(); },
  };
}

/**
 * Spielt einen Ton-Strom vom Server, sobald die ersten Stücke da sind.
 * → Promise<Int16Array> mit dem ganzen Ton (zum Wiederholen); `onStart` beim ersten Stück.
 */
export async function playStream(url, { onStart, onEnd } = {}) {
  stopPlayback();
  const p = player(() => { if (playing === p) playing = null; onEnd?.(); });
  playing = p;
  const kept = [];
  let started = false;
  let odd = null; // ein halbes Sample am Ende eines Stücks gehört zum nächsten
  const take = (bytes) => {
    if (!bytes?.length) return;
    let data = bytes;
    if (odd) { data = new Uint8Array(bytes.length + 1); data[0] = odd[0]; data.set(bytes, 1); odd = null; }
    if (data.length % 2) { odd = data.slice(-1); data = data.subarray(0, data.length - 1); }
    if (!data.length) return;
    const pcm = new Int16Array(data.buffer.slice(data.byteOffset, data.byteOffset + data.length));
    kept.push(pcm);
    if (!started) { started = true; onStart?.(); }
    p.push(pcm);
  };
  try {
    const res = await fetch(url, { cache: 'no-store' });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    if (res.body?.getReader) {
      const reader = res.body.getReader();
      for (;;) { const { done, value } = await reader.read(); if (done) break; take(value); }
    } else take(new Uint8Array(await res.arrayBuffer()));
  } finally {
    p.close();
  }
  const all = new Int16Array(kept.reduce((n, k) => n + k.length, 0));
  let at = 0;
  for (const k of kept) { all.set(k, at); at += k.length; }
  return all;
}

/** Spielt gemerkten Ton noch einmal. */
export function playPcm(int16, { onEnd } = {}) {
  stopPlayback();
  const p = player(() => { if (playing === p) playing = null; onEnd?.(); });
  playing = p;
  p.push(int16);
  p.close();
}
