import { createHash } from "node:crypto";
import { existsSync, statSync } from "node:fs";
import { join } from "node:path";
import type Database from "better-sqlite3";
import { probeDurationSeconds } from "../download/probe.js";
import { PROGRESS_ARGS, parseProgressLine } from "../download/progress.js";
import { YtDlpError, runPreferEmbedded, runYtDlp } from "../yt-dlp/client.js";
import { fillMissingEpisodeInfo } from "./episode-parser.js";
import {
  createPending,
  getPending,
  markDownloading,
  markFailed,
  metadataForCompletion,
  removePending,
  updateProgress,
} from "./pending-imports.js";
import { ensureImportThumbnail } from "./thumbnails.js";
import {
  canonicalEpisodeTitle,
  cleanImportTitle,
  fallbackTitleFromUrl,
  looksLikeMovieUrl,
} from "./titles.js";

/**
 * videoIds, deren Download gerade läuft. Der Bulk-Import verteilt URLs nach
 * Hostname auf parallele Buckets — landet dasselbe Video über zwei
 * verschiedene Host-URLs (z. B. voe + Redirect) in zwei Buckets, liefen sonst
 * zwei yt-dlp-Prozesse parallel auf dieselbe Zieldatei, und der zweite INSERT
 * scheiterte am PRIMARY KEY: im Ergebnis ein falsches "failed" statt
 * "duplicate".
 */
const inFlight = new Set<string>();

function hostOf(url: string | null | undefined): string | undefined {
  if (!url) return undefined;
  try {
    return new URL(url).hostname;
  } catch {
    return undefined;
  }
}

export const MANUAL_CHANNEL_ID = "manual";
/** Ab dieser Laufzeit gilt ein Video ohne Serienzuordnung als Film. */
export const MOVIE_MIN_SECONDS = 60 * 60;
const MANUAL_CHANNEL_TITLE = "Manuell hinzugefügt";
const MANUAL_IMPORT_UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36";

export interface ImportResult {
  url: string;
  status: "ok" | "duplicate" | "failed";
  videoId?: string;
  title?: string;
  error?: string;
}

export interface ManualMetadata {
  title?: string;
  seriesId?: string;
  seriesTitle?: string;
  season?: number;
  episode?: number;
  dubLanguage?: string;
  subLanguage?: string;
  isMovie?: boolean;
}

interface YtDlpVideoMeta {
  id?: string;
  extractor?: string;
  extractor_key?: string;
  title?: string;
  description?: string;
  duration?: number;
  thumbnail?: string;
  thumbnails?: { url?: string }[];
  uploader?: string;
  upload_date?: string;
  webpage_url?: string;
}

interface VoeConfig {
  file_code?: string;
  title?: string;
  thumbnail?: string;
  source?: string;
}

interface ResolvedImportSource {
  downloadUrl: string;
  metadata: YtDlpVideoMeta;
}

function fixProtocol(u: string | null | undefined): string | null {
  if (!u) return null;
  return u.startsWith("//") ? `https:${u}` : u;
}

function parseUploadDate(d: string | undefined): number {
  if (!d || d.length !== 8) return Date.now();
  const y = Number(d.slice(0, 4));
  const m = Number(d.slice(4, 6)) - 1;
  const day = Number(d.slice(6, 8));
  if ([y, m, day].some(Number.isNaN)) return Date.now();
  return Date.UTC(y, m, day);
}

/**
 * Stable internal video id from extractor + extractor's id. Prevents
 * collision if voe.sx and YouTube both used "abc123".
 */
function makeVideoId(extractor: string | undefined, id: string | undefined): string {
  const ex = (extractor ?? "manual").toLowerCase().replace(/[^a-z0-9]+/g, "");
  const safeId = (id ?? "unknown").replace(/[^a-zA-Z0-9_-]+/g, "");
  // Keep YouTube IDs unprefixed (so the existing flow stays identical) — only
  // prefix non-YouTube extractors.
  if (ex === "youtube") return safeId;
  return `${ex}_${safeId}`;
}

function rot13(input: string): string {
  let out = "";
  for (const ch of input) {
    const code = ch.charCodeAt(0);
    if (code >= 65 && code <= 90) {
      out += String.fromCharCode(((code - 65 + 13) % 26) + 65);
      continue;
    }
    if (code >= 97 && code <= 122) {
      out += String.fromCharCode(((code - 97 + 13) % 26) + 97);
      continue;
    }
    out += ch;
  }
  return out;
}

function replaceVoeMarkers(input: string): string {
  let out = input;
  for (const marker of ["@$", "^^", "~@", "%?", "*~", "!!", "#&"]) {
    out = out.replaceAll(marker, "_");
  }
  return out;
}

function shiftAscii(input: string, offset: number): string {
  let out = "";
  for (let i = 0; i < input.length; i++) {
    out += String.fromCharCode(input.charCodeAt(i) + offset);
  }
  return out;
}

function extractVoeEncodedConfig(html: string): string | null {
  const scripts = html.matchAll(/<script type="application\/json">([\s\S]*?)<\/script>/gi);
  for (const match of scripts) {
    const content = match[1];
    if (!content) continue;
    try {
      const parsed = JSON.parse(content);
      if (Array.isArray(parsed) && typeof parsed[0] === "string") {
        return parsed[0];
      }
    } catch {
      // Ignore unrelated JSON blobs.
    }
  }
  return null;
}

function decodeVoeConfig(encoded: string): VoeConfig {
  const normalized = replaceVoeMarkers(rot13(encoded)).replaceAll("_", "");
  const stage1 = Buffer.from(normalized, "base64").toString("binary");
  const stage2 = shiftAscii(stage1, -3);
  const stage3 = stage2.split("").reverse().join("");
  const json = Buffer.from(stage3, "base64").toString("utf8");
  return JSON.parse(json) as VoeConfig;
}

async function resolveViaYtDlp(url: string): Promise<ResolvedImportSource> {
  const result = await runYtDlp(["--dump-single-json", "--no-warnings", "--no-playlist", url], {
    timeoutMs: 30_000,
  });
  const metadata = JSON.parse(result.stdout) as YtDlpVideoMeta;
  if (!metadata.id) {
    throw new Error("yt-dlp returned no video id");
  }
  return { metadata, downloadUrl: url };
}

async function resolveVoePage(url: string): Promise<ResolvedImportSource | null> {
  let response: Response;
  try {
    response = await fetch(url, {
      headers: {
        "User-Agent": MANUAL_IMPORT_UA,
      },
      redirect: "follow",
      // Ohne Limit hing /videos/analyze an einem stummen Host fest — die App
      // lief in ihr 60-s-Timeout, der Server werkelte weiter.
      signal: AbortSignal.timeout(20_000),
    });
  } catch {
    return null;
  }

  if (!response.ok) return null;

  const html = await response.text();
  const encoded = extractVoeEncodedConfig(html);
  if (!encoded) return null;

  let config: VoeConfig;
  try {
    config = decodeVoeConfig(encoded);
  } catch {
    return null;
  }

  if (!config.file_code || !config.source) return null;

  let metadata: YtDlpVideoMeta;
  try {
    const result = await runYtDlp(
      ["--dump-single-json", "--no-warnings", "--no-playlist", config.source],
      { timeoutMs: 30_000 },
    );
    metadata = JSON.parse(result.stdout) as YtDlpVideoMeta;
  } catch {
    return null;
  }

  return {
    downloadUrl: config.source,
    metadata: {
      ...metadata,
      id: config.file_code,
      extractor: "voe",
      extractor_key: "VOE",
      title: config.title ?? metadata.title ?? config.file_code,
      webpage_url: url,
      ...((config.thumbnail ?? metadata.thumbnail)
        ? { thumbnail: config.thumbnail ?? metadata.thumbnail }
        : {}),
    },
  };
}

async function resolveImportSource(url: string): Promise<ResolvedImportSource> {
  let primaryError: unknown;

  try {
    return await resolveViaYtDlp(url);
  } catch (err) {
    primaryError = err;
  }

  const voe = await resolveVoePage(url);
  if (voe) return voe;

  throw primaryError ?? new Error(`Could not resolve import source for ${url}`);
}

export async function fetchImportMetadata(
  url: string,
): Promise<YtDlpVideoMeta & { downloadUrl: string }> {
  const resolved = await resolveImportSource(url);
  return { ...resolved.metadata, downloadUrl: resolved.downloadUrl };
}

function ensureManualChannel(db: Database.Database): void {
  db.prepare(
    `INSERT INTO channels (id, url, title, added_at, is_active)
     VALUES (?, ?, ?, ?, 1)
     ON CONFLICT(id) DO UPDATE SET
       url = excluded.url,
       title = excluded.title,
       is_active = 1`,
  ).run(MANUAL_CHANNEL_ID, "manual:hikari", MANUAL_CHANNEL_TITLE, Date.now());
}

export function seriesSlug(title: string): string {
  // Normalisieren, damit Schreibvarianten nicht als Dubletten aufreihen:
  // "Solo Leveling", "Solo  Leveling" und "Sólo Leveling" sind eine Serie.
  // NFKD löst Umlaute in Grundbuchstabe + Akzent auf, den wir wegwerfen.
  let id = title
    .normalize("NFKD")
    .replace(/\p{Mark}/gu, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
  // Titel ohne lateinische Zeichen (z. B. japanisch) würden sonst leer laufen.
  if (!id) id = `series-${createHash("sha1").update(title).digest("hex").slice(0, 10)}`;
  return id;
}

/**
 * Findet eine bestehende Serie, deren Slug ein Präfix des neuen ist (oder
 * umgekehrt): "american-horror-story" ↔ "american-horror-story-die-dunkle-
 * seite-in-dir". Der kürzere Slug muss mindestens zwei Wörter und zehn
 * Zeichen haben, sonst würde "ted" auch "ted-lasso" schlucken.
 */
function findRelatedSeries(
  db: Database.Database,
  slug: string,
): { id: string; title: string } | undefined {
  const rows = db.prepare("SELECT id, title FROM series").all() as { id: string; title: string }[];
  const qualifies = (short: string) => short.length >= 10 && short.split("-").length >= 2;
  for (const row of rows) {
    if (row.id === slug) return row;
    if (slug.startsWith(`${row.id}-`) && qualifies(row.id)) return row;
    if (row.id.startsWith(`${slug}-`) && qualifies(slug)) return row;
  }
  return undefined;
}

/** Bestehende Serie zu einem Titel — ohne etwas anzulegen. */
export function resolveSeriesId(
  db: Database.Database,
  title: string | null | undefined,
): string | undefined {
  if (!title?.trim()) return undefined;
  return findRelatedSeries(db, seriesSlug(title.trim()))?.id;
}

export function ensureSeries(db: Database.Database, title: string): string {
  const clean = title.trim();
  const id = seriesSlug(clean);
  const related = findRelatedSeries(db, id);
  if (related) {
    // Der kürzere Name ist fast immer der echte Serienname (der lange trägt
    // einen Untertitel aus dem URL-Slug). Anzeige nachziehen, Id bleibt.
    if (clean.length < related.title.length && clean.length >= 3 && id.length < related.id.length) {
      db.prepare("UPDATE series SET title = ? WHERE id = ?").run(clean, related.id);
    }
    return related.id;
  }
  db.prepare(
    `INSERT INTO series (id, title, added_at)
     VALUES (?, ?, ?)
     ON CONFLICT(id) DO NOTHING`,
  ).run(id, clean, Date.now());
  return id;
}

/**
 * Dieselbe Folge (Serie + Staffel + Folge, gleiche Synchro) ist schon da —
 * egal über welche Seiten-URL sie kam. Vorher reihten sich "S01E07" zweimal
 * auf, weil zwei Hoster-Links zwei Identitäten ergaben.
 */
export function findEpisodeDuplicate(
  db: Database.Database,
  meta: ManualMetadata | undefined,
): string | undefined {
  if (!meta || meta.episode === undefined || meta.episode === null) return undefined;
  const seriesId = meta.seriesId ?? resolveSeriesId(db, meta.seriesTitle);
  if (!seriesId) return undefined;
  const row = db
    .prepare(
      `SELECT v.id FROM videos v
        JOIN downloaded_videos dv ON dv.video_id = v.id
       WHERE v.series_id = ? AND COALESCE(v.season, 1) = ? AND v.episode = ?
         AND LOWER(COALESCE(v.dub_language, '')) = LOWER(?)
       LIMIT 1`,
    )
    .get(seriesId, meta.season ?? 1, meta.episode, meta.dubLanguage ?? "") as
    | { id: string }
    | undefined;
  return row?.id;
}

/**
  * Import a single URL: extract metadata via yt-dlp, auto-approve (no LLM

 * call), download via yt-dlp, write all rows. Returns status per URL so
 * the bulk caller can show a result line.
 */
export async function importDirectLink(
  db: Database.Database,
  url: string,
  videoDir: string,
  manualMeta?: ManualMetadata,
  coverDir?: string,
): Promise<ImportResult> {
  const cleanUrl = url.trim();
  if (!cleanUrl) return { url, status: "failed", error: "empty URL" };

  // Keep the synthetic manual channel visible even after the user hid it
  // previously. Imports should revive the archive container automatically.
  ensureManualChannel(db);

  // Step 1: extract metadata only (no download yet — we want to validate first)
  let meta: YtDlpVideoMeta;
  let downloadUrl = cleanUrl;
  try {
    const resolved = await resolveImportSource(cleanUrl);
    meta = resolved.metadata;
    downloadUrl = resolved.downloadUrl;
  } catch (err) {
    const msg = err instanceof YtDlpError ? err.message : String(err);
    return { url, status: "failed", error: msg.slice(0, 200) };
  }

  if (!meta.id) {
    return { url, status: "failed", error: "yt-dlp returned no video id" };
  }

  const videoId = makeVideoId(meta.extractor, meta.id);

  // Already in DB? Skip.
  const existing = db.prepare("SELECT 1 FROM videos WHERE id = ?").get(videoId);
  if (existing) {
    return {
      url,
      status: "duplicate",
      videoId,
      ...(meta.title ? { title: meta.title } : {}),
    };
  }

  // Läuft dasselbe Video gerade in einem anderen Host-Bucket, ist es ein
  // Duplikat — nicht erst nach einem doppelten Download auf dieselbe Datei
  // am PRIMARY KEY scheitern (das wurde im Bulk-Ergebnis zu Unrecht "failed").
  if (inFlight.has(videoId)) {
    return {
      url,
      status: "duplicate",
      videoId,
      ...(meta.title ? { title: meta.title } : {}),
    };
  }

  const title =
    manualMeta?.title ??
    cleanImportTitle(meta.title, hostOf(meta.webpage_url ?? cleanUrl)) ??
    fallbackTitleFromUrl(cleanUrl);
  const description = meta.description ?? "";
  const duration = Math.round(meta.duration ?? 0);
  const remoteThumbnail = fixProtocol(
    meta.thumbnail ?? meta.thumbnails?.[meta.thumbnails.length - 1]?.url,
  );
  const publishedAt = parseUploadDate(meta.upload_date);

  // Serie/Staffel/Folge regelbasiert aus URL und Titel ergänzen — nur Lücken,
  // die Eingaben des Nutzers bleiben unangetastet.
  const effectiveMeta: ManualMetadata = fillMissingEpisodeInfo(manualMeta ?? {}, cleanUrl, title);
  if (
    !effectiveMeta.isMovie &&
    looksLikeMovieUrl(cleanUrl) &&
    effectiveMeta.episode === undefined
  ) {
    effectiveMeta.isMovie = true;
  }
  const dupEpisode = findEpisodeDuplicate(db, effectiveMeta);
  if (dupEpisode) {
    return { url, status: "duplicate", videoId: dupEpisode, title };
  }
  // Step 2: download FIRST, before writing any DB rows. The episode must only
  // become visible once its file is actually on disk. Previously we inserted
  // the videos + scores rows up front, so for the minutes-long download of a
  // hundreds-of-MB episode it showed on the overview but had no file (playback
  // 404) and no downloaded_videos row (absent from /downloads and its series
  // list). Downloading first also means a failed download needs no rollback,
  // and a mid-download crash leaves no orphaned, permanently-unplayable row.
  // yt-dlp writes the file using its own id template, so we override -o to
  // match our internal videoId.
  // Sofort sichtbar machen — der Download laeuft gleich minutenlang.
  createPending(db, {
    id: videoId,
    pageUrl: cleanUrl,
    mediaUrl: downloadUrl,
    metadata: {
      title,
      seriesId: effectiveMeta.seriesId ?? null,
      seriesTitle: effectiveMeta.seriesTitle ?? null,
      season: effectiveMeta.season ?? null,
      episode: effectiveMeta.episode ?? null,
      dubLanguage: effectiveMeta.dubLanguage ?? null,
      subLanguage: effectiveMeta.subLanguage ?? null,
      isMovie: effectiveMeta.isMovie ?? null,
    },
    thumbnailUrl: remoteThumbnail,
  });
  markDownloading(db, videoId);
  inFlight.add(videoId);

  const filePath = join(videoDir, `${videoId}.mp4`);
  try {
    // web_embedded-first: yt-dlps eigener Download bekommt von googlevideo
    // sonst 403 (siehe yt-dlp/client.ts). `() => true`, weil ein erfolgreicher
    // Download nichts Verwertbares auf stdout schreibt — sonst liefe er zweimal.
    await runPreferEmbedded(
      runYtDlp,
      [
        ...PROGRESS_ARGS,
        // Blockweise laden: googlevideo drosselt ungebremste Downloads auf
        // Abspieltempo (gemessen 18.08.2026: 22 s statt 12 s für 64 MB).
        "--http-chunk-size",
        "4M",
        "-f",
        "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[height<=720]/best",
        "--merge-output-format",
        "mp4",
        "-o",
        filePath,
        "--no-warnings",
        downloadUrl,
      ],
      {
        timeoutMs: 30 * 60_000, // up to 30 min for big files
        onLine: (line) => {
          const p = parseProgressLine(line);
          if (p) updateProgress(db, videoId, p);
        },
      },
      () => true,
    );
  } catch (err) {
    inFlight.delete(videoId);
    const msg = err instanceof YtDlpError ? err.message : String(err);
    const error = `download failed: ${msg.slice(0, 200)}`;
    markFailed(db, videoId, error);
    return { url, status: "failed", error };
  }

  if (!existsSync(filePath)) {
    inFlight.delete(videoId);
    const error = "download finished but file not found";
    markFailed(db, videoId, error);
    return { url, status: "failed", error };
  }

  // Waehrend des Downloads geaenderte Angaben gewinnen.
  const edited = metadataForCompletion(getPending(db, videoId));
  const finalMeta: ManualMetadata = {
    ...effectiveMeta,
    ...Object.fromEntries(Object.entries(edited).filter(([, v]) => v !== null && v !== undefined)),
  };
  // Lokal ablegen — Remote-Thumbnail-URLs der Hoster verfallen oder blocken
  // ohne Referer. Schlägt auch der ffmpeg-Standbild-Fallback fehl, bleibt die
  // Remote-URL als letzte Rettung in der Datenbank.
  const thumbnail = coverDir
    ? ((await ensureImportThumbnail(videoId, filePath, remoteThumbnail, coverDir, {
        referer: cleanUrl,
      })) ?? remoteThumbnail)
    : remoteThumbnail;

  const finalTitle =
    canonicalEpisodeTitle(finalMeta.title ?? title, finalMeta) ?? fallbackTitleFromUrl(cleanUrl);
  // persist wirft bei einem DB-Konflikt — die In-Flight-Markierung muss
  // trotzdem fallen, sonst bliebe jede Wiederholung für immer "duplicate".
  try {
    persistImportedVideo(db, {
      videoId,
      filePath,
      title: finalTitle,
      description,
      duration,
      thumbnail,
      publishedAt,
      sourceUrl: cleanUrl,
      manualMeta: finalMeta,
    });
    removePending(db, videoId);
  } finally {
    inFlight.delete(videoId);
  }

  return { url, status: "ok", videoId, title: finalTitle };
}

interface PersistInput {
  videoId: string;
  filePath: string;
  title: string;
  description: string;
  duration: number;
  thumbnail: string | null;
  publishedAt: number;
  /** Herkunfts-URL des Imports (Seiten- oder Direktlink), nur zur Dokumentation. */
  sourceUrl?: string | null;
  manualMeta?: ManualMetadata | undefined;
}

/**
 * Schreibt alle Zeilen einer fertig heruntergeladenen Episode in einem Zug.
 *
 * Die INSERTs laufen synchron ohne await dazwischen, damit keine parallele
 * Anfrage einen halb importierten Zustand sieht: Das Video erscheint
 * vollständig, abspielbar und seiner Serie zugeordnet — oder gar nicht.
 */
function persistImportedVideo(db: Database.Database, input: PersistInput): void {
  const size = statSync(input.filePath).size;
  const now = Date.now();
  const manualMeta = input.manualMeta;

  let seriesId = manualMeta?.seriesId;
  if (!seriesId && manualMeta?.seriesTitle) {
    seriesId = ensureSeries(db, manualMeta.seriesTitle);
  }

  db.prepare(
    `INSERT INTO videos
     (id, channel_id, series_id, title, description, published_at, duration_seconds,
      aspect_ratio, default_language, thumbnail_url, transcript, discovered_at,
      season, episode, dub_language, sub_language, is_movie, source_url)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
  ).run(
    input.videoId,
    MANUAL_CHANNEL_ID,
    seriesId ?? null,
    input.title,
    input.description,
    input.publishedAt,
    input.duration,
    null,
    null,
    input.thumbnail,
    null,
    now,
    manualMeta?.season ?? null,
    manualMeta?.episode ?? null,
    manualMeta?.dubLanguage ?? null,
    manualMeta?.subLanguage ?? null,
    manualMeta?.isMovie ? 1 : 0,
    input.sourceUrl ?? null,
  );

  db.prepare(
    `INSERT INTO scores
     (video_id, overall_score, category, clickbait_risk, educational_value,
      emotional_manipulation, reasoning, model_used, scored_at, decision)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
  ).run(
    input.videoId,
    100,
    "other",
    0,
    0,
    0,
    "Manuell hinzugefügt — Auto-Genehmigt",
    "manual",
    now,
    "approved",
  );

  db.prepare(
    `INSERT INTO downloaded_videos (video_id, file_path, file_size_bytes, downloaded_at)
     VALUES (?, ?, ?, ?)`,
  ).run(input.videoId, input.filePath, size, now);

  db.prepare("INSERT OR IGNORE INTO feed_items (video_id, added_to_feed_at) VALUES (?, ?)").run(
    input.videoId,
    now,
  );

  // Serien-Cover: die erste Folge mit lokalem Bild stiftet der Serie ihr
  // Poster — einmalig, ein manuell gesetztes Cover wird nie überschrieben.
  if (seriesId && input.thumbnail?.startsWith("/covers/")) {
    db.prepare(
      "UPDATE series SET thumbnail_url = ? WHERE id = ? AND (thumbnail_url IS NULL OR thumbnail_url = '')",
    ).run(input.thumbnail, seriesId);
  }
}

/** Im In-App-Browser mitgelesener Stream samt der Header seiner Herkunftsseite. */
export interface SniffedMedia {
  /** Die Seite, auf der der Nutzer stand — die dauerhafte Identität des Videos. */
  pageUrl: string;
  /** Die tatsächlich geladene Medien-URL (HLS-Playlist, DASH-Manifest, Datei). */
  mediaUrl: string;
  referer?: string;
  cookie?: string;
  userAgent?: string;
  title?: string;
  /** og:description/meta description der Herkunftsseite, sofern die Seite eine hat. */
  description?: string;
}

/**
 * Importiert einen Stream, den der In-App-Browser mitgelesen hat.
 *
 * Unterschied zu importDirectLink: Hier ist die Extraktion bereits passiert —
 * die Seite hat ihren Player selbst gestartet, wir kennen die echte Medien-URL
 * schon. yt-dlp muss den Hoster also nicht mehr verstehen, sondern nur noch
 * laden. Genau deshalb funktioniert dieser Weg auch bei Hostern, deren
 * Extraktor kaputt oder gar nicht vorhanden ist.
 *
 * Die Herkunftsseite ([pageUrl]) dient als Identität, nicht die Medien-URL:
 * Letztere trägt bei fast allen Hostern ein ablaufendes Token und sähe bei
 * jedem Aufruf anders aus — die Duplikatserkennung würde nie greifen.
 */
export async function importSniffedMedia(
  db: Database.Database,
  input: SniffedMedia,
  videoDir: string,
  manualMeta?: ManualMetadata,
  coverDir?: string,
): Promise<ImportResult> {
  const pageUrl = input.pageUrl.trim();
  const mediaUrl = input.mediaUrl.trim();
  if (!mediaUrl) return { url: pageUrl, status: "failed", error: "empty media URL" };

  ensureManualChannel(db);

  const videoId = `sniff_${createHash("sha1")
    .update(pageUrl || mediaUrl)
    .digest("hex")
    .slice(0, 16)}`;

  const existing = db.prepare("SELECT 1 FROM videos WHERE id = ?").get(videoId);
  if (existing) {
    return {
      url: pageUrl,
      status: "duplicate",
      videoId,
      ...(input.title ? { title: input.title } : {}),
    };
  }

  if (inFlight.has(videoId)) {
    return {
      url: pageUrl,
      status: "duplicate",
      videoId,
      ...(input.title ? { title: input.title } : {}),
    };
  }

  // Der Browser schickt den document.title der Seite mit — der trägt oft den
  // Seitennamen ("… - AniWorld") oder ist ein Platzhalter. Einmal aufgeräumt
  // sieht die Warteschlange von Anfang an lesbar aus.
  // Auch ein von der App gesetzter Titel läuft durch die Bereinigung: Die
  // Import-Karte übernimmt den document.title ungefiltert, sonst hieße
  // dieselbe Folge je nach Weg "Folge 5 - AniWorld" oder "Folge 5".
  const cleanTitle =
    cleanImportTitle(manualMeta?.title, hostOf(pageUrl)) ??
    cleanImportTitle(input.title, hostOf(pageUrl));
  const initialTitle = cleanTitle ?? fallbackTitleFromUrl(pageUrl, mediaUrl);

  // Serie/Staffel/Folge aus Seiten-URL und Titel ergänzen — das ist die
  // eigentliche Stärke des Browser-Imports, dessen Hostermuster die Info
  // längst in der URL tragen. Nur Lücken: Nutzereingaben gewinnen.
  const effectiveMeta: ManualMetadata = fillMissingEpisodeInfo(
    manualMeta ?? {},
    pageUrl,
    cleanTitle ?? input.title,
  );
  if (!effectiveMeta.isMovie && looksLikeMovieUrl(pageUrl) && effectiveMeta.episode === undefined) {
    effectiveMeta.isMovie = true;
  }
  const dupEpisode = findEpisodeDuplicate(db, effectiveMeta);
  if (dupEpisode) {
    return { url: pageUrl, status: "duplicate", videoId: dupEpisode, title: initialTitle };
  }

  // Sofort sichtbar machen, bevor der Download beginnt. Ein Serienimport dauert
  // Minuten; ohne diesen Eintrag sieht der Nutzer bis zum Schluss nichts und
  // weiss nicht einmal, ob ueberhaupt etwas gestartet ist.
  createPending(db, {
    id: videoId,
    pageUrl,
    mediaUrl,
    metadata: {
      title: initialTitle,
      seriesId: effectiveMeta.seriesId ?? null,
      seriesTitle: effectiveMeta.seriesTitle ?? null,
      season: effectiveMeta.season ?? null,
      episode: effectiveMeta.episode ?? null,
      dubLanguage: effectiveMeta.dubLanguage ?? null,
      subLanguage: effectiveMeta.subLanguage ?? null,
      isMovie: effectiveMeta.isMovie ?? null,
    },
    headers: {
      referer: input.referer ?? null,
      cookie: input.cookie ?? null,
      userAgent: input.userAgent ?? null,
    },
  });
  markDownloading(db, videoId);
  inFlight.add(videoId);

  const filePath = join(videoDir, `${videoId}.mp4`);
  const headerArgs: string[] = [];
  // Filehoster prüfen Referer und Cookie und antworten sonst mit 403. Der
  // Browser kennt beide bereits — sie einfach durchzureichen ist der ganze
  // Trick, warum der Serverdownload danach überhaupt beantwortet wird.
  if (input.referer) headerArgs.push("--referer", input.referer);
  if (input.cookie) headerArgs.push("--add-header", `Cookie:${input.cookie}`);
  if (input.userAgent) headerArgs.push("--user-agent", input.userAgent);

  try {
    await runYtDlp(
      [
        ...headerArgs,
        ...PROGRESS_ARGS,
        "--http-chunk-size",
        "4M",
        "-f",
        "bestvideo[height<=720]+bestaudio/best[height<=720]/best",
        "--merge-output-format",
        "mp4",
        "-o",
        filePath,
        "--no-warnings",
        mediaUrl,
      ],
      {
        timeoutMs: 30 * 60_000,
        onLine: (line) => {
          const p = parseProgressLine(line);
          if (p) updateProgress(db, videoId, p);
        },
      },
    );
  } catch (err) {
    inFlight.delete(videoId);
    const msg = err instanceof YtDlpError ? err.message : String(err);
    const error = `download failed: ${msg.slice(0, 200)}`;
    markFailed(db, videoId, error);
    return { url: pageUrl, status: "failed", error };
  }

  if (!existsSync(filePath)) {
    inFlight.delete(videoId);
    const error = "download finished but file not found";
    markFailed(db, videoId, error);
    return { url: pageUrl, status: "failed", error };
  }

  // Die Eingaben des Nutzers gewinnen: Hat er waehrend des Downloads Titel
  // oder Sprache gesetzt, zaehlt das und nicht, was beim Einreihen mitkam.
  const edited = metadataForCompletion(getPending(db, videoId));
  const finalMeta: ManualMetadata = {
    ...effectiveMeta,
    ...Object.fromEntries(Object.entries(edited).filter(([, v]) => v !== null && v !== undefined)),
  };
  // Der Hoster liefert keine Metadaten — ohne diesen Schritt stuende eine
  // Laufzeit von 0 in der Datenbank. Die App zeigte dann "0 min", und der
  // Abspielfortschritt (position / duration) teilte durch null.
  const duration = (await probeDurationSeconds(filePath)) ?? 0;
  // Film-Erkennung: keine Folge, keine Serie, aber Spielfilmlänge — das ist
  // ein Film, kein "S1" ohne Folgennummer. (Die pending-Zeile liefert
  // is_movie=0 auch dann, wenn nie jemand etwas gesetzt hat — "false" ist
  // hier kein Nein, nur ein Nicht-Wissen.)
  if (
    !finalMeta.isMovie &&
    (finalMeta.episode === undefined || finalMeta.episode === null) &&
    !finalMeta.seriesTitle &&
    !finalMeta.seriesId &&
    duration >= MOVIE_MIN_SECONDS
  ) {
    finalMeta.isMovie = true;
  }
  const persistMeta: ManualMetadata = finalMeta.isMovie
    ? {
        ...(finalMeta.title !== undefined ? { title: finalMeta.title } : {}),
        ...(finalMeta.dubLanguage !== undefined ? { dubLanguage: finalMeta.dubLanguage } : {}),
        ...(finalMeta.subLanguage !== undefined ? { subLanguage: finalMeta.subLanguage } : {}),
        isMovie: true,
      }
    : finalMeta;
  const title =
    canonicalEpisodeTitle(persistMeta.title ?? initialTitle, persistMeta) ?? initialTitle;
  // Der mitgelesene Stream kennt kein Vorschaubild — ohne diesen Schritt kam
  // jeder Browser-Import als dunkle Flaeche an. ffmpeg schneidet ein Standbild
  // aus der fertigen Datei.
  const thumbnail = coverDir
    ? await ensureImportThumbnail(videoId, filePath, null, coverDir, {
        referer: input.referer ?? pageUrl,
      })
    : null;
  try {
    persistImportedVideo(db, {
      videoId,
      filePath,
      title,
      // Der mitgelesene Stream kennt keine Metadaten — die Beschreibung kommt
      // aus dem og:description/meta-Tag der Herkunftsseite, sonst bleibt sie leer.
      description: input.description?.trim() ?? "",
      duration,
      thumbnail,
      publishedAt: Date.now(),
      sourceUrl: pageUrl,
      manualMeta: persistMeta,
    });
    removePending(db, videoId);
  } finally {
    inFlight.delete(videoId);
  }

  return { url: pageUrl, status: "ok", videoId, title };
}

/**
 * Serverseitiger Neuversuch eines gescheiterten Imports — mit den beim
 * Einreihen gespeicherten Daten. Bei mitgelesenen Streams läuft die Medien-URL
 * samt Header erneut, sonst der Direktlink über yt-dlp. Läuft der Eintrag
 * gerade, passiert nichts.
 */
export async function retryPendingImport(
  db: Database.Database,
  id: string,
  videoDir: string,
  coverDir?: string,
): Promise<ImportResult | undefined> {
  const pending = getPending(db, id);
  if (!pending) return undefined;
  if (pending.status === "downloading" || inFlight.has(id)) {
    return {
      url: pending.pageUrl,
      status: "duplicate",
      videoId: id,
      ...(pending.title ? { title: pending.title } : {}),
    };
  }
  const meta: ManualMetadata = {
    ...(pending.title ? { title: pending.title } : {}),
    ...(pending.seriesId ? { seriesId: pending.seriesId } : {}),
    ...(pending.seriesTitle ? { seriesTitle: pending.seriesTitle } : {}),
    ...(pending.season !== null ? { season: pending.season } : {}),
    ...(pending.episode !== null ? { episode: pending.episode } : {}),
    ...(pending.dubLanguage ? { dubLanguage: pending.dubLanguage } : {}),
    ...(pending.subLanguage ? { subLanguage: pending.subLanguage } : {}),
    ...(pending.isMovie ? { isMovie: true } : {}),
  };
  if (id.startsWith("sniff_") && pending.mediaUrl) {
    return importSniffedMedia(
      db,
      {
        pageUrl: pending.pageUrl,
        mediaUrl: pending.mediaUrl,
        ...(pending.referer ? { referer: pending.referer } : {}),
        ...(pending.cookie ? { cookie: pending.cookie } : {}),
        ...(pending.userAgent ? { userAgent: pending.userAgent } : {}),
        ...(pending.title ? { title: pending.title } : {}),
      },
      videoDir,
      meta,
      coverDir,
    );
  }
  return importDirectLink(db, pending.pageUrl, videoDir, meta, coverDir);
}
