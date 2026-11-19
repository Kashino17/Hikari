/**
 * Höflicher Zugang zu MangaDex (https://api.mangadex.org/docs/): eigener User-Agent ohne
 * persönliche Daten, höchstens vier API-Anfragen pro Sekunde für den ganzen Server, Pause
 * nach 429/503 so lange, wie MangaDex es verlangt (`retry-after` oder
 * `x-ratelimit-retry-after`). Bilder (Seiten, Cover) laufen über eine eigene, ebenso
 * gedrosselte Leitung — sie zählen bei MangaDex nicht zum API-Limit, sollen aber trotzdem
 * nicht in Wellen kommen.
 */

export const MANGADEX_UA = "Hikari-Manga/1.0 (private reader)";
export const MANGADEX_API = "https://api.mangadex.org";
const REPORT_URL = "https://api.mangadex.network/report";

export type Query = Record<string, string | number | boolean | string[]>;

export interface ImageResponse {
  status: number;
  body: Buffer;
  headers: Headers;
}

/** Rückmeldung an MangaDex@Home: hat das Bild geklappt? (verlangt die Doku für fremde Knoten) */
export interface ImageReport {
  url: string;
  success: boolean;
  bytes: number;
  duration: number;
  cached: boolean;
}

export interface MangadexClient {
  /** JSON von der API; wirft bei allem außer 200. */
  api<T>(path: string, query?: Query): Promise<T>;
  /** Ein Bild, ein Versuch (Wiederholen entscheidet der Aufrufer). */
  image(url: string): Promise<ImageResponse>;
  /** Fire-and-forget, Fehler werden verschluckt. */
  report(r: ImageReport): void;
}

export class MangadexHttpError extends Error {
  constructor(
    public readonly status: number,
    url: string,
  ) {
    super(`HTTP ${status}: ${url}`);
    this.name = "MangadexHttpError";
  }
}

/** `key[]=a&key[]=b` für Listen, wie die API es erwartet. */
export function buildQuery(query: Query | undefined): string {
  if (!query) return "";
  const p = new URLSearchParams();
  for (const [k, v] of Object.entries(query)) {
    if (Array.isArray(v)) for (const x of v) p.append(k, x);
    else p.append(k, String(v));
  }
  const s = p.toString();
  return s ? `?${s}` : "";
}

type FetchLike = (url: string, init?: RequestInit) => Promise<Response>;

/** Gleichmäßiger Takt: jede Anfrage bekommt ihren Platz im Raster. */
function limiter(perSecond: number, sleep: (ms: number) => Promise<void>) {
  const gap = 1000 / perSecond;
  let next = 0;
  return async () => {
    const now = Date.now();
    const wait = Math.max(0, next - now);
    next = Math.max(now, next) + gap;
    if (wait > 0) await sleep(wait);
  };
}

/** Wie lange nach einem 429/503 warten? */
export function retryWaitMs(headers: Headers, now: number, fallbackMs: number): number {
  const ra = Number(headers.get("retry-after"));
  if (Number.isFinite(ra) && ra > 0) return ra * 1000;
  // MangaDex schickt einen Zeitpunkt (Unix-Sekunden), ab dem es wieder geht.
  const at = Number(headers.get("x-ratelimit-retry-after"));
  if (Number.isFinite(at) && at * 1000 > now) return at * 1000 - now + 250;
  return fallbackMs;
}

export function createMangadexClient(
  opts: {
    fetchImpl?: FetchLike;
    sleep?: (ms: number) => Promise<void>;
    perSecond?: number;
    imagesPerSecond?: number;
    timeoutMs?: number;
    /** Pause nach 429/503 ohne Angabe, wie lange. */
    fallbackWaitMs?: number;
    /** Längste Pause am Stück. */
    maxWaitMs?: number;
    tries?: number;
  } = {},
): MangadexClient {
  const fetchImpl: FetchLike = opts.fetchImpl ?? ((u, i) => fetch(u, i));
  const sleep = opts.sleep ?? ((ms: number) => new Promise<void>((r) => setTimeout(r, ms)));
  const apiSlot = limiter(opts.perSecond ?? 4, sleep);
  const imageSlot = limiter(opts.imagesPerSecond ?? 4, sleep);
  const timeoutMs = opts.timeoutMs ?? 30_000;
  const fallbackWait = opts.fallbackWaitMs ?? 60_000;
  const maxWait = opts.maxWaitMs ?? 5 * 60_000;
  const tries = Math.max(1, opts.tries ?? 3);

  async function get(
    url: string,
    slot: () => Promise<void>,
    accept: string,
    maxTries: number,
  ): Promise<ImageResponse> {
    let last = 0;
    for (let attempt = 1; attempt <= maxTries; attempt++) {
      await slot();
      const res = await fetchImpl(url, {
        headers: { "user-agent": MANGADEX_UA, accept },
        redirect: "follow",
        signal: AbortSignal.timeout(timeoutMs),
      });
      if ((res.status === 429 || res.status === 503) && attempt < maxTries) {
        last = res.status;
        await res.arrayBuffer().catch(() => undefined);
        await sleep(Math.min(retryWaitMs(res.headers, Date.now(), fallbackWait), maxWait));
        continue;
      }
      return {
        status: res.status,
        body: Buffer.from(await res.arrayBuffer()),
        headers: res.headers,
      };
    }
    throw new MangadexHttpError(last, url);
  }

  return {
    async api<T>(path: string, query?: Query): Promise<T> {
      const url = `${MANGADEX_API}${path}${buildQuery(query)}`;
      const r = await get(url, apiSlot, "application/json", tries);
      if (r.status !== 200) throw new MangadexHttpError(r.status, url);
      return JSON.parse(r.body.toString("utf8")) as T;
    },
    image(url: string) {
      return get(url, imageSlot, "image/*", 1);
    },
    report(r: ImageReport) {
      // Nur die Bildadresse und Messwerte — nichts über uns.
      fetchImpl(REPORT_URL, {
        method: "POST",
        headers: { "user-agent": MANGADEX_UA, "content-type": "application/json" },
        body: JSON.stringify(r),
        signal: AbortSignal.timeout(10_000),
      })
        .then((res) => res.arrayBuffer())
        .catch(() => undefined);
    },
  };
}

let shared: MangadexClient | null = null;

/** Die eine Leitung des Servers — Katalog, Öffnen, Cover und Seiten teilen sich das Tempo. */
export function mangadexClient(): MangadexClient {
  if (!shared) shared = createMangadexClient();
  return shared;
}

/** Bild-Host von MangaDex selbst? Dann keine Rückmeldung (nur für @Home-Knoten gedacht). */
export function isMangadexOrgHost(url: string): boolean {
  try {
    const host = new URL(url).hostname;
    return host === "mangadex.org" || host.endsWith(".mangadex.org");
  } catch {
    return false;
  }
}
