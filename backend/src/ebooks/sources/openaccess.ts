import type { RawBook } from "../persist.js";
import { type EbookSource, SourceBlocked } from "./types.js";

const BASE = "https://directory.doabooks.org";
const PAGE = 100;

interface DoabBitstream {
  name?: string;
  mimeType?: string;
  bundleName?: string;
  retrieveLink?: string;
}
interface DoabItem {
  uuid?: string;
  handle?: string;
  name?: string;
  metadata?: { key: string; value: unknown }[];
  bitstreams?: DoabBitstream[];
}

/** Deutschsprachige Open-Access-Bücher aus dem DOAB (DSpace-REST). */
export const openAccessSource: EbookSource = {
  id: "openaccess",
  name: "Open Access (DOAB)",
  async listCatalog(ctx) {
    const out: RawBook[] = [];
    for (let offset = 0; offset < 20_000; offset += PAGE) {
      const r = await ctx.http.get(
        `${BASE}/rest/search?query=${encodeURIComponent("dc.language:German")}&expand=metadata,bitstreams&limit=${PAGE}&offset=${offset}`,
        { accept: "application/json" },
      );
      if (r.status === 403) throw new SourceBlocked("openaccess", 403);
      if (r.status !== 200) throw new Error(`openaccess: HTTP ${r.status}`);
      const items = JSON.parse(r.body.toString("utf8")) as DoabItem[];
      for (const it of items) {
        const meta = (key: string) =>
          (it.metadata ?? []).filter((m) => m.key === key).map((m) => String(m.value));
        if (!meta("dc.language").some((l: string) => /^(german|deutsch|de|ger|deu)$/i.test(l)))
          continue;
        const files = (it.bitstreams ?? []).filter((b) => b.bundleName === "ORIGINAL");
        const epub = files.find(
          (b) => b.mimeType === "application/epub+zip" || /\.epub$/i.test(b.name ?? ""),
        );
        const pdf = files.find(
          (b) => b.mimeType === "application/pdf" || /\.pdf$/i.test(b.name ?? ""),
        );
        const file = epub ?? pdf;
        if (!file?.retrieveLink) continue;
        const thumb = (it.bitstreams ?? []).find((b) => b.bundleName === "THUMBNAIL");
        out.push({
          source: "openaccess",
          sourceId: String(it.handle ?? it.uuid),
          title: meta("dc.title")[0] ?? it.name ?? "",
          authors: meta("dc.contributor.author").map((a: string) => {
            const [last = "", ...first] = a.split(",").map((s) => s.trim());
            return first.length ? `${first.join(" ")} ${last}` : last;
          }),
          year: Number(/\d{4}/.exec(meta("dc.date.issued")[0] ?? "")?.[0]) || undefined,
          language: "de",
          description: meta("dc.description.abstract")[0],
          subjects: [...meta("dc.subject.other"), ...meta("dc.subject.classification"), "Sachbuch"],
          format: epub ? "epub" : "pdf",
          fileUrl: `${BASE}${file.retrieveLink}`,
          coverUrl: thumb ? `${BASE}${thumb.retrieveLink}` : undefined,
          sourceUrl: `${BASE}/handle/${it.handle}`,
        });
      }
      if (items.length < PAGE) break;
    }
    return out;
  },
};
