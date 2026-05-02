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
  kind: "clip" | "legacy";
  id: string;
  parentVideoId: string;
  channelId: string;
  category: string | null;
  addedToFeedAt: number;
  durationSec: number;
}

const COOLDOWN_WINDOW = 3;
const CHANNEL_MAX_IN_WINDOW = 2;
const LOOKAHEAD = 5;

/**
 * UNION over clips (new clipper-pipeline items) + legacy feed_items
 * (pre-clipper items kept around with is_pre_clipper=1). Sorted by recency.
 * Used as the candidate pool for the "new" feed mode before cooldown.
 */
export function listFeedRaw(db: Database.Database, limit: number): RawFeedRow[] {
  return db.prepare(`
    SELECT 'clip' AS kind,
           c.id AS id,
           c.parent_video_id AS parentVideoId,
           v.channel_id AS channelId,
           s.category AS category,
           c.added_to_feed_at AS addedToFeedAt,
           (c.end_seconds - c.start_seconds) AS durationSec
      FROM clips c
      JOIN videos v ON v.id = c.parent_video_id
      LEFT JOIN scores s ON s.video_id = c.parent_video_id
     WHERE c.seen_at IS NULL AND c.playback_failed = 0
    UNION ALL
    SELECT 'legacy' AS kind,
           f.video_id AS id,
           f.video_id AS parentVideoId,
           v.channel_id AS channelId,
           s.category AS category,
           f.added_to_feed_at AS addedToFeedAt,
           v.duration_seconds AS durationSec
      FROM feed_items f
      JOIN videos v ON v.id = f.video_id
      LEFT JOIN scores s ON s.video_id = f.video_id
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
      out.push(remaining.splice(fallbackIdx, 1)[0]);
      continue;
    }

    const primary = window[pickIdx];

    const lastOut = out[out.length - 1];
    if (lastOut && primary.category && lastOut.category === primary.category) {
      const swapIdx = window.findIndex((r, i) =>
        i !== pickIdx &&
        !last3Parents.has(r.parentVideoId) &&
        channelCount(r.channelId) < CHANNEL_MAX_IN_WINDOW &&
        r.category && r.category !== lastOut.category
      );
      if (swapIdx !== -1) {
        const better = window[swapIdx];
        const realIdx = remaining.indexOf(better);
        out.push(remaining.splice(realIdx, 1)[0]);
        continue;
      }
    }

    const realIdx = remaining.indexOf(primary);
    out.push(remaining.splice(realIdx, 1)[0]);
  }

  return out;
}

// ---------------------------------------------------------------------------
// Hydration: lean RawFeedRow → full DTO for the API response
// ---------------------------------------------------------------------------

function hydrateFeedItem(db: Database.Database, row: RawFeedRow): unknown {
  if (row.kind === "clip") {
    return db.prepare(`
      SELECT 'clip' AS kind,
             c.id AS videoId,
             c.parent_video_id AS parentVideoId,
             v.title, v.aspect_ratio AS aspectRatio, v.thumbnail_url AS thumbnailUrl,
             v.channel_id AS channelId, ch.title AS channelTitle,
             s.category, s.reasoning, s.overall_score AS overallScore,
             s.educational_value AS educationalValue,
             c.start_seconds AS startSec, c.end_seconds AS endSec,
             (c.end_seconds - c.start_seconds) AS durationSeconds,
             c.added_to_feed_at AS addedAt, c.saved, c.seen_at AS seenAt,
             c.file_path AS filePath
        FROM clips c
        JOIN videos v ON v.id = c.parent_video_id
        JOIN channels ch ON ch.id = v.channel_id
        LEFT JOIN scores s ON s.video_id = c.parent_video_id
       WHERE c.id = ?
    `).get(row.id);
  }
  // legacy
  return db.prepare(`
    SELECT 'legacy' AS kind,
           f.video_id AS videoId,
           f.video_id AS parentVideoId,
           v.title, v.duration_seconds AS durationSeconds,
           v.aspect_ratio AS aspectRatio, v.thumbnail_url AS thumbnailUrl,
           v.channel_id AS channelId, c.title AS channelTitle,
           s.category, s.reasoning, s.overall_score AS overallScore,
           s.educational_value AS educationalValue,
           NULL AS startSec, NULL AS endSec,
           f.added_to_feed_at AS addedAt, f.saved, f.seen_at AS seenAt,
           dv.file_path AS filePath
      FROM feed_items f
      JOIN videos v ON v.id = f.video_id
      JOIN channels c ON c.id = v.channel_id
      JOIN scores s ON s.video_id = f.video_id
      JOIN downloaded_videos dv ON dv.video_id = f.video_id
     WHERE f.video_id = ?
  `).get(row.id);
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
    SELECT DISTINCT fi.video_id as videoId, v.title, v.duration_seconds as durationSeconds,
           v.aspect_ratio as aspectRatio, v.thumbnail_url as thumbnailUrl,
           v.channel_id as channelId, c.title as channelTitle,
           s.category, s.reasoning, s.overall_score as overallScore,
           s.educational_value as educationalValue,
           fi.added_to_feed_at as addedAt, fi.saved, fi.seen_at as seenAt
    FROM feed_items fi
    JOIN videos v ON v.id = fi.video_id
    JOIN channels c ON c.id = v.channel_id
    JOIN scores s ON s.video_id = fi.video_id
    JOIN downloaded_videos dv ON dv.video_id = fi.video_id
  `;

  app.get<{ Querystring: { mode?: string } }>("/feed", async (req, reply) => {
    const mode = (req.query.mode ?? "new") as "new" | "saved" | "old";
    if (mode !== "new" && mode !== "saved" && mode !== "old") {
      return reply.code(400).send({ error: "mode must be new, saved, or old" });
    }

    if (mode === "new") {
      const candidates = listFeedRaw(deps.db, 100);
      const ordered = applyCooldown(candidates, 50);
      return ordered.map((r) => hydrateFeedItem(deps.db, r));
    } else if (mode === "saved") {
      const clipsRows = deps.db.prepare(`
        SELECT 'clip' AS kind, c.id AS videoId, c.parent_video_id AS parentVideoId,
               v.title, v.aspect_ratio AS aspectRatio, v.thumbnail_url AS thumbnailUrl,
               v.channel_id AS channelId, ch.title AS channelTitle,
               s.category, s.reasoning, s.overall_score AS overallScore,
               s.educational_value AS educationalValue,
               c.start_seconds AS startSec, c.end_seconds AS endSec,
               (c.end_seconds - c.start_seconds) AS durationSeconds,
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
               (c.end_seconds - c.start_seconds) AS durationSeconds,
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
    const row = deps.db
      .prepare(`
        SELECT
          (SELECT COUNT(*) FROM clips WHERE seen_at IS NULL AND playback_failed = 0)
          + (SELECT COUNT(*) FROM feed_items WHERE seen_at IS NULL AND playback_failed = 0 AND is_pre_clipper = 1)
          AS c
      `)
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
