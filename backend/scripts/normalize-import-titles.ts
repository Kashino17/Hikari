/**
 * Bringt bereits importierte Folgen auf die einheitliche Titelform, die der
 * Import seit v0.83 erzeugt: Serienname und SxxEyy/„Folge N"-Nummerierung
 * raus, übrig bleibt der echte Folgentitel oder „Folge N".
 *
 * Außerdem: Serien mit Untertitel-Slug (z. B. "american-horror-story-die-
 * dunkle-seite-in-dir") bekommen den Anzeigenamen der kürzeren Schreibweise,
 * wenn der Titel ihn als Präfix enthält und der Rest wie ein Untertitel
 * aussieht. Doppelte Folgen (Serie+Staffel+Folge+Synchro) werden nur gemeldet.
 *
 *   npx tsx scripts/normalize-import-titles.ts          # nur anzeigen
 *   npx tsx scripts/normalize-import-titles.ts --apply  # schreiben
 */
import Database from "better-sqlite3";
import { homedir } from "node:os";
import { join } from "node:path";
import { canonicalEpisodeTitle } from "../src/import/titles.js";

const apply = process.argv.includes("--apply");
const dataDir = process.env.HIKARI_DATA_DIR ?? join(homedir(), ".hikari");
const db = new Database(join(dataDir, "hikari.db"));
db.pragma("journal_mode = WAL");
db.pragma("busy_timeout = 5000");

interface Row {
  id: string;
  title: string;
  series_id: string | null;
  series_title: string | null;
  season: number | null;
  episode: number | null;
  is_movie: number;
  dub_language: string | null;
}

const rows = db
  .prepare(
    `SELECT v.id, v.title, v.series_id, s.title AS series_title, v.season, v.episode, v.is_movie, v.dub_language
       FROM videos v LEFT JOIN series s ON s.id = v.series_id
      WHERE v.channel_id = 'manual'
      ORDER BY v.series_id, v.season, v.episode`,
  )
  .all() as Row[];

// Serien-Anzeigename: Tragen alle Folgentitel einer Serie denselben kürzeren
// Anfang des Seriennamens (≥ 2 Wörter), war der Slug ein Untertitel-Slug —
// die Serie heißt so, wie die Folgen sie nennen.
const seriesRows = db.prepare("SELECT id, title FROM series").all() as { id: string; title: string }[];
const renameSeries = db.prepare("UPDATE series SET title = ? WHERE id = ?");
for (const s of seriesRows) {
  const titles = rows.filter((r) => r.series_id === s.id).map((r) => r.title);
  if (titles.length === 0) continue;
  const words = s.title.split(" ");
  let best: string | null = null;
  for (let n = words.length - 1; n >= 2; n--) {
    const prefix = words.slice(0, n).join(" ");
    if (titles.every((t) => t.toLowerCase().startsWith(prefix.toLowerCase()))) {
      best = prefix;
      break;
    }
  }
  if (best && best !== s.title) {
    console.log(`${apply ? "✎" : "·"} Serie ${s.id}: "${s.title}"  →  "${best}"`);
    if (apply) renameSeries.run(best, s.id);
    for (const r of rows) if (r.series_id === s.id) r.series_title = best;
  }
}

let changed = 0;
const update = db.prepare("UPDATE videos SET title = ? WHERE id = ?");
for (const r of rows) {
  const next = canonicalEpisodeTitle(r.title, {
    seriesTitle: r.series_title,
    season: r.season,
    episode: r.episode,
    isMovie: r.is_movie === 1,
  });
  if (!next || next === r.title) continue;
  changed += 1;
  console.log(`${apply ? "✎" : "·"} ${r.id}  "${r.title}"  →  "${next}"`);
  if (apply) update.run(next, r.id);
}
console.log(`${changed} Titel ${apply ? "geändert" : "würden geändert"} (${rows.length} manuelle Videos).`);

// Duplikate melden
const dups = db
  .prepare(
    `SELECT series_id, COALESCE(season,1) AS season, episode, COALESCE(dub_language,'') AS dub,
            GROUP_CONCAT(id) AS ids, COUNT(*) AS n
       FROM videos
      WHERE channel_id = 'manual' AND series_id IS NOT NULL AND episode IS NOT NULL
      GROUP BY series_id, COALESCE(season,1), episode, COALESCE(dub_language,'')
     HAVING COUNT(*) > 1`,
  )
  .all() as { series_id: string; season: number; episode: number; dub: string; ids: string; n: number }[];
if (dups.length > 0) {
  console.log("\nDoppelte Folgen (nicht automatisch gelöscht):");
  for (const d of dups) {
    console.log(`  ${d.series_id} S${d.season}E${d.episode} [${d.dub || "–"}]: ${d.ids}`);
  }
}
