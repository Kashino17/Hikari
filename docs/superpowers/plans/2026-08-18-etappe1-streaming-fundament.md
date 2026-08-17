# Etappe 1: Streaming-Fundament — Implementierungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Feed-Videos werden vom Backend live von YouTube proxy-gestreamt (`GET /stream/video/:videoId`) statt aus vorab heruntergeladenen MP4s; die App nutzt den neuen Endpoint; die macOS-„ 2"-Duplikate fliegen raus.

**Architecture:** Neues Modul `backend/src/stream/` nach dem produktionserprobten Musik-Muster: Cache-/Dedup-Helfer und der Range-fähige `proxyMediaStream` werden aus `api/music.ts` dorthin extrahiert (music importiert sie fortan), dazu ein neuer Video-Resolver mit persistentem URL-Cache und eine Fastify-Route mit Datei-Fallback. Android ändert nur den URL-Bau in `HikariPlayerFactory.mediaItemFor`.

**Tech Stack:** Fastify + TypeScript (ESM, **Imports mit `.js`-Endung!**), better-sqlite3, Vitest, yt-dlp via `src/yt-dlp/client.ts`; Android: Kotlin, Media3/ExoPlayer 1.4.1.

**Spec:** `docs/superpowers/specs/2026-08-18-feed-streaming-overhaul-design.md` (Abschnitte 4.1, Etappe 1)

## Global Constraints

- Backend-Tests/Build NUR mit System-Node ausführen (kein nvm/fnm-Override): `cd backend && npm test` bzw. `npm run build`.
- Backend läuft auf `PORT=3939`.
- Codestil: Biome (`backend/biome.json`); Kommentare auf Deutsch im Stil der Nachbardateien; keine Kommentare, die nur die nächste Zeile nacherzählen.
- ESM: relative Imports enden auf `.js`, auch in `.ts`-Dateien.
- `kind == "clip"`-Playback (gerenderte Clips) bleibt unverändert auf `/clips/:id.mp4` — Clips existieren nur auf dem Server, nicht auf YouTube.
- Der statische Mount `/videos/` und der Geräte-Download-Flow (`LocalDownloadManager` lädt `/videos/<id>.mp4`) bleiben unangetastet.

---

### Task 1: macOS-„ 2"-Duplikate löschen

**Files:**
- Delete: alle 37 Dateien `backend/src/**/* 2.ts` (z. B. `backend/src/index 2.ts`, `backend/src/pipeline/orchestrator 2.ts`, `backend/src/api/videos 2.ts`, …)

**Interfaces:**
- Consumes: —
- Produces: sauberer `backend/src`-Baum; `tsc` kompiliert keine Duplikate mehr mit.

- [ ] **Step 1: Duplikate auflisten und löschen**

```bash
cd /Users/ayysir/Desktop/Hikari
find backend/src -name '* 2.*' -print
find backend/src -name '* 2.*' -delete
find backend/src -name '* 2.*' | wc -l   # erwartet: 0
```

Die Dateien sind untracked (`git ls-files` kennt sie nicht) — Löschen erzeugt keinen Diff. Falls doch welche getrackt sind: `git rm` verwenden und committen.

- [ ] **Step 2: Build + Tests verifizieren**

Run: `cd backend && npm run build && npm test`
Expected: `tsc` ohne Fehler, alle Vitest-Suiten PASS (Stand vor dem Umbau).

- [ ] **Step 3: Commit (nur falls getrackte Dateien betroffen waren, sonst entfällt er)**

```bash
git add -A backend/src && git commit -m "chore: restliche macOS-' 2'-Duplikate entfernt"
```

---

### Task 2: `stream/url-cache.ts` — Cache-Helfer extrahieren

**Files:**
- Create: `backend/src/stream/url-cache.ts`
- Modify: `backend/src/api/music.ts` (lokale Kopien entfernen, Import ergänzen)
- Test: `backend/tests/stream/url-cache.test.ts`

**Interfaces:**
- Consumes: bestehende Implementierungen in `api/music.ts` (Zeilen ~121–240): `CacheEntry`, `cacheGet`, `cachePut`, `dedupInflight`, `loadStreamCache`, `saveStreamCacheAsync`, `saveStreamCacheSync`.
- Produces (von Task 3/4 benutzt):

```ts
export interface CacheEntry<T> { at: number; value: T }
export function cacheGet<T>(map: Map<string, CacheEntry<T>>, key: string, ttlMs: number, now: number): T | undefined
export function cachePut<T>(map: Map<string, CacheEntry<T>>, key: string, value: T, now: number, maxEntries?: number): void  // default 200
export function dedupInflight<T>(map: Map<string, Promise<T>>, key: string, run: () => Promise<T>): Promise<T>
export function loadStreamCache(path: string | undefined, ttlMs: number, maxEntries?: number): Map<string, CacheEntry<string>>  // default 200
export function saveStreamCacheAsync(path: string, map: Map<string, CacheEntry<string>>): Promise<void>
export function saveStreamCacheSync(path: string, map: Map<string, CacheEntry<string>>): void
```

- [ ] **Step 1: Failing Test schreiben** (`backend/tests/stream/url-cache.test.ts`)

```ts
import { mkdtempSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { expect, test } from "vitest";
import {
  cacheGet, cachePut, dedupInflight, loadStreamCache, saveStreamCacheSync,
  type CacheEntry,
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
  const run = () => { calls++; return Promise.resolve("x"); };
  const [r1, r2] = await Promise.all([
    dedupInflight(map, "k", run),
    dedupInflight(map, "k", run),
  ]);
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
```

- [ ] **Step 2: Test laufen lassen — muss fehlschlagen**

Run: `cd backend && npx vitest run tests/stream/url-cache.test.ts`
Expected: FAIL — Modul `src/stream/url-cache.ts` existiert nicht.

- [ ] **Step 3: `src/stream/url-cache.ts` anlegen**

Die sieben Symbole **verbatim** aus `api/music.ts` übernehmen (Suchanker: `interface CacheEntry`, `function cacheGet`, `function cachePut`, `async function dedupInflight`, `function loadStreamCache`, `saveStreamCacheAsync`, `saveStreamCacheSync`), mit genau zwei Anpassungen: alle Symbole `export`en und die Konstante `CACHE_MAX_ENTRIES` durch den Parameter `maxEntries = 200` ersetzen (in `cachePut` und `loadStreamCache`). Die `node:fs`-Imports (`readFileSync`, `writeFileSync`, `renameSync`, `writeFile`, `rename`) wandern mit.

- [ ] **Step 4: `api/music.ts` auf den Import umstellen**

Lokale Definitionen der sieben Symbole löschen; oben ergänzen:

```ts
import {
  type CacheEntry, cacheGet, cachePut, dedupInflight,
  loadStreamCache, saveStreamCacheAsync, saveStreamCacheSync,
} from "../stream/url-cache.js";
```

Nicht mehr benötigte `node:fs`-Imports in music.ts entfernen (Biome meckert sonst). `CACHE_MAX_ENTRIES` bleibt als Konstante in music.ts und wird an keiner Stelle mehr gebraucht, falls doch (Suche!), als Argument `maxEntries` durchreichen.

- [ ] **Step 5: Alle Tests + Build**

Run: `cd backend && npm run build && npm test`
Expected: url-cache-Tests PASS, alle bestehenden Musik-Tests weiterhin PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/stream/url-cache.ts backend/src/api/music.ts backend/tests/stream/url-cache.test.ts
git commit -m "refactor(stream): Cache-/Dedup-Helfer aus music.ts nach stream/url-cache.ts extrahiert"
```

---

### Task 3: `stream/proxy.ts` — Range-Proxy extrahieren

**Files:**
- Create: `backend/src/stream/proxy.ts`
- Modify: `backend/src/api/music.ts` (lokalen `proxyMediaStream` entfernen, Import + Options-Aufrufe)
- Test: `backend/tests/stream/proxy.test.ts`

**Interfaces:**
- Consumes: bestehenden `proxyMediaStream` in `api/music.ts` (~Zeile 894) samt Verhalten: Range-Durchreichung, Header-Phase-Timeout, Retry derselben URL bei transienten Fetch-Fehlern, Neuauflösung (force) bei 403/410, 502 am Ende.
- Produces (von Task 4 benutzt):

```ts
export interface ProxyOpts {
  fetchImpl?: typeof fetch;      // default: globalThis.fetch
  headerTimeoutMs?: number;      // default: 12_000
  retryDelaysMs?: number[];      // default: Wert von AUDIO_RETRY_DELAYS_MS aus music.ts (Konstante mit umziehen als DEFAULT_RETRY_DELAYS_MS)
}
export async function proxyMediaStream(
  reply: FastifyReply,
  range: string | undefined,
  resolveUrl: (force: boolean) => Promise<string | undefined>,
  kind: "audio" | "video",
  opts?: ProxyOpts,
): Promise<FastifyReply>
```

- [ ] **Step 1: Failing Test schreiben** (`backend/tests/stream/proxy.test.ts`)

```ts
import Fastify from "fastify";
import { expect, test } from "vitest";
import { proxyMediaStream } from "../../src/stream/proxy.js";

function appWith(
  resolveUrl: (force: boolean) => Promise<string | undefined>,
  fetchImpl: typeof fetch,
) {
  const app = Fastify();
  app.get("/s/:id", (req, reply) =>
    proxyMediaStream(reply, req.headers.range, resolveUrl, "video", {
      fetchImpl, retryDelaysMs: [],
    }),
  );
  return app;
}

test("reicht Range durch und spiegelt 206 + Content-Range", async () => {
  const seen: { range?: string } = {};
  const fetchImpl = (async (_url: unknown, init?: RequestInit) => {
    seen.range = (init?.headers as Record<string, string> | undefined)?.range;
    return new Response("ab", {
      status: 206,
      headers: { "content-range": "bytes 0-1/2", "content-type": "video/mp4" },
    });
  }) as typeof fetch;
  const app = appWith(async () => "https://gv/ok", fetchImpl);
  const res = await app.inject({ url: "/s/x", headers: { range: "bytes=0-1" } });
  expect(seen.range).toBe("bytes=0-1");
  expect(res.statusCode).toBe(206);
  expect(res.headers["content-range"]).toBe("bytes 0-1/2");
});

test("403 vom Upstream ⇒ zweite Auflösung mit force=true", async () => {
  const forces: boolean[] = [];
  const urls = ["https://gv/alt", "https://gv/frisch"];
  const fetchImpl = (async (url: unknown) =>
    new Response(String(url).includes("frisch") ? "ok" : "", {
      status: String(url).includes("frisch") ? 200 : 403,
    })) as typeof fetch;
  const app = appWith(async (force) => { forces.push(force); return urls[forces.length - 1]; }, fetchImpl);
  const res = await app.inject({ url: "/s/x" });
  expect(forces).toEqual([false, true]);
  expect(res.statusCode).toBe(200);
});

test("Auflösung scheitert zweimal ⇒ 502 extraction failed", async () => {
  const app = appWith(async () => undefined, fetch);
  const res = await app.inject({ url: "/s/x" });
  expect(res.statusCode).toBe(502);
  expect(res.json()).toEqual({ error: "video extraction failed" });
});
```

- [ ] **Step 2: Test laufen lassen — muss fehlschlagen**

Run: `cd backend && npx vitest run tests/stream/proxy.test.ts`
Expected: FAIL — Modul existiert nicht.

- [ ] **Step 3: `src/stream/proxy.ts` anlegen**

`proxyMediaStream` verbatim aus music.ts übernehmen; die Closure-Abhängigkeiten werden Options: `fetchImpl` (Default `fetch`), `AUDIO_HEADER_TIMEOUT_MS` → `opts.headerTimeoutMs ?? 12_000`, `retryDelays` → `opts.retryDelaysMs ?? DEFAULT_RETRY_DELAYS_MS` (Konstante `AUDIO_RETRY_DELAYS_MS` mit Wert aus music.ts hierher umziehen und umbenennen), `sleep` lokal definieren. `Readable` aus `node:stream` importieren, `FastifyReply` als type-Import.

- [ ] **Step 4: `api/music.ts` umstellen**

Lokalen `proxyMediaStream` + `AUDIO_HEADER_TIMEOUT_MS` + `AUDIO_RETRY_DELAYS_MS` löschen, importieren:

```ts
import { proxyMediaStream } from "../stream/proxy.js";
```

Beide Aufrufstellen (`/music/audio/:videoId`, `/music/video/:videoId`) bekommen als fünftes Argument `{ fetchImpl, retryDelaysMs: retryDelays }` (die per `MusicDeps` injizierten Werte — Tests nutzen das). `deps.retryDelaysMs`-Doku in `MusicDeps` bleibt.

- [ ] **Step 5: Alle Tests + Build**

Run: `cd backend && npm run build && npm test`
Expected: proxy-Tests PASS, Musik-Tests (insb. Audio-Proxy-Tests) weiterhin PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/stream/proxy.ts backend/src/api/music.ts backend/tests/stream/proxy.test.ts
git commit -m "refactor(stream): proxyMediaStream generalisiert nach stream/proxy.ts extrahiert"
```

---

### Task 4: Video-Resolver + Route `GET /stream/video/:videoId`

**Files:**
- Create: `backend/src/api/stream.ts`
- Modify: `backend/src/index.ts` (Registrierung nahe Zeile 130, neben `registerMusicRoutes`)
- Test: `backend/tests/api/stream.test.ts`

**Interfaces:**
- Consumes: `runYtDlp` aus `../yt-dlp/client.js`; `cacheGet`/`cachePut`/`dedupInflight`/`loadStreamCache`/`saveStreamCacheSync` aus `../stream/url-cache.js`; `proxyMediaStream` aus `../stream/proxy.js`.
- Produces:

```ts
export interface StreamDeps {
  ytDlp?: typeof runYtDlp;
  fetchImpl?: typeof fetch;
  now?: () => number;
  streamCachePath?: string;  // persistenter URL-Cache; ohne Pfad nur In-Memory
  videoDir?: string;         // Fallback: existiert <videoDir>/<id>.mp4, 302 auf /videos/<id>.mp4
  retryDelaysMs?: number[];
}
export function registerStreamRoutes(app: FastifyInstance, deps?: StreamDeps): void
```

- [ ] **Step 1: Failing Test schreiben** (`backend/tests/api/stream.test.ts`)

```ts
import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import Fastify from "fastify";
import { expect, test } from "vitest";
import { registerStreamRoutes, type StreamDeps } from "../../src/api/stream.js";

const VID = "dQw4w9WgXcQ";

function buildApp(deps: StreamDeps) {
  const app = Fastify();
  registerStreamRoutes(app, deps);
  return app;
}

const okFetch = (async () =>
  new Response("bytes", { status: 206, headers: { "content-range": "bytes 0-4/5" } })) as typeof fetch;

test("löst per yt-dlp auf, proxied mit Range und cached die URL (1x yt-dlp für 2 Requests)", async () => {
  let calls = 0;
  const ytDlp = (async () => { calls++; return { stdout: "https://gv/video\n", stderr: "" }; }) as StreamDeps["ytDlp"];
  const app = buildApp({ ytDlp, fetchImpl: okFetch, retryDelaysMs: [] });
  const r1 = await app.inject({ url: `/stream/video/${VID}`, headers: { range: "bytes=0-4" } });
  const r2 = await app.inject({ url: `/stream/video/${VID}` });
  expect(r1.statusCode).toBe(206);
  expect(r2.statusCode).toBe(206);
  expect(calls).toBe(1);
});

test("ungültige videoId ⇒ 400", async () => {
  const app = buildApp({});
  const res = await app.inject({ url: "/stream/video/nix!gut" });
  expect(res.statusCode).toBe(400);
});

test("Manifest-URL (m3u8) wird verworfen ⇒ 502 ohne Datei-Fallback", async () => {
  const ytDlp = (async () => ({ stdout: "https://gv/x.m3u8\n", stderr: "" })) as StreamDeps["ytDlp"];
  const app = buildApp({ ytDlp, fetchImpl: okFetch, retryDelaysMs: [] });
  const res = await app.inject({ url: `/stream/video/${VID}` });
  expect(res.statusCode).toBe(502);
});

test("Auflösung scheitert + Serverdatei existiert ⇒ 302 auf /videos/<id>.mp4", async () => {
  const dir = mkdtempSync(join(tmpdir(), "videos-"));
  writeFileSync(join(dir, `${VID}.mp4`), "x");
  const ytDlp = (async () => { throw new Error("yt-dlp down"); }) as StreamDeps["ytDlp"];
  const app = buildApp({ ytDlp, videoDir: dir, retryDelaysMs: [] });
  const res = await app.inject({ url: `/stream/video/${VID}` });
  expect(res.statusCode).toBe(302);
  expect(res.headers.location).toBe(`/videos/${VID}.mp4`);
});

test("Auflösung scheitert ohne Serverdatei ⇒ 502", async () => {
  const ytDlp = (async () => { throw new Error("yt-dlp down"); }) as StreamDeps["ytDlp"];
  const app = buildApp({ ytDlp, retryDelaysMs: [] });
  const res = await app.inject({ url: `/stream/video/${VID}` });
  expect(res.statusCode).toBe(502);
});
```

Hinweis: Die exakte `runYtDlp`-Rückgabeform vor dem Schreiben in `src/yt-dlp/client.ts` prüfen und die Fakes daran anpassen (music.ts nutzt `result.stdout`).

- [ ] **Step 2: Test laufen lassen — muss fehlschlagen**

Run: `cd backend && npx vitest run tests/api/stream.test.ts`
Expected: FAIL — Modul existiert nicht.

- [ ] **Step 3: `src/api/stream.ts` implementieren**

```ts
import { existsSync } from "node:fs";
import { join } from "node:path";
import type { FastifyInstance } from "fastify";
import { runYtDlp } from "../yt-dlp/client.js";
import {
  type CacheEntry, cacheGet, cachePut, dedupInflight,
  loadStreamCache, saveStreamCacheSync,
} from "../stream/url-cache.js";
import { proxyMediaStream } from "../stream/proxy.js";

const VIDEO_ID_RE = /^[A-Za-z0-9_-]{11}$/;
// googlevideo-URLs leben ~6 h; TTL knapp darunter, damit der Proxy nie mit
// einer sterbenden URL startet (bei 403/410 löst er ohnehin frisch auf).
const STREAM_CACHE_TTL_MS = 5 * 60 * 60 * 1000;
// Muxed MP4, NUR progressives HTTPS — ohne [protocol=https] liefert yt-dlp
// gern HLS-Manifeste, deren Segmente vom Handy aus nicht abspielbar sind.
const VIDEO_FORMAT =
  "best[height<=720][ext=mp4][vcodec!=none][acodec!=none][protocol=https]/18/best[ext=mp4][protocol=https]";

export interface StreamDeps { /* siehe Interfaces-Block oben */ }

export function registerStreamRoutes(app: FastifyInstance, deps: StreamDeps = {}): void {
  const ytDlp = deps.ytDlp ?? runYtDlp;
  const now = deps.now ?? Date.now;
  const cache = loadStreamCache(deps.streamCachePath, STREAM_CACHE_TTL_MS);
  const inflight = new Map<string, Promise<string | undefined>>();
  // Persistenz wie music.ts: dirty-Flag + 5s-unref-Timer, Flush bei onClose.
  // (Debounce-Block sinngemäß aus registerMusicRoutes übernehmen, nur onClose-Hook,
  //  ohne process-Signale — die verwaltet music.ts bereits prozessweit.)

  async function extract(videoId: string): Promise<string | undefined> {
    try {
      const result = await ytDlp(
        ["--no-playlist", "-f", VIDEO_FORMAT, "-g", `https://www.youtube.com/watch?v=${videoId}`],
        { timeoutMs: 45_000, maxRetries: 1 },
      );
      const url = result.stdout.trim().split("\n")[0];
      if (!url?.startsWith("http")) return undefined;
      if (url.includes(".m3u8") || url.includes("/manifest/")) return undefined;
      cachePut(cache, videoId, url, now());
      /* markDirty() für die Persistenz */
      return url;
    } catch {
      return undefined;
    }
  }

  const resolve = (videoId: string, force: boolean): Promise<string | undefined> => {
    const cached = force ? undefined : cacheGet(cache, videoId, STREAM_CACHE_TTL_MS, now());
    if (cached) return Promise.resolve(cached);
    return dedupInflight(inflight, videoId, () => extract(videoId));
  };

  app.get<{ Params: { videoId: string } }>("/stream/video/:videoId", async (req, reply) => {
    const { videoId } = req.params;
    if (!VIDEO_ID_RE.test(videoId)) return reply.code(400).send({ error: "invalid videoId" });

    // Erst auflösen: scheitert YouTube komplett, aber der Server hat noch die
    // heruntergeladene Datei, spielt die App diese über den statischen Mount.
    const url = await resolve(videoId, false);
    if (!url && deps.videoDir && existsSync(join(deps.videoDir, `${videoId}.mp4`)))
      return reply.redirect(`/videos/${videoId}.mp4`, 302);

    return proxyMediaStream(reply, req.headers.range, (force) => resolve(videoId, force), "video", {
      fetchImpl: deps.fetchImpl,
      retryDelaysMs: deps.retryDelaysMs,
    });
  });
}
```

Der Persistenz-Debounce ist bewusst als Kommentar markiert — sinngemäß aus `registerMusicRoutes` (Zeilen ~409–440) übernehmen: `markDirty()` in `extract` nach `cachePut`, Flush per `app.addHook("onClose", …)` mit `saveStreamCacheSync`. Fastify-Redirect-Signatur prüfen (v4: `reply.redirect(302, url)`, v5: `reply.redirect(url, 302)`) — an installierte Major-Version anpassen.

- [ ] **Step 4: Tests laufen lassen**

Run: `cd backend && npx vitest run tests/api/stream.test.ts`
Expected: alle 5 PASS.

- [ ] **Step 5: In `index.ts` registrieren**

Neben `registerMusicRoutes` (Zeile ~130):

```ts
import { registerStreamRoutes } from "./api/stream.js";
// …
registerStreamRoutes(app, {
  streamCachePath: join(cfg.dataDir, "video-stream-cache.json"),
  videoDir: cfg.videoDir,
});
```

Exakte `cfg`-Feldnamen vorher in `config.ts` verifizieren (`videoDir`, `dataDir`).

- [ ] **Step 6: Build + alle Tests + Smoke-Test**

Run: `cd backend && npm run build && npm test`
Expected: PASS.

Smoke (Server muss lokal laufen, System-Node):

```bash
curl -sI -H "Range: bytes=0-1023" "http://localhost:3939/stream/video/dQw4w9WgXcQ" | head -5
```

Expected: `HTTP/1.1 206 Partial Content` + `content-range`-Header.

- [ ] **Step 7: Commit**

```bash
git add backend/src/api/stream.ts backend/src/index.ts backend/tests/api/stream.test.ts
git commit -m "feat(stream): GET /stream/video/:id — Live-Proxy von YouTube mit Cache + Datei-Fallback"
```

---

### Task 5: Android — Playback auf den Stream-Endpoint umstellen

**Files:**
- Modify: `android/app/src/main/java/com/hikari/app/player/HikariPlayerFactory.kt:59-62`

**Interfaces:**
- Consumes: `GET $baseUrl/stream/video/:videoId` aus Task 4 (206/Range-fähig, ExoPlayer-kompatibel; 302-Fallback wird von OkHttp transparent gefolgt).
- Produces: unverändertes `mediaItemFor(baseUrl, videoId, localFilePath, kind)`-Interface — Aufrufer (`FeedScreen`, `VideoPlayerScreen`) brauchen keine Änderung.

- [ ] **Step 1: URL-Bau ändern**

In `mediaItemFor` den `when`-Zweig ersetzen:

```kotlin
val path = when (kind) {
    "clip" -> "/clips/$videoId.mp4"
    else   -> "/stream/video/$videoId"
}
```

`localFilePath`-Zweig (Geräte-Offline) und `"clip"`-Zweig bleiben unangetastet.

- [ ] **Step 2: Kompilieren**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (Bei Dex-/Duplikatfehlern: `./gradlew clean` — bekanntes macOS-„ 2"-Problem.)

- [ ] **Step 3: Manueller Test (Backend läuft auf PORT=3939)**

App im Emulator/Gerät starten, ein Feed-Video (kind ≠ clip) und ein Original-Video im `VideoPlayerScreen` abspielen, dabei seeken. Expected: Wiedergabe startet ≤ ein paar Sekunden (erste Auflösung), Seek funktioniert (Range/206), zweiter Start desselben Videos startet schneller (Cache-Hit).

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/hikari/app/player/HikariPlayerFactory.kt
git commit -m "feat(player): Videos streamen über /stream/video statt Server-MP4"
```

---

### Task 6: Version + Abschluss

**Files:**
- Modify: `android/app/build.gradle.kts` (versionName → `0.58.0`, versionCode +1)

**Interfaces:**
- Consumes: alle vorherigen Tasks abgeschlossen und verifiziert.
- Produces: releasbarer Stand „v0.58.0 — Streaming-Fundament".

- [ ] **Step 1: Version bumpen**

In `android/app/build.gradle.kts`: `versionName = "0.58.0"`, `versionCode` um 1 erhöhen (aktuelle Werte vorher nachschlagen).

- [ ] **Step 2: Gesamtverifikation**

Run: `cd backend && npm run build && npm test` und `cd android && ./gradlew :app:compileDebugKotlin`
Expected: alles PASS/SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add android/app/build.gradle.kts
git commit -m "feat(stream): Streaming-Fundament — v0.58.0"
```

- [ ] **Step 4: Release NUR nach bestandener manueller Verifikation (Task 5 Step 3) und per Release-Prozess**

`git push` + `gh release create v0.58.0` → CI baut die APK. (Dem User erst danach Bescheid geben — Release-Prozess-Regel.)
