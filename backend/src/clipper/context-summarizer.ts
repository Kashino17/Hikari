import type { Caption } from "./transcriber.js";
import { QwenNetworkError } from "./qwen-analyzer.js";

export interface SummarizerConfig {
  baseUrl: string;
  model: string;
  fetchFn?: typeof fetch;
}

const SYSTEM_PROMPT = `Du bist ein Video-Clip-Kontextualisierer. Du bekommst ein Transkript eines kurzen Video-Clips (30-90 Sekunden) der aus einem längeren Video extrahiert wurde. Schreib in EINEM kurzen Satz (maximal zwei) worum es in DIESEM Clip geht, sodass jemand der sonst nichts vom Originalvideo weiß, sofort folgen kann.

REGELN:
- MAXIMAL 1-2 kurze Sätze, zusammen höchstens ~140 Zeichen. Lieber zu kurz als zu lang — der Text wird als kleine Einblendung über dem Video gezeigt und darf NICHT abgeschnitten werden.
- Bring den Kernpunkt sofort. Kein Setup-Geplänkel, keine Aufzählung.
- Erkläre das THEMA und die Kernaussage, nicht nur was der Sprecher macht.
- Keine Floskeln wie "In diesem Clip..." oder "Der Sprecher erklärt..." — geh direkt zum Inhalt.
- Schreib in der Sprache des Transkripts (vermutlich Deutsch).
- Wenn das Transkript fragmentarisch ist: das Beste mit den Worten machen, kein Disclaimer.

OUTPUT: nur der eine kurze Kontext-Satz. Keine Markdown, keine Anführungszeichen, keine Erklärungen drumherum.`;

/**
 * Generate a 1-3 sentence context summary for a clip from its captions.
 * Returns null on any failure — context is non-essential, clip ships
 * without it.
 */
export async function summarizeContext(
  captions: Caption[],
  config: SummarizerConfig,
): Promise<string | null> {
  if (!captions || captions.length === 0) return null;
  const transcript = captions.map((c) => c.text).join(" ").trim();
  if (transcript.length < 20) return null; // too short to be useful
  return chatCompletion(SYSTEM_PROMPT, `Transkript:\n${transcript}`, config);
}

const SYSTEM_PROMPT_VIDEO = `Du schreibst Kurzbeschreibungen für Video-Vorschaukarten einer deutschen App. Du bekommst Titel und Transkript eines YouTube-Videos.

REGELN:
- 1-2 Sätze, zusammen höchstens ~220 Zeichen. Der Text steht auf einer Vorschaukarte und darf nicht abgeschnitten werden.
- Mach neugierig, ohne zu spoilern — worum geht es, was ist der Reiz?
- Nüchtern und konkret, keine Werbesprache, keine Floskeln wie "In diesem Video...".
- Schreib in der Sprache des Transkripts (vermutlich Deutsch).

OUTPUT: nur die Kurzbeschreibung. Keine Markdown, keine Anführungszeichen, kein Präfix.`;

/**
 * Karten-Teaser für ein Langvideo aus Titel + YouTube-Transkript.
 * Best-effort: liefert null bei zu wenig Text oder jedem Fehler — die Karte
 * fällt dann auf die Scorer-Begründung zurück, der Approve läuft weiter.
 */
export async function summarizeVideoTranscript(
  title: string,
  transcript: string,
  config: SummarizerConfig,
): Promise<string | null> {
  const text = transcript.trim();
  if (text.length < 100) return null;
  try {
    return await chatCompletion(
      SYSTEM_PROMPT_VIDEO,
      `Titel: ${title}\n\nTranskript:\n${text.slice(0, 24_000)}`,
      config,
    );
  } catch {
    return null;
  }
}

/** Gemeinsamer OpenAI-kompatibler Chat-Call (LM Studio/Ollama) beider Summarizer. */
async function chatCompletion(
  systemPrompt: string,
  userContent: string,
  config: SummarizerConfig,
): Promise<string | null> {
  const fetchFn = config.fetchFn ?? fetch;
  const body = {
    model: config.model,
    messages: [
      { role: "system", content: systemPrompt },
      { role: "user", content: userContent },
    ],
    temperature: 0.3,
    // Qwen 3.6 is a reasoning model: it spends thinking tokens (reasoning_content)
    // BEFORE the answer lands in content. Measured ~3k reasoning for a short
    // summary; with the old 2000 cap the budget ran out mid-thinking and content
    // came back EMPTY → null (53/60 clips failed the backfill). Give real
    // headroom so reasoning + the one-sentence answer always fit.
    max_tokens: 8000,
    stream: false,
  };

  let res: Response;
  try {
    res = await fetchFn(`${config.baseUrl}/v1/chat/completions`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(body),
    });
  } catch (e) {
    throw new QwenNetworkError(
      `summarizer: cannot reach ${config.baseUrl}: ${(e as Error).message}`,
    );
  }
  if (!res.ok) {
    if (res.status >= 500 && res.status < 600) {
      throw new QwenNetworkError(`summarizer ${res.status}: ${await res.text()}`);
    }
    throw new Error(`summarizer ${res.status}: ${await res.text()}`);
  }
  const json = (await res.json()) as {
    choices: Array<{ message: { content?: string; reasoning_content?: string } }>;
  };
  const msg = json.choices[0]?.message;
  // Prefer content; if a reasoning model returned empty content but parked the
  // answer in reasoning_content, fall back to its last non-empty line.
  let text = msg?.content?.trim();
  if (!text && msg?.reasoning_content) {
    const lines = msg.reasoning_content
      .split("\n")
      .map((l) => l.trim())
      .filter((l) => l.length > 0);
    text = lines[lines.length - 1];
  }
  if (!text) return null;
  // Strip optional surrounding quotes / markdown if Qwen disobeys
  return text.replace(/^["'`]+|["'`]+$/g, "").trim();
}
