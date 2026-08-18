import type Database from "better-sqlite3";
import { decide } from "../scorer/decision.js";
import { getActivePromptForChannel, getFilterForChannel } from "../scorer/filter-repo.js";
import type { Scorer } from "../scorer/types.js";
import { SHORTS_PROMPT_HINT, thresholdsForFilter } from "./orchestrator.js";

/** Marker im scores-Eintrag: dieses Short lief bereits durch die Neubewertung. */
const RESCORE_MARKER = "rescore-shorts";

export interface RescoreDeps {
  db: Database.Database;
  scorer: Scorer;
  /** Wie viele Shorts pro Lauf neu bewertet werden. */
  limit?: number;
  now?: () => number;
}

/**
 * Holt fälschlich abgelehnte Shorts zurück: Vor dem Kurzform-Hinweis im
 * Scorer-Prompt fielen native Shorts pauschal als "zu kurz" durch — teils
 * über tausend Videos aus abonnierten Kanälen. Die Neubewertung braucht kein
 * YouTube: Titel, Beschreibung und Transkript liegen bereits in der Datenbank.
 * Läuft in kleinen Portionen, damit der Scorer nebenher frische Videos schafft.
 */
export async function rescoreLegacyShorts(deps: RescoreDeps): Promise<number> {
  const { db, scorer } = deps;
  const now = deps.now ?? Date.now;
  const limit = deps.limit ?? 10;

  const candidates = db
    .prepare(
      `SELECT v.id, v.channel_id AS channelId, v.title, v.description, v.transcript,
              v.duration_seconds AS durationSeconds
         FROM videos v
         JOIN scores s ON s.video_id = v.id
         JOIN channels c ON c.id = v.channel_id
        WHERE v.format = 'short'
          AND s.decision = 'rejected'
          AND s.model_used <> ?
          -- NUR aktuell abonnierte Kanäle: der Bestand enthält über tausend
          -- Shorts längst entfernter Kanäle, die zu Recht abgelehnt sind —
          -- die dürfen den Scorer nicht blockieren.
          AND c.is_active = 1
        -- Abonnierte Kanäle zuerst: die hat der Nutzer bewusst gewählt,
        -- ihre Shorts sind der wertvollste Rückstand.
        ORDER BY CASE WHEN COALESCE(v.source, 'subscription') = 'subscription' THEN 0 ELSE 1 END,
                 v.published_at DESC
        LIMIT ?`,
    )
    .all(RESCORE_MARKER, limit) as {
    id: string;
    channelId: string;
    title: string;
    description: string | null;
    transcript: string | null;
    durationSeconds: number;
  }[];

  let approvedCount = 0;
  for (const v of candidates) {
    const filter = getFilterForChannel(db, v.channelId);
    const systemPrompt = `${getActivePromptForChannel(db, v.channelId)}${SHORTS_PROMPT_HINT}`;
    let scored: Awaited<ReturnType<Scorer["score"]>>;
    try {
      scored = await scorer.score({
        title: v.title,
        description: v.description ?? "",
        transcript: v.transcript,
        durationSeconds: v.durationSeconds,
        systemPrompt,
      });
    } catch {
      // Scorer nicht erreichbar — nächster Lauf versucht es erneut.
      break;
    }
    const decision = decide(scored.score, thresholdsForFilter(filter));
    const at = now();

    db.transaction(() => {
      db.prepare(
        `UPDATE scores SET overall_score = ?, category = ?, clickbait_risk = ?,
           educational_value = ?, emotional_manipulation = ?, reasoning = ?,
           model_used = ?, scored_at = ?, decision = ?
         WHERE video_id = ?`,
      ).run(
        scored.score.overallScore,
        scored.score.category,
        scored.score.clickbaitRisk,
        scored.score.educationalValue,
        scored.score.emotionalManipulation,
        scored.score.reasoning,
        RESCORE_MARKER,
        at,
        decision,
        v.id,
      );
      if (decision === "approved") {
        db.prepare(
          "INSERT OR IGNORE INTO feed_items (video_id, added_to_feed_at, is_pre_clipper) VALUES (?, ?, 1)",
        ).run(v.id, at);
      }
    })();

    if (decision === "approved") approvedCount++;
  }
  return approvedCount;
}

/**
 * Bewertet freigegebene, noch ungesehene Feed-Videos neu, deren Bewertung
 * ÄLTER ist als die letzte Änderung der Vorgaben. Was den neuen Regeln nicht
 * mehr genügt, verschwindet aus dem Feed (bleibt aber als bewertetes Video
 * erhalten). So wirkt eine Filteränderung nicht nur auf Neuzugänge, sondern
 * räumt auch den Bestand auf — in kleinen Portionen, damit der Scorer
 * nebenher frische Videos schafft.
 *
 * Gibt zurück, wie viele Videos dabei aus dem Feed geflogen sind.
 */
export async function rescoreOutdatedFeedItems(deps: RescoreDeps): Promise<number> {
  const { db, scorer } = deps;
  const now = deps.now ?? Date.now;
  const limit = deps.limit ?? 10;

  const filterChangedAt = (
    db.prepare("SELECT COALESCE(updated_at, 0) AS t FROM filter_config WHERE id = 1").get() as
      | { t: number }
      | undefined
  )?.t;
  if (!filterChangedAt) return 0;

  const candidates = db
    .prepare(
      `SELECT v.id, v.channel_id AS channelId, v.title, v.description, v.transcript,
              v.duration_seconds AS durationSeconds, v.format
         FROM feed_items f
         JOIN videos v ON v.id = f.video_id
         JOIN scores s ON s.video_id = v.id
        WHERE f.seen_at IS NULL AND f.playback_failed = 0
          AND s.decision = 'approved'
          AND s.scored_at < ?
        ORDER BY s.scored_at ASC
        LIMIT ?`,
    )
    .all(filterChangedAt, limit) as {
    id: string;
    channelId: string;
    title: string;
    description: string | null;
    transcript: string | null;
    durationSeconds: number;
    format: string | null;
  }[];

  let removed = 0;
  for (const v of candidates) {
    const filter = getFilterForChannel(db, v.channelId);
    const base = getActivePromptForChannel(db, v.channelId);
    const systemPrompt = v.format === "short" ? `${base}${SHORTS_PROMPT_HINT}` : base;
    let scored: Awaited<ReturnType<Scorer["score"]>>;
    try {
      scored = await scorer.score({
        title: v.title,
        description: v.description ?? "",
        transcript: v.transcript,
        durationSeconds: v.durationSeconds,
        systemPrompt,
      });
    } catch {
      break; // Scorer nicht erreichbar — später weiter
    }
    const decision = decide(scored.score, thresholdsForFilter(filter));
    const at = now();

    db.transaction(() => {
      db.prepare(
        `UPDATE scores SET overall_score = ?, category = ?, clickbait_risk = ?,
           educational_value = ?, emotional_manipulation = ?, reasoning = ?,
           model_used = ?, scored_at = ?, decision = ?
         WHERE video_id = ?`,
      ).run(
        scored.score.overallScore,
        scored.score.category,
        scored.score.clickbaitRisk,
        scored.score.educationalValue,
        scored.score.emotionalManipulation,
        scored.score.reasoning,
        scored.modelUsed,
        at,
        decision,
        v.id,
      );
      if (decision === "rejected") {
        // Aus dem Feed nehmen — das Video bleibt bewertet, taucht aber nicht
        // mehr auf und wird auch nicht erneut geprüft.
        db.prepare("DELETE FROM daily_mix_items WHERE video_id = ?").run(v.id);
        db.prepare("DELETE FROM feed_items WHERE video_id = ?").run(v.id);
      }
    })();

    if (decision === "rejected") removed++;
  }
  return removed;
}

/** Wie viele Feed-Videos warten auf eine Neubewertung nach Vorgaben-Änderung? */
export function outdatedFeedItemCount(db: Database.Database): number {
  const row = db
    .prepare(
      `SELECT COUNT(*) AS c
         FROM feed_items f
         JOIN scores s ON s.video_id = f.video_id
        WHERE f.seen_at IS NULL AND f.playback_failed = 0 AND s.decision = 'approved'
          AND s.scored_at < (SELECT COALESCE(updated_at, 0) FROM filter_config WHERE id = 1)`,
    )
    .get() as { c: number };
  return row.c;
}
