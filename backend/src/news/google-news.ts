import { type NewsItem, parseFeedItems } from "./feeds.js";

export type NewsLang = "de" | "en";

const LOCALE: Record<NewsLang, { hl: string; gl: string; ceid: string }> = {
  de: { hl: "de", gl: "DE", ceid: "DE:de" },
  en: { hl: "en-US", gl: "US", ceid: "US:en" },
};

/**
 * Google News RSS as fallback for topics without a curated feed. The items
 * carry Google-interstitial links (not resolvable server-side — passed through
 * as-is), no images, and the publisher appended to the title as " - QUELLE".
 */
export async function fetchGoogleNewsTopic(
  query: string,
  lang: NewsLang,
  fetchImpl: typeof fetch = fetch,
): Promise<NewsItem[]> {
  const loc = LOCALE[lang];
  const url =
    `https://news.google.com/rss/search?q=${encodeURIComponent(query)}` +
    `&hl=${loc.hl}&gl=${loc.gl}&ceid=${loc.ceid}`;
  const res = await fetchImpl(url, { signal: AbortSignal.timeout(6000) });
  if (!res.ok) throw new Error(`Google News request failed: ${res.status}`);
  const xml = await res.text();
  // source name gets overwritten below — parsed per item from the title suffix.
  // Die Google-News-<description> wiederholt nur den Titel — sie ist kein
  // Inhalt. Leer lassen, damit Zusammenfassung und UI nichts duplizieren.
  return parseFeedItems(xml, "Google News").map((item) => {
    const idx = item.title.lastIndexOf(" - ");
    if (idx <= 0) return { ...item, description: "" };
    return {
      ...item,
      title: item.title.slice(0, idx).trim(),
      source: item.title.slice(idx + 3).trim(),
      description: "",
    };
  });
}
