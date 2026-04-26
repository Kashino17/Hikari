import { runYtDlp } from "../yt-dlp/client.js";

export interface ResolvedChannel {
  channelId: string;
  title: string;
  handle: string | null;       // @-form, e.g. "@finanzfluss"
  description: string | null;
  subscribers: number | null;
  thumbnail: string | null;
  banner: string | null;       // wide 16:7-ish banner art
}

interface YtDlpThumbnail {
  url?: string;
  width?: number | null;
  height?: number | null;
}

interface YtDlpChannelOutput {
  channel_id?: string;
  channel?: string;
  uploader?: string;
  uploader_id?: string;        // @handle
  description?: string;
  channel_follower_count?: number;
  thumbnails?: YtDlpThumbnail[];
  thumbnail?: string;
}

function fixProtocol(url: string | null | undefined): string | null {
  if (!url) return null;
  if (url.startsWith("//")) return `https:${url}`;
  return url;
}

/**
 * yt-dlp's `thumbnails` array on a channel URL contains BOTH banner art
 * (wide aspect, e.g. 2560×424) AND avatar art (square, 88×88 or 900×900).
 * Banners come first in the array, so naively picking [0] gives the banner.
 * We want the avatar — the first roughly-square entry.
 */
function pickAvatar(thumbnails: YtDlpThumbnail[] | undefined): string | null {
  if (!thumbnails?.length) return null;
  // Square = width and height set, ratio close to 1.
  const square = thumbnails.find((t) => {
    const w = t.width ?? 0;
    const h = t.height ?? 0;
    if (w <= 0 || h <= 0) return false;
    const ratio = w / h;
    return ratio > 0.9 && ratio < 1.1;
  });
  if (square?.url) return fixProtocol(square.url);
  // Fallback: last thumbnail in the list — yt-dlp orders avatars after
  // banners on channel URLs.
  const last = thumbnails[thumbnails.length - 1];
  return fixProtocol(last?.url);
}

/**
 * Pick the highest-resolution wide-aspect banner from yt-dlp thumbnails.
 * Banners are typically 2560×424 (~6:1) but YT serves several sizes;
 * pick the widest.
 */
function pickBanner(thumbnails: YtDlpThumbnail[] | undefined): string | null {
  if (!thumbnails?.length) return null;
  const wide = thumbnails
    .filter((t) => {
      const w = t.width ?? 0;
      const h = t.height ?? 0;
      if (w <= 0 || h <= 0) return false;
      return w / h >= 2.5;
    })
    .sort((a, b) => (b.width ?? 0) - (a.width ?? 0));
  const top = wide[0];
  if (top?.url) return fixProtocol(top.url);
  return null;
}

export async function resolveChannel(url: string): Promise<ResolvedChannel> {
  const { stdout } = await runYtDlp([
    "--flat-playlist",
    "--playlist-items",
    "1",
    "--dump-single-json",
    "--no-warnings",
    url,
  ]);

  const parsed = JSON.parse(stdout) as YtDlpChannelOutput;
  if (!parsed.channel_id) {
    throw new Error(`Could not extract channel_id from yt-dlp output for URL: ${url}`);
  }
  return {
    channelId: parsed.channel_id,
    title: parsed.channel ?? parsed.uploader ?? "Unknown",
    handle: parsed.uploader_id ?? null,
    description: parsed.description ?? null,
    subscribers: parsed.channel_follower_count ?? null,
    thumbnail: pickAvatar(parsed.thumbnails) ?? fixProtocol(parsed.thumbnail),
    banner: pickBanner(parsed.thumbnails),
  };
}
