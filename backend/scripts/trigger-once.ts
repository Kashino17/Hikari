// One-off manual trigger: poll every subscribed channel, take the first two
// new videos each and run them through the full pipeline. Used for
// smoke-testing without waiting for the 15-min cron.
import "dotenv/config";
import { loadConfig } from "../src/config.js";
import { openDatabase } from "../src/db/connection.js";
import { SUBSCRIBED_CHANNEL_IDS_SQL } from "../src/feed/subscriptions.js";
import { fetchVideoMetadata } from "../src/ingest/metadata.js";
import { fetchChannelFeed } from "../src/monitor/rss-poller.js";
import { processNewVideo } from "../src/pipeline/orchestrator.js";
import { fetchSponsorSegments } from "../src/sponsorblock/client.js";

const cfg = loadConfig();
const db = openDatabase(cfg.dbPath);

const channels = db
  .prepare(`SELECT id, title FROM channels WHERE id IN (${SUBSCRIBED_CHANNEL_IDS_SQL})`)
  .all() as { id: string; title: string }[];

if (channels.length === 0) {
  console.error("No subscribed channels. POST one to /channels first.");
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
      const result = await processNewVideo({
        db,
        videoId: e.videoId,
        channelId: c.id,
        fetchMetadata: fetchVideoMetadata,
        fetchSponsorSegments,
      });
      console.log(`    → ${result}`);
      processed++;
    } catch (err) {
      console.error(`    [error]`, err);
    }
  }
  db.prepare("UPDATE channels SET last_polled_at = ? WHERE id = ?").run(Date.now(), c.id);
}

console.log("\n=== Done ===");
process.exit(0);
