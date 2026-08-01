import Anthropic from "@anthropic-ai/sdk";
import { z } from "zod";
import type { Config } from "../config.js";
import type { NewsLang } from "./google-news.js";

export interface SummaryInput {
  title: string;
  description: string;
  leadText?: string;
}

export interface ItemSummary {
  headline: string;
  summary: string;
}

export interface SummarizeDeps {
  fetchImpl?: typeof fetch;
}

const SummaryArraySchema = z.array(
  z.object({
    i: z.coerce.number().int().min(0),
    headline: z.string().min(1),
    summary: z.string().min(1),
  }),
);

function buildSystemPrompt(lang: NewsLang): string {
  if (lang === "en") {
    return [
      "You are a news editor writing a daily briefing.",
      "For each news item below, write a punchy headline (max 12 words) and a summary",
      "(max 55 words, roughly 20 seconds of reading time). Be factual, no opinion,",
      "write in English. Answer ONLY with a JSON array:",
      '[{"i": 0, "headline": "...", "summary": "..."}]',
    ].join(" ");
  }
  return [
    "Du bist Nachrichtenredakteur und schreibst einen täglichen Tagesbericht.",
    "Schreibe für jede Nachricht unten eine prägnante Schlagzeile (max. 12 Wörter)",
    "und eine Zusammenfassung (max. 55 Wörter, ca. 20 Sekunden Lesezeit).",
    "Sachlich, ohne Meinung, auf Deutsch. Antworte NUR mit einem JSON-Array:",
    '[{"i": 0, "headline": "...", "summary": "..."}]',
  ].join(" ");
}

function buildUserMessage(items: SummaryInput[]): string {
  return items
    .map((item, i) => {
      const teaser = (item.leadText ?? item.description).slice(0, 800);
      return `${i}) ${item.title} — ${teaser}`;
    })
    .join("\n\n");
}

/** Extract the first JSON array from raw LLM output (tolerates prose around it). */
function extractJsonArray(raw: string): unknown {
  const start = raw.indexOf("[");
  const end = raw.lastIndexOf("]");
  if (start === -1 || end === -1 || end <= start) {
    throw new Error("summarizer returned no JSON array");
  }
  return JSON.parse(raw.slice(start, end + 1));
}

async function callLmStudio(
  cfg: Config,
  fetchImpl: typeof fetch,
  system: string,
  user: string,
): Promise<string> {
  const res = await fetchImpl(`${cfg.lmstudio.baseUrl}/v1/chat/completions`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      model: cfg.lmstudio.model,
      messages: [
        { role: "system", content: system },
        { role: "user", content: user },
      ],
      response_format: { type: "json_object" },
      temperature: 0.3,
      stream: false,
    }),
  });
  if (!res.ok) throw new Error(`LM Studio request failed: ${res.status}`);
  const body = (await res.json()) as {
    choices: { message: { content: string; reasoning_content?: string } }[];
  };
  const msg = body.choices[0]?.message;
  // Reasoning models may put the answer in reasoning_content (see lmstudio-scorer)
  const raw = msg?.content?.trim() ? msg.content : (msg?.reasoning_content ?? "");
  if (!raw.trim()) throw new Error("LM Studio returned empty content");
  return raw;
}

async function callOllama(
  cfg: Config,
  fetchImpl: typeof fetch,
  system: string,
  user: string,
): Promise<string> {
  const res = await fetchImpl(`${cfg.ollama.baseUrl}/api/chat`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      model: cfg.ollama.model,
      messages: [
        { role: "system", content: system },
        { role: "user", content: user },
      ],
      format: "json",
      stream: false,
    }),
  });
  if (!res.ok) throw new Error(`Ollama request failed: ${res.status}`);
  const body = (await res.json()) as { message: { content: string } };
  return body.message.content;
}

const SUMMARY_TOOL = {
  name: "record_summaries",
  description: "Record headline and summary for each news item.",
  input_schema: {
    type: "object" as const,
    required: ["items"],
    properties: {
      items: {
        type: "array",
        items: {
          type: "object",
          required: ["i", "headline", "summary"],
          properties: {
            i: { type: "integer" },
            headline: { type: "string" },
            summary: { type: "string" },
          },
        },
      },
    },
  },
};

async function callClaude(cfg: Config, system: string, user: string): Promise<string> {
  const client = new Anthropic({ apiKey: cfg.claude.apiKey });
  const response = await client.messages.create({
    model: cfg.claude.model,
    max_tokens: 4096,
    system,
    tools: [SUMMARY_TOOL as Anthropic.Messages.Tool],
    tool_choice: { type: "tool", name: "record_summaries" },
    messages: [{ role: "user", content: user }],
  });
  const block = response.content.find((b) => b.type === "tool_use");
  if (!block || block.type !== "tool_use") {
    throw new Error("Claude did not return a tool_use block");
  }
  return JSON.stringify((block.input as { items: unknown }).items);
}

function fallbackSummary(item: SummaryInput): ItemSummary {
  return { headline: item.title, summary: item.description.slice(0, 300) };
}

/**
 * One batched LLM call for all items. Never throws: invalid/missing entries
 * fall back per item, a total LLM failure falls back for all items
 * (headline = title, summary = description truncated to 300 chars).
 */
export async function summarizeItems(
  items: SummaryInput[],
  cfg: Config,
  deps: SummarizeDeps = {},
  lang: NewsLang = "de",
): Promise<ItemSummary[]> {
  if (items.length === 0) return [];
  const fetchImpl = deps.fetchImpl ?? fetch;
  const system = buildSystemPrompt(lang);
  const user = buildUserMessage(items);

  try {
    let raw: string;
    if (cfg.llmProvider === "claude") {
      raw = await callClaude(cfg, system, user);
    } else if (cfg.llmProvider === "ollama") {
      raw = await callOllama(cfg, fetchImpl, system, user);
    } else {
      raw = await callLmStudio(cfg, fetchImpl, system, user);
    }

    const parsed = SummaryArraySchema.safeParse(extractJsonArray(raw));
    if (!parsed.success) throw new Error(`invalid summary payload: ${parsed.error.message}`);

    const byIndex = new Map(parsed.data.map((s) => [s.i, s]));
    return items.map((item, i) => {
      const hit = byIndex.get(i);
      return hit ? { headline: hit.headline, summary: hit.summary } : fallbackSummary(item);
    });
  } catch {
    return items.map(fallbackSummary);
  }
}
