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

test("POST and DELETE /api/manga/library/:seriesId toggles membership", async () => {
  const { app, db } = buildApp();
  const r1 = await app.inject({ method: "POST", url: "/api/manga/library/fake:x" });
  expect(r1.statusCode).toBe(200);
  expect(
    (db.prepare("SELECT COUNT(*) as c FROM manga_library").get() as { c: number }).c,
  ).toBe(1);

  // Idempotent — POST again, still 1 row
  await app.inject({ method: "POST", url: "/api/manga/library/fake:x" });
  expect(
    (db.prepare("SELECT COUNT(*) as c FROM manga_library").get() as { c: number }).c,
  ).toBe(1);

  const r2 = await app.inject({ method: "DELETE", url: "/api/manga/library/fake:x" });
  expect(r2.statusCode).toBe(200);
  expect(
    (db.prepare("SELECT COUNT(*) as c FROM manga_library").get() as { c: number }).c,
  ).toBe(0);
});

test("PUT /api/manga/progress/:seriesId stores chapter+page", async () => {
  const { app, db } = buildApp();
  const r = await app.inject({
    method: "PUT",
    url: "/api/manga/progress/fake:x",
    payload: { chapterId: "fake:x:1", pageNumber: 5 },
  });
  expect(r.statusCode).toBe(200);
  const row = db
    .prepare("SELECT chapter_id, page_number FROM manga_progress WHERE series_id = 'fake:x'")
    .get() as { chapter_id: string; page_number: number } | undefined;
  expect(row?.chapter_id).toBe("fake:x:1");
  expect(row?.page_number).toBe(5);
});

test("PUT /api/manga/progress/:seriesId rejects missing fields with 400", async () => {
  const { app } = buildApp();
  const r = await app.inject({
    method: "PUT",
    url: "/api/manga/progress/fake:x",
    payload: { chapterId: "fake:x:1" }, // pageNumber missing
  });
  expect(r.statusCode).toBe(400);
});

test("PUT /api/manga/progress/:seriesId is idempotent — second call updates same row", async () => {
  const { app, db } = buildApp();
  await app.inject({
    method: "PUT", url: "/api/manga/progress/fake:x",
    payload: { chapterId: "fake:x:1", pageNumber: 5 },
  });
  await app.inject({
    method: "PUT", url: "/api/manga/progress/fake:x",
    payload: { chapterId: "fake:x:1", pageNumber: 8 },
  });
  const c = db.prepare("SELECT COUNT(*) as c FROM manga_progress").get() as { c: number };
  expect(c.c).toBe(1);
  const row = db
    .prepare("SELECT page_number FROM manga_progress WHERE series_id = 'fake:x'")
    .get() as { page_number: number };
  expect(row.page_number).toBe(8);
});

test("PUT /api/manga/chapters/:id/read marks chapter as read", async () => {
  const { app, db } = buildApp();
  const r = await app.inject({ method: "PUT", url: "/api/manga/chapters/fake:x:1/read" });
  expect(r.statusCode).toBe(200);
  const row = db
    .prepare("SELECT chapter_id, read_at FROM manga_chapter_read WHERE chapter_id = 'fake:x:1'")
    .get() as { chapter_id: string; read_at: number } | undefined;
  expect(row).toBeDefined();
  expect(typeof row!.read_at).toBe("number");
});

test("GET /api/manga/continue returns library series with progress, sorted recent first", async () => {
  const { app, db } = buildApp();
  // Setup: add to library and create progress
  db.prepare("INSERT INTO manga_library (series_id, added_at) VALUES (?, ?)").run("fake:x", Date.now());
  db.prepare(
    "INSERT INTO manga_progress (series_id, chapter_id, page_number, updated_at) VALUES (?, ?, ?, ?)",
  ).run("fake:x", "fake:x:1", 3, Date.now());

  const r = await app.inject({ method: "GET", url: "/api/manga/continue" });
  expect(r.statusCode).toBe(200);
  const body = r.json() as {
    seriesId: string;
    title: string;
    chapterId: string;
    pageNumber: number;
    updatedAt: number;
  }[];
  expect(body).toHaveLength(1);
  expect(body[0].seriesId).toBe("fake:x");
  expect(body[0].title).toBe("X");
  expect(body[0].chapterId).toBe("fake:x:1");
  expect(body[0].pageNumber).toBe(3);
});

test("GET /api/manga/continue excludes series with progress but NOT in library", async () => {
  const { app, db } = buildApp();
  db.prepare(
    "INSERT INTO manga_progress (series_id, chapter_id, page_number, updated_at) VALUES (?, ?, ?, ?)",
  ).run("fake:x", "fake:x:1", 3, Date.now());
  // NOT added to library
  const r = await app.inject({ method: "GET", url: "/api/manga/continue" });
  const body = r.json() as unknown[];
  expect(body).toHaveLength(0);
});

test("POST /api/manga/sync returns 409 if a job is already running", async () => {
  const { app, db } = buildApp();
  db.prepare(
    "INSERT INTO manga_sync_jobs (id, source, status, started_at) VALUES (?, ?, 'running', ?)",
  ).run("job-1", "onepiecetube", Date.now());
  const r = await app.inject({ method: "POST", url: "/api/manga/sync" });
  expect(r.statusCode).toBe(409);
});

test("POST /api/manga/sync returns 409 if a queued job exists", async () => {
  const { app, db } = buildApp();
  db.prepare(
    "INSERT INTO manga_sync_jobs (id, source, status, started_at) VALUES (?, ?, 'queued', ?)",
  ).run("job-1", "onepiecetube", Date.now());
  const r = await app.inject({ method: "POST", url: "/api/manga/sync" });
  expect(r.statusCode).toBe(409);
});

test("POST /api/manga/sync returns 202 when no job is running", async () => {
  const { app } = buildApp();
  const r = await app.inject({ method: "POST", url: "/api/manga/sync" });
  expect(r.statusCode).toBe(202);
});

test("POST /api/manga/sync returns 400 when sourceId is unknown", async () => {
  const { app } = buildApp();
  const r = await app.inject({
    method: "POST",
    url: "/api/manga/sync",
    payload: { sourceId: "no-such-source" },
  });
  expect(r.statusCode).toBe(400);
});

test("GET /api/manga/sync/jobs lists recent jobs descending by started_at", async () => {
  const { app, db } = buildApp();
  db.prepare(
    "INSERT INTO manga_sync_jobs (id, source, status, started_at) VALUES (?, ?, 'done', ?)",
  ).run("job-old", "onepiecetube", 1000);
  db.prepare(
    "INSERT INTO manga_sync_jobs (id, source, status, started_at) VALUES (?, ?, 'done', ?)",
  ).run("job-new", "onepiecetube", 9000);
  const r = await app.inject({ method: "GET", url: "/api/manga/sync/jobs" });
  expect(r.statusCode).toBe(200);
  const body = r.json() as { id: string }[];
  expect(body[0].id).toBe("job-new");
  expect(body[1].id).toBe("job-old");
});

test("GET /api/manga/sync/jobs/:id returns single job", async () => {
  const { app, db } = buildApp();
  db.prepare(
    "INSERT INTO manga_sync_jobs (id, source, status, started_at) VALUES (?, ?, 'done', ?)",
  ).run("job-1", "onepiecetube", 1000);
  const r = await app.inject({ method: "GET", url: "/api/manga/sync/jobs/job-1" });
  expect(r.statusCode).toBe(200);
  expect((r.json() as { id: string }).id).toBe("job-1");
});

test("GET /api/manga/sync/jobs/:id returns 404 for unknown id", async () => {
  const { app } = buildApp();
  const r = await app.inject({ method: "GET", url: "/api/manga/sync/jobs/no-such-job" });
  expect(r.statusCode).toBe(404);
});

// Fix #1 — POST /api/manga/chapters/:id/sync

test("POST /api/manga/chapters/:id/sync returns 202 when chapter exists", async () => {
  const { app, db } = buildApp();
  // Use a source that has a real registered adapter so the endpoint can resolve it
  db.prepare("INSERT INTO manga_series (id, source, source_url, title, added_at) VALUES (?, ?, ?, ?, ?)")
    .run("onepiecetube:x", "onepiecetube", "https://onepiece.test/x", "X2", Date.now());
  db.prepare("INSERT INTO manga_chapters (id, series_id, number, source_url, added_at) VALUES (?, ?, ?, ?, ?)")
    .run("onepiecetube:x:1", "onepiecetube:x", 1, "https://onepiece.test/x/1", Date.now());
  const r = await app.inject({ method: "POST", url: "/api/manga/chapters/onepiecetube:x:1/sync" });
  expect(r.statusCode).toBe(202);
});

test("POST /api/manga/chapters/:id/sync returns 404 when chapter unknown", async () => {
  const { app } = buildApp();
  const r = await app.inject({ method: "POST", url: "/api/manga/chapters/no:such:thing/sync" });
  expect(r.statusCode).toBe(404);
});

test("POST /api/manga/chapters/:id/sync returns 400 when adapter for source is unknown", async () => {
  const { app, db } = buildApp();
  // Insert a chapter for a series whose source has no adapter
  db.prepare("INSERT INTO manga_series (id, source, source_url, title, added_at) VALUES (?, ?, ?, ?, ?)")
    .run("zzz:x", "zzz", "https://x", "X", Date.now());
  db.prepare("INSERT INTO manga_chapters (id, series_id, number, source_url, added_at) VALUES (?, ?, ?, ?, ?)")
    .run("zzz:x:1", "zzz:x", 1, "https://x", Date.now());
  const r = await app.inject({ method: "POST", url: "/api/manga/chapters/zzz:x:1/sync" });
  expect(r.statusCode).toBe(400);
});
