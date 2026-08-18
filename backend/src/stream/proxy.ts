import { Readable } from "node:stream";
import type { FastifyReply } from "fastify";

// Timeout NUR für die Header-Phase des Upstream-Fetches; der Body hat seinen
// eigenen Stillstands-Wächter (siehe STALL_TIMEOUT_MS).
const DEFAULT_HEADER_TIMEOUT_MS = 12_000;
// googlevideo verlangt (rollierend erzwungen, beobachtet 2026-08) einen
// browserartigen User-Agent — undici/fetch ohne UA bekommt 403.
export const BROWSER_UA =
  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36";
// WEB_EMBEDDED_PLAYER-URLs (unser bevorzugter yt-dlp-Client) liefern ohne
// Referer/Origin pauschal 403 (hart reproduziert 18.08.2026).
export const YT_FETCH_HEADERS = {
  "user-agent": BROWSER_UA,
  referer: "https://www.youtube.com/",
  origin: "https://www.youtube.com",
} as const;
// Verzögerungen vor Retries derselben Upstream-URL, bevor teuer neu aufgelöst wird.
const DEFAULT_RETRY_DELAYS_MS = [300, 1000];
// Blockgröße für offene Ranges. googlevideo drosselt offene Requests
// ("bytes=N-") auf Abspieltempo — gemessen 18.08.2026 auf derselben URL:
// offen 32 KB/s, 512-KiB-Block 3,8 MB/s, 4-MiB-Block 19 MB/s, 10-MiB-Block
// 32 MB/s. Deshalb wird IMMER in begrenzten Blöcken geholt.
export const UPSTREAM_CHUNK_BYTES = 4 * 1024 * 1024;
// Manche URLs (Nicht-Embedded-Clients) lehnen große Ranges mit 403 ab —
// dann wird die Blockgröße halbiert, bis hier unten Schluss ist.
const MIN_CHUNK_BYTES = 256 * 1024;
// Kommen so lange keine Bytes mehr, gilt der Upstream als eingeschlafen:
// googlevideo hört bei großen Dateien gern mitten im Stream einfach auf zu
// senden, ohne die Verbindung zu schließen (gemessen 18.08.: Stillstand bei
// exakt 9,25 MiB, danach ewiges Hängen → "lädt und lädt" in der App).
const STALL_TIMEOUT_MS = 15_000;
// Fortsetzversuche an derselben Byte-Position, bevor aufgegeben wird.
const MAX_RESUMES_AT_POSITION = 3;

export interface ProxyOpts {
  fetchImpl?: typeof fetch | undefined;
  headerTimeoutMs?: number | undefined;
  retryDelaysMs?: number[] | undefined;
  chunkBytes?: number | undefined;
  stallTimeoutMs?: number | undefined;
}

export interface MediaStreamOpts extends ProxyOpts {
  /** Erstes Byte (Default 0). */
  start?: number | undefined;
  /** Diagnosezeilen für den Aufrufer (Fehlerkette im 502-Log). */
  onNote?: ((msg: string) => void) | undefined;
}

export interface MediaStream {
  /** Antwort auf den ersten Block — Quelle für Content-Type & Co. */
  first: Response;
  /** Gesamtlänge der Datei, falls der Upstream sie nennt. */
  total?: number | undefined;
  /** Bytes ab `start` bis zum Dateiende; wirft, wenn es endgültig hakt. */
  chunks: AsyncGenerator<Uint8Array>;
}

/** Liest den Body und wirft, wenn `stallMs` lang kein Byte mehr ankommt. */
async function* readWithStallGuard(
  body: ReadableStream<Uint8Array>,
  stallMs: number,
): AsyncGenerator<Uint8Array> {
  const reader = body.getReader();
  try {
    for (;;) {
      let timer: NodeJS.Timeout | undefined;
      const stall = new Promise<never>((_, reject) => {
        timer = setTimeout(() => reject(new Error(`upstream stalled ${stallMs}ms`)), stallMs);
      });
      try {
        const { done, value } = await Promise.race([reader.read(), stall]);
        if (done) return;
        if (value) yield value;
      } finally {
        if (timer) clearTimeout(timer);
      }
    }
  } finally {
    // Client weg oder Fortsetzen nötig: Upstream-Verbindung freigeben.
    await reader.cancel().catch(() => undefined);
  }
}

/** Gesamtlänge aus "bytes X-Y/TOTAL". */
function totalFromContentRange(res: Response): number | undefined {
  const total = Number(/\/(\d+)$/.exec(res.headers.get("content-range") ?? "")?.[1] ?? Number.NaN);
  return Number.isFinite(total) ? total : undefined;
}

/**
 * Ein einzelner Upstream-Fetch mit Header-Timeout und Retries derselben URL.
 * Der Timeout gilt NUR für die Header-Phase — der Body hat den
 * Stillstands-Wächter (readWithStallGuard).
 */
export async function fetchUpstreamRange(
  url: string,
  reqRange: string,
  opts: ProxyOpts = {},
  onNote: (msg: string) => void = () => undefined,
): Promise<Response | undefined> {
  const fetchImpl = opts.fetchImpl ?? fetch;
  const headerTimeoutMs = opts.headerTimeoutMs ?? DEFAULT_HEADER_TIMEOUT_MS;
  const retryDelays = opts.retryDelaysMs ?? DEFAULT_RETRY_DELAYS_MS;
  const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

  for (let attempt = 0; ; attempt++) {
    const headerAbort = new AbortController();
    const headerTimer = setTimeout(() => headerAbort.abort(), headerTimeoutMs);
    try {
      return await fetchImpl(url, {
        headers: { ...YT_FETCH_HEADERS, range: reqRange },
        signal: headerAbort.signal,
      });
    } catch (err) {
      const cause = (err as { cause?: { code?: string } })?.cause?.code ?? "";
      onNote(
        `fetch#${attempt}: ${err instanceof Error ? err.message : String(err)}${cause ? ` [${cause}]` : ""}`,
      );
      if (attempt >= retryDelays.length) return undefined;
      await sleep(retryDelays[attempt] ?? 0);
    } finally {
      clearTimeout(headerTimer);
    }
  }
}

/**
 * Öffnet einen googlevideo-Stream als Serie begrenzter Blöcke — der einzige
 * Weg, der volle Bandbreite bekommt (offene/fehlende Ranges drosselt
 * googlevideo auf Abspieltempo) und der Stillstände mitten in der Datei
 * übersteht. Genutzt vom Streaming-Proxy UND vom Offline-Download-Worker.
 *
 * `undefined` heißt: Mit dieser URL geht es nicht (403/Netzfehler) — der
 * Aufrufer soll frisch auflösen.
 */
export async function openMediaStream(
  url: string,
  opts: MediaStreamOpts = {},
): Promise<MediaStream | undefined> {
  const stallTimeoutMs = opts.stallTimeoutMs ?? STALL_TIMEOUT_MS;
  const start = opts.start ?? 0;
  const note = opts.onNote ?? (() => undefined);
  // Schrumpft bei 403 auf große Ranges; gilt für den restlichen Stream.
  let chunkBytes = opts.chunkBytes ?? UPSTREAM_CHUNK_BYTES;
  const minChunkBytes = Math.min(MIN_CHUNK_BYTES, chunkBytes);

  const fetchRange = (reqRange: string) => fetchUpstreamRange(url, reqRange, opts, note);

  // Lehnt der Upstream große Ranges ab (403 bei manchen Nicht-Embedded-URLs),
  // Blockgröße halbieren und erneut versuchen — bis MIN_CHUNK_BYTES.
  const fetchChunkAt = async (pos: number, endExclusive: number): Promise<Response | undefined> => {
    for (;;) {
      const end = Math.min(pos + chunkBytes, endExclusive) - 1;
      const res = await fetchRange(`bytes=${pos}-${end}`);
      if (!res || res.status === 206 || res.status === 200) return res;
      if (chunkBytes <= minChunkBytes) return res;
      chunkBytes = Math.max(minChunkBytes, Math.floor(chunkBytes / 2));
      note(`block ${res.status} @${pos} — Blockgröße auf ${chunkBytes} halbiert`);
    }
  };

  const first = await fetchChunkAt(start, Number.POSITIVE_INFINITY);
  if (!first) return undefined;
  if (first.status === 200) {
    // Upstream ignoriert Ranges (kein googlevideo, z. B. Datei/CDN):
    // durchreichen, blockweises Nachladen ist dort weder nötig noch möglich.
    const length = Number(first.headers.get("content-length") ?? Number.NaN);
    const body = first.body;
    return {
      first,
      total: Number.isFinite(length) ? length : undefined,
      chunks: (async function* () {
        if (body) yield* readWithStallGuard(body, stallTimeoutMs);
      })(),
    };
  }
  if (first.status !== 206) {
    note(`block0 upstream ${first.status}`);
    return undefined;
  }
  const total = totalFromContentRange(first);
  if (total === undefined || total <= start) {
    note(`block0 ohne brauchbares content-range "${first.headers.get("content-range")}"`);
    return undefined;
  }

  /**
   * Block für Block bis zum Dateiende. Schläft googlevideo mitten im Block ein
   * oder endet er vorzeitig, wird ab genau der erreichten Byte-Position
   * weitergeholt — der Empfänger sieht nur eine kurze Pause.
   */
  const chunks = (async function* () {
    let pos = start;
    let current: Response | undefined = first;
    let lastResumePos = -1;
    let resumesHere = 0;
    while (pos < total) {
      try {
        if (current?.body) {
          for await (const piece of readWithStallGuard(current.body, stallTimeoutMs)) {
            pos += piece.byteLength;
            yield piece;
            if (pos >= total) return;
          }
        }
      } catch (err) {
        note(`block @${pos} unterbrochen: ${err instanceof Error ? err.message : String(err)}`);
      }
      current = undefined;
      if (pos >= total) return;

      // Kein Fortschritt seit dem letzten Nachladen? Dann irgendwann Schluss.
      resumesHere = pos === lastResumePos ? resumesHere + 1 : 0;
      lastResumePos = pos;
      if (resumesHere >= MAX_RESUMES_AT_POSITION) {
        throw new Error(`kein Fortschritt nach ${resumesHere} Versuchen @${pos}`);
      }
      current = await fetchChunkAt(pos, total);
      if (!current || (current.status !== 206 && current.status !== 200)) {
        throw new Error(`block ${current?.status ?? "fetch failed"} @${pos}`);
      }
    }
  })();

  return { first, total, chunks };
}

/**
 * Gemeinsamer Streaming-Proxy für Audio UND Video: das Handy holt die Bytes
 * vom Mac statt direkt von googlevideo — die URLs dort sind an Netz/IP des
 * Auflösers gebunden und spielen von fremden Netzen aus nicht zuverlässig ab.
 * Range-Requests werden beantwortet (206 + Content-Range), sonst kann
 * ExoPlayer nicht seeken.
 *
 * Kernpunkt (alles am 18.08.2026 gemessen): googlevideo drosselt offene und
 * fehlende Ranges auf Abspieltempo und hört bei großen Dateien mittendrin auf
 * zu senden. Der Proxy holt deshalb IMMER in begrenzten Blöcken und setzt bei
 * Stillstand byte-genau wieder auf — nach außen bleibt es ein einziger Stream.
 */
export async function proxyMediaStream(
  reply: FastifyReply,
  range: string | undefined,
  resolveUrl: (force: boolean) => Promise<string | undefined>,
  kind: "audio" | "video",
  opts: ProxyOpts = {},
): Promise<FastifyReply> {
  // Startbyte bei offenem ("bytes=N-") oder fehlendem Range. Begrenzte Ranges
  // (null) sind selbst schon Blöcke und gehen unverändert durch.
  const openRangeStart = (() => {
    if (!range) return 0;
    const m = /^bytes=(\d+)-$/.exec(range.trim());
    return m?.[1] !== undefined ? Number(m[1]) : null;
  })();

  let resolved = false;
  // Fehler-Sammler: jeder verworfene Versuch landet hier — beim 502 wird die
  // komplette Kette geloggt (vorher war jede Ursache unsichtbar).
  const failures: string[] = [];
  const triedUrls: string[] = [];
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
    // Dieselbe URL ein zweites Mal zu probieren kostet nur Timeouts.
    if (triedUrls.includes(url)) {
      failures.push(`resolve(force=${force}): selbe URL erneut`);
      continue;
    }
    triedUrls.push(url);
    const note = (m: string) => failures.push(`${m} (force=${force})`);

    // Begrenzter Client-Range: unverändert durchreichen — er ist selbst schon
    // ein Block und wird von googlevideo nicht gedrosselt.
    if (openRangeStart === null) {
      const upstream = await fetchUpstreamRange(url, range as string, opts, note);
      if (!upstream) continue;
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

    const media = await openMediaStream(url, { ...opts, start: openRangeStart, onNote: note });
    if (!media) continue;

    const start = openRangeStart;
    // Der Client sieht eine durchgehende Antwort über den ganzen Rest der
    // Datei — dass sie blockweise geholt wird, bleibt intern.
    if (range && media.total !== undefined) {
      reply.code(206);
      reply.header("content-range", `bytes ${start}-${media.total - 1}/${media.total}`);
    } else {
      reply.code(200);
    }
    if (media.total !== undefined) reply.header("content-length", String(media.total - start));
    reply.header("accept-ranges", "bytes");
    const contentType = media.first.headers.get("content-type");
    if (contentType) reply.header("content-type", contentType);

    // Fehler ab hier dürfen NIE in Fastifys Error-Pipeline laufen: die Header
    // sind gesendet, ein nachgeschobenes reply.send() löst die
    // ERR_HTTP_HEADERS_SENT-Kaskade aus und riss den Prozess (18.08.).
    // Socket kappen — der Client erkennt den Abbruch selbst.
    const guarded = async function* () {
      try {
        yield* media.chunks;
      } catch (err) {
        reply.log.warn(
          { kind, err: err instanceof Error ? err.message : String(err) },
          "stream endgültig abgebrochen",
        );
        reply.raw.destroy();
      }
    };
    return reply.send(Readable.from(guarded()));
  }

  // Guard: falls doch schon ein Stream lief (Header raus), weder warnen noch
  // einen 502 hinterherschicken.
  if (reply.sent) return reply;
  reply.log.warn({ kind, resolved, failures }, "media proxy failed");
  return reply
    .code(502)
    .send({ error: resolved ? `upstream ${kind} fetch failed` : `${kind} extraction failed` });
}
