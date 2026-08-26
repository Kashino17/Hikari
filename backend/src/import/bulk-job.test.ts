import { beforeEach, describe, expect, it } from "vitest";
import {
  finishBulkJob,
  getBulkJob,
  getLatestBulkJob,
  recordBulkResult,
  resetBulkJobs,
  startBulkJob,
} from "./bulk-job.js";

describe("bulk job store", () => {
  beforeEach(() => resetBulkJobs());

  it("zählt Ergebnisse nach Status", () => {
    const job = startBulkJob(3);
    recordBulkResult(job, { url: "a", status: "ok", videoId: "v1" });
    recordBulkResult(job, { url: "b", status: "duplicate", videoId: "v2" });
    recordBulkResult(job, { url: "c", status: "failed", error: "403 vom Hoster" });
    finishBulkJob(job);

    expect(job.ok).toBe(1);
    expect(job.duplicate).toBe(1);
    expect(job.failed).toBe(1);
    expect(job.finishedAt).not.toBeNull();
  });

  // Der eigentliche Zweck: Ein gescheiterter Import darf nicht spurlos
  // verschwinden — der Fehlertext muss abrufbar bleiben.
  it("bewahrt den Fehlertext gescheiterter Items auf", () => {
    const job = startBulkJob(1);
    recordBulkResult(job, {
      url: "https://voe.sx/x",
      status: "failed",
      error: "download failed: 403",
    });

    const stored = getBulkJob(job.id);
    expect(stored?.results[0]?.error).toContain("403");
    expect(stored?.results[0]?.url).toBe("https://voe.sx/x");
  });

  it("liefert den zuletzt gestarteten Job", () => {
    startBulkJob(1);
    const second = startBulkJob(2);
    expect(getLatestBulkJob()?.id).toBe(second.id);
  });

  it("hält den Speicher bei Dauerbetrieb begrenzt", () => {
    for (let i = 0; i < 30; i += 1) startBulkJob(1);
    // MAX_JOBS = 20 — ältere Jobs werden verworfen, der neueste bleibt.
    expect(getLatestBulkJob()).toBeDefined();
  });
});
