import Database from "better-sqlite3";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import { processNewVideo } from "./orchestrator.js";
import type { Scorer } from "../scorer/types.js";

const fakeMetadata = {
  id: "vid1",
  title: "Deep prime talk",
  description: "Primes are cool.",
  durationSeconds: 600,
  publishedAt: 1_700_000_000_000,
  thumbnailUrl: "https://t",
  aspectRatio: "16:9",
  defaultLanguage: "en",
  isLive: false,
  captionsUrl: null,
};

function makeScorer(decision: "approve" | "reject"): Scorer {
  return {
    name: "mock",
    async score() {
      return {
        modelUsed: "mock-v1",
        score: {
          overallScore: decision === "approve" ? 80 : 40,
          category: "math",
          clickbaitRisk: 1,
          educationalValue: 9,
          emotionalManipulation: 0,
          reasoning: "test",
        },
      };
    },
  };
}

describe("processNewVideo", () => {
  let db: Database.Database;
  beforeEach(() => {
    db = new Database(":memory:");
    applyMigrations(db);
    db.prepare(
      "INSERT INTO channels (id, url, title, added_at) VALUES ('UC1','x','chan',0)",
    ).run();
  });

  it("approved video: enqueues for clipper, sets clip_status='pending', NOT in feed_items", async () => {
    const download = vi.fn(async () => ({
      filePath: "/fake/vid1.mp4",
      fileSizeBytes: 1024,
    }));
    await processNewVideo({
      db,
      videoId: "vid1",
      channelId: "UC1",
      fetchMetadata: async () => fakeMetadata,
      fetchTranscript: async () => null,
      fetchSponsorSegments: async () => [],
      scorer: makeScorer("approve"),
      download,
    });

    const v = db.prepare("SELECT clip_status FROM videos WHERE id='vid1'").get() as any;
    expect(v.clip_status).toBe("pending");

    const queued = db.prepare("SELECT * FROM clipper_queue WHERE video_id='vid1'").get();
    expect(queued).toBeTruthy();

    const feed = db.prepare("SELECT * FROM feed_items WHERE video_id='vid1'").all();
    expect(feed).toHaveLength(0);

    expect(download).toHaveBeenCalledOnce();
  });

  it("writes rejected video to scores only, no feed_items, no download", async () => {
    const download = vi.fn();
    await processNewVideo({
      db,
      videoId: "vid1",
      channelId: "UC1",
      fetchMetadata: async () => fakeMetadata,
      fetchTranscript: async () => null,
      fetchSponsorSegments: async () => [],
      scorer: makeScorer("reject"),
      download,
    });

    const scores = db.prepare("SELECT decision FROM scores WHERE video_id='vid1'").get();
    expect(scores).toEqual({ decision: "rejected" });
    expect(db.prepare("SELECT COUNT(*) as c FROM feed_items").get()).toEqual({ c: 0 });
    expect(download).not.toHaveBeenCalled();
  });
});
