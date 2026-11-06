import { unlink } from "node:fs/promises";
import type Database from "better-sqlite3";

/**
 * Alle Tabellen, die per Fremdschlüssel auf videos(id) zeigen. Mit
 * `foreign_keys = ON` scheitert das Löschen der videos-Zeile, solange auch nur
 * eine davon noch einen Eintrag hat — „Später ansehen" oder ein alter
 * Tagesmix-Slot reichten, um ein Video unlöschbar zu machen.
 */
const DEPENDENT_TABLES = [
  "sponsor_segments",
  "feed_items",
  "downloaded_videos",
  "scores",
  "daily_mix_items",
  "watch_later",
  "watch_history",
  "video_progress",
] as const;

function existingTables(db: Database.Database): Set<string> {
  const rows = db.prepare("SELECT name FROM sqlite_master WHERE type = 'table'").all() as {
    name: string;
  }[];
  return new Set(rows.map((r) => r.name));
}

/**
 * Entfernt Videos samt allen abhängigen Zeilen in EINER Transaktion und gibt
 * die Dateipfade zurück, die danach von der Platte gelöscht werden müssen.
 * Die Dateien erst nach dem Commit anfassen: Scheitert die Transaktion, bleibt
 * alles abspielbar.
 */
export function removeVideoRows(db: Database.Database, videoIds: string[]): string[] {
  if (videoIds.length === 0) return [];
  const tables = DEPENDENT_TABLES.filter((t) => existingTables(db).has(t));
  const filePath = db.prepare("SELECT file_path FROM downloaded_videos WHERE video_id = ?");
  const deletes = tables.map((t) => db.prepare(`DELETE FROM ${t} WHERE video_id = ?`));
  const deleteVideo = db.prepare("DELETE FROM videos WHERE id = ?");

  const files: string[] = [];
  db.transaction(() => {
    for (const id of videoIds) {
      const row = filePath.get(id) as { file_path: string } | undefined;
      if (row?.file_path) files.push(row.file_path);
      for (const d of deletes) d.run(id);
      deleteVideo.run(id);
    }
  })();
  return files;
}

export async function unlinkAll(paths: string[]): Promise<void> {
  // Eine schon fehlende Datei ist kein Fehler — das Ziel ist erreicht.
  await Promise.all(paths.map((p) => unlink(p).catch(() => undefined)));
}

/** Löscht ein Video vollständig (DB + Datei). false, wenn es nicht existiert. */
export async function deleteVideoCompletely(
  db: Database.Database,
  videoId: string,
): Promise<boolean> {
  const exists = db.prepare("SELECT 1 FROM videos WHERE id = ?").get(videoId);
  if (!exists) return false;
  await unlinkAll(removeVideoRows(db, [videoId]));
  return true;
}

/**
 * Löscht eine Serie mit allen Folgen (DB + Dateien) und die Serienzeile.
 * Rückgabe: Anzahl gelöschter Folgen, oder null, wenn es die Serie nicht gibt.
 */
export async function deleteSeriesCompletely(
  db: Database.Database,
  seriesId: string,
): Promise<number | null> {
  const exists = db.prepare("SELECT 1 FROM series WHERE id = ?").get(seriesId);
  if (!exists) return null;
  const ids = (
    db.prepare("SELECT id FROM videos WHERE series_id = ?").all(seriesId) as { id: string }[]
  ).map((r) => r.id);
  const files = removeVideoRows(db, ids);
  db.prepare("DELETE FROM series WHERE id = ?").run(seriesId);
  await unlinkAll(files);
  return ids.length;
}
