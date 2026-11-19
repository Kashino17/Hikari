/**
 * Reine Umrechnung von MangaDex-Antworten in unsere Zeilen — ohne Netz, ohne Datenbank.
 * Feldnamen wie in der API-Doku (https://api.mangadex.org/docs/) und in den
 * aufgezeichneten Antworten unter tests/fixtures/manga/mangadex/.
 */

type LocalizedString = Record<string, string>;

export interface MdRelationship {
  id: string;
  type: string;
  attributes?: { name?: string; fileName?: string } & Record<string, unknown>;
}

export interface MdTag {
  id: string;
  type: string;
  attributes: { name: LocalizedString; group: string };
}

export interface MdManga {
  id: string;
  type: string;
  attributes: {
    title: LocalizedString;
    altTitles: LocalizedString[];
    description: LocalizedString;
    originalLanguage: string;
    lastChapter: string | null;
    status: string | null;
    year: number | null;
    contentRating: string;
    tags: MdTag[];
    availableTranslatedLanguages: (string | null)[];
    updatedAt?: string;
  } & Record<string, unknown>;
  relationships: MdRelationship[];
}

export interface MdChapterEntry {
  id: string;
  type: string;
  attributes: {
    chapter: string | null;
    title: string | null;
    translatedLanguage: string;
    externalUrl: string | null;
    isUnavailable?: boolean;
    pages: number;
    publishAt?: string;
    readableAt?: string;
  } & Record<string, unknown>;
  relationships: MdRelationship[];
}

export interface MdAtHome {
  result: string;
  baseUrl: string;
  chapter: { hash: string; data: string[]; dataSaver: string[] };
}

export const SOURCE = "mangadex";
export const LANGS = ["de", "en"] as const;
export type Lang = (typeof LANGS)[number];

/** Deutsche Genres in fester Reihenfolge (so stehen sie auch auf den Karten). */
export const GENRES = [
  "Action",
  "Abenteuer",
  "Romantik",
  "Komödie",
  "Drama",
  "Fantasy",
  "Alltag",
  "Sport",
  "Sci-Fi",
  "Psycho",
  "Thriller",
  "Horror",
  "Mystery",
  "Historisch",
  "Schule",
  "Isekai",
  "Geister",
  "Dämonen",
  "Monster",
] as const;

/** MangaDex-Genre/Thema (englischer Name) → unser Genre. Alles andere fällt weg. */
const TAGS: Record<string, (typeof GENRES)[number]> = {
  Action: "Action",
  Adventure: "Abenteuer",
  Romance: "Romantik",
  Comedy: "Komödie",
  Drama: "Drama",
  Fantasy: "Fantasy",
  "Slice of Life": "Alltag",
  Sports: "Sport",
  "Sci-Fi": "Sci-Fi",
  Psychological: "Psycho",
  Thriller: "Thriller",
  Horror: "Horror",
  Mystery: "Mystery",
  Historical: "Historisch",
  "School Life": "Schule",
  Isekai: "Isekai",
  Reincarnation: "Isekai",
  Ghosts: "Geister",
  Demons: "Dämonen",
  Monsters: "Monster",
};

export function mapTags(tags: Pick<MdTag, "attributes">[]): string[] {
  const got = new Set<string>();
  for (const t of tags) {
    const g = TAGS[t.attributes?.name?.en ?? ""];
    if (g) got.add(g);
  }
  return GENRES.filter((g) => got.has(g));
}

export type Kind = "Manga" | "Manhwa" | "Manhua" | "Webcomic";

export function mapKind(originalLanguage: string): Kind {
  if (originalLanguage === "ja") return "Manga";
  if (originalLanguage === "ko") return "Manhwa";
  if (originalLanguage === "zh" || originalLanguage.startsWith("zh-")) return "Manhua";
  return "Webcomic";
}

/** Für Index UND Suchanfrage gleich: klein, Umlaute/Akzente weg, ß→ss, Satzzeichen → Leerzeichen. */
export function normalizeSearch(s: string): string {
  return s
    .toLowerCase()
    .replace(/ß/g, "ss")
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .replace(/[^\p{L}\p{N}]+/gu, " ")
    .trim();
}

const firstValue = (o: LocalizedString): string | undefined =>
  Object.values(o).find((v) => typeof v === "string" && v.trim().length > 0);

function altTitle(m: MdManga, lang: string): string | undefined {
  for (const a of m.attributes.altTitles ?? []) {
    const v = a[lang];
    if (v?.trim()) return v.trim();
  }
  return undefined;
}

/** Erster Absatz, ohne Markdown, höchstens 600 Zeichen. Deutsch vor Englisch. */
export function pickDescription(d: LocalizedString | undefined): string | null {
  const raw = d?.de?.trim() || d?.en?.trim();
  if (!raw) return null;
  const para =
    raw
      .replace(/\r\n/g, "\n")
      .split(/\n\s*\n/)
      .map((p) => p.trim())
      .find((p) => p.length > 0) ?? "";
  let text = para
    .replace(/!?\[([^\]]*)\]\([^)]*\)/g, "$1")
    .replace(/(\*\*|__|~~|\*)/g, "")
    .replace(/\s+/g, " ")
    .trim();
  if (!text) return null;
  if (text.length > 600) {
    const cut = text.slice(0, 599);
    const space = cut.lastIndexOf(" ");
    text = `${(space > 400 ? cut.slice(0, space) : cut).replace(/[\s,;:.–-]+$/, "")}…`;
  }
  return text;
}

export interface CatalogRow {
  id: string;
  source: string;
  title: string;
  titleDe: string | null;
  authors: string[];
  tags: string[];
  status: string | null;
  year: number | null;
  kind: Kind;
  langs: Lang[];
  lastChapter: string | null;
  coverUrl: string | null;
  description: string | null;
  popularity: number | null;
  contentRating: string | null;
  searchText: string;
  updatedAt: number | null;
  syncedAt: number;
}

export const catalogId = (uuid: string) => `${SOURCE}:${uuid}`;

export function catalogFromManga(m: MdManga, now: number): CatalogRow {
  const a = m.attributes;
  const title = a.title.en?.trim() || altTitle(m, "en") || firstValue(a.title)?.trim() || m.id;
  const titleDe = altTitle(m, "de") ?? (a.title.de?.trim() || null);
  const authors = [
    ...new Set(
      m.relationships
        .filter((r) => r.type === "author" || r.type === "artist")
        .map((r) => r.attributes?.name?.trim())
        .filter((n): n is string => !!n),
    ),
  ];
  const cover = m.relationships.find((r) => r.type === "cover_art")?.attributes?.fileName;
  const langs = LANGS.filter((l) => a.availableTranslatedLanguages?.includes(l));
  const names = [
    title,
    titleDe ?? "",
    ...Object.values(a.title),
    ...(a.altTitles ?? []).flatMap((t) => Object.values(t)),
    ...authors,
  ];
  const updated = a.updatedAt ? Date.parse(a.updatedAt) : Number.NaN;
  return {
    id: catalogId(m.id),
    source: SOURCE,
    title,
    titleDe,
    authors,
    tags: mapTags(a.tags ?? []),
    status: a.status ?? null,
    year: a.year ?? null,
    kind: mapKind(a.originalLanguage ?? ""),
    langs,
    lastChapter: a.lastChapter?.trim() || null,
    coverUrl: cover ? `https://uploads.mangadex.org/covers/${m.id}/${cover}.512.jpg` : null,
    description: pickDescription(a.description),
    popularity: null,
    contentRating: a.contentRating ?? null,
    // Nur Namen in lateinischer o. ä. Schrift taugen für die Suche über die Tastatur —
    // schadet aber nicht, alles mitzunehmen; doppelte fallen weg.
    searchText: [...new Set(names.map(normalizeSearch).filter(Boolean))].join(" | "),
    updatedAt: Number.isNaN(updated) ? null : updated,
    syncedAt: now,
  };
}

export interface MdChapter {
  uuid: string;
  lang: Lang;
  number: number;
  title: string | null;
  pages: number;
  group: string | null;
  publishedAt: number | null;
}

/**
 * Lesbare Kapitel aus dem Feed: ohne externe Links, ohne leere, ohne gesperrte, nur
 * de/en. Hat mehr als eine Gruppe dieselbe Nummer in derselben Sprache hochgeladen,
 * gewinnt die mit mehr Seiten, bei Gleichstand die neuere. Ohne Nummer = Oneshot (0).
 */
export function chaptersFromFeed(entries: MdChapterEntry[]): MdChapter[] {
  const best = new Map<string, MdChapter>();
  for (const e of entries) {
    const a = e.attributes;
    if (a.externalUrl || a.isUnavailable || !(a.pages > 0)) continue;
    const lang = a.translatedLanguage as Lang;
    if (!LANGS.includes(lang)) continue;
    const raw = a.chapter?.trim();
    const number = raw == null || raw === "" ? 0 : Number(raw);
    if (!Number.isFinite(number)) continue;
    const published = Date.parse(a.publishAt ?? a.readableAt ?? "");
    const ch: MdChapter = {
      uuid: e.id,
      lang,
      number,
      title: a.title?.trim() || null,
      pages: a.pages,
      group:
        e.relationships
          .filter((r) => r.type === "scanlation_group")
          .map((r) => r.attributes?.name?.trim())
          .find((n): n is string => !!n) ?? null,
      publishedAt: Number.isNaN(published) ? null : published,
    };
    const key = `${lang}:${number}`;
    const prev = best.get(key);
    if (
      !prev ||
      ch.pages > prev.pages ||
      (ch.pages === prev.pages && (ch.publishedAt ?? 0) > (prev.publishedAt ?? 0))
    ) {
      best.set(key, ch);
    }
  }
  return [...best.values()].sort((x, y) =>
    x.lang === y.lang ? x.number - y.number : x.lang < y.lang ? -1 : 1,
  );
}

/** Seitenadressen in voller Qualität (`data`, nicht `dataSaver`). */
export function pageUrls(ah: MdAtHome): string[] {
  return ah.chapter.data.map((f) => `${ah.baseUrl}/data/${ah.chapter.hash}/${f}`);
}
