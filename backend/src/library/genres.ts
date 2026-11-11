import type Database from "better-sqlite3";
import { RETRY_AFTER_MS, type TvmazeHit, UA, findShow, normalizeTitle } from "./artwork.js";

/**
 * Kategorien für Serien und Filme aus denselben freien Quellen wie die Plakate: Serien bei
 * TVmaze, Filme über Wikipedia bei Wikidata. An die Dienste geht nur der Titel;
 * ARTWORK_LOOKUP=off schaltet auch das ab.
 *
 * Vorher riet die App die Kategorie aus Stichwörtern im Titel — und was auf nichts passte,
 * landete bei »Doku & Wissen«. So standen Comedy-Serien unter Dokus.
 *
 * Gespeichert wird je Titel eine kommagetrennte Liste von Kategorie-Kennungen der App
 * (`comedy,drama`). Leer heißt: nachgeschlagen, nichts Passendes gefunden.
 */

type Fetch = typeof fetch;
type Log = { info: (o: object, m: string) => void; warn: (o: object, m: string) => void };

/** Kennungen der Kategorien, wie die App sie führt (domain/genre/Genre.kt). */
export const GENRES = [
  "action",
  "scifi",
  "anime",
  "docs",
  "comedy",
  "drama",
  "thriller",
  "gaming",
  "tech",
  "music",
] as const;
export type GenreId = (typeof GENRES)[number];

/** Höchstens so viele Kategorien je Titel — sonst steht er überall. */
const MAX_GENRES = 3;

/** Woran eine Genre-Angabe eines Dienstes erkannt wird. Die Reihenfolge entscheidet nichts. */
const RULES: [GenreId, RegExp][] = [
  ["anime", /\banime\b|\banimat(ion|ed)\b|\bcartoon\b/],
  ["docs", /\bdocumentar(y|ies)\b|\bdocuseries\b|\bnature\b/],
  ["comedy", /\bcomed(y|ies)\b|\bsitcom\b|\bsatire\b|\bparody\b|\bmockumentary\b/],
  [
    "thriller",
    /\bthriller\b|\bcrime\b|\bmystery\b|\bhorror\b|\bsuspense\b|\bnoir\b|\bdetective\b|\bslasher\b/,
  ],
  [
    "scifi",
    /\bscience[- ]fiction\b|\bsci-?fi\b|\bfantasy\b|\bsupernatural\b|\bsuperhero\b|\bdystopia/,
  ],
  ["action", /\baction\b|\badventure\b|\bwar\b|\bwestern\b|\bmartial arts\b|\bspy\b|\bdisaster\b/],
  ["drama", /\bdrama\b|\bromance\b|\bromantic\b|\bmelodrama\b|\bcoming-of-age\b|\bbiograph/],
  ["music", /\bmusical?\b|\bconcert\b/],
  ["gaming", /\besports\b|\bvideo games?\b/],
];

/**
 * Ordnet die Genre-Angaben eines Dienstes den Kategorien der App zu — in der Reihenfolge der
 * Angaben (das Hauptgenre steht dort vorn), höchstens [MAX_GENRES].
 * `type` ist die Art der Sendung bei TVmaze (»Animation«, »Documentary« …).
 */
export function mapGenres(raw: string[], type?: string | null): GenreId[] {
  const labels = [...raw];
  if (type === "Animation") labels.unshift("animation");
  if (type === "Documentary") labels.unshift("documentary");
  const out: GenreId[] = [];
  for (const label of labels) {
    const text = label.toLowerCase();
    for (const [genre, pattern] of RULES) {
      if (pattern.test(text) && !out.includes(genre)) out.push(genre);
    }
  }
  // Eine Doku über ein Verbrechen ist eine Doku, kein Krimi.
  if (out.includes("docs")) return ["docs"];
  // Anime ist eine eigene Welt: steht immer vorn und fällt nie der Obergrenze zum Opfer.
  if (out.includes("anime")) out.unshift(...out.splice(out.indexOf("anime"), 1));
  return out.slice(0, MAX_GENRES);
}

async function fetchJson<T>(fetchImpl: Fetch, url: string): Promise<T> {
  const res = await fetchImpl(url, { headers: UA });
  // Ein Dienst, der nicht antwortet, ist kein »nichts gefunden« — der Titel wird später erneut geprüft.
  if (!res.ok) throw new Error(`HTTP ${res.status} ${new URL(url).host}`);
  return (await res.json()) as T;
}

/** Genres einer Serie bei TVmaze; null, wenn TVmaze keine Serie genau dieses Namens kennt. */
async function tvmazeGenres(fetchImpl: Fetch, title: string): Promise<GenreId[] | null> {
  const hits = await fetchJson<TvmazeHit[]>(
    fetchImpl,
    `https://api.tvmaze.com/search/shows?q=${encodeURIComponent(title)}`,
  );
  const show = await findShow(fetchImpl, title, hits, { needImage: false });
  if (!show) return null;
  return mapGenres(show.genres ?? [], show.type);
}

interface WikiSummary {
  description?: string;
  wikibase_item?: string;
}

/**
 * Genres über Wikipedia und Wikidata: die Seite, die genau so heißt und laut Kurzbeschreibung
 * ein Film bzw. eine Serie ist; von dort die Genre-Angaben (P136) aus Wikidata.
 * null, wenn es keine solche Seite gibt.
 */
async function wikidataGenres(
  fetchImpl: Fetch,
  title: string,
  kind: "film" | "series",
): Promise<GenreId[] | null> {
  const word = kind === "film" ? "film" : "TV series";
  const search = await fetchJson<{ query?: { search?: { title: string }[] } }>(
    fetchImpl,
    `https://en.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=5&srsearch=${encodeURIComponent(`${title} ${word}`)}`,
  );
  const wanted = normalizeTitle(title);
  const isKind = kind === "film" ? /\bfilm\b/i : /\bseries\b|\bsitcom\b|\banime\b|\bshow\b/i;
  for (const hit of search.query?.search ?? []) {
    if (normalizeTitle(hit.title.replace(/\s*\([^)]*\)\s*$/, "")) !== wanted) continue;
    const summary = await fetchJson<WikiSummary>(
      fetchImpl,
      `https://en.wikipedia.org/api/rest_v1/page/summary/${encodeURIComponent(hit.title.replace(/ /g, "_"))}`,
    );
    if (!isKind.test(summary.description ?? "")) continue;
    const labels = summary.wikibase_item
      ? await wikidataGenreLabels(fetchImpl, summary.wikibase_item)
      : [];
    // Die Kurzbeschreibung (»2014 science fiction film«) nennt das Hauptgenre — sie zählt mit.
    return mapGenres([...labels, summary.description ?? ""]);
  }
  return null;
}

interface WikidataEntity {
  claims?: { P136?: { mainsnak?: { datavalue?: { value?: { id?: string } } } }[] };
  labels?: { en?: { value?: string } };
}

async function wikidataGenreLabels(fetchImpl: Fetch, item: string): Promise<string[]> {
  const api = "https://www.wikidata.org/w/api.php?action=wbgetentities&format=json";
  const entity = await fetchJson<{ entities?: Record<string, WikidataEntity> }>(
    fetchImpl,
    `${api}&props=claims&ids=${item}`,
  );
  const ids = (entity.entities?.[item]?.claims?.P136 ?? [])
    .map((claim) => claim.mainsnak?.datavalue?.value?.id)
    .filter((id): id is string => !!id)
    .slice(0, 12);
  if (ids.length === 0) return [];
  const labels = await fetchJson<{ entities?: Record<string, WikidataEntity> }>(
    fetchImpl,
    `${api}&props=labels&languages=en&ids=${ids.join("|")}`,
  );
  return ids.map((id) => labels.entities?.[id]?.labels?.en?.value).filter((l): l is string => !!l);
}

/** Kategorien einer Serie: TVmaze, ersatzweise Wikidata. Leer = nichts gefunden. */
export async function lookupSeriesGenres(fetchImpl: Fetch, title: string): Promise<GenreId[]> {
  const fromTvmaze = await tvmazeGenres(fetchImpl, title);
  if (fromTvmaze && fromTvmaze.length > 0) return fromTvmaze;
  return (await wikidataGenres(fetchImpl, title, "series")) ?? [];
}

/** Kategorien eines Films über Wikipedia/Wikidata. Leer = nichts gefunden. */
export async function lookupFilmGenres(fetchImpl: Fetch, title: string): Promise<GenreId[]> {
  return (await wikidataGenres(fetchImpl, title, "film")) ?? [];
}

export interface GenreOpts {
  db: Database.Database;
  log?: Log;
  fetchImpl?: Fetch;
  now?: number;
  /** Pause zwischen zwei Titeln im Sammellauf — Wikipedia bremst schnelle Folgen mit 429 aus. */
  pauseMs?: number;
}

const PAUSE_MS = 2_000;

/** Noch nie geprüft, oder ohne Ergebnis geprüft und das ist lange genug her. */
const DUE = "(genres IS NULL OR genres = '') AND COALESCE(genres_checked_at, 0) < ?";

/** Schlägt die Kategorien einer Serie nach, falls sie fällig ist. null = nichts zu tun gewesen. */
export async function ensureSeriesGenres(opts: GenreOpts, id: string): Promise<GenreId[] | null> {
  const now = opts.now ?? Date.now();
  const row = opts.db
    .prepare(`SELECT title FROM series WHERE id = ? AND ${DUE}`)
    .get(id, now - RETRY_AFTER_MS) as { title: string } | undefined;
  if (!row) return null;
  const genres = await lookupSeriesGenres(opts.fetchImpl ?? fetch, row.title);
  opts.db
    .prepare("UPDATE series SET genres = ?, genres_checked_at = ? WHERE id = ?")
    .run(genres.join(","), now, id);
  opts.log?.info({ series: id, genres }, "genre lookup");
  return genres;
}

/** Schlägt die Kategorien eines Films nach, falls er fällig ist. null = nichts zu tun gewesen. */
export async function ensureFilmGenres(opts: GenreOpts, id: string): Promise<GenreId[] | null> {
  const now = opts.now ?? Date.now();
  const row = opts.db
    .prepare(
      `SELECT title FROM videos
        WHERE id = ? AND series_id IS NULL AND (is_movie = 1 OR channel_id = 'manual') AND ${DUE}`,
    )
    .get(id, now - RETRY_AFTER_MS) as { title: string } | undefined;
  if (!row) return null;
  const genres = await lookupFilmGenres(opts.fetchImpl ?? fetch, row.title);
  opts.db
    .prepare("UPDATE videos SET genres = ?, genres_checked_at = ? WHERE id = ?")
    .run(genres.join(","), now, id);
  opts.log?.info({ video: id, genres }, "genre lookup");
  return genres;
}

export interface GenreResult {
  checked: number;
  found: number;
}

/** Sammellauf: alle Serien und Filme, deren Kategorien noch fehlen — jeder Titel höchstens einmal. */
export async function enrichGenres(opts: GenreOpts): Promise<GenreResult> {
  const now = opts.now ?? Date.now();
  const due = now - RETRY_AFTER_MS;
  const series = opts.db.prepare(`SELECT id FROM series WHERE ${DUE}`).all(due) as { id: string }[];
  const films = opts.db
    .prepare(
      `SELECT id FROM videos
        WHERE series_id IS NULL AND (is_movie = 1 OR channel_id = 'manual') AND ${DUE}`,
    )
    .all(due) as { id: string }[];

  const result: GenreResult = { checked: 0, found: 0 };
  const run = async (
    kind: "series" | "video",
    id: string,
    job: () => Promise<GenreId[] | null>,
  ) => {
    result.checked++;
    try {
      if ((await job())?.length) result.found++;
    } catch (err) {
      // Netz weg o. Ä.: nichts vermerken, der nächste Lauf versucht es wieder.
      opts.log?.warn({ err, [kind]: id }, "genre lookup failed");
    }
  };
  const pause = () => new Promise((resolve) => setTimeout(resolve, opts.pauseMs ?? PAUSE_MS));
  for (const { id } of series) {
    if (result.checked > 0) await pause();
    await run("series", id, () => ensureSeriesGenres({ ...opts, now }, id));
  }
  for (const { id } of films) {
    if (result.checked > 0) await pause();
    await run("video", id, () => ensureFilmGenres({ ...opts, now }, id));
  }
  if (result.checked > 0) opts.log?.info({ ...result }, "genre lookup done");
  return result;
}

// ── Sofort-Suche beim Import ─────────────────────────────────────────────────

let scout: { series: (id: string) => void; film: (id: string) => void } | null = null;

/** Schaltet die Sofort-Suche ein (Serverstart) oder mit `null` wieder aus (Tests). */
export function setGenreScout(next: typeof scout): void {
  scout = next;
}

/** Kategorien für eine Serie besorgen, sobald es sie gibt — tut nichts, solange die Sofort-Suche aus ist. */
export function scoutSeriesGenres(id: string | null | undefined): void {
  if (id) scout?.series(id);
}

/** Kategorien für einen Film besorgen — tut nichts, solange die Sofort-Suche aus ist. */
export function scoutFilmGenres(id: string): void {
  scout?.film(id);
}
