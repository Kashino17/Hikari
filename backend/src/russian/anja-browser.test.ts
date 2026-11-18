import { spawnSync } from "node:child_process";
import { mkdtempSync, readFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import { createLocalAnja } from "./anja-browser.js";

const backend = fileURLToPath(new URL("../..", import.meta.url));

describe("Anja ohne Mac (Bündel fürs Handy)", () => {
  it("das Bündel in der App passt zum Code — sonst: node scripts/build-anja-browser.mjs", () => {
    const out = join(mkdtempSync(join(tmpdir(), "anja-local-")), "anja-local.js");
    const r = spawnSync(process.execPath, ["scripts/build-anja-browser.mjs"], {
      cwd: backend,
      env: { ...process.env, ANJA_LOCAL_OUT: out },
      encoding: "utf8",
    });
    expect(r.status, r.stderr).toBe(0);
    const shipped = readFileSync(join(backend, "../android/app/src/main/assets/russian-web/vendor/anja-local.js"), "utf8");
    expect(readFileSync(out, "utf8") === shipped).toBe(true);
  }, 30_000);

  it("antwortet auf dieselben Wege wie der Mac", async () => {
    const mem = new Map<string, string>();
    const a = createLocalAnja({ key: () => null, store: { get: (k) => mem.get(k), set: (k, v) => void mem.set(k, v) } });
    const status = await a.handle("GET", "/russian/anja/live/status");
    expect(status.status).toBe(200);
    expect(await status.json()).toMatchObject({ available: false, direct: true });
    expect((await a.handle("POST", "/russian/anja/live/start", {})).status).toBe(503);
    expect((await a.handle("POST", "/russian/anja/live/turn", { kind: "text" })).status).toBe(400);
    expect((await a.handle("GET", "/russian/anja/live/turn/nix/1?wait=done")).status).toBe(404);
    expect((await a.handle("GET", "/russian/anja/live/audio/nix/1")).status).toBe(404);
    expect(await (await a.handle("POST", "/russian/anja/live/end", { id: "nix" })).json()).toEqual({ ok: true });
  });
});
