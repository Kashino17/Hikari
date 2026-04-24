import Database from "better-sqlite3";
import { describe, expect, it } from "vitest";
import { applyMigrations } from "./migrations.js";

describe("applyMigrations", () => {
  it("creates all 6 tables on a fresh database", () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    const tables = db
      .prepare("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")
      .all() as { name: string }[];
    const names = tables.map((t) => t.name);
    expect(names).toEqual([
      "channels",
      "downloaded_videos",
      "feed_items",
      "scores",
      "sponsor_segments",
      "videos",
    ]);
  });

  it("is idempotent — can run twice without error", () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    expect(() => applyMigrations(db)).not.toThrow();
  });
});
