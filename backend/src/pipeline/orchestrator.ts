import type Database from "better-sqlite3";
import type { VideoMetadata } from "../ingest/metadata.js";
import { decide } from "../scorer/decision.js";
import type { Scorer } from "../scorer/types.js";
import type { SponsorSegment } from "../sponsorblock/client.js";
import type { DownloadResult } from "../download/worker.js";

export interface ProcessNewVideoDeps {
  db: Database.Database;
  videoId: string;
  channelId: string;
  fetchMetadata: (videoId: string) => Promise<VideoMetadata>;
  fetchTranscript: (url: string) => Promise<string | null>;
  fetchSponsorSegments: (videoId: string) => Promise<SponsorSegment[]>;
  scorer: Scorer;
  download: (videoId: string) => Promise<DownloadResult>;
}

export async function processNewVideo(deps: ProcessNewVideoDeps): Promise<void> {
  const { db, videoId, channelId } = deps;

  const existing = db
    .prepare("SELECT 1 FROM videos WHERE id = ?")
    .get(videoId);
  if (existing) return;

  const meta = await deps.fetchMetadata(videoId);

  if (meta.isLive) return;

  const transcript = meta.captionsUrl ? await deps.fetchTranscript(meta.captionsUrl) : null;
  const [scored, sponsors] = await Promise.all([
    deps.scorer.score({
      title: meta.title,
      description: meta.description,
      transcript,
      durationSeconds: meta.durationSeconds,
    }),
    deps.fetchSponsorSegments(videoId),
  ]);

  const decision = decide(scored.score);
  const now = Date.now();

  if (decision === "approved") {
    const dl = await deps.download(videoId);
    db.transaction(() => {
      insertVideo(db, meta, transcript, channelId);
      insertScore(db, videoId, scored, decision, now);
      insertSponsors(db, videoId, sponsors);
      insertDownload(db, videoId, dl, now);
      insertFeedItem(db, videoId, now);
    })();
  } else {
    db.transaction(() => {
      insertVideo(db, meta, transcript, channelId);
      insertScore(db, videoId, scored, decision, now);
    })();
  }
}

function insertVideo(
  db: Database.Database,
  m: VideoMetadata,
  transcript: string | null,
  channelId: string,
): void {
  db.prepare(
    `INSERT OR IGNORE INTO videos
     (id, channel_id, title, description, published_at, duration_seconds,
      aspect_ratio, default_language, thumbnail_url, transcript, discovered_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
  ).run(
    m.id,
    channelId,
    m.title,
    m.description,
    m.publishedAt,
    m.durationSeconds,
    m.aspectRatio,
    m.defaultLanguage,
    m.thumbnailUrl,
    transcript,
    Date.now(),
  );
}

function insertScore(
  db: Database.Database,
  videoId: string,
  scored: { score: import("../scorer/types.js").Score; modelUsed: string },
  decision: "approved" | "rejected",
  now: number,
): void {
  db.prepare(
    `INSERT INTO scores
     (video_id, overall_score, category, clickbait_risk, educational_value,
      emotional_manipulation, reasoning, model_used, scored_at, decision)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
  ).run(
    videoId,
    scored.score.overallScore,
    scored.score.category,
    scored.score.clickbaitRisk,
    scored.score.educationalValue,
    scored.score.emotionalManipulation,
    scored.score.reasoning,
    scored.modelUsed,
    now,
    decision,
  );
}

function insertSponsors(
  db: Database.Database,
  videoId: string,
  segments: SponsorSegment[],
): void {
  const stmt = db.prepare(
    `INSERT INTO sponsor_segments (video_id, start_seconds, end_seconds, category)
     VALUES (?, ?, ?, ?)`,
  );
  for (const s of segments) {
    stmt.run(videoId, s.startSeconds, s.endSeconds, s.category);
  }
}

function insertDownload(
  db: Database.Database,
  videoId: string,
  dl: DownloadResult,
  now: number,
): void {
  db.prepare(
    `INSERT INTO downloaded_videos (video_id, file_path, file_size_bytes, downloaded_at)
     VALUES (?, ?, ?, ?)`,
  ).run(videoId, dl.filePath, dl.fileSizeBytes, now);
}

function insertFeedItem(db: Database.Database, videoId: string, now: number): void {
  db.prepare(`INSERT INTO feed_items (video_id, added_to_feed_at) VALUES (?, ?)`).run(videoId, now);
}
