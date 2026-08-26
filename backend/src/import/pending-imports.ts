import type Database from "better-sqlite3";
import { type DownloadProgress, progressFraction } from "../download/progress.js";

/**
 * Ein Import, der gerade läuft.
 *
 * Diese Zeilen leben bewusst NICHT in `videos`: Genau das war der alte Fehler,
 * der Folgen in der Bibliothek zeigte, deren Datei nie ankam. Erst wenn die
 * Datei vollständig auf der Platte liegt, wandert der Eintrag hinüber.
 *
 * Bis dahin sind die Metadatenfelder frei editierbar — beim Abschluss werden
 * sie von hier übernommen, nicht die ursprünglich übergebenen Werte. Der
 * Nutzer kann Titel und Sprache also schon eintragen, während noch geladen wird.
 */
export interface PendingImport {
  id: string;
  pageUrl: string;
  mediaUrl: string | null;
  title: string | null;
  seriesId: string | null;
  seriesTitle: string | null;
  season: number | null;
  episode: number | null;
  dubLanguage: string | null;
  subLanguage: string | null;
  isMovie: boolean;
  thumbnailUrl: string | null;
  status: "queued" | "downloading" | "failed";
  downloadedBytes: number;
  totalBytes: number | null;
  speedBps: number | null;
  etaSeconds: number | null;
  fragmentIndex: number | null;
  fragmentCount: number | null;
  /** 0…1, oder null solange sich der Anteil nicht bestimmen lässt. */
  progress: number | null;
  error: string | null;
  startedAt: number;
  updatedAt: number;
}

export interface PendingMetadata {
  title?: string | null;
  seriesId?: string | null;
  seriesTitle?: string | null;
  season?: number | null;
  episode?: number | null;
  dubLanguage?: string | null;
  subLanguage?: string | null;
  isMovie?: boolean | null;
}

interface PendingRow {
  id: string;
  page_url: string;
  media_url: string | null;
  title: string | null;
  series_id: string | null;
  series_title: string | null;
  season: number | null;
  episode: number | null;
  dub_language: string | null;
  sub_language: string | null;
  is_movie: number | null;
  thumbnail_url: string | null;
  status: string;
  downloaded_bytes: number | null;
  total_bytes: number | null;
  speed_bps: number | null;
  eta_seconds: number | null;
  fragment_index: number | null;
  fragment_count: number | null;
  error: string | null;
  started_at: number;
  updated_at: number;
}

function toPending(r: PendingRow): PendingImport {
  const p: DownloadProgress = {
    downloadedBytes: r.downloaded_bytes ?? 0,
    totalBytes: r.total_bytes,
    speedBps: r.speed_bps,
    etaSeconds: r.eta_seconds,
    fragmentIndex: r.fragment_index,
    fragmentCount: r.fragment_count,
  };
  return {
    id: r.id,
    pageUrl: r.page_url,
    mediaUrl: r.media_url,
    title: r.title,
    seriesId: r.series_id,
    seriesTitle: r.series_title,
    season: r.season,
    episode: r.episode,
    dubLanguage: r.dub_language,
    subLanguage: r.sub_language,
    isMovie: r.is_movie === 1,
    thumbnailUrl: r.thumbnail_url,
    status: (r.status as PendingImport["status"]) ?? "queued",
    downloadedBytes: p.downloadedBytes,
    totalBytes: p.totalBytes,
    speedBps: p.speedBps,
    etaSeconds: p.etaSeconds,
    fragmentIndex: p.fragmentIndex,
    fragmentCount: p.fragmentCount,
    progress: progressFraction(p),
    error: r.error,
    startedAt: r.started_at,
    updatedAt: r.updated_at,
  };
}

/** Legt den Eintrag an, bevor der Download startet — sofort sichtbar. */
export function createPending(
  db: Database.Database,
  input: { id: string; pageUrl: string; mediaUrl?: string | null; metadata?: PendingMetadata },
): void {
  const now = Date.now();
  const m = input.metadata ?? {};
  db.prepare(
    `INSERT INTO pending_imports
     (id, page_url, media_url, title, series_id, series_title, season, episode,
      dub_language, sub_language, is_movie, status, started_at, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'queued', ?, ?)
     ON CONFLICT(id) DO UPDATE SET
       status = 'queued', error = NULL, updated_at = excluded.updated_at`,
  ).run(
    input.id,
    input.pageUrl,
    input.mediaUrl ?? null,
    m.title ?? null,
    m.seriesId ?? null,
    m.seriesTitle ?? null,
    m.season ?? null,
    m.episode ?? null,
    m.dubLanguage ?? null,
    m.subLanguage ?? null,
    m.isMovie ? 1 : 0,
    now,
    now,
  );
}

export function markDownloading(db: Database.Database, id: string): void {
  db.prepare("UPDATE pending_imports SET status = 'downloading', updated_at = ? WHERE id = ?").run(
    Date.now(),
    id,
  );
}

export function updateProgress(db: Database.Database, id: string, p: DownloadProgress): void {
  db.prepare(
    `UPDATE pending_imports
        SET status = 'downloading',
            downloaded_bytes = ?, total_bytes = ?, speed_bps = ?, eta_seconds = ?,
            fragment_index = ?, fragment_count = ?, updated_at = ?
      WHERE id = ?`,
  ).run(
    p.downloadedBytes,
    p.totalBytes,
    p.speedBps,
    p.etaSeconds,
    p.fragmentIndex,
    p.fragmentCount,
    Date.now(),
    id,
  );
}

/**
 * Der Eintrag bleibt bei einem Fehler stehen, statt zu verschwinden — sonst
 * sieht der Nutzer nur, dass nichts ankam, und erfährt nie warum.
 */
export function markFailed(db: Database.Database, id: string, error: string): void {
  db.prepare(
    "UPDATE pending_imports SET status = 'failed', error = ?, updated_at = ? WHERE id = ?",
  ).run(error.slice(0, 500), Date.now(), id);
}

/** Nach erfolgreichem Import: Der Eintrag lebt jetzt in `videos` weiter. */
export function removePending(db: Database.Database, id: string): void {
  db.prepare("DELETE FROM pending_imports WHERE id = ?").run(id);
}

export function getPending(db: Database.Database, id: string): PendingImport | undefined {
  const row = db.prepare("SELECT * FROM pending_imports WHERE id = ?").get(id) as
    | PendingRow
    | undefined;
  return row ? toPending(row) : undefined;
}

export function listPending(db: Database.Database): PendingImport[] {
  const rows = db
    .prepare("SELECT * FROM pending_imports ORDER BY started_at DESC")
    .all() as PendingRow[];
  return rows.map(toPending);
}

/**
 * Ändert die Metadaten eines laufenden Imports.
 *
 * Nur ausdrücklich übergebene Felder werden angefasst — `undefined` heißt
 * "unverändert", `null` heißt "leeren".
 */
export function updatePendingMetadata(
  db: Database.Database,
  id: string,
  meta: PendingMetadata,
): PendingImport | undefined {
  const fields: string[] = [];
  const values: unknown[] = [];
  const set = (column: string, value: unknown) => {
    fields.push(`${column} = ?`);
    values.push(value);
  };

  if (meta.title !== undefined) set("title", meta.title);
  if (meta.seriesId !== undefined) set("series_id", meta.seriesId);
  if (meta.seriesTitle !== undefined) set("series_title", meta.seriesTitle);
  if (meta.season !== undefined) set("season", meta.season);
  if (meta.episode !== undefined) set("episode", meta.episode);
  if (meta.dubLanguage !== undefined) set("dub_language", meta.dubLanguage);
  if (meta.subLanguage !== undefined) set("sub_language", meta.subLanguage);
  if (meta.isMovie !== undefined) set("is_movie", meta.isMovie ? 1 : 0);

  if (fields.length > 0) {
    set("updated_at", Date.now());
    values.push(id);
    db.prepare(`UPDATE pending_imports SET ${fields.join(", ")} WHERE id = ?`).run(...values);
  }
  return getPending(db, id);
}

/**
 * Die beim Abschluss zu verwendenden Metadaten.
 *
 * Hat der Nutzer während des Downloads etwas geändert, gewinnt seine Eingabe
 * gegenüber dem, was beim Einreihen übergeben wurde.
 */
export function metadataForCompletion(pending: PendingImport | undefined): PendingMetadata {
  if (!pending) return {};
  return {
    title: pending.title,
    seriesId: pending.seriesId,
    seriesTitle: pending.seriesTitle,
    season: pending.season,
    episode: pending.episode,
    dubLanguage: pending.dubLanguage,
    subLanguage: pending.subLanguage,
    isMovie: pending.isMovie,
  };
}
