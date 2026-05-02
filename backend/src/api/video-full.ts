import type { FastifyInstance } from "fastify";
import type Database from "better-sqlite3";
import { basename } from "node:path";

export function registerVideoFullRoute(app: FastifyInstance, db: Database.Database): void {
  app.get("/videos/:id/full", async (req, reply) => {
    const id = (req.params as any).id;
    const row = db.prepare(`
      SELECT v.title, v.duration_seconds AS durationSec,
             v.thumbnail_url AS thumbnailUrl,
             ch.title AS channelTitle,
             dl.file_path AS filePath
        FROM videos v
        JOIN channels ch ON ch.id = v.channel_id
        JOIN downloaded_videos dl ON dl.video_id = v.id
       WHERE v.id = ?
    `).get(id) as any;
    if (!row) return reply.status(404).send({ error: "video not found" });
    return {
      title: row.title,
      durationSec: row.durationSec,
      thumbnailUrl: row.thumbnailUrl,
      channelTitle: row.channelTitle,
      fileUrl: `/media/originals/${encodeURIComponent(basename(row.filePath))}`,
    };
  });
}
