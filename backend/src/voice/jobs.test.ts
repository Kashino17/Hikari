import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import {
  VoiceJobStore,
  VoiceProcessor,
  cleanTranscript,
  combinedTranscript,
  orderClips,
} from "./jobs.js";
import type { VoiceSummary } from "./summarize.js";

const SUMMARY: VoiceSummary = {
  title: "Termin verschoben",
  summary: "Der Termin ist jetzt Dienstag.",
  points: ["Termin Dienstag"],
  todos: ["Kalkulation schicken"],
  dates: ["Dienstag 10 Uhr"],
  questions: [],
};

describe("orderClips", () => {
  it("sortiert WhatsApp-Sprachnachrichten nach Datum und Nummer", () => {
    const names = [
      "PTT-20261106-WA0012.opus",
      "PTT-20261105-WA0099.opus",
      "PTT-20261106-WA0003.opus",
    ];
    expect(orderClips(names.map((name) => ({ name }))).map((c) => c.name)).toEqual([
      "PTT-20261105-WA0099.opus",
      "PTT-20261106-WA0003.opus",
      "PTT-20261106-WA0012.opus",
    ]);
  });

  it("lässt die Reihenfolge, sobald ein Name nicht passt", () => {
    const clips = [{ name: "PTT-20261106-WA0012.opus" }, { name: "aufnahme.m4a" }];
    expect(orderClips(clips)).toEqual(clips);
  });
});

describe("cleanTranscript", () => {
  it("entfernt Whisper-Halluzinationen aus Stille", () => {
    expect(cleanTranscript("Hallo Kadir. Untertitel der Amara.org-Community")).toBe("Hallo Kadir.");
    expect(cleanTranscript("Untertitel im Auftrag des ZDF, 2021")).toBe("");
  });
});

describe("combinedTranscript", () => {
  it("setzt bei mehreren Nachrichten Überschriften mit Länge", () => {
    const text = combinedTranscript([
      { file: "01.opus", name: "a", size: 1, duration: 75, text: "Erstens.", language: "de" },
      { file: "02.opus", name: "b", size: 1, duration: 5, text: "", language: "de" },
    ]);
    expect(text).toBe(
      "### Sprachnachricht 1 (1:15)\nErstens.\n\n### Sprachnachricht 2 (0:05)\n[nichts verständlich]",
    );
  });
});

describe("VoiceProcessor", () => {
  let dir: string;
  let store: VoiceJobStore;
  beforeEach(() => {
    dir = mkdtempSync(join(tmpdir(), "hikari-voice-"));
    store = new VoiceJobStore(dir);
  });
  afterEach(() => rmSync(dir, { recursive: true, force: true }));

  const clip = (name: string) => ({ name, data: Buffer.from("x"), ext: ".opus" });

  it("transkribiert alle Clips der Reihe nach und fasst einmal zusammen", async () => {
    const seen: string[] = [];
    let summarizedWith = "";
    const p = new VoiceProcessor({
      store,
      transcribe: async (path) => {
        seen.push(path.split("/").pop()!);
        return { text: `Text ${seen.length}`, duration: 10, language: "de" };
      },
      summarize: async (t) => {
        summarizedWith = t;
        return SUMMARY;
      },
    });
    const job = store.create("Mehmet", [
      clip("PTT-20261106-WA0002.opus"),
      clip("PTT-20261106-WA0001.opus"),
    ]);
    p.enqueue(job.id);
    await p.idle();
    const done = store.get(job.id)!;
    expect(done.status).toBe("done");
    expect(done.clips.map((c) => c.name)).toEqual([
      "PTT-20261106-WA0001.opus",
      "PTT-20261106-WA0002.opus",
    ]);
    expect(seen).toEqual(["01.opus", "02.opus"]);
    expect(summarizedWith).toContain("### Sprachnachricht 2 (0:10)\nText 2");
    expect(done.summary).toEqual(SUMMARY);
    expect(done.done).toBe(2);
  });

  it("transkribiert bei Wiederholung nur, was noch fehlt", async () => {
    let calls = 0;
    let fail = true;
    const p = new VoiceProcessor({
      store,
      transcribe: async () => {
        calls++;
        return { text: "Hallo", duration: 3, language: "de" };
      },
      summarize: async () => {
        if (fail) throw new Error("lmstudio_503");
        return SUMMARY;
      },
    });
    const job = store.create(null, [clip("a.opus"), clip("b.opus")]);
    p.enqueue(job.id);
    await p.idle();
    expect(store.get(job.id)!.status).toBe("failed");
    expect(store.get(job.id)!.error).toBe("lmstudio_503");

    fail = false;
    p.enqueue(job.id);
    await p.idle();
    expect(store.get(job.id)!.status).toBe("done");
    expect(calls).toBe(2); // nicht 4
  });

  it("scheitert verständlich, wenn nichts zu hören ist", async () => {
    const p = new VoiceProcessor({
      store,
      transcribe: async () => ({
        text: "Untertitel der Amara.org-Community",
        duration: 2,
        language: "de",
      }),
      summarize: async () => SUMMARY,
    });
    const job = store.create(null, [clip("a.opus")]);
    p.enqueue(job.id);
    await p.idle();
    expect(store.get(job.id)!.status).toBe("failed");
    expect(store.get(job.id)!.error).toMatch(/nichts zu verstehen/);
  });

  it("nimmt Unfertiges nach einem Neustart wieder auf", async () => {
    const job = store.create(null, [clip("a.opus")]);
    const j = store.get(job.id)!;
    j.status = "transcribing";
    store.save(j);
    const p = new VoiceProcessor({
      store,
      transcribe: async () => ({ text: "Hallo", duration: 3, language: "de" }),
      summarize: async () => SUMMARY,
    });
    expect(p.recover()).toBe(1);
    await p.idle();
    expect(store.get(job.id)!.status).toBe("done");
  });

  it("weist fremde Job-IDs ab (kein Pfad-Ausbruch)", () => {
    expect(store.get("../../etc")).toBeNull();
    expect(store.delete("../x")).toBe(false);
  });
});
