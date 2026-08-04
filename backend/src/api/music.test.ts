import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
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
  // Staffelung und Retry-Delays in Tests abschalten (keine echten Wartezeiten);
  // gezielte Tests überschreiben sie per deps.
  await registerMusicRoutes(app, { searchStaggerMs: [], retryDelaysMs: [0, 0], ...deps });
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

  it("queries all instances in parallel and uses the first valid payload", async () => {
    const fetchImpl = vi.fn()
      .mockRejectedValueOnce(new Error("timeout"))
      .mockResolvedValueOnce({ ok: false } as unknown as Response)
      .mockResolvedValueOnce(okJson({ items: [PIPED_ITEM] }));
    const app = await makeApp({ fetchImpl });
    const res = await app.inject({ method: "GET", url: "/music/search?q=rick" });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toHaveLength(1);
    expect(fetchImpl).toHaveBeenCalledTimes(4);
    await app.close();
  });

  it("lets a fast instance win over a slow one", async () => {
    const slow = new Promise<Response>((resolve) =>
      setTimeout(() => resolve(okJson({ items: [{ ...PIPED_ITEM, title: "Langsam" }] })), 100),
    );
    const fetchImpl = vi.fn().mockImplementation((url: string) =>
      url.startsWith("https://api.piped.private.coffee")
        ? slow
        : Promise.resolve(okJson({ items: [PIPED_ITEM] })),
    );
    const app = await makeApp({ fetchImpl });
    const res = await app.inject({ method: "GET", url: "/music/search?q=rick" });
    expect(res.statusCode).toBe(200);
    expect(res.json()[0].title).toBe("Never Gonna Give You Up");
    await app.close();
  });

  it("sets Cache-Control on search responses", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(okJson({ items: [PIPED_ITEM] }));
    const app = await makeApp({ fetchImpl });
    const res = await app.inject({ method: "GET", url: "/music/search?q=rick" });
    expect(res.headers["cache-control"]).toBe("public, max-age=300");
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
    // ein Suchdurchlauf = ein paralleler Aufruf pro Piped-Instanz
    expect(fetchImpl).toHaveBeenCalledTimes(4);
    await app.close();
  });

  it("defaults to the music_songs filter", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(okJson({ items: [PIPED_ITEM] }));
    const app = await makeApp({ fetchImpl });
    await app.inject({ method: "GET", url: "/music/search?q=rick" });
    expect(fetchImpl).toHaveBeenCalledWith(
      expect.stringContaining("filter=music_songs"),
      expect.anything(),
    );
    await app.close();
  });

  it("searches plain videos in audiobook, podcast and truecrime mode", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(okJson({ items: [PIPED_ITEM] }));
    const app = await makeApp({ fetchImpl });
    for (const mode of ["audiobook", "podcast", "truecrime"]) {
      const res = await app.inject({ method: "GET", url: `/music/search?q=rick&mode=${mode}` });
      expect(res.statusCode).toBe(200);
    }
    for (const call of fetchImpl.mock.calls) {
      expect(call[0]).toContain("filter=videos");
    }
    await app.close();
  });

  it("rejects an unknown mode", async () => {
    const app = await makeApp({ fetchImpl: vi.fn() });
    const res = await app.inject({ method: "GET", url: "/music/search?q=rick&mode=radio" });
    expect(res.statusCode).toBe(400);
    await app.close();
  });

  it("caches modes separately", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(okJson({ items: [PIPED_ITEM] }));
    const app = await makeApp({ fetchImpl, now: () => 1_000 });
    await app.inject({ method: "GET", url: "/music/search?q=rick" });
    await app.inject({ method: "GET", url: "/music/search?q=rick&mode=audiobook" });
    // zwei Suchdurchläufe à vier parallele Instanz-Aufrufe
    expect(fetchImpl).toHaveBeenCalledTimes(8);
    await app.close();
  });

  it("deduplicates identical concurrent searches", async () => {
    let release!: (value: Response) => void;
    const gate = new Promise<Response>((r) => { release = r; });
    const fetchImpl = vi.fn().mockReturnValue(gate);
    const app = await makeApp({ fetchImpl });
    const first = app.inject({ method: "GET", url: "/music/search?q=rick" });
    const second = app.inject({ method: "GET", url: "/music/search?q=RICK" });
    release(okJson({ items: [PIPED_ITEM] }));
    const [r1, r2] = await Promise.all([first, second]);
    expect(r1.statusCode).toBe(200);
    expect(r2.statusCode).toBe(200);
    // beide Requests teilen einen Suchdurchlauf = vier parallele Instanz-Aufrufe
    expect(fetchImpl).toHaveBeenCalledTimes(4);
    await app.close();
  });

  it("aborts the staggered starts once an instance has won", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(okJson({ items: [PIPED_ITEM] }));
    const app = await makeApp({ fetchImpl, searchStaggerMs: [0, 400, 1200, 2400] });
    const res = await app.inject({ method: "GET", url: "/music/search?q=rick" });
    expect(res.statusCode).toBe(200);
    // Instanz 1 gewinnt sofort und bricht die verzögerten Starts ab — ohne
    // echtes Abwarten der Staffelung.
    expect(fetchImpl).toHaveBeenCalledTimes(1);
    await app.close();
  });

  it("staggers the instance starts when nobody has won yet", async () => {
    vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout"] });
    try {
      const hanging = new Promise<Response>(() => {});
      const fetchImpl = vi.fn()
        .mockReturnValueOnce(hanging)
        .mockResolvedValue(okJson({ items: [PIPED_ITEM] }));
      const app = await makeApp({ fetchImpl, searchStaggerMs: [0, 400, 1200, 2400] });
      const req = app.inject({ method: "GET", url: "/music/search?q=rick" });
      await vi.advanceTimersByTimeAsync(0);
      expect(fetchImpl).toHaveBeenCalledTimes(1);
      await vi.advanceTimersByTimeAsync(399);
      expect(fetchImpl).toHaveBeenCalledTimes(1);
      await vi.advanceTimersByTimeAsync(1);
      // zweite Instanz startet erst nach 400 ms, die erste hängt noch
      expect(fetchImpl).toHaveBeenCalledTimes(2);
      const res = await req;
      expect(res.statusCode).toBe(200);
      // der Gewinner bricht ab: Instanzen 3+4 werden nie angefragt
      await vi.advanceTimersByTimeAsync(5000);
      expect(fetchImpl).toHaveBeenCalledTimes(2);
      await app.close();
    } finally {
      vi.useRealTimers();
    }
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

  it("force=1 bypasses the stream cache", async () => {
    const ytDlp = vi.fn().mockResolvedValue({ stdout: "https://cdn.example/a.m4a", stderr: "" });
    const app = await makeApp({ ytDlp, now: () => 1_000 });
    await app.inject({ method: "GET", url: "/music/stream/dQw4w9WgXcQ" });
    await app.inject({ method: "GET", url: "/music/stream/dQw4w9WgXcQ?force=1" });
    expect(ytDlp).toHaveBeenCalledTimes(2);
    await app.close();
  });

  it("deduplicates concurrent resolutions of the same video", async () => {
    let release!: (value: { stdout: string; stderr: string }) => void;
    const gate = new Promise<{ stdout: string; stderr: string }>((r) => { release = r; });
    const ytDlp = vi.fn().mockReturnValue(gate);
    const app = await makeApp({ ytDlp });
    const first = app.inject({ method: "GET", url: "/music/stream/dQw4w9WgXcQ" });
    const second = app.inject({ method: "GET", url: "/music/stream/dQw4w9WgXcQ" });
    release({ stdout: "https://cdn.example/a.m4a", stderr: "" });
    const [r1, r2] = await Promise.all([first, second]);
    expect(r1.statusCode).toBe(200);
    expect(r2.statusCode).toBe(200);
    expect(ytDlp).toHaveBeenCalledTimes(1);
    await app.close();
  });

  it("persists the stream cache across restarts", async () => {
    const dir = await mkdtemp(join(tmpdir(), "hikari-music-"));
    try {
      const streamCachePath = join(dir, "stream-cache.json");
      const ytDlp = vi.fn().mockResolvedValue({ stdout: "https://cdn.example/a.m4a", stderr: "" });
      const first = await makeApp({ ytDlp, streamCachePath });
      await first.inject({ method: "GET", url: "/music/stream/dQw4w9WgXcQ" });
      await first.close();

      const ytDlpAfterRestart = vi.fn();
      const restarted = await makeApp({ ytDlp: ytDlpAfterRestart, streamCachePath });
      const res = await restarted.inject({ method: "GET", url: "/music/stream/dQw4w9WgXcQ" });
      expect(res.statusCode).toBe(200);
      expect(res.json()).toEqual({ url: "https://cdn.example/a.m4a" });
      expect(ytDlpAfterRestart).not.toHaveBeenCalled();
      await restarted.close();
    } finally {
      await rm(dir, { recursive: true, force: true });
    }
  });
});

describe("GET /music/audio/:videoId", () => {
  const upstream = (status: number, body: string, headers: Record<string, string> = {}) =>
    new Response(body, {
      status,
      headers: {
        "content-type": "audio/mp4",
        "content-length": String(body.length),
        "accept-ranges": "bytes",
        ...headers,
      },
    });

  it("proxies the audio bytes from the resolved URL", async () => {
    const ytDlp = vi.fn().mockResolvedValue({ stdout: "https://cdn.example/audio.m4a\n", stderr: "" });
    const fetchImpl = vi.fn().mockResolvedValue(upstream(200, "AUDIOBYTES"));
    const app = await makeApp({ ytDlp, fetchImpl });
    const res = await app.inject({ method: "GET", url: "/music/audio/dQw4w9WgXcQ" });
    expect(res.statusCode).toBe(200);
    expect(res.body).toBe("AUDIOBYTES");
    expect(res.headers["content-type"]).toBe("audio/mp4");
    expect(res.headers["accept-ranges"]).toBe("bytes");
    expect(fetchImpl).toHaveBeenCalledWith("https://cdn.example/audio.m4a", expect.anything());
    await app.close();
  });

  it("forwards the Range header and passes 206 + Content-Range through", async () => {
    const ytDlp = vi.fn().mockResolvedValue({ stdout: "https://cdn.example/audio.m4a", stderr: "" });
    const fetchImpl = vi.fn().mockResolvedValue(
      upstream(206, "PART", { "content-range": "bytes 0-3/1000" }),
    );
    const app = await makeApp({ ytDlp, fetchImpl });
    const res = await app.inject({
      method: "GET",
      url: "/music/audio/dQw4w9WgXcQ",
      headers: { range: "bytes=0-3" },
    });
    expect(res.statusCode).toBe(206);
    expect(res.headers["content-range"]).toBe("bytes 0-3/1000");
    const [, init] = fetchImpl.mock.calls[0];
    expect((init as { headers: Record<string, string> }).headers).toMatchObject({ range: "bytes=0-3" });
    await app.close();
  });

  it("re-resolves once when the upstream URL is stale (403)", async () => {
    const ytDlp = vi.fn()
      .mockResolvedValueOnce({ stdout: "https://cdn.example/stale.m4a", stderr: "" })
      .mockResolvedValueOnce({ stdout: "https://cdn.example/fresh.m4a", stderr: "" });
    const fetchImpl = vi.fn()
      .mockResolvedValueOnce(upstream(403, "denied"))
      .mockResolvedValueOnce(upstream(200, "FRESH"));
    const app = await makeApp({ ytDlp, fetchImpl });
    const res = await app.inject({ method: "GET", url: "/music/audio/dQw4w9WgXcQ" });
    expect(res.statusCode).toBe(200);
    expect(res.body).toBe("FRESH");
    expect(ytDlp).toHaveBeenCalledTimes(2);
    expect(fetchImpl).toHaveBeenLastCalledWith("https://cdn.example/fresh.m4a", expect.anything());
    await app.close();
  });

  it("shares the stream URL cache with /music/stream", async () => {
    const ytDlp = vi.fn().mockResolvedValue({ stdout: "https://cdn.example/a.m4a", stderr: "" });
    const fetchImpl = vi.fn().mockResolvedValue(upstream(200, "X"));
    const app = await makeApp({ ytDlp, fetchImpl, now: () => 1_000 });
    await app.inject({ method: "GET", url: "/music/stream/dQw4w9WgXcQ" });
    await app.inject({ method: "GET", url: "/music/audio/dQw4w9WgXcQ" });
    expect(ytDlp).toHaveBeenCalledTimes(1);
    await app.close();
  });

  it("rejects malformed video ids", async () => {
    const app = await makeApp({ ytDlp: vi.fn(), fetchImpl: vi.fn() });
    const res = await app.inject({ method: "GET", url: "/music/audio/not-a-valid-id!!" });
    expect(res.statusCode).toBe(400);
    await app.close();
  });

  it("returns 502 when extraction fails", async () => {
    const ytDlp = vi.fn().mockRejectedValue(new Error("blocked"));
    const app = await makeApp({ ytDlp, fetchImpl: vi.fn() });
    const res = await app.inject({ method: "GET", url: "/music/audio/dQw4w9WgXcQ" });
    expect(res.statusCode).toBe(502);
    await app.close();
  });

  it("returns 502 when the upstream keeps failing", async () => {
    const ytDlp = vi.fn().mockResolvedValue({ stdout: "https://cdn.example/a.m4a", stderr: "" });
    const fetchImpl = vi.fn().mockResolvedValue(upstream(403, "denied"));
    const app = await makeApp({ ytDlp, fetchImpl });
    const res = await app.inject({ method: "GET", url: "/music/audio/dQw4w9WgXcQ" });
    expect(res.statusCode).toBe(502);
    await app.close();
  });

  it("retries a transient fetch error with the same URL before re-resolving", async () => {
    const ytDlp = vi.fn().mockResolvedValue({ stdout: "https://cdn.example/a.m4a", stderr: "" });
    const fetchImpl = vi.fn()
      .mockRejectedValueOnce(new Error("connection reset"))
      .mockResolvedValue(upstream(200, "RECOVERED"));
    const app = await makeApp({ ytDlp, fetchImpl });
    const res = await app.inject({ method: "GET", url: "/music/audio/dQw4w9WgXcQ" });
    expect(res.statusCode).toBe(200);
    expect(res.body).toBe("RECOVERED");
    // der Retry mit derselben URL reicht — kein teures zweites yt-dlp
    expect(ytDlp).toHaveBeenCalledTimes(1);
    expect(fetchImpl).toHaveBeenCalledTimes(2);
    await app.close();
  });

  it("times out a hanging upstream header phase instead of waiting forever", async () => {
    vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout"] });
    try {
      const ytDlp = vi.fn().mockResolvedValue({ stdout: "https://cdn.example/a.m4a", stderr: "" });
      // Fetch hängt in der Header-Phase und reagiert nur auf das Abort-Signal
      const fetchImpl = vi.fn().mockImplementation((_url: string, init: { signal: AbortSignal }) =>
        new Promise<Response>((_resolve, reject) => {
          init.signal.addEventListener("abort", () => reject(new Error("aborted")));
        }),
      );
      const app = await makeApp({ ytDlp, fetchImpl });
      const req = app.inject({ method: "GET", url: "/music/audio/dQw4w9WgXcQ" });
      // 2 Auflösungsrunden à 3 Versuche (1 + 2 Retries) mit je 12 s Header-Timeout
      await vi.advanceTimersByTimeAsync(6 * 12_000 + 1_000);
      const res = await req;
      expect(res.statusCode).toBe(502);
      expect(fetchImpl).toHaveBeenCalledTimes(6);
      expect(ytDlp).toHaveBeenCalledTimes(2);
      await app.close();
    } finally {
      vi.useRealTimers();
    }
  });
});

const CHANNEL_ID = "UCuAXFkgsw1L7xaCfnd5JJOw";

const PIPED_CHANNEL = {
  id: CHANNEL_ID,
  name: "Rick Astley",
  avatarUrl: "https://img.example/avatar.jpg",
  bannerUrl: "https://img.example/banner.jpg",
  description: "Official channel",
  subscriberCount: 4520000,
  verified: true,
  relatedStreams: [],
};

function streamItem(overrides: Record<string, unknown>) {
  return {
    url: "/watch?v=dQw4w9WgXcQ",
    type: "stream",
    title: "Song",
    uploaderName: "Rick Astley",
    uploaderUrl: `/channel/${CHANNEL_ID}`,
    duration: 213,
    views: 1000,
    ...overrides,
  };
}

describe("GET /music/artist/:channelId", () => {
  it("normalizes the piped channel", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(okJson(PIPED_CHANNEL));
    const app = await makeApp({ fetchImpl });
    const res = await app.inject({ method: "GET", url: `/music/artist/${CHANNEL_ID}` });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual({
      channelId: CHANNEL_ID,
      name: "Rick Astley",
      avatarUrl: "https://img.example/avatar.jpg",
      bannerUrl: "https://img.example/banner.jpg",
      subscriberCount: 4520000,
      description: "Official channel",
      verified: true,
    });
    expect(fetchImpl).toHaveBeenCalledWith(
      expect.stringContaining(`/channel/${CHANNEL_ID}`),
      expect.anything(),
    );
    await app.close();
  });

  it("fails over to the next instance", async () => {
    const fetchImpl = vi.fn()
      .mockRejectedValueOnce(new Error("timeout"))
      .mockResolvedValueOnce(okJson(PIPED_CHANNEL));
    const app = await makeApp({ fetchImpl });
    const res = await app.inject({ method: "GET", url: `/music/artist/${CHANNEL_ID}` });
    expect(res.statusCode).toBe(200);
    expect(res.json().name).toBe("Rick Astley");
    expect(fetchImpl).toHaveBeenCalledTimes(2);
    await app.close();
  });

  it("returns 502 when every instance fails", async () => {
    const fetchImpl = vi.fn().mockRejectedValue(new Error("down"));
    const app = await makeApp({ fetchImpl });
    const res = await app.inject({ method: "GET", url: `/music/artist/${CHANNEL_ID}` });
    expect(res.statusCode).toBe(502);
    await app.close();
  });

  it("rejects malformed channel ids", async () => {
    const app = await makeApp({ fetchImpl: vi.fn() });
    const res = await app.inject({ method: "GET", url: "/music/artist/bad!" });
    expect(res.statusCode).toBe(400);
    await app.close();
  });

  it("caches artist pages and sets Cache-Control", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(okJson(PIPED_CHANNEL));
    const app = await makeApp({ fetchImpl, now: () => 1_000 });
    const r1 = await app.inject({ method: "GET", url: `/music/artist/${CHANNEL_ID}` });
    const r2 = await app.inject({ method: "GET", url: `/music/artist/${CHANNEL_ID}` });
    expect(r1.statusCode).toBe(200);
    expect(r2.statusCode).toBe(200);
    expect(r1.headers["cache-control"]).toBe("public, max-age=300");
    expect(fetchImpl).toHaveBeenCalledTimes(1);
    await app.close();
  });

  it("deduplicates concurrent artist lookups", async () => {
    let release!: (value: Response) => void;
    const gate = new Promise<Response>((r) => { release = r; });
    const fetchImpl = vi.fn().mockReturnValue(gate);
    const app = await makeApp({ fetchImpl });
    const first = app.inject({ method: "GET", url: `/music/artist/${CHANNEL_ID}` });
    const second = app.inject({ method: "GET", url: `/music/artist/${CHANNEL_ID}` });
    release(okJson(PIPED_CHANNEL));
    const [r1, r2] = await Promise.all([first, second]);
    expect(r1.statusCode).toBe(200);
    expect(r2.statusCode).toBe(200);
    expect(fetchImpl).toHaveBeenCalledTimes(1);
    await app.close();
  });
});

describe("GET /music/artist/:channelId/top", () => {
  it("sorts by views and prefers the artist's own channel", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(okJson({
      items: [
        streamItem({ title: "Fremd, viele Views", uploaderUrl: "/channel/UCother000", views: 9000 }),
        streamItem({ title: "Eigen 1", views: 500 }),
        streamItem({ title: "Eigen 2", views: 3000 }),
        streamItem({ title: "Eigen 3", views: 100 }),
      ],
    }));
    const app = await makeApp({ fetchImpl });
    const res = await app.inject({
      method: "GET",
      url: `/music/artist/${CHANNEL_ID}/top?name=Rick%20Astley`,
    });
    expect(res.statusCode).toBe(200);
    // 3 eigene Treffer → nur der eigene Kanal, absteigend nach Views
    expect(res.json().map((t: { title: string }) => t.title)).toEqual([
      "Eigen 2", "Eigen 1", "Eigen 3",
    ]);
    expect(fetchImpl).toHaveBeenCalledWith(
      expect.stringContaining("filter=videos"),
      expect.anything(),
    );
    await app.close();
  });

  it("falls back to all streams when the channel has fewer than 3 hits", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(okJson({
      items: [
        streamItem({ title: "Fremd", uploaderUrl: "/channel/UCother000", views: 9000 }),
        streamItem({ title: "Eigen", views: 500 }),
        { url: "/watch?v=dQw4w9WgXcQ", type: "channel", title: "kein Stream" },
      ],
    }));
    const app = await makeApp({ fetchImpl });
    const res = await app.inject({
      method: "GET",
      url: `/music/artist/${CHANNEL_ID}/top?name=Rick%20Astley`,
    });
    expect(res.statusCode).toBe(200);
    expect(res.json().map((t: { title: string }) => t.title)).toEqual(["Fremd", "Eigen"]);
    await app.close();
  });

  it("maps uploaderUrl and views onto tracks", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(okJson({ items: [streamItem({ views: 12345 })] }));
    const app = await makeApp({ fetchImpl });
    const res = await app.inject({
      method: "GET",
      url: `/music/artist/${CHANNEL_ID}/top?name=Rick%20Astley`,
    });
    expect(res.json()[0]).toMatchObject({
      uploaderUrl: `/channel/${CHANNEL_ID}`,
      views: 12345,
    });
    await app.close();
  });

  it("returns 400 without name", async () => {
    const app = await makeApp({ fetchImpl: vi.fn() });
    const res = await app.inject({ method: "GET", url: `/music/artist/${CHANNEL_ID}/top` });
    expect(res.statusCode).toBe(400);
    await app.close();
  });

  it("returns 502 when every instance fails", async () => {
    const fetchImpl = vi.fn().mockRejectedValue(new Error("down"));
    const app = await makeApp({ fetchImpl });
    const res = await app.inject({
      method: "GET",
      url: `/music/artist/${CHANNEL_ID}/top?name=Rick`,
    });
    expect(res.statusCode).toBe(502);
    await app.close();
  });

  it("caches top tracks per channel and name with Cache-Control", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(okJson({ items: [streamItem({})] }));
    const app = await makeApp({ fetchImpl, now: () => 1_000 });
    const url = `/music/artist/${CHANNEL_ID}/top?name=Rick%20Astley`;
    const r1 = await app.inject({ method: "GET", url });
    const r2 = await app.inject({ method: "GET", url });
    expect(r1.statusCode).toBe(200);
    expect(r2.statusCode).toBe(200);
    expect(r1.headers["cache-control"]).toBe("public, max-age=300");
    expect(fetchImpl).toHaveBeenCalledTimes(1);
    await app.close();
  });
});

describe("GET /music/artist/:channelId/playlists", () => {
  const playlistItem = {
    url: "/playlist?list=PLabc123",
    type: "playlist",
    name: "Greatest Hits",
    thumbnail: "https://img.example/pl.jpg",
    uploaderName: "Rick Astley",
    uploaderUrl: `/channel/${CHANNEL_ID}`,
    videos: 42,
  };

  it("extracts playlistId and metadata", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(okJson({ items: [playlistItem] }));
    const app = await makeApp({ fetchImpl });
    const res = await app.inject({
      method: "GET",
      url: `/music/artist/${CHANNEL_ID}/playlists?name=Rick%20Astley`,
    });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual([{
      playlistId: "PLabc123",
      name: "Greatest Hits",
      thumbnailUrl: "https://img.example/pl.jpg",
      videoCount: 42,
      uploaderName: "Rick Astley",
    }]);
    expect(fetchImpl).toHaveBeenCalledWith(
      expect.stringContaining("filter=playlists"),
      expect.anything(),
    );
    await app.close();
  });

  it("prefers playlists of the artist's own channel", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(okJson({
      items: [
        { ...playlistItem, name: "Fremd", uploaderUrl: "/channel/UCother000", url: "/playlist?list=PLother" },
        playlistItem,
      ],
    }));
    const app = await makeApp({ fetchImpl });
    const res = await app.inject({
      method: "GET",
      url: `/music/artist/${CHANNEL_ID}/playlists?name=Rick%20Astley`,
    });
    expect(res.json().map((p: { name: string }) => p.name)).toEqual(["Greatest Hits"]);
    await app.close();
  });

  it("returns 400 without name", async () => {
    const app = await makeApp({ fetchImpl: vi.fn() });
    const res = await app.inject({ method: "GET", url: `/music/artist/${CHANNEL_ID}/playlists` });
    expect(res.statusCode).toBe(400);
    await app.close();
  });

  it("returns 502 when every instance fails", async () => {
    const fetchImpl = vi.fn().mockRejectedValue(new Error("down"));
    const app = await makeApp({ fetchImpl });
    const res = await app.inject({
      method: "GET",
      url: `/music/artist/${CHANNEL_ID}/playlists?name=Rick`,
    });
    expect(res.statusCode).toBe(502);
    await app.close();
  });
});

const CHANNEL_ITEM = {
  url: `/channel/${CHANNEL_ID}`,
  type: "channel",
  name: "Rick Astley",
  thumbnail: "https://img.example/rick.jpg",
  subscribers: 4520000,
};

const ALBUM_ITEM = {
  url: "/playlist?list=OLAK5uy_album",
  type: "playlist",
  name: "Whenever You Need Somebody",
  thumbnail: "https://img.example/album.jpg",
  uploaderName: "Rick Astley",
  videos: 10,
};

const PLAYLIST_ITEM = {
  url: "/playlist?list=PLhits123",
  type: "playlist",
  name: "80s Hits",
  thumbnail: "https://img.example/pl.jpg",
  uploaderName: "Someone Else",
  videos: 42,
};

/** Mock-Fetch, der die Piped-URL auswertet (/suggestions, /search?filter=..., /playlists). */
function urlDispatchMock(handlers: {
  suggestions?: unknown;
  filters?: Record<string, unknown[]>;
  playlist?: unknown;
}) {
  return vi.fn().mockImplementation((url: string) => {
    const parsed = new URL(url);
    if (parsed.pathname.endsWith("/suggestions")) {
      if (handlers.suggestions === undefined) return Promise.reject(new Error("down"));
      return Promise.resolve(okJson(handlers.suggestions));
    }
    if (parsed.pathname.startsWith("/playlists/")) {
      if (handlers.playlist === undefined) return Promise.reject(new Error("down"));
      return Promise.resolve(okJson(handlers.playlist));
    }
    if (parsed.pathname.endsWith("/search")) {
      const filter = parsed.searchParams.get("filter") ?? "";
      return Promise.resolve(okJson({ items: handlers.filters?.[filter] ?? [] }));
    }
    return Promise.reject(new Error(`unexpected url ${url}`));
  });
}

describe("GET /music/suggestions", () => {
  it("returns the suggestion strings", async () => {
    const fetchImpl = urlDispatchMock({ suggestions: ["rick astley", "rickroll"] });
    const app = await makeApp({ fetchImpl });
    const res = await app.inject({ method: "GET", url: "/music/suggestions?q=rick" });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual(["rick astley", "rickroll"]);
    expect(res.headers["cache-control"]).toBe("public, max-age=300");
    expect(fetchImpl).toHaveBeenCalledWith(
      expect.stringContaining("/suggestions?query=rick"),
      expect.anything(),
    );
    await app.close();
  });

  it("returns an empty list instead of 502 when every instance fails", async () => {
    const fetchImpl = vi.fn().mockRejectedValue(new Error("down"));
    const app = await makeApp({ fetchImpl });
    const res = await app.inject({ method: "GET", url: "/music/suggestions?q=rick" });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual([]);
    await app.close();
  });

  it("returns 400 without q", async () => {
    const app = await makeApp({ fetchImpl: vi.fn() });
    const res = await app.inject({ method: "GET", url: "/music/suggestions" });
    expect(res.statusCode).toBe(400);
    await app.close();
  });

  it("serves repeat queries from cache", async () => {
    const fetchImpl = urlDispatchMock({ suggestions: ["rick astley"] });
    const app = await makeApp({ fetchImpl, now: () => 1_000 });
    await app.inject({ method: "GET", url: "/music/suggestions?q=rick" });
    await app.inject({ method: "GET", url: "/music/suggestions?q=RICK" });
    // ein Durchlauf = ein paralleler Aufruf pro Piped-Instanz
    expect(fetchImpl).toHaveBeenCalledTimes(4);
    await app.close();
  });
});

describe("GET /music/search/full", () => {
  const fullMock = (artistName = "Rick Astley") =>
    urlDispatchMock({
      filters: {
        music_songs: [PIPED_ITEM],
        music_albums: [ALBUM_ITEM],
        music_artists: [{ ...CHANNEL_ITEM, name: artistName }],
        playlists: [PLAYLIST_ITEM],
      },
    });

  it("returns the typed sections", async () => {
    const app = await makeApp({ fetchImpl: fullMock() });
    const res = await app.inject({ method: "GET", url: "/music/search/full?q=rick" });
    expect(res.statusCode).toBe(200);
    const body = res.json();
    expect(body.songs).toEqual([{
      videoId: "dQw4w9WgXcQ",
      title: "Never Gonna Give You Up",
      uploader: "Rick Astley",
      thumbnailUrl: "https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg",
      durationSeconds: 213,
    }]);
    expect(body.artists).toEqual([{
      channelId: CHANNEL_ID,
      name: "Rick Astley",
      thumbnailUrl: "https://img.example/rick.jpg",
      subscribers: 4520000,
    }]);
    expect(body.albums).toEqual([{
      playlistId: "OLAK5uy_album",
      name: "Whenever You Need Somebody",
      artistName: "Rick Astley",
      thumbnailUrl: "https://img.example/album.jpg",
      videoCount: 10,
    }]);
    expect(body.playlists).toEqual([{
      playlistId: "PLhits123",
      name: "80s Hits",
      uploaderName: "Someone Else",
      thumbnailUrl: "https://img.example/pl.jpg",
      videoCount: 42,
    }]);
    await app.close();
  });

  it("picks the artist as top result when the name matches the query", async () => {
    const app = await makeApp({ fetchImpl: fullMock() });
    const res = await app.inject({ method: "GET", url: "/music/search/full?q=rick" });
    expect(res.json().topResult).toMatchObject({
      type: "artist",
      channelId: CHANNEL_ID,
      name: "Rick Astley",
    });
    await app.close();
  });

  it("falls back to the first song as top result", async () => {
    const app = await makeApp({ fetchImpl: fullMock("Adele") });
    const res = await app.inject({ method: "GET", url: "/music/search/full?q=rick" });
    expect(res.json().topResult).toMatchObject({ type: "song", videoId: "dQw4w9WgXcQ" });
    await app.close();
  });

  it("returns empty sections for failed partial searches", async () => {
    const fetchImpl = urlDispatchMock({ filters: { music_songs: [PIPED_ITEM] } });
    const app = await makeApp({ fetchImpl });
    const res = await app.inject({ method: "GET", url: "/music/search/full?q=rick" });
    expect(res.statusCode).toBe(200);
    const body = res.json();
    expect(body.songs).toHaveLength(1);
    expect(body.artists).toEqual([]);
    expect(body.albums).toEqual([]);
    expect(body.playlists).toEqual([]);
    expect(body.topResult).toMatchObject({ type: "song" });
    await app.close();
  });

  it("returns 502 when every instance fails", async () => {
    const fetchImpl = vi.fn().mockRejectedValue(new Error("down"));
    const app = await makeApp({ fetchImpl });
    const res = await app.inject({ method: "GET", url: "/music/search/full?q=rick" });
    expect(res.statusCode).toBe(502);
    await app.close();
  });

  it("returns 400 without q", async () => {
    const app = await makeApp({ fetchImpl: vi.fn() });
    const res = await app.inject({ method: "GET", url: "/music/search/full" });
    expect(res.statusCode).toBe(400);
    await app.close();
  });
});

describe("GET /music/search/typed", () => {
  it("returns album objects for type=albums", async () => {
    const fetchImpl = urlDispatchMock({ filters: { music_albums: [ALBUM_ITEM] } });
    const app = await makeApp({ fetchImpl });
    const res = await app.inject({ method: "GET", url: "/music/search/typed?q=rick&type=albums" });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual([{
      playlistId: "OLAK5uy_album",
      name: "Whenever You Need Somebody",
      artistName: "Rick Astley",
      thumbnailUrl: "https://img.example/album.jpg",
      videoCount: 10,
    }]);
    expect(fetchImpl).toHaveBeenCalledWith(
      expect.stringContaining("filter=music_albums"),
      expect.anything(),
    );
    await app.close();
  });

  it("returns tracks for type=songs like /music/search?mode=music", async () => {
    const fetchImpl = urlDispatchMock({ filters: { music_songs: [PIPED_ITEM] } });
    const app = await makeApp({ fetchImpl });
    const res = await app.inject({ method: "GET", url: "/music/search/typed?q=rick&type=songs" });
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

  it("rejects an unknown type", async () => {
    const app = await makeApp({ fetchImpl: vi.fn() });
    const res = await app.inject({ method: "GET", url: "/music/search/typed?q=rick&type=genres" });
    expect(res.statusCode).toBe(400);
    await app.close();
  });

  it("returns 502 when every instance fails", async () => {
    const fetchImpl = vi.fn().mockRejectedValue(new Error("down"));
    const app = await makeApp({ fetchImpl });
    const res = await app.inject({ method: "GET", url: "/music/search/typed?q=rick&type=albums" });
    expect(res.statusCode).toBe(502);
    await app.close();
  });
});

describe("GET /music/playlist/:playlistId", () => {
  it("maps relatedStreams to tracks", async () => {
    const fetchImpl = urlDispatchMock({ playlist: { name: "Mix", relatedStreams: [PIPED_ITEM] } });
    const app = await makeApp({ fetchImpl });
    const res = await app.inject({ method: "GET", url: "/music/playlist/OLAK5uy_test123" });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual([{
      videoId: "dQw4w9WgXcQ",
      title: "Never Gonna Give You Up",
      uploader: "Rick Astley",
      thumbnailUrl: "https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg",
      durationSeconds: 213,
    }]);
    expect(fetchImpl).toHaveBeenCalledWith(
      expect.stringContaining("/playlists/OLAK5uy_test123"),
      expect.anything(),
    );
    await app.close();
  });

  it("rejects invalid playlist ids", async () => {
    const app = await makeApp({ fetchImpl: vi.fn() });
    const res = await app.inject({ method: "GET", url: "/music/playlist/bad!id" });
    expect(res.statusCode).toBe(400);
    await app.close();
  });

  it("returns 502 when every instance fails", async () => {
    const fetchImpl = vi.fn().mockRejectedValue(new Error("down"));
    const app = await makeApp({ fetchImpl });
    const res = await app.inject({ method: "GET", url: "/music/playlist/OLAK5uy_test123" });
    expect(res.statusCode).toBe(502);
    await app.close();
  });

  it("caches playlist tracks", async () => {
    const fetchImpl = urlDispatchMock({ playlist: { relatedStreams: [PIPED_ITEM] } });
    const app = await makeApp({ fetchImpl, now: () => 1_000 });
    await app.inject({ method: "GET", url: "/music/playlist/OLAK5uy_test123" });
    await app.inject({ method: "GET", url: "/music/playlist/OLAK5uy_test123" });
    // ein Durchlauf = ein paralleler Aufruf pro Piped-Instanz
    expect(fetchImpl).toHaveBeenCalledTimes(4);
    await app.close();
  });
});
