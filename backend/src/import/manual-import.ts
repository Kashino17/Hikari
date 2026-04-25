import { existsSync, statSync } from "node:fs";
import { join } from "node:path";
import type Database from "better-sqlite3";
import { runYtDlp, YtDlpError } from "../yt-dlp/client.js";

export const MANUAL_CHANNEL_ID = "manual";
const MANUAL_CHANNEL_TITLE = "Manuell hinzugefügt";

export interface ImportResult {
  url: string;
  status: "ok" | "duplicate" | "failed";
  videoId?: string;
  title?: string;
  error?: string;
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

function ensureManualChannel(db: Database.Database): void {
  db.prepare(
    `INSERT OR IGNORE INTO channels (id, url, title, added_at, is_active)
     VALUES (?, ?, ?, ?, 1)`,
  ).run(MANUAL_CHANNEL_ID, "manual:hikari", MANUAL_CHANNEL_TITLE, Date.now());
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
): Promise<ImportResult> {
  const cleanUrl = url.trim();
  if (!cleanUrl) return { url, status: "failed", error: "empty URL" };

  // Step 1: extract metadata only (no download yet — we want to validate first)
  let meta: YtDlpVideoMeta;
  try {
    const result = await runYtDlp(
      ["--dump-single-json", "--no-warnings", "--no-playlist", cleanUrl],
      { timeoutMs: 30_000 },
    );
    meta = JSON.parse(result.stdout) as YtDlpVideoMeta;
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
    return { url, status: "duplicate", videoId, title: meta.title };
  }

  ensureManualChannel(db);

  const title = meta.title ?? meta.id;
  const description = meta.description ?? "";
  const duration = Math.round(meta.duration ?? 0);
  const thumbnail = fixProtocol(meta.thumbnail ?? meta.thumbnails?.[meta.thumbnails.length - 1]?.url);
  const publishedAt = parseUploadDate(meta.upload_date);
  const now = Date.now();

  // Insert video row.
  db.prepare(
    `INSERT INTO videos
     (id, channel_id, title, description, published_at, duration_seconds,
      aspect_ratio, default_language, thumbnail_url, transcript, discovered_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
  ).run(
    videoId,
    MANUAL_CHANNEL_ID,
    title,
    description,
    publishedAt,
    duration,
    null,
    null,
    thumbnail,
    null,
    now,
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
        cleanUrl,
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
