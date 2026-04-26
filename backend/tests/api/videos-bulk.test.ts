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
