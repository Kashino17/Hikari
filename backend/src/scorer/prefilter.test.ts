import { describe, expect, it } from "vitest";
import { DEFAULT_FILTER } from "./filter.js";
import { prefilterReason } from "./prefilter.js";

const filter = { ...DEFAULT_FILTER, languages: ["de", "en"] };

describe("prefilterReason", () => {
  it("lehnt fremde Schriftsysteme ohne LLM-Aufruf ab", () => {
    expect(prefilterReason("Keralam में बारिश बनी आफत! #monsoon", "", filter)).toMatch(/Schrift/);
    expect(prefilterReason("ทำไมหมอนั่นอยู่ด้วยล่ะ อ้อนรัก", "", filter)).toMatch(/Schrift/);
    expect(prefilterReason("【衝撃】最新のAIニュースまとめ", "", filter)).toMatch(/Schrift/);
  });

  it("lässt deutsche und englische Titel durch", () => {
    expect(prefilterReason("Die verstörendste Idee der KI", "", filter)).toBeNull();
    expect(prefilterReason("How OpenAI's Models Went Rogue", "", filter)).toBeNull();
    // Emojis, Zahlen und Satzzeichen dürfen nicht zum Ausschluss führen.
    expect(prefilterReason("KI-News #47 — 100% krass!", "", filter)).toBeNull();
  });

  it("greift nicht, wenn die passende Sprache erlaubt ist", () => {
    const hindiErlaubt = { ...filter, languages: ["de", "en", "hi"] };
    expect(prefilterReason("Keralam में बारिश बनी आफत!", "", hindiErlaubt)).toBeNull();
  });

  it("einzelne fremde Zeichen im sonst lateinischen Titel sind kein Grund", () => {
    expect(prefilterReason("Anime-Review: 進撃 der Titanen erklärt", "", filter)).toBeNull();
  });
});
