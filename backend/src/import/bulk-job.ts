import type { ImportResult } from "./manual-import.js";

/**
 * Fortschrittsverfolgung für Bulk-Importe.
 *
 * Der Bulk-Endpunkt antwortet sofort mit 202, weil eine Serie mit zwanzig
 * Folgen je nach Hoster eine halbe Stunde lädt — die App darf darauf nicht
 * warten. Ohne Verfolgung war der Import danach aber eine Blackbox: Fehler
 * verschwanden lautlos, und der Nutzer sah nur, dass Folgen fehlen. Der Store
 * hält deshalb je Job die Einzelergebnisse fest, abrufbar über
 * GET /videos/import/bulk/status.
 *
 * Bewusst nur im Speicher: Ein Serverneustart bricht laufende Downloads
 * ohnehin ab, ein persistierter Job wäre danach nur eine Leiche. Es werden die
 * letzten MAX_JOBS Jobs behalten, damit ein Dauerbetrieb nicht leckt.
 */
export interface BulkJob {
  id: string;
  total: number;
  ok: number;
  duplicate: number;
  failed: number;
  results: ImportResult[];
  startedAt: number;
  finishedAt: number | null;
}

const MAX_JOBS = 20;
const jobs = new Map<string, BulkJob>();
let latestId: string | null = null;
let counter = 0;

export function startBulkJob(total: number): BulkJob {
  counter += 1;
  const job: BulkJob = {
    id: `bulk-${Date.now().toString(36)}-${counter}`,
    total,
    ok: 0,
    duplicate: 0,
    failed: 0,
    results: [],
    startedAt: Date.now(),
    finishedAt: null,
  };
  jobs.set(job.id, job);
  latestId = job.id;

  // Älteste Jobs verwerfen (Map bewahrt Einfügereihenfolge).
  while (jobs.size > MAX_JOBS) {
    const oldest = jobs.keys().next().value;
    if (oldest === undefined) break;
    jobs.delete(oldest);
  }
  return job;
}

export function recordBulkResult(job: BulkJob, result: ImportResult): void {
  job.results.push(result);
  if (result.status === "ok") job.ok += 1;
  else if (result.status === "duplicate") job.duplicate += 1;
  else job.failed += 1;
}

export function finishBulkJob(job: BulkJob): void {
  job.finishedAt = Date.now();
}

export function getBulkJob(id: string): BulkJob | undefined {
  return jobs.get(id);
}

export function getLatestBulkJob(): BulkJob | undefined {
  return latestId ? jobs.get(latestId) : undefined;
}

/** Nur für Tests — setzt den Store zurück. */
export function resetBulkJobs(): void {
  jobs.clear();
  latestId = null;
  counter = 0;
}
