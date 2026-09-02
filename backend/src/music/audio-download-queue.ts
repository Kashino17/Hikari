import { createWriteStream, existsSync } from "node:fs";
import { mkdir, rename, unlink } from "node:fs/promises";
import { join } from "node:path";
import { Readable } from "node:stream";
import { pipeline } from "node:stream/promises";
import { openMediaStream } from "../stream/proxy.js";

export type AudioJobStatus = "queued" | "downloading" | "waiting" | "done" | "failed";

export interface AudioJob {
  videoId: string;
  status: AudioJobStatus;
  /** Bereits geschriebene Bytes des laufenden Versuchs. */
  bytes: number;
  /** Gesamtgröße, sobald googlevideo sie nennt. */
  total?: number | undefined;
  attempts: number;
  enqueuedAt: number;
  /** Wann der nächste Versuch startet (nur bei status=waiting). */
  retryAt?: number | undefined;
  /** Endgültiger Fehler (nur bei status=failed). */
  error?: string;
  /** Letzter Fehler, auch wenn noch Versuche folgen — für die Anzeige „wartet wegen …". */
  lastError?: string;
}

export interface AudioDownloadQueueOpts {
  /** Zielverzeichnis der fertigen Dateien (`<videoId>.m4a`). */
  dir: string;
  /** Löst die googlevideo-URL auf; `force` erzwingt eine frische Auflösung. */
  resolve: (videoId: string, force: boolean) => Promise<string | undefined>;
  fetchImpl?: typeof fetch;
  /** Millisekunden, bis die aktuelle Drossel-Welle vorbei ist (0 = keine). */
  throttledForMs?: () => number;
  /** Meldet einen Upstream-403 an den Wellen-Brecher. */
  onUpstreamFail?: (videoId: string) => void;
  now?: () => number;
  sleep?: (ms: number) => Promise<void>;
  log?: {
    info: (obj: object, msg: string) => void;
    warn: (obj: object, msg: string) => void;
  };
  /** Pause zwischen zwei Songs — YouTube zählt Tempo, nicht nur Menge. */
  gapMs?: number;
  /** Wartezeiten nach Fehlschlägen; der letzte Wert wiederholt sich. */
  backoffMs?: number[];
  /** Gesamtbudget pro Song, danach gilt er als gescheitert. */
  maxTotalMs?: number;
  /** So oft darf die URL-Auflösung außerhalb einer Welle scheitern. */
  maxResolveFails?: number;
}

/** Fehler, nach dem sich ein weiterer Versuch lohnt (Drossel, Netz, Stillstand). */
class RetryableError extends Error {}

const DEFAULT_BACKOFF_MS = [30_000, 60_000, 120_000, 300_000, 600_000];
const DEFAULT_MAX_TOTAL_MS = 3 * 60 * 60 * 1000;

/**
 * Serielle Download-Warteschlange für Musik auf dem Server.
 *
 * Warum nicht einfach durch den Proxy laden, wie die App es bisher tat:
 * googlevideo sperrt die Server-IP nach einer Handvoll schneller Downloads
 * mit 403 auf alles (gemessen 02.09.2026: nach 9 Songs). Der Proxy kann
 * dann nur 502/503 liefern, und die App wertete das als endgültig — die
 * restliche Playlist scheiterte binnen Sekunden.
 *
 * Hier läuft stattdessen ein Song nach dem anderen mit Atempause, ein 403
 * ist kein Fehler, sondern ein Grund zu warten, und die fertige Datei liegt
 * anschließend auf der Platte: Die App holt sie in LAN-Geschwindigkeit ab —
 * derselbe Weg, über den Serien und Filme schon immer aufs Gerät kommen.
 */
export class AudioDownloadQueue {
  private readonly jobs = new Map<string, AudioJob>();
  private readonly order: string[] = [];
  private readonly cancelled = new Set<string>();
  private running = false;

  private readonly now: () => number;
  private readonly sleep: (ms: number) => Promise<void>;
  private readonly fetchImpl: typeof fetch;
  private readonly throttledForMs: () => number;
  private readonly onUpstreamFail: (videoId: string) => void;
  private readonly log: NonNullable<AudioDownloadQueueOpts["log"]>;
  private readonly gapMs: number;
  private readonly backoffMs: number[];
  private readonly maxTotalMs: number;
  private readonly maxResolveFails: number;

  constructor(private readonly opts: AudioDownloadQueueOpts) {
    this.now = opts.now ?? Date.now;
    this.sleep = opts.sleep ?? ((ms) => new Promise((r) => setTimeout(r, ms)));
    this.fetchImpl = opts.fetchImpl ?? fetch;
    this.throttledForMs = opts.throttledForMs ?? (() => 0);
    this.onUpstreamFail = opts.onUpstreamFail ?? (() => undefined);
    this.log = opts.log ?? { info: () => undefined, warn: () => undefined };
    this.gapMs = opts.gapMs ?? 2_000;
    this.backoffMs = opts.backoffMs?.length ? opts.backoffMs : DEFAULT_BACKOFF_MS;
    this.maxTotalMs = opts.maxTotalMs ?? DEFAULT_MAX_TOTAL_MS;
    this.maxResolveFails = opts.maxResolveFails ?? 3;
  }

  filePath(videoId: string): string {
    return join(this.opts.dir, `${videoId}.m4a`);
  }

  /** Liegt die fertige Datei schon da? */
  isDone(videoId: string): boolean {
    return existsSync(this.filePath(videoId));
  }

  /** Song einreihen; ein bereits fertiger oder laufender Song wird nicht doppelt geladen. */
  enqueue(videoId: string): AudioJob {
    const existing = this.jobs.get(videoId);
    if (existing && existing.status !== "failed") return existing;
    if (this.isDone(videoId)) return this.doneJob(videoId);
    const job: AudioJob = {
      videoId,
      status: "queued",
      bytes: 0,
      attempts: 0,
      enqueuedAt: this.now(),
    };
    this.jobs.set(videoId, job);
    this.cancelled.delete(videoId);
    this.order.push(videoId);
    void this.run();
    return job;
  }

  get(videoId: string): AudioJob | undefined {
    const job = this.jobs.get(videoId);
    if (job) return job;
    return this.isDone(videoId) ? this.doneJob(videoId) : undefined;
  }

  /**
   * Nimmt einen Song wieder heraus. Ein laufender Download wird nach dem
   * aktuellen Block abgebrochen; die Teildatei verschwindet.
   */
  remove(videoId: string): boolean {
    const job = this.jobs.get(videoId);
    if (!job || job.status === "done") return false;
    this.cancelled.add(videoId);
    if (job.status !== "downloading") {
      this.jobs.delete(videoId);
      const i = this.order.indexOf(videoId);
      if (i >= 0) this.order.splice(i, 1);
    }
    return true;
  }

  snapshot(): AudioJob[] {
    return [...this.jobs.values()];
  }

  private doneJob(videoId: string): AudioJob {
    return {
      videoId,
      status: "done",
      bytes: 0,
      attempts: 0,
      enqueuedAt: this.now(),
    };
  }

  private async run(): Promise<void> {
    if (this.running) return;
    this.running = true;
    try {
      await mkdir(this.opts.dir, { recursive: true });
      while (this.order.length > 0) {
        const videoId = this.order.shift();
        if (!videoId) continue;
        const job = this.jobs.get(videoId);
        if (!job || this.cancelled.has(videoId)) {
          this.jobs.delete(videoId);
          continue;
        }
        await this.process(job);
        if (this.order.length > 0 && this.gapMs > 0) await this.sleep(this.gapMs);
      }
    } finally {
      this.running = false;
    }
  }

  private async process(job: AudioJob): Promise<void> {
    const part = `${this.filePath(job.videoId)}.part`;
    let resolveFails = 0;

    for (;;) {
      if (this.cancelled.has(job.videoId)) {
        this.jobs.delete(job.videoId);
        return;
      }
      // Läuft gerade eine Welle, gar nicht erst anklopfen — jeder Versuch
      // verlängert sie.
      const throttled = this.throttledForMs();
      if (throttled > 0) {
        await this.wait(job, throttled + 1_000, "YouTube drosselt gerade");
        continue;
      }

      job.status = "downloading";
      job.attempts += 1;
      job.bytes = 0;
      job.retryAt = undefined;
      try {
        const url = await this.opts.resolve(job.videoId, job.attempts > 1);
        if (!url) {
          resolveFails += 1;
          if (this.throttledForMs() <= 0 && resolveFails >= this.maxResolveFails) {
            return this.fail(job, "Audio-URL nicht auflösbar — Video nicht verfügbar?");
          }
          throw new RetryableError("Audio-URL nicht auflösbar");
        }
        resolveFails = 0;

        const notes: string[] = [];
        const media = await openMediaStream(url, {
          fetchImpl: this.fetchImpl,
          onNote: (m) => notes.push(m),
        });
        if (!media) {
          this.onUpstreamFail(job.videoId);
          throw new RetryableError(`googlevideo verweigert (${notes.slice(-2).join("; ")})`);
        }
        job.total = media.total;

        const tap = this.countBytes(media.chunks, job);
        await pipeline(Readable.from(tap), createWriteStream(part));
        if (this.cancelled.has(job.videoId)) {
          await unlink(part).catch(() => undefined);
          this.jobs.delete(job.videoId);
          return;
        }
        if (job.total !== undefined && job.bytes < job.total) {
          throw new RetryableError(`Datei unvollständig (${job.bytes}/${job.total})`);
        }
        await rename(part, this.filePath(job.videoId));
        job.status = "done";
        this.log.info(
          { videoId: job.videoId, bytes: job.bytes, attempts: job.attempts },
          "audio download done",
        );
        return;
      } catch (err) {
        await unlink(part).catch(() => undefined);
        const msg = err instanceof Error ? err.message : String(err);
        job.lastError = msg;
        if (this.now() - job.enqueuedAt > this.maxTotalMs) {
          return this.fail(job, `Aufgegeben nach ${job.attempts} Versuchen: ${msg}`);
        }
        const backoff = this.backoffMs[Math.min(job.attempts - 1, this.backoffMs.length - 1)] ?? 0;
        await this.wait(job, Math.max(backoff, this.throttledForMs()), msg);
      }
    }
  }

  private async wait(job: AudioJob, ms: number, reason: string): Promise<void> {
    job.status = "waiting";
    job.retryAt = this.now() + ms;
    job.lastError = reason;
    this.log.warn({ videoId: job.videoId, waitMs: ms, reason }, "audio download waiting");
    // In Scheiben schlafen, damit ein Abbruch nicht minutenlang hängt.
    let remaining = ms;
    while (remaining > 0 && !this.cancelled.has(job.videoId)) {
      const slice = Math.min(remaining, 1_000);
      await this.sleep(slice);
      remaining -= slice;
    }
  }

  private fail(job: AudioJob, error: string): void {
    job.status = "failed";
    job.error = error;
    this.log.warn({ videoId: job.videoId, error, attempts: job.attempts }, "audio download failed");
  }

  private async *countBytes(
    chunks: AsyncGenerator<Uint8Array>,
    job: AudioJob,
  ): AsyncGenerator<Uint8Array> {
    for await (const piece of chunks) {
      job.bytes += piece.byteLength;
      yield piece;
      if (this.cancelled.has(job.videoId)) return;
    }
  }
}
