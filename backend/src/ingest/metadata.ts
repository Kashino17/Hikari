import { runYtDlp } from "../yt-dlp/client.js";

export interface VideoMetadata {
  id: string;
  title: string;
  description: string;
  durationSeconds: number;
  publishedAt: number;
  thumbnailUrl: string;
  aspectRatio: string;
  defaultLanguage: string | null;
  isLive: boolean;
  captionsUrl: string | null;
}

interface YtDlpJson {
  id: string;
  title: string;
  description?: string;
  duration: number;
  upload_date: string;
  thumbnail?: string;
  width?: number;
  height?: number;
  language?: string;
  live_status?: string;
  automatic_captions?: Record<string, { url: string; ext: string }[]>;
}

export async function fetchVideoMetadata(videoId: string): Promise<VideoMetadata> {
  const { stdout } = await runYtDlp([
    "--dump-json",
    "--skip-download",
    "--write-auto-subs",
    "--sub-lang",
    "en,de",
    "--no-warnings",
    `https://www.youtube.com/watch?v=${videoId}`,
  ]);
  const d = JSON.parse(stdout) as YtDlpJson;

  return {
    id: d.id,
    title: d.title,
    description: d.description ?? "",
    durationSeconds: d.duration,
    publishedAt: parseYouTubeDate(d.upload_date),
    thumbnailUrl: d.thumbnail ?? "",
    aspectRatio: classifyAspect(d.width, d.height),
    defaultLanguage: d.language ?? null,
    isLive: d.live_status !== "not_live" && d.live_status !== undefined,
    captionsUrl: pickCaptionsUrl(d.automatic_captions),
  };
}

function parseYouTubeDate(yyyymmdd: string): number {
  const y = Number(yyyymmdd.slice(0, 4));
  const m = Number(yyyymmdd.slice(4, 6)) - 1;
  const day = Number(yyyymmdd.slice(6, 8));
  return Date.UTC(y, m, day);
}

function classifyAspect(width?: number, height?: number): string {
  if (!width || !height) return "unknown";
  const ratio = width / height;
  if (ratio >= 1.5) return "16:9";
  if (ratio <= 0.7) return "9:16";
  return "1:1";
}

function pickCaptionsUrl(caps?: Record<string, { url: string; ext: string }[]>): string | null {
  if (!caps) return null;
  for (const lang of ["en", "de"]) {
    const entry = caps[lang]?.find((c) => c.ext === "vtt");
    if (entry) return entry.url;
  }
  return null;
}
