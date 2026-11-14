/**
 * Misst die Satzprüfung von Anja live am echten Modell — vor jeder Änderung an HELPS_PROMPT,
 * an den Modellen oder an settleHelps laufen lassen:
 *
 *   cd backend && ./node_modules/.bin/tsx scripts/anja-live-eval.ts
 *
 * Kostet rund 30 Anfragen aus dem Tageskontingent der Flash-Lite-Modelle.
 * Ziel: 0 richtige Sätze korrigiert, alle Fehler gefunden und richtig gelöst.
 */
import { homedir } from "node:os";
import { join } from "node:path";
import { readGeminiKey } from "../src/api/russian-anja-live.js";
import { AnjaLive } from "../src/russian/anja-live.js";

const dir = join(homedir(), ".hikari", "erleben");
const live = new AnjaLive({ key: () => readGeminiKey(dir), usageFile: join(dir, "usage.json") });

/** [Anjas Frage, Antwort des Lernenden] — alle sprachlich richtig. */
const RIGHT: [string, string][] = [
  ["Ты любишь чай?", "Нет я не люблю чай"],
  ["Ты любишь чай?", "Да, я люблю чай"],
  ["Как дела?", "Хорошо, спасибо"],
  ["Что ты хочешь?", "Я хочу воду"],
  ["Ты любишь музыку?", "Да, очень люблю музыку"],
  ["Где ты сегодня?", "Я дома"],
  ["Что ты делаешь сегодня?", "Сегодня я работаю"],
  ["Ты любишь кофе?", "Нет"],
  ["Чай или кофе?", "Кофе, пожалуйста"],
  ["Как тебя зовут?", "Меня зовут Кадир"],
  ["Ты любишь фильмы?", "Да, я люблю смотреть фильмы"],
  ["Где твой дом?", "Мой дом в Берлине"],
  ["Что ты любишь?", "Я люблю мою семью"],
  ["Ты работаешь сегодня?", "Нет, сегодня я отдыхаю"],
  ["Ты хочешь чай?", "Да хочу спасибо"],
  // Am Thema vorbei, aber richtiges Russisch — kein Sprachfehler.
  ["Ты любишь кино?", "Нет, я не люблю кофе"],
  ["Что ты делаешь сегодня?", "Я люблю музыку"],
  ["Где ты работаешь?", "Я дома"],
  ["Какой фильм ты любишь?", "Я хочу чай"],
  ["Ты любишь зиму?", "Сегодня я работаю"],
];
/** [Frage, Antwort mit Fehler, Wortform, die in der Lösung stehen muss] */
const WRONG: [string, string, string][] = [
  ["Ты любишь чай?", "Я любить чай", "люблю"],
  ["Что ты хочешь?", "Я хочу вода", "воду"],
  ["Ты любишь музыку?", "Я люблю музыка", "музыку"],
  ["Что ты делаешь?", "Я смотреть кино", "смотрю"],
  ["Где ты работаешь?", "Я работаю в офис", "офисе"],
  ["Что ты любишь?", "Я люблю мой семья", "семью"],
  ["Где твой дом?", "Мой дом в Берлин", "берлине"],
  ["Что ты читаешь?", "Я читаю книга", "книгу"],
  ["Как тебя зовут?", "Я зовут Кадир", "меня"],
  ["Ты любишь зиму?", "Нет, я люблю лета", "лето"],
];
const REPLIES = [
  "Хорошо! А что ты делаешь сегодня?",
  "Понятно. Ты любишь смотреть фильмы?",
  "Я тоже. Какую музыку ты любишь?",
  "Отлично! Где ты работаешь?",
];

let falseFix = 0;
let found = 0;
let solved = 0;
let failed = 0;
const notes: string[] = [];
const cases = [...RIGHT.map(([q, a]) => [q, a, ""] as const), ...WRONG];
for (const [i, [question, answer, want]] of cases.entries()) {
  const reply = REPLIES[i % REPLIES.length] as string;
  try {
    const h = await live.helps(question, answer, reply);
    const fix = h.check && !h.check.ok ? h.check : null;
    if (!want) {
      if (fix) {
        falseFix++;
        notes.push(`FALSCH korrigiert: «${answer}» → ${fix.correctedRu} (${fix.focus})`);
      }
    } else if (fix) {
      found++;
      notes.push(`  Erklärung zu «${answer}»: ${fix.focus} — ${fix.explanationDe}`);
      if (fix.correctedRu.toLowerCase().includes(want)) solved++;
      else notes.push(`schief gelöst: «${answer}» → ${fix.correctedRu}`);
    } else notes.push(`ÜBERSEHEN: «${answer}»`);
    notes.push(`  Lob zu «${answer}»: ${h.goodDe || "—"}`);
    if (i % 8 === 0)
      notes.push(
        `  Beispiel-Hilfen zu «${reply}»: ${h.replyDe} | ${h.words.map((w) => `${w.ru}=${w.de}`).join(", ")} | ${h.examples.map((e) => `${e.ru} (${e.de})`).join(" / ")}`,
      );
  } catch (e) {
    failed++;
    notes.push(`AUSGEFALLEN: «${answer}» ${String(e).slice(0, 80)}`);
  }
}
console.log(
  JSON.stringify({
    richtigeFalschKorrigiert: `${falseFix}/${RIGHT.length}`,
    fehlerGefunden: `${found}/${WRONG.length}`,
    richtigGeloest: `${solved}/${WRONG.length}`,
    ausgefallen: failed,
  }),
);
console.log(notes.join("\n"));
