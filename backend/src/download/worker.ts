import { existsSync, statSync } from "node:fs";
import { join } from "node:path";
import { runYtDlp } from "../yt-dlp/client.js";

export interface DownloadResult {
  filePath: string;
  fileSizeBytes: number;
}

export async function downloadVideo(opts: {
  videoId: string;
  outDir: string;
  timeoutMs?: number;
}): Promise<DownloadResult> {
  const outTemplate = join(opts.outDir, "%(id)s.%(ext)s");
  await runYtDlp(
    [
      "-f",
      "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[height<=720]",
      "--merge-output-format",
      "mp4",
      "-o",
      outTemplate,
      "--no-warnings",
      `https://www.youtube.com/watch?v=${opts.videoId}`,
    ],
    { timeoutMs: opts.timeoutMs ?? 10 * 60_000 },
  );

  const filePath = join(opts.outDir, `${opts.videoId}.mp4`);
  if (!existsSync(filePath)) {
    throw new Error(`Download completed but file not found at ${filePath}`);
  }
  return { filePath, fileSizeBytes: statSync(filePath).size };
}
