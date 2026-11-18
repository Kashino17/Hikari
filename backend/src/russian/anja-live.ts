/**
 * Anja live — ein gesprochenes Übungsgespräch.
 *
 * Der Lernende spricht (Sprechtaste), Anja antwortet sofort gesprochen. Dahinter stehen zwei Dienste
 * aus Googles kostenloser Stufe, jeder für das, was er gemessen gut kann (14.11.2026):
 *
 *  - Gemini Live (Sprache rein, Sprache raus) führt das Gespräch. Kein Tageslimit, erste Tonausgabe
 *    nach rund 0,8 s. Es hört Fehler so, wie sie gesagt wurden, und sagt die richtige Form vor.
 *  - Gemini Transcribe Live ist das Ohr: Es schreibt wortgetreu mit, was der Lernende gesagt hat —
 *    fest auf Russisch eingestellt. Ohne diese Einstellung rät die Mitschrift die Sprache und macht
 *    aus Russisch mit Akzent Türkisch oder Spanisch (so geschehen am 14.11.2026).
 *  - Gemini Flash-Lite liefert nach jedem Zug, was auf dem Bildschirm steht: die strenge Satzprüfung,
 *    die deutsche Übersetzung, die Bedeutung je Wort und zwei Beispielantworten.
 *
 * Die Mitschrift des Gesprächsmodells bleibt als zweites Ohr: Hat es andere russische Wörter gehört
 * als das Mitschreib-Modell, steht dort »nicht sicher verstanden« — ohne Urteil.
 *
 * Die sichtbare Korrektur kommt NIE aus dem, was Anja sagt (am Ton ist nicht zu erkennen, ob sie
 * korrigiert oder selbst antwortet), sondern nur aus der Satzprüfung — und die wird fest nachgeprüft
 * (plausibleCorrection): Richtiges darf nie wie falsch aussehen.
 *
 * Der Server hält die Verbindung zu Google; die Oberfläche braucht nur gewöhnliche Abrufe.
 *
 * Anja als Lehrerin (Bauplan 18.11.2026, Kadirs Wahl: »sofort nach jedem Satz korrigieren« und
 * »Erklärung erst prüfen, dann vorlesen«):
 *  - Anjas Antwort auf einen Satz wird zurückgehalten, bis die Satzprüfung entschieden hat.
 *    Stimmt der Satz, wird sie freigegeben. Ist ein Fehler bestätigt, wird sie verworfen: Anja liest
 *    stattdessen eine Erklärung vor, die fest aus geprüften Teilen zusammengesetzt ist (Lob, so heißt
 *    es, warum, ein zweites Beispiel) — vom Modell nur vorgelesen, nie frei formuliert.
 *  - Danach sagt der Lernende den Satz noch einmal (höchstens drei Versuche). Das hört nur das Ohr;
 *    verglichen wird fest mit dem verbesserten Satz. Dann geht das Gespräch auf Russisch weiter.
 *  - Frei gesprochene Erklärungen (Frage auf Deutsch, »Ich komme nicht weiter«) werden vor dem
 *    Abspielen von einem zweiten Modell geprüft.
 *  - Wie viel Deutsch: nach Niveau. Bis A2 erklärt Anja auf Deutsch, ab B1 zuerst kurz auf Russisch
 *    und erst, wenn der zweite Versuch nicht klappt, auf Deutsch.
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

/** Das Ohr: schreibt nur mit. Kein Tageslimit, Antwort rund eine halbe Sekunde nach dem Satzende. */
export const EAR_MODEL = "gemini-3.5-transcribe-live";
/** Die Sprache, in der gesprochen wird — als Vorgabe für beide Mitschriften. */
const SPOKEN_LANGUAGE = "ru-RU";
/**
 * Wie lange auf das Ohr gewartet wird. Es arbeitet den Ton erst nach dem Loslassen der Sprechtaste
 * ab — gemessen am 15.11.2026 rund 0,25 s je Sekunde Ton (3 s Ton → 0,9 s, 11 s → 3,0 s,
 * 20 s mit Denkpausen → 4,9 s), bei Last mehr. Bei Stille meldet es gar nichts; sobald es Sprache
 * bemerkt, schickt es nach 0,4–1,6 s einen Zwischenstand. Deshalb zwei Fristen:
 *  - bis zum ersten Lebenszeichen (sonst gilt: Stille),
 *  - danach großzügig bis zur fertigen Mitschrift (sonst gilt: das Ohr ist ausgefallen).
 * Vorher galten feste 3 s für alles — lange Sätze liefen hinein und wurden nicht bewertet.
 */
const EAR_SILENCE_MS = 3_000;
const EAR_SILENCE_PER_SECOND_MS = 300;
const EAR_SPEECH_MS = 5_000;
const EAR_SPEECH_PER_SECOND_MS = 600;
const EAR_SPEECH_MAX_MS = 20_000;
/** Google beendet eine Mitschreib-Sitzung nach 10 Minuten. */
const EAR_MAX_AGE_MS = 8 * 60_000;
/** Die Mitschrift des Gesprächsmodells kommt manchmal erst nach Anjas Antwort zu Ende an. */
const LATE_TRANSCRIPT_MS = 700;

const SETUP_TIMEOUT_MS = 8_000;
/** Ein Zug kann jetzt mehrere Schritte haben: hören, Antwort, Prüfung, zweite Meinung, Erklärung. */
const TURN_TIMEOUT_MS = 60_000;
/** So oft darf der Lernende einen verbesserten Satz noch einmal sagen, dann geht das Gespräch weiter. */
export const REPEAT_TRIES = 3;
const LOW_LEVELS = new Set(["A0", "A1", "A2"]);
/** Google beendet eine Sitzung nach 15 Minuten — vorher wird mit dem Verlauf neu verbunden. */
const SOCKET_MAX_AGE_MS = 12 * 60_000;
const IDLE_MS = 10 * 60_000;
const MAX_SESSIONS = 8;
/** Üblich sind 1,3 s. Wer nach 6 s nicht geantwortet hat, ist überlastet — dann lieber das nächste Modell. */
const HELP_TIMEOUT_MS = 6_000;
/** Höchstens so viele Anfragen je Minute an ein Hilfen-Modell (Google erlaubt 15). */
const HELP_PER_MINUTE = 12;
const QUOTA_ZONE = "America/Los_Angeles";
/** So lange gilt eine gemessene Antwortzeit; danach bekommt ein Modell wieder eine Chance. */
const SPEED_MEMORY_MS = 15 * 60_000;

export const liveStart = z.object({
  stage: z.number().int().min(0).max(4).default(0),
  scenario: z.string().trim().max(300).default(""),
  interest: z.string().trim().max(300).default(""),
  knownWords: z.array(z.string().trim().min(1).max(80)).max(150).default([]),
  /** Geschätztes Sprachniveau aus dem Lernstand — bestimmt, wie viel Anja auf Deutsch erklärt. */
  level: z.enum(["A0", "A1", "A2", "B1", "B2", "C1", "C2"]).default("A1"),
  /** Typische Fehler aus früheren Gesprächen (Fehler-Gedächtnis der App) — Anja baut Gelegenheiten dafür ein. */
  stones: z
    .array(
      z.object({
        topic: z.string().trim().min(1).max(80),
        wrongRu: z.string().trim().max(200).default(""),
        rightRu: z.string().trim().max(200).default(""),
      }),
    )
    .max(8)
    .default([]),
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
  /** »Ich komme nicht weiter« — Anja hilft, nach Niveau auf Deutsch. */
  z.object({ kind: z.literal("help") }),
]);
export type LiveInput = z.infer<typeof liveInput>;

const helpsReply = z.object({
  check: z
    .object({
      ok: z.boolean(),
      correctedRu: z.string().max(600).default(""),
      correctedDe: z.string().max(600).default(""),
      focus: z.string().max(240).default(""),
      explanationDe: z.string().max(500).default(""),
      /** Kurzer Name des Stolpersteins für das Fehler-Gedächtnis, z. B. »Wen-Fall nach »wollen««. */
      topicDe: z.string().max(80).default(""),
      /** Ein zweiter kurzer Satz mit derselben Regel. */
      exampleRu: z.string().max(200).default(""),
      exampleDe: z.string().max(200).default(""),
    })
    .default({ ok: true, correctedRu: "", correctedDe: "", focus: "", explanationDe: "", topicDe: "", exampleRu: "", exampleDe: "" }),
  goodDe: z.string().max(300).default(""),
  // Leer, wenn nur ein Satz geprüft wird (getipptes Gespräch: keine Antwort von Anja dabei).
  replyDe: z.string().trim().max(900).default(""),
  words: z
    .array(z.object({ ru: z.string().trim().max(80), de: z.string().trim().max(100) }))
    .max(40),
  examples: z
    .array(z.object({ ru: z.string().trim().max(220), de: z.string().trim().max(260) }))
    .max(4),
});

export interface LiveHelps {
  /** Was am Satz des Lernenden gelungen ist — ein Satz wie von einer Lehrerin; leer, wenn nichts Bestimmtes. */
  goodDe: string;
  replyDe: string;
  words: { ru: string; de: string }[];
  examples: { ru: string; de: string }[];
  /**
   * null = kein Urteil (nichts zu prüfen, nicht sicher verstanden, oder die zweite Meinung fehlt
   * bzw. widerspricht); ok = der Satz stimmt; sonst die eine, bestätigte Verbesserung.
   */
  check: null | { ok: true } | LiveFix;
}

/** Eine bestätigte Verbesserung samt allem, was die Lehrerin dazu sagt. */
export interface LiveFix {
  ok: false;
  correctedRu: string;
  correctedDe: string;
  focus: string;
  explanationDe: string;
  topicDe: string;
  exampleRu: string;
  exampleDe: string;
}

/** Was Anja nach einem Fehler vorliest — fest zusammengesetzt aus geprüften Teilen. */
export interface LiveTeach {
  /** Was der Lernende gesagt hat. */
  wrongRu: string;
  fix: LiveFix;
  /** Konkretes Lob (fest nachgeprüft) — leer, wenn nichts Bestimmtes. */
  goodDe: string;
  /** Der vorgelesene Text. */
  speech: string;
  /** de = Erklärung auf Deutsch, ru = kurz auf Russisch (ab B1, erster Hinweis). */
  lang: "de" | "ru";
}

/** Noch einmal sagen: Stand des verbesserten Satzes, den der Lernende wiederholen soll. */
export interface LiveExpect {
  correctedRu: string;
  correctedDe: string;
  topicDe: string;
  /** Bisherige Versuche. */
  tries: number;
  /** Wurde schon auf Deutsch erklärt? (ab B1 erst nach dem ersten Fehlversuch) */
  explained: boolean;
}

export interface LiveRepeat {
  ok: boolean;
  tries: number;
  /** Nach dem letzten Versuch: Das Gespräch geht weiter, der Satz bleibt im Satzbuch. */
  gaveUp: boolean;
}

export type HelpsState = "none" | "pending" | "ready" | "failed";

/**
 * Wie verlässlich die Mitschrift des Lernenden ist:
 *  sure    das Ohr hat Russisch gehört und das zweite Ohr widerspricht nicht — der Satz wird geprüft
 *  unsure  die beiden Ohren haben verschiedene Wörter gehört, oder das Ohr selbst fiel aus —
 *          der Satz wird gezeigt, aber nicht bewertet
 *  foreign kein Russisch verstanden (eine andere Sprache)
 *  none    nichts verstanden (Stille) oder kein gesprochener Zug
 */
export type HeardState = "sure" | "unsure" | "foreign" | "none";

export interface LiveTurnView {
  n: number;
  kind: "start" | LiveInput["kind"];
  done: boolean;
  error: string | null;
  /** Was die Spracherkennung vom Lernenden verstanden hat (bei getippter Antwort: der Text). */
  heard: string;
  heardState: HeardState;
  /** Die abweichende Fassung des zweiten Ohrs — oder, bei `foreign`, was stattdessen verstanden wurde. */
  heardAlt: string;
  /** Anjas Antwort als Mitschrift. */
  said: string;
  firstMs: number | null;
  helps: LiveHelps | null;
  helpsState: HelpsState;
  /** hearing → checking → speaking: was gerade passiert (für »Moment, ich schau mir das an«). */
  phase: "hearing" | "checking" | "speaking";
  /** Anja hat korrigiert und erklärt. */
  teach: LiveTeach | null;
  /** Dieser Zug war ein Wiederholungsversuch. */
  repeat: LiveRepeat | null;
  /** Nach diesem Zug soll der Lernende diesen Satz noch einmal sagen. */
  expecting: LiveExpect | null;
  /** Anja hat (auch) Deutsch gesprochen und die Erklärung wurde geprüft — false = nicht bestätigt. */
  verified: boolean | null;
}

interface Turn extends LiveTurnView {
  /** Zurückgehaltene Antwort: Ton und Mitschrift, bis die Prüfung entschieden hat. */
  holding: boolean;
  held: Buffer[];
  heldSaid: string;
  /** Wartet auf das Ende der laufenden Antwort des Gesprächsmodells. */
  generation: { resolve: () => void; reject: (e: Error) => void } | null;
  heardSettled: boolean;
  /** Mitschrift des Gesprächsmodells (zweites Ohr). */
  heardAgent: string;
  /** Mitschrift des Ohrs; null = das Ohr war nicht erreichbar. */
  ear: Promise<string | null> | null;
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
  /** Nur für Tests: Geduld mit dem Ohr (ohne / mit bemerkter Sprache) und mit verspäteten Mitschriften. */
  earTimeoutMs?: number;
  earSpeechTimeoutMs?: number;
  lateMs?: number;
}

const REGIE = {
  start: "(Regie: Beginne jetzt das Gespräch. Begrüße Kadir kurz und stelle deine erste Frage.)",
  resume:
    "(Regie: Die Verbindung war kurz weg. Mach genau dort weiter und stelle deine nächste Frage.)",
  again:
    "(Regie: Kadir hat dich nicht verstanden. Sag deinen letzten Satz noch einmal, langsamer und deutlich.)",
  easier:
    "(Regie: Das war zu schwer für Kadir. Stell deine letzte Frage noch einmal, mit einfacheren Wörtern und kürzer.)",
  helpLow:
    "(Regie: Kadir kommt nicht weiter. Erklär ihm auf Deutsch in höchstens zwei kurzen Sätzen, was deine letzte Frage bedeutet, und gib ihm einen einfachen Anfang für seine Antwort auf Russisch. Stell danach die Frage noch einmal auf Russisch.)",
  helpHigh:
    "(Regie: Kadir kommt nicht weiter. Sag deine letzte Frage noch einmal in ganz einfachem Russisch und gib ihm einen Anfang für seine Antwort. Nur wenn es gar nicht anders geht, ein Wort auf Deutsch.)",
  /** Die frei gesprochene Erklärung hat die Prüfung nicht bestanden — ein zweiter, vorsichtigerer Anlauf. */
  retryExplain:
    "(Regie: Deine letzte Antwort hat Kadir nicht gehört. Antworte ihm noch einmal, kürzer und ganz genau. Bist du bei einer Regel nicht sicher, erkläre keine Regel, sondern gib nur ein richtiges Beispiel.)",
};

/** Anja liest einen fest zusammengesetzten Text vor — Wort für Wort. */
export function regieSayExactly(text: string): string {
  return `(Regie: Deine letzte Antwort hat Kadir nicht gehört — sie zählt nicht. Sprich jetzt als Lehrerin GENAU den folgenden Text, Wort für Wort, ohne etwas wegzulassen oder hinzuzufügen. Deutsche Teile auf Deutsch, die russischen Teile in «» auf Russisch, langsam und deutlich: ${text})`;
}

/** Der verbesserte Satz kam jetzt richtig: loben und weiter im Gespräch. */
export function regieRepeatOk(said: string): string {
  return `(Regie: Kadir hat seinen verbesserten Satz jetzt richtig gesagt: «${said}». Lob ihn kurz auf Russisch (zwei, drei Wörter) und mach mit dem Gespräch weiter: reagiere auf das, was er gesagt hat, und stelle genau eine neue Frage.)`;
}

/** Dreimal nicht geschafft: kein Drama, weiter im Gespräch. */
export function regieGiveUp(correctedRu: string): string {
  return `(Regie: Kadir hat den Satz «${correctedRu}» noch nicht geschafft. Sag ihm freundlich auf Russisch, dass das nicht schlimm ist und ihr später darauf zurückkommt. Mach dann mit dem Gespräch weiter und stelle genau eine neue Frage.)`;
}

/** Eine Frage oder ein Satz auf Deutsch (nicht als Russisch verstanden), den Anja beantworten soll. */
export function regieGerman(text: string, low: boolean): string {
  return `(Regie: Kadir hat gerade auf Deutsch gesprochen: «${text}». ${
    low
      ? "Antworte ihm kurz auf Deutsch (höchstens drei kurze Sätze)."
      : "Antworte zuerst in einfachem Russisch; nur wenn das nicht reicht, kurz auf Deutsch."
  } Ist die Mitschrift unverständlich, bitte ihn auf Russisch, es noch einmal zu sagen. Kehre danach mit einer russischen Frage ins Gespräch zurück.)`;
}

/**
 * Der Text, den Anja nach einem bestätigten Fehler vorliest — nur aus geprüften Teilen.
 * Bis A2 auf Deutsch mit Erklärung; ab B1 beim ersten Mal nur kurz auf Russisch (die Erklärung
 * kommt, wenn der nächste Versuch nicht klappt).
 */
export function teachSpeech(fix: LiveFix, goodDe: string, lang: "de" | "ru"): string {
  if (lang === "ru") return `Почти! Правильно так: «${fix.correctedRu}». Скажи ещё раз.`;
  const why = fix.explanationDe.trim();
  return [
    goodDe ? `Fast! ${goodDe}` : "Fast!",
    `So heißt es richtig: «${fix.correctedRu}».`,
    why,
    fix.exampleRu && fix.exampleDe ? `Noch ein Beispiel: «${fix.exampleRu}», das heißt: ${fix.exampleDe}` : "",
    "Sag den Satz jetzt noch einmal.",
  ]
    .map((x) => x.trim())
    .filter(Boolean)
    .map((x) => (/[.!?»«]$/.test(x) ? x : `${x}.`))
    .join(" ");
}

/** Ein misslungener Wiederholungsversuch: kurz, und der Satz noch einmal zum Nachsprechen. */
export function retrySpeech(correctedRu: string, low: boolean, reason: "wrong" | "silence" | "unsure"): string {
  if (!low && reason === "wrong") return `Ещё раз, медленно: «${correctedRu}».`;
  const lead =
    reason === "silence"
      ? "Ich habe dich nicht gehört."
      : reason === "unsure"
        ? "Ich habe dich nicht sicher verstanden."
        : "Noch nicht ganz. Hör genau hin:";
  return `${lead} «${correctedRu}». Sag es noch einmal.`;
}

/** Steckt in einem Satz derselbe verbesserte Satz? Satzzeichen, Groß/klein und ё/е zählen nicht. */
export function sameSentence(heard: string, target: string): boolean {
  const a = wordsOf(heard);
  const b = wordsOf(target);
  return b.length > 0 && a.join(" ") === b.join(" ");
}

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
    "- Du sprichst Russisch. Deutsch nur, wenn eine Regie-Anweisung es verlangt oder Kadir dich auf Deutsch etwas fragt — und danach geht es sofort auf Russisch weiter.",
    `- Kadirs Niveau: ${setup.level}. ${
      LOW_LEVELS.has(setup.level)
        ? "Hilfen und Erklärungen bekommt er noch auf Deutsch."
        : "Erkläre zuerst in einfachem Russisch, Deutsch nur, wenn es nicht anders geht."
    }`,
    "- Sprich langsam und deutlich.",
    "- Kadir spricht Russisch mit Akzent und macht Fehler. Geh davon aus, dass er Russisch spricht, und hör wohlwollend zu.",
    `- ${LEVELS[setup.stage]}`,
    known ? `- Diese Wörter kennt Kadir schon gut, benutze sie bevorzugt: ${known}.` : "",
    "- Reagiere zuerst kurz und echt auf das, was Kadir gesagt hat, und stelle dann GENAU EINE Frage. Fragt Kadir dich etwas («А ты?»), beantworte es zuerst.",
    "- Stelle nie eine Frage, die du in diesem Gespräch schon gestellt hast. Wechsle das Thema, wenn eines erschöpft ist — du bist neugierig auf Kadirs Leben.",
    "- Korrigiere Kadir NIE von dir aus und wiederhole seinen Satz nicht: Ob er einen Fehler gemacht hat, wird gesondert geprüft — Korrekturen bekommst du als Regie-Anweisung.",
    "- Du bist Gesprächspartnerin und Lehrerin zugleich: herzlich, direkt und genau. Im Gespräch keine Vorträge; Erklärungen nur, wenn eine Regie-Anweisung es verlangt oder Kadir fragt.",
    setup.stones.length
      ? `- Darüber stolpert Kadir oft: ${setup.stones
          .map((s) => `${s.topic}${s.wrongRu && s.rightRu ? ` (falsch «${s.wrongRu}», richtig «${s.rightRu}»)` : ""}`)
          .join("; ")}. Stelle ab und zu eine Frage, bei deren Antwort er genau so eine Form braucht.`
      : "",
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
{"check":{"ok":true,"correctedRu":"","correctedDe":"","focus":"","explanationDe":"","topicDe":"","exampleRu":"","exampleDe":""},"goodDe":"","replyDe":"","words":[{"ru":"","de":""}],"examples":[{"ru":"","de":""},{"ru":"","de":""}]}

check — judge ONLY Kadir's answer. It is a speech transcript: ignore punctuation and capitalisation completely.
ok=true when the sentence is understandable and has no real grammar mistake. Short answers and single words are fine.
WHAT the learner says (likes, dislikes, choices) is never a mistake, and a different wording than you would choose is not a mistake.
Whether the answer fits the question is NOT your concern: an answer about a different topic is still ok=true when its Russian is correct. Never swap a word for another word to make the answer fit the question.
ok=false ONLY for a real language mistake such as a wrong word form or a missing verb. Then:
correctedRu = the learner's own sentence with ONLY that mistake fixed (same words otherwise), correctedDe = its German translation,
focus = one short German sentence naming the issue.
explanationDe = at most two short German sentences for a learner who does NOT know grammar terms: start from the meaning (what the word does in the sentence, e.g. "Was du willst, ..."), quote the Russian forms, and name the precise grammar term only at the very end in parentheses (for example "(Akkusativ)"). Every grammatical statement must be exactly right.
topicDe = a short German label of the grammar point for the learner's list of recurring mistakes, at most 6 words, plain words first (e.g. "Wen-Fall nach »wollen«", "Endung bei »du«", "Wo-Fall nach »в«").
exampleRu = ONE more short, simple Russian sentence (3 to 6 words) that uses exactly the same rule correctly AND shows the same visible change (for example another word ending in -а that becomes -у; never a word that stays unchanged, like «спорт» or «кофе»); exampleDe = its German translation.
When ok=true, or when answer is empty, all other check fields are empty strings.

goodDe — like a calm teacher: ONE short German sentence (at most 12 words, informal "du") naming one concrete word or phrase Kadir used correctly, quoted exactly as he said it, in plain words. Examples: Die Ich-Form «люблю» stimmt. / Die Verneinung «не люблю» ist richtig. / «в Берлине» hat die richtige Endung. In goodDe ONLY, avoid grammar terminology (no case names, no "Subjekt", "Adverb", "Substantiv") and never hedge with "oder". No exclamation marks, no "super", "perfekt" or "toll". Never mention the mistake here. Empty string when answer is empty, has fewer than three words, or there is nothing specific to name.

replyDe — a natural German translation of reply (informal "du").
words — every word of reply in order (skip punctuation, list a repeated word once): ru = the word exactly as it appears in reply (lower case), de = its German meaning in THIS sentence, one to three words, German nouns capitalised as usual.
examples — two DIFFERENT short answers (2 to 6 words) Kadir could give to the question in reply. Simple beginner Russian, grammatically perfect, natural, written as normal sentences (capital letter, full stop); de = German translation. Do not answer with a bare "да" or "нет".`;

/**
 * Zweite Meinung zu einer Verbesserung — eine eigene Frage an das Modell, bevor irgendetwas als
 * Fehler gezeigt wird. Gemessen am 14.11.2026:
 *  - Die Satzprüfung hielt »Я хочу воды« für falsch (richtig wäre nur »воду«) — dabei ist der
 *    Teilungsgenitiv gutes Russisch. Mit dieser Frage: 12 von 12 Sätzen richtig beurteilt.
 *  - Erklärungen enthielten gelegentlich falsche Fachwörter (»Reflexivpronomen меня im Genitiv«).
 *    Als Lehrer befragt, erkannte das Modell 5 von 5 solcher Erklärungen als falsch.
 */
export const CONFIRM_PROMPT = `You are a strict but fair native-speaker reviewer of Russian and a careful grammar teacher.
A beginner said a sentence (speech transcript: ignore punctuation and capitalisation). Another checker claims it contains a grammar mistake, proposes a correction and explains it in German.
1. originalAcceptable — decide whether the ORIGINAL sentence is acceptable Russian that an educated native speaker could say — even if the proposed correction is also fine or more common.
Remember that Russian allows, among other things: the partitive genitive after verbs of wanting, giving, eating and drinking («хочу воды», «выпей чаю»), free word order, omitted pronouns, and short elliptical answers.
2. explanationCorrect — check the German explanation as a grammar teacher would: is every grammatical statement in it factually right (the case named, the word class, the gender, the rule)? A single wrong term (for example calling an accusative a genitive, a personal pronoun a reflexive pronoun, a noun a verb) makes it false. Wording and style do not matter.
3. exampleCorrect — if an example is given: is it a grammatically correct, natural Russian sentence that uses the same rule and shows the same VISIBLE change as the correction (a word whose form does not change, like «спорт» in the accusative, does not count), and is its German translation right? false when no example is given.
Return ONLY JSON: {"originalAcceptable": true|false, "explanationCorrect": true|false, "exampleCorrect": true|false, "why": "one short English sentence"}`;

/**
 * Prüft, was Anja frei auf Deutsch erklärt hat (Antwort auf eine Frage, »Ich komme nicht weiter«),
 * bevor es abgespielt wird. Nur Fakten über das Russische zählen, nicht der Stil.
 */
export const VERIFY_PROMPT = `You review what a Russian teacher (Anja) is about to say aloud to a German-speaking beginner. Her answer mixes German and Russian.
Check every factual claim about the Russian language in it: translations of Russian words or sentences, grammar statements (cases, verb forms, gender, word classes, rules), and whether every quoted Russian phrase is grammatically correct. Ignore style, friendliness, questions she asks and the conversation itself.
Return ONLY JSON: {"correct": true|false, "why": "one short English sentence"}`;

const secondOpinion = z.object({
  originalAcceptable: z.boolean(),
  // Fehlt das Urteil, gilt die Erklärung als nicht bestätigt.
  explanationCorrect: z.boolean().default(false),
  exampleCorrect: z.boolean().default(false),
});
const verifyReply = z.object({ correct: z.boolean() });

/** Steht statt eines Hinweises, dessen Erklärung die zweite Meinung nicht bestanden hat. */
export const PLAIN_FOCUS = "Hier stimmt eine Form nicht.";

/** Das erste vollständige JSON-Objekt im Text — Modelle hängen gelegentlich etwas an. */
export function firstJson(text: string): unknown {
  const start = text.indexOf("{");
  if (start < 0) throw new Error("no_json");
  let depth = 0;
  let inString = false;
  for (let i = start; i < text.length; i++) {
    const c = text[i];
    if (inString) {
      if (c === "\\") i++;
      else if (c === '"') inString = false;
    } else if (c === '"') inString = true;
    else if (c === "{") depth++;
    else if (c === "}" && --depth === 0) return JSON.parse(text.slice(start, i + 1));
  }
  throw new Error("no_json");
}

export function quotaDay(nowMs: number): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: QUOTA_ZONE }).format(new Date(nowMs));
}

const cyrillic = (s: string) => /[а-яё]/i.test(s);
const tidy = (s: string) => s.replace(/\s+/g, " ").trim();

/** Russisch ist ein Text, dessen Buchstaben überwiegend kyrillisch sind. */
export function isRussian(text: string): boolean {
  const letters = text.match(/\p{L}/gu) ?? [];
  if (letters.length === 0) return false;
  return letters.filter((c) => /[а-яё]/i.test(c)).length / letters.length >= 0.6;
}

/** Steckt `part` als zusammenhängendes Stück in `whole` — und ist es mindestens die Hälfte davon? */
function mostOf(part: string[], whole: string[]): boolean {
  if (part.length === 0 || part.length * 2 < whole.length) return false;
  for (let at = 0; at + part.length <= whole.length; at++)
    if (part.every((w, i) => w === whole[at + i])) return true;
  return false;
}

/**
 * Legt fest, was der Lernende gesagt hat — aus zwei unabhängigen Mitschriften.
 * `ear` ist die des Mitschreib-Modells (null = nicht erreichbar), `agent` die des Gesprächsmodells.
 *
 * Maßgeblich ist das Ohr: ein eigenes Modell nur fürs Mitschreiben. Die Mitschrift des
 * Gesprächsmodells ist die schwächere Quelle — sie kommt gelegentlich abgeschnitten an, rät bei
 * Akzent trotz Vorgabe manchmal eine fremde Sprache (»Yo llevo Alemania« für »Я живу в Германии«)
 * und erfindet Wörter dazu (15.11.2026 bei Kadirs Stimme: »люблю любой чай«, »не люблю любовь
 * люблю кофе«, während das Ohr jedes Mal richtig mitschrieb).
 *  - Hat sie andere russische Wörter gehört, gilt trotzdem das Ohr; ihre Fassung wird als `alt`
 *    mitgegeben. Bis v0.89.117 verhinderte jede Abweichung das Urteil — dann blieb jeder zweite
 *    richtige Satz unbewertet. Gegen ein Verhören des Ohrs schützt jetzt `otherEarHeardFix`
 *    gezielt an der Stelle, um die es geht: der Form, die korrigiert werden soll.
 *  - Hat nur sie Russisch gehört (das Ohr nicht), reicht das nicht für ein Urteil.
 */
export function settleHeard(
  ear: string | null,
  agent: string,
): { text: string; state: HeardState; alt: string } {
  // Zahlen oder Zeichen ohne einen einzigen Buchstaben sind Rauschen, keine Sprache.
  const spoken = (text: string) => (/\p{L}/u.test(text) ? tidy(text) : "");
  const e = spoken(ear ?? "");
  const a = spoken(agent);
  const eRu = isRussian(e);
  const aRu = isRussian(a);
  if (!eRu && !aRu)
    return e || a
      ? { text: "", state: "foreign", alt: e || a }
      : { text: "", state: "none", alt: "" };
  if (!eRu) return { text: a, state: "unsure", alt: "" };
  if (!aRu) return { text: e, state: "sure", alt: "" };
  const we = wordsOf(e);
  const wa = wordsOf(a);
  if (we.join(" ") === wa.join(" ") || mostOf(wa, we)) return { text: e, state: "sure", alt: "" };
  if (mostOf(we, wa)) return { text: a, state: "sure", alt: "" };
  // Eine sehr kurze zweite Mitschrift ohne Widerspruch ist nur ein abgeschnittener Anfang.
  if (wa.length * 2 < we.length && wa.every((w) => we.includes(w)))
    return { text: e, state: "sure", alt: "" };
  return { text: e, state: "sure", alt: a };
}

/**
 * Hat das zweite Ohr genau die Form gehört, die die Verbesserung verlangt? Dann hat sich vielleicht
 * das Ohr verhört (»вода« statt gesagtem »воду«), und ein richtiger Satz stünde als falsch da.
 * Verglichen wird nur, was die Verbesserung neu einführt — erfundene Zusatzwörter des zweiten
 * Ohrs spielen keine Rolle.
 */
export function otherEarHeardFix(heard: string, correctedRu: string, alt: string): boolean {
  if (!alt) return false;
  const said = new Set(wordsOf(heard));
  const other = new Set(wordsOf(alt));
  return wordsOf(correctedRu).some((w) => !said.has(w) && other.has(w));
}
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

const GRAMMAR_TERMS =
  /Nominativ|Akkusativ|Genitiv|Dativ|Präpositiv|Lokativ|Instrumental|Subjekt|Objekt|Adjektiv|Adverb|Substantiv|Nomen|Infinitiv|Partizip|Kasus|\bFall\b/i;
/** Endungen, an denen ein Anfänger-Verb zu erkennen ist (Gegenwart, Grundform, Vergangenheit). */
const VERB_LIKE =
  /(ю|у|ешь|ет|ем|ете|ют|ут|ишь|ит|им|ите|ат|ят|ть|ти|чь|л|ла|ло|ли|юсь|усь|ется|ится)$/;

/**
 * Lob gilt nur, wenn es etwas nennt, das der Lernende wirklich gesagt hat: Jedes russische Wort im
 * Lob muss wörtlich in seiner Antwort stehen — und, falls es eine Verbesserung gibt, auch dort
 * (sonst würde gerade die falsche Form gelobt). Spricht das Lob von einem Verb, muss eines genannt sein.
 */
export function soundPraise(goodDe: string, answer: string, correctedRu = ""): string {
  const text = goodDe.trim();
  const quoted = wordsOf(text);
  if (!text || !answer.trim() || quoted.length === 0) return "";
  const said = new Set(wordsOf(answer));
  const fixed = correctedRu ? new Set(wordsOf(correctedRu)) : null;
  if (!quoted.every((w) => said.has(w) && (!fixed || fixed.has(w)))) return "";
  // Gemessen 18.11.2026: »Die Form «Кадир» ist richtig.« — einen Namen zu loben ist leeres Lob.
  // Namen sind Wörter, die mitten im Satz groß geschrieben stehen.
  const names = new Set(
    (answer.match(/(?<=\S\s+)\p{Lu}\p{Ll}+/gu) ?? []).map((w) => w.toLowerCase().replaceAll("ё", "е")),
  );
  if (quoted.every((w) => names.has(w))) return "";
  // Gemessen: Das Modell nannte »дом« ein Verb und »я дома« einen Nominativ. Ein Lob mit falschem Fachwort
  // lehrt Falsches — Fachbegriffe gehören in die Erklärung eines Fehlers (dort gemessen zuverlässig), nicht ins Lob.
  if (GRAMMAR_TERMS.test(text) || /\boder\b/i.test(text)) return "";
  // Eine Verneinung loben, wo keine ist — das wäre erfunden.
  if (
    /Verneinung/i.test(text) &&
    !wordsOf(answer).some((w) => w === "не" || w === "нет" || w === "ни")
  )
    return "";
  if (/\bVerb|\bIch-Form|\bkonjugiert/i.test(text) && !quoted.some((w) => VERB_LIKE.test(w)))
    return "";
  return text;
}

/** Macht aus der Modell-Antwort das, was die Oberfläche zeigen darf — fest nachgeprüft. */
export function settleHelps(raw: unknown, answer: string, question = ""): LiveHelps {
  const r = helpsReply.parse(raw);
  const c = r.check;
  // Was kein Russisch ist, wird nicht beurteilt — auch nicht mit »Genau so«.
  const judged = isRussian(answer);
  const fixable =
    judged &&
    !c.ok &&
    !!c.focus.trim() &&
    plausibleCorrection(answer, c.correctedRu) &&
    onlyFormChanged(question, answer, c.correctedRu);
  return {
    goodDe: judged ? soundPraise(r.goodDe, answer, fixable ? c.correctedRu : "") : "",
    replyDe: r.replyDe,
    words: r.words.filter((w) => cyrillic(w.ru) && w.de),
    examples: r.examples.filter((e) => cyrillic(e.ru) && e.de).slice(0, 3),
    check: !judged
      ? null
      : fixable
        ? {
            ok: false,
            correctedRu: c.correctedRu,
            correctedDe: c.correctedDe,
            focus: c.focus,
            explanationDe: c.explanationDe,
            topicDe: c.topicDe.trim() || c.focus.trim().slice(0, 80),
            // Ein Beispiel nur, wenn es Russisch ist und eine Übersetzung hat; geprüft wird es mit der zweiten Meinung.
            exampleRu: cyrillic(c.exampleRu) && c.exampleDe.trim() ? c.exampleRu.trim() : "",
            exampleDe: cyrillic(c.exampleRu) && c.exampleDe.trim() ? c.exampleDe.trim() : "",
          }
        : { ok: true },
  };
}

/**
 * Das Ohr eines Gesprächs: eine eigene Verbindung zum Mitschreib-Modell, fest auf Russisch gestellt.
 * Es bekommt denselben Ton wie Anja und liefert, was wortwörtlich gesagt wurde — samt Fehlern.
 */
class Ear {
  private socket: LiveSocket | null = null;
  private openedAt = 0;
  private opening: Promise<LiveSocket | null> | null = null;
  private pending: {
    text: string;
    finish: (text: string | null) => void;
    /** Das Ohr hat Sprache bemerkt (Zwischenstand) — die fertige Mitschrift ist unterwegs. */
    heardSpeech: () => void;
  } | null = null;

  constructor(private host: AnjaLive) {}

  /** Verbindung schon öffnen, bevor der erste Satz kommt. */
  warm() {
    void this.ensure();
  }

  close() {
    const s = this.socket;
    this.socket = null;
    try {
      s?.close();
    } catch {
      /* schon zu */
    }
    this.pending?.finish(null);
  }

  /** Verbindung verwerfen, ohne einen wartenden Satz zu beenden. */
  private drop() {
    const s = this.socket;
    this.socket = null;
    try {
      s?.close();
    } catch {
      /* schon zu */
    }
  }

  private ensure(): Promise<LiveSocket | null> {
    if (this.opening) return this.opening;
    if (this.socket && this.host.now() - this.openedAt < EAR_MAX_AGE_MS)
      return Promise.resolve(this.socket);
    this.close();
    this.opening = this.open()
      .then((socket) => {
        this.socket = socket;
        this.openedAt = this.host.now();
        return socket as LiveSocket | null;
      })
      .catch((e) => {
        this.host.log(`[anja-live] Ohr nicht bereit: ${String(e).slice(0, 120)}`);
        return null;
      })
      .finally(() => {
        this.opening = null;
      });
    return this.opening;
  }

  private open(): Promise<LiveSocket> {
    return new Promise((resolve, reject) => {
      const key = this.host.deps.key();
      if (!key) {
        reject(new Error("no_key"));
        return;
      }
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
              model: `models/${EAR_MODEL}`,
              generationConfig: { responseModalities: ["TEXT"] },
              inputAudioTranscription: { languageCodes: [SPOKEN_LANGUAGE] },
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
        const sc = msg.serverContent as
          | {
              inputTranscription?: { text?: string };
              interimInputTranscription?: unknown;
              generationComplete?: boolean;
              turnComplete?: boolean;
            }
          | undefined;
        const p = this.pending;
        // Eine verworfene Verbindung meldet nichts mehr — ihr Nachzügler gehörte zu einem früheren Satz.
        if (!sc || !p || this.socket !== socket) return;
        if (sc.interimInputTranscription || sc.inputTranscription?.text) p.heardSpeech();
        if (sc.inputTranscription?.text) p.text += sc.inputTranscription.text;
        if (sc.generationComplete || sc.turnComplete) p.finish(tidy(p.text));
      };
      socket.onerror = () => finish(new Error("socket_error"));
      socket.onclose = (ev) => {
        finish(new Error(`closed_${ev?.code ?? ""}`));
        if (this.socket === socket) {
          this.socket = null;
          this.pending?.finish(null);
        }
      };
    });
  }

  /** Schreibt einen gesprochenen Satz mit. "" = nichts gehört (Stille), null = Ohr nicht erreichbar. */
  async listen(pcm: Buffer, rate: number): Promise<string | null> {
    const socket = await this.ensure();
    if (!socket) return null;
    this.pending?.finish(null);
    const seconds = pcm.length / 2 / rate;
    const deps = this.host.deps;
    const silenceMs = deps.earTimeoutMs ?? EAR_SILENCE_MS + seconds * EAR_SILENCE_PER_SECOND_MS;
    const speechMs =
      deps.earSpeechTimeoutMs ??
      Math.min(EAR_SPEECH_MAX_MS, EAR_SPEECH_MS + seconds * EAR_SPEECH_PER_SECOND_MS);
    const startedAt = Date.now();
    return new Promise((resolve) => {
      let speech = false;
      const p = {
        text: "",
        finish: (text: string | null) => {
          if (this.pending === p) this.pending = null;
          clearTimeout(timer);
          resolve(text);
        },
        heardSpeech: () => {
          if (speech) return;
          speech = true;
          clearTimeout(timer);
          timer = setTimeout(giveUp, Math.max(0, speechMs - (Date.now() - startedAt)));
        },
      };
      const giveUp = () => {
        // Eine Antwort, die jetzt noch kommt, gehörte zu diesem Satz — nie zum nächsten. Also eine
        // frische Verbindung für den nächsten Satz.
        this.drop();
        if (speech) {
          this.host.log(
            `[anja-live] Ohr: Sprache bemerkt, aber nach ${Math.round(speechMs)} ms keine fertige Mitschrift (Ton ${seconds.toFixed(1)} s)`,
          );
          // Sprache bemerkt, aber nicht fertig mitgeschrieben: Das Ohr ist ausgefallen, es war keine Stille.
          p.finish(p.text ? tidy(p.text) : null);
        } else p.finish(tidy(p.text));
      };
      // Bei Stille kommt keine Meldung: Dann gilt, was bis dahin da ist.
      let timer = setTimeout(giveUp, silenceMs);
      this.pending = p;
      try {
        const say = (payload: unknown) => socket.send(JSON.stringify({ realtimeInput: payload }));
        say({ activityStart: {} });
        for (let i = 0; i < pcm.length; i += 16_000)
          say({
            audio: {
              data: pcm.subarray(i, i + 16_000).toString("base64"),
              mimeType: `audio/pcm;rate=${rate}`,
            },
          });
        say({ activityEnd: {} });
      } catch {
        p.finish(null);
      }
    });
  }
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
  private ear: Ear;
  /** Der gesprochene Zug, zu dem eintreffende Mitschrift-Stücke des Gesprächsmodells gehören. */
  private listening: Turn | null = null;
  /** Der verbesserte Satz, den der Lernende gerade noch einmal sagen soll. */
  private expect: LiveExpect | null = null;
  private expectFix: LiveFix | null = null;

  constructor(
    private host: AnjaLive,
    readonly setup: LiveStart,
  ) {
    this.lines = [...setup.lines];
    this.lastUsed = host.now();
    this.ear = new Ear(host);
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

  /** Gespräch zu Ende: beide Verbindungen schließen. */
  end() {
    this.close();
    this.ear.close();
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
              // Ohne Sprachvorgabe rät die Mitschrift — und macht aus Russisch mit Akzent eine andere Sprache.
              inputAudioTranscription: { languageCodes: [SPOKEN_LANGUAGE] },
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

  private get low() {
    return LOW_LEVELS.has(this.setup.level);
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
    // Mitschrift-Stücke können nach Anjas Antwort noch eintreffen — sie gehören zum letzten gesprochenen Zug.
    if (sc?.inputTranscription?.text && this.listening) {
      this.listening.heardAgent += sc.inputTranscription.text;
      wake(this.listening);
    }
    if (!turn || turn.done || turn.error || !sc) return;
    for (const part of sc.modelTurn?.parts ?? []) {
      const data = part.inlineData?.data;
      if (!data) continue;
      const buf = Buffer.from(data, "base64");
      // Zurückgehalten, bis die Prüfung entschieden hat — sonst sofort hörbar.
      if (turn.holding) turn.held.push(buf);
      else {
        turn.firstMs ??= this.host.now() - turn.startedAt;
        turn.audio.push(buf);
      }
    }
    const text = sc.outputTranscription?.text;
    if (text) {
      if (turn.holding) turn.heldSaid += text;
      else turn.said += text;
    }
    if (sc.turnComplete) {
      const g = turn.generation;
      turn.generation = null;
      g?.resolve();
    }
    wake(turn);
  }

  /**
   * Schickt etwas an das Gesprächsmodell und wartet, bis seine Antwort fertig ist.
   * `hold`: die Antwort zurückhalten (release/discard entscheiden später).
   */
  private generate(turn: Turn, payload: unknown | unknown[], hold: boolean): Promise<void> {
    const socket = this.socket;
    if (!socket) return Promise.reject(new Error("connection_lost"));
    turn.holding = hold;
    turn.held = [];
    turn.heldSaid = "";
    return new Promise<void>((resolve, reject) => {
      turn.generation = { resolve, reject };
      for (const p of Array.isArray(payload) ? payload : [payload])
        socket.send(JSON.stringify({ realtimeInput: p }));
    });
  }

  /** Die zurückgehaltene Antwort freigeben: Sie wird jetzt hörbar. */
  private release(turn: Turn) {
    if (turn.held.length) turn.firstMs ??= this.host.now() - turn.startedAt;
    turn.audio.push(...turn.held);
    turn.said = tidy(`${turn.said} ${turn.heldSaid}`);
    turn.held = [];
    turn.heldSaid = "";
    turn.holding = false;
    turn.phase = "speaking";
    wake(turn);
  }

  /** Die zurückgehaltene Antwort verwerfen — der Lernende hört sie nie. */
  private discard(turn: Turn) {
    turn.held = [];
    turn.heldSaid = "";
    turn.holding = false;
  }

  /** Anja liest einen fest zusammengesetzten Text vor; der Ton läuft sofort. */
  private async sayExactly(turn: Turn, text: string) {
    turn.phase = "speaking";
    wake(turn);
    await this.generate(turn, { text: regieSayExactly(text) }, false);
    turn.said = tidy(turn.said);
    // Auf dem Bildschirm steht der feste Text — eine Abweichung beim Vorlesen nur fürs Protokoll.
    if (!mostlySame(turn.said, text)) this.host.log("[anja-live] Vorgelesener Text weicht vom festen Text ab");
  }

  /**
   * Eine zurückgehaltene, frei formulierte Antwort: Ist sie reines Russisch (Gespräch), wird sie
   * freigegeben. Enthält sie Deutsch, ist es eine Erklärung — erst prüfen, dann abspielen.
   */
  private async checkHeld(turn: Turn) {
    const said = tidy(turn.heldSaid);
    if (!said || isRussian(said)) {
      this.release(turn);
      return;
    }
    turn.phase = "checking";
    wake(turn);
    let ok = await this.host.verify(said);
    if (ok === false) {
      this.host.log("[anja-live] Erklärung nicht bestätigt — zweiter Anlauf");
      await this.generate(turn, { text: REGIE.retryExplain }, true);
      const again = tidy(turn.heldSaid);
      ok = !again || isRussian(again) ? true : await this.host.verify(again);
    }
    turn.verified = ok === true;
    if (ok === true) {
      this.release(turn);
      return;
    }
    // Nicht bestätigt (oder die Prüfung war nicht erreichbar): nichts Ungeprüftes vorlesen.
    this.host.log(`[anja-live] Erklärung ${ok === false ? "verworfen" : "ungeprüft zurückgehalten"}`);
    this.discard(turn);
    await this.sayExactly(
      turn,
      this.low
        ? "Das kann ich dir gerade nicht sicher erklären. Schau es dir gleich in Ruhe in der Grammatik an. Lass uns weitersprechen."
        : "Давай вернёмся к разговору.",
    );
  }

  /** Bestätigter Fehler: Anjas Antwort verwerfen, die feste Erklärung vorlesen, Wiederholung erwarten. */
  private async teach(turn: Turn, fix: LiveFix, goodDe: string, lang: "de" | "ru") {
    this.discard(turn);
    const speech = teachSpeech(fix, goodDe, lang);
    turn.teach = { wrongRu: turn.heard, fix, goodDe, speech, lang };
    this.expectFix = fix;
    this.expect = {
      correctedRu: fix.correctedRu,
      correctedDe: fix.correctedDe,
      topicDe: fix.topicDe,
      tries: this.expect?.tries ?? 0,
      explained: lang === "de",
    };
    await this.sayExactly(turn, speech);
  }

  /** Was der Lernende gesagt hat — aus dem Ohr und der Mitschrift des Gesprächsmodells. */
  private async settleSpoken(turn: Turn) {
    const ear = turn.ear ? await turn.ear : null;
    // Ist die zweite Mitschrift noch kürzer als die des Ohrs, kommt ihr Rest meist gleich nach.
    if (wordsOf(turn.heardAgent).length < wordsOf(ear ?? "").length)
      await new Promise((r) => setTimeout(r, this.host.deps.lateMs ?? LATE_TRANSCRIPT_MS));
    const heard = settleHeard(ear, turn.heardAgent);
    turn.heard = heard.text;
    turn.heardState = heard.state;
    turn.heardAlt = heard.alt;
    if (heard.state !== "sure")
      this.host.log(`[anja-live] Mitschrift ${heard.state}${ear === null ? " (Ohr fehlt)" : ""}`);
    // Nur zur Fehlersuche (ANJA_LIVE_DEBUG=1): was die beiden Ohren gehört haben. Sonst nie im Protokoll.
    if (process.env.ANJA_LIVE_DEBUG)
      this.host.log(
        `[anja-live] Ohr «${ear ?? "—"}» · Gespräch «${tidy(turn.heardAgent)}» · Anja «${tidy(turn.heldSaid)}»`,
      );
  }

  private heardDone(turn: Turn) {
    turn.heardSettled = true;
    turn.phase = "checking";
    wake(turn);
  }

  /** Eine Antwort des Lernenden (gesprochen oder getippt). */
  private async runAnswer(turn: Turn, input: Extract<LiveInput, { kind: "audio" | "text" }>) {
    if (input.kind === "audio") {
      const pcm = Buffer.from(input.audio, "base64");
      this.listening = turn;
      // Derselbe Ton geht zugleich ans Ohr; ein Fehler dort hält das Gespräch nie auf.
      turn.ear = this.ear.listen(pcm, input.rate).catch(() => null);
      const chunks: unknown[] = [{ activityStart: {} }];
      for (let i = 0; i < pcm.length; i += 16_000)
        chunks.push({
          audio: {
            data: pcm.subarray(i, i + 16_000).toString("base64"),
            mimeType: `audio/pcm;rate=${input.rate}`,
          },
        });
      chunks.push({ activityEnd: {} });
      // Anja antwortet — zurückgehalten, bis entschieden ist, ob korrigiert wird.
      await this.generate(turn, chunks, true);
      await this.settleSpoken(turn);
    } else {
      await this.generate(turn, { text: input.text }, true);
    }
    this.heardDone(turn);
    if (turn.heardState === "sure") {
      const question = [...this.lines].reverse().find((l) => l.who === "anja")?.ru ?? "";
      const helps = await this.host.helps(question, turn.heard, tidy(turn.heldSaid)).catch((e) => {
        this.host.log(`[anja-live] Hilfen fehlgeschlagen: ${String(e).slice(0, 160)}`);
        return null;
      });
      const fix = helps?.check && !helps.check.ok ? helps.check : null;
      if (helps && fix && otherEarHeardFix(turn.heard, fix.correctedRu, turn.heardAlt)) {
        this.host.log("[anja-live] Verbesserung zurückgehalten — das zweite Ohr hörte die richtige Form");
        helps.check = null;
        helps.goodDe = "";
      } else if (helps && fix) {
        // Die verworfene Antwort braucht keine Übersetzung; Urteil und Lob bleiben.
        turn.helps = { ...helps, replyDe: "", words: [], examples: [] };
        turn.helpsState = "ready";
        await this.teach(turn, fix, helps.goodDe, this.low ? "de" : "ru");
        return;
      }
      turn.helps = helps;
      turn.helpsState = helps ? "ready" : "failed";
      this.release(turn);
      return;
    }
    // Auf Deutsch gesprochen oder getippt: Anja hat geantwortet — erklärt sie dabei etwas, wird geprüft.
    if (turn.heardState === "foreign") {
      await this.checkHeld(turn);
      return;
    }
    // Nichts oder nicht sicher verstanden: wie bisher, ohne Urteil.
    this.release(turn);
  }

  /** Der Lernende sagt den verbesserten Satz noch einmal. Nur das Ohr hört zu; verglichen wird fest. */
  private async runRepeat(turn: Turn, input: Extract<LiveInput, { kind: "audio" }>, exp: LiveExpect) {
    const pcm = Buffer.from(input.audio, "base64");
    turn.ear = this.ear.listen(pcm, input.rate).catch(() => null);
    const heard = settleHeard(await turn.ear, "");
    turn.heard = heard.text;
    turn.heardState = heard.state;
    turn.heardAlt = heard.alt;
    this.heardDone(turn);
    if (heard.state === "sure" && sameSentence(heard.text, exp.correctedRu)) {
      this.expect = null;
      this.expectFix = null;
      turn.repeat = { ok: true, tries: exp.tries + 1, gaveUp: false };
      turn.phase = "speaking";
      await this.generate(turn, { text: regieRepeatOk(heard.text) }, false);
      return;
    }
    if (heard.state === "foreign") {
      // Eine Frage auf Deutsch mitten im Wiederholen — beantworten (geprüft), der Satz bleibt offen.
      await this.generate(turn, { text: regieGerman(heard.alt, this.low) }, true);
      await this.checkHeld(turn);
      return;
    }
    if (heard.state !== "sure") {
      await this.sayExactly(turn, retrySpeech(exp.correctedRu, this.low, heard.state === "none" ? "silence" : "unsure"));
      return;
    }
    const tries = exp.tries + 1;
    if (tries >= REPEAT_TRIES) {
      this.expect = null;
      this.expectFix = null;
      turn.repeat = { ok: false, tries, gaveUp: true };
      turn.phase = "speaking";
      await this.generate(turn, { text: regieGiveUp(exp.correctedRu) }, false);
      return;
    }
    this.expect = { ...exp, tries };
    turn.repeat = { ok: false, tries, gaveUp: false };
    // Ab B1 kam zuerst nur der kurze russische Hinweis — klappt es nicht, folgt jetzt die Erklärung auf Deutsch.
    if (!exp.explained && this.expectFix) {
      await this.teach(turn, this.expectFix, "", "de");
      return;
    }
    await this.sayExactly(turn, retrySpeech(exp.correctedRu, this.low, "wrong"));
  }

  private async run(turn: Turn, input: LiveInput | { kind: "start" }, resumed: boolean) {
    try {
      const exp = this.expect;
      if (input.kind === "audio" && exp) await this.runRepeat(turn, input, exp);
      else if (input.kind === "audio" || input.kind === "text") await this.runAnswer(turn, input);
      else if (input.kind === "help") {
        await this.generate(turn, { text: this.low ? REGIE.helpLow : REGIE.helpHigh }, true);
        await this.checkHeld(turn);
      } else {
        const regie = input.kind === "start" ? (resumed ? REGIE.resume : REGIE.start) : REGIE[input.kind];
        await this.generate(turn, { text: regie }, false);
      }
      this.finish(turn);
    } catch (e) {
      if (!turn.error) this.fail(turn, e instanceof Error ? e.message : "failed");
    }
  }

  /** Der Zug ist fertig: Verlauf fortschreiben, Übersetzung zu Anjas Antwort nachtragen. */
  private finish(turn: Turn) {
    if (turn.error) return;
    if (turn.timer) clearTimeout(turn.timer);
    if (turn.holding) this.release(turn);
    turn.said = tidy(turn.said);
    turn.heardSettled = true;
    turn.done = true;
    turn.phase = "speaking";
    turn.expecting = this.expect ? { ...this.expect } : null;
    const question = [...this.lines].reverse().find((l) => l.who === "anja")?.ru ?? "";
    const answered = turn.kind === "audio" || turn.kind === "text";
    if (answered && turn.heard && turn.heardState !== "foreign") this.lines.push({ who: "kadir", ru: turn.heard });
    if (turn.teach) this.lines.push({ who: "anja", ru: `«${turn.teach.fix.correctedRu}» — скажи ещё раз.` });
    else if (turn.said) this.lines.push({ who: "anja", ru: turn.said });
    wake(turn);
    // Übersetzung und Wortbedeutungen zu Anjas freier Antwort, falls noch nicht da (nicht zu einer Erklärung).
    if (turn.helpsState !== "none" || !turn.said || turn.teach || !isRussian(turn.said)) return;
    turn.helpsState = "pending";
    this.host
      .helps(question, "", turn.said)
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
    const g = turn.generation;
    turn.generation = null;
    g?.reject(new Error(error));
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
    if (!this.socket) throw new Error("live_unavailable");
    const turn: Turn = {
      n: this.turns.length + 1,
      kind: input.kind,
      done: false,
      error: null,
      heard: input.kind === "text" ? input.text : "",
      heardState: input.kind === "text" ? (isRussian(input.text) ? "sure" : "foreign") : "none",
      heardAlt: input.kind === "text" && !isRussian(input.text) ? input.text : "",
      heardAgent: "",
      ear: null,
      said: "",
      firstMs: null,
      helps: null,
      helpsState: "none",
      phase: input.kind === "audio" ? "hearing" : input.kind === "text" ? "checking" : "speaking",
      teach: null,
      repeat: null,
      expecting: null,
      verified: null,
      holding: false,
      held: [],
      heldSaid: "",
      generation: null,
      heardSettled: input.kind !== "audio",
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
    if (input.kind === "start") this.ear.warm();
    // Der Ablauf läuft weiter, während der Lernende schon den Stand abfragt. Bis zum ersten Warten
    // ist alles an Google gesendet.
    void this.run(turn, input, resumed);
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
  heardState: t.heardState,
  heardAlt: t.heardAlt,
  said: t.said,
  firstMs: t.firstMs,
  helps: t.helps,
  helpsState: t.helpsState,
  phase: t.phase,
  teach: t.teach,
  repeat: t.repeat,
  expecting: t.expecting,
  verified: t.verified,
});

/** Hat Anja beim Vorlesen im Wesentlichen den festen Text gesagt? (Wortvergleich, ohne Satzzeichen) */
function mostlySame(said: string, text: string): boolean {
  const words = (x: string) => (x.toLowerCase().replaceAll("ё", "е").match(/\p{L}+/gu) ?? []);
  const want = words(text);
  if (!want.length) return true;
  const got = new Set(words(said));
  return want.filter((w) => got.has(w)).length / want.length >= 0.7;
}

export class AnjaLive {
  private sessions = new Map<string, Session>();
  private recent = new Map<string, number[]>();
  /**
   * Zuletzt gemessene Antwortzeit je Hilfen-Modell. Gemessen am 18.11.2026: 3.5 Flash-Lite brauchte
   * 6–7 s (knapp über der Frist, jeder Anlauf brach ab), 3.1 Flash-Lite 1–2 s. Gefragt wird zuerst
   * das zuletzt schnellere; ein Abbruch stuft ein Modell für eine Weile zurück.
   */
  private speed = new Map<string, { ms: number; at: number }>();

  private lastSpeed(model: string): number {
    const s = this.speed.get(model);
    return s && this.now() - s.at < SPEED_MEMORY_MS ? s.ms : 0;
  }

  private noteSpeed(model: string, ms: number) {
    const prev = this.lastSpeed(model);
    this.speed.set(model, { ms: prev ? Math.round((prev + ms) / 2) : ms, at: this.now() });
  }

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

  /** Bleibt unter Googles Minutenlimit (15), ohne zwischen zwei Anfragen eines Zugs zu warten. */
  private async pace(model: string) {
    for (;;) {
      const now = this.now();
      const times = (this.recent.get(model) ?? []).filter((t) => now - t < 60_000);
      if (times.length < HELP_PER_MINUTE) {
        times.push(now);
        this.recent.set(model, times);
        return;
      }
      await new Promise((r) => setTimeout(r, Math.max(250, (times[0] as number) + 60_000 - now)));
    }
  }

  /**
   * Eine Frage an die Hilfen-Modelle; die Antwort ist ein JSON-Objekt, das `parse` bestehen muss.
   * Überlastete Modelle (503) und verstümmelte Antworten kommen vor: bis zu fünf Anläufe, reihum.
   */
  private async ask<T>(
    what: string,
    system: string,
    input: unknown,
    maxOutputTokens: number,
    parse: (raw: unknown) => T,
  ): Promise<T> {
    const key = this.deps.key();
    if (!key) throw new Error("no_key");
    const request = this.deps.request ?? fetch;
    let lastError = "helps_exhausted";
    let failedLast = "";
    for (let attempt = 0; attempt < 5; attempt++) {
      const u = this.usage();
      const open = HELP_MODELS.filter(([m, cap]) => (u.count[m] ?? 0) < cap)
        .map(([m]) => m)
        // Stabil sortiert: ohne Messung bleibt die Reihenfolge aus HELP_MODELS.
        .sort((a, b) => this.lastSpeed(a) - this.lastSpeed(b));
      if (!open.length) throw new Error("helps_exhausted");
      // Das schnellste — aber nicht gleich wieder das, das eben gescheitert ist.
      const model = (open.find((m) => m !== failedLast) ?? open[0]) as string;
      failedLast = model;
      await this.pace(model);
      const t0 = Date.now();
      try {
        const res = await request(`${TEXT_URL}/${model}:generateContent`, {
          method: "POST",
          headers: { "content-type": "application/json", "x-goog-api-key": key },
          signal: AbortSignal.timeout(HELP_TIMEOUT_MS),
          body: JSON.stringify({
            systemInstruction: { parts: [{ text: system }] },
            contents: [{ role: "user", parts: [{ text: JSON.stringify(input) }] }],
            generationConfig: {
              temperature: 0.2,
              maxOutputTokens,
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
          this.noteSpeed(model, HELP_TIMEOUT_MS * 2);
          this.log(`[anja-live] ${what} ${model}: ${lastError}`);
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
        this.noteSpeed(model, Date.now() - t0);
        return parse(firstJson(text));
      } catch (e) {
        lastError = e instanceof Error ? e.message.slice(0, 80) : "failed";
        if (/timeout|abort/i.test(lastError)) this.noteSpeed(model, HELP_TIMEOUT_MS * 2);
        this.log(`[anja-live] ${what} ${model}: ${lastError}`);
      }
    }
    throw new Error(lastError);
  }

  /**
   * Satzprüfung, Übersetzung, Wortbedeutungen und Beispielantworten in EINER Anfrage.
   * Meldet die Prüfung einen Fehler, wird eine zweite Meinung eingeholt (eigene Frage: Ist der
   * ursprüngliche Satz wirklich falsch?). Nur wenn beide einig sind, wird die Verbesserung gezeigt.
   */
  async helps(question: string, answer: string, reply: string): Promise<LiveHelps> {
    const helps = await this.ask("Hilfen", HELPS_PROMPT, { question, answer, reply }, 1200, (raw) =>
      settleHelps(raw, answer, question),
    );
    if (helps.check && !helps.check.ok) {
      const fix = helps.check;
      const second = await this.ask(
        "Zweite Meinung",
        CONFIRM_PROMPT,
        {
          original: answer,
          proposedCorrection: fix.correctedRu,
          explanationDe: `${fix.focus} ${fix.explanationDe}`.trim(),
          example: fix.exampleRu ? `${fix.exampleRu} — ${fix.exampleDe}` : "",
        },
        300,
        (raw) => secondOpinion.parse(raw),
      ).catch(() => null);
      if (!second) {
        // Nicht erreichbar: weder »falsch« noch »Genau so« — lieber kein Urteil.
        this.log("[anja-live] Korrektur ohne zweite Meinung");
        helps.check = null;
        helps.goodDe = "";
      } else if (second.originalAcceptable) {
        // Die zweite Meinung ist die gezielte Frage »ist das Original wirklich falsch?« und darin
        // gemessen verlässlich (12/12 Sätze, 10/10 echte Fehler bestätigt); die Satzprüfung meldet
        // dagegen gelegentlich Fehler, wo keine sind. Verwirft sie die Verbesserung, ist der Satz
        // richtig — und das soll der Lernende auch erfahren, statt ohne Urteil dazustehen.
        this.log("[anja-live] Korrektur verworfen — der Satz ist richtig");
        helps.check = { ok: true };
      } else {
        let checked: LiveFix = fix;
        if (!second.explanationCorrect) {
          // Der Fehler ist bestätigt, die Erklärung nicht: die richtige Fassung zeigen, aber nichts Falsches lehren.
          this.log("[anja-live] Erklärung nicht bestätigt");
          // Ohne bestätigte Erklärung auch kein Beispiel (gemessen: »лета« → Beispiel »этот парк« ohne sichtbare Änderung).
          checked = { ...checked, focus: PLAIN_FOCUS, explanationDe: "", exampleRu: "", exampleDe: "" };
        }
        if (checked.exampleRu && !second.exampleCorrect) {
          this.log("[anja-live] Beispiel nicht bestätigt");
          checked = { ...checked, exampleRu: "", exampleDe: "" };
        }
        helps.check = checked;
      }
    }
    return helps;
  }

  /**
   * Prüft eine frei gesprochene Antwort von Anja, bevor sie abgespielt wird. Reines Russisch
   * (Gespräch) braucht keine Prüfung; enthält sie Deutsch, ist es eine Erklärung.
   * → true = bestätigt, false = nicht bestätigt, null = Prüfung nicht erreichbar
   */
  async verify(said: string): Promise<boolean | null> {
    return this.ask("Erklärung prüfen", VERIFY_PROMPT, { said }, 200, (raw) => verifyReply.parse(raw).correct).catch(
      () => null,
    );
  }

  // ── Gespräche ────────────────────────────────────────────────────────────

  private prune() {
    const now = this.now();
    for (const [id, s] of this.sessions)
      if (now - s.lastUsed > IDLE_MS) {
        s.end();
        this.sessions.delete(id);
      }
    while (this.sessions.size >= MAX_SESSIONS) {
      const oldest = [...this.sessions.values()].sort((a, b) => a.lastUsed - b.lastUsed)[0];
      if (!oldest) break;
      oldest.end();
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
      session.end();
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
    this.sessions.get(id)?.end();
    this.sessions.delete(id);
  }

  /** Stand eines Zugs; `wait` hält die Antwort zurück, bis Anja fertig gesprochen hat bzw. die Hilfen da sind. */
  async turn(id: string, n: number, wait: "none" | "heard" | "done" | "helps" = "none", maxMs = 25_000) {
    const turn = this.sessions.get(id)?.turn(n);
    if (!turn) return null;
    const until = this.now() + maxMs;
    const settled = () =>
      !!turn.error ||
      (wait === "none"
        ? true
        : wait === "heard"
          ? turn.heardSettled || turn.done
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
