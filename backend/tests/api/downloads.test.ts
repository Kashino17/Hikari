import { test, expect, beforeEach } from "vitest";
import Fastify from "fastify";
import Database from "better-sqlite3";
import { applyMigrations } from "../../src/db/migrations.js";
import { registerDownloadsRoutes, type DownloadsResponse } from "../../src/api/downloads.js";

let db: Database.Database;

beforeEach(() => {
  db = new Database(":memory:");
  db.pragma("foreign_keys = ON");
  applyMigrations(db);
});

function buildApp() {
  const app = Fastify();
  registerDownloadsRoutes(app, { db, diskLimitBytes: 10 * 1024 ** 3 });
  return app;
}

function seedChannel(id: string, title: string, banner: string | null = null) {
  db.prepare(
    "INSERT INTO channels (id, url, title, is_active, added_at, banner_url, thumbnail_url) VALUES (?, ?, ?, 1, 0, ?, ?)",
  ).run(id, "https://x/" + id, title, banner, "https://x/" + id + "-avatar.jpg");
}

function seedSeries(id: string, title: string, cover: string | null = null) {
  db.prepare(
    "INSERT INTO series (id, title, added_at, thumbnail_url) VALUES (?, ?, 0, ?)",
  ).run(id, title, cover);
}

function seedVideo(opts: {
  id: string;
  channelId: string;
  seriesId?: string;
  title: string;
  isMovie?: boolean;
  season?: number;
  episode?: number;
  duration?: number;
  thumb?: string | null;
  size: number;
  downloadedAt?: number;
}) {
  db.prepare(
    "INSERT INTO videos (id, channel_id, series_id, title, published_at, duration_seconds, discovered_at, thumbnail_url, season, episode, is_movie) VALUES (?, ?, ?, ?, 0, ?, 0, ?, ?, ?, ?)",
  ).run(
    opts.id,
    opts.channelId,
    opts.seriesId ?? null,
    opts.title,
    opts.duration ?? 1800,
    opts.thumb ?? null,
    opts.season ?? null,
    opts.episode ?? null,
    opts.isMovie ? 1 : 0,
  );
  db.prepare(
    "INSERT INTO downloaded_videos (video_id, file_path, file_size_bytes, downloaded_at) VALUES (?, ?, ?, ?)",
  ).run(opts.id, "/tmp/" + opts.id + ".mp4", opts.size, opts.downloadedAt ?? 0);
}

test("GET /downloads returns empty buckets when nothing downloaded", async () => {
  const app = buildApp();
  const r = await app.inject({ method: "GET", url: "/downloads" });
  expect(r.statusCode).toBe(200);
  const body = r.json() as DownloadsResponse;
  expect(body.total_bytes).toBe(0);
  expect(body.series).toEqual([]);
  expect(body.channels).toEqual([]);
  expect(body.movies).toEqual([]);
  expect(body.limit_bytes).toBe(10 * 1024 ** 3);
});

test("GET /downloads groups episodes under series, sorted by season+episode", async () => {
  seedChannel("c1", "Manuell hinzugefügt");
  seedSeries("dbs", "Dragonball Super", "https://cover/dbs.jpg");
  seedVideo({ id: "v3", channelId: "c1", seriesId: "dbs", title: "F3", season: 1, episode: 3, size: 200_000_000 });
  seedVideo({ id: "v1", channelId: "c1", seriesId: "dbs", title: "F1", season: 1, episode: 1, size: 184_000_000 });
  seedVideo({ id: "v2", channelId: "c1", seriesId: "dbs", title: "F2", season: 1, episode: 2, size: 156_000_000 });

  const app = buildApp();
  const r = await app.inject({ method: "GET", url: "/downloads" });
  const body = r.json() as DownloadsResponse;
  expect(body.series).toHaveLength(1);
  const dbs = body.series[0];
  expect(dbs.id).toBe("dbs");
  expect(dbs.title).toBe("Dragonball Super");
  expect(dbs.thumbnail_url).toBe("https://cover/dbs.jpg");
  expect(dbs.episode_count).toBe(3);
  expect(dbs.total_bytes).toBe(540_000_000);
  expect(dbs.episodes.map((e) => e.episode)).toEqual([1, 2, 3]);
});

test("GET /downloads buckets is_movie videos as movies", async () => {
  seedChannel("c1", "Manuell hinzugefügt");
  seedVideo({ id: "m1", channelId: "c1", title: "Spirited Away", isMovie: true, duration: 7500, size: 1_800_000_000 });

  const app = buildApp();
  const body = (await app.inject({ method: "GET", url: "/downloads" })).json() as DownloadsResponse;
  expect(body.movies).toHaveLength(1);
  expect(body.movies[0]).toMatchObject({
    id: "m1",
    title: "Spirited Away",
    duration_seconds: 7500,
    size_bytes: 1_800_000_000,
  });
  expect(body.series).toEqual([]);
});

test("GET /downloads groups videos without series under their channel", async () => {
  seedChannel("fireship", "Fireship");
  seedVideo({ id: "v1", channelId: "fireship", title: "Tailscale Setup", size: 89_000_000, thumb: "https://yt/v1.jpg" });
  seedVideo({ id: "v2", channelId: "fireship", title: "Compose Tricks", size: 62_000_000, thumb: "https://yt/v2.jpg" });

  const app = buildApp();
  const body = (await app.inject({ method: "GET", url: "/downloads" })).json() as DownloadsResponse;
  expect(body.channels).toHaveLength(1);
  const fs = body.channels[0];
  expect(fs.title).toBe("Fireship");
  expect(fs.video_count).toBe(2);
  expect(fs.total_bytes).toBe(151_000_000);
});

test("GET /downloads handles all three buckets in one response", async () => {
  seedChannel("c1", "Manuell");
  seedChannel("fireship", "Fireship");
  seedSeries("dbs", "Dragonball Super");
  seedVideo({ id: "ep", channelId: "c1", seriesId: "dbs", title: "F1", season: 1, episode: 1, size: 100_000_000 });
  seedVideo({ id: "ch", channelId: "fireship", title: "Compose", size: 50_000_000 });
  seedVideo({ id: "mv", channelId: "c1", title: "Mononoke", isMovie: true, size: 2_000_000_000 });

  const app = buildApp();
  const body = (await app.inject({ method: "GET", url: "/downloads" })).json() as DownloadsResponse;
  expect(body.series).toHaveLength(1);
  expect(body.channels).toHaveLength(1);
  expect(body.movies).toHaveLength(1);
  expect(body.total_bytes).toBe(2_150_000_000);
});

test("GET /downloads falls back to first episode thumb when series has no cover", async () => {
  seedChannel("c1", "X");
  seedSeries("s1", "X-Series", null);
  seedVideo({
    id: "v1",
    channelId: "c1",
    seriesId: "s1",
    title: "F1",
    season: 1,
    episode: 1,
    thumb: "https://yt/ep1.jpg",
    size: 1,
  });

  const body = (await buildApp().inject({ method: "GET", url: "/downloads" })).json() as DownloadsResponse;
  expect(body.series[0].thumbnail_url).toBe("https://yt/ep1.jpg");
});

test("GET /downloads sorts series by total_bytes desc and channels by total_bytes desc", async () => {
  seedChannel("c1", "C1");
  seedChannel("c2", "C2");
  seedSeries("a", "A");
  seedSeries("b", "B");
  seedVideo({ id: "a1", channelId: "c1", seriesId: "a", title: "A1", season: 1, episode: 1, size: 100 });
  seedVideo({ id: "b1", channelId: "c1", seriesId: "b", title: "B1", season: 1, episode: 1, size: 1000 });
  seedVideo({ id: "ch_small", channelId: "c1", title: "S", size: 50 });
  seedVideo({ id: "ch_big", channelId: "c2", title: "L", size: 500 });

  const body = (await buildApp().inject({ method: "GET", url: "/downloads" })).json() as DownloadsResponse;
  expect(body.series.map((s) => s.id)).toEqual(["b", "a"]);
  expect(body.channels.map((c) => c.id)).toEqual(["c2", "c1"]);
});
