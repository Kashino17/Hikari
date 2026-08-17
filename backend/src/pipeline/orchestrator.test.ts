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

const fakeShortMetadata = {
  ...fakeMetadata,
  durationSeconds: 45,
  aspectRatio: "9:16",
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

  function baseDeps(overrides: Record<string, unknown> = {}) {
    return {
      db,
      videoId: "vid1",
      channelId: "UC1",
      fetchMetadata: async () => fakeMetadata,
      fetchTranscript: async () => null,
      fetchSponsorSegments: async () => [],
      scorer: makeScorer("approve"),
      ...overrides,
    };
  }

  it("approve (long): feed_items-Row, format/source gesetzt, KEIN Download, KEIN Clipper", async () => {
    const summarize = vi.fn(async () => "Ein Teaser.");
    await processNewVideo(
      baseDeps({
        fetchTranscript: async () => "ein ausreichend langes transkript ".repeat(10),
        fetchMetadata: async () => ({ ...fakeMetadata, captionsUrl: "https://cc" }),
        summarize,
      }),
    );

    const v = db
      .prepare("SELECT format, source, summary, clip_status FROM videos WHERE id='vid1'")
      .get() as { format: string; source: string; summary: string; clip_status: string | null };
    expect(v).toEqual({ format: "long", source: "subscription", summary: "Ein Teaser.", clip_status: null });
    expect(summarize).toHaveBeenCalledOnce();

    const feed = db
      .prepare("SELECT is_pre_clipper FROM feed_items WHERE video_id='vid1'")
      .get() as { is_pre_clipper: number };
    expect(feed.is_pre_clipper).toBe(1);

    expect(db.prepare("SELECT COUNT(*) c FROM downloaded_videos").get()).toEqual({ c: 0 });
    expect(db.prepare("SELECT COUNT(*) c FROM clipper_queue").get()).toEqual({ c: 0 });
  });

  it("approve (short): format 'short', summarize wird nicht gerufen", async () => {
    const summarize = vi.fn(async () => "sollte nicht passieren");
    await processNewVideo(
      baseDeps({
        fetchMetadata: async () => ({ ...fakeShortMetadata, captionsUrl: "https://cc" }),
        fetchTranscript: async () => "worte ".repeat(50),
        summarize,
      }),
    );
    const v = db.prepare("SELECT format, summary FROM videos WHERE id='vid1'").get() as {
      format: string;
      summary: string | null;
    };
    expect(v).toEqual({ format: "short", summary: null });
    expect(summarize).not.toHaveBeenCalled();
    expect(db.prepare("SELECT COUNT(*) c FROM feed_items").get()).toEqual({ c: 1 });
  });

  it("summarize wirft ⇒ Approve läuft durch, summary bleibt null", async () => {
    await processNewVideo(
      baseDeps({
        fetchMetadata: async () => ({ ...fakeMetadata, captionsUrl: "https://cc" }),
        fetchTranscript: async () => "worte ".repeat(50),
        summarize: async () => {
          throw new Error("LLM down");
        },
      }),
    );
    const v = db.prepare("SELECT summary FROM videos WHERE id='vid1'").get() as {
      summary: string | null;
    };
    expect(v.summary).toBeNull();
    expect(db.prepare("SELECT COUNT(*) c FROM feed_items").get()).toEqual({ c: 1 });
  });

  it("clipperEnabled=true: clip_status pending + Enqueue wie früher (weiterhin ohne Download)", async () => {
    await processNewVideo(baseDeps({ clipperEnabled: true }));
    const v = db.prepare("SELECT clip_status FROM videos WHERE id='vid1'").get() as {
      clip_status: string;
    };
    expect(v.clip_status).toBe("pending");
    expect(db.prepare("SELECT COUNT(*) c FROM clipper_queue").get()).toEqual({ c: 1 });
    expect(db.prepare("SELECT COUNT(*) c FROM downloaded_videos").get()).toEqual({ c: 0 });
  });

  it("rejected: nur videos+scores, keine feed_items", async () => {
    await processNewVideo(baseDeps({ scorer: makeScorer("reject") }));
    const s = db.prepare("SELECT decision FROM scores WHERE video_id='vid1'").get() as {
      decision: string;
    };
    expect(s.decision).toBe("rejected");
    expect(db.prepare("SELECT COUNT(*) c FROM feed_items").get()).toEqual({ c: 0 });
  });

  it("Green Card: Short unterhalb minDurationSec wird trotzdem approved", async () => {
    db.prepare("UPDATE channels SET auto_approve = 1 WHERE id='UC1'").run();
    // DEFAULT_FILTER.minDurationSec = 180 — ein 45s-Short fiele ohne Bypass durch.
    await processNewVideo(baseDeps({ fetchMetadata: async () => fakeShortMetadata }));
    const s = db.prepare("SELECT decision FROM scores WHERE video_id='vid1'").get() as {
      decision: string;
    };
    expect(s.decision).toBe("approved");
    expect(db.prepare("SELECT COUNT(*) c FROM feed_items").get()).toEqual({ c: 1 });
  });

  it("Green Card: Langvideo außerhalb der Dauer-Range bleibt rejected", async () => {
    db.prepare("UPDATE channels SET auto_approve = 1 WHERE id='UC1'").run();
    await processNewVideo(
      baseDeps({
        fetchMetadata: async () => ({ ...fakeMetadata, durationSeconds: 30, aspectRatio: "16:9" }),
      }),
    );
    const s = db.prepare("SELECT decision FROM scores WHERE video_id='vid1'").get() as {
      decision: string;
    };
    expect(s.decision).toBe("rejected");
  });
});
