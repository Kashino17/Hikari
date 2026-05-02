import { z } from "zod";

export class QwenNetworkError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "QwenNetworkError";
  }
}

export interface ClipSpec {
  startSec: number;
  endSec: number;
  focus: { x: number; y: number; w: number; h: number };
  reason: string;
}

export interface AnalyzeInput {
  filePath: string;
  videoId: string;
  durationSec: number;
}

export interface AnalyzerConfig {
  provider: "lmstudio" | "ollama";
  baseUrl: string;
  model: string;
  fetchFn?: typeof fetch;
}

const MIN_CLIP_SEC = 20;
const MAX_CLIP_SEC = 90;

const rawSpecSchema = z.object({
  start_sec: z.number(),
  end_sec: z.number(),
  focus: z.object({
    x: z.number().min(0).max(1),
    y: z.number().min(0).max(1),
    w: z.number().min(0).max(1),
    h: z.number().min(0).max(1),
  }),
  reason: z.string(),
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
  const body = {
    model: config.model,
    messages: [
      { role: "system", content: systemPrompt },
      {
        role: "user",
        content: [
          { type: "text", text: `Analysiere dieses Video (Dauer: ${input.durationSec}s).` },
          { type: "video_url", video_url: { url: `file://${input.filePath}` } },
        ],
      },
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
  const json = (await res.json()) as {
    choices: Array<{ message: { content: string } }>;
  };
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
    out.push({
      startSec: r.start_sec,
      endSec,
      focus: r.focus,
      reason: r.reason,
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
