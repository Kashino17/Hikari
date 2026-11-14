import { mkdtempSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import {
  AnjaLive,
  HELP_MODELS,
  type LiveSocket,
  liveStart,
  onlyFormChanged,
  quotaDay,
  settleHelps,
  systemPrompt,
} from "./anja-live.js";

/** Attrappe für die Verbindung zu Google: merkt sich, was gesendet wurde, und lässt den Test antworten. */
class FakeSocket implements LiveSocket {
  binaryType?: string;
  // biome-ignore lint/suspicious/noExplicitAny: frei geformte Nachrichten der Attrappe
  sent: Record<string, any>[] = [];
  closed = false;
  onopen: ((ev: unknown) => void) | null = null;
  onmessage: ((ev: { data: unknown }) => void) | null = null;
  onclose: ((ev: { code?: number; reason?: string }) => void) | null = null;
  onerror: ((ev: unknown) => void) | null = null;
  constructor(private ready = true) {
    queueMicrotask(() => this.onopen?.({}));
  }
  send(data: string) {
    const msg = JSON.parse(data);
    this.sent.push(msg);
    if (msg.setup && this.ready) queueMicrotask(() => this.emit({ setupComplete: {} }));
  }
  close() {
    this.closed = true;
  }
  emit(msg: unknown) {
    this.onmessage?.({ data: JSON.stringify(msg) });
  }
  /** Anja spricht: Ton in zwei Stücken, Mitschriften, Ende des Zugs. */
  speak(said: string, heard = "") {
    const chunk = (text: string) => ({
      serverContent: {
        modelTurn: { parts: [{ inlineData: { data: Buffer.from(text).toString("base64") } }] },
      },
    });
    if (heard) this.emit({ serverContent: { inputTranscription: { text: heard } } });
    this.emit(chunk("AAAA"));
    this.emit({ serverContent: { outputTranscription: { text: said } } });
    this.emit(chunk("BBBB"));
    this.emit({ serverContent: { turnComplete: true } });
  }
}

const helpsBody = (check: Record<string, unknown>) => ({
  candidates: [
    {
      content: {
        parts: [
          {
            text: JSON.stringify({
              check,
              replyDe: "Gut! Was machst du heute?",
              words: [
                { ru: "хорошо", de: "gut" },
                { ru: "что", de: "was" },
                { ru: "!", de: "" },
              ],
              examples: [
                { ru: "Я работаю", de: "Ich arbeite" },
                { ru: "Я дома", de: "Ich bin zu Hause" },
              ],
            }),
          },
        ],
      },
    },
  ],
});

/** Die n-te Verbindung, die der Test geöffnet hat. */
function sock(s: { sockets: FakeSocket[] }, n = 0): FakeSocket {
  const found = s.sockets[n];
  if (!found) throw new Error(`Verbindung ${n} fehlt`);
  return found;
}

function setup(
  opts: { check?: Record<string, unknown>; statuses?: number[]; key?: string | null } = {},
) {
  const dir = mkdtempSync(join(tmpdir(), "anja-live-"));
  const sockets: FakeSocket[] = [];
  // biome-ignore lint/suspicious/noExplicitAny: frei geformte Anfrage an das Hilfen-Modell
  const requests: { url: string; body: any }[] = [];
  const statuses = [...(opts.statuses ?? [])];
  let now = Date.parse("2026-11-14T12:00:00Z");
  const live = new AnjaLive({
    key: () => (opts.key === undefined ? "test-key" : opts.key),
    usageFile: join(dir, "usage.json"),
    now: () => now,
    connect: () => {
      const s = new FakeSocket();
      sockets.push(s);
      return s;
    },
    request: (async (url: string, init: { body: string }) => {
      requests.push({ url, body: JSON.parse(init.body) });
      // Die Wartezeit zwischen zwei Anfragen an dasselbe Modell soll den Test nicht bremsen.
      now += 5000;
      const status = statuses.shift() ?? 200;
      return new Response(
        JSON.stringify(status === 200 ? helpsBody(opts.check ?? { ok: true }) : {}),
        { status },
      );
    }) as unknown as typeof fetch,
  });
  return {
    live,
    sockets,
    requests,
    dir,
    advance: (ms: number) => {
      now += ms;
    },
  };
}

const START = liveStart.parse({ stage: 0, scenario: "Im Café", knownWords: ["чай", "кофе"] });
const audio = Buffer.alloc(40_000, 1).toString("base64");

async function collect(gen: AsyncGenerator<Buffer>) {
  const out: Buffer[] = [];
  for await (const b of gen) out.push(b);
  return Buffer.concat(out).toString();
}

describe("Anja live", () => {
  it("eröffnet das Gespräch: Regie-Anweisung, Sprechtaste, Niveau und bekannte Wörter im Auftrag", async () => {
    const s = setup();
    const { id, turn } = await s.live.start(START);
    const socket = sock(s, 0);
    const cfg = socket.sent[0]?.setup;
    expect(cfg.model).toBe("models/gemini-3.8-live");
    expect(cfg.realtimeInputConfig.automaticActivityDetection.disabled).toBe(true);
    expect(cfg.systemInstruction.parts[0].text).toContain("чай, кофе");
    expect(cfg.systemInstruction.parts[0].text).toContain("Im Café");
    expect(socket.sent[1]?.realtimeInput.text).toMatch(/^\(Regie: Beginne/);
    socket.speak("Привет! Как дела?");
    const t = await s.live.turn(id, turn, "helps");
    expect(t).toMatchObject({
      done: true,
      said: "Привет! Как дела?",
      heard: "",
      helpsState: "ready",
    });
    // Anja hat begonnen: nichts zu prüfen.
    expect(t?.helps?.check).toBeNull();
    expect(t?.helps?.words).toEqual([
      { ru: "хорошо", de: "gut" },
      { ru: "что", de: "was" },
    ]);
    expect(await collect(s.live.audio(id, turn))).toBe("AAAABBBB");
  });

  it("gesprochene Antwort: Ton geht zwischen Start und Ende der Sprechtaste hinaus, die Prüfung kennt Frage und Antwort", async () => {
    const s = setup({
      check: {
        ok: false,
        correctedRu: "Я люблю чай",
        correctedDe: "Ich mag Tee",
        focus: "Verbform",
        explanationDe: "Nach я: люблю.",
      },
    });
    const { id } = await s.live.start(START);
    const socket = sock(s, 0);
    socket.speak("Ты любишь чай?");
    await s.live.turn(id, 1, "helps");
    const n = await s.live.send(id, { kind: "audio", audio, rate: 16000 });
    const sent = socket.sent.slice(2).map((m) => Object.keys(m.realtimeInput)[0]);
    expect(sent[0]).toBe("activityStart");
    expect(sent.at(-1)).toBe("activityEnd");
    expect(sent.filter((k) => k === "audio").length).toBe(3);
    expect(socket.sent[3]?.realtimeInput.audio.mimeType).toBe("audio/pcm;rate=16000");
    socket.speak("Я люблю чай. Хорошо!", "Я любить чай");
    const t = await s.live.turn(id, n, "helps");
    expect(t?.heard).toBe("Я любить чай");
    expect(t?.helps?.check).toEqual({
      ok: false,
      correctedRu: "Я люблю чай",
      correctedDe: "Ich mag Tee",
      focus: "Verbform",
      explanationDe: "Nach я: люблю.",
    });
    expect(JSON.parse(s.requests[1]?.body.contents[0].parts[0].text)).toEqual({
      question: "Ты любишь чай?",
      answer: "Я любить чай",
      reply: "Я люблю чай. Хорошо!",
    });
  });

  it("der Tonstrom liefert schon, während Anja noch spricht", async () => {
    const s = setup();
    const { id } = await s.live.start(START);
    const socket = sock(s, 0);
    const stream = s.live.audio(id, 1);
    socket.emit({
      serverContent: {
        modelTurn: { parts: [{ inlineData: { data: Buffer.from("eins").toString("base64") } }] },
      },
    });
    expect((await stream.next()).value?.toString()).toBe("eins");
    expect((await s.live.turn(id, 1))?.done).toBe(false);
    socket.emit({ serverContent: { outputTranscription: { text: "Привет" }, turnComplete: true } });
    expect((await stream.next()).done).toBe(true);
  });

  it("solange Anja spricht, wird kein zweiter Zug angenommen", async () => {
    const s = setup();
    const { id } = await s.live.start(START);
    await expect(s.live.send(id, { kind: "text", text: "Привет" })).rejects.toThrow("busy");
  });

  it("nach 12 Minuten wird neu verbunden — mit dem bisherigen Verlauf im Auftrag", async () => {
    const s = setup();
    const { id } = await s.live.start(START);
    s.sockets[0]?.speak("Привет! Как дела?");
    await s.live.turn(id, 1, "helps");
    s.advance(13 * 60_000);
    // Das Gespräch selbst darf nicht als verwaist gelten: Es wurde gerade benutzt.
    const n = await s.live.send(id, { kind: "text", text: "Хорошо" });
    expect(s.sockets.length).toBe(2);
    expect(s.sockets[0]?.closed).toBe(true);
    const prompt = s.sockets[1]?.sent[0]?.setup.systemInstruction.parts[0].text;
    expect(prompt).toContain("Anja: Привет! Как дела?");
    expect(s.sockets[1]?.sent[1]?.realtimeInput.text).toBe("Хорошо");
    s.sockets[1]?.speak("Что ты делаешь?");
    expect((await s.live.turn(id, n, "done"))?.heard).toBe("Хорошо");
  });

  it("überlastetes Hilfen-Modell: das nächste übernimmt; 429 sperrt das Modell für den Tag", async () => {
    const s = setup({ statuses: [503, 429, 200] });
    const { id } = await s.live.start(START);
    s.sockets[0]?.speak("Привет!");
    const t = await s.live.turn(id, 1, "helps");
    expect(t?.helpsState).toBe("ready");
    expect(s.requests.map((r) => r.url.split("/").pop())).toEqual([
      "gemini-3.5-flash-lite:generateContent",
      "gemini-3.1-flash-lite:generateContent",
      "gemini-3.5-flash-lite:generateContent",
    ]);
    const usage = JSON.parse(readFileSync(join(s.dir, "usage.json"), "utf8"));
    expect(usage.count["gemini-3.1-flash-lite"]).toBe(HELP_MODELS[1]?.[1]);
    expect(usage.count["gemini-3.5-flash-lite"]).toBe(1);
  });

  it("zählt im gemeinsamen Tageszähler mit und lässt fremde Einträge stehen", async () => {
    const s = setup();
    writeFileSync(
      join(s.dir, "usage.json"),
      JSON.stringify({
        day: quotaDay(Date.parse("2026-11-14T12:00:00Z")),
        count: { "gemini-4-hydrogen": 7, "gemini-3.5-flash-lite": 100 },
      }),
    );
    await s.live.helps("Как дела?", "Хорошо", "Отлично!");
    const usage = JSON.parse(readFileSync(join(s.dir, "usage.json"), "utf8"));
    expect(usage.count).toEqual({ "gemini-4-hydrogen": 7, "gemini-3.5-flash-lite": 101 });
    expect(s.live.status().helpsLeft).toBe(480 - 101 + 480);
  });

  it("scheitern die Hilfen, läuft das Gespräch trotzdem weiter", async () => {
    const s = setup({ statuses: [500, 500, 500, 500] });
    const { id } = await s.live.start(START);
    s.sockets[0]?.speak("Привет!");
    const t = await s.live.turn(id, 1, "helps");
    expect(t).toMatchObject({ done: true, helpsState: "failed", helps: null });
    await expect(s.live.send(id, { kind: "again" })).resolves.toBe(2);
    expect(s.sockets[0]?.sent.at(-1)?.realtimeInput.text).toMatch(/nicht verstanden/);
  });

  it("ohne Schlüssel kein Gespräch", async () => {
    const s = setup({ key: null });
    expect(s.live.status().available).toBe(false);
    await expect(s.live.start(START)).rejects.toThrow("no_key");
  });
});

describe("settleHelps — Richtiges nie wie falsch", () => {
  const base = { replyDe: "x", words: [], examples: [] };
  it("derselbe Satz noch einmal ist keine Korrektur", () => {
    const h = settleHelps(
      {
        ...base,
        check: {
          ok: false,
          correctedRu: "Нет, я не люблю чай.",
          focus: "Komma",
          explanationDe: "…",
        },
      },
      "Нет я не люблю чай",
    );
    expect(h.check).toEqual({ ok: true });
  });
  it("ein ganz anderer Satz ist keine Korrektur", () => {
    const h = settleHelps(
      {
        ...base,
        check: {
          ok: false,
          correctedRu: "Сегодня очень хорошая погода в городе",
          focus: "x",
          explanationDe: "y",
        },
      },
      "Я дома",
    );
    expect(h.check).toEqual({ ok: true });
  });
  it("am Thema vorbei ist kein Sprachfehler: ein Wort aus der Frage einzutauschen zählt nicht", () => {
    const h = settleHelps(
      {
        ...base,
        check: {
          ok: false,
          correctedRu: "Нет, я не люблю кино.",
          focus: "Falsches Substantiv",
          explanationDe: "Gefragt war Kino.",
        },
      },
      "Нет, я не люблю кофе.",
      "Хорошо. Ты любишь кино?",
    );
    expect(h.check).toEqual({ ok: true });
  });
  it("ein einzelnes ergänztes oder umgeformtes Wort bleibt eine Verbesserung", () => {
    expect(onlyFormChanged("Как тебя зовут?", "Я зовут Кадир", "Меня зовут Кадир")).toBe(true);
    expect(onlyFormChanged("Что ты хочешь?", "Я хотеть спать", "Я хочу спать")).toBe(true);
    expect(onlyFormChanged("Где ты работаешь?", "Я работаю офис", "Я работаю в офисе")).toBe(true);
    expect(onlyFormChanged("Что ты делаешь?", "Я дома", "Я читаю книгу дома")).toBe(false);
  });
  it("eine echte Verbesserung bleibt", () => {
    const h = settleHelps(
      {
        ...base,
        check: { ok: false, correctedRu: "Я хочу воду", focus: "Akkusativ", explanationDe: "…" },
      },
      "Я хочу вода",
    );
    expect(h.check).toMatchObject({ ok: false, correctedRu: "Я хочу воду" });
  });
});

describe("systemPrompt", () => {
  it("höhere Stufe erlaubt längere Sätze; der Verlauf wird nur beim Wiederverbinden angehängt", () => {
    expect(systemPrompt(liveStart.parse({ stage: 0 }))).toContain("höchstens fünf Wörter");
    expect(systemPrompt(liveStart.parse({ stage: 3 }))).toContain("Zwei bis drei Sätze");
    expect(systemPrompt(liveStart.parse({ stage: 0 }))).not.toContain("Bisheriger Verlauf");
  });
});
