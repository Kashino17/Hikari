import type { FastifyInstance } from "fastify";
import { MANUAL_CHANNEL_ID } from "../import/manual-import.js";
import type Database from "better-sqlite3";
import { refreshChannelMetadata, resolveChannel } from "../monitor/channel-resolver.js";
import { validateYouTubeChannelUrl } from "../monitor/youtube-url.js";
import { searchChannels } from "../monitor/channel-search.js";
import { fetchChannelDeepScan } from "../monitor/deep-scan.js";
import { recommendChannels } from "../monitor/recommendations.js";
import { fetchChannelFeed } from "../monitor/rss-poller.js";
import { processNewVideo } from "../pipeline/orchestrator.js";
import { fetchVideoMetadata } from "../ingest/metadata.js";
import { fetchTranscript } from "../ingest/transcript.js";
import { fetchSponsorSegments } from "../sponsorblock/client.js";
import type { Scorer } from "../scorer/types.js";

export interface ChannelsDeps {
  db: Database.Database;
  scorer?: Scorer;
  videoDir?: string;
  /** Clipper-Maschinerie im Ingest — Default aus seit Etappe 2. */
  clipperEnabled?: boolean;
  /** Karten-Teaser für Langvideos — best-effort, darf fehlen. */
  summarize?: (title: string, transcript: string) => Promise<string | null>;
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
    deps.db
      .prepare(
        `INSERT OR REPLACE INTO channels
         (id, url, title, added_at, is_active, handle, description, subscribers, thumbnail_url, banner_url)
         VALUES (?, ?, ?, ?, 1, ?, ?, ?, ?, ?)`,
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
                handle, description, subscribers, thumbnail_url,
                banner_url, auto_approve AS autoApprove
         FROM channels WHERE is_active=1 ORDER BY added_at DESC`,
      )
      .all();
  });

  app.patch<{
    Params: { id: string };
    Body: { autoApprove: boolean };
  }>("/channels/:id/auto-approve", async (req, reply) => {
    const channel = deps.db
      .prepare("SELECT id FROM channels WHERE id = ? AND is_active = 1")
      .get(req.params.id);
    if (!channel) return reply.code(404).send({ error: "channel not found" });
    deps.db
      .prepare("UPDATE channels SET auto_approve = ? WHERE id = ?")
      .run(req.body.autoApprove ? 1 : 0, req.params.id);
    return { id: req.params.id, autoApprove: req.body.autoApprove };
  });

  // Probe → Abo: der Kanal wandert in die reguläre Poll-Rotation.
  app.post<{ Params: { id: string } }>("/channels/:id/subscribe", async (req, reply) => {
    const row = deps.db.prepare("SELECT 1 FROM channels WHERE id = ?").get(req.params.id);
    if (!row) return reply.code(404).send({ error: "channel not found" });
    deps.db
      .prepare("UPDATE channels SET status = 'subscribed', is_active = 1 WHERE id = ?")
      .run(req.params.id);
    return reply.code(204).send();
  });

  // Kanal dauerhaft blocken: taucht nie wieder als Empfehlung/Probe auf.
  app.post<{ Params: { id: string } }>("/channels/:id/block", async (req, reply) => {
    const row = deps.db.prepare("SELECT 1 FROM channels WHERE id = ?").get(req.params.id);
    if (!row) return reply.code(404).send({ error: "channel not found" });
    deps.db.transaction(() => {
      deps.db
        .prepare("UPDATE channels SET status = 'blocked', is_active = 0 WHERE id = ?")
        .run(req.params.id);
      // Geblockter Kanal verschwindet sofort aus dem Feed — weich (→ "Alt"), kein Löschen.
      deps.db
        .prepare(
          `UPDATE feed_items SET seen_at = ? WHERE seen_at IS NULL AND video_id IN
           (SELECT id FROM videos WHERE channel_id = ?)`,
        )
        .run(Date.now(), req.params.id);
    })();
    return reply.code(204).send();
  });

  app.get<{ Querystring: { force?: string } }>(
    "/channels/recommendations",
    async (req, reply) => {
      const bypassCache = req.query.force === "true" || req.query.force === "1";
      try {
        const results = await recommendChannels(deps.db, { bypassCache });
        return reply.code(200).send(results);
      } catch (err) {
        app.log.warn({ err }, "channel recommendations failed");
        return reply.code(502).send({ error: "recommendations failed" });
      }
    },
  );

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

  // Pro Kanal max. ein laufender Poll: die Ingest-Pipeline unten (yt-dlp-
  // Metadaten + Transkript + Download pro Video) ist teuer — mehrfaches
  // Antippen darf keine parallelen Läufe desselben Kanals stapeln.
  const pollsInFlight = new Set<string>();

  app.post<{
    Params: { id: string };
    Querystring: { deep?: string; limit?: string };
  }>("/channels/:id/poll", async (req, reply) => {
    if (!deps.scorer || !deps.videoDir) {
      return reply.code(503).send({ error: "poll not available: scorer/videoDir not configured" });
    }
    const channelId = req.params.id;
    if (pollsInFlight.has(channelId)) {
      return reply.code(409).send({ error: "poll already running for this channel" });
    }
    const channel = deps.db
      .prepare("SELECT id, url FROM channels WHERE id = ? AND is_active = 1")
      .get(channelId) as { id: string; url: string } | undefined;
    if (!channel) return reply.code(404).send({ error: "channel not found or inactive" });

    // Das Archiv der manuellen Importe ist kein echter Kanal — seine URL
    // ("manual:hikari") ist kein RSS-Feed. Der Abruf endete deshalb in einem
    // 500er, sobald jemand im Archiv auf Aktualisieren tippte. Es gibt hier
    // schlicht nichts abzurufen: Der Bestand wächst nur durch eigene Importe.
    if (channelId === MANUAL_CHANNEL_ID) {
      return { added: 0, skipped: 0, message: "Manuelle Importe kennen keinen Abruf" };
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
            fetchTranscript,
            fetchSponsorSegments,
            scorer: deps.scorer!,
            clipperEnabled: deps.clipperEnabled,
            summarize: deps.summarize,
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
