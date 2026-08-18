# Etappe 5: Bibliothek als Sammlung — Implementierungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die Bibliothek zeigt, was DU gesammelt hast — Später ansehen (automatisch vom Karten-Wegswipe), Gespeichert, Verlauf, Kanäle, Serien, Offline — und Downloads werden ein bewusster On-Demand-Flow statt totes Relikt.

**Architecture:** Neue `watch_later`-Tabelle (Migration liegt schon im Baum) + Routen-Datei `api/watch-later.ts`; `GET /library` wird ADDITIV um `watchLater` und `history` erweitert (hydratisiert über das exportierte `hydrateFeedBatch`). Der Karten-Wegswipe im Feed legt Langvideos automatisch in „Später ansehen", das Öffnen entfernt sie wieder. `POST /videos/:id/download` lädt serverseitig on demand (fire-and-forget, schreibt die `downloaded_videos`-Row NACH der Datei); der SmartDownloadWorker nimmt Saved + WatchLater als Quelle und stößt fehlende Serverdateien erst an (nächster Lauf zieht sie).

**Tech Stack:** Fastify + TypeScript (ESM, `.js`-Imports), better-sqlite3, Vitest; Android: Kotlin/Compose, WorkManager (kein Room-Bump nötig — alles serverseitig bzw. bestehende Tabellen).

**Spec:** `docs/superpowers/specs/2026-08-18-feed-streaming-overhaul-design.md` §4.7/§4.8/§6. Abweichungen: (a) `GET /library` wird erweitert statt ersetzt (`recentlyAdded` bleibt für manuelle Importe/Alt-App); (b) kein expliziter „Später"-Button — die Automatik (Wegswipe → rein, Öffnen → raus) deckt den Fluss ab; (c) Verlauf kommt aus `feed_items.seen_at` (nicht Playback-Positionen — einfacher und vollständig, Shorts inklusive).

## Global Constraints

- Backend NUR mit System-Node: `cd /Users/ayysir/Desktop/Hikari/backend && npm test` / `npm run build`. Biome, deutsche Kommentare, `.js`-Imports.
- Reihenfolge beim On-Demand-Download: ERST Datei fertig, DANN `downloaded_videos`-Row — der Startup-Check in `index.ts:64-82` löscht Dateien ohne Row und Rows ohne Datei.
- `hydrateFeedBatch` erwartet `{ id }`-Rows und liefert nur Videos, die eine `feed_items`-Row haben (approvte Inhalte) — watch_later/History-Einträge erfüllen das per Konstruktion.
- UI: keine Emojis, Amber nur Akzent; neue Karten als private Composables in `LibraryScreen.kt` (bestehende Karten sind ebenfalls privat — keine Extraktions-Refaktor in dieser Etappe).
- Parallel-Sessions: vor Release `git fetch --tags` + `gh release list`; Musik-Dateien und `build.gradle.kts` nur für den eigenen Bump.
- Die `watch_later`-Migration (schema.sql + Tabellenlisten-Test) ist BEREITS im Arbeitsbaum — Task 1 committet sie mit.

---

### Task 1: `api/watch-later.ts` — Später-ansehen-API

**Files:**
- Create: `backend/src/api/watch-later.ts`
- Modify: `backend/src/api/feed.ts` (`hydrateFeedBatch` exportieren), `backend/src/index.ts` (registrieren), `backend/src/db/schema.sql` + `backend/src/db/migrations.test.ts` (bereits geändert — mitcommitten)
- Test: `backend/tests/api/watch-later.test.ts` (neu)

**Interfaces:**
- Consumes: `hydrateFeedBatch(db, rows: {id}[])` aus `./feed.js` (in feed.ts `function` → `export function`).
- Produces: `export async function registerWatchLaterRoutes(app: FastifyInstance, deps: { db: Database.Database }): Promise<void>` mit:
  - `GET /watch-later` → hydratisierte Items (added_at DESC, LIMIT 100)
  - `POST /watch-later/:id` → 204 (`INSERT OR IGNORE`; 404 wenn Video unbekannt)
  - `DELETE /watch-later/:id` → 204 (idempotent)

- [ ] **Step 1: Failing Test** (`backend/tests/api/watch-later.test.ts`; Seed-Muster aus `feed.test.ts::seedFeedItem` — Video + Score + feed_items, OHNE downloaded_videos):

```ts
import Database from "better-sqlite3";
import Fastify from "fastify";
import { beforeEach, describe, expect, it } from "vitest";
import { registerWatchLaterRoutes } from "../../src/api/watch-later.js";
import { applyMigrations } from "../../src/db/migrations.js";

function seedVideo(db: Database.Database, id: string) {
  db.prepare("INSERT OR IGNORE INTO channels (id,url,title,added_at) VALUES ('c1','x','C',0)").run();
  db.prepare(
    `INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at, format)
     VALUES (?, 'c1', ?, 0, 600, 0, 'long')`,
  ).run(id, `t-${id}`);
  db.prepare(
    `INSERT INTO scores (video_id, overall_score, category, clickbait_risk, educational_value,
      emotional_manipulation, reasoning, model_used, scored_at, decision)
     VALUES (?, 80, 'x', 1, 9, 0, 'ok', 'mock', 0, 'approved')`,
  ).run(id);
  db.prepare(
    "INSERT INTO feed_items (video_id, added_to_feed_at, is_pre_clipper) VALUES (?, 0, 1)",
  ).run(id);
}

describe("watch-later API", () => {
  let db: Database.Database;
  beforeEach(() => {
    db = new Database(":memory:");
    applyMigrations(db);
  });
  async function app() {
    const a = Fastify();
    await registerWatchLaterRoutes(a, { db });
    return a;
  }

  it("POST + GET: hydratisierte Items, neueste zuerst", async () => {
    seedVideo(db, "w1");
    seedVideo(db, "w2");
    const a = await app();
    expect((await a.inject({ method: "POST", url: "/watch-later/w1" })).statusCode).toBe(204);
    expect((await a.inject({ method: "POST", url: "/watch-later/w2" })).statusCode).toBe(204);
    const body = (await a.inject({ method: "GET", url: "/watch-later" })).json() as {
      videoId: string;
      kind: string;
    }[];
    expect(body.map((x) => x.videoId)).toEqual(["w2", "w1"]);
    expect(body[0]?.kind).toBe("video");
  });

  it("POST unbekanntes Video ⇒ 404; DELETE ist idempotent", async () => {
    const a = await app();
    expect((await a.inject({ method: "POST", url: "/watch-later/nix" })).statusCode).toBe(404);
    seedVideo(db, "w1");
    await a.inject({ method: "POST", url: "/watch-later/w1" });
    expect((await a.inject({ method: "DELETE", url: "/watch-later/w1" })).statusCode).toBe(204);
    expect((await a.inject({ method: "DELETE", url: "/watch-later/w1" })).statusCode).toBe(204);
    expect(((await a.inject({ method: "GET", url: "/watch-later" })).json() as unknown[]).length).toBe(0);
  });
});
```

- [ ] **Step 2: Run** — FAIL (Modul fehlt).
- [ ] **Step 3: Implementierung:**

```ts
import type Database from "better-sqlite3";
import type { FastifyInstance } from "fastify";
import { hydrateFeedBatch } from "./feed.js";

/** "Später ansehen": vom Karten-Wegswipe befüllt, beim Öffnen wieder geräumt. */
export async function registerWatchLaterRoutes(
  app: FastifyInstance,
  deps: { db: Database.Database },
): Promise<void> {
  app.get("/watch-later", async () => {
    const rows = deps.db
      .prepare("SELECT video_id AS id FROM watch_later ORDER BY added_at DESC LIMIT 100")
      .all() as { id: string }[];
    return hydrateFeedBatch(deps.db, rows as never);
  });

  app.post<{ Params: { id: string } }>("/watch-later/:id", async (req, reply) => {
    const known = deps.db.prepare("SELECT 1 FROM videos WHERE id = ?").get(req.params.id);
    if (!known) return reply.code(404).send({ error: "video not found" });
    deps.db
      .prepare("INSERT OR IGNORE INTO watch_later (video_id, added_at) VALUES (?, ?)")
      .run(req.params.id, Date.now());
    return reply.code(204).send();
  });

  app.delete<{ Params: { id: string } }>("/watch-later/:id", async (req, reply) => {
    deps.db.prepare("DELETE FROM watch_later WHERE video_id = ?").run(req.params.id);
    return reply.code(204).send();
  });
}
```

In `feed.ts`: `function hydrateFeedBatch` → `export function hydrateFeedBatch` (Signatur unverändert; Parametertyp auf `Array<{ id: string }>`-kompatibel lockern, falls TS meckert: `rows: Pick<RawFeedRow, "id">[]`). In `index.ts` neben den Feed-Routen: `await registerWatchLaterRoutes(app, { db });` + Import.

- [ ] **Step 4: Run** neue Tests + Vollsuite — PASS.
- [ ] **Step 5: Commit** `git add backend/src/api/watch-later.ts backend/src/api/feed.ts backend/src/index.ts backend/src/db backend/tests/api/watch-later.test.ts && git commit -m "feat(library): watch_later-Tabelle + API"`

---

### Task 2: `GET /library` — Sektionen `watchLater` + `history`

**Files:**
- Modify: `backend/src/api/videos.ts:119-143` (Response additiv erweitern; `hydrateFeedBatch`-Import)
- Test: `backend/src/api/videos.test.ts` (erweitern)

**Interfaces:**
- Produces: `GET /library` → `{ series, recentlyAdded, channels, watchLater: FeedItemJson[], history: FeedItemJson[] }` — `watchLater` = hydratisierte watch_later-Einträge (added_at DESC, LIMIT 50), `history` = hydratisierte gesehene feed_items (`seen_at IS NOT NULL ORDER BY seen_at DESC LIMIT 30`).

- [ ] **Step 1: Failing Test** — im Stil der bestehenden `videos.test.ts` (Fastify + applyMigrations + Seeds wie in Task 1):

```ts
it("GET /library liefert watchLater und history hydratisiert", async () => {
  // seedVideo-Helfer wie in tests/api/watch-later.test.ts (Video+Score+feed_items)
  seedVideo(db, "wl1");
  seedVideo(db, "seen1");
  db.prepare("INSERT INTO watch_later (video_id, added_at) VALUES ('wl1', 1000)").run();
  db.prepare("UPDATE feed_items SET seen_at = 2000 WHERE video_id = 'seen1'").run();
  const res = await app.inject({ method: "GET", url: "/library" });
  const body = res.json() as { watchLater: { videoId: string }[]; history: { videoId: string }[] };
  expect(body.watchLater.map((x) => x.videoId)).toEqual(["wl1"]);
  expect(body.history.map((x) => x.videoId)).toEqual(["seen1"]);
});
```

(Der bestehende Test-Aufbau von `videos.test.ts` vorher lesen und Seeds/`registerVideosRoutes`-Deps daran ausrichten.)

- [ ] **Step 2: Run** — FAIL. **Step 3:** Im `/library`-Handler ergänzen:

```ts
const watchLaterRows = db
  .prepare("SELECT video_id AS id FROM watch_later ORDER BY added_at DESC LIMIT 50")
  .all() as { id: string }[];
const historyRows = db
  .prepare(
    "SELECT video_id AS id FROM feed_items WHERE seen_at IS NOT NULL ORDER BY seen_at DESC LIMIT 30",
  )
  .all() as { id: string }[];
return {
  series, recentlyAdded, channels,
  watchLater: hydrateFeedBatch(db, watchLaterRows as never),
  history: hydrateFeedBatch(db, historyRows as never),
};
```

- [ ] **Step 4: Run** Vollsuite — PASS. **Step 5: Commit** `git add backend/src/api/videos.ts backend/src/api/videos.test.ts && git commit -m "feat(library): watchLater + history Sektionen"`

---

### Task 3: `POST /videos/:id/download` — On-Demand-Server-Download

**Files:**
- Modify: `backend/src/api/videos.ts` (neue Route; `downloadVideo`-Import; `videoDir` ist bereits in den Deps), `docs` keine
- Test: `backend/tests/api/videos-download.test.ts` (neu)

**Interfaces:**
- Consumes: `downloadVideo({ videoId, outDir })` → `{ filePath, fileSizeBytes }` aus `../download/worker.js` (wirft bei Fehler; schreibt KEINE DB-Row).
- Produces: `POST /videos/:id/download` →
  - 404 wenn Video unbekannt;
  - `{ status: "ready" }` (200) wenn `downloaded_videos`-Row existiert;
  - `{ status: "queued" }` (202) sonst: fire-and-forget `downloadVideo` + danach `INSERT OR IGNORE INTO downloaded_videos (video_id, file_path, file_size_bytes, downloaded_at)`; In-Flight-Set verhindert Doppel-Downloads (erneuter POST während des Laufs → 202 ohne zweiten Start).
  - Deps erweitert um optionales `download?: typeof downloadVideo` (Test-Injection).

- [ ] **Step 1: Failing Test:**

```ts
import Database from "better-sqlite3";
import Fastify from "fastify";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { registerVideosRoutes } from "../../src/api/videos.js";
import { applyMigrations } from "../../src/db/migrations.js";

describe("POST /videos/:id/download", () => {
  let db: Database.Database;
  beforeEach(() => {
    db = new Database(":memory:");
    applyMigrations(db);
    db.prepare("INSERT INTO channels (id,url,title,added_at) VALUES ('c1','x','C',0)").run();
    db.prepare(
      `INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at)
       VALUES ('v1', 'c1', 't', 0, 600, 0)`,
    ).run();
  });
  function buildApp(download: unknown) {
    const app = Fastify();
    // WICHTIG: exakte Pflicht-Deps von registerVideosRoutes vorher nachlesen
    // (db, videoDir, coverDir, extractor) und minimal befüllen.
    return registerVideosRoutes(app, {
      db,
      videoDir: "/tmp",
      coverDir: "/tmp",
      extractor: null,
      download,
    } as never).then(() => app);
  }

  it("unbekannt ⇒ 404; queued ⇒ 202 + Row nach Abschluss; ready ⇒ 200", async () => {
    let resolveDl: (v: { filePath: string; fileSizeBytes: number }) => void = () => {};
    const download = vi.fn(
      () => new Promise((res) => { resolveDl = res; }),
    );
    const app = await buildApp(download);

    expect((await app.inject({ method: "POST", url: "/videos/nix/download" })).statusCode).toBe(404);

    const q = await app.inject({ method: "POST", url: "/videos/v1/download" });
    expect(q.statusCode).toBe(202);
    expect(q.json()).toEqual({ status: "queued" });
    // Doppel-POST während des Laufs startet keinen zweiten Download
    await app.inject({ method: "POST", url: "/videos/v1/download" });
    expect(download).toHaveBeenCalledTimes(1);

    resolveDl({ filePath: "/tmp/v1.mp4", fileSizeBytes: 123 });
    await new Promise((r) => setTimeout(r, 20)); // fire-and-forget abschliessen lassen
    expect(db.prepare("SELECT file_path FROM downloaded_videos WHERE video_id='v1'").get()).toEqual({
      file_path: "/tmp/v1.mp4",
    });

    const ready = await app.inject({ method: "POST", url: "/videos/v1/download" });
    expect(ready.statusCode).toBe(200);
    expect(ready.json()).toEqual({ status: "ready" });
  });
});
```

- [ ] **Step 2: Run** — FAIL. **Step 3: Implementierung** in `videos.ts` (Route + Deps-Erweiterung; In-Flight-`Set<string>` im Register-Scope; Fehlerfall: `catch` → Set räumen + `app.log.warn`).
- [ ] **Step 4: Run** Vollsuite — PASS. **Step 5: Commit** `git add backend/src/api/videos.ts backend/tests/api/videos-download.test.ts && git commit -m "feat(videos): On-Demand-Server-Download"`

---

### Task 4: App-Datenmodell — DTOs, API, Repository

**Files:**
- Modify: `android/.../data/api/dto/LibraryDto.kt` (`LibraryResponse` + `watchLater: List<FeedItemDto> = emptyList()`, `history: List<FeedItemDto> = emptyList()`), `data/api/HikariApi.kt`, `domain/repo/FeedRepository.kt`

**Interfaces:**
- Produces:

```kotlin
// HikariApi
@GET("watch-later") suspend fun getWatchLater(): List<FeedItemDto>
@POST("watch-later/{id}") suspend fun addWatchLater(@Path("id") videoId: String)
@DELETE("watch-later/{id}") suspend fun removeWatchLater(@Path("id") videoId: String)
@POST("videos/{id}/download") suspend fun requestServerDownload(@Path("id") videoId: String): ServerDownloadStatus
// dto: @Serializable data class ServerDownloadStatus(val status: String)  // "ready" | "queued"

// FeedRepository
suspend fun fetchWatchLater(): List<FeedItem> = api.getWatchLater().map { it.toDomain() }
suspend fun addWatchLater(videoId: String) { runCatching { api.addWatchLater(videoId) } }
suspend fun removeWatchLater(videoId: String) { runCatching { api.removeWatchLater(videoId) } }
suspend fun requestServerDownload(videoId: String): String? =
    runCatching { api.requestServerDownload(videoId).status }.getOrNull()
```

- [ ] **Step 1:** Mechanisch umsetzen (`ServerDownloadStatus` in eigene Datei `data/api/dto/ServerDownloadStatus.kt`; FeedItemDto-Import in LibraryDto prüfen).
- [ ] **Step 2: Kompilieren** `cd android && ./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** `git add android/app/src/main/java/com/hikari/app && git commit -m "feat(app): watch-later + Server-Download API"`

---

### Task 5: Feed-Automatik — Wegswipe → Später, Öffnen → raus

**Files:**
- Modify: `android/.../ui/feed/FeedViewModel.kt`, `ui/feed/FeedScreen.kt`

**Interfaces:**
- Produces in FeedViewModel:

```kotlin
/** Langvideo-Karte weggeswiped ohne zu oeffnen: gesehen + Später ansehen. */
fun onCardSkipped(videoId: String) {
    viewModelScope.launch {
        runCatching { repo.addWatchLater(videoId) }
        repo.markSeen(videoId)
    }
}

/** Karte geoeffnet: gehoert in den Verlauf, nicht in Später ansehen. */
fun onCardOpened(videoId: String) {
    viewModelScope.launch { repo.removeWatchLater(videoId) }
}
```

- [ ] **Step 1:** In `FeedScreen.kt` beim Karten-Dwell-Effekt (`LaunchedEffect(item.videoId, pagerState.settledPage)` in der `kind == "video"`-Seite): `vm.onSeen(item.videoId)` → `vm.onCardSkipped(item.videoId)` ersetzen; im `onOpen` der `LongVideoCard` zusätzlich `vm.onCardOpened(item.videoId)` VOR `onNavigate(...)`.

Semantik-Hinweis: Öffnen NACH dem Dwell (Karte lag >1,5 s im Fokus, dann Tap) ergibt add→remove in richtiger Reihenfolge, weil `onCardOpened` später feuert — Restrisiko Race bei langsamem Netz akzeptiert (Video landet schlimmstenfalls in Später ansehen UND Verlauf, verschwindet beim nächsten Öffnen).

- [ ] **Step 2: Kompilieren** — BUILD SUCCESSFUL. **Step 3: Commit** `git add android/app/src/main/java/com/hikari/app/ui/feed && git commit -m "feat(feed): Wegswipe legt Langvideos in Später ansehen"`

---

### Task 6: Library-UI — Sektionen Später ansehen / Gespeichert / Verlauf

**Files:**
- Modify: `android/.../ui/library/LibraryViewModel.kt` (watchLater-Flow), `ui/library/LibraryScreen.kt` (3 neue Sektionen + private FeedItem-Karte)

**Interfaces:**
- LibraryViewModel: neben den bestehenden (bereits befüllten!) `savedItems`/`today`-Flows:

```kotlin
private val _watchLater = MutableStateFlow<List<FeedItem>>(emptyList())
val watchLater: StateFlow<List<FeedItem>> = _watchLater.asStateFlow()
private val _history = MutableStateFlow<List<FeedItem>>(emptyList())
val history: StateFlow<List<FeedItem>> = _history.asStateFlow()
// in loadBriefingExtras(): _watchLater.value = runCatching { repo.fetchWatchLater() }.getOrDefault(emptyList())
//                          _history.value    = runCatching { repo.fetchOld() }.getOrDefault(emptyList())
fun removeWatchLater(videoId: String) { viewModelScope.launch { repo.removeWatchLater(videoId); _watchLater.value = _watchLater.value.filter { it.videoId != videoId } } }
```

(History über `repo.fetchOld()` statt LibraryResponse-Feld — der Backend-`history`-Abschnitt aus Task 2 dient Alt-/Fremd-Clients; die App nutzt den bestehenden `mode=old`-Weg, ein Datenpfad weniger.)

- LibraryScreen — neue private Composable (an `RecentVideoCard`-Optik anlehnen: 200dp, 16:9, AsyncImage, Titel 2-zeilig, Kanalzeile HikariTextFaint, Dauer-Badge wie in `LongVideoCard`):

```kotlin
@Composable
private fun CollectionCard(item: FeedItem, onPlay: () -> Unit, onRemove: (() -> Unit)? = null)
```

`onRemove` (nur Später ansehen) als Long-Press (`combinedClickable`) mit kleinem „Entfernt"-Verhalten (direkt entfernen, kein Dialog).

- [ ] **Step 1:** In `LibraryContent` nach der `HeroSection` drei `LazyRow`-Sektionen einfügen (jeweils nur wenn nicht leer, `SectionHeader` wie die Nachbarn):
  1. „Später ansehen" → `watchLater`, `onPlay = { vm.removeWatchLater(item.videoId); onPlayVideo(item.videoId, item.title, item.channelTitle) }` (Abspielen räumt den Eintrag), `onRemove` = Long-Press.
  2. „Gespeichert" → `savedItems` (Socket existiert schon), `onPlay = onPlayVideo(...)`.
  3. „Verlauf" → `history`, `onPlay = onPlayVideo(...)`.
  Flows in `LibraryScreen` einsammeln (`collectAsState`), Parameter durchreichen.
- [ ] **Step 2: Kompilieren** — BUILD SUCCESSFUL. **Step 3: Commit** `git add android/app/src/main/java/com/hikari/app/ui/library && git commit -m "feat(library): Sektionen Später ansehen, Gespeichert, Verlauf"`

---

### Task 7: SmartDownloadWorker — Saved + WatchLater, Server-Anstoß

**Files:**
- Modify: `android/.../domain/download/SmartDownloadWorker.kt`

**Interfaces:**
- Consumes: `feedRepo.fetchWatchLater()` + `feedRepo.requestServerDownload(videoId): String?` aus Task 4.
- Neues Verhalten der Video-Schleife:

```kotlin
val candidates = (feedRepo.fetchSaved() + runCatching { feedRepo.fetchWatchLater() }.getOrDefault(emptyList()))
    .distinctBy { it.videoId }
var downloaded = 0
for (item in candidates) {
    if (downloaded >= MAX_PER_FIRE) break
    if (localDownloads.isDownloaded(item.videoId)) continue
    // Serverdatei sicherstellen: "ready" → sofort ziehen; "queued" → der Server
    // laedt gerade, der naechste 6-h-Lauf zieht die Datei; null → Netzfehler, skip.
    when (feedRepo.requestServerDownload(item.videoId)) {
        "ready" -> {
            val ok = localDownloads.download(item.toLocalDownloadMetadata()).isSuccess
            if (ok) downloaded++
        }
        else -> continue
    }
}
```

(`toLocalDownloadMetadata()` = die bestehende Mapping-Stelle im Worker — `kind = CHANNEL` etc. — als kleine private Extension herausziehen, damit beide Quellen sie nutzen.)

- [ ] **Step 1:** Umbauen wie oben (bestehende Struktur/Kommentare respektieren; Songs-Teil unangetastet).
- [ ] **Step 2: Kompilieren** — BUILD SUCCESSFUL. **Step 3: Commit** `git add android/app/src/main/java/com/hikari/app/domain/download && git commit -m "feat(offline): SmartDownload zieht Saved+WatchLater, stoesst Server-Download an"`

---

### Task 8: Verifikation, Deploy, Release, Spec-Abschluss

- [ ] **Step 1:** Backend `npm run build && npm test` PASS; `cli/hikari restart`; Smoke:
  - `curl -X POST localhost:3939/watch-later/<echte-video-id>` → 204; `curl localhost:3939/watch-later` → Item; `curl localhost:3939/library | python3 -c "..."` → watchLater/history-Keys.
  - `curl -X POST localhost:3939/videos/<nicht-heruntergeladene-id>/download` → `{"status":"queued"}`; nach Abschluss zweiter POST → `{"status":"ready"}` + Datei in `~/.hikari/videos/`.
- [ ] **Step 2:** Android `./gradlew :app:compileDebugKotlin` SUCCESS.
- [ ] **Step 3:** `git fetch --tags` + `gh release list`; nächste freie Version (versionCode +1), Release-Notes im Hausstil, `gh run watch` bis CI-APK.
- [ ] **Step 4:** Spec-Status aktualisieren (alle 5 Etappen geliefert) + Memory.
