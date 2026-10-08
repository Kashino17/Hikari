import { describe, expect, it } from "vitest";
import { mergeAnalyzeMeta } from "./videos.js";

describe("mergeAnalyzeMeta", () => {
  it("lässt URL-Staffel und -Folge nicht vom Modell überschreiben", () => {
    const merged = mergeAnalyzeMeta(
      { seriesTitle: "Solos", season: 2, episode: 3 },
      { seriesTitle: "SOLOS", season: 1, episode: 1 },
    );
    expect(merged.season).toBe(2);
    expect(merged.episode).toBe(3);
    expect(merged.seriesTitle).toBe("SOLOS");
  });

  it("nimmt die Modell-Werte, wenn die URL nichts nennt", () => {
    const merged = mergeAnalyzeMeta({}, { season: 4, episode: 9 });
    expect(merged.season).toBe(4);
    expect(merged.episode).toBe(9);
  });
});
