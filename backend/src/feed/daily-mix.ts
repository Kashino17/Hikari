import type Database from "better-sqlite3";
import { type RawFeedRow, interleaveByChannel, rankCandidates } from "../api/feed.js";

// Zeitbudget-Grenzen (Minuten) — der Tuning-Regler bewegt sich hierin.
const BUDGET_MIN = 10;
const BUDGET_MAX = 240;
const BUDGET_DEFAULT = 45;

// Rhythmus: der Feed ist ein Kurzform-Feed — nach je 6 Shorts kommt eine
// Langvideo-Karte als Abwechslung, nicht als Hauptgang.
const SHORTS_PER_LONG = 6;
// Höchstens so viele Langvideos am Stück. Gibt es keine Kurzform als Trenner,
// endet der Mix lieber — Nachschub kommt, Blöcke bleiben aus.
const LONG_RUN_MAX = 2;
// Vorrat an ungesehenen Items, den der Feed bereithält. Kein Zeitdeckel mehr:
// der Feed ist unendlich, seine Qualität kommt aus dem Filter, nicht aus einer
// künstlichen Bremse. Das Zeitbudget bleibt reine Anzeige ("heute geschaut").
const FEED_TARGET_UNSEEN = 40;
// Ab diesem Restvorrat wird Discovery angestoßen, damit nie Leere entsteht.
const REFILL_TRIGGER_UNSEEN = 15;

// Quellen-Priorität: eigene Abos zuerst, dann Entdecktes, Backfill zuletzt.
const SOURCE_PRIORITY: Record<string, number> = {
  subscription: 0,
  probe: 1,
  topic: 2,
  backfill: 3,
};

/** Lokaler Kalendertag (sv-SE liefert ISO-Format YYYY-MM-DD in lokaler Zeit). */
export function mixDateFor(now: number): string {
  return new Date(now).toLocaleDateString("sv-SE");
}

function ensureSettingsRow(db: Database.Database): void {
  db.prepare(
    "INSERT OR IGNORE INTO feed_settings (id, daily_time_budget_minutes, updated_at) VALUES (1, ?, ?)",
  ).run(BUDGET_DEFAULT, Date.now());
}

export function getTimeBudgetMinutes(db: Database.Database): number {
  ensureSettingsRow(db);
  const row = db
    .prepare("SELECT daily_time_budget_minutes AS m FROM feed_settings WHERE id = 1")
    .get() as { m: number };
  return row.m;
}

export function setTimeBudgetMinutes(db: Database.Database, minutes: number): number {
  ensureSettingsRow(db);
  const clamped = Math.min(BUDGET_MAX, Math.max(BUDGET_MIN, Math.round(minutes)));
  db.prepare(
    "UPDATE feed_settings SET daily_time_budget_minutes = ?, updated_at = ? WHERE id = 1",
  ).run(clamped, Date.now());
  return clamped;
}

interface MixCandidate extends RawFeedRow {
  kind: "short" | "video";
  source: string;
  durationSec: number;
}

/**
 * Idempotenter Top-up des heutigen Tagesmixes: Kandidaten nach
 * Quellen-Priorität + Kurations-Ranking, Kanal-Vielfalt per Interleave,
 * verwoben im Rhythmus ~5 Shorts : 1 Langvideo, aufgenommen bis die
 * Dauersumme das Zeitbudget erreicht (das letzte Item darf überziehen).
 * Gesehene Mix-Items zählen weiter gegen das Budget — konsumiert ist konsumiert.
 */
/**
 * Tatsächlich konsumierte Zeit des Tages: die abgespielten Sekunden der
 * Mix-Items (gedeckelt auf die Videolänge). Eine weggeswipte Karte kostet
 * damit NICHTS vom Tagesbudget — nur echtes Schauen zählt.
 */
function consumedSeconds(db: Database.Database, mixDate: string): number {
  return (
    db
      .prepare(
        `SELECT COALESCE(SUM(MIN(COALESCE(f.progress_seconds, 0), m.duration_seconds)), 0) AS s
           FROM daily_mix_items m
           JOIN feed_items f ON f.video_id = m.video_id
          WHERE m.mix_date = ?`,
      )
      .get(mixDate) as { s: number }
  ).s;
}

/** Dauer der noch ungesehenen Items im heutigen Mix — der aktuelle Vorrat. */
function unseenSeconds(db: Database.Database, mixDate: string): number {
  return (
    db
      .prepare(
        `SELECT COALESCE(SUM(m.duration_seconds), 0) AS s
           FROM daily_mix_items m
           JOIN feed_items f ON f.video_id = m.video_id
          WHERE m.mix_date = ? AND f.seen_at IS NULL AND f.playback_failed = 0`,
      )
      .get(mixDate) as { s: number }
  ).s;
}

/** Wie viele Langvideos schon im heutigen Mix liegen (für den Anteils-Deckel). */
function existingLongCount(db: Database.Database, mixDate: string): number {
  return (
    db
      .prepare(
        `SELECT COUNT(*) AS c FROM daily_mix_items m
           JOIN videos v ON v.id = m.video_id
           JOIN feed_items f ON f.video_id = m.video_id
          WHERE m.mix_date = ? AND v.format = 'long'
            AND f.seen_at IS NULL AND f.playback_failed = 0`,
      )
      .get(mixDate) as { c: number }
  ).c;
}

/** Anzahl der ungesehenen Items im heutigen Mix. */
export function unseenMixCount(db: Database.Database, mixDate: string): number {
  return (
    db
      .prepare(
        `SELECT COUNT(*) AS c FROM daily_mix_items m
           JOIN feed_items f ON f.video_id = m.video_id
          WHERE m.mix_date = ? AND f.seen_at IS NULL AND f.playback_failed = 0`,
      )
      .get(mixDate) as { c: number }
  ).c;
}

export function buildDailyMix(db: Database.Database, now: number = Date.now()): void {
  const mixDate = mixDateFor(now);

  // ALLE ungesehenen freigegebenen Videos sind Kandidaten — auch die, die
  // bereits im Mix liegen. Die Reihenfolge wird bei jedem Lauf neu gedacht,
  // sonst klebt später eingetroffene Kurzform hinter einer Langvideo-Schlange.
  const candidates = db
    .prepare(
      `SELECT f.video_id AS id, f.video_id AS parentVideoId, v.channel_id AS channelId,
              v.duration_seconds AS durationSec,
              CASE WHEN v.format = 'short' THEN 'short' ELSE 'video' END AS kind,
              COALESCE(v.source, 'subscription') AS source,
              f.added_to_feed_at AS addedToFeedAt, s.category AS category,
              s.overall_score AS overallScore, s.educational_value AS educationalValue,
              cms.calculated_score AS channelMatch
         FROM feed_items f
         JOIN videos v ON v.id = f.video_id
         LEFT JOIN scores s ON s.video_id = f.video_id
         LEFT JOIN channel_match_scores cms ON cms.channel_id = v.channel_id
        WHERE f.seen_at IS NULL AND f.playback_failed = 0 AND f.is_pre_clipper = 1`,
    )
    .all() as MixCandidate[];
  if (candidates.length === 0) return;

  // Priorität nach Quelle, innerhalb einer Quelle das Kurations-Ranking,
  // darüber Kanal-Round-Robin gegen Kanal-Blöcke.
  const byPriority = new Map<number, MixCandidate[]>();
  for (const c of candidates) {
    const prio = SOURCE_PRIORITY[c.source] ?? 9;
    const bucket = byPriority.get(prio) ?? [];
    bucket.push(c);
    byPriority.set(prio, bucket);
  }
  const rotation = Number(mixDate.replaceAll("-", ""));
  const ordered: MixCandidate[] = [];
  for (const prio of [...byPriority.keys()].sort((a, b) => a - b)) {
    const ranked = rankCandidates(byPriority.get(prio) ?? [], now) as MixCandidate[];
    ordered.push(...(interleaveByChannel(ranked, rotation) as MixCandidate[]));
  }

  const shorts = ordered.filter((c) => c.kind === "short");
  const longs = ordered.filter((c) => c.kind === "video");

  /** Nimmt das nächste Video, das nicht vom zuletzt gezeigten Kanal stammt. */
  const pick = (pool: MixCandidate[], recent: string[]): MixCandidate | undefined => {
    const idx = pool.findIndex((c) => !recent.includes(c.channelId));
    return pool.splice(idx >= 0 ? idx : 0, 1)[0];
  };

  const result: MixCandidate[] = [];
  let shortRun = 0;
  let longRun = 0;
  while (result.length < FEED_TARGET_UNSEEN && (shorts.length > 0 || longs.length > 0)) {
    const recent = result.slice(-2).map((r) => r.channelId);
    const longTurn = shorts.length === 0 || shortRun >= SHORTS_PER_LONG;
    let next: MixCandidate | undefined;
    if (longTurn && longs.length > 0 && longRun < LONG_RUN_MAX) {
      next = pick(longs, recent);
      longRun++;
      shortRun = 0;
    } else if (shorts.length > 0) {
      next = pick(shorts, recent);
      shortRun++;
      longRun = 0;
    } else {
      // Nur noch Langvideos, aber der Block wäre zu lang: lieber hier enden —
      // der Nachschub bringt Kurzform, statt eine Langform-Schlange zu bauen.
      break;
    }
    if (!next) break;
    result.push(next);
  }
  if (result.length === 0) return;

  // Gesehene Einträge behalten ihre Plätze; der ungesehene Rest wird ersetzt.
  const maxSeenPos = (
    db
      .prepare(
        `SELECT COALESCE(MAX(m.position), -1) AS p FROM daily_mix_items m
           JOIN feed_items f ON f.video_id = m.video_id
          WHERE m.mix_date = ? AND f.seen_at IS NOT NULL`,
      )
      .get(mixDate) as { p: number }
  ).p;

  const insert = db.prepare(
    "INSERT OR REPLACE INTO daily_mix_items (mix_date, video_id, position, source, duration_seconds) VALUES (?, ?, ?, ?, ?)",
  );
  db.transaction(() => {
    db.prepare(
      `DELETE FROM daily_mix_items WHERE mix_date = ? AND video_id IN (
         SELECT m.video_id FROM daily_mix_items m
           JOIN feed_items f ON f.video_id = m.video_id
          WHERE m.mix_date = ? AND f.seen_at IS NULL)`,
    ).run(mixDate, mixDate);
    let position = maxSeenPos;
    for (const item of result) {
      position++;
      insert.run(mixDate, item.id, position, item.source, item.durationSec);
    }
  })();
}

export interface TodayMixStats {
  budgetMinutes: number;
  /** Gesamtdauer aller heutigen Mix-Items (Angebot). */
  totalSeconds: number;
  /** Tatsächlich geschaute Sekunden — nur die zählen gegen das Budget. */
  consumedSeconds: number;
  /** Verbleibendes Tagesbudget in Sekunden. */
  remainingSeconds: number;
  unseenCount: number;
  capped: boolean;
}

export function todayMixStats(db: Database.Database, now: number = Date.now()): TodayMixStats {
  const mixDate = mixDateFor(now);
  const budgetMinutes = getTimeBudgetMinutes(db);
  const total = (
    db
      .prepare(
        "SELECT COALESCE(SUM(duration_seconds), 0) AS s FROM daily_mix_items WHERE mix_date = ?",
      )
      .get(mixDate) as { s: number }
  ).s;
  const unseen = db
    .prepare(
      `SELECT COUNT(*) AS c FROM daily_mix_items m
         JOIN feed_items f ON f.video_id = m.video_id
        WHERE m.mix_date = ? AND f.seen_at IS NULL AND f.playback_failed = 0`,
    )
    .get(mixDate) as { c: number };
  const consumed = consumedSeconds(db, mixDate);
  const budgetSeconds = budgetMinutes * 60;
  return {
    budgetMinutes,
    totalSeconds: total,
    consumedSeconds: consumed,
    remainingSeconds: Math.max(0, budgetSeconds - consumed),
    unseenCount: unseen.c,
    // Nur noch Information für die Anzeige — der Feed wird davon nicht gestoppt.
    capped: consumed >= budgetSeconds,
  };
}
