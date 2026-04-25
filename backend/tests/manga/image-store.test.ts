import { test, expect, vi, beforeEach, afterEach } from "vitest";
import { mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { downloadPage } from "../../src/manga/image-store.js";

let baseDir: string;

beforeEach(() => {
  baseDir = mkdtempSync(join(tmpdir(), "manga-test-"));
  vi.restoreAllMocks();
});

afterEach(() => {
  rmSync(baseDir, { recursive: true, force: true });
});

test("downloadPage writes image to relative path under baseDir", async () => {
  const bytes = Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0, 16, 0x4a]); // JPEG start
  vi.stubGlobal("fetch", vi.fn(async () => ({
    ok: true,
    arrayBuffer: async () =>
      bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength),
  })));

  const out = await downloadPage({
    sourceUrl: "https://example.com/p.jpg",
    baseDir,
    relativePath: "onepiecetube/one-piece/0001/01.jpg",
  });

  expect(out.relativePath).toBe("onepiecetube/one-piece/0001/01.jpg");
  expect(out.bytes).toBe(bytes.length);
  const written = readFileSync(join(baseDir, out.relativePath));
  expect(written.equals(bytes)).toBe(true);
});

test("downloadPage rejects non-2xx", async () => {
  vi.stubGlobal("fetch", vi.fn(async () => ({ ok: false, status: 404 })));
  await expect(
    downloadPage({
      sourceUrl: "https://x.test/p.jpg",
      baseDir,
      relativePath: "a/b/c.jpg",
    }),
  ).rejects.toThrow(/404/);
});

test("downloadPage works with PNG content (no jpeg assumption)", async () => {
  const png = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  vi.stubGlobal("fetch", vi.fn(async () => ({
    ok: true,
    arrayBuffer: async () => png.buffer.slice(png.byteOffset, png.byteOffset + png.byteLength),
  })));

  const out = await downloadPage({
    sourceUrl: "https://example.com/p.png",
    baseDir,
    relativePath: "x/y/01.png",
  });
  const written = readFileSync(join(baseDir, out.relativePath));
  expect(written.equals(png)).toBe(true);
  expect(out.bytes).toBe(png.length);
});
