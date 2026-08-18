import Database from "better-sqlite3";
import Fastify from "fastify";
import { beforeEach, describe, expect, it } from "vitest";
import { registerChannelsRoutes } from "../../src/api/channels.js";
import { applyMigrations } from "../../src/db/migrations.js";

describe("channel status routes", () => {
  let db: Database.Database;
  beforeEach(() => {
    db = new Database(":memory:");
    applyMigrations(db);
    db.prepare(
      "INSERT INTO channels (id,url,title,added_at,is_active,status) VALUES ('UC-p','x','P',0,0,'probe')",
    ).run();
  });
  async function app() {
    const a = Fastify();
    await registerChannelsRoutes(a, { db });
    return a;
  }

  it("subscribe macht aus Probe ein Abo", async () => {
    const res = await (await app()).inject({ method: "POST", url: "/channels/UC-p/subscribe" });
    expect(res.statusCode).toBe(204);
    expect(db.prepare("SELECT is_active, status FROM channels WHERE id='UC-p'").get()).toEqual({
      is_active: 1,
      status: "subscribed",
    });
  });

  it("block deaktiviert den Kanal und räumt seine ungesehenen Feed-Items weg", async () => {
    db.prepare(
      "INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at) VALUES ('v1','UC-p','t',0,60,0)",
    ).run();
    db.prepare(
      "INSERT INTO feed_items (video_id, added_to_feed_at, is_pre_clipper) VALUES ('v1', 0, 1)",
    ).run();
    const res = await (await app()).inject({ method: "POST", url: "/channels/UC-p/block" });
    expect(res.statusCode).toBe(204);
    expect(db.prepare("SELECT status, is_active FROM channels WHERE id='UC-p'").get()).toEqual({
      status: "blocked",
      is_active: 0,
    });
    const fi = db.prepare("SELECT seen_at FROM feed_items WHERE video_id='v1'").get() as {
      seen_at: number | null;
    };
    expect(fi.seen_at).toBeGreaterThan(0);
  });

  it("unbekannter Kanal ⇒ 404", async () => {
    const res = await (await app()).inject({ method: "POST", url: "/channels/UC-nix/subscribe" });
    expect(res.statusCode).toBe(404);
  });
});
