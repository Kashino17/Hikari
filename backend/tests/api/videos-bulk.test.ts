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

// ── Video edit tests ──────────────────────────────────────────────────────

test("GET /videos/:id returns video with series_title joined", async () => {
  seedSeriesWithVideo("s1");
  const app = buildApp();
  const r = await app.inject({ method: "GET", url: "/videos/v1" });
  expect(r.statusCode).toBe(200);
  const body = r.json() as { id: string; series_id: string; series_title: string };
  expect(body.id).toBe("v1");
  expect(body.series_id).toBe("s1");
  expect(body.series_title).toBe("Test Series");
});

test("GET /videos/:id returns 404 for missing video", async () => {
  const app = buildApp();
  const r = await app.inject({ method: "GET", url: "/videos/nope" });
  expect(r.statusCode).toBe(404);
});

test("PATCH /videos/:id updates basic fields", async () => {
  seedSeriesWithVideo();
  const app = buildApp();
  const r = await app.inject({
    method: "PATCH",
    url: "/videos/v1",
    payload: {
      title: "Neuer Titel",
      description: "Neue Beschreibung",
      season: 2,
      episode: 5,
      dub_language: "Japanisch",
      sub_language: "Deutsch",
      is_movie: false,
    },
  });
  expect(r.statusCode).toBe(200);
  const body = r.json() as Record<string, unknown>;
  expect(body.title).toBe("Neuer Titel");
  expect(body.description).toBe("Neue Beschreibung");
  expect(body.season).toBe(2);
  expect(body.episode).toBe(5);
  expect(body.dub_language).toBe("Japanisch");
  expect(body.sub_language).toBe("Deutsch");
  expect(body.is_movie).toBe(0);
});

test("PATCH /videos/:id with series_title creates new series if needed", async () => {
  seedSeriesWithVideo();
  const app = buildApp();
  const r = await app.inject({
    method: "PATCH",
    url: "/videos/v1",
    payload: { series_title: "Neue Serie" },
  });
  expect(r.statusCode).toBe(200);
  const body = r.json() as { series_id: string; series_title: string };
  expect(body.series_id).toBe("neue-serie");
  expect(body.series_title).toBe("Neue Serie");
});

test("PATCH /videos/:id auto-deletes old series when last video moves out", async () => {
  // s1 has only v1. Move v1 to a new series "Other".
  seedSeriesWithVideo("s1");
  const app = buildApp();
  await app.inject({
    method: "PATCH",
    url: "/videos/v1",
    payload: { series_title: "Other" },
  });
  const oldSeries = db.prepare("SELECT id FROM series WHERE id = 's1'").get();
  expect(oldSeries).toBeUndefined();
  const newSeries = db.prepare("SELECT title FROM series WHERE id = 'other'").get() as
    | { title: string }
    | undefined;
  expect(newSeries?.title).toBe("Other");
});

test("PATCH /videos/:id keeps old series when other videos remain", async () => {
  seedSeriesWithVideo("s1");
  // Add a second video to s1 so it stays populated after we move v1 out.
  db.prepare(
    "INSERT INTO videos (id, channel_id, series_id, title, published_at, duration_seconds, discovered_at) VALUES (?, ?, ?, ?, 0, 0, 0)",
  ).run("v2", "c1", "s1", "Episode 2");

  const app = buildApp();
  await app.inject({
    method: "PATCH",
    url: "/videos/v1",
    payload: { series_title: "Other" },
  });
  const stillThere = db.prepare("SELECT id FROM series WHERE id = 's1'").get();
  expect(stillThere).toBeDefined();
});

test("PATCH /videos/:id returns 404 for missing video", async () => {
  const app = buildApp();
  const r = await app.inject({
    method: "PATCH",
    url: "/videos/nope",
    payload: { title: "x" },
  });
  expect(r.statusCode).toBe(404);
});

test("PATCH /videos/:id returns 400 when no fields", async () => {
  seedSeriesWithVideo();
  const app = buildApp();
  const r = await app.inject({
    method: "PATCH",
    url: "/videos/v1",
    payload: {},
  });
  expect(r.statusCode).toBe(400);
});
