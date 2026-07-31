import { z } from "zod";
import { sampleFrames, type SampledFrame } from "./frame-sampler.js";

export class QwenNetworkError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "QwenNetworkError";
  }
}

export type DisplayMode = "smart-crop" | "fit";

export interface DisplaySegment {
  startSec: number;
  endSec: number;
  mode: "smart-crop" | "fit";
  focus?: { x: number; y: number; w: number; h: number };
}

export interface ClipSpec {
  startSec: number;
  endSec: number;
  focus: { x: number; y: number; w: number; h: number };
  reason: string;
  // "smart-crop" → crop tightly around focus (default; faces, action).
  // "fit"        → show entire 16:9 frame in 9:16 with blur-bg padding
  //                (text-heavy slides, multi-column UI, banners).
  displayMode: DisplayMode;
  // Optional per-clip sub-segment switching. When present, renderer uses
  // these instead of the clip-level displayMode for the full duration.
  displaySegments?: DisplaySegment[];
}

export interface AnalyzeInput {
  filePath: string;
  videoId: string;
  durationSec: number;
  transcript?: string;
}

export interface AnalyzerConfig {
  provider: "lmstudio" | "ollama";
  baseUrl: string;
  model: string;
  fetchFn?: typeof fetch;
  sampleFn?: (filePath: string, durationSec: number) => Promise<SampledFrame[]>;
}

// Bumped from 20 → 30: too-short clips were context-less and confusing for
// the user. 30s gives Qwen room to include setup + the actual point.
const MIN_CLIP_SEC = 30;
const MAX_CLIP_SEC = 90;

const rawSegmentSchema = z.object({
  start_sec: z.number(),
  end_sec: z.number(),
  mode: z.enum(["smart-crop", "fit"]),
  focus: z.object({
    x: z.number().min(0).max(1),
    y: z.number().min(0).max(1),
    w: z.number().min(0).max(1),
    h: z.number().min(0).max(1),
  }).optional(),
});

const rawSpecSchema = z.object({
  start_sec: z.number(),
  end_sec: z.number(),
  reason: z.string(),
  // Legacy fields. Kept optional so older Qwen outputs (and the rare case
  // where Qwen still includes them) parse, but they're ignored at render
  // time — the renderer always uses fit-mode now.
  focus: z.object({
    x: z.number().min(0).max(1),
    y: z.number().min(0).max(1),
    w: z.number().min(0).max(1),
    h: z.number().min(0).max(1),
  }).optional(),
  display_mode: z.enum(["smart-crop", "fit"]).optional(),
  display_segments: z.array(rawSegmentSchema).optional(),
});
const rawSpecArraySchema = z.array(rawSpecSchema);

async function callQwen(
  input: AnalyzeInput,
  prompt: string,
  config: AnalyzerConfig,
  retryHint = "",
): Promise<string> {
  const fetchFn = config.fetchFn ?? fetch;
  const url = `${config.baseUrl}/v1/chat/completions`;
  const systemPrompt = retryHint
    ? `${prompt}\n\nWICHTIG: ${retryHint}`
    : prompt;

  // Sample frames unless caller injected them via the optional sampleFn (for tests)
  const frames = config.sampleFn
    ? await config.sampleFn(input.filePath, input.durationSec)
    : await sampleFrames(input.filePath, input.durationSec);

  const userTextParts: string[] = [
    `Analysiere dieses Video. Dauer: ${input.durationSec}s.`,
    `Du siehst ${frames.length} Frames, gleichmäßig verteilt im Video.`,
    `Frame-Timestamps (Sekunden): ${frames.map((f) => f.timestampSec).join(", ")}.`,
  ];
  if (input.transcript && input.transcript.length > 0) {
    userTextParts.push(`\nTranskript:\n${input.transcript.slice(0, 8000)}`);
  }

  const userContent: Array<unknown> = [
    { type: "text", text: userTextParts.join("\n") },
  ];
  for (const f of frames) {
    userContent.push({
      type: "image_url",
      image_url: { url: f.base64DataUri },
    });
  }

  const body = {
    model: config.model,
    messages: [
      { role: "system", content: systemPrompt },
      { role: "user", content: userContent },
    ],
    temperature: 0.2,
    stream: false,
  };

  let res: Response;
  try {
    res = await fetchFn(url, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(body),
    });
  } catch (e) {
    throw new QwenNetworkError(`Cannot reach Qwen at ${config.baseUrl}: ${(e as Error).message}`);
  }
  if (!res.ok) {
    const text = await res.text();
    if (res.status >= 500 && res.status < 600) {
      throw new QwenNetworkError(`Qwen returned ${res.status}: ${text}`);
    }
    throw new Error(`Qwen request failed: ${res.status} ${text}`);
  }
  const json = (await res.json()) as { choices: Array<{ message: { content: string } }> };
  const content = json.choices[0]?.message?.content;
  if (!content) throw new Error("Qwen returned no content");
  return content.trim();
}

function parseAndValidate(content: string): z.infer<typeof rawSpecArraySchema> {
  const stripped = content
    .replace(/^```(?:json)?\s*/i, "")
    .replace(/\s*```\s*$/, "")
    .trim();
  let parsed: unknown;
  try {
    parsed = JSON.parse(stripped);
  } catch (e) {
    throw new Error(`invalid JSON from Qwen: ${(e as Error).message}`);
  }
  return rawSpecArraySchema.parse(parsed);
}

const MIN_SEGMENT_SEC = 3;

/**
 * Clean up raw display_segments for a single clip:
 * 1. Sort by startSec
 * 2. Clamp endSec to <= clipDurationSec
 * 3. Merge any segment shorter than 3s into the longer adjacent neighbor
 * 4. Fill gaps: extend segment[i].endSec to segment[i+1].startSec
 * 5. Extend first segment down to 0 if it doesn't start there
 * 6. Extend last segment up to clipDurationSec if it doesn't end there
 * 7. Drop zero-duration segments
 * 8. Return null if fewer than 2 valid segments remain
 */
export function cleanSegments(
  raw: Array<{
    start_sec: number;
    end_sec: number;
    mode: "smart-crop" | "fit";
    focus?: { x: number; y: number; w: number; h: number } | undefined;
  }>,
  clipDurationSec: number,
): DisplaySegment[] | null {
  if (!raw || raw.length === 0) return null;

  // Convert to DisplaySegment with clip-local seconds, sort.
  // Build with conditional focus to satisfy exactOptionalPropertyTypes.
  let segs: DisplaySegment[] = raw
    .map((s) => {
      const base: DisplaySegment = {
        startSec: s.start_sec,
        endSec: Math.min(s.end_sec, clipDurationSec),
        mode: s.mode,
      };
      return s.focus ? { ...base, focus: s.focus } : base;
    })
    .filter((s) => s.endSec > s.startSec)
    .sort((a, b) => a.startSec - b.startSec);

  if (segs.length === 0) return null;

  // Resolve overlaps: if segment[i].endSec > segment[i+1].startSec, trim segment[i]
  for (let i = 0; i < segs.length - 1; i++) {
    if (segs[i]!.endSec > segs[i + 1]!.startSec) {
      segs[i] = { ...segs[i]!, endSec: segs[i + 1]!.startSec };
    }
  }

  // Remove zero-duration after overlap fix
  segs = segs.filter((s) => s.endSec > s.startSec);
  if (segs.length === 0) return null;

  // Extend first segment to start at 0
  if (segs[0]!.startSec > 0) {
    segs[0] = { ...segs[0]!, startSec: 0 };
  }

  // Extend last segment to cover clipDurationSec
  const last = segs[segs.length - 1]!;
  if (last.endSec < clipDurationSec) {
    segs[segs.length - 1] = { ...last, endSec: clipDurationSec };
  }

  // Fill gaps between segments
  for (let i = 0; i < segs.length - 1; i++) {
    if (segs[i]!.endSec < segs[i + 1]!.startSec) {
      segs[i] = { ...segs[i]!, endSec: segs[i + 1]!.startSec };
    }
  }

  // Merge short segments (< MIN_SEGMENT_SEC) into longer adjacent neighbor
  let changed = true;
  while (changed) {
    changed = false;
    for (let i = 0; i < segs.length; i++) {
      const seg = segs[i]!;
      const dur = seg.endSec - seg.startSec;
      if (dur < MIN_SEGMENT_SEC) {
        const prevDur = i > 0 ? segs[i - 1]!.endSec - segs[i - 1]!.startSec : -Infinity;
        const nextDur = i < segs.length - 1 ? segs[i + 1]!.endSec - segs[i + 1]!.startSec : -Infinity;

        if (prevDur >= nextDur && i > 0) {
          // Merge into previous (extend its endSec)
          segs[i - 1] = { ...segs[i - 1]!, endSec: seg.endSec };
          segs.splice(i, 1);
        } else if (i < segs.length - 1) {
          // Merge into next (extend its startSec backward)
          segs[i + 1] = { ...segs[i + 1]!, startSec: seg.startSec };
          segs.splice(i, 1);
        } else if (i > 0) {
          // Last segment, only previous exists
          segs[i - 1] = { ...segs[i - 1]!, endSec: seg.endSec };
          segs.splice(i, 1);
        }
        changed = true;
        break;
      }
    }
  }

  // Drop zero-duration
  segs = segs.filter((s) => s.endSec > s.startSec);

  // If fewer than 2 segments remain, fall back to clip-level display_mode
  if (segs.length < 2) return null;

  return segs;
}

function clampSpecs(raw: z.infer<typeof rawSpecArraySchema>, durationSec: number): ClipSpec[] {
  const out: ClipSpec[] = [];
  for (const r of raw) {
    if (r.start_sec < 0 || r.start_sec >= durationSec) continue;
    if (r.end_sec <= r.start_sec) continue;

    let endSec = Math.min(r.end_sec, durationSec);
    const len = endSec - r.start_sec;
    if (len < MIN_CLIP_SEC) {
      endSec = Math.min(r.start_sec + MIN_CLIP_SEC, durationSec);
      if (endSec - r.start_sec < MIN_CLIP_SEC) continue;
    }
    if (endSec - r.start_sec > MAX_CLIP_SEC) {
      endSec = r.start_sec + MAX_CLIP_SEC;
    }

    // Layout fields are no longer used at render-time. Provide harmless
    // defaults so the rest of the pipeline (worker INSERT, schema) keeps
    // working without churn. The renderer ignores these values.
    out.push({
      startSec: r.start_sec,
      endSec,
      focus: r.focus ?? { x: 0.5, y: 0.5, w: 1, h: 1 },
      reason: r.reason,
      displayMode: "fit",
    });
  }
  return out.sort((a, b) => a.startSec - b.startSec);
}

export async function analyzeVideo(
  input: AnalyzeInput,
  prompt: string,
  config: AnalyzerConfig,
): Promise<ClipSpec[]> {
  let raw: z.infer<typeof rawSpecArraySchema>;
  try {
    raw = parseAndValidate(await callQwen(input, prompt, config));
  } catch (firstErr) {
    if (!/invalid JSON/i.test((firstErr as Error).message)) throw firstErr;
    raw = parseAndValidate(
      await callQwen(input, prompt, config, "Respond with valid JSON only. No prose, no markdown."),
    );
  }
  return clampSpecs(raw, input.durationSec);
}
