import { readFileSync, renameSync, writeFileSync } from "node:fs";
import { rename, writeFile } from "node:fs/promises";

/**
 * Gemeinsame Cache-/Dedup-Bausteine für Stream-URL-Auflösung — extrahiert aus
 * api/music.ts, damit Musik- und Video-Streaming dieselbe Mechanik teilen.
 */

const DEFAULT_MAX_ENTRIES = 200;

export interface CacheEntry<T> {
  at: number;
  value: T;
}

export function cacheGet<T>(
  map: Map<string, CacheEntry<T>>,
  key: string,
  ttlMs: number,
  now: number,
): T | undefined {
  const hit = map.get(key);
  if (hit && now - hit.at < ttlMs) return hit.value;
  if (hit) map.delete(key);
  return undefined;
}

export function cachePut<T>(
  map: Map<string, CacheEntry<T>>,
  key: string,
  value: T,
  now: number,
  maxEntries: number = DEFAULT_MAX_ENTRIES,
): void {
  if (map.size >= maxEntries) {
    const oldest = map.keys().next().value;
    if (oldest !== undefined) map.delete(oldest);
  }
  map.set(key, { at: now, value });
}

/**
 * In-Flight-Dedup: gleichzeitige identische Anfragen (Prefetch + Play,
 * doppelt gerenderte App-Screens) teilen sich ein laufendes Promise.
 */
export async function dedupInflight<T>(
  map: Map<string, Promise<T>>,
  key: string,
  run: () => Promise<T>,
): Promise<T> {
  const existing = map.get(key);
  if (existing) return existing;
  const pending = run();
  map.set(key, pending);
  try {
    return await pending;
  } finally {
    if (map.get(key) === pending) map.delete(key);
  }
}

/** Lädt den persistierten Stream-URL-Cache; abgelaufene Einträge werden verworfen. */
export function loadStreamCache(
  path: string | undefined,
  ttlMs: number,
  maxEntries: number = DEFAULT_MAX_ENTRIES,
): Map<string, CacheEntry<string>> {
  const map = new Map<string, CacheEntry<string>>();
  if (!path) return map;
  try {
    const raw = JSON.parse(readFileSync(path, "utf8")) as Record<string, CacheEntry<string>>;
    const now = Date.now();
    for (const [key, entry] of Object.entries(raw)) {
      if (map.size >= maxEntries) break; // gleiche Obergrenze wie cachePut
      if (
        typeof entry?.at === "number" &&
        typeof entry?.value === "string" &&
        now - entry.at < ttlMs
      ) {
        map.set(key, entry);
      }
    }
  } catch {
    // keine oder korrupte Datei — mit leerem Cache starten
  }
  return map;
}

/** Schreibt den Stream-URL-Cache atomar (tmp + rename) — asynchron, für den Debounce-Timer. */
export async function saveStreamCacheAsync(
  path: string,
  map: Map<string, CacheEntry<string>>,
): Promise<void> {
  try {
    const tmp = `${path}.tmp`;
    await writeFile(tmp, JSON.stringify(Object.fromEntries(map)));
    await rename(tmp, path);
  } catch {
    // Persistenz ist best-effort — ein Schreibfehler darf keinen Request brechen
  }
}

/** Synchrone Variante für Prozess-Shutdown und Server-Close. */
export function saveStreamCacheSync(path: string, map: Map<string, CacheEntry<string>>): void {
  try {
    const tmp = `${path}.tmp`;
    writeFileSync(tmp, JSON.stringify(Object.fromEntries(map)));
    renameSync(tmp, path);
  } catch {
    // Persistenz ist best-effort — ein Schreibfehler darf keinen Request brechen
  }
}
