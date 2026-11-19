import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import type { DownloadInput, DownloadResult } from "../image-store.js";
import { type MangadexClient, isMangadexOrgHost, mangadexClient } from "../mangadex/client.js";
import { type MdAtHome, pageUrls } from "../mangadex/map.js";
import type { MangaSourceAdapter, RawPage, RawSeries, RawSeriesDetail } from "./types.js";

/**
 * MangaDex als Quelle für den Leser. Reihen und Kapitel kommen über den Katalog
 * (manga/mangadex/catalog.ts + open.ts); dieser Adapter liefert nur die Seiten eines
 * Kapitels. `chapterUrl` ist die Kapitel-UUID von MangaDex.
 *
 * Seiten kommen von MangaDex@Home-Knoten. Scheitert ein Bild, wird einmal ein frischer
 * Knoten erfragt und das Bild von dort geholt. Jedes Bild von einem Knoten außerhalb von
 * mangadex.org wird an MangaDex zurückgemeldet (Erfolg, Bytes, Dauer, Cache) — so verlangt
 * es die Doku, damit kaputte Knoten aus dem Netz fliegen.
 */

/** Wie lange ein erfragter Knoten für Wiederholungen derselben Kapitel-Seiten gilt. */
const FRESH_MS = 60_000;

export function createMangadexAdapter(client: MangadexClient): MangaSourceAdapter {
  /** Hash eines Kapitels → Kapitel-UUID, damit ein gescheitertes Bild seinen Knoten neu erfragen kann. */
  const chapterOfHash = new Map<string, string>();
  const refreshed = new Map<string, { at: number; baseUrl: Promise<string> }>();

  async function atHome(chapterUuid: string): Promise<MdAtHome> {
    const ah = await client.api<MdAtHome>(`/at-home/server/${encodeURIComponent(chapterUuid)}`);
    chapterOfHash.set(ah.chapter.hash, chapterUuid);
    return ah;
  }

  async function fetchChapterPages(chapterUrl: string): Promise<RawPage[]> {
    const ah = await atHome(chapterUrl);
    return pageUrls(ah).map((sourceUrl, i) => ({ pageNumber: i + 1, sourceUrl }));
  }

  /** Dieselbe Seite über einen frisch erfragten Knoten — oder `null`, wenn das nicht geht. */
  async function freshUrl(url: string): Promise<string | null> {
    const m = url.match(/\/data\/([^/]+)\/([^/?#]+)/);
    if (!m) return null;
    const [, hash, file] = m as unknown as [string, string, string];
    const chapter = chapterOfHash.get(hash);
    if (!chapter) return null;
    let entry = refreshed.get(chapter);
    // Mehrere Seiten scheitern meist zusammen — ein Knoten-Wechsel für alle.
    if (!entry || Date.now() - entry.at > FRESH_MS) {
      entry = { at: Date.now(), baseUrl: atHome(chapter).then((ah) => ah.baseUrl) };
      refreshed.set(chapter, entry);
      entry.baseUrl.catch(() => refreshed.delete(chapter));
    }
    const base = await entry.baseUrl;
    return `${base}/data/${hash}/${file}`;
  }

  async function tryImage(url: string): Promise<Buffer | null> {
    const t0 = Date.now();
    const report = !isMangadexOrgHost(url);
    try {
      const r = await client.image(url);
      const ok = r.status === 200 && r.body.length > 0;
      if (report) {
        client.report({
          url,
          success: ok,
          bytes: ok ? r.body.length : 0,
          duration: Date.now() - t0,
          cached: (r.headers.get("x-cache") ?? "").toUpperCase().startsWith("HIT"),
        });
      }
      return ok ? r.body : null;
    } catch {
      if (report) {
        client.report({ url, success: false, bytes: 0, duration: Date.now() - t0, cached: false });
      }
      return null;
    }
  }

  async function downloadPage(input: DownloadInput): Promise<DownloadResult> {
    let body = await tryImage(input.sourceUrl);
    if (!body) {
      const again = await freshUrl(input.sourceUrl).catch(() => null);
      if (again) body = await tryImage(again);
    }
    if (!body) throw new Error(`Seite nicht ladbar: ${input.sourceUrl}`);
    const full = join(input.baseDir, input.relativePath);
    mkdirSync(dirname(full), { recursive: true });
    writeFileSync(full, body);
    return { relativePath: input.relativePath, bytes: body.length };
  }

  return {
    id: "mangadex",
    name: "MangaDex",
    baseUrl: "https://mangadex.org",
    catalogOnly: true,
    async listSeries(): Promise<RawSeries[]> {
      return [];
    },
    async fetchSeriesDetail(): Promise<RawSeriesDetail> {
      throw new Error("MangaDex-Reihen werden über den Katalog geöffnet");
    },
    fetchChapterPages,
    downloadPage,
  };
}

/** Der Adapter des Servers — teilt sich die Leitung (und damit das Tempo) mit dem Katalog. */
export const mangadexAdapter: MangaSourceAdapter = createMangadexAdapter(mangadexClient());
