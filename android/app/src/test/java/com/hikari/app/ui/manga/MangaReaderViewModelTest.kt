package com.hikari.app.ui.manga

import app.cash.turbine.test
import com.hikari.app.data.api.dto.MangaChapterDto
import com.hikari.app.data.api.dto.MangaPageDto
import com.hikari.app.data.api.dto.MangaSeriesDetailDto
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.download.LocalMangaDownloadManager
import com.hikari.app.domain.repo.MangaRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class MangaReaderViewModelTest {
    private val memory = object : com.hikari.app.domain.manga.MangaPositionStore.Storage {
        var raw: String? = null
        override fun read() = raw
        override fun write(json: String) { raw = json }
    }
    private val store = com.hikari.app.domain.manga.MangaPositionStore(memory)
    private val positions = mockk<com.hikari.app.domain.manga.MangaPositions>().apply { every { store } returns this@MangaReaderViewModelTest.store }

    private val repo = mockk<MangaRepository>(relaxUnitFun = true)
    private val settings = mockk<SettingsStore>().apply {
        every { backendUrl } returns MutableStateFlow("http://x")
    }
    // relaxed = true: localPageFile() returns null for all pageIds, so the
    // ViewModel falls back to backend URLs as in pre-Phase-4 behavior.
    private val mangaDownloads = mockk<LocalMangaDownloadManager>(relaxed = true)

    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test(timeout = 5_000) fun success_emitsPagesAndNextChapter() = runTest {
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
        val vm = MangaReaderViewModel(repo, mangaDownloads, settings, positions)
        try {
            vm.uiState.test(timeout = 4.seconds) {
                assertTrue(awaitItem() is ReaderUiState.Loading)
                vm.load("s", "ch-1")
                advanceTimeBy(500)
                val s = awaitItem()
                assertTrue(s is ReaderUiState.Success)
                assertEquals(2, s.pages.size)
                assertEquals("ch-2", s.nextChapterId)
                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            vm.stopPolling()
        }
    }

    @Test(timeout = 5_000) fun emptyPages_triggersChapterSyncAndShowsSyncing() = runTest {
        coEvery { repo.getChapterPages("ch-1") } returns emptyList()
        coEvery { repo.getSeries("s") } returns MangaSeriesDetailDto(id = "s", title = "X")
        val vm = MangaReaderViewModel(repo, mangaDownloads, settings, positions)
        try {
            vm.uiState.test(timeout = 4.seconds) {
                awaitItem() // Loading
                vm.load("s", "ch-1")
                // Bounded advance: enough for `load` to settle and emit Syncing,
                // but well below the 3s polling delay so we don't enter the loop.
                advanceTimeBy(500)
                assertTrue(awaitItem() is ReaderUiState.Syncing)
                cancelAndIgnoreRemainingEvents()
            }
            coVerify { repo.startChapterSync("ch-1") }
        } finally {
            vm.stopPolling()
        }
    }

    @Test(timeout = 5_000) fun savePosition_debouncedToLatestValueWithin1500ms() = runTest {
        coEvery { repo.getChapterPages("ch-1") } returns listOf(MangaPageDto("p1", 1, true))
        coEvery { repo.getSeries("s") } returns MangaSeriesDetailDto(id = "s", title = "X")
        val vm = MangaReaderViewModel(repo, mangaDownloads, settings, positions)
        vm.saveDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)
        try {
            vm.load("s", "ch-1")
            advanceTimeBy(500)
            vm.savePosition(1)
            vm.savePosition(2)
            vm.savePosition(3)
            advanceTimeBy(1_600)
            coVerify(exactly = 1) { repo.setProgress("s", "ch-1", 3, any()) }
        } finally {
            vm.stopPolling()
        }
    }

    @Test(timeout = 5_000) fun pages404_alsoTriggersSync() = runTest {
        // Der Mac antwortet 404, solange ein Kapitel noch nie geladen wurde (MangaDex-Reihen).
        coEvery { repo.getChapterPages("ch-1") } throws retrofit2.HttpException(
            retrofit2.Response.error<Any>(404, okhttp3.ResponseBody.create(null, "{}")),
        )
        coEvery { repo.getSeries("s") } returns MangaSeriesDetailDto(id = "s", title = "X")
        val vm = MangaReaderViewModel(repo, mangaDownloads, settings, positions)
        try {
            vm.uiState.test(timeout = 4.seconds) {
                awaitItem() // Loading
                vm.load("s", "ch-1")
                advanceTimeBy(500)
                assertTrue(awaitItem() is ReaderUiState.Syncing)
                coVerify { repo.startChapterSync("ch-1") }
                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            vm.stopPolling()
        }
    }

    @Test(timeout = 5_000) fun nextChapter_staysInSameLanguage() = runTest {
        coEvery { repo.getChapterPages("m:u:de:1") } returns listOf(MangaPageDto("p1", 1, true))
        coEvery { repo.getSeries("s") } returns MangaSeriesDetailDto(
            id = "s", title = "X",
            chapters = listOf(
                MangaChapterDto("m:u:de:1", 1.0, lang = "de"),
                MangaChapterDto("m:u:en:2", 2.0, lang = "en"),
                MangaChapterDto("m:u:de:5", 5.0, lang = "de"),
            ),
        )
        val vm = MangaReaderViewModel(repo, mangaDownloads, settings, positions)
        try {
            vm.uiState.test(timeout = 4.seconds) {
                awaitItem()
                vm.load("s", "m:u:de:1")
                advanceTimeBy(500)
                val st = awaitItem()
                assertTrue(st is ReaderUiState.Success)
                assertEquals("m:u:de:5", st.nextChapterId)
                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            vm.stopPolling()
        }
    }

    @Test(timeout = 5_000) fun pagesNotReady_keepsSyncing() = runTest {
        // Der Mac legt alle Seiten sofort an und lädt sie danach — erst wenn alle da sind, wird gelesen.
        coEvery { repo.getChapterPages("ch-1") } returns listOf(MangaPageDto("p1", 1, true), MangaPageDto("p2", 2, false))
        coEvery { mangaDownloads.localPageFile(any()) } returns null
        coEvery { repo.getSeries("s") } returns MangaSeriesDetailDto(id = "s", title = "X")
        val vm = MangaReaderViewModel(repo, mangaDownloads, settings, positions)
        try {
            vm.uiState.test(timeout = 4.seconds) {
                awaitItem()
                vm.load("s", "ch-1")
                advanceTimeBy(500)
                assertTrue(awaitItem() is ReaderUiState.Syncing)
                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            vm.stopPolling()
        }
    }

    @Test(timeout = 5_000) fun flushProgress_survivesLeavingTheReader() = runTest {
        // Raus aus dem Leser direkt nach dem Blättern: der Bildschirm (und sein Ablauf) ist weg,
        // die letzte Seite muss trotzdem beim Mac ankommen.
        coEvery { repo.getChapterPages("ch-1") } returns listOf(MangaPageDto("p1", 1, true))
        coEvery { repo.getSeries("s") } returns MangaSeriesDetailDto(id = "s", title = "X")
        val vm = MangaReaderViewModel(repo, mangaDownloads, settings, positions)
        vm.saveDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        vm.load("s", "ch-1")
        advanceTimeBy(100)
        vm.savePosition(7)
        vm.flushProgress()
        vm.viewModelScope.cancel()
        advanceUntilIdle()
        coVerify { repo.setProgress("s", "ch-1", 7, any()) }
    }

    @Test(timeout = 5_000) fun saves_arriveInOrder_lastPageWins() = runTest {
        // Eine langsame ältere Speicherung darf die letzte Seite nicht überholen.
        val saved = mutableListOf<Int>()
        coEvery { repo.getChapterPages("ch-1") } returns listOf(MangaPageDto("p1", 1, true))
        coEvery { repo.getSeries("s") } returns MangaSeriesDetailDto(id = "s", title = "X")
        coEvery { repo.setProgress("s", "ch-1", any(), any()) } coAnswers {
            val p = thirdArg<Int>()
            if (p == 4) kotlinx.coroutines.delay(400) // die alte Anfrage hängt im Netz
            synchronized(saved) { saved += p }
        }
        val vm = MangaReaderViewModel(repo, mangaDownloads, settings, positions)
        vm.saveDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        vm.load("s", "ch-1")
        advanceTimeBy(100)
        vm.savePosition(4)
        advanceTimeBy(1_600) // Wartezeit vorbei: Seite 4 wird gespeichert und hängt 400 ms
        vm.savePosition(7)
        vm.flushProgress() // Seite 7 sofort hinterher
        advanceUntilIdle()
        assertEquals(listOf(4, 7), synchronized(saved) { saved.toList() })
    }

    @Test(timeout = 5_000) fun offline_readsDownloadedChapterFromPhone() = runTest {
        // Kein Netz: das heruntergeladene Kapitel kommt vom Handy, das nächste geladene in derselben Sprache folgt.
        coEvery { repo.getChapterPages(any()) } throws java.io.IOException("offline")
        coEvery { repo.getSeries(any()) } throws java.io.IOException("offline")
        fun page(ch: String, n: Double, p: Int) = com.hikari.app.data.db.LocalMangaPageEntity(
            pageId = "$ch:0$p", arcId = "a", chapterId = ch, chapterNumber = n, pageNumber = p,
            localFilePath = "/x/$ch/$p.jpg", byteSize = 1, downloadedAt = 1,
        )
        coEvery { mangaDownloads.localChapterPages("m:u:de:1") } returns listOf(page("m:u:de:1", 1.0, 1), page("m:u:de:1", 1.0, 2))
        coEvery { mangaDownloads.localChapterIds("s") } returns listOf("m:u:de:1" to 1.0, "m:u:en:1" to 1.0, "m:u:de:2" to 2.0)
        coEvery { mangaDownloads.localPageFile(any()) } answers { java.io.File("/x/${firstArg<String>()}") }
        val vm = MangaReaderViewModel(repo, mangaDownloads, settings, positions)
        try {
            vm.uiState.test(timeout = 4.seconds) {
                awaitItem()
                vm.load("s", "m:u:de:1")
                advanceTimeBy(500)
                val st = awaitItem()
                assertTrue(st is ReaderUiState.Success, "war $st")
                assertEquals(listOf("m:u:de:1:01", "m:u:de:1:02"), st.pages.map { it.id })
                assertEquals("m:u:de:2", st.nextChapterId)
                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            vm.stopPolling()
        }
    }

    @Test(timeout = 5_000) fun opensAtStoredPage_evenIfButtonSaysPage1() = runTest {
        // „Weiterlesen“ kannte noch Seite 1, das Handy weiß: Seite 6 in diesem Kapitel.
        store.put("s", "ch-1", 6, at = 500)
        coEvery { repo.getChapterPages("ch-1") } returns (1..10).map { MangaPageDto("p$it", it, true) }
        coEvery { repo.getSeries("s") } returns MangaSeriesDetailDto(id = "s", title = "X")
        coEvery { repo.getContinue() } returns emptyList()
        val vm = MangaReaderViewModel(repo, mangaDownloads, settings, positions)
        try {
            vm.uiState.test(timeout = 4.seconds) {
                awaitItem()
                vm.load("s", "ch-1")
                advanceTimeBy(500)
                val st = awaitItem()
                assertTrue(st is ReaderUiState.Success)
                assertEquals(6, st.startPage)
                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            vm.stopPolling()
        }
    }

    @Test(timeout = 5_000) fun offlineProgress_isPushedWithItsTimestampWhenOnlineAgain() = runTest {
        // Offline gelesen: steht nur auf dem Handy. Beim nächsten Öffnen mit Netz geht er an den Mac.
        store.put("s", "ch-1", 8, at = 1_234)
        coEvery { repo.getChapterPages("ch-1") } returns listOf(MangaPageDto("p1", 1, true))
        coEvery { repo.getSeries("s") } returns MangaSeriesDetailDto(id = "s", title = "X")
        coEvery { repo.getContinue() } returns emptyList()
        val vm = MangaReaderViewModel(repo, mangaDownloads, settings, positions)
        vm.saveDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        vm.load("s", "ch-1")
        advanceUntilIdle()
        coVerify { repo.setProgress("s", "ch-1", 8, 1_234) }
        assertEquals(true, store.get("s")?.synced)
        vm.stopPolling()
    }

    @Test(timeout = 5_000) fun newerProgressFromMac_winsOverOlderPhoneProgress() = runTest {
        store.put("s", "ch-1", 2, at = 100)
        coEvery { repo.getChapterPages("ch-1") } returns (1..10).map { MangaPageDto("p$it", it, true) }
        coEvery { repo.getSeries("s") } returns MangaSeriesDetailDto(id = "s", title = "X")
        coEvery { repo.getContinue() } returns listOf(com.hikari.app.data.api.dto.MangaContinueDto("s", "X", null, "ch-1", 9, 900))
        val vm = MangaReaderViewModel(repo, mangaDownloads, settings, positions)
        try {
            vm.uiState.test(timeout = 4.seconds) {
                awaitItem()
                vm.load("s", "ch-1")
                advanceTimeBy(500)
                val st = awaitItem()
                assertTrue(st is ReaderUiState.Success)
                assertEquals(9, st.startPage)
                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            vm.stopPolling()
        }
    }
}
