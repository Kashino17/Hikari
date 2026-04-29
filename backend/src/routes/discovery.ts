import type { FastifyInstance } from "fastify";
import type Database from "better-sqlite3";
import {
  getDiscoveryCandidates,
  type UserPreferences,
} from "../discovery/discoveryEngine.js";
import { getSettings } from "../discovery/discovery-repo.js";

export interface DiscoveryDeps {
  db: Database.Database;
}

const MAX_LIMIT = 50;
const DEFAULT_LIMIT = 12;
// Long-form threshold for the anti-doom-scroll bias. Not yet user-tunable
// in discovery_settings — surfaced via query string for now.
const DEFAULT_LONGFORM_SECONDS = 600;

interface ProfileRow {
  category: string;
  cnt: number;
}

export async function registerDiscoveryRoutes(
  app: FastifyInstance,
  deps: DiscoveryDeps,
): Promise<void> {
  app.get<{
    Querystring: { limit?: string; longFormMinSeconds?: string };
  }>("/discovery", async (req, reply) => {
    const limit = clampInt(req.query.limit, DEFAULT_LIMIT, 1, MAX_LIMIT);
    const longFormMinSeconds = clampInt(
      req.query.longFormMinSeconds,
      DEFAULT_LONGFORM_SECONDS,
      0,
      24 * 3600,
    );

    // Pull DB-persisted discovery state — categoryWeights and qualityThreshold
    // come from discovery_settings (managed via /discovery/settings PUT).
    const settings = getSettings(deps.db);

    // Followed = currently active channel ids. They are both the user's
    // positive-signal anchor (for similarity) and the exclusion set for
    // candidates (we don't re-recommend something they already follow).
    const followedRows = deps.db
      .prepare("SELECT id FROM channels WHERE is_active = 1")
      .all() as { id: string }[];
    const followedChannelIds = followedRows.map((r) => r.id);

    // Similarity reference vector: what the user's library actually IS,
    // by category — derived from scored videos of followed channels. On
    // cold start (no scores yet) this is empty and the similarity axis
    // contributes 0 — the other axes carry the score.
    const profileRows = deps.db
      .prepare(
        `SELECT s.category AS category, COUNT(*) AS cnt
         FROM scores s
         JOIN videos v   ON v.id = s.video_id
         JOIN channels c ON c.id = v.channel_id
         WHERE c.is_active = 1
         GROUP BY s.category`,
      )
      .all() as ProfileRow[];
    const followedCategoryProfile: Record<string, number> = {};
    for (const r of profileRows) followedCategoryProfile[r.category] = r.cnt;

    const prefs: UserPreferences = {
      categoryWeights: settings.categoryWeights,
      followedCategoryProfile,
      followedChannelIds,
      qualityThreshold: settings.qualityThreshold,
      longFormMinSeconds,
    };

    try {
      const results = getDiscoveryCandidates(deps.db, prefs, limit);
      return reply.code(200).send({
        results,
        meta: {
          limit,
          followedCount: followedChannelIds.length,
          candidatePoolSize: results.length,
          qualityThreshold: settings.qualityThreshold,
          categoryWeights: settings.categoryWeights,
          longFormMinSeconds,
        },
      });
    } catch (err) {
      app.log.warn({ err }, "discovery scoring failed");
      return reply.code(500).send({ error: "discovery failed" });
    }
  });
}

function clampInt(
  raw: string | undefined,
  fallback: number,
  min: number,
  max: number,
): number {
  if (raw === undefined) return fallback;
  const n = Number(raw);
  if (!Number.isFinite(n)) return fallback;
  return Math.min(max, Math.max(min, Math.trunc(n)));
}
