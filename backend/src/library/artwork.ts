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

export const UA = { "User-Agent": "HikariServer/1.0 (privater Medienserver)" };
/** Erfolglose Suche erst nach zwei Wochen wiederholen — neue Einträge tauchen bei den Diensten nicht stündlich auf. */
export const RETRY_AFTER_MS = 14 * 24 * 60 * 60 * 1000;
/** Breiten, auf die die Bilder fürs Handy verkleinert werden. */
export const POSTER_WIDTH = 600;
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

export interface TvmazeHit {
  show: {
    id: number;
    name: string;
    image?: { original?: string } | null;
    /** Genre-Angaben und Art der Sendung — für die Kategorien (library/genres.ts). */
    genres?: string[];
    type?: string | null;
  };
}

/**
 * Wählt aus den TVmaze-Treffern die Serie, deren Name genau passt. Bewusst
 * streng: lieber kein Plakat als das einer fremden Serie („Ted" ≠ „Ted Lasso").
 */
export function pickShow(
  title: string,
  hits: TvmazeHit[],
  needImage = true,
): TvmazeHit["show"] | null {
  const wanted = normalizeTitle(title);
  if (!wanted) return null;
  return (
    hits
      .map((h) => h.show)
      .find((s) => normalizeTitle(s.name) === wanted && (!needImage || s.image?.original)) ?? null
  );
}

async function fetchJson<T>(fetchImpl: Fetch, url: string): Promise<T | null> {
  const res = await fetchImpl(url, { headers: UA });
  if (!res.ok) return null;
  return (await res.json()) as T;
}

export async function download(
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

export function safeName(id: string): string {
  return id.replace(/[^A-Za-z0-9_-]/g, "_").slice(0, 80);
}

/** Wie viele Suchtreffer auf ihre anderen Namen geprüft werden, wenn keiner genau so heißt. */
const AKA_CANDIDATES = 3;

/**
 * Die Serie zu einem Titel. Zuerst zählt der Name selbst; passt keiner,
 * die anderen Namen der ersten Treffer („Haus des Geldes" führt TVmaze als
 * „La Casa de Papel" — der deutsche Titel steht dort unter den Zweitnamen).
 * Auch hier gilt nur ein genau passender Name.
 */
export async function findShow(
  fetchImpl: Fetch,
  title: string,
  hits: TvmazeHit[],
  /** Für Plakate muss die Serie ein Bild haben; für die Kategorien reicht der Eintrag. */
  opts: { needImage?: boolean } = {},
): Promise<TvmazeHit["show"] | null> {
  const needImage = opts.needImage ?? true;
  const exact = pickShow(title, hits, needImage);
  if (exact) return exact;
  const wanted = normalizeTitle(title);
  if (!wanted) return null;
  const candidates = hits.filter((h) => !needImage || h.show.image?.original);
  for (const { show } of candidates.slice(0, AKA_CANDIDATES)) {
    const akas = await fetchJson<{ name?: string }[]>(
      fetchImpl,
      `https://api.tvmaze.com/shows/${show.id}/akas`,
    );
    if (akas?.some((aka) => normalizeTitle(aka.name ?? "") === wanted)) return show;
  }
  return null;
}

async function lookupSeries(
  fetchImpl: Fetch,
  title: string,
): Promise<{ poster: string; backdrop: string | null } | null> {
  const hits = await fetchJson<TvmazeHit[]>(
    fetchImpl,
    `https://api.tvmaze.com/search/shows?q=${encodeURIComponent(title)}`,
  );
  const show = hits ? await findShow(fetchImpl, title, hits) : null;
  if (!show?.image?.original) return null;
  const images = await fetchJson<{ type: string; resolutions?: { original?: { url?: string } } }[]>(
    fetchImpl,
    `https://api.tvmaze.com/shows/${show.id}/images`,
  );
  const backdrop = images?.find((i) => i.type === "background")?.resolutions?.original?.url ?? null;
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

/** Plakat und Hintergrund einer Serie suchen und in den Cover-Ordner legen. */
async function fetchSeriesArtwork(
  fetchImpl: Fetch,
  coverDir: string,
  id: string,
  title: string,
): Promise<{ poster: string | null; backdrop: string | null }> {
  const found = await lookupSeries(fetchImpl, title);
  let poster: string | null = null;
  let backdrop: string | null = null;
  if (found) {
    const name = safeName(id);
    if (
      await download(fetchImpl, found.poster, join(coverDir, `poster_${name}.jpg`), POSTER_WIDTH)
    ) {
      poster = `/covers/poster_${name}.jpg`;
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
  return { poster, backdrop };
}

/** Filmplakat suchen und in den Cover-Ordner legen. */
async function fetchFilmPoster(
  fetchImpl: Fetch,
  coverDir: string,
  id: string,
  title: string,
): Promise<string | null> {
  const url = await lookupFilm(fetchImpl, title);
  if (!url) return null;
  const name = safeName(id);
  return (await download(fetchImpl, url, join(coverDir, `poster_${name}.jpg`), POSTER_WIDTH))
    ? `/covers/poster_${name}.jpg`
    : null;
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
      const { poster, backdrop } = await fetchSeriesArtwork(fetchImpl, coverDir, s.id, s.title);
      if (poster) result.seriesFound++;
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
      const poster = await fetchFilmPoster(fetchImpl, coverDir, f.id, f.title);
      if (poster) result.filmsFound++;
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

// ── Sofort-Suche beim Import ─────────────────────────────────────────────────

/**
 * Sucht das Plakat in dem Moment, in dem ein Import beginnt — nicht erst beim
 * nächsten Sammellauf. Eine neue Serie hat ihr Plakat damit meist schon, bevor
 * die erste Folge fertig geladen ist.
 *
 * Der Haken: Solange der Download läuft, gibt es die Serie in der Datenbank
 * noch gar nicht (sie entsteht erst mit der ersten fertigen Folge). Das
 * Ergebnis der Suche wird deshalb im Speicher gehalten und eingetragen, sobald
 * die Zeile da ist — der Import meldet sich dafür am Ende noch einmal.
 */
export class ArtworkScout {
  private readonly fetchImpl: Fetch;
  /** Alle Aufträge nacheinander: schont die Dienste und verhindert, dass zwei dieselbe Datei schreiben. */
  private chain: Promise<unknown> = Promise.resolve();
  private readonly series = new Map<
    string,
    { poster: string | null; backdrop: string | null; at: number }
  >();
  private readonly films = new Map<string, { poster: string | null; at: number }>();

  constructor(
    private readonly opts: {
      db: Database.Database;
      coverDir: string;
      log?: Log;
      fetchImpl?: Fetch;
      now?: () => number;
    },
  ) {
    this.fetchImpl = opts.fetchImpl ?? fetch;
  }

  /** Reiht einen beliebigen Auftrag (z. B. den Sammellauf) in dieselbe Schlange ein. */
  run<T>(job: () => Promise<T>): Promise<T> {
    const next = this.chain.then(job, job);
    this.chain = next.catch(() => {});
    return next;
  }

  /** Wartet, bis alle bisher eingereihten Aufträge durch sind (für Tests). */
  idle(): Promise<void> {
    return this.chain.then(() => {});
  }

  /** [title] zählt nur, solange es die Serie noch nicht gibt — sonst gilt ihr gespeicherter Name. */
  seriesArtwork(id: string, title: string): void {
    void this.run(() => this.seriesJob(id, title)).catch((err) =>
      this.opts.log?.warn({ err, series: id }, "artwork lookup failed"),
    );
  }

  film(id: string, title: string): void {
    void this.run(() => this.filmJob(id, title)).catch((err) =>
      this.opts.log?.warn({ err, video: id }, "artwork lookup failed"),
    );
  }

  private now(): number {
    return this.opts.now?.() ?? Date.now();
  }

  private async seriesJob(id: string, title: string): Promise<void> {
    const { db, coverDir } = this.opts;
    const now = this.now();
    const row = db
      .prepare("SELECT title, poster_url, artwork_checked_at FROM series WHERE id = ?")
      .get(id) as
      | { title: string; poster_url: string | null; artwork_checked_at: number | null }
      | undefined;
    if (row?.poster_url) return;

    let found = this.series.get(id);
    if (found && now - found.at >= RETRY_AFTER_MS) found = undefined;
    if (!found) {
      // Schon vom Sammellauf geprüft und nichts gefunden: nicht bei jeder Folge wieder fragen.
      if (row && (row.artwork_checked_at ?? 0) >= now - RETRY_AFTER_MS) return;
      const art = await fetchSeriesArtwork(this.fetchImpl, coverDir, id, row?.title ?? title);
      found = { ...art, at: now };
      this.series.set(id, found);
      this.opts.log?.info({ series: id, found: !!art.poster }, "artwork lookup on import");
    }
    // Gibt es die Serie noch nicht, trifft das UPDATE nichts — das Ergebnis bleibt
    // gemerkt und wird beim zweiten Aufruf (nach dem Anlegen) eingetragen.
    db.prepare(
      "UPDATE series SET poster_url = ?, backdrop_url = ?, artwork_checked_at = ? WHERE id = ? AND poster_url IS NULL",
    ).run(found.poster, found.backdrop, found.at, id);
  }

  private async filmJob(id: string, title: string): Promise<void> {
    const { db, coverDir } = this.opts;
    const now = this.now();
    const row = db
      .prepare("SELECT title, series_id, poster_url, artwork_checked_at FROM videos WHERE id = ?")
      .get(id) as
      | {
          title: string;
          series_id: string | null;
          poster_url: string | null;
          artwork_checked_at: number | null;
        }
      | undefined;
    if (row?.poster_url || row?.series_id) return;

    let found = this.films.get(id);
    if (found && now - found.at >= RETRY_AFTER_MS) found = undefined;
    // Unter dem vorläufigen Titel nichts gefunden, jetzt steht der endgültige fest: noch ein Versuch.
    if (found && !found.poster && row && normalizeTitle(row.title) !== normalizeTitle(title)) {
      found = undefined;
    }
    if (!found) {
      if (row && (row.artwork_checked_at ?? 0) >= now - RETRY_AFTER_MS) return;
      const poster = await fetchFilmPoster(this.fetchImpl, coverDir, id, row?.title ?? title);
      found = { poster, at: now };
      this.films.set(id, found);
      this.opts.log?.info({ video: id, found: !!poster }, "artwork lookup on import");
    }
    db.prepare(
      "UPDATE videos SET poster_url = ?, artwork_checked_at = ? WHERE id = ? AND poster_url IS NULL",
    ).run(found.poster, found.at, id);
  }
}

let scout: ArtworkScout | null = null;

/** Schaltet die Sofort-Suche ein (Serverstart) oder mit `null` wieder aus (Tests). */
export function setArtworkScout(next: ArtworkScout | null): void {
  scout = next;
}

/** Plakat für eine Serie besorgen — tut nichts, solange die Sofort-Suche aus ist. */
export function scoutSeriesArtwork(
  id: string | null | undefined,
  title: string | null | undefined,
): void {
  if (scout && id) scout.seriesArtwork(id, title?.trim() || id);
}

/** Plakat für einen Film besorgen — tut nichts, solange die Sofort-Suche aus ist. */
export function scoutFilmArtwork(id: string, title: string | null | undefined): void {
  if (scout && title?.trim()) scout.film(id, title.trim());
}
