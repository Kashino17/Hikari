import { describe, it, expect, beforeEach } from "vitest";
import Database from "better-sqlite3";
import { applyMigrations } from "../db/migrations.js";
import { DEFAULT_FILTER, type FilterConfig } from "./filter.js";
import {
  getFilterState,
  setFilterConfig,
  getResolvedChannelFilter,
  getFilterForChannel,
  getActivePromptForChannel,
  setChannelFilterConfig,
  setChannelPromptOverride,
  clearChannelFilter,
} from "./filter-repo.js";

function makeDb(): Database.Database {
  const db = new Database(":memory:");
  applyMigrations(db);
  return db;
}

function seedChannel(db: Database.Database, id: string): void {
  db.prepare("INSERT INTO channels (id, url, title, added_at) VALUES (?, ?, ?, ?)").run(
    id,
    `https://youtube.com/${id}`,
    `Channel ${id}`,
    Date.now(),
  );
}

const customFilter: FilterConfig = {
  ...DEFAULT_FILTER,
  likeTags: ["Kochen", "Garten"],
  scoreThreshold: 85,
};

describe("per-channel filter resolution", () => {
  let db: Database.Database;
  beforeEach(() => {
    db = makeDb();
    seedChannel(db, "ch1");
    seedChannel(db, "ch2");
  });

  it("inherits the global filter when the channel has no override", () => {
    const resolved = getResolvedChannelFilter(db, "ch1");
    expect(resolved.inherited).toBe(true);
    expect(resolved.filter).toEqual(getFilterState(db).filter);
  });

  it("returns the channel's own filter once set", () => {
    setChannelFilterConfig(db, "ch1", customFilter);
    const resolved = getResolvedChannelFilter(db, "ch1");
    expect(resolved.inherited).toBe(false);
    expect(resolved.filter.likeTags).toEqual(["Kochen", "Garten"]);
    expect(resolved.filter.scoreThreshold).toBe(85);
  });

  it("keeps channels independent — ch2 still inherits when ch1 is customized", () => {
    setChannelFilterConfig(db, "ch1", customFilter);
    expect(getResolvedChannelFilter(db, "ch1").inherited).toBe(false);
    expect(getResolvedChannelFilter(db, "ch2").inherited).toBe(true);
  });

  it("reflects later global changes for inheriting channels", () => {
    setFilterConfig(db, { ...DEFAULT_FILTER, scoreThreshold: 42 });
    expect(getFilterForChannel(db, "ch2").scoreThreshold).toBe(42);
  });

  it("upserts: a second setChannelFilterConfig replaces the first", () => {
    setChannelFilterConfig(db, "ch1", customFilter);
    setChannelFilterConfig(db, "ch1", { ...DEFAULT_FILTER, scoreThreshold: 50 });
    expect(getFilterForChannel(db, "ch1").scoreThreshold).toBe(50);
  });

  it("clearChannelFilter reverts to inheriting the global filter", () => {
    setChannelFilterConfig(db, "ch1", customFilter);
    clearChannelFilter(db, "ch1");
    expect(getResolvedChannelFilter(db, "ch1").inherited).toBe(true);
  });

  it("uses the channel prompt override over the built prompt", () => {
    setChannelPromptOverride(db, "ch1", "MY CUSTOM PROMPT");
    expect(getActivePromptForChannel(db, "ch1")).toBe("MY CUSTOM PROMPT");
  });

  it("builds the prompt from the channel filter when no override is set", () => {
    setChannelFilterConfig(db, "ch1", customFilter);
    expect(getActivePromptForChannel(db, "ch1")).toContain("Kochen");
  });

  it("falls back to the global prompt for inheriting channels", () => {
    expect(getActivePromptForChannel(db, "ch2")).toContain("Hikari");
  });

  it("cascades the filter row away when the channel is deleted", () => {
    setChannelFilterConfig(db, "ch1", customFilter);
    db.prepare("DELETE FROM channels WHERE id = ?").run("ch1");
    const row = db.prepare("SELECT 1 FROM channel_filters WHERE channel_id = ?").get("ch1");
    expect(row).toBeUndefined();
  });
});
