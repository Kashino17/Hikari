import type Database from "better-sqlite3";
import type { FastifyInstance } from "fastify";
import {
  type ImportResult,
  type ManualMetadata,
  fetchImportMetadata,
  importDirectLink,
  scrapeImportLinksFromPage,
} from "../import/manual-import.js";
import type { MetadataExtractor } from "../scorer/metadata-extractor.js";

export interface VideosDeps {
  db: Database.Database;
  videoDir: string;
  extractor: MetadataExtractor | null;
}

interface ImportBody {
  url?: string;
  urls?: string[];
  metadata?: ManualMetadata;
  scrapeLinks?: boolean;
}

function collectImportUrls(body: ImportBody): string[] {
  const raw = [body.url, ...(body.urls ?? [])];
  const urls = raw
    .map((u) => u?.trim() ?? "")
    .filter((u) => u.startsWith("http://") || u.startsWith("https://"));
  return [...new Set(urls)];
}

// @fastify/static with { prefix: "/videos/", root: VIDEO_DIR } handles Range,
// Content-Type, and ETag automatically. Direct-link import lives here too.
export async function registerVideosRoutes(app: FastifyInstance, deps: VideosDeps): Promise<void> {
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
    const body = req.body ?? {};
    const { metadata, scrapeLinks } = body;
    const inputUrls = collectImportUrls(body);
    if (inputUrls.length === 0) return reply.code(400).send({ error: "no url" });

    const scrapeErrors: Array<{ url: string; error: string }> = [];
    let importUrls = inputUrls;

    if (scrapeLinks === true) {
      const discovered: string[] = [];
      for (const pageUrl of inputUrls) {
        try {
          const result = await scrapeImportLinksFromPage(pageUrl);
          discovered.push(...result.links);
          app.log.info(
            { pageUrl, totalFound: result.totalFound, queued: result.links.length },
            "manual import page scraped",
          );
        } catch (err) {
          scrapeErrors.push({
            url: pageUrl,
            error: err instanceof Error ? err.message : String(err),
          });
        }
      }
      importUrls = [...new Set(discovered)];
      if (importUrls.length === 0) {
        return reply.code(422).send({
          error: "no importable links found",
          scrapeErrors,
        });
      }
    }

    (async () => {
      const results: ImportResult[] = [];
      for (const url of importUrls) {
        try {
          const r = await importDirectLink(deps.db, url, deps.videoDir, metadata);
          results.push(r);
          app.log.info({ url, result: r }, "manual import");
        } catch (err) {
          app.log.error({ err, url }, "manual import threw");
        }
      }
      app.log.info({ queued: importUrls.length, results }, "manual import queue completed");
    })().catch((err) => app.log.error({ err }, "manual import failed"));

    return reply.code(202).send({
      status: "queued",
      queued: importUrls.length,
      scrapeErrors,
    });
  });

  app.get("/library", async () => {
    const series = deps.db.prepare("SELECT * FROM series ORDER BY added_at DESC").all();
    const recentlyAdded = deps.db
      .prepare(`
      SELECT v.*, c.title as channelTitle, fi.progress_seconds
      FROM videos v
      JOIN channels c ON c.id = v.channel_id
      JOIN feed_items fi ON fi.video_id = v.id
      ORDER BY v.discovered_at DESC
      LIMIT 20
    `)
      .all();
    const channels = deps.db.prepare("SELECT * FROM channels WHERE is_active = 1").all();

    return { series, recentlyAdded, channels };
  });

  app.get<{ Params: { id: string } }>("/series/:id", async (req, reply) => {
    const series = deps.db.prepare("SELECT * FROM series WHERE id = ?").get(req.params.id);
    if (!series) return reply.code(404).send({ error: "series not found" });

    const videos = deps.db
      .prepare(`
      SELECT v.*, fi.progress_seconds
      FROM videos v
      LEFT JOIN feed_items fi ON fi.video_id = v.id
      WHERE v.series_id = ?
      ORDER BY v.season ASC, v.episode ASC
    `)
      .all(req.params.id);

    return { ...series, videos };
  });
}
