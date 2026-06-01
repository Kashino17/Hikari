import { describe, expect, it } from "vitest";
import Fastify from "fastify";
import { isAuthorized, registerAuth } from "./auth.js";

describe("isAuthorized (policy)", () => {
  it("is open when no token configured", () => {
    expect(isAuthorized("POST", undefined, null)).toBe(true);
    expect(isAuthorized("DELETE", undefined, "")).toBe(true);
  });

  it("allows safe read methods even with a token set", () => {
    expect(isAuthorized("GET", undefined, "secret")).toBe(true);
    expect(isAuthorized("HEAD", undefined, "secret")).toBe(true);
    expect(isAuthorized("OPTIONS", undefined, "secret")).toBe(true);
  });

  it("rejects mutating methods without the bearer token", () => {
    expect(isAuthorized("POST", undefined, "secret")).toBe(false);
    expect(isAuthorized("PUT", "Bearer wrong", "secret")).toBe(false);
    expect(isAuthorized("DELETE", "secret", "secret")).toBe(false); // missing "Bearer "
  });

  it("accepts mutating methods with the correct bearer token", () => {
    expect(isAuthorized("POST", "Bearer secret", "secret")).toBe(true);
    expect(isAuthorized("patch", "Bearer secret", "secret")).toBe(true); // case-insensitive method
  });
});

describe("registerAuth (hook)", () => {
  it("does nothing when token is null — POST passes through", async () => {
    const app = Fastify();
    registerAuth(app, { token: null });
    app.post("/x", async () => ({ ok: true }));
    const res = await app.inject({ method: "POST", url: "/x" });
    expect(res.statusCode).toBe(200);
  });

  it("401s an unauthenticated POST when a token is set", async () => {
    const app = Fastify();
    registerAuth(app, { token: "secret" });
    app.post("/x", async () => ({ ok: true }));
    const res = await app.inject({ method: "POST", url: "/x" });
    expect(res.statusCode).toBe(401);
  });

  it("lets a GET through unauthenticated even with a token set", async () => {
    const app = Fastify();
    registerAuth(app, { token: "secret" });
    app.get("/x", async () => ({ ok: true }));
    const res = await app.inject({ method: "GET", url: "/x" });
    expect(res.statusCode).toBe(200);
  });

  it("accepts a POST carrying the correct bearer token", async () => {
    const app = Fastify();
    registerAuth(app, { token: "secret" });
    app.post("/x", async () => ({ ok: true }));
    const res = await app.inject({
      method: "POST",
      url: "/x",
      headers: { authorization: "Bearer secret" },
    });
    expect(res.statusCode).toBe(200);
  });
});
