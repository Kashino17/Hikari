import type Database from "better-sqlite3";
import { execa } from "execa";
import type { PoliteHttp } from "./http.js";

/**
 * Titelbilder: Gutenberg erzeugt für Bücher ohne erfassten Deckel ein Muster-Bild (weißes Kopffeld,
 * knallige Flächen, „Project Gutenberg“) — bei ~85 % der deutschen Bücher. Solche Bilder erkennen
 * wir und holen stattdessen den Deckel einer deutschen Ausgabe aus Open Library.
 */

/** Gemessen an echten Covern (19.11.): erzeugte haben Buntheit 147–216 in der Mitte, echte 0–43. */
const GENERATED_MIN_COLORFUL = 120;
const GENERATED_MIN_TOP = 190;

export async function isGeneratedCover(image: Buffer): Promise<boolean> {
  const r = await execa(
    "ffmpeg",
    [
      "-v",
      "error",
      "-i",
      "pipe:0",
      "-vf",
      "scale=20:30",
      "-f",
      "rawvideo",
      "-pix_fmt",
      "rgb24",
      "pipe:1",
    ],
    {
      input: image,
      encoding: "buffer",
      reject: false,
      timeout: 20_000,
    },
  );
  const px = Buffer.from(r.stdout as Uint8Array);
  if (r.exitCode !== 0 || px.length < 20 * 30 * 3) return false;
  const at = (x: number, y: number) => {
    const i = (y * 20 + x) * 3;
    return [px[i] ?? 0, px[i + 1] ?? 0, px[i + 2] ?? 0] as const;
  };
  let top = 0;
  let n = 0;
  for (let y = 0; y < 4; y++)
    for (let x = 0; x < 20; x++) {
      const [r1, g, b] = at(x, y);
      top += (r1 + g + b) / 3;
      n++;
    }
  let colorful = 0;
  let m = 0;
  for (let y = 10; y < 26; y++)
    for (let x = 0; x < 20; x++) {
      const [r1, g, b] = at(x, y);
      colorful += Math.max(r1, g, b) - Math.min(r1, g, b);
      m++;
    }
  return top / n > GENERATED_MIN_TOP && colorful / m > GENERATED_MIN_COLORFUL;
}

interface OlEdition {
  key?: string;
  cover_i?: number;
  language?: string[];
}
interface OlDoc extends OlEdition {
  cover_edition_key?: string;
  editions?: { docs?: OlEdition[] };
}

/** Nur rein deutsche Ausgaben — zweisprachige oder übersetzte Ausgaben zeigen fremde Deckel. */
const onlyGerman = (langs?: string[]) => !!langs?.length && langs.every((l) => l === "ger");

const coverUrl = (id: number) => `https://covers.openlibrary.org/b/id/${id}-L.jpg`;

/** Deckel einer deutschen Ausgabe aus Open Library — oder null. */
export async function findOpenLibraryCover(
  http: PoliteHttp,
  title: string,
  authors: string[],
): Promise<string | null> {
  try {
    const q = new URLSearchParams({
      title: title.replace(/\s*[:(].*$/, "").slice(0, 120),
      lang: "de",
      fields:
        "key,cover_i,cover_edition_key,language,editions,editions.key,editions.cover_i,editions.language",
      limit: "5",
    });
    if (authors[0]) q.set("author", authors[0]);
    const r = await http.get(`https://openlibrary.org/search.json?${q}`, {
      accept: "application/json",
      tries: 1,
    });
    if (r.status !== 200) return null;
    const docs = (JSON.parse(r.body.toString("utf8")) as { docs?: OlDoc[] }).docs ?? [];
    // 1. Eine deutsche Ausgabe mit Deckel.
    for (const d of docs)
      for (const e of d.editions?.docs ?? [])
        if (e.cover_i && onlyGerman(e.language)) return coverUrl(e.cover_i);
    // 2. Der Deckel des Werks — nur, wenn die Ausgabe dahinter deutsch ist.
    for (const d of docs) {
      if (!d.cover_i || !d.cover_edition_key || !d.language?.includes("ger")) continue;
      const e = await http.get(`https://openlibrary.org/books/${d.cover_edition_key}.json`, {
        accept: "application/json",
        tries: 1,
      });
      if (e.status !== 200) continue;
      const ed = JSON.parse(e.body.toString("utf8")) as { languages?: { key: string }[] };
      if (onlyGerman(ed.languages?.map((l) => l.key.replace("/languages/", ""))))
        return coverUrl(d.cover_i);
    }
    return null;
  } catch {
    return null;
  }
}

export type CoverResult = { status: "source" | "openlibrary"; url: string } | { status: "none" };

/** Prüft das Titelbild eines Buchs einmal und merkt sich das Ergebnis. */
export async function resolveCover(
  db: Database.Database,
  http: PoliteHttp,
  id: string,
): Promise<CoverResult> {
  const b = db.prepare("SELECT title, authors_json, cover_url FROM ebooks WHERE id = ?").get(id) as
    | { title: string; authors_json: string; cover_url: string | null }
    | undefined;
  if (!b) return { status: "none" };
  let result: CoverResult | null = null;
  if (b.cover_url) {
    const img = await http.get(b.cover_url, { tries: 1 }).catch(() => null);
    if (img?.status === 200 && img.body.length > 100 && !(await isGeneratedCover(img.body))) {
      result = { status: "source", url: b.cover_url };
    }
  }
  if (!result) {
    const ol = await findOpenLibraryCover(http, b.title, JSON.parse(b.authors_json) as string[]);
    result = ol ? { status: "openlibrary", url: ol } : { status: "none" };
  }
  db.prepare("UPDATE ebooks SET cover_status = ?, cover_alt_url = ? WHERE id = ?").run(
    result.status,
    result.status === "openlibrary" ? result.url : null,
    id,
  );
  return result;
}

/** Ungeprüfte Titelbilder nach und nach prüfen — die beliebtesten zuerst. */
export async function resolvePendingCovers(
  db: Database.Database,
  http: PoliteHttp,
  opts: { limit: number; log: (msg: string) => void },
): Promise<number> {
  const ids = db
    .prepare(
      `SELECT id FROM ebooks WHERE cover_status IS NULL AND hidden_reason IS NULL
       ORDER BY popularity IS NULL, popularity DESC, added_at DESC LIMIT ?`,
    )
    .all(opts.limit) as { id: string }[];
  const counts: Record<string, number> = {};
  for (const { id } of ids) {
    const r = await resolveCover(db, http, id).catch(() => null);
    if (r) counts[r.status] = (counts[r.status] ?? 0) + 1;
  }
  if (ids.length) opts.log(`ebooks: Titelbilder geprüft — ${JSON.stringify(counts)}`);
  return ids.length;
}
