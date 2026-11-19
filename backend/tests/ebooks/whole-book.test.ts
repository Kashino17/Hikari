import { describe, expect, it } from "vitest";
import type { RawBook } from "../../src/ebooks/persist.js";
import { MIN_WORDS, isWholeBook } from "../../src/ebooks/whole-book.js";

const base: RawBook = {
  source: "gutenberg",
  sourceId: "1",
  title: "Effi Briest",
  authors: ["Theodor Fontane"],
  language: "de",
  subjects: ["DE Prosa"],
  format: "epub",
  fileUrl: "x",
  sourceUrl: "y",
};

describe("isWholeBook", () => {
  it("lässt ein normales Buch durch", () => {
    expect(isWholeBook(base)).toEqual({ ok: true });
  });
  it("verwirft Leseprobe und Auszug", () => {
    expect(isWholeBook({ ...base, title: "Der Prozess (Leseprobe)" }).ok).toBe(false);
    expect(isWholeBook({ ...base, title: "Auszug aus dem Zauberberg" }).ok).toBe(false);
  });
  it("verwirft Wikisource-Unterseiten", () => {
    expect(
      isWholeBook({ ...base, source: "wikisource", title: "Die Gartenlaube (1853)/Heft 1" }).ok,
    ).toBe(false);
  });
  it("verwirft Zeitschriften", () => {
    expect(isWholeBook({ ...base, subjects: ["Periodicals"] }).ok).toBe(false);
    expect(isWholeBook({ ...base, subjects: ["Zeitschrift"] }).ok).toBe(false);
  });
  it("Titel mit Journal oder Heft sind nicht automatisch Zeitschriften", () => {
    expect(isWholeBook({ ...base, title: "Journal einer Reise" }).ok).toBe(true);
    expect(isWholeBook({ ...base, title: "Das blaue Heft" }).ok).toBe(true);
    expect(isWholeBook({ ...base, title: "Die Gartenlaube, Jahrgang 1853" }).ok).toBe(false);
    expect(isWholeBook({ ...base, title: "Simplicissimus Heft 12" }).ok).toBe(false);
  });
  it("verwirft zu kurze Werke nach Gattung", () => {
    expect(isWholeBook({ ...base, wordCount: MIN_WORDS.prosa - 1 }).ok).toBe(false);
    expect(isWholeBook({ ...base, wordCount: MIN_WORDS.prosa }).ok).toBe(true);
    expect(isWholeBook({ ...base, subjects: ["DE Lyrik"], wordCount: 3500 }).ok).toBe(true);
    expect(isWholeBook({ ...base, subjects: ["DE Drama"], wordCount: 4000 }).ok).toBe(false);
  });
  it("ohne Wortzahl ok — gemessen wird beim Laden", () => {
    expect(isWholeBook({ ...base, wordCount: undefined }).ok).toBe(true);
  });
  it("Grenzen wie im Plan", () => {
    expect(MIN_WORDS).toEqual({ prosa: 8000, drama: 5000, lyrik: 3000 });
  });
});
