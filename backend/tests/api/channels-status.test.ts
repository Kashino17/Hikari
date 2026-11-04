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

  it("subscribe macht aus einem bekannten Kanal ein Abo", async () => {
    const res = await (await app()).inject({ method: "POST", url: "/channels/UC-p/subscribe" });
    expect(res.statusCode).toBe(204);
    expect(db.prepare("SELECT is_active, status FROM channels WHERE id='UC-p'").get()).toEqual({
      is_active: 1,
      status: "subscribed",
    });
  });

  it("unbekannter Kanal ⇒ 404", async () => {
    const res = await (await app()).inject({ method: "POST", url: "/channels/UC-nix/subscribe" });
    expect(res.statusCode).toBe(404);
  });
});
