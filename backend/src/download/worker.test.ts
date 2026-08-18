import { mkdtempSync, readFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { downloadVideo } from "./worker.js";

vi.mock("../yt-dlp/client.js", () => ({
  runYtDlp: vi.fn(),
  // delegiert an den übergebenen Runner — wie das Original bei Erfolg
  runPreferEmbedded: vi.fn((run: (a: string[], o?: unknown) => unknown, args: string[], opts?: unknown) =>
    run(args, opts),
  ),
  YtDlpError: class extends Error {},
}));

describe("downloadVideo", () => {
  beforeEach(() => vi.clearAllMocks());

  it("loest die URL per yt-dlp -g auf und laedt sie mit Browser-UA in die Datei", async () => {
    const { runYtDlp } = await import("../yt-dlp/client.js");
    vi.mocked(runYtDlp).mockResolvedValue({ stdout: "https://gv/video\n", stderr: "" });
    const seen: { ua?: string } = {};
    const fetchImpl = (async (_url: unknown, init?: RequestInit) => {
      seen.ua = (init?.headers as Record<string, string> | undefined)?.["user-agent"];
      return new Response(Buffer.alloc(1024, 0xff), { status: 200 });
    }) as typeof fetch;

    const dir = mkdtempSync(join(tmpdir(), "hikari-dl-"));
    const result = await downloadVideo({ videoId: "abc12345678", outDir: dir, fetchImpl });

    expect(result.filePath).toBe(join(dir, "abc12345678.mp4"));
    expect(result.fileSizeBytes).toBe(1024);
    expect(readFileSync(result.filePath).length).toBe(1024);
    expect(seen.ua).toMatch(/^Mozilla\/5\.0/);
    expect(vi.mocked(runYtDlp).mock.calls[0]?.[0]).toEqual(
      expect.arrayContaining(["-g", "https://www.youtube.com/watch?v=abc12345678"]),
    );
  });

  it("wirft bei Manifest-URL und bei googlevideo-Fehlerstatus", async () => {
    const { runYtDlp } = await import("../yt-dlp/client.js");
    const dir = mkdtempSync(join(tmpdir(), "hikari-dl-"));

    vi.mocked(runYtDlp).mockResolvedValue({ stdout: "https://gv/x.m3u8\n", stderr: "" });
    await expect(downloadVideo({ videoId: "abc12345678", outDir: dir })).rejects.toThrow(
      /no progressive URL/,
    );

    vi.mocked(runYtDlp).mockResolvedValue({ stdout: "https://gv/video\n", stderr: "" });
    const failFetch = (async () => new Response("x", { status: 403 })) as typeof fetch;
    await expect(
      downloadVideo({ videoId: "abc12345678", outDir: dir, fetchImpl: failFetch }),
    ).rejects.toThrow(/403/);
  });
});
