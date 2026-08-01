import type Database from "better-sqlite3";
import type { FastifyInstance } from "fastify";
import type { Config } from "../config.js";
import { buildBriefing } from "../news/briefing.js";
import { NEWS_TOPICS } from "../news/feeds.js";
import type { NewsLang } from "../news/google-news.js";
import type { summarizeItems } from "../news/summarize.js";

export interface NewsDeps {
  cfg?: Config;
  db?: Database.Database;
  fetchImpl?: typeof fetch;
  summarizer?: typeof summarizeItems;
  now?: () => number;
}

const DEFAULT_TOPICS: Record<NewsLang, string[]> = {
  de: ["politik", "technologie", "wissen"],
  en: ["world", "technology", "science"],
};

export async function registerNewsRoutes(app: FastifyInstance, deps: NewsDeps = {}): Promise<void> {
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
