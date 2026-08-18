import Database from "better-sqlite3";
import { beforeEach, describe, expect, it } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import type { Scorer } from "../scorer/types.js";
import { getFilterState } from "../scorer/filter-repo.js";
import { rescoreLegacyShorts, rescoreOutdatedFeedItems } from "./rescore-shorts.js";

function scorerWith(overall: number, capture?: string[]): Scorer {
  return {
    name: "mock",
    async score(input: { systemPrompt?: string }) {
      if (capture && input.systemPrompt) capture.push(input.systemPrompt);
      return {
        modelUsed: "mock-v1",
        score: {
          overallScore: overall,
          category: "tech",
          clickbaitRisk: 1,
          educationalValue: 8,
          emotionalManipulation: 0,
          reasoning: "neu bewertet",
        },
      };
    },
  };
}

describe("rescoreLegacyShorts", () => {
  let db: Database.Database;
  beforeEach(() => {
    db = new Database(":memory:");
    applyMigrations(db);
    db.prepare(
      "INSERT INTO channels (id,url,title,added_at,is_active) VALUES ('c1','x','C',0,1)",
    ).run();
  });

  function seedRejectedShort(id: string) {
    db.prepare(
      `INSERT INTO videos (id, channel_id, title, description, published_at, duration_seconds,
         discovered_at, format, source)
       VALUES (?, 'c1', ?, 'beschreibung', 0, 45, 0, 'short', 'subscription')`,
    ).run(id, `t-${id}`);
    db.prepare(
      `INSERT INTO scores (video_id, overall_score, category, clickbait_risk, educational_value,
         emotional_manipulation, reasoning, model_used, scored_at, decision)
       VALUES (?, 20, 'other', 0, 0, 0, 'zu kurz', 'alt-modell', 0, 'rejected')`,
    ).run(id);
  }

  it("bewertet abgelehnte Shorts neu und macht Freigegebene im Feed sichtbar", async () => {
    seedRejectedShort("s1");
    const prompts: string[] = [];
    const approved = await rescoreLegacyShorts({
      db,
      scorer: scorerWith(85, prompts),
      limit: 10,
    });
    expect(approved).toBe(1);
    expect(db.prepare("SELECT decision FROM scores WHERE video_id='s1'").get()).toEqual({
      decision: "approved",
    });
    expect(db.prepare("SELECT COUNT(*) c FROM feed_items WHERE video_id='s1'").get()).toEqual({
      c: 1,
    });
    // Der Scorer bekommt den Kurzform-Hinweis — sonst fällt das Short wieder durch.
    expect(prompts[0]).toContain("natives YouTube-Short");
  });

  it("bleibt es abgelehnt, kommt es nicht in den Feed und nicht erneut dran", async () => {
    seedRejectedShort("s2");
    const first = await rescoreLegacyShorts({ db, scorer: scorerWith(10), limit: 10 });
    expect(first).toBe(0);
    expect(db.prepare("SELECT COUNT(*) c FROM feed_items").get()).toEqual({ c: 0 });
    // Zweiter Lauf findet nichts mehr: bereits neu bewertet.
    const second = await rescoreLegacyShorts({ db, scorer: scorerWith(90), limit: 10 });
    expect(second).toBe(0);
  });

  it("fasst Langvideos und bereits freigegebene Shorts nicht an", async () => {
    db.prepare(
      `INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at, format)
       VALUES ('lang','c1','t',0,900,0,'long')`,
    ).run();
    db.prepare(
      `INSERT INTO scores (video_id, overall_score, category, clickbait_risk, educational_value,
         emotional_manipulation, reasoning, model_used, scored_at, decision)
       VALUES ('lang', 20, 'other', 0, 0, 0, 'r', 'alt-modell', 0, 'rejected')`,
    ).run();
    const n = await rescoreLegacyShorts({ db, scorer: scorerWith(90), limit: 10 });
    expect(n).toBe(0);
    expect(db.prepare("SELECT decision FROM scores WHERE video_id='lang'").get()).toEqual({
      decision: "rejected",
    });
  });
});

describe("rescoreOutdatedFeedItems", () => {
  let db: Database.Database;
  beforeEach(() => {
    db = new Database(":memory:");
    applyMigrations(db);
    db.prepare(
      "INSERT INTO channels (id,url,title,added_at,is_active) VALUES ('c1','x','C',0,1)",
    ).run();
  });

  function seedApproved(id: string, scoredAt: number) {
    db.prepare(
      `INSERT INTO videos (id, channel_id, title, description, published_at, duration_seconds,
         discovered_at, format, source)
       VALUES (?, 'c1', ?, 'd', 0, 60, 0, 'short', 'subscription')`,
    ).run(id, `t-${id}`);
    db.prepare(
      `INSERT INTO scores (video_id, overall_score, category, clickbait_risk, educational_value,
         emotional_manipulation, reasoning, model_used, scored_at, decision)
       VALUES (?, 80, 'x', 0, 8, 0, 'ok', 'alt', ?, 'approved')`,
    ).run(id, scoredAt);
    db.prepare(
      "INSERT INTO feed_items (video_id, added_to_feed_at, is_pre_clipper) VALUES (?, 0, 1)",
    ).run(id);
  }

  it("wirft Videos aus dem Feed, die den neuen Vorgaben nicht mehr genügen", async () => {
    seedApproved("alt1", 1000);
    // Filter wurde nach der Bewertung geändert.
    getFilterState(db); // legt die Vorgaben-Zeile an
    db.prepare("UPDATE filter_config SET updated_at = 5000 WHERE id = 1").run();
    const n = await rescoreOutdatedFeedItems({ db, scorer: scorerWith(10), limit: 5 });
    expect(n).toBe(1);
    expect(db.prepare("SELECT COUNT(*) c FROM feed_items WHERE video_id='alt1'").get()).toEqual({
      c: 0,
    });
    expect(db.prepare("SELECT decision FROM scores WHERE video_id='alt1'").get()).toEqual({
      decision: "rejected",
    });
  });

  it("lässt weiterhin passende Videos im Feed und bewertet nichts doppelt", async () => {
    seedApproved("alt2", 1000);
    getFilterState(db);
    db.prepare("UPDATE filter_config SET updated_at = 5000 WHERE id = 1").run();
    await rescoreOutdatedFeedItems({ db, scorer: scorerWith(90), limit: 5 });
    expect(db.prepare("SELECT COUNT(*) c FROM feed_items WHERE video_id='alt2'").get()).toEqual({
      c: 1,
    });
    // Zweiter Lauf: Bewertung ist jetzt jünger als die Filteränderung.
    const zweiter = await rescoreOutdatedFeedItems({ db, scorer: scorerWith(90), limit: 5 });
    expect(zweiter).toBe(0);
  });

  it("fasst bereits gesehene Videos nicht an", async () => {
    seedApproved("gesehen", 1000);
    db.prepare("UPDATE feed_items SET seen_at = 2000 WHERE video_id = 'gesehen'").run();
    getFilterState(db);
    db.prepare("UPDATE filter_config SET updated_at = 5000 WHERE id = 1").run();
    const n = await rescoreOutdatedFeedItems({ db, scorer: scorerWith(10), limit: 5 });
    expect(n).toBe(0);
  });
});
