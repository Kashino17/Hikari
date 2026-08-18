import { mkdtempSync, readFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { downloadVideo } from "./worker.js";

// Nur das Binary-Ausführen wird ersetzt — runPreferEmbedded (web_embedded-
// Client + Fallback) läuft echt mit, damit der 403-Fix mitgetestet wird.
vi.mock("../yt-dlp/client.js", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../yt-dlp/client.js")>()),
  runYtDlp: vi.fn(),
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

  it("laedt blockweise mit begrenzten Ranges statt in einem Rutsch", async () => {
    const { runYtDlp } = await import("../yt-dlp/client.js");
    vi.mocked(runYtDlp).mockResolvedValue({ stdout: "https://gv/video\n", stderr: "" });
    const data = Buffer.alloc(2048, 0xab);
    const seen: string[] = [];
    const fetchImpl = (async (_url: unknown, init?: RequestInit) => {
      const range = (init?.headers as Record<string, string> | undefined)?.range ?? "";
      seen.push(range);
      const m = /^bytes=(\d+)-(\d+)$/.exec(range);
      if (!m) return new Response("kein Range", { status: 403 });
      const from = Number(m[1]);
      const to = Math.min(Number(m[2]), data.length - 1);
      return new Response(data.subarray(from, to + 1), {
        status: 206,
        headers: { "content-range": `bytes ${from}-${to}/${data.length}` },
      });
    }) as typeof fetch;

    const dir = mkdtempSync(join(tmpdir(), "hikari-dl-"));
    const result = await downloadVideo({ videoId: "abc12345678", outDir: dir, fetchImpl });

    expect(result.fileSizeBytes).toBe(2048);
    expect(readFileSync(result.filePath).equals(data)).toBe(true);
    // Ohne Range drosselt googlevideo auf Abspieltempo (18.08.: 32 KB/s statt
    // 19 MB/s) — es darf nie ein offener oder fehlender Range rausgehen.
    expect(seen.length).toBeGreaterThan(0);
    for (const range of seen) expect(range).toMatch(/^bytes=\d+-\d+$/);
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
