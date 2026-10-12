import { describe, expect, it } from "vitest";
import { alignPhonemes } from "./phonemes.js";

describe("alignPhonemes", () => {
  it("perfekte Folge = 100", () => {
    const r = alignPhonemes(["m", "ɨ"], ["m", "ɨ"]);
    expect(r.score).toBe(100);
    expect(r.marks.every((m) => m.status === "ok")).toBe(true);
  });

  it("ɨ → i wird als Ersetzung benannt", () => {
    const r = alignPhonemes(["m", "ɨ", "l", "a"], ["m", "i", "l", "a"]);
    expect(r.score).toBe(75);
    expect(r.marks[1]).toEqual({ ref: "ɨ", user: "i", status: "sub" });
  });

  it("ausgelassene und zusätzliche Phoneme", () => {
    const r = alignPhonemes(["p", "r", "i", "v", "e", "t"], ["p", "i", "v", "e", "t", "s"]);
    expect(r.marks.find((m) => m.ref === "r")?.status).toBe("del");
    expect(r.inserted).toBe(1);
  });

  it("Längenzeichen zählen nicht", () => {
    expect(alignPhonemes(["aː", "b"], ["a", "b"]).score).toBe(100);
  });
});
