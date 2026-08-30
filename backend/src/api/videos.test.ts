import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import fastifyStatic from "@fastify/static";
import Fastify from "fastify";
import { describe, expect, it } from "vitest";
import { registerVideosRoutes } from "./videos.js";

describe("videos API", () => {
  it("serves MP4 with Content-Type and supports Range requests", async () => {
    const dir = mkdtempSync(join(tmpdir(), "hikari-videos-"));
    writeFileSync(join(dir, "vid1.mp4"), Buffer.alloc(10000, 0xaa));

    const app = Fastify();
    await app.register(fastifyStatic, { root: dir, prefix: "/videos/" });
    await registerVideosRoutes(app, {
      db: undefined as never,
      videoDir: dir,
      coverDir: dir,
      extractor: null,
    });

    const full = await app.inject({ method: "GET", url: "/videos/vid1.mp4" });
    expect(full.statusCode).toBe(200);
    expect(full.body.length).toBe(10000);

    const ranged = await app.inject({
      method: "GET",
      url: "/videos/vid1.mp4",
      headers: { range: "bytes=0-99" },
    });
    expect(ranged.statusCode).toBe(206);
    expect(ranged.body.length).toBe(100);
  });

  it("GET /library liefert watchLater und history hydratisiert", async () => {
    const Database = (await import("better-sqlite3")).default;
    const { applyMigrations } = await import("../db/migrations.js");
    const db = new Database(":memory:");
    applyMigrations(db);
    const seedVideo = (id: string) => {
      db.prepare(
        "INSERT OR IGNORE INTO channels (id,url,title,added_at) VALUES ('c1','x','C',0)",
      ).run();
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
    };
    seedVideo("wl1");
    seedVideo("seen1");
    db.prepare("INSERT INTO watch_later (video_id, added_at) VALUES ('wl1', 1000)").run();
    db.prepare("UPDATE feed_items SET seen_at = 2000 WHERE video_id = 'seen1'").run();

    const dir = mkdtempSync(join(tmpdir(), "hikari-lib-"));
    const app = Fastify();
    await registerVideosRoutes(app, { db, videoDir: dir, coverDir: dir, extractor: null });
    const body = (await app.inject({ method: "GET", url: "/library" })).json() as {
      watchLater: { videoId: string }[];
      history: { videoId: string }[];
    };
    expect(body.watchLater.map((x) => x.videoId)).toEqual(["wl1"]);
    expect(body.history.map((x) => x.videoId)).toEqual(["seen1"]);
  });

  it("returns 404 for missing file", async () => {
    const dir = mkdtempSync(join(tmpdir(), "hikari-videos-empty-"));
    const app = Fastify();
    await app.register(fastifyStatic, { root: dir, prefix: "/videos/" });
    await registerVideosRoutes(app, {
      db: undefined as never,
      videoDir: dir,
      coverDir: dir,
      extractor: null,
    });

    const res = await app.inject({ method: "GET", url: "/videos/nope.mp4" });
    expect(res.statusCode).toBe(404);
  });

  describe("POST /series/merge", () => {
    async function setup() {
      const Database = (await import("better-sqlite3")).default;
      const { applyMigrations } = await import("../db/migrations.js");
      const db = new Database(":memory:");
      applyMigrations(db);
      db.prepare("INSERT INTO channels (id,url,title,added_at) VALUES ('manual','x','M',0)").run();
      db.prepare(
        "INSERT INTO series (id, title, added_at) VALUES ('solo-leveling', 'Solo Leveling', 1)",
      ).run();
      db.prepare(
        "INSERT INTO series (id, title, added_at, thumbnail_url) VALUES ('solo-leveling-2', 'Solo Leveling!', 2, '/covers/a.jpg')",
      ).run();
      const addVideo = (id: string, seriesId: string, season: number, episode: number) => {
        db.prepare(
          `INSERT INTO videos (id, channel_id, series_id, title, published_at, duration_seconds, discovered_at, season, episode)
           VALUES (?, 'manual', ?, ?, 0, 600, 0, ?, ?)`,
        ).run(id, seriesId, `Folge ${episode}`, season, episode);
        db.prepare(
          "INSERT INTO downloaded_videos (video_id, file_path, file_size_bytes, downloaded_at) VALUES (?, ?, 1, 0)",
        ).run(id, `/tmp/${id}.mp4`);
      };
      addVideo("v1", "solo-leveling", 1, 1);
      addVideo("v2", "solo-leveling-2", 1, 2);
      const dir = mkdtempSync(join(tmpdir(), "hikari-merge-"));
      const app = Fastify();
      await registerVideosRoutes(app, { db, videoDir: dir, coverDir: dir, extractor: null });
      return { db, app };
    }

    it("verschiebt alle Folgen zur Zielserie und löscht die Quelle", async () => {
      const { db, app } = await setup();
      const res = await app.inject({
        method: "POST",
        url: "/series/merge",
        payload: { sourceId: "solo-leveling-2", targetId: "solo-leveling" },
      });
      expect(res.statusCode).toBe(200);
      expect(res.json()).toEqual({ merged: 1, into: "solo-leveling" });

      const rows = db.prepare("SELECT id, series_id FROM videos ORDER BY id").all() as {
        id: string;
        series_id: string;
      }[];
      expect(rows).toEqual([
        { id: "v1", series_id: "solo-leveling" },
        { id: "v2", series_id: "solo-leveling" },
      ]);
      expect(db.prepare("SELECT 1 FROM series WHERE id = 'solo-leveling-2'").get()).toBeUndefined();
      // Cover der Quelle wandert mit, weil das Ziel keines hatte.
      expect(
        (
          db.prepare("SELECT thumbnail_url FROM series WHERE id = 'solo-leveling'").get() as {
            thumbnail_url: string;
          }
        ).thumbnail_url,
      ).toBe("/covers/a.jpg");
    });

    it("lehnt identische IDs ab", async () => {
      const { app } = await setup();
      const res = await app.inject({
        method: "POST",
        url: "/series/merge",
        payload: { sourceId: "solo-leveling", targetId: "solo-leveling" },
      });
      expect(res.statusCode).toBe(400);
    });

    it("404 bei unbekannter Serie", async () => {
      const { app } = await setup();
      const res = await app.inject({
        method: "POST",
        url: "/series/merge",
        payload: { sourceId: "gibts-nicht", targetId: "solo-leveling" },
      });
      expect(res.statusCode).toBe(404);
    });
  });

  describe("GET /videos/:id/next", () => {
    async function setup() {
      const Database = (await import("better-sqlite3")).default;
      const { applyMigrations } = await import("../db/migrations.js");
      const db = new Database(":memory:");
      applyMigrations(db);
      db.prepare("INSERT INTO channels (id,url,title,added_at) VALUES ('manual','x','M',0)").run();
      db.prepare("INSERT INTO series (id, title, added_at) VALUES ('s1', 'Serie', 1)").run();
      const addVideo = (id: string, season: number, episode: number, downloaded = true) => {
        db.prepare(
          `INSERT INTO videos (id, channel_id, series_id, title, published_at, duration_seconds, discovered_at, season, episode)
           VALUES (?, 'manual', 's1', ?, 0, 600, 0, ?, ?)`,
        ).run(id, `S${season}F${episode}`, season, episode);
        if (downloaded) {
          db.prepare(
            "INSERT INTO downloaded_videos (video_id, file_path, file_size_bytes, downloaded_at) VALUES (?, ?, 1, 0)",
          ).run(id, `/tmp/${id}.mp4`);
        }
      };
      addVideo("e1", 1, 1);
      addVideo("e2", 1, 2, false); // Lücke: nicht heruntergeladen
      addVideo("e3", 1, 3);
      addVideo("e4", 2, 1); // Staffelgrenze
      const dir = mkdtempSync(join(tmpdir(), "hikari-next-"));
      const app = Fastify();
      await registerVideosRoutes(app, { db, videoDir: dir, coverDir: dir, extractor: null });
      return { app };
    }

    it("liefert die nächste vorhandene Folge und überspringt Lücken", async () => {
      const { app } = await setup();
      const res = await app.inject({ method: "GET", url: "/videos/e1/next" });
      expect(res.statusCode).toBe(200);
      expect(res.json()).toMatchObject({ id: "e3", season: 1, episode: 3 });
    });

    it("springt über die Staffelgrenze", async () => {
      const { app } = await setup();
      const res = await app.inject({ method: "GET", url: "/videos/e3/next" });
      expect(res.statusCode).toBe(200);
      expect(res.json()).toMatchObject({ id: "e4", season: 2, episode: 1 });
    });

    it("404 am Serienende und bei Videos ohne Serie", async () => {
      const { app } = await setup();
      expect((await app.inject({ method: "GET", url: "/videos/e4/next" })).statusCode).toBe(404);
      expect(
        (await app.inject({ method: "GET", url: "/videos/gibts-nicht/next" })).statusCode,
      ).toBe(404);
    });
  });
});
