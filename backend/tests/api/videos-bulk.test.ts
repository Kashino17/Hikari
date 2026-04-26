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
  registerVideosRoutes(app, { db, videoDir: "/tmp/test", extractor: null });
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
