import { join } from "node:path";
import type Database from "better-sqlite3";
import {
  POSTER_WIDTH,
  RETRY_AFTER_MS,
  UA,
  download,
  normalizeTitle,
  safeName,
} from "../library/artwork.js";

/**
 * Titelbilder für Manga-Reihen — dasselbe Prinzip wie die Plakate für Serien und Filme
 * (library/artwork.ts): Die Reihe wird bei AniList nachgeschlagen, ersatzweise bei Kitsu,
 * das Bild landet lokal im Cover-Ordner. An die Dienste geht nur der Titel;
 * ARTWORK_LOOKUP=off schaltet auch das ab.
 *
 * Gesucht wird nur für Reihen, die sich selbst als »ohne Cover« ausweisen:
 * `cover_path` leer und seit zwei Wochen nicht erfolglos gesucht (`cover_checked_at`).
 * Der Server geht dafür nie alle Reihen durch — die Datenbank liefert genau diese Zeilen.
 */

type Fetch = typeof fetch;
type Log = { info: (o: object, m: string) => void; warn: (o: object, m: string) => void };

export interface MangaCoverOpts {
  db: Database.Database;
  coverDir: string;
  log?: Log;
  fetchImpl?: Fetch;
  now?: number;
}

/** Ein Suchtreffer, egal von welchem Dienst. */
export interface MangaCandidate {
  /** Haupttitel (englisch, Umschrift, Original). */
  names: string[];
  /** Zweitnamen und Abkürzungen — zählen nur, wenn kein Haupttitel passt. */
  aliases: string[];
  cover: string | null;
  /** Fortlaufende Reihe (kein Roman, kein One-Shot). */
  series: boolean;
}

/**
 * Wählt den Treffer, dessen Name genau passt. Bewusst streng: lieber kein Cover als das
 * einer fremden Reihe (»One Piece« ≠ »One Piece Party«). Haupttitel gehen vor Zweitnamen,
 * fortlaufende Reihen vor Romanen und One-Shots.
 */
export function pickManga(title: string, candidates: MangaCandidate[]): MangaCandidate | null {
  const wanted = normalizeTitle(title);
  if (!wanted) return null;
  const usable = candidates.filter((c) => c.cover);
  const matches = (names: string[]) => names.some((n) => normalizeTitle(n) === wanted);
  const byName = usable.filter((c) => matches(c.names));
  const pool = byName.length > 0 ? byName : usable.filter((c) => matches(c.aliases));
  return pool.find((c) => c.series) ?? pool[0] ?? null;
}

const ANILIST_QUERY = "query($s:String){Page(perPage:8){media(search:$s,type:MANGA,sort:SEARCH_MATCH){format title{romaji english native} synonyms coverImage{extraLarge large}}}}";

interface AniListMedia {
  format?: string | null;
  title?: { romaji?: string | null; english?: string | null; native?: string | null };
  synonyms?: (string | null)[] | null;
  coverImage?: { extraLarge?: string | null; large?: string | null };
}

const strings = (values: (string | null | undefined)[]): string[] =>
  values.filter((v): v is string => typeof v === "string" && v.length > 0);

/** Ein Dienst, der nicht antwortet, ist kein »nichts gefunden« — deshalb wirft das hier. */
async function searchAniList(fetchImpl: Fetch, title: string): Promise<MangaCandidate[]> {
  const res = await fetchImpl("https://graphql.anilist.co", {
    method: "POST",
    headers: { ...UA, "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify({ query: ANILIST_QUERY, variables: { s: title } }),
  });
  if (!res.ok) throw new Error(`AniList HTTP ${res.status}`);
  const body = (await res.json()) as { data?: { Page?: { media?: AniListMedia[] } } };
  return (body.data?.Page?.media ?? []).map((m) => ({
    names: strings([m.title?.english, m.title?.romaji, m.title?.native]),
    aliases: strings(m.synonyms ?? []),
    cover: m.coverImage?.extraLarge ?? m.coverImage?.large ?? null,
    series: m.format !== "NOVEL" && m.format !== "ONE_SHOT",
  }));
}

interface KitsuManga {
  attributes?: {
    subtype?: string | null;
    canonicalTitle?: string | null;
    titles?: Record<string, string | null> | null;
    abbreviatedTitles?: (string | null)[] | null;
    posterImage?: { original?: string | null; large?: string | null } | null;
  };
}

async function searchKitsu(fetchImpl: Fetch, title: string): Promise<MangaCandidate[]> {
  const res = await fetchImpl(
    `https://kitsu.io/api/edge/manga?filter[text]=${encodeURIComponent(title)}&page[limit]=8`,
    { headers: { ...UA, Accept: "application/vnd.api+json" } },
  );
  if (!res.ok) throw new Error(`Kitsu HTTP ${res.status}`);
  const body = (await res.json()) as { data?: KitsuManga[] };
  return (body.data ?? []).map(({ attributes: a }) => ({
    names: strings([a?.canonicalTitle, ...Object.values(a?.titles ?? {})]),
    aliases: strings(a?.abbreviatedTitles ?? []),
    cover: a?.posterImage?.original ?? a?.posterImage?.large ?? null,
    series: a?.subtype !== "novel" && a?.subtype !== "oneshot",
  }));
}

/**
 * Adresse des Titelbilds zu einem Reihentitel, oder null, wenn keine Reihe genau so heißt.
 * Wirft, wenn ein Dienst nicht erreichbar war und der andere nichts fand — dann ist offen,
 * ob es ein Cover gibt, und die Reihe soll es beim nächsten Lauf noch einmal versuchen.
 */
export async function lookupMangaCover(fetchImpl: Fetch, title: string): Promise<string | null> {
  let failure: unknown = null;
  for (const search of [searchAniList, searchKitsu]) {
    try {
      const hit = pickManga(title, await search(fetchImpl, title));
      if (hit?.cover) return hit.cover;
    } catch (err) {
      failure = err;
    }
  }
  if (failure) throw failure;
  return null;
}

/** Dateiname des Titelbilds einer Reihe im Cover-Ordner. */
export function mangaCoverFile(seriesId: string): string {
  return `manga_${safeName(seriesId)}.jpg`;
}

/** Nur solche Namen liefert die Cover-Route aus (kein Pfad, nichts außerhalb des Ordners). */
export function isMangaCoverFile(file: string): boolean {
  return /^manga_[A-Za-z0-9_-]{1,80}\.jpg$/.test(file);
}

const MISSING = "(cover_path IS NULL OR cover_path = '')";

/**
 * Besorgt das Titelbild für eine Reihe, falls sie keins hat und nicht gerade erst erfolglos
 * gesucht wurde. Gibt zurück, ob jetzt eins da ist; `null` heißt: nichts zu tun gewesen.
 */
export async function ensureMangaCover(opts: MangaCoverOpts, id: string): Promise<boolean | null> {
  const { db, coverDir } = opts;
  const now = opts.now ?? Date.now();
  const row = db
    .prepare(
      `SELECT title FROM manga_series
        WHERE id = ? AND ${MISSING} AND COALESCE(cover_checked_at, 0) < ?`,
    )
    .get(id, now - RETRY_AFTER_MS) as { title: string } | undefined;
  if (!row) return null;

  const fetchImpl = opts.fetchImpl ?? fetch;
  const url = await lookupMangaCover(fetchImpl, row.title);
  let file: string | null = null;
  if (url) {
    file = mangaCoverFile(id);
    // Bild gefunden, aber nicht ladbar: kein »geprüft« vermerken, der nächste Lauf versucht es wieder.
    if (!(await download(fetchImpl, url, join(coverDir, file), POSTER_WIDTH))) {
      throw new Error(`cover download failed: ${url}`);
    }
  }
  db.prepare(
    `UPDATE manga_series SET cover_path = ?, cover_checked_at = ? WHERE id = ? AND ${MISSING}`,
  ).run(file, now, id);
  opts.log?.info({ manga: id, found: !!file }, "manga cover lookup");
  return !!file;
}

export interface MangaCoverResult {
  checked: number;
  found: number;
}

/** Sammellauf: alle Reihen, die sich als »ohne Cover« ausweisen — jede höchstens einmal. */
export async function enrichMangaCovers(opts: MangaCoverOpts): Promise<MangaCoverResult> {
  const now = opts.now ?? Date.now();
  const due = opts.db
    .prepare(`SELECT id FROM manga_series WHERE ${MISSING} AND COALESCE(cover_checked_at, 0) < ?`)
    .all(now - RETRY_AFTER_MS) as { id: string }[];
  const result: MangaCoverResult = { checked: 0, found: 0 };
  for (const { id } of due) {
    result.checked++;
    try {
      if (await ensureMangaCover({ ...opts, now }, id)) result.found++;
    } catch (err) {
      // Netz weg o. Ä.: nicht als »geprüft« vermerken, der nächste Lauf versucht es wieder.
      opts.log?.warn({ err, manga: id }, "manga cover lookup failed");
    }
  }
  return result;
}

// ── Sofort-Suche beim Abgleich ───────────────────────────────────────────────

let scout: ((seriesId: string) => void) | null = null;

/** Schaltet die Sofort-Suche ein (Serverstart) oder mit `null` wieder aus (Tests). */
export function setMangaCoverScout(next: ((seriesId: string) => void) | null): void {
  scout = next;
}

/** Titelbild für eine Reihe besorgen — tut nichts, solange die Sofort-Suche aus ist. */
export function scoutMangaCover(seriesId: string): void {
  scout?.(seriesId);
}
