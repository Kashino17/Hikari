/**
 * Ergänzt und repariert Thumbnails für bereits importierte Videos und Serien:
 *
 *  1. Videos mit lokaler Datei, deren Thumbnail fehlt, nicht lokal ist oder
 *     auf eine inzwischen gelöschte Datei zeigt, bekommen einen neuen
 *     Zufalls-Frame (Minute 3–7, gleiche Logik wie beim Import).
 *  2. Absolute Backend-URLs (alte IP/Host eingebettet) werden auf den
 *     relativen /covers/-Pfad reduziert.
 *  3. Serien ohne (gültiges) Cover erben das erste lokale Folgen-Thumbnail.
 *
 *   npx tsx scripts/backfill-video-thumbnails.ts
 */

import Database from "better-sqlite3";
import { existsSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";
import { extractRandomFrame } from "../src/import/frames.js";

const dataDir = process.env.HIKARI_DATA_DIR ?? join(homedir(), ".hikari");
const db = new Database(join(dataDir, "hikari.db"));
db.pragma("journal_mode = WAL");
db.pragma("busy_timeout = 5000");
const coverDir = join(dataDir, "covers");

/** Absolute Backend-URL ("http://ip:3939/covers/...") → relativer Pfad. */
function toRelativeCoverPath(url: string): string {
  const idx = url.indexOf("/covers/");
  return idx >= 0 ? url.slice(idx) : url;
}

/**
 * true, wenn das Thumbnail fehlt, nicht lokal ausgeliefert wird oder auf
 * eine Datei zeigt, die es nicht mehr gibt (z. B. früher weggeräumte
 * Frame-Caches).
 */
function thumbnailBroken(url: string | null): boolean {
  if (!url) return true;
  if (!url.startsWith("/covers/")) return true;
  return !existsSync(join(dataDir, url));
}

// Schritt 2: absolute Cover-URLs normalisieren (alte Server-IPs hängen sonst
// im Client fest).
for (const table of ["videos", "series"] as const) {
  const rows = db
    .prepare(`SELECT id, thumbnail_url FROM ${table} WHERE thumbnail_url LIKE 'http%/covers/%'`)
    .all() as { id: string; thumbnail_url: string }[];
  for (const r of rows) {
    db.prepare(`UPDATE ${table} SET thumbnail_url = ? WHERE id = ?`).run(
      toRelativeCoverPath(r.thumbnail_url).split("?")[0],
      r.id,
    );
  }
  if (rows.length > 0) console.log(`${rows.length} absolute ${table}-URLs relativiert.`);
}

// Schritt 1: Videos reparieren.
// Nur manuelle Importe: YouTube-Kanalvideos haben frische Remote-Thumbs.
const videos = (
  db
    .prepare(
      `SELECT v.id, v.thumbnail_url, d.file_path AS filePath
         FROM videos v
         JOIN downloaded_videos d ON d.video_id = v.id
        WHERE v.channel_id = 'manual'`,
    )
    .all() as { id: string; thumbnail_url: string | null; filePath: string }[]
).filter((v) => thumbnailBroken(v.thumbnail_url));

console.log(`${videos.length} Videos mit kaputtem/fehlendem Thumbnail\n`);

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

// Schritt 3: Serien-Cover reparieren — erste Folge mit existierendem
// lokalem Thumbnail gewinnt.
const brokenSeries = (
  db.prepare("SELECT id, title, thumbnail_url FROM series").all() as {
    id: string;
    title: string;
    thumbnail_url: string | null;
  }[]
).filter((s) => thumbnailBroken(s.thumbnail_url));

let seriesFixed = 0;
for (const s of brokenSeries) {
  const candidates = db
    .prepare(
      `SELECT thumbnail_url FROM videos
        WHERE series_id = ? AND thumbnail_url LIKE '/covers/%'
        ORDER BY season, episode`,
    )
    .all(s.id) as { thumbnail_url: string }[];
  const cover = candidates.find((c) => existsSync(join(dataDir, c.thumbnail_url)));
  if (!cover) {
    console.log(`OHNE  ${s.title}: kein gültiges Folgen-Thumbnail gefunden`);
    continue;
  }
  db.prepare("UPDATE series SET thumbnail_url = ? WHERE id = ?").run(cover.thumbnail_url, s.id);
  seriesFixed++;
  console.log(`COVER ${s.title} → ${cover.thumbnail_url}`);
}

console.log(`\n${ok}/${videos.length} Video-Thumbnails, ${seriesFixed}/${brokenSeries.length} Serien-Cover repariert.`);
db.close();
