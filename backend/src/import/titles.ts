/**
 * Titel-Bereinigung für Importe.
 *
 * Filehoster und In-App-Browser liefern Titel, die so nicht in die Bibliothek
 * gehören: "Dragonball Super 2 HD GER SUB by Dragonball-Tube" (VOE hängt den
 * Uploader an), "Folge 5 - AniWorld" (Seitenname im document.title), oder der
 * generische Extraktor meldet schlicht "master". Und wenn gar nichts kommt,
 * landete bisher die rohe URL als Titel in der Datenbank.
 */

const HTML_ENTITIES: Record<string, string> = {
  "&amp;": "&",
  "&lt;": "<",
  "&gt;": ">",
  "&quot;": '"',
  "&#39;": "'",
  "&apos;": "'",
  "&nbsp;": " ",
};

/** Titel ohne Aussagekraft — lieber null und den URL-Fallback nehmen. */
const GENERIC_TITLES = new Set([
  "master",
  "index",
  "playlist",
  "video",
  "watch",
  "player",
  "stream",
  "untitled",
]);

function decodeEntities(input: string): string {
  let out = input;
  for (const [entity, ch] of Object.entries(HTML_ENTITIES)) {
    out = out.replaceAll(entity, ch);
  }
  return out.replace(/&#(\d+);/g, (_, code) => {
    const n = Number(code);
    return Number.isFinite(n) ? String.fromCodePoint(n) : "";
  });
}

/**
 * Schneidet einen Site-Suffix ab: "Titel - AniWorld", "Titel | VOE".
 * Nur wenn der Rest hinter dem Trenner zum Host der Seite passt — sonst wäre
 * "Mission: Impossible - Fallout" plötzlich kürzer. Ohne hostHint wird nichts
 * abgeschnitten.
 */
function stripSiteSuffix(title: string, hostHint?: string): string {
  if (!hostHint) return title;
  const host = hostHint.toLowerCase().replace(/^www\./, "");
  const hostName = host.split(".")[0] ?? host;
  // Kurzdomains wie "s.to" taugen als hostName ("s") nicht zum Matchen — der
  // Seitenname im Suffix ("SerienStream (S.to)") enthält aber die Domain selbst.
  const hostNorm = host.replace(/[^a-z0-9]+/g, "");
  const match = /^(.*?)\s[-|–—]\s([^-|–—]{1,30})$/.exec(title);
  if (!match) return title;
  const [, head, tail] = match as unknown as [string, string, string];
  const tailNorm = tail.toLowerCase().replace(/[^a-z0-9]+/g, "");
  const belongsToSite =
    (hostName.length >= 3 && tailNorm.includes(hostName)) ||
    (hostNorm.length >= 3 && tailNorm.includes(hostNorm)) ||
    (tailNorm.length >= 3 && hostName.includes(tailNorm));
  if (!belongsToSite) return title;
  if (head.trim().length < 3) return title;
  return head.trim();
}

/**
 * Räumt einen Titel aus yt-dlp/VOE/document.title auf. Liefert null, wenn
 * nichts Brauchbares übrig bleibt — der Aufrufer fällt dann auf die URL zurück.
 */
export function cleanImportTitle(raw: string | null | undefined, hostHint?: string): string | null {
  if (!raw) return null;
  let title = decodeEntities(raw).replace(/\s+/g, " ").trim();
  if (!title) return null;

  // VOE hängt den Uploader an: "Dragonball Super 2 HD GER SUB by Dragonball-Tube"
  title = title.replace(/\s+by\s+\S.{0,40}$/i, "").trim();

  title = stripSiteSuffix(title, hostHint);

  // Scene-Release-Namen als Titel: "Solo.Leveling.S01E01.German.Dub.720p.WEB.h264-DK"
  // — Punkte/Unterstriche sind Leerzeichen, alles ab dem ersten Qualitäts-Tag
  // ist Ballast. Nur anfassen, wenn der Titel sonst keine Leerzeichen hat
  // und ein SxxEyy-Muster trägt, sonst gingen normale Titel mit Punkt kaputt.
  if (!title.includes(" ") && /[._]/.test(title) && /s\d{1,2}e\d{1,4}/i.test(title)) {
    title = title
      .replace(/\.(mp4|mkv|webm|avi|m4v)$/i, "")
      .replace(/[._]+/g, " ")
      .replace(
        /(\s+(?:german|deutsch|ger|jpn|eng|dub|sub|hardsub|multi|ml|dl|aac|ac3|dts|ddp\d?(?:\.\d)?|480p|576p|720p|1080p|2160p|web(?:-?(?:rip|dl))?|hdtv|hd|sd|x\.?26[45]|h\.?26[45]|hevc|avc|cr|amzn|nf|dsnp|bluray|bdrip|remux|proper|repack|internal))+\s*(-\s*\w+)?$/i,
        "",
      )
      .trim();
  }

  if (!title) return null;
  if (GENERIC_TITLES.has(title.toLowerCase())) return null;
  // Kein Titel, sondern eine URL: passiert, wenn der Browser beim Einsammeln
  // schon auf einer Ad-/Tracking-Weiterleitung stand — deren document.title
  // ist schlicht die eigene URL. Lieber null und den URL-Fallback der
  // Herkunftsseite nehmen.
  if (/^(https?:\/\/|www\.)\S+$/i.test(title)) return null;
  if (/^[\w-]+(\.[a-z0-9-]{2,})+\/\S*$/i.test(title) && !title.includes(" ")) return null;
  // Reine Dateinamen ("abc123.mp4") sind keine Titel.
  if (/^[\w-]{1,40}\.(mp4|mkv|webm|m3u8|mpd|ts)$/i.test(title) && !title.includes(" ")) {
    return null;
  }
  return title;
}

/**
 * Nimmt dem Titel den Seriennamen weg, wenn er vorne dran steht — sonst zeigt
 * die Bibliothek "Serie — Serie Folge 3". Vergleich case-insensitiv, danach
 * wird ein Trenner (Satz von - : – — |) mit entfernt.
 */
export function stripSeriesPrefix(title: string, seriesTitle: string | null | undefined): string {
  if (!seriesTitle) return title;
  const series = seriesTitle.trim();
  if (series.length < 3) return title;
  if (!title.toLowerCase().startsWith(series.toLowerCase())) return title;
  const rest = title
    .slice(series.length)
    .replace(/^[\s\-:–—|]+/, "")
    .trim();
  // Nicht leer ausgeben — dann lieber den Originaltitel behalten.
  return rest.length >= 2 ? rest : title;
}

export function humanizeSlug(slug: string): string | null {
  const cleaned = decodeURIComponent(slug)
    .replace(/\.(mp4|mkv|webm|m3u8|mpd|ts|html?|php)$/i, "")
    .replace(/[-_+]+/g, " ")
    .replace(/\s+/g, " ")
    .trim();
  if (cleaned.length < 3) return null;
  if (GENERIC_TITLES.has(cleaned.toLowerCase())) return null;
  // Wortanfänge groß schreiben, Zahlwörter wie "staffel 2" bleiben lesbar.
  return cleaned.replace(/(^|\s)(\p{L})/gu, (_, pre, ch) => `${pre}${ch.toUpperCase()}`);
}

/**
 * Letzter Ausweg vor der rohen URL: den hübschesten Pfadteil hernehmen.
 * "…/serie/staffel-2/episode-5" → "Episode 5" wäre zu kurz gegriffen — die
 * letzten zwei aussagekräftigen Segmente zusammen ergeben "Staffel 2 Episode 5".
 */
export function fallbackTitleFromUrl(pageUrl: string, mediaUrl?: string): string {
  for (const candidate of [pageUrl, mediaUrl]) {
    if (!candidate) continue;
    let pathname: string;
    try {
      pathname = new URL(candidate).pathname;
    } catch {
      continue;
    }
    const segments = pathname.split("/").filter((s) => s.length > 0);
    const humanized = segments.map(humanizeSlug).filter((s): s is string => s !== null);
    if (humanized.length > 0) {
      return humanized.slice(-2).join(" ");
    }
  }
  return "Unbenanntes Video";
}

// ── Einheitliche Folgentitel ────────────────────────────────────────────────

/** Am Titelende hängende Sprach-/Qualitäts-Tags, die keine Aussage tragen. */
const TRAILING_TAGS =
  /(?:\s*[-–—|·]?\s*\b(?:ger(?:man)?|deutsch|eng(?:lish)?|jap(?:anese)?|jpn|sub(?:bed)?|dub(?:bed)?|omu|hardsub|hd|sd|480p|720p|1080p|2160p|web(?:-?dl)?|x26[45]|h26[45])\b)+\s*$/i;

/** Nummerierungs-Token, die in Serienlisten redundant sind. */
const EPISODE_TOKENS: RegExp[] = [
  /\bs\d{1,2}\s*[._-]?\s*e\d{1,4}\b/gi,
  /\b\d{1,2}x\d{1,4}\b/gi,
  /\b(?:staffel|season)\s*\d{1,3}\b/gi,
  /\b(?:folge|episode|ep\.?|epis\.?)\s*\d{1,4}\b/gi,
];

function trimSeparators(input: string): string {
  return input
    .replace(/^[\s\-:–—|·.,]+/, "")
    .replace(/[\s\-:–—|·,]+$/, "")
    .trim();
}

/**
 * Bringt Folgentitel auf eine Form: Serienname raus, Nummerierung raus,
 * Sprach-Tags raus — übrig bleibt der eigentliche Folgentitel. Bleibt nichts
 * Brauchbares, heißt die Folge schlicht „Folge N", damit die Bibliothek
 * nie mehr "S01E01" neben "Folge 1" neben "Ted" zeigt.
 *
 * Filme und Videos ohne Folgennummer behalten ihren bereinigten Titel.
 */
export function canonicalEpisodeTitle(
  rawTitle: string | null | undefined,
  meta: {
    seriesTitle?: string | null;
    season?: number | null;
    episode?: number | null;
    isMovie?: boolean | null;
  },
): string | null {
  const base = rawTitle
    ? stripSeriesPrefix(rawTitle.replace(/\s+/g, " ").trim(), meta.seriesTitle)
    : "";
  const episode = meta.episode ?? null;
  if (meta.isMovie || episode === null) {
    const cleaned = trimSeparators(base.replace(TRAILING_TAGS, ""));
    return cleaned.length >= 2 ? cleaned : base || null;
  }

  let rest = base;
  for (const re of EPISODE_TOKENS) rest = rest.replace(re, " ");
  // Führende Nummer ("3. Der Anfang", "03 - Der Anfang")
  rest = rest.replace(/^\s*\d{1,4}\s*[.:\-–—|)]\s*/, "");
  rest = rest.replace(TRAILING_TAGS, "");
  rest = trimSeparators(rest.replace(/\s+/g, " "));

  const series = meta.seriesTitle?.trim().toLowerCase();
  const restLower = rest.toLowerCase();
  const generic =
    rest.length < 3 ||
    GENERIC_TITLES.has(restLower) ||
    // Rest ist der Serienname — oder dessen Anfang ("American Horror Story"
    // bei Serie "American Horror Story Die Dunkle Seite In Dir"): keine Aussage.
    (series !== undefined &&
      series.length > 0 &&
      (restLower === series ||
        series.startsWith(`${restLower} `) ||
        restLower.startsWith(`${series} `))) ||
    /^\d+$/.test(rest);
  return generic ? `Folge ${episode}` : rest;
}

/** Ist der Titel nur die Standardform „Folge N"? (Für Listen, die N ohnehin zeigen.) */
export function isPlainEpisodeTitle(title: string): boolean {
  return /^(?:folge|episode)\s+\d{1,4}$/i.test(title.trim());
}

/** Film-Hinweis in der Seiten-URL (/film/, /filme/, /movie/, /movies/). */
export function looksLikeMovieUrl(url: string): boolean {
  try {
    const path = new URL(url).pathname.toLowerCase();
    return /(?:^|\/)(?:film|filme|movie|movies|kinofilm|kinofilme)(?:\/|$)/.test(path);
  } catch {
    return false;
  }
}
