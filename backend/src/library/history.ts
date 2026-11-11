import type Database from "better-sqlite3";

/**
 * Verlauf = was tatsächlich angeschaut wurde. Bisher hing er an
 * feed_items.seen_at, das nur der alte Reel-Player setzte — seit dessen
 * Abschaffung wurde der Verlauf nie mehr fortgeschrieben, und Serienfolgen
 * oder Filme (ohne feed_items-Zeile) tauchten darin nie auf.
 *
 * Jetzt schreibt jeder Fortschritts-Push einen Eintrag. Ab dieser Schwelle
 * zählt ein Video als angeschaut — kurzes Reinklicken landet nicht im Verlauf.
 */
export const HISTORY_MIN_SECONDS = 10;

export function recordWatch(db: Database.Database, videoId: string, seconds: number): void {
  if (seconds < HISTORY_MIN_SECONDS) return;
  const exists = db.prepare("SELECT 1 FROM videos WHERE id = ?").get(videoId);
  if (!exists) return;
  db.prepare(
    `INSERT INTO watch_history (video_id, watched_at) VALUES (?, ?)
     ON CONFLICT(video_id) DO UPDATE SET watched_at = excluded.watched_at`,
  ).run(videoId, Date.now());
}

export function removeFromHistory(db: Database.Database, videoId: string): boolean {
  return db.prepare("DELETE FROM watch_history WHERE video_id = ?").run(videoId).changes > 0;
}

export function clearHistory(db: Database.Database): number {
  return db.prepare("DELETE FROM watch_history").run().changes;
}

/**
 * Zeilen im Format von LibraryVideoDto — dieselben Felder wie
 * „Neu hinzugefügt", aber ohne Pflicht-Join auf downloaded_videos: gestreamte
 * YouTube-Videos haben keine Serverdatei und gehören trotzdem dazu.
 */
const LIBRARY_VIDEO_SELECT = `
  SELECT v.*, c.title AS channelTitle,
         COALESCE(vp.progress_seconds, fi.progress_seconds) AS progress_seconds,
         COALESCE(vp.updated_at, 0) AS progress_updated_at,
         s.overall_score AS overall_score,
         CASE WHEN dv.video_id IS NULL THEN 0 ELSE 1 END AS downloaded
    FROM videos v
    JOIN channels c ON c.id = v.channel_id
    LEFT JOIN feed_items fi ON fi.video_id = v.id
    LEFT JOIN video_progress vp ON vp.video_id = v.id
    LEFT JOIN scores s ON s.video_id = v.id
    LEFT JOIN downloaded_videos dv ON dv.video_id = v.id`;

/** Gestreamte Videos sind nur abspielbar, wenn sie entweder als Datei
 *  vorliegen oder von YouTube kommen (Live-Proxy). */
const PLAYABLE = "(dv.video_id IS NOT NULL OR v.channel_id != 'manual')";

export function listHistory(db: Database.Database, limit = 50): unknown[] {
  return db
    .prepare(
      `${LIBRARY_VIDEO_SELECT}
       JOIN watch_history wh ON wh.video_id = v.id
       WHERE ${PLAYABLE}
       ORDER BY wh.watched_at DESC
       LIMIT ?`,
    )
    .all(limit);
}

/** Ab diesem Anteil gilt ein Video als zu Ende gesehen (der Rest ist Abspann). */
export const FINISHED_SHARE = 0.95;
/** So viele Titel zeigt »Weiterschauen« höchstens — die zuletzt gesehenen. */
export const CONTINUE_LIMIT = 10;

interface ProgressRow {
  id: string;
  series_id: string | null;
  season: number | null;
  episode: number | null;
  duration_seconds: number;
  progress_seconds: number;
  progress_updated_at: number;
}

/** Unter diesem Schlüssel steht ein Titel in »Weiterschauen«: die Serie als Ganzes oder das einzelne Video. */
function continueKey(row: { id: string; series_id: string | null }): string {
  return row.series_id ? `s:${row.series_id}` : `v:${row.id}`;
}

/**
 * »Weiterschauen« nach dem Vorbild von Netflix — ein Eintrag je Titel, zuletzt gesehen zuerst:
 *  - Film oder Einzelvideo: angefangen und noch nicht zu Ende.
 *  - Serie: Es zählt die zuletzt gesehene Folge. Ist sie nicht zu Ende, steht sie da. Ist sie
 *    zu Ende, steht die nächste Folge da (`up_next`); gibt es keine mehr, fällt die Serie heraus.
 *  - Was aus der Reihe entfernt wurde, bleibt weg, bis man den Titel wieder schaut.
 * Vorher stand hier jede angefangene Folge einzeln, auch fünf derselben Serie.
 */
export function listContinueWatching(db: Database.Database, limit = CONTINUE_LIMIT): unknown[] {
  const started = db
    .prepare(
      `${LIBRARY_VIDEO_SELECT}
       WHERE vp.progress_seconds IS NOT NULL
         AND v.duration_seconds > 0
         AND vp.progress_seconds >= ${HISTORY_MIN_SECONDS}
         AND ${PLAYABLE}
       ORDER BY vp.updated_at DESC
       LIMIT 500`,
    )
    .all() as ProgressRow[];
  const dismissed = new Map(
    (
      db.prepare("SELECT key, dismissed_at FROM continue_dismissed").all() as {
        key: string;
        dismissed_at: number;
      }[]
    ).map((d) => [d.key, d.dismissed_at]),
  );
  const nextEpisode = db.prepare(
    `${LIBRARY_VIDEO_SELECT}
     WHERE v.series_id = ? AND ${PLAYABLE}
       AND (COALESCE(v.season, 1) > ? OR (COALESCE(v.season, 1) = ? AND COALESCE(v.episode, 0) > ?))
     ORDER BY COALESCE(v.season, 1), COALESCE(v.episode, 0)
     LIMIT 1`,
  );

  const out: unknown[] = [];
  const seen = new Set<string>();
  for (const row of started) {
    if (out.length >= limit) break;
    const key = continueKey(row);
    if (seen.has(key)) continue; // die jüngste Folge der Serie hat schon entschieden
    seen.add(key);
    if ((dismissed.get(key) ?? 0) >= row.progress_updated_at) continue;

    const finished = row.progress_seconds >= row.duration_seconds * FINISHED_SHARE;
    if (!finished) {
      out.push({ ...row, up_next: 0 });
    } else if (row.series_id) {
      const season = row.season ?? 1;
      const next = nextEpisode.get(row.series_id, season, season, row.episode ?? 0) as
        | ProgressRow
        | undefined;
      if (!next) continue;
      // Eine früher schon ganz gesehene Folge beginnt wieder von vorn; Angefangenes behält seine Stelle.
      const resume =
        next.progress_seconds < next.duration_seconds * FINISHED_SHARE
          ? (next.progress_seconds ?? 0)
          : 0;
      out.push({
        ...next,
        progress_seconds: resume,
        progress_updated_at: row.progress_updated_at,
        up_next: 1,
      });
    }
  }
  return out;
}

/**
 * Nimmt einen Titel aus »Weiterschauen« — die Serie als Ganzes, wenn [videoId] eine Folge ist.
 * Fortschritt und Verlauf bleiben; sobald man den Titel wieder schaut, taucht er wieder auf.
 */
export function dismissContinue(db: Database.Database, videoId: string, now = Date.now()): boolean {
  const row = db.prepare("SELECT id, series_id FROM videos WHERE id = ?").get(videoId) as
    | { id: string; series_id: string | null }
    | undefined;
  if (!row) return false;
  db.prepare(
    `INSERT INTO continue_dismissed (key, dismissed_at) VALUES (?, ?)
     ON CONFLICT(key) DO UPDATE SET dismissed_at = excluded.dismissed_at`,
  ).run(continueKey(row), now);
  return true;
}

/**
 * Neueste Videos der abonnierten YouTube-Kanäle — gestreamt, ohne Download.
 * Reihum gemischt (1. Video jedes Kanals, dann das 2. …), sonst füllt ein
 * fleißiger Kanal die ganze Reihe und die anderen tauchen nie auf.
 */
export function listChannelUploads(
  db: Database.Database,
  subscribedCondition: string,
  limit = 72,
  perChannel = 12,
): unknown[] {
  return db
    .prepare(
      `WITH ranked AS (
         SELECT v.id,
                ROW_NUMBER() OVER (
                  PARTITION BY v.channel_id
                  ORDER BY COALESCE(v.published_at, v.discovered_at) DESC
                ) AS rn,
                COALESCE(v.published_at, v.discovered_at) AS ts
           FROM videos v
           LEFT JOIN scores s ON s.video_id = v.id
          WHERE v.channel_id != 'manual'
            AND ${subscribedCondition}
            AND COALESCE(v.format, 'long') != 'short'
            AND COALESCE(s.decision, 'approved') = 'approved'
       )
       ${LIBRARY_VIDEO_SELECT}
       JOIN ranked r ON r.id = v.id
       WHERE r.rn <= ?
       ORDER BY r.rn ASC, r.ts DESC
       LIMIT ?`,
    )
    .all(perChannel, limit);
}

/**
 * Die Uploads der abonnierten Kanäle als fortlaufende Liste, neueste zuerst —
 * seitenweise, optional auf einen Kanal und/oder einen Suchbegriff (Titel oder
 * Kanalname) eingegrenzt. Grundlage für den Kanal-Bereich der App, der beim
 * Scrollen nachlädt, statt alles auf einmal zu holen.
 */
export function listUploadsPage(
  db: Database.Database,
  subscribedCondition: string,
  opts: { channelId?: string; query?: string; offset?: number; limit?: number } = {},
): unknown[] {
  const limit = Math.min(Math.max(opts.limit ?? 5, 1), 50);
  const offset = Math.max(opts.offset ?? 0, 0);
  const where: string[] = [
    "v.channel_id != 'manual'",
    subscribedCondition,
    "COALESCE(v.format, 'long') != 'short'",
    "COALESCE(s.decision, 'approved') = 'approved'",
  ];
  const params: unknown[] = [];
  if (opts.channelId) {
    where.push("v.channel_id = ?");
    params.push(opts.channelId);
  }
  const query = opts.query?.trim();
  if (query) {
    // LIKE-Sonderzeichen im Suchbegriff wörtlich nehmen.
    const like = `%${query.replace(/[\\%_]/g, (c) => `\\${c}`)}%`;
    where.push("(v.title LIKE ? ESCAPE '\\' OR c.title LIKE ? ESCAPE '\\')");
    params.push(like, like);
  }
  return db
    .prepare(
      `${LIBRARY_VIDEO_SELECT}
       WHERE ${where.join(" AND ")}
       ORDER BY COALESCE(v.published_at, v.discovered_at) DESC, v.id DESC
       LIMIT ? OFFSET ?`,
    )
    .all(...params, limit, offset);
}
