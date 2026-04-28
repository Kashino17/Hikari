import type Database from "better-sqlite3";
import type { FastifyInstance } from "fastify";
import {
  DiscoveryValidationError,
  getSettings,
  updateSettings,
} from "../discovery/discovery-repo.js";
import type { DiscoverySettingsUpdate } from "../discovery/types.js";

export interface DiscoveryDeps {
  db: Database.Database;
}

interface PutBody {
  discoveryRatio?: unknown;
  qualityThreshold?: unknown;
  categoryWeights?: unknown;
}

export async function registerDiscoveryRoutes(
  app: FastifyInstance,
  deps: DiscoveryDeps,
): Promise<void> {
  app.get("/discovery/settings", async () => getSettings(deps.db));

  app.put<{ Body: PutBody }>("/discovery/settings", async (req, reply) => {
    const body = req.body ?? {};
    const patch: DiscoverySettingsUpdate = {};

    if (body.discoveryRatio !== undefined) {
      patch.discoveryRatio = body.discoveryRatio as number;
    }
    if (body.qualityThreshold !== undefined) {
      patch.qualityThreshold = body.qualityThreshold as number;
    }
    if (body.categoryWeights !== undefined) {
      patch.categoryWeights = body.categoryWeights as Partial<
        DiscoverySettingsUpdate["categoryWeights"] & object
      >;
    }

    try {
      return updateSettings(deps.db, patch);
    } catch (err) {
      if (err instanceof DiscoveryValidationError) {
        return reply.code(400).send({ error: err.message });
      }
      throw err;
    }
  });
}
