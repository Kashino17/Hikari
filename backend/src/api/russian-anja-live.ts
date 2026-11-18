import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { Readable } from "node:stream";
import type { FastifyInstance } from "fastify";
import { AnjaLive, askInput, liveInput, liveStart } from "../russian/anja-live.js";

export interface AnjaLiveRouteDeps {
  /** Ordner von »Lieder & Szenen« — dort liegen Schlüssel (keys.env) und Tageszähler (usage.json). */
  dir: string;
  log?: (msg: string) => void;
  live?: AnjaLive;
}

/** Der Gemini-Schlüssel: aus der Umgebung oder aus keys.env (dort hat Kadir ihn hinterlegt). */
export function readGeminiKey(dir: string, env: NodeJS.ProcessEnv = process.env): string | null {
  if (env.GEMINI_API_KEY?.trim()) return env.GEMINI_API_KEY.trim();
  const file = join(dir, "keys.env");
  if (!existsSync(file)) return null;
  const line = readFileSync(file, "utf8")
    .split("\n")
    .find((l) => l.trim().startsWith("GEMINI_API_KEY="));
  const value = line
    ?.split("=")
    .slice(1)
    .join("=")
    .trim()
    .replace(/^["']|["']$/g, "");
  return value || null;
}

/**
 * Nur aus dem eigenen Netz: Mac selbst, Heimnetz, Tailscale (100.64.0.0/10, fd7a:115c:a1e0::/48),
 * Emulator. Der Schlüssel für »Anja ohne Mac« geht nie an eine Adresse von außen.
 */
export function privateAddress(ip: string | undefined): boolean {
  if (!ip) return false;
  const v4 = ip.startsWith("::ffff:") ? ip.slice(7) : ip;
  const m = v4.match(/^(\d+)\.(\d+)\.\d+\.\d+$/);
  if (m) {
    const a = Number(m[1]);
    const b = Number(m[2]);
    return (
      a === 127 ||
      a === 10 ||
      (a === 192 && b === 168) ||
      (a === 172 && b >= 16 && b <= 31) ||
      (a === 100 && b >= 64 && b <= 127)
    );
  }
  const low = ip.toLowerCase();
  return low === "::1" || low.startsWith("fd7a:115c:a1e0:");
}

const STATUS: Record<string, number> = {
  no_key: 503,
  unknown_session: 404,
  busy: 409,
};

/**
 * Anja live: gesprochenes Gespräch über Gemini Live.
 *   POST /russian/anja/live/start          → { id, turn }      Anja eröffnet
 *   POST /russian/anja/live/turn           → { turn }          Antwort des Lernenden (Ton oder Text) / »nochmal« / »einfacher«
 *   GET  /russian/anja/live/audio/:id/:n   → Anjas Ton als Strom (24 kHz, 16 Bit, mono), während er entsteht
 *   POST /russian/anja/ask                → Nachfrage: geprüfte Antwort {text, verified, id, turn} (vorgelesen über audio)
 *   GET  /russian/anja/live/turn/:id/:n    → Mitschrift, Prüfung und Hilfen (?wait=heard|done|helps hält zurück, bis es so weit ist)
 *   POST /russian/anja/live/end            → Gespräch schließen
 */
export async function registerRussianAnjaLiveRoutes(app: FastifyInstance, deps: AnjaLiveRouteDeps) {
  const live =
    deps.live ??
    new AnjaLive({
      key: () => readGeminiKey(deps.dir),
      usageFile: join(deps.dir, "usage.json"),
      voice: process.env.ANJA_LIVE_VOICE,
      log: deps.log,
    });

  const fail = (reply: { code: (n: number) => { send: (b: unknown) => unknown } }, e: unknown) => {
    const error = e instanceof Error ? e.message : "failed";
    // Keine Mitschriften und keine Antworten des Anbieters im Protokoll — nur der Grund.
    deps.log?.(`[anja-live] ${error.slice(0, 120)}`);
    return reply
      .code(STATUS[error] ?? 503)
      .send({ error: STATUS[error] ? error : "live_unavailable" });
  };

  app.get("/russian/anja/live/status", () => live.status());

  // Anja ohne Mac (Bauplan 18.11.2026): Das Handy holt sich den Schlüssel, solange es den Mac
  // erreicht, und legt ihn verschlüsselt im Android-Schlüsselspeicher ab. Im APK steht nichts —
  // Repo und Releases sind öffentlich.
  app.get("/russian/anja/key", (req, reply) => {
    if (!privateAddress(req.ip)) return reply.code(403).send({ error: "forbidden" });
    const key = readGeminiKey(deps.dir);
    if (!key) return reply.code(404).send({ error: "no_key" });
    return reply.header("cache-control", "no-store").send({ key });
  });

  // Nachfrage aus der Grammatik: geprüfte Antwort als Text, vorgelesen über /audio/:id/:n.
  app.post("/russian/anja/ask", async (req, reply) => {
    const parsed = askInput.safeParse(req.body ?? {});
    if (!parsed.success) return reply.code(400).send({ error: "invalid_ask" });
    try {
      return await live.ask(parsed.data);
    } catch (e) {
      return fail(reply, e);
    }
  });

  app.post("/russian/anja/live/start", { bodyLimit: 120_000 }, async (req, reply) => {
    const parsed = liveStart.safeParse(req.body ?? {});
    if (!parsed.success) return reply.code(400).send({ error: "invalid_start" });
    try {
      return await live.start(parsed.data);
    } catch (e) {
      return fail(reply, e);
    }
  });

  app.post("/russian/anja/live/turn", { bodyLimit: 8_000_000 }, async (req, reply) => {
    const body = (req.body ?? {}) as { id?: unknown };
    const parsed = liveInput.safeParse(req.body);
    if (!parsed.success || typeof body.id !== "string")
      return reply.code(400).send({ error: "invalid_turn" });
    try {
      return { turn: await live.send(body.id, parsed.data) };
    } catch (e) {
      return fail(reply, e);
    }
  });

  app.get<{ Params: { id: string; n: string } }>(
    "/russian/anja/live/audio/:id/:n",
    (req, reply) => {
      const n = Number(req.params.n);
      if (!live.hasTurn(req.params.id, n)) return reply.code(404).send({ error: "unknown_turn" });
      return reply
        .header("content-type", "application/octet-stream")
        .header("cache-control", "no-store")
        .header("x-audio-format", "pcm_s16le;rate=24000;channels=1")
        .send(Readable.from(live.audio(req.params.id, n)));
    },
  );

  app.get<{ Params: { id: string; n: string }; Querystring: { wait?: string } }>(
    "/russian/anja/live/turn/:id/:n",
    async (req, reply) => {
      const wait =
        req.query.wait === "heard" || req.query.wait === "done" || req.query.wait === "helps"
          ? req.query.wait
          : "none";
      const turn = await live.turn(req.params.id, Number(req.params.n), wait);
      if (!turn) return reply.code(404).send({ error: "unknown_turn" });
      return reply.header("cache-control", "no-store").send(turn);
    },
  );

  app.post("/russian/anja/live/end", async (req) => {
    const id = (req.body as { id?: unknown } | null)?.id;
    if (typeof id === "string") live.end(id);
    return { ok: true };
  });
}
