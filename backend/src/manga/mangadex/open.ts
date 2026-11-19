import { copyFileSync, existsSync, mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import type Database from "better-sqlite3";
import { mangaCoverFile } from "../cover.js";
import { upsertChapter, upsertSeries } from "../persist.js";
import { getCatalogRow } from "./catalog.js";
import type { MangadexClient } from "./client.js";
import { type MdChapterEntry, SOURCE, chaptersFromFeed } from "./map.js";

/**
 * Eine Reihe aus dem Katalog in den Leser holen: manga_series anlegen, Titelbild
 * übernehmen, Kapitel-Feed (deutsch + englisch) nach manga_chapters schreiben. Die Seiten
 * lädt später der gewohnte Kapitel-Abgleich (POST /api/manga/chapters/:id/sync).
 *
 * Kapitel-Kennung: `mangadex:<uuid>:<lang>:<nummer>` — für den Rest des Servers ist
 * `<uuid>:<lang>` der Slug (persist.chapterId), so bleiben Seiten-Kennungen und der
 * Kapitel-Abgleich unverändert.
 */

const FEED_TTL_MS = 6 * 60 * 60 * 1000;
const FEED_PAGE = 500;
/** MangaDex erlaubt offset + limit höchstens 10.000. */
const FEED_MAX = 10_000;

export class CatalogNotFoundError extends Error {
  constructor(id: string) {
    super(`unbekannter Katalog-Eintrag: ${id}`);
    this.name = "CatalogNotFoundError";
  }
}

export interface OpenDeps {
  db: Database.Database;
  client: MangadexClient;
  coverDir?: string;
  now?: () => number;
  log?: (msg: string) => void;
}

const uuidOf = (id: string) => id.slice(SOURCE.length + 1);

const coverLoads = new Map<string, Promise<string | null>>();

/**
 * Titelbild eines Katalog-Eintrags: einmal bei MangaDex holen, dann von Platte.
 * Gibt den Dateipfad zurück oder `null`, wenn es keins gibt.
 */
export function ensureCatalogCover(
  deps: { db: Database.Database; client: MangadexClient; coverDir: string },
  id: string,
): Promise<string | null> {
  const row = getCatalogRow(deps.db, id);
  if (!row?.cover_url) return Promise.resolve(null);
  const name = row.cover_url.split("/").pop() ?? "cover.jpg";
  const dir = join(deps.coverDir, "manga-catalog");
  const file = join(
    dir,
    `${uuidOf(id).replace(/[^A-Za-z0-9-]/g, "_")}_${name.replace(/[^A-Za-z0-9._-]/g, "_")}`,
  );
  if (existsSync(file)) return Promise.resolve(file);
  const running = coverLoads.get(file);
  if (running) return running;
  const url = row.cover_url;
  const p = (async () => {
    const r = await deps.client.image(url);
    if (r.status !== 200 || r.body.length < 100) return null;
    mkdirSync(dir, { recursive: true });
    writeFileSync(file, r.body);
    return file;
  })().finally(() => coverLoads.delete(file));
  coverLoads.set(file, p);
  return p;
}

interface FeedResponse {
  data: MdChapterEntry[];
  total: number;
  limit: number;
  offset: number;
}

/** Holt den ganzen Feed und schreibt die Kapitel. Gibt die Zahl lesbarer Kapitel zurück. */
async function refreshFeed(deps: OpenDeps, seriesId: string, now: number): Promise<number> {
  const uuid = uuidOf(seriesId);
  const entries: MdChapterEntry[] = [];
  let offset = 0;
  while (offset + FEED_PAGE <= FEED_MAX) {
    const page = await deps.client.api<FeedResponse>(`/manga/${uuid}/feed`, {
      "translatedLanguage[]": ["de", "en"],
      "order[chapter]": "asc",
      limit: FEED_PAGE,
      offset,
      "includes[]": ["scanlation_group"],
    });
    const data = page.data ?? [];
    entries.push(...data);
    offset += data.length;
    if (data.length === 0 || offset >= (page.total ?? 0)) break;
  }
  const chapters = chaptersFromFeed(entries);
  const { db } = deps;
  db.transaction(() => {
    const ids: string[] = [];
    for (const c of chapters) {
      ids.push(
        upsertChapter(db, {
          source: SOURCE,
          seriesSlug: `${uuid}:${c.lang}`,
          seriesId,
          number: c.number,
          ...(c.title ? { title: c.title } : {}),
          sourceUrl: c.uuid,
          pageCount: c.pages,
          isAvailable: true,
          ...(c.publishedAt !== null ? { publishedAt: c.publishedAt } : {}),
          lang: c.lang,
          scanlationGroup: c.group,
        }),
      );
    }
    // Was MangaDex nicht mehr anbietet, bleibt sichtbar, ist aber nicht mehr ladbar.
    const keep = new Set(ids);
    const old = db.prepare("SELECT id FROM manga_chapters WHERE series_id = ?").all(seriesId) as {
      id: string;
    }[];
    const off = db.prepare("UPDATE manga_chapters SET is_available = 0 WHERE id = ?");
    for (const o of old) if (!keep.has(o.id)) off.run(o.id);
    db.prepare("UPDATE manga_series SET total_chapters = ?, last_synced_at = ? WHERE id = ?").run(
      chapters.length,
      now,
      seriesId,
    );
  })();
  return chapters.length;
}

const opening = new Map<string, Promise<{ seriesId: string }>>();

export function openCatalogSeries(deps: OpenDeps, id: string): Promise<{ seriesId: string }> {
  const running = opening.get(id);
  if (running) return running;
  const p = doOpen(deps, id).finally(() => opening.delete(id));
  opening.set(id, p);
  return p;
}

async function doOpen(deps: OpenDeps, id: string): Promise<{ seriesId: string }> {
  const { db } = deps;
  const now = (deps.now ?? Date.now)();
  const row = getCatalogRow(db, id);
  if (!row || row.source !== SOURCE) throw new CatalogNotFoundError(id);
  const uuid = uuidOf(id);
  const authors = JSON.parse(row.authors_json) as string[];

  const seriesId = upsertSeries(db, {
    source: SOURCE,
    sourceSlug: uuid,
    title: row.title_de ?? row.title,
    sourceUrl: `https://mangadex.org/title/${uuid}`,
    ...(authors.length ? { author: authors.join(", ") } : {}),
    ...(row.description ? { description: row.description } : {}),
    ...(row.status === "completed" || row.status === "ongoing" ? { status: row.status } : {}),
  });

  // Titelbild gleich vom Katalog übernehmen (die AniList-Suche bleibt der Rückfall).
  const series = db
    .prepare(
      "SELECT cover_path AS coverPath, last_synced_at AS lastSync FROM manga_series WHERE id = ?",
    )
    .get(seriesId) as { coverPath: string | null; lastSync: number | null };
  if (!series.coverPath && deps.coverDir) {
    try {
      const cached = await ensureCatalogCover(
        { db, client: deps.client, coverDir: deps.coverDir },
        id,
      );
      if (cached) {
        const file = mangaCoverFile(seriesId);
        copyFileSync(cached, join(deps.coverDir, file));
        db.prepare("UPDATE manga_series SET cover_path = ? WHERE id = ?").run(file, seriesId);
      }
    } catch (err) {
      deps.log?.(`manga-katalog: Titelbild für ${id} fehlt (${(err as Error).message})`);
    }
  }

  if (series.lastSync === null || now - series.lastSync > FEED_TTL_MS) {
    try {
      await refreshFeed(deps, seriesId, now);
    } catch (err) {
      const has = db
        .prepare("SELECT COUNT(*) AS n FROM manga_chapters WHERE series_id = ?")
        .get(seriesId) as { n: number };
      // Ohne ein einziges Kapitel taugt die Reihe nichts — dann soll die App es erfahren.
      if (has.n === 0) throw err;
      deps.log?.(`manga-katalog: Kapitel von ${id} nicht aufgefrischt (${(err as Error).message})`);
    }
  }
  return { seriesId };
}
