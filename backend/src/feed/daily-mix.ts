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

/**
 * Kleiner deterministischer Zufallsgenerator (mulberry32). Gleicher Startwert →
 * gleiche Folge, damit sich Mixe in Tests reproduzieren lassen.
 */
function rng(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export interface MixOptions {
  /**
   * Neu würfeln statt fortschreiben. Ohne das bleibt die Reihenfolge stabil —
   * beim Nachladen soll nichts unter dem Finger wegrutschen. Beim bewussten
   * Neuladen dagegen sollen andere Videos kommen, nicht dieselben.
   */
  reshuffle?: boolean;
  /** Startwert für die Auswahl; nur für Tests, sonst zufällig. */
  seed?: number;
}

/** Aus wie vielen der bestplatzierten Kandidaten beim Würfeln gezogen wird. */
const RESHUFFLE_WINDOW = 8;

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

export function buildDailyMix(
  db: Database.Database,
  now: number = Date.now(),
  options: MixOptions = {},
): void {
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

  // Was schon im heutigen Mix liegt und noch nicht gesehen ist. Ohne Würfeln
  // bleibt das unangetastet — sonst würde ein beiläufiges Nachladen die
  // Reihenfolge unter dem Finger neu sortieren.
  const inMix = new Set(
    (
      db
        .prepare(
          `SELECT m.video_id AS id FROM daily_mix_items m
             JOIN feed_items f ON f.video_id = m.video_id
            WHERE m.mix_date = ? AND f.seen_at IS NULL AND f.playback_failed = 0`,
        )
        .all(mixDate) as { id: string }[]
    ).map((r) => r.id),
  );
  const reshuffle = options.reshuffle === true;
  const pool = reshuffle ? candidates : candidates.filter((c) => !inMix.has(c.id));
  // Beim Ergänzen zählt nur die Lücke bis zum Zielvorrat.
  const target = reshuffle ? FEED_TARGET_UNSEEN : FEED_TARGET_UNSEEN - inMix.size;
  if (pool.length === 0 || target <= 0) return;

  // Priorität nach Quelle, innerhalb einer Quelle das Kurations-Ranking,
  // darüber Kanal-Round-Robin gegen Kanal-Blöcke.
  const byPriority = new Map<number, MixCandidate[]>();
  for (const c of pool) {
    const prio = SOURCE_PRIORITY[c.source] ?? 9;
    const bucket = byPriority.get(prio) ?? [];
    bucket.push(c);
    byPriority.set(prio, bucket);
  }
  // Ohne Würfeln hängt die Rotation am Datum: derselbe Tag ergibt denselben
  // Mix. Beim Neuladen bekommt sie einen frischen Startwert, sonst bliebe der
  // Vorrat hinter den immer gleichen Bestplatzierten verborgen.
  const seed =
    options.seed ??
    (reshuffle ? Math.floor(Math.random() * 0x7fffffff) : Number(mixDate.replaceAll("-", "")));
  const rotation = seed;
  const random = reshuffle || options.seed !== undefined ? rng(seed) : null;
  const groups = [...byPriority.keys()]
    .sort((a, b) => a - b)
    .map(
      (prio) =>
        interleaveByChannel(
          rankCandidates(byPriority.get(prio) ?? [], now),
          rotation,
        ) as MixCandidate[],
    );
  const ordered: MixCandidate[] = [];
  if (!random) {
    // Ohne Würfeln strikt gestaffelt: erst Abos, dann Entdecktes, dann Backfill.
    for (const g of groups) ordered.push(...g);
  } else {
    // Beim Würfeln verschränkt statt gestaffelt. Ein paar Dutzend Abo-Videos
    // würden sonst immer die vorderen Plätze belegen, und der Feed sähe nach
    // jedem Neuladen gleich aus. Abos bleiben bevorzugt — sie ziehen doppelt so
    // oft wie Entdecktes — stehen aber nicht mehr als geschlossener Block vorn.
    const weights = groups.map((_, i) => 1 / (i + 1));
    while (groups.some((g) => g.length > 0)) {
      const total = groups.reduce((sum, g, i) => sum + (g.length > 0 ? (weights[i] ?? 0) : 0), 0);
      let ticket = random() * total;
      let idx = groups.findIndex((g, i) => {
        if (g.length === 0) return false;
        ticket -= weights[i] ?? 0;
        return ticket <= 0;
      });
      if (idx < 0) idx = groups.findIndex((g) => g.length > 0);
      const next = groups[idx]?.shift();
      if (next) ordered.push(next);
    }
  }

  const shorts = ordered.filter((c) => c.kind === "short");
  const longs = ordered.filter((c) => c.kind === "video");

  /**
   * Nimmt das nächste Video, das nicht vom zuletzt gezeigten Kanal stammt.
   * Beim Würfeln nicht stur das bestplatzierte, sondern eines aus den vorderen
   * Kandidaten — die Kuration bleibt, die Auswahl wird abwechslungsreich.
   */
  const pick = (pool: MixCandidate[], recent: string[]): MixCandidate | undefined => {
    const eligible: number[] = [];
    for (let i = 0; i < pool.length && eligible.length < RESHUFFLE_WINDOW; i++) {
      const cand = pool[i];
      if (cand && !recent.includes(cand.channelId)) eligible.push(i);
      if (!random && eligible.length > 0) break; // ohne Würfeln reicht der erste
    }
    // Keine Alternative zum zuletzt gezeigten Kanal: dann eben aus den vorderen
    // Plätzen desselben Kanals ziehen — gewürfelt, wenn gewürfelt werden soll.
    if (eligible.length === 0) {
      const span = Math.min(pool.length, RESHUFFLE_WINDOW);
      return pool.splice(random ? pickWeighted(span, random) : 0, 1)[0];
    }
    const chosen = random ? pickWeighted(eligible.length, random) : 0;
    return pool.splice(eligible[chosen] ?? 0, 1)[0];
  };

  // Beim Anhängen an einen bestehenden Mix zählt dessen Ende weiter: sonst
  // stünde direkt an der Naht ein Kanal doppelt oder ein Langvideo zu früh.
  const tail = reshuffle
    ? []
    : (
        db
          .prepare(
            `SELECT v.channel_id AS channelId, v.format AS format
               FROM daily_mix_items m
               JOIN videos v ON v.id = m.video_id
              WHERE m.mix_date = ?
              ORDER BY m.position DESC LIMIT ?`,
          )
          .all(mixDate, SHORTS_PER_LONG) as { channelId: string; format: string }[]
      ).reverse();
  const result: MixCandidate[] = [];
  let shortRun = 0;
  for (let i = tail.length - 1; i >= 0 && tail[i]?.format === "short"; i--) shortRun++;
  let longRun = 0;
  for (let i = tail.length - 1; i >= 0 && tail[i]?.format !== "short"; i--) longRun++;
  const tailChannels = tail.map((t) => t.channelId);
  while (result.length < target && (shorts.length > 0 || longs.length > 0)) {
    const recent = [...tailChannels, ...result.map((r) => r.channelId)]
      .slice(-2)
      .filter((id): id is string => Boolean(id));
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

  // Beim Würfeln behalten nur gesehene Einträge ihre Plätze, der ungesehene
  // Rest wird ersetzt. Beim Ergänzen wird ans Ende angehängt.
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
  const maxPos = (
    db
      .prepare("SELECT COALESCE(MAX(position), -1) AS p FROM daily_mix_items WHERE mix_date = ?")
      .get(mixDate) as { p: number }
  ).p;
  db.transaction(() => {
    if (reshuffle) {
      db.prepare(
        `DELETE FROM daily_mix_items WHERE mix_date = ? AND video_id IN (
           SELECT m.video_id FROM daily_mix_items m
             JOIN feed_items f ON f.video_id = m.video_id
            WHERE m.mix_date = ? AND f.seen_at IS NULL)`,
      ).run(mixDate, mixDate);
    }
    let position = reshuffle ? maxSeenPos : maxPos;
    for (const item of result) {
      position++;
      insert.run(mixDate, item.id, position, item.source, item.durationSec);
    }
  })();
}

/**
 * Zieht einen Index aus [0, n) mit Vorliebe für vordere Plätze: das Produkt
 * zweier Zufallszahlen lässt die bestplatzierten Kandidaten häufiger gewinnen,
 * ohne die hinteren auszuschließen.
 */
function pickWeighted(n: number, random: () => number): number {
  return Math.min(n - 1, Math.floor(random() * random() * n));
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
