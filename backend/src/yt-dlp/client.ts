import { execa } from "execa";

export interface YtDlpResult {
  stdout: string;
  stderr: string;
}

export interface RunYtDlpOptions {
  args: string[];
  timeoutMs?: number;
  /** How many times to retry a TRANSIENT failure (default 2 → up to 3 attempts). */
  maxRetries?: number;
  /** Injectable delay, so tests don't actually wait. */
  sleep?: (ms: number) => Promise<void>;
}

const defaultSleep = (ms: number): Promise<void> =>
  new Promise((resolve) => setTimeout(resolve, ms));

function errorMessage(err: unknown): string {
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
  const msg = errorMessage(err);
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
  const msg = errorMessage(err);
  if (isRateLimited(err)) return true;
  return (
    msg.includes("timed out") ||
    msg.includes("timeout") ||
    msg.includes("etimedout") ||
    msg.includes("econnreset") ||
    msg.includes("econnrefused") ||
    msg.includes("temporary failure") ||
    msg.includes("getaddrinfo") ||
    msg.includes("network") ||
    msg.includes("503") ||
    msg.includes("502")
  );
}

/**
 * Thin wrapper around the yt-dlp binary with bounded exponential backoff for
 * transient failures. Keeps spawn logic in one place so metadata, transcript,
 * and download callers share identical resilience. Non-transient failures
 * (private video, age-gate, bad URL) throw immediately — no wasted retries.
 */
export async function runYtDlp(options: RunYtDlpOptions): Promise<YtDlpResult> {
  const { args, timeoutMs = 60_000, maxRetries = 2, sleep = defaultSleep } = options;

  let lastErr: unknown;
  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    try {
      const result = await execa("yt-dlp", args, { timeout: timeoutMs, reject: true });
      return { stdout: result.stdout, stderr: result.stderr };
    } catch (err) {
      lastErr = err;
      if (attempt === maxRetries || !isRetryableYtDlpError(err)) throw err;
      // Rate-limits back off much harder (5s, 10s, …) than ordinary transient
      // errors (1s, 2s, …) to give YouTube's throttle time to clear.
      const base = isRateLimited(err) ? 5_000 : 1_000;
      await sleep(base * 2 ** attempt);
    }
  }
  throw lastErr;
}
