import type { FastifyInstance } from "fastify";
import type Database from "better-sqlite3";
import fs from "node:fs";

export interface FeedDeps {
  db: Database.Database;
  dailyBudget: number;
}

// ---------------------------------------------------------------------------
// UNION helpers: lean row type + raw candidate query + cooldown algorithm
// ---------------------------------------------------------------------------

export interface RawFeedRow {
  kind: "short" | "video";
  id: string;
  parentVideoId: string;
  channelId: string;
  category: string | null;
  addedToFeedAt: number;
  durationSec: number;
  // Ranking signals — optional so hand-built RawFeedRow literals (tests) and
  // the cooldown pass, which don't need them, stay valid.
  overallScore?: number | null;
  educationalValue?: number | null;
  channelMatch?: number | null;
}

const COOLDOWN_WINDOW = 3;
const CHANNEL_MAX_IN_WINDOW = 2;
const LOOKAHEAD = 5;

// ---------------------------------------------------------------------------
// Curation ranking
//
// A TRANSPARENT, anti-doomscroll composite score. It deliberately uses NO
// engagement signal (no watch-time, clicks, dwell, completion). It rewards:
//   - freshness   : exponential decay by age (calm content surfaces, but old
//                   gems don't vanish — 48h half-life)
//   - quality     : the scorer's overall_score (0–100)
//   - educational : the scorer's educational_value (0–10)
//   - channelMatch: the user's own per-channel affinity (channel_match_scores)
// Diversity is handled separately by applyCooldown AFTER ranking.
// ---------------------------------------------------------------------------

export interface RankWeights {
  freshness: number;
  quality: number;
  educational: number;
  channelMatch: number;
}

export const DEFAULT_RANK_WEIGHTS: RankWeights = {
  freshness: 0.4,
  quality: 0.3,
  educational: 0.15,
  channelMatch: 0.15,
};

const FRESHNESS_HALFLIFE_HOURS = 48;

function clamp01(n: number): number {
  if (!Number.isFinite(n) || n < 0) return 0;
  if (n > 1) return 1;
  return n;
}

/**
 * Reorders candidates by the composite curation score (highest first). Pure +
 * deterministic for a given `now`; ties break by recency then id so paging and
 * tests are stable. Missing signals fall back to neutral midpoints so a clip
 * is never unfairly buried just because its channel hasn't been match-scored
 * yet.
 */
export function rankCandidates(
  candidates: RawFeedRow[],
  now: number,
  weights: RankWeights = DEFAULT_RANK_WEIGHTS,
): RawFeedRow[] {
  const scored = candidates.map((c) => {
    const ageHours = Math.max(0, (now - c.addedToFeedAt) / 3_600_000);
    const freshness = Math.exp(-ageHours / FRESHNESS_HALFLIFE_HOURS); // (0,1]
    const quality = clamp01((c.overallScore ?? 70) / 100);
    const educational = clamp01((c.educationalValue ?? 5) / 10);
    const channelMatch = clamp01((c.channelMatch ?? 50) / 100);
    const score =
      weights.freshness * freshness +
      weights.quality * quality +
      weights.educational * educational +
      weights.channelMatch * channelMatch;
    return { c, score };
  });
  scored.sort(
    (a, b) =>
      b.score - a.score ||
      b.c.addedToFeedAt - a.c.addedToFeedAt ||
      (a.c.id < b.c.id ? -1 : a.c.id > b.c.id ? 1 : 0),
  );
  return scored.map((s) => s.c);
}

/**
 * Kandidaten-Pool des "new"-Feeds: approvte Videos direkt aus feed_items.
 * Seit Etappe 2 sind gerenderte Clips kein Feed-Bestandteil mehr — der Feed
 * zeigt native Shorts (kind 'short') und Langvideos (kind 'video', Karten).
 */
export function listFeedRaw(db: Database.Database, limit: number): RawFeedRow[] {
  return db.prepare(`
    SELECT CASE WHEN v.format = 'short' THEN 'short' ELSE 'video' END AS kind,
           f.video_id AS id,
           f.video_id AS parentVideoId,
           v.channel_id AS channelId,
           s.category AS category,
           f.added_to_feed_at AS addedToFeedAt,
           v.duration_seconds AS durationSec,
           s.overall_score AS overallScore,
           s.educational_value AS educationalValue,
           cms.calculated_score AS channelMatch
      FROM feed_items f
      JOIN videos v ON v.id = f.video_id
      LEFT JOIN scores s ON s.video_id = f.video_id
      LEFT JOIN channel_match_scores cms ON cms.channel_id = v.channel_id
     WHERE f.seen_at IS NULL AND f.playback_failed = 0 AND f.is_pre_clipper = 1
    ORDER BY addedToFeedAt DESC
    LIMIT ?
  `).all(limit) as RawFeedRow[];
}

/**
 * Apply soft cooldown: same parent_video_id never twice in 3-window,
 * channel max 2× in 3-window, plus a topic-mix lookahead that prefers
 * a different category from last-output when available. Falls back to
 * gradually relaxed constraints if the strict pass cannot fill the page.
 */
export function applyCooldown(candidates: RawFeedRow[], pageSize: number): RawFeedRow[] {
  const out: RawFeedRow[] = [];
  const remaining = [...candidates];

  while (out.length < pageSize && remaining.length > 0) {
    const window = remaining.slice(0, LOOKAHEAD);
    const last3 = out.slice(-COOLDOWN_WINDOW);
    const last3Parents = new Set(last3.map((r) => r.parentVideoId));
    const channelCount = (chan: string) =>
      last3.filter((r) => r.channelId === chan).length;

    let pickIdx = window.findIndex((r) =>
      !last3Parents.has(r.parentVideoId) &&
      channelCount(r.channelId) < CHANNEL_MAX_IN_WINDOW
    );
    if (pickIdx === -1) {
      pickIdx = window.findIndex((r) =>
        !last3Parents.has(r.parentVideoId) &&
        channelCount(r.channelId) < 3
      );
    }
    if (pickIdx === -1) {
      pickIdx = window.findIndex((r) => !last3Parents.has(r.parentVideoId));
    }
    if (pickIdx === -1) {
      const fallbackIdx = remaining.findIndex((r) => !last3Parents.has(r.parentVideoId));
      if (fallbackIdx === -1) break;
      const picked = remaining.splice(fallbackIdx, 1)[0]!;
      out.push(picked);
      continue;
    }

    const primary = window[pickIdx]!;

    const lastOut = out[out.length - 1];
    if (lastOut && primary.category && lastOut.category === primary.category) {
      const swapIdx = window.findIndex((r, i) =>
        i !== pickIdx &&
        !last3Parents.has(r.parentVideoId) &&
        channelCount(r.channelId) < CHANNEL_MAX_IN_WINDOW &&
        r.category && r.category !== lastOut.category
      );
      if (swapIdx !== -1) {
        const better = window[swapIdx]!;
        const realIdx = remaining.indexOf(better);
        const picked = remaining.splice(realIdx, 1)[0]!;
        out.push(picked);
        continue;
      }
    }

    const realIdx = remaining.indexOf(primary);
    const picked = remaining.splice(realIdx, 1)[0]!;
    out.push(picked);
  }

  return out;
}

// ---------------------------------------------------------------------------
// Channel round-robin interleave — the real "variety" pass.
//
// applyCooldown only forbids clustering in a tiny 3-window, so when one channel
// dominates the pool (e.g. 29 clips vs 8) you still get long same-channel runs.
// This instead deals candidates out round-robin across channels: A, B, A, B, …
// so consecutive items come from DIFFERENT channels as much as possible, never
// the same parent video twice. Within a channel the ranked order is preserved.
//
// `rotation` shifts which channel starts each call, so a fresh app open / tab
// switch reshuffles the top of the feed → new mix every time, without ever
// reusing a clip the user already saw (seen items are filtered upstream).
// This maximizes DIVERSITY, not watch-time — anti-doomscroll by design.
// ---------------------------------------------------------------------------

export function interleaveByChannel(ranked: RawFeedRow[], rotation = 0): RawFeedRow[] {
  if (ranked.length <= 1) return [...ranked];

  // Group preserving rank order; remember first-seen channel order.
  const groups = new Map<string, RawFeedRow[]>();
  for (const r of ranked) {
    const g = groups.get(r.channelId);
    if (g) g.push(r);
    else groups.set(r.channelId, [r]);
  }
  const channels = [...groups.keys()];
  if (channels.length <= 1) return [...ranked];

  // Rotate the channel start position so each call leads with a different one.
  const offset = ((rotation % channels.length) + channels.length) % channels.length;
  const order = [...channels.slice(offset), ...channels.slice(0, offset)];

  const out: RawFeedRow[] = [];
  const seenParents = new Set<string>();
  let remaining = ranked.length;
  while (remaining > 0) {
    let progressed = false;
    for (const ch of order) {
      const g = groups.get(ch)!;
      // Take the next not-yet-emitted, distinct-parent item from this channel.
      while (g.length > 0) {
        const next = g.shift()!;
        remaining--;
        if (seenParents.has(next.parentVideoId)) continue; // skip dup parent
        seenParents.add(next.parentVideoId);
        out.push(next);
        progressed = true;
        break;
      }
    }
    if (!progressed) break; // all groups exhausted
  }
  return out;
}

// ---------------------------------------------------------------------------
// Hydration: lean RawFeedRow → full DTO for the API response
// ---------------------------------------------------------------------------

/**
 * Batched hydration: turns N ranked RawFeedRows into N DTOs with just TWO
 * queries (one for all clips, one for all legacy items) instead of one query
 * per row. Per-row hydration was an N+1 on the hot feed path — up to 50
 * synchronous SQLite round-trips per request, each blocking the event loop.
 * Output preserves the input order (the ranking + cooldown order).
 */
function hydrateFeedBatch(db: Database.Database, rows: RawFeedRow[]): unknown[] {
  if (rows.length === 0) return [];
  const ids = rows.map((r) => r.id);
  const byId = new Map<string, any>();

  const ph = ids.map(() => "?").join(",");
  // downloaded_videos per LEFT JOIN: seit dem Streaming-Umbau existieren
  // approvte Videos in der Regel OHNE Serverdatei (filePath = null).
  const videoRows = db
    .prepare(`
      SELECT CASE WHEN v.format = 'short' THEN 'short' ELSE 'video' END AS kind,
             f.video_id AS videoId,
             f.video_id AS parentVideoId,
             v.title, v.duration_seconds AS durationSeconds,
             v.aspect_ratio AS aspectRatio, v.thumbnail_url AS thumbnailUrl,
             v.channel_id AS channelId, c.title AS channelTitle,
             s.category, s.reasoning, s.overall_score AS overallScore,
             s.educational_value AS educationalValue,
             NULL AS startSec, NULL AS endSec,
             f.added_to_feed_at AS addedAt, f.saved, f.seen_at AS seenAt,
             v.summary AS summary,
             v.source AS source,
             dv.file_path AS filePath
        FROM feed_items f
        JOIN videos v ON v.id = f.video_id
        JOIN channels c ON c.id = v.channel_id
        JOIN scores s ON s.video_id = f.video_id
        LEFT JOIN downloaded_videos dv ON dv.video_id = f.video_id
       WHERE f.video_id IN (${ph})
    `)
    .all(...ids) as any[];
  for (const vr of videoRows) byId.set(vr.videoId, vr);

  // Reassemble in the ranked order; drop any row a JOIN couldn't resolve.
  return rows.map((r) => byId.get(r.id)).filter((x) => x != null);
}

// ---------------------------------------------------------------------------
// Feed-state mutation helpers — write to BOTH clips and feed_items so that
// clip ids and legacy feed-item ids are handled by the same code path.
// An id exists in exactly one of the two tables; the UPDATE on the other is
// a harmless no-op.
// ---------------------------------------------------------------------------

function updateFeedRow(
  db: Database.Database,
  id: string,
  set: string,
  ...params: unknown[]
): void {
  db.prepare(`UPDATE clips SET ${set} WHERE id = ?`).run(...params, id);
  db.prepare(`UPDATE feed_items SET ${set} WHERE video_id = ?`).run(...params, id);
}

// ---------------------------------------------------------------------------
// Route registration
// ---------------------------------------------------------------------------

export async function registerFeedRoutes(app: FastifyInstance, deps: FeedDeps): Promise<void> {
  // SELECT DISTINCT as defensive safeguard — even though all four JOINed tables
  // (videos, feed_items, scores, downloaded_videos) have video_id as PRIMARY KEY
  // and can't structurally produce duplicates, future schema changes or
  // dev-state drift shouldn't be able to leak duplicate rows to the client.
  const BASE_SELECT = `
    SELECT DISTINCT CASE WHEN v.format = 'short' THEN 'short' ELSE 'video' END AS kind,
           fi.video_id as videoId, v.title, v.duration_seconds as durationSeconds,
           v.aspect_ratio as aspectRatio, v.thumbnail_url as thumbnailUrl,
           v.channel_id as channelId, c.title as channelTitle,
           s.category, s.reasoning, s.overall_score as overallScore,
           s.educational_value as educationalValue,
           fi.added_to_feed_at as addedAt, fi.saved, fi.seen_at as seenAt,
           v.summary AS summary, v.source AS source
    FROM feed_items fi
    JOIN videos v ON v.id = fi.video_id
    JOIN channels c ON c.id = v.channel_id
    JOIN scores s ON s.video_id = fi.video_id
    LEFT JOIN downloaded_videos dv ON dv.video_id = fi.video_id
  `;

  app.get<{ Querystring: { mode?: string; rotation?: string } }>("/feed", async (req, reply) => {
    const mode = (req.query.mode ?? "new") as "new" | "saved" | "old";
    if (mode !== "new" && mode !== "saved" && mode !== "old") {
      return reply.code(400).send({ error: "mode must be new, saved, or old" });
    }

    if (mode === "new") {
      // Pipeline: large pool → curation rank → channel round-robin interleave
      // (variety) → diversity cooldown → page. The rotation makes each app
      // open / tab switch lead with a different channel, so the mix is fresh
      // every time without ever reusing an already-seen clip.
      const now = Date.now();
      // rotation: explicit query param wins (tests/paging); else time-derived
      // so consecutive calls reshuffle. 90s bucket = stable within a session
      // burst but changes between opens.
      const rotation =
        req.query.rotation !== undefined
          ? Number(req.query.rotation) || 0
          : Math.floor(now / 90_000);
      const candidates = listFeedRaw(deps.db, 200);
      const ranked = rankCandidates(candidates, now);
      const interleaved = interleaveByChannel(ranked, rotation);
      const ordered = applyCooldown(interleaved, 50);
      // Batched hydration (2 queries) instead of one query per row (N+1).
      return hydrateFeedBatch(deps.db, ordered);
    } else if (mode === "saved") {
      const clipsRows = deps.db.prepare(`
        SELECT 'clip' AS kind, c.id AS videoId, c.parent_video_id AS parentVideoId,
               v.title, v.aspect_ratio AS aspectRatio, v.thumbnail_url AS thumbnailUrl,
               v.channel_id AS channelId, ch.title AS channelTitle,
               s.category, s.reasoning, s.overall_score AS overallScore,
               s.educational_value AS educationalValue,
               c.start_seconds AS startSec, c.end_seconds AS endSec,
               CAST(ROUND(c.end_seconds - c.start_seconds) AS INTEGER) AS durationSeconds,
               c.added_to_feed_at AS addedAt, c.saved, c.seen_at AS seenAt,
               c.file_path AS filePath
          FROM clips c
          JOIN videos v ON v.id = c.parent_video_id
          JOIN channels ch ON ch.id = v.channel_id
          LEFT JOIN scores s ON s.video_id = c.parent_video_id
         WHERE c.saved = 1
         ORDER BY COALESCE(c.seen_at, c.added_to_feed_at) DESC
      `).all();
      const legacyRows = deps.db.prepare(BASE_SELECT + `
        WHERE fi.saved = 1 AND fi.is_pre_clipper = 1
        ORDER BY COALESCE(fi.seen_at, fi.added_to_feed_at) DESC
        LIMIT 100`).all();
      return [...clipsRows, ...legacyRows].slice(0, 100);
    } else {
      // mode === "old"
      const clipsRows = deps.db.prepare(`
        SELECT 'clip' AS kind, c.id AS videoId, c.parent_video_id AS parentVideoId,
               v.title, v.aspect_ratio AS aspectRatio, v.thumbnail_url AS thumbnailUrl,
               v.channel_id AS channelId, ch.title AS channelTitle,
               s.category, s.reasoning, s.overall_score AS overallScore,
               s.educational_value AS educationalValue,
               c.start_seconds AS startSec, c.end_seconds AS endSec,
               CAST(ROUND(c.end_seconds - c.start_seconds) AS INTEGER) AS durationSeconds,
               c.added_to_feed_at AS addedAt, c.saved, c.seen_at AS seenAt,
               c.file_path AS filePath
          FROM clips c
          JOIN videos v ON v.id = c.parent_video_id
          JOIN channels ch ON ch.id = v.channel_id
          LEFT JOIN scores s ON s.video_id = c.parent_video_id
         WHERE c.seen_at IS NOT NULL
         ORDER BY c.seen_at DESC
         LIMIT 100
      `).all();
      const legacyRows = deps.db.prepare(BASE_SELECT + `
        WHERE fi.seen_at IS NOT NULL AND fi.is_pre_clipper = 1
        ORDER BY fi.seen_at DESC
        LIMIT 100`).all();
      return [...clipsRows, ...legacyRows].slice(0, 100);
    }
  });

  app.get("/queue", async () => {
    const explicit = deps.db
      .prepare(BASE_SELECT + `
        WHERE fi.playback_failed = 0
          AND fi.queued_at IS NOT NULL
          AND fi.is_pre_clipper = 1
        ORDER BY COALESCE(fi.queue_order, fi.queued_at) ASC, fi.queued_at ASC
        LIMIT 12`)
      .all();

    if (explicit.length > 0) return explicit;

    return deps.db
      .prepare(BASE_SELECT + `
        WHERE fi.playback_failed = 0
          AND fi.is_pre_clipper = 1
        ORDER BY
          CASE
            WHEN fi.progress_seconds > 0 THEN 0
            WHEN fi.seen_at IS NULL THEN 1
            WHEN fi.saved = 1 THEN 2
            ELSE 3
          END ASC,
          s.educational_value DESC,
          s.overall_score DESC,
          v.duration_seconds ASC,
          fi.added_to_feed_at DESC
        LIMIT 6`)
      .all();
  });

  app.post<{ Params: { id: string } }>("/queue/:id", async (req, reply) => {
    const existing = deps.db.prepare("SELECT 1 FROM feed_items WHERE video_id = ?").get(req.params.id);
    if (!existing) return reply.code(404).send({ error: "video not found in feed" });

    const maxOrder = deps.db
      .prepare("SELECT COALESCE(MAX(queue_order), 0) AS maxOrder FROM feed_items")
      .get() as { maxOrder: number };

    deps.db
      .prepare(
        `UPDATE feed_items
         SET queued_at = COALESCE(queued_at, ?),
             queue_order = COALESCE(queue_order, ?)
         WHERE video_id = ?`,
      )
      .run(Date.now(), maxOrder.maxOrder + 1, req.params.id);
    return reply.code(204).send();
  });

  app.delete<{ Params: { id: string } }>("/queue/:id", async (req, reply) => {
    deps.db
      .prepare("UPDATE feed_items SET queued_at = NULL, queue_order = NULL WHERE video_id = ?")
      .run(req.params.id);
    return reply.code(204).send();
  });

  app.post<{ Params: { id: string } }>("/feed/:id/seen", async (req, reply) => {
    const id = req.params.id;
    const now = Date.now();
    updateFeedRow(deps.db, id, "seen_at = ?", now);
    // For clips, update last_served_at on the PARENT video; for legacy items, update
    // last_served_at on the video itself. A single UNION query resolves the right id.
    const parent = deps.db.prepare(`
      SELECT parent_video_id AS parentVideoId FROM clips WHERE id = ?
      UNION
      SELECT video_id AS parentVideoId FROM feed_items WHERE video_id = ?
    `).get(id, id) as { parentVideoId: string } | undefined;
    const parentId = parent?.parentVideoId ?? id;
    deps.db
      .prepare("UPDATE downloaded_videos SET last_served_at = ? WHERE video_id = ?")
      .run(now, parentId);
    return reply.code(204).send();
  });

  app.post<{ Params: { id: string } }>("/feed/:id/save", async (req, reply) => {
    updateFeedRow(deps.db, req.params.id, "saved = 1");
    return reply.code(204).send();
  });

  app.put<{
    Params: { id: string };
    Body: { seconds?: number };
  }>("/feed/:id/progress", async (req, reply) => {
    const seconds = Math.max(0, Number(req.body?.seconds ?? 0));
    updateFeedRow(deps.db, req.params.id, "progress_seconds = ?", seconds);
    return reply.code(204).send();
  });

  app.delete<{ Params: { id: string } }>("/feed/:id/save", async (req, reply) => {
    updateFeedRow(deps.db, req.params.id, "saved = 0");
    return reply.code(204).send();
  });

  app.post<{ Params: { id: string } }>("/feed/:id/unplayable", async (req, reply) => {
    updateFeedRow(deps.db, req.params.id, "playback_failed = 1");
    return reply.code(204).send();
  });

  app.post<{ Params: { id: string } }>("/feed/:id/less-like-this", async (req, reply) => {
    const now = Date.now();
    deps.db
      .prepare("UPDATE clips SET seen_at = COALESCE(seen_at, ?), playback_failed = 1 WHERE id = ?")
      .run(now, req.params.id);
    deps.db
      .prepare(
        "UPDATE feed_items SET seen_at = COALESCE(seen_at, ?), playback_failed = 1 WHERE video_id = ?",
      )
      .run(now, req.params.id);
    return reply.code(204).send();
  });

  app.get("/feed/today-count", async () => {
    // Seit Etappe 2 besteht der Feed nur noch aus feed_items (Videos/Shorts) —
    // ungesehene Alt-Clips zählen nicht mehr mit.
    const row = deps.db
      .prepare(
        "SELECT COUNT(*) AS c FROM feed_items WHERE seen_at IS NULL AND playback_failed = 0 AND is_pre_clipper = 1",
      )
      .get() as { c: number };
    const unseenCount = row.c;
    return {
      dailyBudget: deps.dailyBudget,
      unseenCount,
      capped: unseenCount >= deps.dailyBudget,
    };
  });

  app.delete<{ Params: { id: string } }>("/feed/:id", async (req, reply) => {
    const videoId = req.params.id;
    const existing = deps.db.prepare("SELECT 1 FROM videos WHERE id = ?").get(videoId);
    if (!existing) return reply.code(404).send({ error: "video not found" });

    const dlRow = deps.db
      .prepare("SELECT file_path FROM downloaded_videos WHERE video_id = ?")
      .get(videoId) as { file_path: string } | undefined;

    // Collect clip file paths before deleting rows so we can unlink after.
    const clipFiles = deps.db
      .prepare("SELECT file_path FROM clips WHERE parent_video_id = ?")
      .all(videoId) as { file_path: string }[];

    deps.db.transaction(() => {
      deps.db.prepare("DELETE FROM sponsor_segments WHERE video_id = ?").run(videoId);
      deps.db.prepare("DELETE FROM clips WHERE parent_video_id = ?").run(videoId);
      deps.db.prepare("DELETE FROM clipper_queue WHERE video_id = ?").run(videoId);
      deps.db.prepare("DELETE FROM feed_items WHERE video_id = ?").run(videoId);
      deps.db.prepare("DELETE FROM downloaded_videos WHERE video_id = ?").run(videoId);
      deps.db.prepare("DELETE FROM scores WHERE video_id = ?").run(videoId);
      deps.db.prepare("DELETE FROM videos WHERE id = ?").run(videoId);
    })();

    if (dlRow?.file_path) {
      try {
        await fs.promises.unlink(dlRow.file_path);
      } catch {}
    }
    // Unlink clip files (on-disk only — DB rows already deleted above).
    for (const cf of clipFiles) {
      await fs.promises.unlink(cf.file_path).catch(() => undefined);
    }
    return reply.code(204).send();
  });

  app.get<{ Querystring: { limit?: string } }>("/rejected", async (req) => {
    const limit = Math.min(Number(req.query.limit ?? 50), 200);
    return deps.db
      .prepare(`
        SELECT
          v.id AS videoId, v.title, v.channel_id AS channelId,
          c.title AS channelTitle, v.duration_seconds AS durationSeconds,
          v.thumbnail_url AS thumbnailUrl,
          s.overall_score AS overallScore, s.category, s.reasoning,
          s.clickbait_risk AS clickbaitRisk, s.emotional_manipulation AS emotionalManipulation
        FROM scores s
        JOIN videos v ON v.id = s.video_id
        LEFT JOIN channels c ON c.id = v.channel_id
        WHERE s.decision = 'rejected'
        ORDER BY s.scored_at DESC
        LIMIT ?
      `)
      .all(limit);
  });
}
