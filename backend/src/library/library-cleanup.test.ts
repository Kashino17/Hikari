import { existsSync, mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import Database from "better-sqlite3";
import Fastify from "fastify";
import { describe, expect, it } from "vitest";
import { registerFeedRoutes } from "../api/feed.js";
import { registerVideosRoutes } from "../api/videos.js";
import { applyMigrations } from "../db/migrations.js";

function freshDb(): Database.Database {
  const db = new Database(":memory:");
  db.pragma("foreign_keys = ON");
  applyMigrations(db);
  db.prepare(
    "INSERT INTO channels (id,url,title,added_at) VALUES ('manual','x','Manuell',0)",
  ).run();
  db.prepare("INSERT INTO channels (id,url,title,added_at) VALUES ('UC1','y','Kanal',0)").run();
  return db;
}

function addVideo(
  db: Database.Database,
  id: string,
  opts: { channel?: string; series?: string; duration?: number; file?: string } = {},
): void {
  db.prepare(
    `INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at, series_id, format)
     VALUES (?, ?, ?, 0, ?, 0, ?, 'long')`,
  ).run(id, opts.channel ?? "manual", `Titel ${id}`, opts.duration ?? 3000, opts.series ?? null);
  if (opts.file) {
    db.prepare(
      "INSERT INTO downloaded_videos (video_id, file_path, file_size_bytes, downloaded_at) VALUES (?, ?, 10, 0)",
    ).run(id, opts.file);
  }
}

async function buildApp(db: Database.Database) {
  const dir = mkdtempSync(join(tmpdir(), "hikari-cleanup-"));
  const app = Fastify();
  await registerFeedRoutes(app, { db, dailyBudget: 10 });
  await registerVideosRoutes(app, { db, videoDir: dir, coverDir: dir, extractor: null });
  return { app, dir };
}

describe("Video vom PC löschen", () => {
  it("löscht auch Videos, die auf „Später ansehen“ und im Tagesmix stehen", async () => {
    const db = freshDb();
    const { app, dir } = await buildApp(db);
    const file = join(dir, "v1.mp4");
    writeFileSync(file, "x");
    addVideo(db, "v1", { channel: "UC1", file });
    db.prepare("INSERT INTO watch_later (video_id, added_at) VALUES ('v1', 0)").run();
    db.prepare(
      "INSERT INTO daily_mix_items (mix_date, video_id, position, duration_seconds) VALUES ('2026-11-07','v1',0,10)",
    ).run();

    const res = await app.inject({ method: "DELETE", url: "/feed/v1" });
    expect(res.statusCode).toBe(204);
    expect(db.prepare("SELECT COUNT(*) AS n FROM videos").get()).toEqual({ n: 0 });
    expect(existsSync(file)).toBe(false);
  });

  it("DELETE /series/:id entfernt alle Folgen, ihre Dateien und die Serie", async () => {
    const db = freshDb();
    const { app, dir } = await buildApp(db);
    db.prepare("INSERT INTO series (id,title,added_at) VALUES ('s1','Serie',0)").run();
    const files = ["a", "b"].map((n) => {
      const f = join(dir, `${n}.mp4`);
      writeFileSync(f, "x");
      addVideo(db, n, { series: "s1", file: f });
      return f;
    });
    addVideo(db, "andere");

    const res = await app.inject({ method: "DELETE", url: "/series/s1" });
    expect(res.json()).toEqual({ removedEpisodes: 2 });
    expect(db.prepare("SELECT id FROM videos").all()).toEqual([{ id: "andere" }]);
    expect(db.prepare("SELECT COUNT(*) AS n FROM series").get()).toEqual({ n: 0 });
    for (const f of files) expect(existsSync(f)).toBe(false);

    const missing = await app.inject({ method: "DELETE", url: "/series/s1" });
    expect(missing.statusCode).toBe(404);
  });
});

describe("Verlauf", () => {
  it("wird beim Fortschritt fortgeschrieben, ab 10 s, und lässt sich leeren", async () => {
    const db = freshDb();
    const { app } = await buildApp(db);
    addVideo(db, "kurz", { channel: "UC1" });
    addVideo(db, "lang", { channel: "UC1" });

    await app.inject({ method: "PUT", url: "/feed/kurz/progress", payload: { seconds: 3 } });
    await app.inject({ method: "PUT", url: "/feed/lang/progress", payload: { seconds: 600 } });

    const lib = (await app.inject({ method: "GET", url: "/library" })).json() as {
      watchHistory: { id: string }[];
      continueWatching: { id: string }[];
    };
    expect(lib.watchHistory.map((v) => v.id)).toEqual(["lang"]);
    // Gestreamtes YouTube-Video ohne Serverdatei landet trotzdem in Weiterschauen.
    expect(lib.continueWatching.map((v) => v.id)).toEqual(["lang"]);

    await app.inject({ method: "DELETE", url: "/history/lang" });
    const after = (await app.inject({ method: "GET", url: "/library" })).json() as {
      watchHistory: unknown[];
      continueWatching: unknown[];
    };
    expect(after.watchHistory).toEqual([]);
    // Fortschritt bleibt — nur der Verlaufseintrag ist weg.
    expect(after.continueWatching).toHaveLength(1);
  });

  it("Migration 2 räumt alte gespeicherte Videos ab", () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    db.prepare("INSERT INTO channels (id,url,title,added_at) VALUES ('UC1','y','Kanal',0)").run();
    addVideo(db, "v1", { channel: "UC1" });
    db.prepare(
      "INSERT INTO feed_items (video_id, added_to_feed_at, saved) VALUES ('v1', 0, 1)",
    ).run();
    db.pragma("user_version = 1");
    applyMigrations(db);
    expect(db.prepare("SELECT saved FROM feed_items").get()).toEqual({ saved: 0 });
  });
});
