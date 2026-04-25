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
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val newItemsFlow = MutableStateFlow<List<FeedItem>>(emptyList())

    @Before fun setUp() {
        every { settings.backendUrl } returns flowOf("http://laptop.local:3000")
        every { settings.dailyBudget } returns flowOf(15)
        coEvery { repo.refresh() } returns Unit
        coEvery { repo.fetchSaved() } returns emptyList()
        coEvery { repo.fetchOld() } returns emptyList()
        coEvery { repo.toggleSave(any(), any()) } answers { !secondArg<Boolean>() }
        every { repo.newItems() } returns newItemsFlow
    }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun newItems_flowsFromRepo_withoutDailyBudgetCap() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        val items = (0..19).map { FeedItem("v$it", "t$it", 60, "9:16", null, "c", "sci", "r", false) }
        newItemsFlow.value = items

        val vm = FeedViewModel(repo, settings)
        vm.items.test {
            val current = awaitItem()
            assertEquals(20, current.size)
            assertEquals("v0", current[0].videoId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun setMode_saved_loadsRemoteSavedItems() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        val saved = listOf(FeedItem("saved1", "t", 60, null, null, "c", "sci", "r", true))
        coEvery { repo.fetchSaved() } returns saved

        val vm = FeedViewModel(repo, settings)
        vm.setMode(FeedMode.SAVED)

        vm.items.test {
            val current = awaitItem()
            assertEquals(listOf("saved1"), current.map { it.videoId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun onToggleSave_updatesNewItemsImmediately() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        newItemsFlow.value = listOf(
            FeedItem("v1", "t", 60, "16:9", null, "c", "sci", "r", false)
        )

        val vm = FeedViewModel(repo, settings)

        vm.items.test {
            assertEquals(false, awaitItem().first().saved)
            vm.onToggleSave("v1", currentlySaved = false)
            assertEquals(true, awaitItem().first().saved)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun onToggleSave_removesUnsavedItemFromSavedModeImmediately() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        coEvery { repo.fetchSaved() } returns listOf(
            FeedItem("saved1", "t", 60, "16:9", null, "c", "sci", "r", true)
        )

        val vm = FeedViewModel(repo, settings)
        vm.setMode(FeedMode.SAVED)

        vm.items.test {
            assertEquals(listOf("saved1"), awaitItem().map { it.videoId })
            vm.onToggleSave("saved1", currentlySaved = true)
            assertEquals(emptyList(), awaitItem().map { it.videoId })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
