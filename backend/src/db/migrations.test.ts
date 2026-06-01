import Database from "better-sqlite3";
import { describe, expect, it } from "vitest";
import { applyMigrations } from "./migrations.js";

describe("applyMigrations", () => {
  it("creates all expected tables on a fresh database", () => {
    const db = new Database(":memory:");
    applyMigrations(db);

    const tables = db
      .prepare("SELECT name FROM sqlite_master WHERE type='table'")
      .all() as { name: string }[];
    const tableNames = tables.map((t) => t.name);

    expect(tableNames).toContain("channels");
    expect(tableNames).toContain("videos");
    expect(tableNames).toContain("scores");
    expect(tableNames).toContain("feed_items");
    expect(tableNames).toContain("sponsor_segments");
    expect(tableNames).toContain("downloaded_videos");
    expect(tableNames).toContain("filter_config");
    expect(tableNames).toContain("channel_filters");
  });

  it("is idempotent (can run twice without error)", () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    expect(() => applyMigrations(db)).not.toThrow();
  });
});
