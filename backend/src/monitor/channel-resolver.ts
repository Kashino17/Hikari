import { runYtDlp } from "../yt-dlp/client.js";

export interface ResolvedChannel {
  channelId: string;
  title: string;
  handle: string | null;       // @-form, e.g. "@finanzfluss"
  description: string | null;
  subscribers: number | null;
  thumbnail: string | null;
}

interface YtDlpChannelOutput {
  channel_id?: string;
  channel?: string;
  uploader?: string;
  uploader_id?: string;        // @handle
  description?: string;
  channel_follower_count?: number;
  thumbnails?: { url?: string }[];
  thumbnail?: string;
  // For URL-form input, when the source is a channel page (not a video),
  // yt-dlp may surface fields at the top level of the playlist.
}

function fixProtocol(url: string | null | undefined): string | null {
  if (!url) return null;
  if (url.startsWith("//")) return `https:${url}`;
  return url;
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
    thumbnail: fixProtocol(parsed.thumbnails?.[0]?.url ?? parsed.thumbnail),
  };
}
