import { describe, expect, it } from "vitest";
import { loadConfig } from "./config.js";

describe("loadConfig", () => {
  it("reads env with sensible defaults", () => {
    const cfg = loadConfig({
      HOME: "/home/k",
      ANTHROPIC_API_KEY: "sk-test",
    });
    expect(cfg.port).toBe(3000);
    expect(cfg.dailyBudget).toBe(15);
    expect(cfg.diskLimitBytes).toBe(10 * 1024 ** 3);
    expect(cfg.dataDir).toBe("/home/k/.hikari");
    expect(cfg.llmProvider).toBe("claude");
  });

  it("honors PORT and DAILY_BUDGET override", () => {
    const cfg = loadConfig({ HOME: "/h", ANTHROPIC_API_KEY: "sk-test", PORT: "4000", DAILY_BUDGET: "7" });
    expect(cfg.port).toBe(4000);
    expect(cfg.dailyBudget).toBe(7);
  });

  it("throws when LLM_PROVIDER=claude but no ANTHROPIC_API_KEY", () => {
    expect(() => loadConfig({ HOME: "/h", LLM_PROVIDER: "claude" })).toThrow(/ANTHROPIC_API_KEY/);
  });
});
