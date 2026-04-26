import type { FastifyInstance } from "fastify";
import type Database from "better-sqlite3";
import { createWriteStream } from "node:fs";
import { unlink } from "node:fs/promises";
import { join } from "node:path";
import { pipeline } from "node:stream/promises";
import { importDirectLink, fetchImportMetadata, ensureSeries, type ImportResult, type ManualMetadata } from "../import/manual-import.js";
import type { MetadataExtractor } from "../scorer/metadata-extractor.js";

export interface VideosDeps {
  db: Database.Database;
  videoDir: string;
  coverDir: string;
  extractor: MetadataExtractor | null;
}

interface SeriesRow {
  id: string;
  title: string;
  description: string | null;
  thumbnail_url: string | null;
  added_at: number;
}

/**
 * If series has no manual cover, fall back to the first episode's thumbnail.
 * Mutates by returning a new object — does not write to DB.
 */
function withCoverFallback(
  db: Database.Database,
  s: SeriesRow,
): SeriesRow {
  if (s.thumbnail_url) return s;
  const fallback = db
    .prepare(
      "SELECT thumbnail_url FROM videos WHERE series_id = ? AND thumbnail_url IS NOT NULL ORDER BY season ASC, episode ASC LIMIT 1",
    )
    .get(s.id) as { thumbnail_url: string } | undefined;
  return fallback ? { ...s, thumbnail_url: fallback.thumbnail_url } : s;
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
    const series = (deps.db.prepare("SELECT * FROM series ORDER BY added_at DESC").all() as SeriesRow[])
      .map((s) => withCoverFallback(deps.db, s));
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

  app.get("/languages", async () => {
    const dub = deps.db
      .prepare(
        "SELECT DISTINCT dub_language AS v FROM videos WHERE dub_language IS NOT NULL AND dub_language != '' ORDER BY v",
      )
      .all()
      .map((r) => (r as { v: string }).v);
    const sub = deps.db
      .prepare(
        "SELECT DISTINCT sub_language AS v FROM videos WHERE sub_language IS NOT NULL AND sub_language != '' ORDER BY v",
      )
      .all()
      .map((r) => (r as { v: string }).v);
    return { dub, sub };
  });

  app.get<{ Params: { id: string } }>("/series/:id", async (req, reply) => {
    const row = deps.db.prepare("SELECT * FROM series WHERE id = ?").get(req.params.id) as SeriesRow | undefined;
    if (!row) return reply.code(404).send({ error: "series not found" });

    const videos = deps.db.prepare(`
      SELECT v.*, fi.progress_seconds
      FROM videos v
      LEFT JOIN feed_items fi ON fi.video_id = v.id
      WHERE v.series_id = ?
      ORDER BY v.season ASC, v.episode ASC
    `).all(req.params.id);

    const series = withCoverFallback(deps.db, row);
    return { ...series, videos };
  });

  app.patch<{
    Params: { id: string };
    Body: { thumbnail_url?: string | null; description?: string | null };
  }>("/series/:id", async (req, reply) => {
    const row = deps.db.prepare("SELECT id FROM series WHERE id = ?").get(req.params.id);
    if (!row) return reply.code(404).send({ error: "series not found" });

    const fields: string[] = [];
    const values: unknown[] = [];
    if ("thumbnail_url" in req.body) {
      fields.push("thumbnail_url = ?");
      values.push(req.body.thumbnail_url || null);
    }
    if ("description" in req.body) {
      fields.push("description = ?");
      values.push(req.body.description || null);
    }
    if (fields.length === 0) return reply.code(400).send({ error: "no fields to update" });

    values.push(req.params.id);
    deps.db.prepare(`UPDATE series SET ${fields.join(", ")} WHERE id = ?`).run(...values);

    const updated = deps.db.prepare("SELECT * FROM series WHERE id = ?").get(req.params.id) as SeriesRow;
    return withCoverFallback(deps.db, updated);
  });

  app.post<{ Params: { id: string } }>("/series/:id/cover", async (req, reply) => {
    const row = deps.db.prepare("SELECT id FROM series WHERE id = ?").get(req.params.id);
    if (!row) return reply.code(404).send({ error: "series not found" });

    const file = await req.file();
    if (!file) return reply.code(400).send({ error: "no file uploaded" });

    const ext = (() => {
      const m = file.mimetype;
      if (m === "image/jpeg" || m === "image/jpg") return "jpg";
      if (m === "image/png") return "png";
      if (m === "image/webp") return "webp";
      return null;
    })();
    if (!ext) return reply.code(415).send({ error: `unsupported mime: ${file.mimetype}` });

    const filename = `${req.params.id}.${ext}`;
    const filepath = join(deps.coverDir, filename);

    // Clean up older cover files for this series with different extensions
    for (const oldExt of ["jpg", "png", "webp"]) {
      if (oldExt === ext) continue;
      await unlink(join(deps.coverDir, `${req.params.id}.${oldExt}`)).catch(() => {});
    }

    await pipeline(file.file, createWriteStream(filepath));

    const proto = req.headers["x-forwarded-proto"] ?? req.protocol;
    const host = req.headers["x-forwarded-host"] ?? req.headers.host;
    const url = `${proto}://${host}/covers/${filename}?t=${Date.now()}`;

    deps.db.prepare("UPDATE series SET thumbnail_url = ? WHERE id = ?").run(url, req.params.id);

    const updated = deps.db.prepare("SELECT * FROM series WHERE id = ?").get(req.params.id) as SeriesRow;
    return updated;
  });

  // ── Video edit endpoints ─────────────────────────────────────────────────
  app.get<{ Params: { id: string } }>("/videos/:id", async (req, reply) => {
    // Stream URLs (`/videos/<id>.mp4`) match this route before @fastify/static
    // can serve them — Fastify prefers explicit param routes over the static
    // wildcard. Hand .mp4 requests over to fastify-static via reply.sendFile so
    // Range / ETag / Content-Type still work for ExoPlayer.
    if (req.params.id.endsWith(".mp4")) {
      return reply.sendFile(req.params.id);
    }
    const row = deps.db.prepare(`
      SELECT v.*, s.title AS series_title
      FROM videos v
      LEFT JOIN series s ON s.id = v.series_id
      WHERE v.id = ?
    `).get(req.params.id);
    if (!row) return reply.code(404).send({ error: "video not found" });
    return row;
  });

  app.patch<{
    Params: { id: string };
    Body: {
      title?: string;
      description?: string | null;
      thumbnail_url?: string | null;
      season?: number | null;
      episode?: number | null;
      dub_language?: string | null;
      sub_language?: string | null;
      is_movie?: boolean;
      series_id?: string | null;
      series_title?: string | null;
    };
  }>("/videos/:id", async (req, reply) => {
    const existing = deps.db
      .prepare("SELECT id, series_id FROM videos WHERE id = ?")
      .get(req.params.id) as { id: string; series_id: string | null } | undefined;
    if (!existing) return reply.code(404).send({ error: "video not found" });

    const body = req.body;
    const fields: string[] = [];
    const values: unknown[] = [];

    const setField = (name: string, value: unknown) => {
      fields.push(`${name} = ?`);
      values.push(value);
    };

    if ("title" in body && typeof body.title === "string") setField("title", body.title);
    if ("description" in body) setField("description", body.description || null);
    if ("thumbnail_url" in body) setField("thumbnail_url", body.thumbnail_url || null);
    if ("season" in body) setField("season", body.season ?? null);
    if ("episode" in body) setField("episode", body.episode ?? null);
    if ("dub_language" in body) setField("dub_language", body.dub_language || null);
    if ("sub_language" in body) setField("sub_language", body.sub_language || null);

    // Movie/Series mutual exclusion: a video is EITHER a film OR an episode
    // of a series, never both. Whichever the user just set wins; the other
    // gets auto-cleared so /downloads bucketing stays clean.
    let newSeriesId: string | null | undefined;
    if ("series_id" in body) newSeriesId = body.series_id;
    else if ("series_title" in body) {
      newSeriesId = body.series_title ? ensureSeries(deps.db, body.series_title) : null;
    }
    let newIsMovie: number | undefined;
    if ("is_movie" in body) newIsMovie = body.is_movie ? 1 : 0;

    // If movie is being set true, force-clear series_id (any incoming or existing)
    if (newIsMovie === 1) {
      newSeriesId = null
    }
    // If a non-null series_id is being set, force-clear is_movie
    if (newSeriesId != null && newSeriesId !== undefined) {
      newIsMovie = 0;
    }

    if (newIsMovie !== undefined) setField("is_movie", newIsMovie);
    if (newSeriesId !== undefined) setField("series_id", newSeriesId);

    if (fields.length === 0) return reply.code(400).send({ error: "no fields to update" });

    values.push(req.params.id);
    deps.db.prepare(`UPDATE videos SET ${fields.join(", ")} WHERE id = ?`).run(...values);

    // Auto-delete old series if it was changed and is now empty
    if (
      newSeriesId !== undefined &&
      existing.series_id &&
      existing.series_id !== newSeriesId
    ) {
      const remaining = deps.db
        .prepare("SELECT COUNT(*) AS n FROM videos WHERE series_id = ?")
        .get(existing.series_id) as { n: number };
      if (remaining.n === 0) {
        deps.db.prepare("DELETE FROM series WHERE id = ?").run(existing.series_id);
      }
    }

    const updated = deps.db.prepare(`
      SELECT v.*, s.title AS series_title
      FROM videos v
      LEFT JOIN series s ON s.id = v.series_id
      WHERE v.id = ?
    `).get(req.params.id);
    return updated;
  });

  app.post<{ Params: { id: string } }>("/videos/:id/thumbnail", async (req, reply) => {
    const existing = deps.db.prepare("SELECT id FROM videos WHERE id = ?").get(req.params.id);
    if (!existing) return reply.code(404).send({ error: "video not found" });

    const file = await req.file();
    if (!file) return reply.code(400).send({ error: "no file uploaded" });

    const ext = (() => {
      const m = file.mimetype;
      if (m === "image/jpeg" || m === "image/jpg") return "jpg";
      if (m === "image/png") return "png";
      if (m === "image/webp") return "webp";
      return null;
    })();
    if (!ext) return reply.code(415).send({ error: `unsupported mime: ${file.mimetype}` });

    const filename = `vid_${req.params.id}.${ext}`;
    const filepath = join(deps.coverDir, filename);

    for (const oldExt of ["jpg", "png", "webp"]) {
      if (oldExt === ext) continue;
      await unlink(join(deps.coverDir, `vid_${req.params.id}.${oldExt}`)).catch(() => {});
    }

    await pipeline(file.file, createWriteStream(filepath));

    const proto = req.headers["x-forwarded-proto"] ?? req.protocol;
    const host = req.headers["x-forwarded-host"] ?? req.headers.host;
    const url = `${proto}://${host}/covers/${filename}?t=${Date.now()}`;

    deps.db.prepare("UPDATE videos SET thumbnail_url = ? WHERE id = ?").run(url, req.params.id);

    const updated = deps.db.prepare(`
      SELECT v.*, s.title AS series_title
      FROM videos v
      LEFT JOIN series s ON s.id = v.series_id
      WHERE v.id = ?
    `).get(req.params.id);
    return updated;
  });
}
