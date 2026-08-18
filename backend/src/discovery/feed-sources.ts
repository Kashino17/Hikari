import type Database from "better-sqlite3";
import { itChannelShorts, itChannelVideos, itVideoSearch } from "../api/music-innertube.js";
import { enqueueIngest } from "../ingest/queue.js";
import { recommendChannels } from "../monitor/recommendations.js";
import { getFilterState } from "../scorer/filter-repo.js";

// Drosseln: Discovery erweitert die Kandidatenmenge, darf den Feed aber nicht
// fluten — der Scorer bleibt der Türsteher, die Mengen bleiben überschaubar.
const PROBE_POOL_MAX = 15; // gleichzeitig geführte Probe-Kanäle
const PROBE_PER_CYCLE = 8; // neue Videos je Probe-Kanal je Lauf
const TOPIC_PER_TAG = 5; // neue Langvideos je likeTag je Lauf
const TOPIC_SHORTS_PER_TAG = 20; // neue Kurzvideos je likeTag je Lauf
const BACKFILL_MAX = 40; // Backfill-Videos gesamt je Lauf
// Bis zu diesem Vorrat an ungesehenen Videos wird nachgefüllt.
const BACKFILL_UNTIL_UNSEEN = 120;

/** Pseudo-Kanal für Themen-Treffer — die Suche liefert nur Video-IDs, keinen Kanal. */
const TOPICS_CHANNEL_ID = "discovery-topics";

export interface FeedSourceDeps {
  recommend?: typeof recommendChannels;
  channelVideos?: typeof itChannelVideos;
  channelShorts?: typeof itChannelShorts;
  videoSearch?: typeof itVideoSearch;
}

/**
 * Ein Discovery-Lauf (Cron): Probe-Kanäle auffüllen und anzapfen, Themen
 * durchsuchen, bei Bedarf Abo-Backfill — alles nur ENQUEUE, die Pipeline
 * (Metadaten → Transkript → Scorer) entscheidet wie immer. Jede Phase ist
 * best-effort: ein Innertube-Schluckauf bricht nie den ganzen Lauf.
 */
export async function runDiscoveryCycle(
  db: Database.Database,
  deps: FeedSourceDeps = {},
): Promise<void> {
  const recommend = deps.recommend ?? recommendChannels;
  const channelVideos = deps.channelVideos ?? itChannelVideos;
  const channelShorts = deps.channelShorts ?? itChannelShorts;
  const videoSearch = deps.videoSearch ?? itVideoSearch;

  const isKnownVideo = db.prepare("SELECT 1 FROM videos WHERE id = ?");

  // --- 1) Probe-Pool auffüllen: Empfehlungs-Kandidaten als Probe-Kanäle ---
  try {
    const probeCount = (
      db.prepare("SELECT COUNT(*) AS c FROM channels WHERE status = 'probe'").get() as {
        c: number;
      }
    ).c;
    if (probeCount < PROBE_POOL_MAX) {
      const candidates = await recommend(db);
      const insert = db.prepare(
        `INSERT INTO channels (id, url, title, added_at, is_active, status, thumbnail_url, subscribers)
         VALUES (?, ?, ?, ?, 0, 'probe', ?, ?)`,
      );
      let free = PROBE_POOL_MAX - probeCount;
      for (const c of candidates) {
        if (free <= 0) break;
        const exists = db.prepare("SELECT 1 FROM channels WHERE id = ?").get(c.channelId);
        if (exists) continue; // subscribed/blocked/probe — nie überschreiben
        insert.run(c.channelId, c.channelUrl, c.title, Date.now(), c.thumbnail, c.subscribers);
        free--;
      }
    }
  } catch {
    // Empfehlungen sind best-effort
  }

  // --- 2) Probe-Kanäle anzapfen: Shorts zuerst, dann Videos, gedrosselt ---
  const probes = db
    .prepare("SELECT id FROM channels WHERE status = 'probe' AND id != ?")
    .all(TOPICS_CHANNEL_ID) as { id: string }[];
  for (const probe of probes) {
    try {
      const shortIds = (await channelShorts(fetch, probe.id)) ?? [];
      const videoIds = ((await channelVideos(fetch, probe.id)) ?? []).map((t) => t.videoId);
      let quota = PROBE_PER_CYCLE;
      for (const id of [...shortIds, ...videoIds]) {
        if (quota <= 0) break;
        if (isKnownVideo.get(id)) continue;
        enqueueIngest(db, id, probe.id, "probe");
        quota--;
      }
    } catch {
      // nächster Probe-Kanal
    }
  }

  // --- 3) Themen-Suche über die likeTags aus dem Tuning ---
  try {
    const tags = getFilterState(db).filter.likeTags;
    if (tags.length > 0) {
      db.prepare(
        `INSERT OR IGNORE INTO channels (id, url, title, added_at, is_active, status)
         VALUES (?, '', 'Themen-Entdeckung', ?, 0, 'probe')`,
      ).run(TOPICS_CHANNEL_ID, Date.now());
      for (const tag of tags) {
        // Kurzform zuerst: der Feed lebt von Shorts, Langvideos sind die Würze.
        const shortIds = (await videoSearch(fetch, tag, 25, true)) ?? [];
        const longIds = (await videoSearch(fetch, tag, 10)) ?? [];
        let shortQuota = TOPIC_SHORTS_PER_TAG;
        let longQuota = TOPIC_PER_TAG;
        for (const id of shortIds) {
          if (shortQuota <= 0) break;
          if (isKnownVideo.get(id)) continue;
          // Der echte Uploader-Kanal ist aus der Suche nicht bekannt — die
          // Items laufen unter dem Pseudo-Kanal (Anzeige: "Themen-Entdeckung").
          enqueueIngest(db, id, TOPICS_CHANNEL_ID, "topic");
          shortQuota--;
        }
        for (const id of longIds) {
          if (longQuota <= 0) break;
          if (isKnownVideo.get(id)) continue;
          enqueueIngest(db, id, TOPICS_CHANNEL_ID, "topic");
          longQuota--;
        }
      }
    }
  } catch {
    // Themen sind best-effort
  }

  // --- 4) Backfill: die günstigste Quelle (bekannte Kanäle, kein Fremdrisiko).
  // Läuft immer, solange der Vorrat nicht üppig ist — der Feed soll nie leerlaufen.
  try {
    const unseen = (
      db
        .prepare(
          "SELECT COUNT(*) AS c FROM feed_items WHERE seen_at IS NULL AND playback_failed = 0 AND is_pre_clipper = 1",
        )
        .get() as { c: number }
    ).c;
    if (unseen < BACKFILL_UNTIL_UNSEEN) {
      const subscribed = db.prepare("SELECT id FROM channels WHERE is_active = 1").all() as {
        id: string;
      }[];
      let quota = BACKFILL_MAX;
      for (const ch of subscribed) {
        if (quota <= 0) break;
        // Shorts zuerst: abonnierte Kanäle sind die beste Kurzform-Quelle.
        const shortIds = (await channelShorts(fetch, ch.id)) ?? [];
        const videoIds = ((await channelVideos(fetch, ch.id)) ?? []).map((t) => t.videoId);
        for (const id of [...shortIds, ...videoIds]) {
          if (quota <= 0) break;
          if (isKnownVideo.get(id)) continue;
          enqueueIngest(db, id, ch.id, "backfill");
          quota--;
        }
      }
    }
  } catch {
    // Backfill ist best-effort
  }
}
