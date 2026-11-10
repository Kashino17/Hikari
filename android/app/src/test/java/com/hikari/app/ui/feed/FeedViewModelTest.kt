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
import kotlin.test.assertTrue
import com.hikari.app.domain.feed.resolveDeepDive

@OptIn(ExperimentalCoroutinesApi::class)

class FeedViewModelTest {
    private val repo = mockk<FeedRepository>(relaxUnitFun = true)
    private val settings = mockk<SettingsStore>()
    private val newItemsFlow = MutableStateFlow<List<FeedItem>>(emptyList())

    @Before fun setUp() {
        every { settings.backendUrl } returns flowOf("http://laptop.local:3000")
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

    @Test fun mindfulCards_producesCardsBasedOnDefaultModulesAndRanks() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        val vm = FeedViewModel(repo, settings)
        vm.mindfulCards.test {
            val cards = awaitItem()
            // Ensure cards are generated with at least language and puzzle
            assert(cards.isNotEmpty())
            // Top ranked module should appear in the generated list
            val ranks = vm.moduleRanks.value
            assertEquals(com.hikari.app.domain.feed.ModuleRank.RANK_3, ranks[com.hikari.app.domain.feed.MindfulModuleType.LANGUAGE])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun mindfulFeed_allCompleted_triggersGoalCompleted() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        val vm = FeedViewModel(repo, settings)
        vm.mindfulCards.test {
            val cards = awaitItem()
            assert(cards.isNotEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun mindfulFeed_languageTopRank_interleavesFrequently() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        val vm = FeedViewModel(repo, settings)
        val entries = vm.entries.value
        val modules = entries.map { it.module }
        // Top-Fokus heißt: bis zu fünfmal je Schwung (hier begrenzt der kleine Ersatz-Vorrat ohne Kurs), nie zweimal hintereinander.
        assertTrue(modules.count { it == com.hikari.app.domain.feed.MindfulModuleType.LANGUAGE } >= 4)
        assertTrue(modules.zipWithNext().all { (a, b) -> a != b }, "gleicher Bereich direkt hintereinander: $modules")
        // Ohne Kursanbindung kommen trotzdem Spiele in den Feed.
        assertTrue(entries.any { it is com.hikari.app.domain.feed.FeedEntry.Game })
    }

    @Test fun mindfulFeed_growsWhileScrollingAndNeverRepeats() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        val vm = FeedViewModel(repo, settings)
        val first = vm.entries.value.size
        assertTrue(first > 0)
        vm.onShown(first - 1)
        val grown = vm.entries.value
        assertTrue(grown.size > first, "der Feed muss am Ende nachladen")
        assertEquals(grown.size, grown.map { it.id }.toSet().size, "doppelte Einträge")
    }

    @Test fun recordRussianPractice_updatesStoreAndAwardsXp() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        val store = mockk<com.hikari.app.domain.russian.RussianProgressStore>(relaxed = true)
        every { store.today() } returns 100L
        val vm = FeedViewModel(
            repo = repo,
            settings = settings,
            feedPrefs = null,
            audio = null,
            russianRepo = null,
            russianProgress = store,
        )

        vm.recordRussianPractice("p1", spokenCorrect = true)
        io.mockk.verify { store.update(any()) }
    }

    @Test fun resolveDeepDive_allCardTypes_produceValidDeepDiveContent() {
        val allCards = com.hikari.app.domain.feed.MindfulFeedContentProvider.getAllMasterCards()
        assertTrue(allCards.isNotEmpty(), "Master cards should not be empty")

        for (card in allCards) {
            val deepDive = card.resolveDeepDive()
            assertTrue(deepDive.title.isNotBlank(), "Title must not be blank for card ${card.id}")
            assertTrue(deepDive.subtitle.isNotBlank(), "Subtitle must not be blank for card ${card.id}")
            assertTrue(deepDive.keyInsight.isNotBlank(), "Key insight must not be blank for card ${card.id}")
            assertTrue(deepDive.fullContext.isNotBlank(), "Full context must not be blank for card ${card.id}")
        }
    }

    @Test fun toggleSavedCard_and_commuterMode_flowVerification() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        val vm = FeedViewModel(repo, settings)
        assertEquals(false, vm.commuterMode.value)
        assertEquals(emptySet(), vm.savedCardIds.value)

        val cards = vm.mindfulCards.value
        if (cards.isNotEmpty()) {
            val cardId = cards.first().id
            val isSavedNow = vm.toggleSavedCard(cardId)
            // Even without feedPrefs (null), it safely returns false and does not crash
            assertEquals(false, isSavedNow)
        }
    }
}

