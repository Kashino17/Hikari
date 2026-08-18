import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import Fastify from "fastify";
import { describe, expect, it, vi } from "vitest";
import { trackCountFrom } from "./music-innertube.js";
import { type MusicDeps, registerMusicRoutes } from "./music.js";

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

/**
 * Baut die Test-App. Innertube-Aufrufe (music.youtube.com) werden standard-
 * mäßig VOR dem übergebenen fetchImpl abgefangen und abgelehnt — die
 * bestehenden Piped-Tests (Call-Counts, URL-Assertions) sehen sie so nie.
 * Innertube-Tests setzen `innertube: true` und routen selbst per URL.
 */
async function makeApp(deps: MusicDeps, opts: { innertube?: boolean } = {}) {
  const app = Fastify();
  const inner = deps.fetchImpl;
  const routed: typeof fetch | undefined =
    inner && !opts.innertube
      ? (((url: unknown, init?: unknown) =>
          String(url).includes("music.youtube.com")
            ? Promise.reject(new Error("innertube disabled in test"))
            : (inner as unknown as (u: unknown, i?: unknown) => Promise<Response>)(
                url,
                init,
              )) as unknown as typeof fetch)
      : inner;
  // Staffelung und Retry-Delays in Tests abschalten (keine echten Wartezeiten);
  // gezielte Tests überschreiben sie per deps.
  await registerMusicRoutes(app, {
    searchStaggerMs: [],
    retryDelaysMs: [0, 0],
    ...deps,
    ...(routed ? { fetchImpl: routed } : {}),
  });
  return app;
}

describe("GET /music/search", () => {
  it("normalizes piped items", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(okJson({ items: [PIPED_ITEM] }));
    const app = await makeApp({ fetchImpl });
    const res = await app.inject({ method: "GET", url: "/music/search?q=rick" });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual([
      {
        videoId: "dQw4w9WgXcQ",
        title: "Never Gonna Give You Up",
        uploader: "Rick Astley",
        thumbnailUrl: "https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg",
        durationSeconds: 213,
      },
    ]);
    await app.close();
  });

  it("queries all instances in parallel and uses the first valid payload", async () => {
    const fetchImpl = vi
      .fn()
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
    const fetchImpl = vi
      .fn()
      .mockImplementation((url: string) =>
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
    const gate = new Promise<Response>((r) => {
      release = r;
    });
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
      const fetchImpl = vi
        .fn()
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
    const ytDlp = vi
      .fn()
      .mockResolvedValue({ stdout: "https://cdn.example/audio.m4a\n", stderr: "" });
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
    const gate = new Promise<{ stdout: string; stderr: string }>((r) => {
      release = r;
    });
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
    const ytDlp = vi
      .fn()
      .mockResolvedValue({ stdout: "https://cdn.example/audio.m4a\n", stderr: "" });
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
    const ytDlp = vi
      .fn()
      .mockResolvedValue({ stdout: "https://cdn.example/audio.m4a", stderr: "" });
    const fetchImpl = vi
      .fn()
      .mockResolvedValue(upstream(206, "PART", { "content-range": "bytes 0-3/1000" }));
    const app = await makeApp({ ytDlp, fetchImpl });
    const res = await app.inject({
      method: "GET",
      url: "/music/audio/dQw4w9WgXcQ",
      headers: { range: "bytes=0-3" },
    });
    expect(res.statusCode).toBe(206);
    expect(res.headers["content-range"]).toBe("bytes 0-3/1000");
    const [, init] = fetchImpl.mock.calls[0];
    expect((init as { headers: Record<string, string> }).headers).toMatchObject({
      range: "bytes=0-3",
    });
    await app.close();
  });

  it("re-resolves once when the upstream URL is stale (403)", async () => {
    const ytDlp = vi
      .fn()
      .mockResolvedValueOnce({ stdout: "https://cdn.example/stale.m4a", stderr: "" })
      .mockResolvedValueOnce({ stdout: "https://cdn.example/fresh.m4a", stderr: "" });
    // URL-abhängig statt sequenziell: die stale URL 403t auch den
    // Chunk-Fallback des Proxys, erst die frische URL liefert.
    const fetchImpl = vi
      .fn()
      .mockImplementation(async (url: string) =>
        url.includes("fresh") ? upstream(200, "FRESH") : upstream(403, "denied"),
      );
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
    const fetchImpl = vi
      .fn()
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
      const fetchImpl = vi.fn().mockImplementation(
        (_url: string, init: { signal: AbortSignal }) =>
          new Promise<Response>((_resolve, reject) => {
            init.signal.addEventListener("abort", () => reject(new Error("aborted")));
          }),
      );
      const app = await makeApp({ ytDlp, fetchImpl });
      const req = app.inject({ method: "GET", url: "/music/audio/dQw4w9WgXcQ" });
      // 3 Auflösungsrunden à 3 Versuche (1 + 2 Retries) mit je 12 s Header-Timeout
      await vi.advanceTimersByTimeAsync(9 * 12_000 + 1_000);
      const res = await req;
      expect(res.statusCode).toBe(502);
      expect(fetchImpl).toHaveBeenCalledTimes(9);
      expect(ytDlp).toHaveBeenCalledTimes(3);
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
    const fetchImpl = vi
      .fn()
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
    const gate = new Promise<Response>((r) => {
      release = r;
    });
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
    const fetchImpl = vi.fn().mockResolvedValue(
      okJson({
        items: [
          streamItem({
            title: "Fremd, viele Views",
            uploaderUrl: "/channel/UCother000",
            views: 9000,
          }),
          streamItem({ title: "Eigen 1", views: 500 }),
          streamItem({ title: "Eigen 2", views: 3000 }),
          streamItem({ title: "Eigen 3", views: 100 }),
        ],
      }),
    );
    const app = await makeApp({ fetchImpl });
    const res = await app.inject({
      method: "GET",
      url: `/music/artist/${CHANNEL_ID}/top?name=Rick%20Astley`,
    });
    expect(res.statusCode).toBe(200);
    // 3 eigene Treffer → nur der eigene Kanal, absteigend nach Views
    expect(res.json().map((t: { title: string }) => t.title)).toEqual([
      "Eigen 2",
      "Eigen 1",
      "Eigen 3",
    ]);
    expect(fetchImpl).toHaveBeenCalledWith(
      expect.stringContaining("filter=videos"),
      expect.anything(),
    );
    await app.close();
  });

  it("keeps only own-channel streams — no foreign uploaders on artist pages", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(
      okJson({
        items: [
          streamItem({ title: "Fremd", uploaderUrl: "/channel/UCother000", views: 9000 }),
          streamItem({ title: "Eigen", views: 500 }),
          { url: "/watch?v=dQw4w9WgXcQ", type: "channel", title: "kein Stream" },
        ],
      }),
    );
    const app = await makeApp({ fetchImpl });
    const res = await app.inject({
      method: "GET",
      url: `/music/artist/${CHANNEL_ID}/top?name=Rick%20Astley`,
    });
    expect(res.statusCode).toBe(200);
    // Fremde Uploader fliegen raus — auch wenn dadurch weniger übrig bleibt.
    expect(res.json().map((t: { title: string }) => t.title)).toEqual(["Eigen"]);
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
    expect(res.json()).toEqual([
      {
        playlistId: "PLabc123",
        name: "Greatest Hits",
        thumbnailUrl: "https://img.example/pl.jpg",
        videoCount: 42,
        uploaderName: "Rick Astley",
      },
    ]);
    expect(fetchImpl).toHaveBeenCalledWith(
      expect.stringContaining("filter=playlists"),
      expect.anything(),
    );
    await app.close();
  });

  it("prefers playlists of the artist's own channel", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(
      okJson({
        items: [
          {
            ...playlistItem,
            name: "Fremd",
            uploaderUrl: "/channel/UCother000",
            url: "/playlist?list=PLother",
          },
          playlistItem,
        ],
      }),
    );
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

/** Erwarteter Query-Vorschlag der neuen Rich-Shape. */
function querySuggestion(text: string) {
  return {
    text,
    kind: "query",
    thumbnailUrl: null,
    subtitle: null,
    videoId: null,
    channelId: null,
    playlistId: null,
  };
}

describe("GET /music/suggestions", () => {
  it("maps Piped fallback strings to plain query suggestions", async () => {
    const fetchImpl = urlDispatchMock({ suggestions: ["rick astley", "rickroll"] });
    const app = await makeApp({ fetchImpl });
    const res = await app.inject({ method: "GET", url: "/music/suggestions?q=rick" });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual([querySuggestion("rick astley"), querySuggestion("rickroll")]);
    expect(res.headers["cache-control"]).toBe("public, max-age=300");
    expect(fetchImpl).toHaveBeenCalledWith(
      expect.stringContaining("/suggestions?query=rick"),
      expect.anything(),
    );
    await app.close();
  });

  it("mixes query and entity suggestions with thumbnails in original order", async () => {
    const fetchImpl = itOnlyFetch({
      contents: [
        {
          searchSuggestionsSectionRenderer: {
            contents: [
              {
                searchSuggestionRenderer: {
                  navigationEndpoint: { searchEndpoint: { query: "imagine dragons" } },
                },
              },
            ],
          },
        },
        {
          searchSuggestionsSectionRenderer: {
            contents: [
              {
                musicResponsiveListItemRenderer: {
                  thumbnail: {
                    musicThumbnailRenderer: {
                      thumbnail: {
                        thumbnails: [
                          { url: "https://img.example/artist-60.jpg" },
                          { url: "https://img.example/artist-120.jpg" },
                        ],
                      },
                    },
                  },
                  flexColumns: [
                    {
                      musicResponsiveListItemFlexColumnRenderer: {
                        text: { runs: [{ text: "Imagine Dragons" }] },
                      },
                    },
                    {
                      musicResponsiveListItemFlexColumnRenderer: {
                        text: { runs: [{ text: "Künstler" }] },
                      },
                    },
                  ],
                  navigationEndpoint: { browseEndpoint: { browseId: CHANNEL_ID } },
                },
              },
              {
                musicResponsiveListItemRenderer: {
                  flexColumns: [
                    {
                      musicResponsiveListItemFlexColumnRenderer: {
                        text: {
                          runs: [
                            {
                              text: "Believer",
                              navigationEndpoint: { watchEndpoint: { videoId: "belieVid001" } },
                            },
                          ],
                        },
                      },
                    },
                    {
                      musicResponsiveListItemFlexColumnRenderer: {
                        text: {
                          runs: [{ text: "Song" }, { text: " • " }, { text: "Imagine Dragons" }],
                        },
                      },
                    },
                  ],
                },
              },
            ],
          },
        },
      ],
    });
    const app = await makeApp({ fetchImpl }, { innertube: true });
    const res = await app.inject({ method: "GET", url: "/music/suggestions?q=imagine" });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual([
      querySuggestion("imagine dragons"),
      {
        text: "Imagine Dragons",
        kind: "artist",
        thumbnailUrl: "https://img.example/artist-60.jpg",
        subtitle: "Künstler",
        videoId: null,
        channelId: CHANNEL_ID,
        playlistId: null,
      },
      {
        text: "Believer",
        kind: "song",
        thumbnailUrl: "https://i.ytimg.com/vi/belieVid001/default.jpg",
        subtitle: "Song • Imagine Dragons",
        videoId: "belieVid001",
        channelId: null,
        playlistId: null,
      },
    ]);
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
    expect(body.songs).toEqual([
      {
        videoId: "dQw4w9WgXcQ",
        title: "Never Gonna Give You Up",
        uploader: "Rick Astley",
        thumbnailUrl: "https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg",
        durationSeconds: 213,
      },
    ]);
    expect(body.artists).toEqual([
      {
        channelId: CHANNEL_ID,
        name: "Rick Astley",
        thumbnailUrl: "https://img.example/rick.jpg",
        subscribers: 4520000,
      },
    ]);
    expect(body.albums).toEqual([
      {
        playlistId: "OLAK5uy_album",
        name: "Whenever You Need Somebody",
        artistName: "Rick Astley",
        thumbnailUrl: "https://img.example/album.jpg",
        videoCount: 10,
      },
    ]);
    expect(body.playlists).toEqual([
      {
        playlistId: "PLhits123",
        name: "80s Hits",
        uploaderName: "Someone Else",
        thumbnailUrl: "https://img.example/pl.jpg",
        videoCount: 42,
      },
    ]);
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
    expect(res.json()).toEqual([
      {
        playlistId: "OLAK5uy_album",
        name: "Whenever You Need Somebody",
        artistName: "Rick Astley",
        thumbnailUrl: "https://img.example/album.jpg",
        videoCount: 10,
      },
    ]);
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
    expect(res.json()).toEqual([
      {
        videoId: "dQw4w9WgXcQ",
        title: "Never Gonna Give You Up",
        uploader: "Rick Astley",
        thumbnailUrl: "https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg",
        durationSeconds: 213,
      },
    ]);
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
    expect(res.json()).toEqual([
      {
        videoId: "dQw4w9WgXcQ",
        title: "Never Gonna Give You Up",
        uploader: "Rick Astley",
        thumbnailUrl: "https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg",
        durationSeconds: 213,
      },
    ]);
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

// ————— Innertube (YouTube Music API) —————

/** fetchImpl-Router: Innertube-URLs → Fixture, alles andere (Piped) → Fehler. */
function itOnlyFetch(body: unknown) {
  return vi.fn((url: unknown) =>
    String(url).includes("music.youtube.com")
      ? Promise.resolve(okJson(body))
      : Promise.reject(new Error("piped down")),
  );
}

function itRun(text: string, browseId?: string) {
  return { text, ...(browseId ? { navigationEndpoint: { browseEndpoint: { browseId } } } : {}) };
}

function itSongItem(videoId: string, title: string, artist: string, channelId: string) {
  return {
    musicResponsiveListItemRenderer: {
      playlistItemData: { videoId },
      flexColumns: [
        { musicResponsiveListItemFlexColumnRenderer: { text: { runs: [itRun(title)] } } },
        {
          musicResponsiveListItemFlexColumnRenderer: {
            text: { runs: [itRun(artist, channelId), itRun(" • "), itRun("3:33")] },
          },
        },
      ],
    },
  };
}

describe("Innertube-first search", () => {
  it("serves typed song search from Innertube without touching Piped", async () => {
    const fetchImpl = itOnlyFetch({
      contents: [itSongItem("dQw4w9WgXcQ", "Never Gonna Give You Up", "Rick Astley", CHANNEL_ID)],
    });
    const app = await makeApp({ fetchImpl }, { innertube: true });
    const res = await app.inject({ method: "GET", url: "/music/search/typed?q=rick&type=songs" });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual([
      {
        videoId: "dQw4w9WgXcQ",
        title: "Never Gonna Give You Up",
        uploader: "Rick Astley",
        thumbnailUrl: "https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg",
        durationSeconds: 213,
        uploaderUrl: `/channel/${CHANNEL_ID}`,
        artists: [{ name: "Rick Astley", channelId: CHANNEL_ID }],
      },
    ]);
    await app.close();
  });

  it("parses all collaborating artists with their channel ids", async () => {
    const fetchImpl = itOnlyFetch({
      contents: [
        {
          musicResponsiveListItemRenderer: {
            playlistItemData: { videoId: "collabvid01" },
            flexColumns: [
              {
                musicResponsiveListItemFlexColumnRenderer: {
                  text: { runs: [itRun("Feature Song")] },
                },
              },
              {
                musicResponsiveListItemFlexColumnRenderer: {
                  text: {
                    runs: [
                      itRun("Artist A", CHANNEL_ID),
                      itRun(", "),
                      itRun("Artist B", "UCother0000000000000000"),
                      itRun(" & "),
                      itRun("C ohne Kanal"),
                      itRun(" • "),
                      itRun("3:33"),
                    ],
                  },
                },
              },
            ],
          },
        },
      ],
    });
    const app = await makeApp({ fetchImpl }, { innertube: true });
    const res = await app.inject({ method: "GET", url: "/music/search/typed?q=collab&type=songs" });
    expect(res.statusCode).toBe(200);
    const track = res.json()[0];
    expect(track.uploader).toBe("Artist A, Artist B & C ohne Kanal");
    expect(track.uploaderUrl).toBe(`/channel/${CHANNEL_ID}`);
    expect(track.artists).toEqual([
      { name: "Artist A", channelId: CHANNEL_ID },
      { name: "Artist B", channelId: "UCother0000000000000000" },
      { name: "C ohne Kanal", channelId: null },
    ]);
    await app.close();
  });

  it("falls back to Piped when Innertube is down (full search)", async () => {
    const fetchImpl = vi.fn((url: unknown) =>
      String(url).includes("music.youtube.com")
        ? Promise.reject(new Error("it down"))
        : Promise.resolve(okJson({ items: [PIPED_ITEM] })),
    );
    const app = await makeApp({ fetchImpl }, { innertube: true });
    const res = await app.inject({ method: "GET", url: "/music/search/full?q=rick" });
    expect(res.statusCode).toBe(200);
    expect(res.json().songs).toHaveLength(1);
    await app.close();
  });
});

describe("GET /music/related/:videoId", () => {
  it("returns the radio queue without the seed song", async () => {
    const fetchImpl = itOnlyFetch({
      contents: [
        {
          playlistPanelVideoRenderer: {
            videoId: "dQw4w9WgXcQ",
            title: { runs: [{ text: "Seed" }] },
            longBylineText: { runs: [{ text: "Rick Astley" }] },
            lengthText: { runs: [{ text: "3:33" }] },
          },
        },
        {
          playlistPanelVideoRenderer: {
            videoId: "abcabcabc12",
            title: { runs: [{ text: "Next Song" }] },
            longBylineText: { runs: [{ text: "Other Artist" }] },
            lengthText: { runs: [{ text: "3:03" }] },
          },
        },
      ],
    });
    const app = await makeApp({ fetchImpl }, { innertube: true });
    const res = await app.inject({ method: "GET", url: "/music/related/dQw4w9WgXcQ" });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual([
      {
        videoId: "abcabcab" + "c12",
        title: "Next Song",
        uploader: "Other Artist",
        thumbnailUrl: "https://i.ytimg.com/vi/abcabcabc12/mqdefault.jpg",
        durationSeconds: 183,
        artists: [{ name: "Other Artist", channelId: null }],
      },
    ]);
    await app.close();
  });

  it("502s when Innertube yields nothing", async () => {
    const fetchImpl = itOnlyFetch({});
    const app = await makeApp({ fetchImpl }, { innertube: true });
    const res = await app.inject({ method: "GET", url: "/music/related/dQw4w9WgXcQ" });
    expect(res.statusCode).toBe(502);
    await app.close();
  });
});

describe("GET /music/home", () => {
  it("parses carousels into typed sections (min 3 items)", async () => {
    const playlistTile = (id: string, name: string) => ({
      musicTwoRowItemRenderer: {
        title: { runs: [itRun(name, `VL${id}`)] },
        subtitle: { runs: [{ text: "Playlist" }] },
        thumbnailRenderer: {
          musicThumbnailRenderer: { thumbnail: { thumbnails: [{ url: "https://img/p.jpg" }] } },
        },
      },
    });
    const fetchImpl = itOnlyFetch({
      contents: [
        {
          musicCarouselShelfRenderer: {
            header: {
              musicCarouselShelfBasicHeaderRenderer: { title: { runs: [{ text: "Für dich" }] } },
            },
            contents: [
              playlistTile("PL1x", "Mix 1"),
              playlistTile("PL2x", "Mix 2"),
              playlistTile("PL3x", "Mix 3"),
            ],
          },
        },
        {
          musicCarouselShelfRenderer: {
            header: {
              musicCarouselShelfBasicHeaderRenderer: { title: { runs: [{ text: "Zu klein" }] } },
            },
            contents: [playlistTile("PL4x", "Mix 4")],
          },
        },
      ],
    });
    const app = await makeApp({ fetchImpl }, { innertube: true });
    const res = await app.inject({ method: "GET", url: "/music/home" });
    expect(res.statusCode).toBe(200);
    const body = res.json();
    expect(body.sections).toHaveLength(1);
    expect(body.sections[0].title).toBe("Für dich");
    expect(body.sections[0].items[0]).toEqual({
      kind: "playlist",
      playlist: {
        playlistId: "PL1x",
        name: "Mix 1",
        thumbnailUrl: "https://img/p.jpg",
        videoCount: 0,
        uploaderName: "Playlist",
      },
    });
    await app.close();
  });
});

describe("GET /music/artist/:channelId/page", () => {
  const artistBody = {
    header: {
      musicImmersiveHeaderRenderer: {
        title: { runs: [{ text: "Rick Astley" }] },
        description: { runs: [{ text: "Bio" }] },
        thumbnail: {
          musicThumbnailRenderer: { thumbnail: { thumbnails: [{ url: "https://img/a.jpg" }] } },
        },
        subscriptionButton: {
          subscribeButtonRenderer: {
            subscriberCountText: { runs: [{ text: "1,2 Mio. Abonnenten" }] },
          },
        },
      },
    },
    contents: [
      {
        musicShelfRenderer: {
          contents: [
            itSongItem("dQw4w9WgXcQ", "Never Gonna Give You Up", "Rick Astley", CHANNEL_ID),
          ],
        },
      },
      {
        musicCarouselShelfRenderer: {
          header: {
            musicCarouselShelfBasicHeaderRenderer: { title: { runs: [{ text: "Alben" }] } },
          },
          contents: [
            {
              musicTwoRowItemRenderer: {
                title: { runs: [itRun("Whenever You Need Somebody", "MPREb_abc")] },
                subtitle: { runs: [{ text: "Album" }, { text: " • " }, { text: "1987" }] },
                thumbnailRenderer: {
                  musicThumbnailRenderer: {
                    thumbnail: { thumbnails: [{ url: "https://img/album.jpg" }] },
                  },
                },
              },
            },
            {
              musicTwoRowItemRenderer: {
                title: { runs: [itRun("Lonely Single", "MPREb_s1")] },
                subtitle: { runs: [{ text: "Single" }, { text: " • " }, { text: "2020" }] },
              },
            },
            {
              musicTwoRowItemRenderer: {
                title: { runs: [itRun("Similar Act", "UCother0000000000000000")] },
                subtitle: { runs: [{ text: "Künstler" }] },
              },
            },
          ],
        },
      },
    ],
  };

  it("returns the full artist page from one Innertube browse", async () => {
    const fetchImpl = itOnlyFetch(artistBody);
    const app = await makeApp({ fetchImpl }, { innertube: true });
    const res = await app.inject({ method: "GET", url: `/music/artist/${CHANNEL_ID}/page` });
    expect(res.statusCode).toBe(200);
    const body = res.json();
    expect(body.artist).toMatchObject({
      channelId: CHANNEL_ID,
      name: "Rick Astley",
      subscriberCount: 1_200_000,
    });
    expect(body.topSongs).toHaveLength(1);
    expect(body.topSongs[0].uploader).toBe("Rick Astley");
    expect(body.albums).toEqual([
      expect.objectContaining({
        playlistId: "MPREb_abc",
        name: "Whenever You Need Somebody",
        year: 1987,
      }),
    ]);
    expect(body.singles).toEqual([expect.objectContaining({ playlistId: "MPREb_s1" })]);
    expect(body.related).toEqual([
      expect.objectContaining({ channelId: "UCother0000000000000000", name: "Similar Act" }),
    ]);
    await app.close();
  });

  it("serves /top from the same Innertube page — no name search, no foreign songs", async () => {
    const fetchImpl = itOnlyFetch(artistBody);
    const app = await makeApp({ fetchImpl }, { innertube: true });
    const res = await app.inject({
      method: "GET",
      url: `/music/artist/${CHANNEL_ID}/top?name=Rick%20Astley`,
    });
    expect(res.statusCode).toBe(200);
    expect(res.json().map((t: { title: string }) => t.title)).toEqual(["Never Gonna Give You Up"]);
    await app.close();
  });
});

describe("channel fallback for non-music channels", () => {
  // WEB_REMIX kennt normale Kanäle nur als Header — keine Songs-Shelves.
  const channelHeaderOnly = {
    header: {
      musicVisualHeaderRenderer: {
        title: { runs: [{ text: "Lucia Leona" }] },
        thumbnail: {
          musicThumbnailRenderer: { thumbnail: { thumbnails: [{ url: "https://img/l.jpg" }] } },
        },
      },
    },
    contents: [],
  };

  const webVideosBody = {
    metadata: { channelMetadataRenderer: { title: "Lucia Leona" } },
    contents: [
      {
        videoRenderer: {
          videoId: "vidvidvid01",
          title: { runs: [{ text: "Der Fall Peggy" }] },
          lengthText: { simpleText: "57:07" },
          viewCountText: { simpleText: "123.456 Aufrufe" },
        },
      },
      {
        // ohne Dauer = Livestream — darf nicht als Track auftauchen
        videoRenderer: {
          videoId: "vidvidvid02",
          title: { runs: [{ text: "Live dabei" }] },
        },
      },
    ],
  };

  it("fills topSongs from the channel's uploads via WEB browse", async () => {
    const fetchImpl = vi.fn((url: unknown) => {
      const u = String(url);
      if (u.includes("music.youtube.com")) return Promise.resolve(okJson(channelHeaderOnly));
      if (u.includes("www.youtube.com/youtubei")) return Promise.resolve(okJson(webVideosBody));
      return Promise.reject(new Error("piped down"));
    });
    const app = await makeApp({ fetchImpl }, { innertube: true });
    const res = await app.inject({
      method: "GET",
      url: `/music/artist/${CHANNEL_ID}/page?name=Lucia%20Leona`,
    });
    expect(res.statusCode).toBe(200);
    const body = res.json();
    expect(body.artist.name).toBe("Lucia Leona");
    expect(body.topSongs).toEqual([
      {
        videoId: "vidvidvid01",
        title: "Der Fall Peggy",
        uploader: "Lucia Leona",
        thumbnailUrl: "https://i.ytimg.com/vi/vidvidvid01/mqdefault.jpg",
        durationSeconds: 3427,
        uploaderUrl: `/channel/${CHANNEL_ID}`,
        views: 123456,
        artists: [{ name: "Lucia Leona", channelId: CHANNEL_ID }],
      },
    ]);
    await app.close();
  });

  it("parses the current lockupViewModel channel format", async () => {
    const lockupBody = {
      metadata: { channelMetadataRenderer: { title: "Lucia Leona" } },
      contents: [
        {
          lockupViewModel: {
            contentId: "lockuplck01",
            contentType: "LOCKUP_CONTENT_TYPE_VIDEO",
            metadata: {
              lockupMetadataViewModel: {
                title: { content: "Der Fall Maja" },
                metadata: {
                  contentMetadataViewModel: {
                    metadataRows: [{ metadataParts: [{ text: { content: "3.412 Aufrufe" } }] }],
                  },
                },
              },
            },
            contentImage: {
              thumbnailViewModel: {
                overlays: [{ thumbnailBadgeViewModel: { text: "56:31" } }],
              },
            },
          },
        },
      ],
    };
    const fetchImpl = vi.fn((url: unknown) => {
      const u = String(url);
      if (u.includes("music.youtube.com")) return Promise.resolve(okJson(channelHeaderOnly));
      if (u.includes("www.youtube.com/youtubei")) return Promise.resolve(okJson(lockupBody));
      return Promise.reject(new Error("piped down"));
    });
    const app = await makeApp({ fetchImpl }, { innertube: true });
    const res = await app.inject({ method: "GET", url: `/music/artist/${CHANNEL_ID}/page` });
    expect(res.statusCode).toBe(200);
    expect(res.json().topSongs).toEqual([
      {
        videoId: "lockuplck01",
        title: "Der Fall Maja",
        uploader: "Lucia Leona",
        thumbnailUrl: "https://i.ytimg.com/vi/lockuplck01/mqdefault.jpg",
        durationSeconds: 3391,
        uploaderUrl: `/channel/${CHANNEL_ID}`,
        views: 3412,
        artists: [{ name: "Lucia Leona", channelId: CHANNEL_ID }],
      },
    ]);
    await app.close();
  });

  it("falls back to the Piped name search, strictly own-channel only", async () => {
    const own = { ...PIPED_ITEM, uploaderUrl: `/channel/${CHANNEL_ID}` };
    const foreign = {
      ...PIPED_ITEM,
      url: "/watch?v=zzzzzzzzzz1",
      title: "Fremdes Video",
      uploaderUrl: "/channel/UCsomeoneelse0000000000",
    };
    const fetchImpl = vi.fn((url: unknown) => {
      const u = String(url);
      if (u.includes("music.youtube.com")) return Promise.resolve(okJson(channelHeaderOnly));
      if (u.includes("www.youtube.com/youtubei")) return Promise.reject(new Error("web down"));
      return Promise.resolve(okJson({ items: [foreign, own] }));
    });
    const app = await makeApp({ fetchImpl }, { innertube: true });
    const res = await app.inject({
      method: "GET",
      url: `/music/artist/${CHANNEL_ID}/top?name=Lucia%20Leona`,
    });
    expect(res.statusCode).toBe(200);
    const titles = res.json().map((t: { title: string }) => t.title);
    expect(titles).toEqual(["Never Gonna Give You Up"]);
    await app.close();
  });

  it("keeps topSongs empty without a name when the WEB browse fails", async () => {
    const fetchImpl = vi.fn((url: unknown) =>
      String(url).includes("music.youtube.com")
        ? Promise.resolve(okJson(channelHeaderOnly))
        : Promise.reject(new Error("down")),
    );
    const app = await makeApp({ fetchImpl }, { innertube: true });
    const res = await app.inject({ method: "GET", url: `/music/artist/${CHANNEL_ID}/page` });
    expect(res.statusCode).toBe(200);
    expect(res.json().topSongs).toEqual([]);
    await app.close();
  });
});

describe("channel page: latest, popular order and playlists tab", () => {
  const channelHeaderOnly = {
    header: {
      musicVisualHeaderRenderer: {
        title: { runs: [{ text: "Lucia Leona" }] },
      },
    },
    contents: [],
  };
  const video = (id: string, title: string, length: string, views: string) => ({
    videoRenderer: {
      videoId: id,
      title: { runs: [{ text: title }] },
      lengthText: { simpleText: length },
      viewCountText: { simpleText: views },
    },
  });
  const webVideosTwo = {
    metadata: { channelMetadataRenderer: { title: "Lucia Leona" } },
    contents: [
      video("vidvidvid01", "Neuestes Video", "10:00", "100 Aufrufe"),
      video("vidvidvid02", "Beliebtes Video", "20:00", "999.999 Aufrufe"),
    ],
  };
  const webPlaylistsTab = {
    metadata: { channelMetadataRenderer: { title: "Lucia Leona" } },
    contents: [
      {
        gridPlaylistRenderer: {
          playlistId: "PLchannel0001",
          title: { runs: [{ text: "Alle Fälle" }] },
          videoCountText: { runs: [{ text: "42" }] },
          thumbnail: { thumbnails: [{ url: "https://img/pl.jpg" }] },
        },
      },
    ],
  };

  /** Router: WEB-Browse nach params im Request-Body auf Videos-/Playlists-Tab verteilen. */
  function channelFetch(videosBody: unknown, playlistsBody: unknown, continuationBody?: unknown) {
    return vi.fn((url: unknown, init?: unknown) => {
      const u = String(url);
      if (u.includes("music.youtube.com")) return Promise.resolve(okJson(channelHeaderOnly));
      if (u.includes("www.youtube.com/youtubei")) {
        const body = JSON.parse(String((init as { body?: unknown })?.body ?? "{}")) as {
          params?: string;
          continuation?: string;
        };
        if (body.continuation) {
          return continuationBody
            ? Promise.resolve(okJson(continuationBody))
            : Promise.reject(new Error("no continuation"));
        }
        if (body.params?.startsWith("Eglw")) return Promise.resolve(okJson(playlistsBody));
        return Promise.resolve(okJson(videosBody));
      }
      return Promise.reject(new Error("piped down"));
    });
  }

  it("keeps latest in upload order and sorts topSongs by views", async () => {
    const fetchImpl = channelFetch(webVideosTwo, webPlaylistsTab);
    const app = await makeApp({ fetchImpl }, { innertube: true });
    const res = await app.inject({ method: "GET", url: `/music/artist/${CHANNEL_ID}/page` });
    expect(res.statusCode).toBe(200);
    const body = res.json();
    expect(body.latest.map((t: { videoId: string }) => t.videoId)).toEqual([
      "vidvidvid01",
      "vidvidvid02",
    ]);
    expect(body.topSongs.map((t: { videoId: string }) => t.videoId)).toEqual([
      "vidvidvid02",
      "vidvidvid01",
    ]);
    await app.close();
  });

  it("fills channel playlists from the playlists tab", async () => {
    const fetchImpl = channelFetch(webVideosTwo, webPlaylistsTab);
    const app = await makeApp({ fetchImpl }, { innertube: true });
    const res = await app.inject({ method: "GET", url: `/music/artist/${CHANNEL_ID}/page` });
    expect(res.statusCode).toBe(200);
    expect(res.json().playlists).toEqual([
      {
        playlistId: "PLchannel0001",
        name: "Alle Fälle",
        thumbnailUrl: "https://img/pl.jpg",
        videoCount: 42,
        uploaderName: "Lucia Leona",
      },
    ]);
    await app.close();
  });

  it("follows one continuation of the videos tab", async () => {
    const withToken = {
      ...webVideosTwo,
      contents: [
        ...webVideosTwo.contents,
        {
          continuationItemRenderer: {
            continuationEndpoint: { continuationCommand: { token: "CT1" } },
          },
        },
      ],
    };
    const continuation = {
      onResponseReceivedActions: [
        {
          appendContinuationItemsAction: {
            continuationItems: [video("vidvidvid03", "Älteres Video", "30:00", "5 Aufrufe")],
          },
        },
      ],
    };
    const fetchImpl = channelFetch(withToken, webPlaylistsTab, continuation);
    const app = await makeApp({ fetchImpl }, { innertube: true });
    const res = await app.inject({ method: "GET", url: `/music/artist/${CHANNEL_ID}/page` });
    expect(res.statusCode).toBe(200);
    expect(res.json().latest.map((t: { videoId: string }) => t.videoId)).toEqual([
      "vidvidvid01",
      "vidvidvid02",
      "vidvidvid03",
    ]);
    await app.close();
  });

  it("leaves latest empty for real music artists", async () => {
    const fetchImpl = itOnlyFetch({
      header: { musicImmersiveHeaderRenderer: { title: { runs: [{ text: "Rick Astley" }] } } },
      contents: [
        {
          musicShelfRenderer: {
            contents: [itSongItem("dQw4w9WgXcQ", "Never Gonna Give You Up", "Rick", CHANNEL_ID)],
          },
        },
      ],
    });
    const app = await makeApp({ fetchImpl }, { innertube: true });
    const res = await app.inject({ method: "GET", url: `/music/artist/${CHANNEL_ID}/page` });
    expect(res.statusCode).toBe(200);
    expect(res.json().latest).toEqual([]);
    expect(res.json().topSongs).toHaveLength(1);
    await app.close();
  });
});

describe("GET /music/search/full with video modes", () => {
  const longVideo = { ...PIPED_ITEM, duration: 3600 };
  const shortClip = {
    ...PIPED_ITEM,
    url: "/watch?v=shortclip01",
    title: "Trailer",
    duration: 60,
  };

  it("returns videos, channels and playlists for mode=truecrime", async () => {
    const fetchImpl = urlDispatchMock({
      filters: {
        videos: [longVideo, shortClip],
        channels: [CHANNEL_ITEM],
        playlists: [PLAYLIST_ITEM],
      },
    });
    const app = await makeApp({ fetchImpl });
    const res = await app.inject({
      method: "GET",
      url: "/music/search/full?q=rick&mode=truecrime",
    });
    expect(res.statusCode).toBe(200);
    const body = res.json();
    // Dauerfilter greift nicht hart genug (<4 lange Treffer) → ungefiltert
    expect(body.songs.map((t: { title: string }) => t.title)).toEqual([
      "Never Gonna Give You Up",
      "Trailer",
    ]);
    expect(body.artists).toEqual([
      expect.objectContaining({ channelId: CHANNEL_ID, name: "Rick Astley" }),
    ]);
    expect(body.albums).toEqual([]);
    expect(body.playlists).toEqual([expect.objectContaining({ playlistId: "PLhits123" })]);
    expect(body.topResult).toMatchObject({ type: "artist", name: "Rick Astley" });
    await app.close();
  });

  it("filters short clips when enough long hits remain", async () => {
    const longs = [0, 1, 2, 3].map((i) => ({
      ...PIPED_ITEM,
      url: `/watch?v=longvideo0${i}x`.slice(0, "/watch?v=".length + 11),
      duration: 700 + i,
    }));
    const fetchImpl = urlDispatchMock({
      filters: { videos: [...longs, shortClip], channels: [], playlists: [] },
    });
    const app = await makeApp({ fetchImpl });
    const res = await app.inject({
      method: "GET",
      url: "/music/search/full?q=fall&mode=podcast",
    });
    expect(res.statusCode).toBe(200);
    const durations = res.json().songs.map((t: { durationSeconds: number }) => t.durationSeconds);
    expect(durations.every((d: number) => d >= 300)).toBe(true);
    await app.close();
  });

  it("caches music and video modes separately", async () => {
    const fetchImpl = urlDispatchMock({
      filters: {
        videos: [longVideo],
        channels: [],
        playlists: [PLAYLIST_ITEM],
        music_songs: [PIPED_ITEM],
        music_albums: [],
        music_artists: [],
      },
    });
    const app = await makeApp({ fetchImpl, now: () => 1_000 });
    await app.inject({ method: "GET", url: "/music/search/full?q=rick&mode=truecrime" });
    const calls = fetchImpl.mock.calls.length;
    await app.inject({ method: "GET", url: "/music/search/full?q=rick&mode=truecrime" });
    expect(fetchImpl.mock.calls.length).toBe(calls); // zweiter Aufruf aus dem Cache
    await app.inject({ method: "GET", url: "/music/search/full?q=rick" });
    expect(fetchImpl.mock.calls.length).toBeGreaterThan(calls); // anderer Modus = eigener Key
    await app.close();
  });

  it("rejects an unknown mode", async () => {
    const app = await makeApp({ fetchImpl: vi.fn() });
    const res = await app.inject({ method: "GET", url: "/music/search/full?q=rick&mode=nope" });
    expect(res.statusCode).toBe(400);
    await app.close();
  });
});

describe("GET /music/video/:videoId", () => {
  const upstream = (status: number, body: string, headers: Record<string, string> = {}) =>
    new Response(body, {
      status,
      headers: {
        "content-type": "video/mp4",
        "content-length": String(body.length),
        "accept-ranges": "bytes",
        ...headers,
      },
    });

  it("proxies the muxed video stream and requests an mp4 format", async () => {
    const ytDlp = vi
      .fn()
      .mockResolvedValue({ stdout: "https://cdn.example/video.mp4\n", stderr: "" });
    const fetchImpl = vi.fn().mockResolvedValue(upstream(200, "VIDEOBYTES"));
    const app = await makeApp({ ytDlp, fetchImpl });
    const res = await app.inject({ method: "GET", url: "/music/video/dQw4w9WgXcQ" });
    expect(res.statusCode).toBe(200);
    expect(res.body).toBe("VIDEOBYTES");
    expect(res.headers["content-type"]).toBe("video/mp4");
    const format = (ytDlp.mock.calls[0]?.[0] as string[]).join(" ");
    expect(format).toContain("vcodec!=none");
    expect(format).toContain("height<=720");
    await app.close();
  });

  it("forwards Range and passes 206 + Content-Range through", async () => {
    const ytDlp = vi
      .fn()
      .mockResolvedValue({ stdout: "https://cdn.example/video.mp4", stderr: "" });
    const fetchImpl = vi
      .fn()
      .mockResolvedValue(upstream(206, "PART", { "content-range": "bytes 0-3/9000" }));
    const app = await makeApp({ ytDlp, fetchImpl });
    const res = await app.inject({
      method: "GET",
      url: "/music/video/dQw4w9WgXcQ",
      headers: { range: "bytes=0-3" },
    });
    expect(res.statusCode).toBe(206);
    expect(res.headers["content-range"]).toBe("bytes 0-3/9000");
    const [, init] = fetchImpl.mock.calls[0];
    expect((init as { headers: Record<string, string> }).headers).toMatchObject({
      range: "bytes=0-3",
    });
    await app.close();
  });

  it("caches video urls separately from audio urls", async () => {
    const ytDlp = vi.fn().mockResolvedValue({ stdout: "https://cdn.example/media\n", stderr: "" });
    const fetchImpl = vi.fn().mockResolvedValue(upstream(200, "X"));
    const app = await makeApp({ ytDlp, fetchImpl });
    await app.inject({ method: "GET", url: "/music/audio/dQw4w9WgXcQ" });
    await app.inject({ method: "GET", url: "/music/video/dQw4w9WgXcQ" });
    // zwei getrennte Auflösungen (bestaudio vs. muxed MP4) trotz gleicher videoId
    expect(ytDlp).toHaveBeenCalledTimes(2);
    const formats = ytDlp.mock.calls.map((c) => (c[0] as string[]).join(" "));
    expect(formats[0]).toContain("bestaudio");
    expect(formats[1]).toContain("vcodec!=none");
    await app.close();
  });

  it("rejects malformed video ids", async () => {
    const app = await makeApp({ fetchImpl: vi.fn() });
    const res = await app.inject({ method: "GET", url: "/music/video/nope" });
    expect(res.statusCode).toBe(400);
    await app.close();
  });

  it("returns 502 when extraction fails", async () => {
    const ytDlp = vi.fn().mockRejectedValue(new Error("boom"));
    const app = await makeApp({ ytDlp, fetchImpl: vi.fn() });
    const res = await app.inject({ method: "GET", url: "/music/video/dQw4w9WgXcQ" });
    expect(res.statusCode).toBe(502);
    expect(res.json().error).toContain("video");
    await app.close();
  });
});

describe("trackCountFrom", () => {
  it("parses German and English track counts with thousand separators", () => {
    expect(trackCountFrom("54 Titel")).toBe(54);
    expect(trackCountFrom("1.234 Titel")).toBe(1234);
    expect(trackCountFrom("1,234 tracks")).toBe(1234);
    expect(trackCountFrom("12 Songs")).toBe(12);
    expect(trackCountFrom("Playlist • 7 Lieder")).toBe(7);
    expect(trackCountFrom("38 Videos")).toBe(38);
  });

  it("ignores view counts and years", () => {
    expect(trackCountFrom("1,2 Mio. Aufrufe")).toBe(0);
    expect(trackCountFrom("Album • 2019")).toBe(0);
    expect(trackCountFrom("")).toBe(0);
  });
});
