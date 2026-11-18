/**
 * Anja als Lehrerin am echten Modell durchspielen (18.11.2026): Fehler → geprüfte Erklärung →
 * Wiederholung (falsch, dann richtig) → »Ich komme nicht weiter« → Frage auf Deutsch → Niveau B1.
 * Gesprochene Wiederholungen kommen von der Mac-Stimme Milena (say + afconvert).
 *
 *   cd backend && ./node_modules/.bin/tsx scripts/anja-teach-smoke.ts
 *
 * Kostet rund 10–15 Anfragen aus dem Flash-Lite-Kontingent (Gemini Live selbst hat kein Tageslimit).
 * Nie parallel zu einem anderen Messlauf starten (Minutenlimit → Tagessperre im Zähler).
 */
import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync } from "node:fs";
import { homedir, tmpdir } from "node:os";
import { join } from "node:path";
import { readGeminiKey } from "../src/api/russian-anja-live.js";
import { AnjaLive, type LiveInput, liveStart } from "../src/russian/anja-live.js";

const dir = join(homedir(), ".hikari", "erleben");
const live = new AnjaLive({
  key: () => readGeminiKey(dir),
  usageFile: join(dir, "usage.json"),
  log: (m) => console.log(`    ${m}`),
});
const tmp = mkdtempSync(join(tmpdir(), "anja-teach-"));

/** Russischer Satz → 16 kHz PCM (Base64), gesprochen von Milena. */
function voice(ru: string): string {
  const aiff = join(tmp, "x.aiff");
  const wav = join(tmp, "x.wav");
  execFileSync("say", ["-v", "Milena", "-r", "150", "-o", aiff, ru]);
  execFileSync("afconvert", ["-f", "WAVE", "-d", "LEI16@16000", "-c", "1", aiff, wav]);
  const data = readFileSync(wav);
  const at = data.indexOf("data") + 8;
  return data.subarray(at).toString("base64");
}

async function step(id: string, label: string, input: LiveInput) {
  const t0 = Date.now();
  const n = await live.send(id, input);
  const heard = await live.turn(id, n, "heard", 40_000);
  const tHeard = Date.now() - t0;
  const t = await live.turn(id, n, "done", 60_000);
  const total = Date.now() - t0;
  console.log(`\n▶ ${label}  (verstanden nach ${(tHeard / 1000).toFixed(1)} s, Ton ab ${t?.firstMs != null ? (t.firstMs / 1000).toFixed(1) : "–"} s, fertig ${(total / 1000).toFixed(1)} s)`);
  console.log(`  gehört: «${heard?.heard ?? ""}» [${heard?.heardState}]`);
  if (t?.teach) console.log(`  FESTER TEXT (${t.teach.lang}): ${t.teach.speech}`);
  console.log(`  ANJA SAGTE: ${t?.said}`);
  if (t?.repeat) console.log(`  Wiederholung: ${JSON.stringify(t.repeat)}`);
  if (t?.expecting) console.log(`  erwartet: «${t.expecting.correctedRu}» (Versuche ${t.expecting.tries})`);
  if (t?.verified != null) console.log(`  Erklärung geprüft: ${t.verified}`);
  if (t?.error) console.log(`  FEHLER: ${t.error}`);
  return t;
}

async function session(level: "A1" | "B1") {
  console.log(`\n══════ Niveau ${level} ══════`);
  const { id, turn } = await live.start(
    liveStart.parse({
      stage: 0,
      level,
      scenario: "Im Café: Anja und Kadir bestellen etwas zu trinken.",
      knownWords: ["чай", "кофе", "вода", "хочу", "люблю"],
      stones: [{ topic: "Wen-Fall nach »wollen«", wrongRu: "вода", rightRu: "воду" }],
    }),
  );
  const first = await live.turn(id, turn, "done", 40_000);
  console.log(`▶ Anja beginnt: ${first?.said}`);
  return id;
}

const a = await session("A1");
await step(a, "Fehler (getippt): Я хочу вода", { kind: "text", text: "Я хочу вода" });
await step(a, "Wiederholung falsch (gesprochen)", { kind: "audio", audio: voice("Я хочу вода."), rate: 16000 });
await step(a, "Wiederholung richtig (gesprochen)", { kind: "audio", audio: voice("Я хочу воду."), rate: 16000 });
await step(a, "Ich komme nicht weiter", { kind: "help" });
await step(a, "Frage auf Deutsch (getippt)", { kind: "text", text: "Was heißt eigentlich пить?" });
await step(a, "Richtiger Satz (gesprochen)", { kind: "audio", audio: voice("Я люблю чай с молоком."), rate: 16000 });
live.end(a);

const b = await session("B1");
await step(b, "Fehler (getippt) bei B1", { kind: "text", text: "Я люблю музыка" });
await step(b, "Wiederholung falsch bei B1 → jetzt Deutsch", { kind: "audio", audio: voice("Я люблю музыка."), rate: 16000 });
live.end(b);
process.exit(0);
