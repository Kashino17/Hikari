import { XMLParser } from "fast-xml-parser";

export interface FeedEntry {
  videoId: string;
  title: string;
  publishedAt: number;
}

interface AtomEntry {
  "yt:videoId": string;
  title: string;
  published: string;
}

interface AtomFeed {
  feed: { entry?: AtomEntry | AtomEntry[] };
}

const parser = new XMLParser({
  ignoreAttributes: true,
  removeNSPrefix: false,
  textNodeName: "#text",
});

export function parseChannelFeed(xml: string): FeedEntry[] {
  const parsed = parser.parse(xml) as AtomFeed;
  const raw = parsed.feed?.entry;
  if (!raw) return [];
  const entries = Array.isArray(raw) ? raw : [raw];
  return entries.map((e) => ({
    videoId: e["yt:videoId"],
    title: e.title,
    publishedAt: new Date(e.published).getTime(),
  }));
}

export async function fetchChannelFeed(channelId: string): Promise<FeedEntry[]> {
  const url = `https://www.youtube.com/feeds/videos.xml?channel_id=${channelId}`;
  const res = await fetch(url);
  if (!res.ok) {
    throw new Error(`RSS fetch failed: ${res.status} for channel ${channelId}`);
  }
  const xml = await res.text();
  return parseChannelFeed(xml);
}
