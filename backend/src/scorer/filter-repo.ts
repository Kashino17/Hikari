import type Database from "better-sqlite3";
import { DEFAULT_FILTER, buildPrompt, type FilterConfig } from "./filter.ts";

export interface FilterState {
  filter: FilterConfig;
  promptOverride: string | null;
  updatedAt: number;
}

/**
 * A channel's resolved filter, annotated with whether it came from the
 * channel's own override (`inherited: false`) or fell back to the global
 * filter (`inherited: true`).
 */
export interface ResolvedChannelFilterState extends FilterState {
  channelId: string;
  inherited: boolean;
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

// ---------------------------------------------------------------------------
// Per-channel filters — a channel either has its own row in channel_filters or
// inherits the global filter above.
// ---------------------------------------------------------------------------

function getChannelFilterRow(db: Database.Database, channelId: string): Row | undefined {
  return db
    .prepare(
      "SELECT filter_json, prompt_override, updated_at FROM channel_filters WHERE channel_id = ?",
    )
    .get(channelId) as Row | undefined;
}

/**
 * Resolves the effective filter for a channel: its own override if present,
 * otherwise the global filter. `inherited` tells the caller which it was.
 */
export function getResolvedChannelFilter(
  db: Database.Database,
  channelId: string,
): ResolvedChannelFilterState {
  const row = getChannelFilterRow(db, channelId);
  if (row) {
    return {
      channelId,
      inherited: false,
      filter: JSON.parse(row.filter_json) as FilterConfig,
      promptOverride: row.prompt_override,
      updatedAt: row.updated_at,
    };
  }
  const global = getFilterState(db);
  return { channelId, inherited: true, ...global };
}

/** The FilterConfig to enforce for a channel (own → global fallback). */
export function getFilterForChannel(db: Database.Database, channelId: string): FilterConfig {
  return getResolvedChannelFilter(db, channelId).filter;
}

/**
 * The system prompt to send when scoring a video from this channel:
 * channel prompt_override → channel filter prompt → global active prompt.
 */
export function getActivePromptForChannel(db: Database.Database, channelId: string): string {
  const row = getChannelFilterRow(db, channelId);
  if (row) {
    return row.prompt_override ?? buildPrompt(JSON.parse(row.filter_json) as FilterConfig);
  }
  return getActivePrompt(db);
}

/** Sets (or replaces) a channel's own filter. Atomic upsert keyed on channel_id. */
export function setChannelFilterConfig(
  db: Database.Database,
  channelId: string,
  filter: FilterConfig,
): void {
  db.prepare(
    `INSERT INTO channel_filters (channel_id, filter_json, prompt_override, updated_at)
     VALUES (?, ?, NULL, ?)
     ON CONFLICT(channel_id) DO UPDATE SET filter_json = excluded.filter_json,
                                           updated_at = excluded.updated_at`,
  ).run(channelId, JSON.stringify(filter), Date.now());
}

export function setChannelPromptOverride(
  db: Database.Database,
  channelId: string,
  override: string | null,
): void {
  const row = getChannelFilterRow(db, channelId);
  if (!row) {
    // Seed from the global filter so the channel row is consistent.
    const global = getFilterState(db);
    db.prepare(
      `INSERT INTO channel_filters (channel_id, filter_json, prompt_override, updated_at)
       VALUES (?, ?, ?, ?)`,
    ).run(channelId, JSON.stringify(global.filter), override, Date.now());
    return;
  }
  db.prepare(
    "UPDATE channel_filters SET prompt_override = ?, updated_at = ? WHERE channel_id = ?",
  ).run(override, Date.now(), channelId);
}

/** Removes a channel's override so it inherits the global filter again. */
export function clearChannelFilter(db: Database.Database, channelId: string): void {
  db.prepare("DELETE FROM channel_filters WHERE channel_id = ?").run(channelId);
}
