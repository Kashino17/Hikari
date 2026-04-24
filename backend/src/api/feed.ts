import type { FastifyInstance } from "fastify";
import type Database from "better-sqlite3";

export interface FeedDeps {
  db: Database.Database;
  dailyBudget: number;
}

export async function registerFeedRoutes(app: FastifyInstance, deps: FeedDeps): Promise<void> {
  app.get("/feed", async () => {
    return deps.db
      .prepare(
        `SELECT fi.video_id as videoId, v.title, v.duration_seconds as durationSeconds,
                v.aspect_ratio as aspectRatio, v.thumbnail_url as thumbnailUrl,
                v.channel_id as channelId, c.title as channelTitle,
                s.category, s.reasoning,
                fi.added_to_feed_at as addedAt, fi.saved
         FROM feed_items fi
         JOIN videos v ON v.id = fi.video_id
         JOIN channels c ON c.id = v.channel_id
         JOIN scores s ON s.video_id = fi.video_id
         JOIN downloaded_videos dv ON dv.video_id = fi.video_id
         WHERE fi.seen_at IS NULL
           AND fi.playback_failed = 0
         ORDER BY fi.added_to_feed_at DESC
         LIMIT ?`,
      )
      .all(deps.dailyBudget);
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
