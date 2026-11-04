import { join } from "node:path";

/**
 * Sprachmodell NUR für die KI-Zusammenfassungen der News. Videos werden nicht
 * bewertet — der Feed besteht aus den abonnierten Kanälen.
 * "off" = News ohne KI-Zusammenfassung.
 */
export type LLMProvider = "claude" | "ollama" | "lmstudio" | "off";

export interface Config {
  port: number;
  dataDir: string;
  videoDir: string;
  mangaDir: string;
  coverDir: string;
  dbPath: string;
  dailyBudget: number;
  diskLimitBytes: number;
  llmProvider: LLMProvider;
  claude: { apiKey: string; model: string };
  ollama: { baseUrl: string; model: string };
  lmstudio: { baseUrl: string; model: string };
  /**
   * Optional bearer token. When set (HIKARI_AUTH_TOKEN), mutating routes require
   * it (see api/auth.ts). null = open, the single-user localhost default.
   */
  authToken: string | null;
  /**
   * Comma-separated CORS origin allowlist (HIKARI_CORS_ORIGINS). Empty = CORS
   * disabled (no JSON Access-Control headers), the localhost/native-client
   * default. A browser client would set e.g. "https://app.example.com".
   */
  corsOrigins: string[];
}

/**
 * Parses a numeric env var, throwing on a malformed value instead of letting
 * NaN flow into the config. `Number("abc")` is NaN, and NaN silently breaks
 * port binding / budget math; fail fast at startup instead.
 */
function num(name: string, raw: string | undefined, fallback: number): number {
  if (raw === undefined) return fallback;
  const n = Number(raw);
  if (!Number.isFinite(n)) {
    throw new Error(`Config: ${name} must be a finite number, got "${raw}"`);
  }
  return n;
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env): Config {
  const home = env.HOME ?? env.USERPROFILE ?? "/tmp";
  const dataDir = (env.HIKARI_DATA_DIR ?? join(home, ".hikari")).replace(/^~/, home);
  const llmProvider = (env.LLM_PROVIDER ?? "lmstudio") as LLMProvider;

  if (llmProvider === "claude" && !env.ANTHROPIC_API_KEY) {
    throw new Error("ANTHROPIC_API_KEY is required when LLM_PROVIDER=claude");
  }

  const authTokenRaw = env.HIKARI_AUTH_TOKEN?.trim();
  const corsOrigins = (env.HIKARI_CORS_ORIGINS ?? "")
    .split(",")
    .map((o) => o.trim())
    .filter((o) => o !== "");

  return {
    port: num("PORT", env.PORT, 3939),
    dataDir,
    videoDir: join(dataDir, "videos"),
    mangaDir: join(dataDir, "manga"),
    coverDir: join(dataDir, "covers"),
    dbPath: join(dataDir, "hikari.db"),
    dailyBudget: num("DAILY_BUDGET", env.DAILY_BUDGET, 15),
    diskLimitBytes: num("DISK_LIMIT_GB", env.DISK_LIMIT_GB, 10) * 1024 ** 3,
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
    authToken: authTokenRaw ? authTokenRaw : null,
    corsOrigins,
  };
}
