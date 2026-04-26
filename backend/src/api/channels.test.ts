import Database from "better-sqlite3";
import Fastify from "fastify";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import { registerChannelsRoutes } from "./channels.js";

vi.mock("../monitor/channel-resolver.js", () => ({
  resolveChannel: vi.fn(),
}));

describe("channels API", () => {
  let db: Database.Database;

  beforeEach(() => {
    db = new Database(":memory:");
    applyMigrations(db);
    vi.clearAllMocks();
  });

  it("POST /channels resolves URL and inserts row with metadata", async () => {
    const { resolveChannel } = await import("../monitor/channel-resolver.js");
    vi.mocked(resolveChannel).mockResolvedValue({
      channelId: "UC1",
      title: "Test Channel",
      handle: "@test",
      description: "desc",
      subscribers: 12345,
      thumbnail: "https://yt.example/thumb.jpg",
    });

    const app = Fastify();
    await registerChannelsRoutes(app, { db });

    const res = await app.inject({
      method: "POST",
      url: "/channels",
      payload: { channelUrl: "https://www.youtube.com/@test" },
    });

    expect(res.statusCode).toBe(200);
    expect(res.json()).toMatchObject({
      id: "UC1",
      title: "Test Channel",
      handle: "@test",
      thumbnail_url: "https://yt.example/thumb.jpg",
      subscribers: 12345,
    });
    const row = db.prepare(
      "SELECT handle, subscribers, thumbnail_url FROM channels WHERE id='UC1'",
    ).get();
    expect(row).toEqual({ handle: "@test", subscribers: 12345, thumbnail_url: "https://yt.example/thumb.jpg" });
  });

  it("GET /channels lists active channels", async () => {
    db.prepare(
      "INSERT INTO channels (id, url, title, added_at, is_active) VALUES ('UC1','x','Alpha',0,1)",
    ).run();
    db.prepare(
      "INSERT INTO channels (id, url, title, added_at, is_active) VALUES ('UC2','y','Beta',0,1)",
    ).run();

    const app = Fastify();
    await registerChannelsRoutes(app, { db });
    const res = await app.inject({ method: "GET", url: "/channels" });

    expect(res.statusCode).toBe(200);
    const body = res.json() as { id: string; title: string }[];
    expect(body).toHaveLength(2);
    expect(body[0]).toMatchObject({ id: "UC1", title: "Alpha" });
  });

  it("DELETE /channels/:id sets is_active=0", async () => {
    db.prepare(
      "INSERT INTO channels (id, url, title, added_at, is_active) VALUES ('UC1','x','Alpha',0,1)",
    ).run();

    const app = Fastify();
    await registerChannelsRoutes(app, { db });
    const res = await app.inject({ method: "DELETE", url: "/channels/UC1" });

    expect(res.statusCode).toBe(204);
    expect(
      db.prepare("SELECT is_active FROM channels WHERE id='UC1'").get(),
    ).toEqual({ is_active: 0 });
  });
});
