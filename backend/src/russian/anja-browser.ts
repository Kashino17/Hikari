/**
 * Anja ohne Mac (Bauplan 18.11.2026): dieselbe Gesprächssteuerung wie auf dem Mac (anja-live.ts),
 * gebündelt für die WebView der App. Ist der Mac nicht erreichbar, aber Internet da, spricht das
 * Handy damit direkt mit Google — mit dem Schlüssel, der verschlüsselt auf dem Gerät liegt.
 *
 * handle() beantwortet dieselben Wege wie src/api/russian-anja-live.ts, als fetch-Response — die
 * Oberfläche merkt keinen Unterschied. Gebaut mit scripts/build-anja-browser.mjs.
 */
import { AnjaLive, askInput, liveInput, liveStart, type LiveSocket } from "./anja-live.js";

export interface LocalStore {
  get(key: string): string | null | undefined;
  set(key: string, value: string): void;
}

const STATUS: Record<string, number> = { no_key: 503, unknown_session: 404, busy: 409 };

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });

export function createLocalAnja(o: {
  key: () => string | null;
  store: LocalStore;
  log?: (msg: string) => void;
}) {
  (globalThis as { __anjaStore?: LocalStore }).__anjaStore = o.store;
  const live = new AnjaLive({
    key: o.key,
    // Eigener Tageszähler auf dem Handy (der Mac zählt seinen eigenen).
    usageFile: "anja_usage_v1",
    connect: (url) => {
      const ws = new WebSocket(url);
      ws.binaryType = "arraybuffer";
      return ws as unknown as LiveSocket;
    },
    request: (url, init) => fetch(url, init),
    log: o.log,
  });
  const ids = new Set<string>();

  const fail = (e: unknown) => {
    const error = e instanceof Error ? e.message : "failed";
    o.log?.(`[anja-direkt] ${error.slice(0, 120)}`);
    return json(STATUS[error] ?? 503, { error: STATUS[error] ? error : "live_unavailable" });
  };

  async function handle(method: string, path: string, body?: unknown): Promise<Response> {
    const [p = "", q = ""] = path.split("?");
    const b = (body ?? {}) as { id?: unknown };
    try {
      if (method === "GET" && p === "/russian/anja/live/status")
        return json(200, { ...live.status(), direct: true });
      if (method === "POST" && p === "/russian/anja/ask") {
        const parsed = askInput.safeParse(b);
        if (!parsed.success) return json(400, { error: "invalid_ask" });
        const r = await live.ask(parsed.data);
        if ((r as { id?: string }).id) ids.add((r as { id: string }).id);
        return json(200, r);
      }
      if (method === "POST" && p === "/russian/anja/live/start") {
        const parsed = liveStart.safeParse(b);
        if (!parsed.success) return json(400, { error: "invalid_start" });
        const r = await live.start(parsed.data);
        ids.add(r.id);
        return json(200, r);
      }
      if (method === "POST" && p === "/russian/anja/live/turn") {
        const parsed = liveInput.safeParse(b);
        if (!parsed.success || typeof b.id !== "string") return json(400, { error: "invalid_turn" });
        return json(200, { turn: await live.send(b.id, parsed.data) });
      }
      if (method === "POST" && p === "/russian/anja/live/end") {
        if (typeof b.id === "string") {
          live.end(b.id);
          ids.delete(b.id);
        }
        return json(200, { ok: true });
      }
      const audio = p.match(/^\/russian\/anja\/live\/audio\/([^/]+)\/(\d+)$/);
      if (method === "GET" && audio) {
        const [, id = "", n = "0"] = audio;
        if (!live.hasTurn(id, Number(n))) return json(404, { error: "unknown_turn" });
        const it = live.audio(id, Number(n));
        const stream = new ReadableStream<Uint8Array>({
          async pull(c) {
            const { done, value } = await it.next();
            if (done) c.close();
            else c.enqueue(new Uint8Array(value.buffer, value.byteOffset, value.length));
          },
        });
        return new Response(stream, {
          status: 200,
          headers: { "content-type": "application/octet-stream", "x-audio-format": "pcm_s16le;rate=24000;channels=1" },
        });
      }
      const turn = p.match(/^\/russian\/anja\/live\/turn\/([^/]+)\/(\d+)$/);
      if (method === "GET" && turn) {
        const w = new URLSearchParams(q).get("wait");
        const wait = w === "heard" || w === "done" || w === "helps" ? w : "none";
        const t = await live.turn(turn[1] ?? "", Number(turn[2]), wait);
        return t ? json(200, t) : json(404, { error: "unknown_turn" });
      }
      return json(404, { error: "not_found" });
    } catch (e) {
      return fail(e);
    }
  }

  return { handle, owns: (id: string) => ids.has(id), status: () => live.status() };
}
