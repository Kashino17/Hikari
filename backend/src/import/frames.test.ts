import { existsSync, mkdtempSync, readdirSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { beforeEach, describe, expect, it, vi } from "vitest";

// ffmpeg wird ersetzt — der Test prüft Cache-Verhalten und Argumente,
// nicht die echte Extraktion.
vi.mock("execa", () => ({ execa: vi.fn() }));

import { execa } from "execa";
import { ensureFrame, pickSeekSeconds } from "./frames.js";

const execaMock = vi.mocked(execa);

describe("pickSeekSeconds", () => {
  it("wählt bei langen Videos eine Position zwischen Minute 3 und 7", () => {
    for (const r of [0, 0.25, 0.5, 0.75, 0.999]) {
      const seek = pickSeekSeconds(3600, () => r);
      expect(seek).toBeGreaterThanOrEqual(180);
      expect(seek).toBeLessThanOrEqual(420);
    }
  });

  it("bleibt bei mittellangen Videos hinter Minute 3, aber vor dem Ende", () => {
    const seek = pickSeekSeconds(240, () => 0.999);
    expect(seek).toBeGreaterThanOrEqual(180);
    expect(seek).toBeLessThan(240);
  });

  it("weicht bei Kurzvideos auf 10 % der Laufzeit aus, nie Sekunde 0", () => {
    expect(pickSeekSeconds(120, () => 0.5)).toBe(12);
    expect(pickSeekSeconds(5, () => 0.5)).toBe(1);
  });
});

describe("ensureFrame", () => {
  let coverDir: string;
  let videoDir: string;
  let filePath: string;

  beforeEach(() => {
    vi.clearAllMocks();
    coverDir = mkdtempSync(join(tmpdir(), "hikari-covers-"));
    videoDir = mkdtempSync(join(tmpdir(), "hikari-video-"));
    filePath = join(videoDir, "v.mp4");
    writeFileSync(filePath, Buffer.alloc(1024, 0xff));
    execaMock.mockImplementation(async (cmd: string, args?: string[]) => {
      if (cmd === "ffmpeg") {
        writeFileSync(String(args?.[args.length - 1]), Buffer.alloc(2048, 0xcc));
        return { stdout: "", stderr: "" } as never;
      }
      throw new Error(`unexpected: ${cmd}`);
    });
  });

  it("rundet die Position auf 5-Sekunden-Buckets und cached die Datei", async () => {
    const name = await ensureFrame("vid1", filePath, 187, coverDir);
    expect(name).toBe("f_vid1_185.jpg");
    expect(existsSync(join(coverDir, "frames", "f_vid1_185.jpg"))).toBe(true);

    // Gleicher Bucket → kein zweiter ffmpeg-Lauf.
    const again = await ensureFrame("vid1", filePath, 189, coverDir);
    expect(again).toBe("f_vid1_185.jpg");
    expect(execaMock).toHaveBeenCalledTimes(1);
  });

  it("räumt überholte Frames desselben Videos weg", async () => {
    await ensureFrame("vid1", filePath, 100, coverDir);
    await ensureFrame("vid1", filePath, 900, coverDir);
    const files = readdirSync(join(coverDir, "frames")).filter((f) => f.startsWith("f_vid1_"));
    expect(files).toEqual(["f_vid1_900.jpg"]);
  });

  it("gibt null zurück statt zu werfen, wenn ffmpeg scheitert", async () => {
    execaMock.mockRejectedValue(new Error("ffmpeg missing"));
    expect(await ensureFrame("vid1", filePath, 60, coverDir)).toBeNull();
  });
});
