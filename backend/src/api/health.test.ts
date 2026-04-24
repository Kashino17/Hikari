import Database from "better-sqlite3";
import Fastify from "fastify";
import { describe, expect, it, vi } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import { registerHealthRoute } from "./health.js";

vi.mock("../yt-dlp/client.js", () => ({
  runYtDlp: vi.fn().mockResolvedValue({ stdout: "2026.04.01\n", stderr: "" }),
  YtDlpError: class extends Error {},
}));

describe("health API", () => {
  it("returns ok with yt-dlp version and db status", async () => {
    const db = new Database(":memory:");
    applyMigrations(db);

    const app = Fastify();
    await registerHealthRoute(app, { db, videoDir: "/tmp/hikari-test" });
    const res = await app.inject({ method: "GET", url: "/health" });
    expect(res.statusCode).toBe(200);
    const body = res.json() as { status: string; ytDlpVersion: string };
    expect(body.status).toBe("ok");
    expect(body.ytDlpVersion).toBe("2026.04.01");
  });
});
