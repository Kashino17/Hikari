import { humanizeSlug } from "./titles.js";

/**
 * Regelbasierte Serien-/Folgen-Erkennung aus URL und Titel.
 *
 * Der LLM-Extractor läuft nur, wenn ein Anthropic-Key konfiguriert ist — im
 * Default-Setup blieben Serie, Staffel und Folge komplett leer und jede Folge
 * landete unsortiert als Einzelvideo. Die Hoster, um die es hier geht
 * (aniworld-Stil: /serie/stream/<name>/staffel-2/episode-5), tragen die
 * Information aber längst in der URL — die auszulesen braucht kein Modell.
 */

export interface EpisodeInfo {
  seriesTitle?: string;
  season?: number;
  episode?: number;
}

/** Pfadsegmente ohne Aussage — keine Seriennamen. */
const JUNK_SEGMENTS = new Set([
  "serie",
  "series",
  "serien",
  "stream",
  "anime",
  "animes",
  "watch",
  "video",
  "videos",
  "film",
  "filme",
  "movie",
  "movies",
  "staffel",
  "season",
  "episode",
  "folge",
  "deutsch",
  "german",
  "ger-sub",
  "ger-dub",
  "sub",
  "dub",
]);

function seasonFromSegment(seg: string): number | undefined {
  const m = /^(?:staffel|season|s)[-_]?(\d{1,3})$/i.exec(seg);
  if (!m?.[1]) return undefined;
  const n = Number(m[1]);
  return n >= 1 && n <= 100 ? n : undefined;
}

function episodeFromSegment(seg: string): number | undefined {
  const m = /^(?:episode|folge|ep)[-_]?(\d{1,4})$/i.exec(seg);
  if (!m?.[1]) return undefined;
  const n = Number(m[1]);
  return n >= 1 && n <= 2000 ? n : undefined;
}

/** S02E05-Matching passiert inline in fromUrl (inkl. Serien-Präfix). */

function fromUrl(rawUrl: string): EpisodeInfo {
  let pathname: string;
  try {
    pathname = new URL(rawUrl).pathname;
  } catch {
    return {};
  }
  const segments = pathname
    .split("/")
    .map((s) => s.trim().toLowerCase())
    .filter((s) => s.length > 0);

  const info: EpisodeInfo = {};
  let seriesIdx = -1;
  let inlineSeries: string | undefined;

  for (let i = 0; i < segments.length; i++) {
    const seg = segments[i] ?? "";
    const seMatch = /(?:^|[-_])s(\d{1,2})e(\d{1,4})(?:[-_]|$)/i.exec(seg);
    if (seMatch?.[1] && seMatch[2]) {
      const season = Number(seMatch[1]);
      const episode = Number(seMatch[2]);
      if (season >= 1 && season <= 100 && episode >= 1 && episode <= 2000) {
        info.season ??= season;
        info.episode ??= episode;
        if (seriesIdx < 0) seriesIdx = i - 1;
        // Bei "arcane-s02e05" steckt der Serienname im selben Segment.
        if (seMatch.index > 0) {
          const prefix = humanizeSlug(decodeURIComponent(seg.slice(0, seMatch.index)));
          if (prefix) inlineSeries = prefix;
        }
        continue;
      }
    }
    const season = seasonFromSegment(seg);
    if (season !== undefined) {
      info.season ??= season;
      if (seriesIdx < 0) seriesIdx = i - 1;
      continue;
    }
    const episode = episodeFromSegment(seg);
    if (episode !== undefined) {
      info.episode ??= episode;
      if (seriesIdx < 0 && info.season === undefined) seriesIdx = i - 1;
    }
  }

  // Serienname: das Segment direkt vor staffel/season/episode, sofern es
  // kein Container-Wort ist ("serie", "stream", …).
  for (let i = seriesIdx; i >= 0; i--) {
    const seg = segments[i] ?? "";
    if (JUNK_SEGMENTS.has(seg)) continue;
    // Serienname steckt im selben Segment vor dem SxxEyy-Teil.
    if (seasonFromSegment(seg) !== undefined || episodeFromSegment(seg) !== undefined) continue;
    const name = humanizeSlug(decodeURIComponent(seg));
    if (name) {
      info.seriesTitle = name;
      break;
    }
  }
  if (!info.seriesTitle && inlineSeries) info.seriesTitle = inlineSeries;

  return info;
}

function fromTitle(title: string): EpisodeInfo {
  const info: EpisodeInfo = {};
  const se = /\bs(\d{1,2})e(\d{1,4})\b/i.exec(title);
  if (se?.[1] && se[2]) {
    info.season = Number(se[1]);
    info.episode = Number(se[2]);
    return info;
  }
  const season = /\b(?:staffel|season)\s*(\d{1,3})\b/i.exec(title);
  if (season?.[1]) info.season = Number(season[1]);
  const episode = /\b(?:folge|episode|ep\.?)\s*(\d{1,4})\b/i.exec(title);
  if (episode?.[1]) info.episode = Number(episode[1]);
  return info;
}

/**
 * Liest Serie/Staffel/Folge aus URL und Titel. URL gewinnt — sie ist
 * maschinengeneriert und damit zuverlässiger als ein freier Seitentitel.
 * Liefert nur Felder, die tatsächlich erkannt wurden.
 */
export function parseEpisodeInfo(url: string, title?: string | null): EpisodeInfo {
  const fromUrlInfo = fromUrl(url);
  const fromTitleInfo = title ? fromTitle(title) : {};
  const merged: EpisodeInfo = { ...fromUrlInfo };
  if (merged.season === undefined && fromTitleInfo.season !== undefined) {
    merged.season = fromTitleInfo.season;
  }
  if (merged.episode === undefined && fromTitleInfo.episode !== undefined) {
    merged.episode = fromTitleInfo.episode;
  }
  return merged;
}

/**
 * Füllt nur Lücken auf — was der Nutzer (oder ein früherer Schritt) gesetzt
 * hat, bleibt unangetastet.
 */
export function fillMissingEpisodeInfo<
  T extends {
    seriesTitle?: string | null;
    season?: number | null;
    episode?: number | null;
  },
>(target: T, url: string, title?: string | null): T {
  const parsed = parseEpisodeInfo(url, title);
  return {
    ...target,
    seriesTitle: target.seriesTitle ?? parsed.seriesTitle ?? target.seriesTitle,
    season: target.season ?? parsed.season ?? target.season,
    episode: target.episode ?? parsed.episode ?? target.episode,
  };
}
