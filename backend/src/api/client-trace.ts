/**
 * Spur aus der App (z. B. Video-Player): Zeilen werden an ~/.hikari/client-trace.log angehängt,
 * damit sich Fehler auf dem Handy nachvollziehen lassen, ohne dass es per Kabel dranhängt.
 * Begrenzt auf 2 MB (danach wird die Datei auf die letzte Hälfte gekürzt).
 */
import { appendFileSync, existsSync, readFileSync, statSync, writeFileSync } from "node:fs";
import type { FastifyInstance } from "fastify";

export function registerClientTraceRoutes(app: FastifyInstance, file: string) {
  app.post<{ Body: { lines?: unknown } }>("/debug/trace", async (req, reply) => {
    const lines = Array.isArray(req.body?.lines) ? req.body.lines.slice(0, 500) : [];
    const text = lines.map((l) => String(l).replace(/[\r\n]+/g, " ").slice(0, 600)).join("\n");
    if (!text) return reply.code(204).send();
    try {
      if (existsSync(file) && statSync(file).size > 2_000_000) {
        const all = readFileSync(file, "utf8");
        writeFileSync(file, all.slice(all.length / 2));
      }
      appendFileSync(file, `${text}\n`);
    } catch {
      /* Spur ist nur Hilfe — nie den Aufrufer stören */
    }
    return reply.code(204).send();
  });
}
