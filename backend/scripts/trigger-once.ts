// One-off manual trigger: poll the first active channel, pick the first new
// video, run it through the full pipeline. Used for smoke-testing without
// waiting for the 15-min cron.
import "dotenv/config";
import { loadConfig } from "../src/config.js";
import { openDatabase } from "../src/db/connection.js";
import { downloadVideo } from "../src/download/worker.js";
import { fetchVideoMetadata } from "../src/ingest/metadata.js";
import { fetchTranscript } from "../src/ingest/transcript.js";
import { fetchChannelFeed } from "../src/monitor/rss-poller.js";
import { processNewVideo } from "../src/pipeline/orchestrator.js";
import { createScorer } from "../src/scorer/factory.js";
import { fetchSponsorSegments } from "../src/sponsorblock/client.js";

const cfg = loadConfig();
const db = openDatabase(cfg.dbPath);
const scorer = createScorer(cfg);

const channels = db
  .prepare("SELECT id, title FROM channels WHERE is_active = 1")
  .all() as { id: string; title: string }[];

if (channels.length === 0) {
  console.error("No active channels. POST one to /channels first.");
  process.exit(1);
}

for (const c of channels) {
  console.log(`\n=== Polling channel: ${c.title} (${c.id}) ===`);
  const entries = await fetchChannelFeed(c.id);
  console.log(`Got ${entries.length} entries. Processing first 2 unseen...`);

  let processed = 0;
  for (const e of entries) {
    if (processed >= 2) break;
    const already = db.prepare("SELECT 1 FROM videos WHERE id = ?").get(e.videoId);
    if (already) {
      console.log(`  [skip] ${e.videoId} already processed`);
      continue;
    }
    console.log(`  [process] ${e.videoId} — "${e.title}"`);
    try {
      await processNewVideo({
        db,
        videoId: e.videoId,
        channelId: c.id,
        fetchMetadata: fetchVideoMetadata,
        fetchTranscript,
        fetchSponsorSegments,
        scorer,
        download: (id) => downloadVideo({ videoId: id, outDir: cfg.videoDir }),
      });
      const row = db
        .prepare("SELECT decision, overall_score, category, reasoning FROM scores WHERE video_id = ?")
        .get(e.videoId);
      console.log(`    → score:`, row);
      processed++;
    } catch (err) {
      console.error(`    [error]`, err);
    }
  }
  db.prepare("UPDATE channels SET last_polled_at = ? WHERE id = ?").run(Date.now(), c.id);
}

console.log("\n=== Done ===");
process.exit(0);
