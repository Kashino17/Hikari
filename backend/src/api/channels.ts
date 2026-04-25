import type { FastifyInstance } from "fastify";
import type Database from "better-sqlite3";
import { resolveChannel } from "../monitor/channel-resolver.js";
import { searchChannels } from "../monitor/channel-search.js";
import { fetchChannelFeed } from "../monitor/rss-poller.js";
import { processNewVideo } from "../pipeline/orchestrator.js";
import { fetchVideoMetadata } from "../ingest/metadata.js";
import { fetchTranscript } from "../ingest/transcript.js";
import { fetchSponsorSegments } from "../sponsorblock/client.js";
import { downloadVideo } from "../download/worker.js";
import type { Scorer } from "../scorer/types.js";

export interface ChannelsDeps {
  db: Database.Database;
  scorer?: Scorer;
  videoDir?: string;
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

  app.get<{ Querystring: { q?: string; limit?: string } }>(
    "/channels/search",
    async (req, reply) => {
      const q = (req.query.q ?? "").trim();
      if (q.length < 2) return reply.code(200).send([]);
      const limit = Math.min(Math.max(Number(req.query.limit ?? 10) || 10, 1), 25);
      try {
        const results = await searchChannels(q, limit);
        // Mark which results the user already follows so the UI can dim those.
        const subscribedIds = new Set(
          (deps.db
            .prepare("SELECT id FROM channels WHERE is_active=1")
            .all() as { id: string }[]).map((r) => r.id),
        );
        return results.map((r) => ({ ...r, subscribed: subscribedIds.has(r.channelId) }));
      } catch (err) {
        app.log.warn({ err, q }, "channel search failed");
        return reply.code(502).send({ error: "search failed" });
      }
    },
  );

  app.delete<{ Params: { id: string } }>("/channels/:id", async (req, reply) => {
    deps.db.prepare("UPDATE channels SET is_active = 0 WHERE id = ?").run(req.params.id);
    return reply.code(204).send();
  });

  app.post<{ Params: { id: string } }>("/channels/:id/poll", async (req, reply) => {
    if (!deps.scorer || !deps.videoDir) {
      return reply.code(503).send({ error: "poll not available: scorer/videoDir not configured" });
    }
    const channelId = req.params.id;
    const channel = deps.db
      .prepare("SELECT id FROM channels WHERE id = ? AND is_active = 1")
      .get(channelId);
    if (!channel) return reply.code(404).send({ error: "channel not found or inactive" });

    // Fetch RSS (all ~15 entries YouTube returns) and split into
    // {already-processed, new}. Respond immediately with the counts so the
    // UI unblocks; actual ingestion runs in the background — each video can
    // take 30s-2min (metadata + LLM score + optional 100 MB download).
    const entries = await fetchChannelFeed(channelId);
    const newEntries = entries.filter(
      (e) => !deps.db.prepare("SELECT 1 FROM videos WHERE id = ?").get(e.videoId),
    );
    const skipped = entries.length - newEntries.length;
    const queued = newEntries.length;

    deps.db
      .prepare("UPDATE channels SET last_polled_at = ? WHERE id = ?")
      .run(Date.now(), channelId);

    // Fire-and-forget background processing
    (async () => {
      for (const e of newEntries) {
        try {
          await processNewVideo({
            db: deps.db,
            videoId: e.videoId,
            channelId,
            fetchMetadata: fetchVideoMetadata,
            fetchTranscript,
            fetchSponsorSegments,
            scorer: deps.scorer!,
            download: (id) => downloadVideo({ videoId: id, outDir: deps.videoDir! }),
          });
        } catch (err) {
          app.log.warn(
            { err, videoId: e.videoId, channelId },
            "channel poll: video failed",
          );
        }
      }
      app.log.info({ channelId, queued }, "channel poll completed");
    })().catch((err) => {
      app.log.error({ err, channelId }, "channel poll background failed");
    });

    return reply.code(202).send({ queued, skipped, errors: [] });
  });

  app.get<{ Params: { id: string } }>("/channels/:id/stats", async (req, reply) => {
    const channelId = req.params.id;
    const channel = deps.db
      .prepare("SELECT id FROM channels WHERE id = ?")
      .get(channelId);
    if (!channel) return reply.code(404).send({ error: "channel not found" });

    const row = deps.db.prepare(`
      SELECT
        COUNT(DISTINCT v.id) AS totalVideos,
        SUM(CASE WHEN s.decision = 'approved' THEN 1 ELSE 0 END) AS approved,
        SUM(CASE WHEN s.decision = 'rejected' THEN 1 ELSE 0 END) AS rejected,
        MAX(v.discovered_at) AS latestAdded,
        COALESCE(SUM(dv.file_size_bytes), 0) AS diskBytes
      FROM videos v
      LEFT JOIN scores s ON s.video_id = v.id
      LEFT JOIN downloaded_videos dv ON dv.video_id = v.id
      WHERE v.channel_id = ?
    `).get(channelId) as {
      totalVideos: number;
      approved: number | null;
      rejected: number | null;
      latestAdded: number | null;
      diskBytes: number;
    };

    return {
      totalVideos: row.totalVideos,
      approved: row.approved ?? 0,
      rejected: row.rejected ?? 0,
      latestAdded: row.latestAdded,
      diskBytes: row.diskBytes,
    };
  });
}
