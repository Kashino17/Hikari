import { unlink } from "node:fs/promises";
import { join } from "node:path";
import { randomUUID } from "node:crypto";
import type Database from "better-sqlite3";
import { getFilterState } from "../scorer/filter-repo.js";
import { buildClipperPrompt } from "./prompt-builder.js";
import { complete, dequeue, fail, setStep } from "./queue.js";
import { QwenNetworkError } from "./qwen-analyzer.js";
import type { ClipSpec, AnalyzerConfig } from "./qwen-analyzer.js";
import type { RenderResult } from "./remotion-renderer.js";

export interface WorkerDeps {
  analyze: (
    input: { filePath: string; videoId: string; durationSec: number; transcript?: string },
    prompt: string,
    config: AnalyzerConfig,
  ) => Promise<ClipSpec[]>;
  render: (input: {
    inputPath: string;
    videoWidth: number;
    videoHeight: number;
    spec: ClipSpec;
    outputPath: string;
  }) => Promise<RenderResult>;
  mediaDir: string;
  analyzerConfig: AnalyzerConfig;
}

interface VideoRow {
  id: string;
  duration_seconds: number;
  aspect_ratio: string | null;
  transcript: string | null;
}
interface DownloadRow {
  file_path: string;
}

function parseAspect(aspect: string | null): { width: number; height: number } {
  if (!aspect) return { width: 1920, height: 1080 };
  const m = aspect.match(/^(\d+):(\d+)$/);
  if (!m) return { width: 1920, height: 1080 };
  const w = Number(m[1]);
  const h = Number(m[2]);
  if (w === 9 && h === 16) return { width: 1080, height: 1920 };
  return { width: 1920, height: Math.round((1920 * h) / w) };
}

const SHORT_FORM_THRESHOLD_SEC = 90;

export async function processNextJob(
  db: Database.Database,
  deps: WorkerDeps,
): Promise<boolean> {
  const job = dequeue(db);
  if (!job) return false;

  const video = db
    .prepare(`
      SELECT id, duration_seconds, aspect_ratio, transcript FROM videos WHERE id = ?
    `)
    .get(job.videoId) as VideoRow | undefined;
  const dl = db
    .prepare("SELECT file_path FROM downloaded_videos WHERE video_id = ?")
    .get(job.videoId) as DownloadRow | undefined;

  if (!video || !dl) {
    fail(db, job.videoId, "video or download row missing");
    db.prepare("UPDATE videos SET clip_status='failed' WHERE id=?").run(job.videoId);
    return true;
  }

  // Short-form passthrough: no analysis/rendering needed
  if (video.duration_seconds <= SHORT_FORM_THRESHOLD_SEC) {
    insertPassthroughClip(db, video.id, video.duration_seconds, dl.file_path);
    db.prepare("UPDATE videos SET clip_status='done' WHERE id=?").run(video.id);
    complete(db, video.id);
    return true;
  }

  // Analysis phase
  db.prepare("UPDATE videos SET clip_status='analyzing' WHERE id=?").run(video.id);
  setStep(db, video.id, "analyzing");

  // promptOverride applies to the scorer only — the clipper always builds its
  // own prompt from FilterConfig because the operational instructions and output
  // shape are clipper-specific and would not be expressible in the scorer's
  // override slot.
  const { filter } = getFilterState(db);
  const prompt = buildClipperPrompt(filter, { aspectRatio: video.aspect_ratio });

  let specs: ClipSpec[];
  try {
    const analyzeInput: { filePath: string; videoId: string; durationSec: number; transcript?: string } = {
      filePath: dl.file_path,
      videoId: video.id,
      durationSec: video.duration_seconds,
    };
    if (video.transcript != null) analyzeInput.transcript = video.transcript;
    specs = await deps.analyze(analyzeInput, prompt, deps.analyzerConfig);
  } catch (e) {
    if (e instanceof QwenNetworkError || (e as Error).name === "QwenNetworkError") {
      // Transient: don't mark failed. Unlock the queue row but reset clip_status
      // to 'pending' so the worker picks it up on the next iteration.
      db.prepare("UPDATE videos SET clip_status='pending' WHERE id=?").run(video.id);
      db.prepare(`
        UPDATE clipper_queue
           SET locked_at = NULL, locked_step = NULL, last_error = ?
         WHERE video_id = ?
      `).run(`transient: ${(e as Error).message}`, video.id);
      return true;  // ran (didn't fail, didn't succeed — caller will sleep + retry)
    }
    // Real failure (invalid JSON after retry, or other bug)
    fail(db, video.id, `analyze: ${(e as Error).message}`);
    db.prepare("UPDATE videos SET clip_status='failed' WHERE id=?").run(video.id);
    return true;
  }

  if (specs.length === 0) {
    db.prepare("UPDATE videos SET clip_status='no_highlights' WHERE id=?").run(video.id);
    complete(db, video.id);
    return true;
  }

  // Render phase
  db.prepare("UPDATE videos SET clip_status='rendering' WHERE id=?").run(video.id);
  setStep(db, video.id, "rendering");

  const { width: vw, height: vh } = parseAspect(video.aspect_ratio);
  const rendered: {
    id: string;
    result: RenderResult;
    spec: ClipSpec;
    order: number;
  }[] = [];

  for (let i = 0; i < specs.length; i++) {
    const spec = specs[i]!;
    const clipId = randomUUID();
    const outputPath = join(deps.mediaDir, `${clipId}.mp4`);
    try {
      const result = await deps.render({
        inputPath: dl.file_path,
        videoWidth: vw,
        videoHeight: vh,
        spec,
        outputPath,
      });
      rendered.push({ id: clipId, result, spec, order: i });
    } catch (e) {
      // Clean up already-rendered files
      for (const r of rendered) {
        await unlink(r.result.filePath).catch(() => undefined);
      }
      fail(db, video.id, `render clip ${i}: ${(e as Error).message}`);
      db.prepare("UPDATE videos SET clip_status='failed' WHERE id=?").run(video.id);
      return true;
    }
  }

  // Persist all clips in a single transaction
  const insert = db.prepare(`
    INSERT INTO clips (
      id, parent_video_id, order_in_parent,
      start_seconds, end_seconds, file_path, file_size_bytes,
      focus_x, focus_y, focus_w, focus_h,
      reason, created_at, added_to_feed_at
    ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
  `);
  const now = Date.now();
  db.transaction(() => {
    for (const r of rendered) {
      insert.run(
        r.id,
        video.id,
        r.order,
        r.spec.startSec,
        r.spec.endSec,
        r.result.filePath,
        r.result.sizeBytes,
        r.spec.focus.x,
        r.spec.focus.y,
        r.spec.focus.w,
        r.spec.focus.h,
        r.spec.reason,
        now,
        now,
      );
    }
  })();

  db.prepare("UPDATE videos SET clip_status='done' WHERE id=?").run(video.id);
  complete(db, video.id);
  return true;
}

function insertPassthroughClip(
  db: Database.Database,
  videoId: string,
  durationSec: number,
  filePath: string,
): void {
  const sizeBytes =
    (
      db
        .prepare("SELECT file_size_bytes FROM downloaded_videos WHERE video_id=?")
        .get(videoId) as { file_size_bytes: number } | undefined
    )?.file_size_bytes ?? 0;
  const now = Date.now();
  db.prepare(`
    INSERT INTO clips (
      id, parent_video_id, order_in_parent,
      start_seconds, end_seconds, file_path, file_size_bytes,
      focus_x, focus_y, focus_w, focus_h,
      reason, created_at, added_to_feed_at
    ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
  `).run(
    randomUUID(),
    videoId,
    0,
    0,
    durationSec,
    filePath,
    sizeBytes,
    0,
    0,
    1,
    1,
    "short-form-passthrough",
    now,
    now,
  );
}
