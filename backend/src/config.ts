import { join } from "node:path";

export type LLMProvider = "claude" | "ollama" | "lmstudio";

export interface Config {
  port: number;
  dataDir: string;
  videoDir: string;
  mangaDir: string;
  dbPath: string;
  dailyBudget: number;
  diskLimitBytes: number;
  llmProvider: LLMProvider;
  claude: { apiKey: string; model: string };
  ollama: { baseUrl: string; model: string };
  lmstudio: { baseUrl: string; model: string };
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env): Config {
  const home = env.HOME ?? env.USERPROFILE ?? "/tmp";
  const dataDir = (env.HIKARI_DATA_DIR ?? join(home, ".hikari")).replace(/^~/, home);
  const llmProvider = (env.LLM_PROVIDER ?? "lmstudio") as LLMProvider;

  if (llmProvider === "claude" && !env.ANTHROPIC_API_KEY) {
    throw new Error("ANTHROPIC_API_KEY is required when LLM_PROVIDER=claude");
  }

  return {
    port: Number(env.PORT ?? 3000),
    dataDir,
    videoDir: join(dataDir, "videos"),
    mangaDir: join(dataDir, "manga"),
    dbPath: join(dataDir, "hikari.db"),
    dailyBudget: Number(env.DAILY_BUDGET ?? 15),
    diskLimitBytes: Number(env.DISK_LIMIT_GB ?? 10) * 1024 ** 3,
    llmProvider,
    claude: {
      apiKey: env.ANTHROPIC_API_KEY ?? "",
      model: env.CLAUDE_MODEL ?? "claude-haiku-4-5",
    },
    ollama: {
      baseUrl: env.OLLAMA_URL ?? "http://localhost:11434",
      model: env.OLLAMA_MODEL ?? "qwen2.5:14b",
    },
    lmstudio: {
      baseUrl: env.LMSTUDIO_URL ?? "http://localhost:1234",
      model: env.LMSTUDIO_MODEL ?? "qwen3-27b",
    },
  };
}
