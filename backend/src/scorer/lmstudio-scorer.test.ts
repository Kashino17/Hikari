import { afterEach, describe, expect, it, vi } from "vitest";
import { LMStudioScorer } from "./lmstudio-scorer.js";

describe("LMStudioScorer", () => {
  afterEach(() => vi.restoreAllMocks());

  it("POSTs to /v1/chat/completions with json_schema response_format", async () => {
    const fetchSpy = vi.spyOn(global, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({
          choices: [
            {
              message: {
                content: JSON.stringify({
                  overallScore: 72,
                  category: "science",
                  clickbaitRisk: 2,
                  educationalValue: 8,
                  emotionalManipulation: 1,
                  reasoning: "Solid explanation of entropy.",
                }),
              },
            },
          ],
        }),
        { status: 200 },
      ),
    );

    const scorer = new LMStudioScorer({
      baseUrl: "http://localhost:1234",
      model: "qwen3-27b",
    });
    const result = await scorer.score({
      title: "What is entropy really?",
      description: "",
      transcript: null,
      durationSeconds: 400,
    });

    expect(result.modelUsed).toBe("qwen3-27b");
    expect(result.score.overallScore).toBe(72);

    const [url, init] = fetchSpy.mock.calls[0]!;
    expect(url).toBe("http://localhost:1234/v1/chat/completions");
    const body = JSON.parse((init as RequestInit).body as string) as {
      model: string;
      response_format: { type: string };
      messages: unknown[];
    };
    expect(body.model).toBe("qwen3-27b");
    expect(body.response_format.type).toBe("json_schema");
  });

  it("throws on non-200", async () => {
    vi.spyOn(global, "fetch").mockResolvedValue(new Response("boom", { status: 500 }));
    const scorer = new LMStudioScorer({
      baseUrl: "http://localhost:1234",
      model: "qwen3-27b",
    });
    await expect(
      scorer.score({ title: "x", description: "", transcript: null, durationSeconds: 60 }),
    ).rejects.toThrow(/LM Studio/);
  });

  it("throws when message content is not valid JSON", async () => {
    vi.spyOn(global, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({
          choices: [{ message: { content: "I refuse to comply." } }],
        }),
        { status: 200 },
      ),
    );
    const scorer = new LMStudioScorer({
      baseUrl: "http://localhost:1234",
      model: "qwen3-27b",
    });
    await expect(
      scorer.score({ title: "x", description: "", transcript: null, durationSeconds: 60 }),
    ).rejects.toThrow();
  });

  it("falls back to reasoning_content when content is empty (Qwen3/DeepSeek-R1 quirk)", async () => {
    vi.spyOn(global, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({
          choices: [
            {
              message: {
                content: "",
                reasoning_content: JSON.stringify({
                  overallScore: 68,
                  category: "math",
                  clickbaitRisk: 2,
                  educationalValue: 8,
                  emotionalManipulation: 1,
                  reasoning: "Classic probability puzzle.",
                }),
              },
            },
          ],
        }),
        { status: 200 },
      ),
    );
    const scorer = new LMStudioScorer({
      baseUrl: "http://localhost:1234",
      model: "qwen/qwen3.6-27b",
    });
    const result = await scorer.score({
      title: "Monty Hall",
      description: "",
      transcript: null,
      durationSeconds: 380,
    });
    expect(result.score.overallScore).toBe(68);
    expect(result.score.category).toBe("math");
  });
});
