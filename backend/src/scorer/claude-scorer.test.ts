import { beforeEach, describe, expect, it, vi } from "vitest";
import { ClaudeScorer } from "./claude-scorer.js";

const mockCreate = vi.fn();

vi.mock("@anthropic-ai/sdk", () => ({
  default: class {
    messages = { create: mockCreate };
  },
}));

describe("ClaudeScorer", () => {
  beforeEach(() => mockCreate.mockReset());

  it("returns typed Score from tool_use response", async () => {
    mockCreate.mockResolvedValue({
      content: [
        {
          type: "tool_use",
          name: "record_score",
          input: {
            overallScore: 82,
            category: "science",
            clickbaitRisk: 1,
            educationalValue: 9,
            emotionalManipulation: 0,
            reasoning: "Deep dive on prime numbers.",
          },
        },
      ],
    });

    const scorer = new ClaudeScorer({ apiKey: "test", model: "claude-haiku-4-5" });
    const result = await scorer.score({
      title: "Why primes are weird",
      description: "A look at prime distribution.",
      transcript: "Some transcript text...",
      durationSeconds: 420,
    });

    expect(result.modelUsed).toBe("claude-haiku-4-5");
    expect(result.score.overallScore).toBe(82);
    expect(result.score.category).toBe("science");
  });

  it("throws when no tool_use block in response", async () => {
    mockCreate.mockResolvedValue({
      content: [{ type: "text", text: "I refuse." }],
    });
    const scorer = new ClaudeScorer({ apiKey: "test", model: "claude-haiku-4-5" });
    await expect(
      scorer.score({ title: "x", description: "", transcript: null, durationSeconds: 60 }),
    ).rejects.toThrow(/tool_use/);
  });
});
