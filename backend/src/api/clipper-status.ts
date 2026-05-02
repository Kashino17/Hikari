import type { FastifyInstance } from "fastify";
import type Database from "better-sqlite3";
import { enqueue, isWindowActive } from "../clipper/queue.js";

export function registerClipperStatusRoutes(
  app: FastifyInstance,
  db: Database.Database,
  schedule: { startHour: number; endHour: number },
): void {
  app.get("/clipper/status", async () => {
    const counts = db.prepare(`
      SELECT clip_status AS status, COUNT(*) AS c
        FROM videos
       WHERE clip_status IS NOT NULL
       GROUP BY clip_status
    `).all() as { status: string; c: number }[];
    const map = Object.fromEntries(counts.map((r) => [r.status, r.c]));

    const forceRow = db.prepare("SELECT force_until FROM clipper_runtime WHERE id = 1")
      .get() as { force_until: number } | undefined;
    const forceUntil = forceRow?.force_until ?? 0;
    const isForceActive = forceUntil > Date.now();

    return {
      pending:        map["pending"]        ?? 0,
      processing:    (map["analyzing"]      ?? 0) + (map["rendering"] ?? 0),
      failed:         map["failed"]         ?? 0,
      no_highlights: map["no_highlights"]   ?? 0,
      done:           map["done"]           ?? 0,
      isWindowActive: isWindowActive(new Date(), schedule.startHour, schedule.endHour, forceUntil),
      lastRanAt: lastRunTimestamp(db),
      forceUntil,
      isForceActive,
    };
  });

  app.post("/clipper/retry-failed", async () => {
    const failed = db.prepare("SELECT id FROM videos WHERE clip_status='failed'").all() as
      { id: string }[];
    db.transaction(() => {
      for (const v of failed) {
        db.prepare("UPDATE videos SET clip_status='pending' WHERE id=?").run(v.id);
        enqueue(db, v.id);
      }
    })();
    return { retriedCount: failed.length };
  });

  app.post("/clipper/force-window", async () => {
    const forceUntil = Date.now() + 60 * 60 * 1000;  // 1h from now
    db.prepare(`
      INSERT INTO clipper_runtime (id, force_until, updated_at) VALUES (1, ?, ?)
      ON CONFLICT(id) DO UPDATE SET force_until = excluded.force_until,
                                    updated_at  = excluded.updated_at
    `).run(forceUntil, Date.now());
    return { forceUntil };
  });
}

function lastRunTimestamp(db: Database.Database): number | null {
  const row = db.prepare("SELECT MAX(created_at) AS t FROM clips").get() as { t: number | null };
  return row.t;
}
