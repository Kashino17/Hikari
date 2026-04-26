import type { FastifyInstance } from "fastify";
import type Database from "better-sqlite3";
import fs from "node:fs";

export interface FeedDeps {
  db: Database.Database;
  dailyBudget: number;
}

export async function registerFeedRoutes(app: FastifyInstance, deps: FeedDeps): Promise<void> {
  const NEW_BASELINE_COUNT = 10;
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
      return deps.db
        .prepare(`
          WITH recent AS (
            SELECT video_id
            FROM feed_items
            WHERE playback_failed = 0
            ORDER BY added_to_feed_at DESC
            LIMIT ${NEW_BASELINE_COUNT}
          )
          ${BASE_SELECT}
          WHERE fi.playback_failed = 0
            AND (fi.seen_at IS NULL OR fi.video_id IN (SELECT video_id FROM recent))
          ORDER BY fi.added_to_feed_at DESC
        `)
        .all();
    } else if (mode === "saved") {
      return deps.db
        .prepare(BASE_SELECT + `
          WHERE fi.saved = 1
          ORDER BY COALESCE(fi.seen_at, fi.added_to_feed_at) DESC
          LIMIT 100`)
        .all();
    } else {
      return deps.db
        .prepare(BASE_SELECT + `
          WHERE fi.seen_at IS NOT NULL
          ORDER BY fi.seen_at DESC
          LIMIT 100`)
        .all();
    }
  });

  app.get("/queue", async () => {
    const explicit = deps.db
      .prepare(BASE_SELECT + `
        WHERE fi.playback_failed = 0
          AND fi.queued_at IS NOT NULL
        ORDER BY COALESCE(fi.queue_order, fi.queued_at) ASC, fi.queued_at ASC
        LIMIT 12`)
      .all();

    if (explicit.length > 0) return explicit;

    return deps.db
      .prepare(BASE_SELECT + `
        WHERE fi.playback_failed = 0
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
    deps.db
      .prepare("UPDATE feed_items SET seen_at = ? WHERE video_id = ?")
      .run(Date.now(), req.params.id);
    deps.db
      .prepare("UPDATE downloaded_videos SET last_served_at = ? WHERE video_id = ?")
      .run(Date.now(), req.params.id);
    return reply.code(204).send();
  });

  app.post<{ Params: { id: string } }>("/feed/:id/save", async (req, reply) => {
    deps.db.prepare("UPDATE feed_items SET saved = 1 WHERE video_id = ?").run(req.params.id);
    return reply.code(204).send();
  });

  app.delete<{ Params: { id: string } }>("/feed/:id/save", async (req, reply) => {
    deps.db.prepare("UPDATE feed_items SET saved = 0 WHERE video_id = ?").run(req.params.id);
    return reply.code(204).send();
  });

  app.post<{ Params: { id: string } }>("/feed/:id/unplayable", async (req, reply) => {
    deps.db
      .prepare("UPDATE feed_items SET playback_failed = 1 WHERE video_id = ?")
      .run(req.params.id);
    return reply.code(204).send();
  });

  app.post<{ Params: { id: string } }>("/feed/:id/less-like-this", async (req, reply) => {
    deps.db
      .prepare(
        "UPDATE feed_items SET seen_at = COALESCE(seen_at, ?), playback_failed = 1 WHERE video_id = ?",
      )
      .run(Date.now(), req.params.id);
    return reply.code(204).send();
  });

  app.get("/feed/today-count", async () => {
    const row = deps.db
      .prepare("SELECT COUNT(*) AS c FROM feed_items WHERE seen_at IS NULL AND playback_failed = 0")
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

    deps.db.transaction(() => {
      deps.db.prepare("DELETE FROM sponsor_segments WHERE video_id = ?").run(videoId);
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
