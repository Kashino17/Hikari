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
const TURN_TIMEOUT_MS = 30_000;
/** Google beendet eine Sitzung nach 15 Minuten — vorher wird mit dem Verlauf neu verbunden. */
const SOCKET_MAX_AGE_MS = 12 * 60_000;
const IDLE_MS = 10 * 60_000;
const MAX_SESSIONS = 8;
/** Üblich sind 1,3 s. Wer nach 6 s nicht geantwortet hat, ist überlastet — dann lieber das nächste Modell. */
const HELP_TIMEOUT_MS = 6_000;
/** Höchstens so viele Anfragen je Minute an ein Hilfen-Modell (Google erlaubt 15). */
const HELP_PER_MINUTE = 12;
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
  goodDe: z.string().max(300).default(""),
  replyDe: z.string().trim().min(1).max(900),
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
  check:
    | null
    | { ok: true }
    | { ok: false; correctedRu: string; correctedDe: string; focus: string; explanationDe: string };
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
}

interface Turn extends LiveTurnView {
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
    "- Kadir spricht Russisch mit Akzent und macht Fehler. Geh davon aus, dass er Russisch spricht, und hör wohlwollend zu.",
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
{"check":{"ok":true,"correctedRu":"","correctedDe":"","focus":"","explanationDe":""},"goodDe":"","replyDe":"","words":[{"ru":"","de":""}],"examples":[{"ru":"","de":""},{"ru":"","de":""}]}

check — judge ONLY Kadir's answer. It is a speech transcript: ignore punctuation and capitalisation completely.
ok=true when the sentence is understandable and has no real grammar mistake. Short answers and single words are fine.
WHAT the learner says (likes, dislikes, choices) is never a mistake, and a different wording than you would choose is not a mistake.
Whether the answer fits the question is NOT your concern: an answer about a different topic is still ok=true when its Russian is correct. Never swap a word for another word to make the answer fit the question.
ok=false ONLY for a real language mistake such as a wrong word form or a missing verb. Then:
correctedRu = the learner's own sentence with ONLY that mistake fixed (same words otherwise), correctedDe = its German translation,
focus = one short German sentence naming the issue, explanationDe = one short German sentence explaining the rule precisely, naming the grammar (for example the case or the verb form) and quoting the Russian forms.
When ok=true, or when answer is empty, the other four fields are empty strings.

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
Return ONLY JSON: {"originalAcceptable": true|false, "explanationCorrect": true|false, "why": "one short English sentence"}`;

const secondOpinion = z.object({
  originalAcceptable: z.boolean(),
  // Fehlt das Urteil, gilt die Erklärung als nicht bestätigt.
  explanationCorrect: z.boolean().default(false),
});

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
    if (!turn || turn.done || !sc) return;
    for (const part of sc.modelTurn?.parts ?? []) {
      const data = part.inlineData?.data;
      if (!data) continue;
      turn.firstMs ??= this.host.now() - turn.startedAt;
      turn.audio.push(Buffer.from(data, "base64"));
    }
    if (sc.outputTranscription?.text) turn.said += sc.outputTranscription.text;
    if (sc.turnComplete) void this.complete(turn);
    else wake(turn);
  }

  /** Anja ist fertig: festlegen, was der Lernende gesagt hat, dann Prüfung und Hilfen anstoßen. */
  private async complete(turn: Turn) {
    if (turn.timer) clearTimeout(turn.timer);
    turn.said = tidy(turn.said);
    if (turn.kind === "audio") {
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
          `[anja-live] Ohr «${ear ?? "—"}» · Gespräch «${tidy(turn.heardAgent)}» · Anja «${turn.said}»`,
        );
    }
    if (turn.error) return;
    turn.done = true;
    const question = [...this.lines].reverse().find((l) => l.who === "anja")?.ru ?? "";
    const answered = turn.kind === "audio" || turn.kind === "text";
    if (answered && turn.heard) this.lines.push({ who: "kadir", ru: turn.heard });
    if (turn.said) this.lines.push({ who: "anja", ru: turn.said });
    wake(turn);
    if (!turn.said) return;
    turn.helpsState = "pending";
    // Beurteilt wird nur, was sicher verstanden wurde.
    const judged = answered && turn.heardState === "sure" ? turn.heard : "";
    this.host
      .helps(question, judged, turn.said)
      .then((helps) => {
        const fix = helps.check;
        if (fix && !fix.ok && otherEarHeardFix(judged, fix.correctedRu, turn.heardAlt)) {
          this.host.log("[anja-live] Verbesserung zurückgehalten — das zweite Ohr hörte die richtige Form");
          helps.check = null;
          helps.goodDe = "";
        }
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
      heardState: input.kind === "text" ? (isRussian(input.text) ? "sure" : "foreign") : "none",
      heardAlt: "",
      heardAgent: "",
      ear: null,
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
      this.listening = turn;
      // Derselbe Ton geht zugleich ans Ohr; ein Fehler dort hält das Gespräch nie auf.
      turn.ear = this.ear.listen(pcm, input.rate).catch(() => null);
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
    else if (input.kind === "start") {
      this.ear.warm();
      say({ text: resumed ? REGIE.resume : REGIE.start });
    } else say({ text: REGIE[input.kind] });
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
});

export class AnjaLive {
  private sessions = new Map<string, Session>();
  private recent = new Map<string, number[]>();

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
    for (let attempt = 0; attempt < 5; attempt++) {
      const u = this.usage();
      const open = HELP_MODELS.filter(([m, cap]) => (u.count[m] ?? 0) < cap).map(([m]) => m);
      if (!open.length) throw new Error("helps_exhausted");
      const model = open[attempt % open.length] as string;
      await this.pace(model);
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
        return parse(firstJson(text));
      } catch (e) {
        lastError = e instanceof Error ? e.message.slice(0, 80) : "failed";
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
      } else if (!second.explanationCorrect) {
        // Der Fehler ist bestätigt, die Erklärung nicht: die richtige Fassung zeigen, aber nichts Falsches lehren.
        this.log("[anja-live] Erklärung nicht bestätigt");
        helps.check = { ...fix, focus: PLAIN_FOCUS, explanationDe: "" };
      }
    }
    return helps;
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
