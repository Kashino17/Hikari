package com.hikari.app.ui.feed

import app.cash.turbine.test
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.model.FeedItem
import com.hikari.app.domain.repo.FeedRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)

class FeedViewModelTest {
    private val repo = mockk<FeedRepository>(relaxUnitFun = true)
    private val settings = mockk<SettingsStore>()

    @Before fun setUp() {
        every { settings.backendUrl } returns flowOf("http://laptop.local:3000")
        every { settings.dailyBudget } returns flowOf(15)
        coEvery { repo.refresh() } returns Unit
    }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun unseenItems_flowsFromRepo_cappedAtDailyBudget() = runTest {
        // Share the test scheduler so viewModelScope coroutines are controlled by runTest
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        val items = (0..19).map { FeedItem("v$it", "t$it", 60, "9:16", "", "c", "sci", "r", false) }
        every { repo.unseenItems() } returns flowOf(items)

        val vm = FeedViewModel(repo, settings)
        // With Eagerly + UnconfinedTestDispatcher sharing the scheduler, the combine runs
        // synchronously before test subscribes; we receive the already-combined current value.
        vm.items.test {
            val capped = awaitItem()
            assertEquals(15, capped.size)
            assertEquals("v0", capped[0].videoId)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
