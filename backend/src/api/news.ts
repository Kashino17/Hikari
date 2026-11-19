import type Database from "better-sqlite3";
import type { FastifyInstance } from "fastify";
import type { Config } from "../config.js";
import { buildBriefing } from "../news/briefing.js";
import type { NewsDiscover } from "../news/discover.js";
import { NEWS_TOPICS } from "../news/feeds.js";
import type { NewsLang } from "../news/google-news.js";
import type { summarizeItems } from "../news/summarize.js";

export interface NewsDeps {
  cfg?: Config;
  db?: Database.Database;
  fetchImpl?: typeof fetch;
  summarizer?: typeof summarizeItems;
  now?: () => number;
  discover?: NewsDiscover;
}

const DEFAULT_TOPICS: Record<NewsLang, string[]> = {
  de: ["politik", "technologie", "wissen"],
  en: ["world", "technology", "science"],
};

export async function registerNewsRoutes(app: FastifyInstance, deps: NewsDeps = {}): Promise<void> {
  const discover = deps.discover;
  if (discover) {
    // Tagesbericht im Discover-Stil: eigene Stichwörter, nur frei lesbare Volltexte.
    app.get<{ Querystring: { force?: string } }>("/news/discover", async (req) => {
      return discover.discover(req.query.force === "1");
    });
    app.get<{ Params: { id: string } }>("/news/article/:id", async (req, reply) => {
      const a = await discover.cached(req.params.id);
      if (!a?.ok) return reply.code(404).send({ error: "not_found" });
      return a;
    });
    // GET mit ?set=[…] speichert — die Web-Oberfläche der App reicht nur GET durch.
    app.get<{ Querystring: { set?: string } }>("/news/keywords", async (req, reply) => {
      if (req.query.set === undefined) return discover.topics();
      let list: unknown;
      try {
        list = JSON.parse(req.query.set);
      } catch {
        return reply.code(400).send({ error: "invalid json" });
      }
      if (!Array.isArray(list)) return reply.code(400).send({ error: "expected array" });
      return discover.setTopics(list.map(String));
    });
    app.get<{ Querystring: { u?: string } }>("/news/img", async (req, reply) => {
      const u = req.query.u ?? "";
      let target: URL;
      try {
        target = new URL(u);
      } catch {
        return reply.code(400).send({ error: "bad url" });
      }
      if (target.protocol !== "http:" && target.protocol !== "https:")
        return reply.code(400).send({ error: "bad url" });
      const r = await (deps.fetchImpl ?? fetch)(target, {
        headers: {
          "user-agent":
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36",
          accept: "image/avif,image/webp,image/*,*/*;q=0.8",
          referer: `${target.origin}/`,
        },
        signal: AbortSignal.timeout(12000),
      }).catch(() => null);
      const type = r?.headers.get("content-type") ?? "";
      if (!r?.ok || !/^image\//.test(type))
        return reply.code(502).send({ error: "image unavailable" });
      const body = Buffer.from(await r.arrayBuffer());
      if (body.length > 15 * 1024 * 1024) return reply.code(413).send({ error: "too large" });
      return reply
        .header("content-type", type)
        .header("cache-control", "public, max-age=86400")
        .send(body);
    });
  }

  app.get("/news/topics", async () => {
    return NEWS_TOPICS.map(({ key, label, lang }) => ({ key, label, lang }));
  });

  app.get<{
    Querystring: { topics?: string; city?: string; lang?: string; force?: string; limit?: string };
  }>("/news/briefing", async (req, reply) => {
    const langParam = req.query.lang ?? "de";
    if (langParam !== "de" && langParam !== "en") {
      return reply.code(400).send({ error: `invalid lang "${langParam}" — use de or en` });
    }
    const lang: NewsLang = langParam;

    const topics = (req.query.topics ?? "")
      .split(",")
      .map((t) => t.trim())
      .filter((t) => t !== "");
    const effectiveTopics = topics.length > 0 ? topics : DEFAULT_TOPICS[lang];

    const city = req.query.city?.trim() || undefined;
    const limitRaw = Number(req.query.limit);
    const limit = Number.isFinite(limitRaw) && limitRaw > 0 ? Math.min(limitRaw, 50) : undefined;

    return buildBriefing(
      {
        topics: effectiveTopics,
        city,
        lang,
        limit,
        force: req.query.force === "1",
      },
      {
        db: deps.db,
        cfg: deps.cfg,
        fetchImpl: deps.fetchImpl,
        summarizer: deps.summarizer,
        now: deps.now,
      },
    );
  });
}
