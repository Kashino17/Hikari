import Database from "better-sqlite3";
import { beforeEach, describe, expect, it } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import { getFilterState } from "../scorer/filter-repo.js";
import { runDiscoveryCycle } from "./feed-sources.js";

const noRecommend = (async () => []) as never;
const none = async () => undefined;

function track(videoId: string) {
  return { videoId, title: "t", uploader: "P", thumbnailUrl: "", durationSeconds: 60 };
}

describe("runDiscoveryCycle", () => {
  let db: Database.Database;
  beforeEach(() => {
    db = new Database(":memory:");
    applyMigrations(db);
    db.prepare(
      "INSERT INTO channels (id,url,title,added_at,is_active,status) VALUES ('UC-abo','x','Abo',0,1,'subscribed')",
    ).run();
  });

  it("legt Probe-Kanäle aus Empfehlungen an (is_active=0, status probe)", async () => {
    const recommend = (async () => [
      {
        channelId: "UC-neu",
        channelUrl: "u",
        title: "Neu",
        handle: null,
        description: null,
        subscribers: 5,
        thumbnail: "t",
        banner: null,
      },
    ]) as never;
    await runDiscoveryCycle(db, {
      recommend,
      channelVideos: none as never,
      channelShorts: none as never,
      videoSearch: none as never,
    });
    expect(db.prepare("SELECT is_active, status FROM channels WHERE id='UC-neu'").get()).toEqual({
      is_active: 0,
      status: "probe",
    });
  });

  it("enqueued max PROBE_PER_CYCLE unbekannte Videos je Probe-Kanal, Shorts zuerst, source probe", async () => {
    db.prepare(
      "INSERT INTO channels (id,url,title,added_at,is_active,status) VALUES ('UC-p','x','P',0,0,'probe')",
    ).run();
    const channelShorts = (async () => ["sssssssssss"]) as never;
    const channelVideos = (async () =>
      ["v0000000001", "v0000000002", "v0000000003", "v0000000004"].map(track)) as never;
    await runDiscoveryCycle(db, {
      recommend: noRecommend,
      channelShorts,
      channelVideos,
      videoSearch: none as never,
    });
    const rows = db
      .prepare("SELECT video_id, source FROM ingest_queue WHERE channel_id='UC-p' ORDER BY rowid")
      .all() as { video_id: string; source: string }[];
    expect(rows).toHaveLength(5); // alle Kandidaten (unter der Quote von 8)
    expect(rows[0]).toEqual({ video_id: "sssssssssss", source: "probe" });
  });

  it("Themen-Suche enqueued unter dem Pseudo-Kanal mit source topic", async () => {
    getFilterState(db); // legt die Default-Config an
    db.prepare(
      `UPDATE filter_config SET filter_json = json_set(filter_json, '$.likeTags', json('["weltraum"]')) WHERE id = 1`,
    ).run();
    const videoSearch = (async () => ["ttttttttttt"]) as never;
    await runDiscoveryCycle(db, {
      recommend: noRecommend,
      channelVideos: none as never,
      channelShorts: none as never,
      videoSearch,
    });
    expect(
      db.prepare("SELECT channel_id, source FROM ingest_queue WHERE video_id='ttttttttttt'").get(),
    ).toEqual({ channel_id: "discovery-topics", source: "topic" });
  });

  it("Backfill nur wenn unseen unter dailyBudget; source backfill", async () => {
    const channelVideos = (async () => [
      { ...track("bfbfbfbfbfb"), durationSeconds: 600 },
    ]) as never;
    await runDiscoveryCycle(db, {
      recommend: noRecommend,
      channelVideos,
      channelShorts: none as never,
      videoSearch: none as never,
    });
    expect(
      db.prepare("SELECT source FROM ingest_queue WHERE video_id='bfbfbfbfbfb'").get(),
    ).toEqual({ source: "backfill" });
  });

  it("Backfill füllt auch bei vorhandenem Vorrat nach — der Feed soll nie leerlaufen", async () => {
    db.prepare(
      `INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at)
       VALUES ('u1','UC-abo','t',0,60,0), ('u2','UC-abo','t',0,60,0)`,
    ).run();
    db.prepare(
      "INSERT INTO feed_items (video_id, added_to_feed_at, is_pre_clipper) VALUES ('u1',0,1),('u2',0,1)",
    ).run();
    const channelVideos = (async () => [track("bfbfbfbfbfb")]) as never;
    await runDiscoveryCycle(db, {
      recommend: noRecommend,
      channelVideos,
      channelShorts: none as never,
      videoSearch: none as never,
    });
    expect(db.prepare("SELECT source FROM ingest_queue WHERE video_id='bfbfbfbfbfb'").get()).toEqual({
      source: "backfill",
    });
  });
});
