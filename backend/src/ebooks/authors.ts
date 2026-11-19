import type Database from "better-sqlite3";
import type { PoliteHttp } from "./http.js";
import { normalizeForSearch } from "./persist.js";

/** Porträts der Autoren: Open Library führt für viele klassische Autoren ein Foto. */
const OL_SEARCH = "https://openlibrary.org/search/authors.json";
const OL_PHOTO = (olid: string) =>
  `https://covers.openlibrary.org/a/olid/${olid}-M.jpg?default=false`;
const RECHECK_MS = 30 * 24 * 60 * 60 * 1000;

const same = (a: string, b: string) =>
  normalizeForSearch(a)
    .replace(/[^a-z0-9]+/g, " ")
    .trim() ===
  normalizeForSearch(b)
    .replace(/[^a-z0-9]+/g, " ")
    .trim();

/** Foto des Autors mit genau diesem Namen (der mit den meisten Werken), sonst null. */
export async function findAuthorPhoto(http: PoliteHttp, name: string): Promise<string | null> {
  try {
    const r = await http.get(`${OL_SEARCH}?q=${encodeURIComponent(name)}&limit=8`, { tries: 1 });
    if (r.status !== 200) return null;
    const docs = (JSON.parse(r.body.toString("utf8")).docs ?? []) as {
      key: string;
      name?: string;
      work_count?: number;
    }[];
    const best = docs
      .filter((d) => d.name && same(d.name, name))
      .sort((a, b) => (b.work_count ?? 0) - (a.work_count ?? 0))[0];
    if (!best) return null;
    const url = OL_PHOTO(best.key.replace(/^\/authors\//, ""));
    const photo = await http.get(url, { tries: 1 });
    return photo.status === 200 && photo.body.length > 200 ? url : null;
  } catch {
    return null;
  }
}

/** url = Foto gefunden, null = geprüft und keins, undefined = noch nie geprüft. */
export function authorPhoto(db: Database.Database, name: string): string | null | undefined {
  const r = db.prepare("SELECT url FROM ebook_author_photos WHERE name = ?").get(name) as
    | { url: string | null }
    | undefined;
  return r ? r.url : undefined;
}

export function saveAuthorPhoto(
  db: Database.Database,
  name: string,
  url: string | null,
  now = Date.now(),
): void {
  db.prepare(
    `INSERT INTO ebook_author_photos (name, url, checked_at) VALUES (?, ?, ?)
     ON CONFLICT(name) DO UPDATE SET url = excluded.url, checked_at = excluded.checked_at`,
  ).run(name, url, now);
}

/** Autoren ab zwei Büchern, die meisten zuerst, die noch kein Ergebnis haben (ohne Foto: nach 30 Tagen neu). */
export async function resolvePendingAuthorPhotos(
  db: Database.Database,
  http: PoliteHttp,
  opts: { limit: number; log: (msg: string) => void; now?: number },
): Promise<number> {
  const now = opts.now ?? Date.now();
  const counts = new Map<string, number>();
  for (const r of db
    .prepare("SELECT authors_json FROM ebooks WHERE hidden_reason IS NULL")
    .all() as { authors_json: string }[])
    for (const a of JSON.parse(r.authors_json) as string[]) counts.set(a, (counts.get(a) ?? 0) + 1);
  const checked = new Map(
    (
      db.prepare("SELECT name, url, checked_at FROM ebook_author_photos").all() as {
        name: string;
        url: string | null;
        checked_at: number;
      }[]
    ).map((r) => [r.name, r]),
  );
  const due = [...counts]
    .filter(([name, n]) => {
      if (n < 2) return false;
      const c = checked.get(name);
      return !c || (c.url === null && c.checked_at < now - RECHECK_MS);
    })
    .sort((a, b) => b[1] - a[1])
    .slice(0, opts.limit);
  let found = 0;
  for (const [name] of due) {
    const url = await findAuthorPhoto(http, name);
    saveAuthorPhoto(db, name, url, now);
    if (url) found++;
  }
  if (due.length) opts.log(`ebooks: Autorenfotos geprüft — ${found} von ${due.length} gefunden`);
  return due.length;
}
