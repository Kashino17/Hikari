import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { downloadVideo } from "./worker.js";

vi.mock("../yt-dlp/client.js", () => ({
  runYtDlp: vi.fn(),
  YtDlpError: class extends Error {},
}));

describe("downloadVideo", () => {
  beforeEach(() => vi.clearAllMocks());

  it("invokes yt-dlp with expected format args and returns file metadata", async () => {
    const { runYtDlp } = await import("../yt-dlp/client.js");
    const dir = mkdtempSync(join(tmpdir(), "hikari-test-"));
    const videoId = "abc123";
    const path = join(dir, `${videoId}.mp4`);

    vi.mocked(runYtDlp).mockImplementation(async () => {
      writeFileSync(path, Buffer.alloc(1024, 0xff));
      return { stdout: "", stderr: "" };
    });

    const result = await downloadVideo({ videoId, outDir: dir });

    expect(result.filePath).toBe(path);
    expect(result.fileSizeBytes).toBe(1024);
    expect(runYtDlp).toHaveBeenCalledWith(
      expect.arrayContaining([
        "-f",
        "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[height<=720]",
        "--merge-output-format",
        "mp4",
        "-o",
        join(dir, "%(id)s.%(ext)s"),
      ]),
      expect.any(Object),
    );
  });

  it("throws when download fails to create the expected file", async () => {
    const { runYtDlp } = await import("../yt-dlp/client.js");
    const dir = mkdtempSync(join(tmpdir(), "hikari-test-"));
    vi.mocked(runYtDlp).mockResolvedValue({ stdout: "", stderr: "" });

    await expect(downloadVideo({ videoId: "missing", outDir: dir })).rejects.toThrow(/file/);
  });
});
