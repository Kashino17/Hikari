import { describe, expect, it } from "vitest";
import { PROGRESS_ARGS, PROGRESS_MARKER, parseProgressLine, progressFraction } from "./progress.js";

describe("parseProgressLine", () => {
  it("liest eine vollständige Fortschrittszeile", () => {
    const p = parseProgressLine("HKPROG|1048576|10485760|10485760|524288|18|NA|NA");
    expect(p).toEqual({
      downloadedBytes: 1048576,
      totalBytes: 10485760,
      speedBps: 524288,
      etaSeconds: 18,
      fragmentIndex: null,
      fragmentCount: null,
    });
  });

  // Bei HLS kennt yt-dlp die Gesamtgröße nicht und liefert nur eine Schätzung.
  it("weicht auf die Schätzung aus, wenn die echte Größe fehlt", () => {
    const p = parseProgressLine("HKPROG|500|NA|20000|1000|10|5|100");
    expect(p?.totalBytes).toBe(20000);
    expect(p?.fragmentIndex).toBe(5);
    expect(p?.fragmentCount).toBe(100);
  });

  it("ignoriert alles, was keine Fortschrittszeile ist", () => {
    for (const line of [
      "[download] Destination: /tmp/video.mp4",
      "[hlsnative] Downloading m3u8 manifest",
      "WARNING: something",
      "",
    ]) {
      expect(parseProgressLine(line)).toBeNull();
    }
  });

  it("ignoriert Zeilen ohne verwertbare Bytezahl", () => {
    expect(parseProgressLine("HKPROG|NA|NA|NA|NA|NA|NA|NA")).toBeNull();
  });

  it("das Template deckt sich mit dem Parser", () => {
    const template = PROGRESS_ARGS[PROGRESS_ARGS.length - 1] ?? "";
    expect(template.startsWith(`${PROGRESS_MARKER}|`)).toBe(true);
    // Marker + 7 Felder
    expect(template.split("|")).toHaveLength(8);
  });
  // yt-dlp liefert Groesse, Tempo und Restzeit als Fliesskommazahlen
  // ("239690509.7142857"). Als solche gespeichert brachen sie die App: Deren
  // Felder sind ganzzahlig, das Parsen der Antwort scheiterte, und weil der
  // Fehler verschluckt wurde, war die Downloadliste einfach leer. Bytes und
  // Sekunden brauchen keine Nachkommastellen.
  it("rundet Fliesskommawerte auf ganze Zahlen", () => {
    const p = parseProgressLine(
      "HKPROG|122978568|NA|239690509.7142857|2011712.0049568545|59.504575810593934|55|108",
    );
    expect(p?.downloadedBytes).toBe(122978568);
    expect(p?.totalBytes).toBe(239690510);
    expect(p?.speedBps).toBe(2011712);
    expect(p?.etaSeconds).toBe(60);
    expect(Number.isInteger(p?.totalBytes)).toBe(true);
    expect(Number.isInteger(p?.speedBps)).toBe(true);
    expect(Number.isInteger(p?.etaSeconds)).toBe(true);
  });
});


describe("progressFraction", () => {
  const base = {
    downloadedBytes: 0,
    totalBytes: null,
    speedBps: null,
    etaSeconds: null,
    fragmentIndex: null,
    fragmentCount: null,
  };

  // Fragmente werden exakt gezählt, die Bytegröße ist bei HLS geraten —
  // deshalb hat die Fragmentzählung Vorrang.
  it("bevorzugt die Fragmentzählung vor der Bytegröße", () => {
    const f = progressFraction({
      ...base,
      downloadedBytes: 10,
      totalBytes: 1000,
      fragmentIndex: 50,
      fragmentCount: 100,
    });
    expect(f).toBeCloseTo(0.5);
  });

  it("nutzt die Bytegröße, wenn es keine Fragmente gibt", () => {
    expect(progressFraction({ ...base, downloadedBytes: 250, totalBytes: 1000 })).toBeCloseTo(0.25);
  });

  it("liefert null, wenn sich nichts bestimmen lässt", () => {
    expect(progressFraction({ ...base, downloadedBytes: 500 })).toBeNull();
  });

  // Die Schätzung liegt gelegentlich zu niedrig — ein Balken über 100 % sieht
  // nach einem Fehler aus.
  it("deckelt bei 100 Prozent", () => {
    expect(progressFraction({ ...base, downloadedBytes: 2000, totalBytes: 1000 })).toBe(1);
  });
});