# Manga — Android Port Design Spec

**Date:** 2026-04-25
**Author:** Kadir + Claude (Brainstorming-Session)
**Status:** Draft, awaiting user approval before plan
**Builds on:** `2026-04-25-manga-tab-design.md` (web-demo design, shipped as 0.2.0)
**Target:** Hikari Android client (`android/app/`) — Compose, Hilt, Retrofit, Coil

## Goal

Bring the Manga feature to the Hikari Android client with feature parity to the web demo: list of series (Hero + Rows), arc-accordion detail page, and a reader with horizontal RTL paged flow, authentic 3D book-page-flip animation, pinch zoom + pan, swipe gestures, and progress-save. Backend (`/api/manga/*`) is already shipped in 0.2.0 and is not modified except for an optional cover-download polish.

## Decisions (Brainstorming Outcome)

| # | Decision | Choice |
|---|---|---|
| 1 | Reader strategy | C — `HorizontalPager` with per-page `graphicsLayer` 3D-flip transformer (gesture robustness from Pager + authentic flip animation) |
| 2 | Pinch zoom | `me.saket:telephoto:zoomable-image-coil` — purpose-built image-viewer wrapper around Coil with pinch/pan/double-tap |
| 3 | Local persistence | API-first (no Room mirror for manga state) — same pattern as existing `library/` and `channels/` modules |
| 4 | Image loading | Coil for covers + thumbs, Telephoto's `ZoomableAsyncImage` (which wraps Coil) for the reader |
| 5 | Navigation slot | 5th BottomNav item `Manga` between `Feed` and `Kanäle`, mirroring the web demo |
| 6 | Backend polish | Add cover-download to `runSeriesSync` so covers actually render (small additive change, ~30 LOC) |

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────────┐
│  android/app  (Compose / Hilt / Navigation-Compose)              │
│  ├── ui/manga/                                                   │
│  │    ├── MangaListScreen.kt        (route: "manga")             │
│  │    ├── MangaDetailScreen.kt      (route: "manga/{seriesId}")  │
│  │    ├── MangaReaderScreen.kt      (route:                      │
│  │    │     "manga/{seriesId}/{chapterId}?page={page}")          │
│  │    ├── MangaListViewModel.kt                                  │
│  │    ├── MangaDetailViewModel.kt                                │
│  │    ├── MangaReaderViewModel.kt                                │
│  │    └── components/                                            │
│  │         ├── MangaHero.kt                                      │
│  │         ├── MangaCard.kt                                      │
│  │         ├── MangaRow.kt                                       │
│  │         ├── MangaSyncBanner.kt                                │
│  │         ├── ArcAccordion.kt                                   │
│  │         ├── ChapterRow.kt                                     │
│  │         ├── ZoomablePage.kt                                   │
│  │         ├── ReaderChrome.kt                                   │
│  │         └── ChapterEndPage.kt                                 │
│  ├── data/api/                                                   │
│  │    ├── HikariApi.kt              (+ 11 manga endpoints)       │
│  │    └── dto/Manga.kt              (all manga DTOs)             │
│  ├── domain/                                                     │
│  │    ├── repo/MangaRepository.kt                                │
│  │    └── sync/MangaSyncObserver.kt (singleton sync poller)      │
│  ├── ui/navigation/                                              │
│  │    ├── NavDestinations.kt        (+ "manga" entry)            │
│  │    └── HikariNavHost.kt          (+ 3 manga composables)      │
│  └── ui/tuning/TuningScreen.kt      (+ "Manga sync now" Section) │
└────────────────────────────┬─────────────────────────────────────┘
                             │ Retrofit + OkHttp
                             ▼
              Backend /api/manga/* (already shipped in 0.2.0)
```

**Pattern (matches existing Hikari-Android modules):**
- ViewModels expose `StateFlow<UiState>` with `sealed interface UiState { Loading; Success(...); Error(...) }`
- Repositories are thin: `suspend` functions calling Retrofit + DTO mapping
- Hilt provides `HikariApi`, `MangaRepository`, `MangaSyncObserver` via constructor injection
- Coil for images, Telephoto for the reader's zoomable images

**No fundamental backend work** — endpoints exist since 0.2.0. The only optional backend addition is a cover-download step inside `runSeriesSync` so covers render properly on Android cards.

## Data Model

### DTOs (`data/api/dto/Manga.kt`)

```kotlin
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

**Snake-case note:** Sync-job rows come from SQLite raw via `SELECT * FROM manga_sync_jobs` and use snake_case (`total_chapters` etc.), while other manga endpoints use camelCase. `@SerialName` is applied per-field rather than relying on a global naming strategy, so each field's contract is explicit.

### Repository (`domain/repo/MangaRepository.kt`)

```kotlin
@Singleton
class MangaRepository @Inject constructor(
    private val api: HikariApi,
    private val baseUrlProvider: BaseUrlProvider,  // existing — used by other repos
) {
    suspend fun listSeries() = api.listMangaSeries()
    suspend fun getSeries(id: String) = api.getMangaSeries(id)
    suspend fun getChapterPages(chapterId: String) = api.getMangaChapterPages(chapterId)
    suspend fun getContinue() = api.getMangaContinue()
    fun pageImageUrl(pageId: String): String =
        "${baseUrlProvider.current}/api/manga/page/$pageId"
    fun coverImageUrl(coverPath: String): String =
        "${baseUrlProvider.current}/api/manga/cover/$coverPath"  // see Backend Polish below

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

`BaseUrlProvider` follows the existing Hikari-Android pattern (already injected into other repos). It exposes `.current` so the URL responds to user-config changes.

### API additions (`data/api/HikariApi.kt`)

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

## Screens

### `MangaListScreen` — route `"manga"`

ViewModel state:
```kotlin
sealed interface MangaListUiState {
    object Loading : MangaListUiState
    data class Success(
        val series: List<MangaSeriesDto>,
        val continueItems: List<MangaContinueDto>,
    ) : MangaListUiState
    data class Error(val message: String) : MangaListUiState
}
```

`init { reload() }` runs `listSeries()` + `getContinue()` concurrently via `coroutineScope { async ... async ... }`. `MangaSyncObserver` is observed independently via `vm.syncStatus: StateFlow<SyncStatus>`.

Composable structure:
```
Column (HikariBg)
├── MangaSyncBanner — visible when SyncStatus.Active
├── MangaHero — backdrop + "Weiterlesen"/"Lesen" CTA
├── MangaRow("Weiterlesen") — LazyRow, only when continueItems non-empty
└── MangaRow("Alle Mangas") — LazyRow with all series
```

`MangaCard` is `2:3` aspect, `AsyncImage` with `coverImageUrl(...)` plus a gradient fallback when `coverPath` is null. Optional progress streak at the bottom edge when the card matches a continue-item.

Empty state: centered "Noch keine Mangas — Trigger den Sync im Tuning-Tab → System".

### `MangaDetailScreen` — route `"manga/{seriesId}"`

ViewModel state:
```kotlin
sealed interface MangaDetailUiState {
    object Loading : MangaDetailUiState
    data class Success(
        val detail: MangaSeriesDetailDto,
        val continueItem: MangaContinueDto?,
    ) : MangaDetailUiState
    data class Error(val message: String) : MangaDetailUiState
}
```

Loads `getSeries(seriesId)` + filters `getContinue()` for this series. Handles 404 → `Error("Nicht gefunden")` with a back button.

Composable structure:
```
LazyColumn (HikariBg)
├── item { MangaDetailHero(detail, continueItem) — Hero with cover + author + CTA }
└── item { ArcAccordion(arcs, chapters, initialExpandedArcId) }
```

`ArcAccordion` keeps expanded state in `rememberSaveable<Set<String>>(saver = ...)`. `initialExpandedArcId` defaults to the arc that contains `continueItem.chapterId`, or arcs[0] if no progress yet.

`ChapterRow` is a clickable `Row` with chapter number (mono font), title, and a "READ"-amber-dot when `chapter.isRead == 1`.

### `MangaReaderScreen` — route `"manga/{seriesId}/{chapterId}?page={page}"`

ViewModel state:
```kotlin
sealed interface ReaderUiState {
    object Loading : ReaderUiState
    data class Syncing(val chapterId: String) : ReaderUiState
    data class Success(
        val pages: List<MangaPageDto>,
        val nextChapterId: String?,
    ) : ReaderUiState
    data class Error(val message: String) : ReaderUiState
}
```

`init` does:
1. `getChapterPages(chapterId)` — if empty, transition to `Syncing`, fire `startChapterSync(chapterId)`, start a 3s poll loop
2. Concurrently `getSeries(seriesId)` — used to compute `nextChapterId`

The 3s poll is a `flow { while (true) { emit(...); delay(3000) } }` collected in the ViewModel's scope. When pages arrive, transition to `Success`.

Composable:
```kotlin
val pagerState = rememberPagerState(
    initialPage = (initialPage - 1).coerceIn(0, pages.size - 1),
    pageCount = { pages.size + 1 },  // +1 sentinel for chapter-end
)
HorizontalPager(
    state = pagerState,
    reverseLayout = true,
    beyondViewportPageCount = 1,
    modifier = Modifier.fillMaxSize().background(Color.Black)
        .pointerInput(Unit) {
            detectTapGestures(onTap = { chromeVisible = !chromeVisible })
        },
) { pageIdx ->
    if (pageIdx == pages.size) {
        ChapterEndPage(nextChapterId, onNextChapter, onBack)
    } else {
        ZoomablePage(page = pages[pageIdx], pagerState = pagerState, pageIdx = pageIdx, onError = ...)
    }
}
```

Progress-save: `LaunchedEffect(pagerState.currentPage) { vm.savePosition(currentPage + 1) }`. The ViewModel internally debounces with a `Channel<Int>` consumer that drops intermediate values for 1.5s.

`markChapterRead()` fires when `pagerState.currentPage == pages.size - 1` (last actual page, not the sentinel).

`DisposableEffect(Unit) { onDispose { vm.flushProgress() } }` ensures the latest position is saved when the user navigates away.

### `ZoomablePage` — the heart of the reader

Three combined effects:
1. **3D-Flip** during swipe via `Modifier.graphicsLayer { rotationY = ... }`, driven by `pagerState.getOffsetFractionForPage(pageIdx)`
2. **Pinch-Zoom + Pan** via Telephoto's `ZoomableAsyncImage` (wraps Coil)
3. **Pager pause when zoomed** via `pagerState.isScrollEnabled = !isZoomed`

```kotlin
@Composable
fun ZoomablePage(
    page: MangaPageDto,
    pagerState: PagerState,
    pageIdx: Int,
    onError: () -> Unit,
) {
    val zoomState = rememberZoomableImageState()
    val isZoomed by remember {
        derivedStateOf { (zoomState.zoomFraction ?: 0f) > 0.05f }
    }

    LaunchedEffect(isZoomed) { pagerState.isScrollEnabled = !isZoomed }

    val pageOffset = pagerState
        .getOffsetFractionForPage(pageIdx)
        .coerceIn(-1f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                cameraDistance = 16f * density
                rotationY = pageOffset * 75f
                transformOrigin = TransformOrigin(
                    pivotFractionX = if (pageOffset < 0) 0f else 1f,
                    pivotFractionY = 1f,
                )
                shadowElevation = 24f
            },
    ) {
        ZoomableAsyncImage(
            model = page.imageUrl,
            contentDescription = "Page ${page.pageNumber}",
            state = zoomState,
            modifier = Modifier.fillMaxSize(),
            onError = { onError() },
        )
    }
}
```

`pageOffset` is 0 when centered, ±1 when fully one page away. `transformOrigin` switches to the bottom-left corner when offset < 0 (page is being pulled to the left = forward in RTL) or bottom-right when offset > 0 (back). The page tilts continuously with the finger; at full ±1 offset the page is at ±75° rotation, mid-flip-looking, and the next page (already rendered by Pager's `beyondViewportPageCount = 1`) is partially visible behind it.

### `ReaderChrome`

`AnimatedVisibility(chromeVisible)` wrapping a `Column`:
- Top: `Row` with back-arrow (`IconButton`) + page counter (`pageNumber / total — N missing`)
- Bottom: `LinearProgressIndicator` showing `currentPage / (pages.size - 1)`

### `ChapterEndPage`

Sentinel page rendered as the last item in HorizontalPager. Centered:
- "Kapitel-Ende" label
- "Nächstes Kapitel →" button (when `nextChapterId` is set)
- "Zur Übersicht" button (when no next chapter)

## Navigation

`NavDestinations.kt`:
```kotlin
val hikariDestinations = listOf(
    NavDest("library", "Bibliothek", Icons.Default.GridView),
    NavDest("feed", "Feed", Icons.Default.PlayArrow),
    NavDest("manga", "Manga", Icons.Default.MenuBook),
    NavDest("channels", "Kanäle", Icons.AutoMirrored.Filled.List),
    NavDest("tuning", "Tuning", Icons.Default.Settings),
)
```

`HikariNavHost.kt` adds three composables (see brainstorming Section 5 for full code) and updates `bottomBar` visibility to hide while in the reader (`isReaderRoute = currentRoute?.matches(Regex("manga/[^/]+/[^/?]+(\\?.*)?")) == true`).

## Sync Status (`domain/sync/MangaSyncObserver.kt`)

A `@Singleton` poller:
```kotlin
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

    fun stopPolling() { pollingJob?.cancel(); pollingJob = null }
}

sealed interface SyncStatus {
    object Idle : SyncStatus
    data class Active(val job: MangaSyncJobDto) : SyncStatus
}
```

`MangaListScreen` and `TuningScreen` start the poller via `DisposableEffect` while their composable is on screen, and stop it on dispose. Both screens observe `MangaSyncObserver.status` to decide whether to render the banner / status text.

## Theme

No new tokens. Uses existing `HikariBg = #0a0a0a`, `HikariBorder`, `HikariTextFaint`, `Accent = amber-400` from `ui/theme/`.

## Tuning Integration

`TuningScreen.kt` System tab gets a new section:
```
SectionLabel("Manga")
ListItem(
    headlineContent = { Text("Manga sync now") },
    modifier = Modifier.clickable { vm.triggerMangaSync() },
)
mangaSyncStatusText?.let { Text(it, style = ...faint) }
```

`TuningViewModel` gains `triggerMangaSync()` that calls `repo.startSync()` and tracks status text:
- Success → "Sync gestartet"
- 409 → "Sync läuft bereits"
- IOException / 5xx → "Backend nicht erreichbar"

Status text auto-clears after 5s.

## Error Handling

| Error | Where | Strategy |
|---|---|---|
| Backend unreachable (IOException) | `MangaRepository` calls inside ViewModel | `runCatching` → `UiState.Error("Backend nicht erreichbar")`, retry button in composable |
| 404 (unknown series/chapter) | DetailScreen, ReaderScreen | `UiState.Error("Nicht gefunden")`. Reader auto-pops back after 3s. |
| Image load failure | `ZoomablePage` Coil onError | `vm.markPageMissing(pageId)`. ReaderChrome counter shows "X / Y · N missing". Coil shows a small placeholder drawable. |
| Page `ready: false` | Reader | Already handled: trigger `startChapterSync` once, poll `getChapterPages` every 3s. UI shows pulse animation while syncing. |
| Sync 409 | TuningScreen | "Sync läuft bereits" status text |
| Network error on sync trigger | TuningScreen | "Backend nicht erreichbar" |
| Activity Pause / process death with unsaved progress | Reader | `DisposableEffect.onDispose { vm.flushProgress() }` synchronously emits the latest position before composable detaches |

## Backend Polish (optional, recommended)

Two small additions to `backend/src/manga/sync.ts` and one to `backend/src/api/manga.ts` that make the Android UX noticeably better. Total: ~60 LOC.

### 1. Cover download in `runSeriesSync`

After `upsertSeries`, fetch the series cover and store it in `MANGA_DATA_DIR/<source>/<seriesSlug>/cover.{ext}`. Update `manga_series.cover_path` to the relative path. Adapter exposes `coverUrl` already — One-Piece-Tube has it via the listing's category data or affiliate-image; verify during plan.

### 2. `GET /api/manga/cover/:path*` endpoint

Streams a cover file with the same path-traversal guard pattern used by `/api/manga/page/:id`. Necessary because `manga_series.cover_path` is a filesystem-relative path, not a `pageId`. Reuses the same security primitive.

### 3. (deferred) typed sync_job error fields

Currently `error_message` is a JSON string with `{ kind, message, url, ... }`. Could be split into typed columns for cleaner UI. Not required for v1.

## Testing

Manual E2E only — matches the existing Hikari-Android pattern (no Compose UI tests, no Paparazzi, no Retrofit-mock tests).

Validation flow on Tailscale-connected phone:
1. `./gradlew :app:assembleDebug` — build APK
2. Install + open
3. Bottom nav has 5 entries including Manga
4. `/manga` — empty-state if no sync run yet
5. Tuning → System → "Manga sync now" — banner appears in Manga tab within 2s
6. Wait for ~5 chapters synced; back to /manga; series card has cover (gradient fallback if cover-download not implemented)
7. Series card → detail with arc accordion, current arc auto-expanded
8. Chapter → reader opens; 3D flip on swipe; pinch zoom works; double-tap resets
9. Tap zones still work (left = forward in RTL, right = back, middle = chrome toggle)
10. App close + reopen → continue-reading lands on saved page
11. Reach last page → "Nächstes Kapitel" button

What is **not** tested in v1:
- Compose UI tests (Paparazzi, Showkase)
- ViewModel unit tests (existing modules don't have them)
- Network mocks (existing modules don't have them)

## Implementation Constraints

1. **Compose stability:** all `Modifier.graphicsLayer { ... }` calls must use the lambda-form (block-receiver) to avoid recompositions on every state read. Verified by Compose docs — block form is read inside the GraphicsLayer scope, so reading `pagerState.getOffsetFractionForPage(...)` inside the block is recomposition-friendly.
2. **Telephoto compatibility:** `me.saket.telephoto:zoomable-image-coil` requires Compose `1.6+` (we have BOM-managed via `compose-bom`). Add as new dependency in `app/build.gradle.kts` and `gradle/libs.versions.toml`.
3. **`HorizontalPager` API surface:** `pagerState.getOffsetFractionForPage(...)` is `@ExperimentalFoundationApi` in current Compose Foundation. Suppress with `@OptIn(ExperimentalFoundationApi::class)` at the file level for the Reader file. Acceptable — this API is stable in practice.
4. **Coil `OkHttpClient`:** existing Hikari-Android already configures Coil's OkHttp via Hilt. Manga page/cover requests inherit that — no per-request setup.
5. **Activity lifecycle:** Reader-progress flush relies on `DisposableEffect.onDispose` firing before the composable detaches. On `Activity.onPause` Compose runs disposes for the off-screen composables; on process death we lose any in-flight unsaved progress. Acceptable for v1 (worst case: 1.5s of position lost).

## Open Questions Deferred to the Plan

1. Exact URL pattern for cover-download from onepiece.tube (need to inspect the listing JSON's `category.affiliates` or scrape the series-page banner during plan).
2. Whether `me.saket.telephoto` is in `gradle/libs.versions.toml` already — if not, version pin during plan.
3. Whether the existing `BaseUrlProvider` exposes `current` as `String` directly or as `StateFlow<String>` (one read in plan).
4. Reader gesture on edge swipes: at the first/last actual page, the Pager naturally over-scrolls. Default behavior is fine — the sentinel page handles last-page UX, and first-page over-swipe just bounces. No explicit handling needed.

## Glossary

- **Adapter, Sync, Series, Arc, Chapter, Page, Library, Progress** — same as web spec.
- **Pager**: Compose's `HorizontalPager` from `androidx.compose.foundation.pager`.
- **Telephoto**: `me.saket.telephoto`, a Compose image-viewer library by Saket Narayan.
- **Coil**: image-loading library, already used throughout Hikari-Android.
- **graphicsLayer**: Compose modifier for GPU-accelerated 2D/3D transforms on a composable.
