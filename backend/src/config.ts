import { z } from "zod";
import { config as loadEnv } from "dotenv";

loadEnv();

// `.finite()` on the numeric fields is the point: env vars arrive as strings and
// `Number("abc")` is NaN, which `z.number()` alone would happily accept (NaN is
// a number). Parsing through this schema turns a malformed PORT / DAILY_BUDGET
// into a startup error instead of a silently-broken server.
const ConfigSchema = z
  .object({
    llmProvider: z.enum(["claude", "ollama", "lmstudio"]),
    claude: z.object({
      apiKey: z.string(), // may be "" when running a local provider
      model: z.string().min(1),
    }),
    ollama: z.object({
      baseUrl: z.string().url(),
      model: z.string().min(1),
    }),
    lmstudio: z.object({
      baseUrl: z.string().url(),
      model: z.string().min(1),
    }),
    port: z.number().finite().int().positive(),
    videoDir: z.string().min(1),
    mangaDir: z.string().min(1),
    coverDir: z.string().min(1),
    dbPath: z.string().min(1),
    dailyBudget: z.number().finite().int().positive(),
    diskLimitBytes: z.number().finite().positive(),
    // Optional bearer token. When set, mutating routes require it (see auth
    // hook). null/"" = open (single-user localhost default).
    authToken: z.string().nullable(),
    clipper: z.object({
      scheduleStartHour: z.number().finite().int().min(0).max(23),
      scheduleEndHour: z.number().finite().int().min(0).max(23),
    }),
  })
  .readonly();

export type Config = z.infer<typeof ConfigSchema>;
export type ClipperConfig = Config["clipper"];

export function loadConfig(): Config {
  const env = process.env;
  const authTokenRaw = env.HIKARI_AUTH_TOKEN?.trim();

  const cfg = {
    llmProvider: (env.LLM_PROVIDER ?? "claude") as "claude" | "ollama" | "lmstudio",
    claude: {
      apiKey: env.ANTHROPIC_API_KEY ?? "",
      model: env.CLAUDE_MODEL ?? "claude-3-5-sonnet-20241022",
    },
    ollama: {
      baseUrl: env.OLLAMA_BASE_URL ?? "http://localhost:11434",
      model: env.OLLAMA_MODEL ?? "llama3.2",
    },
    lmstudio: {
      baseUrl: env.LMSTUDIO_BASE_URL ?? "http://localhost:1234",
      model: env.LMSTUDIO_MODEL ?? "qwen2.5",
    },
    port: Number(env.PORT ?? 8080),
    videoDir: env.VIDEO_DIR ?? "./data/videos",
    mangaDir: env.MANGA_DIR ?? "./data/manga",
    coverDir: env.COVER_DIR ?? "./data/covers",
    dbPath: env.DB_PATH ?? "./data/hikari.db",
    dailyBudget: Number(env.DAILY_BUDGET ?? 50),
    diskLimitBytes: Number(env.DISK_LIMIT_BYTES ?? 50 * 1024 * 1024 * 1024),
    authToken: authTokenRaw ? authTokenRaw : null,
    clipper: {
      scheduleStartHour: Number(env.CLIPPER_SCHEDULE_START_HOUR ?? 8),
      scheduleEndHour: Number(env.CLIPPER_SCHEDULE_END_HOUR ?? 2),
    },
  };

  return ConfigSchema.parse(cfg);
}
