import { describe, expect, it, vi } from "vitest";
import { runYtDlp, isRetryableYtDlpError, isRateLimited } from "./client.js";

vi.mock("execa", () => ({
  execa: vi.fn(),
}));

import { execa } from "execa";

const noSleep = () => Promise.resolve();

describe("runYtDlp", () => {
  it("passes args through to execa and returns stdout/stderr", async () => {
    const mockExeca = vi.mocked(execa);
    mockExeca.mockResolvedValue({ stdout: "out", stderr: "err" } as never);
    const result = await runYtDlp({ args: ["--version"] });
    expect(result.stdout).toBe("out");
    expect(result.stderr).toBe("err");
  });

  it("throws when execa rejects with a non-transient error (no retry)", async () => {
    const mockExeca = vi.mocked(execa);
    mockExeca.mockReset();
    mockExeca.mockRejectedValue(new Error("Private video. Sign in"));
    await expect(runYtDlp({ args: ["x"], sleep: noSleep })).rejects.toThrow("Private video");
    // Non-transient → attempted exactly once.
    expect(mockExeca).toHaveBeenCalledTimes(1);
  });

  it("retries a rate-limit error then succeeds", async () => {
    const mockExeca = vi.mocked(execa);
    mockExeca.mockReset();
    mockExeca
      .mockRejectedValueOnce(new Error("HTTP Error 429: Too Many Requests"))
      .mockResolvedValueOnce({ stdout: "ok", stderr: "" } as never);
    const result = await runYtDlp({ args: ["x"], sleep: noSleep });
    expect(result.stdout).toBe("ok");
    expect(mockExeca).toHaveBeenCalledTimes(2);
  });

  it("gives up after maxRetries transient failures", async () => {
    const mockExeca = vi.mocked(execa);
    mockExeca.mockReset();
    mockExeca.mockRejectedValue(new Error("Connection timed out"));
    await expect(
      runYtDlp({ args: ["x"], maxRetries: 2, sleep: noSleep }),
    ).rejects.toThrow("timed out");
    // 1 initial + 2 retries.
    expect(mockExeca).toHaveBeenCalledTimes(3);
  });
});

describe("error classification", () => {
  it("detects rate-limit signatures", () => {
    expect(isRateLimited(new Error("HTTP Error 429"))).toBe(true);
    expect(isRateLimited(new Error("Too Many Requests"))).toBe(true);
    expect(isRateLimited(new Error("Private video"))).toBe(false);
  });

  it("treats network/timeout as retryable but content errors as terminal", () => {
    expect(isRetryableYtDlpError(new Error("ETIMEDOUT"))).toBe(true);
    expect(isRetryableYtDlpError(new Error("getaddrinfo ENOTFOUND"))).toBe(true);
    expect(isRetryableYtDlpError(new Error("Video unavailable"))).toBe(false);
    expect(isRetryableYtDlpError(new Error("This video is age-restricted"))).toBe(false);
  });

  it("reads execa-style stderr/shortMessage fields", () => {
    expect(isRetryableYtDlpError({ shortMessage: "Command failed", stderr: "HTTP Error 429" })).toBe(
      true,
    );
  });
});
