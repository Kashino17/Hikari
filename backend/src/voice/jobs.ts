import { randomUUID } from "node:crypto";
import {
  existsSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  renameSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import { join } from "node:path";
import type { VoiceSummary } from "./summarize.js";
import type { Transcript } from "./whisper.js";

export type VoiceJobStatus = "queued" | "transcribing" | "summarizing" | "done" | "failed";

export interface VoiceClip {
  /** Dateiname im Job-Ordner. */
  file: string;
  /** Ursprünglicher Name (z. B. PTT-20261106-WA0012.opus). */
  name: string;
  size: number;
  duration: number | null;
  text: string | null;
  language: string | null;
}

export interface VoiceJob {
  id: string;
  createdAt: number;
  updatedAt: number;
  status: VoiceJobStatus;
  /** Wie viele Clips schon transkribiert sind. */
  done: number;
  label: string | null;
  clips: VoiceClip[];
  summary: VoiceSummary | null;
  error: string | null;
  transcribeMs: number | null;
  summarizeMs: number | null;
}

/** Whisper-Halluzinationen in Stille (bekannt aus deutschen Untertitel-Trainingsdaten). */
const HALLUCINATION = /untertitel (im auftrag|der|von)|amara\.org|copyright wdr/i;

/** Sätze mit typischen Whisper-Halluzinationen entfernen. */
export function cleanTranscript(text: string): string {
  // Satzende = Satzzeichen + Leerraum; "Amara.org" bleibt so ein Satz.
  return text
    .split(/(?<=[.!?])\s+/)
    .filter((s) => !HALLUCINATION.test(s))
    .join(" ")
    .replace(/[ \t]+/g, " ")
    .trim();
}

const WA_NAME = /(?:PTT|AUD)-(\d{8})-WA(\d+)/i;

/**
 * Bringt Clips in die Gesprächsreihenfolge. WhatsApp benennt Sprachnachrichten
 * PTT-JJJJMMTT-WAnnnn; tragen alle Clips so einen Namen, wird danach sortiert,
 * sonst bleibt die Reihenfolge des Uploads.
 */
export function orderClips<T extends { name: string }>(clips: T[]): T[] {
  const keys = clips.map((c) => WA_NAME.exec(c.name));
  if (keys.some((k) => !k)) return clips;
  return clips
    .map((c, i) => ({ c, k: keys[i]!, i }))
    .sort((a, b) => a.k[1]!.localeCompare(b.k[1]!) || Number(a.k[2]) - Number(b.k[2]) || a.i - b.i)
    .map((x) => x.c);
}

export function formatDuration(sec: number): string {
  const s = Math.round(sec);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;
}

/** Ein Text für das Sprachmodell: jede Nachricht mit Überschrift. */
export function combinedTranscript(clips: VoiceClip[]): string {
  if (clips.length === 1) return clips[0]!.text ?? "";
  return clips
    .map((c, i) => {
      const len = c.duration != null ? ` (${formatDuration(c.duration)})` : "";
      return `### Sprachnachricht ${i + 1}${len}\n${c.text?.trim() || "[nichts verständlich]"}`;
    })
    .join("\n\n");
}

export class VoiceJobStore {
  constructor(private readonly dir: string) {
    mkdirSync(dir, { recursive: true });
  }

  jobDir(id: string): string {
    if (!/^[a-f0-9-]{36}$/.test(id)) throw new Error("bad_job_id");
    return join(this.dir, id);
  }

  create(label: string | null, clips: { name: string; data: Buffer; ext: string }[]): VoiceJob {
    const id = randomUUID();
    const dir = this.jobDir(id);
    mkdirSync(dir, { recursive: true });
    const ordered = orderClips(clips);
    const now = Date.now();
    const job: VoiceJob = {
      id,
      createdAt: now,
      updatedAt: now,
      status: "queued",
      done: 0,
      label,
      clips: ordered.map((c, i) => {
        const file = `${String(i + 1).padStart(2, "0")}${c.ext}`;
        writeFileSync(join(dir, file), c.data);
        return {
          file,
          name: c.name,
          size: c.data.length,
          duration: null,
          text: null,
          language: null,
        };
      }),
      summary: null,
      error: null,
      transcribeMs: null,
      summarizeMs: null,
    };
    this.save(job);
    return job;
  }

  save(job: VoiceJob): void {
    job.updatedAt = Date.now();
    const file = join(this.jobDir(job.id), "job.json");
    // Erst schreiben, dann umbenennen: ein Absturz hinterlässt nie halbes JSON.
    writeFileSync(`${file}.tmp`, JSON.stringify(job, null, 2));
    renameSync(`${file}.tmp`, file);
  }

  get(id: string): VoiceJob | null {
    let file: string;
    try {
      file = join(this.jobDir(id), "job.json");
    } catch {
      return null;
    }
    if (!existsSync(file)) return null;
    try {
      return JSON.parse(readFileSync(file, "utf8")) as VoiceJob;
    } catch {
      return null;
    }
  }

  list(): VoiceJob[] {
    const jobs: VoiceJob[] = [];
    for (const name of readdirSync(this.dir)) {
      const j = this.get(name);
      if (j) jobs.push(j);
    }
    return jobs.sort((a, b) => b.createdAt - a.createdAt);
  }

  delete(id: string): boolean {
    const j = this.get(id);
    if (!j) return false;
    rmSync(this.jobDir(id), { recursive: true, force: true });
    return true;
  }

  clipPath(job: VoiceJob, clip: VoiceClip): string {
    return join(this.jobDir(job.id), clip.file);
  }
}

export interface VoiceProcessorDeps {
  store: VoiceJobStore;
  transcribe: (path: string) => Promise<Transcript>;
  summarize: (transcript: string) => Promise<VoiceSummary>;
  log?: (msg: string, extra?: Record<string, unknown>) => void;
}

/**
 * Arbeitet Jobs streng nacheinander ab: erst alle Clips durch Whisper, dann
 * das Ganze durch Qwen. Bereits transkribierte Clips werden beim erneuten
 * Versuch übersprungen.
 */
export class VoiceProcessor {
  private queue: string[] = [];
  private running = false;
  private idleWaiters: (() => void)[] = [];

  constructor(private readonly deps: VoiceProcessorDeps) {}

  /** Nach Neustart: alles Unfertige wieder einreihen. */
  recover(): number {
    let n = 0;
    for (const j of this.deps.store.list().reverse()) {
      if (j.status === "queued" || j.status === "transcribing" || j.status === "summarizing") {
        this.enqueue(j.id);
        n++;
      }
    }
    return n;
  }

  enqueue(id: string): void {
    if (!this.queue.includes(id)) this.queue.push(id);
    void this.pump();
  }

  /** Läuft gerade ein Job? */
  get busy(): boolean {
    return this.running;
  }

  /** Wartenden Job aus der Schlange nehmen (der laufende bleibt). */
  remove(id: string): void {
    const i = this.queue.indexOf(id);
    if (i > 0 || (i === 0 && !this.running)) this.queue.splice(i, 1);
  }

  /** Position in der Warteschlange (0 = wird gerade bearbeitet oder nicht drin). */
  position(id: string): number {
    const i = this.queue.indexOf(id);
    return i < 0 ? 0 : i;
  }

  /** Für Tests: wartet, bis die Warteschlange leer ist. */
  idle(): Promise<void> {
    if (!this.running && this.queue.length === 0) return Promise.resolve();
    return new Promise((r) => this.idleWaiters.push(r));
  }

  private async pump(): Promise<void> {
    if (this.running) return;
    this.running = true;
    try {
      while (this.queue.length > 0) {
        const id = this.queue[0]!;
        try {
          await this.process(id);
        } catch (err) {
          this.deps.log?.("voice job crashed", { id, err: String(err) });
        }
        this.queue.shift();
      }
    } finally {
      this.running = false;
      for (const w of this.idleWaiters.splice(0)) w();
    }
  }

  private async process(id: string): Promise<void> {
    const { store } = this.deps;
    const job = store.get(id);
    if (!job || job.status === "done") return;
    job.error = null;
    try {
      if (job.clips.some((c) => c.text === null)) {
        job.status = "transcribing";
        job.done = job.clips.filter((c) => c.text !== null).length;
        store.save(job);
        const t0 = Date.now();
        for (const clip of job.clips) {
          if (clip.text !== null) continue;
          const r = await this.deps.transcribe(store.clipPath(job, clip));
          clip.text = cleanTranscript(r.text);
          clip.duration = r.duration;
          clip.language = r.language;
          job.done++;
          store.save(job);
        }
        job.transcribeMs = Date.now() - t0;
      }
      const text = combinedTranscript(job.clips);
      if (!text.replace(/### Sprachnachricht \d+.*|\[nichts verständlich\]/g, "").trim()) {
        throw new Error("Auf den Aufnahmen war nichts zu verstehen.");
      }
      job.status = "summarizing";
      store.save(job);
      const t1 = Date.now();
      job.summary = await this.deps.summarize(text);
      job.summarizeMs = Date.now() - t1;
      job.status = "done";
      store.save(job);
      this.deps.log?.("voice job done", {
        id,
        clips: job.clips.length,
        transcribeMs: job.transcribeMs,
        summarizeMs: job.summarizeMs,
      });
    } catch (err) {
      job.status = "failed";
      job.error = err instanceof Error ? err.message : String(err);
      store.save(job);
      this.deps.log?.("voice job failed", { id, err: job.error });
    }
  }
}
