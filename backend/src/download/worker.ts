import { createWriteStream, existsSync, statSync } from "node:fs";
import { unlink } from "node:fs/promises";
import { join } from "node:path";
import { Readable } from "node:stream";
import { pipeline } from "node:stream/promises";
import { BROWSER_UA } from "../stream/proxy.js";
import { runYtDlp } from "../yt-dlp/client.js";

export interface DownloadResult {
  filePath: string;
  fileSizeBytes: number;
}

// Muxed MP4, nur progressives HTTPS — identisch zum Streaming-Resolver:
// yt-dlps EIGENER Download bekommt von googlevideo inzwischen 403 (Client-
// Kontext passt nicht); URL aufloesen + selbst mit Browser-UA fetchen ist der
// nachweislich funktionierende Pfad (gleicher Mechanismus wie /stream/video).
const VIDEO_FORMAT =
  "best[height<=720][ext=mp4][vcodec!=none][acodec!=none][protocol=https]/18/best[ext=mp4][protocol=https]";

export async function downloadVideo(opts: {
  videoId: string;
  outDir: string;
  timeoutMs?: number;
  fetchImpl?: typeof fetch;
}): Promise<DownloadResult> {
  const fetchImpl = opts.fetchImpl ?? fetch;
  const result = await runYtDlp(
    [
      "--no-playlist",
      "-f",
      VIDEO_FORMAT,
      "-g",
      `https://www.youtube.com/watch?v=${opts.videoId}`,
    ],
    { timeoutMs: 60_000, maxRetries: 1 },
  );
  const url = result.stdout.trim().split("\n")[0];
  if (!url?.startsWith("http") || url.includes(".m3u8") || url.includes("/manifest/")) {
    throw new Error(`Download failed: no progressive URL for ${opts.videoId}`);
  }

  const res = await fetchImpl(url, {
    headers: { "user-agent": BROWSER_UA },
    signal: AbortSignal.timeout(opts.timeoutMs ?? 10 * 60_000),
  });
  if (!res.ok || !res.body) {
    throw new Error(`Download failed: googlevideo ${res.status} for ${opts.videoId}`);
  }

  const filePath = join(opts.outDir, `${opts.videoId}.mp4`);
  try {
    await pipeline(Readable.fromWeb(res.body), createWriteStream(filePath));
  } catch (err) {
    await unlink(filePath).catch(() => undefined);
    throw err;
  }

  if (!existsSync(filePath)) {
    throw new Error(`Download completed but file not found at ${filePath}`);
  }
  return { filePath, fileSizeBytes: statSync(filePath).size };
}
