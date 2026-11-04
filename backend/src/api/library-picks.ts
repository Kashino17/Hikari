import { dailySeed, seededShuffle } from "../feed/daily-seed.js";
import { type RawFeedRow, interleaveByChannel } from "./feed.js";

/** Aus so vielen der neuesten Abo-Langvideos wird pro Tag gewürfelt. */
export const PICKS_POOL_LIMIT = 120;
export const SUBSCRIPTION_PICKS_COUNT = 15;

export interface LibraryPoolRow {
  id: string;
  channelId: string;
}

/**
 * „Von deinen Kanälen": tagesgedreht UND kanalweise gemischt. Erst würfelt der
 * Tages-Seed die Reihenfolge innerhalb der Kanäle, dann verteilt
 * interleaveByChannel per Round-Robin mit tagesgedrehtem Start-Kanal — so
 * dominiert kein Kanal die vorderen Plätze.
 */
export function pickSubscriptionMix<T extends LibraryPoolRow>(
  pool: T[],
  mixDate: string,
  count = SUBSCRIPTION_PICKS_COUNT,
): T[] {
  const seed = dailySeed(mixDate);
  const shuffled = seededShuffle(pool, seed);
  const rows: RawFeedRow[] = shuffled.map((r) => ({
    kind: "video",
    id: r.id,
    channelId: r.channelId,
    category: null,
    addedToFeedAt: 0,
    durationSec: 0,
  }));
  const mixed = interleaveByChannel(rows, seed);
  const byId = new Map(shuffled.map((r) => [r.id, r]));
  return mixed.slice(0, count).map((r) => byId.get(r.id) as T);
}
