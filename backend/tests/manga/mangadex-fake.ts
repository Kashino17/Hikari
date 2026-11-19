import { readFileSync } from "node:fs";
import { join } from "node:path";
import type { ImageReport, MangadexClient, Query } from "../../src/manga/mangadex/client.js";

export const fx = <T>(n: string): T =>
  JSON.parse(readFileSync(join(__dirname, "../fixtures/manga/mangadex", n), "utf8")) as T;

export interface FakeCall {
  path: string;
  query: Query | undefined;
}

/** Ein JPEG-Kopf plus Füllbytes — reicht für Cover- und Seiten-Tests. */
export const JPEG = Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0]), Buffer.alloc(400, 7)]);

/**
 * MangaDex ohne Netz: `routes` beantwortet API-Pfade (erster passender Präfix gewinnt),
 * `images` Bild-Adressen. Alle Aufrufe und Meldungen werden mitgeschrieben.
 */
export function fakeClient(opts: {
  routes?: Array<[string, (q: Query | undefined, path: string) => unknown]>;
  images?: (url: string) => { status: number; body?: Buffer; headers?: Record<string, string> };
}): MangadexClient & { calls: FakeCall[]; imageCalls: string[]; reports: ImageReport[] } {
  const calls: FakeCall[] = [];
  const imageCalls: string[] = [];
  const reports: ImageReport[] = [];
  return {
    calls,
    imageCalls,
    reports,
    async api<T>(path: string, query?: Query): Promise<T> {
      calls.push({ path, query });
      const route = (opts.routes ?? []).find(([prefix]) => path.startsWith(prefix));
      if (!route) throw new Error(`HTTP 404: ${path}`);
      return route[1](query, path) as T;
    },
    async image(url: string) {
      imageCalls.push(url);
      const r = opts.images?.(url) ?? { status: 200, body: JPEG };
      return { status: r.status, body: r.body ?? Buffer.alloc(0), headers: new Headers(r.headers) };
    },
    report(r: ImageReport) {
      reports.push(r);
    },
  };
}
