import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";

export interface DownloadInput {
  sourceUrl: string;
  baseDir: string;
  relativePath: string;
}

export interface DownloadResult {
  relativePath: string;
  bytes: number;
}

export async function downloadPage(input: DownloadInput): Promise<DownloadResult> {
  const r = await fetch(input.sourceUrl, {
    headers: { "User-Agent": "Mozilla/5.0 Hikari/0.1" },
  });
  if (!r.ok) throw new Error(`HTTP ${r.status} for ${input.sourceUrl}`);
  const buf = Buffer.from(await r.arrayBuffer());
  const fullPath = join(input.baseDir, input.relativePath);
  mkdirSync(dirname(fullPath), { recursive: true });
  writeFileSync(fullPath, buf);
  return { relativePath: input.relativePath, bytes: buf.length };
}
