import type Database from "better-sqlite3";
import { DEFAULT_FILTER, buildPrompt, type FilterConfig } from "./filter.js";

export interface FilterState {
  filter: FilterConfig;
  promptOverride: string | null;
  updatedAt: number;
}

interface Row {
  filter_json: string;
  prompt_override: string | null;
  updated_at: number;
}

/**
 * Reads the filter config row, seeding defaults on first access. Single-row
 * table (PK = 1) — single user, no auth.
 */
export function getFilterState(db: Database.Database): FilterState {
  const row = db
    .prepare("SELECT filter_json, prompt_override, updated_at FROM filter_config WHERE id = 1")
    .get() as Row | undefined;
  if (!row) {
    const now = Date.now();
    db.prepare(
      "INSERT INTO filter_config (id, filter_json, prompt_override, updated_at) VALUES (1, ?, NULL, ?)",
    ).run(JSON.stringify(DEFAULT_FILTER), now);
    return { filter: DEFAULT_FILTER, promptOverride: null, updatedAt: now };
  }
  return {
    filter: JSON.parse(row.filter_json) as FilterConfig,
    promptOverride: row.prompt_override,
    updatedAt: row.updated_at,
  };
}

export function setFilterConfig(db: Database.Database, filter: FilterConfig): void {
  // Ensure the row exists, then update. Defensive UPSERT keeps prompt_override.
  getFilterState(db);
  db.prepare(
    "UPDATE filter_config SET filter_json = ?, updated_at = ? WHERE id = 1",
  ).run(JSON.stringify(filter), Date.now());
}

export function setPromptOverride(db: Database.Database, override: string | null): void {
  getFilterState(db);
  db.prepare(
    "UPDATE filter_config SET prompt_override = ?, updated_at = ? WHERE id = 1",
  ).run(override, Date.now());
}

/**
 * Returns the system prompt the scorer should send: override if set,
 * otherwise the live-built prompt from the form. Called once per score.
 */
export function getActivePrompt(db: Database.Database): string {
  const s = getFilterState(db);
  return s.promptOverride ?? buildPrompt(s.filter);
}
