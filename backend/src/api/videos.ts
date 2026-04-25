import type { FastifyInstance } from "fastify";
import type Database from "better-sqlite3";
import { importDirectLink, type ImportResult } from "../import/manual-import.js";

export interface VideosDeps {
  db: Database.Database;
  videoDir: string;
}

interface ImportBody {
  urls?: string[];
  url?: string;
}

// @fastify/static with { prefix: "/videos/", root: VIDEO_DIR } handles Range,
// Content-Type, and ETag automatically. Direct-link import lives here too.
export async function registerVideosRoutes(
  app: FastifyInstance,
  deps: VideosDeps,
): Promise<void> {
  app.post<{ Body: ImportBody }>("/videos/import", async (req, reply) => {
    const body = req.body ?? {};
    const urls = Array.isArray(body.urls) ? body.urls : body.url ? [body.url] : [];
    const cleaned = urls
      .map((u) => u.trim())
      .filter((u) => u.length > 0);
    if (cleaned.length === 0) {
      return reply.code(400).send({ error: "no urls" });
    }
    if (cleaned.length > 50) {
      return reply.code(400).send({ error: "max 50 urls per request" });
    }

    // Respond immediately with "queued" — actual import happens in the
    // background. Each URL is sequential to avoid hammering yt-dlp +
    // saturating disk bandwidth on big downloads.
    const queued = cleaned.length;

    (async () => {
      const results: ImportResult[] = [];
      for (const url of cleaned) {
        try {
          const r = await importDirectLink(deps.db, url, deps.videoDir);
          results.push(r);
          app.log.info({ url, result: r }, "manual import");
        } catch (err) {
          app.log.error({ err, url }, "manual import threw");
          results.push({ url, status: "failed", error: String(err) });
        }
      }
      const ok = results.filter((r) => r.status === "ok").length;
      const dup = results.filter((r) => r.status === "duplicate").length;
      const fail = results.filter((r) => r.status === "failed").length;
      app.log.info({ queued, ok, dup, fail }, "manual import batch done");
    })().catch((err) => app.log.error({ err }, "manual import batch failed"));

    return reply.code(202).send({ queued });
  });
}
