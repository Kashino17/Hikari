import type Database from "better-sqlite3";
import { type BulkJob, finishBulkJob, recordBulkResult, startBulkJob } from "./bulk-job.js";
import {
  type ImportResult,
  type ManualMetadata,
  type SniffedMedia,
  importDirectLink,
  importSniffedMedia,
} from "./manual-import.js";

/**
 * Ein Bulk-Import, der Direktlinks und mitgelesene Streams gemeinsam
 * abarbeitet.
 *
 * Vorher liefen beide Sorten als zwei getrennte Jobs — die App konnte nur den
 * jüngeren beobachten, Fehler des ersten verschwanden. Jetzt: ein Job, eine
 * Statusabfrage.
 *
 * Verteilung: pro Hoster seriell (Filehoster beantworten parallele Downloads
 * derselben IP mit 403/429), verschiedene Hoster parallel, aber höchstens
 * MAX_PARALLEL_HOSTS gleichzeitig — sonst hängen bei einer Staffel mit vier
 * Hostern vier yt-dlp-Prozesse an derselben Leitung.
 *
 * Neuversuch: Netzfehler, 403/429/5xx und Timeouts bekommen bis zu zwei
 * weitere Anläufe mit Pause. Ein 403 nach neun Folgen ist eine Drossel, kein
 * Urteil über die Folge.
 */
export type DirectBulkItem = { url: string; metadata?: ManualMetadata };
export type SniffedBulkItem = SniffedMedia & { metadata?: ManualMetadata };
export type BulkItem = DirectBulkItem | SniffedBulkItem;

export function isSniffedItem(item: BulkItem): item is SniffedBulkItem {
  return (
    typeof (item as SniffedBulkItem).mediaUrl === "string" &&
    (item as SniffedBulkItem).mediaUrl.length > 0
  );
}

export function itemUrl(item: BulkItem): string {
  return isSniffedItem(item) ? item.pageUrl : item.url;
}

export interface BulkRunnerDeps {
  db: Database.Database;
  videoDir: string;
  coverDir?: string;
  log: {
    info: (obj: Record<string, unknown>, msg: string) => void;
    error: (obj: Record<string, unknown>, msg: string) => void;
  };
  /** Test-Injektion. */
  importDirect?: typeof importDirectLink;
  importSniffed?: typeof importSniffedMedia;
  sleep?: (ms: number) => Promise<void>;
  retryDelaysMs?: number[];
  maxParallelHosts?: number;
}

const DEFAULT_RETRY_DELAYS_MS = [15_000, 45_000];
const MAX_PARALLEL_HOSTS = 3;

const NON_RETRYABLE = [
  "empty url",
  "empty media url",
  "no video id",
  "unsupported url",
  "private video",
  "video unavailable",
  "sign in to confirm",
  "age-restricted",
  "not available",
  "file not found",
];

export function isRetryableImportError(error: string | undefined): boolean {
  if (!error) return false;
  const msg = error.toLowerCase();
  if (NON_RETRYABLE.some((s) => msg.includes(s))) return false;
  return (
    msg.includes("403") ||
    msg.includes("429") ||
    msg.includes("too many requests") ||
    msg.includes("rate limit") ||
    msg.includes("http error 5") ||
    msg.includes("502") ||
    msg.includes("503") ||
    msg.includes("504") ||
    msg.includes("timed out") ||
    msg.includes("timeout") ||
    msg.includes("connection") ||
    msg.includes("econnreset") ||
    msg.includes("econnrefused") ||
    msg.includes("getaddrinfo") ||
    msg.includes("network") ||
    msg.includes("unable to download") ||
    msg.includes("ssl") ||
    msg.includes("tls") ||
    msg.includes("eof occurred")
  );
}

function hostKey(item: BulkItem): string {
  const raw = isSniffedItem(item) ? item.mediaUrl : item.url;
  try {
    return new URL(raw).hostname;
  } catch {
    return "unknown";
  }
}

function defaultSleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/** Startet den Job und liefert ihn sofort zurück — die Arbeit läuft im Hintergrund. */
export function runBulkImport(deps: BulkRunnerDeps, items: BulkItem[]): BulkJob {
  const job = startBulkJob(items.length);
  void runBulkImportAndWait(deps, items, job);
  return job;
}

/** Wie runBulkImport, aber wartet auf das Ende (Tests, Retry-Endpunkt). */
export async function runBulkImportAndWait(
  deps: BulkRunnerDeps,
  items: BulkItem[],
  job: BulkJob = startBulkJob(items.length),
): Promise<BulkJob> {
  const importDirect = deps.importDirect ?? importDirectLink;
  const importSniffed = deps.importSniffed ?? importSniffedMedia;
  const sleep = deps.sleep ?? defaultSleep;
  const delays = deps.retryDelaysMs ?? DEFAULT_RETRY_DELAYS_MS;
  const maxParallel = deps.maxParallelHosts ?? MAX_PARALLEL_HOSTS;

  const buckets = new Map<string, BulkItem[]>();
  for (const item of items) {
    const key = hostKey(item);
    const bucket = buckets.get(key);
    if (bucket) bucket.push(item);
    else buckets.set(key, [item]);
  }

  const runOne = async (item: BulkItem): Promise<ImportResult> => {
    const url = itemUrl(item);
    let last: ImportResult = { url, status: "failed", error: "not started" };
    for (let attempt = 0; attempt <= delays.length; attempt++) {
      try {
        last = isSniffedItem(item)
          ? await importSniffed(deps.db, item, deps.videoDir, item.metadata, deps.coverDir)
          : await importDirect(deps.db, item.url, deps.videoDir, item.metadata, deps.coverDir);
      } catch (err) {
        last = { url, status: "failed", error: String(err).slice(0, 200) };
      }
      if (last.status !== "failed") return last;
      const delay = delays[attempt];
      if (delay === undefined || !isRetryableImportError(last.error)) return last;
      deps.log.info(
        { url, attempt: attempt + 1, error: last.error, delayMs: delay },
        "bulk import retry",
      );
      await sleep(delay);
    }
    return last;
  };

  const queue = [...buckets.values()];
  const worker = async () => {
    while (queue.length > 0) {
      const bucket = queue.shift();
      if (!bucket) return;
      for (const item of bucket) {
        const result = await runOne(item);
        recordBulkResult(job, result);
        if (result.status === "failed") {
          deps.log.error({ url: result.url, error: result.error }, "bulk import item failed");
        } else {
          deps.log.info(
            { url: result.url, status: result.status, videoId: result.videoId },
            "bulk import item done",
          );
        }
      }
    }
  };
  await Promise.all(Array.from({ length: Math.min(maxParallel, queue.length) }, () => worker()));

  finishBulkJob(job);
  deps.log.info(
    { total: job.total, ok: job.ok, duplicate: job.duplicate, failed: job.failed },
    "bulk import finished",
  );
  return job;
}
