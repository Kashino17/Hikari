import type { Score, ScoredVideo, ScoreInput, Scorer } from "./types.js";

export interface OllamaScorerOptions {
  baseUrl: string;
  model: string;
}

const JSON_SCHEMA = {
  type: "object",
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
} as const;

export class OllamaScorer implements Scorer {
  readonly name = "ollama";
  constructor(private readonly opts: OllamaScorerOptions) {}

  async score(input: ScoreInput): Promise<ScoredVideo> {
    const userMessage =
      `TITLE: ${input.title}\n\nDURATION: ${input.durationSeconds}s\n\n` +
      `DESCRIPTION:\n${input.description.slice(0, 1000)}\n\n` +
      (input.transcript
        ? `TRANSCRIPT (first 2000 chars):\n${input.transcript.slice(0, 2000)}`
        : "TRANSCRIPT: (not available)");

    const res = await fetch(`${this.opts.baseUrl}/api/chat`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        model: this.opts.model,
        messages: [
          { role: "system", content: input.systemPrompt },
          { role: "user", content: userMessage },
        ],
        format: JSON_SCHEMA,
        stream: false,
      }),
    });
    if (!res.ok) {
      throw new Error(`Ollama request failed: ${res.status} ${await res.text()}`);
    }
    const body = (await res.json()) as { message: { content: string } };
    const score = JSON.parse(body.message.content) as Score;
    return { score, modelUsed: this.opts.model };
  }
}
