import type Database from "better-sqlite3";
import type { VideoMetadata } from "../ingest/metadata.js";
import { decide, type Thresholds } from "../scorer/decision.js";
import { getActivePromptForChannel, getFilterForChannel } from "../scorer/filter-repo.js";
import type { FilterConfig } from "../scorer/filter.js";
import type { ScoredVideo, Scorer } from "../scorer/types.js";
import type { SponsorSegment } from "../sponsorblock/client.js";
import type { DownloadResult } from "../download/worker.js";
import { enqueue } from "../clipper/queue.js";
import { recalculateChannelScore } from "../discovery/discovery-repo.js";

const AUTO_APPROVE_MODEL = "auto-approve";

// Baseline anti-manipulation floor that every channel keeps; only the overall
// quality gate is driven per-channel by the filter's scoreThreshold.
const BASELINE_MAX_CLICKBAIT = 4;
const BASELINE_MAX_MANIPULATION = 3;

/** Decision thresholds for a (resolved, per-channel or global) filter. */
function thresholdsForFilter(filter: FilterConfig): Thresholds {
  return {
    minOverall: filter.scoreThreshold,
    maxClickbait: BASELINE_MAX_CLICKBAIT,
    maxManipulation: BASELINE_MAX_MANIPULATION,
  };
}

/**
 * Refresh a channel's cached match-score after its scored set changes, so the
 * feed's channelMatch ranking signal stays live. Best-effort — a failure here
 * must never abort ingestion.
 */
function refreshChannelMatch(db: Database.Database, channelId: string): void {
  try {
    recalculateChannelScore(db, channelId);
  } catch {
    // non-fatal — the feed falls back to a neutral channelMatch
  }
}

function autoApproveScore(): ScoredVideo {
  return {
    score: {
      overallScore: 100,
      category: "other",
      clickbaitRisk: 0,
      educationalValue: 10,
      emotionalManipulation: 0,
      reasoning: "auto-approved (Green Card / Vertrauenskanal)",
    },
    modelUsed: AUTO_APPROVE_MODEL,
  };
}

function autoRejectScore(reasoning: string): ScoredVideo {
  return {
    score: {
      overallScore: 0,
      category: "other",
      clickbaitRisk: 0,
      educationalValue: 0,
      emotionalManipulation: 0,
      reasoning,
    },
    modelUsed: AUTO_APPROVE_MODEL,
  };
}

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

  const existing = db.prepare("SELECT 1 FROM videos WHERE id = ?").get(videoId);
  if (existing) return;

  const meta = await deps.fetchMetadata(videoId);

  if (meta.isLive) return;

  // Per-channel curation: resolve this channel's own filter (or the global
  // fallback) once and use it for duration gating, prompt, and thresholds.
  const filter = getFilterForChannel(db, channelId);

  // Green Card / "Vertrauenskanal": skip scorer entirely. Hard filters that
  // still apply: isLive (above) + duration range from the channel's filter.
  const channelRow = db
    .prepare("SELECT auto_approve FROM channels WHERE id = ?")
    .get(channelId) as { auto_approve: number } | undefined;
  if ((channelRow?.auto_approve ?? 0) === 1) {
    const inRange =
      meta.durationSeconds >= filter.minDurationSec &&
      meta.durationSeconds <= filter.maxDurationSec;
    const now = Date.now();

    if (!inRange) {
      const reasoning =
        `auto-rejected (Green Card): duration ${Math.round(meta.durationSeconds / 60)}min ` +
        `outside ${Math.round(filter.minDurationSec / 60)}–` +
        `${Math.round(filter.maxDurationSec / 60)}min range`;
      db.transaction(() => {
        insertVideo(db, meta, null, channelId);
        insertScore(db, videoId, autoRejectScore(reasoning), "rejected", now);
      })();
      return;
    }

    const [transcript, sponsors] = await Promise.all([
      meta.captionsUrl ? deps.fetchTranscript(meta.captionsUrl) : Promise.resolve(null),
      deps.fetchSponsorSegments(videoId),
    ]);
    const dl = await deps.download(videoId);
    db.transaction(() => {
      insertVideo(db, meta, transcript, channelId);
      insertScore(db, videoId, autoApproveScore(), "approved", now);
      insertSponsors(db, videoId, sponsors);
      insertDownload(db, videoId, dl, now);
      db.prepare("UPDATE videos SET clip_status='pending' WHERE id=?").run(videoId);
      enqueue(db, videoId);
    })();
    refreshChannelMatch(db, channelId);
    return;
  }

  const transcript = meta.captionsUrl ? await deps.fetchTranscript(meta.captionsUrl) : null;
  const systemPrompt = getActivePromptForChannel(db, channelId);
  const [scored, sponsors] = await Promise.all([
    deps.scorer.score({
      title: meta.title,
      description: meta.description,
      transcript,
      durationSeconds: meta.durationSeconds,
      systemPrompt,
    }),
    deps.fetchSponsorSegments(videoId),
  ]);

  const decision = decide(scored.score, thresholdsForFilter(filter));
  const now = Date.now();

  if (decision === "approved") {
    const dl = await deps.download(videoId);
    db.transaction(() => {
      insertVideo(db, meta, transcript, channelId);
      insertScore(db, videoId, scored, decision, now);
      insertSponsors(db, videoId, sponsors);
      insertDownload(db, videoId, dl, now);
      db.prepare("UPDATE videos SET clip_status='pending' WHERE id=?").run(videoId);
      enqueue(db, videoId);
    })();
  } else {
    db.transaction(() => {
      insertVideo(db, meta, transcript, channelId);
      insertScore(db, videoId, scored, decision, now);
    })();
  }
  refreshChannelMatch(db, channelId);
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
    `INSERT OR IGNORE INTO scores
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
  // Idempotent: clear any existing segments for this video, then insert fresh
  db.prepare("DELETE FROM sponsor_segments WHERE video_id = ?").run(videoId);
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
    `INSERT OR IGNORE INTO downloaded_videos (video_id, file_path, file_size_bytes, downloaded_at)
     VALUES (?, ?, ?, ?)`,
  ).run(videoId, dl.filePath, dl.fileSizeBytes, now);
}
