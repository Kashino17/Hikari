/**
 * Tägliche Sicherung des Fitness-Bereichs: Datenbank (Trainings, Ernährung, Gewicht, Tests …) als
 * vollständige Kopie über die SQLite-Sicherung (geht im laufenden Betrieb) und neue Körperfotos.
 * Ziel: ~/.hikari/fitness-backup (getrennt vom Arbeitsordner), die letzten 14 Tage bleiben.
 */
import { copyFileSync, existsSync, mkdirSync, readdirSync, statSync, unlinkSync } from "node:fs";
import { join } from "node:path";
import { type FitDb, localDay } from "./db.js";

const KEEP = 14;

export async function backupNow(db: FitDb, fitDir: string, target: string, log?: (m: string) => void) {
  mkdirSync(target, { recursive: true });
  const file = join(target, `fitness-${localDay()}.db`);
  await db.backup(file);
  // Ältere Stände über die letzten 14 Tage hinaus löschen.
  const dbs = readdirSync(target).filter((f) => /^fitness-\d{4}-\d{2}-\d{2}\.db$/.test(f)).sort();
  for (const f of dbs.slice(0, Math.max(0, dbs.length - KEEP))) unlinkSync(join(target, f));
  // Körperfotos: nur neue kopieren (werden nie verändert).
  const src = join(fitDir, "body");
  let photos = 0;
  if (existsSync(src)) {
    const dst = join(target, "body");
    mkdirSync(dst, { recursive: true });
    for (const f of readdirSync(src)) if (!existsSync(join(dst, f))) { copyFileSync(join(src, f), join(dst, f)); photos++; }
  }
  log?.(`[fitness] Sicherung: ${file}${photos ? ` + ${photos} Fotos` : ""}`);
  return file;
}

export function backupInfo(target: string) {
  if (!existsSync(target)) return { last: null, count: 0 };
  const dbs = readdirSync(target).filter((f) => /^fitness-\d{4}-\d{2}-\d{2}\.db$/.test(f)).sort();
  const last = dbs[dbs.length - 1];
  return { last: last ? { day: last.slice(8, 18), at: statSync(join(target, last)).mtimeMs } : null, count: dbs.length };
}

/** Beim Start sichern (falls heute noch nicht) und dann alle 6 h prüfen. */
export function scheduleBackups(db: FitDb, fitDir: string, target: string, log?: (m: string) => void) {
  const tick = () => {
    const info = backupInfo(target);
    if (info.last?.day === localDay()) return;
    backupNow(db, fitDir, target, log).catch((e) => log?.(`[fitness] Sicherung fehlgeschlagen: ${e instanceof Error ? e.message : e}`));
  };
  setTimeout(tick, 20_000).unref();
  setInterval(tick, 6 * 3600_000).unref();
}
