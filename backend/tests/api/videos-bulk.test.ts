import { test, expect, beforeEach } from "vitest";
import Fastify from "fastify";
import Database from "better-sqlite3";
import { applyMigrations } from "../../src/db/migrations.js";
import { registerVideosRoutes } from "../../src/api/videos.js";

let db: Database.Database;

beforeEach(() => {
  db = new Database(":memory:");
  db.pragma("foreign_keys = ON");
  applyMigrations(db);
});

function buildApp() {
  const app = Fastify();
  registerVideosRoutes(app, { db, videoDir: "/tmp/test", coverDir: "/tmp/test-covers", extractor: null });
  return app;
}

test("POST /videos/import/bulk returns 400 when items array is empty", async () => {
  const app = buildApp();
  const r = await app.inject({
    method: "POST",
    url: "/videos/import/bulk",
    payload: { items: [] },
  });
  expect(r.statusCode).toBe(400);
});

test("POST /videos/import/bulk returns 400 when items field is missing", async () => {
  const app = buildApp();
  const r = await app.inject({
    method: "POST",
    url: "/videos/import/bulk",
    payload: {},
  });
  expect(r.statusCode).toBe(400);
});

test("POST /videos/import/bulk returns 202 with queued count for valid body", async () => {
  const app = buildApp();
  const r = await app.inject({
    method: "POST",
    url: "/videos/import/bulk",
    payload: {
      items: [
        { url: "https://example.test/1" },
        { url: "https://example.test/2", metadata: { title: "Custom" } },
        { url: "https://example.test/3" },
      ],
    },
  });
  expect(r.statusCode).toBe(202);
  const body = r.json() as { queued: number };
  expect(body.queued).toBe(3);
});

test("GET /languages returns distinct dub + sub values from videos table", async () => {
  // Seed videos with channel scaffolding
  db.prepare(
    "INSERT INTO channels (id, title, url, is_active, added_at) VALUES (?, ?, ?, 1, 0)",
  ).run("c1", "Test", "http://x");
  const insert = db.prepare(
    "INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at, dub_language, sub_language) VALUES (?, ?, ?, 0, 0, 0, ?, ?)",
  );
  insert.run("v1", "c1", "T1", "Japanisch", "Deutsch");
  insert.run("v2", "c1", "T2", "Japanisch", "Englisch");
  insert.run("v3", "c1", "T3", "Deutsch", null);
  insert.run("v4", "c1", "T4", null, "Deutsch");

  const app = buildApp();
  const r = await app.inject({ method: "GET", url: "/languages" });
  expect(r.statusCode).toBe(200);
  const body = r.json() as { dub: string[]; sub: string[] };
  expect(body.dub).toEqual(["Deutsch", "Japanisch"]);
  expect(body.sub).toEqual(["Deutsch", "Englisch"]);
});

test("GET /languages returns empty arrays when no videos", async () => {
  const app = buildApp();
  const r = await app.inject({ method: "GET", url: "/languages" });
  expect(r.statusCode).toBe(200);
  expect(r.json()).toEqual({ dub: [], sub: [] });
});

function seedSeriesWithVideo(
  seriesId = "s1",
  episodeThumb = "https://thumb.test/ep1.jpg",
) {
  db.prepare(
    "INSERT INTO series (id, title, added_at) VALUES (?, ?, 0)",
  ).run(seriesId, "Test Series");
  db.prepare(
    "INSERT INTO channels (id, title, url, is_active, added_at) VALUES (?, ?, ?, 1, 0)",
  ).run("c1", "Channel", "http://x");
  db.prepare(
    "INSERT INTO videos (id, channel_id, series_id, title, published_at, duration_seconds, discovered_at, thumbnail_url, season, episode) VALUES (?, ?, ?, ?, 0, 0, 0, ?, 1, 1)",
  ).run("v1", "c1", seriesId, "Episode 1", episodeThumb);
}

test("GET /series/:id falls back to first episode thumbnail when cover not set", async () => {
  seedSeriesWithVideo("s1", "https://thumb.test/ep1.jpg");
  const app = buildApp();
  const r = await app.inject({ method: "GET", url: "/series/s1" });
  expect(r.statusCode).toBe(200);
  const body = r.json() as { thumbnail_url: string | null };
  expect(body.thumbnail_url).toBe("https://thumb.test/ep1.jpg");
});

test("GET /series/:id keeps manual cover over fallback", async () => {
  seedSeriesWithVideo("s1", "https://thumb.test/ep1.jpg");
  db.prepare("UPDATE series SET thumbnail_url = ? WHERE id = ?").run(
    "https://manual.test/cover.jpg",
    "s1",
  );
  const app = buildApp();
  const r = await app.inject({ method: "GET", url: "/series/s1" });
  const body = r.json() as { thumbnail_url: string };
  expect(body.thumbnail_url).toBe("https://manual.test/cover.jpg");
});

test("GET /library applies cover fallback to all series", async () => {
  seedSeriesWithVideo("s1", "https://thumb.test/ep1.jpg");
  const app = buildApp();
  const r = await app.inject({ method: "GET", url: "/library" });
  const body = r.json() as { series: { id: string; thumbnail_url: string }[] };
  expect(body.series.find((s) => s.id === "s1")?.thumbnail_url).toBe(
    "https://thumb.test/ep1.jpg",
  );
});

test("PATCH /series/:id updates thumbnail_url and description", async () => {
  seedSeriesWithVideo();
  const app = buildApp();
  const r = await app.inject({
    method: "PATCH",
    url: "/series/s1",
    payload: { thumbnail_url: "https://new.test/c.jpg", description: "neue Beschreibung" },
  });
  expect(r.statusCode).toBe(200);
  const body = r.json() as { thumbnail_url: string; description: string };
  expect(body.thumbnail_url).toBe("https://new.test/c.jpg");
  expect(body.description).toBe("neue Beschreibung");
});

test("PATCH /series/:id with empty thumbnail_url clears manual cover (fallback resumes)", async () => {
  seedSeriesWithVideo("s1", "https://thumb.test/ep1.jpg");
  db.prepare("UPDATE series SET thumbnail_url = 'https://old.test/c.jpg' WHERE id = 's1'").run();

  const app = buildApp();
  const r = await app.inject({
    method: "PATCH",
    url: "/series/s1",
    payload: { thumbnail_url: "" },
  });
  expect(r.statusCode).toBe(200);
  const body = r.json() as { thumbnail_url: string };
  // After clearing, fallback to first episode thumb kicks in
  expect(body.thumbnail_url).toBe("https://thumb.test/ep1.jpg");
});

test("PATCH /series/:id returns 404 when series not found", async () => {
  const app = buildApp();
  const r = await app.inject({
    method: "PATCH",
    url: "/series/nope",
    payload: { description: "x" },
  });
  expect(r.statusCode).toBe(404);
});

test("PATCH /series/:id returns 400 when no updatable fields supplied", async () => {
  seedSeriesWithVideo();
  const app = buildApp();
  const r = await app.inject({
    method: "PATCH",
    url: "/series/s1",
    payload: {},
  });
  expect(r.statusCode).toBe(400);
});
