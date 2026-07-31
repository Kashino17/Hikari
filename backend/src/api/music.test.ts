import { describe, expect, it, vi } from "vitest";
import Fastify from "fastify";
import { registerMusicRoutes, type MusicDeps } from "./music.js";

const PIPED_ITEM = {
  url: "/watch?v=dQw4w9WgXcQ",
  type: "stream",
  title: "Never Gonna Give You Up",
  uploaderName: "Rick Astley",
  thumbnail: "//img.example/thumb.jpg",
  duration: 213,
};

function okJson(body: unknown): Response {
  return { ok: true, json: async () => body } as unknown as Response;
}

async function makeApp(deps: MusicDeps) {
  const app = Fastify();
  await registerMusicRoutes(app, deps);
  return app;
}

describe("GET /music/search", () => {
  it("normalizes piped items", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(okJson({ items: [PIPED_ITEM] }));
    const app = await makeApp({ fetchImpl });
    const res = await app.inject({ method: "GET", url: "/music/search?q=rick" });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual([{
      videoId: "dQw4w9WgXcQ",
      title: "Never Gonna Give You Up",
      uploader: "Rick Astley",
      thumbnailUrl: "https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg",
      durationSeconds: 213,
    }]);
    await app.close();
  });

  it("fails over to the next instance when the first is dead", async () => {
    const fetchImpl = vi.fn()
      .mockRejectedValueOnce(new Error("timeout"))
      .mockResolvedValueOnce({ ok: false } as unknown as Response)
      .mockResolvedValueOnce(okJson({ items: [PIPED_ITEM] }));
    const app = await makeApp({ fetchImpl });
    const res = await app.inject({ method: "GET", url: "/music/search?q=rick" });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toHaveLength(1);
    expect(fetchImpl).toHaveBeenCalledTimes(3);
    await app.close();
  });

  it("returns 502 when every instance fails", async () => {
    const fetchImpl = vi.fn().mockRejectedValue(new Error("down"));
    const app = await makeApp({ fetchImpl });
    const res = await app.inject({ method: "GET", url: "/music/search?q=rick" });
    expect(res.statusCode).toBe(502);
    await app.close();
  });

  it("returns 400 without q", async () => {
    const app = await makeApp({ fetchImpl: vi.fn() });
    const res = await app.inject({ method: "GET", url: "/music/search" });
    expect(res.statusCode).toBe(400);
    await app.close();
  });

  it("serves repeat queries from cache", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(okJson({ items: [PIPED_ITEM] }));
    const app = await makeApp({ fetchImpl, now: () => 1_000 });
    await app.inject({ method: "GET", url: "/music/search?q=rick" });
    await app.inject({ method: "GET", url: "/music/search?q=RICK" });
    expect(fetchImpl).toHaveBeenCalledTimes(1);
    await app.close();
  });
});

describe("GET /music/stream/:videoId", () => {
  it("returns the yt-dlp audio URL", async () => {
    const ytDlp = vi.fn().mockResolvedValue({ stdout: "https://cdn.example/audio.m4a\n", stderr: "" });
    const app = await makeApp({ ytDlp });
    const res = await app.inject({ method: "GET", url: "/music/stream/dQw4w9WgXcQ" });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual({ url: "https://cdn.example/audio.m4a" });
    expect(ytDlp).toHaveBeenCalledWith(
      expect.arrayContaining(["-g", "https://www.youtube.com/watch?v=dQw4w9WgXcQ"]),
      expect.objectContaining({ maxRetries: 1 }),
    );
    await app.close();
  });

  it("rejects malformed video ids", async () => {
    const app = await makeApp({ ytDlp: vi.fn() });
    const res = await app.inject({ method: "GET", url: "/music/stream/not-a-valid-id!!" });
    expect(res.statusCode).toBe(400);
    await app.close();
  });

  it("returns 502 when extraction fails", async () => {
    const ytDlp = vi.fn().mockRejectedValue(new Error("blocked"));
    const app = await makeApp({ ytDlp });
    const res = await app.inject({ method: "GET", url: "/music/stream/dQw4w9WgXcQ" });
    expect(res.statusCode).toBe(502);
    await app.close();
  });

  it("caches stream URLs per video", async () => {
    const ytDlp = vi.fn().mockResolvedValue({ stdout: "https://cdn.example/a.m4a", stderr: "" });
    const app = await makeApp({ ytDlp, now: () => 1_000 });
    await app.inject({ method: "GET", url: "/music/stream/dQw4w9WgXcQ" });
    await app.inject({ method: "GET", url: "/music/stream/dQw4w9WgXcQ" });
    expect(ytDlp).toHaveBeenCalledTimes(1);
    await app.close();
  });
});
