import type { FastifyInstance } from "fastify";
import type Database from "better-sqlite3";

export interface StatsDeps { db: Database.Database }

export async function registerStatsRoutes(app: FastifyInstance, deps: StatsDeps): Promise<void> {
  app.get("/stats/weekly", async () => {
    const weekAgo = Date.now() - 7 * 24 * 60 * 60 * 1000;

    const viewed = (deps.db.prepare(
      "SELECT COUNT(*) AS c FROM feed_items WHERE seen_at >= ?"
    ).get(weekAgo) as { c: number }).c;

    const decisions = deps.db.prepare(
      "SELECT s.decision, COUNT(*) AS c FROM scores s WHERE s.scored_at >= ? GROUP BY s.decision"
    ).all(weekAgo) as { decision: string; c: number }[];
    const approved = decisions.find(d => d.decision === "approved")?.c ?? 0;
    const rejected = decisions.find(d => d.decision === "rejected")?.c ?? 0;

    const byCategory = Object.fromEntries(
      (deps.db.prepare(
        "SELECT s.category, COUNT(*) AS c FROM scores s JOIN feed_items fi ON fi.video_id = s.video_id WHERE fi.seen_at >= ? GROUP BY s.category"
      ).all(weekAgo) as { category: string; c: number }[]).map(r => [r.category, r.c])
    );

    const avgRow = deps.db.prepare(
      "SELECT AVG(overall_score) AS a FROM scores WHERE scored_at >= ? AND decision = 'approved'"
    ).get(weekAgo) as { a: number | null };

    const diskRow = deps.db.prepare(
      "SELECT COALESCE(SUM(file_size_bytes), 0) AS b FROM downloaded_videos"
    ).get() as { b: number };

    return {
      windowDays: 7,
      viewed,
      approved,
      rejected,
      byCategory,
      avgScore: avgRow.a ? Math.round(avgRow.a * 10) / 10 : 0,
      diskUsedMb: Math.round(diskRow.b / 1024 / 1024),
    };
  });
}
