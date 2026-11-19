/**
 * Nur die Fitness-Routen auf eigenem Port (Standard 3940) — zum Entwickeln der Oberfläche,
 * ohne den laufenden Hikari-Server anzufassen. Datenordner: FIT_DIR (Standard ~/.hikari/fitness-dev).
 *   npx tsx scripts/fitness-dev.ts
 */
import { join } from "node:path";
import Fastify from "fastify";
import { registerFitnessRoutes } from "../src/api/fitness.js";
import { readGeminiKey } from "../src/api/russian-anja-live.js";

const home = process.env.HOME ?? "";
const dir = process.env.FIT_DIR ?? join(home, ".hikari", "fitness-dev");
const app = Fastify({ logger: false, bodyLimit: 12 * 1024 * 1024 });
const h = registerFitnessRoutes(app, {
  dir,
  dataDir: new URL("../fitness", import.meta.url).pathname,
  geminiKey: () => readGeminiKey(join(home, ".hikari", "fitness"), {}),
  log: (m) => console.log(m),
});
if (process.env.NO_IMPORT) h.importer.run = async () => {};
await app.listen({ port: Number(process.env.PORT ?? 3940), host: "127.0.0.1" });
console.log(`fitness-dev auf :${process.env.PORT ?? 3940}, Daten ${dir}`);
