import Database from "better-sqlite3";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import { processNewVideo } from "./orchestrator.js";

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
  channelId: null,
  channelTitle: null,
  captionsUrl: null,
};

const fakeShortMetadata = {
  ...fakeMetadata,
  durationSeconds: 45,
  aspectRatio: "9:16",
};

// Kurzes Querformat-Video — gehört ebenfalls in den Kurzform-Feed.
const fakeShortLandscape = {
  ...fakeMetadata,
  durationSeconds: 120,
  aspectRatio: "16:9",
};

describe("processNewVideo", () => {
  let db: Database.Database;
  beforeEach(() => {
    db = new Database(":memory:");
    applyMigrations(db);
    db.prepare(
      "INSERT INTO channels (id, url, title, added_at, is_active, status) VALUES ('UC1','x','chan',0,1,'subscribed')",
    ).run();
  });

  function baseDeps(overrides: Record<string, unknown> = {}) {
    return {
      db,
      videoId: "vid1",
      channelId: "UC1",
      fetchMetadata: async () => fakeMetadata,
      fetchSponsorSegments: async () => [],
      ...overrides,
    };
  }

  it("Abo-Video (lang): direkt in den Feed, neutrale scores-Zeile, KEIN Download", async () => {
    expect(await processNewVideo(baseDeps())).toBe("added");

    expect(db.prepare("SELECT format, source FROM videos WHERE id='vid1'").get()).toEqual({
      format: "long",
      source: "subscription",
    });
    expect(
      db.prepare("SELECT decision, model_used FROM scores WHERE video_id='vid1'").get(),
    ).toEqual({ decision: "approved", model_used: "subscription" });
    expect(db.prepare("SELECT COUNT(*) c FROM feed_items WHERE video_id='vid1'").get()).toEqual({
      c: 1,
    });
    expect(db.prepare("SELECT COUNT(*) c FROM downloaded_videos").get()).toEqual({ c: 0 });
  });

  it("keine Dauer-, Sprach- oder Qualitätsfilter: alles Anschaubare kommt durch", async () => {
    await processNewVideo(baseDeps({ fetchMetadata: async () => fakeShortMetadata }));
    await processNewVideo(
      baseDeps({
        videoId: "vid2",
        fetchMetadata: async () => ({ ...fakeMetadata, id: "vid2", durationSeconds: 60 * 60 * 5 }),
      }),
    );
    await processNewVideo(
      baseDeps({
        videoId: "vid3",
        fetchMetadata: async () => ({
          ...fakeMetadata,
          id: "vid3",
          title: "Keralam में बारिश बनी आफत!",
          defaultLanguage: "hi",
        }),
      }),
    );
    expect(db.prepare("SELECT COUNT(*) c FROM feed_items").get()).toEqual({ c: 3 });
    expect(
      db.prepare("SELECT COUNT(*) c FROM scores WHERE decision <> 'approved'").get(),
    ).toEqual({ c: 0 });
  });

  it("kurzes Querformat-Video zählt als Kurzform", async () => {
    await processNewVideo(baseDeps({ fetchMetadata: async () => fakeShortLandscape }));
    expect(db.prepare("SELECT format FROM videos WHERE id='vid1'").get()).toEqual({
      format: "short",
    });
  });

  it("Live-/Premiere-Streams werden nicht aufgenommen", async () => {
    const res = await processNewVideo(
      baseDeps({ fetchMetadata: async () => ({ ...fakeMetadata, isLive: true }) }),
    );
    expect(res).toBe("live");
    expect(db.prepare("SELECT COUNT(*) c FROM videos").get()).toEqual({ c: 0 });
  });

  it("bekanntes Video: kein erneuter Metadaten-Abruf", async () => {
    await processNewVideo(baseDeps());
    const fetchMetadata = vi.fn(async () => fakeMetadata);
    expect(await processNewVideo(baseDeps({ fetchMetadata }))).toBe("known");
    expect(fetchMetadata).not.toHaveBeenCalled();
  });

  it.each([
    ["Probe-Kanal", "probe", 0],
    ["geblockter Kanal", "blocked", 0],
    ["abbestellter Kanal", "subscribed", 0],
    ["statusloser Kanal", null, 1],
  ])("%s: nichts aufnehmen, nicht einmal Metadaten holen", async (_label, status, active) => {
    db.prepare(
      "INSERT INTO channels (id,url,title,added_at,is_active,status) VALUES ('UC-x','','X',0,?,?)",
    ).run(active, status);
    const fetchMetadata = vi.fn(async () => fakeMetadata);
    const res = await processNewVideo(baseDeps({ channelId: "UC-x", fetchMetadata }));
    expect(res).toBe("not-subscribed");
    expect(fetchMetadata).not.toHaveBeenCalled();
    expect(db.prepare("SELECT COUNT(*) c FROM videos").get()).toEqual({ c: 0 });
  });

  it("unbekannter Kanal: nichts aufnehmen", async () => {
    expect(await processNewVideo(baseDeps({ channelId: "UC-unknown" }))).toBe("not-subscribed");
    expect(db.prepare("SELECT COUNT(*) c FROM videos").get()).toEqual({ c: 0 });
  });

  it("SponsorBlock-Segmente werden mitgeschrieben", async () => {
    await processNewVideo(
      baseDeps({
        fetchSponsorSegments: async () => [{ startSeconds: 1, endSeconds: 5, category: "sponsor" }],
      }),
    );
    expect(db.prepare("SELECT COUNT(*) c FROM sponsor_segments WHERE video_id='vid1'").get()).toEqual(
      { c: 1 },
    );
  });
});
