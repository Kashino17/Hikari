/**
 * Höflicher Netzzugriff für die E-Book-Quellen: wenige Anfragen pro Sekunde, Pause
 * nach `retry-after` bei 429/503, höchstens drei Versuche. Eigener User-Agent ohne
 * persönliche Daten (Repo und APK sind öffentlich).
 */
export interface PoliteHttp {
  get(url: string, opts?: { accept?: string }): Promise<{ status: number; body: Buffer }>;
}

export const USER_AGENT = "Hikari-Ebooks/1.0 (private reader)";
const DEFAULT_WAIT_MS = 10 * 60 * 1000;
/** Länger wartet ein Lauf nie am Stück — eine Quelle darf die anderen nicht stundenlang aufhalten. */
const MAX_WAIT_MS = 15 * 60 * 1000;
const TRIES = 3;

export function createPoliteHttp(
  opts: {
    perSecond?: number;
    sleep?: (ms: number) => Promise<void>;
    fetchImpl?: (url: string, init?: RequestInit) => Promise<Response>;
    timeoutMs?: number;
  } = {},
): PoliteHttp {
  const perSecond = opts.perSecond ?? 2;
  const sleep = opts.sleep ?? ((ms: number) => new Promise<void>((r) => setTimeout(r, ms)));
  const fetchImpl = opts.fetchImpl ?? ((u: string, i?: RequestInit) => fetch(u, i));
  const gap = 1000 / perSecond;
  let next = 0;

  async function slot(): Promise<void> {
    const now = Date.now();
    const wait = Math.max(0, next - now);
    next = Math.max(now, next) + gap;
    if (wait > 0) await sleep(wait);
  }

  return {
    async get(url, o) {
      let last = 0;
      for (let attempt = 1; attempt <= TRIES; attempt++) {
        await slot();
        const res = await fetchImpl(url, {
          headers: { "user-agent": USER_AGENT, accept: o?.accept ?? "*/*" },
          redirect: "follow",
          signal: AbortSignal.timeout(opts.timeoutMs ?? 20 * 60 * 1000),
        });
        if (res.status === 429 || res.status === 503) {
          last = res.status;
          await res.arrayBuffer().catch(() => undefined);
          if (attempt === TRIES) break;
          const ra = Number(res.headers.get("retry-after"));
          await sleep(
            Math.min(Number.isFinite(ra) && ra > 0 ? ra * 1000 : DEFAULT_WAIT_MS, MAX_WAIT_MS),
          );
          continue;
        }
        return { status: res.status, body: Buffer.from(await res.arrayBuffer()) };
      }
      throw new Error(`HTTP ${last} nach ${TRIES} Versuchen: ${url}`);
    },
  };
}
