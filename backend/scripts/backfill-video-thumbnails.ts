/**
 * Ergänzt fehlende Thumbnails für bereits importierte Videos und Serien:
 *
 *  1. Videos mit lokaler Datei, aber ohne Thumbnail bekommen einen
 *     Zufalls-Frame (Minute 3–7, gleiche Logik wie beim Import).
 *  2. Serien ohne Cover erben das erste lokale Folgen-Thumbnail (persistiert,
 *     nicht nur zur Laufzeit wie der Cover-Fallback in der API).
 *
 *   npx tsx scripts/backfill-video-thumbnails.ts
 */

import Database from "better-sqlite3";
import { homedir } from "node:os";
import { join } from "node:path";
import { extractRandomFrame } from "../src/import/frames.js";

const dataDir = process.env.HIKARI_DATA_DIR ?? join(homedir(), ".hikari");
const db = new Database(join(dataDir, "hikari.db"));
db.pragma("journal_mode = WAL");
db.pragma("busy_timeout = 5000");
const coverDir = join(dataDir, "covers");

const videos = db
  .prepare(
    `SELECT v.id, d.file_path AS filePath
       FROM videos v
       JOIN downloaded_videos d ON d.video_id = v.id
      -- Nur manuelle Importe: YouTube-Kanalvideos haben frische Remote-Thumbs.
      -- Nicht-lokale Thumbnails der Importe (Hoster-URLs verfallen,
      -- VOE-Domains rotieren) werden durch einen eigenen Frame ersetzt.
      WHERE v.channel_id = 'manual'
        AND (v.thumbnail_url IS NULL OR v.thumbnail_url = ''
          OR v.thumbnail_url NOT LIKE '/covers/%')`,
  )
  .all() as { id: string; filePath: string }[];

console.log(`${videos.length} Videos ohne Thumbnail\n`);

let ok = 0;
for (const v of videos) {
  const frame = await extractRandomFrame(v.id, v.filePath, coverDir);
  if (!frame) {
    console.log(`FEHLER ${v.id}`);
    continue;
  }
  db.prepare("UPDATE videos SET thumbnail_url = ? WHERE id = ?").run(`/covers/frames/${frame}`, v.id);
  ok++;
  console.log(`OK    ${v.id} → /covers/frames/${frame}`);
}

const seriesFilled = db
  .prepare(
    `UPDATE series SET thumbnail_url = (
       SELECT thumbnail_url FROM videos
        WHERE series_id = series.id AND thumbnail_url LIKE '/covers/%'
        ORDER BY season, episode LIMIT 1
     )
     WHERE thumbnail_url IS NULL OR thumbnail_url = ''`,
  )
  .run();

console.log(`\n${ok}/${videos.length} Video-Thumbnails, ${seriesFilled.changes} Serien-Cover nachgezogen.`);
db.close();
