import { beforeEach, describe, expect, it, vi } from "vitest";
import { resetBulkJobs } from "./bulk-job.js";
import { isRetryableImportError, runBulkImportAndWait } from "./bulk-runner.js";
import type { ImportResult } from "./manual-import.js";

const log = { info: vi.fn(), error: vi.fn() };
const noSleep = async () => {};

describe("runBulkImportAndWait", () => {
  beforeEach(() => {
    resetBulkJobs();
    vi.clearAllMocks();
  });

  it("verarbeitet Direktlinks und mitgelesene Streams in einem Job", async () => {
    const importDirect = vi.fn(
      async (_db: unknown, url: string): Promise<ImportResult> => ({
        url,
        status: "ok",
        videoId: "d",
      }),
    );
    const importSniffed = vi.fn(
      async (_db: unknown, item: { pageUrl: string }): Promise<ImportResult> => ({
        url: item.pageUrl,
        status: "ok",
        videoId: "s",
      }),
    );

    const job = await runBulkImportAndWait(
      {
        db: {} as never,
        videoDir: "/tmp",
        log,
        importDirect: importDirect as never,
        importSniffed: importSniffed as never,
        sleep: noSleep,
      },
      [
        { url: "https://youtube.com/watch?v=1" },
        {
          pageUrl: "https://s.to/serie/x/staffel-1/episode-1",
          mediaUrl: "https://cdn.voe/master.m3u8",
        },
      ],
    );

    expect(job.total).toBe(2);
    expect(job.ok).toBe(2);
    expect(job.finishedAt).not.toBeNull();
    expect(importDirect).toHaveBeenCalledTimes(1);
    expect(importSniffed).toHaveBeenCalledTimes(1);
  });

  it("wiederholt bei vorübergehenden Fehlern und hält seriell pro Hoster", async () => {
    const order: string[] = [];
    let calls = 0;
    const importSniffed = vi.fn(
      async (_db: unknown, item: { pageUrl: string }): Promise<ImportResult> => {
        order.push(item.pageUrl);
        calls += 1;
        if (calls === 1) {
          return {
            url: item.pageUrl,
            status: "failed",
            error: "download failed: Unable to download webpage: HTTPSConnectionPool",
          };
        }
        return { url: item.pageUrl, status: "ok", videoId: `v${calls}` };
      },
    );
    const sleep = vi.fn(async () => {});

    const job = await runBulkImportAndWait(
      {
        db: {} as never,
        videoDir: "/tmp",
        log,
        importSniffed: importSniffed as never,
        sleep,
        retryDelaysMs: [5, 10],
      },
      [
        { pageUrl: "https://s.to/e1", mediaUrl: "https://cdn.voe/a.m3u8" },
        { pageUrl: "https://s.to/e2", mediaUrl: "https://cdn.voe/b.m3u8" },
      ],
    );

    expect(job.ok).toBe(2);
    expect(job.failed).toBe(0);
    expect(sleep).toHaveBeenCalledWith(5);
    // e1 zweimal (Fehler + Erfolg), dann e2 — nie verschränkt.
    expect(order).toEqual(["https://s.to/e1", "https://s.to/e1", "https://s.to/e2"]);
  });

  it("gibt endgültige Fehler nach den Versuchen als failed zurück", async () => {
    const importSniffed = vi.fn(
      async (_db: unknown, item: { pageUrl: string }): Promise<ImportResult> => ({
        url: item.pageUrl,
        status: "failed",
        error: "download failed: HTTP Error 403: Forbidden",
      }),
    );
    const job = await runBulkImportAndWait(
      {
        db: {} as never,
        videoDir: "/tmp",
        log,
        importSniffed: importSniffed as never,
        sleep: noSleep,
        retryDelaysMs: [1, 1],
      },
      [{ pageUrl: "https://s.to/e1", mediaUrl: "https://cdn.voe/a.m3u8" }],
    );
    expect(job.failed).toBe(1);
    expect(importSniffed).toHaveBeenCalledTimes(3);
    expect(job.results[0]?.error).toContain("403");
  });

  it("wiederholt nicht bei endgültigen Fehlern", async () => {
    const importDirect = vi.fn(
      async (_db: unknown, url: string): Promise<ImportResult> => ({
        url,
        status: "failed",
        error: "ERROR: Unsupported URL: https://x",
      }),
    );
    const job = await runBulkImportAndWait(
      {
        db: {} as never,
        videoDir: "/tmp",
        log,
        importDirect: importDirect as never,
        sleep: noSleep,
        retryDelaysMs: [1, 1],
      },
      [{ url: "https://x" }],
    );
    expect(job.failed).toBe(1);
    expect(importDirect).toHaveBeenCalledTimes(1);
  });

  it("fängt geworfene Fehler als failed ab", async () => {
    const importDirect = vi.fn(async () => {
      throw new Error("boom");
    });
    const job = await runBulkImportAndWait(
      {
        db: {} as never,
        videoDir: "/tmp",
        log,
        importDirect: importDirect as never,
        sleep: noSleep,
        retryDelaysMs: [],
      },
      [{ url: "https://x" }],
    );
    expect(job.failed).toBe(1);
    expect(job.results[0]?.error).toContain("boom");
  });

  it("begrenzt die Parallelität über Hoster", async () => {
    let running = 0;
    let peak = 0;
    const importDirect = vi.fn(async (_db: unknown, url: string): Promise<ImportResult> => {
      running += 1;
      peak = Math.max(peak, running);
      await new Promise((r) => setTimeout(r, 5));
      running -= 1;
      return { url, status: "ok", videoId: url };
    });
    const items = ["a", "b", "c", "d", "e", "f"].map((h) => ({ url: `https://${h}.example/v` }));
    const job = await runBulkImportAndWait(
      {
        db: {} as never,
        videoDir: "/tmp",
        log,
        importDirect: importDirect as never,
        sleep: noSleep,
        maxParallelHosts: 2,
      },
      items,
    );
    expect(job.ok).toBe(6);
    expect(peak).toBeLessThanOrEqual(2);
  });
});

describe("isRetryableImportError", () => {
  it("klassifiziert Netz-/Drosselfehler als wiederholbar", () => {
    expect(isRetryableImportError("download failed: HTTP Error 403: Forbidden")).toBe(true);
    expect(
      isRetryableImportError(
        "Unable to download webpage: HTTPSConnectionPool(host=…): Read timed out",
      ),
    ).toBe(true);
    expect(
      isRetryableImportError(
        "ERROR: [generic] master.m3u8: Unable to download webpage: <urlopen error [Errno 54] Connection reset by peer>",
      ),
    ).toBe(true);
  });
  it("lässt endgültige Fehler stehen", () => {
    expect(isRetryableImportError("empty media URL")).toBe(false);
    expect(isRetryableImportError("ERROR: Unsupported URL: https://x")).toBe(false);
    expect(isRetryableImportError("download finished but file not found")).toBe(false);
    expect(isRetryableImportError(undefined)).toBe(false);
  });

  it("ein werfender Import kippt weder den Bucket noch den Job", async () => {
    const importSniffed = vi.fn(async (_db: unknown, item: { pageUrl: string }) => {
      if (item.pageUrl.endsWith("2")) throw new Error("kaputt");
      return { url: item.pageUrl, status: "ok" as const, videoId: item.pageUrl };
    });
    const job = await runBulkImportAndWait(
      {
        db: {} as never,
        videoDir: "/tmp",
        log,
        importSniffed: importSniffed as never,
        sleep: noSleep,
        retryDelaysMs: [],
      },
      [1, 2, 3].map((n) => ({
        pageUrl: `https://s.to/serie/x/staffel-1/episode-${n}`,
        mediaUrl: `https://cdn.voe/${n}/master.m3u8`,
      })),
    );
    expect(job.ok).toBe(2);
    expect(job.failed).toBe(1);
    expect(job.results.find((r) => r.status === "failed")?.error).toContain("kaputt");
    expect(job.finishedAt).not.toBeNull();
  });

  it("überlebt einen werfenden Logger und schließt den Job ab", async () => {
    const badLog = {
      info: vi.fn(() => {
        throw new Error("log kaputt");
      }),
      error: vi.fn(),
    };
    const importDirect = vi.fn(async (_db: unknown, url: string) => ({
      url,
      status: "ok" as const,
      videoId: "d",
    }));
    const job = await runBulkImportAndWait(
      {
        db: {} as never,
        videoDir: "/tmp",
        log: badLog,
        importDirect: importDirect as never,
        sleep: noSleep,
      },
      [{ url: "https://youtube.com/watch?v=1" }, { url: "https://youtube.com/watch?v=2" }],
    );
    expect(job.ok).toBe(2);
    expect(job.finishedAt).not.toBeNull();
  });
});
