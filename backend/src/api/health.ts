import { existsSync, statSync, readdirSync } from "node:fs";
import { join } from "node:path";
import type { FastifyInstance } from "fastify";
import type Database from "better-sqlite3";
import { runYtDlp, YtDlpError } from "../yt-dlp/client.js";

export interface HealthDeps {
  db: Database.Database;
  videoDir: string;
}

export async function registerHealthRoute(app: FastifyInstance, deps: HealthDeps): Promise<void> {
  app.get("/health", async () => {
    let ytDlpVersion = "unknown";
    try {
      const { stdout } = await runYtDlp(["--version"], { timeoutMs: 5000 });
      ytDlpVersion = stdout.trim();
    } catch (e) {
      if (e instanceof YtDlpError) ytDlpVersion = "unavailable";
    }

    const dbOk = (() => {
      try {
        deps.db.prepare("SELECT 1").get();
        return true;
      } catch {
        return false;
      }
    })();

    const diskBytes = existsSync(deps.videoDir)
      ? readdirSync(deps.videoDir).reduce(
          (sum, f) => sum + statSync(join(deps.videoDir, f)).size,
          0,
        )
      : 0;

    return {
      status: dbOk && ytDlpVersion !== "unavailable" ? "ok" : "degraded",
      ytDlpVersion,
      dbOk,
      videoDirBytes: diskBytes,
    };
  });
}
