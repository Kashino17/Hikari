import { gunzipSync } from "node:zlib";
import type { RawBook } from "../persist.js";
import { parseCsv } from "./csv.js";
import type { EbookSource } from "./types.js";

const MIRROR = "https://gutenberg.pglaf.org";
const MAIN = "https://www.gutenberg.org";

/** "Goethe, Johann Wolfgang von, 1749-1832" → "Johann Wolfgang von Goethe"; Übersetzer usw. fallen weg. */
export function parseAuthors(field: string): string[] {
  const parts = field
    .split(";")
    .map((s) => s.trim())
    .filter(Boolean);
  const people = parts.map((p) => {
    const role = /\[([^\]]+)\]\s*$/.exec(p)?.[1];
    // Lebensdaten am Ende („, 1749-1832“, „, -1832“, „, active 1500“) abschneiden.
    const segs = p
      .replace(/\[[^\]]+\]\s*$/, "")
      .split(",")
      .map((x) => x.trim());
    while (segs.length > 1 && /\d|^active\b|^fl\./i.test(segs[segs.length - 1] ?? "")) segs.pop();
    const name = segs.join(", ");
    const [last = "", ...first] = name.split(",").map((s) => s.trim());
    return { role, name: first.length ? `${first.join(" ")} ${last}`.trim() : last };
  });
  const authors = people.filter((p) => !p.role || /^author$/i.test(p.role)).map((p) => p.name);
  return authors.length ? authors : people.map((p) => p.name);
}

export const gutenbergSource: EbookSource = {
  id: "gutenberg",
  name: "Project Gutenberg",
  async listCatalog(ctx) {
    let csv: Buffer | undefined;
    // Gepackt zuerst (5 statt 21 MB — der Spiegel ist langsam), dann ungepackt, dann die Hauptseite.
    for (const [base, file] of [
      [MIRROR, "pg_catalog.csv.gz"],
      [MIRROR, "pg_catalog.csv"],
      [MAIN, "pg_catalog.csv.gz"],
    ] as const) {
      try {
        const r = await ctx.http.get(`${base}/cache/epub/feeds/${file}`);
        if (r.status === 200 && r.body.length > 100) {
          csv = r.body[0] === 0x1f && r.body[1] === 0x8b ? gunzipSync(r.body) : r.body;
          break;
        }
        ctx.log(`gutenberg: ${base}/${file} antwortet ${r.status}`);
      } catch (err) {
        ctx.log(`gutenberg: ${base}/${file} — ${(err as Error).message}`);
      }
    }
    if (!csv) throw new Error("gutenberg: Katalog nicht erreichbar");
    const [head = [], ...rows] = parseCsv(csv.toString("utf8"));
    const col = (name: string) => head.indexOf(name);
    const cId = col("Text#");
    const cType = col("Type");
    const cTitle = col("Title");
    const cLang = col("Language");
    const cAuthors = col("Authors");
    const cSubjects = col("Subjects");
    const cShelves = col("Bookshelves");
    const out: RawBook[] = [];
    for (const r of rows) {
      if (r[cType] !== "Text" || r[cLang]?.trim() !== "de") continue;
      const id = r[cId]?.trim();
      if (!id || !/^\d+$/.test(id)) continue;
      const split = (s = "") =>
        s
          .split(";")
          .map((x) => x.trim())
          .filter(Boolean);
      out.push({
        source: "gutenberg",
        sourceId: id,
        title: (r[cTitle] ?? "").replace(/\s+/g, " ").trim(),
        authors: parseAuthors(r[cAuthors] ?? ""),
        language: "de",
        subjects: [...split(r[cSubjects]), ...split(r[cShelves])],
        format: "epub",
        fileUrl: `${MIRROR}/cache/epub/${id}/pg${id}-images-3.epub`,
        coverUrl: `${MIRROR}/cache/epub/${id}/pg${id}.cover.medium.jpg`,
        sourceUrl: `${MAIN}/ebooks/${id}`,
      });
    }
    return out;
  },
};
