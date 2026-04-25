import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import Fastify from "fastify";
import fastifyStatic from "@fastify/static";
import { describe, expect, it } from "vitest";
import { registerVideosRoutes } from "./videos.js";

describe("videos API", () => {
  it("serves MP4 with Content-Type and supports Range requests", async () => {
    const dir = mkdtempSync(join(tmpdir(), "hikari-videos-"));
    writeFileSync(join(dir, "vid1.mp4"), Buffer.alloc(10000, 0xaa));

    const app = Fastify();
    await app.register(fastifyStatic, { root: dir, prefix: "/videos/" });
    await registerVideosRoutes(app, { db: undefined as never, videoDir: dir });

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
    await registerVideosRoutes(app, { db: undefined as never, videoDir: dir });

    const res = await app.inject({ method: "GET", url: "/videos/nope.mp4" });
    expect(res.statusCode).toBe(404);
  });
});
