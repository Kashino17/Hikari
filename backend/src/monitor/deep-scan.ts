import { runYtDlp } from "../yt-dlp/client.js";
import type { FeedEntry } from "./rss-poller.js";

interface YtDlpVideoEntry {
  id?: string;
  title?: string;
  timestamp?: number | null;
  upload_date?: string | null; // YYYYMMDD
  duration?: number | null;
}

interface YtDlpPlaylist {
  entries?: YtDlpVideoEntry[];
}

function parseUploadDate(d: string | null | undefined): number | null {
  if (!d || d.length !== 8) return null;
  // YYYYMMDD → epoch ms (UTC midnight; no time-of-day available from this field)
  const year = Number.parseInt(d.slice(0, 4), 10);
  const month = Number.parseInt(d.slice(4, 6), 10) - 1;
  const day = Number.parseInt(d.slice(6, 8), 10);
  if ([year, month, day].some(Number.isNaN)) return null;
  return Date.UTC(year, month, day);
}

/**
 * Scrapes the channel's full video page via yt-dlp — up to `limit` entries,
 * sorted newest-first. Use this when RSS's 15-entry cap is too short.
 *
 * Slow: ~3-10s per call depending on channel size.
 */
export async function fetchChannelDeepScan(
  channelId: string,
  limit = 50,
): Promise<FeedEntry[]> {
  const safeLimit = Math.min(Math.max(limit, 1), 200);
  const url = `https://www.youtube.com/channel/${channelId}/videos`;

  const { stdout } = await runYtDlp(
    [
      "--flat-playlist",
      "--skip-download",
      "--dump-single-json",
      "--no-warnings",
      "--playlist-end",
      String(safeLimit),
      url,
    ],
    { timeoutMs: 60_000 },
  );

  if (!stdout.trim()) return [];
  const parsed = JSON.parse(stdout) as YtDlpPlaylist;
  const entries = parsed.entries ?? [];
  return entries
    .filter((e): e is Required<Pick<YtDlpVideoEntry, "id" | "title">> & YtDlpVideoEntry =>
      Boolean(e.id) && Boolean(e.title),
    )
    .map((e) => ({
      videoId: e.id,
      title: e.title,
      publishedAt:
        (typeof e.timestamp === "number" ? e.timestamp * 1000 : null) ??
        parseUploadDate(e.upload_date) ??
        Date.now(),
    }));
}
