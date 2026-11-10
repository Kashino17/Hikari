import Database from "better-sqlite3";
import { describe, expect, it } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import { subscribedChannelCondition } from "../feed/subscriptions.js";
import { listUploadsPage } from "./history.js";

function setup() {
  const db = new Database(":memory:");
  applyMigrations(db);
  const channel = db.prepare(
    "INSERT INTO channels (id,url,title,added_at,status,is_active) VALUES (?,?,?,0,?,1)",
  );
  channel.run("a", "x", "Alpha", "subscribed");
  channel.run("b", "y", "Beta", "subscribed");
  channel.run("gone", "z", "Abbestellt", "unsubscribed");
  const video = db.prepare(
    "INSERT INTO videos (id,channel_id,title,published_at,duration_seconds,discovered_at,format) VALUES (?,?,?,?,600,0,?)",
  );
  video.run("a1", "a", "Erstes von Alpha", 100, "long");
  video.run("a2", "a", "Zweites von Alpha", 300, "long");
  video.run("b1", "b", "100% Beta", 200, "long");
  video.run("b-short", "b", "Kurzclip", 400, "short");
  video.run("g1", "gone", "Altes Video", 500, "long");
  return db;
}

const ids = (rows: unknown[]) => (rows as { id: string }[]).map((r) => r.id);
const cond = subscribedChannelCondition("v.channel_id");

describe("listUploadsPage", () => {
  it("liefert die Uploads aller abonnierten Kanäle, neueste zuerst, ohne Shorts", () => {
    expect(ids(listUploadsPage(setup(), cond, { limit: 10 }))).toEqual(["a2", "b1", "a1"]);
  });

  it("blättert seitenweise", () => {
    const db = setup();
    expect(ids(listUploadsPage(db, cond, { limit: 2, offset: 0 }))).toEqual(["a2", "b1"]);
    expect(ids(listUploadsPage(db, cond, { limit: 2, offset: 2 }))).toEqual(["a1"]);
    expect(ids(listUploadsPage(db, cond, { limit: 2, offset: 4 }))).toEqual([]);
  });

  it("grenzt auf einen Kanal ein", () => {
    expect(ids(listUploadsPage(setup(), cond, { channelId: "a", limit: 10 }))).toEqual(["a2", "a1"]);
  });

  it("sucht in Titel und Kanalname und nimmt % wörtlich", () => {
    const db = setup();
    expect(ids(listUploadsPage(db, cond, { query: "zweites", limit: 10 }))).toEqual(["a2"]);
    expect(ids(listUploadsPage(db, cond, { query: "beta", limit: 10 }))).toEqual(["b1"]);
    expect(ids(listUploadsPage(db, cond, { query: "100%", limit: 10 }))).toEqual(["b1"]);
    expect(ids(listUploadsPage(db, cond, { query: "%", limit: 10 }))).toEqual(["b1"]);
  });
});
