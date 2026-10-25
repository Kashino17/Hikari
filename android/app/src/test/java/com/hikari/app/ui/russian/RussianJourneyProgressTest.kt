package com.hikari.app.ui.russian

import android.os.SystemClock
import com.hikari.app.data.api.HikariApi
import com.hikari.app.domain.russian.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class RussianJourneyProgressTest {
    private val state = MutableStateFlow(RuChatProgress())
    private lateinit var vm: RussianConversationViewModel
    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 0L
        val api = mockk<HikariApi>()
        coEvery { api.russianConversationStatus() } returns RuChatStatus(false)
        val store = mockk<RussianConversationStore>(relaxed = true)
        every { store.state } returns state
        every { store.loadSession() } returns null
        every { store.update(any()) } answers { state.value = firstArg<(RuChatProgress) -> RuChatProgress>().invoke(state.value) }
        vm = RussianConversationViewModel(api, store)
    }
    @After fun teardown() { unmockkStatic(SystemClock::class); Dispatchers.resetMain() }
    @Test fun copiedAnswersDoNotBecomeIndependentEvidenceAndCompletedWordsAreSaved() {
        vm.beginLesson("hello")
        repeat(4) { vm.nextFoundation() }
        repeat(3) {
            val line = RuJourney.line(state.value.foundation.run!!)
            vm.foundationDraft(line.answers.first(), spoken = true)
            vm.checkFoundation(); vm.nextFoundation()
        }
        assertTrue(state.value.observations.isEmpty())
        repeat(3) { index ->
            val line = RuJourney.line(state.value.foundation.run!!)
            if (index == 0) vm.foundationHelp()
            vm.foundationDraft(line.answers.first(), spoken = true)
            vm.checkFoundation(); vm.checkFoundation()
            vm.nextFoundation()
        }
        assertEquals("complete", state.value.foundation.run!!.phase)
        assertEquals(2, state.value.foundation.run!!.recalled)
        assertEquals(3, state.value.observations.size)
        assertEquals(2, state.value.observations.count { it.independent })
        assertEquals(4, state.value.cards.size)
        assertTrue(RuChatLearning.due(state.value, vm.today()).isEmpty())
        assertEquals(4, RuChatLearning.due(state.value, vm.today() + 1).size)
        vm.nextFoundation()
        assertEquals(4, state.value.cards.size)
    }
    @Test fun wrongAnswerCannotAdvanceAndReturningHomePreservesTheCurrentStep() {
        vm.beginLesson("hello"); repeat(4) { vm.nextFoundation() }
        vm.foundationDraft("Untertitelung des ZDF 2020", spoken = true)
        vm.checkFoundation(); vm.nextFoundation()
        assertEquals("retry", state.value.foundation.run!!.result)
        assertEquals(0, state.value.foundation.run!!.index)
        assertTrue(state.value.observations.isEmpty())
        vm.navigate("home"); vm.resumeLesson()
        assertEquals("lesson", vm.page)
        assertEquals("retry", state.value.foundation.run!!.result)
    }
}
