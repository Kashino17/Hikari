import type { Caption } from "./transcriber.js";
import { QwenNetworkError } from "./qwen-analyzer.js";

export interface SummarizerConfig {
  baseUrl: string;
  model: string;
  fetchFn?: typeof fetch;
}

const SYSTEM_PROMPT = `Du bist ein Video-Clip-Kontextualisierer. Du bekommst ein Transkript eines kurzen Video-Clips (30-90 Sekunden) der aus einem längeren Video extrahiert wurde. Schreib in 1-3 prägnanten kurzen Sätzen worum es in DIESEM Clip geht — Setup + Kernpunkt — sodass jemand der sonst nichts vom Originalvideo weiß, sofort folgen kann.

REGELN:
- 1 bis 3 Sätze, jeder kurz und konkret
- Erkläre das THEMA und was der Sprecher AUSSAGT, nicht nur was er macht
- Keine Floskeln wie "In diesem Clip..." oder "Der Sprecher erklärt..." — geh direkt zum Inhalt
- Schreib in der Sprache des Transkripts (vermutlich Deutsch)
- Wenn das Transkript fragmentarisch oder nicht eindeutig ist: das Beste mit den Worten machen, kein Disclaimer

OUTPUT: nur der Kontext-Text. Keine Markdown, keine Anführungszeichen, keine Erklärungen drumherum.`;

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

  const fetchFn = config.fetchFn ?? fetch;
  const body = {
    model: config.model,
    messages: [
      { role: "system", content: SYSTEM_PROMPT },
      { role: "user", content: `Transkript:\n${transcript}` },
    ],
    temperature: 0.3,
    // Qwen 3.6 is a reasoning model and dumps thinking into reasoning_content
    // before the actual answer lands in content. ~5k reasoning tokens is
    // typical for a short summary, so we leave plenty of headroom.
    max_tokens: 2000,
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
  const json = (await res.json()) as { choices: Array<{ message: { content: string } }> };
  const text = json.choices[0]?.message?.content?.trim();
  if (!text) return null;
  // Strip optional surrounding quotes / markdown if Qwen disobeys
  return text.replace(/^["'`]+|["'`]+$/g, "").trim();
}
