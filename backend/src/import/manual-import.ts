import { existsSync, statSync } from "node:fs";
import { join } from "node:path";
import type Database from "better-sqlite3";
import { runYtDlp, YtDlpError } from "../yt-dlp/client.js";

export const MANUAL_CHANNEL_ID = "manual";
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
  const result = await runYtDlp(
    ["--dump-single-json", "--no-warnings", "--no-playlist", url],
    { timeoutMs: 30_000 },
  );
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
      ...(config.thumbnail ?? metadata.thumbnail
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

  return { metadata, downloadUrl: url };
  }

  export async function fetchImportMetadata(url: string): Promise<YtDlpVideoMeta & { downloadUrl: string }> {
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

  function ensureSeries(db: Database.Database, title: string): string {
  const id = title.toLowerCase().replace(/[^a-z0-9]+/g, "-");
  db.prepare(
    `INSERT INTO series (id, title, added_at)
     VALUES (?, ?, ?)
     ON CONFLICT(id) DO NOTHING`,
  ).run(id, title, Date.now());
  return id;
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

  const title = meta.title ?? meta.id;
  const description = meta.description ?? "";
  const duration = Math.round(meta.duration ?? 0);
  const thumbnail = fixProtocol(meta.thumbnail ?? meta.thumbnails?.[meta.thumbnails.length - 1]?.url);
  const publishedAt = parseUploadDate(meta.upload_date);
  const now = Date.now();

  let seriesId = manualMeta?.seriesId;
  if (!seriesId && manualMeta?.seriesTitle) {
    seriesId = ensureSeries(db, manualMeta.seriesTitle);
  }

  // Insert video row.
  db.prepare(
    `INSERT INTO videos
     (id, channel_id, series_id, title, description, published_at, duration_seconds,
      aspect_ratio, default_language, thumbnail_url, transcript, discovered_at,
      season, episode, dub_language, sub_language, is_movie)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
  ).run(
    videoId,
    MANUAL_CHANNEL_ID,
    seriesId ?? null,
    title,
    description,
    publishedAt,
    duration,
    null,
    null,
    thumbnail,
    null,
    now,
    manualMeta?.season ?? null,
    manualMeta?.episode ?? null,
    manualMeta?.dubLanguage ?? null,
    manualMeta?.subLanguage ?? null,
    manualMeta?.isMovie ? 1 : 0,
  );

  // Insert auto-approved score — user-curated content, no LLM.
  db.prepare(
    `INSERT INTO scores
     (video_id, overall_score, category, clickbait_risk, educational_value,
      emotional_manipulation, reasoning, model_used, scored_at, decision)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
  ).run(
    videoId,
    100,                       // top score — user explicitly added this
    "other",
    0,
    0,
    0,
    "Manuell hinzugefügt — Auto-Genehmigt",
    "manual",
    now,
    "approved",
  );

  // Step 2: download. yt-dlp writes the file using its own id template,
  // not ours, so we override -o to match our internal videoId.
  const filePath = join(videoDir, `${videoId}.mp4`);
  try {
    await runYtDlp(
      [
        "-f",
        "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[height<=720]/best",
        "--merge-output-format",
        "mp4",
        "-o",
        filePath,
        "--no-warnings",
        downloadUrl,
      ],
      { timeoutMs: 30 * 60_000 }, // up to 30 min for big files
    );
  } catch (err) {
    // Roll back the video + score so the user can retry without "duplicate"
    db.prepare("DELETE FROM scores WHERE video_id = ?").run(videoId);
    db.prepare("DELETE FROM videos WHERE id = ?").run(videoId);
    const msg = err instanceof YtDlpError ? err.message : String(err);
    return { url, status: "failed", error: `download failed: ${msg.slice(0, 200)}` };
  }

  if (!existsSync(filePath)) {
    db.prepare("DELETE FROM scores WHERE video_id = ?").run(videoId);
    db.prepare("DELETE FROM videos WHERE id = ?").run(videoId);
    return { url, status: "failed", error: "download finished but file not found" };
  }

  const size = statSync(filePath).size;

  db.prepare(
    `INSERT INTO downloaded_videos (video_id, file_path, file_size_bytes, downloaded_at)
     VALUES (?, ?, ?, ?)`,
  ).run(videoId, filePath, size, now);

  db.prepare(
    `INSERT OR IGNORE INTO feed_items (video_id, added_to_feed_at) VALUES (?, ?)`,
  ).run(videoId, now);

  return { url, status: "ok", videoId, title };
}
