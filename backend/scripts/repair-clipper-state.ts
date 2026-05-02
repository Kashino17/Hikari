// Repair script for v0.30.3 deploy.
// 1. Reset stuck 'analyzing' jobs to 'pending' so they retry with new code.
// 2. Reset 'failed' jobs to 'pending' (re-enqueue) since the failure cause was a bug we just fixed.
// 3. Backfill is_pre_clipper=1 on feed_items rows that slipped through (bridge state between v0.29.1 and v0.30.0 migration).
import "dotenv/config";
import { loadConfig } from "../src/config.js";
import { openDatabase } from "../src/db/connection.js";
import { applyMigrations } from "../src/db/migrations.js";
import { enqueue } from "../src/clipper/queue.js";

const cfg = loadConfig(process.env);
const db = openDatabase(cfg.dbPath);
applyMigrations(db);

const stuck = db.prepare(`
  UPDATE videos SET clip_status = 'pending'
  WHERE clip_status IN ('analyzing', 'rendering', 'failed')
  RETURNING id
`).all() as { id: string }[];

console.log(`Reset ${stuck.length} videos to pending. Re-enqueueing…`);
db.prepare(`UPDATE clipper_queue SET locked_at = NULL, locked_step = NULL, last_error = NULL
            WHERE video_id IN (${stuck.map(() => "?").join(",") || "''"})`)
  .run(...stuck.map(s => s.id));
for (const v of stuck) enqueue(db, v.id);

const orphans = db.prepare(`
  UPDATE feed_items SET is_pre_clipper = 1 WHERE is_pre_clipper = 0
  RETURNING video_id
`).all() as { video_id: string }[];
console.log(`Backfilled ${orphans.length} feed_items to is_pre_clipper=1`);

console.log("Repair complete.");
