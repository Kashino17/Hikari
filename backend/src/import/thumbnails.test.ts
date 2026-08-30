import { existsSync, mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// execa wird für ffprobe (Dauer) und ffmpeg (Standbild) genutzt — beides
// ersetzen, damit der Test keine echten Binaries braucht.
vi.mock("execa", () => ({ execa: vi.fn() }));

import { execa } from "execa";
import { ensureImportThumbnail } from "./thumbnails.js";

const execaMock = vi.mocked(execa);

function imageResponse(bytes = 4096): Response {
  return new Response(Buffer.alloc(bytes, 0xab), {
    status: 200,
    headers: { "content-type": "image/jpeg" },
  });
}

describe("ensureImportThumbnail", () => {
  let coverDir: string;
  let videoDir: string;
  let filePath: string;

  beforeEach(() => {
    vi.clearAllMocks();
    coverDir = mkdtempSync(join(tmpdir(), "hikari-covers-"));
    videoDir = mkdtempSync(join(tmpdir(), "hikari-video-"));
    filePath = join(videoDir, "vid_x.mp4");
    writeFileSync(filePath, Buffer.alloc(1024, 0xff));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("lädt das Remote-Thumbnail lokal herunter", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(imageResponse()));

    const result = await ensureImportThumbnail(
      "voe_abc",
      filePath,
      "https://hoster.test/thumb.jpg",
      coverDir,
    );

    expect(result).toBe("/covers/vid_voe_abc.jpg");
    expect(existsSync(join(coverDir, "vid_voe_abc.jpg"))).toBe(true);
    // Kein ffmpeg nötig, wenn der Hoster liefert.
    expect(execaMock).not.toHaveBeenCalled();
  });

  it("weicht auf ein ffmpeg-Standbild aus, wenn der Remote-Download scheitert", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("nope", { status: 403 })));
    execaMock.mockImplementation(async (cmd: string, args?: string[]) => {
      if (cmd === "ffprobe") return { stdout: "120.0", stderr: "" } as never;
      if (cmd === "ffmpeg") {
        const target = args?.[args.length - 1];
        writeFileSync(String(target), Buffer.alloc(2048, 0xcc));
        return { stdout: "", stderr: "" } as never;
      }
      throw new Error(`unexpected: ${cmd}`);
    });

    const result = await ensureImportThumbnail(
      "sniff_xyz",
      filePath,
      "https://hoster.test/blocked.jpg",
      coverDir,
    );

    expect(result).toBe("/covers/vid_sniff_xyz.jpg");
    expect(existsSync(join(coverDir, "vid_sniff_xyz.jpg"))).toBe(true);
    // 10 % von 120 s = Sekunde 12, nicht der schwarze Anfang.
    const ffmpegArgs = execaMock.mock.calls.find((c) => c[0] === "ffmpeg")?.[1] as string[];
    expect(ffmpegArgs).toContain("-ss");
    expect(ffmpegArgs[ffmpegArgs.indexOf("-ss") + 1]).toBe("12");
  });

  it("schneidet bei Importen ohne Remote-URL direkt ein Standbild", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    execaMock.mockImplementation(async (cmd: string, args?: string[]) => {
      if (cmd === "ffprobe") throw new Error("no duration");
      if (cmd === "ffmpeg") {
        writeFileSync(String(args?.[args.length - 1]), Buffer.alloc(2048, 0xcc));
        return { stdout: "", stderr: "" } as never;
      }
      throw new Error(`unexpected: ${cmd}`);
    });

    const result = await ensureImportThumbnail("sniff_noimg", filePath, null, coverDir);

    expect(result).toBe("/covers/vid_sniff_noimg.jpg");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("liefert null statt zu werfen, wenn weder Hoster noch ffmpeg liefern", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("network down")));
    execaMock.mockRejectedValue(new Error("ffmpeg missing"));

    const result = await ensureImportThumbnail(
      "sniff_z",
      filePath,
      "https://x.test/t.jpg",
      coverDir,
    );

    expect(result).toBeNull();
  });

  it("verwirft zu kleine oder keine Bilder vom Hoster", async () => {
    // 100 Bytes "image/jpeg" — ein Platzhalter-Pixel, kein echtes Cover.
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(imageResponse(100)));
    execaMock.mockImplementation(async (cmd: string, args?: string[]) => {
      if (cmd === "ffprobe") throw new Error("no duration");
      if (cmd === "ffmpeg") {
        writeFileSync(String(args?.[args.length - 1]), Buffer.alloc(2048, 0xcc));
        return { stdout: "", stderr: "" } as never;
      }
      throw new Error(`unexpected: ${cmd}`);
    });

    const result = await ensureImportThumbnail(
      "voe_tiny",
      filePath,
      "https://x.test/t.jpg",
      coverDir,
    );

    expect(result).toBe("/covers/vid_voe_tiny.jpg");
    expect(existsSync(join(coverDir, "vid_voe_tiny.jpg"))).toBe(true);
  });
});
