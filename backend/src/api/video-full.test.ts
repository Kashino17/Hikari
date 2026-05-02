import Database from "better-sqlite3";
import Fastify from "fastify";
import { describe, expect, it } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import { registerVideoFullRoute } from "./video-full.js";

function setup() {
  const db = new Database(":memory:");
  applyMigrations(db);
  db.prepare("INSERT INTO channels (id,url,title,added_at) VALUES ('c1','x','Ch',0)").run();
  db.prepare(`
    INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at)
    VALUES ('v1', 'c1', 'My Vid', 0, 1234, 0)
  `).run();
  db.prepare(`
    INSERT INTO downloaded_videos (video_id, file_path, file_size_bytes, downloaded_at)
    VALUES ('v1', '/orig/v1.mp4', 100000, 0)
  `).run();
  const app = Fastify();
  registerVideoFullRoute(app, db);
  return { app, db };
}

describe("GET /videos/:id/full", () => {
  it("returns full-video info for known id", async () => {
    const { app } = setup();
    const res = await app.inject({ method: "GET", url: "/videos/v1/full" });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toMatchObject({
      durationSec: 1234,
      title: "My Vid",
      channelTitle: "Ch",
      fileUrl: expect.stringContaining("/media/originals/"),
    });
    await app.close();
  });

  it("returns 404 for unknown id", async () => {
    const { app } = setup();
    const res = await app.inject({ method: "GET", url: "/videos/UNKNOWN/full" });
    expect(res.statusCode).toBe(404);
    await app.close();
  });
});
