import { mkdtempSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import {
  AnjaLive,
  ASK_PROMPT,
  ASK_UNSURE,
  ASK_VERIFY_PROMPT,
  CONFIRM_PROMPT,
  EAR_MODEL,
  REPEAT_TRIES,
  VERIFY_PROMPT,
  HELP_MODELS,
  type LiveSocket,
  PLAIN_FOCUS,
  firstJson,
  liveStart,
  onlyFormChanged,
  quotaDay,
  settleHeard,
  settleHelps,
  soundPraise,
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
  /** Welches Modell hinter der Verbindung steht — bekannt, sobald der Auftrag gesendet ist. */
  model = "";
  constructor(private earDown = false) {
    queueMicrotask(() => this.onopen?.({}));
  }
  get isEar() {
    return this.model === `models/${EAR_MODEL}`;
  }
  send(data: string) {
    const msg = JSON.parse(data);
    this.sent.push(msg);
    // Anja liest einen festen Text vor: Die Attrappe spricht ihn wortgetreu.
    const exact = String(msg.realtimeInput?.text ?? "").match(/langsam und deutlich: ([\s\S]*)\)$/);
    if (exact) queueMicrotask(() => this.speak(exact[1] as string));
    if (!msg.setup) return;
    this.model = msg.setup.model;
    // Fällt das Ohr aus, schließt Google die Verbindung, statt sie zu bestätigen.
    if (this.isEar && this.earDown) queueMicrotask(() => this.onclose?.({ code: 1011 }));
    else queueMicrotask(() => this.emit({ setupComplete: {} }));
  }
  /** Das Ohr hat Sprache bemerkt und meldet einen Zwischenstand — die fertige Mitschrift folgt. */
  noticeSpeech(text = "Я") {
    this.emit({ serverContent: { interimInputTranscription: { text } } });
  }
  /** Das Ohr meldet, was es mitgeschrieben hat. */
  hear(text: string) {
    this.emit({ serverContent: { inputTranscription: { text }, generationComplete: true } });
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

/** Die n-te Verbindung zum Gesprächsmodell, die der Test geöffnet hat. */
function sock(s: { sockets: FakeSocket[] }, n = 0): FakeSocket {
  const found = s.sockets.filter((x) => !x.isEar)[n];
  if (!found) throw new Error(`Verbindung ${n} fehlt`);
  return found;
}

/** Die Verbindung zum Mitschreib-Modell. */
function ear(s: { sockets: FakeSocket[] }): FakeSocket {
  const found = s.sockets.find((x) => x.isEar);
  if (!found) throw new Error("Das Ohr fehlt");
  return found;
}

/** Lässt angestoßene Abläufe (Verbindungsaufbau, Senden) zu Ende laufen. */
const tick = () => new Promise((r) => setTimeout(r, 0));

function setup(
  opts: {
    check?: Record<string, unknown>;
    statuses?: number[];
    key?: string | null;
    earDown?: boolean;
    /** Was die zweite Meinung zu einer Verbesserung sagt: Ist der ursprüngliche Satz in Ordnung? */
    acceptable?: boolean | "down";
    /** … und zur Erklärung: fachlich richtig? */
    explanationOk?: boolean;
    /** Urteile über frei gesprochene Erklärungen, der Reihe nach (fehlt eins: bestätigt). */
    verify?: (boolean | "down")[];
    /** … und zum zweiten Beispiel: richtig? */
    exampleOk?: boolean;
    /** So viele erste Anfragen brechen wegen Zeitüberschreitung ab. */
    timeouts?: number;
  } = {},
) {
  const dir = mkdtempSync(join(tmpdir(), "anja-live-"));
  const sockets: FakeSocket[] = [];
  // biome-ignore lint/suspicious/noExplicitAny: frei geformte Anfrage an das Hilfen-Modell
  const requests: { url: string; body: any }[] = [];
  const statuses = [...(opts.statuses ?? [])];
  const verifies = [...(opts.verify ?? [])];
  let timeouts = opts.timeouts ?? 0;
  let now = Date.parse("2026-11-14T12:00:00Z");
  const live = new AnjaLive({
    key: () => (opts.key === undefined ? "test-key" : opts.key),
    usageFile: join(dir, "usage.json"),
    now: () => now,
    // Im Test wartet niemand lange: Ein stummes Ohr gilt nach 30 ms als »nichts gehört«; hat es
    // Sprache bemerkt, bekommt es 150 ms für die fertige Mitschrift.
    earTimeoutMs: 30,
    earSpeechTimeoutMs: 150,
    lateMs: 15,
    connect: () => {
      const s = new FakeSocket(opts.earDown);
      sockets.push(s);
      return s;
    },
    request: (async (url: string, init: { body: string }) => {
      requests.push({ url, body: JSON.parse(init.body) });
      if (timeouts-- > 0) throw new Error("The operation was aborted due to timeout");
      // Die Wartezeit zwischen zwei Anfragen an dasselbe Modell soll den Test nicht bremsen.
      now += 5000;
      const system = JSON.parse(init.body).systemInstruction.parts[0].text;
      if (system.startsWith(ASK_PROMPT))
        return new Response(
          JSON.stringify({ candidates: [{ content: { parts: [{ text: JSON.stringify({ answerDe: "«Воду» ist die Wen-Form von «вода»: Я пью воду — ich trinke Wasser. (Akkusativ)" }) }] } }] }),
          { status: 200 },
        );
      if (system === VERIFY_PROMPT || system === ASK_VERIFY_PROMPT) {
        const v = verifies.shift() ?? true;
        if (v === "down") return new Response("{}", { status: 503 });
        return new Response(
          JSON.stringify({ candidates: [{ content: { parts: [{ text: JSON.stringify({ correct: v, why: "x" }) }] } }] }),
          { status: 200 },
        );
      }
      const second = system === CONFIRM_PROMPT;
      if (second) {
        if (opts.acceptable === "down") return new Response("{}", { status: 503 });
        const verdict = {
          originalAcceptable: opts.acceptable ?? false,
          explanationCorrect: opts.explanationOk ?? true,
          exampleCorrect: opts.exampleOk ?? true,
          why: "x",
        };
        return new Response(
          JSON.stringify({
            candidates: [{ content: { parts: [{ text: JSON.stringify(verdict) }] } }],
          }),
          { status: 200 },
        );
      }
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
    // Die Mitschrift ist fest auf Russisch gestellt — sonst rät sie die Sprache.
    expect(cfg.inputAudioTranscription).toEqual({ languageCodes: ["ru-RU"] });
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
    // Derselbe Ton geht ans Ohr — fest auf Russisch gestellt.
    await tick();
    const e = ear(s);
    expect(e.sent[0]?.setup.inputAudioTranscription).toEqual({ languageCodes: ["ru-RU"] });
    expect(e.sent.slice(1).map((m) => Object.keys(m.realtimeInput)[0])).toEqual(sent);
    e.hear("Я любить чай.");
    socket.speak("Я люблю чай. Хорошо!", "Я любить чай");
    const t = await s.live.turn(id, n, "helps");
    expect(t).toMatchObject({ heard: "Я любить чай.", heardState: "sure", heardAlt: "" });
    expect(t?.helps?.check).toMatchObject({
      ok: false,
      correctedRu: "Я люблю чай",
      correctedDe: "Ich mag Tee",
      focus: "Verbform",
      explanationDe: "Nach я: люблю.",
    });
    expect(JSON.parse(s.requests[1]?.body.contents[0].parts[0].text)).toEqual({
      question: "Ты любишь чай?",
      answer: "Я любить чай.",
      reply: "Я люблю чай. Хорошо!",
    });
  });

  /** Ein gesprochener Zug: was das Ohr hört, was das Gesprächsmodell mitschreibt, was danach noch nachkommt. */
  async function spoken(
    s: ReturnType<typeof setup>,
    heard: { ear?: string | null; agent?: string; late?: string },
  ) {
    const { id } = await s.live.start(START);
    sock(s).speak("Где ты живёшь?");
    await s.live.turn(id, 1, "helps");
    const n = await s.live.send(id, { kind: "audio", audio, rate: 16000 });
    await tick();
    if (heard.ear != null) ear(s).hear(heard.ear);
    sock(s).speak("Понятно. Ты любишь кофе?", heard.agent ?? "");
    if (heard.late) sock(s).emit({ serverContent: { inputTranscription: { text: heard.late } } });
    const t = await s.live.turn(id, n, "helps");
    const asked = JSON.parse(s.requests.at(-1)?.body.contents[0].parts[0].text);
    return { t, asked };
  }

  it("Russisch mit Akzent: Das Gesprächsmodell schreibt Türkisch mit, das Ohr Russisch — es gilt das Ohr", async () => {
    // So geschehen am 14.11.2026: Aus »Я живу в Германии« wurde »Yaşadığım Almanya'da« — mit »Genau so.« darunter.
    const { t, asked } = await spoken(setup(), {
      ear: "Я живу в Германии.",
      agent: "Yaşadığım Almanya'da",
    });
    expect(t).toMatchObject({ heard: "Я живу в Германии.", heardState: "sure", heardAlt: "" });
    expect(asked.answer).toBe("Я живу в Германии.");
  });

  it("kein Russisch gesprochen: nichts wird gezeigt oder bewertet, die fremde Mitschrift steht daneben", async () => {
    const { t, asked } = await spoken(setup(), {
      ear: "Ich weiß es nicht.",
      agent: "Ich weiß es nicht, keine Ahnung.",
    });
    expect(t).toMatchObject({ heard: "", heardState: "foreign", heardAlt: "Ich weiß es nicht." });
    expect(asked.answer).toBe("");
    expect(t?.helps?.check).toBeNull();
  });

  it("Stille: beide Ohren hören nichts", async () => {
    const { t } = await spoken(setup(), {});
    expect(t).toMatchObject({ heard: "", heardState: "none", done: true });
  });

  it("das zweite Ohr erfindet Wörter dazu: Es gilt das Ohr, der Satz wird bewertet", async () => {
    // So geschehen am 15.11.2026 (v0.89.117): »Да, я люблю чай.« — das Gesprächsmodell schrieb
    // »Да, я люблю любой чай.« mit, und der richtige Satz blieb ohne Urteil.
    const { t, asked } = await spoken(setup(), {
      ear: "Да, я люблю чай.",
      agent: "Да, я люблю любой чай.",
    });
    expect(t).toMatchObject({ heard: "Да, я люблю чай.", heardState: "sure" });
    expect(asked.answer).toBe("Да, я люблю чай.");
    expect(t?.helps?.check).toEqual({ ok: true });
  });

  it("hat das zweite Ohr genau die verbesserte Form gehört, wird nicht korrigiert", async () => {
    // Vielleicht hat das Ohr sich verhört — dann wäre ein richtiger Satz als falsch gezeigt.
    const { t } = await spoken(setup({ check: FIX, acceptable: false }), {
      ear: "Я хочу вода.",
      agent: "Я хочу воду.",
    });
    expect(t).toMatchObject({ heard: "Я хочу вода.", heardState: "sure" });
    expect(t?.helps?.check).toBeNull();
  });

  it("hat das zweite Ohr etwas anderes gehört, aber nicht die verbesserte Form, bleibt die Verbesserung", async () => {
    const { t } = await spoken(setup({ check: FIX, acceptable: false }), {
      ear: "Я хочу вода.",
      agent: "Я хочу любой вода.",
    });
    expect(t?.helps?.check).toMatchObject({ ok: false, correctedRu: "Я хочу воду" });
  });

  it("die zweite Mitschrift kommt erst nach Anjas Antwort zu Ende an — sie zählt trotzdem", async () => {
    const { t, asked } = await spoken(setup(), {
      ear: "Нет, я не люблю кофе.",
      agent: "Нет, я",
      late: " не люблю кофе.",
    });
    expect(t).toMatchObject({ heard: "Нет, я не люблю кофе.", heardState: "sure" });
    expect(asked.answer).toBe("Нет, я не люблю кофе.");
  });

  it("fällt das Ohr aus, läuft das Gespräch weiter — nur ohne Bewertung", async () => {
    const { t, asked } = await spoken(setup({ earDown: true }), {
      ear: null,
      agent: "Я живу в Германии.",
    });
    expect(t).toMatchObject({ heard: "Я живу в Германии.", heardState: "unsure", done: true });
    expect(asked.answer).toBe("");
  });

  const FIX = {
    ok: false,
    correctedRu: "Я хочу воду",
    correctedDe: "Ich möchte Wasser",
    focus: "Akkusativ",
    explanationDe: "…",
  };

  it("eine Verbesserung wird nur gezeigt, wenn die zweite Meinung sie bestätigt", async () => {
    const yes = await spoken(setup({ check: FIX, acceptable: false }), {
      ear: "Я хочу вода",
      agent: "Я хочу вода",
    });
    expect(yes.t?.helps?.check).toMatchObject({ ok: false, correctedRu: "Я хочу воду" });
    expect(yes.asked).toMatchObject({
      original: "Я хочу вода",
      proposedCorrection: "Я хочу воду",
      explanationDe: "Akkusativ …",
    });
  });

  it("hält die zweite Meinung den Satz für richtig, war die Verbesserung falsch — der Satz ist richtig", async () => {
    // Gemessen: »Я хочу воды« (Teilungsgenitiv) wurde als Fehler gemeldet. Am 15.11. stand unter
    // »Я из Германии, а ты?« deshalb gar kein Urteil — der Lernende wusste nicht, ob es stimmte.
    const no = await spoken(setup({ check: { ...FIX }, acceptable: true }), {
      ear: "Я хочу воды",
      agent: "Я хочу воды",
    });
    expect(no.t).toMatchObject({ heardState: "sure", helpsState: "ready" });
    expect(no.t?.helps?.check).toEqual({ ok: true });
  });

  it("langer Satz: Das Ohr braucht für die Mitschrift länger als bei Stille — es wird abgewartet", async () => {
    // So geschehen am 15.11.2026: Zwei Sätze mit Denkpausen, das Ohr brauchte länger als die feste
    // Frist von 3 s — unter klar verstandenem Russisch stand »nicht sicher verstanden«.
    const s = setup();
    const { id } = await s.live.start(START);
    sock(s).speak("Где ты живёшь?");
    await s.live.turn(id, 1, "helps");
    const n = await s.live.send(id, { kind: "audio", audio, rate: 16000 });
    await tick();
    ear(s).noticeSpeech();
    sock(s).speak("Здорово! Тебе там нравится?", "Я сейчас в Куала-Лумпур, Малайзия.");
    // Länger als die Frist bei Stille (30 ms), kürzer als die bei Sprache (150 ms).
    await new Promise((r) => setTimeout(r, 80));
    ear(s).hear("Я сейчас в Куала-Лумпур, Малайзия.");
    const t = await s.live.turn(id, n, "helps");
    expect(t).toMatchObject({ heard: "Я сейчас в Куала-Лумпур, Малайзия.", heardState: "sure" });
  });

  it("bleibt die Mitschrift trotz bemerkter Sprache aus, ist das ein Ausfall des Ohrs, keine Stille — und ein spätes Ergebnis landet nie beim nächsten Satz", async () => {
    const s = setup();
    const { id } = await s.live.start(START);
    sock(s).speak("Где ты живёшь?");
    await s.live.turn(id, 1, "helps");
    const n = await s.live.send(id, { kind: "audio", audio, rate: 16000 });
    await tick();
    const first = ear(s);
    first.noticeSpeech();
    sock(s).speak("Понятно.", "Я живу в Германии.");
    const t = await s.live.turn(id, n, "helps");
    expect(t).toMatchObject({ heard: "Я живу в Германии.", heardState: "unsure" });
    // Die verspätete Mitschrift des ersten Satzes darf nicht dem zweiten untergeschoben werden.
    const n2 = await s.live.send(id, { kind: "audio", audio, rate: 16000 });
    await tick();
    first.hear("Я живу в Германии.");
    const second = s.sockets.filter((x) => x.isEar).at(-1) as FakeSocket;
    expect(second).not.toBe(first);
    second.hear("Да.");
    sock(s).speak("Хорошо.", "Да.");
    const t2 = await s.live.turn(id, n2, "helps");
    expect(t2).toMatchObject({ heard: "Да.", heardState: "sure" });
  });

  it("ist der Fehler bestätigt, die Erklärung aber fachlich falsch, bleibt die Verbesserung — ohne die Erklärung", async () => {
    // Gemessen: »Das Reflexivpronomen меня steht im Genitiv« zu »Меня зовут Кадир«.
    const r = await spoken(setup({ check: FIX, acceptable: false, explanationOk: false }), {
      ear: "Я хочу вода",
      agent: "Я хочу вода",
    });
    expect(r.t?.helps?.check).toMatchObject({
      ok: false,
      correctedRu: "Я хочу воду",
      correctedDe: "Ich möchte Wasser",
      focus: PLAIN_FOCUS,
      explanationDe: "",
    });
    // Vorgelesen wird nur Geprüftes: keine Erklärung, nur die richtige Fassung.
    expect(r.t?.teach?.speech).not.toContain("…");
    expect(r.t?.teach?.speech).toContain("«Я хочу воду»");
  });

  it("ist die zweite Meinung nicht erreichbar, gibt es ebenfalls kein Urteil", async () => {
    const down = await spoken(setup({ check: FIX, acceptable: "down" }), {
      ear: "Я хочу вода",
      agent: "Я хочу вода",
    });
    expect(down.t?.helps?.check).toBeNull();
    expect(down.t?.helps?.replyDe).toBe("Gut! Was machst du heute?");
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
    sock(s, 0).speak("Привет! Как дела?");
    await s.live.turn(id, 1, "helps");
    s.advance(13 * 60_000);
    // Das Gespräch selbst darf nicht als verwaist gelten: Es wurde gerade benutzt.
    const n = await s.live.send(id, { kind: "text", text: "Хорошо" });
    expect(s.sockets.filter((x) => !x.isEar).length).toBe(2);
    expect(sock(s, 0).closed).toBe(true);
    const prompt = sock(s, 1).sent[0]?.setup.systemInstruction.parts[0].text;
    expect(prompt).toContain("Anja: Привет! Как дела?");
    expect(sock(s, 1).sent[1]?.realtimeInput.text).toBe("Хорошо");
    sock(s, 1).speak("Что ты делаешь?");
    expect((await s.live.turn(id, n, "done"))?.heard).toBe("Хорошо");
  });

  it("überlastetes Hilfen-Modell: das nächste übernimmt; 429 sperrt das Modell für den Tag", async () => {
    const s = setup({ statuses: [503, 429, 200] });
    const { id } = await s.live.start(START);
    sock(s, 0).speak("Привет!");
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

  it("ein Modell, das in die Frist läuft, wird danach erst als zweites gefragt", async () => {
    const s = setup({ timeouts: 1 });
    const { id } = await s.live.start(START);
    sock(s, 0).speak("Привет!");
    await s.live.turn(id, 1, "helps");
    const n = await s.live.send(id, { kind: "text", text: "Привет" });
    sock(s, 0).speak("Как дела?");
    await s.live.turn(id, n, "done");
    const models = s.requests.map((r) => r.url.split("/").pop()?.split(":")[0]);
    // Erst 3.5 (Abbruch), dann 3.1 — und beim nächsten Zug gleich 3.1.
    expect(models).toEqual(["gemini-3.5-flash-lite", "gemini-3.1-flash-lite", "gemini-3.1-flash-lite"]);
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
    const s = setup({ statuses: [500, 500, 500, 500, 500] });
    const { id } = await s.live.start(START);
    sock(s, 0).speak("Привет!");
    const t = await s.live.turn(id, 1, "helps");
    expect(t).toMatchObject({ done: true, helpsState: "failed", helps: null });
    await expect(s.live.send(id, { kind: "again" })).resolves.toBe(2);
    expect(sock(s, 0).sent.at(-1)?.realtimeInput.text).toMatch(/nicht verstanden/);
  });

  it("ohne Schlüssel kein Gespräch", async () => {
    const s = setup({ key: null });
    expect(s.live.status().available).toBe(false);
    await expect(s.live.start(START)).rejects.toThrow("no_key");
  });
});

describe("Anja als Lehrerin", () => {
  const FIX = {
    ok: false,
    correctedRu: "Я хочу воду",
    correctedDe: "Ich will Wasser",
    focus: "Hier fehlt die Wen-Form.",
    explanationDe: "Was du willst, bekommt etwas ab: вода wird zu воду (Akkusativ).",
    topicDe: "Wen-Fall nach »wollen«",
    exampleRu: "Я хочу пиццу.",
    exampleDe: "Ich will Pizza.",
  };

  /** Bis zur ersten Frage, dann ein gesprochener Satz mit Fehler. → Kennung und Zug */
  async function wrongSentence(s: ReturnType<typeof setup>, start = START) {
    const { id } = await s.live.start(start);
    sock(s).speak("Что ты хочешь пить?");
    await s.live.turn(id, 1, "helps");
    const n = await s.live.send(id, { kind: "audio", audio, rate: 16000 });
    await tick();
    ear(s).hear("Я хочу вода.");
    sock(s).speak("Хорошо, вот вода!", "Я хочу вода");
    const t = await s.live.turn(id, n, "done");
    return { id, n, t };
  }

  /** Ein Wiederholungsversuch: Nur das Ohr hört zu. */
  async function repeat(s: ReturnType<typeof setup>, id: string, heard: string) {
    const before = sock(s).sent.length;
    const n = await s.live.send(id, { kind: "audio", audio, rate: 16000 });
    await tick();
    // Der Ton geht NICHT an das Gesprächsmodell.
    expect(sock(s).sent.slice(before).some((m) => m.realtimeInput?.audio)).toBe(false);
    s.sockets.filter((x) => x.isEar).at(-1)?.hear(heard);
    for (let i = 0; i < 20 && sock(s).sent.length === before; i++) await tick();
    return { n, regie: String(sock(s).sent.at(-1)?.realtimeInput?.text ?? "") };
  }

  it("bestätigter Fehler: Anjas Antwort wird verworfen, sie liest die geprüfte Erklärung vor", async () => {
    const s = setup({ check: FIX, acceptable: false });
    const { id, n, t } = await wrongSentence(s);
    expect(t?.teach?.lang).toBe("de");
    const speech = t?.teach?.speech ?? "";
    expect(speech).toContain("So heißt es richtig: «Я хочу воду».");
    expect(speech).toContain("(Akkusativ)");
    expect(speech).toContain("Noch ein Beispiel: «Я хочу пиццу.»");
    expect(speech).toMatch(/Sag den Satz jetzt noch einmal\.$/);
    // Die verworfene Antwort ist nie zu hören — nur die Erklärung.
    expect(t?.said).not.toContain("вот вода");
    expect(t?.said).toBe(speech);
    expect(t?.expecting).toMatchObject({ correctedRu: "Я хочу воду", tries: 0, topicDe: "Wen-Fall nach »wollen«" });
    expect(t?.helps?.check).toMatchObject({ ok: false, topicDe: "Wen-Fall nach »wollen«" });
    // Die Übersetzung der verworfenen Antwort wird nicht gezeigt.
    expect(t?.helps?.replyDe).toBe("");
  });

  it("ein nicht bestätigtes Beispiel wird weder gezeigt noch vorgelesen", async () => {
    const s = setup({ check: FIX, acceptable: false, exampleOk: false });
    const { t } = await wrongSentence(s);
    expect(t?.helps?.check).toMatchObject({ exampleRu: "", exampleDe: "" });
    expect(t?.teach?.speech).not.toContain("Beispiel");
  });

  it("richtiger Satz: Anjas Antwort wird nach der Prüfung freigegeben", async () => {
    const s = setup({ check: { ok: true } });
    const { id } = await s.live.start(START);
    sock(s).speak("Что ты хочешь пить?");
    await s.live.turn(id, 1, "helps");
    const n = await s.live.send(id, { kind: "audio", audio, rate: 16000 });
    await tick();
    ear(s).hear("Я хочу воду.");
    sock(s).speak("Хорошо, вот вода!", "Я хочу воду");
    const t = await s.live.turn(id, n, "done");
    expect(t).toMatchObject({ said: "Хорошо, вот вода!", teach: null, expecting: null, helpsState: "ready" });
    expect(await collect(s.live.audio(id, n))).toBe("AAAABBBB");
  });

  it("wait=heard meldet sich, sobald verstanden ist — noch bevor Anja antwortet", async () => {
    const s = setup({ check: { ok: true } });
    const { id } = await s.live.start(START);
    sock(s).speak("Что ты хочешь пить?");
    await s.live.turn(id, 1, "helps");
    const n = await s.live.send(id, { kind: "audio", audio, rate: 16000 });
    await tick();
    ear(s).hear("Я хочу воду.");
    sock(s).emit({ serverContent: { turnComplete: true } });
    const heard = await s.live.turn(id, n, "heard");
    expect(heard).toMatchObject({ heard: "Я хочу воду.", phase: "checking" });
  });

  it("noch einmal richtig gesagt: Lob und weiter im Gespräch", async () => {
    const s = setup({ check: FIX, acceptable: false });
    const { id } = await wrongSentence(s);
    const r = await repeat(s, id, "Я хочу воду.");
    expect(r.regie).toContain("jetzt richtig gesagt: «Я хочу воду.»");
    sock(s).speak("Молодец! А какую воду ты любишь?");
    const t = await s.live.turn(id, r.n, "done");
    expect(t).toMatchObject({ repeat: { ok: true, tries: 1, gaveUp: false }, expecting: null });
    expect(t?.said).toBe("Молодец! А какую воду ты любишь?");
  });

  it("dreimal nicht geschafft: kein Drama, das Gespräch geht weiter", async () => {
    const s = setup({ check: FIX, acceptable: false });
    const { id } = await wrongSentence(s);
    for (let i = 1; i < REPEAT_TRIES; i++) {
      const r = await repeat(s, id, "Я хочу вода.");
      expect(r.regie).toContain("Noch nicht ganz. Hör genau hin: «Я хочу воду».");
      const t = await s.live.turn(id, r.n, "done");
      expect(t).toMatchObject({ repeat: { ok: false, tries: i, gaveUp: false }, expecting: { tries: i } });
    }
    const last = await repeat(s, id, "Я хочу вода.");
    expect(last.regie).toContain("noch nicht geschafft");
    sock(s).speak("Ничего страшного! Что ты делаешь сегодня?");
    const t = await s.live.turn(id, last.n, "done");
    expect(t).toMatchObject({ repeat: { ok: false, tries: REPEAT_TRIES, gaveUp: true }, expecting: null });
  });

  it("Stille beim Wiederholen zählt nicht als Versuch", async () => {
    const s = setup({ check: FIX, acceptable: false });
    const { id } = await wrongSentence(s);
    const n = await s.live.send(id, { kind: "audio", audio, rate: 16000 });
    const t = await s.live.turn(id, n, "done");
    expect(t?.said).toContain("Ich habe dich nicht gehört.");
    expect(t?.expecting).toMatchObject({ tries: 0 });
  });

  it("ab B1: zuerst kurz auf Russisch, erst nach einem Fehlversuch die Erklärung auf Deutsch", async () => {
    const s = setup({ check: FIX, acceptable: false });
    const { id, t } = await wrongSentence(s, liveStart.parse({ ...START, level: "B1" }));
    expect(t?.teach).toMatchObject({ lang: "ru" });
    expect(t?.said).toBe("Почти! Правильно так: «Я хочу воду». Скажи ещё раз.");
    const r = await repeat(s, id, "Я хочу вода.");
    const t2 = await s.live.turn(id, r.n, "done");
    expect(t2?.teach?.lang).toBe("de");
    expect(t2?.said).toContain("So heißt es richtig");
    expect(t2?.expecting).toMatchObject({ tries: 1, explained: true });
  });

  it("»Ich komme nicht weiter«: Anjas deutsche Hilfe wird geprüft, bevor sie zu hören ist", async () => {
    const s = setup({ verify: [true] });
    const { id } = await s.live.start(START);
    sock(s).speak("Что ты хочешь пить?");
    await s.live.turn(id, 1, "helps");
    const n = await s.live.send(id, { kind: "help" });
    expect(sock(s).sent.at(-1)?.realtimeInput.text).toMatch(/kommt nicht weiter\. Erklär ihm auf Deutsch/);
    sock(s).speak("Sie fragt, was du trinken willst. Du kannst sagen: «Я хочу чай». Что ты хочешь пить?");
    const t = await s.live.turn(id, n, "done");
    expect(t).toMatchObject({ verified: true });
    expect(t?.said).toContain("Sie fragt");
    expect(s.requests.some((r) => r.body.systemInstruction.parts[0].text === VERIFY_PROMPT)).toBe(true);
  });

  it("nicht bestätigte Erklärung: zweiter Anlauf; scheitert auch der, wird nichts Ungeprüftes vorgelesen", async () => {
    const s = setup({ verify: [false, false] });
    const { id } = await s.live.start(START);
    sock(s).speak("Что ты хочешь пить?");
    await s.live.turn(id, 1, "helps");
    const n = await s.live.send(id, { kind: "help" });
    sock(s).speak("Das ist der Genitiv von пить.");
    for (let i = 0; i < 20 && !String(sock(s).sent.at(-1)?.realtimeInput?.text ?? "").includes("kürzer und ganz genau"); i++) await tick();
    sock(s).speak("Пить ist ein Substantiv.");
    const t = await s.live.turn(id, n, "done");
    expect(t?.verified).toBe(false);
    expect(t?.said).not.toContain("Genitiv");
    expect(t?.said).not.toContain("Substantiv");
    expect(t?.said).toContain("nicht sicher erklären");
    expect(await collect(s.live.audio(id, n))).toBe("AAAABBBB");
  });

  it("Nachfrage: geprüfte Antwort wird gezeigt und von Anja wortgetreu vorgelesen", async () => {
    const s = setup({ verify: [true] });
    const r = await s.live.ask({ question: "Warum воду?", topic: "Wen-Fall", level: "A1" });
    expect(r).toMatchObject({ verified: true, text: expect.stringContaining("Wen-Form") });
    expect(r.id).toBeTruthy();
    const reader = s.sockets.filter((x) => !x.isEar).at(-1) as FakeSocket;
    expect(reader.sent[0]?.setup.systemInstruction.parts[0].text).toContain("liest Kadir Texte vor");
    const t = await s.live.turn(r.id as string, r.turn as number, "done");
    expect(t?.said).toContain("Wen-Form");
  });

  it("Nachfrage: besteht keine Antwort die Prüfung, wird nichts Ungeprüftes gezeigt", async () => {
    const s = setup({ verify: [false, false] });
    const r = await s.live.ask({ question: "Warum воду?", topic: "", level: "A1" });
    expect(r).toEqual({ text: ASK_UNSURE, verified: false, id: null, turn: null });
  });

  it("Niveau und Stolpersteine stehen in Anjas Auftrag", () => {
    const text = systemPrompt(
      liveStart.parse({
        ...START,
        level: "A1",
        stones: [{ topic: "Wen-Fall nach »wollen«", wrongRu: "вода", rightRu: "воду" }],
      }),
    );
    expect(text).toContain("Kadirs Niveau: A1. Hilfen und Erklärungen bekommt er noch auf Deutsch.");
    expect(text).toContain("Wen-Fall nach »wollen« (falsch «вода», richtig «воду»)");
    expect(text).toContain("Korrigiere Kadir NIE von dir aus");
    expect(systemPrompt(liveStart.parse({ ...START, level: "B2" }))).toContain("zuerst in einfachem Russisch");
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
  it("Lob nur für Wörter, die wirklich gesagt wurden — nie für die falsche Form", () => {
    expect(soundPraise("Du hast «хочу» richtig gebeugt.", "Я хочу вода", "Я хочу воду")).toBe(
      "Du hast «хочу» richtig gebeugt.",
    );
    // »вода« war gerade der Fehler.
    expect(soundPraise("«вода» steht richtig.", "Я хочу вода", "Я хочу воду")).toBe("");
    // Das Wort kam in der Antwort gar nicht vor.
    expect(soundPraise("Schön: «люблю» stimmt.", "Я хочу воду")).toBe("");
    // Falsches Fachwort: »дом« ist kein Verb.
    expect(
      soundPraise(
        "Das Verb «дом» ist korrekt als Subjekt verwendet.",
        "Мой дом в Берлин",
        "Мой дом в Берлине",
      ),
    ).toBe("");
    expect(
      soundPraise("Die Ich-Form «работаю» stimmt.", "Я работаю в офис", "Я работаю в офисе"),
    ).toBe("Die Ich-Form «работаю» stimmt.");
    // Fachbegriffe im Lob waren gemessen unzuverlässig (»я дома« ist kein Nominativ).
    expect(soundPraise("«я дома» steht richtig im Nominativ.", "Сегодня я дома")).toBe("");
    expect(soundPraise("Die Verneinung «не люблю» ist richtig.", "Нет, я не люблю кофе")).toBe(
      "Die Verneinung «не люблю» ist richtig.",
    );
    // Schwammig oder erfunden: kein »oder«, keine Verneinung ohne «не».
    expect(
      soundPraise("Die Verneinung oder das Verb «хочу» ist gut.", "Я хочу вода", "Я хочу воду"),
    ).toBe("");
    expect(soundPraise("Die Verneinung «хочу» ist richtig.", "Я хочу вода", "Я хочу воду")).toBe(
      "",
    );
    // Ohne genanntes Wort ist das Lob zu allgemein.
    expect(soundPraise("Sehr gut gemacht!", "Я хочу воду")).toBe("");
  });
  it("was kein Russisch ist, bekommt kein Urteil — auch kein »Genau so«", () => {
    const h = settleHelps(
      { ...base, goodDe: "Gut gesagt.", check: { ok: true } },
      "Yaşadığım Almanya'da",
      "Где ты живёшь?",
    );
    expect(h.check).toBeNull();
    expect(h.goodDe).toBe("");
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

describe("soundPraise — Namen", () => {
  it("einen Namen zu loben ist kein Lob, ein Wort davor schon", () => {
    expect(soundPraise("Die Form «Кадир» ist richtig.", "Я зовут Кадир")).toBe("");
    expect(soundPraise("«Меня зовут» stimmt.", "Меня зовут Кадир")).toBe("«Меня зовут» stimmt.");
    // Am Satzanfang ist Großschreibung kein Name.
    expect(soundPraise("Die Ich-Form «Люблю» stimmt.", "Люблю кофе")).toBe("Die Ich-Form «Люблю» stimmt.");
  });
});

describe("settleHeard — das Ohr gilt", () => {
  it("gleich gehört (Satzzeichen und Großschreibung egal)", () => {
    expect(settleHeard("Привет, очень хорошо. А у тебя?", "привет очень хорошо а у тебя")).toEqual({
      text: "Привет, очень хорошо. А у тебя?",
      state: "sure",
      alt: "",
    });
  });
  it("das Ohr gilt, wenn das zweite fehlt, abgeschnitten ist oder eine fremde Sprache gehört hat", () => {
    expect(settleHeard("Я живу в Германии.", "").state).toBe("sure");
    expect(settleHeard("Я живу в Германии.", "Yo llevo Alemania.")).toEqual({
      text: "Я живу в Германии.",
      state: "sure",
      alt: "",
    });
    expect(settleHeard("Да, я люблю музыка.", "Да, я люблю").state).toBe("sure");
    expect(settleHeard("Да, я очень люблю русскую музыку.", "Да").state).toBe("sure");
  });
  it("ist die Mitschrift des Ohrs die kürzere, gilt die vollständige", () => {
    expect(settleHeard("я хочу", "Я хочу чай.")).toMatchObject({
      text: "Я хочу чай.",
      state: "sure",
    });
  });
  it("andere russische Wörter gehört: Es gilt das Ohr, die andere Fassung bleibt zum Abgleich", () => {
    // Gemessen am 15.11.2026: Das Gesprächsmodell erfand »любовь«, »любой«, ließ »по-русски« aus —
    // das Ohr schrieb jedes Mal richtig mit.
    expect(settleHeard("Нет, я не люблю кофе.", "Нет, я не люблю любовь люблю кофе.")).toEqual({
      text: "Нет, я не люблю кофе.",
      state: "sure",
      alt: "Нет, я не люблю любовь люблю кофе.",
    });
    expect(settleHeard("Я живу в Германии.", "Он пьёт кофе").state).toBe("sure");
  });
  it("hat nur das zweite Ohr Russisch gehört, reicht das nicht für ein Urteil", () => {
    expect(settleHeard(null, "Я живу в Германии.")).toMatchObject({
      text: "Я живу в Германии.",
      state: "unsure",
    });
    expect(settleHeard("Yo llevo a Alemania.", "Я живу в Германии.")).toMatchObject({
      text: "Я живу в Германии.",
      state: "unsure",
    });
  });
  it("fremde Sprache, Stille und Rauschen", () => {
    expect(settleHeard("Almanya'da yaşıyorum.", "")).toEqual({
      text: "",
      state: "foreign",
      alt: "Almanya'da yaşıyorum.",
    });
    expect(settleHeard("", "")).toEqual({ text: "", state: "none", alt: "" });
    expect(settleHeard(null, "")).toEqual({ text: "", state: "none", alt: "" });
    // Bei Stille erfindet das Gesprächsmodell gelegentlich eine Zahl.
    expect(settleHeard("", "100")).toEqual({ text: "", state: "none", alt: "" });
  });
});

describe("firstJson", () => {
  it("nimmt das erste vollständige Objekt, auch wenn etwas dahinter hängt oder Klammern im Text stehen", () => {
    expect(firstJson('{"a":{"b":"x } y"},"c":1} und noch etwas }')).toEqual({
      a: { b: "x } y" },
      c: 1,
    });
    expect(firstJson('Hier: {"a":"\\"zitat\\" }"}')).toEqual({ a: '"zitat" }' });
    expect(() => firstJson("kein Objekt")).toThrow("no_json");
  });
});

describe("systemPrompt", () => {
  it("höhere Stufe erlaubt längere Sätze; der Verlauf wird nur beim Wiederverbinden angehängt", () => {
    expect(systemPrompt(liveStart.parse({ stage: 0 }))).toContain("höchstens fünf Wörter");
    expect(systemPrompt(liveStart.parse({ stage: 3 }))).toContain("Zwei bis drei Sätze");
    expect(systemPrompt(liveStart.parse({ stage: 0 }))).not.toContain("Bisheriger Verlauf");
  });
});
