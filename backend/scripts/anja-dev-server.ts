/**
 * Entwicklungs-Server für Anja live: Die Anja-Routen laufen mit dem Code aus diesem Arbeitsbaum,
 * alles andere geht unverändert an den echten Hikari-Server (Port 3939). So lässt sich ein neuer
 * Gesprächsablauf in der Vorschau ausprobieren, ohne den laufenden Server anzufassen.
 *
 *   cd backend && PORT=3942 ./node_modules/.bin/tsx scripts/anja-dev-server.ts
 *   HIKARI_BACKEND=http://127.0.0.1:3942 PORT=3091 node ../tools/russian_web/server.mjs
 */
import { request } from "node:http";
import { homedir } from "node:os";
import { join } from "node:path";
import Fastify from "fastify";
import { registerRussianAnjaLiveRoutes } from "../src/api/russian-anja-live.js";

const PORT = Number(process.env.PORT ?? 3942);
const REAL = { host: "127.0.0.1", port: 3939 };

const app = Fastify({ bodyLimit: 12 * 1024 * 1024 });
await registerRussianAnjaLiveRoutes(app, {
  dir: join(homedir(), ".hikari", "erleben"),
  log: (m) => console.log(m),
});

// Alles, was nicht Anja live ist: an den echten Server durchreichen.
app.setNotFoundHandler((req, reply) => {
  const up = request(
    { ...REAL, method: req.method, path: req.url, headers: { ...req.headers, host: `${REAL.host}:${REAL.port}` } },
    (res) => {
      reply.raw.writeHead(res.statusCode ?? 502, res.headers);
      res.pipe(reply.raw);
    },
  );
  up.on("error", () => reply.code(502).send({ error: "server" }));
  if (req.body != null) up.end(typeof req.body === "string" ? req.body : JSON.stringify(req.body));
  else up.end();
});

await app.listen({ port: PORT, host: "127.0.0.1" });
console.log(`Anja-Entwicklung auf ${PORT}, sonst echter Server auf ${REAL.port}`);
