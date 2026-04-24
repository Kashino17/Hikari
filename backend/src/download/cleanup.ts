import { existsSync, unlinkSync } from "node:fs";
import type Database from "better-sqlite3";

interface CleanupCandidate {
  video_id: string;
  file_path: string;
  file_size_bytes: number;
}

export interface CleanupResult {
  deletedCount: number;
  deletedVideoIds: string[];
  freedBytes: number;
  finalBytes: number;
}

export function runCleanup(opts: {
  db: Database.Database;
  limitBytes: number;
}): CleanupResult {
  const totalRow = opts.db
    .prepare("SELECT COALESCE(SUM(file_size_bytes), 0) as total FROM downloaded_videos")
    .get() as { total: number };

  let currentBytes = totalRow.total;
  const deleted: string[] = [];
  let freed = 0;

  if (currentBytes <= opts.limitBytes) {
    return { deletedCount: 0, deletedVideoIds: [], freedBytes: 0, finalBytes: currentBytes };
  }

  const candidates = opts.db
    .prepare(
      `SELECT dv.video_id, dv.file_path, dv.file_size_bytes
       FROM downloaded_videos dv
       JOIN feed_items fi ON fi.video_id = dv.video_id
       WHERE fi.saved = 0
       ORDER BY COALESCE(dv.last_served_at, dv.downloaded_at) ASC`,
    )
    .all() as CleanupCandidate[];

  const removeRow = opts.db.prepare("DELETE FROM downloaded_videos WHERE video_id = ?");

  for (const c of candidates) {
    if (currentBytes <= opts.limitBytes) break;
    if (existsSync(c.file_path)) {
      unlinkSync(c.file_path);
    }
    removeRow.run(c.video_id);
    deleted.push(c.video_id);
    currentBytes -= c.file_size_bytes;
    freed += c.file_size_bytes;
  }

  return {
    deletedCount: deleted.length,
    deletedVideoIds: deleted,
    freedBytes: freed,
    finalBytes: currentBytes,
  };
}
