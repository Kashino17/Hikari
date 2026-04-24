import { join } from "node:path";

export interface Config {
  port: number;
  dataDir: string;
  videoDir: string;
  dbPath: string;
  dailyBudget: number;
  diskLimitBytes: number;
  llmProvider: "claude" | "ollama";
  claude: { apiKey: string; model: string };
  ollama: { baseUrl: string; model: string };
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env): Config {
  const home = env.HOME ?? env.USERPROFILE ?? "/tmp";
  const dataDir = (env.HIKARI_DATA_DIR ?? join(home, ".hikari")).replace(/^~/, home);
  const llmProvider = (env.LLM_PROVIDER ?? "claude") as "claude" | "ollama";

  if (llmProvider === "claude" && !env.ANTHROPIC_API_KEY) {
    throw new Error("ANTHROPIC_API_KEY is required when LLM_PROVIDER=claude");
  }

  return {
    port: Number(env.PORT ?? 3000),
    dataDir,
    videoDir: join(dataDir, "videos"),
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
  };
}
