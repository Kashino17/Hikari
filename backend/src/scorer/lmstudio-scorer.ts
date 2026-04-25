import type { Score, ScoredVideo, ScoreInput, Scorer } from "./types.js";

export interface LMStudioScorerOptions {
  baseUrl: string;
  model: string;
}

const JSON_SCHEMA = {
  name: "video_score",
  strict: true,
  schema: {
    type: "object",
    additionalProperties: false,
    required: [
      "overallScore",
      "category",
      "clickbaitRisk",
      "educationalValue",
      "emotionalManipulation",
      "reasoning",
    ],
    properties: {
      overallScore: { type: "integer", minimum: 0, maximum: 100 },
      category: {
        type: "string",
        enum: [
          "science",
          "tech",
          "philosophy",
          "history",
          "math",
          "art",
          "language",
          "society",
          "other",
        ],
      },
      clickbaitRisk: { type: "integer", minimum: 0, maximum: 10 },
      educationalValue: { type: "integer", minimum: 0, maximum: 10 },
      emotionalManipulation: { type: "integer", minimum: 0, maximum: 10 },
      reasoning: { type: "string" },
    },
  },
} as const;

export class LMStudioScorer implements Scorer {
  readonly name = "lmstudio";
  constructor(private readonly opts: LMStudioScorerOptions) {}

  async score(input: ScoreInput): Promise<ScoredVideo> {
    const userMessage =
      `TITLE: ${input.title}\n\nDURATION: ${input.durationSeconds}s\n\n` +
      `DESCRIPTION:\n${input.description.slice(0, 1000)}\n\n` +
      (input.transcript
        ? `TRANSCRIPT (first 2000 chars):\n${input.transcript.slice(0, 2000)}`
        : "TRANSCRIPT: (not available — score on title+description alone; apply stricter thresholds)");

    const res = await fetch(`${this.opts.baseUrl}/v1/chat/completions`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        model: this.opts.model,
        messages: [
          { role: "system", content: input.systemPrompt },
          { role: "user", content: userMessage },
        ],
        response_format: { type: "json_schema", json_schema: JSON_SCHEMA },
        temperature: 0.2,
        stream: false,
      }),
    });
    if (!res.ok) {
      throw new Error(`LM Studio request failed: ${res.status} ${await res.text()}`);
    }
    const body = (await res.json()) as {
      choices: { message: { content: string; reasoning_content?: string } }[];
    };
    const msg = body.choices[0]?.message;
    if (!msg) {
      throw new Error("LM Studio returned no choices");
    }
    // Reasoning models (Qwen3, DeepSeek-R1, QwQ) put structured output in
    // reasoning_content when thinking mode is on. Prefer content; fall back.
    const raw = msg.content?.trim() ? msg.content : (msg.reasoning_content ?? "");
    if (!raw.trim()) {
      throw new Error("LM Studio returned empty content and reasoning_content");
    }
    const score = JSON.parse(raw) as Score;
    return { score, modelUsed: this.opts.model };
  }
}
