import Database from "better-sqlite3";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import { enqueue } from "./queue.js";
import { processNextJob } from "./worker.js";
import type { ClipSpec } from "./qwen-analyzer.js";

function makeDb(): Database.Database {
  const db = new Database(":memory:");
  applyMigrations(db);
  db.prepare("INSERT INTO channels (id,url,title,added_at) VALUES (?,?,?,?)")
    .run("c1", "x", "ch", 0);
  db.prepare(`
    INSERT INTO videos (id, channel_id, title, published_at, duration_seconds,
                        discovered_at, aspect_ratio, clip_status)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
  `).run("v1", "c1", "Vid 1", 0, 600, 0, "16:9", "pending");
  db.prepare(`
    INSERT INTO downloaded_videos (video_id, file_path, file_size_bytes, downloaded_at)
    VALUES (?, ?, ?, ?)
  `).run("v1", "/orig/v1.mp4", 100_000_000, 0);
  db.prepare(`
    INSERT INTO filter_config (id, filter_json, prompt_override, updated_at)
    VALUES (1, ?, NULL, 0)
  `).run(JSON.stringify({
    likeTags: [], dislikeTags: [], moodTags: [], depthTags: [], languages: [],
    minDurationSec: 0, maxDurationSec: 0, examples: "", scoreThreshold: 0,
  }));
  return db;
}

const SPEC_TWO_CLIPS: ClipSpec[] = [
  { startSec: 30, endSec: 90, focus: { x: 0.2, y: 0.2, w: 0.6, h: 0.6 }, reason: "first" },
  { startSec: 200, endSec: 260, focus: { x: 0.3, y: 0.2, w: 0.4, h: 0.6 }, reason: "second" },
];

describe("processNextJob", () => {
  let db: Database.Database;
  beforeEach(() => { db = makeDb(); });

  it("happy path: dequeues, analyzes, renders, inserts clips, marks done", async () => {
    enqueue(db, "v1");
    const analyze = vi.fn(async () => SPEC_TWO_CLIPS);
    const render = vi.fn(async (i: any) => ({
      filePath: i.outputPath, sizeBytes: 5_000_000,
    }));

    const ran = await processNextJob(db, {
      analyze, render,
      mediaDir: "/clips",
      analyzerConfig: { provider: "lmstudio", baseUrl: "http://x", model: "qwen" },
    });
    expect(ran).toBe(true);
    expect(analyze).toHaveBeenCalledOnce();
    expect(render).toHaveBeenCalledTimes(2);

    const status = db.prepare("SELECT clip_status FROM videos WHERE id=?").get("v1") as any;
    expect(status.clip_status).toBe("done");

    const clips = db.prepare("SELECT * FROM clips ORDER BY order_in_parent").all() as any[];
    expect(clips).toHaveLength(2);
    expect(clips[0].order_in_parent).toBe(0);
    expect(clips[1].order_in_parent).toBe(1);
    expect(clips[0].parent_video_id).toBe("v1");
    expect(clips[0].file_path).toMatch(/\/clips\//);

    const queueRows = db.prepare("SELECT * FROM clipper_queue").all();
    expect(queueRows).toHaveLength(0);
  });

  it("no_highlights path: empty spec → status='no_highlights', no clips, queue cleaned", async () => {
    enqueue(db, "v1");
    const analyze = vi.fn(async () => []);
    const render = vi.fn();

    await processNextJob(db, {
      analyze, render,
      mediaDir: "/clips",
      analyzerConfig: { provider: "lmstudio", baseUrl: "http://x", model: "qwen" },
    });
    expect(render).not.toHaveBeenCalled();
    const v = db.prepare("SELECT clip_status FROM videos WHERE id=?").get("v1") as any;
    expect(v.clip_status).toBe("no_highlights");
    expect(db.prepare("SELECT COUNT(*) c FROM clipper_queue").get()).toEqual({ c: 0 });
    expect(db.prepare("SELECT COUNT(*) c FROM clips").get()).toEqual({ c: 0 });
  });

  it("render-fail path: clip_status='failed', already-rendered clips deleted", async () => {
    enqueue(db, "v1");
    const analyze = vi.fn(async () => SPEC_TWO_CLIPS);
    let calls = 0;
    const render = vi.fn(async (i: any) => {
      calls++;
      if (calls === 1) return { filePath: i.outputPath, sizeBytes: 5_000_000 };
      throw new Error("ffmpeg crashed");
    });

    await processNextJob(db, {
      analyze, render,
      mediaDir: "/clips",
      analyzerConfig: { provider: "lmstudio", baseUrl: "http://x", model: "qwen" },
    });
    const v = db.prepare("SELECT clip_status FROM videos WHERE id=?").get("v1") as any;
    expect(v.clip_status).toBe("failed");
    expect(db.prepare("SELECT COUNT(*) c FROM clips WHERE parent_video_id=?")
      .get("v1")).toEqual({ c: 0 });
  });

  it("short-form passthrough: video ≤ 90s skips analyze + render, inserts passthrough clip", async () => {
    db.prepare("UPDATE videos SET duration_seconds=60 WHERE id=?").run("v1");
    enqueue(db, "v1");
    const analyze = vi.fn();
    const render = vi.fn();

    await processNextJob(db, {
      analyze, render,
      mediaDir: "/clips",
      analyzerConfig: { provider: "lmstudio", baseUrl: "http://x", model: "qwen" },
    });
    expect(analyze).not.toHaveBeenCalled();
    expect(render).not.toHaveBeenCalled();
    const clips = db.prepare("SELECT * FROM clips").all() as any[];
    expect(clips).toHaveLength(1);
    expect(clips[0].reason).toBe("short-form-passthrough");
    expect(clips[0].file_path).toBe("/orig/v1.mp4");
    expect(clips[0].focus_x).toBe(0);
    expect(clips[0].focus_w).toBe(1);
    const v = db.prepare("SELECT clip_status FROM videos WHERE id=?").get("v1") as any;
    expect(v.clip_status).toBe("done");
  });

  it("returns false when queue is empty", async () => {
    const ran = await processNextJob(db, {
      analyze: vi.fn(), render: vi.fn(),
      mediaDir: "/clips",
      analyzerConfig: { provider: "lmstudio", baseUrl: "http://x", model: "qwen" },
    });
    expect(ran).toBe(false);
  });

  it("transient network error: clip_status stays 'pending', queue unlocks for retry", async () => {
    enqueue(db, "v1");
    // First call throws network error
    const analyze = vi.fn(async () => {
      const err = new Error("Cannot reach Qwen at http://x");
      err.name = "QwenNetworkError";
      throw err;
    });
    const render = vi.fn();

    await processNextJob(db, {
      analyze, render, mediaDir: "/clips",
      analyzerConfig: { provider: "lmstudio", baseUrl: "http://x", model: "q" },
    });

    const v = db.prepare("SELECT clip_status FROM videos WHERE id=?").get("v1") as any;
    expect(v.clip_status).toBe("pending");  // NOT 'failed'

    const q = db.prepare("SELECT * FROM clipper_queue WHERE video_id=?").get("v1") as any;
    expect(q).toBeTruthy();           // still in queue
    expect(q.locked_at).toBeNull();   // unlocked
    expect(q.attempts).toBe(0);       // NOT incremented (it's transient, not a fail)
    expect(q.last_error).toMatch(/transient/);
  });
});
