import type { FastifyInstance } from "fastify";

// @fastify/static with { prefix: "/videos/", root: VIDEO_DIR } handles Range,
// Content-Type, and ETag automatically. This function is a no-op placeholder
// for future logic (access logs, last_served_at updates).
export async function registerVideosRoutes(_app: FastifyInstance): Promise<void> {
  // intentionally empty — static serving is registered in index.ts
}
