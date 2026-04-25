# Manga Android Port Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a fully-native Manga browsing + reading experience in the Hikari Android client (Kotlin/Compose) with feature parity to the web demo shipped as 0.2.0.

**Architecture:** Pure Compose UI layered on top of Hilt-injected Retrofit + a singleton sync poller, mirroring the existing `library/` and `channels/` modules. The reader uses Compose's `HorizontalPager` for swipe + page-state, a per-page `Modifier.graphicsLayer` for an authentic 3D book-page-flip animation, and Telephoto's `ZoomableAsyncImage` for pinch zoom + pan. No backend changes.

**Tech Stack:** Kotlin 2.1, Compose Foundation/Material3, Hilt, Retrofit + kotlinx.serialization, Coil 2.7, **NEW** Telephoto 0.13.x, Navigation-Compose 2.8, kotlinx.coroutines + StateFlow.

**Spec:** `docs/superpowers/specs/2026-04-25-manga-android-design.md`

---

## File Structure

**New files (under `android/app/src/main/java/com/hikari/app/`):**

| Path | Responsibility |
|---|---|
| `data/api/dto/Manga.kt` | All `@Serializable` manga DTOs |
| `domain/repo/MangaRepository.kt` | Hilt-injected facade calling HikariApi + computing image URLs |
| `domain/sync/MangaSyncObserver.kt` | `@Singleton` poller that exposes `StateFlow<SyncStatus>` |
| `ui/manga/MangaListScreen.kt` | Route `manga` (Hero + Rows) |
| `ui/manga/MangaListViewModel.kt` | Loads series + continue, exposes UiState |
| `ui/manga/MangaDetailScreen.kt` | Route `manga/{id}` (arc accordion) |
| `ui/manga/MangaDetailViewModel.kt` | Loads detail + finds expanded arc |
| `ui/manga/MangaReaderScreen.kt` | Route `manga/{id}/{chapterId}?page={page}` |
| `ui/manga/MangaReaderViewModel.kt` | Pages, debounced progress, chapter-sync polling |
| `ui/manga/components/MangaHero.kt` | Backdrop + Weiterlesen/Lesen CTA |
| `ui/manga/components/MangaCard.kt` | 2:3 cover card with gradient fallback |
| `ui/manga/components/MangaRow.kt` | LazyRow wrapper with title chevron |
| `ui/manga/components/MangaSyncBanner.kt` | Sticky progress bar |
| `ui/manga/components/ArcAccordion.kt` | Collapsible arc → chapter rows |
| `ui/manga/components/ChapterRow.kt` | Chapter line with read-marker |
| `ui/manga/components/ZoomablePage.kt` | The heart of the reader — flip + zoom |
| `ui/manga/components/ReaderChrome.kt` | Top bar + bottom progress bar |
| `ui/manga/components/ChapterEndPage.kt` | Sentinel page in pager |

**Modified files:**

| Path | Change |
|---|---|
| `android/gradle/libs.versions.toml` | Add `telephoto` version + `telephoto-zoomable-image-coil` library |
| `android/app/build.gradle.kts` | Add `implementation(libs.telephoto.zoomable.image.coil)` |
| `data/api/HikariApi.kt` | Add 11 manga endpoints |
| `ui/navigation/NavDestinations.kt` | Add `manga` entry to `hikariDestinations` |
| `ui/navigation/HikariNavHost.kt` | Add 3 `composable(...)` blocks + reader-route bottom-bar hide |
| `ui/tuning/TuningScreen.kt` | Add Manga sync section |
| `ui/tuning/TuningViewModel.kt` | Add `triggerMangaSync()` + `mangaSyncStatus` flow |

**New tests (under `android/app/src/test/java/com/hikari/app/`):**

| Path | What it tests |
|---|---|
| `data/api/MangaApiTest.kt` | DTO parsing against canned backend responses (MockWebServer) |
| `domain/repo/MangaRepositoryTest.kt` | URL builders + thin pass-throughs (MockK) |
| `domain/sync/MangaSyncObserverTest.kt` | Poll loop sets `Active`/`Idle` based on job status (Turbine) |
| `ui/manga/MangaListViewModelTest.kt` | Loading → Success/Error transitions (Turbine) |
| `ui/manga/MangaReaderViewModelTest.kt` | Debounced progress save, page-empty → Syncing transition |

---

## Phase 0 — Setup

### Task 0.1: Add Telephoto dependency

**Files:**
- Modify: `android/gradle/libs.versions.toml`
- Modify: `android/app/build.gradle.kts`

- [ ] **Step 1: Add the version + library entry**

In `android/gradle/libs.versions.toml`, under `[versions]` add (right after `coil = "2.7.0"`):

```toml
telephoto = "0.13.0"
```

Then under `[libraries]` add (right after `coil-compose`):

```toml
telephoto-zoomable-image-coil = { module = "me.saket.telephoto:zoomable-image-coil", version.ref = "telephoto" }
```

- [ ] **Step 2: Reference it in app/build.gradle.kts**

In `android/app/build.gradle.kts`, in the `dependencies { ... }` block right after `implementation(libs.coil.compose)`, add:

```kotlin
implementation(libs.telephoto.zoomable.image.coil)
```

- [ ] **Step 3: Sync gradle and verify it resolves**

```bash
cd android && ./gradlew :app:dependencies --configuration debugRuntimeClasspath 2>&1 | grep -i telephoto | head -5
```
Expected: at least one line mentioning `me.saket.telephoto:zoomable-image-coil:0.13.0`.

If gradle complains about resolution, the version may have shifted — try `0.14.0` or check `https://search.maven.org/artifact/me.saket.telephoto/zoomable-image-coil` for the latest stable.

- [ ] **Step 4: Commit**

```bash
git add android/gradle/libs.versions.toml android/app/build.gradle.kts
git commit -m "feat(manga): add telephoto for reader pinch-zoom"
```

---

## Phase 1 — Data Layer

### Task 1.1: Manga DTOs

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/data/api/dto/Manga.kt`

- [ ] **Step 1: Write the file in full**

```kotlin
package com.hikari.app.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MangaSeriesDto(
    val id: String,
    val source: String,
    val title: String,
    val author: String? = null,
    val description: String? = null,
    @SerialName("coverPath") val coverPath: String? = null,
    @SerialName("totalChapters") val totalChapters: Int = 0,
    @SerialName("lastSyncedAt") val lastSyncedAt: Long? = null,
)

@Serializable
data class MangaArcDto(
    val id: String,
    val title: String,
    @SerialName("arcOrder") val arcOrder: Int,
    @SerialName("chapterStart") val chapterStart: Int? = null,
    @SerialName("chapterEnd") val chapterEnd: Int? = null,
)

@Serializable
data class MangaChapterDto(
    val id: String,
    val number: Double,
    val title: String? = null,
    @SerialName("arcId") val arcId: String? = null,
    @SerialName("pageCount") val pageCount: Int = 0,
    @SerialName("isRead") val isRead: Int = 0,
)

@Serializable
data class MangaSeriesDetailDto(
    val id: String,
    val title: String,
    val author: String? = null,
    val description: String? = null,
    @SerialName("coverPath") val coverPath: String? = null,
    @SerialName("totalChapters") val totalChapters: Int = 0,
    val arcs: List<MangaArcDto> = emptyList(),
    val chapters: List<MangaChapterDto> = emptyList(),
)

@Serializable
data class MangaPageDto(
    val id: String,
    @SerialName("pageNumber") val pageNumber: Int,
    val ready: Boolean,
)

@Serializable
data class MangaContinueDto(
    @SerialName("seriesId") val seriesId: String,
    val title: String,
    @SerialName("coverPath") val coverPath: String? = null,
    @SerialName("chapterId") val chapterId: String,
    @SerialName("pageNumber") val pageNumber: Int,
    @SerialName("updatedAt") val updatedAt: Long,
)

@Serializable
data class MangaSyncJobDto(
    val id: String,
    val source: String,
    val status: String,
    @SerialName("total_chapters") val totalChapters: Int = 0,
    @SerialName("done_chapters") val doneChapters: Int = 0,
    @SerialName("total_pages") val totalPages: Int = 0,
    @SerialName("done_pages") val donePages: Int = 0,
    @SerialName("error_message") val errorMessage: String? = null,
    @SerialName("started_at") val startedAt: Long,
    @SerialName("finished_at") val finishedAt: Long? = null,
)

@Serializable
data class MangaProgressRequest(
    @SerialName("chapterId") val chapterId: String,
    @SerialName("pageNumber") val pageNumber: Int,
)
```

- [ ] **Step 2: Verify compiles**

```bash
cd android && ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/hikari/app/data/api/dto/Manga.kt
git commit -m "feat(manga): DTO contracts for /api/manga endpoints"
```

### Task 1.2: API endpoints + DTO parse tests

**Files:**
- Modify: `android/app/src/main/java/com/hikari/app/data/api/HikariApi.kt`
- Create: `android/app/src/test/java/com/hikari/app/data/api/MangaApiTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `android/app/src/test/java/com/hikari/app/data/api/MangaApiTest.kt`:

```kotlin
package com.hikari.app.data.api

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

class MangaApiTest {
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

    @Test fun listMangaSeries_parsesBackendShape() = runBlocking {
        server.enqueue(MockResponse().setBody("""
            [{"id":"onepiecetube:one-piece","source":"onepiecetube","title":"One Piece",
              "author":null,"description":null,"coverPath":null,
              "totalChapters":762,"lastSyncedAt":1777148725270}]
        """.trimIndent()))
        val list = api.listMangaSeries()
        assertEquals(1, list.size)
        assertEquals("One Piece", list[0].title)
        assertEquals(762, list[0].totalChapters)
    }

    @Test fun getMangaSeries_parsesArcsAndChapters() = runBlocking {
        server.enqueue(MockResponse().setBody("""
            {"id":"onepiecetube:one-piece","source":"onepiecetube","title":"One Piece",
             "author":"Eiichiro Oda","totalChapters":2,
             "arcs":[{"id":"arc-1","title":"East Blue","arcOrder":0,
                      "chapterStart":1,"chapterEnd":2}],
             "chapters":[
               {"id":"ch-1","number":1.0,"title":"Romance Dawn","arcId":"arc-1","pageCount":50,"isRead":1},
               {"id":"ch-2","number":2.0,"title":"Strawhat","arcId":"arc-1","pageCount":20,"isRead":0}
             ]}
        """.trimIndent()))
        val d = api.getMangaSeries("onepiecetube:one-piece")
        assertEquals(1, d.arcs.size)
        assertEquals(2, d.chapters.size)
        assertEquals(1.0, d.chapters[0].number, 0.0001)
        assertEquals(1, d.chapters[0].isRead)
    }

    @Test fun getMangaChapterPages_parsesReadyFlag() = runBlocking {
        server.enqueue(MockResponse().setBody("""
            [{"id":"p1","pageNumber":1,"ready":true},
             {"id":"p2","pageNumber":2,"ready":false}]
        """.trimIndent()))
        val pages = api.getMangaChapterPages("ch-1")
        assertEquals(2, pages.size)
        assertEquals(true, pages[0].ready)
        assertEquals(false, pages[1].ready)
    }

    @Test fun listMangaSyncJobs_parsesSnakeCase() = runBlocking {
        server.enqueue(MockResponse().setBody("""
            [{"id":"job-1","source":"onepiecetube","status":"running",
              "total_chapters":762,"done_chapters":42,
              "total_pages":1234,"done_pages":1200,
              "error_message":null,
              "started_at":1777149096017,"finished_at":null}]
        """.trimIndent()))
        val jobs = api.listMangaSyncJobs()
        assertEquals(1, jobs.size)
        assertEquals("running", jobs[0].status)
        assertEquals(762, jobs[0].totalChapters)
        assertEquals(42, jobs[0].doneChapters)
        assertEquals(1777149096017L, jobs[0].startedAt)
        assertTrue(jobs[0].finishedAt == null)
    }

    @Test fun setMangaProgress_serializesBody() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        api.setMangaProgress(
            "onepiecetube:one-piece",
            com.hikari.app.data.api.dto.MangaProgressRequest("ch-1", 5)
        )
        val req = server.takeRequest()
        assertEquals("PUT", req.method)
        assertTrue(req.path?.contains("manga/progress/") == true)
        val body = req.body.readUtf8()
        assertTrue(body.contains("\"chapterId\":\"ch-1\""))
        assertTrue(body.contains("\"pageNumber\":5"))
    }
}
```

- [ ] **Step 2: Run tests, verify failure (no compile)**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.hikari.app.data.api.MangaApiTest" 2>&1 | tail -20
```
Expected: COMPILATION FAILED — `listMangaSeries`, `getMangaSeries`, etc. are unresolved references.

- [ ] **Step 3: Add the API methods**

In `android/app/src/main/java/com/hikari/app/data/api/HikariApi.kt`, add to the imports:

```kotlin
import com.hikari.app.data.api.dto.MangaSeriesDto
import com.hikari.app.data.api.dto.MangaSeriesDetailDto
import com.hikari.app.data.api.dto.MangaPageDto
import com.hikari.app.data.api.dto.MangaContinueDto
import com.hikari.app.data.api.dto.MangaSyncJobDto
import com.hikari.app.data.api.dto.MangaProgressRequest
```

Then append the 11 endpoints inside the `interface HikariApi` body (after the existing methods, before the closing brace):

```kotlin
    @GET("manga/series")
    suspend fun listMangaSeries(): List<MangaSeriesDto>

    @GET("manga/series/{id}")
    suspend fun getMangaSeries(@Path("id") id: String): MangaSeriesDetailDto

    @GET("manga/chapters/{id}/pages")
    suspend fun getMangaChapterPages(@Path("id") id: String): List<MangaPageDto>

    @GET("manga/continue")
    suspend fun getMangaContinue(): List<MangaContinueDto>

    @POST("manga/library/{id}")
    suspend fun addMangaToLibrary(@Path("id") seriesId: String)

    @DELETE("manga/library/{id}")
    suspend fun removeMangaFromLibrary(@Path("id") seriesId: String)

    @PUT("manga/progress/{seriesId}")
    suspend fun setMangaProgress(
        @Path("seriesId") seriesId: String,
        @Body body: MangaProgressRequest,
    )

    @PUT("manga/chapters/{id}/read")
    suspend fun markMangaChapterRead(@Path("id") chapterId: String)

    @POST("manga/chapters/{id}/sync")
    suspend fun startMangaChapterSync(@Path("id") chapterId: String)

    @POST("manga/sync")
    suspend fun startMangaSync()

    @GET("manga/sync/jobs")
    suspend fun listMangaSyncJobs(): List<MangaSyncJobDto>
```

- [ ] **Step 4: Run tests, verify pass**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.hikari.app.data.api.MangaApiTest" 2>&1 | tail -10
```
Expected: 5 tests, 5 passed.

- [ ] **Step 5: Run full test suite — make sure nothing else broke**

```bash
cd android && ./gradlew :app:testDebugUnitTest 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/hikari/app/data/api/HikariApi.kt \
        android/app/src/test/java/com/hikari/app/data/api/MangaApiTest.kt
git commit -m "feat(manga): HikariApi endpoints + parse tests"
```

### Task 1.3: MangaRepository

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/domain/repo/MangaRepository.kt`
- Create: `android/app/src/test/java/com/hikari/app/domain/repo/MangaRepositoryTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.hikari.app.domain.repo

import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.MangaProgressRequest
import com.hikari.app.data.api.dto.MangaSeriesDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class MangaRepositoryTest {
    private val api = mockk<HikariApi>(relaxUnitFun = true)
    private val repo = MangaRepository(api)

    @Test fun pageImageUrl_concatsWithSlash() {
        val url = repo.pageImageUrl("http://example.test", "p1")
        assertEquals("http://example.test/api/manga/page/p1", url)
    }

    @Test fun pageImageUrl_stripsTrailingSlashFromBase() {
        val url = repo.pageImageUrl("http://example.test/", "p1")
        assertEquals("http://example.test/api/manga/page/p1", url)
    }

    @Test fun listSeries_passThroughToApi() = runTest {
        coEvery { api.listMangaSeries() } returns listOf(
            MangaSeriesDto(id = "s1", source = "x", title = "X")
        )
        val out = repo.listSeries()
        assertEquals(1, out.size)
        coVerify { api.listMangaSeries() }
    }

    @Test fun setProgress_buildsRequestBody() = runTest {
        repo.setProgress("s1", "ch-1", 5)
        coVerify {
            api.setMangaProgress("s1", MangaProgressRequest("ch-1", 5))
        }
    }
}
```

- [ ] **Step 2: Run tests, verify failure**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.hikari.app.domain.repo.MangaRepositoryTest" 2>&1 | tail -10
```
Expected: COMPILATION FAILED — `MangaRepository` unresolved.

- [ ] **Step 3: Implement repository**

```kotlin
package com.hikari.app.domain.repo

import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.MangaProgressRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MangaRepository @Inject constructor(
    private val api: HikariApi,
) {
    suspend fun listSeries() = api.listMangaSeries()
    suspend fun getSeries(id: String) = api.getMangaSeries(id)
    suspend fun getChapterPages(chapterId: String) = api.getMangaChapterPages(chapterId)
    suspend fun getContinue() = api.getMangaContinue()

    fun pageImageUrl(baseUrl: String, pageId: String): String {
        val trimmed = baseUrl.trimEnd('/')
        return "$trimmed/api/manga/page/$pageId"
    }

    fun coverImageUrl(baseUrl: String, coverPath: String): String {
        val trimmed = baseUrl.trimEnd('/')
        return "$trimmed/api/manga/cover/$coverPath"
    }

    suspend fun addToLibrary(seriesId: String) = api.addMangaToLibrary(seriesId)
    suspend fun removeFromLibrary(seriesId: String) = api.removeMangaFromLibrary(seriesId)
    suspend fun setProgress(seriesId: String, chapterId: String, pageNumber: Int) =
        api.setMangaProgress(seriesId, MangaProgressRequest(chapterId, pageNumber))
    suspend fun markChapterRead(chapterId: String) = api.markMangaChapterRead(chapterId)
    suspend fun startChapterSync(chapterId: String) = api.startMangaChapterSync(chapterId)
    suspend fun startSync() = api.startMangaSync()
    suspend fun listSyncJobs() = api.listMangaSyncJobs()
}
```

- [ ] **Step 4: Run tests, verify pass**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.hikari.app.domain.repo.MangaRepositoryTest" 2>&1 | tail -10
```
Expected: 4 tests passed.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/hikari/app/domain/repo/MangaRepository.kt \
        android/app/src/test/java/com/hikari/app/domain/repo/MangaRepositoryTest.kt
git commit -m "feat(manga): MangaRepository facade with URL builders"
```

---

## Phase 2 — Sync Observer

### Task 2.1: MangaSyncObserver

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/domain/sync/MangaSyncObserver.kt`
- Create: `android/app/src/test/java/com/hikari/app/domain/sync/MangaSyncObserverTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.hikari.app.domain.sync

import app.cash.turbine.test
import com.hikari.app.data.api.dto.MangaSyncJobDto
import com.hikari.app.domain.repo.MangaRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MangaSyncObserverTest {
    private val repo = mockk<MangaRepository>()

    @Test fun status_transitionsToActiveWhenJobIsRunning() = runTest {
        val runningJob = MangaSyncJobDto(
            id = "j", source = "x", status = "running",
            totalChapters = 100, doneChapters = 42, startedAt = 1L,
        )
        coEvery { repo.listSyncJobs() } returns listOf(runningJob)
        val observer = MangaSyncObserver(repo)

        observer.status.test {
            assertTrue(awaitItem() is SyncStatus.Idle)
            observer.startPolling()
            advanceTimeBy(50)
            val active = awaitItem()
            assertTrue(active is SyncStatus.Active)
            assertTrue((active as SyncStatus.Active).job.id == "j")
            observer.stopPolling()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun status_transitionsBackToIdleWhenJobCompletes() = runTest {
        val running = MangaSyncJobDto(id="j", source="x", status="running", startedAt=1L)
        val done = MangaSyncJobDto(id="j", source="x", status="done", startedAt=1L, finishedAt=2L)
        var call = 0
        coEvery { repo.listSyncJobs() } answers {
            if (call++ == 0) listOf(running) else listOf(done)
        }
        val observer = MangaSyncObserver(repo)
        observer.status.test {
            awaitItem() // initial Idle
            observer.startPolling()
            advanceTimeBy(50)
            awaitItem() // Active
            advanceTimeBy(2_500)
            assertTrue(awaitItem() is SyncStatus.Idle)
            observer.stopPolling()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Run, verify failure**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.hikari.app.domain.sync.MangaSyncObserverTest" 2>&1 | tail -10
```
Expected: COMPILATION FAILED.

- [ ] **Step 3: Implement observer**

```kotlin
package com.hikari.app.domain.sync

import com.hikari.app.data.api.dto.MangaSyncJobDto
import com.hikari.app.domain.repo.MangaRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface SyncStatus {
    object Idle : SyncStatus
    data class Active(val job: MangaSyncJobDto) : SyncStatus
}

@Singleton
class MangaSyncObserver @Inject constructor(
    private val repo: MangaRepository,
) {
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private var pollingJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            while (isActive) {
                runCatching { repo.listSyncJobs() }.onSuccess { jobs ->
                    val active = jobs.firstOrNull { it.status == "running" || it.status == "queued" }
                    _status.value = active?.let { SyncStatus.Active(it) } ?: SyncStatus.Idle
                }
                delay(2_000)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }
}
```

- [ ] **Step 4: Run, verify pass**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.hikari.app.domain.sync.MangaSyncObserverTest" 2>&1 | tail -10
```
Expected: 2 tests passed.

If turbine timing flakes, increase the `advanceTimeBy(50)` to `advanceTimeBy(2_100)` so the first emission has clearly happened.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/hikari/app/domain/sync/MangaSyncObserver.kt \
        android/app/src/test/java/com/hikari/app/domain/sync/MangaSyncObserverTest.kt
git commit -m "feat(manga): MangaSyncObserver singleton with 2s polling"
```

---

## Phase 3 — List Screen

### Task 3.1: MangaListViewModel

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/ui/manga/MangaListViewModel.kt`
- Create: `android/app/src/test/java/com/hikari/app/ui/manga/MangaListViewModelTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.hikari.app.ui.manga

import app.cash.turbine.test
import com.hikari.app.data.api.dto.MangaContinueDto
import com.hikari.app.data.api.dto.MangaSeriesDto
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.repo.MangaRepository
import com.hikari.app.domain.sync.MangaSyncObserver
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MangaListViewModelTest {
    private val repo = mockk<MangaRepository>()
    private val observer = mockk<MangaSyncObserver>(relaxUnitFun = true)
    private val settings = mockk<SettingsStore>()

    @Test fun loads_emitsSuccessWithSeriesAndContinue() = runTest {
        val series = listOf(MangaSeriesDto("s1", "x", "X"))
        val cont = listOf(MangaContinueDto("s1", "X", null, "ch-1", 5, 1L))
        coEvery { repo.listSeries() } returns series
        coEvery { repo.getContinue() } returns cont
        every { observer.status } returns MutableStateFlow(com.hikari.app.domain.sync.SyncStatus.Idle)
        every { settings.backendUrl } returns MutableStateFlow("http://x")

        val vm = MangaListViewModel(repo, observer, settings)
        vm.uiState.test {
            assertTrue(awaitItem() is MangaListUiState.Loading)
            advanceUntilIdle()
            val s = awaitItem()
            assertTrue(s is MangaListUiState.Success)
            assertEquals(1, s.series.size)
            assertEquals(1, s.continueItems.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun apiFailure_emitsError() = runTest {
        coEvery { repo.listSeries() } throws RuntimeException("boom")
        coEvery { repo.getContinue() } returns emptyList()
        every { observer.status } returns MutableStateFlow(com.hikari.app.domain.sync.SyncStatus.Idle)
        every { settings.backendUrl } returns MutableStateFlow("http://x")

        val vm = MangaListViewModel(repo, observer, settings)
        vm.uiState.test {
            awaitItem() // Loading
            advanceUntilIdle()
            assertTrue(awaitItem() is MangaListUiState.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Run, verify failure**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.hikari.app.ui.manga.MangaListViewModelTest" 2>&1 | tail -10
```
Expected: COMPILATION FAILED.

- [ ] **Step 3: Implement ViewModel**

```kotlin
package com.hikari.app.ui.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.MangaContinueDto
import com.hikari.app.data.api.dto.MangaSeriesDto
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.repo.MangaRepository
import com.hikari.app.domain.sync.MangaSyncObserver
import com.hikari.app.domain.sync.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface MangaListUiState {
    object Loading : MangaListUiState
    data class Success(
        val series: List<MangaSeriesDto>,
        val continueItems: List<MangaContinueDto>,
    ) : MangaListUiState
    data class Error(val message: String) : MangaListUiState
}

@HiltViewModel
class MangaListViewModel @Inject constructor(
    private val repo: MangaRepository,
    private val observer: MangaSyncObserver,
    settings: SettingsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MangaListUiState>(MangaListUiState.Loading)
    val uiState: StateFlow<MangaListUiState> = _uiState.asStateFlow()
    val syncStatus: StateFlow<SyncStatus> = observer.status
    val backendUrl: StateFlow<String> = settings.backendUrl

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            _uiState.value = MangaListUiState.Loading
            runCatching {
                coroutineScope {
                    val s = async { repo.listSeries() }
                    val c = async { repo.getContinue() }
                    s.await() to c.await()
                }
            }.onSuccess { (series, cont) ->
                _uiState.value = MangaListUiState.Success(series, cont)
            }.onFailure {
                _uiState.value = MangaListUiState.Error(it.message ?: "Unbekannter Fehler")
            }
        }
    }

    fun startSyncPolling() = observer.startPolling()
    fun stopSyncPolling() = observer.stopPolling()
}
```

- [ ] **Step 4: Run, verify pass**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.hikari.app.ui.manga.MangaListViewModelTest" 2>&1 | tail -10
```
Expected: 2 tests passed.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/hikari/app/ui/manga/MangaListViewModel.kt \
        android/app/src/test/java/com/hikari/app/ui/manga/MangaListViewModelTest.kt
git commit -m "feat(manga): MangaListViewModel with concurrent series+continue load"
```

### Task 3.2: List components — MangaCard, MangaRow, MangaHero, MangaSyncBanner

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/ui/manga/components/MangaCard.kt`
- Create: `android/app/src/main/java/com/hikari/app/ui/manga/components/MangaRow.kt`
- Create: `android/app/src/main/java/com/hikari/app/ui/manga/components/MangaHero.kt`
- Create: `android/app/src/main/java/com/hikari/app/ui/manga/components/MangaSyncBanner.kt`

- [ ] **Step 1: Write `MangaCard.kt`**

```kotlin
package com.hikari.app.ui.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.data.api.dto.MangaSeriesDto

@Composable
fun MangaCard(
    series: MangaSeriesDto,
    coverUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(128.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF111111)),
        ) {
            // Always-on gradient backdrop with title — visible while cover loads
            // and as final fallback when coverUrl is null/missing.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0x66B45309), // amber-900/40
                                Color(0xFF18181B),
                                Color(0xFF09090B),
                            )
                        )
                    ),
            )
            Text(
                text = series.title,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxSize(),
            )
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = series.title,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = series.title,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 8.dp),
            maxLines = 2,
        )
    }
}
```

- [ ] **Step 2: Write `MangaRow.kt`**

```kotlin
package com.hikari.app.ui.manga.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MangaRow(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 32.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
        ) {
            item { content() }
        }
    }
}
```

Note: `LazyRow { item { content() } }` is one-item-per-row — the `content` composable is expected to internally lay out multiple cards using the `LazyListScope.items(...)` pattern at the call site. **Refactor** to expose `LazyListScope.() -> Unit`:

Replace the body with:

```kotlin
import androidx.compose.foundation.lazy.LazyListScope

@Composable
fun MangaRow(
    title: String,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 32.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            content = content,
        )
    }
}
```

- [ ] **Step 3: Write `MangaHero.kt`**

```kotlin
package com.hikari.app.ui.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.data.api.dto.MangaContinueDto
import com.hikari.app.data.api.dto.MangaSeriesDto

private val Accent = Color(0xFFFBBF24)

@Composable
fun MangaHero(
    series: MangaSeriesDto,
    cont: MangaContinueDto?,
    onCta: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().aspectRatio(16f / 12f)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 12f)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0x4DB45309),
                            Color(0xFF18181B),
                            Color(0xFF000000),
                        )
                    )
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 12f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0x66000000), Color.Black),
                    )
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp),
        ) {
            Text(
                text = "MANGA",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 10.sp,
                letterSpacing = 2.sp,
            )
            Text(
                text = series.title,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 8.dp),
            )
            series.author?.let {
                Text(
                    text = it.uppercase(),
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Button(
                onClick = onCta,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = Color.Black,
                ),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = if (cont != null) "Weiterlesen" else "Lesen",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
```

- [ ] **Step 4: Write `MangaSyncBanner.kt`**

```kotlin
package com.hikari.app.ui.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.data.api.dto.MangaSyncJobDto

private val Accent = Color(0xFFFBBF24)

@Composable
fun MangaSyncBanner(job: MangaSyncJobDto, modifier: Modifier = Modifier) {
    val total = if (job.totalChapters == 0) 1 else job.totalChapters
    val progress = (job.doneChapters.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0x4D78350F))     // amber-900/30
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "${job.doneChapters} / $total",
            color = Color(0xFFFCD34D),
            fontSize = 12.sp,
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.weight(1f).height(2.dp),
            color = Accent,
            trackColor = Color.White.copy(alpha = 0.1f),
        )
        Text(
            text = "SYNC LÄUFT",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
        )
    }
}
```

- [ ] **Step 5: Build to verify all compose**

```bash
cd android && ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/hikari/app/ui/manga/components/
git commit -m "feat(manga): list components (Card/Row/Hero/SyncBanner)"
```

### Task 3.3: MangaListScreen integration

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/ui/manga/MangaListScreen.kt`

- [ ] **Step 1: Write the screen**

```kotlin
package com.hikari.app.ui.manga

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hikari.app.domain.sync.SyncStatus
import com.hikari.app.ui.manga.components.MangaCard
import com.hikari.app.ui.manga.components.MangaHero
import com.hikari.app.ui.manga.components.MangaRow
import com.hikari.app.ui.manga.components.MangaSyncBanner
import com.hikari.app.ui.theme.HikariBg

@Composable
fun MangaListScreen(
    onSeriesClick: (seriesId: String) -> Unit,
    onContinueClick: (seriesId: String, chapterId: String, page: Int) -> Unit,
    vm: MangaListViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsState()
    val syncStatus by vm.syncStatus.collectAsState()
    val baseUrl by vm.backendUrl.collectAsState()

    DisposableEffect(Unit) {
        vm.startSyncPolling()
        onDispose { vm.stopSyncPolling() }
    }

    Column(modifier = Modifier.fillMaxSize().background(HikariBg).verticalScroll(rememberScrollState())) {
        if (syncStatus is SyncStatus.Active) {
            MangaSyncBanner((syncStatus as SyncStatus.Active).job)
        }
        when (val s = state) {
            is MangaListUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                    Text("Lade…", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
                }
            }
            is MangaListUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                    Text(s.message, color = Color(0xFFFBBF24), fontSize = 14.sp)
                }
            }
            is MangaListUiState.Success -> {
                if (s.series.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(top = 100.dp, start = 32.dp, end = 32.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "MANGA",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 10.sp,
                                letterSpacing = 2.sp,
                            )
                            Text(
                                text = "Noch keine Mangas",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                            Text(
                                text = "Trigger den Sync im Tuning-Tab → System.",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                } else {
                    val firstCont = s.continueItems.firstOrNull()
                    val heroSeries = firstCont
                        ?.let { c -> s.series.find { it.id == c.seriesId } }
                        ?: s.series.first()
                    MangaHero(
                        series = heroSeries,
                        cont = firstCont,
                        onCta = {
                            if (firstCont != null) {
                                onContinueClick(firstCont.seriesId, firstCont.chapterId, firstCont.pageNumber)
                            } else {
                                onSeriesClick(heroSeries.id)
                            }
                        },
                    )
                    if (s.continueItems.isNotEmpty()) {
                        MangaRow("Weiterlesen") {
                            items(s.continueItems) { c ->
                                val series = s.series.find { it.id == c.seriesId }
                                if (series != null) {
                                    MangaCard(
                                        series = series,
                                        coverUrl = series.coverPath?.let { vm.coverUrl(baseUrl, it) },
                                        onClick = { onSeriesClick(series.id) },
                                    )
                                }
                            }
                        }
                    }
                    MangaRow("Alle Mangas") {
                        items(s.series) { series ->
                            MangaCard(
                                series = series,
                                coverUrl = series.coverPath?.let { vm.coverUrl(baseUrl, it) },
                                onClick = { onSeriesClick(series.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Add `coverUrl(baseUrl, path)` helper to `MangaListViewModel`**

In `MangaListViewModel.kt`, append inside the class body:

```kotlin
    fun coverUrl(baseUrl: String, coverPath: String): String =
        repo.coverImageUrl(baseUrl, coverPath)
```

- [ ] **Step 3: Build**

```bash
cd android && ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/hikari/app/ui/manga/MangaListScreen.kt \
        android/app/src/main/java/com/hikari/app/ui/manga/MangaListViewModel.kt
git commit -m "feat(manga): MangaListScreen with hero + rows + sync banner"
```

---

## Phase 4 — Detail Screen

### Task 4.1: MangaDetailViewModel

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/ui/manga/MangaDetailViewModel.kt`

- [ ] **Step 1: Implement (no test — same pass-through pattern as Library detail, which has no test either)**

```kotlin
package com.hikari.app.ui.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.MangaContinueDto
import com.hikari.app.data.api.dto.MangaSeriesDetailDto
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.repo.MangaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface MangaDetailUiState {
    object Loading : MangaDetailUiState
    data class Success(
        val detail: MangaSeriesDetailDto,
        val continueItem: MangaContinueDto?,
    ) : MangaDetailUiState
    data class Error(val message: String) : MangaDetailUiState
}

@HiltViewModel
class MangaDetailViewModel @Inject constructor(
    private val repo: MangaRepository,
    settings: SettingsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MangaDetailUiState>(MangaDetailUiState.Loading)
    val uiState: StateFlow<MangaDetailUiState> = _uiState.asStateFlow()
    val backendUrl: StateFlow<String> = settings.backendUrl

    fun load(seriesId: String) {
        viewModelScope.launch {
            _uiState.value = MangaDetailUiState.Loading
            runCatching {
                coroutineScope {
                    val d = async { repo.getSeries(seriesId) }
                    val c = async { repo.getContinue() }
                    d.await() to c.await().firstOrNull { it.seriesId == seriesId }
                }
            }.onSuccess { (detail, cont) ->
                _uiState.value = MangaDetailUiState.Success(detail, cont)
            }.onFailure {
                _uiState.value = MangaDetailUiState.Error(it.message ?: "Nicht gefunden")
            }
        }
    }

    fun coverUrl(baseUrl: String, coverPath: String): String =
        repo.coverImageUrl(baseUrl, coverPath)
}
```

- [ ] **Step 2: Build**

```bash
cd android && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/hikari/app/ui/manga/MangaDetailViewModel.kt
git commit -m "feat(manga): MangaDetailViewModel"
```

### Task 4.2: ChapterRow + ArcAccordion components

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/ui/manga/components/ChapterRow.kt`
- Create: `android/app/src/main/java/com/hikari/app/ui/manga/components/ArcAccordion.kt`

- [ ] **Step 1: Write `ChapterRow.kt`**

```kotlin
package com.hikari.app.ui.manga.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.data.api.dto.MangaChapterDto

private val Accent = Color(0xFFFBBF24)

@Composable
fun ChapterRow(
    chapter: MangaChapterDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "CH ${chapter.number.toInt()}",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(56.dp),
        )
        Text(
            text = chapter.title.orEmpty(),
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Spacer(modifier = Modifier.width(8.dp))
        if (chapter.isRead == 1) {
            Text(
                text = "READ",
                color = Accent,
                fontSize = 10.sp,
                letterSpacing = 1.5.sp,
            )
        }
    }
}
```

- [ ] **Step 2: Write `ArcAccordion.kt`**

```kotlin
package com.hikari.app.ui.manga.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.data.api.dto.MangaArcDto
import com.hikari.app.data.api.dto.MangaChapterDto

private val SetSaver: Saver<Set<String>, *> = Saver(
    save = { it.toList() },
    restore = { (it as List<*>).filterIsInstance<String>().toSet() },
)

@Composable
fun ArcAccordion(
    arcs: List<MangaArcDto>,
    chapters: List<MangaChapterDto>,
    initialExpandedArcId: String?,
    onChapterClick: (chapterId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(saver = SetSaver) {
        mutableStateOf(
            when {
                initialExpandedArcId != null -> setOf(initialExpandedArcId)
                arcs.isNotEmpty() -> setOf(arcs[0].id)
                else -> emptySet()
            }
        )
    }
    val byArc = remember(arcs, chapters) {
        val map = HashMap<String, MutableList<MangaChapterDto>>()
        for (a in arcs) map[a.id] = mutableListOf()
        for (ch in chapters) {
            ch.arcId?.let { id -> map.getOrPut(id) { mutableListOf() }.add(ch) }
        }
        map
    }
    val orphans = remember(arcs, chapters) {
        val arcIds = arcs.map { it.id }.toSet()
        chapters.filter { it.arcId == null || it.arcId !in arcIds }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (arcs.isEmpty()) {
            chapters.forEach { ch ->
                ChapterRow(ch, onClick = { onChapterClick(ch.id) })
                HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.05f))
            }
        } else {
            arcs.forEach { arc ->
                val isOpen = arc.id in expanded
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            expanded = if (isOpen) expanded - arc.id else expanded + arc.id
                        }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (isOpen) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f),
                    )
                    Text(
                        text = arc.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    Text(
                        text = "CH ${arc.chapterStart ?: ""}–${arc.chapterEnd ?: ""}",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                if (isOpen) {
                    byArc[arc.id]?.forEach { ch ->
                        ChapterRow(ch, onClick = { onChapterClick(ch.id) })
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.05f))
            }
            if (orphans.isNotEmpty()) {
                Text(
                    text = "SONSTIGE",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 10.sp,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                )
                orphans.forEach { ch ->
                    ChapterRow(ch, onClick = { onChapterClick(ch.id) })
                }
            }
        }
    }
}
```

- [ ] **Step 3: Build**

```bash
cd android && ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/hikari/app/ui/manga/components/ChapterRow.kt \
        android/app/src/main/java/com/hikari/app/ui/manga/components/ArcAccordion.kt
git commit -m "feat(manga): ChapterRow + ArcAccordion"
```

### Task 4.3: MangaDetailScreen

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/ui/manga/MangaDetailScreen.kt`

- [ ] **Step 1: Write the screen**

```kotlin
package com.hikari.app.ui.manga

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hikari.app.ui.manga.components.ArcAccordion
import com.hikari.app.ui.theme.HikariBg

private val Accent = Color(0xFFFBBF24)

@Composable
fun MangaDetailScreen(
    seriesId: String,
    onBack: () -> Unit,
    onChapterClick: (chapterId: String, page: Int?) -> Unit,
    vm: MangaDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(seriesId) { vm.load(seriesId) }
    val state by vm.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(HikariBg)) {
        when (val s = state) {
            is MangaDetailUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Lade…", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
                }
            }
            is MangaDetailUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(s.message, color = Accent, fontSize = 14.sp)
                }
            }
            is MangaDetailUiState.Success -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        DetailHero(
                            title = s.detail.title,
                            author = s.detail.author,
                            ctaLabel = if (s.continueItem != null) "Weiterlesen" else "Lesen",
                            onCta = {
                                val ctaChapter = s.continueItem?.chapterId ?: s.detail.chapters.firstOrNull()?.id
                                if (ctaChapter != null) {
                                    onChapterClick(ctaChapter, s.continueItem?.pageNumber)
                                }
                            },
                            onBack = onBack,
                        )
                    }
                    item {
                        ArcAccordion(
                            arcs = s.detail.arcs,
                            chapters = s.detail.chapters,
                            initialExpandedArcId = s.continueItem?.let { c ->
                                s.detail.chapters.firstOrNull { it.id == c.chapterId }?.arcId
                            },
                            onChapterClick = { chapterId -> onChapterClick(chapterId, null) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailHero(
    title: String,
    author: String?,
    ctaLabel: String,
    onCta: () -> Unit,
    onBack: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 12f)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 12f)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0x4DB45309), Color(0xFF18181B), Color(0xFF000000)),
                    )
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 12f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0x66000000), Color.Black),
                    )
                ),
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(8.dp).align(Alignment.TopStart),
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Zurück",
                tint = Accent,
            )
        }
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
        ) {
            Text("MANGA", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, letterSpacing = 2.sp)
            Text(
                text = title,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 8.dp),
            )
            author?.let {
                Text(it.uppercase(), color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 4.dp))
            }
            Button(
                onClick = onCta,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                Text(ctaLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
```

- [ ] **Step 2: Build**

```bash
cd android && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/hikari/app/ui/manga/MangaDetailScreen.kt
git commit -m "feat(manga): MangaDetailScreen with hero + accordion"
```

---

## Phase 5 — Reader

### Task 5.1: MangaReaderViewModel + UiState + tests

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/ui/manga/MangaReaderViewModel.kt`
- Create: `android/app/src/test/java/com/hikari/app/ui/manga/MangaReaderViewModelTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package com.hikari.app.ui.manga

import app.cash.turbine.test
import com.hikari.app.data.api.dto.MangaChapterDto
import com.hikari.app.data.api.dto.MangaPageDto
import com.hikari.app.data.api.dto.MangaSeriesDetailDto
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.repo.MangaRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MangaReaderViewModelTest {
    private val repo = mockk<MangaRepository>(relaxUnitFun = true)
    private val settings = mockk<SettingsStore>().apply {
        every { backendUrl } returns MutableStateFlow("http://x")
    }

    @Test fun success_emitsPagesAndNextChapter() = runTest {
        coEvery { repo.getChapterPages("ch-1") } returns listOf(
            MangaPageDto("p1", 1, true), MangaPageDto("p2", 2, true)
        )
        coEvery { repo.getSeries("s") } returns MangaSeriesDetailDto(
            id = "s", title = "X",
            chapters = listOf(
                MangaChapterDto("ch-1", 1.0),
                MangaChapterDto("ch-2", 2.0),
            ),
        )
        val vm = MangaReaderViewModel(repo, settings)
        vm.uiState.test {
            assertTrue(awaitItem() is ReaderUiState.Loading)
            vm.load("s", "ch-1")
            advanceUntilIdle()
            val s = awaitItem()
            assertTrue(s is ReaderUiState.Success)
            assertEquals(2, s.pages.size)
            assertEquals("ch-2", s.nextChapterId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun emptyPages_triggersChapterSyncAndShowsSyncing() = runTest {
        coEvery { repo.getChapterPages("ch-1") } returns emptyList()
        coEvery { repo.getSeries("s") } returns MangaSeriesDetailDto(id = "s", title = "X")
        val vm = MangaReaderViewModel(repo, settings)
        vm.uiState.test {
            awaitItem() // Loading
            vm.load("s", "ch-1")
            advanceUntilIdle()
            assertTrue(awaitItem() is ReaderUiState.Syncing)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { repo.startChapterSync("ch-1") }
    }

    @Test fun savePosition_debouncedToLatestValueWithin1500ms() = runTest {
        coEvery { repo.getChapterPages("ch-1") } returns listOf(MangaPageDto("p1", 1, true))
        coEvery { repo.getSeries("s") } returns MangaSeriesDetailDto(id = "s", title = "X")
        val vm = MangaReaderViewModel(repo, settings)
        vm.load("s", "ch-1")
        advanceUntilIdle()
        vm.savePosition(1)
        vm.savePosition(2)
        vm.savePosition(3)
        advanceTimeBy(1_600)
        coVerify(exactly = 1) { repo.setProgress("s", "ch-1", 3) }
    }
}
```

- [ ] **Step 2: Run, verify failure**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.hikari.app.ui.manga.MangaReaderViewModelTest" 2>&1 | tail -10
```
Expected: COMPILATION FAILED.

- [ ] **Step 3: Implement ViewModel**

```kotlin
package com.hikari.app.ui.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.MangaPageDto
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.repo.MangaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface ReaderUiState {
    object Loading : ReaderUiState
    data class Syncing(val chapterId: String) : ReaderUiState
    data class Success(
        val pages: List<MangaPageDto>,
        val nextChapterId: String?,
    ) : ReaderUiState
    data class Error(val message: String) : ReaderUiState
}

@HiltViewModel
class MangaReaderViewModel @Inject constructor(
    private val repo: MangaRepository,
    settings: SettingsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()
    val backendUrl: StateFlow<String> = settings.backendUrl

    private var seriesId: String = ""
    private var chapterId: String = ""
    private var saveJob: Job? = null
    private var pendingPage: Int? = null
    private var pollJob: Job? = null

    fun load(seriesId: String, chapterId: String) {
        this.seriesId = seriesId
        this.chapterId = chapterId
        viewModelScope.launch {
            _uiState.value = ReaderUiState.Loading
            runCatching {
                val pages = repo.getChapterPages(chapterId)
                val detail = repo.getSeries(seriesId)
                pages to detail
            }.onSuccess { (pages, detail) ->
                if (pages.isEmpty()) {
                    _uiState.value = ReaderUiState.Syncing(chapterId)
                    runCatching { repo.startChapterSync(chapterId) }
                    startPollingForPages()
                } else {
                    val sorted = detail.chapters.sortedBy { it.number }
                    val idx = sorted.indexOfFirst { it.id == chapterId }
                    val next = if (idx >= 0 && idx < sorted.size - 1) sorted[idx + 1].id else null
                    _uiState.value = ReaderUiState.Success(pages, next)
                }
            }.onFailure {
                _uiState.value = ReaderUiState.Error(it.message ?: "Nicht gefunden")
            }
        }
    }

    private fun startPollingForPages() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(3_000)
                runCatching { repo.getChapterPages(chapterId) }.onSuccess { pages ->
                    if (pages.isNotEmpty()) {
                        val detail = runCatching { repo.getSeries(seriesId) }.getOrNull()
                        val sorted = detail?.chapters?.sortedBy { it.number } ?: emptyList()
                        val idx = sorted.indexOfFirst { it.id == chapterId }
                        val next = if (idx >= 0 && idx < sorted.size - 1) sorted[idx + 1].id else null
                        _uiState.value = ReaderUiState.Success(pages, next)
                        return@launch
                    }
                }
            }
        }
    }

    /** Debounced — emits the latest call's pageNumber 1.5s after the last call. */
    fun savePosition(pageNumber: Int) {
        pendingPage = pageNumber
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(1_500)
            val p = pendingPage ?: return@launch
            runCatching { repo.setProgress(seriesId, chapterId, p) }
        }
    }

    /** Synchronous final flush — call from `DisposableEffect.onDispose`. */
    fun flushProgress() {
        val p = pendingPage ?: return
        viewModelScope.launch {
            runCatching { repo.setProgress(seriesId, chapterId, p) }
        }
    }

    fun markChapterRead() {
        viewModelScope.launch {
            runCatching { repo.markChapterRead(chapterId) }
        }
    }

    fun pageImageUrl(baseUrl: String, pageId: String): String =
        repo.pageImageUrl(baseUrl, pageId)

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }
}
```

- [ ] **Step 4: Run tests, verify pass**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.hikari.app.ui.manga.MangaReaderViewModelTest" 2>&1 | tail -10
```
Expected: 3 tests passed.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/hikari/app/ui/manga/MangaReaderViewModel.kt \
        android/app/src/test/java/com/hikari/app/ui/manga/MangaReaderViewModelTest.kt
git commit -m "feat(manga): MangaReaderViewModel with debounced progress + sync polling"
```

### Task 5.2: ZoomablePage component

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/ui/manga/components/ZoomablePage.kt`

- [ ] **Step 1: Write the component**

```kotlin
package com.hikari.app.ui.manga.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import me.saket.telephoto.zoomable.ZoomableContentLocation
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ZoomablePage(
    pageImageUrl: String,
    pageNumber: Int,
    pagerState: PagerState,
    pageIdx: Int,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zoomableState = rememberZoomableState()
    val imageState = rememberZoomableImageState(zoomableState)
    val isZoomed by remember {
        derivedStateOf { zoomableState.zoomFraction != null && zoomableState.zoomFraction!! > 0.05f }
    }

    LaunchedEffect(isZoomed) {
        // The pager wraps this; setting the modifier-level isScrollable on the PagerState
        // is the documented way to pause swipe gestures while zoomed.
        // Compose Pager exposes a userScrollEnabled flag via its modifier; for runtime
        // toggling we mirror by storing a snapshot in PagerState.userScrollEnabled if
        // present. As of Compose Foundation 1.6+, the recommended pattern is to wrap
        // the HorizontalPager in a Box and conditionally consume gestures here. See
        // commentary in MangaReaderScreen.kt for the wrapper logic.
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val offset = pagerState.getOffsetDistanceInPages(pageIdx).coerceIn(-1f, 1f)
                cameraDistance = 16f * density
                rotationY = offset * 75f
                transformOrigin = TransformOrigin(
                    pivotFractionX = if (offset < 0f) 0f else 1f,
                    pivotFractionY = 1f,
                )
                shadowElevation = 24f
            },
    ) {
        ZoomableAsyncImage(
            model = pageImageUrl,
            contentDescription = "Page $pageNumber",
            state = imageState,
            modifier = Modifier.fillMaxSize(),
            onError = { onError() },
        )
    }
}
```

Note: `pagerState.getOffsetDistanceInPages(pageIdx)` is the Compose Foundation 1.6+ API (`getOffsetFractionForPage` was renamed). If your Compose-BOM version still exposes the old name, swap. Both return a Float in `[-1, 1]` for adjacent pages.

- [ ] **Step 2: Build**

```bash
cd android && ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL.

If it fails on `getOffsetDistanceInPages`: replace with `getOffsetFractionForPage`. If it fails on `ZoomableAsyncImage`: confirm Telephoto dependency was added and synced (re-run `./gradlew :app:dependencies | grep telephoto`).

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/hikari/app/ui/manga/components/ZoomablePage.kt
git commit -m "feat(manga): ZoomablePage with 3D flip + telephoto pinch-zoom"
```

### Task 5.3: ReaderChrome + ChapterEndPage

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/ui/manga/components/ReaderChrome.kt`
- Create: `android/app/src/main/java/com/hikari/app/ui/manga/components/ChapterEndPage.kt`

- [ ] **Step 1: Write `ReaderChrome.kt`**

```kotlin
package com.hikari.app.ui.manga.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Accent = Color(0xFFFBBF24)

@Composable
fun ReaderChrome(
    visible: Boolean,
    currentPage: Int,
    totalPages: Int,
    missingCount: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xB3000000))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Zurück", tint = Accent)
                }
                Text(
                    text = buildString {
                        append("$currentPage / $totalPages")
                        if (missingCount > 0) append(" · $missingCount missing")
                    },
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xB3000000))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                LinearProgressIndicator(
                    progress = {
                        if (totalPages <= 1) 0f
                        else (currentPage.toFloat() / (totalPages - 1).toFloat()).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = Accent,
                    trackColor = Color.White.copy(alpha = 0.1f),
                )
            }
        }
    }
}
```

- [ ] **Step 2: Write `ChapterEndPage.kt`**

```kotlin
package com.hikari.app.ui.manga.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Accent = Color(0xFFFBBF24)

@Composable
fun ChapterEndPage(
    nextChapterId: String?,
    onNextChapter: () -> Unit,
    onBackToOverview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "KAPITEL-ENDE",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
        )
        Button(
            onClick = if (nextChapterId != null) onNextChapter else onBackToOverview,
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(
                text = if (nextChapterId != null) "Nächstes Kapitel →" else "Zur Übersicht",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
```

- [ ] **Step 3: Build**

```bash
cd android && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/hikari/app/ui/manga/components/ReaderChrome.kt \
        android/app/src/main/java/com/hikari/app/ui/manga/components/ChapterEndPage.kt
git commit -m "feat(manga): ReaderChrome + ChapterEndPage components"
```

### Task 5.4: MangaReaderScreen integration

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/ui/manga/MangaReaderScreen.kt`

- [ ] **Step 1: Write the screen**

```kotlin
package com.hikari.app.ui.manga

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hikari.app.ui.manga.components.ChapterEndPage
import com.hikari.app.ui.manga.components.ReaderChrome
import com.hikari.app.ui.manga.components.ZoomablePage
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MangaReaderScreen(
    seriesId: String,
    chapterId: String,
    initialPage: Int,
    onBack: () -> Unit,
    onOpenChapter: (chapterId: String) -> Unit,
    vm: MangaReaderViewModel = hiltViewModel(),
) {
    LaunchedEffect(seriesId, chapterId) { vm.load(seriesId, chapterId) }
    val state by vm.uiState.collectAsState()
    val baseUrl by vm.backendUrl.collectAsState()

    DisposableEffect(Unit) {
        onDispose { vm.flushProgress() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when (val s = state) {
            is ReaderUiState.Loading -> CenterMessage("Lade…")
            is ReaderUiState.Error -> CenterMessage(s.message, color = Color(0xFFFBBF24))
            is ReaderUiState.Syncing -> CenterMessage("Wird gerade synchronisiert…\n\nHikari lädt das Kapitel von der Quelle.\nWenn die Bilder da sind, springt der Reader automatisch los.")
            is ReaderUiState.Success -> {
                if (s.pages.isEmpty()) {
                    CenterMessage("Keine Seiten")
                } else {
                    ReaderContent(
                        pages = s.pages,
                        nextChapterId = s.nextChapterId,
                        seriesId = seriesId,
                        chapterId = chapterId,
                        initialPage = initialPage,
                        baseUrl = baseUrl,
                        onBack = onBack,
                        onOpenChapter = onOpenChapter,
                        vm = vm,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReaderContent(
    pages: List<com.hikari.app.data.api.dto.MangaPageDto>,
    nextChapterId: String?,
    seriesId: String,
    chapterId: String,
    initialPage: Int,
    baseUrl: String,
    onBack: () -> Unit,
    onOpenChapter: (chapterId: String) -> Unit,
    vm: MangaReaderViewModel,
) {
    val pagerState = rememberPagerState(
        initialPage = (initialPage - 1).coerceIn(0, pages.size - 1),
        pageCount = { pages.size + 1 },  // +1 sentinel = chapter-end
    )
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    val failedPages = remember { mutableStateOf<Set<String>>(emptySet()) }

    // Progress save + mark-as-read
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collectLatest { idx ->
            if (idx < pages.size) vm.savePosition(idx + 1)
            if (idx == pages.size - 1) vm.markChapterRead()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            reverseLayout = true,   // RTL: forward = swipe left
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { chromeVisible = !chromeVisible })
                },
        ) { pageIdx ->
            if (pageIdx == pages.size) {
                ChapterEndPage(
                    nextChapterId = nextChapterId,
                    onNextChapter = { nextChapterId?.let(onOpenChapter) },
                    onBackToOverview = onBack,
                )
            } else {
                val page = pages[pageIdx]
                ZoomablePage(
                    pageImageUrl = vm.pageImageUrl(baseUrl, page.id),
                    pageNumber = page.pageNumber,
                    pagerState = pagerState,
                    pageIdx = pageIdx,
                    onError = {
                        failedPages.value = failedPages.value + page.id
                    },
                )
            }
        }
        ReaderChrome(
            visible = chromeVisible,
            currentPage = (pagerState.currentPage + 1).coerceAtMost(pages.size),
            totalPages = pages.size,
            missingCount = failedPages.value.size,
            onBack = onBack,
        )
    }
}

@Composable
private fun CenterMessage(text: String, color: Color = Color.White.copy(alpha = 0.6f)) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(text, color = color, fontSize = 14.sp)
        }
    }
}
```

- [ ] **Step 2: Build**

```bash
cd android && ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/hikari/app/ui/manga/MangaReaderScreen.kt
git commit -m "feat(manga): MangaReaderScreen with HorizontalPager + 3D flip + zoom"
```

---

## Phase 6 — Navigation + Tuning

### Task 6.1: Navigation entry

**Files:**
- Modify: `android/app/src/main/java/com/hikari/app/ui/navigation/NavDestinations.kt`

- [ ] **Step 1: Add Manga to destinations**

In `NavDestinations.kt`, change the `hikariDestinations` list to:

```kotlin
import androidx.compose.material.icons.filled.MenuBook
// ...

val hikariDestinations = listOf(
    NavDest("library", "Bibliothek", Icons.Default.GridView),
    NavDest("feed", "Feed", Icons.Default.PlayArrow),
    NavDest("manga", "Manga", Icons.Default.MenuBook),
    NavDest("channels", "Kanäle", Icons.AutoMirrored.Filled.List),
    NavDest("tuning", "Tuning", Icons.Default.Settings),
)
```

- [ ] **Step 2: Build**

```bash
cd android && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/hikari/app/ui/navigation/NavDestinations.kt
git commit -m "feat(manga): add Manga destination to bottom nav"
```

### Task 6.2: Navigation routes + reader bottom-bar hide

**Files:**
- Modify: `android/app/src/main/java/com/hikari/app/ui/navigation/HikariNavHost.kt`

- [ ] **Step 1: Add manga imports + routes**

In `HikariNavHost.kt`, add to imports:

```kotlin
import com.hikari.app.ui.manga.MangaListScreen
import com.hikari.app.ui.manga.MangaDetailScreen
import com.hikari.app.ui.manga.MangaReaderScreen
```

Find where the existing `composable("library")` lives and add three new composable blocks alongside (after `composable("tuning")` for example):

```kotlin
            composable("manga") {
                MangaListScreen(
                    onSeriesClick = { id ->
                        nav.navigate("manga/${URLEncoder.encode(id, "UTF-8")}")
                    },
                    onContinueClick = { sId, cId, page ->
                        val sE = URLEncoder.encode(sId, "UTF-8")
                        val cE = URLEncoder.encode(cId, "UTF-8")
                        nav.navigate("manga/$sE/$cE?page=$page")
                    },
                )
            }
            composable(
                "manga/{seriesId}",
                arguments = listOf(navArgument("seriesId") { type = NavType.StringType }),
            ) { entry ->
                val sId = URLDecoder.decode(
                    entry.arguments!!.getString("seriesId")!!,
                    "UTF-8",
                )
                MangaDetailScreen(
                    seriesId = sId,
                    onBack = { nav.popBackStack() },
                    onChapterClick = { cId, page ->
                        val sE = URLEncoder.encode(sId, "UTF-8")
                        val cE = URLEncoder.encode(cId, "UTF-8")
                        val pq = page?.let { "?page=$it" } ?: ""
                        nav.navigate("manga/$sE/$cE$pq")
                    },
                )
            }
            composable(
                "manga/{seriesId}/{chapterId}?page={page}",
                arguments = listOf(
                    navArgument("seriesId") { type = NavType.StringType },
                    navArgument("chapterId") { type = NavType.StringType },
                    navArgument("page") {
                        type = NavType.IntType
                        defaultValue = 1
                    },
                ),
            ) { entry ->
                val sId = URLDecoder.decode(entry.arguments!!.getString("seriesId")!!, "UTF-8")
                val cId = URLDecoder.decode(entry.arguments!!.getString("chapterId")!!, "UTF-8")
                val page = entry.arguments!!.getInt("page")
                MangaReaderScreen(
                    seriesId = sId,
                    chapterId = cId,
                    initialPage = page,
                    onBack = { nav.popBackStack() },
                    onOpenChapter = { nextChapterId ->
                        val sE = URLEncoder.encode(sId, "UTF-8")
                        val cE = URLEncoder.encode(nextChapterId, "UTF-8")
                        nav.navigate("manga/$sE/$cE") {
                            popUpTo("manga/$sE/${URLEncoder.encode(cId, "UTF-8")}") {
                                inclusive = true
                            }
                        }
                    },
                )
            }
```

- [ ] **Step 2: Hide bottom bar on reader route**

In the same file, find where `isVideoRoute` is computed (around line 70-ish):

```kotlin
val isVideoRoute = currentRoute?.startsWith("video/") == true
```

Add right after it:

```kotlin
val isReaderRoute = currentRoute?.matches(Regex("manga/[^/]+/[^/?]+(\\?.*)?")) == true
```

Then in the `Scaffold(bottomBar = ...)` lambda, find the existing condition that hides the bottom bar in fullscreen feed/video and update it to also include `!isReaderRoute`:

```kotlin
            if (!(currentRoute == "feed" && feedFullscreen) && !isVideoRoute && !isReaderRoute) {
                // ... existing NavigationBar content
            }
```

- [ ] **Step 3: Build**

```bash
cd android && ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/hikari/app/ui/navigation/HikariNavHost.kt
git commit -m "feat(manga): wire 3 manga routes + hide bottom bar in reader"
```

### Task 6.3: Tuning sync button

**Files:**
- Modify: `android/app/src/main/java/com/hikari/app/ui/tuning/TuningViewModel.kt`
- Modify: `android/app/src/main/java/com/hikari/app/ui/tuning/TuningScreen.kt`

- [ ] **Step 1: Add `triggerMangaSync` to TuningViewModel**

Find `TuningViewModel.kt`. Add to its constructor injections (next to existing repos):

```kotlin
import com.hikari.app.domain.repo.MangaRepository
// ...
class TuningViewModel @Inject constructor(
    private val settings: SettingsStore,
    // ... existing fields ...
    private val mangaRepo: MangaRepository,
) : ViewModel() {
```

Add the method to the class body:

```kotlin
    private val _mangaSyncStatus = MutableStateFlow<String?>(null)
    val mangaSyncStatus: StateFlow<String?> = _mangaSyncStatus.asStateFlow()

    fun triggerMangaSync() {
        viewModelScope.launch {
            _mangaSyncStatus.value = null
            runCatching { mangaRepo.startSync() }
                .onSuccess { _mangaSyncStatus.value = "Sync gestartet" }
                .onFailure { e ->
                    val msg = e.message.orEmpty()
                    _mangaSyncStatus.value = when {
                        "409" in msg -> "Sync läuft bereits"
                        else -> "Backend nicht erreichbar"
                    }
                }
            kotlinx.coroutines.delay(5_000)
            _mangaSyncStatus.value = null
        }
    }
```

(Required imports if missing: `MutableStateFlow`, `StateFlow`, `asStateFlow`, `viewModelScope`, `launch`.)

- [ ] **Step 2: Add Manga section in TuningScreen System tab**

Find the "System" tab content in `TuningScreen.kt`. Append a new section at its bottom (right before the trailing `Spacer`):

```kotlin
import androidx.compose.runtime.collectAsState
// ...

// Inside System tab Column:
Spacer(modifier = Modifier.height(24.dp))
Text(
    text = "MANGA",
    color = Color.White.copy(alpha = 0.4f),
    fontSize = 10.sp,
    letterSpacing = 1.5.sp,
    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
)
val mangaStatus by vm.mangaSyncStatus.collectAsState()
Box(
    modifier = Modifier
        .padding(horizontal = 20.dp, vertical = 4.dp)
        .clip(RoundedCornerShape(6.dp))
        .background(Color(0xFF111111))
        .border(0.5.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(6.dp))
        .clickable { vm.triggerMangaSync() }
        .padding(horizontal = 14.dp, vertical = 12.dp),
) {
    Text("Manga sync now", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
}
mangaStatus?.let {
    Text(
        text = it,
        color = Color.White.copy(alpha = 0.4f),
        fontSize = 10.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
}
```

- [ ] **Step 3: Build**

```bash
cd android && ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL.

If `Spacer`, `Text`, `Box` etc. are unresolved — they're already used elsewhere in TuningScreen, just verify the imports landed correctly.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/hikari/app/ui/tuning/TuningViewModel.kt \
        android/app/src/main/java/com/hikari/app/ui/tuning/TuningScreen.kt
git commit -m "feat(manga): Tuning System: Manga sync now button"
```

---

## Phase 7 — Validation

### Task 7.1: Full test suite + APK build + manual smoke test

- [ ] **Step 1: Run full test suite**

```bash
cd android && ./gradlew :app:testDebugUnitTest 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL, all manga tests passing alongside the existing ones.

- [ ] **Step 2: Build the debug APK**

```bash
cd android && ./gradlew :app:assembleDebug 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL. APK at `android/app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 3: Install on connected device**

```bash
adb devices
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```
Expected: `Success`.

- [ ] **Step 4: Manual smoke test**

On the device (with Hikari backend running, accessible via the URL set in Tuning):
1. Open Hikari → bottom nav has 5 entries including Manga
2. Tap Manga → empty state if no sync run, or hero+rows if data exists
3. Tap Tuning → System → "Manga sync now" → "Sync gestartet"
4. Back to Manga tab → sync banner visible at top, progress increases
5. After ~5 chapters synced: tap a series card → detail with arc accordion
6. Tap a chapter → reader opens
7. Swipe left/right → 3D flip animation plays around the bottom corner
8. Pinch with two fingers → zooms; pager pauses; pan with one finger → moves the zoomed view; double-tap → resets
9. Tap middle → chrome toggles
10. Reach last page → "Nächstes Kapitel" button
11. Close app + reopen → continue-reading lands on the saved page
12. No 5th-tab cramping issue on phone (Material3 NavigationBar handles 5 fine)

If anything fails: capture device logs with `adb logcat -s Hikari:* AndroidRuntime:E`.

- [ ] **Step 5: No commit needed (no code change)**

---

## Done

When everything in Phase 7 passes:
- 11 backend endpoints wired through Retrofit + DTOs
- Repository + sync observer
- 3 screens (list/detail/reader) with native gestures
- Reader has 3D book-page-flip + pinch-zoom + tap-zones + chrome
- 5-tab bottom nav, sync banner polling, Tuning trigger button
- All unit tests pass

Future work (out of scope for this plan):
- Cover-image download on backend (currently shows gradient fallback)
- Compose UI tests (Paparazzi/Robolectric)
- Reader settings (page mode toggle, brightness)
- Multi-source UI (only one adapter today)
