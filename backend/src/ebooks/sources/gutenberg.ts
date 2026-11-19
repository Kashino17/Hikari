import { gunzipSync } from "node:zlib";
import type { RawBook } from "../persist.js";
import { parseCsv } from "./csv.js";
import type { EbookSource } from "./types.js";

const MIRROR = "https://gutenberg.pglaf.org";
const MAIN = "https://www.gutenberg.org";
/** Bücher, Titelbilder und RDF: dieser Spiegel liefert ~30× schneller (gemessen 19.11.: 6 s statt 202 s). */
const FILES = "https://gutenberg.nabasny.com";

/** Ausweichadressen einer Gutenberg-Datei, falls der schnelle Spiegel sie nicht hat. */
export function gutenbergAlternates(url: string): string[] {
  return url.startsWith(FILES) ? [MIRROR + url.slice(FILES.length)] : [];
}
/** So viele Downloadzahlen holt ein Lauf höchstens nach (eine Anfrage je Buch). */
export const GUTENBERG_POPULARITY_PER_RUN = 400;
const POPULARITY_MAX_AGE_MS = 30 * 24 * 60 * 60 * 1000;

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
        fileUrl: `${FILES}/cache/epub/${id}/pg${id}-images-3.epub`,
        coverUrl: `${FILES}/cache/epub/${id}/pg${id}.cover.medium.jpg`,
        sourceUrl: `${MAIN}/ebooks/${id}`,
      });
    }
    await addPopularity(ctx, out);
    return out;
  },
};

/** Beliebtheit = Downloadzahl laut Gutenberg (RDF je Buch), 30 Tage gemerkt, nach und nach ergänzt. */
async function addPopularity(
  ctx: Parameters<EbookSource["listCatalog"]>[0],
  books: RawBook[],
): Promise<void> {
  const now = Date.now();
  const key = (b: RawBook) => `pop:${b.sourceId}`;
  const stale: RawBook[] = [];
  for (const b of books) {
    const c = ctx.cache.get<{ n: number; at: number }>(key(b));
    if (c) b.popularity = c.n;
    if (!c || now - c.at > POPULARITY_MAX_AGE_MS) stale.push(b);
  }
  // Nie geholte zuerst, dann die ältesten.
  stale.sort(
    (a, b) =>
      (ctx.cache.get<{ at: number }>(key(a))?.at ?? 0) -
      (ctx.cache.get<{ at: number }>(key(b))?.at ?? 0),
  );
  for (const b of stale.slice(0, GUTENBERG_POPULARITY_PER_RUN)) {
    try {
      const r = await ctx.http.get(`${FILES}/cache/epub/${b.sourceId}/pg${b.sourceId}.rdf`);
      const n = Number(/<pgterms:downloads[^>]*>(\d+)</.exec(r.body.toString("utf8"))?.[1]);
      if (r.status === 200 && Number.isFinite(n)) {
        b.popularity = n;
        ctx.cache.set(key(b), { n, at: now });
      }
    } catch (err) {
      ctx.log(`gutenberg: Beliebtheit ${b.sourceId} — ${(err as Error).message}`);
    }
  }
}
