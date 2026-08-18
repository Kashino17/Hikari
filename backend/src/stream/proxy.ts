import { Readable } from "node:stream";
import type { FastifyReply } from "fastify";

// Timeout NUR für die Header-Phase des Upstream-Fetches; nach Response-Eingang
// streamt der Body ohne Timeout weiter.
const DEFAULT_HEADER_TIMEOUT_MS = 12_000;
// googlevideo verlangt (rollierend erzwungen, beobachtet 2026-08) einen
// browserartigen User-Agent — undici/fetch ohne UA bekommt 403.
export const BROWSER_UA =
  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36";
// Verzögerungen vor Retries derselben Upstream-URL, bevor teuer neu aufgelöst wird.
const DEFAULT_RETRY_DELAYS_MS = [300, 1000];

export interface ProxyOpts {
  fetchImpl?: typeof fetch | undefined;
  headerTimeoutMs?: number | undefined;
  retryDelaysMs?: number[] | undefined;
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
  // Fehler-Sammler: jeder verworfene Versuch landet hier — beim 502 wird die
  // komplette Kette geloggt (vorher war jede Ursache unsichtbar).
  const failures: string[] = [];
  for (const force of [false, true]) {
    const url = await resolveUrl(force);
    if (!url) {
      failures.push(`resolve(force=${force}): keine URL`);
      continue;
    }
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
          headers: { "user-agent": BROWSER_UA, ...(range ? { range } : {}) },
          signal: headerAbort.signal,
        });
        break;
      } catch (err) {
        const cause = (err as { cause?: { code?: string } })?.cause?.code ?? "";
        failures.push(
          `fetch#${attempt}(force=${force}): ${err instanceof Error ? err.message : String(err)}${cause ? ` [${cause}]` : ""}`,
        );
        if (attempt >= retryDelays.length) break;
        await sleep(retryDelays[attempt] ?? 0);
      } finally {
        clearTimeout(headerTimer);
      }
    }
    if (!upstream) continue;
    // 403/410 = abgelaufene oder netzfremde URL → einmal frisch auflösen
    if (upstream.status === 403 || upstream.status === 410) {
      failures.push(`upstream ${upstream.status} (force=${force})`);
      continue;
    }
    if (!upstream.ok && upstream.status !== 206) {
      failures.push(`upstream ${upstream.status} (force=${force})`);
      continue;
    }

    reply.code(upstream.status);
    for (const name of ["content-type", "content-length", "content-range", "accept-ranges"]) {
      const value = upstream.headers.get(name);
      if (value) reply.header(name, value);
    }
    if (!upstream.headers.get("accept-ranges")) reply.header("accept-ranges", "bytes");
    return reply.send(upstream.body ? Readable.fromWeb(upstream.body) : "");
  }
  reply.log.warn({ kind, resolved, failures }, "media proxy failed");
  return reply
    .code(502)
    .send({ error: resolved ? `upstream ${kind} fetch failed` : `${kind} extraction failed` });
}
