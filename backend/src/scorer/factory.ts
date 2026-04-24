import type { Config } from "../config.js";
import { ClaudeScorer } from "./claude-scorer.js";
import { OllamaScorer } from "./ollama-scorer.js";
import type { Scorer } from "./types.js";

export function createScorer(cfg: Config): Scorer {
  if (cfg.llmProvider === "claude") {
    return new ClaudeScorer({ apiKey: cfg.claude.apiKey, model: cfg.claude.model });
  }
  return new OllamaScorer({ baseUrl: cfg.ollama.baseUrl, model: cfg.ollama.model });
}
