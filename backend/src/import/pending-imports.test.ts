import Database from "better-sqlite3";
import { beforeEach, describe, expect, it } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import {
  createPending,
  getPending,
  listPending,
  markFailed,
  metadataForCompletion,
  removePending,
  updatePendingMetadata,
  updateProgress,
} from "./pending-imports.js";

describe("pending imports", () => {
  let db: Database.Database;

  beforeEach(() => {
    db = new Database(":memory:");
    applyMigrations(db);
  });

  it("ist sofort nach dem Anlegen sichtbar", () => {
    createPending(db, {
      id: "sniff_a",
      pageUrl: "https://serien.test/folge-1",
      metadata: { seriesTitle: "Solo Leveling", episode: 1 },
    });

    const list = listPending(db);
    expect(list).toHaveLength(1);
    expect(list[0]?.status).toBe("queued");
    expect(list[0]?.seriesTitle).toBe("Solo Leveling");
    expect(list[0]?.progress).toBeNull();
  });

  it("rechnet den Fortschritt aus Fragmenten", () => {
    createPending(db, { id: "sniff_a", pageUrl: "https://x.test/1" });
    updateProgress(db, "sniff_a", {
      downloadedBytes: 5_000_000,
      totalBytes: null,
      speedBps: 1_000_000,
      etaSeconds: 30,
      fragmentIndex: 25,
      fragmentCount: 100,
    });

    const p = getPending(db, "sniff_a");
    expect(p?.status).toBe("downloading");
    expect(p?.progress).toBeCloseTo(0.25);
    expect(p?.etaSeconds).toBe(30);
    expect(p?.speedBps).toBe(1_000_000);
  });

  // Verschwindet der Eintrag beim Fehler, sieht der Nutzer nur, dass nichts
  // ankam — und erfährt nie warum.
  it("behält gescheiterte Importe samt Fehlertext", () => {
    createPending(db, { id: "sniff_a", pageUrl: "https://x.test/1" });
    markFailed(db, "sniff_a", "download failed: 403 vom Hoster");

    const p = getPending(db, "sniff_a");
    expect(p?.status).toBe("failed");
    expect(p?.error).toContain("403");
  });

  it("übernimmt Änderungen während des Downloads", () => {
    createPending(db, {
      id: "sniff_a",
      pageUrl: "https://x.test/1",
      metadata: { title: "Roher Dateiname", seriesTitle: "Falsch" },
    });

    const updated = updatePendingMetadata(db, "sniff_a", {
      title: "Solo Leveling Folge 1",
      seriesTitle: "Solo Leveling",
      dubLanguage: "de",
      season: 1,
      episode: 1,
    });

    expect(updated?.title).toBe("Solo Leveling Folge 1");
    expect(updated?.seriesTitle).toBe("Solo Leveling");
    expect(updated?.dubLanguage).toBe("de");
  });

  // undefined heißt "unverändert" — sonst würde ein Teil-Update alle anderen
  // Felder des Nutzers stillschweigend leeren.
  it("lässt nicht übergebene Felder unangetastet", () => {
    createPending(db, {
      id: "sniff_a",
      pageUrl: "https://x.test/1",
      metadata: { title: "Titel", seriesTitle: "Serie", season: 2 },
    });

    const updated = updatePendingMetadata(db, "sniff_a", { dubLanguage: "de" });

    expect(updated?.title).toBe("Titel");
    expect(updated?.seriesTitle).toBe("Serie");
    expect(updated?.season).toBe(2);
    expect(updated?.dubLanguage).toBe("de");
  });

  it("leert ein Feld bei ausdrücklichem null", () => {
    createPending(db, { id: "sniff_a", pageUrl: "https://x.test/1", metadata: { title: "Titel" } });
    expect(updatePendingMetadata(db, "sniff_a", { title: null })?.title).toBeNull();
  });

  // Die Eingabe des Nutzers muss den finalen Import bestimmen, nicht das,
  // was beim Einreihen mitgeschickt wurde.
  it("liefert die bearbeiteten Metadaten für den Abschluss", () => {
    createPending(db, {
      id: "sniff_a",
      pageUrl: "https://x.test/1",
      metadata: { title: "Alt", episode: 1 },
    });
    updatePendingMetadata(db, "sniff_a", { title: "Neu", dubLanguage: "de" });

    const meta = metadataForCompletion(getPending(db, "sniff_a"));
    expect(meta.title).toBe("Neu");
    expect(meta.dubLanguage).toBe("de");
    expect(meta.episode).toBe(1);
  });

  it("verschwindet nach erfolgreichem Abschluss", () => {
    createPending(db, { id: "sniff_a", pageUrl: "https://x.test/1" });
    removePending(db, "sniff_a");
    expect(listPending(db)).toHaveLength(0);
  });

  // Die Spalte thumbnail_url existierte im Schema, wurde aber nie geschrieben —
  // die Fortschrittszeile zeigte selbst bei bekanntem Vorschaubild nichts.
  it("zeigt ein schon beim Einreihen bekanntes Thumbnail sofort", () => {
    createPending(db, {
      id: "voe_x",
      pageUrl: "https://x.test/1",
      metadata: { title: "Folge 1" },
      thumbnailUrl: "https://hoster.test/thumb.jpg",
    });

    expect(getPending(db, "voe_x")?.thumbnailUrl).toBe("https://hoster.test/thumb.jpg");
    expect(listPending(db)[0]?.thumbnailUrl).toBe("https://hoster.test/thumb.jpg");
  });

  // Ein zweiter Anlauf auf dieselbe Seite darf keinen doppelten Eintrag
  // erzeugen, sondern den alten Fehlversuch zurücksetzen.
  it("setzt einen erneuten Anlauf zurück statt zu doppeln", () => {
    createPending(db, { id: "sniff_a", pageUrl: "https://x.test/1" });
    markFailed(db, "sniff_a", "kaputt");
    createPending(db, { id: "sniff_a", pageUrl: "https://x.test/1" });

    const list = listPending(db);
    expect(list).toHaveLength(1);
    expect(list[0]?.status).toBe("queued");
    expect(list[0]?.error).toBeNull();
  });
});
