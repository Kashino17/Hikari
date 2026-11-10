import { rename, unlink, writeFile } from "node:fs/promises";
import { join } from "node:path";
import type Database from "better-sqlite3";
import { execa } from "execa";

/**
 * Echte Plakate statt Standbildern: Serien werden bei TVmaze nachgeschlagen
 * (Plakat hochkant + Hintergrundbild quer), Filme in der englischen Wikipedia
 * (Filmplakat). Die Bilder landen lokal im Cover-Ordner; an die Dienste geht
 * nur der Titel. Wer das nicht will, setzt ARTWORK_LOOKUP=off.
 *
 * Ein von Hand gesetztes Cover bleibt unangetastet — Plakat und Hintergrund
 * liegen in eigenen Spalten daneben.
 */

const UA = { "User-Agent": "HikariServer/1.0 (privater Medienserver)" };
/** Erfolglose Suche erst nach zwei Wochen wiederholen — neue Einträge tauchen bei den Diensten nicht stündlich auf. */
const RETRY_AFTER_MS = 14 * 24 * 60 * 60 * 1000;
/** Breiten, auf die die Bilder fürs Handy verkleinert werden. */
const POSTER_WIDTH = 600;
const BACKDROP_WIDTH = 1280;

type Fetch = typeof fetch;
type Log = { info: (o: object, m: string) => void; warn: (o: object, m: string) => void };

/** „Dragon Ball: Super!" und „dragonball super" gelten als derselbe Titel. */
export function normalizeTitle(title: string): string {
  return title
    .toLowerCase()
    .normalize("NFKD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/[^a-z0-9]/g, "");
}

interface TvmazeHit {
  show: { id: number; name: string; image?: { original?: string } | null };
}

/**
 * Wählt aus den TVmaze-Treffern die Serie, deren Name genau passt. Bewusst
 * streng: lieber kein Plakat als das einer fremden Serie („Ted" ≠ „Ted Lasso").
 */
export function pickShow(title: string, hits: TvmazeHit[]): TvmazeHit["show"] | null {
  const wanted = normalizeTitle(title);
  if (!wanted) return null;
  return hits.map((h) => h.show).find((s) => normalizeTitle(s.name) === wanted && s.image?.original) ?? null;
}

async function fetchJson<T>(fetchImpl: Fetch, url: string): Promise<T | null> {
  const res = await fetchImpl(url, { headers: UA });
  if (!res.ok) return null;
  return (await res.json()) as T;
}

async function download(
  fetchImpl: Fetch,
  url: string,
  target: string,
  maxWidth: number,
): Promise<boolean> {
  const res = await fetchImpl(url, { headers: UA });
  if (!res.ok) return false;
  const bytes = Buffer.from(await res.arrayBuffer());
  // Schutz vor Fehlerseiten und Platzhaltern: ein echtes Plakat ist größer.
  if (bytes.length < 4_000) return false;
  await writeFile(target, bytes);
  await shrink(target, maxWidth);
  return true;
}

/**
 * Originale sind teils mehrere MB groß — fürs Handy auf [maxWidth] verkleinern.
 * Scheitert ffmpeg (nicht installiert, kaputtes Bild), bleibt das Original liegen.
 */
async function shrink(file: string, maxWidth: number): Promise<void> {
  const tmp = `${file}.tmp.jpg`;
  try {
    await execa(
      "ffmpeg",
      ["-v", "error", "-i", file, "-vf", `scale='min(${maxWidth},iw)':-2`, "-q:v", "4", "-y", tmp],
      { timeout: 30_000 },
    );
    await rename(tmp, file);
  } catch {
    await unlink(tmp).catch(() => {});
  }
}

function safeName(id: string): string {
  return id.replace(/[^A-Za-z0-9_-]/g, "_").slice(0, 80);
}

async function lookupSeries(
  fetchImpl: Fetch,
  title: string,
): Promise<{ poster: string; backdrop: string | null } | null> {
  const hits = await fetchJson<TvmazeHit[]>(
    fetchImpl,
    `https://api.tvmaze.com/search/shows?q=${encodeURIComponent(title)}`,
  );
  const show = hits ? pickShow(title, hits) : null;
  if (!show?.image?.original) return null;
  const images = await fetchJson<
    { type: string; resolutions?: { original?: { url?: string } } }[]
  >(fetchImpl, `https://api.tvmaze.com/shows/${show.id}/images`);
  const backdrop =
    images?.find((i) => i.type === "background")?.resolutions?.original?.url ?? null;
  return { poster: show.image.original, backdrop };
}

async function lookupFilm(fetchImpl: Fetch, title: string): Promise<string | null> {
  const search = await fetchJson<{ query?: { search?: { title: string }[] } }>(
    fetchImpl,
    `https://en.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=5&srsearch=${encodeURIComponent(`${title} film`)}`,
  );
  const wanted = normalizeTitle(title);
  for (const hit of search?.query?.search ?? []) {
    // „Interstellar (film)" → „interstellar"; nur Seiten, deren Name der Titel ist.
    if (normalizeTitle(hit.title.replace(/\s*\([^)]*\)\s*$/, "")) !== wanted) continue;
    const summary = await fetchJson<{
      description?: string;
      originalimage?: { source?: string };
    }>(
      fetchImpl,
      `https://en.wikipedia.org/api/rest_v1/page/summary/${encodeURIComponent(hit.title.replace(/ /g, "_"))}`,
    );
    if (summary?.originalimage?.source && /\bfilm\b/i.test(summary.description ?? "")) {
      return summary.originalimage.source;
    }
  }
  return null;
}

export interface ArtworkResult {
  seriesFound: number;
  filmsFound: number;
  checked: number;
}

/** Sucht für alles ohne Plakat eines; pro Lauf jeder Titel höchstens einmal. */
export async function enrichArtwork(opts: {
  db: Database.Database;
  coverDir: string;
  log?: Log;
  fetchImpl?: Fetch;
  now?: number;
}): Promise<ArtworkResult> {
  const { db, coverDir } = opts;
  const fetchImpl = opts.fetchImpl ?? fetch;
  const now = opts.now ?? Date.now();
  const due = now - RETRY_AFTER_MS;
  const result: ArtworkResult = { seriesFound: 0, filmsFound: 0, checked: 0 };

  const series = db
    .prepare(
      `SELECT id, title FROM series
        WHERE poster_url IS NULL AND COALESCE(artwork_checked_at, 0) < ?`,
    )
    .all(due) as { id: string; title: string }[];
  for (const s of series) {
    result.checked++;
    try {
      const found = await lookupSeries(fetchImpl, s.title);
      let poster: string | null = null;
      let backdrop: string | null = null;
      if (found) {
        const name = safeName(s.id);
        if (await download(fetchImpl, found.poster, join(coverDir, `poster_${name}.jpg`), POSTER_WIDTH)) {
          poster = `/covers/poster_${name}.jpg`;
          result.seriesFound++;
        }
        if (
          found.backdrop &&
          (await download(
            fetchImpl,
            found.backdrop,
            join(coverDir, `backdrop_${name}.jpg`),
            BACKDROP_WIDTH,
          ))
        ) {
          backdrop = `/covers/backdrop_${name}.jpg`;
        }
      }
      db.prepare(
        "UPDATE series SET poster_url = ?, backdrop_url = ?, artwork_checked_at = ? WHERE id = ?",
      ).run(poster, backdrop, now, s.id);
    } catch (err) {
      // Netz weg o. Ä.: nicht als „geprüft" vermerken, der nächste Lauf versucht es wieder.
      opts.log?.warn({ err, series: s.id }, "artwork lookup failed");
    }
  }

  const films = db
    .prepare(
      `SELECT id, title FROM videos
        WHERE series_id IS NULL AND (is_movie = 1 OR channel_id = 'manual')
          AND poster_url IS NULL AND COALESCE(artwork_checked_at, 0) < ?`,
    )
    .all(due) as { id: string; title: string }[];
  for (const f of films) {
    result.checked++;
    try {
      const url = await lookupFilm(fetchImpl, f.title);
      let poster: string | null = null;
      if (url) {
        const name = safeName(f.id);
        if (await download(fetchImpl, url, join(coverDir, `poster_${name}.jpg`), POSTER_WIDTH)) {
          poster = `/covers/poster_${name}.jpg`;
          result.filmsFound++;
        }
      }
      db.prepare("UPDATE videos SET poster_url = ?, artwork_checked_at = ? WHERE id = ?").run(
        poster,
        now,
        f.id,
      );
    } catch (err) {
      opts.log?.warn({ err, video: f.id }, "artwork lookup failed");
    }
  }

  if (result.checked > 0) opts.log?.info({ ...result }, "artwork lookup done");
  return result;
}
