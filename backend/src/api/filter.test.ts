import Database from "better-sqlite3";
import Fastify from "fastify";
import { beforeEach, describe, expect, it } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import { DEFAULT_FILTER } from "../scorer/filter.js";
import { registerFilterRoutes } from "./filter.js";

async function makeApp() {
  const db = new Database(":memory:");
  applyMigrations(db);
  const app = Fastify();
  await registerFilterRoutes(app, { db });
  return { app, db };
}

describe("GET /filter", () => {
  it("seeds DEFAULT_FILTER on first call and returns the assembled prompt", async () => {
    const { app } = await makeApp();
    const res = await app.inject({ method: "GET", url: "/filter" });
    expect(res.statusCode).toBe(200);
    const body = res.json();
    expect(body.filter).toEqual(DEFAULT_FILTER);
    expect(body.promptOverride).toBeNull();
    expect(body.assembledPrompt).toMatch(/Du bist Hikaris/);
  });
});

describe("PUT /filter", () => {
  let app: Awaited<ReturnType<typeof makeApp>>["app"];
  beforeEach(async () => {
    ({ app } = await makeApp());
  });

  it("persists a new filter and re-renders the assembled prompt", async () => {
    const next = { ...DEFAULT_FILTER, likeTags: ["Quantum", "Linguistik"] };
    const res = await app.inject({ method: "PUT", url: "/filter", payload: { filter: next } });
    expect(res.statusCode).toBe(200);
    expect(res.json().filter.likeTags).toEqual(["Quantum", "Linguistik"]);
    expect(res.json().assembledPrompt).toMatch(/Quantum, Linguistik/);
  });

  it("400s on malformed filter shape", async () => {
    const res = await app.inject({
      method: "PUT",
      url: "/filter",
      payload: { filter: { likeTags: 42 } },
    });
    expect(res.statusCode).toBe(400);
  });

  it("sets and clears promptOverride", async () => {
    const setRes = await app.inject({
      method: "PUT",
      url: "/filter",
      payload: { promptOverride: "MANUAL PROMPT" },
    });
    expect(setRes.json().promptOverride).toBe("MANUAL PROMPT");
    expect(setRes.json().assembledPrompt).toBe("MANUAL PROMPT");

    const clearRes = await app.inject({
      method: "PUT",
      url: "/filter",
      payload: { promptOverride: null },
    });
    expect(clearRes.json().promptOverride).toBeNull();
    expect(clearRes.json().assembledPrompt).toMatch(/Du bist Hikaris/);
  });

  it("rejects oversized override", async () => {
    const res = await app.inject({
      method: "PUT",
      url: "/filter",
      payload: { promptOverride: "x".repeat(60_000) },
    });
    expect(res.statusCode).toBe(400);
  });
});
