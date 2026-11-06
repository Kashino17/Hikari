import { z } from "zod";

export interface VoiceSummary {
  /** Kurzer Titel, worum es geht. */
  title: string;
  /** Die Zusammenfassung in wenigen Sätzen. */
  summary: string;
  /** Kernpunkte als Stichpunkte. */
  points: string[];
  /** Was ich (der Empfänger) tun soll. */
  todos: string[];
  /** Termine, Fristen, Beträge — alles mit Datum oder Zahl. */
  dates: string[];
  /** Offene Fragen an mich, auf die eine Antwort erwartet wird. */
  questions: string[];
}

export interface SummarizerOptions {
  baseUrl: string;
  model: string;
  /** LM Studio entlädt das Modell nach so vielen Sekunden ohne Anfrage. */
  ttlSeconds: number;
  fetchImpl?: typeof fetch;
  /** Ab dieser Länge (Zeichen) wird in Abschnitten zusammengefasst. */
  chunkChars?: number;
}

export const voiceSummarySchema = z.object({
  title: z.string().trim().min(1).max(120),
  summary: z.string().trim().min(1).max(3000),
  points: z.array(z.string().trim().min(1).max(400)).max(15),
  todos: z.array(z.string().trim().min(1).max(400)).max(15),
  dates: z.array(z.string().trim().min(1).max(300)).max(15),
  questions: z.array(z.string().trim().min(1).max(400)).max(10),
});

const list = (description: string) => ({
  type: "array",
  maxItems: 12,
  items: { type: "string", minLength: 1 },
  description,
});

const summaryJsonSchema = {
  type: "object",
  additionalProperties: false,
  required: ["title", "summary", "points", "todos", "dates", "questions"],
  properties: {
    title: { type: "string", minLength: 1, description: "3 bis 8 Wörter" },
    summary: { type: "string", minLength: 1, description: "3 bis 6 Sätze" },
    points: list("Kernpunkte als kurze Stichpunkte"),
    todos: list("Konkrete Aufgaben für den Empfänger, leer wenn keine"),
    dates: list("Termine, Fristen, Beträge mit Kontext, leer wenn keine"),
    questions: list("Fragen an den Empfänger, die eine Antwort brauchen, leer wenn keine"),
  },
};

const SYSTEM = `Du bekommst das automatische Transkript von WhatsApp-Sprachnachrichten, die ein Geschäftspartner an mich geschickt hat.
Mehrere Nachrichten stehen in ihrer Reihenfolge hintereinander, jeweils mit Überschrift.
Fasse auf Deutsch zusammen, sachlich und konkret, egal in welcher Sprache gesprochen wurde.
- title: worum es geht, 3 bis 8 Wörter.
- summary: das Wichtigste in 3 bis 6 Sätzen, so dass ich die Nachrichten nicht mehr anhören muss.
- points: die Kernpunkte als kurze Stichpunkte, in sinnvoller Reihenfolge.
- todos: was ich konkret tun soll ("Kalkulation bis Freitag schicken"). Nur was wirklich gesagt wurde.
- dates: jeder Termin, jede Frist, jeder Betrag, jede Zahl mit Kontext ("Dienstag 10 Uhr: Termin mit Kunde München").
- questions: Fragen, auf die er eine Antwort von mir erwartet.
Erfinde nichts. Das Transkript kann Hörfehler enthalten: bei offensichtlichen Fehlern das Gemeinte nehmen, bei Unklarem lieber weglassen.
Leere Listen sind richtig, wenn nichts davon vorkommt. Antworte NUR mit dem JSON-Objekt.`;

const CHUNK_SYSTEM = `Du bekommst einen Abschnitt aus dem automatischen Transkript von WhatsApp-Sprachnachrichten eines Geschäftspartners.
Schreib auf Deutsch dichte Notizen als Stichpunkte: alle Aussagen, Bitten, Aufgaben, Fragen, Termine, Fristen, Beträge und Namen.
Nichts erfinden, nichts weglassen, was später wichtig sein könnte. Nur die Stichpunkte, keine Einleitung.`;

/**
 * Teilt langen Text an Satzgrenzen in Stücke von höchstens `max` Zeichen.
 * Ein einzelner Satz über `max` wird hart geschnitten.
 */
export function chunkText(text: string, max: number): string[] {
  const sentences = text.match(/[^.!?\n]+[.!?]*\s*|\n+/g) ?? [text];
  const chunks: string[] = [];
  let cur = "";
  for (const s of sentences) {
    if ((cur + s).trimEnd().length > max && cur.trim()) {
      chunks.push(cur.trim());
      cur = "";
    }
    if (s.length > max) {
      for (let i = 0; i < s.length; i += max) chunks.push(s.slice(i, i + max).trim());
      continue;
    }
    cur += s;
  }
  if (cur.trim()) chunks.push(cur.trim());
  return chunks.filter((c) => c.length > 0);
}

/** Das erste JSON-Objekt aus der Modellantwort (toleriert Text drumherum). */
export function extractJsonObject(raw: string): unknown {
  const cleaned = raw.replace(/<think>[\s\S]*?<\/think>/g, "");
  const start = cleaned.indexOf("{");
  const end = cleaned.lastIndexOf("}");
  if (start === -1 || end <= start) throw new Error("summary_no_json");
  return JSON.parse(cleaned.slice(start, end + 1));
}

export class VoiceSummarizer {
  private readonly fetchImpl: typeof fetch;
  private readonly chunkChars: number;

  constructor(private readonly opts: SummarizerOptions) {
    this.fetchImpl = opts.fetchImpl ?? fetch;
    // ~14.000 Zeichen ≈ 4.000 Tokens; passt mit Antwort und Denkphase in den
    // 10k-Kontext, mit dem LM Studio das Modell per JIT lädt.
    this.chunkChars = opts.chunkChars ?? 14_000;
  }

  private async chat(
    system: string,
    user: string,
    schema: Record<string, unknown> | null,
    maxTokens: number,
  ): Promise<string> {
    const res = await this.fetchImpl(`${this.opts.baseUrl}/v1/chat/completions`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      signal: AbortSignal.timeout(15 * 60_000),
      body: JSON.stringify({
        model: this.opts.model,
        // JIT-geladene Modelle entlädt LM Studio nach so vielen Sekunden
        // Leerlauf selbst — kein eigener Timer nötig.
        ttl: this.opts.ttlSeconds,
        messages: [
          { role: "system", content: system },
          { role: "user", content: user },
        ],
        temperature: 0.2,
        max_tokens: maxTokens,
        stream: false,
        ...(schema
          ? {
              response_format: {
                type: "json_schema",
                json_schema: { name: "voice_summary", strict: true, schema },
              },
            }
          : {}),
      }),
    });
    if (!res.ok) {
      const detail = await res.text().catch(() => "");
      throw new Error(`lmstudio_${res.status}${detail ? `: ${detail.slice(0, 200)}` : ""}`);
    }
    const body = (await res.json()) as {
      choices?: { message?: { content?: string | null; reasoning_content?: string } }[];
    };
    const msg = body.choices?.[0]?.message;
    const raw = msg?.content?.trim() ? msg.content : (msg?.reasoning_content ?? "");
    if (!raw.trim()) throw new Error("lmstudio_empty");
    return raw;
  }

  async summarize(transcript: string): Promise<VoiceSummary> {
    let material = transcript;
    if (transcript.length > this.chunkChars) {
      // Lang: erst jeden Abschnitt in Notizen verdichten, dann aus allen
      // Notizen die eine Zusammenfassung bauen.
      const parts = chunkText(transcript, this.chunkChars);
      const notes: string[] = [];
      for (const [i, part] of parts.entries()) {
        const n = await this.chat(CHUNK_SYSTEM, part, null, 2500);
        notes.push(
          `Abschnitt ${i + 1} von ${parts.length}:\n${n.replace(/<think>[\s\S]*?<\/think>/g, "").trim()}`,
        );
      }
      material = `Notizen aus den Sprachnachrichten (in Abschnitten verdichtet):\n\n${notes.join("\n\n")}`;
    }
    // Ein zweiter Versuch fängt kaputtes JSON und kurze LM-Studio-Aussetzer ab.
    let lastErr: unknown = null;
    for (let attempt = 0; attempt < 2; attempt++) {
      try {
        const raw = await this.chat(SYSTEM, material, summaryJsonSchema, 4000);
        return voiceSummarySchema.parse(extractJsonObject(raw));
      } catch (err) {
        lastErr = err;
      }
    }
    throw lastErr instanceof Error ? lastErr : new Error("summary_failed");
  }
}
