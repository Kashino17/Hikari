import { execa } from "execa";

export class YtDlpError extends Error {
  constructor(
    message: string,
    public readonly stderr: string,
    public readonly exitCode: number | undefined,
  ) {
    super(message);
    this.name = "YtDlpError";
  }
}

export interface YtDlpResult {
  stdout: string;
  stderr: string;
}

export interface RunYtDlpOptions {
  timeoutMs?: number;
  /** Retries for a TRANSIENT failure (default 2 → up to 3 attempts total). */
  maxRetries?: number;
  /** Injectable delay so tests don't actually wait. */
  sleep?: (ms: number) => Promise<void>;
}

// Bevorzugter Player-Client fürs Stream-URL-Auflösen: WEB_EMBEDDED_PLAYER-URLs
// erlauben (mit Referer/Origin-Headern) als einzige volle Range-Zugriffe —
// googlevideo kappt URLs aller anderen Clients seit 08/2026 nach ~768 KiB (403).
export const YT_EMBEDDED_CLIENT_ARGS = [
  "--extractor-args",
  "youtube:player_client=web_embedded",
];

/**
 * Stream-URL-Auflösung: erst mit web_embedded-Client (voll rangebare URLs),
 * bei Fehlschlag (z. B. Embed vom Uploader deaktiviert) mit den
 * Default-Clients. Nimmt den Runner als Parameter, damit injizierte
 * yt-dlp-Mocks (Tests) genauso funktionieren wie das echte Binary.
 */
export async function runPreferEmbedded(
  run: (args: string[], opts?: RunYtDlpOptions) => Promise<YtDlpResult>,
  args: string[],
  opts?: RunYtDlpOptions,
): Promise<YtDlpResult> {
  try {
    const result = await run([...YT_EMBEDDED_CLIENT_ARGS, ...args], opts);
    if (result.stdout.trim()) return result;
  } catch {
    // weiter mit Default-Clients
  }
  return run(args, opts);
}

const DEFAULT_TIMEOUT_MS = 120_000;
const defaultSleep = (ms: number): Promise<void> =>
  new Promise((resolve) => setTimeout(resolve, ms));

function errorText(err: unknown): string {
  if (err && typeof err === "object") {
    const e = err as { message?: unknown; stderr?: unknown; shortMessage?: unknown };
    return [e.shortMessage, e.message, e.stderr]
      .filter((x) => typeof x === "string")
      .join(" ")
      .toLowerCase();
  }
  return String(err).toLowerCase();
}

/** YouTube/network throttling — deserves a LONGER backoff before retrying. */
export function isRateLimited(err: unknown): boolean {
  const msg = errorText(err);
  return (
    msg.includes("429") ||
    msg.includes("too many requests") ||
    msg.includes("rate limit") ||
    msg.includes("rate-limit")
  );
}

/**
 * Whether a yt-dlp failure is worth retrying. Rate-limits, timeouts, and
 * transient network errors are; an age-gate, private/removed video, or bad URL
 * is NOT (retrying just wastes calls and risks a harder throttle).
 */
export function isRetryableYtDlpError(err: unknown): boolean {
  if (isRateLimited(err)) return true;
  const msg = errorText(err);
  return (
    msg.includes("timed out") ||
    msg.includes("timeout") ||
    msg.includes("etimedout") ||
    msg.includes("econnreset") ||
    msg.includes("econnrefused") ||
    msg.includes("getaddrinfo") ||
    msg.includes("temporary failure") ||
    msg.includes("network") ||
    msg.includes("http error 5") ||
    msg.includes("503") ||
    msg.includes("502")
  );
}

/**
 * Runs the yt-dlp binary with bounded exponential backoff for TRANSIENT
 * failures. Signature is unchanged (args, opts) so every caller — metadata,
 * transcript, download, channel resolve/scan/search — gets resilience for free.
 * Non-transient failures (private video, age-gate, bad URL) throw immediately
 * as YtDlpError; no wasted retries, no harder throttle.
 */
export async function runYtDlp(
  args: string[],
  opts: RunYtDlpOptions = {},
): Promise<YtDlpResult> {
  const { timeoutMs = DEFAULT_TIMEOUT_MS, maxRetries = 2, sleep = defaultSleep } = opts;

  let lastErr: unknown;
  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    try {
      const result = await execa("yt-dlp", args, { timeout: timeoutMs });
      return { stdout: result.stdout, stderr: result.stderr };
    } catch (err) {
      lastErr = err;
      if (attempt < maxRetries && isRetryableYtDlpError(err)) {
        // Rate-limits back off much harder (5s, 10s, …) than ordinary transient
        // errors (1s, 2s, …) to give YouTube's throttle time to clear.
        const base = isRateLimited(err) ? 5_000 : 1_000;
        await sleep(base * 2 ** attempt);
        continue;
      }
      const e = err as { stderr?: string; exitCode?: number; shortMessage?: string };
      throw new YtDlpError(
        e.stderr ?? e.shortMessage ?? "yt-dlp failed: unknown error",
        e.stderr ?? "",
        e.exitCode,
      );
    }
  }
  // Unreachable (loop either returns or throws), but satisfies the type checker.
  throw lastErr;
}
