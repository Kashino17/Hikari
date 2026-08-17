import { mkdtempSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { expect, test } from "vitest";
import {
  type CacheEntry,
  cacheGet,
  cachePut,
  dedupInflight,
  loadStreamCache,
  saveStreamCacheSync,
} from "../../src/stream/url-cache.js";

test("cacheGet liefert Treffer innerhalb der TTL und räumt abgelaufene auf", () => {
  const map = new Map<string, CacheEntry<string>>();
  cachePut(map, "a", "url-a", 1000);
  expect(cacheGet(map, "a", 500, 1400)).toBe("url-a");
  expect(cacheGet(map, "a", 500, 1600)).toBeUndefined();
  expect(map.size).toBe(0); // abgelaufener Eintrag entfernt
});

test("cachePut verdrängt den ältesten Eintrag bei maxEntries", () => {
  const map = new Map<string, CacheEntry<string>>();
  cachePut(map, "a", "1", 0, 2);
  cachePut(map, "b", "2", 0, 2);
  cachePut(map, "c", "3", 0, 2);
  expect(map.has("a")).toBe(false);
  expect(map.has("c")).toBe(true);
});

test("dedupInflight teilt ein laufendes Promise und räumt danach auf", async () => {
  const map = new Map<string, Promise<string>>();
  let calls = 0;
  const run = () => {
    calls++;
    return Promise.resolve("x");
  };
  const [r1, r2] = await Promise.all([dedupInflight(map, "k", run), dedupInflight(map, "k", run)]);
  expect([r1, r2]).toEqual(["x", "x"]);
  expect(calls).toBe(1);
  expect(map.size).toBe(0);
});

test("loadStreamCache lädt nur gültige, nicht abgelaufene Einträge; korrupte Datei ⇒ leer", () => {
  const dir = mkdtempSync(join(tmpdir(), "urlcache-"));
  const path = join(dir, "cache.json");
  const fresh = { at: Date.now(), value: "https://ok" };
  const stale = { at: Date.now() - 10 * 60 * 60 * 1000, value: "https://alt" };
  writeFileSync(path, JSON.stringify({ fresh, stale, kaputt: { at: "nein" } }));
  const map = loadStreamCache(path, 6 * 60 * 60 * 1000);
  expect([...map.keys()]).toEqual(["fresh"]);
  writeFileSync(path, "{nicht json");
  expect(loadStreamCache(path, 1000).size).toBe(0);
});

test("saveStreamCacheSync schreibt atomar und ist per loadStreamCache lesbar", () => {
  const dir = mkdtempSync(join(tmpdir(), "urlcache-"));
  const path = join(dir, "cache.json");
  const map = new Map<string, CacheEntry<string>>([["v", { at: Date.now(), value: "https://u" }]]);
  saveStreamCacheSync(path, map);
  expect(JSON.parse(readFileSync(path, "utf8")).v.value).toBe("https://u");
  expect(loadStreamCache(path, 60_000).get("v")?.value).toBe("https://u");
});
