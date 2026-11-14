/**
 * Anja live — ein gesprochenes Übungsgespräch.
 *
 * Der Lernende spricht (Sprechtaste), Anja antwortet sofort gesprochen. Dahinter stehen zwei Dienste
 * aus Googles kostenloser Stufe, jeder für das, was er gemessen gut kann (14.11.2026):
 *
 *  - Gemini Live (Sprache rein, Sprache raus) führt das Gespräch. Kein Tageslimit, erste Tonausgabe
 *    nach rund 0,8 s. Es hört Fehler so, wie sie gesagt wurden, und sagt die richtige Form vor.
 *  - Gemini Flash-Lite liefert nach jedem Zug, was auf dem Bildschirm steht: die strenge Satzprüfung,
 *    die deutsche Übersetzung, die Bedeutung je Wort und zwei Beispielantworten.
 *
 * Die sichtbare Korrektur kommt NIE aus dem, was Anja sagt (am Ton ist nicht zu erkennen, ob sie
 * korrigiert oder selbst antwortet), sondern nur aus der Satzprüfung — und die wird fest nachgeprüft
 * (plausibleCorrection): Richtiges darf nie wie falsch aussehen.
 *
 * Der Server hält die Verbindung zu Google; die Oberfläche braucht nur gewöhnliche Abrufe.
 */
import { randomUUID } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";
import { z } from "zod";
import { plausibleCorrection } from "./conversation.js";

const LIVE_URL =
  "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent";
const TEXT_URL = "https://generativelanguage.googleapis.com/v1beta/models";

/** Gesprächsmodelle in Reihe: antwortet das erste beim Verbinden nicht, übernimmt das nächste. */
export const LIVE_MODELS = ["gemini-3.8-live", "gemini-3.1-flash-live-preview"];
/** Modelle für Prüfung und Hilfen mit ihrer Tagesgrenze (Google: 500; etwas Luft für die Prüfkette der Stücke). */
export const HELP_MODELS: [string, number][] = [
  ["gemini-3.5-flash-lite", 480],
  ["gemini-3.1-flash-lite", 480],
];

const SETUP_TIMEOUT_MS = 8_000;
const TURN_TIMEOUT_MS = 30_000;
/** Google beendet eine Sitzung nach 15 Minuten — vorher wird mit dem Verlauf neu verbunden. */
const SOCKET_MAX_AGE_MS = 12 * 60_000;
const IDLE_MS = 10 * 60_000;
const MAX_SESSIONS = 8;
const HELP_TIMEOUT_MS = 9_000;
const QUOTA_ZONE = "America/Los_Angeles";

export const liveStart = z.object({
  stage: z.number().int().min(0).max(4).default(0),
  scenario: z.string().trim().max(300).default(""),
  interest: z.string().trim().max(300).default(""),
  knownWords: z.array(z.string().trim().min(1).max(80)).max(150).default([]),
  /** Bisheriger Verlauf, wenn ein Gespräch nach einer Unterbrechung weitergeht. */
  lines: z
    .array(z.object({ who: z.enum(["anja", "kadir"]), ru: z.string().trim().min(1).max(600) }))
    .max(80)
    .default([]),
});
export type LiveStart = z.infer<typeof liveStart>;

export const liveInput = z.discriminatedUnion("kind", [
  z.object({
    kind: z.literal("audio"),
    /** 16 Bit, mono, little-endian — als Base64. */
    audio: z.string().min(400).max(6_000_000),
    rate: z.number().int().min(8000).max(48000).default(16000),
  }),
  z.object({ kind: z.literal("text"), text: z.string().trim().min(1).max(600) }),
  z.object({ kind: z.literal("again") }),
  z.object({ kind: z.literal("easier") }),
]);
export type LiveInput = z.infer<typeof liveInput>;

const helpsReply = z.object({
  check: z
    .object({
      ok: z.boolean(),
      correctedRu: z.string().max(600).default(""),
      correctedDe: z.string().max(600).default(""),
      focus: z.string().max(240).default(""),
      explanationDe: z.string().max(400).default(""),
    })
    .default({ ok: true, correctedRu: "", correctedDe: "", focus: "", explanationDe: "" }),
  replyDe: z.string().trim().min(1).max(900),
  words: z
    .array(z.object({ ru: z.string().trim().max(80), de: z.string().trim().max(100) }))
    .max(40),
  examples: z
    .array(z.object({ ru: z.string().trim().max(220), de: z.string().trim().max(260) }))
    .max(4),
});

export interface LiveHelps {
  replyDe: string;
  words: { ru: string; de: string }[];
  examples: { ru: string; de: string }[];
  /** null = nichts zu prüfen (Anja hat begonnen); ok = der Satz stimmt; sonst die eine Verbesserung. */
  check:
    | null
    | { ok: true }
    | { ok: false; correctedRu: string; correctedDe: string; focus: string; explanationDe: string };
}

export type HelpsState = "none" | "pending" | "ready" | "failed";

export interface LiveTurnView {
  n: number;
  kind: "start" | LiveInput["kind"];
  done: boolean;
  error: string | null;
  /** Was die Spracherkennung vom Lernenden verstanden hat (bei getippter Antwort: der Text). */
  heard: string;
  /** Anjas Antwort als Mitschrift. */
  said: string;
  firstMs: number | null;
  helps: LiveHelps | null;
  helpsState: HelpsState;
}

interface Turn extends LiveTurnView {
  audio: Buffer[];
  startedAt: number;
  waiters: Set<() => void>;
  timer: ReturnType<typeof setTimeout> | null;
}

/** Die Teile der WebSocket-Schnittstelle, die gebraucht werden — in Tests durch eine Attrappe ersetzt. */
export interface LiveSocket {
  binaryType?: string;
  send(data: string): void;
  close(): void;
  onopen: ((ev: unknown) => void) | null;
  onmessage: ((ev: { data: unknown }) => void) | null;
  onclose: ((ev: { code?: number; reason?: string }) => void) | null;
  onerror: ((ev: unknown) => void) | null;
}

export interface AnjaLiveDeps {
  /** Liefert den Schlüssel bei jedem Gebrauch neu — er darf nachträglich hinterlegt werden. */
  key: () => string | null;
  /** Gemeinsamer Tageszähler mit der Aufbereitung der Stücke (erleben/usage.json). */
  usageFile: string;
  connect?: (url: string) => LiveSocket;
  request?: typeof fetch;
  now?: () => number;
  voice?: string | undefined;
  log?: ((msg: string) => void) | undefined;
}

const REGIE = {
  start: "(Regie: Beginne jetzt das Gespräch. Begrüße Kadir kurz und stelle deine erste Frage.)",
  resume:
    "(Regie: Die Verbindung war kurz weg. Mach genau dort weiter und stelle deine nächste Frage.)",
  again:
    "(Regie: Kadir hat dich nicht verstanden. Sag deinen letzten Satz noch einmal, langsamer und deutlich.)",
  easier:
    "(Regie: Das war zu schwer für Kadir. Stell deine letzte Frage noch einmal, mit einfacheren Wörtern und kürzer.)",
};

/** Wie anspruchsvoll Anja je Stufe spricht — dieselben fünf Stufen wie im getippten Gespräch. */
const LEVELS = [
  "Kadir fängt fast bei null an. Höchstens zwei sehr kurze Sätze je Antwort, jeder höchstens fünf Wörter. Nur die allereinfachsten Alltagswörter; höchstens EIN neues Wort je Antwort.",
  "Kadir ist Anfänger. Höchstens zwei kurze Sätze je Antwort, jeder höchstens sechs Wörter. Einfache Alltagswörter; höchstens ein neues Wort je Antwort.",
  "Kadir kann einfache Sätze bilden. Höchstens zwei Sätze je Antwort, jeder höchstens acht Wörter. Alltagswortschatz, Gegenwart und einfache Vergangenheit.",
  "Kadir kommt im Alltag zurecht. Zwei bis drei Sätze je Antwort, normale Alltagssprache, gelegentlich eine Nachfrage nach dem Warum.",
  "Kadir spricht schon flüssiger. Zwei bis drei Sätze je Antwort in natürlicher Alltagssprache, Nachfragen nach Gründen und Meinungen.",
];

export function systemPrompt(
  setup: LiveStart,
  lines: { who: "anja" | "kadir"; ru: string }[] = [],
): string {
  const known = setup.knownWords.slice(0, 120).join(", ");
  return [
    "Du bist Anja, eine herzliche Russin Mitte zwanzig. Du führst ein gesprochenes Übungsgespräch mit Kadir, einem deutschen Russisch-Lernenden.",
    "",
    "Regeln, die immer gelten:",
    "- Sprich NUR Russisch. Kein Deutsch, kein Englisch, auch nicht zur Erklärung.",
    "- Sprich langsam und deutlich.",
    `- ${LEVELS[setup.stage]}`,
    known ? `- Diese Wörter kennt Kadir schon gut, benutze sie bevorzugt: ${known}.` : "",
    "- Reagiere zuerst kurz und echt auf das, was Kadir gesagt hat, und stelle dann GENAU EINE Frage. Fragt Kadir dich etwas («А ты?»), beantworte es zuerst.",
    "- Stelle nie eine Frage, die du in diesem Gespräch schon gestellt hast. Wechsle das Thema, wenn eines erschöpft ist — du bist neugierig auf Kadirs Leben.",
    "- Macht Kadir einen Sprachfehler, sag seinen Satz einmal richtig vor und geh dann weiter. War sein Satz richtig, wiederhole ihn NICHT.",
    "- Du bist eine Gesprächspartnerin, keine Lehrerin: keine Erklärungen, keine Vorträge, kein Lob für jede Antwort.",
    "- Text in runden Klammern, der mit «Regie:» beginnt, stammt nicht von Kadir. Es ist eine Anweisung an dich: befolge sie, sprich sie nie aus.",
    setup.scenario ? `\nDie Situation: ${setup.scenario}` : "",
    setup.interest
      ? `Kadir interessiert sich für: ${setup.interest}. Lenk das Gespräch gern dorthin.`
      : "",
    lines.length
      ? `\nBisheriger Verlauf — führe ihn nahtlos fort und begrüße Kadir nicht noch einmal:\n${lines
          .slice(-24)
          .map((l) => `${l.who === "anja" ? "Anja" : "Kadir"}: ${l.ru}`)
          .join("\n")}`
      : "",
  ]
    .filter((line) => line !== "")
    .join("\n");
}

export const HELPS_PROMPT = `You support a spoken Russian practice conversation between Anja (a Russian native speaker) and Kadir, a German-speaking beginner.
Input is JSON: {"question": Anja's previous line, "answer": what Kadir just said (a speech transcript), "reply": Anja's new line}.
Return ONLY one JSON object with exactly these fields:
{"check":{"ok":true,"correctedRu":"","correctedDe":"","focus":"","explanationDe":""},"replyDe":"","words":[{"ru":"","de":""}],"examples":[{"ru":"","de":""},{"ru":"","de":""}]}

check — judge ONLY Kadir's answer. It is a speech transcript: ignore punctuation and capitalisation completely.
ok=true when the sentence is understandable and has no real grammar mistake. Short answers and single words are fine.
WHAT the learner says (likes, dislikes, choices) is never a mistake, and a different wording than you would choose is not a mistake.
Whether the answer fits the question is NOT your concern: an answer about a different topic is still ok=true when its Russian is correct. Never swap a word for another word to make the answer fit the question.
ok=false ONLY for a real language mistake such as a wrong word form or a missing verb. Then:
correctedRu = the learner's own sentence with ONLY that mistake fixed (same words otherwise), correctedDe = its German translation,
focus = one short German sentence naming the issue, explanationDe = one short German sentence explaining the rule.
When ok=true, or when answer is empty, the other four fields are empty strings.

replyDe — a natural German translation of reply (informal "du").
words — every word of reply in order (skip punctuation, list a repeated word once): ru = the word exactly as it appears in reply (lower case), de = its German meaning in THIS sentence, one to three words, German nouns capitalised as usual.
examples — two DIFFERENT short answers (2 to 6 words) Kadir could give to the question in reply. Simple beginner Russian, grammatically perfect, natural, written as normal sentences (capital letter, full stop); de = German translation. Do not answer with a bare "да" or "нет".`;

export function quotaDay(nowMs: number): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: QUOTA_ZONE }).format(new Date(nowMs));
}

const cyrillic = (s: string) => /[а-яё]/i.test(s);
const wordsOf = (s: string) =>
  s
    .toLowerCase()
    .replaceAll("ё", "е")
    .split(/[^а-я]+/)
    .filter(Boolean);

/**
 * Eine Verbesserung darf die FORM eines Wortes ändern, nie den Inhalt. Tauscht das Modell ein Wort
 * gegen eines aus der Frage (»кофе« → »кино«, weil nach Kino gefragt war), dann bewertet es, ob die
 * Antwort zur Frage passt — das ist kein Sprachfehler. Ebenso wenig, wenn gleich zwei neue Wörter
 * auftauchen: Das wäre ein anderer Satz.
 * »Neu« ist ein Wort, das mit keinem Wort der Antwort den Anfang teilt (хотеть → хочу ist dasselbe Wort).
 */
export function onlyFormChanged(question: string, answer: string, correctedRu: string): boolean {
  const mine = wordsOf(answer);
  const asked = new Set(wordsOf(question).map((w) => w.slice(0, 3)));
  const added = wordsOf(correctedRu).filter(
    (w) => !mine.some((m) => m.slice(0, 2) === w.slice(0, 2)),
  );
  return added.length <= 1 && !added.some((w) => w.length >= 3 && asked.has(w.slice(0, 3)));
}

/** Macht aus der Modell-Antwort das, was die Oberfläche zeigen darf — fest nachgeprüft. */
export function settleHelps(raw: unknown, answer: string, question = ""): LiveHelps {
  const r = helpsReply.parse(raw);
  const c = r.check;
  const fixable =
    !!answer.trim() &&
    !c.ok &&
    !!c.focus.trim() &&
    plausibleCorrection(answer, c.correctedRu) &&
    onlyFormChanged(question, answer, c.correctedRu);
  return {
    replyDe: r.replyDe,
    words: r.words.filter((w) => cyrillic(w.ru) && w.de),
    examples: r.examples.filter((e) => cyrillic(e.ru) && e.de).slice(0, 3),
    check: !answer.trim()
      ? null
      : fixable
        ? {
            ok: false,
            correctedRu: c.correctedRu,
            correctedDe: c.correctedDe,
            focus: c.focus,
            explanationDe: c.explanationDe,
          }
        : { ok: true },
  };
}

class Session {
  readonly id = randomUUID().replace(/-/g, "").slice(0, 16);
  readonly lines: { who: "anja" | "kadir"; ru: string }[];
  readonly turns: Turn[] = [];
  lastUsed: number;
  private socket: LiveSocket | null = null;
  private openedAt = 0;
  private stale = false;
  private current: Turn | null = null;

  constructor(
    private host: AnjaLive,
    readonly setup: LiveStart,
  ) {
    this.lines = [...setup.lines];
    this.lastUsed = host.now();
  }

  get busy() {
    return !!this.current && !this.current.done && !this.current.error;
  }

  close() {
    const s = this.socket;
    this.socket = null;
    try {
      s?.close();
    } catch {
      /* schon zu */
    }
    if (this.current && !this.current.done) this.fail(this.current, "closed");
  }

  /** Öffnet die Verbindung zu Google und wartet, bis das Modell bereit ist. */
  private async open(): Promise<LiveSocket> {
    const key = this.host.deps.key();
    if (!key) throw new Error("no_key");
    let lastError = "live_unavailable";
    for (const model of LIVE_MODELS) {
      try {
        const socket = await this.openWith(model, key);
        this.socket = socket;
        this.openedAt = this.host.now();
        this.stale = false;
        return socket;
      } catch (e) {
        lastError = e instanceof Error ? e.message : String(e);
        this.host.log(`[anja-live] ${model} nicht bereit: ${lastError}`);
      }
    }
    throw new Error(lastError);
  }

  private openWith(model: string, key: string): Promise<LiveSocket> {
    return new Promise((resolve, reject) => {
      const socket = this.host.connect(`${LIVE_URL}?key=${encodeURIComponent(key)}`);
      socket.binaryType = "arraybuffer";
      let settled = false;
      const timer = setTimeout(() => finish(new Error("setup_timeout")), SETUP_TIMEOUT_MS);
      const finish = (error?: Error) => {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        if (error) {
          try {
            socket.close();
          } catch {
            /* schon zu */
          }
          reject(error);
        } else resolve(socket);
      };
      socket.onopen = () =>
        socket.send(
          JSON.stringify({
            setup: {
              model: `models/${model}`,
              generationConfig: {
                responseModalities: ["AUDIO"],
                speechConfig: {
                  voiceConfig: {
                    prebuiltVoiceConfig: { voiceName: this.host.deps.voice ?? "Kore" },
                  },
                },
              },
              systemInstruction: { parts: [{ text: systemPrompt(this.setup, this.lines) }] },
              outputAudioTranscription: {},
              inputAudioTranscription: {},
              // Sprechtaste: Der Lernende bestimmt, wann sein Satz zu Ende ist — ein Anfänger denkt mitten im Satz nach.
              realtimeInputConfig: { automaticActivityDetection: { disabled: true } },
            },
          }),
        );
      socket.onmessage = (ev) => {
        const msg = decode(ev.data);
        if (!msg) return;
        if (!settled) {
          if (msg.setupComplete) finish();
          return;
        }
        this.onMessage(msg);
      };
      socket.onerror = () => finish(new Error("socket_error"));
      socket.onclose = (ev) => {
        finish(new Error(`closed_${ev?.code ?? ""}_${String(ev?.reason ?? "").slice(0, 80)}`));
        if (this.socket === socket) {
          this.socket = null;
          if (this.current && !this.current.done) this.fail(this.current, "connection_lost");
        }
      };
    });
  }

  private onMessage(msg: Record<string, unknown>) {
    if (msg.goAway) this.stale = true;
    const turn = this.current;
    const sc = msg.serverContent as
      | {
          modelTurn?: { parts?: { inlineData?: { data?: string } }[] };
          outputTranscription?: { text?: string };
          inputTranscription?: { text?: string };
          turnComplete?: boolean;
        }
      | undefined;
    if (!turn || turn.done || !sc) return;
    for (const part of sc.modelTurn?.parts ?? []) {
      const data = part.inlineData?.data;
      if (!data) continue;
      turn.firstMs ??= this.host.now() - turn.startedAt;
      turn.audio.push(Buffer.from(data, "base64"));
    }
    if (sc.outputTranscription?.text) turn.said += sc.outputTranscription.text;
    if (sc.inputTranscription?.text) turn.heard += sc.inputTranscription.text;
    if (sc.turnComplete) this.complete(turn);
    else wake(turn);
  }

  private complete(turn: Turn) {
    if (turn.timer) clearTimeout(turn.timer);
    turn.heard = turn.heard.replace(/\s+/g, " ").trim();
    turn.said = turn.said.replace(/\s+/g, " ").trim();
    turn.done = true;
    const question = [...this.lines].reverse().find((l) => l.who === "anja")?.ru ?? "";
    const answered = turn.kind === "audio" || turn.kind === "text";
    if (answered && turn.heard) this.lines.push({ who: "kadir", ru: turn.heard });
    if (turn.said) this.lines.push({ who: "anja", ru: turn.said });
    wake(turn);
    if (!turn.said) return;
    turn.helpsState = "pending";
    this.host
      .helps(question, answered ? turn.heard : "", turn.said)
      .then((helps) => {
        turn.helps = helps;
        turn.helpsState = "ready";
      })
      .catch((e) => {
        this.host.log(`[anja-live] Hilfen fehlgeschlagen: ${String(e).slice(0, 160)}`);
        turn.helpsState = "failed";
      })
      .finally(() => wake(turn));
  }

  private fail(turn: Turn, error: string) {
    if (turn.timer) clearTimeout(turn.timer);
    turn.error = error;
    wake(turn);
  }

  async send(input: LiveInput | { kind: "start" }): Promise<number> {
    if (this.busy) throw new Error("busy");
    this.lastUsed = this.host.now();
    const resumed = this.lines.length > 0;
    if (!this.socket || this.stale || this.host.now() - this.openedAt > SOCKET_MAX_AGE_MS) {
      this.close();
      await this.open();
    }
    const socket = this.socket;
    if (!socket) throw new Error("live_unavailable");
    const turn: Turn = {
      n: this.turns.length + 1,
      kind: input.kind,
      done: false,
      error: null,
      heard: input.kind === "text" ? input.text : "",
      said: "",
      firstMs: null,
      helps: null,
      helpsState: "none",
      audio: [],
      startedAt: this.host.now(),
      waiters: new Set(),
      timer: null,
    };
    this.turns.push(turn);
    this.current = turn;
    turn.timer = setTimeout(() => {
      if (turn.done || turn.error) return;
      this.fail(turn, "timeout");
      this.stale = true;
    }, TURN_TIMEOUT_MS);
    const say = (payload: unknown) => socket.send(JSON.stringify({ realtimeInput: payload }));
    if (input.kind === "audio") {
      const pcm = Buffer.from(input.audio, "base64");
      say({ activityStart: {} });
      for (let i = 0; i < pcm.length; i += 16_000)
        say({
          audio: {
            data: pcm.subarray(i, i + 16_000).toString("base64"),
            mimeType: `audio/pcm;rate=${input.rate}`,
          },
        });
      say({ activityEnd: {} });
    } else if (input.kind === "text") say({ text: input.text });
    else if (input.kind === "start") say({ text: resumed ? REGIE.resume : REGIE.start });
    else say({ text: REGIE[input.kind] });
    return turn.n;
  }

  turn(n: number): Turn | undefined {
    return this.turns[n - 1];
  }
}

function wake(turn: Turn) {
  const waiting = [...turn.waiters];
  turn.waiters.clear();
  for (const fn of waiting) fn();
}

/** Wartet auf die nächste Änderung am Zug (oder höchstens `ms`). */
function changed(turn: Turn, ms: number): Promise<void> {
  return new Promise((resolve) => {
    const done = () => {
      clearTimeout(timer);
      turn.waiters.delete(done);
      resolve();
    };
    const timer = setTimeout(done, ms);
    turn.waiters.add(done);
  });
}

function decode(data: unknown): Record<string, unknown> | null {
  try {
    const text =
      typeof data === "string"
        ? data
        : data instanceof ArrayBuffer
          ? Buffer.from(data).toString("utf8")
          : Buffer.isBuffer(data)
            ? data.toString("utf8")
            : null;
    return text ? (JSON.parse(text) as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}

const view = (t: Turn): LiveTurnView => ({
  n: t.n,
  kind: t.kind,
  done: t.done,
  error: t.error,
  heard: t.heard,
  said: t.said,
  firstMs: t.firstMs,
  helps: t.helps,
  helpsState: t.helpsState,
});

export class AnjaLive {
  private sessions = new Map<string, Session>();
  private lastHelp = new Map<string, number>();

  constructor(readonly deps: AnjaLiveDeps) {}

  now() {
    return (this.deps.now ?? Date.now)();
  }

  log(msg: string) {
    this.deps.log?.(msg);
  }

  connect(url: string): LiveSocket {
    if (this.deps.connect) return this.deps.connect(url);
    return new WebSocket(url) as unknown as LiveSocket;
  }

  status() {
    return { available: !!this.deps.key(), helpsLeft: this.helpsLeft() };
  }

  // ── Tageszähler (geteilt mit der Aufbereitung der Stücke) ────────────────

  private usage(): { day: string; count: Record<string, number> } {
    const day = quotaDay(this.now());
    try {
      const u = JSON.parse(readFileSync(this.deps.usageFile, "utf8")) as {
        day?: string;
        count?: Record<string, number>;
      };
      if (u.day === day && u.count) return { day, count: u.count };
    } catch {
      /* noch kein Zähler */
    }
    return { day, count: {} };
  }

  private count(model: string, to?: number) {
    const u = this.usage();
    u.count[model] = to ?? (u.count[model] ?? 0) + 1;
    try {
      if (!existsSync(dirname(this.deps.usageFile)))
        mkdirSync(dirname(this.deps.usageFile), { recursive: true });
      writeFileSync(this.deps.usageFile, JSON.stringify(u));
    } catch {
      /* Zähler ist nur Vorsicht */
    }
  }

  helpsLeft(): number {
    const u = this.usage();
    return HELP_MODELS.reduce((sum, [m, cap]) => sum + Math.max(0, cap - (u.count[m] ?? 0)), 0);
  }

  // ── Prüfung und Hilfen ───────────────────────────────────────────────────

  /**
   * Satzprüfung, Übersetzung, Wortbedeutungen und Beispielantworten in EINER Anfrage.
   * Überlastete Modelle (503) kommen vor: bis zu vier Anläufe, reihum über die Modelle.
   */
  async helps(question: string, answer: string, reply: string): Promise<LiveHelps> {
    const key = this.deps.key();
    if (!key) throw new Error("no_key");
    const request = this.deps.request ?? fetch;
    let lastError = "helps_exhausted";
    for (let attempt = 0; attempt < 4; attempt++) {
      const u = this.usage();
      const open = HELP_MODELS.filter(([m, cap]) => (u.count[m] ?? 0) < cap).map(([m]) => m);
      if (!open.length) throw new Error("helps_exhausted");
      const model = open[attempt % open.length] as string;
      // 15 Anfragen je Minute und Modell: nie dichter als alle 4,2 s an dasselbe Modell.
      const wait = (this.lastHelp.get(model) ?? 0) + 4_200 - this.now();
      if (wait > 0) await new Promise((r) => setTimeout(r, wait));
      this.lastHelp.set(model, this.now());
      try {
        const res = await request(`${TEXT_URL}/${model}:generateContent`, {
          method: "POST",
          headers: { "content-type": "application/json", "x-goog-api-key": key },
          signal: AbortSignal.timeout(HELP_TIMEOUT_MS),
          body: JSON.stringify({
            systemInstruction: { parts: [{ text: HELPS_PROMPT }] },
            contents: [
              { role: "user", parts: [{ text: JSON.stringify({ question, answer, reply }) }] },
            ],
            generationConfig: {
              temperature: 0.2,
              maxOutputTokens: 1200,
              responseMimeType: "application/json",
            },
          }),
        });
        if (res.status === 429) {
          const cap = HELP_MODELS.find(([m]) => m === model)?.[1] ?? 0;
          this.count(model, cap);
          lastError = "quota";
          continue;
        }
        if (!res.ok) {
          lastError = `http_${res.status}`;
          continue;
        }
        this.count(model);
        const body = (await res.json()) as {
          candidates?: { content?: { parts?: { text?: string; thought?: boolean }[] } }[];
        };
        const text = (body.candidates?.[0]?.content?.parts ?? [])
          .filter((p) => !p.thought)
          .map((p) => p.text ?? "")
          .join("");
        const found = text.match(/\{[\s\S]*\}/);
        if (!found) {
          lastError = "no_json";
          continue;
        }
        return settleHelps(JSON.parse(found[0]), answer, question);
      } catch (e) {
        lastError = e instanceof Error ? e.message.slice(0, 80) : "failed";
      }
    }
    throw new Error(lastError);
  }

  // ── Gespräche ────────────────────────────────────────────────────────────

  private prune() {
    const now = this.now();
    for (const [id, s] of this.sessions)
      if (now - s.lastUsed > IDLE_MS) {
        s.close();
        this.sessions.delete(id);
      }
    while (this.sessions.size >= MAX_SESSIONS) {
      const oldest = [...this.sessions.values()].sort((a, b) => a.lastUsed - b.lastUsed)[0];
      if (!oldest) break;
      oldest.close();
      this.sessions.delete(oldest.id);
    }
  }

  /** Beginnt ein Gespräch: Anja eröffnet. → Kennung und Nummer des ersten Zugs */
  async start(input: LiveStart): Promise<{ id: string; turn: number }> {
    if (!this.deps.key()) throw new Error("no_key");
    this.prune();
    const session = new Session(this, input);
    this.sessions.set(session.id, session);
    try {
      return { id: session.id, turn: await session.send({ kind: "start" }) };
    } catch (e) {
      session.close();
      this.sessions.delete(session.id);
      throw e;
    }
  }

  async send(id: string, input: LiveInput): Promise<number> {
    const session = this.sessions.get(id);
    if (!session) throw new Error("unknown_session");
    return session.send(input);
  }

  end(id: string) {
    this.sessions.get(id)?.close();
    this.sessions.delete(id);
  }

  /** Stand eines Zugs; `wait` hält die Antwort zurück, bis Anja fertig gesprochen hat bzw. die Hilfen da sind. */
  async turn(id: string, n: number, wait: "none" | "done" | "helps" = "none", maxMs = 25_000) {
    const turn = this.sessions.get(id)?.turn(n);
    if (!turn) return null;
    const until = this.now() + maxMs;
    const settled = () =>
      !!turn.error ||
      (wait === "none"
        ? true
        : wait === "done"
          ? turn.done
          : turn.done &&
            turn.helpsState !== "pending" &&
            (turn.helpsState !== "none" || !turn.said));
    while (!settled() && this.now() < until)
      await changed(turn, Math.min(1000, until - this.now()));
    return view(turn);
  }

  /** Anjas Ton, während er entsteht: 24 kHz, 16 Bit, mono. Endet, wenn der Zug fertig ist. */
  async *audio(id: string, n: number): AsyncGenerator<Buffer> {
    const turn = this.sessions.get(id)?.turn(n);
    if (!turn) return;
    let i = 0;
    for (;;) {
      while (i < turn.audio.length) yield turn.audio[i++] as Buffer;
      if (turn.done || turn.error) return;
      await changed(turn, 1000);
    }
  }

  hasTurn(id: string, n: number) {
    return !!this.sessions.get(id)?.turn(n);
  }
}
