import { describe, expect, it } from "vitest";
import { pickSubscriptionMix } from "./library-picks.js";

function pool(n: number, channels: string[] = ["c1"], prefix = "v") {
  return Array.from({ length: n }, (_, i) => ({
    id: `${prefix}${i}`,
    channelId: channels[i % channels.length] as string,
  }));
}

const DAY_A = "2026-10-13";
const DAY_B = "2026-10-14";

describe("pickSubscriptionMix", () => {
  it("gleiche Auswahl am selben Tag, andere am nächsten", () => {
    const p = pool(90, ["a", "b", "c"]);
    expect(pickSubscriptionMix(p, DAY_A)).toEqual(pickSubscriptionMix(p, DAY_A));
    const a = pickSubscriptionMix(p, DAY_A).map((r) => r.id);
    const b = pickSubscriptionMix(p, DAY_B).map((r) => r.id);
    expect(b).not.toEqual(a);
  });

  it("kürzt auf 15 Einträge", () => {
    const p = pool(90, ["a", "b", "c"]);
    expect(pickSubscriptionMix(p, DAY_A)).toHaveLength(15);
  });

  it("mischt über die Kanäle — kein Kanal dominiert", () => {
    const p = pool(90, ["a", "b", "c"]);
    const picks = pickSubscriptionMix(p, DAY_A);
    const counts = new Map<string, number>();
    for (const pick of picks) counts.set(pick.channelId, (counts.get(pick.channelId) ?? 0) + 1);
    // Round-Robin über 3 gleich große Kanäle: jeder bekommt genau 5.
    expect(counts.size).toBe(3);
    for (const n of counts.values()) expect(n).toBe(5);
    // Kein Kanal steht zweimal direkt hintereinander.
    for (let i = 1; i < picks.length; i++) {
      expect(picks[i]?.channelId).not.toBe(picks[i - 1]?.channelId);
    }
  });

  it("auch ein dominanter Kanal verdrängt die kleinen nicht", () => {
    // 30 von Kanal "big", je 3 von zwei kleinen — die kleinen müssen vorn dabei sein.
    const p = [
      ...pool(30, ["big"], "big"),
      ...pool(3, ["smallA"], "sa"),
      ...pool(3, ["smallB"], "sb"),
    ];
    const picks = pickSubscriptionMix(p, DAY_A);
    const counts = new Map<string, number>();
    for (const pick of picks) counts.set(pick.channelId, (counts.get(pick.channelId) ?? 0) + 1);
    expect(counts.get("smallA")).toBe(3);
    expect(counts.get("smallB")).toBe(3);
  });

  it("verschiedene Tage drehen den Start-Kanal", () => {
    const p = pool(60, ["a", "b", "c"]);
    const firstChannels = new Set(
      [DAY_A, DAY_B, "2026-10-15", "2026-10-16", "2026-10-17"].map(
        (d) => pickSubscriptionMix(p, d)[0]?.channelId,
      ),
    );
    // Der Tages-Offset rotiert den Start — über eine Woche bleibt es nicht derselbe.
    expect(firstChannels.size).toBeGreaterThan(1);
  });
});
