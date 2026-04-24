import { describe, expect, it, vi } from "vitest";
import type { ExecaReturnValue } from "execa";
import { runYtDlp } from "./client.js";

vi.mock("execa", () => ({
  execa: vi.fn(),
}));

describe("runYtDlp", () => {
  it("calls yt-dlp with given args and returns stdout", async () => {
    const { execa } = await import("execa");
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
      expect.objectContaining({ timeout: expect.any(Number) })
    );
  });

  it("throws YtDlpError with stderr when exit code is non-zero", async () => {
    const { execa } = await import("execa");
    vi.mocked(execa).mockRejectedValue({
      stderr: "ERROR: Video unavailable",
      exitCode: 1,
      shortMessage: "Command failed",
    });

    await expect(runYtDlp(["--dump-json", "bad-url"])).rejects.toThrow(/Video unavailable/);
  });
});
