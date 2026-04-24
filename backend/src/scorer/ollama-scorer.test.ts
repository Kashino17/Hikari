import { afterEach, describe, expect, it, vi } from "vitest";
import { OllamaScorer } from "./ollama-scorer.js";

describe("OllamaScorer", () => {
  afterEach(() => vi.restoreAllMocks());

  it("POSTs to /api/chat and parses JSON-mode response", async () => {
    const fetchSpy = vi.spyOn(global, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({
          message: {
            content: JSON.stringify({
              overallScore: 75,
              category: "tech",
              clickbaitRisk: 2,
              educationalValue: 8,
              emotionalManipulation: 1,
              reasoning: "Clear tutorial on rust.",
            }),
          },
        }),
        { status: 200 },
      ),
    );

    const scorer = new OllamaScorer({ baseUrl: "http://localhost:11434", model: "qwen2.5:14b" });
    const result = await scorer.score({
      title: "Rust lifetimes explained",
      description: "",
      transcript: null,
      durationSeconds: 300,
    });

    expect(result.modelUsed).toBe("qwen2.5:14b");
    expect(result.score.overallScore).toBe(75);
    expect(fetchSpy).toHaveBeenCalledWith(
      "http://localhost:11434/api/chat",
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("throws on non-200", async () => {
    vi.spyOn(global, "fetch").mockResolvedValue(new Response("boom", { status: 500 }));
    const scorer = new OllamaScorer({ baseUrl: "http://localhost:11434", model: "qwen2.5:14b" });
    await expect(
      scorer.score({ title: "x", description: "", transcript: null, durationSeconds: 60 }),
    ).rejects.toThrow(/Ollama/);
  });
});
