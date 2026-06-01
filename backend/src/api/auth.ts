import type { FastifyInstance } from "fastify";

const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);

/**
 * Pure authorization check, extracted so it can be unit-tested without Fastify.
 *
 * Policy: when no token is configured the server is OPEN (single-user localhost
 * default — preserves current behavior). When a token IS configured, only safe
 * read methods stay open; every mutating request must carry
 * `Authorization: Bearer <token>`.
 */
export function isAuthorized(
  method: string,
  authHeader: string | undefined,
  token: string | null,
): boolean {
  if (!token) return true; // auth disabled
  if (SAFE_METHODS.has(method.toUpperCase())) return true;
  if (!authHeader) return false;
  const expected = `Bearer ${token}`;
  // Length-checked compare; tokens are local secrets, not attacker-grindable
  // online, so constant-time isn't required, but avoid a trivially-short match.
  return authHeader.length === expected.length && authHeader === expected;
}

export interface AuthDeps {
  token: string | null;
}

/**
 * Registers a global onRequest hook enforcing the policy above. A no-op when no
 * token is configured, so the default single-user deployment is unaffected.
 */
export function registerAuth(app: FastifyInstance, deps: AuthDeps): void {
  if (!deps.token) return;
  app.addHook("onRequest", async (req, reply) => {
    const header =
      typeof req.headers.authorization === "string" ? req.headers.authorization : undefined;
    if (!isAuthorized(req.method, header, deps.token)) {
      return reply.code(401).send({ error: "unauthorized" });
    }
  });
}
