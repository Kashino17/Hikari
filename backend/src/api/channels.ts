import type { FastifyInstance } from "fastify";
import { MANUAL_CHANNEL_ID } from "../import/manual-import.js";
import type Database from "better-sqlite3";
import { refreshChannelMetadata, resolveChannel } from "../monitor/channel-resolver.js";
import { validateYouTubeChannelUrl } from "../monitor/youtube-url.js";
import { searchChannels } from "../monitor/channel-search.js";
import { fetchChannelDeepScan } from "../monitor/deep-scan.js";
import { fetchChannelFeed } from "../monitor/rss-poller.js";
import { isSubscribedChannel, SUBSCRIBED_CHANNEL_IDS_SQL } from "../feed/subscriptions.js";
import { processNewVideo } from "../pipeline/orchestrator.js";
import { fetchVideoMetadata } from "../ingest/metadata.js";
import { fetchSponsorSegments } from "../sponsorblock/client.js";

export interface ChannelsDeps {
  db: Database.Database;
}

export async function registerChannelsRoutes(
  app: FastifyInstance,
  deps: ChannelsDeps,
): Promise<void> {
  app.post<{ Body: { channelUrl: string } }>("/channels", async (req, reply) => {
    const { channelUrl } = req.body ?? {};
    // SSRF guard: only spawn yt-dlp against a validated YouTube channel URL.
    // Without this, an arbitrary url (file://, internal host, metadata endpoint)
    // would be passed straight to the yt-dlp child process.
    const safeUrl = validateYouTubeChannelUrl(channelUrl ?? "");
    if (!safeUrl) {
      return reply.code(400).send({ error: "invalid YouTube channel URL" });
    }
    const resolved = await resolveChannel(safeUrl);
    // Hinzufügen = abonnieren. Upsert statt INSERT OR REPLACE: ein schon
    // bekannter Kanal (früher abbestellt, alte Probe) behält seine Zeile samt
    // Videos und Poll-Stand und wird nur wieder zum Abo.
    deps.db
      .prepare(
        `INSERT INTO channels
         (id, url, title, added_at, is_active, status, handle, description, subscribers, thumbnail_url, banner_url)
         VALUES (?, ?, ?, ?, 1, 'subscribed', ?, ?, ?, ?, ?)
         ON CONFLICT(id) DO UPDATE SET
           url = excluded.url,
           title = excluded.title,
           added_at = excluded.added_at,
           is_active = 1,
           status = 'subscribed',
           handle = excluded.handle,
           description = excluded.description,
           subscribers = excluded.subscribers,
           thumbnail_url = excluded.thumbnail_url,
           banner_url = excluded.banner_url`,
      )
      .run(
        resolved.channelId,
        safeUrl,
        resolved.title,
        Date.now(),
        resolved.handle,
        resolved.description,
        resolved.subscribers,
        resolved.thumbnail,
        resolved.banner,
      );
    return reply.code(200).send({
      id: resolved.channelId,
      title: resolved.title,
      url: safeUrl,
      handle: resolved.handle,
      thumbnail_url: resolved.thumbnail,
      banner_url: resolved.banner,
      subscribers: resolved.subscribers,
    });
  });

  app.get("/channels", async () => {
    return deps.db
      .prepare(
        `SELECT id, url, title, added_at, is_active, last_polled_at,
                handle, description, subscribers, thumbnail_url, banner_url
         FROM channels WHERE is_active=1 ORDER BY added_at DESC`,
      )
      .all();
  });

  // Bekannter Kanal → Abo (z. B. der Kanal eines gespeicherten Videos): er
  // wandert in die reguläre Poll-Rotation, seine neuen Videos in den Feed.
  app.post<{ Params: { id: string } }>("/channels/:id/subscribe", async (req, reply) => {
    const row = deps.db.prepare("SELECT 1 FROM channels WHERE id = ?").get(req.params.id);
    if (!row) return reply.code(404).send({ error: "channel not found" });
    deps.db
      .prepare("UPDATE channels SET status = 'subscribed', is_active = 1 WHERE id = ?")
      .run(req.params.id);
    return reply.code(204).send();
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
          (deps.db.prepare(SUBSCRIBED_CHANNEL_IDS_SQL).all() as { id: string }[]).map((r) => r.id),
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

  // Pro Kanal max. ein laufender Poll: die Ingest-Pipeline unten (yt-dlp-
  // Metadaten + SponsorBlock pro Video) ist teuer — mehrfaches
  // Antippen darf keine parallelen Läufe desselben Kanals stapeln.
  const pollsInFlight = new Set<string>();

  app.post<{
    Params: { id: string };
    Querystring: { deep?: string; limit?: string };
  }>("/channels/:id/poll", async (req, reply) => {
    const channelId = req.params.id;
    if (pollsInFlight.has(channelId)) {
      return reply.code(409).send({ error: "poll already running for this channel" });
    }
    // Das Archiv der manuellen Importe ist kein echter Kanal — seine URL
    // ("manual:hikari") ist kein RSS-Feed. Der Abruf endete deshalb in einem
    // 500er, sobald jemand im Archiv auf Aktualisieren tippte. Es gibt hier
    // schlicht nichts abzurufen: Der Bestand wächst nur durch eigene Importe.
    if (channelId === MANUAL_CHANNEL_ID) {
      return { added: 0, skipped: 0, message: "Manuelle Importe kennen keinen Abruf" };
    }
    // Nur Abos werden abgerufen — Videos anderer Kanäle kommen nie in den Feed.
    const channel = deps.db.prepare("SELECT id, url FROM channels WHERE id = ?").get(channelId) as
      | { id: string; url: string }
      | undefined;
    if (!channel || !isSubscribedChannel(deps.db, channelId)) {
      return reply.code(404).send({ error: "channel not found or not subscribed" });
    }

    const isDeep = req.query.deep === "true" || req.query.deep === "1";
    const deepLimit = Math.min(Math.max(Number(req.query.limit ?? 50) || 50, 1), 200);

    pollsInFlight.add(channelId);

    // RSS gives ~15 entries. Deep scan via yt-dlp can fetch up to `limit`.
    const entries = await (isDeep
      ? fetchChannelDeepScan(channelId, deepLimit)
      : fetchChannelFeed(channelId)
    ).catch((err) => {
      pollsInFlight.delete(channelId);
      throw err;
    });

    const newEntries = entries.filter(
      (e) => !deps.db.prepare("SELECT 1 FROM videos WHERE id = ?").get(e.videoId),
    );
    const skipped = entries.length - newEntries.length;
    const queued = newEntries.length;

    deps.db
      .prepare("UPDATE channels SET last_polled_at = ? WHERE id = ?")
      .run(Date.now(), channelId);

    // Refresh channel metadata in the background — keeps thumbnail/handle/
    // subscribers fresh without an extra explicit endpoint. Errors silenced
    // because metadata refresh is best-effort.
    (async () => {
      try {
        await refreshChannelMetadata(deps.db, channelId, channel.url);
      } catch (err) {
        app.log.debug({ err, channelId }, "channel metadata refresh failed (non-fatal)");
      }
    })().catch(() => {});

    // Fire-and-forget background ingest of new videos.
    (async () => {
      for (const e of newEntries) {
        try {
          await processNewVideo({
            db: deps.db,
            videoId: e.videoId,
            channelId,
            fetchMetadata: fetchVideoMetadata,
            fetchSponsorSegments,
          });
        } catch (err) {
          app.log.warn(
            { err, videoId: e.videoId, channelId },
            "channel poll: video failed",
          );
        }
      }
      app.log.info({ channelId, queued, isDeep }, "channel poll completed");
    })()
      .catch((err) => {
        app.log.error({ err, channelId }, "channel poll background failed");
      })
      .finally(() => {
        pollsInFlight.delete(channelId);
      });

    return reply.code(202).send({ queued, skipped, errors: [], deep: isDeep });
  });

  app.get<{ Params: { id: string } }>("/channels/:id/videos", async (req, reply) => {
    const channelId = req.params.id;
    const channel = deps.db
      .prepare("SELECT id FROM channels WHERE id = ?")
      .get(channelId);
    if (!channel) return reply.code(404).send({ error: "channel not found" });

    return deps.db.prepare(`
      SELECT v.id AS videoId,
             v.title,
             v.thumbnail_url AS thumbnailUrl,
             v.duration_seconds AS durationSeconds,
             v.published_at AS publishedAt,
             v.discovered_at AS discoveredAt,
             s.overall_score AS score,
             s.category,
             s.reasoning,
             s.decision,
             dv.file_size_bytes AS downloadedBytes,
             fi.added_to_feed_at AS addedToFeedAt,
             fi.seen_at AS seenAt,
             fi.saved AS saved
      FROM videos v
      LEFT JOIN scores s ON s.video_id = v.id
      LEFT JOIN downloaded_videos dv ON dv.video_id = v.id
      LEFT JOIN feed_items fi ON fi.video_id = v.id
      WHERE v.channel_id = ?
      ORDER BY COALESCE(v.published_at, v.discovered_at) DESC
    `).all(channelId);
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
