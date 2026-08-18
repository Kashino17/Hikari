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

  it("kein Zeitdeckel: langes Material kommt rein, solange Kurzform es trennt", () => {
    setTimeBudgetMinutes(db, 10); // Budget ist nur noch Anzeige
    for (let i = 0; i < 8; i++) seed(db, `sh${i}`, { dur: 45, format: "short" });
    for (let i = 0; i < 2; i++) seed(db, `lang${i}`, { dur: 900, format: "long" });
    buildDailyMix(db, NOW);
    const rows = db
      .prepare("SELECT video_id FROM daily_mix_items WHERE mix_date = ?")
      .all(mixDateFor(NOW));
    expect(rows).toHaveLength(10); // 38 min Material trotz 10-min-Budget
    buildDailyMix(db, NOW); // erneuter Lauf ändert die Menge nicht
    expect(db.prepare("SELECT COUNT(*) c FROM daily_mix_items").get()).toEqual({ c: 10 });
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

  it("beim Neuwürfeln stehen Abos nicht mehr als Block ganz vorn", () => {
    for (let i = 0; i < 3; i++) seed(db, `abo${i}`, { dur: 40, source: "subscription" });
    for (let i = 0; i < 30; i++) seed(db, `thema${i}`, { dur: 40, source: "topic" });
    buildDailyMix(db, NOW, { reshuffle: true, seed: 4242 });
    const erste = (
      db
        .prepare(
          `SELECT v.source AS s FROM daily_mix_items m JOIN videos v ON v.id = m.video_id
            WHERE m.mix_date = ? ORDER BY m.position LIMIT 8`,
        )
        .all(mixDateFor(NOW)) as { s: string }[]
    ).map((r) => r.s);
    // Themen-Funde mischen sich unter die vorderen Plätze …
    expect(erste).toContain("topic");
    // … die Abos landen trotzdem früh, nicht irgendwo hinten.
    const aboPos = (
      db
        .prepare(
          `SELECT MAX(m.position) AS p FROM daily_mix_items m JOIN videos v ON v.id = m.video_id
            WHERE m.mix_date = ? AND v.source = 'subscription'`,
        )
        .get(mixDateFor(NOW)) as { p: number }
    ).p;
    expect(aboPos).toBeLessThan(15);
  });

  it("beim Neuwürfeln kommt eine andere Reihenfolge heraus", () => {
    for (let i = 0; i < 12; i++) seed(db, `sh${i}`, { dur: 40, format: "short" });
    buildDailyMix(db, NOW);
    const ersteRunde = (
      db
        .prepare("SELECT video_id FROM daily_mix_items WHERE mix_date = ? ORDER BY position")
        .all(mixDateFor(NOW)) as { video_id: string }[]
    ).map((r) => r.video_id);

    buildDailyMix(db, NOW, { reshuffle: true, seed: 987654 });
    const zweiteRunde = (
      db
        .prepare("SELECT video_id FROM daily_mix_items WHERE mix_date = ? ORDER BY position")
        .all(mixDateFor(NOW)) as { video_id: string }[]
    ).map((r) => r.video_id);

    expect(zweiteRunde).toHaveLength(ersteRunde.length);
    expect(zweiteRunde.slice().sort()).toEqual(ersteRunde.slice().sort()); // gleiche Menge
    expect(zweiteRunde).not.toEqual(ersteRunde); // andere Reihenfolge
  });

  it("ohne Neuwürfeln bleibt die Reihenfolge stabil", () => {
    for (let i = 0; i < 10; i++) seed(db, `st${i}`, { dur: 40, format: "short" });
    buildDailyMix(db, NOW);
    const a = (
      db
        .prepare("SELECT video_id FROM daily_mix_items WHERE mix_date = ? ORDER BY position")
        .all(mixDateFor(NOW)) as { video_id: string }[]
    ).map((r) => r.video_id);
    buildDailyMix(db, NOW);
    const b = (
      db
        .prepare("SELECT video_id FROM daily_mix_items WHERE mix_date = ? ORDER BY position")
        .all(mixDateFor(NOW)) as { video_id: string }[]
    ).map((r) => r.video_id);
    expect(b).toEqual(a);
  });

  it("mischt Kanäle durch — nie zweimal derselbe Kanal hintereinander", () => {
    db.prepare(
      "INSERT OR IGNORE INTO channels (id,url,title,added_at) VALUES ('cA','x','A',0)",
    ).run();
    db.prepare(
      "INSERT OR IGNORE INTO channels (id,url,title,added_at) VALUES ('cB','x','B',0)",
    ).run();
    // Zwei Kanäle mit je 4 Shorts, blockweise eingespielt.
    for (const ch of ["cA", "cB"]) {
      for (let i = 0; i < 4; i++) {
        const id = `${ch}-${i}`;
        db.prepare(
          `INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at, format, source)
           VALUES (?, ?, ?, 0, 40, 0, 'short', 'subscription')`,
        ).run(id, ch, id);
        db.prepare(
          `INSERT INTO scores (video_id, overall_score, category, clickbait_risk, educational_value,
             emotional_manipulation, reasoning, model_used, scored_at, decision)
           VALUES (?, 80, 'x', 1, 9, 0, 'ok', 'm', 0, 'approved')`,
        ).run(id);
        db.prepare(
          "INSERT INTO feed_items (video_id, added_to_feed_at, is_pre_clipper) VALUES (?, ?, 1)",
        ).run(id, NOW - 1000);
      }
    }
    buildDailyMix(db, NOW);
    const channels = (
      db
        .prepare(
          `SELECT v.channel_id AS ch FROM daily_mix_items m
             JOIN videos v ON v.id = m.video_id
            WHERE m.mix_date = ? ORDER BY m.position`,
        )
        .all(mixDateFor(NOW)) as { ch: string }[]
    ).map((r) => r.ch);
    for (let i = 1; i < channels.length; i++) {
      expect(channels[i]).not.toBe(channels[i - 1]);
    }
  });

  it("nie mehr als zwei Langvideos am Stück", () => {
    for (let i = 0; i < 8; i++) seed(db, `l${i}`, { dur: 900, format: "long" });
    buildDailyMix(db, NOW);
    const formats = (
      db
        .prepare(
          `SELECT v.format AS f FROM daily_mix_items m JOIN videos v ON v.id = m.video_id
            WHERE m.mix_date = ? ORDER BY m.position`,
        )
        .all(mixDateFor(NOW)) as { f: string }[]
    ).map((r) => r.f);
    let run = 0;
    for (const f of formats) {
      run = f === "long" ? run + 1 : 0;
      expect(run).toBeLessThanOrEqual(2);
    }
  });

  it("ordnet später dazugekommene Shorts mit ein, statt sie hinten anzuhängen", () => {
    for (let i = 0; i < 4; i++) seed(db, `alt${i}`, { dur: 900, format: "long" });
    buildDailyMix(db, NOW); // erst nur Langvideos verfügbar
    for (let i = 0; i < 6; i++) seed(db, `neu${i}`, { dur: 40, format: "short" });
    buildDailyMix(db, NOW, { reshuffle: true }); // beim Neuladen wird einsortiert
    const formats = (
      db
        .prepare(
          `SELECT v.format AS f FROM daily_mix_items m JOIN videos v ON v.id = m.video_id
            WHERE m.mix_date = ? ORDER BY m.position LIMIT 3`,
        )
        .all(mixDateFor(NOW)) as { f: string }[]
    ).map((r) => r.f);
    // Vorne im Feed steht jetzt Kurzform, nicht die alte Langvideo-Schlange.
    expect(formats[0]).toBe("short");
  });

  it("ohne Neuwürfeln wird nur hinten ergänzt, nie umsortiert", () => {
    for (let i = 0; i < 5; i++) seed(db, `erst${i}`, { dur: 40, format: "short" });
    buildDailyMix(db, NOW);
    const vorher = (
      db
        .prepare("SELECT video_id FROM daily_mix_items WHERE mix_date = ? ORDER BY position")
        .all(mixDateFor(NOW)) as { video_id: string }[]
    ).map((r) => r.video_id);

    for (let i = 0; i < 5; i++) seed(db, `spaet${i}`, { dur: 40, format: "short" });
    buildDailyMix(db, NOW);
    const nachher = (
      db
        .prepare("SELECT video_id FROM daily_mix_items WHERE mix_date = ? ORDER BY position")
        .all(mixDateFor(NOW)) as { video_id: string }[]
    ).map((r) => r.video_id);

    // Der bestehende Anfang bleibt Zeichen für Zeichen stehen, das Neue folgt.
    expect(nachher.slice(0, vorher.length)).toEqual(vorher);
    expect(nachher.length).toBeGreaterThan(vorher.length);
  });

  it("Rhythmus: nach 6 Shorts kommt ein Langvideo", () => {
    for (let i = 0; i < 8; i++) seed(db, `sh${i}`, { dur: 30, format: "short" });
    seed(db, "long1", { dur: 600, format: "long" });
    buildDailyMix(db, NOW);
    const order = (
      db.prepare("SELECT video_id FROM daily_mix_items ORDER BY position").all() as {
        video_id: string;
      }[]
    ).map((r) => r.video_id);
    expect(order.indexOf("long1")).toBe(6); // Position 6 = nach 6 Shorts
  });

  it("auch lange Videos kommen in den Feed — nichts wird wegen Dauer verworfen", () => {
    setTimeBudgetMinutes(db, 10);
    for (let i = 0; i < 3; i++) seed(db, `sh${i}`, { dur: 60, format: "short" });
    seed(db, "lang", { dur: 5000, format: "long" }); // 83 min
    buildDailyMix(db, NOW);
    const ids = (
      db.prepare("SELECT video_id FROM daily_mix_items ORDER BY position").all() as {
        video_id: string;
      }[]
    ).map((r) => r.video_id);
    expect(ids).toContain("lang");
    expect(ids).toHaveLength(4);
  });

  it("Garantie: gibt es NUR ein überlanges Video, kommt es trotzdem rein (kein leerer Feed)", () => {
    setTimeBudgetMinutes(db, 10);
    seed(db, "nur-lang", { dur: 5000, format: "long" });
    buildDailyMix(db, NOW);
    expect(db.prepare("SELECT COUNT(*) c FROM daily_mix_items").get()).toEqual({ c: 1 });
  });

  it("todayMixStats: Budget-Rest zählt nur tatsächlich Geschautes", () => {
    setTimeBudgetMinutes(db, 10); // 600s
    seed(db, "a", { dur: 300, format: "long" });
    seed(db, "b", { dur: 300, format: "long" });
    buildDailyMix(db, NOW);
    // 'a' wurde 120s geschaut, 'b' liegt unangetastet im Feed.
    db.prepare(
      "UPDATE feed_items SET seen_at = ?, progress_seconds = 120 WHERE video_id = 'a'",
    ).run(NOW);
    const stats = todayMixStats(db, NOW);
    expect(stats.budgetMinutes).toBe(10);
    expect(stats.consumedSeconds).toBe(120);
    expect(stats.remainingSeconds).toBe(480); // 600 - 120
    expect(stats.unseenCount).toBe(1);
    expect(stats.capped).toBe(false);
  });

  it("weggeswipte Karten (gesehen, aber nie abgespielt) blockieren den Nachschub NICHT", () => {
    setTimeBudgetMinutes(db, 10);
    seed(db, "karte", { dur: 700, format: "long" });
    buildDailyMix(db, NOW);
    // Weggeswiped: seen_at gesetzt, aber keine Sekunde abgespielt.
    db.prepare("UPDATE feed_items SET seen_at = ? WHERE video_id = 'karte'").run(NOW);
    seed(db, "neu", { dur: 300, format: "long" });
    buildDailyMix(db, NOW);
    const ids = (
      db.prepare("SELECT video_id FROM daily_mix_items ORDER BY position").all() as {
        video_id: string;
      }[]
    ).map((r) => r.video_id);
    expect(ids).toContain("neu"); // Nachschub kommt
  });

  it("auch nach verbrauchtem Zeitbudget kommt Nachschub — der Feed endet nicht", () => {
    setTimeBudgetMinutes(db, 10); // 600s
    seed(db, "lang", { dur: 700, format: "long" });
    buildDailyMix(db, NOW);
    db.prepare(
      "UPDATE feed_items SET seen_at = ?, progress_seconds = 700 WHERE video_id = 'lang'",
    ).run(NOW);
    seed(db, "neu", { dur: 60, format: "short" });
    buildDailyMix(db, NOW);
    const ids = (
      db.prepare("SELECT video_id FROM daily_mix_items ORDER BY position").all() as {
        video_id: string;
      }[]
    ).map((r) => r.video_id);
    expect(ids).toContain("neu"); // Nachschub trotz überschrittenem Budget
    // Das Budget ist nur noch Information für die Anzeige.
    expect(todayMixStats(db, NOW).capped).toBe(true);
  });
});
