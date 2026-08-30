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
  const match = /^(.*?)\s[-|–—]\s([^-|–—]{1,30})$/.exec(title);
  if (!match) return title;
  const [, head, tail] = match as unknown as [string, string, string];
  const tailNorm = tail.toLowerCase().replace(/[^a-z0-9]+/g, "");
  const belongsToSite =
    (hostName.length >= 3 && tailNorm.includes(hostName)) ||
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

  if (!title) return null;
  if (GENERIC_TITLES.has(title.toLowerCase())) return null;
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
