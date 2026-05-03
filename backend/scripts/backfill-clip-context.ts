// One-shot backfill for v0.32.3 deploy.
// For every clip that has captions but no context, run the summarizer and
// store the resulting 1-3 sentence summary on `clips.context`.
import "dotenv/config";
import { loadConfig } from "../src/config.js";
import { openDatabase } from "../src/db/connection.js";
import { summarizeContext } from "../src/clipper/context-summarizer.js";
import type { Caption } from "../src/clipper/transcriber.js";

const cfg = loadConfig(process.env);
const db = openDatabase(cfg.dbPath);

const rows = db.prepare(`
  SELECT id, captions
  FROM clips
  WHERE captions IS NOT NULL AND (context IS NULL OR context = '')
  ORDER BY added_to_feed_at DESC
`).all() as { id: string; captions: string }[];

console.log(`Backfilling context for ${rows.length} clips`);

const update = db.prepare("UPDATE clips SET context = ? WHERE id = ?");

let ok = 0;
let skipped = 0;
let failed = 0;
for (const r of rows) {
  let captions: Caption[];
  try {
    captions = JSON.parse(r.captions) as Caption[];
  } catch {
    console.warn(`  ${r.id}: unparseable captions, skip`);
    skipped++;
    continue;
  }
  try {
    const ctx = await summarizeContext(captions, {
      baseUrl: cfg.clipper.baseUrl,
      model: cfg.clipper.model,
    });
    if (!ctx) {
      console.warn(`  ${r.id}: summarizer returned null, skip`);
      skipped++;
      continue;
    }
    update.run(ctx, r.id);
    ok++;
    console.log(`  ${r.id}: ${ctx.slice(0, 80)}${ctx.length > 80 ? "…" : ""}`);
  } catch (e) {
    failed++;
    console.error(`  ${r.id}: ${(e as Error).message}`);
  }
}

console.log(`Done. ok=${ok} skipped=${skipped} failed=${failed}`);
db.close();
