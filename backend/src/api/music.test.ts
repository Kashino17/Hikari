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
    expect(fetchImpl).toHaveBeenCalledTimes(2);
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

  it("force=1 bypasses the stream cache", async () => {
    const ytDlp = vi.fn().mockResolvedValue({ stdout: "https://cdn.example/a.m4a", stderr: "" });
    const app = await makeApp({ ytDlp, now: () => 1_000 });
    await app.inject({ method: "GET", url: "/music/stream/dQw4w9WgXcQ" });
    await app.inject({ method: "GET", url: "/music/stream/dQw4w9WgXcQ?force=1" });
    expect(ytDlp).toHaveBeenCalledTimes(2);
    await app.close();
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
