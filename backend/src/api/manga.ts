import type { FastifyInstance } from "fastify";
import type Database from "better-sqlite3";

export interface MangaDeps {
  db: Database.Database;
  mangaDir: string;
}

export function registerMangaRoutes(app: FastifyInstance, deps: MangaDeps): void {
  const { db } = deps;

  app.get("/api/manga/series", async () => {
    return db.prepare(
      `SELECT id, source, title, author, description, cover_path AS coverPath,
              total_chapters AS totalChapters, last_synced_at AS lastSyncedAt
       FROM manga_series
       ORDER BY title`,
    ).all();
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
         FROM manga_arcs WHERE series_id = ? ORDER BY arc_order`,
      )
      .all(id);
    const chapters = db
      .prepare(
        `SELECT c.id, c.number, c.title, c.arc_id AS arcId,
                c.page_count AS pageCount,
                CASE WHEN r.chapter_id IS NOT NULL THEN 1 ELSE 0 END AS isRead
         FROM manga_chapters c
         LEFT JOIN manga_chapter_read r ON r.chapter_id = c.id
         WHERE c.series_id = ?
         ORDER BY c.number`,
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
}
