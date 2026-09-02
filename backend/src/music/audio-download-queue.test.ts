import { existsSync, readFileSync } from "node:fs";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { AudioDownloadQueue } from "./audio-download-queue.js";

// Größer als ein 4-MiB-Block, damit der blockweise Abruf sichtbar wird.
const BODY = new Uint8Array(9_000_000).map((_, i) => i % 251);

/** googlevideo-Attrappe: 206 mit Content-Range, oder 403 solange `failing` gesetzt ist. */
function fakeUpstream(state: { failing: boolean; hits: number }): typeof fetch {
  return (async (_url: string | URL | Request, init?: RequestInit) => {
    state.hits += 1;
    if (state.failing) return new Response(null, { status: 403 });
    const range = String((init?.headers as Record<string, string>)?.range ?? "");
    const m = /^bytes=(\d+)-(\d+)$/.exec(range);
    if (!m) return new Response(null, { status: 403 });
    const start = Number(m[1]);
    const end = Math.min(Number(m[2]), BODY.length - 1);
    return new Response(BODY.slice(start, end + 1), {
      status: 206,
      headers: { "content-range": `bytes ${start}-${end}/${BODY.length}` },
    });
  }) as typeof fetch;
}

async function untilSettled(queue: AudioDownloadQueue, videoId: string): Promise<void> {
  for (let i = 0; i < 2_000; i++) {
    const job = queue.get(videoId);
    if (job?.status === "done" || job?.status === "failed") return;
    await new Promise((r) => setTimeout(r, 5));
  }
  throw new Error("job never settled");
}

describe("AudioDownloadQueue", () => {
  let dir: string;
  beforeEach(async () => {
    dir = await mkdtemp(join(tmpdir(), "hikari-audio-q-"));
  });
  afterEach(async () => {
    await rm(dir, { recursive: true, force: true });
  });

  it("lädt einen Song blockweise in die Datei", async () => {
    const state = { failing: false, hits: 0 };
    const queue = new AudioDownloadQueue({
      dir,
      resolve: async () => "https://rr1.googlevideo.test/videoplayback?x=1",
      fetchImpl: fakeUpstream(state),
      sleep: async () => undefined,
      gapMs: 0,
    });
    queue.enqueue("abcdefghijk");
    await untilSettled(queue, "abcdefghijk");

    const job = queue.get("abcdefghijk");
    expect(job?.status).toBe("done");
    expect(job?.attempts).toBe(1);
    const file = join(dir, "abcdefghijk.m4a");
    expect(existsSync(file)).toBe(true);
    expect(readFileSync(file).byteLength).toBe(BODY.length);
    // Mehrere Blöcke, nicht ein offener Request — sonst drosselt googlevideo.
    expect(state.hits).toBeGreaterThan(1);
  });

  it("wartet bei 403 statt aufzugeben und löst danach frisch auf", async () => {
    const state = { failing: true, hits: 0 };
    const resolves: boolean[] = [];
    const failed: string[] = [];
    const queue = new AudioDownloadQueue({
      dir,
      resolve: async (_id, force) => {
        resolves.push(force);
        // Nach dem ersten Fehlschlag „endet die Welle".
        if (resolves.length >= 2) state.failing = false;
        return "https://rr1.googlevideo.test/videoplayback?x=2";
      },
      fetchImpl: fakeUpstream(state),
      onUpstreamFail: (id) => failed.push(id),
      sleep: async () => undefined,
      backoffMs: [1],
      gapMs: 0,
    });
    queue.enqueue("abcdefghijk");
    await untilSettled(queue, "abcdefghijk");

    const job = queue.get("abcdefghijk");
    expect(job?.status).toBe("done");
    expect(job?.attempts).toBe(2);
    expect(failed).toEqual(["abcdefghijk"]);
    // Zweiter Versuch erzwingt eine frische URL — die alte war ein Blindgänger.
    expect(resolves).toEqual([false, true]);
    expect(existsSync(join(dir, "abcdefghijk.m4a"))).toBe(true);
    expect(existsSync(join(dir, "abcdefghijk.m4a.part"))).toBe(false);
  });

  it("klopft während einer Welle gar nicht erst an", async () => {
    const state = { failing: false, hits: 0 };
    let throttled = 5_000;
    const queue = new AudioDownloadQueue({
      dir,
      resolve: async () => "https://rr1.googlevideo.test/videoplayback?x=3",
      fetchImpl: fakeUpstream(state),
      throttledForMs: () => throttled,
      sleep: async () => {
        throttled = 0; // Welle vorbei
      },
      gapMs: 0,
    });
    queue.enqueue("abcdefghijk");
    await untilSettled(queue, "abcdefghijk");
    expect(queue.get("abcdefghijk")?.status).toBe("done");
    // Genau ein echter Versuch — der erste Durchlauf hat nur gewartet.
    expect(queue.get("abcdefghijk")?.attempts).toBe(1);
  });

  it("gibt ein nicht auflösbares Video nach drei Versuchen auf und macht mit dem nächsten weiter", async () => {
    const state = { failing: false, hits: 0 };
    const queue = new AudioDownloadQueue({
      dir,
      resolve: async (id) =>
        id === "unavailable1" ? undefined : "https://rr1.googlevideo.test/videoplayback?x=4",
      fetchImpl: fakeUpstream(state),
      sleep: async () => undefined,
      backoffMs: [1],
      gapMs: 0,
    });
    queue.enqueue("unavailable1");
    queue.enqueue("abcdefghijk");
    await untilSettled(queue, "abcdefghijk");

    expect(queue.get("unavailable1")?.status).toBe("failed");
    expect(queue.get("unavailable1")?.attempts).toBe(3);
    expect(queue.get("abcdefghijk")?.status).toBe("done");
  });

  it("meldet eine schon vorhandene Datei sofort als fertig", async () => {
    const state = { failing: false, hits: 0 };
    const queue = new AudioDownloadQueue({
      dir,
      resolve: async () => "https://rr1.googlevideo.test/videoplayback?x=5",
      fetchImpl: fakeUpstream(state),
      sleep: async () => undefined,
      gapMs: 0,
    });
    queue.enqueue("abcdefghijk");
    await untilSettled(queue, "abcdefghijk");
    const hits = state.hits;
    const again = new AudioDownloadQueue({
      dir,
      resolve: async () => undefined,
      fetchImpl: fakeUpstream(state),
    });
    expect(again.enqueue("abcdefghijk").status).toBe("done");
    expect(again.get("abcdefghijk")?.status).toBe("done");
    expect(state.hits).toBe(hits);
  });
});
