import { test, expect } from "vitest";
import { adapters, getAdapter } from "../../src/manga/sources/index.js";

test("registry exposes onepiecetube adapter", () => {
  expect(adapters.length).toBeGreaterThan(0);
  expect(adapters.find((a) => a.id === "onepiecetube")).toBeDefined();
});

test("getAdapter returns adapter by id, undefined for unknown", () => {
  expect(getAdapter("onepiecetube")).toBeDefined();
  expect(getAdapter("nonsense")).toBeUndefined();
});
