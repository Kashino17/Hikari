import type { FastifyInstance } from "fastify";
import type Database from "better-sqlite3";
import {
  buildPrompt,
  validateFilter,
  type FilterConfig,
} from "../scorer/filter.js";
import {
  getFilterState,
  setFilterConfig,
  setPromptOverride,
} from "../scorer/filter-repo.js";

export interface FilterDeps {
  db: Database.Database;
}

interface PutBody {
  filter?: unknown;
  promptOverride?: string | null;
}

export async function registerFilterRoutes(
  app: FastifyInstance,
  deps: FilterDeps,
): Promise<void> {
  app.get("/filter", async () => {
    const state = getFilterState(deps.db);
    return {
      filter: state.filter,
      promptOverride: state.promptOverride,
      assembledPrompt: state.promptOverride ?? buildPrompt(state.filter),
      updatedAt: state.updatedAt,
    };
  });

  app.put<{ Body: PutBody }>("/filter", async (req, reply) => {
    const { filter, promptOverride } = req.body ?? {};

    let nextFilter: FilterConfig | undefined;
    if (filter !== undefined) {
      const validated = validateFilter(filter);
      if (!validated) {
        return reply.code(400).send({ error: "invalid filter shape" });
      }
      nextFilter = validated;
    }

    if (promptOverride !== undefined && promptOverride !== null) {
      if (typeof promptOverride !== "string") {
        return reply.code(400).send({ error: "promptOverride must be string or null" });
      }
      if (promptOverride.length > 50_000) {
        return reply.code(400).send({ error: "promptOverride too long" });
      }
    }

    deps.db.transaction(() => {
      if (nextFilter) setFilterConfig(deps.db, nextFilter);
      if (promptOverride !== undefined) setPromptOverride(deps.db, promptOverride);
    })();

    const state = getFilterState(deps.db);
    return {
      filter: state.filter,
      promptOverride: state.promptOverride,
      assembledPrompt: state.promptOverride ?? buildPrompt(state.filter),
      updatedAt: state.updatedAt,
    };
  });
}
