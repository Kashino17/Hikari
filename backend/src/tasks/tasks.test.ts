import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import fastifyMultipart from "@fastify/multipart";
import Fastify, { type FastifyInstance } from "fastify";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { registerTaskRoutes } from "../api/tasks.js";
import { CaptureProcessor } from "./capture.js";
import { type ParsedTask, TaskParser, fallbackParse } from "./parse.js";
import { addDays, blocked, briefing, effDue, fitFor, openTasks, projectNext } from "./plan.js";
import { TaskStore } from "./store.js";

const TODAY = "2026-11-20"; // Freitag
const ctx = { today: TODAY, projects: [] };

let dir: string;
let store: TaskStore;
beforeEach(() => {
  dir = mkdtempSync(join(tmpdir(), "hikari-tasks-"));
  store = new TaskStore(dir);
});
afterEach(() => rmSync(dir, { recursive: true, force: true }));

describe("Reihenfolge", () => {
  it("Frist heute und Folgen schlagen alte Kleinigkeiten", () => {
    store.addTask({ title: "Zahnarzt anrufen", area: "haus", minutes: 5 });
    store.addTask({
      title: "Kontoauszüge schicken",
      area: "geld",
      minutes: 10,
      due: TODAY,
      stakes: 2,
    });
    expect(openTasks(store.state(), TODAY)[0].title).toBe("Kontoauszüge schicken");
  });

  it("bei Vorhaben ist nur der nächste Schritt offen, Wartendes nie", () => {
    const p = store.addProject({
      title: "Visum",
      area: "amt",
      deadline: addDays(TODAY, 10),
      goal: "Termin",
      origin: "x",
    });
    const a = store.addTask({ title: "Passfotos", area: "amt", project: p.id });
    const b = store.addTask({ title: "Antrag", area: "amt", project: p.id });
    const c = store.addTask({ title: "Flug", area: "amt", project: p.id, wait: "nach dem Visum" });
    p.steps.push(
      { task: a.id },
      { title: "Termin", fixed: addDays(TODAY, 10) },
      { task: b.id },
      { task: c.id },
    );
    const s = store.state();
    expect(projectNext(s, p.id)?.id).toBe(a.id);
    expect(blocked(s, b)).toBe(true);
    expect(blocked(s, c)).toBe(true);
    // Schritt erbt eine Frist vom Termin: 10 Tage − (2 offene − 1) × 2 − 1 = 7
    expect(effDue(s, a, TODAY)).toBe(7);
    a.done = true;
    expect(projectNext(s, p.id)?.id).toBe(b.id);
  });

  it("fitFor füllt die Zeit, Briefing höchstens 90 Minuten", () => {
    store.addTask({ title: "A", minutes: 10, due: TODAY });
    store.addTask({ title: "B", minutes: 60, due: TODAY });
    store.addTask({ title: "C", minutes: 5 });
    expect(
      fitFor(store.state(), TODAY, 15)
        .map((t) => t.title)
        .sort(),
    ).toEqual(["A", "C"]);
    const b = briefing(store.state(), TODAY);
    expect(b.minutes).toBeLessThanOrEqual(90);
    expect(b.items.length).toBe(3);
  });

  it("geschoben ist bis einschließlich holdUntil weg", () => {
    const t = store.addTask({ title: "A" });
    t.holdUntil = addDays(TODAY, 1);
    expect(openTasks(store.state(), TODAY)).toHaveLength(0);
    expect(openTasks(store.state(), addDays(TODAY, 2))).toHaveLength(1);
  });
});

describe("Rückfall-Erkennung ohne Modell", () => {
  it("zerlegt einen Satz in mehrere Aufgaben mit Bereich und Frist", () => {
    const out = fallbackParse(
      "Ich muss bis Montag die Rechnung für Kunde Berger schreiben, morgen Lisa zum Geburtstag anrufen und Entkalker kaufen",
      ctx,
    );
    expect(out.map((t) => t.title)).toEqual([
      "Rechnung für Kunde Berger schreiben",
      "Lisa zum Geburtstag anrufen",
      "Entkalker kaufen",
    ]);
    expect(out[0]).toMatchObject({ area: "job", due: "2026-11-23", kind: "rechner" });
    expect(out[1]).toMatchObject({ area: "leute", due: "2026-11-21", kind: "telefon", minutes: 5 });
    expect(out[2]).toMatchObject({ area: "haus", kind: "unterwegs", due: null });
  });
});

describe("Qwen-Zerlegung", () => {
  it("baut die Anfrage mit Datumsliste und übernimmt das Ergebnis", async () => {
    let sent: { messages: { content: string }[] } | null = null;
    const fetchImpl = (async (_u: string, init: RequestInit) => {
      sent = JSON.parse(String(init.body));
      return new Response(
        JSON.stringify({
          choices: [
            {
              message: {
                content: JSON.stringify({
                  tasks: [
                    {
                      title: "ich muss Bank anrufen",
                      area: "geld",
                      kind: "telefon",
                      minutes: 5,
                      due: "2026-11-23",
                      stakes: 2,
                      note: "",
                      project: "",
                      steps: [],
                    },
                    {
                      title: "Altes Datum",
                      area: "haus",
                      kind: "zuhause",
                      minutes: 5,
                      due: "2026-01-01",
                      stakes: 0,
                      note: "",
                      project: "",
                      steps: [],
                    },
                  ],
                }),
              },
            },
          ],
        }),
      );
    }) as typeof fetch;
    const p = new TaskParser({ baseUrl: "http://lm", model: "m", ttlSeconds: 60, fetchImpl });
    const out = await p.parse("Bank anrufen bis Montag", ctx);
    expect(sent?.messages[0].content).toContain("Montag 2026-11-23");
    expect(out[0]).toMatchObject({
      title: "Bank anrufen",
      due: "2026-11-23",
      note: null,
      project: null,
    });
    expect(out[1].due).toBeNull(); // Vergangenes Datum zählt nicht als Frist
  });
});

describe("Aufnahme im Hintergrund", () => {
  const parsed: ParsedTask[] = [
    {
      title: "Rechnung schreiben",
      area: "job",
      kind: "rechner",
      minutes: 20,
      due: null,
      stakes: 1,
      note: null,
      project: null,
      steps: [],
    },
    {
      title: "Umzug",
      area: "haus",
      kind: "zuhause",
      minutes: 60,
      due: "2026-12-01",
      stakes: 1,
      note: "Übergabe",
      project: "Umzug",
      steps: [
        { title: "Kartons besorgen", minutes: 20, kind: "unterwegs" },
        { title: "Keller ausräumen", minutes: 90, kind: "zuhause" },
      ],
    },
  ];

  it("Ton → Whisper → Qwen → Aufgaben und Vorhaben", async () => {
    const proc = new CaptureProcessor({
      store,
      today: () => TODAY,
      transcribe: async () => ({ text: "Rechnung schreiben und Umzug planen" }),
      parse: async () => parsed,
      fallback: fallbackParse,
    });
    const c = store.newCapture("voice", null, { data: Buffer.from("x"), ext: ".m4a" });
    proc.enqueue(c.id);
    await new Promise((r) => setTimeout(r, 20));
    expect(c.status).toBe("done");
    expect(c.created).toHaveLength(3);
    expect(store.state().projects[0]).toMatchObject({
      title: "Umzug",
      deadline: "2026-12-01",
      goal: "Übergabe",
    });
    expect(c.file).toBeNull(); // Tondatei nach Erfolg gelöscht
  });

  it("ohne LM Studio sortieren die Regeln, nichts geht verloren", async () => {
    const proc = new CaptureProcessor({
      store,
      today: () => TODAY,
      transcribe: async () => ({ text: "" }),
      parse: async () => {
        throw new Error("lmstudio_down");
      },
      fallback: fallbackParse,
    });
    const c = store.newCapture("text", "morgen Bank anrufen", null);
    proc.enqueue(c.id);
    await new Promise((r) => setTimeout(r, 20));
    expect(c).toMatchObject({ status: "done", fallback: true });
    expect(store.task(c.created[0])?.title).toBe("Bank anrufen");
  });
});

describe("Routen", () => {
  let app: FastifyInstance;
  beforeEach(async () => {
    app = Fastify();
    await app.register(fastifyMultipart);
    const proc = new CaptureProcessor({
      store,
      today: () => TODAY,
      transcribe: async () => ({ text: "Paket zurückschicken" }),
      parse: async () => {
        throw new Error("off");
      },
      fallback: fallbackParse,
    });
    await registerTaskRoutes(app, {
      store,
      processor: proc,
      parser: {
        split: async () => {
          throw new Error("off");
        },
      },
      today: () => TODAY,
    });
  });
  afterEach(() => app.close());

  it("anlegen, abhaken, zurückholen, löschen", async () => {
    const created = (
      await app.inject({
        method: "POST",
        url: "/tasks",
        payload: { title: "Miete überweisen", area: "geld", minutes: 5 },
      })
    ).json();
    const done = (
      await app.inject({ method: "PATCH", url: `/tasks/${created.id}`, payload: { done: true } })
    ).json();
    expect(done.done).toBe(true);
    expect(done.doneAt).toBeGreaterThan(0);
    const back = (
      await app.inject({ method: "PATCH", url: `/tasks/${created.id}`, payload: { done: false } })
    ).json();
    expect(back).toMatchObject({ done: false, doneAt: null });
    expect((await app.inject({ method: "DELETE", url: `/tasks/${created.id}` })).statusCode).toBe(
      204,
    );
    expect((await app.inject({ method: "GET", url: "/tasks" })).json().tasks).toHaveLength(0);
  });

  it("Text-Aufnahme liefert Ergebnis und lässt sich rückgängig machen", async () => {
    const c = (
      await app.inject({
        method: "POST",
        url: "/tasks/capture",
        payload: { text: "heute Kontoauszüge schicken, Paket zurückschicken" },
      })
    ).json();
    await new Promise((r) => setTimeout(r, 20));
    const res = (await app.inject({ method: "GET", url: `/tasks/capture/${c.id}` })).json();
    expect(res.status).toBe("done");
    expect(res.created.map((t: { title: string }) => t.title)).toEqual([
      "Kontoauszüge schicken",
      "Paket zurückschicken",
    ]);
    await app.inject({ method: "POST", url: `/tasks/capture/${c.id}/undo` });
    expect(store.state().tasks).toHaveLength(0);
  });

  it("Zerlegen ersetzt die Aufgabe durch ein Vorhaben", async () => {
    const t = (
      await app.inject({
        method: "POST",
        url: "/tasks",
        payload: { title: "Angebot fertig machen", minutes: 60 },
      })
    ).json();
    const sug = (await app.inject({ method: "POST", url: `/tasks/${t.id}/split-suggest` })).json();
    expect(sug.fallback).toBe(true);
    const r = await app.inject({
      method: "POST",
      url: `/tasks/${t.id}/split`,
      payload: { steps: sug.steps },
    });
    expect(r.statusCode).toBe(201);
    expect(store.task(t.id)).toBeUndefined();
    expect(store.state().projects[0].steps).toHaveLength(3);
  });

  it("Briefing nach Minuten", async () => {
    store.addTask({ title: "A", minutes: 10, due: TODAY });
    store.addTask({ title: "B", minutes: 40 });
    const b = (await app.inject({ method: "GET", url: "/tasks/briefing?minutes=15&max=1" })).json();
    expect(b.items.map((x: { title: string }) => x.title)).toEqual(["A"]);
  });
});
