import { describe, expect, it } from "vitest";
import { cleanImportTitle, fallbackTitleFromUrl, stripSeriesPrefix } from "./titles.js";

describe("cleanImportTitle", () => {
  it("entfernt den VOE-Uploader-Suffix", () => {
    expect(cleanImportTitle("Dragonball Super 2 HD GER SUB by Dragonball-Tube")).toBe(
      "Dragonball Super 2 HD GER SUB",
    );
  });

  it("entfernt den Seitennamen aus document.title, wenn er zum Host passt", () => {
    expect(cleanImportTitle("Folge 5 - AniWorld", "aniworld.to")).toBe("Folge 5");
    expect(cleanImportTitle("Dragonball Super | VOE", "voe.sx")).toBe("Dragonball Super");
  });

  it("lässt echte Titel mit Bindestrich in Ruhe", () => {
    expect(cleanImportTitle("Mission: Impossible - Fallout", "example.com")).toBe(
      "Mission: Impossible - Fallout",
    );
  });

  it("verwirft generische Extraktor-Platzhalter", () => {
    expect(cleanImportTitle("master")).toBeNull();
    expect(cleanImportTitle("index")).toBeNull();
    expect(cleanImportTitle("Video")).toBeNull();
  });

  it("verwirft reine Dateinamen", () => {
    expect(cleanImportTitle("fsz0jl0y8u39.mp4")).toBeNull();
  });

  it("löst HTML-Entities und Whitespace auf", () => {
    expect(cleanImportTitle("Tom &amp; Jerry   Folge  1")).toBe("Tom & Jerry Folge 1");
  });

  it("entfernt den Seitennamen auch bei Kurzdomains wie s.to", () => {
    expect(cleanImportTitle("Ted S01E07 | SerienStream (S.to)", "s.to")).toBe("Ted S01E07");
    expect(cleanImportTitle("Ted S01E07 | SerienStream (S.to)", "serienstream.to")).toBe(
      "Ted S01E07",
    );
  });

  it("verwirft Titel, die in Wahrheit URLs sind (Ad-Redirect-Seiten)", () => {
    expect(
      cleanImportTitle("s.lazada.co.th/s.ZRRUaS?t=p-i2eLCtz&sub_aff_id=104882", "serienstream.to"),
    ).toBeNull();
    expect(cleanImportTitle("https://tracker.example.com/click?id=1")).toBeNull();
    // Release-Namen mit Punkten, aber ohne Pfad, sind keine URLs.
    expect(cleanImportTitle("Solo.Leveling.S01E01.German.Dub.720p")).toBe(
      "Solo.Leveling.S01E01.German.Dub.720p",
    );
  });

  it("liefert null bei leerem Input", () => {
    expect(cleanImportTitle(null)).toBeNull();
    expect(cleanImportTitle("   ")).toBeNull();
    expect(cleanImportTitle(undefined)).toBeNull();
  });
});

describe("stripSeriesPrefix", () => {
  it("zieht den Seriennamen vorne ab", () => {
    expect(stripSeriesPrefix("Solo Leveling - Folge 3", "Solo Leveling")).toBe("Folge 3");
    expect(stripSeriesPrefix("solo leveling: Arise", "Solo Leveling")).toBe("Arise");
  });

  it("behält den Titel, wenn sonst nichts übrig bliebe", () => {
    expect(stripSeriesPrefix("Solo Leveling", "Solo Leveling")).toBe("Solo Leveling");
  });

  it("lässt fremde Titel unangetastet", () => {
    expect(stripSeriesPrefix("Naruto Folge 1", "Solo Leveling")).toBe("Naruto Folge 1");
  });

  it("ohne Serie passiert nichts", () => {
    expect(stripSeriesPrefix("Irgendein Titel", null)).toBe("Irgendein Titel");
    expect(stripSeriesPrefix("Irgendein Titel", undefined)).toBe("Irgendein Titel");
  });
});

describe("fallbackTitleFromUrl", () => {
  it("humanisiert die letzten Pfadsegmente", () => {
    expect(fallbackTitleFromUrl("https://aniworld.to/serie/stream/x/staffel-2/episode-5")).toBe(
      "Staffel 2 Episode 5",
    );
  });

  it("nimmt den Dateinamen der Medien-URL, wenn die Seiten-URL nichts hergibt", () => {
    expect(
      fallbackTitleFromUrl("https://x.test/", "https://cdn.test/video/die-grosse-folge.mp4"),
    ).toBe("Die Grosse Folge");
  });

  it("fällt nie auf die rohe URL zurück", () => {
    expect(fallbackTitleFromUrl("https://x.test/")).toBe("Unbenanntes Video");
    expect(fallbackTitleFromUrl("keine url")).toBe("Unbenanntes Video");
  });
});
