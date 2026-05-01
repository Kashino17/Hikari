import { describe, expect, it } from "vitest";
import { loadConfig } from "./config.js";

describe("loadConfig", () => {
  it("defaults to lmstudio provider with no API key required", () => {
    const cfg = loadConfig({ HOME: "/home/k" });
    expect(cfg.port).toBe(3939);
    expect(cfg.dailyBudget).toBe(15);
    expect(cfg.diskLimitBytes).toBe(10 * 1024 ** 3);
    expect(cfg.dataDir).toBe("/home/k/.hikari");
    expect(cfg.llmProvider).toBe("lmstudio");
    expect(cfg.lmstudio.baseUrl).toBe("http://localhost:1234");
    expect(cfg.lmstudio.model).toBe("qwen3-27b");
  });

  it("honors PORT and DAILY_BUDGET override", () => {
    const cfg = loadConfig({ HOME: "/h", PORT: "4000", DAILY_BUDGET: "7" });
    expect(cfg.port).toBe(4000);
    expect(cfg.dailyBudget).toBe(7);
  });

  it("honors LMSTUDIO_URL and LMSTUDIO_MODEL overrides", () => {
    const cfg = loadConfig({
      HOME: "/h",
      LMSTUDIO_URL: "http://192.168.1.50:1234",
      LMSTUDIO_MODEL: "qwen3-32b-instruct",
    });
    expect(cfg.lmstudio.baseUrl).toBe("http://192.168.1.50:1234");
    expect(cfg.lmstudio.model).toBe("qwen3-32b-instruct");
  });

  it("throws when LLM_PROVIDER=claude but no ANTHROPIC_API_KEY", () => {
    expect(() => loadConfig({ HOME: "/h", LLM_PROVIDER: "claude" })).toThrow(/ANTHROPIC_API_KEY/);
  });

  it("accepts claude when ANTHROPIC_API_KEY provided", () => {
    const cfg = loadConfig({ HOME: "/h", LLM_PROVIDER: "claude", ANTHROPIC_API_KEY: "sk-test" });
    expect(cfg.llmProvider).toBe("claude");
  });

  it("exposes mangaDir under HIKARI_DATA_DIR", () => {
    const cfg = loadConfig({ HIKARI_DATA_DIR: "/tmp/hikari-test" });
    expect(cfg.mangaDir).toBe("/tmp/hikari-test/manga");
  });
});
