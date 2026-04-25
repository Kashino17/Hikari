import Anthropic from "@anthropic-ai/sdk";
import type { Score, ScoredVideo, ScoreInput, Scorer } from "./types.js";

const SCORE_TOOL = {
  name: "record_score",
  description: "Record the score for this video.",
  input_schema: {
    type: "object" as const,
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
      reasoning: { type: "string", minLength: 1, maxLength: 500 },
    },
  },
};

export interface ClaudeScorerOptions {
  apiKey: string;
  model: string;
}

export class ClaudeScorer implements Scorer {
  readonly name = "claude";
  private readonly client: Anthropic;
  private readonly model: string;

  constructor(opts: ClaudeScorerOptions) {
    this.client = new Anthropic({ apiKey: opts.apiKey });
    this.model = opts.model;
  }

  async score(input: ScoreInput): Promise<ScoredVideo> {
    const userText = buildUserMessage(input);

    const response = await this.client.messages.create({
      model: this.model,
      max_tokens: 1024,
      system: [
        {
          type: "text",
          text: input.systemPrompt,
          cache_control: { type: "ephemeral" },
        },
      ],
      tools: [SCORE_TOOL as Anthropic.Messages.Tool],
      tool_choice: { type: "tool", name: "record_score" },
      messages: [{ role: "user", content: userText }],
    });

    const block = response.content.find((b) => b.type === "tool_use");
    if (!block || block.type !== "tool_use") {
      throw new Error("Claude did not return a tool_use block");
    }
    return { score: block.input as Score, modelUsed: this.model };
  }
}

function buildUserMessage(input: Omit<ScoreInput, "systemPrompt">): string {
  const transcriptPart = input.transcript
    ? `TRANSCRIPT (first 2000 chars):\n${input.transcript.slice(0, 2000)}`
    : "TRANSCRIPT: (not available — score on title+description alone; apply stricter thresholds)";
  return [
    `TITLE: ${input.title}`,
    `DURATION: ${input.durationSeconds}s`,
    `DESCRIPTION:\n${input.description.slice(0, 1000)}`,
    transcriptPart,
  ].join("\n\n");
}
