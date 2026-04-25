import { runYtDlp } from "../yt-dlp/client.js";

export interface ChannelSearchResult {
  channelId: string;
  channelUrl: string;
  title: string;
  handle: string | null;
  description: string | null;
  subscribers: number | null;
  thumbnail: string | null;
  verified: boolean;
}

interface YtDlpSearchEntry {
  id?: string;
  ie_key?: string;
  channel_id?: string;
  channel?: string;
  uploader?: string;
  channel_url?: string;
  uploader_url?: string;
  uploader_id?: string;
  description?: string | null;
  channel_follower_count?: number | null;
  channel_is_verified?: boolean | null;
  thumbnails?: { url?: string }[];
}

const SEARCH_URL_BASE = "https://www.youtube.com/results";
// Protobuf-encoded YT search filter: type=channel. Stable for years.
const FILTER_CHANNELS = "EgIQAg%253D%253D";

export async function searchChannels(
  query: string,
  limit: number,
): Promise<ChannelSearchResult[]> {
  const trimmed = query.trim();
  if (trimmed.length < 2) return [];
  const safeLimit = Math.min(Math.max(limit, 1), 25);
  const url =
    `${SEARCH_URL_BASE}?search_query=${encodeURIComponent(trimmed)}&sp=${FILTER_CHANNELS}`;

  const result = await runYtDlp(
    [
      "--flat-playlist",
      "--skip-download",
      "--dump-single-json",
      "--no-warnings",
      "--playlist-end",
      String(safeLimit),
      url,
    ],
    { timeoutMs: 20_000 },
  );

  if (!result.stdout.trim()) return [];
  const parsed = JSON.parse(result.stdout) as { entries?: YtDlpSearchEntry[] };
  const entries = parsed.entries ?? [];

  return entries
    .filter((e) => e.ie_key === "YoutubeTab" && (e.channel_id ?? e.id))
    .map<ChannelSearchResult>((e) => {
      const id = e.channel_id ?? e.id ?? "";
      const rawThumb = e.thumbnails?.[0]?.url ?? null;
      const thumb = rawThumb?.startsWith("//") ? `https:${rawThumb}` : rawThumb;
      return {
        channelId: id,
        // Prefer canonical channel URL — the resolver accepts both forms but
        // /channel/UC... is unambiguous.
        channelUrl: e.channel_url ?? `https://www.youtube.com/channel/${id}`,
        title: e.channel ?? e.uploader ?? id,
        handle: e.uploader_id ?? null,
        description: e.description ?? null,
        subscribers: e.channel_follower_count ?? null,
        thumbnail: thumb,
        verified: e.channel_is_verified === true,
      };
    });
}
