import { XMLParser } from "fast-xml-parser";

export interface NewsItem {
  id: string;
  title: string;
  url: string;
  description: string;
  source: string;
  publishedAt: string; // ISO 8601
  imageUrl?: string;
}

export interface NewsTopic {
  key: string;
  label: string;
  lang: "de" | "en";
  feeds: string[];
}

/**
 * Curated RSS/Atom feeds per topic. Source name is derived from the feed
 * hostname (strip "www.") — no separate metadata needed per feed.
 */
export const NEWS_TOPICS: NewsTopic[] = [
  {
    key: "politik",
    label: "Politik",
    lang: "de",
    feeds: ["https://www.tagesschau.de/inland/index~rss2.xml"],
  },
  {
    key: "wirtschaft",
    label: "Wirtschaft",
    lang: "de",
    feeds: ["https://www.tagesschau.de/wirtschaft/index~rss2.xml"],
  },
  {
    key: "technologie",
    label: "Technologie",
    lang: "de",
    feeds: ["https://www.heise.de/rss/heise-atom.xml"],
  },
  {
    key: "sport",
    label: "Sport",
    lang: "de",
    feeds: ["https://www.sportschau.de/index~rss2.xml"],
  },
  {
    key: "wissen",
    label: "Wissenschaft",
    lang: "de",
    feeds: ["https://www.spektrum.de/alias/rss/spektrum-de-rss-feed/996406"],
  },
  {
    key: "kultur",
    label: "Kultur",
    lang: "de",
    feeds: ["https://www.tagesschau.de/kultur/index~rss2.xml"],
  },
  {
    key: "world",
    label: "Welt",
    lang: "en",
    feeds: ["http://feeds.bbci.co.uk/news/world/rss.xml"],
  },
  {
    key: "business",
    label: "Business",
    lang: "en",
    feeds: ["http://feeds.bbci.co.uk/news/business/rss.xml"],
  },
  {
    key: "technology",
    label: "Technology",
    lang: "en",
    feeds: ["https://www.theverge.com/rss/index.xml"],
  },
  {
    key: "science",
    label: "Science",
    lang: "en",
    feeds: ["https://feeds.npr.org/1007/rss.xml"],
  },
  {
    key: "sports",
    label: "Sports",
    lang: "en",
    feeds: ["http://feeds.bbci.co.uk/sport/rss.xml"],
  },
];

/** Case-insensitive lookup by key or label. */
export function findTopic(query: string): NewsTopic | undefined {
  const q = query.trim().toLowerCase();
  return NEWS_TOPICS.find((t) => t.key.toLowerCase() === q || t.label.toLowerCase() === q);
}

/** djb2 hash as hex — stable id from a URL, no crypto import needed. */
export function hashId(input: string): string {
  let h = 5381;
  for (let i = 0; i < input.length; i++) {
    h = ((h << 5) + h + input.charCodeAt(i)) >>> 0;
  }
  return h.toString(16);
}

export function stripHtml(html: string): string {
  return html
    .replace(/<[^>]*>/g, " ")
    .replace(/&nbsp;/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&#39;|&apos;/g, "'")
    .replace(/\s+/g, " ")
    .trim();
}

const parser = new XMLParser({
  ignoreAttributes: false,
  attributeNamePrefix: "@_",
  parseTagValue: false,
  trimValues: true,
});

type XmlNode = Record<string, unknown>;

function asArray<T>(value: T | T[] | undefined): T[] {
  if (value === undefined || value === null) return [];
  return Array.isArray(value) ? value : [value];
}

function textOf(value: unknown): string {
  if (value === undefined || value === null) return "";
  if (typeof value === "object") {
    const node = value as XmlNode;
    // <tag attr="x">text</tag> parses to { "@_attr": "x", "#text": "text" }
    if (typeof node["#text"] === "string") return node["#text"];
    return "";
  }
  return String(value);
}

function firstAttrUrl(nodes: XmlNode[]): string | undefined {
  for (const n of nodes) {
    const url = n["@_url"];
    if (typeof url === "string" && url.startsWith("http")) return url;
  }
  return undefined;
}

function extractImageUrl(entry: XmlNode): string | undefined {
  // 1. media:content @url
  const mediaContent = firstAttrUrl(asArray(entry["media:content"] as XmlNode | XmlNode[]));
  if (mediaContent) return mediaContent;
  // 2. media:thumbnail @url
  const mediaThumb = firstAttrUrl(asArray(entry["media:thumbnail"] as XmlNode | XmlNode[]));
  if (mediaThumb) return mediaThumb;
  // 3. enclosure @url when type is image/*
  for (const enc of asArray(entry.enclosure as XmlNode | XmlNode[])) {
    const url = enc["@_url"];
    const type = enc["@_type"];
    if (
      typeof url === "string" &&
      url.startsWith("http") &&
      (typeof type !== "string" || type.startsWith("image/"))
    ) {
      return url;
    }
  }
  // 4. first <img src> in content:encoded (or Atom content)
  const content = textOf(entry["content:encoded"] ?? entry.content);
  const imgSrc = content.match(/<img[^>]+src=["']([^"']+)["']/i)?.[1];
  if (imgSrc?.startsWith("http")) return imgSrc;
  return undefined;
}

function toIsoDate(raw: string): string {
  if (!raw) return new Date(0).toISOString();
  const d = new Date(raw);
  return Number.isNaN(d.getTime()) ? new Date(0).toISOString() : d.toISOString();
}

function atomLinkUrl(entry: XmlNode): string {
  const links = asArray(entry.link as XmlNode | XmlNode[] | string);
  // prefer rel="alternate" (or no rel) over rel="self"/"replies"
  let fallback = "";
  for (const l of links) {
    if (typeof l === "string") return l;
    const href = l["@_href"];
    if (typeof href !== "string") continue;
    const rel = l["@_rel"];
    if (rel === undefined || rel === "alternate") return href;
    if (!fallback) fallback = href;
  }
  return fallback;
}

/**
 * Parses RSS 2.0 and Atom feeds into NewsItems. Unlike the YouTube rss-poller
 * this parser needs attributes (media:content/@url, enclosure/@type), hence a
 * separate XMLParser instance with ignoreAttributes: false.
 */
export function parseFeedItems(xml: string, sourceName: string): NewsItem[] {
  const doc = parser.parse(xml) as XmlNode;

  const rss = doc.rss as XmlNode | undefined;
  const channel = rss?.channel as XmlNode | undefined;
  const feed = doc.feed as XmlNode | undefined; // Atom

  if (channel) {
    return asArray(channel.item as XmlNode | XmlNode[]).map((item) => {
      const url = textOf(item.link);
      const imageUrl = extractImageUrl(item);
      return {
        id: hashId(url || textOf(item.guid)),
        title: stripHtml(textOf(item.title)),
        url,
        description: stripHtml(textOf(item.description)),
        source: sourceName,
        publishedAt: toIsoDate(textOf(item.pubDate ?? item["dc:date"])),
        ...(imageUrl ? { imageUrl } : {}),
      };
    });
  }

  if (feed) {
    return asArray(feed.entry as XmlNode | XmlNode[]).map((entry) => {
      const url = atomLinkUrl(entry);
      const imageUrl = extractImageUrl(entry);
      const description = stripHtml(textOf(entry.summary ?? entry.content));
      return {
        id: hashId(url || textOf(entry.id)),
        title: stripHtml(textOf(entry.title)),
        url,
        description,
        source: sourceName,
        publishedAt: toIsoDate(textOf(entry.published ?? entry.updated)),
        ...(imageUrl ? { imageUrl } : {}),
      };
    });
  }

  return [];
}
