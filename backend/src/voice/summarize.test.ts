import { describe, expect, it } from "vitest";
import { VoiceSummarizer, chunkText, extractJsonObject } from "./summarize.js";

const ANSWER = {
  title: "Termin verschoben",
  summary: "Der Kunde hat abgesagt.",
  points: ["Kunde sagt ab"],
  todos: ["Kalkulation schicken"],
  dates: ["Dienstag 10 Uhr"],
  questions: [],
};

function fakeLm(replies: string[]) {
  const bodies: Record<string, unknown>[] = [];
  const impl = (async (_url: string, init?: RequestInit) => {
    bodies.push(JSON.parse(String(init?.body)));
    const content = replies.shift() ?? "";
    return new Response(JSON.stringify({ choices: [{ message: { content } }] }), { status: 200 });
  }) as typeof fetch;
  return { impl, bodies };
}

describe("chunkText", () => {
  it("schneidet an Satzgrenzen und hält die Grenze ein", () => {
    const text = "Erster Satz. Zweiter Satz! Dritter Satz? Vierter Satz.";
    const chunks = chunkText(text, 27);
    expect(chunks).toEqual(["Erster Satz. Zweiter Satz!", "Dritter Satz? Vierter Satz."]);
    expect(chunks.join(" ")).toBe(text);
  });

  it("schneidet überlange Sätze hart", () => {
    expect(chunkText("a".repeat(25), 10)).toEqual(["a".repeat(10), "a".repeat(10), "a".repeat(5)]);
  });
});

describe("extractJsonObject", () => {
  it("ignoriert Denkblöcke und Text drumherum", () => {
    expect(extractJsonObject('<think>{"x":1}</think>Hier: {"a":2} fertig')).toEqual({ a: 2 });
  });
});

describe("VoiceSummarizer", () => {
  it("schickt Modell und TTL mit, damit LM Studio selbst entlädt", async () => {
    const lm = fakeLm([JSON.stringify(ANSWER)]);
    const s = new VoiceSummarizer({
      baseUrl: "http://lm",
      model: "qwen/qwen3.8-27b",
      ttlSeconds: 600,
      fetchImpl: lm.impl,
    });
    expect(await s.summarize("Kurzer Text.")).toEqual(ANSWER);
    expect(lm.bodies).toHaveLength(1);
    expect(lm.bodies[0]).toMatchObject({ model: "qwen/qwen3.8-27b", ttl: 600 });
    expect((lm.bodies[0]!.response_format as { type: string }).type).toBe("json_schema");
  });

  it("fasst lange Transkripte erst abschnittsweise zusammen", async () => {
    const lm = fakeLm(["- Notiz eins", "- Notiz zwei", JSON.stringify(ANSWER)]);
    const s = new VoiceSummarizer({
      baseUrl: "http://lm",
      model: "m",
      ttlSeconds: 600,
      fetchImpl: lm.impl,
      chunkChars: 30,
    });
    await s.summarize("Das ist der erste Satz hier. Und das ist der zweite Satz.");
    expect(lm.bodies).toHaveLength(3);
    const last = lm.bodies[2]!.messages as { content: string }[];
    expect(last[1]!.content).toContain("Abschnitt 1 von 2:\n- Notiz eins");
    expect(last[1]!.content).toContain("Abschnitt 2 von 2:\n- Notiz zwei");
  });

  it("versucht es bei kaputtem JSON ein zweites Mal", async () => {
    const lm = fakeLm(["kein json", JSON.stringify(ANSWER)]);
    const s = new VoiceSummarizer({
      baseUrl: "http://lm",
      model: "m",
      ttlSeconds: 600,
      fetchImpl: lm.impl,
    });
    expect((await s.summarize("Text.")).title).toBe("Termin verschoben");
    expect(lm.bodies).toHaveLength(2);
  });
});
