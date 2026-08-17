import { Readable } from "node:stream";
import type { FastifyReply } from "fastify";

// Timeout NUR für die Header-Phase des Upstream-Fetches; nach Response-Eingang
// streamt der Body ohne Timeout weiter.
const DEFAULT_HEADER_TIMEOUT_MS = 12_000;
// Verzögerungen vor Retries derselben Upstream-URL, bevor teuer neu aufgelöst wird.
const DEFAULT_RETRY_DELAYS_MS = [300, 1000];

export interface ProxyOpts {
  fetchImpl?: typeof fetch;
  headerTimeoutMs?: number;
  retryDelaysMs?: number[];
}

/**
 * Gemeinsamer Streaming-Proxy für Audio UND Video: das Handy holt die Bytes
 * vom Mac statt direkt von googlevideo — die URLs dort sind an Netz/IP des
 * Auflösers gebunden und spielen von fremden Netzen aus nicht zuverlässig
 * ab. Range-Requests werden durchgereicht (206 + Content-Range), sonst kann
 * ExoPlayer nicht seeken.
 */
export async function proxyMediaStream(
  reply: FastifyReply,
  range: string | undefined,
  resolveUrl: (force: boolean) => Promise<string | undefined>,
  kind: "audio" | "video",
  opts: ProxyOpts = {},
): Promise<FastifyReply> {
  const fetchImpl = opts.fetchImpl ?? fetch;
  const headerTimeoutMs = opts.headerTimeoutMs ?? DEFAULT_HEADER_TIMEOUT_MS;
  const retryDelays = opts.retryDelaysMs ?? DEFAULT_RETRY_DELAYS_MS;
  const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

  let resolved = false;
  for (const force of [false, true]) {
    const url = await resolveUrl(force);
    if (!url) continue;
    resolved = true;

    let upstream: Response | undefined;
    // Transiente Fetch-Exceptions (Reset, Header-Timeout): erst dieselbe URL
    // kurz retryen — sie lebt meist noch — und erst dann teuer neu auflösen.
    for (let attempt = 0; ; attempt++) {
      // Timeout NUR für die Header-Phase: hängt googlevideo bei Connect/Headern,
      // hängt sonst der Request ewig. Nach Response-Eingang wird der Timer
      // gecleart — der Body streamt ohne Timeout, so lange abgespielt wird.
      const headerAbort = new AbortController();
      const headerTimer = setTimeout(() => headerAbort.abort(), headerTimeoutMs);
      try {
        upstream = await fetchImpl(url, {
          headers: range ? { range } : {},
          signal: headerAbort.signal,
        });
        break;
      } catch {
        if (attempt >= retryDelays.length) break;
        await sleep(retryDelays[attempt] ?? 0);
      } finally {
        clearTimeout(headerTimer);
      }
    }
    if (!upstream) continue;
    // 403/410 = abgelaufene oder netzfremde URL → einmal frisch auflösen
    if (upstream.status === 403 || upstream.status === 410) continue;
    if (!upstream.ok && upstream.status !== 206) continue;

    reply.code(upstream.status);
    for (const name of ["content-type", "content-length", "content-range", "accept-ranges"]) {
      const value = upstream.headers.get(name);
      if (value) reply.header(name, value);
    }
    if (!upstream.headers.get("accept-ranges")) reply.header("accept-ranges", "bytes");
    return reply.send(upstream.body ? Readable.fromWeb(upstream.body) : "");
  }
  return reply
    .code(502)
    .send({ error: resolved ? `upstream ${kind} fetch failed` : `${kind} extraction failed` });
}
