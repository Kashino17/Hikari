import { describe, expect, it, vi } from "vitest";
import { summarizeContext } from "./context-summarizer.js";

const captionsToTranscript = (text: string) =>
  text.split(" ").map((w, i) => ({ start: i * 0.5, end: i * 0.5 + 0.4, text: w }));

describe("summarizeContext", () => {
  it("returns null when captions are empty or too short", async () => {
    const fetchFn = vi.fn();
    expect(await summarizeContext([], { baseUrl: "http://x", model: "q", fetchFn })).toBeNull();
    expect(await summarizeContext(captionsToTranscript("Ja nein"), { baseUrl: "http://x", model: "q", fetchFn })).toBeNull();
    expect(fetchFn).not.toHaveBeenCalled();
  });

  it("calls Qwen with the joined transcript and returns the summary", async () => {
    const fetchFn = vi.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => ({
        choices: [{ message: { content: "Es geht um Multi-Hop-Reasoning. Der Sprecher zeigt ein Beispiel mit GPT-5." } }],
      }),
    } as Response));
    const captions = captionsToTranscript("Multi Hop Reasoning ist die Fähigkeit von GPT-5 mehrere Schritte vorauszudenken hier ein Beispiel das zeigt es");
    const out = await summarizeContext(captions, { baseUrl: "http://x", model: "q", fetchFn });
    expect(out).toBe("Es geht um Multi-Hop-Reasoning. Der Sprecher zeigt ein Beispiel mit GPT-5.");
    const sentBody = JSON.parse((fetchFn.mock.calls[0]![1] as { body: string }).body);
    expect(sentBody.messages[1].content).toContain("Multi Hop Reasoning ist die Fähigkeit");
  });

  it("strips surrounding quotes/backticks from Qwen output", async () => {
    const fetchFn = vi.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => ({ choices: [{ message: { content: '"Test summary"' } }] }),
    } as Response));
    const out = await summarizeContext(
      captionsToTranscript("Lorem ipsum dolor sit amet consectetur adipiscing"),
      { baseUrl: "http://x", model: "q", fetchFn },
    );
    expect(out).toBe("Test summary");
  });
});
