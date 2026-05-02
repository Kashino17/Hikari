import { execa } from "execa";

export interface SampledFrame {
  timestampSec: number;
  base64DataUri: string;  // "data:image/jpeg;base64,..."
}

const MIN_FRAMES = 6;
const MAX_FRAMES = 16;
const FRAME_INTERVAL_SEC = 60;
const FRAME_WIDTH_PX = 720;  // bumped from 480 — Qwen needs the detail to
                              // distinguish faces / UI elements / slide content
                              // when picking focus. 720x405 ≈ 50KB jpg @ q70.
const JPEG_QUALITY = 4;  // ffmpeg -q:v scale; 2 (best) – 31 (worst); 4 ≈ q70

export function pickFrameCount(durationSec: number): number {
  const target = Math.round(durationSec / FRAME_INTERVAL_SEC);
  return Math.min(MAX_FRAMES, Math.max(MIN_FRAMES, target));
}

/**
 * Pick N evenly-spaced timestamps in (0, duration). Excludes start/end so we
 * don't sample title cards or end-screen black frames.
 *
 *   pickTimestamps(600, 6) → [86, 171, 257, 343, 429, 514]
 */
export function pickTimestamps(durationSec: number, count: number): number[] {
  const step = durationSec / (count + 1);
  const out: number[] = [];
  for (let i = 1; i <= count; i++) out.push(Math.round(step * i));
  return out;
}

/**
 * Extract one frame at timestampSec, scaled to FRAME_WIDTH_PX wide, as base64
 * JPEG. Uses ffmpeg via execa; relies on ffmpeg being on PATH (yt-dlp pulls it
 * in already).
 */
export async function extractFrame(
  filePath: string,
  timestampSec: number,
): Promise<string> {
  const result = await execa("ffmpeg", [
    "-ss", String(timestampSec),
    "-i", filePath,
    "-frames:v", "1",
    "-vf", `scale=${FRAME_WIDTH_PX}:-2`,
    "-q:v", String(JPEG_QUALITY),
    "-f", "image2pipe",
    "-vcodec", "mjpeg",
    "-loglevel", "error",
    "-",
  ], { encoding: "buffer", timeout: 10_000 });
  // execa with encoding: 'buffer' returns Uint8Array (NOT a Node Buffer) in stdout —
  // (Uint8Array).toString("base64") would silently produce decimal-comma-separated
  // bytes, not actual base64. Always wrap with Buffer.from() to guarantee correct
  // base64 output.
  const bytes = result.stdout as Uint8Array;
  return `data:image/jpeg;base64,${Buffer.from(bytes).toString("base64")}`;
}

export async function sampleFrames(
  filePath: string,
  durationSec: number,
): Promise<SampledFrame[]> {
  const count = pickFrameCount(durationSec);
  const timestamps = pickTimestamps(durationSec, count);
  // Sequential to avoid spawning 16 ffmpeg processes simultaneously.
  // ~10s per frame at worst → ~3 min for 16 frames; usually much faster.
  const frames: SampledFrame[] = [];
  for (const t of timestamps) {
    const b64 = await extractFrame(filePath, t);
    frames.push({ timestampSec: t, base64DataUri: b64 });
  }
  return frames;
}
