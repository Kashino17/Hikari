import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import fastifyStatic from "@fastify/static";
import Fastify from "fastify";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { importDirectLink, scrapeImportLinksFromPage } from "../import/manual-import.js";
import { registerVideosRoutes } from "./videos.js";

vi.mock("../import/manual-import.js", () => ({
  fetchImportMetadata: vi.fn(),
  importDirectLink: vi.fn().mockResolvedValue({ url: "https://video.example/1", status: "ok" }),
  scrapeImportLinksFromPage: vi.fn(),
}));

describe("videos API", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(importDirectLink).mockResolvedValue({
      url: "https://video.example/1",
      status: "ok",
    });
  });

  it("serves MP4 with Content-Type and supports Range requests", async () => {
    const dir = mkdtempSync(join(tmpdir(), "hikari-videos-"));
    writeFileSync(join(dir, "vid1.mp4"), Buffer.alloc(10000, 0xaa));

    const app = Fastify();
    await app.register(fastifyStatic, { root: dir, prefix: "/videos/" });
    await registerVideosRoutes(app, { db: undefined as never, videoDir: dir, extractor: null });

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

  it("returns 404 for missing file", async () => {
    const dir = mkdtempSync(join(tmpdir(), "hikari-videos-empty-"));
    const app = Fastify();
    await app.register(fastifyStatic, { root: dir, prefix: "/videos/" });
    await registerVideosRoutes(app, { db: undefined as never, videoDir: dir, extractor: null });

    const res = await app.inject({ method: "GET", url: "/videos/nope.mp4" });
    expect(res.statusCode).toBe(404);
  });

  it("queues every direct URL from the bulk import body", async () => {
    const dir = mkdtempSync(join(tmpdir(), "hikari-import-api-"));
    const db = {} as never;
    const app = Fastify();
    await registerVideosRoutes(app, { db, videoDir: dir, extractor: null });

    const res = await app.inject({
      method: "POST",
      url: "/videos/import",
      payload: {
        urls: ["https://video.example/1", " https://video.example/2 ", "https://video.example/1"],
      },
    });

    expect(res.statusCode).toBe(202);
    expect(res.json()).toMatchObject({ status: "queued", queued: 2 });

    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(importDirectLink).toHaveBeenCalledTimes(2);
    expect(importDirectLink).toHaveBeenNthCalledWith(
      1,
      db,
      "https://video.example/1",
      dir,
      undefined,
    );
    expect(importDirectLink).toHaveBeenNthCalledWith(
      2,
      db,
      "https://video.example/2",
      dir,
      undefined,
    );
  });

  it("scrapes a page URL before queueing imports when scrapeLinks is true", async () => {
    const dir = mkdtempSync(join(tmpdir(), "hikari-import-page-api-"));
    const db = {} as never;
    vi.mocked(scrapeImportLinksFromPage).mockResolvedValue({
      sourceUrl: "https://library.example/list",
      links: ["https://video.example/1", "https://video.example/2", "https://video.example/1"],
      totalFound: 3,
      limited: false,
    });

    const app = Fastify();
    await registerVideosRoutes(app, { db, videoDir: dir, extractor: null });

    const res = await app.inject({
      method: "POST",
      url: "/videos/import",
      payload: {
        urls: ["https://library.example/list"],
        scrapeLinks: true,
      },
    });

    expect(res.statusCode).toBe(202);
    expect(res.json()).toMatchObject({ status: "queued", queued: 2 });
    expect(scrapeImportLinksFromPage).toHaveBeenCalledWith("https://library.example/list");

    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(importDirectLink).toHaveBeenCalledTimes(2);
    expect(importDirectLink).toHaveBeenNthCalledWith(
      1,
      db,
      "https://video.example/1",
      dir,
      undefined,
    );
    expect(importDirectLink).toHaveBeenNthCalledWith(
      2,
      db,
      "https://video.example/2",
      dir,
      undefined,
    );
  });
});
