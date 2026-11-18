import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import Fastify from "fastify";
import { describe, expect, it } from "vitest";
import { AnjaLive } from "../russian/anja-live.js";
import { privateAddress, registerRussianAnjaLiveRoutes } from "./russian-anja-live.js";

describe("privateAddress", () => {
  it("lässt nur das eigene Netz durch", () => {
    for (const ip of ["127.0.0.1", "::1", "::ffff:127.0.0.1", "10.0.2.2", "192.168.1.20", "172.20.0.5", "100.101.5.7", "fd7a:115c:a1e0::12"])
      expect(privateAddress(ip), ip).toBe(true);
    for (const ip of ["8.8.8.8", "100.128.0.1", "172.32.0.1", "2a00:1450::1", "::ffff:93.184.216.34", "", undefined])
      expect(privateAddress(ip), String(ip)).toBe(false);
  });
});

describe("GET /russian/anja/key", () => {
  async function server(withKey: boolean) {
    const dir = mkdtempSync(join(tmpdir(), "anja-key-"));
    if (withKey) writeFileSync(join(dir, "keys.env"), 'GEMINI_API_KEY="test-schluessel"\n');
    const app = Fastify();
    const live = new AnjaLive({ key: () => null, usageFile: join(dir, "usage.json") });
    await registerRussianAnjaLiveRoutes(app, { dir, live });
    return app;
  }

  it("gibt den Schlüssel ans eigene Netz, nie zwischengespeichert", async () => {
    const app = await server(true);
    const r = await app.inject({ method: "GET", url: "/russian/anja/key", remoteAddress: "100.90.1.2" });
    expect(r.statusCode).toBe(200);
    expect(r.json()).toEqual({ key: "test-schluessel" });
    expect(r.headers["cache-control"]).toBe("no-store");
  });

  it("verweigert Anfragen von außen", async () => {
    const app = await server(true);
    const r = await app.inject({ method: "GET", url: "/russian/anja/key", remoteAddress: "93.184.216.34" });
    expect(r.statusCode).toBe(403);
    expect(r.body).not.toContain("test-schluessel");
  });

  it("meldet, wenn auf dem Mac kein Schlüssel liegt", async () => {
    const app = await server(false);
    const r = await app.inject({ method: "GET", url: "/russian/anja/key", remoteAddress: "127.0.0.1" });
    expect(r.statusCode).toBe(404);
  });
});
