import type { Config } from "../config.ts";
import { ClaudeScorer } from "./claude-scorer.ts";
import { LMStudioScorer } from "./lmstudio-scorer.ts";
import { OllamaScorer } from "./ollama-scorer.ts";
import type { Scorer } from "./types.ts";

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
