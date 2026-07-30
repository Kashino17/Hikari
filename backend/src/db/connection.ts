import { mkdirSync } from "node:fs";
import { dirname } from "node:path";
import Database from "better-sqlite3";
import { applyMigrations } from "./migrations.ts";

export function openDatabase(filePath: string): Database.Database {
  mkdirSync(dirname(filePath), { recursive: true });
  const db = new Database(filePath);
  db.pragma("journal_mode = WAL");
  db.pragma("foreign_keys = ON");
  // Background ingest (cron poll) and API requests share this connection's
  // writer. Without a busy timeout a colliding write throws SQLITE_BUSY
  // immediately; 5s lets the contending statement wait for the lock.
  db.pragma("busy_timeout = 5000");
  applyMigrations(db);
  return db;
}
