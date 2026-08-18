import type Database from "better-sqlite3";
import type { VideoMetadata } from "../ingest/metadata.js";
import { decide, type Thresholds } from "../scorer/decision.js";
import { getActivePromptForChannel, getFilterForChannel } from "../scorer/filter-repo.js";
import { prefilterReason } from "../scorer/prefilter.js";
import type { FilterConfig } from "../scorer/filter.js";
import type { ScoredVideo, Scorer } from "../scorer/types.js";
import type { SponsorSegment } from "../sponsorblock/client.js";
import { enqueue } from "../clipper/queue.js";
import { recalculateChannelScore } from "../discovery/discovery-repo.js";

const AUTO_APPROVE_MODEL = "auto-approve";

// Baseline anti-manipulation floor that every channel keeps; only the overall
// quality gate is driven per-channel by the filter's scoreThreshold.
const BASELINE_MAX_CLICKBAIT = 4;
const BASELINE_MAX_MANIPULATION = 3;

/** Decision thresholds for a (resolved, per-channel or global) filter. */
/** Kurzform-Hinweis für den Scorer — Mindestdauer-Regeln gelten für Shorts nicht. */
export const SHORTS_PROMPT_HINT =
  "\n\nHINWEIS: Dieses Video ist ein natives YouTube-Short (Hochkant-Kurzform, max. 3 Minuten). " +
  "Mindestdauer-Regeln gelten hier NICHT — bewerte ausschließlich Inhalt, Clickbait-Risiko und Manipulation.";

export function thresholdsForFilter(filter: FilterConfig): Thresholds {
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
  fetchSponsorSegments: (videoId: string) => Promise<SponsorSegment[] | null>;
  scorer: Scorer;
  /** Clipper-Maschinerie (clip_status + Queue) — Default aus seit Etappe 2. */
  clipperEnabled?: boolean | undefined;
  /** Karten-Teaser für Langvideos — best-effort, darf fehlen und darf werfen. */
  summarize?: ((title: string, transcript: string) => Promise<string | null>) | undefined;
  /** Herkunft des Kandidaten ('subscription' | 'probe' | 'topic' | 'backfill'). */
  source?: string | undefined;
}

/**
 * Kurzform = bis 3 Minuten. Das Seitenverhältnis entscheidet bewusst NICHT:
 * ein zweiminütiges Querformat-Video ist im Swipe-Feed genauso Kurzform wie
 * ein natives Short — der Player zeigt es mit Rändern, statt es als Karte zu
 * verstecken. So landet auch kurze Themen-Kost im Kurzform-Feed.
 */
function classifyFormat(meta: VideoMetadata): "short" | "long" {
  return meta.durationSeconds <= 180 ? "short" : "long";
}

export async function processNewVideo(deps: ProcessNewVideoDeps): Promise<void> {
  const { db, videoId } = deps;
  let channelId = deps.channelId;

  const existing = db.prepare("SELECT 1 FROM videos WHERE id = ?").get(videoId);
  if (existing) return;

  const meta = await deps.fetchMetadata(videoId);

  if (meta.isLive) return;

  // Themen-Treffer kommen unter einem Sammel-Kanal herein; die Metadaten
  // verraten den echten Uploader. Erst dadurch lässt sich der Kanal
  // abonnieren oder blocken — und der Feed zeigt den richtigen Namen.
  if (meta.channelId && meta.channelId !== channelId) {
    const known = db.prepare("SELECT status FROM channels WHERE id = ?").get(meta.channelId) as
      | { status: string | null }
      | undefined;
    if (known?.status === "blocked") return; // geblockter Kanal: nichts aufnehmen
    if (!known) {
      db.prepare(
        `INSERT OR IGNORE INTO channels (id, url, title, added_at, is_active, status)
         VALUES (?, ?, ?, ?, 0, 'probe')`,
      ).run(
        meta.channelId,
        `https://www.youtube.com/channel/${meta.channelId}`,
        meta.channelTitle ?? meta.channelId,
        Date.now(),
      );
    }
    channelId = meta.channelId;
  }

  const format = classifyFormat(meta);

  // Per-channel curation: resolve this channel's own filter (or the global
  // fallback) once and use it for duration gating, prompt, and thresholds.
  const filter = getFilterForChannel(db, channelId);

  // Green Card / "Vertrauenskanal": skip scorer entirely. Hard filters that
  // still apply: isLive (above) + duration range from the channel's filter.
  const channelRow = db
    .prepare("SELECT auto_approve FROM channels WHERE id = ?")
    .get(channelId) as { auto_approve: number } | undefined;
  if ((channelRow?.auto_approve ?? 0) === 1) {
    // Der Dauer-Range-Filter ist ein Langform-Kriterium — native Shorts sind
    // per Definition kurz und würden sonst pauschal durchfallen.
    const inRange =
      format === "short" ||
      (meta.durationSeconds >= filter.minDurationSec &&
        meta.durationSeconds <= filter.maxDurationSec);
    const now = Date.now();

    if (!inRange) {
      const reasoning =
        `auto-rejected (Green Card): duration ${Math.round(meta.durationSeconds / 60)}min ` +
        `outside ${Math.round(filter.minDurationSec / 60)}–` +
        `${Math.round(filter.maxDurationSec / 60)}min range`;
      db.transaction(() => {
        insertVideo(db, meta, null, channelId, format, null, deps.source ?? "subscription");
        insertScore(db, videoId, autoRejectScore(reasoning), "rejected", now);
      })();
      return;
    }

    const [transcript, sponsors] = await Promise.all([
      meta.captionsUrl ? deps.fetchTranscript(meta.captionsUrl) : Promise.resolve(null),
      deps.fetchSponsorSegments(videoId),
    ]);
    const summary = await buildSummary(deps, format, meta.title, transcript);
    db.transaction(() => {
      insertVideo(db, meta, transcript, channelId, format, summary, deps.source ?? "subscription");
      insertScore(db, videoId, autoApproveScore(), "approved", now);
      insertSponsors(db, videoId, sponsors);
      insertFeedItem(db, videoId, now);
      if (deps.clipperEnabled) {
        db.prepare("UPDATE videos SET clip_status='pending' WHERE id=?").run(videoId);
        enqueue(db, videoId);
      }
    })();
    refreshChannelMatch(db, channelId);
    return;
  }

  // Billiger Vorfilter: fremdsprachige Titel (Devanagari, Thai, …) lehnt der
  // Scorer ohnehin ab — das braucht keinen LLM-Aufruf und spart bei
  // Empfehlungswellen Stunden.
  const preReason = prefilterReason(meta.title, meta.description, filter);
  if (preReason) {
    const now = Date.now();
    db.transaction(() => {
      insertVideo(db, meta, null, channelId, format, null, deps.source ?? "subscription");
      insertScore(db, videoId, autoRejectScore(preReason), "rejected", now);
    })();
    return;
  }

  const transcript = meta.captionsUrl ? await deps.fetchTranscript(meta.captionsUrl) : null;
  const basePrompt = getActivePromptForChannel(db, channelId);
  // Die Dauer-Regeln des Filters sind Langform-Kriterien. Ohne diesen Hinweis
  // rejected der Scorer jedes native Short pauschal als "zu kurz".
  const systemPrompt =
    format === "short"
      ? `${basePrompt}${SHORTS_PROMPT_HINT}`
      : basePrompt;
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
    const summary = await buildSummary(deps, format, meta.title, transcript);
    db.transaction(() => {
      insertVideo(db, meta, transcript, channelId, format, summary, deps.source ?? "subscription");
      insertScore(db, videoId, scored, decision, now);
      insertSponsors(db, videoId, sponsors);
      insertFeedItem(db, videoId, now);
      if (deps.clipperEnabled) {
        db.prepare("UPDATE videos SET clip_status='pending' WHERE id=?").run(videoId);
        enqueue(db, videoId);
      }
    })();
  } else {
    db.transaction(() => {
      insertVideo(db, meta, transcript, channelId, format, null, deps.source ?? "subscription");
      insertScore(db, videoId, scored, decision, now);
    })();
  }
  refreshChannelMatch(db, channelId);
}

/**
 * Karten-Teaser für Langvideos — läuft VOR der Transaktion (LLM-Call gehört
 * nicht in eine SQLite-Transaktion) und ist best-effort: jeder Fehler wird zu
 * null, der Approve läuft ungebremst weiter.
 */
async function buildSummary(
  deps: ProcessNewVideoDeps,
  format: "short" | "long",
  title: string,
  transcript: string | null,
): Promise<string | null> {
  if (format !== "long" || !transcript || !deps.summarize) return null;
  try {
    return (await deps.summarize(title, transcript)) ?? null;
  } catch {
    return null;
  }
}

function insertVideo(
  db: Database.Database,
  m: VideoMetadata,
  transcript: string | null,
  channelId: string,
  format: "short" | "long",
  summary: string | null,
  source: string,
): void {
  db.prepare(
    `INSERT OR IGNORE INTO videos
     (id, channel_id, title, description, published_at, duration_seconds,
      aspect_ratio, default_language, thumbnail_url, transcript, discovered_at,
      format, source, summary)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
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
    format,
    source,
    summary,
  );
}

/** Approve macht das Video sichtbar: eine feed_items-Row IST der Feed-Eintrag. */
function insertFeedItem(db: Database.Database, videoId: string, now: number): void {
  db.prepare(
    "INSERT OR IGNORE INTO feed_items (video_id, added_to_feed_at, is_pre_clipper) VALUES (?, ?, 1)",
  ).run(videoId, now);
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
  segments: SponsorSegment[] | null,
): void {
  // null = the SponsorBlock lookup FAILED (network/5xx). Skip the write
  // entirely so a transient failure can't overwrite real segments with empty.
  // An empty array IS persisted — it's a confirmed "no segments here".
  if (segments === null) return;
  // Idempotent: clear any existing segments for this video, then insert fresh.
  db.prepare("DELETE FROM sponsor_segments WHERE video_id = ?").run(videoId);
  const stmt = db.prepare(
    `INSERT INTO sponsor_segments (video_id, start_seconds, end_seconds, category)
     VALUES (?, ?, ?, ?)`,
  );
  for (const s of segments) {
    stmt.run(videoId, s.startSeconds, s.endSeconds, s.category);
  }
}

