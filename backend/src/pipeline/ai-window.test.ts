import { describe, expect, it } from "vitest";
import { describeAiWindow, isAiWindowOpen, minutesUntilOpen, parseAiWindow } from "./ai-window.js";

const at = (h: number, m = 0) => new Date(2026, 7, 26, h, m, 0);

describe("parseAiWindow", () => {
  it("liest ein Fenster", () => {
    expect(parseAiWindow("22:00-02:00")).toEqual({ startMinutes: 1320, endMinutes: 120 });
    expect(parseAiWindow("9:30 - 11:45")).toEqual({ startMinutes: 570, endMinutes: 705 });
  });

  it("schaltet die Begrenzung ab", () => {
    for (const v of ["always", "immer", "", "  ", undefined]) {
      expect(parseAiWindow(v)).toBeNull();
    }
  });

  // Lieber unbegrenzt als dauerhaft still: Ein falsch verstandenes Fenster
  // würde die Hintergrundarbeit stilllegen, ohne dass der Grund sichtbar wird.
  it("faellt bei unlesbarer Eingabe auf unbegrenzt zurueck", () => {
    for (const v of ["22:00", "abends", "25:00-02:00", "22:70-02:00", "22-02"]) {
      expect(parseAiWindow(v), v).toBeNull();
    }
  });

  it("liest ein Fenster ohne Laenge als unbegrenzt", () => {
    expect(parseAiWindow("22:00-22:00")).toBeNull();
  });
});

describe("isAiWindowOpen", () => {
  const abends = parseAiWindow("22:00-02:00");

  // Der Normalfall ist ein Fenster über Mitternacht.
  it("erkennt ein Fenster ueber Mitternacht", () => {
    expect(isAiWindowOpen(at(22, 0), abends)).toBe(true);
    expect(isAiWindowOpen(at(23, 59), abends)).toBe(true);
    expect(isAiWindowOpen(at(0, 30), abends)).toBe(true);
    expect(isAiWindowOpen(at(1, 59), abends)).toBe(true);

    expect(isAiWindowOpen(at(2, 0), abends)).toBe(false);
    expect(isAiWindowOpen(at(12, 0), abends)).toBe(false);
    expect(isAiWindowOpen(at(21, 59), abends)).toBe(false);
  });

  it("erkennt ein Fenster innerhalb eines Tages", () => {
    const tags = parseAiWindow("09:00-17:00");
    expect(isAiWindowOpen(at(9, 0), tags)).toBe(true);
    expect(isAiWindowOpen(at(16, 59), tags)).toBe(true);
    expect(isAiWindowOpen(at(17, 0), tags)).toBe(false);
    expect(isAiWindowOpen(at(8, 59), tags)).toBe(false);
  });

  it("ohne Fenster ist immer offen", () => {
    expect(isAiWindowOpen(at(3, 0), null)).toBe(true);
    expect(isAiWindowOpen(at(15, 0), null)).toBe(true);
  });
});

describe("minutesUntilOpen", () => {
  const abends = parseAiWindow("22:00-02:00");

  it("ist null solange offen", () => {
    expect(minutesUntilOpen(at(23, 0), abends)).toBe(0);
    expect(minutesUntilOpen(at(12, 0), null)).toBe(0);
  });

  it("rechnet bis zum naechsten Oeffnen", () => {
    expect(minutesUntilOpen(at(21, 30), abends)).toBe(30);
    expect(minutesUntilOpen(at(12, 0), abends)).toBe(600);
    // Kurz nach Schluss: fast ein ganzer Tag.
    expect(minutesUntilOpen(at(2, 0), abends)).toBe(20 * 60);
  });
});

describe("describeAiWindow", () => {
  it("beschreibt lesbar", () => {
    expect(describeAiWindow(parseAiWindow("22:00-02:00"))).toBe("22:00–02:00");
    expect(describeAiWindow(parseAiWindow("9:05-10:00"))).toBe("09:05–10:00");
    expect(describeAiWindow(null)).toBe("immer");
  });
});
