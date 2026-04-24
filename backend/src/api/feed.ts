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
}
