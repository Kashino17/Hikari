package com.hikari.app.ui.channels

import com.hikari.app.data.api.dto.AiMeta
import com.hikari.app.data.api.dto.AnalyzeResponse
import com.hikari.app.data.api.dto.BulkImportItem
import com.hikari.app.data.api.dto.LanguagesResponse
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
        coEvery { repo.listLanguages() } returns LanguagesResponse(
            dub = listOf("Japanisch", "Deutsch"),
            sub = listOf("Deutsch", "Englisch"),
        )
    }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test(timeout = 5_000) fun init_loadsSeriesList() = runTest {
        val vm = ImportSheetViewModel(repo)
        advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals(1, s.allSeries.size)
        assertEquals("One Piece", s.allSeries[0].title)
    }

    @Test(timeout = 5_000) fun onInputChanged_debouncesUrlParse() = runTest {
        coEvery { repo.analyzeVideo(any()) } returns AnalyzeResponse(
            url = "https://x.test/1", title = "T",
        )
        val vm = ImportSheetViewModel(repo)
        advanceUntilIdle()
        vm.onInputChanged("https://x.test/1")
        advanceTimeBy(200) // less than 500ms debounce
        assertTrue(vm.uiState.value.cards.isEmpty())
        advanceTimeBy(500)
        advanceUntilIdle()
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
        // Set shared defaults
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
