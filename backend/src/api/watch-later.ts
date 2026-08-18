import type Database from "better-sqlite3";
import type { FastifyInstance } from "fastify";
import { hydrateFeedBatch } from "./feed.js";

/**
 * "Später ansehen" — der Ablageort für weggeswipte Langvideo-Karten:
 * der Feed-Wegswipe legt Videos hier ab, das Öffnen räumt sie wieder raus.
 */
export async function registerWatchLaterRoutes(
  app: FastifyInstance,
  deps: { db: Database.Database },
): Promise<void> {
  app.get("/watch-later", async () => {
    const rows = deps.db
      .prepare("SELECT video_id AS id FROM watch_later ORDER BY added_at DESC LIMIT 100")
      .all() as { id: string }[];
    return hydrateFeedBatch(deps.db, rows);
  });

  app.post<{ Params: { id: string } }>("/watch-later/:id", async (req, reply) => {
    const known = deps.db.prepare("SELECT 1 FROM videos WHERE id = ?").get(req.params.id);
    if (!known) return reply.code(404).send({ error: "video not found" });
    deps.db
      .prepare("INSERT OR IGNORE INTO watch_later (video_id, added_at) VALUES (?, ?)")
      .run(req.params.id, Date.now());
    return reply.code(204).send();
  });

  app.delete<{ Params: { id: string } }>("/watch-later/:id", async (req, reply) => {
    deps.db.prepare("DELETE FROM watch_later WHERE video_id = ?").run(req.params.id);
    return reply.code(204).send();
  });
}
