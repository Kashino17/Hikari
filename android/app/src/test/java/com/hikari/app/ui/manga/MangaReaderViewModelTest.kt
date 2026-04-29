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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
        val vm = MangaReaderViewModel(repo, mangaDownloads, settings)
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
        val vm = MangaReaderViewModel(repo, mangaDownloads, settings)
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
        val vm = MangaReaderViewModel(repo, mangaDownloads, settings)
        try {
            vm.load("s", "ch-1")
            advanceTimeBy(500)
            vm.savePosition(1)
            vm.savePosition(2)
            vm.savePosition(3)
            advanceTimeBy(1_600)
            coVerify(exactly = 1) { repo.setProgress("s", "ch-1", 3) }
        } finally {
            vm.stopPolling()
        }
    }
}
