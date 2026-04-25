import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { importDirectLink } from "./manual-import.js";

vi.mock("../yt-dlp/client.js", () => {
  class YtDlpError extends Error {
    stderr: string;
    exitCode: number | undefined;

    constructor(message: string, stderr = "", exitCode?: number) {
      super(message);
      this.name = "YtDlpError";
      this.stderr = stderr;
      this.exitCode = exitCode;
    }
  }

  return {
    runYtDlp: vi.fn(),
    YtDlpError,
  };
});

function rot13(input: string): string {
  let out = "";
  for (const ch of input) {
    const code = ch.charCodeAt(0);
    if (code >= 65 && code <= 90) {
      out += String.fromCharCode(((code - 65 + 13) % 26) + 65);
      continue;
    }
    if (code >= 97 && code <= 122) {
      out += String.fromCharCode(((code - 97 + 13) % 26) + 97);
      continue;
    }
    out += ch;
  }
  return out;
}

function encodeVoeConfig(config: Record<string, unknown>): string {
  const stage1 = Buffer.from(JSON.stringify(config), "utf8").toString("base64");
  const stage2 = stage1.split("").reverse().join("");
  const stage3 = stage2
    .split("")
    .map((ch) => String.fromCharCode(ch.charCodeAt(0) + 3))
    .join("");
  return rot13(Buffer.from(stage3, "binary").toString("base64"));
}

class MockDb {
  channels = new Map<string, { id: string; url: string; title: string; added_at: number; is_active: number }>();
  videos = new Map<
    string,
    {
      id: string;
      channel_id: string;
      title: string;
      description: string;
      published_at: number;
      duration_seconds: number;
      thumbnail_url: string | null;
      discovered_at: number;
    }
  >();
  scores = new Map<string, { video_id: string }>();
  downloadedVideos = new Map<string, { video_id: string; file_path: string; file_size_bytes: number }>();
  feedItems = new Map<string, { video_id: string; added_to_feed_at: number }>();

  prepare(sql: string) {
    if (sql.includes("SELECT 1 FROM videos WHERE id = ?")) {
      return {
        get: (id: string) => (this.videos.has(id) ? { 1: 1 } : undefined),
      };
    }

    if (sql.includes("INSERT OR IGNORE INTO channels")) {
      return {
        run: (id: string, url: string, title: string, addedAt: number) => {
          if (!this.channels.has(id)) {
            this.channels.set(id, { id, url, title, added_at: addedAt, is_active: 1 });
          }
        },
      };
    }

    if (sql.includes("INSERT INTO channels") && sql.includes("ON CONFLICT(id) DO UPDATE")) {
      return {
        run: (id: string, url: string, title: string, addedAt: number) => {
          const existing = this.channels.get(id);
          this.channels.set(id, {
            id,
            url,
            title,
            added_at: existing?.added_at ?? addedAt,
            is_active: 1,
          });
        },
      };
    }

    if (sql.includes("INSERT INTO videos")) {
      return {
        run: (
          id: string,
          channelId: string,
          title: string,
          description: string,
          publishedAt: number,
          duration: number,
          _aspectRatio: string | null,
          _defaultLanguage: string | null,
          thumbnailUrl: string | null,
          _transcript: string | null,
          discoveredAt: number,
        ) => {
          this.videos.set(id, {
            id,
            channel_id: channelId,
            title,
            description,
            published_at: publishedAt,
            duration_seconds: duration,
            thumbnail_url: thumbnailUrl,
            discovered_at: discoveredAt,
          });
        },
      };
    }

    if (sql.includes("INSERT INTO scores")) {
      return {
        run: (videoId: string) => {
          this.scores.set(videoId, { video_id: videoId });
        },
      };
    }

    if (sql.includes("DELETE FROM scores WHERE video_id = ?")) {
      return {
        run: (videoId: string) => {
          this.scores.delete(videoId);
        },
      };
    }

    if (sql.includes("DELETE FROM videos WHERE id = ?")) {
      return {
        run: (videoId: string) => {
          this.videos.delete(videoId);
        },
      };
    }

    if (sql.includes("INSERT INTO downloaded_videos")) {
      return {
        run: (videoId: string, filePath: string, fileSizeBytes: number) => {
          this.downloadedVideos.set(videoId, {
            video_id: videoId,
            file_path: filePath,
            file_size_bytes: fileSizeBytes,
          });
        },
      };
    }

    if (sql.includes("INSERT OR IGNORE INTO feed_items")) {
      return {
        run: (videoId: string, addedToFeedAt: number) => {
          if (!this.feedItems.has(videoId)) {
            this.feedItems.set(videoId, { video_id: videoId, added_to_feed_at: addedToFeedAt });
          }
        },
      };
    }

    throw new Error(`Unexpected SQL in test: ${sql}`);
  }
}

describe("importDirectLink", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("falls back to VOE page config when yt-dlp rejects the share URL", async () => {
    const pageUrl =
      "https://timmaybealready.com/fsz0jl0y8u39?Dragonball%20Super%202%20HD%20GER%20SUB";
    const sourceUrl =
      "https://ugc-cdn.example.com/engine/hls2-c/01/00190/fsz0jl0y8u39_,n,.urlset/master.m3u8?t=abc";
    const thumbnailUrl = "https://timmaybealready.com/cache/fsz0jl0y8u39_storyboard_L2.jpg";
    const encoded = encodeVoeConfig({
      file_code: "fsz0jl0y8u39",
      title: "Dragonball Super 2 HD GER SUB by Dragonball-Tube",
      thumbnail: thumbnailUrl,
      source: sourceUrl,
    });
    const html = `<script type="application/json">${JSON.stringify([encoded])}</script>`;

    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(html, { status: 200 })));

    const { runYtDlp, YtDlpError } = await import("../yt-dlp/client.js");
    const dir = mkdtempSync(join(tmpdir(), "hikari-import-"));
    const filePath = join(dir, "voe_fsz0jl0y8u39.mp4");

    vi.mocked(runYtDlp).mockImplementation(async (args: string[]) => {
      const target = args[args.length - 1];

      if (args[0] === "--dump-single-json" && target === pageUrl) {
        throw new YtDlpError("Unsupported URL", "", 1);
      }

      if (args[0] === "--dump-single-json" && target === sourceUrl) {
        return {
          stdout: JSON.stringify({
            id: "ignored-generic-id",
            extractor: "generic",
            title: "master",
            duration: 1209,
            upload_date: "20260425",
          }),
          stderr: "",
        };
      }

      if (args.includes("-o") && target === sourceUrl) {
        writeFileSync(filePath, Buffer.alloc(1024, 0xff));
        return { stdout: "", stderr: "" };
      }

      throw new Error(`Unexpected yt-dlp args: ${JSON.stringify(args)}`);
    });

    const db = new MockDb();

    const result = await importDirectLink(db as never, pageUrl, dir);

    expect(result).toMatchObject({
      status: "ok",
      videoId: "voe_fsz0jl0y8u39",
      title: "Dragonball Super 2 HD GER SUB by Dragonball-Tube",
    });

    const video = db.videos.get("voe_fsz0jl0y8u39");
    expect(video).toEqual({
      id: "voe_fsz0jl0y8u39",
      channel_id: "manual",
      title: "Dragonball Super 2 HD GER SUB by Dragonball-Tube",
      description: "",
      published_at: expect.any(Number),
      duration_seconds: 1209,
      thumbnail_url: thumbnailUrl,
      discovered_at: expect.any(Number),
    });

    expect(runYtDlp).toHaveBeenCalledWith(
      ["--dump-single-json", "--no-warnings", "--no-playlist", pageUrl],
      expect.any(Object),
    );
    expect(runYtDlp).toHaveBeenCalledWith(
      ["--dump-single-json", "--no-warnings", "--no-playlist", sourceUrl],
      expect.any(Object),
    );
    expect(runYtDlp).toHaveBeenCalledWith(
      expect.arrayContaining(["-o", filePath, "--no-warnings", sourceUrl]),
      expect.any(Object),
    );
  });

  it("reactivates the manual channel before returning duplicate imports", async () => {
    const pageUrl =
      "https://timmaybealready.com/fsz0jl0y8u39?Dragonball%20Super%202%20HD%20GER%20SUB";
    const db = new MockDb();
    db.channels.set("manual", {
      id: "manual",
      url: "manual:hikari",
      title: "Manuell hinzugefügt",
      added_at: 1,
      is_active: 0,
    });
    db.videos.set("voe_fsz0jl0y8u39", {
      id: "voe_fsz0jl0y8u39",
      channel_id: "manual",
      title: "Dragonball Super 2 HD GER SUB by Dragonball-Tube",
      description: "",
      published_at: 1,
      duration_seconds: 1209,
      thumbnail_url: null,
      discovered_at: 1,
    });

    const { runYtDlp } = await import("../yt-dlp/client.js");
    vi.mocked(runYtDlp).mockResolvedValue({
      stdout: JSON.stringify({
        id: "fsz0jl0y8u39",
        extractor: "voe",
        title: "Dragonball Super 2 HD GER SUB by Dragonball-Tube",
      }),
      stderr: "",
    });

    const result = await importDirectLink(db as never, pageUrl, tmpdir());

    expect(result).toMatchObject({
      status: "duplicate",
      videoId: "voe_fsz0jl0y8u39",
    });
    expect(db.channels.get("manual")?.is_active).toBe(1);
  });
});
