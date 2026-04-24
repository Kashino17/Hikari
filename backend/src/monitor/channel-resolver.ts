import { runYtDlp } from "../yt-dlp/client.js";

export interface ResolvedChannel {
  channelId: string;
  title: string;
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

  const parsed = JSON.parse(stdout) as { channel_id?: string; channel?: string; uploader?: string };
  if (!parsed.channel_id) {
    throw new Error(`Could not extract channel_id from yt-dlp output for URL: ${url}`);
  }
  return {
    channelId: parsed.channel_id,
    title: parsed.channel ?? parsed.uploader ?? "Unknown",
  };
}
