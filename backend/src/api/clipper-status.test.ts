import Database from "better-sqlite3";
import { beforeEach, describe, expect, it } from "vitest";
import Fastify from "fastify";
import { applyMigrations } from "../db/migrations.js";
import { registerClipperStatusRoutes } from "./clipper-status.js";

function makeApp(db: Database.Database) {
  const app = Fastify();
  registerClipperStatusRoutes(app, db, { startHour: 22, endHour: 8 });
  return app;
}

function seedVideo(db: Database.Database, id: string, status: string | null): void {
  db.prepare("INSERT OR IGNORE INTO channels (id,url,title,added_at) VALUES ('c1','x','c',0)").run();
  db.prepare(`
    INSERT INTO videos (id, channel_id, title, published_at, duration_seconds,
                        discovered_at, clip_status)
    VALUES (?, 'c1', ?, 0, 600, 0, ?)
  `).run(id, id, status);
}

describe("GET /clipper/status", () => {
  let db: Database.Database;
  beforeEach(() => {
    db = new Database(":memory:");
    applyMigrations(db);
  });

  it("returns counts grouped by clip_status", async () => {
    seedVideo(db, "v1", "pending");
    seedVideo(db, "v2", "pending");
    seedVideo(db, "v3", "rendering");
    seedVideo(db, "v4", "failed");
    seedVideo(db, "v5", "no_highlights");
    seedVideo(db, "v6", "done");
    db.prepare("INSERT INTO clipper_queue (video_id, queued_at) VALUES ('v1', 0)").run();
    db.prepare("INSERT INTO clipper_queue (video_id, queued_at) VALUES ('v2', 0)").run();

    const app = makeApp(db);
    const res = await app.inject({ method: "GET", url: "/clipper/status" });
    expect(res.statusCode).toBe(200);
    const body = res.json();
    expect(body).toMatchObject({
      pending: 2,
      processing: 1,
      failed: 1,
      no_highlights: 1,
    });
    expect(typeof body.isWindowActive).toBe("boolean");
    await app.close();
  });
});

describe("POST /clipper/retry-failed", () => {
  it("resets failed videos to pending and re-enqueues them", async () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    seedVideo(db, "v1", "failed");
    seedVideo(db, "v2", "failed");
    seedVideo(db, "v3", "done");

    const app = Fastify();
    registerClipperStatusRoutes(app, db, { startHour: 22, endHour: 8 });
    const res = await app.inject({ method: "POST", url: "/clipper/retry-failed" });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual({ retriedCount: 2 });

    const v1 = db.prepare("SELECT clip_status FROM videos WHERE id='v1'").get() as any;
    expect(v1.clip_status).toBe("pending");
    expect(db.prepare("SELECT COUNT(*) c FROM clipper_queue").get())
      .toEqual({ c: 2 });
    await app.close();
  });
});

describe("POST /clipper/force-window", () => {
  it("sets force_until to ~1 hour from now and returns it", async () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    const app = makeApp(db);
    const before = Date.now();
    const res = await app.inject({ method: "POST", url: "/clipper/force-window" });
    const after = Date.now();
    expect(res.statusCode).toBe(200);
    const body = res.json();
    // Should be roughly now + 1h
    expect(body.forceUntil).toBeGreaterThanOrEqual(before + 60 * 60 * 1000 - 1000);
    expect(body.forceUntil).toBeLessThanOrEqual(after + 60 * 60 * 1000 + 1000);

    const row = db.prepare("SELECT force_until FROM clipper_runtime WHERE id = 1").get() as any;
    expect(row.force_until).toBe(body.forceUntil);
    await app.close();
  });
});

describe("GET /clipper/status with force window", () => {
  it("returns isForceActive=true when force_until is in future", async () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    db.prepare("UPDATE clipper_runtime SET force_until = ? WHERE id = 1")
      .run(Date.now() + 60 * 1000);
    const app = makeApp(db);
    const res = await app.inject({ method: "GET", url: "/clipper/status" });
    expect(res.json().isForceActive).toBe(true);
    await app.close();
  });
});
