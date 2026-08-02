import { mkdirSync } from "node:fs";
import { dirname } from "node:path";
import Database from "better-sqlite3";
import { applyMigrations } from "./migrations.js";

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
  installStatementCache(db);
  return db;
}

const STMT_CACHE_MAX = 512;

// better-sqlite3 hält den nativen sqlite3_stmt jedes prepare() am Leben, bis
// der winzige JS-Wrapper irgendwann GC'd wird — und meldet den nativen Speicher
// nie an V8. Hot-Paths, die pro Request/Iteration neu preparen (Feed-Hydration,
// Manga-Sync mit ~37k prepares pro Full-Sync), lassen den RSS so unbegrenzt
// wachsen. Memoisierung per SQL-Text ist hier sicher: kein Call-Site nutzt
// .iterate()/.pluck()/.raw()/.bind(), Statements laufen also stets synchron
// durch und tragen keinen persistenten Modus-Zustand.
function installStatementCache(db: Database.Database): void {
  const rawPrepare = db.prepare.bind(db);
  const cache = new Map<string, Database.Statement>();
  (db as { prepare: (sql: string) => Database.Statement }).prepare = (sql: string) => {
    let stmt = cache.get(sql);
    if (stmt) {
      cache.delete(sql);
      cache.set(sql, stmt); // LRU: Treffer nach hinten
      return stmt;
    }
    stmt = rawPrepare(sql);
    if (cache.size >= STMT_CACHE_MAX) {
      const oldest = cache.keys().next().value;
      if (oldest !== undefined) cache.delete(oldest);
    }
    cache.set(sql, stmt);
    return stmt;
  };
}
