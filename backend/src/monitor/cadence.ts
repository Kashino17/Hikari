/**
 * Adaptive per-channel poll cadence.
 *
 * Every channel used to be polled on the same 15-min tick regardless of how
 * often it uploads — wasteful yt-dlp/RSS load on dormant channels. These pure
 * helpers derive a per-channel interval from its recent upload rhythm so a
 * daily uploader is checked a few times a day while a frequent one is still
 * caught promptly. The cron keeps firing every 15 min; it just skips channels
 * that aren't due yet.
 */

export const MIN_POLL_INTERVAL_MS = 15 * 60 * 1000; // floor — poll at most this often
export const MAX_POLL_INTERVAL_MS = 6 * 60 * 60 * 1000; // ceiling — at least 4×/day
// We poll this many times faster than the upload cadence, so a new upload is
// usually caught within interval ≈ gap/POLL_RATE of going live.
const POLL_RATE = 4;
// A channel with no upload in this long is treated as dormant → max interval.
const DORMANT_AFTER_MS = 30 * 24 * 60 * 60 * 1000;

function clampInterval(ms: number): number {
  if (!Number.isFinite(ms)) return MIN_POLL_INTERVAL_MS;
  return Math.min(MAX_POLL_INTERVAL_MS, Math.max(MIN_POLL_INTERVAL_MS, ms));
}

function median(values: number[]): number {
  const sorted = [...values].sort((a, b) => a - b);
  const mid = Math.floor(sorted.length / 2);
  return sorted.length % 2 === 0 ? (sorted[mid - 1]! + sorted[mid]!) / 2 : sorted[mid]!;
}

/**
 * Derive a poll interval from a channel's recent `published_at` timestamps
 * (any order). Fewer than 2 uploads → MIN (poll often; new/unknown channel).
 * A channel whose newest upload is older than DORMANT_AFTER_MS → MAX.
 * Otherwise: median inter-upload gap / POLL_RATE, clamped to [MIN, MAX].
 */
export function computePollIntervalMs(publishedAt: number[], now: number): number {
  const ts = publishedAt.filter((t) => Number.isFinite(t)).sort((a, b) => b - a); // newest first
  if (ts.length < 2) return MIN_POLL_INTERVAL_MS;

  const newest = ts[0]!;
  if (now - newest > DORMANT_AFTER_MS) return MAX_POLL_INTERVAL_MS;

  const gaps: number[] = [];
  for (let i = 0; i < ts.length - 1; i++) gaps.push(ts[i]! - ts[i + 1]!);
  return clampInterval(median(gaps) / POLL_RATE);
}

/**
 * Whether a channel is due for a poll. Never-polled (null) is always due.
 * `now - lastPolledAt >= intervalMs`.
 */
export function isChannelDue(
  lastPolledAt: number | null,
  intervalMs: number,
  now: number,
): boolean {
  if (lastPolledAt == null) return true;
  return now - lastPolledAt >= intervalMs;
}
