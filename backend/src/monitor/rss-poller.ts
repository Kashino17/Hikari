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

function feedUrl(channelId: string): string {
  return `https://www.youtube.com/feeds/videos.xml?channel_id=${channelId}`;
}

export async function fetchChannelFeed(channelId: string): Promise<FeedEntry[]> {
  const res = await fetch(feedUrl(channelId));
  if (!res.ok) {
    throw new Error(`RSS fetch failed: ${res.status} for channel ${channelId}`);
  }
  const xml = await res.text();
  return parseChannelFeed(xml);
}

// ---------------------------------------------------------------------------
// Conditional fetch (ETag / If-Modified-Since)
//
// YouTube's RSS endpoint supports HTTP caching validators. Sending the
// previously-seen ETag / Last-Modified lets the server answer 304 Not Modified
// (tiny, body-less) when nothing changed — which is the common case on a
// frequent poll. Saves bandwidth and parse work; the caller skips the channel
// entirely on 304.
// ---------------------------------------------------------------------------

export interface ConditionalHeaders {
  etag: string | null;
  lastModified: string | null;
}

export type ConditionalFeedResult =
  | { status: "notModified" }
  | { status: "ok"; entries: FeedEntry[]; etag: string | null; lastModified: string | null };

export async function fetchChannelFeedConditional(
  channelId: string,
  prior: ConditionalHeaders,
): Promise<ConditionalFeedResult> {
  const headers: Record<string, string> = {};
  if (prior.etag) headers["If-None-Match"] = prior.etag;
  if (prior.lastModified) headers["If-Modified-Since"] = prior.lastModified;

  const res = await fetch(feedUrl(channelId), { headers });
  if (res.status === 304) return { status: "notModified" };
  if (!res.ok) {
    throw new Error(`RSS fetch failed: ${res.status} for channel ${channelId}`);
  }
  const xml = await res.text();
  return {
    status: "ok",
    entries: parseChannelFeed(xml),
    etag: res.headers.get("etag"),
    lastModified: res.headers.get("last-modified"),
  };
}
