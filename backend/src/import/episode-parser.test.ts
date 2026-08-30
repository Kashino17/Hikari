import { describe, expect, it } from "vitest";
import { fillMissingEpisodeInfo, parseEpisodeInfo } from "./episode-parser.js";

describe("parseEpisodeInfo — URL-Muster", () => {
  it("aniworld-Stil: /serie/stream/<name>/staffel-2/episode-5", () => {
    expect(
      parseEpisodeInfo("https://aniworld.to/serie/stream/solo-leveling/staffel-2/episode-5"),
    ).toEqual({ seriesTitle: "Solo Leveling", season: 2, episode: 5 });
  });

  it("englische Variante: /season-1/episode-12", () => {
    expect(parseEpisodeInfo("https://s.to/serie/stream/breaking-bad/season-1/episode-12")).toEqual({
      seriesTitle: "Breaking Bad",
      season: 1,
      episode: 12,
    });
  });

  it("deutsche Folge: /folge-7", () => {
    expect(parseEpisodeInfo("https://hoster.test/one-piece/folge-7")).toEqual({
      seriesTitle: "One Piece",
      episode: 7,
    });
  });

  it("S02E05 im Pfad", () => {
    expect(parseEpisodeInfo("https://x.test/watch/arcane-s02e05")).toEqual({
      seriesTitle: "Arcane",
      episode: 5,
      season: 2,
    });
  });

  it("YouTube-URL ohne Muster bleibt leer", () => {
    expect(parseEpisodeInfo("https://www.youtube.com/watch?v=dQw4w9WgXcQ")).toEqual({});
  });

  it("kaputte URL bleibt leer", () => {
    expect(parseEpisodeInfo("keine url")).toEqual({});
  });

  it("Container-Segmente werden nicht zum Seriennamen", () => {
    const info = parseEpisodeInfo("https://x.test/serie/stream/staffel-1/episode-2");
    expect(info.seriesTitle).toBeUndefined();
    expect(info.season).toBe(1);
    expect(info.episode).toBe(2);
  });
});

describe("parseEpisodeInfo — Titel-Muster", () => {
  it("Folge und Staffel aus dem Titel", () => {
    expect(parseEpisodeInfo("https://x.test/v/abc", "Dragonball Super Staffel 2 Folge 5")).toEqual({
      season: 2,
      episode: 5,
    });
  });

  it("S01E03 im Titel", () => {
    expect(parseEpisodeInfo("https://x.test/v/abc", "Arcane S01E03 german")).toEqual({
      season: 1,
      episode: 3,
    });
  });

  it("Titel ohne Muster bleibt leer", () => {
    expect(parseEpisodeInfo("https://x.test/v/abc", "Einfach ein schönes Video")).toEqual({});
  });

  it("URL gewinnt vor dem Titel", () => {
    expect(
      parseEpisodeInfo(
        "https://x.test/serie/stream/show/staffel-1/episode-2",
        "Show Staffel 9 Folge 99",
      ),
    ).toEqual({ seriesTitle: "Show", season: 1, episode: 2 });
  });
});

describe("fillMissingEpisodeInfo", () => {
  it("füllt nur Lücken, überschreibt keine Eingaben", () => {
    const result = fillMissingEpisodeInfo(
      { seriesTitle: "Meine Serie", season: null, episode: null },
      "https://aniworld.to/serie/stream/solo-leveling/staffel-2/episode-5",
    );
    expect(result.seriesTitle).toBe("Meine Serie");
    expect(result.season).toBe(2);
    expect(result.episode).toBe(5);
  });

  it("lässt komplett befüllte Metadaten unberührt", () => {
    const input = { seriesTitle: "A", season: 3, episode: 4 };
    const result = fillMissingEpisodeInfo(
      input,
      "https://aniworld.to/serie/stream/solo-leveling/staffel-2/episode-5",
    );
    expect(result).toMatchObject(input);
  });
});
