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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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
class MangaListViewModelTest {
    private val repo = mockk<MangaRepository>()
    private val observer = mockk<MangaSyncObserver>(relaxUnitFun = true)
    private val settings = mockk<SettingsStore>()

    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

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
