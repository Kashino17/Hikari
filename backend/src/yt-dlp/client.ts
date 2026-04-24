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

const DEFAULT_TIMEOUT_MS = 120_000;

export async function runYtDlp(
  args: string[],
  opts: { timeoutMs?: number } = {},
): Promise<YtDlpResult> {
  try {
    const result = await execa("yt-dlp", args, {
      timeout: opts.timeoutMs ?? DEFAULT_TIMEOUT_MS,
    });
    return { stdout: result.stdout, stderr: result.stderr };
  } catch (err) {
    const e = err as { stderr?: string; exitCode?: number; shortMessage?: string };
    throw new YtDlpError(
      e.stderr ?? e.shortMessage ?? "yt-dlp failed: unknown error",
      e.stderr ?? "",
      e.exitCode,
    );
  }
}
