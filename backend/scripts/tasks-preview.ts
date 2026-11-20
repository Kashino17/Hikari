/**
 * Vorschau der Aufgaben-Oberfläche ohne App-Build und ohne den Hikari-Server anzufassen:
 * startet die echten Aufgaben-Routen (backend/src/api/tasks.ts) mit eigenem Datenordner
 * (~/.hikari/tasks-preview) und liefert assets/tasks-web aus. Qwen über LM Studio wie im Server.
 *
 *   cd backend && node --import tsx scripts/tasks-preview.ts   →  http://localhost:3954/
 *   ?page=kinds usw. zeigt eine Unterseite wie in der App.
 */
import { readFile } from "node:fs/promises";
import { homedir } from "node:os";
import { extname, join } from "node:path";
import fastifyMultipart from "@fastify/multipart";
import Fastify from "fastify";
import { registerTaskRoutes } from "../src/api/tasks.js";
import { CaptureProcessor } from "../src/tasks/capture.js";
import { TaskParser, fallbackParse } from "../src/tasks/parse.js";
import { TaskStore } from "../src/tasks/store.js";

const PORT = Number(process.env.PORT ?? 3954);
const ASSETS = new URL("../../android/app/src/main/assets/tasks-web/", import.meta.url).pathname;
const store = new TaskStore(join(homedir(), ".hikari", "tasks-preview"));
const parser = new TaskParser({
  baseUrl: process.env.LMSTUDIO_URL ?? "http://127.0.0.1:1234",
  model: process.env.VOICE_LLM_MODEL ?? "qwen/qwen3.8-27b",
  ttlSeconds: 600,
});
const processor = new CaptureProcessor({
  store,
  transcribe: async () => {
    throw new Error("Vorschau: nur Text");
  },
  parse: (t, c) => parser.parse(t, c),
  fallback: fallbackParse,
  log: (m, x) => console.log(m, x ?? ""),
});

const app = Fastify();
await app.register(fastifyMultipart);
await app.register(
  async (api) => {
    await registerTaskRoutes(api, { store, processor, parser });
  },
  { prefix: "/api" },
);
const TYPES: Record<string, string> = { ".html": "text/html; charset=utf-8", ".js": "text/javascript", ".css": "text/css" };
app.get("/*", async (req, reply) => {
  const path = (req.url.split("?")[0] ?? "/").replace(/^\/+/, "") || "index.html";
  if (path.includes("..")) return reply.code(400).send();
  try {
    const body = await readFile(join(ASSETS, path));
    return reply.type(TYPES[extname(path)] ?? "application/octet-stream").header("cache-control", "no-store").send(body);
  } catch {
    return reply.code(404).send();
  }
});
await app.listen({ port: PORT, host: "0.0.0.0" });
console.log(`Aufgaben-Vorschau: http://localhost:${PORT}/`);
