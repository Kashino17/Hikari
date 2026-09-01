/**
 * Zieht Avatar/Banner/Handle/Subs für Kanäle nach, die vor der
 * Karten-Metadaten-Ära abonniert wurden (thumbnail_url leer).
 *
 *   npx tsx scripts/backfill-channel-meta.ts
 *
 * Seriell und gemächlich — jeder Kanal ist ein yt-dlp-Aufruf gegen YouTube.
 */

import Database from "better-sqlite3";
import { homedir } from "node:os";
import { join } from "node:path";
import { refreshChannelMetadata } from "../src/monitor/channel-resolver.js";

const dbPath = process.env.HIKARI_DB ?? join(homedir(), ".hikari", "hikari.db");
const db = new Database(dbPath);
db.pragma("journal_mode = WAL");
db.pragma("busy_timeout = 5000");

const rows = db
  .prepare(
    `SELECT id, title, url FROM channels
     WHERE is_active = 1 AND (thumbnail_url IS NULL OR thumbnail_url = '')
       AND url LIKE 'http%'`,
  )
  .all() as { id: string; title: string; url: string }[];

console.log(`${rows.length} Kanäle ohne Metadaten in ${dbPath}\n`);

let ok = 0;
for (const c of rows) {
  try {
    await refreshChannelMetadata(db, c.id, c.url);
    ok++;
    console.log(`OK    ${c.title}`);
  } catch (err) {
    console.log(`FEHLER ${c.title}: ${err instanceof Error ? err.message.slice(0, 120) : err}`);
  }
}
console.log(`\n${ok}/${rows.length} aktualisiert.`);
db.close();
