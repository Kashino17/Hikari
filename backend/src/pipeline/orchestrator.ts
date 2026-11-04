import type Database from "better-sqlite3";
import { isSubscribedChannel } from "../feed/subscriptions.js";
import type { VideoMetadata } from "../ingest/metadata.js";
import type { SponsorSegment } from "../sponsorblock/client.js";

/**
 * model_used der scores-Zeile für Abo-Videos. Es wird nichts bewertet — die
 * Zeile existiert nur, weil Feed-, Bibliotheks- und Statistik-Abfragen gegen
 * `scores` joinen (Entscheidung + Kategorie).
 */
export const SUBSCRIPTION_MODEL = "subscription";

export interface ProcessNewVideoDeps {
  db: Database.Database;
  videoId: string;
  channelId: string;
  fetchMetadata: (videoId: string) => Promise<VideoMetadata>;
  fetchSponsorSegments: (videoId: string) => Promise<SponsorSegment[] | null>;
}

/**
 * added          — neu im Feed
 * known          — Video war schon bekannt, nichts zu tun
 * not-subscribed — Kanal ist (nicht mehr) abonniert, nichts aufgenommen
 * live           — Live-/Premiere-Stream, (noch) nicht anschaubar
 */
export type ProcessResult = "added" | "known" | "not-subscribed" | "live";

/**
 * Kurzform = bis 3 Minuten. Das Seitenverhältnis entscheidet bewusst NICHT:
 * ein zweiminütiges Querformat-Video ist im Swipe-Feed genauso Kurzform wie
 * ein natives Short — der Player zeigt es mit Rändern, statt es als Karte zu
 * verstecken.
 */
function classifyFormat(meta: VideoMetadata): "short" | "long" {
  return meta.durationSeconds <= 180 ? "short" : "long";
}

/**
 * Nimmt ein neues Video eines abonnierten Kanals in den Feed auf. Es gibt
 * keine Bewertung und keine Filter: jedes anschaubare Video eines Abos geht
 * direkt in den Feed. Ausgeschlossen bleibt nur, was sich nicht abspielen
 * lässt — Live/Premiere hier, nicht verfügbare Videos scheitern schon beim
 * Metadaten-Abruf (der Ingest-Job läuft dann in den Retry/Dead-Letter).
 *
 * Videos fremder Kanäle (Probe, geblockt, inaktiv, unbekannt) werden nie
 * aufgenommen — geprüft VOR dem Metadaten-Abruf, damit dafür auch kein
 * yt-dlp-Aufruf mehr anfällt.
 */
export async function processNewVideo(deps: ProcessNewVideoDeps): Promise<ProcessResult> {
  const { db, videoId, channelId } = deps;

  const existing = db.prepare("SELECT 1 FROM videos WHERE id = ?").get(videoId);
  if (existing) return "known";
  if (!isSubscribedChannel(db, channelId)) return "not-subscribed";

  const meta = await deps.fetchMetadata(videoId);
  if (meta.isLive) return "live";

  const format = classifyFormat(meta);
  const sponsors = await deps.fetchSponsorSegments(videoId);
  const now = Date.now();
  db.transaction(() => {
    insertVideo(db, meta, channelId, format);
    insertSubscriptionScore(db, videoId, now);
    insertSponsors(db, videoId, sponsors);
    insertFeedItem(db, videoId, now);
  })();
  return "added";
}

function insertVideo(
  db: Database.Database,
  m: VideoMetadata,
  channelId: string,
  format: "short" | "long",
): void {
  db.prepare(
    `INSERT OR IGNORE INTO videos
     (id, channel_id, title, description, published_at, duration_seconds,
      aspect_ratio, default_language, thumbnail_url, discovered_at, format, source)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'subscription')`,
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
    Date.now(),
    format,
  );
}

/** Eine feed_items-Row IST der Feed-Eintrag. */
function insertFeedItem(db: Database.Database, videoId: string, now: number): void {
  db.prepare("INSERT OR IGNORE INTO feed_items (video_id, added_to_feed_at) VALUES (?, ?)").run(
    videoId,
    now,
  );
}

/**
 * Neutrale scores-Zeile (keine Bewertung): decision 'approved', mittlere
 * Werte. Feed-Hydration, Queue und Statistik joinen gegen `scores`; ohne
 * Zeile fielen Abo-Videos dort still heraus.
 */
function insertSubscriptionScore(db: Database.Database, videoId: string, now: number): void {
  db.prepare(
    `INSERT OR IGNORE INTO scores
     (video_id, overall_score, category, clickbait_risk, educational_value,
      emotional_manipulation, reasoning, model_used, scored_at, decision)
     VALUES (?, 50, 'other', 0, 5, 0, 'Abo-Kanal — ohne Bewertung übernommen', ?, ?, 'approved')`,
  ).run(videoId, SUBSCRIPTION_MODEL, now);
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
