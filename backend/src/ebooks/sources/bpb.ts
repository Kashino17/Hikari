import * as cheerio from "cheerio";
import type { RawBook } from "../persist.js";
import type { EbookSource } from "./types.js";

const BASE = "https://www.bpb.de";
/** Buchseiten: /shop/buecher/<reihe>/<nid>/<slug>/ */
const BOOK_URL = /^https:\/\/www\.bpb\.de\/shop\/buecher\/[^/]+\/(\d+)\/[^/]+\/$/;
const NOT_FULL = /leseprobe|inhaltsverzeichnis|widerruf|flyer|vorwort|einleitung/i;

interface Parsed {
  book?: Omit<RawBook, "source" | "sourceId" | "sourceUrl"> | undefined;
}
interface Cached {
  lastmod: string | undefined;
  book: Parsed["book"];
}

/** Eine bpb-Buchseite: nur die Dateien aus „Download auswählen“ zählen, Leseproben nie. */
export function parseBpbPage(html: string): Parsed["book"] {
  const $ = cheerio.load(html);
  const files = $("bpb-input-radio-option")
    .map((_, el) => String($(el).attr("value") ?? ""))
    .get()
    .filter((v) => /\.(epub|pdf)(\?|$)/i.test(v) && !NOT_FULL.test(v));
  const epub = files.find((f) => /\.epub(\?|$)/i.test(f));
  const pdf = files.find((f) => /\.pdf(\?|$)/i.test(f));
  const file = epub ?? pdf;
  if (!file) return undefined;
  const title =
    $("h2.opening-header__title").first().text().replace(/\s+/g, " ").trim() ||
    String($('meta[property="og:title"]').attr("content") ?? "")
      .replace(/\s*\|\s*bpb\.de$/, "")
      .trim();
  const author = $(".opening-header__author").first().text().replace(/\s+/g, " ").trim();
  const desc = String(
    $('meta[property="og:description"]').attr("content") ??
      $('meta[name="description"]').attr("content") ??
      "",
  ).trim();
  const year =
    Number(
      /\b(19|20)\d{2}\b/.exec($(".opening-header__panel, .publication-meta, time").text())?.[0],
    ) || undefined;
  return {
    title,
    authors: author ? author.split(/\s*(?:,|;| und |\/)\s*/).filter(Boolean) : [],
    year,
    language: "de",
    description: desc || undefined,
    subjects: ["Politik", "Sachbuch"],
    format: epub ? "epub" : "pdf",
    fileUrl: new URL(file, BASE).toString(),
    coverUrl: String($('meta[property="og:image"]').attr("content") ?? "") || undefined,
  };
}

export const bpbSource: EbookSource = {
  id: "bpb",
  name: "Bundeszentrale für politische Bildung",
  async listCatalog(ctx) {
    const pages: { url: string; nid: string; lastmod: string | undefined }[] = [];
    for (const page of ["1", "2", "3"]) {
      const r = await ctx.http.get(`${BASE}/sitemap.xml?page=${page}`);
      if (r.status !== 200) break;
      const xml = r.body.toString("utf8");
      for (const m of xml.matchAll(
        /<url>\s*<loc>([^<]+)<\/loc>(?:\s*<lastmod>([^<]+)<\/lastmod>)?/g,
      )) {
        const loc = m[1] ?? "";
        const nid = BOOK_URL.exec(loc)?.[1];
        if (nid && !pages.some((p) => p.nid === nid)) pages.push({ url: loc, nid, lastmod: m[2] });
      }
      if (!xml.includes("<url>")) break;
    }
    const out: RawBook[] = [];
    for (const p of pages) {
      const key = `bpb:${p.nid}`;
      let cached = ctx.cache.get<Cached>(key);
      if (!cached || cached.lastmod !== p.lastmod) {
        try {
          const r = await ctx.http.get(p.url);
          if (r.status !== 200) continue;
          cached = { lastmod: p.lastmod, book: parseBpbPage(r.body.toString("utf8")) };
          ctx.cache.set(key, cached);
        } catch (err) {
          ctx.log(`bpb: ${p.url} — ${(err as Error).message}`);
          continue;
        }
      }
      if (cached?.book)
        out.push({ ...cached.book, source: "bpb", sourceId: p.nid, sourceUrl: p.url });
    }
    return out;
  },
};
