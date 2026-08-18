import { describe, expect, it } from "vitest";
import { DEFAULT_FILTER } from "./filter.js";
import { prefilterReason } from "./prefilter.js";

const filter = { ...DEFAULT_FILTER, languages: ["de", "en"] };

describe("prefilterReason — Sprachfeld", () => {
  it("lehnt Videos ab, deren YouTube-Sprache nicht gewünscht ist", () => {
    expect(prefilterReason("Latest Update Today", "", filter, "hi")).toMatch(/Sprache/);
    expect(prefilterReason("Breaking News", "", filter, "ur")).toMatch(/Sprache/);
    expect(prefilterReason("Anime Review", "", filter, "ja")).toMatch(/Sprache/);
  });

  it("lässt gewünschte Sprachen durch — auch mit Regionszusatz", () => {
    expect(prefilterReason("Cool video about AI", "", filter, "en-US")).toBeNull();
    expect(prefilterReason("Video über KI", "", filter, "de-DE")).toBeNull();
    expect(prefilterReason("Ohne Sprachangabe", "", filter, null)).toBeNull();
  });
});

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
