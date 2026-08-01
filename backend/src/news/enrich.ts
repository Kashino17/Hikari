import * as cheerio from "cheerio";
import type { NewsItem } from "./feeds.js";

export interface EnrichedNewsItem extends NewsItem {
  imageUrls: string[];
  videoUrl: string | null;
  leadText: string;
}

const USER_AGENT =
  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
  "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
const BATCH_SIZE = 5;

function isDirectVideo(url: string): boolean {
  return /\.(mp4|webm)(\?|#|$)/i.test(url);
}

function isYoutubeEmbed(url: string): boolean {
  return /^https?:\/\/(www\.)?(youtube\.com|youtube-nocookie\.com)\/embed\//i.test(url);
}

async function enrichOne(item: NewsItem, fetchImpl: typeof fetch): Promise<EnrichedNewsItem> {
  const base: EnrichedNewsItem = {
    ...item,
    imageUrls: item.imageUrl ? [item.imageUrl] : [],
    videoUrl: null,
    leadText: item.description,
  };
  // Google News links are interstitials that never render article HTML.
  if (item.url.includes("news.google.com")) return base;

  try {
    const res = await fetchImpl(item.url, {
      signal: AbortSignal.timeout(4000),
      headers: { "user-agent": USER_AGENT, accept: "text/html" },
    });
    if (!res.ok) return base;
    const html = await res.text();
    const $ = cheerio.load(html);

    const images: string[] = [...base.imageUrls];
    const pushImage = (url: string | undefined) => {
      if (url?.startsWith("http") && !images.includes(url) && images.length < 2) {
        images.push(url);
      }
    };
    pushImage($('meta[property="og:image"]').attr("content"));
    pushImage($('meta[name="twitter:image"]').attr("content"));

    let videoUrl: string | null = null;
    const ogVideo =
      $('meta[property="og:video:secure_url"]').attr("content") ??
      $('meta[property="og:video"]').attr("content");
    if (ogVideo && isDirectVideo(ogVideo)) {
      videoUrl = ogVideo;
    } else {
      const embed = $('iframe[src*="youtube.com/embed"], iframe[src*="youtube-nocookie.com/embed"]')
        .first()
        .attr("src");
      if (embed && isYoutubeEmbed(embed)) videoUrl = embed;
    }

    const leadText = $("p")
      .toArray()
      .map((p) => $(p).text().trim())
      .filter((t) => t.length > 40)
      .join(" ")
      .slice(0, 800);

    return {
      ...base,
      imageUrls: images,
      videoUrl,
      leadText: leadText || base.leadText,
    };
  } catch {
    // best effort — timeout, DNS, TLS: keep the unenriched item
    return base;
  }
}

/**
 * Enriches registry-feed items with article-page metadata (images, video,
 * lead text). Best-effort: any failure leaves the item unchanged. Concurrency
 * is capped in batches of 5 to avoid hammering publishers.
 */
export async function enrichItems(
  items: NewsItem[],
  fetchImpl: typeof fetch = fetch,
): Promise<EnrichedNewsItem[]> {
  const out: EnrichedNewsItem[] = [];
  for (let i = 0; i < items.length; i += BATCH_SIZE) {
    const batch = items.slice(i, i + BATCH_SIZE);
    out.push(...(await Promise.all(batch.map((item) => enrichOne(item, fetchImpl)))));
  }
  return out;
}
