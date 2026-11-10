// Satzmelodie, Rhythmus und Tempo aus rohem Audio — reine Autokorrelation.
// Portierung von RussianProsody.kt. Eingabe: Float32Array, mono, 16 kHz.

export const SAMPLE_RATE = 16000;
const FRAME = 640; // 40 ms
const HOP = 160; // 10 ms
const MIN_LAG = 40; // 400 Hz
const MAX_LAG = 228; // ~70 Hz
const POINTS = 40;

/** → {semitones, energy, speechStart, speechEnd, speechSeconds, voicedFrames} */
export function analyze(pcm) {
  const frames = pcm.length < FRAME ? 0 : Math.floor((pcm.length - FRAME) / HOP) + 1;
  const mk = (semitones, energy, speechStart, speechEnd) => ({
    semitones, energy, speechStart, speechEnd,
    speechSeconds: speechEnd <= speechStart ? 0 : ((speechEnd - speechStart) * HOP) / SAMPLE_RATE,
    voicedFrames: semitones.reduce((n, v) => n + (Number.isNaN(v) ? 0 : 1), 0),
  });
  if (frames === 0) return mk(new Float32Array(0), new Float32Array(0), 0, 0);
  const energy = new Float32Array(frames);
  const f0 = new Float32Array(frames).fill(NaN);
  for (let i = 0; i < frames; i++) {
    const off = i * HOP;
    let sum = 0;
    for (let k = 0; k < FRAME; k++) sum += pcm[off + k] * pcm[off + k];
    energy[i] = Math.sqrt(sum / FRAME);
  }
  let peak = 0;
  for (const e of energy) if (e > peak) peak = e;
  const gate = peak * 0.06;
  for (let i = 0; i < frames; i++) {
    if (energy[i] < gate || peak <= 0) continue;
    const off = i * HOP;
    let r0 = 0;
    for (let k = 0; k < FRAME; k++) r0 += pcm[off + k] * pcm[off + k];
    if (r0 <= 1e-9) continue;
    let bestLag = -1;
    let best = 0;
    for (let lag = MIN_LAG; lag <= MAX_LAG; lag++) {
      let r = 0;
      for (let k = 0; k < FRAME - lag; k++) r += pcm[off + k] * pcm[off + k + lag];
      const norm = r / r0;
      if (norm > best) { best = norm; bestLag = lag; }
    }
    if (bestLag > 0 && best > 0.4) f0[i] = SAMPLE_RATE / bestLag;
  }
  medianSmooth(f0);
  const voiced = [...f0].filter((v) => !Number.isNaN(v)).sort((a, b) => a - b);
  const semis = new Float32Array(frames).fill(NaN);
  if (voiced.length) {
    const med = voiced[Math.floor(voiced.length / 2)];
    for (let i = 0; i < frames; i++) if (!Number.isNaN(f0[i])) semis[i] = (12 * Math.log(f0[i] / med)) / Math.LN2;
  }
  let start = energy.findIndex((e) => e >= gate);
  let end = -1;
  for (let i = frames - 1; i >= 0; i--) if (energy[i] >= gate) { end = i; break; }
  if (start < 0) { start = 0; end = 0; }
  return mk(semis, energy, start, end + 1);
}

function medianSmooth(a) {
  const src = a.slice();
  for (let i = 2; i < a.length - 2; i++) {
    const v = [];
    for (let k = i - 2; k <= i + 2; k++) if (!Number.isNaN(src[k])) v.push(src[k]);
    a[i] = v.length >= 3 ? v.sort((x, y) => x - y)[Math.floor(v.length / 2)] : NaN;
  }
}

/** → {melody 0–100, rhythm 0–100, tempoRatio, refCurve, userCurve} oder null (zu wenig Stimme). */
export function compare(ref, user) {
  if (ref.voicedFrames < 8 || user.voicedFrames < 8) return null;
  const rc = curve(ref);
  const uc = curve(user);
  const clamp01 = (x) => Math.min(1, Math.max(0, x));
  const melody = Math.trunc(clamp01(correlation(rc, uc)) * 100);
  const rhythm = Math.trunc(clamp01(correlation(envelope(ref), envelope(user))) * 100);
  const tempoRatio = ref.speechSeconds > 0 ? user.speechSeconds / ref.speechSeconds : 1;
  return { melody, rhythm, tempoRatio, refCurve: rc, userCurve: uc };
}

function bucket(c, p) {
  const span = Math.max(1, c.speechEnd - c.speechStart);
  const from = c.speechStart + Math.floor((p * span) / POINTS);
  const to = Math.max(from + 1, c.speechStart + Math.floor(((p + 1) * span) / POINTS));
  return [from, to];
}

function curve(c) {
  const raw = new Float32Array(POINTS);
  for (let p = 0; p < POINTS; p++) {
    const [from, to] = bucket(c, p);
    let sum = 0;
    let n = 0;
    for (let i = from; i < Math.min(to, c.semitones.length); i++) if (!Number.isNaN(c.semitones[i])) { sum += c.semitones[i]; n++; }
    raw[p] = n === 0 ? NaN : sum / n;
  }
  return fill(raw);
}

function envelope(c) {
  const out = new Float32Array(POINTS);
  for (let p = 0; p < POINTS; p++) {
    const [from, to] = bucket(c, p);
    let sum = 0;
    let n = 0;
    for (let i = from; i < Math.min(to, c.energy.length); i++) { sum += c.energy[i]; n++; }
    out[p] = n === 0 ? 0 : sum / n;
  }
  let mx = 0;
  for (const v of out) if (v > mx) mx = v;
  return mx > 0 ? out.map((v) => v / mx) : out;
}

function fill(a) {
  const out = a.slice();
  const known = [];
  for (let i = 0; i < out.length; i++) if (!Number.isNaN(out[i])) known.push(i);
  if (!known.length) return new Float32Array(a.length);
  for (let i = 0; i < out.length; i++) {
    if (!Number.isNaN(out[i])) continue;
    let prev = null;
    let next = null;
    for (const k of known) { if (k < i) prev = k; else if (k > i && next === null) next = k; }
    if (prev !== null && next !== null) out[i] = out[prev] + ((out[next] - out[prev]) * (i - prev)) / (next - prev);
    else out[i] = prev !== null ? out[prev] : out[next];
  }
  return out;
}

export function correlation(a, b) {
  const n = Math.min(a.length, b.length);
  if (n === 0) return 0;
  let ma = 0;
  let mb = 0;
  for (let i = 0; i < n; i++) { ma += a[i]; mb += b[i]; }
  ma /= n; mb /= n;
  let num = 0;
  let da = 0;
  let db = 0;
  for (let i = 0; i < n; i++) {
    const x = a[i] - ma;
    const y = b[i] - mb;
    num += x * y; da += x * x; db += y * y;
  }
  const den = Math.sqrt(da * db);
  return den < 1e-6 ? 0 : num / den;
}

const de1 = (x) => x.toFixed(1).replace('.', ',');
/** Kurzer deutscher Befund zum Tempo. */
export function tempoText(ratio) {
  if (Math.abs(ratio - 1) <= 0.15) return 'Tempo passt';
  return ratio > 1 ? `Du bist ${de1(ratio)}× langsamer` : `Du bist ${de1(1 / ratio)}× schneller`;
}
