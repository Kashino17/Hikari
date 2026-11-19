import type Database from "better-sqlite3";
import type { MangadexClient } from "./client.js";
import {
  type CatalogRow,
  LANGS,
  type Lang,
  type MdManga,
  catalogFromManga,
  normalizeSearch,
} from "./map.js";

export { normalizeSearch };

/**
 * MangaDex-Katalog: nachts die beliebtesten Titel mit deutschen und mit englischen
 * Kapiteln holen (je bis zu 1.500, sortiert nach Followern) und in manga_catalog ablegen.
 * Von dort stöbert die App; geöffnet wird eine Reihe erst in mangadex/open.ts.
 */

const DAY_MS = 24 * 60 * 60 * 1000;
/** Wie oft die Cover auf den Genre-Kacheln wechseln. */
const COLLAGE_MS = 6 * 60 * 60 * 1000;

export function upsertCatalog(db: Database.Database, row: CatalogRow): void {
  db.prepare(
    `INSERT INTO manga_catalog
       (id, source, title, title_de, authors_json, tags_json, status, year, kind, langs_json,
        last_chapter, cover_url, description, popularity, content_rating, search_text,
        updated_at, synced_at, missing_since)
     VALUES
       (@id, @source, @title, @title_de, @authors_json, @tags_json, @status, @year, @kind, @langs_json,
        @last_chapter, @cover_url, @description, @popularity, @content_rating, @search_text,
        @updated_at, @synced_at, NULL)
     ON CONFLICT(id) DO UPDATE SET
       title = excluded.title,
       title_de = excluded.title_de,
       authors_json = excluded.authors_json,
       tags_json = excluded.tags_json,
       status = excluded.status,
       year = excluded.year,
       kind = excluded.kind,
       langs_json = excluded.langs_json,
       last_chapter = excluded.last_chapter,
       cover_url = excluded.cover_url,
       description = excluded.description,
       popularity = COALESCE(excluded.popularity, manga_catalog.popularity),
       content_rating = excluded.content_rating,
       search_text = excluded.search_text,
       updated_at = excluded.updated_at,
       synced_at = excluded.synced_at,
       missing_since = NULL`,
  ).run({
    id: row.id,
    source: row.source,
    title: row.title,
    title_de: row.titleDe,
    authors_json: JSON.stringify(row.authors),
    tags_json: JSON.stringify(row.tags),
    status: row.status,
    year: row.year,
    kind: row.kind,
    langs_json: JSON.stringify(row.langs),
    last_chapter: row.lastChapter,
    cover_url: row.coverUrl,
    description: row.description,
    popularity: row.popularity,
    content_rating: row.contentRating,
    search_text: row.searchText,
    updated_at: row.updatedAt,
    synced_at: row.syncedAt,
  });
}

function setMeta(db: Database.Database, key: string, value: unknown, now: number): void {
  db.prepare(
    `INSERT INTO manga_catalog_meta (key, value, updated_at) VALUES (?, ?, ?)
     ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at`,
  ).run(key, JSON.stringify(value), now);
}

function getMeta<T>(db: Database.Database, key: string): T | undefined {
  const row = db.prepare("SELECT value FROM manga_catalog_meta WHERE key = ?").get(key) as
    | { value: string }
    | undefined;
  return row ? (JSON.parse(row.value) as T) : undefined;
}

/** Zeitpunkt des letzten vollständigen Laufs. */
export function catalogLastRun(db: Database.Database): number | undefined {
  return getMeta<number>(db, "lastRun");
}

/** Beim Start abgleichen? Ja, wenn der Katalog leer ist oder der letzte Lauf über 24 h her ist. */
export function shouldSyncCatalogOnStart(db: Database.Database, now: number): boolean {
  const lastRun = catalogLastRun(db);
  const n = (db.prepare("SELECT COUNT(*) AS n FROM manga_catalog").get() as { n: number }).n;
  return n === 0 || lastRun === undefined || now - lastRun > DAY_MS;
}

export interface CatalogSyncResult {
  de: number;
  en: number;
  total: number;
  error?: string;
}

interface MangaListResponse {
  data: MdManga[];
  total: number;
  limit: number;
  offset: number;
}

interface StatisticsResponse {
  statistics?: Record<string, { follows?: number | null } | undefined>;
}

let running: Promise<CatalogSyncResult> | null = null;
let last: { at: number; result: CatalogSyncResult } | null = null;

export function catalogSyncState(): { running: boolean; lastResult?: CatalogSyncResult } {
  return { running: running !== null, ...(last ? { lastResult: last.result } : {}) };
}

/**
 * Ein Lauf: erst deutsch, dann englisch. Fehler brechen ab, ohne etwas als verschwunden zu
 * markieren; geschriebene Zeilen bleiben. Läuft nie doppelt.
 */
export function runMangaCatalogSync(deps: {
  db: Database.Database;
  client: MangadexClient;
  log?: (msg: string) => void;
  now?: () => number;
  maxPerLang?: number;
  pageSize?: number;
}): Promise<CatalogSyncResult> {
  if (running) return running;
  running = (async () => {
    const { db, client } = deps;
    const now = deps.now ?? Date.now;
    const max = deps.maxPerLang ?? 1500;
    const pageSize = deps.pageSize ?? 100;
    const started = now();
    const result: CatalogSyncResult = { de: 0, en: 0, total: 0 };
    const seen = new Set<string>();
    const withStats = new Set<string>();

    try {
      for (const lang of LANGS) {
        let offset = 0;
        while (offset < max) {
          const limit = Math.min(pageSize, max - offset);
          const page = await client.api<MangaListResponse>("/manga", {
            limit,
            offset,
            "availableTranslatedLanguage[]": [lang],
            "order[followedCount]": "desc",
            "includes[]": ["cover_art", "author", "artist"],
            "contentRating[]": ["safe", "suggestive"],
            hasAvailableChapters: "true",
          });
          const data = page.data ?? [];
          // Follower in einem Rutsch je Seite — nur für Titel, die dieser Lauf noch nicht gefragt hat.
          const ask = data.map((m) => m.id).filter((id) => !withStats.has(id));
          const follows = new Map<string, number>();
          if (ask.length > 0) {
            try {
              const st = await client.api<StatisticsResponse>("/statistics/manga", {
                "manga[]": ask,
              });
              for (const [id, s] of Object.entries(st.statistics ?? {})) {
                if (typeof s?.follows === "number") follows.set(id, s.follows);
              }
              for (const id of ask) withStats.add(id);
            } catch (err) {
              deps.log?.(`manga-katalog: Statistik fehlt (${(err as Error).message})`);
            }
          }
          const t = now();
          db.transaction(() => {
            for (const m of data) {
              const row = catalogFromManga(m, t);
              row.popularity = follows.get(m.id) ?? null;
              upsertCatalog(db, row);
              seen.add(row.id);
            }
          })();
          result[lang as Lang] += data.length;
          offset += data.length;
          if (data.length < limit || offset >= (page.total ?? 0)) break;
        }
      }
    } catch (err) {
      result.error = (err as Error).message;
      result.total = seen.size;
      deps.log?.(`manga-katalog: abgebrochen — ${result.error} (Einträge bleiben wie sie sind)`);
      last = { at: now(), result };
      return result;
    }

    // Vollständig durchgelaufen: wer nicht mehr dabei war, bekommt eine Marke (bleibt aber sichtbar).
    db.prepare(
      `UPDATE manga_catalog SET missing_since = ?
        WHERE source = 'mangadex' AND synced_at < ? AND missing_since IS NULL`,
    ).run(now(), started);
    result.total = seen.size;
    const at = now();
    setMeta(db, "lastRun", at, at);
    last = { at, result };
    deps.log?.(
      `manga-katalog: ${result.total} Titel (${result.de} mit deutschen, ${result.en} mit englischen Kapiteln)`,
    );
    return result;
  })().finally(() => {
    running = null;
  });
  return running;
}

// ── Abfragen für die App ─────────────────────────────────────────────────────

export type LangFilter = "all" | Lang;

export interface CatalogDbRow {
  id: string;
  source: string;
  title: string;
  title_de: string | null;
  authors_json: string;
  tags_json: string;
  status: string | null;
  year: number | null;
  kind: string;
  langs_json: string;
  last_chapter: string | null;
  cover_url: string | null;
  description: string | null;
  popularity: number | null;
  content_rating: string | null;
  readable_de: number | null;
  readable_en: number | null;
}

export interface CatalogCard {
  id: string;
  title: string;
  titleDe?: string | undefined;
  authors: string[];
  tags: string[];
  status?: string | undefined;
  kind: string;
  langs: string[];
  lastChapter?: string | undefined;
  coverUrl?: string | undefined;
  source: string;
  /** Lesbare Kapitel je Sprache. */
  chapters: { de: number; en: number };
  /** false = bei MangaDex keine lesbaren Kapitel (nur beim Verlag). */
  readable: boolean;
}

export function toCard(r: CatalogDbRow): CatalogCard {
  return {
    id: r.id,
    title: r.title,
    titleDe: r.title_de ?? undefined,
    authors: JSON.parse(r.authors_json) as string[],
    tags: JSON.parse(r.tags_json) as string[],
    status: r.status ?? undefined,
    kind: r.kind,
    // Nur Sprachen mit wirklich lesbaren Kapiteln — nicht MangaDex' Angabe (die zählt auch Links zu Verlagen).
    langs: [
      ...((r.readable_de ?? 0) > 0 ? ["de"] : []),
      ...((r.readable_en ?? 0) > 0 ? ["en"] : []),
    ],
    lastChapter: r.last_chapter ?? undefined,
    coverUrl: r.cover_url ? `/api/manga/catalog/${encodeURIComponent(r.id)}/cover` : undefined,
    source: r.source,
    chapters: { de: r.readable_de ?? 0, en: r.readable_en ?? 0 },
    readable: (r.readable_de ?? 0) + (r.readable_en ?? 0) > 0,
  };
}

const COLS =
  "c.id, c.source, c.title, c.title_de, c.authors_json, c.tags_json, c.status, c.year, c.kind, c.langs_json, c.last_chapter, c.cover_url, c.description, c.popularity, c.content_rating, c.readable_de, c.readable_en";
/** Nur geprüfte Reihen mit mindestens einem lesbaren Kapitel. */
const READABLE =
  "c.readable_at IS NOT NULL AND (COALESCE(c.readable_de, 0) > 0 OR COALESCE(c.readable_en, 0) > 0)";

const SORT: Record<string, string> = {
  pop: "c.popularity IS NULL, c.popularity DESC, c.title COLLATE NOCASE",
  az: "c.title COLLATE NOCASE",
  new: "c.updated_at IS NULL, c.updated_at DESC, c.title COLLATE NOCASE",
};

export function parseLang(v: string | undefined): LangFilter {
  return v === "de" || v === "en" ? v : "all";
}

interface Where {
  sql: string[];
  params: (string | number)[];
}

function langWhere(w: Where, lang: LangFilter): void {
  if (lang === "all") return;
  w.sql.push(lang === "de" ? "COALESCE(c.readable_de, 0) > 0" : "COALESCE(c.readable_en, 0) > 0");
}

export interface ListFilter {
  lang?: LangFilter;
  genre?: string;
  /** Eine Art oder mehrere, mit Komma getrennt. */
  kind?: string;
  status?: string;
  q?: string;
  sort?: string;
  offset?: number;
  limit?: number;
  /** Mindestzahl lesbarer deutscher Kapitel (Regal „Auf Deutsch“). */
  minDe?: number;
}

export function listCatalog(
  db: Database.Database,
  f: ListFilter,
): { total: number; items: CatalogCard[] } {
  // Bei einer Suche auch geprüfte Reihen ohne lesbare Kapitel finden (lizenziert) — markiert und hinten.
  const searching = !!f.q?.trim() && (f.lang ?? "all") === "all";
  const w: Where = { sql: [searching ? "c.readable_at IS NOT NULL" : READABLE], params: [] };
  langWhere(w, f.lang ?? "all");
  if (f.minDe) {
    w.sql.push("COALESCE(c.readable_de, 0) >= ?");
    w.params.push(f.minDe);
  }
  if (f.genre) {
    const genres = f.genre.split(",").filter(Boolean);
    w.sql.push(
      `EXISTS (SELECT 1 FROM json_each(c.tags_json) WHERE value IN (${genres.map(() => "?").join(",")}))`,
    );
    w.params.push(...genres);
  }
  if (f.kind) {
    const kinds = f.kind.split(",").filter(Boolean);
    w.sql.push(`c.kind IN (${kinds.map(() => "?").join(",")})`);
    w.params.push(...kinds);
  }
  if (f.status) {
    w.sql.push("c.status = ?");
    w.params.push(f.status);
  }
  if (f.q) {
    const tokens = normalizeSearch(f.q).split(" ").filter(Boolean).slice(0, 8);
    if (tokens.length === 0) return { total: 0, items: [] };
    for (const t of tokens) {
      w.sql.push("c.search_text LIKE ? ESCAPE '\\'");
      w.params.push(`%${t.replace(/[\\%_]/g, (m) => `\\${m}`)}%`);
    }
  }
  const from = `FROM manga_catalog c WHERE ${w.sql.join(" AND ")}`;
  // Suche: Treffer im (deutschen) Titel vor Treffern nur in Nebentiteln oder bei Autoren.
  const titleFirst = f.q?.trim() && (f.sort ?? "pop") === "pop" ? f.q.trim().toLowerCase() : "";
  const limit = Math.min(Math.max(Number(f.limit) || 30, 1), 100);
  const offset = Math.max(Number(f.offset) || 0, 0);
  const total = (db.prepare(`SELECT COUNT(*) AS n ${from}`).get(...w.params) as { n: number }).n;
  const rows = db
    .prepare(
      `SELECT ${COLS} ${from} ORDER BY ${searching ? "(COALESCE(c.readable_de, 0) + COALESCE(c.readable_en, 0)) = 0, " : ""}${titleFirst ? "(instr(lower(c.title), ?) > 0 OR instr(lower(COALESCE(c.title_de, '')), ?) > 0) DESC, " : ""}${SORT[f.sort ?? "pop"] ?? SORT.pop} LIMIT ? OFFSET ?`,
    )
    .all(
      ...w.params,
      ...(titleFirst ? [titleFirst, titleFirst] : []),
      limit,
      offset,
    ) as CatalogDbRow[];
  return { total, items: rows.map(toCard) };
}

interface ShelfDef {
  key: string;
  title: string;
  subtitle: string;
  filter: ListFilter;
}

const SHELVES: ShelfDef[] = [
  {
    key: "de",
    title: "Auf Deutsch",
    subtitle: "Mindestens 10 Kapitel auf Deutsch",
    filter: { lang: "de", minDe: 10 },
  },
  { key: "popular", title: "Beliebt", subtitle: "Die meisten Follower", filter: {} },
  {
    key: "completed",
    title: "Am Stück lesen",
    subtitle: "Abgeschlossen — kein Warten auf das nächste Kapitel",
    filter: { status: "completed" },
  },
  {
    key: "romcom",
    title: "Romantik & Komödie",
    subtitle: "Herz und Humor",
    filter: { genre: "Romantik,Komödie" },
  },
  {
    key: "action",
    title: "Action & Kämpfe",
    subtitle: "Wenn es kracht",
    filter: { genre: "Action" },
  },
  {
    key: "isekai",
    title: "Andere Welten",
    subtitle: "Isekai und Wiedergeburt",
    filter: { genre: "Isekai" },
  },
  {
    key: "webtoon",
    title: "Manhwa & Webcomics",
    subtitle: "Farbig, zum Scrollen",
    filter: { kind: "Manhwa,Manhua,Webcomic" },
  },
];

export function catalogHome(
  db: Database.Database,
  lang: LangFilter,
  perShelf = 20,
  now = Date.now(),
): {
  shelves: { key: string; title: string; subtitle: string; items: CatalogCard[] }[];
  genres: { name: string; count: number; covers: string[] }[];
} {
  const shelves = SHELVES
    // Wer »nur Englisch« gewählt hat, bekommt kein Regal »Auf Deutsch«.
    .filter((s) => !(s.filter.lang && lang !== "all" && s.filter.lang !== lang))
    .map((s) => ({
      key: s.key,
      title: s.title,
      subtitle: s.subtitle,
      items: listCatalog(db, { ...s.filter, lang: s.filter.lang ?? lang, limit: perShelf }).items,
    }))
    .filter((s) => s.items.length > 0);

  const w: Where = { sql: [READABLE], params: [] };
  langWhere(w, lang);
  const genres = db
    .prepare(
      `SELECT t.value AS name, COUNT(*) AS count
         FROM manga_catalog c, json_each(c.tags_json) t
        WHERE ${w.sql.join(" AND ")}
        GROUP BY t.value ORDER BY count DESC, name`,
    )
    .all(...w.params) as { name: string; count: number }[];

  // Collage je Genre: drei Cover aus den beliebtesten 24, alle sechs Stunden andere, keins doppelt.
  const window = Math.floor(now / COLLAGE_MS);
  const used = new Set<string>();
  const pool = db.prepare(
    `SELECT c.id FROM manga_catalog c, json_each(c.tags_json) t
      WHERE ${w.sql.join(" AND ")} AND t.value = ? AND c.cover_url IS NOT NULL
      ORDER BY c.popularity IS NULL, c.popularity DESC LIMIT 24`,
  );
  const withCovers = genres.map((g) => {
    const ids = (pool.all(...w.params, g.name) as { id: string }[]).map((r) => r.id);
    const covers: string[] = [];
    const start = ids.length ? (window * 3) % ids.length : 0;
    for (let k = 0; k < ids.length && covers.length < 3; k++) {
      const id = ids[(start + k) % ids.length] as string;
      if (used.has(id)) continue;
      used.add(id);
      covers.push(`/api/manga/catalog/${encodeURIComponent(id)}/cover`);
    }
    return { ...g, covers };
  });
  return { shelves, genres: withCovers };
}

export function getCatalogRow(db: Database.Database, id: string): CatalogDbRow | undefined {
  return db.prepare(`SELECT ${COLS} FROM manga_catalog c WHERE c.id = ?`).get(id) as
    | CatalogDbRow
    | undefined;
}

// ── Lesbare Kapitel zählen ───────────────────────────────────────────────────

const RECHECK_MS = 7 * DAY_MS;
let checking: Promise<number> | null = null;

/**
 * Zählt je Reihe und Sprache die Kapitel, die man bei MangaDex wirklich lesen kann (ohne Links zu
 * Verlagen, ohne leere). Beliebteste zuerst; geprüfte Reihen erst nach einer Woche wieder.
 * Gibt die Zahl geprüfter Reihen zurück.
 */
export function checkReadableChapters(deps: {
  db: Database.Database;
  client: MangadexClient;
  limit?: number;
  now?: () => number;
  log?: (msg: string) => void;
}): Promise<number> {
  if (checking) return checking;
  checking = (async () => {
    const { db, client } = deps;
    const now = deps.now ?? Date.now;
    const due = db
      .prepare(
        `SELECT id, langs_json FROM manga_catalog
          WHERE source = 'mangadex' AND (readable_at IS NULL OR readable_at < ?)
          ORDER BY readable_at IS NOT NULL, popularity IS NULL, popularity DESC LIMIT ?`,
      )
      .all(now() - RECHECK_MS, deps.limit ?? 4000) as { id: string; langs_json: string }[];
    const save = db.prepare(
      "UPDATE manga_catalog SET readable_de = ?, readable_en = ?, readable_at = ? WHERE id = ?",
    );
    let done = 0;
    for (const row of due) {
      const uuid = row.id.slice("mangadex:".length);
      const langs = JSON.parse(row.langs_json) as string[];
      const count: Record<string, number> = { de: 0, en: 0 };
      try {
        for (const lang of ["de", "en"]) {
          if (!langs.includes(lang)) continue;
          const r = await client.api<{ total?: number }>("/chapter", {
            manga: uuid,
            "translatedLanguage[]": [lang],
            limit: 1,
            includeExternalUrl: 0,
            includeEmptyPages: 0,
            includeFuturePublishAt: 0,
          });
          count[lang] = r.total ?? 0;
        }
      } catch (err) {
        deps.log?.(`manga-katalog: Kapitel-Prüfung abgebrochen — ${(err as Error).message}`);
        break;
      }
      save.run(count.de, count.en, now(), row.id);
      done++;
    }
    if (done > 0) deps.log?.(`manga-katalog: lesbare Kapitel für ${done} Reihen gezählt`);
    return done;
  })().finally(() => {
    checking = null;
  });
  return checking;
}

/** Gibt es Reihen, deren Kapitel noch nie gezählt wurden? */
export function hasUncheckedCatalog(db: Database.Database): boolean {
  return !!db.prepare("SELECT 1 FROM manga_catalog WHERE readable_at IS NULL LIMIT 1").get();
}
