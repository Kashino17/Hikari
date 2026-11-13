// Führt Anjas Gespräch auf den ersten Stufen nach festem Plan (guided-content.ts): versteht die
// Antwort an Schlüsselwörtern, reagiert darauf und stellt die nächste, neue Frage. Kein Sprachmodell —
// deshalb sofort da, nie wiederholt, nie nachgeplappert.
import type { ConversationReply, ConversationRequest } from "./conversation.js";
import {
  BASE,
  type Beat,
  type Line,
  MISSIONS,
  type Option,
  planFor,
  scenarioId,
} from "./guided-content.js";

/** Russische Wortformen eines Textes: klein, ё wie е, ohne Betonungszeichen. */
export const formsOf = (text: string) =>
  text
    .toLowerCase()
    .replace(/[+́]/g, "")
    .replace(/ё/g, "е")
    .match(/[а-я]+/g) ?? [];

/** Wörter, die allein schon Zustimmung bedeuten. */
const YES = new Set(["да", "ага", "угу", "конечно", "люблю", "хочу", "буду", "пью", "ем", "есть"]);
/** Zu kurz oder zu allgemein, um an ihnen eine Frage wiederzuerkennen. */
const SMALL = new Set(["ты", "я", "а", "и", "это", "что", "как", "или", "не", "у", "в"]);

const NEUTRAL: Line = { ru: "Хорошо!", de: "Gut!" };

/** Bedeutungen der Grundwörter — für Lernende, die eines davon doch noch nicht hatten. */
const BASE_LINES: Line[] = [
  ["Привет", "Hallo"],
  ["чай", "Tee"],
  ["да", "ja"],
  ["спасибо", "danke"],
  ["кофе", "Kaffee"],
  ["нет", "nein"],
  ["пожалуйста", "bitte"],
  ["или", "oder"],
  ["я", "ich"],
  ["люблю", "ich mag"],
  ["ты", "du"],
  ["а", "und"],
  ["как дела", "wie geht's"],
  ["хорошо", "gut"],
  ["нормально", "ganz okay"],
].map(([ru, de]) => ({ ru: ru as string, de: de as string }));

/**
 * Welche Antwort-Möglichkeit eines Schritts der Lernende gemeint hat — an Schlüsselwörtern erkannt,
 * nicht an ganzen Sätzen. null = nicht verstanden; dann reagiert Anja neutral und fragt weiter.
 */
export function readAnswer(beat: Beat, message: string): Option | null {
  const tokens: string[] = formsOf(message);
  if (!tokens.length) return null;
  const joined = tokens.join(" ");
  const stemOptions = beat.options.filter((o) => Array.isArray(o.when));
  const matches = (token: string, option: Option) =>
    (option.when as string[]).some((stem) => !stem.includes(" ") && token.startsWith(stem));

  // »не« verneint das nächste genannte Ding: »я не люблю кофе«, »не чай«.
  const negated = new Set<number>();
  tokens.forEach((token, i) => {
    if (token !== "не") return;
    for (let j = i + 1; j <= i + 3 && j < tokens.length; j++)
      if (stemOptions.some((o) => matches(tokens[j] as string, o))) {
        negated.add(j);
        break;
      }
  });
  const saysNo = tokens.includes("нет") || tokens.includes("не");

  // 1. Ein genanntes (und nicht verneintes) Ding entscheidet.
  for (const option of stemOptions)
    if ((option.when as string[]).some((stem) => stem.includes(" ") && joined.includes(stem)))
      return option;
  for (let i = 0; i < tokens.length; i++) {
    if (negated.has(i)) continue;
    const hit = stemOptions.find((o) => matches(tokens[i] as string, o));
    if (hit) return hit;
  }
  // 2. Nein.
  const no = beat.options.find((o) => o.when === "no");
  if (saysNo && no) return no;
  // 3. »Не кофе« bei genau zwei Möglichkeiten heißt: das andere.
  if (negated.size && stemOptions.length === 2) {
    const rejected = stemOptions.find((o) =>
      [...negated].some((i) => matches(tokens[i] as string, o)),
    );
    const other = stemOptions.find((o) => o !== rejected);
    if (other) return other;
  }
  // 4. Ja — ausdrücklich oder indem das Wort aus der Frage aufgegriffen wird.
  const yes = beat.options.find((o) => o.when === "yes");
  if (yes && !saysNo) {
    const asked = formsOf(beat.ask.ru).filter((f) => f.length >= 3 && !SMALL.has(f));
    const echoes = tokens.some((t) => asked.some((a) => a.slice(0, 3) === t.slice(0, 3)));
    if (tokens.some((t) => YES.has(t)) || echoes) return yes;
  }
  // 5. Offene Fragen nehmen jede Antwort.
  return beat.options.find((o) => o.when === "any") ?? null;
}

/** »Привет!« gehört nur an den Gesprächsanfang. */
const withoutGreeting = (line: Line): Line => ({
  ru: line.ru.replace(/^Привет!\s*/, ""),
  de: line.de.replace(/^Hallo!\s*/, ""),
});

/** Die Wortkarte: Einträge für alles, was in diesem Zug vorkommt und der Lernende noch nicht kennt. */
export function cardFor(spoken: string[], entries: Line[], known: Set<string>): Line[] {
  const needed = new Set(spoken.flatMap(formsOf).filter((form) => !known.has(form)));
  const card: Line[] = [];
  const covered = new Set<string>();
  for (const entry of [...entries, ...BASE_LINES]) {
    const forms = formsOf(entry.ru);
    if (!forms.some((form) => needed.has(form) && !covered.has(form))) continue;
    card.push(entry);
    for (const form of forms) covered.add(form);
  }
  return card;
}

export type GuidedTurn = {
  reply: ConversationReply;
  /** Die Frage, auf die der Lernende gerade geantwortet hat (für die Satzprüfung), sonst null. */
  answered: Line | null;
  /** Wörter, die nicht mehr auf die Karte passten — sollte leer sein (siehe Test). */
  overflow: Line[];
};

/** Ein Zug des geführten Gesprächs. Der Stand ergibt sich allein aus dem Verlauf. */
export function guidedTurn(input: ConversationRequest): GuidedTurn {
  const id = scenarioId(input.scenario);
  const plan = planFor(id);
  const at = (i: number) => plan[((i % plan.length) + plan.length) % plan.length] as Beat;
  const asked = input.history.filter((h) => h.role === "assistant").length;
  const known = new Set([...input.knownWords.flatMap((word) => formsOf(word.ru))]);
  if (!input.knownWords.length) for (const form of formsOf(BASE)) known.add(form);

  const build = (
    partner: Line,
    beat: Beat,
    extraVocab: Line[],
    feedback: ConversationReply["feedback"],
  ): GuidedTurn => {
    const examples = beat.options.slice(0, 3).map((o) => o.example);
    if (examples.length < 2) examples.push({ ru: "Да.", de: "Ja." });
    const spoken = [partner.ru, beat.starter ?? "", ...examples.map((e) => e.ru)];
    const entries = [
      ...extraVocab,
      ...(beat.vocab ?? []),
      ...beat.options.flatMap((o) => o.vocab ?? []),
    ];
    const card = cardFor(spoken, entries, known);
    return {
      reply: {
        partnerRu: partner.ru,
        partnerDe: partner.de,
        cue: beat.cue,
        words: card.slice(0, 4),
        starter: beat.starter ?? "",
        examples,
        feedback,
        chunk: withoutGreeting(beat.simple ?? beat.ask),
        mission: MISSIONS[id] ?? "Antworte Anja auf Russisch.",
      },
      answered: null,
      overflow: card.slice(4),
    };
  };

  if (input.action === "start" || asked === 0) return build(at(0).ask, at(0), [], null);

  if (input.action === "simplify") {
    const beat = at(asked - 1);
    return build(withoutGreeting(beat.simple ?? beat.ask), beat, [], null);
  }

  const next = at(asked);
  const question = withoutGreeting(asked % plan.length === 0 ? next.ask : next.ask);
  if (input.action !== "reply" && input.action !== "repair") return build(question, next, [], null);

  // Stammt die letzte Frage wirklich aus diesem Plan? (Ältere Gespräche hat noch das Sprachmodell geführt.)
  const previous = at(asked - 1);
  const last = formsOf(
    [...input.history].reverse().find((h) => h.role === "assistant")?.content ?? "",
  ).join(" ");
  const ours = [previous.ask, previous.simple].some(
    (line) => line && last.endsWith(formsOf(withoutGreeting(line).ru).join(" ")),
  );
  const option = ours ? readAnswer(previous, input.message) : null;
  const react = option?.react ?? NEUTRAL;
  const turn = build(
    { ru: `${react.ru} ${question.ru}`, de: `${react.de} ${question.de}` },
    next,
    option?.vocab ?? [],
    {
      meaning: option ? "clear" : "partial",
      praise: option ? `Gut — du hast gesagt, ${option.says}.` : "Danke für deine Antwort.",
      focus: "",
      repairCue: "",
      correctedRu: "",
      explanationDe: "",
    },
  );
  return { ...turn, answered: ours ? withoutGreeting(previous.ask) : null };
}
