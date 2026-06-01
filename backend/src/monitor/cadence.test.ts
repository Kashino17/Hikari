import { describe, expect, it } from "vitest";
import {
  computePollIntervalMs,
  isChannelDue,
  MIN_POLL_INTERVAL_MS,
  MAX_POLL_INTERVAL_MS,
} from "./cadence.js";

const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;
const now = 1_000_000_000_000;

describe("computePollIntervalMs", () => {
  it("returns the floor for a channel with <2 uploads", () => {
    expect(computePollIntervalMs([], now)).toBe(MIN_POLL_INTERVAL_MS);
    expect(computePollIntervalMs([now - DAY], now)).toBe(MIN_POLL_INTERVAL_MS);
  });

  it("polls a frequent uploader at the floor (gap/4 below MIN)", () => {
    // Uploads ~every 30 min → gap/4 = 7.5min, clamped up to MIN (15min).
    const ts = [now - 30 * 60_000, now - 60 * 60_000, now - 90 * 60_000];
    expect(computePollIntervalMs(ts, now)).toBe(MIN_POLL_INTERVAL_MS);
  });

  it("scales interval to a daily uploader (24h gap → 6h, hits ceiling)", () => {
    const ts = [now - DAY, now - 2 * DAY, now - 3 * DAY];
    // median gap 24h / 4 = 6h = MAX.
    expect(computePollIntervalMs(ts, now)).toBe(MAX_POLL_INTERVAL_MS);
  });

  it("scales for an every-4-hours uploader (gap 4h → 1h)", () => {
    const ts = [now - 4 * HOUR, now - 8 * HOUR, now - 12 * HOUR];
    expect(computePollIntervalMs(ts, now)).toBe(HOUR);
  });

  it("caps a weekly uploader at the ceiling", () => {
    const ts = [now - 7 * DAY, now - 14 * DAY, now - 21 * DAY];
    expect(computePollIntervalMs(ts, now)).toBe(MAX_POLL_INTERVAL_MS);
  });

  it("treats a long-dormant channel as max interval even with frequent past gaps", () => {
    // Frequent uploads, but the newest is 60 days ago → dormant → MAX.
    const base = now - 60 * DAY;
    const ts = [base, base - HOUR, base - 2 * HOUR];
    expect(computePollIntervalMs(ts, now)).toBe(MAX_POLL_INTERVAL_MS);
  });

  it("is order-insensitive", () => {
    const a = computePollIntervalMs([now - 4 * HOUR, now - 8 * HOUR, now - 12 * HOUR], now);
    const b = computePollIntervalMs([now - 12 * HOUR, now - 4 * HOUR, now - 8 * HOUR], now);
    expect(a).toBe(b);
  });
});

describe("isChannelDue", () => {
  it("a never-polled channel is always due", () => {
    expect(isChannelDue(null, MAX_POLL_INTERVAL_MS, now)).toBe(true);
  });

  it("is not due before the interval elapses", () => {
    expect(isChannelDue(now - HOUR, MAX_POLL_INTERVAL_MS, now)).toBe(false);
  });

  it("is due once the interval has elapsed", () => {
    expect(isChannelDue(now - 7 * HOUR, MAX_POLL_INTERVAL_MS, now)).toBe(true);
  });

  it("is due exactly at the boundary", () => {
    expect(isChannelDue(now - MIN_POLL_INTERVAL_MS, MIN_POLL_INTERVAL_MS, now)).toBe(true);
  });
});
