import { Readable } from "node:stream";
import type { FastifyReply } from "fastify";

// Timeout NUR für die Header-Phase des Upstream-Fetches; nach Response-Eingang
// streamt der Body ohne Timeout weiter.
const DEFAULT_HEADER_TIMEOUT_MS = 12_000;
// googlevideo verlangt (rollierend erzwungen, beobachtet 2026-08) einen
// browserartigen User-Agent — undici/fetch ohne UA bekommt 403.
export const BROWSER_UA =
  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36";
// WEB_EMBEDDED_PLAYER-URLs (unser bevorzugter yt-dlp-Client) liefern ohne
// Referer/Origin pauschal 403 — mit ihnen funktionieren sogar offene Ranges
// (hart reproduziert 18.08.2026).
export const YT_FETCH_HEADERS = {
  "user-agent": BROWSER_UA,
  referer: "https://www.youtube.com/",
  origin: "https://www.youtube.com",
} as const;
// Verzögerungen vor Retries derselben Upstream-URL, bevor teuer neu aufgelöst wird.
const DEFAULT_RETRY_DELAYS_MS = [300, 1000];
// Fallback, falls googlevideo offene Ranges wieder 403t (ANDROID_VR-URLs tun
// das heute schon): offene Client-Ranges als Serie begrenzter Chunks streamen.
// Achtung Größenlimit dort: ≤768 KiB ok, ab ~1 MiB wieder 403 — 512 KiB puffern.
export const UPSTREAM_CHUNK_BYTES = 512 * 1024;

export interface ProxyOpts {
  fetchImpl?: typeof fetch | undefined;
  headerTimeoutMs?: number | undefined;
  retryDelaysMs?: number[] | undefined;
  chunkBytes?: number | undefined;
}

/**
 * Gemeinsamer Streaming-Proxy für Audio UND Video: das Handy holt die Bytes
 * vom Mac statt direkt von googlevideo — die URLs dort sind an Netz/IP des
 * Auflösers gebunden und spielen von fremden Netzen aus nicht zuverlässig
 * ab. Range-Requests werden durchgereicht (206 + Content-Range), sonst kann
 * ExoPlayer nicht seeken. 403t der Upstream einen offenen Range, springt der
 * Chunk-Fallback ein, bevor teuer neu aufgelöst wird.
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
  const chunkBytes = opts.chunkBytes ?? UPSTREAM_CHUNK_BYTES;
  const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

  // "bytes=N-" oder gar kein Range: nur dafür existiert der Chunk-Fallback —
  // begrenzte Client-Ranges gehen ohnehin als einzelner Chunk durch.
  const openRangeStart = (() => {
    if (!range) return 0;
    const m = /^bytes=(\d+)-$/.exec(range.trim());
    return m?.[1] !== undefined ? Number(m[1]) : null;
  })();

  // Ein Upstream-Fetch mit Header-Timeout + Retries derselben URL.
  const fetchWithRetry = async (
    url: string,
    reqRange: string | undefined,
    onError?: (msg: string) => void,
  ): Promise<Response | undefined> => {
    for (let attempt = 0; ; attempt++) {
      const headerAbort = new AbortController();
      const headerTimer = setTimeout(() => headerAbort.abort(), headerTimeoutMs);
      try {
        return await fetchImpl(url, {
          headers: { ...YT_FETCH_HEADERS, ...(reqRange ? { range: reqRange } : {}) },
          signal: headerAbort.signal,
        });
      } catch (err) {
        const cause = (err as { cause?: { code?: string } })?.cause?.code ?? "";
        onError?.(
          `fetch#${attempt}: ${err instanceof Error ? err.message : String(err)}${cause ? ` [${cause}]` : ""}`,
        );
        if (attempt >= retryDelays.length) return undefined;
        await sleep(retryDelays[attempt] ?? 0);
      } finally {
        clearTimeout(headerTimer);
      }
    }
  };

  // Chunk-Fallback: offenen Range als Serie begrenzter Chunks streamen.
  // Gibt undefined zurück, wenn schon der erste Chunk scheitert (→ nächste
  // Auflösungsrunde); ab gesendeten Headern werden Folgefehler zum Stream-Abbruch.
  const tryChunked = async (url: string, failures: string[]): Promise<FastifyReply | undefined> => {
    // Niemals doppelt senden — falls ein früherer Versuch schon Header
    // rausgeschickt hat, ist dieser Request entschieden.
    if (reply.sent) return reply;
    const start = openRangeStart ?? 0;
    const first = await fetchWithRetry(url, `bytes=${start}-${start + chunkBytes - 1}`, (m) =>
      failures.push(`chunk0 ${m}`),
    );
    if (!first) return undefined;
    if (first.status !== 206) {
      failures.push(`chunk0 upstream ${first.status}`);
      return undefined;
    }
    const contentRange = first.headers.get("content-range") ?? "";
    const total = Number(/\/(\d+)$/.exec(contentRange)?.[1] ?? Number.NaN);
    if (!Number.isFinite(total) || total <= start) {
      failures.push(`chunk0 ohne brauchbares content-range "${contentRange}"`);
      return undefined;
    }

    if (range) {
      reply.code(206);
      reply.header("content-range", `bytes ${start}-${total - 1}/${total}`);
    } else {
      reply.code(200);
    }
    reply.header("content-length", String(total - start));
    reply.header("accept-ranges", "bytes");
    const contentType = first.headers.get("content-type");
    if (contentType) reply.header("content-type", contentType);

    const chunkIterator = async function* () {
      // Fehler ab hier dürfen NIE in Fastifys Error-Pipeline laufen — die
      // Header sind gesendet, ein reply.send(500) crasht den Prozess
      // (ERR_HTTP_HEADERS_SENT-Kaskade, passiert 18.08.). Stattdessen: Socket
      // hart kappen, der Client erkennt den abgebrochenen Stream selbst.
      try {
        let pos = start;
        let current: Response | undefined = first;
        while (pos < total) {
          if (!current) {
            const end = Math.min(pos + chunkBytes, total) - 1;
            current = await fetchWithRetry(url, `bytes=${pos}-${end}`);
            if (!current || current.status !== 206) {
              throw new Error(`chunk upstream ${current?.status ?? "fetch failed"} @${pos}`);
            }
          }
          if (current.body) {
            for await (const piece of current.body) {
              pos += (piece as Uint8Array).byteLength;
              yield piece as Uint8Array;
            }
          }
          current = undefined;
        }
      } catch (err) {
        reply.log.warn(
          { kind, err: err instanceof Error ? err.message : String(err) },
          "chunk stream abgebrochen",
        );
        reply.raw.destroy();
      }
    };
    return reply.send(Readable.from(chunkIterator(), { objectMode: false }));
  };

  let resolved = false;
  // Fehler-Sammler: jeder verworfene Versuch landet hier — beim 502 wird die
  // komplette Kette geloggt (vorher war jede Ursache unsichtbar).
  const failures: string[] = [];
  // 403/410-URLs für den Chunk-Fallback NACH beiden Auflösungsrunden — nicht
  // davor: eine alte, positional gekappte URL würde sonst gechunkt streamen
  // und bei ~1 MiB abreißen, obwohl die Force-Auflösung eine voll rangebare
  // web_embedded-URL geliefert hätte.
  const blockedUrls: string[] = [];
  // Zwei Force-Runden: frisch aufgelöste URLs sind gelegentlich Blindgänger
  // (sofort 403 auf alles, beobachtet 18.08.) — die nächste Auflösung
  // desselben Videos liefert dann eine funktionierende URL.
  for (const force of [false, true, true]) {
    const url = await resolveUrl(force);
    if (!url) {
      failures.push(`resolve(force=${force}): keine URL`);
      continue;
    }
    resolved = true;
    if (blockedUrls.includes(url)) {
      failures.push(`resolve(force=${force}): selbe URL erneut`);
      continue;
    }

    const upstream = await fetchWithRetry(url, range, (m) => failures.push(`${m} (force=${force})`));
    if (!upstream) continue;
    // 403/410 = abgelaufene/netzfremde URL oder geblockter offener Range
    if (upstream.status === 403 || upstream.status === 410) {
      failures.push(`upstream ${upstream.status} (force=${force})`);
      blockedUrls.unshift(url); // frischeste zuerst
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

  // Letzte Rettung für offene Ranges: begrenzte Chunks von der frischesten
  // geblockten URL — greift, falls googlevideo offene Ranges generell 403t.
  if (openRangeStart !== null) {
    for (const url of blockedUrls) {
      const sent = await tryChunked(url, failures);
      if (sent || reply.sent) return reply;
    }
  }
  // Guard: falls doch schon ein Stream lief (Header raus), weder warnen noch
  // einen 502 hinterherschicken — Letzteres löst Fastifys
  // ERR_HTTP_HEADERS_SENT-Kaskade aus, die den Prozess reißen kann.
  if (reply.sent) return reply;
  reply.log.warn({ kind, resolved, failures }, "media proxy failed");
  return reply
    .code(502)
    .send({ error: resolved ? `upstream ${kind} fetch failed` : `${kind} extraction failed` });
}
