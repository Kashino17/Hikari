/**
 * Nachfragen in der Grammatik am echten Modell (18.11.2026): Stimmt der Grund, den Anja nennt?
 * Ich lese jede Antwort selbst gegen; die Prüfung muss falsche Gründe verwerfen.
 *
 *   cd backend && ./node_modules/.bin/tsx scripts/anja-ask-eval.ts
 *
 * Kostet je Frage 2–4 Anfragen (Flash-Lite) plus das Vorlesen (Gemini Live, ohne Tageslimit).
 */
import { homedir } from "node:os";
import { join } from "node:path";
import { readGeminiKey } from "../src/api/russian-anja-live.js";
import { AnjaLive } from "../src/russian/anja-live.js";

const dir = join(homedir(), ".hikari", "erleben");
const live = new AnjaLive({ key: () => readGeminiKey(dir), usageFile: join(dir, "usage.json"), log: (m) => console.log(`    ${m}`) });
const AKK = "Wen-Fall (Akkusativ): Endet das Wort auf «-а», wird daraus «-у»; auf «-я» wird «-ю». Männliche Dinge wie «сок» bleiben, wie sie sind. Männliche Personen und Tiere bekommen «-а»: «брат» → «брата». Beispiele: «Это вода.» → «Я пью воду.»; «Это сок.» → «Я пью сок.»";
const QS = process.argv[2] ? [process.argv[2]] : [
  "Warum bleibt sok gleich, aber wada wird zu wodu?",
  "Warum ändert sich кофе nicht?",
  "Wann sage ich брат und wann брата?",
  "Was ist der Unterschied zwischen воду und воды?",
];
for (const question of QS) {
  const t0 = Date.now();
  const r = await live.ask({ question, topic: "»Wen oder was?« (Akkusativ)", reference: AKK, level: "A1" });
  console.log(`\n? ${question}  (${((Date.now() - t0) / 1000).toFixed(1)} s, geprüft: ${r.verified})\n  ${r.text}`);
  if (r.id) live.end(r.id);
}
process.exit(0);
