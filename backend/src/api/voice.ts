import { execFile } from "node:child_process";
import { existsSync } from "node:fs";
import { homedir } from "node:os";
import { extname, join } from "node:path";
import type { FastifyInstance } from "fastify";
import type { VoiceJob, VoiceJobStore, VoiceProcessor } from "../voice/jobs.js";
import type { WhisperWorker } from "../voice/whisper.js";

export interface VoiceDeps {
  store: VoiceJobStore;
  processor: VoiceProcessor;
  whisper: WhisperWorker;
  lmstudio: { baseUrl: string; model: string };
  fetchImpl?: typeof fetch;
  /** Für Tests: LLM sofort entladen. */
  unloadLlm?: () => Promise<void>;
}

const MAX_FILE_BYTES = 200 * 1024 * 1024;
const MAX_FILES = 30;
const AUDIO_EXT = /^\.(opus|ogg|oga|m4a|mp4|aac|mp3|wav|amr|3gp|flac|webm|caf)$/i;

function extFor(name: string, mime: string): string {
  const e = extname(name).toLowerCase();
  if (AUDIO_EXT.test(e)) return e;
  if (/ogg|opus/.test(mime)) return ".ogg";
  if (/mp4|m4a|aac/.test(mime)) return ".m4a";
  if (/mpeg/.test(mime)) return ".mp3";
  if (/wav/.test(mime)) return ".wav";
  if (/amr/.test(mime)) return ".amr";
  // ffmpeg erkennt das Format am Inhalt, die Endung ist nur Hinweis.
  return ".audio";
}

function totalDuration(job: VoiceJob): number | null {
  if (job.clips.some((c) => c.duration == null)) return null;
  return job.clips.reduce((s, c) => s + (c.duration ?? 0), 0);
}

function compact(job: VoiceJob) {
  return {
    id: job.id,
    createdAt: job.createdAt,
    updatedAt: job.updatedAt,
    status: job.status,
    done: job.done,
    total: job.clips.length,
    label: job.label,
    title: job.summary?.title ?? null,
    preview: job.summary?.summary.slice(0, 220) ?? null,
    todoCount: job.summary?.todos.length ?? 0,
    duration: totalDuration(job),
    error: job.error,
  };
}

function full(job: VoiceJob, position: number) {
  return {
    ...compact(job),
    queuePosition: position,
    summary: job.summary,
    clips: job.clips.map((c, i) => ({
      index: i + 1,
      name: c.name,
      size: c.size,
      duration: c.duration,
      language: c.language,
      text: c.text,
    })),
    transcribeMs: job.transcribeMs,
    summarizeMs: job.summarizeMs,
  };
}

function lmsUnload(model: string): Promise<void> {
  const lms = join(homedir(), ".lmstudio", "bin", "lms");
  if (!existsSync(lms)) return Promise.resolve();
  return new Promise((resolve) => {
    execFile(lms, ["unload", model], { timeout: 20_000 }, () => resolve());
  });
}

export async function registerVoiceRoutes(app: FastifyInstance, deps: VoiceDeps) {
  const { store, processor, whisper } = deps;
  const fetchImpl = deps.fetchImpl ?? fetch;

  app.post("/voice/jobs", async (req, reply) => {
    if (!req.isMultipart()) return reply.code(400).send({ error: "multipart_required" });
    const clips: { name: string; data: Buffer; ext: string }[] = [];
    let label: string | null = null;
    try {
      for await (const part of req.parts({
        limits: { fileSize: MAX_FILE_BYTES, files: MAX_FILES },
      })) {
        if (part.type === "file") {
          const data = await part.toBuffer();
          if (part.file.truncated) return reply.code(413).send({ error: "file_too_large" });
          if (data.length === 0) continue;
          const name = (part.filename || `Sprachnachricht ${clips.length + 1}`).slice(0, 200);
          clips.push({ name, data, ext: extFor(name, part.mimetype) });
        } else if (part.fieldname === "label") {
          const v = String(part.value ?? "")
            .trim()
            .slice(0, 120);
          label = v || null;
        }
      }
    } catch (err) {
      const code = (err as { code?: string }).code;
      if (code === "FST_REQ_FILE_TOO_LARGE")
        return reply.code(413).send({ error: "file_too_large" });
      if (code === "FST_FILES_LIMIT") return reply.code(413).send({ error: "too_many_files" });
      throw err;
    }
    if (clips.length === 0) return reply.code(400).send({ error: "no_audio" });
    const job = store.create(label, clips);
    processor.enqueue(job.id);
    req.log.info({ id: job.id, clips: clips.length }, "voice job queued");
    return reply.code(201).send(full(job, processor.position(job.id)));
  });

  app.get("/voice/jobs", async () => store.list().slice(0, 200).map(compact));

  app.get<{ Params: { id: string } }>("/voice/jobs/:id", async (req, reply) => {
    const job = store.get(req.params.id);
    if (!job) return reply.code(404).send({ error: "not_found" });
    return full(job, processor.position(job.id));
  });

  app.post<{ Params: { id: string } }>("/voice/jobs/:id/retry", async (req, reply) => {
    const job = store.get(req.params.id);
    if (!job) return reply.code(404).send({ error: "not_found" });
    if (job.status !== "failed") return reply.code(409).send({ error: "not_failed" });
    job.status = "queued";
    job.error = null;
    store.save(job);
    processor.enqueue(job.id);
    return full(job, processor.position(job.id));
  });

  app.delete<{ Params: { id: string } }>("/voice/jobs/:id", async (req, reply) => {
    const job = store.get(req.params.id);
    if (!job) return reply.code(404).send({ error: "not_found" });
    if (job.status === "transcribing" || job.status === "summarizing") {
      return reply.code(409).send({ error: "busy" });
    }
    processor.remove(job.id);
    store.delete(job.id);
    return reply.code(204).send();
  });

  /** Was gerade im Speicher liegt. */
  app.get("/voice/status", async () => {
    let llmLoaded: boolean | null = null;
    try {
      const r = await fetchImpl(`${deps.lmstudio.baseUrl}/api/v0/models`, {
        signal: AbortSignal.timeout(2000),
      });
      if (r.ok) {
        const body = (await r.json()) as { data?: { id: string; state?: string }[] };
        llmLoaded =
          body.data?.some((m) => m.id === deps.lmstudio.model && m.state === "loaded") ?? false;
      }
    } catch {
      llmLoaded = null; // LM Studio läuft nicht
    }
    return {
      whisper: whisper.status(),
      llm: { model: deps.lmstudio.model, loaded: llmLoaded, reachable: llmLoaded !== null },
      busy: processor.busy,
    };
  });

  /** Beide Modelle sofort freigeben, statt den Leerlauf abzuwarten. */
  app.post("/voice/unload", async (_req, reply) => {
    if (processor.busy) return reply.code(409).send({ error: "busy" });
    whisper.stop();
    await (deps.unloadLlm?.() ?? lmsUnload(deps.lmstudio.model));
    return { ok: true };
  });
}
