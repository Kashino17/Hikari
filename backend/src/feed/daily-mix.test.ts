import Database from "better-sqlite3";
import { beforeEach, describe, expect, it } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import {
  buildDailyMix,
  getTimeBudgetMinutes,
  mixDateFor,
  setTimeBudgetMinutes,
  todayMixStats,
} from "./daily-mix.js";

const NOW = new Date("2026-08-18T12:00:00").getTime();

function seed(
  db: Database.Database,
  id: string,
  opts: { dur?: number; format?: string; source?: string; seen?: boolean } = {},
) {
  db.prepare(
    "INSERT OR IGNORE INTO channels (id,url,title,added_at) VALUES ('c1','x','C',0)",
  ).run();
  db.prepare(
    `INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at, format, source)
     VALUES (?, 'c1', ?, 0, ?, 0, ?, ?)`,
  ).run(id, `t-${id}`, opts.dur ?? 60, opts.format ?? "short", opts.source ?? "subscription");
  db.prepare(
    `INSERT INTO scores (video_id, overall_score, category, clickbait_risk, educational_value,
      emotional_manipulation, reasoning, model_used, scored_at, decision)
     VALUES (?, 80, 'x', 1, 9, 0, 'ok', 'mock', 0, 'approved')`,
  ).run(id);
  db.prepare(
    "INSERT INTO feed_items (video_id, added_to_feed_at, seen_at, is_pre_clipper) VALUES (?, ?, ?, 1)",
  ).run(id, NOW - 1000, opts.seen ? NOW : null);
}

describe("daily-mix", () => {
  let db: Database.Database;
  beforeEach(() => {
    db = new Database(":memory:");
    applyMigrations(db);
  });

  it("Budget: Default 45, set clamped und persistent", () => {
    expect(getTimeBudgetMinutes(db)).toBe(45);
    expect(setTimeBudgetMinutes(db, 60)).toBe(60);
    expect(getTimeBudgetMinutes(db)).toBe(60);
    expect(setTimeBudgetMinutes(db, 5)).toBe(10); // clamp unten
    expect(setTimeBudgetMinutes(db, 999)).toBe(240); // clamp oben
  });

  it("füllt bis zum Budget (letztes Item darf überziehen) und ist idempotent", () => {
    setTimeBudgetMinutes(db, 10); // 600s
    for (let i = 0; i < 5; i++) seed(db, `v${i}`, { dur: 240, format: "long" });
    buildDailyMix(db, NOW);
    const rows = db
      .prepare("SELECT video_id FROM daily_mix_items WHERE mix_date = ?")
      .all(mixDateFor(NOW));
    // 240+240 = 480 < 600 → drittes kommt noch rein (720 gesamt), viertes nicht
    expect(rows).toHaveLength(3);
    buildDailyMix(db, NOW); // Budget voll — nichts kommt dazu
    expect(db.prepare("SELECT COUNT(*) c FROM daily_mix_items").get()).toEqual({ c: 3 });
  });

  it("Quellen-Priorität: subscription vor probe vor topic", () => {
    setTimeBudgetMinutes(db, 10);
    seed(db, "t-topic", { dur: 60, source: "topic" });
    seed(db, "s-abo", { dur: 60, source: "subscription" });
    seed(db, "p-probe", { dur: 60, source: "probe" });
    buildDailyMix(db, NOW);
    const order = (
      db.prepare("SELECT video_id FROM daily_mix_items ORDER BY position").all() as {
        video_id: string;
      }[]
    ).map((r) => r.video_id);
    expect(order).toEqual(["s-abo", "p-probe", "t-topic"]);
  });

  it("Rhythmus: nach 5 Shorts kommt ein Langvideo", () => {
    setTimeBudgetMinutes(db, 240);
    for (let i = 0; i < 8; i++) seed(db, `sh${i}`, { dur: 30, format: "short" });
    seed(db, "long1", { dur: 600, format: "long" });
    buildDailyMix(db, NOW);
    const order = (
      db.prepare("SELECT video_id FROM daily_mix_items ORDER BY position").all() as {
        video_id: string;
      }[]
    ).map((r) => r.video_id);
    expect(order.indexOf("long1")).toBe(5); // Position 5 = nach 5 Shorts
  });

  it("überspringt Items, die das Budget massiv sprengen, und füllt mit passenden auf", () => {
    setTimeBudgetMinutes(db, 10); // 600s, Toleranz bis 720s
    for (let i = 0; i < 3; i++) seed(db, `sh${i}`, { dur: 60, format: "short" });
    seed(db, "riesig", { dur: 5000, format: "long" }); // 83 min — passt nie
    buildDailyMix(db, NOW);
    const ids = (
      db.prepare("SELECT video_id FROM daily_mix_items ORDER BY position").all() as {
        video_id: string;
      }[]
    ).map((r) => r.video_id);
    expect(ids).toEqual(["sh0", "sh1", "sh2"]);
    expect(ids).not.toContain("riesig");
  });

  it("Garantie: gibt es NUR ein überlanges Video, kommt es trotzdem rein (kein leerer Feed)", () => {
    setTimeBudgetMinutes(db, 10);
    seed(db, "nur-lang", { dur: 5000, format: "long" });
    buildDailyMix(db, NOW);
    expect(db.prepare("SELECT COUNT(*) c FROM daily_mix_items").get()).toEqual({ c: 1 });
  });

  it("todayMixStats: total zählt alles, remaining nur Ungesehenes", () => {
    setTimeBudgetMinutes(db, 10);
    seed(db, "a", { dur: 300, format: "long" });
    seed(db, "b", { dur: 300, format: "long" });
    buildDailyMix(db, NOW);
    db.prepare("UPDATE feed_items SET seen_at = ? WHERE video_id = 'a'").run(NOW);
    const stats = todayMixStats(db, NOW);
    expect(stats.budgetMinutes).toBe(10);
    expect(stats.totalSeconds).toBe(600);
    expect(stats.remainingSeconds).toBe(300);
    expect(stats.unseenCount).toBe(1);
    expect(stats.capped).toBe(true);
  });

  it("gesehene Items zählen weiter gegen das Budget — kein Nachfüllen", () => {
    setTimeBudgetMinutes(db, 10);
    seed(db, "a", { dur: 700, format: "long" });
    buildDailyMix(db, NOW);
    db.prepare("UPDATE feed_items SET seen_at = ? WHERE video_id = 'a'").run(NOW);
    seed(db, "b", { dur: 700, format: "long" });
    buildDailyMix(db, NOW); // Budget (600s) durch 'a' (700s) bereits überzogen
    expect(db.prepare("SELECT COUNT(*) c FROM daily_mix_items").get()).toEqual({ c: 1 });
  });
});
