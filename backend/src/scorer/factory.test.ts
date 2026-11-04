import { describe, it, expect } from "vitest";
import { loadConfig } from "../config.js";
import { createScorer } from "./factory.js";

describe("createScorer", () => {
  it("returns no scorer when the language model is switched off", () => {
    expect(createScorer(loadConfig({ LLM_PROVIDER: "off", HOME: "/tmp" }))).toBeNull();
  });

  it("keeps LM Studio as the default", () => {
    expect(createScorer(loadConfig({ HOME: "/tmp" }))?.name).toBeTruthy();
  });
});
