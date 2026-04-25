import { test, expect, beforeEach } from "vitest";
import Fastify, { type FastifyInstance } from "fastify";
import Database from "better-sqlite3";
import { applyMigrations } from "../../src/db/migrations.js";
import { upsertSeries, upsertChapter, upsertPage } from "../../src/manga/persist.js";
import { registerMangaRoutes } from "../../src/api/manga.js";

function buildApp(): { app: FastifyInstance; db: Database.Database } {
  const db = new Database(":memory:");
  db.pragma("foreign_keys = ON");
  applyMigrations(db);
  upsertSeries(db, {
    source: "fake",
    sourceSlug: "x",
    title: "X",
    sourceUrl: "https://fake.test/x",
  });
  upsertChapter(db, {
    source: "fake",
    seriesSlug: "x",
    number: 1,
    sourceUrl: "https://fake.test/x/1",
  });
  upsertPage(db, {
    source: "fake",
    seriesSlug: "x",
    chapterNumber: 1,
    pageNumber: 1,
    sourceUrl: "https://fake.test/p1.png",
    localPath: "fake/x/1/01.png",
  });

  const app = Fastify();
  registerMangaRoutes(app, { db, mangaDir: "/tmp/manga-test-doesnt-need-to-exist" });
  return { app, db };
}

test("GET /api/manga/series returns all series", async () => {
  const { app } = buildApp();
  const r = await app.inject({ method: "GET", url: "/api/manga/series" });
  expect(r.statusCode).toBe(200);
  const body = r.json() as { id: string; title: string }[];
  expect(body).toHaveLength(1);
  expect(body[0].id).toBe("fake:x");
  expect(body[0].title).toBe("X");
});

test("GET /api/manga/series/:id returns chapters and arcs", async () => {
  const { app } = buildApp();
  const r = await app.inject({ method: "GET", url: "/api/manga/series/fake:x" });
  expect(r.statusCode).toBe(200);
  const body = r.json() as {
    id: string;
    chapters: { number: number; isRead: 0 | 1 }[];
    arcs: unknown[];
  };
  expect(body.id).toBe("fake:x");
  expect(body.chapters).toHaveLength(1);
  expect(body.chapters[0].number).toBe(1);
  expect(body.chapters[0].isRead).toBe(0); // not marked as read
  expect(Array.isArray(body.arcs)).toBe(true);
});

test("GET /api/manga/series/:id returns 404 for unknown id", async () => {
  const { app } = buildApp();
  const r = await app.inject({ method: "GET", url: "/api/manga/series/no-such-thing" });
  expect(r.statusCode).toBe(404);
});

test("GET /api/manga/chapters/:id/pages returns ordered page list with ready flag", async () => {
  const { app } = buildApp();
  const r = await app.inject({
    method: "GET",
    url: "/api/manga/chapters/fake:x:1/pages",
  });
  expect(r.statusCode).toBe(200);
  const body = r.json() as { id: string; pageNumber: number; ready: boolean }[];
  expect(body).toHaveLength(1);
  expect(body[0].pageNumber).toBe(1);
  expect(body[0].ready).toBe(true);
});

test("GET /api/manga/chapters/:id/pages returns 404 when chapter has no pages", async () => {
  const { app, db } = buildApp();
  // Add a chapter without any pages
  upsertChapter(db, {
    source: "fake", seriesSlug: "x", number: 999,
    sourceUrl: "https://fake.test/x/999",
  });
  const r = await app.inject({ method: "GET", url: "/api/manga/chapters/fake:x:999/pages" });
  expect(r.statusCode).toBe(404);
});

test("GET /api/manga/chapters/:id/pages reports ready: false when local_path is NULL", async () => {
  const { app, db } = buildApp();
  upsertChapter(db, {
    source: "fake", seriesSlug: "x", number: 2,
    sourceUrl: "https://fake.test/x/2",
  });
  upsertPage(db, {
    source: "fake", seriesSlug: "x", chapterNumber: 2, pageNumber: 1,
    sourceUrl: "https://fake.test/p.png",
    // localPath omitted → stays NULL
  });
  const r = await app.inject({ method: "GET", url: "/api/manga/chapters/fake:x:2/pages" });
  expect(r.statusCode).toBe(200);
  const body = r.json() as { ready: boolean }[];
  expect(body[0].ready).toBe(false);
});

import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

test("GET /api/manga/page/:id streams image bytes when local_path set", async () => {
  const dir = mkdtempSync(join(tmpdir(), "manga-api-"));
  try {
    const sub = join(dir, "fake", "x", "1");
    mkdirSync(sub, { recursive: true });
    const bytes = Buffer.from([1, 2, 3, 4, 5]);
    writeFileSync(join(sub, "01.png"), bytes);

    const db = new Database(":memory:");
    db.pragma("foreign_keys = ON");
    applyMigrations(db);
    upsertSeries(db, {
      source: "fake", sourceSlug: "x",
      title: "X", sourceUrl: "https://fake.test/x",
    });
    upsertChapter(db, {
      source: "fake", seriesSlug: "x",
      number: 1, sourceUrl: "https://fake.test/x/1",
    });
    upsertPage(db, {
      source: "fake", seriesSlug: "x",
      chapterNumber: 1, pageNumber: 1,
      sourceUrl: "https://fake.test/p.png",
      localPath: "fake/x/1/01.png",
    });

    const app = Fastify();
    registerMangaRoutes(app, { db, mangaDir: dir });
    const r = await app.inject({ method: "GET", url: "/api/manga/page/fake:x:1:01" });
    expect(r.statusCode).toBe(200);
    expect(r.headers["content-type"]).toMatch(/image\//);
    expect(r.rawPayload.equals(bytes)).toBe(true);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("GET /api/manga/page/:id sets long-lived Cache-Control", async () => {
  const dir = mkdtempSync(join(tmpdir(), "manga-api-"));
  try {
    const sub = join(dir, "fake", "x", "1");
    mkdirSync(sub, { recursive: true });
    writeFileSync(join(sub, "01.png"), Buffer.from([1]));

    const db = new Database(":memory:");
    db.pragma("foreign_keys = ON");
    applyMigrations(db);
    upsertSeries(db, { source: "fake", sourceSlug: "x", title: "X", sourceUrl: "https://x" });
    upsertChapter(db, { source: "fake", seriesSlug: "x", number: 1, sourceUrl: "https://x" });
    upsertPage(db, {
      source: "fake", seriesSlug: "x",
      chapterNumber: 1, pageNumber: 1,
      sourceUrl: "https://x/p.png",
      localPath: "fake/x/1/01.png",
    });

    const app = Fastify();
    registerMangaRoutes(app, { db, mangaDir: dir });
    const r = await app.inject({ method: "GET", url: "/api/manga/page/fake:x:1:01" });
    expect(r.headers["cache-control"]).toMatch(/max-age=\d+/);
    expect(r.headers["cache-control"]).toMatch(/immutable/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("GET /api/manga/page/:id rejects local_path that escapes mangaDir (path traversal)", async () => {
  const dir = mkdtempSync(join(tmpdir(), "manga-api-"));
  try {
    const db = new Database(":memory:");
    db.pragma("foreign_keys = ON");
    applyMigrations(db);
    upsertSeries(db, { source: "fake", sourceSlug: "x", title: "X", sourceUrl: "https://x" });
    upsertChapter(db, { source: "fake", seriesSlug: "x", number: 1, sourceUrl: "https://x" });
    // Inject a malicious local_path manually (bypassing upsertPage normalization)
    db.prepare(
      "INSERT INTO manga_pages (id, chapter_id, page_number, source_url, local_path) VALUES (?, ?, ?, ?, ?)",
    ).run("fake:x:1:99", "fake:x:1", 99, "https://x", "../../../etc/passwd");

    const app = Fastify();
    registerMangaRoutes(app, { db, mangaDir: dir });
    const r = await app.inject({ method: "GET", url: "/api/manga/page/fake:x:1:99" });
    expect(r.statusCode).toBe(400);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("GET /api/manga/page/:id also rejects absolute paths in local_path", async () => {
  const dir = mkdtempSync(join(tmpdir(), "manga-api-"));
  try {
    const db = new Database(":memory:");
    db.pragma("foreign_keys = ON");
    applyMigrations(db);
    upsertSeries(db, { source: "fake", sourceSlug: "x", title: "X", sourceUrl: "https://x" });
    upsertChapter(db, { source: "fake", seriesSlug: "x", number: 1, sourceUrl: "https://x" });
    db.prepare(
      "INSERT INTO manga_pages (id, chapter_id, page_number, source_url, local_path) VALUES (?, ?, ?, ?, ?)",
    ).run("fake:x:1:50", "fake:x:1", 50, "https://x", "/etc/passwd");

    const app = Fastify();
    registerMangaRoutes(app, { db, mangaDir: dir });
    const r = await app.inject({ method: "GET", url: "/api/manga/page/fake:x:1:50" });
    expect(r.statusCode).toBe(400);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("GET /api/manga/page/:id returns 404 when local_path is NULL (pending)", async () => {
  const dir = mkdtempSync(join(tmpdir(), "manga-api-"));
  try {
    const db = new Database(":memory:");
    db.pragma("foreign_keys = ON");
    applyMigrations(db);
    upsertSeries(db, { source: "fake", sourceSlug: "x", title: "X", sourceUrl: "https://x" });
    upsertChapter(db, { source: "fake", seriesSlug: "x", number: 1, sourceUrl: "https://x" });
    upsertPage(db, {
      source: "fake", seriesSlug: "x",
      chapterNumber: 1, pageNumber: 1,
      sourceUrl: "https://x/p.png",
      // localPath omitted → NULL
    });
    const app = Fastify();
    registerMangaRoutes(app, { db, mangaDir: dir });
    const r = await app.inject({ method: "GET", url: "/api/manga/page/fake:x:1:01" });
    expect(r.statusCode).toBe(404);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("GET /api/manga/page/:id returns 404 when page id doesn't exist", async () => {
  const dir = mkdtempSync(join(tmpdir(), "manga-api-"));
  try {
    const db = new Database(":memory:");
    db.pragma("foreign_keys = ON");
    applyMigrations(db);
    const app = Fastify();
    registerMangaRoutes(app, { db, mangaDir: dir });
    const r = await app.inject({ method: "GET", url: "/api/manga/page/no:such:thing" });
    expect(r.statusCode).toBe(404);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});
