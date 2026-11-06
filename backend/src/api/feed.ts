import type Database from "better-sqlite3";
import type { FastifyInstance } from "fastify";
import {
  buildDailyMix,
  getTimeBudgetMinutes,
  mixDateFor,
  setTimeBudgetMinutes,
  todayMixStats,
} from "../feed/daily-mix.js";
import { subscribedChannelCondition } from "../feed/subscriptions.js";
import { MANUAL_CHANNEL_ID } from "../import/manual-import.js";
import { recordWatch } from "../library/history.js";
import { deleteVideoCompletely } from "../library/removal.js";

/** Seitengröße des Endlos-Feeds; die App lädt beim Scrollen nach. */
const FEED_PAGE_SIZE = 30;

export interface FeedDeps {
  db: Database.Database;
  dailyBudget: number;
  /** Wärmt Stream-URLs vor, damit der Player nicht auf yt-dlp warten muss. */
  prefetchStreams?: ((videoIds: string[]) => void) | undefined;
}

// ---------------------------------------------------------------------------
// Lean row type + Reihenfolge-Helfer (Ranking, Cooldown, Kanal-Interleave)
// ---------------------------------------------------------------------------

export interface RawFeedRow {
  kind: "short" | "video";
  id: string;
  channelId: string;
  category: string | null;
  addedToFeedAt: number;
  durationSec: number;
}

const COOLDOWN_WINDOW = 3;
const CHANNEL_MAX_IN_WINDOW = 2;
const LOOKAHEAD = 5;

/**
 * Reihenfolge der Kandidaten: neueste zuerst. Es gibt keine Bewertung mehr,
 * also auch kein Qualitäts-Ranking — der Feed besteht aus den Abos, und von
 * denen zählt, was frisch ist. Bewusst KEIN Engagement-Signal (Watch-Time,
 * Klicks). Vielfalt besorgt danach interleaveByChannel. Gleichstände brechen
 * nach id, damit Paging und Tests stabil bleiben.
 */
export function rankCandidates<T extends RawFeedRow>(candidates: T[]): T[] {
  return [...candidates].sort(
    (a, b) => b.addedToFeedAt - a.addedToFeedAt || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0),
  );
}

/**
 * Apply soft cooldown: same video never twice in 3-window,
 * channel max 2× in 3-window, plus a topic-mix lookahead that prefers
 * a different category from last-output when available. Falls back to
 * gradually relaxed constraints if the strict pass cannot fill the page.
 */
export function applyCooldown(candidates: RawFeedRow[], pageSize: number): RawFeedRow[] {
  const out: RawFeedRow[] = [];
  const remaining = [...candidates];

  while (out.length < pageSize && remaining.length > 0) {
    const window = remaining.slice(0, LOOKAHEAD);
    const last3 = out.slice(-COOLDOWN_WINDOW);
    const last3Ids = new Set(last3.map((r) => r.id));
    const channelCount = (chan: string) => last3.filter((r) => r.channelId === chan).length;

    let pickIdx = window.findIndex(
      (r) =>
        !last3Ids.has(r.id) && channelCount(r.channelId) < CHANNEL_MAX_IN_WINDOW,
    );
    if (pickIdx === -1) {
      pickIdx = window.findIndex(
        (r) => !last3Ids.has(r.id) && channelCount(r.channelId) < 3,
      );
    }
    if (pickIdx === -1) {
      pickIdx = window.findIndex((r) => !last3Ids.has(r.id));
    }
    if (pickIdx === -1) {
      const fallbackIdx = remaining.findIndex((r) => !last3Ids.has(r.id));
      if (fallbackIdx === -1) break;
      const picked = remaining.splice(fallbackIdx, 1)[0]!;
      out.push(picked);
      continue;
    }

    const primary = window[pickIdx]!;

    const lastOut = out[out.length - 1];
    if (lastOut && primary.category && lastOut.category === primary.category) {
      const swapIdx = window.findIndex(
        (r, i) =>
          i !== pickIdx &&
          !last3Ids.has(r.id) &&
          channelCount(r.channelId) < CHANNEL_MAX_IN_WINDOW &&
          r.category &&
          r.category !== lastOut.category,
      );
      if (swapIdx !== -1) {
        const better = window[swapIdx]!;
        const realIdx = remaining.indexOf(better);
        const picked = remaining.splice(realIdx, 1)[0]!;
        out.push(picked);
        continue;
      }
    }

    const realIdx = remaining.indexOf(primary);
    const picked = remaining.splice(realIdx, 1)[0]!;
    out.push(picked);
  }

  return out;
}

// ---------------------------------------------------------------------------
// Channel round-robin interleave — the real "variety" pass.
//
// applyCooldown only forbids clustering in a tiny 3-window, so when one channel
// dominates the pool (e.g. 29 videos vs 8) you still get long same-channel runs.
// This instead deals candidates out round-robin across channels: A, B, A, B, …
// so consecutive items come from DIFFERENT channels as much as possible, never
// the same video twice. Within a channel the ranked order is preserved.
//
// `rotation` shifts which channel starts each call, so a fresh app open / tab
// switch reshuffles the top of the feed → new mix every time, without ever
// reusing a video the user already saw (seen items are filtered upstream).
// This maximizes DIVERSITY, not watch-time — anti-doomscroll by design.
// ---------------------------------------------------------------------------

export function interleaveByChannel(ranked: RawFeedRow[], rotation = 0): RawFeedRow[] {
  if (ranked.length <= 1) return [...ranked];

  // Group preserving rank order; remember first-seen channel order.
  const groups = new Map<string, RawFeedRow[]>();
  for (const r of ranked) {
    const g = groups.get(r.channelId);
    if (g) g.push(r);
    else groups.set(r.channelId, [r]);
  }
  const channels = [...groups.keys()];
  if (channels.length <= 1) return [...ranked];

  // Rotate the channel start position so each call leads with a different one.
  const offset = ((rotation % channels.length) + channels.length) % channels.length;
  const order = [...channels.slice(offset), ...channels.slice(0, offset)];

  const out: RawFeedRow[] = [];
  const seenIds = new Set<string>();
  let remaining = ranked.length;
  while (remaining > 0) {
    let progressed = false;
    for (const ch of order) {
      const g = groups.get(ch)!;
      // Take the next not-yet-emitted, distinct-parent item from this channel.
      while (g.length > 0) {
        const next = g.shift()!;
        remaining--;
        if (seenIds.has(next.id)) continue; // skip dup
        seenIds.add(next.id);
        out.push(next);
        progressed = true;
        break;
      }
    }
    if (!progressed) break; // all groups exhausted
  }
  return out;
}

// ---------------------------------------------------------------------------
// Hydration: lean RawFeedRow → full DTO for the API response
// ---------------------------------------------------------------------------

/**
 * Batched hydration: turns N ranked rows into N DTOs with ONE query instead of
 * one query per row. Per-row hydration was an N+1 on the hot feed path — up to
 * 50 synchronous SQLite round-trips per request, each blocking the event loop.
 * Output preserves the input order (the ranking order).
 */
export function hydrateFeedBatch(db: Database.Database, rows: Pick<RawFeedRow, "id">[]): unknown[] {
  if (rows.length === 0) return [];
  const ids = rows.map((r) => r.id);
  const byId = new Map<string, any>();

  const ph = ids.map(() => "?").join(",");
  // downloaded_videos per LEFT JOIN: seit dem Streaming-Umbau existieren
  // approvte Videos in der Regel OHNE Serverdatei (filePath = null).
  const videoRows = db
    .prepare(`
      SELECT CASE WHEN v.format = 'short' THEN 'short' ELSE 'video' END AS kind,
             f.video_id AS videoId,
             v.title, v.duration_seconds AS durationSeconds,
             v.aspect_ratio AS aspectRatio, v.thumbnail_url AS thumbnailUrl,
             v.channel_id AS channelId, c.title AS channelTitle,
             s.category, s.reasoning, s.overall_score AS overallScore,
             s.educational_value AS educationalValue,
             f.added_to_feed_at AS addedAt, f.saved, f.seen_at AS seenAt,
             v.summary AS summary,
             v.source AS source,
             dv.file_path AS filePath
        FROM feed_items f
        JOIN videos v ON v.id = f.video_id
        JOIN channels c ON c.id = v.channel_id
        JOIN scores s ON s.video_id = f.video_id
        LEFT JOIN downloaded_videos dv ON dv.video_id = f.video_id
       WHERE f.video_id IN (${ph})
    `)
    .all(...ids) as any[];
  for (const vr of videoRows) byId.set(vr.videoId, vr);

  // Reassemble in the ranked order; drop any row a JOIN couldn't resolve.
  return rows.map((r) => byId.get(r.id)).filter((x) => x != null);
}

// ---------------------------------------------------------------------------
// Feed-state mutation helpers
// ---------------------------------------------------------------------------

function updateFeedRow(db: Database.Database, id: string, set: string, ...params: unknown[]): void {
  db.prepare(`UPDATE feed_items SET ${set} WHERE video_id = ?`).run(...params, id);
}

/**
 * Fortschritt eines Videos auch ohne feed_items-Zeile festhalten.
 * Heruntergeladene Serienfolgen laufen nie durch den Feed und hatten deshalb
 * bisher keine Zeile, in der ihr Fortschritt landen konnte.
 */
function upsertVideoProgress(db: Database.Database, id: string, seconds: number): void {
  const video = db.prepare("SELECT 1 AS ok FROM videos WHERE id = ?").get(id);
  if (!video) return;
  db.prepare(
    `INSERT INTO video_progress (video_id, progress_seconds, updated_at)
     VALUES (?, ?, ?)
     ON CONFLICT(video_id) DO UPDATE SET
       progress_seconds = excluded.progress_seconds,
       updated_at = excluded.updated_at`,
  ).run(id, seconds, Date.now());
}

// ---------------------------------------------------------------------------
// Route registration
// ---------------------------------------------------------------------------

export async function registerFeedRoutes(app: FastifyInstance, deps: FeedDeps): Promise<void> {
  // SELECT DISTINCT as defensive safeguard — even though all four JOINed tables
  // (videos, feed_items, scores, downloaded_videos) have video_id as PRIMARY KEY
  // and can't structurally produce duplicates, future schema changes or
  // dev-state drift shouldn't be able to leak duplicate rows to the client.
  const BASE_SELECT = `
    SELECT DISTINCT CASE WHEN v.format = 'short' THEN 'short' ELSE 'video' END AS kind,
           fi.video_id as videoId, v.title, v.duration_seconds as durationSeconds,
           v.aspect_ratio as aspectRatio, v.thumbnail_url as thumbnailUrl,
           v.channel_id as channelId, c.title as channelTitle,
           s.category, s.reasoning, s.overall_score as overallScore,
           s.educational_value as educationalValue,
           fi.added_to_feed_at as addedAt, fi.saved, fi.seen_at as seenAt,
           v.summary AS summary, v.source AS source
    FROM feed_items fi
    JOIN videos v ON v.id = fi.video_id
    JOIN channels c ON c.id = v.channel_id
    JOIN scores s ON s.video_id = fi.video_id
    LEFT JOIN downloaded_videos dv ON dv.video_id = fi.video_id
  `;
  // Feed = nur Abos. Gilt für alles, was der Feed von sich aus anbietet
  // (Tagesmix, Verlauf, Warteschlangen-Vorschlag). Gespeichertes und explizit
  // Eingereihtes bleibt sichtbar — das hat der Nutzer bewusst behalten.
  const SUBSCRIBED_VIDEO = subscribedChannelCondition("v.channel_id");
  // Manuelle Importe (Serien, Filme) haben eine feed_items-Zeile für
  // Fortschritt/Cleanup, gehören aber in die Bibliothek, nie in den Feed.
  const NOT_MANUAL = `v.channel_id <> '${MANUAL_CHANNEL_ID}'`;

  app.get<{
    Querystring: {
      mode?: string;
      rotation?: string;
      offset?: string;
      limit?: string;
      refresh?: string;
    };
  }>("/feed", async (req, reply) => {
    const mode = (req.query.mode ?? "new") as "new" | "saved" | "old";
    if (mode !== "new" && mode !== "saved" && mode !== "old") {
      return reply.code(400).send({ error: "mode must be new, saved, or old" });
    }

    if (mode === "new") {
      // Etappe 4: Der Feed IST der Tagesmix — einmal kuratiert, dann stabil.
      // Lazy-Top-up bei jedem Abruf (idempotent, budgetbegrenzt); die frühere
      // Request-Zeit-Pipeline (rank/interleave/cooldown) läuft jetzt im
      // Mix-Builder. Reihenfolge bleibt über App-Öffnungen hinweg identisch.
      // Endlos-Feed: seitenweise ausliefern, die App lädt beim Scrollen nach.
      const limit = Math.min(Math.max(Number(req.query.limit) || FEED_PAGE_SIZE, 1), 100);
      const offset = Math.max(Number(req.query.offset) || 0, 0);
      // Nach-oben-Ziehen, Tab-Tipp und App-Start schicken refresh=1: dann wird
      // die Auswahl neu gewürfelt, sonst sieht man immer dieselben Videos aus
      // dem Vorrat. Nur auf der ersten Seite — beim Nachladen darf sich die
      // Reihenfolge unter dem Finger nicht ändern.
      const forced = req.query.refresh === "1" || req.query.refresh === "true";
      buildDailyMix(deps.db, Date.now(), { reshuffle: forced && offset === 0 });
      const mixDate = mixDateFor(Date.now());
      const mixRows = deps.db
        .prepare(
          `SELECT m.video_id AS id FROM daily_mix_items m
            JOIN feed_items f ON f.video_id = m.video_id
            JOIN videos v ON v.id = m.video_id
           WHERE m.mix_date = ? AND f.seen_at IS NULL AND f.playback_failed = 0
             AND ${SUBSCRIBED_VIDEO}
           ORDER BY m.position ASC
           LIMIT ? OFFSET ?`,
        )
        .all(mixDate, limit, offset) as { id: string }[];
      // Die nächsten Videos vorwärmen, bevor der Player sie anfragt.
      deps.prefetchStreams?.(mixRows.map((r) => r.id));
      return hydrateFeedBatch(deps.db, mixRows as RawFeedRow[]);
    } else if (mode === "saved") {
      return deps.db
        .prepare(
          BASE_SELECT +
            `
        WHERE fi.saved = 1 AND ${NOT_MANUAL}
        ORDER BY COALESCE(fi.seen_at, fi.added_to_feed_at) DESC
        LIMIT 100`,
        )
        .all();
    } else {
      // mode === "old"
      return deps.db
        .prepare(
          BASE_SELECT +
            `
        WHERE fi.seen_at IS NOT NULL AND ${SUBSCRIBED_VIDEO}
        ORDER BY fi.seen_at DESC
        LIMIT 100`,
        )
        .all();
    }
  });

  app.get("/queue", async () => {
    const explicit = deps.db
      .prepare(
        BASE_SELECT +
          `
        WHERE fi.playback_failed = 0
          AND fi.queued_at IS NOT NULL
          AND ${NOT_MANUAL}
        ORDER BY COALESCE(fi.queue_order, fi.queued_at) ASC, fi.queued_at ASC
        LIMIT 12`,
      )
      .all();

    if (explicit.length > 0) return explicit;

    return deps.db
      .prepare(
        BASE_SELECT +
          `
        WHERE fi.playback_failed = 0
          AND ${SUBSCRIBED_VIDEO}
        ORDER BY
          CASE
            WHEN fi.progress_seconds > 0 THEN 0
            WHEN fi.seen_at IS NULL THEN 1
            WHEN fi.saved = 1 THEN 2
            ELSE 3
          END ASC,
          v.duration_seconds ASC,
          fi.added_to_feed_at DESC
        LIMIT 6`,
      )
      .all();
  });

  app.post<{ Params: { id: string } }>("/queue/:id", async (req, reply) => {
    const existing = deps.db
      .prepare("SELECT 1 FROM feed_items WHERE video_id = ?")
      .get(req.params.id);
    if (!existing) return reply.code(404).send({ error: "video not found in feed" });

    const maxOrder = deps.db
      .prepare("SELECT COALESCE(MAX(queue_order), 0) AS maxOrder FROM feed_items")
      .get() as { maxOrder: number };

    deps.db
      .prepare(
        `UPDATE feed_items
         SET queued_at = COALESCE(queued_at, ?),
             queue_order = COALESCE(queue_order, ?)
         WHERE video_id = ?`,
      )
      .run(Date.now(), maxOrder.maxOrder + 1, req.params.id);
    return reply.code(204).send();
  });

  app.delete<{ Params: { id: string } }>("/queue/:id", async (req, reply) => {
    deps.db
      .prepare("UPDATE feed_items SET queued_at = NULL, queue_order = NULL WHERE video_id = ?")
      .run(req.params.id);
    return reply.code(204).send();
  });

  app.post<{ Params: { id: string } }>("/feed/:id/seen", async (req, reply) => {
    const id = req.params.id;
    const now = Date.now();
    updateFeedRow(deps.db, id, "seen_at = ?", now);
    deps.db
      .prepare("UPDATE downloaded_videos SET last_served_at = ? WHERE video_id = ?")
      .run(now, id);
    // Nachschub vorwärmen: die nächsten ungesehenen Mix-Items auflösen, damit
    // der Swipe nicht in einen kalten yt-dlp-Lauf läuft.
    const prefetch = deps.prefetchStreams;
    if (prefetch) {
      const next = deps.db
        .prepare(
          `SELECT m.video_id AS id FROM daily_mix_items m
             JOIN feed_items f ON f.video_id = m.video_id
             JOIN videos v ON v.id = m.video_id
            WHERE m.mix_date = ? AND f.seen_at IS NULL AND f.playback_failed = 0
              AND ${SUBSCRIBED_VIDEO}
            ORDER BY m.position ASC LIMIT 8`,
        )
        .all(mixDateFor(Date.now())) as { id: string }[];
      prefetch(next.map((r) => r.id));
    }
    return reply.code(204).send();
  });

  app.post<{ Params: { id: string } }>("/feed/:id/save", async (req, reply) => {
    updateFeedRow(deps.db, req.params.id, "saved = 1");
    return reply.code(204).send();
  });

  app.put<{
    Params: { id: string };
    Body: { seconds?: number };
  }>("/feed/:id/progress", async (req, reply) => {
    const seconds = Math.max(0, Number(req.body?.seconds ?? 0));
    updateFeedRow(deps.db, req.params.id, "progress_seconds = ?", seconds);
    upsertVideoProgress(deps.db, req.params.id, seconds);
    recordWatch(deps.db, req.params.id, seconds);
    return reply.code(204).send();
  });

  app.delete<{ Params: { id: string } }>("/feed/:id/save", async (req, reply) => {
    updateFeedRow(deps.db, req.params.id, "saved = 0");
    return reply.code(204).send();
  });

  app.post<{ Params: { id: string } }>("/feed/:id/unplayable", async (req, reply) => {
    updateFeedRow(deps.db, req.params.id, "playback_failed = 1");
    return reply.code(204).send();
  });

  app.post<{ Params: { id: string } }>("/feed/:id/less-like-this", async (req, reply) => {
    const now = Date.now();
    deps.db
      .prepare(
        "UPDATE feed_items SET seen_at = COALESCE(seen_at, ?), playback_failed = 1 WHERE video_id = ?",
      )
      .run(now, req.params.id);
    return reply.code(204).send();
  });

  app.get("/feed/today-count", async () => {
    // Etappe 4: Zeitbudget statt Stückzahl. Alte Feldnamen bleiben gefüllt,
    // damit ältere App-Versionen nicht brechen.
    const stats = todayMixStats(deps.db);
    return {
      dailyBudget: stats.budgetMinutes,
      unseenCount: stats.unseenCount,
      capped: stats.capped,
      budgetMinutes: stats.budgetMinutes,
      remainingSeconds: stats.remainingSeconds,
      totalSeconds: stats.totalSeconds,
      consumedSeconds: stats.consumedSeconds,
    };
  });

  app.get("/feed/budget", async () => ({ minutes: getTimeBudgetMinutes(deps.db) }));

  app.put<{ Body: { minutes?: unknown } }>("/feed/budget", async (req, reply) => {
    const minutes = req.body?.minutes;
    if (typeof minutes !== "number" || !Number.isFinite(minutes)) {
      return reply.code(400).send({ error: "minutes must be a number" });
    }
    return { minutes: setTimeBudgetMinutes(deps.db, minutes) };
  });

  app.delete<{ Params: { id: string } }>("/feed/:id", async (req, reply) => {
    // Vollständig: DB-Zeilen aller abhängigen Tabellen + Datei auf der Platte.
    const removed = await deleteVideoCompletely(deps.db, req.params.id);
    if (!removed) return reply.code(404).send({ error: "video not found" });
    return reply.code(204).send();
  });
}
