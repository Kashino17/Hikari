# Import-Form Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Erweitere die existierende Android Import-Funktion: Bulk-paste mit per-URL editierbaren Cards (Title, Series-Picker, Staffel/Folge, Sprache, Untertitel) plus Shared-Defaults oben. Backend bekommt einen Bulk-Endpoint mit Per-URL-Metadata.

**Architecture:** Backend erhält additive Endpoints (`POST /videos/import/bulk`, `GET /series`) und einen `title`-Override im existierenden `ManualMetadata`. Android-Frontend ersetzt den simplen `ImportSheet` (Bulk-Textarea) durch einen mehrstufigen Flow: Textarea → debounced URL-Parse → analysierte Cards → Shared-Defaults-Block + Per-Card-Override → Bulk-Submit. Series-Picker ist ein Material3-Typeahead (`ExposedDropdownMenuBox`) mit „+ Erstellen"-Option als Letzteintrag.

**Tech Stack:** Backend Fastify 5 + better-sqlite3 + vitest. Android Kotlin 2.1 + Compose Material3 + Hilt + Retrofit + MockK + Turbine. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-04-26-import-form-design.md`

---

## File Structure

**Backend — modified:**

| Path | Change |
|---|---|
| `backend/src/import/manual-import.ts` | Add `title?: string` to `ManualMetadata`; let it override yt-dlp title in `importDirectLink` |
| `backend/src/import/manual-import.test.ts` | Add test for title-override |
| `backend/src/api/videos.ts` | Add `GET /series`; add `POST /videos/import/bulk` |

**Backend — new:**

| Path | Responsibility |
|---|---|
| `backend/src/api/videos-bulk.test.ts` | Tests for new bulk endpoint (Fastify in-memory) |

**Android — modified:**

| Path | Change |
|---|---|
| `android/app/src/main/java/com/hikari/app/data/api/dto/ImportVideosDto.kt` | Replace existing types with `BulkImportItem`/`BulkImportRequest`/`BulkImportResponse`/`AnalyzeRequest`/`AnalyzeResponse`/`AiMeta`/`ImportItemMetadata`/`SeriesItemDto` |
| `android/app/src/main/java/com/hikari/app/data/api/HikariApi.kt` | Replace `@POST("videos/import")` (broken-bulk) with `@POST("videos/analyze")`, `@POST("videos/import/bulk")`, `@GET("series")` |
| `android/app/src/main/java/com/hikari/app/domain/repo/ChannelsRepository.kt` | Replace `importVideos(urls)` with `importVideosBulk(items)`; add `analyzeVideo(url)` and `listSeries()` |
| `android/app/src/main/java/com/hikari/app/ui/channels/ChannelsScreen.kt` | Replace existing `ImportSheet` composable; pass `ImportSheetViewModel` |
| `android/app/src/main/java/com/hikari/app/ui/channels/ChannelsViewModel.kt` | Replace `importVideos(urls)` with delegate to `ImportSheetViewModel` (or remove, depending on integration) |

**Android — new:**

| Path | Responsibility |
|---|---|
| `android/app/src/main/java/com/hikari/app/ui/channels/ImportSheetViewModel.kt` | Hilt-injected ViewModel: state, debounced URL parse, parallel analyze, submit |
| `android/app/src/main/java/com/hikari/app/ui/channels/components/SeriesTypeahead.kt` | Material3 `ExposedDropdownMenuBox` typeahead with "+ Erstellen"-Option |
| `android/app/src/main/java/com/hikari/app/ui/channels/components/SharedDefaultsBlock.kt` | Card with applies-to-all defaults (Series, Staffel, Sprache, Untertitel) |
| `android/app/src/main/java/com/hikari/app/ui/channels/components/ImportCard.kt` | Per-URL card: Loading/Failed/Ready states + expand/collapse |
| `android/app/src/test/java/com/hikari/app/data/api/VideosBulkApiTest.kt` | DTO/HikariApi parse tests with MockWebServer |
| `android/app/src/test/java/com/hikari/app/ui/channels/ImportSheetViewModelTest.kt` | ViewModel tests (Turbine + MockK) |

---

## Phase 0 — Backend Foundation

### Task 0.1: Title override in ManualMetadata

**Files:**
- Modify: `backend/src/import/manual-import.ts`
- Modify: `backend/src/import/manual-import.test.ts`

- [ ] **Step 1: Add failing test for title override**

Append to `backend/src/import/manual-import.test.ts` inside the existing `describe("importDirectLink", ...)` block (or at top-level if no describe):

```typescript
import { test, expect, vi, beforeEach } from "vitest";
// ... existing imports

test("importDirectLink uses manualMeta.title when provided", async () => {
  const db = new MockDb();
  // Mock yt-dlp to return a different title
  vi.stubGlobal("fetch", vi.fn(async () => ({
    ok: true,
    arrayBuffer: async () => new ArrayBuffer(1024),
  })));

  // Mock execa-wrapped yt-dlp via an existing pattern in this file. Use the
  // same `runYtDlp = vi.fn` setup as the existing tests in this file. If the
  // test file already has a mock for runYtDlp at file-scope, reuse it; the
  // pattern from the existing "falls back to VOE page config" test:
  const runYtDlp = vi.fn(async (args: string[]) => {
    if (args.includes("--dump-single-json")) {
      return {
        stdout: JSON.stringify({
          id: "abc123",
          extractor: "youtube",
          title: "auto-generated-title",
          duration: 100,
          thumbnail: "https://t/x.jpg",
          upload_date: "20240101",
          webpage_url: "https://example.com/abc123",
        }),
        stderr: "",
      };
    }
    return { stdout: "", stderr: "" };
  });

  // If `runYtDlp` is injected via test setup, use the same mechanism the
  // existing test suite uses. Otherwise patch `import.meta` mock. Either way:
  // the assertion is what matters.

  const result = await importDirectLink(db as never, "https://example.com/abc123", "/tmp/test", {
    title: "User Override Title",
  });

  expect(result.status).toBe("ok");
  const stored = db.videos.get("youtube_abc123");
  expect(stored?.title).toBe("User Override Title");
});
```

**Note:** The existing tests in `manual-import.test.ts` already mock `runYtDlp`. Inspect the file before writing this test and use the **identical mocking pattern** as the existing "falls back to VOE page config" test. If the existing tests use a module-mock at the top of the file, follow that. Don't introduce a new mocking mechanism.

- [ ] **Step 2: Run, verify failure**

```bash
cd /Users/ayysir/Desktop/Hikari/backend && npm test -- src/import/manual-import.test.ts 2>&1 | tail -20
```
Expected: New test FAIL because `manualMeta.title` isn't honored — `stored?.title` is `"auto-generated-title"`, not `"User Override Title"`.

- [ ] **Step 3: Update ManualMetadata interface**

In `backend/src/import/manual-import.ts`, find the `ManualMetadata` interface (around line 19) and add `title?: string` as the first field:

```typescript
export interface ManualMetadata {
  title?: string;
  seriesId?: string;
  seriesTitle?: string;
  season?: number;
  episode?: number;
  dubLanguage?: string;
  subLanguage?: string;
  isMovie?: boolean;
}
```

- [ ] **Step 4: Use the title in importDirectLink**

In `backend/src/import/manual-import.ts`, find the line:

```typescript
const title = meta.title ?? meta.id;
```

Change it to:

```typescript
const title = manualMeta?.title ?? meta.title ?? meta.id;
```

- [ ] **Step 5: Run test, verify pass**

```bash
cd /Users/ayysir/Desktop/Hikari/backend && npm test -- src/import/manual-import.test.ts 2>&1 | tail -10
```
Expected: All tests pass, including the new override test.

- [ ] **Step 6: Run full backend suite — no regressions**

```bash
cd /Users/ayysir/Desktop/Hikari/backend && npm test 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL, all tests green.

- [ ] **Step 7: Commit**

```bash
git add backend/src/import/manual-import.ts backend/src/import/manual-import.test.ts
git commit -m "feat(import): allow manualMeta.title to override yt-dlp title"
```

### Task 0.2: GET /series endpoint

**Files:**
- Modify: `backend/src/api/videos.ts`

- [ ] **Step 1: Add the route**

In `backend/src/api/videos.ts`, inside `registerVideosRoutes`, after the existing `app.get<{ Params: { id: string } }>("/series/:id", ...)` block, add:

```typescript
  app.get("/series", async () => {
    return deps.db
      .prepare("SELECT id, title FROM series ORDER BY title")
      .all();
  });
```

- [ ] **Step 2: Smoke-test with curl**

Start the dev backend if it's not running:

```bash
cd /Users/ayysir/Desktop/Hikari/backend && npm run dev > /tmp/it-dev.log 2>&1 &
sleep 3
curl -s http://localhost:3000/series | head -c 200
echo
kill %1 2>/dev/null
```
Expected: A JSON array (possibly `[]` if no series exist yet, or with `{id, title}` rows).

- [ ] **Step 3: Run full suite**

```bash
cd /Users/ayysir/Desktop/Hikari/backend && npm test 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add backend/src/api/videos.ts
git commit -m "feat(api): GET /series — list of {id, title} for typeahead picker"
```

### Task 0.3: POST /videos/import/bulk endpoint

**Files:**
- Modify: `backend/src/api/videos.ts`
- Create: `backend/tests/api/videos-bulk.test.ts`

- [ ] **Step 1: Write failing tests**

Create `backend/tests/api/videos-bulk.test.ts`:

```typescript
import { test, expect, beforeEach, vi } from "vitest";
import Fastify from "fastify";
import Database from "better-sqlite3";
import { applyMigrations } from "../../src/db/migrations.js";
import { registerVideosRoutes } from "../../src/api/videos.js";

let db: Database.Database;

beforeEach(() => {
  db = new Database(":memory:");
  db.pragma("foreign_keys = ON");
  applyMigrations(db);
  vi.restoreAllMocks();
});

function buildApp() {
  const app = Fastify();
  registerVideosRoutes(app, { db, videoDir: "/tmp/test", extractor: null });
  return app;
}

test("POST /videos/import/bulk returns 202 with queued count", async () => {
  // Mock importDirectLink so we don't actually run yt-dlp
  vi.doMock("../../src/import/manual-import.js", async (orig) => {
    const real = await orig() as Record<string, unknown>;
    return {
      ...real,
      importDirectLink: vi.fn(async () => ({ status: "ok", videoId: "x" })),
    };
  });

  const app = buildApp();
  const r = await app.inject({
    method: "POST",
    url: "/videos/import/bulk",
    payload: {
      items: [
        { url: "https://x.test/1" },
        { url: "https://x.test/2", metadata: { title: "Custom" } },
        { url: "https://x.test/3" },
      ],
    },
  });
  expect(r.statusCode).toBe(202);
  const body = r.json() as { queued: number };
  expect(body.queued).toBe(3);
});

test("POST /videos/import/bulk returns 400 on empty items", async () => {
  const app = buildApp();
  const r = await app.inject({
    method: "POST",
    url: "/videos/import/bulk",
    payload: { items: [] },
  });
  expect(r.statusCode).toBe(400);
});

test("POST /videos/import/bulk returns 400 when items field missing", async () => {
  const app = buildApp();
  const r = await app.inject({
    method: "POST",
    url: "/videos/import/bulk",
    payload: {},
  });
  expect(r.statusCode).toBe(400);
});
```

**Note:** The `vi.doMock` pattern for `importDirectLink` may need to be adapted if the existing test suite uses a different module-mocking pattern. If `vi.doMock` causes hoisting issues with ESM, fallback: use a `vi.spyOn` on the imported binding in a `beforeAll`. The goal is to prevent real yt-dlp invocation during the test.

If module-mocking is fragile, an acceptable simplification: write the 400-case tests only and skip the 202-with-mock-import test. The 202 happy-path is exercised by the manual E2E in Phase 4.

- [ ] **Step 2: Run, verify failure**

```bash
cd /Users/ayysir/Desktop/Hikari/backend && npm test -- tests/api/videos-bulk.test.ts 2>&1 | tail -20
```
Expected: FAIL with "Route POST:/videos/import/bulk not found" (or 404 in test injection).

- [ ] **Step 3: Add the bulk endpoint**

In `backend/src/api/videos.ts`, after the existing `app.post<{ Body: ImportBody }>("/videos/import", ...)` block, add:

```typescript
  app.post<{
    Body: { items?: { url: string; metadata?: ManualMetadata }[] };
  }>("/videos/import/bulk", async (req, reply) => {
    const items = req.body?.items;
    if (!Array.isArray(items) || items.length === 0) {
      return reply.code(400).send({ error: "no items" });
    }

    const queue = [...items];
    const max = 4;
    const runners = Array.from({ length: max }, async () => {
      while (queue.length > 0) {
        const item = queue.shift();
        if (!item) break;
        try {
          await importDirectLink(deps.db, item.url, deps.videoDir, item.metadata);
        } catch (err) {
          app.log.error({ err, url: item.url }, "bulk import item failed");
        }
      }
    });
    Promise.all(runners).catch((err) =>
      app.log.error({ err }, "bulk import runners crashed"),
    );

    return reply.code(202).send({ queued: items.length });
  });
```

- [ ] **Step 4: Run tests**

```bash
cd /Users/ayysir/Desktop/Hikari/backend && npm test -- tests/api/videos-bulk.test.ts 2>&1 | tail -20
```
Expected: 400-case tests pass. 202-test passes if the mock works; if it doesn't, simplify to only 400 tests as noted in Step 1.

- [ ] **Step 5: Run full suite**

```bash
cd /Users/ayysir/Desktop/Hikari/backend && npm test 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add backend/src/api/videos.ts backend/tests/api/videos-bulk.test.ts
git commit -m "feat(api): POST /videos/import/bulk with per-item metadata + concurrency 4"
```

---

## Phase 1 — Android Data Layer

### Task 1.1: New DTOs

**Files:**
- Modify: `android/app/src/main/java/com/hikari/app/data/api/dto/ImportVideosDto.kt`

- [ ] **Step 1: Replace file content fully**

Replace the entire contents of `android/app/src/main/java/com/hikari/app/data/api/dto/ImportVideosDto.kt` with:

```kotlin
package com.hikari.app.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ImportItemMetadata(
    val title: String? = null,
    @SerialName("seriesId")     val seriesId: String? = null,
    @SerialName("seriesTitle")  val seriesTitle: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    @SerialName("dubLanguage")  val dubLanguage: String? = null,
    @SerialName("subLanguage")  val subLanguage: String? = null,
    @SerialName("isMovie")      val isMovie: Boolean? = null,
)

@Serializable
data class BulkImportItem(
    val url: String,
    val metadata: ImportItemMetadata? = null,
)

@Serializable
data class BulkImportRequest(
    val items: List<BulkImportItem>,
)

@Serializable
data class BulkImportResponse(
    val queued: Int,
)

@Serializable
data class AnalyzeRequest(val url: String)

@Serializable
data class AiMeta(
    @SerialName("seriesTitle") val seriesTitle: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    @SerialName("dubLanguage") val dubLanguage: String? = null,
    @SerialName("subLanguage") val subLanguage: String? = null,
    @SerialName("isMovie")     val isMovie: Boolean? = null,
)

@Serializable
data class AnalyzeResponse(
    val url: String,
    val title: String? = null,
    val description: String? = null,
    @SerialName("thumbnailUrl") val thumbnailUrl: String? = null,
    @SerialName("aiMeta")       val aiMeta: AiMeta? = null,
)

@Serializable
data class SeriesItemDto(
    val id: String,
    val title: String,
)
```

The existing `ImportVideosRequest` and `ImportVideosResponse` are removed. Any leftover usage will be a compile error and is fixed in subsequent tasks.

- [ ] **Step 2: Compile check**

```bash
cd /Users/ayysir/Desktop/Hikari/android && ./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```
Expected: Compile FAILS — `ImportVideosRequest`/`ImportVideosResponse` are referenced from `HikariApi.kt`, `ChannelsRepository.kt`, `ChannelsViewModel.kt`. That's expected; we fix in 1.2 and beyond.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/hikari/app/data/api/dto/ImportVideosDto.kt
git commit -m "feat(import): new DTOs for analyze + bulk-import + series list"
```

### Task 1.2: HikariApi additions + tests

**Files:**
- Modify: `android/app/src/main/java/com/hikari/app/data/api/HikariApi.kt`
- Create: `android/app/src/test/java/com/hikari/app/data/api/VideosBulkApiTest.kt`

- [ ] **Step 1: Update HikariApi imports + endpoints**

In `android/app/src/main/java/com/hikari/app/data/api/HikariApi.kt`:

Find the existing imports and remove `import com.hikari.app.data.api.dto.ImportVideosRequest` and `import com.hikari.app.data.api.dto.ImportVideosResponse`. Add:

```kotlin
import com.hikari.app.data.api.dto.AnalyzeRequest
import com.hikari.app.data.api.dto.AnalyzeResponse
import com.hikari.app.data.api.dto.BulkImportRequest
import com.hikari.app.data.api.dto.BulkImportResponse
import com.hikari.app.data.api.dto.SeriesItemDto
```

Find the existing endpoint:

```kotlin
@POST("videos/import")
suspend fun importVideos(@Body req: ImportVideosRequest): ImportVideosResponse
```

Replace it with:

```kotlin
@POST("videos/analyze")
suspend fun analyzeVideo(@Body req: AnalyzeRequest): AnalyzeResponse

@POST("videos/import/bulk")
suspend fun importVideosBulk(@Body req: BulkImportRequest): BulkImportResponse

@GET("series")
suspend fun listSeries(): List<SeriesItemDto>
```

- [ ] **Step 2: Write API tests**

Create `android/app/src/test/java/com/hikari/app/data/api/VideosBulkApiTest.kt`:

```kotlin
package com.hikari.app.data.api

import com.hikari.app.data.api.dto.AnalyzeRequest
import com.hikari.app.data.api.dto.BulkImportItem
import com.hikari.app.data.api.dto.BulkImportRequest
import com.hikari.app.data.api.dto.ImportItemMetadata
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VideosBulkApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: HikariApi

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true }
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(HikariApi::class.java)
    }
    @After fun tearDown() { server.shutdown() }

    @Test fun analyzeVideo_parsesAiMeta() = runBlocking {
        server.enqueue(MockResponse().setBody("""
            {"url":"https://x.test/abc","title":"Auto Title","description":"d",
             "thumbnailUrl":"https://x.test/t.jpg",
             "aiMeta":{"seriesTitle":"X","season":1,"episode":7,
                       "dubLanguage":"de","subLanguage":null,"isMovie":false}}
        """.trimIndent()))
        val r = api.analyzeVideo(AnalyzeRequest("https://x.test/abc"))
        assertEquals("Auto Title", r.title)
        assertEquals("X", r.aiMeta?.seriesTitle)
        assertEquals(1, r.aiMeta?.season)
        assertEquals(7, r.aiMeta?.episode)
    }

    @Test fun importVideosBulk_serializesPerItemMetadata() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(202).setBody("""{"queued":2}"""))
        val r = api.importVideosBulk(BulkImportRequest(listOf(
            BulkImportItem(url = "https://x.test/1"),
            BulkImportItem(
                url = "https://x.test/2",
                metadata = ImportItemMetadata(
                    title = "Custom Title",
                    seriesTitle = "One Piece",
                    season = 1, episode = 7,
                    dubLanguage = "de", subLanguage = null,
                ),
            ),
        )))
        assertEquals(2, r.queued)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertTrue(req.path?.contains("/videos/import/bulk") == true)
        val body = req.body.readUtf8()
        assertTrue(body.contains("\"url\":\"https://x.test/1\""))
        assertTrue(body.contains("\"title\":\"Custom Title\""))
        assertTrue(body.contains("\"seriesTitle\":\"One Piece\""))
        assertTrue(body.contains("\"episode\":7"))
    }

    @Test fun listSeries_parsesArray() = runBlocking {
        server.enqueue(MockResponse().setBody("""
            [{"id":"s1","title":"One Piece"},{"id":"s2","title":"Naruto"}]
        """.trimIndent()))
        val list = api.listSeries()
        assertEquals(2, list.size)
        assertEquals("One Piece", list[0].title)
    }
}
```

- [ ] **Step 3: Build will still fail** — ChannelsRepository and ChannelsViewModel still reference removed `ImportVideosRequest`. Continue to next task.

```bash
cd /Users/ayysir/Desktop/Hikari/android && ./gradlew :app:compileDebugKotlin 2>&1 | tail -10 || true
```
Expected: still failing on ChannelsRepository / ChannelsViewModel. Tests can't run yet.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/hikari/app/data/api/HikariApi.kt \
        android/app/src/test/java/com/hikari/app/data/api/VideosBulkApiTest.kt
git commit -m "feat(import): HikariApi analyze + bulk-import + listSeries (+tests)"
```

### Task 1.3: ChannelsRepository updates

**Files:**
- Modify: `android/app/src/main/java/com/hikari/app/domain/repo/ChannelsRepository.kt`

- [ ] **Step 1: Read the current file**

```bash
cat /Users/ayysir/Desktop/Hikari/android/app/src/main/java/com/hikari/app/domain/repo/ChannelsRepository.kt | grep -n "importVideos\|class ChannelsRepository"
```

Identify the existing `importVideos(urls: List<String>): Int` method. Replace it.

- [ ] **Step 2: Replace `importVideos` with new methods**

In `ChannelsRepository.kt`, find:

```kotlin
suspend fun importVideos(urls: List<String>): Int =
    api.importVideos(com.hikari.app.data.api.dto.ImportVideosRequest(urls)).queued
```

Replace with:

```kotlin
suspend fun analyzeVideo(url: String) = api.analyzeVideo(
    com.hikari.app.data.api.dto.AnalyzeRequest(url),
)

suspend fun importVideosBulk(items: List<com.hikari.app.data.api.dto.BulkImportItem>): Int =
    api.importVideosBulk(com.hikari.app.data.api.dto.BulkImportRequest(items)).queued

suspend fun listSeries(): List<com.hikari.app.data.api.dto.SeriesItemDto> =
    api.listSeries()
```

- [ ] **Step 3: Compile check**

```bash
cd /Users/ayysir/Desktop/Hikari/android && ./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```
Expected: still failing — `ChannelsViewModel.importVideos(urls)` is still referenced by `ChannelsScreen.kt`. We fix that in Task 3.4.

- [ ] **Step 4: Run the API test isolation**

```bash
cd /Users/ayysir/Desktop/Hikari/android && ./gradlew :app:testDebugUnitTest --tests "com.hikari.app.data.api.VideosBulkApiTest" 2>&1 | tail -10
```
Expected: 3 tests pass (the test compiles and runs even if the production source has unresolved refs in OTHER files, because Gradle's test compile only compiles what's needed).

If gradle complains about other unresolved refs blocking test compile: skip this verification and continue. The test will pass once the rest is fixed.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/hikari/app/domain/repo/ChannelsRepository.kt
git commit -m "feat(import): ChannelsRepository analyze + bulk + listSeries"
```

---

## Phase 2 — ImportSheetViewModel

### Task 2.1: ImportSheetViewModel

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/ui/channels/ImportSheetViewModel.kt`
- Create: `android/app/src/test/java/com/hikari/app/ui/channels/ImportSheetViewModelTest.kt`

- [ ] **Step 1: Write failing tests**

Create `android/app/src/test/java/com/hikari/app/ui/channels/ImportSheetViewModelTest.kt`:

```kotlin
package com.hikari.app.ui.channels

import app.cash.turbine.test
import com.hikari.app.data.api.dto.AiMeta
import com.hikari.app.data.api.dto.AnalyzeResponse
import com.hikari.app.data.api.dto.BulkImportItem
import com.hikari.app.data.api.dto.SeriesItemDto
import com.hikari.app.domain.repo.ChannelsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ImportSheetViewModelTest {
    private val repo = mockk<ChannelsRepository>(relaxUnitFun = true)

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        coEvery { repo.listSeries() } returns listOf(SeriesItemDto("s1", "One Piece"))
    }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test(timeout = 5_000) fun init_loadsSeriesList() = runTest {
        val vm = ImportSheetViewModel(repo)
        vm.uiState.test(timeout = kotlin.time.Duration.parse("4s")) {
            // Initial state has empty series; after init it loads
            awaitItem()
            advanceUntilIdle()
            val s = awaitItem()
            assertEquals(1, s.allSeries.size)
            assertEquals("One Piece", s.allSeries[0].title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test(timeout = 5_000) fun onInputChanged_debouncesUrlParse() = runTest {
        coEvery { repo.analyzeVideo(any()) } returns AnalyzeResponse(
            url = "https://x.test/1", title = "T",
        )
        val vm = ImportSheetViewModel(repo)
        advanceUntilIdle() // let init finish
        vm.onInputChanged("https://x.test/1")
        advanceTimeBy(200) // less than debounce
        // Cards should still be empty
        assertTrue(vm.uiState.value.cards.isEmpty())
        advanceTimeBy(500)
        advanceUntilIdle()
        // Now we should have one card
        assertEquals(1, vm.uiState.value.cards.size)
    }

    @Test(timeout = 10_000) fun analyze_success_fillsReadyCard() = runTest {
        coEvery { repo.analyzeVideo("https://x.test/1") } returns AnalyzeResponse(
            url = "https://x.test/1",
            title = "Title One",
            thumbnailUrl = "https://x.test/t.jpg",
            aiMeta = AiMeta(seriesTitle = "One Piece", season = 1, episode = 7),
        )
        val vm = ImportSheetViewModel(repo)
        advanceUntilIdle()
        vm.onInputChanged("https://x.test/1")
        advanceTimeBy(700)
        advanceUntilIdle()
        val card = vm.uiState.value.cards.first()
        assertTrue(card is ImportCardState.Ready)
        val ready = card as ImportCardState.Ready
        assertEquals("Title One", ready.title)
        assertEquals("One Piece", ready.seriesTitle)
        assertEquals(7, ready.episode)
    }

    @Test(timeout = 10_000) fun analyze_failure_marksCardFailed() = runTest {
        coEvery { repo.analyzeVideo("https://x.test/1") } throws RuntimeException("yt-dlp failed")
        val vm = ImportSheetViewModel(repo)
        advanceUntilIdle()
        vm.onInputChanged("https://x.test/1")
        advanceTimeBy(700)
        advanceUntilIdle()
        val card = vm.uiState.value.cards.first()
        assertTrue(card is ImportCardState.Failed)
    }

    @Test(timeout = 10_000) fun removeCard_removesFromCardsAndRawInput() = runTest {
        coEvery { repo.analyzeVideo(any()) } returns AnalyzeResponse(url = "x", title = "T")
        val vm = ImportSheetViewModel(repo)
        advanceUntilIdle()
        vm.onInputChanged("https://x.test/1\nhttps://x.test/2")
        advanceTimeBy(700)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.cards.size)
        vm.removeCard("https://x.test/1")
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.cards.size)
        assertTrue(!vm.uiState.value.rawInput.contains("https://x.test/1"))
    }

    @Test(timeout = 10_000) fun submit_buildsRequestWithDefaultsFallback() = runTest {
        coEvery { repo.analyzeVideo("https://x.test/1") } returns AnalyzeResponse(
            url = "https://x.test/1", title = "T1",
        )
        val captured = slot<List<BulkImportItem>>()
        coEvery { repo.importVideosBulk(capture(captured)) } returns 1
        val vm = ImportSheetViewModel(repo)
        advanceUntilIdle()
        vm.onInputChanged("https://x.test/1")
        advanceTimeBy(700)
        advanceUntilIdle()
        // Set shared defaults: Series + dub language
        vm.updateDefaults { copy(seriesTitle = "One Piece", dubLanguage = "de") }
        val n = vm.submit()
        advanceUntilIdle()
        assertEquals(1, n)
        coVerify { repo.importVideosBulk(any()) }
        val item = captured.captured.first()
        assertEquals("One Piece", item.metadata?.seriesTitle)
        assertEquals("de", item.metadata?.dubLanguage)
    }
}
```

**Note on test timing:** Card analysis runs on `viewModelScope` which uses `Dispatchers.Main`. With `Dispatchers.setMain(StandardTestDispatcher())` and `runTest` test scheduler, virtual time advances cooperate. If a test hangs, ensure `advanceUntilIdle()` is called after the time advance, and that `vm.stopAnalyses()` (or similar cleanup) is called at the end of long-running tests. The reader uses the same pattern — see `MangaReaderViewModelTest`.

- [ ] **Step 2: Run, verify failure**

```bash
cd /Users/ayysir/Desktop/Hikari/android && ./gradlew :app:testDebugUnitTest --tests "com.hikari.app.ui.channels.ImportSheetViewModelTest" 2>&1 | tail -10
```
Expected: COMPILATION FAILED — `ImportSheetViewModel`, `ImportCardState`, `SharedDefaults` unresolved.

- [ ] **Step 3: Implement ViewModel**

Create `android/app/src/main/java/com/hikari/app/ui/channels/ImportSheetViewModel.kt`:

```kotlin
package com.hikari.app.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.BulkImportItem
import com.hikari.app.data.api.dto.ImportItemMetadata
import com.hikari.app.data.api.dto.SeriesItemDto
import com.hikari.app.domain.repo.ChannelsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

sealed interface ImportCardState {
    val url: String

    data class Loading(override val url: String) : ImportCardState

    data class Ready(
        override val url: String,
        val title: String,
        val thumbnailUrl: String? = null,
        val seriesId: String? = null,
        val seriesTitle: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val dubLanguage: String? = null,
        val subLanguage: String? = null,
        val isMovie: Boolean = false,
        val expanded: Boolean = false,
    ) : ImportCardState

    data class Failed(
        override val url: String,
        val error: String,
    ) : ImportCardState
}

data class SharedDefaults(
    val seriesId: String? = null,
    val seriesTitle: String? = null,
    val season: Int? = null,
    val dubLanguage: String? = null,
    val subLanguage: String? = null,
)

data class ImportSheetUiState(
    val rawInput: String = "",
    val cards: List<ImportCardState> = emptyList(),
    val defaults: SharedDefaults = SharedDefaults(),
    val allSeries: List<SeriesItemDto> = emptyList(),
    val submitting: Boolean = false,
    val submitError: String? = null,
)

@HiltViewModel
class ImportSheetViewModel @Inject constructor(
    private val repo: ChannelsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportSheetUiState())
    val uiState: StateFlow<ImportSheetUiState> = _uiState.asStateFlow()

    private var inputDebounceJob: Job? = null

    init {
        viewModelScope.launch {
            runCatching { repo.listSeries() }
                .onSuccess { fetched -> _uiState.update { it.copy(allSeries = fetched) } }
        }
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(rawInput = text) }
        inputDebounceJob?.cancel()
        inputDebounceJob = viewModelScope.launch {
            delay(500)
            reconcileUrls(parseUrls(text))
        }
    }

    private fun parseUrls(text: String): List<String> =
        text.split('\n', ',')
            .map { it.trim() }
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .distinct()

    private suspend fun reconcileUrls(newUrls: List<String>) {
        val current = _uiState.value.cards
        val keep = current.filter { it.url in newUrls }
        val keepUrls = keep.map { it.url }.toSet()
        val fresh = newUrls.filterNot { it in keepUrls }
        val withLoaders = keep + fresh.map { ImportCardState.Loading(it) }
        _uiState.update { it.copy(cards = withLoaders) }

        coroutineScope {
            val sem = Semaphore(4)
            fresh.map { url ->
                async {
                    sem.withPermit {
                        val result = runCatching { repo.analyzeVideo(url) }
                        replaceCard(url) { _ ->
                            result.fold(
                                onSuccess = { r ->
                                    ImportCardState.Ready(
                                        url = url,
                                        title = r.title.orEmpty(),
                                        thumbnailUrl = r.thumbnailUrl,
                                        seriesTitle = r.aiMeta?.seriesTitle,
                                        season = r.aiMeta?.season,
                                        episode = r.aiMeta?.episode,
                                        dubLanguage = r.aiMeta?.dubLanguage,
                                        subLanguage = r.aiMeta?.subLanguage,
                                        isMovie = r.aiMeta?.isMovie ?: false,
                                    )
                                },
                                onFailure = { e ->
                                    ImportCardState.Failed(url, e.message ?: "Analyze fehlgeschlagen")
                                },
                            )
                        }
                    }
                }
            }.awaitAll()
        }
    }

    private fun replaceCard(url: String, transform: (ImportCardState) -> ImportCardState) {
        _uiState.update { state ->
            state.copy(cards = state.cards.map { if (it.url == url) transform(it) else it })
        }
    }

    fun updateCard(url: String, patch: ImportCardState.Ready.() -> ImportCardState.Ready) {
        replaceCard(url) {
            if (it is ImportCardState.Ready) it.patch() else it
        }
    }

    fun toggleExpanded(url: String) =
        updateCard(url) { copy(expanded = !expanded) }

    fun removeCard(url: String) {
        _uiState.update { state ->
            state.copy(
                cards = state.cards.filterNot { it.url == url },
                rawInput = state.rawInput.lines().filter { it.trim() != url }.joinToString("\n"),
            )
        }
    }

    fun retryCard(url: String) {
        replaceCard(url) { ImportCardState.Loading(url) }
        viewModelScope.launch {
            val result = runCatching { repo.analyzeVideo(url) }
            replaceCard(url) {
                result.fold(
                    onSuccess = { r ->
                        ImportCardState.Ready(
                            url = url,
                            title = r.title.orEmpty(),
                            thumbnailUrl = r.thumbnailUrl,
                            seriesTitle = r.aiMeta?.seriesTitle,
                            season = r.aiMeta?.season,
                            episode = r.aiMeta?.episode,
                            dubLanguage = r.aiMeta?.dubLanguage,
                            subLanguage = r.aiMeta?.subLanguage,
                            isMovie = r.aiMeta?.isMovie ?: false,
                        )
                    },
                    onFailure = { e ->
                        ImportCardState.Failed(url, e.message ?: "Analyze fehlgeschlagen")
                    },
                )
            }
        }
    }

    fun updateDefaults(transform: SharedDefaults.() -> SharedDefaults) {
        _uiState.update { it.copy(defaults = it.defaults.transform()) }
    }

    suspend fun submit(): Int? {
        val state = _uiState.value
        val items = state.cards.mapNotNull { card ->
            if (card !is ImportCardState.Ready) return@mapNotNull null
            BulkImportItem(
                url = card.url,
                metadata = ImportItemMetadata(
                    title = card.title.takeIf { it.isNotBlank() },
                    seriesId = card.seriesId ?: state.defaults.seriesId,
                    seriesTitle = card.seriesTitle ?: state.defaults.seriesTitle,
                    season = card.season ?: state.defaults.season,
                    episode = card.episode,
                    dubLanguage = card.dubLanguage ?: state.defaults.dubLanguage,
                    subLanguage = card.subLanguage ?: state.defaults.subLanguage,
                    isMovie = card.isMovie.takeIf { it },
                ),
            )
        }
        if (items.isEmpty()) return null
        _uiState.update { it.copy(submitting = true, submitError = null) }
        val n = runCatching { repo.importVideosBulk(items) }
            .onFailure { e ->
                _uiState.update {
                    it.copy(submitting = false, submitError = e.message ?: "Import fehlgeschlagen")
                }
            }
            .getOrNull()
        if (n != null) {
            _uiState.update { state ->
                ImportSheetUiState(allSeries = state.allSeries) // reset everything except series cache
            }
        }
        return n
    }
}
```

- [ ] **Step 4: Run tests**

```bash
cd /Users/ayysir/Desktop/Hikari/android && ./gradlew :app:testDebugUnitTest --tests "com.hikari.app.ui.channels.ImportSheetViewModelTest" 2>&1 | tail -15
```
Expected: 6 tests pass.

If tests hang on `advanceUntilIdle()` because `viewModelScope` jobs don't terminate: add a `stopPolling()`-style cancellation method to the VM and call it in test `tearDown`. The pattern is the same as `MangaReaderViewModel.stopPolling()`.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/hikari/app/ui/channels/ImportSheetViewModel.kt \
        android/app/src/test/java/com/hikari/app/ui/channels/ImportSheetViewModelTest.kt
git commit -m "feat(import): ImportSheetViewModel with debounced parse + parallel analyze"
```

---

## Phase 3 — UI Components

### Task 3.1: SeriesTypeahead component

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/ui/channels/components/SeriesTypeahead.kt`

- [ ] **Step 1: Write the component**

```kotlin
package com.hikari.app.ui.channels.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.hikari.app.data.api.dto.SeriesItemDto

private val Accent = Color(0xFFFBBF24)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesTypeahead(
    value: String,
    allSeries: List<SeriesItemDto>,
    onChange: (input: String, seriesId: String?, seriesTitle: String?) -> Unit,
    label: String = "Serie",
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val matches = remember(value, allSeries) {
        if (value.isBlank()) allSeries
        else allSeries.filter { it.title.startsWith(value, ignoreCase = true) }
    }
    val exactMatch = matches.firstOrNull { it.title.equals(value, ignoreCase = true) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { input ->
                // Free-text typing: clears existing-id binding, sets seriesTitle for new
                onChange(input, null, input.takeIf { it.isNotBlank() })
                expanded = true
            },
            label = { Text(label) },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            matches.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.title) },
                    onClick = {
                        onChange(s.title, s.id, null)
                        expanded = false
                    },
                )
            }
            if (value.isNotBlank() && exactMatch == null) {
                DropdownMenuItem(
                    text = { Text("+ Erstellen: \"$value\"", color = Accent) },
                    onClick = {
                        onChange(value, null, value)
                        expanded = false
                    },
                )
            }
        }
    }
}
```

- [ ] **Step 2: Build**

```bash
cd /Users/ayysir/Desktop/Hikari/android && ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```
Expected: still failing because `ChannelsScreen.kt`'s `ImportSheet` still references the old broken API. That's fixed in Task 3.4. The new component itself should compile.

To isolate-check that this file compiles, check that the unresolved-reference errors do NOT include `SeriesTypeahead`:

```bash
cd /Users/ayysir/Desktop/Hikari/android && ./gradlew :app:compileDebugKotlin 2>&1 | grep -i "SeriesTypeahead\|ExposedDropdown" | head
```
Expected: no errors mentioning these.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/hikari/app/ui/channels/components/SeriesTypeahead.kt
git commit -m "feat(import): SeriesTypeahead component"
```

### Task 3.2: SharedDefaultsBlock component

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/ui/channels/components/SharedDefaultsBlock.kt`

- [ ] **Step 1: Write the component**

```kotlin
package com.hikari.app.ui.channels.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.data.api.dto.SeriesItemDto
import com.hikari.app.ui.channels.SharedDefaults

@Composable
fun SharedDefaultsBlock(
    defaults: SharedDefaults,
    allSeries: List<SeriesItemDto>,
    onUpdate: (SharedDefaults.() -> SharedDefaults) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF111111))
            .border(0.5.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Defaults für alle",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
        )
        SeriesTypeahead(
            value = defaults.seriesTitle.orEmpty(),
            allSeries = allSeries,
            onChange = { _, sid, stitle ->
                onUpdate { copy(seriesId = sid, seriesTitle = stitle) }
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = defaults.season?.toString().orEmpty(),
                onValueChange = { input ->
                    val v = input.toIntOrNull()
                    onUpdate { copy(season = v) }
                },
                label = { Text("Staffel") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(100.dp),
            )
            OutlinedTextField(
                value = defaults.dubLanguage.orEmpty(),
                onValueChange = { input -> onUpdate { copy(dubLanguage = input.takeIf { it.isNotBlank() }) } },
                label = { Text("Sprache") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = defaults.subLanguage.orEmpty(),
            onValueChange = { input -> onUpdate { copy(subLanguage = input.takeIf { it.isNotBlank() }) } },
            label = { Text("Untertitel") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
```

- [ ] **Step 2: Compile check**

```bash
cd /Users/ayysir/Desktop/Hikari/android && ./gradlew :app:compileDebugKotlin 2>&1 | grep "SharedDefaultsBlock" | head -3
```
Expected: no errors mentioning `SharedDefaultsBlock`.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/hikari/app/ui/channels/components/SharedDefaultsBlock.kt
git commit -m "feat(import): SharedDefaultsBlock — applies-to-all defaults card"
```

### Task 3.3: ImportCard component

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/ui/channels/components/ImportCard.kt`

- [ ] **Step 1: Write the component**

```kotlin
package com.hikari.app.ui.channels.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.data.api.dto.SeriesItemDto
import com.hikari.app.ui.channels.ImportCardState

private val Accent = Color(0xFFFBBF24)
private val FailedRed = Color(0xFFEF4444)

@Composable
fun ImportCard(
    card: ImportCardState,
    allSeries: List<SeriesItemDto>,
    onToggleExpand: () -> Unit,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
    onPatchReady: ((ImportCardState.Ready) -> ImportCardState.Ready) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF111111))
            .border(0.5.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        when (card) {
            is ImportCardState.Loading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 60.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF1A1A1A)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Analysiere…", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                        Text(
                            card.url,
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 10.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
            is ImportCardState.Failed -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Analyze fehlgeschlagen", color = FailedRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(card.error, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, maxLines = 2)
                        Text(card.url, color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp, maxLines = 1)
                    }
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = Accent)
                    }
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Close, contentDescription = "Entfernen", tint = Color.White.copy(alpha = 0.4f))
                    }
                }
            }
            is ImportCardState.Ready -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onToggleExpand),
                ) {
                    if (card.thumbnailUrl != null) {
                        AsyncImage(
                            model = card.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(width = 40.dp, height = 60.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1A1A1A)),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(width = 40.dp, height = 60.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1A1A1A)),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            card.title.ifBlank { "(kein Titel)" },
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        Text(
                            card.url,
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 10.sp,
                            maxLines = 1,
                        )
                        if (card.episode != null) {
                            Text(
                                "S${card.season ?: '-'} · E${card.episode}",
                                color = Accent,
                                fontSize = 10.sp,
                            )
                        }
                    }
                    Icon(
                        imageVector = if (card.expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (card.expanded) "Zuklappen" else "Aufklappen",
                        tint = Color.White.copy(alpha = 0.4f),
                    )
                }
                AnimatedVisibility(visible = card.expanded) {
                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = card.title,
                            onValueChange = { v -> onPatchReady { it -> it.copy(title = v) } },
                            label = { Text("Titel") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Film", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Switch(
                                checked = card.isMovie,
                                onCheckedChange = { v -> onPatchReady { it -> it.copy(isMovie = v) } },
                            )
                        }
                        AnimatedVisibility(visible = !card.isMovie) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SeriesTypeahead(
                                    value = card.seriesTitle.orEmpty(),
                                    allSeries = allSeries,
                                    onChange = { _, sid, stitle ->
                                        onPatchReady { it -> it.copy(seriesId = sid, seriesTitle = stitle) }
                                    },
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = card.season?.toString().orEmpty(),
                                        onValueChange = { input ->
                                            val v = input.toIntOrNull()
                                            onPatchReady { it -> it.copy(season = v) }
                                        },
                                        label = { Text("Staffel") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                    )
                                    OutlinedTextField(
                                        value = card.episode?.toString().orEmpty(),
                                        onValueChange = { input ->
                                            val v = input.toIntOrNull()
                                            onPatchReady { it -> it.copy(episode = v) }
                                        },
                                        label = { Text("Folge") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = card.dubLanguage.orEmpty(),
                                onValueChange = { input ->
                                    onPatchReady { it -> it.copy(dubLanguage = input.takeIf { v -> v.isNotBlank() }) }
                                },
                                label = { Text("Sprache") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = card.subLanguage.orEmpty(),
                                onValueChange = { input ->
                                    onPatchReady { it -> it.copy(subLanguage = input.takeIf { v -> v.isNotBlank() }) }
                                },
                                label = { Text("Untertitel") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            IconButton(onClick = onRemove) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Entfernen",
                                    tint = Color.White.copy(alpha = 0.4f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Compile check**

```bash
cd /Users/ayysir/Desktop/Hikari/android && ./gradlew :app:compileDebugKotlin 2>&1 | grep "ImportCard\.kt" | head -5
```
Expected: no errors specific to `ImportCard.kt`.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/hikari/app/ui/channels/components/ImportCard.kt
git commit -m "feat(import): ImportCard component (Loading/Failed/Ready states)"
```

### Task 3.4: Replace ImportSheet integration

**Files:**
- Modify: `android/app/src/main/java/com/hikari/app/ui/channels/ChannelsScreen.kt`
- Possibly modify: `android/app/src/main/java/com/hikari/app/ui/channels/ChannelsViewModel.kt`

- [ ] **Step 1: Read ChannelsScreen.kt to understand current ImportSheet usage**

```bash
grep -n "ImportSheet\|importVideos" /Users/ayysir/Desktop/Hikari/android/app/src/main/java/com/hikari/app/ui/channels/ChannelsScreen.kt | head -20
```

Identify (a) where `ImportSheet(...)` is invoked, (b) where the existing private `ImportSheet` composable is defined, (c) any `vm.importVideos(urls)` call from the `onImport` callback.

- [ ] **Step 2: Replace the existing private `ImportSheet` composable**

Find `private fun ImportSheet(...)` (around line 655 per `grep`). Replace its full body with:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportSheet(
    onDismiss: () -> Unit,
    vm: com.hikari.app.ui.channels.ImportSheetViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state by vm.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HikariBg,
        contentColor = HikariText,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Videos importieren",
                color = HikariText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = state.rawInput,
                onValueChange = vm::onInputChanged,
                placeholder = { Text("URLs hier einfügen (eine pro Zeile)…") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                maxLines = 6,
            )

            if (state.cards.isNotEmpty()) {
                com.hikari.app.ui.channels.components.SharedDefaultsBlock(
                    defaults = state.defaults,
                    allSeries = state.allSeries,
                    onUpdate = { transform -> vm.updateDefaults(transform) },
                )

                state.cards.forEach { card ->
                    com.hikari.app.ui.channels.components.ImportCard(
                        card = card,
                        allSeries = state.allSeries,
                        onToggleExpand = { vm.toggleExpanded(card.url) },
                        onRemove = { vm.removeCard(card.url) },
                        onRetry = { vm.retryCard(card.url) },
                        onPatchReady = { transform ->
                            vm.updateCard(card.url) { transform(this) }
                        },
                    )
                }
            }

            state.submitError?.let { err ->
                Text(err, color = Color(0xFFEF4444), fontSize = 12.sp)
            }

            // Sticky-feeling submit row at the bottom of the scrollable column
            val readyCount = state.cards.count { it is com.hikari.app.ui.channels.ImportCardState.Ready }
            val anyLoading = state.cards.any { it is com.hikari.app.ui.channels.ImportCardState.Loading }
            Button(
                onClick = {
                    scope.launch {
                        val n = vm.submit()
                        if (n != null) onDismiss()
                    }
                },
                enabled = readyCount > 0 && !anyLoading && !state.submitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        state.submitting -> "Importiere…"
                        anyLoading -> "Analysiere $readyCount von ${state.cards.size}…"
                        readyCount == 0 -> "Keine URLs"
                        else -> "$readyCount Importieren"
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
```

Add the necessary imports at the top of `ChannelsScreen.kt`:

```kotlin
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
```

(The existing `ChannelsScreen.kt` already imports `Button`, `Column`, `Modifier`, `Arrangement`, `padding`, `fillMaxWidth`, `Text`, `FontWeight`, `Color`, `dp`, `sp`, `ModalBottomSheet`, `rememberModalBottomSheetState`, `ExperimentalMaterial3Api`, `HikariBg`, `HikariText`. If any of those are missing — flagged by the build error — add the missing one.)

- [ ] **Step 3: Update the call-site of `ImportSheet`**

Find the call (around line 139 per earlier `grep`) that passes `onImport = { urls -> vm.importVideos(urls) { _: Int -> } }`. Update to:

```kotlin
ImportSheet(onDismiss = { showImportSheet = false })
```

(The `urls` and the previous `onImport` callback are no longer needed — the new `ImportSheet` owns its ViewModel and handles submit internally, dismissing on success.)

- [ ] **Step 4: Remove unused `importVideos` from ChannelsViewModel**

In `ChannelsViewModel.kt`, find and remove:

```kotlin
fun importVideos(urls: List<String>, onDone: (Int) -> Unit) = viewModelScope.launch {
    runCatching { repo.importVideos(urls) }
        .onSuccess(onDone)
}
```

It was the only place calling the now-removed `repo.importVideos(urls)`.

- [ ] **Step 5: Build**

```bash
cd /Users/ayysir/Desktop/Hikari/android && ./gradlew :app:compileDebugKotlin 2>&1 | tail -15
```
Expected: BUILD SUCCESSFUL.

If there are leftover unresolved references — likely just imports — fix them and re-run.

- [ ] **Step 6: Run full test suite**

```bash
cd /Users/ayysir/Desktop/Hikari/android && ./gradlew :app:testDebugUnitTest 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL — all tests pass.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/java/com/hikari/app/ui/channels/ChannelsScreen.kt \
        android/app/src/main/java/com/hikari/app/ui/channels/ChannelsViewModel.kt
git commit -m "feat(import): wire new ImportSheet — bulk paste with per-URL form"
```

---

## Phase 4 — Validation + APK

### Task 4.1: Full E2E + APK build

- [ ] **Step 1: Backend test suite**

```bash
cd /Users/ayysir/Desktop/Hikari/backend && npm test 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 2: Android test suite**

```bash
cd /Users/ayysir/Desktop/Hikari/android && ./gradlew :app:testDebugUnitTest 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Build debug APK**

```bash
cd /Users/ayysir/Desktop/Hikari/android && ./gradlew :app:assembleDebug 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL. APK at `android/app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 4: Bump version**

In `android/app/build.gradle.kts`:

```kotlin
versionCode = 15
versionName = "0.14.0"
```

(Manga work brought it to 0.13.2/14. This is a new feature → minor bump 0.14.0.)

- [ ] **Step 5: Commit + tag**

```bash
git add android/app/build.gradle.kts
git commit -m "release: Android 0.14.0 — Import-Form mit Per-URL-Edit"
git push origin main
git tag -a android-mvp-v0.14.0 -m "Android 0.14.0 — Bulk import with per-URL form"
git push origin android-mvp-v0.14.0
```

- [ ] **Step 6: Manual E2E on phone**

1. Install fresh APK
2. ChannelsScreen → tap "+ Videos importieren" (or whatever the existing trigger is)
3. Sheet opens with empty Textarea
4. Paste 3 VOE-URLs (eine pro Zeile) — wait 500ms
5. 3 Loading-Cards erscheinen → werden Ready mit auto-Title + Thumbnail
6. SharedDefaultsBlock: Series „One Piece" via Typeahead wählen, Staffel 1
7. Card 2 expanden → Episode 7 setzen
8. Submit-Button zeigt "3 Importieren"
9. Tap → Toast „3 Videos werden importiert", Sheet schließt
10. Library-Tab → 3 neue Videos in der „One Piece" Series-Gruppe

Falls Cards nie Ready werden: `adb logcat -s OkHttp:* HikariApi:*` und prüfen, ob `/videos/analyze` aufgerufen wird.

- [ ] **Step 7: GitHub Release**

```bash
cp /Users/ayysir/Desktop/Hikari/android/app/build/outputs/apk/debug/app-debug.apk \
   ~/Desktop/Hikari-0.14.0-import-form.apk

gh release create android-mvp-v0.14.0 \
  --title "Android MVP v0.14.0 — Import-Form mit Per-URL-Edit" \
  --notes "## Bulk-Import erweitert

Per-URL editierbare Cards: Title, Series-Picker (Typeahead, existierend oder neu), Staffel/Folge, Sprache, Untertitel.
Shared-Defaults oben für die ganze Bulk-Sequenz mit Per-Card-Override.
Backend bekommt POST /videos/import/bulk + GET /series.

Drüberinstallieren über v0.13.x — gleiche Signatur, DataStore bleibt." \
  ~/Desktop/Hikari-0.14.0-import-form.apk
```

---

## Done

When everything in Phase 4 passes:
- Backend hat `POST /videos/import/bulk`, `GET /series`, und `manualMeta.title`-Override
- Android-Client hat das neue `ImportSheet` mit Per-URL-Cards + Shared-Defaults + Series-Typeahead
- Alle Tests grün, APK gebaut, GitHub Release live
- Manueller Smoke-Test auf'm Handy bestätigt Bulk-Import-Flow

Future work (out of scope):
- Web-Demo-Pendant
- Job-Status-Polling pro Item nach Submit (zeigt welche Imports gefailed sind)
- Saved-State (Sheet beim Reopen mit letztem Stand wiederherstellen)
- Sprach-Dropdowns mit vordefinierten Optionen statt Free-Text
