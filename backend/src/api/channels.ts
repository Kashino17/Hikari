import type { FastifyInstance } from "fastify";
import type Database from "better-sqlite3";
import { resolveChannel } from "../monitor/channel-resolver.js";

export interface ChannelsDeps {
  db: Database.Database;
}

export async function registerChannelsRoutes(
  app: FastifyInstance,
  deps: ChannelsDeps,
): Promise<void> {
  app.post<{ Body: { channelUrl: string } }>("/channels", async (req, reply) => {
    const { channelUrl } = req.body;
    const resolved = await resolveChannel(channelUrl);
    deps.db
      .prepare(
        `INSERT OR REPLACE INTO channels (id, url, title, added_at, is_active)
         VALUES (?, ?, ?, ?, 1)`,
      )
      .run(resolved.channelId, channelUrl, resolved.title, Date.now());
    return reply.code(200).send({ id: resolved.channelId, title: resolved.title, url: channelUrl });
  });

  app.get("/channels", async () => {
    return deps.db
      .prepare("SELECT id, url, title, added_at, is_active FROM channels WHERE is_active=1 ORDER BY added_at DESC")
      .all();
  });

  app.delete<{ Params: { id: string } }>("/channels/:id", async (req, reply) => {
    deps.db.prepare("UPDATE channels SET is_active = 0 WHERE id = ?").run(req.params.id);
    return reply.code(204).send();
  });
}
