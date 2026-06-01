import { describe, expect, it } from "vitest";
import Fastify from "fastify";
import { resolveCorsOrigin, registerCors } from "./cors.js";

describe("resolveCorsOrigin (policy)", () => {
  it("returns null when the allowlist is empty (CORS disabled)", () => {
    expect(resolveCorsOrigin("https://app.example.com", [])).toBeNull();
  });

  it("returns null for a non-browser request (no Origin header)", () => {
    expect(resolveCorsOrigin(undefined, ["https://app.example.com"])).toBeNull();
  });

  it("reflects an allowlisted origin", () => {
    expect(resolveCorsOrigin("https://app.example.com", ["https://app.example.com"])).toBe(
      "https://app.example.com",
    );
  });

  it("rejects an origin not on the allowlist", () => {
    expect(resolveCorsOrigin("https://evil.com", ["https://app.example.com"])).toBeNull();
  });

  it("echoes any origin when '*' is allowlisted", () => {
    expect(resolveCorsOrigin("https://whatever.com", ["*"])).toBe("https://whatever.com");
  });
});

describe("registerCors (hook)", () => {
  it("does nothing when allowlist is empty — no CORS header", async () => {
    const app = Fastify();
    registerCors(app, { origins: [] });
    app.get("/x", async () => ({ ok: true }));
    const res = await app.inject({
      method: "GET",
      url: "/x",
      headers: { origin: "https://app.example.com" },
    });
    expect(res.headers["access-control-allow-origin"]).toBeUndefined();
    expect(res.statusCode).toBe(200);
  });

  it("sets the CORS header for an allowlisted origin", async () => {
    const app = Fastify();
    registerCors(app, { origins: ["https://app.example.com"] });
    app.get("/x", async () => ({ ok: true }));
    const res = await app.inject({
      method: "GET",
      url: "/x",
      headers: { origin: "https://app.example.com" },
    });
    expect(res.headers["access-control-allow-origin"]).toBe("https://app.example.com");
  });

  it("omits the header for a non-allowlisted origin", async () => {
    const app = Fastify();
    registerCors(app, { origins: ["https://app.example.com"] });
    app.get("/x", async () => ({ ok: true }));
    const res = await app.inject({
      method: "GET",
      url: "/x",
      headers: { origin: "https://evil.com" },
    });
    expect(res.headers["access-control-allow-origin"]).toBeUndefined();
  });

  it("answers a preflight OPTIONS with 204 + headers", async () => {
    const app = Fastify();
    registerCors(app, { origins: ["https://app.example.com"] });
    app.post("/x", async () => ({ ok: true }));
    const res = await app.inject({
      method: "OPTIONS",
      url: "/x",
      headers: { origin: "https://app.example.com" },
    });
    expect(res.statusCode).toBe(204);
    expect(res.headers["access-control-allow-origin"]).toBe("https://app.example.com");
  });
});
