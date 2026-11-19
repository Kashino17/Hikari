import { createReadStream } from "node:fs";
import type Database from "better-sqlite3";
import type { FastifyInstance } from "fastify";
import {
  catalogHome,
  catalogLastRun,
  catalogSyncState,
  listCatalog,
  parseLang,
  runMangaCatalogSync,
} from "../manga/mangadex/catalog.js";
import type { MangadexClient } from "../manga/mangadex/client.js";
import {
  CatalogNotFoundError,
  ensureCatalogCover,
  openCatalogSeries,
} from "../manga/mangadex/open.js";

/**
 * Manga stöbern (MangaDex-Katalog): Regale, Liste mit Filtern und Suche, Titelbilder über
 * unseren Server (das Handy spricht nur mit dem Mac), Öffnen einer Reihe in den Leser.
 */

export interface MangaCatalogDeps {
  db: Database.Database;
  client: MangadexClient;
  coverDir: string;
  log?: (msg: string) => void;
}

type ListQuery = Partial<
  Record<"lang" | "genre" | "kind" | "status" | "sort" | "q" | "offset" | "limit", string>
>;

export function registerMangaCatalogRoutes(app: FastifyInstance, deps: MangaCatalogDeps): void {
  const { db } = deps;

  app.get<{ Querystring: { lang?: string } }>("/api/manga/catalog/home", async (req) => {
    const sync = catalogSyncState();
    return {
      ...catalogHome(db, parseLang(req.query.lang)),
      lastSync: catalogLastRun(db),
      syncing: sync.running,
    };
  });

  app.get<{ Querystring: ListQuery }>("/api/manga/catalog/list", async (req) => {
    const q = req.query;
    return listCatalog(db, {
      lang: parseLang(q.lang),
      ...(q.genre ? { genre: q.genre } : {}),
      ...(q.kind ? { kind: q.kind } : {}),
      ...(q.status ? { status: q.status } : {}),
      ...(q.q ? { q: q.q } : {}),
      ...(q.sort ? { sort: q.sort } : {}),
      offset: Number(q.offset) || 0,
      limit: Number(q.limit) || 30,
    });
  });

  // Von Hand anstoßen (sonst nachts um 4:50 und beim Start, wenn der letzte Lauf zu alt ist).
  app.post("/api/manga/catalog/sync", async (_req, reply) => {
    void runMangaCatalogSync({
      db,
      client: deps.client,
      ...(deps.log ? { log: deps.log } : {}),
    }).catch(() => {});
    return reply.code(202).send({ started: true });
  });

  app.get<{ Params: { id: string } }>("/api/manga/catalog/:id/cover", async (req, reply) => {
    const file = await ensureCatalogCover(deps, req.params.id).catch(() => null);
    if (!file) return reply.code(404).send({ error: "kein Titelbild" });
    reply.header("cache-control", "public, max-age=604800");
    return reply
      .type(file.endsWith(".png") ? "image/png" : "image/jpeg")
      .send(createReadStream(file));
  });

  app.post<{ Params: { id: string } }>("/api/manga/catalog/:id/open", async (req, reply) => {
    try {
      return await openCatalogSeries(
        {
          db,
          client: deps.client,
          coverDir: deps.coverDir,
          ...(deps.log ? { log: deps.log } : {}),
        },
        req.params.id,
      );
    } catch (err) {
      if (err instanceof CatalogNotFoundError) {
        return reply.code(404).send({ error: "unbekannter Titel" });
      }
      deps.log?.(
        `manga-katalog: Öffnen von ${req.params.id} gescheitert — ${(err as Error).message}`,
      );
      return reply.code(502).send({ error: "MangaDex antwortet gerade nicht" });
    }
  });
}
