import { describe, expect, it, vi } from "vitest";
import type { ExecaReturnValue } from "execa";
import { runYtDlp, isRateLimited, isRetryableYtDlpError } from "./client.js";

vi.mock("execa", () => ({
  execa: vi.fn(),
}));

const noSleep = () => Promise.resolve();

describe("runYtDlp", () => {
  it("calls yt-dlp with given args and returns stdout", async () => {
    const { execa } = await import("execa");
    vi.mocked(execa).mockReset();
    vi.mocked(execa).mockResolvedValue({
      stdout: '{"id":"abc","title":"test"}',
      stderr: "",
      exitCode: 0,
    } as unknown as ExecaReturnValue);

    const result = await runYtDlp(["--dump-json", "https://youtube.com/watch?v=abc"]);
    expect(result.stdout).toBe('{"id":"abc","title":"test"}');
    expect(execa).toHaveBeenCalledWith(
      "yt-dlp",
      ["--dump-json", "https://youtube.com/watch?v=abc"],
      expect.objectContaining({ timeout: expect.any(Number) }),
    );
  });

  it("throws YtDlpError with stderr when exit code is non-zero", async () => {
    const { execa } = await import("execa");
    vi.mocked(execa).mockReset();
    vi.mocked(execa).mockRejectedValue({
      stderr: "ERROR: Video unavailable",
      exitCode: 1,
      shortMessage: "Command failed",
    });

    await expect(runYtDlp(["--dump-json", "bad-url"], { sleep: noSleep })).rejects.toThrow(
      /Video unavailable/,
    );
  });

  it("does NOT retry a non-transient (content) error", async () => {
    const { execa } = await import("execa");
    vi.mocked(execa).mockReset();
    vi.mocked(execa).mockRejectedValue({ stderr: "ERROR: Private video", exitCode: 1 });
    await expect(runYtDlp(["x"], { sleep: noSleep })).rejects.toThrow(/Private video/);
    expect(execa).toHaveBeenCalledTimes(1);
  });

  it("retries a rate-limit error then succeeds", async () => {
    const { execa } = await import("execa");
    vi.mocked(execa).mockReset();
    vi.mocked(execa)
      .mockRejectedValueOnce({ stderr: "HTTP Error 429: Too Many Requests", exitCode: 1 })
      .mockResolvedValueOnce({ stdout: "ok", stderr: "" } as unknown as ExecaReturnValue);
    const result = await runYtDlp(["x"], { sleep: noSleep });
    expect(result.stdout).toBe("ok");
    expect(execa).toHaveBeenCalledTimes(2);
  });

  it("gives up after maxRetries transient failures, throwing YtDlpError", async () => {
    const { execa } = await import("execa");
    vi.mocked(execa).mockReset();
    vi.mocked(execa).mockRejectedValue({ stderr: "Connection timed out", exitCode: 1 });
    await expect(runYtDlp(["x"], { maxRetries: 2, sleep: noSleep })).rejects.toThrow(/timed out/);
    expect(execa).toHaveBeenCalledTimes(3); // 1 initial + 2 retries
  });
});

describe("yt-dlp error classification", () => {
  it("detects rate-limit signatures", () => {
    expect(isRateLimited({ stderr: "HTTP Error 429" })).toBe(true);
    expect(isRateLimited(new Error("Too Many Requests"))).toBe(true);
    expect(isRateLimited(new Error("Private video"))).toBe(false);
  });

  it("treats network/timeout as retryable, content errors as terminal", () => {
    expect(isRetryableYtDlpError(new Error("ETIMEDOUT"))).toBe(true);
    expect(isRetryableYtDlpError({ stderr: "getaddrinfo ENOTFOUND" })).toBe(true);
    expect(isRetryableYtDlpError(new Error("Video unavailable"))).toBe(false);
    expect(isRetryableYtDlpError(new Error("age-restricted"))).toBe(false);
  });
});
