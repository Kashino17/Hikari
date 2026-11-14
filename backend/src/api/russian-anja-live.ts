import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { Readable } from "node:stream";
import type { FastifyInstance } from "fastify";
import { AnjaLive, liveInput, liveStart } from "../russian/anja-live.js";

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
 *   GET  /russian/anja/live/turn/:id/:n    → Mitschrift, Prüfung und Hilfen (?wait=done|helps hält zurück, bis sie da sind)
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
        req.query.wait === "done" || req.query.wait === "helps" ? req.query.wait : "none";
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
