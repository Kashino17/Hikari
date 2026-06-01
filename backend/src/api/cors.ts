import type { FastifyInstance } from "fastify";

/**
 * Decides the Access-Control-Allow-Origin value for a request, or null to send
 * no CORS headers. Pure, so it can be unit-tested without Fastify.
 *
 * Policy: only origins on the explicit allowlist are reflected back. A request
 * with no Origin header (curl, the native Android client) needs no CORS and
 * gets null. "*" in the allowlist means allow any origin (echoes it back so
 * credentialed requests still work) — opt-in, never the default.
 */
export function resolveCorsOrigin(
  requestOrigin: string | undefined,
  allowlist: string[],
): string | null {
  if (allowlist.length === 0) return null; // CORS disabled
  if (!requestOrigin) return null; // non-browser request, no CORS needed
  if (allowlist.includes("*")) return requestOrigin;
  return allowlist.includes(requestOrigin) ? requestOrigin : null;
}

export interface CorsDeps {
  origins: string[];
}

/**
 * Registers a CORS hook over JSON routes using an explicit origin allowlist.
 * A no-op when the allowlist is empty, so the localhost / native-client default
 * is unaffected. Static media keep their own wildcard headers (set in index.ts)
 * — this covers the API surface a browser client would call.
 *
 * Handles preflight OPTIONS by short-circuiting with 204 + the CORS headers.
 */
export function registerCors(app: FastifyInstance, deps: CorsDeps): void {
  if (deps.origins.length === 0) return;
  app.addHook("onRequest", async (req, reply) => {
    const origin = typeof req.headers.origin === "string" ? req.headers.origin : undefined;
    const allowed = resolveCorsOrigin(origin, deps.origins);
    if (allowed) {
      reply.header("Access-Control-Allow-Origin", allowed);
      reply.header("Vary", "Origin");
      reply.header("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
      reply.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }
    if (req.method === "OPTIONS") {
      // Preflight: answer here so it doesn't fall through to a 404 route.
      return reply.code(204).send();
    }
  });
}
