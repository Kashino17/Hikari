import { z } from "zod";
import type { Score } from "./types.js";

/**
 * Runtime schema for an LLM-produced Score. Local scorers (Ollama, LM Studio)
 * return free-form JSON text that was previously `JSON.parse`d and cast straight
 * to `Score` — so a malformed payload (missing field, NaN, out-of-range number,
 * unknown category) flowed unchecked into `decide()`, where `NaN < 60` is
 * `false` and `NaN > 4` is `false`, silently APPROVING junk. This schema closes
 * that hole: it coerces numbers, clamps them to their valid ranges, and rejects
 * anything non-finite or structurally wrong.
 */
export const ScoreSchema = z.object({
  overallScore: z.coerce.number().finite().min(0).max(100),
  category: z.enum([
    "science",
    "tech",
    "philosophy",
    "history",
    "math",
    "art",
    "language",
    "society",
    "other",
  ]),
  clickbaitRisk: z.coerce.number().finite().min(0).max(10),
  educationalValue: z.coerce.number().finite().min(0).max(10),
  emotionalManipulation: z.coerce.number().finite().min(0).max(10),
  reasoning: z.string().min(1).max(2000),
});

/**
 * Parse + validate raw JSON text from a local LLM into a Score. Throws a
 * descriptive Error on malformed JSON or schema violation so the caller's
 * per-video try/catch can reject the video instead of approving garbage.
 */
export function parseScore(content: string): Score {
  let json: unknown;
  try {
    json = JSON.parse(content);
  } catch {
    throw new Error(`scorer returned non-JSON content: ${content.slice(0, 120)}`);
  }
  const result = ScoreSchema.safeParse(json);
  if (!result.success) {
    throw new Error(`scorer returned invalid score: ${result.error.message}`);
  }
  return result.data;
}

/**
 * Validate an already-structured Score object (e.g. Claude tool_use input,
 * which is schema-enforced server-side but still untyped at runtime). Throws on
 * violation. Returns the clamped, finite Score.
 */
export function validateScore(input: unknown): Score {
  const result = ScoreSchema.safeParse(input);
  if (!result.success) {
    throw new Error(`invalid score object: ${result.error.message}`);
  }
  return result.data;
}
