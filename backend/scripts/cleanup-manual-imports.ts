/**
 * Einmaliges Aufräumen der manuellen Importe aus der Zeit vor der
 * Titelbereinigung (v0.78.x):
 *
 *  1. Titel: Site-Suffixe ("| SerienStream (S.to)") abschneiden, URL-artige
 *     "Titel" (Ad-Redirect-Seiten) durch einen lesbaren Platzhalter ersetzen,
 *     Serienpräfix abziehen — so wie es der aktuelle Import-Code tun würde.
 *  2. Fehlende Staffel/Folge aus dem bereinigten Titel nachziehen.
 *  3. Serien zusammenführen: "ted-" / "Ted " → "ted" (gleiche Normalisierung
 *     wie ensureSeries). Videos werden umgehängt, leere Serien gelöscht.
 *
 * Standard ist ein Dry-Run (zeigt nur, was passieren würde):
 *   npx tsx scripts/cleanup-manual-imports.ts
 * Schreiben mit:
 *   npx tsx scripts/cleanup-manual-imports.ts --apply
 */

import Database from "better-sqlite3";
import { homedir } from "node:os";
import { join } from "node:path";
import { cleanImportTitle, stripSeriesPrefix } from "../src/import/titles.js";
import { parseEpisodeInfo } from "../src/import/episode-parser.js";

const APPLY = process.argv.includes("--apply");
const dbPath = process.env.HIKARI_DB ?? join(homedir(), ".hikari", "hikari.db");
const db = new Database(dbPath);
db.pragma("journal_mode = WAL");
db.pragma("busy_timeout = 5000");

// Hosts, deren Seitennamen damals im Titel landeten. cleanImportTitle prüft
// den Suffix ohnehin gegen den Host — ein falscher Treffer schneidet nichts ab.
const KNOWN_HOSTS = ["serienstream.to", "s.to", "aniworld.to", "voe.sx"];

interface VideoRow {
  id: string;
  title: string;
  series_id: string | null;
  season: number | null;
  episode: number | null;
}
interface SeriesRow {
  id: string;
  title: string;
}

const normalizeSeriesId = (title: string): string =>
  title
    .trim()
    .normalize("NFKD")
    .replace(/\p{Mark}/gu, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");

const looksLikeUrl = (t: string): boolean =>
  /^(https?:\/\/|www\.)\S+$/i.test(t) ||
  (/^[\w-]+(\.[a-z0-9-]{2,})+\/\S*$/i.test(t) && !t.includes(" "));

function cleanTitle(title: string): string | null {
  if (looksLikeUrl(title)) return null;
  let best: string | null = title;
  for (const host of KNOWN_HOSTS) {
    const cleaned = cleanImportTitle(title, host);
    if (cleaned && cleaned.length < (best?.length ?? Infinity)) best = cleaned;
  }
  return best;
}

const videos = db
  .prepare("SELECT id, title, series_id, season, episode FROM videos WHERE channel_id = 'manual'")
  .all() as VideoRow[];
const seriesRows = db.prepare("SELECT id, title FROM series").all() as SeriesRow[];
const seriesTitleById = new Map(seriesRows.map((s) => [s.id, s.title]));

// 1. Serien-Merge planen: alte ID → kanonische ID
const seriesMap = new Map<string, string>();
for (const s of seriesRows) {
  const canonical = normalizeSeriesId(s.title);
  if (canonical && canonical !== s.id) seriesMap.set(s.id, canonical);
}

// 2. Video-Updates planen
interface PlannedUpdate {
  id: string;
  before: string;
  after: string;
  season: number | null;
  episode: number | null;
  seriesId: string | null;
}
const updates: PlannedUpdate[] = [];

for (const v of videos) {
  const seriesTitle = v.series_id ? seriesTitleById.get(v.series_id)?.trim() : undefined;
  const cleaned = cleanTitle(v.title);
  // URL-artige Titel: nichts mehr rekonstruierbar — lesbarer Platzhalter.
  let newTitle = cleaned ?? "Unbekannte Folge";
  newTitle = stripSeriesPrefix(newTitle, seriesTitle ?? null);
  // Reste wie "4 HD GER SUB" (Folgennummer + Qualitäts-/Sprach-Tags) werden
  // zu "Folge 4" — die Tags stehen bereits in eigenen Spalten.
  const episodeJunk = /^(\d{1,4})(?:\s+(?:HD|SD|\d{3,4}p|GER|DEUTSCH|GERMAN|SUB|DUB|HARDSUB|AAC|HDTV|WEB|DL))*$/i.exec(
    newTitle,
  );
  if (episodeJunk?.[1]) newTitle = `Folge ${Number(episodeJunk[1])}`;

  const info = parseEpisodeInfo("", newTitle);
  const season = v.season ?? info.season ?? null;
  const episode = v.episode ?? info.episode ?? null;
  const seriesId = v.series_id ? (seriesMap.get(v.series_id) ?? v.series_id) : null;

  if (
    newTitle !== v.title ||
    season !== v.season ||
    episode !== v.episode ||
    seriesId !== v.series_id
  ) {
    updates.push({ id: v.id, before: v.title, after: newTitle, season, episode, seriesId });
  }
}

// 3. Bericht
console.log(`DB: ${dbPath}${APPLY ? " (APPLY)" : " (Dry-Run — nichts wird geschrieben)"}\n`);
console.log(`Serien-Merges: ${seriesMap.size}`);
for (const [from, to] of seriesMap) console.log(`  ${from}  →  ${to}`);
console.log(`\nVideo-Updates: ${updates.length}`);
for (const u of updates) {
  console.log(`  ${u.id}`);
  console.log(`    Titel:  "${u.before}"  →  "${u.after}"`);
  console.log(`    S/E:    ${u.season ?? "-"}/${u.episode ?? "-"}   Serie: ${u.seriesId ?? "-"}`);
}

// 4. Verdacht auf Dubletten: gleiche Serie+Staffel+Folge bei mehreren Videos.
const dupes = new Map<string, string[]>();
for (const v of videos) {
  const planned = updates.find((u) => u.id === v.id);
  const seriesId = planned?.seriesId ?? v.series_id;
  const season = planned?.season ?? v.season;
  const episode = planned?.episode ?? v.episode;
  if (!seriesId || episode == null) continue;
  const key = `${seriesId}|${season ?? "-"}|${episode}`;
  dupes.set(key, [...(dupes.get(key) ?? []), v.id]);
}
const dupeList = [...dupes.entries()].filter(([, ids]) => ids.length > 1);
if (dupeList.length > 0) {
  console.log(`\nDubletten-Verdacht (NICHT gelöscht — bitte manuell prüfen):`);
  for (const [key, ids] of dupeList) console.log(`  ${key}: ${ids.join(", ")}`);
}

// 5. Anwenden
if (APPLY) {
  const run = db.transaction(() => {
    // Kanonische Serien anlegen (falls neu), Titel trimmen.
    for (const [from, to] of seriesMap) {
      const oldTitle = seriesTitleById.get(from) ?? to;
      db.prepare(
        "INSERT INTO series (id, title, added_at) VALUES (?, ?, ?) ON CONFLICT(id) DO NOTHING",
      ).run(to, oldTitle.trim(), Date.now());
    }
    for (const u of updates) {
      db.prepare("UPDATE videos SET title = ?, season = ?, episode = ?, series_id = ? WHERE id = ?")
        .run(u.after, u.season, u.episode, u.seriesId, u.id);
    }
    // Auch Videos ohne Titel-Update auf die kanonische Serie umhängen.
    for (const [from, to] of seriesMap) {
      db.prepare("UPDATE videos SET series_id = ? WHERE series_id = ?").run(to, from);
      db.prepare("DELETE FROM series WHERE id = ?").run(from);
    }
    // Serientitel trimmen.
    for (const s of seriesRows) {
      const trimmed = s.title.trim();
      if (trimmed !== s.title) {
        db.prepare("UPDATE series SET title = ? WHERE id = ?").run(trimmed, s.id);
      }
    }
  });
  run();
  console.log("\nAngewendet.");
} else {
  console.log("\nDry-Run. Mit --apply schreiben.");
}

db.close();
