import type Database from "better-sqlite3";
import { categorize } from "./categories.js";
import type { PoliteHttp } from "./http.js";
import { bookId, markSourceSeen, upsertBook } from "./persist.js";
import { type EbookSource, SourceBlocked, type SourceCache } from "./sources/types.js";
import { isWholeBook } from "./whole-book.js";

export type SyncResult = Record<
  string,
  { found: number; added: number; skipped: number; error?: string }
>;

/** Merker einer Quelle in der Datenbank (ebook_source_cache). */
export function dbSourceCache(db: Database.Database, source: string): SourceCache {
  const get = db.prepare("SELECT value_json FROM ebook_source_cache WHERE source = ? AND key = ?");
  const set = db.prepare(
    `INSERT INTO ebook_source_cache (source, key, value_json, updated_at) VALUES (?, ?, ?, ?)
     ON CONFLICT(source, key) DO UPDATE SET value_json = excluded.value_json, updated_at = excluded.updated_at`,
  );
  return {
    get<T>(key: string): T | undefined {
      const row = get.get(source, key) as { value_json: string } | undefined;
      return row ? (JSON.parse(row.value_json) as T) : undefined;
    },
    set(key, value) {
      set.run(source, key, JSON.stringify(value), Date.now());
    },
  };
}

const DAY_MS = 24 * 60 * 60 * 1000;

/** Beim Serverstart abgleichen? Ja, wenn der Katalog leer ist oder der letzte Lauf über 24 h her ist. */
export function shouldSyncOnStart(db: Database.Database, now: number): boolean {
  const lastRun = dbSourceCache(db, "_sync").get<number>("lastRun");
  const count = (db.prepare("SELECT COUNT(*) AS n FROM ebooks").get() as { n: number }).n;
  return count === 0 || lastRun === undefined || now - lastRun > DAY_MS;
}

let running: Promise<SyncResult> | null = null;
let last: { at: number; result: SyncResult } | null = null;

export function ebookSyncState(): { running: boolean; lastRun?: number; result?: SyncResult } {
  return { running: running !== null, ...(last ? { lastRun: last.at, result: last.result } : {}) };
}

/**
 * Holt die Kataloge aller Quellen. Eine Quelle, die scheitert, ändert nichts an
 * ihren Einträgen — der letzte gute Stand bleibt. Läuft nie doppelt.
 */
export function runEbookSync(deps: {
  db: Database.Database;
  sources: EbookSource[];
  http: PoliteHttp;
  log: (msg: string) => void;
  now?: () => number;
}): Promise<SyncResult> {
  if (running) return running;
  running = (async () => {
    const now = deps.now ?? Date.now;
    const result: SyncResult = {};
    for (const source of deps.sources) {
      const r = { found: 0, added: 0, skipped: 0 } as SyncResult[string];
      result[source.id] = r;
      try {
        const books = await source.listCatalog({
          http: deps.http,
          cache: dbSourceCache(deps.db, source.id),
          log: deps.log,
        });
        r.found = books.length;
        const seen: string[] = [];
        const perCategory: Record<string, number> = {};
        for (const raw of books) {
          const whole = isWholeBook(raw);
          if (!whole.ok) {
            r.skipped++;
            continue;
          }
          const cats = categorize(raw.source, raw.subjects);
          const { id, isNew } = upsertBook(deps.db, raw, cats, now());
          seen.push(id);
          if (isNew) r.added++;
          for (const c of cats) perCategory[c] = (perCategory[c] ?? 0) + 1;
        }
        // Übersprungene gelten trotzdem als „noch da“ — nur Fehlendes bekommt missing_since.
        for (const raw of books)
          if (!isWholeBook(raw).ok) seen.push(bookId(raw.source, raw.sourceId));
        markSourceSeen(deps.db, source.id, seen, now());
        deps.log(
          `ebooks: ${source.id} — ${r.found} gefunden, ${r.added} neu, ${r.skipped} übersprungen; ${Object.entries(
            perCategory,
          )
            .sort((a, b) => b[1] - a[1])
            .map(([c, n]) => `${c} ${n}`)
            .join(", ")}`,
        );
      } catch (err) {
        r.error =
          err instanceof SourceBlocked ? `gesperrt (${err.status})` : (err as Error).message;
        deps.log(`ebooks: ${source.id} — ${r.error} (Einträge bleiben wie sie sind)`);
      }
    }
    last = { at: now(), result };
    dbSourceCache(deps.db, "_sync").set("lastRun", last.at);
    return result;
  })().finally(() => {
    running = null;
  });
  return running;
}
