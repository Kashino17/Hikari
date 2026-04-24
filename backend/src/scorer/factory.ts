import type { Config } from "../config.js";
import { ClaudeScorer } from "./claude-scorer.js";
import { LMStudioScorer } from "./lmstudio-scorer.js";
import { OllamaScorer } from "./ollama-scorer.js";
import type { Scorer } from "./types.js";

export function createScorer(cfg: Config): Scorer {
  switch (cfg.llmProvider) {
    case "claude":
      return new ClaudeScorer({ apiKey: cfg.claude.apiKey, model: cfg.claude.model });
    case "ollama":
      return new OllamaScorer({ baseUrl: cfg.ollama.baseUrl, model: cfg.ollama.model });
    case "lmstudio":
      return new LMStudioScorer({ baseUrl: cfg.lmstudio.baseUrl, model: cfg.lmstudio.model });
  }
}
