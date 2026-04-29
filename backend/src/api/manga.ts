import { createReadStream } from "node:fs";
import { extname, resolve, sep } from "node:path";
import type Database from "better-sqlite3";
import type { FastifyInstance } from "fastify";
import {
  AdapterMismatchError,
  ArcNotFoundError,
  getArcManifest,
  runArcSync,
} from "../manga/arc-download.js";
import { adapters, getAdapter } from "../manga/sources/index.js";
import { runChapterSync, runFullSync } from "../manga/sync.js";

export interface MangaDeps {
  db: Database.Database;
  mangaDir: string;
}

export function registerMangaRoutes(app: FastifyInstance, deps: MangaDeps): void {
  const { db } = deps;
  let syncQueuedOrRunning = false;

  app.get("/api/manga/series", async () => {
    return db
      .prepare(
        `SELECT id, source, title, author, description, cover_path AS coverPath,
              total_chapters AS totalChapters, last_synced_at AS lastSyncedAt
       FROM manga_series
       ORDER BY title`,
      )
      .all();
  });

  app.get<{ Params: { id: string } }>("/api/manga/series/:id", async (req, reply) => {
    const { id } = req.params;
    const series = db.prepare("SELECT * FROM manga_series WHERE id = ?").get(id) as
      | Record<string, unknown>
      | undefined;
    if (!series) return reply.code(404).send({ error: "not found" });

    const arcs = db
      .prepare(
        `SELECT id, title, arc_order AS arcOrder,
                chapter_start AS chapterStart, chapter_end AS chapterEnd
         FROM manga_arcs WHERE series_id = ? ORDER BY arc_order DESC`,
      )
      .all(id);
    const chapters = db
      .prepare(
        `SELECT c.id, c.number, c.title, c.arc_id AS arcId,
                c.page_count AS pageCount,
                c.is_available AS isAvailable,
                CASE WHEN r.chapter_id IS NOT NULL THEN 1 ELSE 0 END AS isRead
         FROM manga_chapters c
         LEFT JOIN manga_chapter_read r ON r.chapter_id = c.id
         WHERE c.series_id = ?
         ORDER BY c.number DESC`,
      )
      .all(id);

    return { ...series, arcs, chapters };
  });

  app.get<{ Params: { id: string } }>("/api/manga/chapters/:id/pages", async (req, reply) => {
    const { id } = req.params;
    const rows = db
      .prepare(
        `SELECT id, page_number AS pageNumber, local_path AS localPath
         FROM manga_pages WHERE chapter_id = ? ORDER BY page_number`,
      )
      .all(id) as { id: string; pageNumber: number; localPath: string | null }[];
    if (rows.length === 0) {
      return reply.code(404).send({ error: "no pages — sync may not have run yet" });
    }
    return rows.map((r) => ({
      id: r.id,
      pageNumber: r.pageNumber,
      ready: r.localPath !== null,
    }));
  });

  app.get<{ Params: { pageId: string } }>("/api/manga/page/:pageId", async (req, reply) => {
    const row = db
      .prepare("SELECT local_path AS localPath FROM manga_pages WHERE id = ?")
      .get(req.params.pageId) as { localPath: string | null } | undefined;

    if (!row) return reply.code(404).send({ error: "page not found" });
    if (!row.localPath) {
      return reply.code(404).send({ status: "pending", error: "page not yet downloaded" });
    }

    const baseAbs = resolve(deps.mangaDir);
    const fullAbs = resolve(baseAbs, row.localPath);
    if (!fullAbs.startsWith(baseAbs + sep) && fullAbs !== baseAbs) {
      return reply.code(400).send({ error: "invalid path" });
    }

    const ext = extname(fullAbs).toLowerCase();
    const ct = ext === ".png" ? "image/png" : ext === ".webp" ? "image/webp" : "image/jpeg";

    reply.header("Content-Type", ct);
    reply.header("Cache-Control", "public, max-age=31536000, immutable");
    return reply.send(createReadStream(fullAbs));
  });

  app.post<{ Params: { seriesId: string } }>("/api/manga/library/:seriesId", async (req) => {
    db.prepare(
      "INSERT INTO manga_library (series_id, added_at) VALUES (?, ?) ON CONFLICT(series_id) DO NOTHING",
    ).run(req.params.seriesId, Date.now());
    return { ok: true };
  });

  app.delete<{ Params: { seriesId: string } }>("/api/manga/library/:seriesId", async (req) => {
    db.prepare("DELETE FROM manga_library WHERE series_id = ?").run(req.params.seriesId);
    return { ok: true };
  });

  app.put<{
    Params: { seriesId: string };
    Body: { chapterId: string; pageNumber: number };
  }>("/api/manga/progress/:seriesId", async (req, reply) => {
    const { chapterId, pageNumber } = req.body ?? {};
    if (!chapterId || typeof pageNumber !== "number") {
      return reply.code(400).send({ error: "chapterId + pageNumber required" });
    }
    db.prepare(
      `INSERT INTO manga_progress (series_id, chapter_id, page_number, updated_at)
       VALUES (?, ?, ?, ?)
       ON CONFLICT(series_id) DO UPDATE SET
         chapter_id = excluded.chapter_id,
         page_number = excluded.page_number,
         updated_at = excluded.updated_at`,
    ).run(req.params.seriesId, chapterId, pageNumber, Date.now());
    return { ok: true };
  });

  app.put<{ Params: { id: string } }>("/api/manga/chapters/:id/read", async (req) => {
    db.prepare(
      `INSERT INTO manga_chapter_read (chapter_id, read_at) VALUES (?, ?)
       ON CONFLICT(chapter_id) DO UPDATE SET read_at = excluded.read_at`,
    ).run(req.params.id, Date.now());
    return { ok: true };
  });

  app.get("/api/manga/continue", async () => {
    return db
      .prepare(
        `SELECT
         s.id AS seriesId,
         s.title,
         s.cover_path AS coverPath,
         p.chapter_id AS chapterId,
         p.page_number AS pageNumber,
         p.updated_at AS updatedAt
       FROM manga_progress p
       JOIN manga_library l ON l.series_id = p.series_id
       JOIN manga_series s ON s.id = p.series_id
       ORDER BY p.updated_at DESC`,
      )
      .all();
  });

  app.post<{ Body?: { sourceId?: string } }>("/api/manga/sync", async (req, reply) => {
    const running = db
      .prepare("SELECT id FROM manga_sync_jobs WHERE status IN ('queued','running') LIMIT 1")
      .get();
    if (running || syncQueuedOrRunning)
      return reply.code(409).send({ error: "sync already running" });

    const sourceId = req.body?.sourceId;
    const targets = sourceId
      ? [getAdapter(sourceId)].filter((x): x is NonNullable<typeof x> => Boolean(x))
      : adapters;
    if (sourceId && targets.length === 0) return reply.code(400).send({ error: "no such source" });
    if (targets.length === 0) return reply.code(400).send({ error: "no source registered" });

    syncQueuedOrRunning = true;
    // Fire-and-forget — return immediately with job started.
    queueMicrotask(async () => {
      try {
        for (const adapter of targets) {
          try {
            await runFullSync({ db, adapter, mangaDir: deps.mangaDir });
          } catch (err) {
            app.log.error({ err, adapter: adapter.id }, "manga sync failed");
          }
        }
      } finally {
        syncQueuedOrRunning = false;
      }
    });

    return reply.code(202).send({ status: "queued" });
  });

  app.get("/api/manga/sync/jobs", async () => {
    return db.prepare("SELECT * FROM manga_sync_jobs ORDER BY started_at DESC LIMIT 10").all();
  });

  app.get<{ Params: { id: string } }>("/api/manga/sync/jobs/:id", async (req, reply) => {
    const row = db.prepare("SELECT * FROM manga_sync_jobs WHERE id = ?").get(req.params.id);
    if (!row) return reply.code(404).send({ error: "not found" });
    return row;
  });

  app.get<{ Params: { arcId: string } }>(
    "/api/manga/arcs/:arcId/manifest",
    async (req, reply) => {
      const manifest = getArcManifest(db, req.params.arcId);
      if (!manifest) return reply.code(404).send({ error: "arc not found" });
      return manifest;
    },
  );

  app.post<{ Params: { arcId: string } }>(
    "/api/manga/arcs/:arcId/download",
    async (req, reply) => {
      const arc = db
        .prepare(
          `SELECT a.id AS arcId, s.source AS source
             FROM manga_arcs a
             JOIN manga_series s ON s.id = a.series_id
            WHERE a.id = ?`,
        )
        .get(req.params.arcId) as { arcId: string; source: string } | undefined;
      if (!arc) return reply.code(404).send({ error: "arc not found" });

      const adapter = getAdapter(arc.source);
      if (!adapter) {
        return reply.code(400).send({ error: `no adapter for source ${arc.source}` });
      }

      queueMicrotask(async () => {
        try {
          await runArcSync({
            db,
            adapter,
            arcId: arc.arcId,
            mangaDir: deps.mangaDir,
          });
        } catch (err) {
          if (err instanceof ArcNotFoundError || err instanceof AdapterMismatchError) {
            app.log.warn({ err, arcId: arc.arcId }, "arc sync rejected");
          } else {
            app.log.error({ err, arcId: arc.arcId }, "arc sync failed");
          }
        }
      });

      return reply.code(202).send({ status: "queued" });
    },
  );

  app.post<{ Params: { id: string } }>("/api/manga/chapters/:id/sync", async (req, reply) => {
    const chapter = db
      .prepare(
        `SELECT c.id, c.number, c.source_url AS sourceUrl, s.id AS seriesId, s.source, s.source_url AS seriesUrl
       FROM manga_chapters c
       JOIN manga_series s ON s.id = c.series_id
       WHERE c.id = ?`,
      )
      .get(req.params.id) as
      | {
          id: string;
          number: number;
          sourceUrl: string;
          seriesId: string;
          source: string;
          seriesUrl: string;
        }
      | undefined;
    if (!chapter) return reply.code(404).send({ error: "chapter not found" });

    const adapter = getAdapter(chapter.source);
    if (!adapter) return reply.code(400).send({ error: `no adapter for source ${chapter.source}` });

    const seriesSlug = chapter.seriesId.slice(chapter.source.length + 1); // "source:slug" → "slug"

    queueMicrotask(async () => {
      try {
        await runChapterSync({
          db,
          adapter,
          seriesSlug,
          chapterNumber: chapter.number,
          chapterUrl: chapter.sourceUrl,
          mangaDir: deps.mangaDir,
        });
      } catch (err) {
        app.log.error({ err, chapterId: chapter.id }, "chapter sync failed");
      }
    });

    return reply.code(202).send({ status: "queued" });
  });
}
