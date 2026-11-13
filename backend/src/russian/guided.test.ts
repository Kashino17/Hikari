import { describe, expect, it } from "vitest";
import { conversationReply, conversationRequest } from "./conversation.js";
import { BASE, type Beat, SCENARIO_IDS, planFor, scenarioId } from "./guided-content.js";
import { formsOf, guidedTurn, readAnswer } from "./guided.js";

const TITLES: Record<string, string> = {
  cafe: "Ein Café, zwei Menschen: Du triffst Anja in einem kleinen Café.",
  friend: "Ein neuer Kontakt: Ihr lernt euch kennen.",
  weekend: "Ein schöner freier Tag: Ihr plant etwas.",
  journey: "Unterwegs: Ihr findet gemeinsam den richtigen Weg.",
  memory: "Eine kleine Geschichte: Eine schöne Erinnerung.",
  film: "Nach dem Film: Ihr sprecht über eine Filmszene.",
  music: "Dein Soundtrack: Ihr sprecht über Musik.",
  choice: "Was ist dir wichtig?: Ihr trefft eine kleine Entscheidung.",
};
const NAMES = new Set(["аня", "макс", "анна"]);
const base = new Set(formsOf(BASE));
const baseWords = BASE.split(" ").map((ru) => ({ ru, de: "bekannt" }));
const beatOf = (id: string, key: string) => planFor(id).find((b) => b.key === key) as Beat;

describe("Anjas Gesprächsinhalte", () => {
  it("kennt jedes Szenario der App", () => {
    for (const [id, title] of Object.entries(TITLES)) expect(scenarioId(title)).toBe(id);
    expect(Object.keys(TITLES).sort()).toEqual([...SCENARIO_IDS].sort());
  });

  it("erklärt jedes Wort, das nicht zu den Grundwörtern gehört", () => {
    const missing: string[] = [];
    for (const id of SCENARIO_IDS)
      for (const beat of planFor(id)) {
        const own = new Set((beat.vocab ?? []).flatMap((w) => formsOf(w.ru)));
        const all = new Set([
          ...own,
          ...beat.options.flatMap((o) => (o.vocab ?? []).flatMap((w) => formsOf(w.ru))),
        ]);
        const check = (where: string, text: string, allowed: Set<string>) => {
          for (const form of formsOf(text))
            if (!base.has(form) && !allowed.has(form) && !NAMES.has(form))
              missing.push(`${id}/${beat.key} ${where}: ${form}`);
        };
        check("Frage", `${beat.ask.ru} ${beat.simple?.ru ?? ""} ${beat.starter ?? ""}`, own);
        for (const option of beat.options) {
          // Die Reaktion steht erst im NÄCHSTEN Zug — dort gilt nur der Wortschatz dieser Möglichkeit.
          check(
            "Reaktion",
            option.react.ru,
            new Set((option.vocab ?? []).flatMap((w) => formsOf(w.ru))),
          );
          check("Beispiel", option.example.ru, all);
        }
      }
    expect(missing).toEqual([]);
  });

  it("fragt in keinem Plan zweimal dasselbe und endet jede Frage mit einem Fragezeichen", () => {
    for (const id of SCENARIO_IDS) {
      const asks = planFor(id).map((b) => formsOf(b.ask.ru.replace(/^Привет!\s*/, "")).join(" "));
      expect(new Set(asks).size, id).toBe(asks.length);
      for (const beat of planFor(id))
        expect(beat.ask.ru.endsWith("?"), `${id}/${beat.key}`).toBe(true);
      expect(planFor(id).length, id).toBeGreaterThanOrEqual(13);
    }
  });
});

describe("Antworten verstehen", () => {
  it("erkennt die Wahl an einem Wort — auch in Kadirs ganzen Sätzen", () => {
    const order = beatOf("cafe", "tee-kaffee");
    expect(readAnswer(order, "я буду чай спасибо")?.says).toBe("dass du Tee möchtest");
    expect(readAnswer(order, "Кофе, пожалуйста!")?.says).toBe("dass du Kaffee möchtest");
    expect(readAnswer(order, "нет я не люблю кофе")?.says).toBe("dass du Tee möchtest");
    expect(readAnswer(order, "не чай")?.says).toBe("dass du Kaffee möchtest");
    expect(readAnswer(order, "абракадабра")).toBeNull();
  });
  it("trennt Ja und Nein, auch ohne »да«", () => {
    const milk = beatOf("cafe", "milch");
    expect(readAnswer(milk, "Нет я не люблю молоко")?.when).toBe("no");
    expect(readAnswer(milk, "не очень")?.when).toBe("no");
    expect(readAnswer(milk, "да")?.when).toBe("yes");
    expect(readAnswer(milk, "я люблю молоко")?.when).toBe("yes");
    expect(readAnswer(milk, "молоко")?.when).toBe("yes");
    expect(readAnswer(milk, "чай")).toBeNull();
  });
  it("nimmt bei offenen Fragen jede Antwort und versteht Wendungen", () => {
    expect(readAnswer(beatOf("friend", "name"), "меня зовут Кадир")?.says).toBe("wie du heißt");
    const mood = beatOf("friend", "befinden");
    expect(readAnswer(mood, "плохо")?.says).toBe("dass es dir nicht gut geht");
    expect(readAnswer(mood, "не очень")?.says).toBe("dass es dir nicht so gut geht");
    expect(readAnswer(mood, "хорошо спасибо")?.says).toBe("dass es dir gut geht");
  });
});

describe("ein ganzes Gespräch", () => {
  /** Spielt ein Gespräch durch; der Lernende antwortet mit dem Beispiel, das `pick` wählt. */
  function play(id: string, pick: (n: number, count: number) => number, turns: number) {
    let knownWords = [...baseWords];
    const history: { role: "user" | "assistant"; content: string }[] = [];
    const log: {
      said: string;
      anja: string;
      card: string[];
      overflow: string[];
      meaning?: string;
      praise?: string;
    }[] = [];
    let message = "";
    for (let n = 0; n <= turns; n++) {
      const input = conversationRequest.parse({
        stage: 0,
        scenario: TITLES[id],
        action: n === 0 ? "start" : "reply",
        message,
        history,
        knownWords,
      });
      const turn = guidedTurn(input);
      expect(() => conversationReply.parse(turn.reply), `${id} Zug ${n}`).not.toThrow();
      log.push({
        said: message,
        anja: turn.reply.partnerRu,
        card: turn.reply.words.map((w) => w.ru),
        overflow: turn.overflow.map((w) => w.ru),
        meaning: turn.reply.feedback?.meaning,
        praise: turn.reply.feedback?.praise,
      });
      history.push(
        { role: "user", content: message || "Gesprächsaktion: start" },
        { role: "assistant", content: turn.reply.partnerRu },
      );
      knownWords = [...knownWords, ...turn.reply.words];
      const examples = turn.reply.examples;
      message = (examples[pick(n, examples.length)] ?? examples[0])?.ru ?? "Да.";
    }
    return log;
  }
  const strategies: [string, (n: number, count: number) => number][] = [
    ["erste Beispielantwort", () => 0],
    ["letzte Beispielantwort", (_n, count) => count - 1],
    ["abwechselnd", (n, count) => n % count],
  ];

  for (const id of Object.keys(TITLES))
    for (const [name, pick] of strategies)
      it(`${id}, ${name}: geht voran, reagiert, erklärt alles`, () => {
        const turns = planFor(id).length - 1;
        const log = play(id, pick, turns);
        // Jeder Zug endet mit einer Frage, und keine Frage kommt zweimal.
        const questions = log.map((t) => formsOf(t.anja.split(/[.!]\s+/).pop() ?? "").join(" "));
        for (const t of log) expect(t.anja.endsWith("?"), t.anja).toBe(true);
        expect(new Set(questions).size, questions.join(" | ")).toBe(questions.length);
        // Anja plappert nie nach.
        for (const t of log.slice(1))
          expect(formsOf(t.anja).join(" ")).not.toBe(formsOf(t.said).join(" "));
        // Jede Beispielantwort wird verstanden und bekommt eine Reaktion vor der nächsten Frage.
        for (const t of log.slice(1)) {
          expect(t.meaning, `»${t.said}« → ${t.anja}`).toBe("clear");
          expect(t.praise).toMatch(/^Gut — du hast gesagt, /);
          expect(t.anja.split(/[.!?]\s+/).length, t.anja).toBeGreaterThanOrEqual(2);
        }
        // Nie mehr neue Wörter, als auf die Karte passen.
        for (const t of log)
          expect(t.overflow, `${t.anja} · Karte: ${t.card.join(", ")}`).toEqual([]);
      });

  it("läuft Kadirs Café-Gespräch vom 14.11. so, wie es hätte laufen sollen", () => {
    const history: { role: "user" | "assistant"; content: string }[] = [];
    const say = (action: string, message: string) => {
      const turn = guidedTurn(
        conversationRequest.parse({
          stage: 0,
          scenario: TITLES.cafe,
          action,
          message,
          history,
          knownWords: baseWords,
        }),
      );
      history.push(
        { role: "user", content: message || "start" },
        { role: "assistant", content: turn.reply.partnerRu },
      );
      return turn.reply;
    };
    expect(say("start", "").partnerRu).toBe("Привет! Чай или кофе?");
    const second = say("reply", "я буду чай спасибо");
    expect(second.partnerRu).toBe("Чай — хорошо. А я буду кофе. Ты будешь сахар?");
    expect(second.feedback?.praise).toBe("Gut — du hast gesagt, dass du Tee möchtest.");
    expect(second.words.map((w) => w.ru)).toEqual(["буду", "будешь", "сахар"]);
    expect(say("reply", "нет спасибо").partnerRu).toBe("Хорошо. А я буду сахар. Ты любишь молоко?");
  });

  it("reagiert neutral, wenn sie nicht versteht oder das Gespräch noch vom Sprachmodell stammt", () => {
    const request = (history: { role: "user" | "assistant"; content: string }[], message: string) =>
      guidedTurn(
        conversationRequest.parse({
          stage: 0,
          scenario: TITLES.cafe,
          action: "reply",
          message,
          history,
          knownWords: baseWords,
        }),
      ).reply;
    const ours = [
      { role: "user" as const, content: "start" },
      { role: "assistant" as const, content: "Привет! Чай или кофе?" },
    ];
    const unclear = request(ours, "абракадабра");
    expect(unclear.partnerRu).toBe("Хорошо! Ты будешь сахар?");
    expect(unclear.feedback).toMatchObject({ meaning: "partial", focus: "", correctedRu: "" });
    const foreign = [
      { role: "user" as const, content: "start" },
      { role: "assistant" as const, content: "Ты любишь суп?" },
    ];
    expect(request(foreign, "чай").partnerRu).toBe("Хорошо! Ты будешь сахар?");
  });

  it("fragt auf Wunsch einfacher und geht bei den übrigen Gesprächs-Knöpfen einfach weiter", () => {
    const history = [
      { role: "user" as const, content: "start" },
      { role: "assistant" as const, content: "Привет! Ты любишь лето или зиму?" },
    ];
    const turn = (action: string) =>
      guidedTurn(
        conversationRequest.parse({
          stage: 0,
          scenario: TITLES.memory,
          action,
          history,
          knownWords: baseWords,
        }),
      ).reply;
    expect(turn("simplify").partnerRu).toBe("Лето или зима?");
    expect(turn("simplify").feedback).toBeNull();
    expect(turn("deeper").partnerRu).toBe("Ты любишь море?");
    expect(turn("deeper").feedback).toBeNull();
  });
});
