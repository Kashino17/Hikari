import Database from "better-sqlite3";
import Fastify from "fastify";
import { beforeEach, describe, expect, it } from "vitest";
import { registerWatchLaterRoutes } from "../../src/api/watch-later.js";
import { applyMigrations } from "../../src/db/migrations.js";

function seedVideo(db: Database.Database, id: string) {
  db.prepare("INSERT OR IGNORE INTO channels (id,url,title,added_at) VALUES ('c1','x','C',0)").run();
  db.prepare(
    `INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at, format)
     VALUES (?, 'c1', ?, 0, 600, 0, 'long')`,
  ).run(id, `t-${id}`);
  db.prepare(
    `INSERT INTO scores (video_id, overall_score, category, clickbait_risk, educational_value,
      emotional_manipulation, reasoning, model_used, scored_at, decision)
     VALUES (?, 80, 'x', 1, 9, 0, 'ok', 'mock', 0, 'approved')`,
  ).run(id);
  db.prepare(
    "INSERT INTO feed_items (video_id, added_to_feed_at, is_pre_clipper) VALUES (?, 0, 1)",
  ).run(id);
}

describe("watch-later API", () => {
  let db: Database.Database;
  beforeEach(() => {
    db = new Database(":memory:");
    applyMigrations(db);
  });
  async function app() {
    const a = Fastify();
    await registerWatchLaterRoutes(a, { db });
    return a;
  }

  it("POST + GET: hydratisierte Items, neueste zuerst", async () => {
    seedVideo(db, "w1");
    seedVideo(db, "w2");
    const a = await app();
    expect((await a.inject({ method: "POST", url: "/watch-later/w1" })).statusCode).toBe(204);
    expect((await a.inject({ method: "POST", url: "/watch-later/w2" })).statusCode).toBe(204);
    // w2 zuletzt hinzugefügt — added_at gleich? Date.now() kann kollidieren:
    db.prepare("UPDATE watch_later SET added_at = 1 WHERE video_id = 'w1'").run();
    db.prepare("UPDATE watch_later SET added_at = 2 WHERE video_id = 'w2'").run();
    const body = (await a.inject({ method: "GET", url: "/watch-later" })).json() as {
      videoId: string;
      kind: string;
    }[];
    expect(body.map((x) => x.videoId)).toEqual(["w2", "w1"]);
    expect(body[0]?.kind).toBe("video");
  });

  it("POST unbekanntes Video ⇒ 404; DELETE ist idempotent", async () => {
    const a = await app();
    expect((await a.inject({ method: "POST", url: "/watch-later/nix" })).statusCode).toBe(404);
    seedVideo(db, "w1");
    await a.inject({ method: "POST", url: "/watch-later/w1" });
    expect((await a.inject({ method: "DELETE", url: "/watch-later/w1" })).statusCode).toBe(204);
    expect((await a.inject({ method: "DELETE", url: "/watch-later/w1" })).statusCode).toBe(204);
    expect(
      ((await a.inject({ method: "GET", url: "/watch-later" })).json() as unknown[]).length,
    ).toBe(0);
  });
});
