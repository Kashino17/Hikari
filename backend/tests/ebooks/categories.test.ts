import { describe, expect, it } from "vitest";
import { CATEGORIES, categorize, kindOf } from "../../src/ebooks/categories.js";

describe("categorize", () => {
  it("bildet Gutenberg-Regale und Schlagwörter ab", () => {
    expect(categorize("gutenberg", ["DE Prosa", "Detective and mystery stories"])).toContain(
      "krimi",
    );
    expect(categorize("gutenberg", ["DE Sachbuch", "Philosophy, German"])).toContain("philosophie");
    expect(categorize("gutenberg", ["DE Kinderbuch"])).toContain("kinder");
    expect(categorize("gutenberg", ["Fairy tales -- Germany"])).toContain("maerchen");
    expect(categorize("gutenberg", ["DE Drama"])).toContain("drama");
    expect(categorize("gutenberg", ["DE Lyrik"])).toContain("lyrik");
    expect(categorize("gutenberg", ["Comedy plays"])).toEqual(
      expect.arrayContaining(["drama", "humor"]),
    );
    expect(categorize("gutenberg", ["Love stories"])).toContain("liebe");
    expect(categorize("gutenberg", ["Category: Adventure"])).toContain("abenteuer");
    expect(categorize("gutenberg", ["Psychoanalysis"])).toContain("psychologie");
    expect(categorize("gutenberg", ["Historical fiction"])).toContain("historisch");
  });

  it("bildet Wikisource-Kategorien ab", () => {
    expect(categorize("wikisource", ["Kriminalliteratur"])).toContain("krimi");
    expect(categorize("wikisource", ["Roman"])).toContain("roman");
    expect(categorize("wikisource", ["Märchen"])).toContain("maerchen");
  });

  it("Unbekanntes landet unter Weitere", () => {
    expect(categorize("gutenberg", ["Irgendwas Seltsames"])).toEqual(["weitere"]);
    expect(categorize("gutenberg", [])).toEqual(["weitere"]);
  });

  it("jede vergebene ID steht in CATEGORIES", () => {
    const ids = new Set(CATEGORIES.map((c) => c.id));
    for (const id of categorize("gutenberg", [
      "DE Prosa",
      "Detective",
      "Philosophy",
      "Travel",
      "Comedy plays",
    ]))
      expect(ids.has(id), id).toBe(true);
    expect(CATEGORIES).toHaveLength(22);
  });

  it("kindOf erkennt Drama und Lyrik, sonst Prosa", () => {
    expect(kindOf(["drama"])).toBe("drama");
    expect(kindOf(["lyrik", "kinder"])).toBe("lyrik");
    expect(kindOf(["krimi"])).toBe("prosa");
  });
});
