import { createWriteStream, mkdirSync } from "node:fs";
import { stat } from "node:fs/promises";
import { dirname, join } from "node:path";
import { Readable } from "node:stream";
import { pipeline } from "node:stream/promises";
import type { ReadableStream as WebReadableStream } from "node:stream/web";

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
  const fullPath = join(input.baseDir, input.relativePath);
  mkdirSync(dirname(fullPath), { recursive: true });
  if (r.body) {
    // Direkt auf Platte streamen — arrayBuffer()+Buffer.from hielt jede Seite
    // doppelt im Heap, und writeFileSync blockierte die Event-Loop.
    await pipeline(Readable.fromWeb(r.body as WebReadableStream), createWriteStream(fullPath));
  } else {
    // Fallback für Responses ohne Body-Stream (u.a. Test-Mocks).
    const buf = Buffer.from(await r.arrayBuffer());
    await pipeline(Readable.from(buf), createWriteStream(fullPath));
  }
  const { size } = await stat(fullPath);
  return { relativePath: input.relativePath, bytes: size };
}
