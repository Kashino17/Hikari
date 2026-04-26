import type { FastifyInstance } from "fastify";
import type Database from "better-sqlite3";
import { importDirectLink, fetchImportMetadata, type ImportResult, type ManualMetadata } from "../import/manual-import.js";
import type { MetadataExtractor } from "../scorer/metadata-extractor.js";

export interface VideosDeps {
  db: Database.Database;
  videoDir: string;
  extractor: MetadataExtractor | null;
}

interface ImportBody {
  url?: string;
  metadata?: ManualMetadata;
}

// @fastify/static with { prefix: "/videos/", root: VIDEO_DIR } handles Range,
// Content-Type, and ETag automatically. Direct-link import lives here too.
export async function registerVideosRoutes(
  app: FastifyInstance,
  deps: VideosDeps,
): Promise<void> {
  app.post<{ Body: { url: string } }>("/videos/analyze", async (req, reply) => {
    const { url } = req.body;
    if (!url) return reply.code(400).send({ error: "no url" });

    try {
      const meta = await fetchImportMetadata(url);
      let aiMeta = {};
      if (deps.extractor) {
        aiMeta = await deps.extractor.extract(meta.title ?? "", meta.description ?? "");
      }
      return {
        url,
        title: meta.title,
        description: meta.description,
        thumbnailUrl: meta.thumbnail,
        aiMeta,
      };
    } catch (err) {
      return reply.code(500).send({ error: String(err) });
    }
  });

  app.post<{ Body: ImportBody }>("/videos/import", async (req, reply) => {
    const { url, metadata } = req.body;
    if (!url) return reply.code(400).send({ error: "no url" });

    (async () => {
      try {
        const r = await importDirectLink(deps.db, url, deps.videoDir, metadata);
        app.log.info({ url, result: r }, "manual import");
      } catch (err) {
        app.log.error({ err, url }, "manual import threw");
      }
    })().catch((err) => app.log.error({ err }, "manual import failed"));

    return reply.code(202).send({ status: "queued" });
  });

  app.post<{
    Body: { items?: { url: string; metadata?: ManualMetadata }[] };
  }>("/videos/import/bulk", async (req, reply) => {
    const items = req.body?.items;
    if (!Array.isArray(items) || items.length === 0) {
      return reply.code(400).send({ error: "no items" });
    }

    const queue = [...items];
    const max = 4;
    const runners = Array.from({ length: max }, async () => {
      while (queue.length > 0) {
        const item = queue.shift();
        if (!item) break;
        try {
          await importDirectLink(deps.db, item.url, deps.videoDir, item.metadata);
        } catch (err) {
          app.log.error({ err, url: item.url }, "bulk import item failed");
        }
      }
    });
    Promise.all(runners).catch((err) =>
      app.log.error({ err }, "bulk import runners crashed"),
    );

    return reply.code(202).send({ queued: items.length });
  });

  app.get("/library", async () => {
    const series = deps.db.prepare("SELECT * FROM series ORDER BY added_at DESC").all();
    const recentlyAdded = deps.db.prepare(`
      SELECT v.*, c.title as channelTitle, fi.progress_seconds
      FROM videos v
      JOIN channels c ON c.id = v.channel_id
      JOIN feed_items fi ON fi.video_id = v.id
      ORDER BY v.discovered_at DESC
      LIMIT 20
    `).all();
    const channels = deps.db.prepare("SELECT * FROM channels WHERE is_active = 1").all();

    return { series, recentlyAdded, channels };
  });

  app.get("/series", async () => {
    return deps.db
      .prepare("SELECT id, title FROM series ORDER BY title")
      .all();
  });

  app.get<{ Params: { id: string } }>("/series/:id", async (req, reply) => {
    const series = deps.db.prepare("SELECT * FROM series WHERE id = ?").get(req.params.id);
    if (!series) return reply.code(404).send({ error: "series not found" });

    const videos = deps.db.prepare(`
      SELECT v.*, fi.progress_seconds
      FROM videos v
      LEFT JOIN feed_items fi ON fi.video_id = v.id
      WHERE v.series_id = ?
      ORDER BY v.season ASC, v.episode ASC
    `).all(req.params.id);

    return { ...series, videos };
  });
}
