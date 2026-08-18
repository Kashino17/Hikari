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
  getResolvedChannelFilter,
  setChannelFilterConfig,
  setChannelPromptOverride,
  clearChannelFilter,
} from "../scorer/filter-repo.js";

export interface FilterDeps {
  db: Database.Database;
  /** Wird nach jeder Filter-Änderung gerufen: Feed neu mischen, Suche anstoßen. */
  onFilterChanged?: (() => void) | undefined;
}

interface PutBody {
  filter?: unknown;
  promptOverride?: string | null;
}

function channelExists(db: Database.Database, id: string): boolean {
  return !!db.prepare("SELECT 1 FROM channels WHERE id = ?").get(id);
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
    deps.onFilterChanged?.();
    return {
      filter: state.filter,
      promptOverride: state.promptOverride,
      assembledPrompt: state.promptOverride ?? buildPrompt(state.filter),
      updatedAt: state.updatedAt,
    };
  });

  // ── Per-channel filter ──────────────────────────────────────────────────
  // Same response shape as /filter, plus `inherited` (true = falling back to
  // the global filter, false = this channel has its own override).

  app.get<{ Params: { id: string } }>("/channels/:id/filter", async (req, reply) => {
    if (!channelExists(deps.db, req.params.id)) {
      return reply.code(404).send({ error: "channel not found" });
    }
    const state = getResolvedChannelFilter(deps.db, req.params.id);
    return {
      filter: state.filter,
      promptOverride: state.promptOverride,
      assembledPrompt: state.promptOverride ?? buildPrompt(state.filter),
      updatedAt: state.updatedAt,
      inherited: state.inherited,
    };
  });

  app.put<{ Params: { id: string }; Body: PutBody }>(
    "/channels/:id/filter",
    async (req, reply) => {
      if (!channelExists(deps.db, req.params.id)) {
        return reply.code(404).send({ error: "channel not found" });
      }
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
        if (nextFilter) setChannelFilterConfig(deps.db, req.params.id, nextFilter);
        if (promptOverride !== undefined) {
          setChannelPromptOverride(deps.db, req.params.id, promptOverride);
        }
      })();

      const state = getResolvedChannelFilter(deps.db, req.params.id);
      return {
        filter: state.filter,
        promptOverride: state.promptOverride,
        assembledPrompt: state.promptOverride ?? buildPrompt(state.filter),
        updatedAt: state.updatedAt,
        inherited: state.inherited,
      };
    },
  );

  // Revert a channel to inheriting the global filter.
  app.delete<{ Params: { id: string } }>("/channels/:id/filter", async (req, reply) => {
    if (!channelExists(deps.db, req.params.id)) {
      return reply.code(404).send({ error: "channel not found" });
    }
    clearChannelFilter(deps.db, req.params.id);
    return reply.code(204).send();
  });
}
