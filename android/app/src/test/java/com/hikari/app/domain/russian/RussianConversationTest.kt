package com.hikari.app.domain.russian

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class RussianConversationTest {
    private fun evidence(stage: Int = 0) = (0 until 15).map {
        RuChatObservation("turn-$it", 100L + it % 3, stage, if (it % 2 == 0) "cafe" else "friend", "слово $it", true, true, true)
    }
    private fun cards() = listOf("чай", "кофе").map { RuChatCard(RuChatPhrase(it, "Getränk"), 100, 105, delayedCorrect = 1) }

    @Test fun alwaysStartsAtBeginner() { assertEquals(0, RuChatProgress().stage) }
    @Test fun helpedTypedOrOneDayAnswersCannotUnlockLevels() {
        for (observations in listOf(evidence().map { it.copy(independent = false) }, evidence().map { it.copy(spoken = false) }, evidence().map { it.copy(day = 100) })) {
            assertFalse(RuChatLearning.readyToAdvance(RuChatProgress(observations = observations, cards = cards())))
        }
    }
    @Test fun requiresDelayedRecallVarietyAndEnoughEvidence() {
        assertFalse(RuChatLearning.readyToAdvance(RuChatProgress(observations = evidence())))
        assertFalse(RuChatLearning.readyToAdvance(RuChatProgress(observations = evidence().map { it.copy(answer = "да") }, cards = cards())))
        assertFalse(RuChatLearning.readyToAdvance(RuChatProgress(observations = evidence().map { it.copy(scenario = "cafe") }, cards = cards())))
        assertFalse(RuChatLearning.readyToAdvance(RuChatProgress(observations = evidence().take(5), cards = cards())))
        assertTrue(RuChatLearning.readyToAdvance(RuChatProgress(observations = evidence(), cards = cards())))
    }
    @Test fun advancesOnlyOneStageAndDoesNotDoubleCredit() {
        val input = RuChatProgress(observations = evidence().dropLast(1), cards = cards())
        val next = RuChatLearning.record(input, evidence().last())
        assertEquals(1, next.stage)
        assertEquals(next, RuChatLearning.record(next, evidence().last()))
    }
    @Test fun repeatedDifficultyAddsSupportWithoutErasingProgress() {
        val p = RuChatProgress(stage = 3, observations = evidence(3).take(3).map { it.copy(clear = false) })
        assertEquals(2, RuChatLearning.supportStage(p))
        assertEquals(3, p.stage)
    }
    @Test fun savedExpressionsDeduplicateAndAreNotDueImmediately() {
        val p = RuChatLearning.saveCard(RuChatProgress(), RuChatPhrase("Всё хорошо!", "Alles gut!"), 100)
        val duplicate = RuChatLearning.saveCard(p, RuChatPhrase("все хорошо", "Gut"), 100)
        assertEquals(1, duplicate.cards.size)
        assertTrue(RuChatLearning.due(p, 100).isEmpty())
        assertEquals(1, RuChatLearning.due(p, 101).size)
    }
    @Test fun onlyLaterDueRecallCountsAndLapsesResetSpacing() {
        val card = RuChatCard(RuChatPhrase("чай", "Tee"), 100, 101)
        assertEquals(0, RuChatLearning.grade(card, true, 100).delayedCorrect)
        val recalled = RuChatLearning.grade(card, true, 101)
        assertEquals(1, recalled.delayedCorrect)
        assertEquals(102L, recalled.due)
        val lapse = RuChatLearning.grade(recalled, false, 102)
        assertEquals(0, lapse.box)
        assertEquals(103L, lapse.due)
    }
    @Test fun planIsTransparentCalendarArithmeticNotFluencyPrediction() {
        val start = LocalDate.of(2026, 10, 26)
        val plan = RuStudyPlanner.calculate(start, start.plusDays(14), 5, 30, 10)!!
        assertEquals(10, plan.studyDays)
        assertEquals(5.0, plan.availableHours, 0.001)
        assertEquals(110.0, plan.referenceHoursPerStudyDay, 0.001)
        assertEquals(3, plan.blocksPerDay)
        assertTrue(plan.exceedsBudget)
    }
    @Test fun rejectsImpossibleInputsAndAccountsForPartialWeeks() {
        val start = LocalDate.of(2026, 10, 26)
        assertNull(RuStudyPlanner.calculate(start, start, 5, 30, 10))
        assertNull(RuStudyPlanner.calculate(start, start.minusDays(1), 5, 30, 10))
        assertNull(RuStudyPlanner.calculate(start, start.plusDays(1), 0, 30, 10))
        assertNull(RuStudyPlanner.calculate(start, start.plusDays(1), 5, 0, 10))
        val plan = RuStudyPlanner.calculate(start, start.plusDays(10), 5, 25, 10)!!
        assertEquals(8, plan.studyDays)
        assertEquals(3, plan.blocksPerDay)
    }
    @Test fun serializationPreservesSessionAndOldDefaults() {
        assertEquals(0, ruJson.decodeFromString<RuChatProgress>("{}").stage)
        val session = RuChatSession("id", RuChatSettings(), 0, draft = "Привет", hint = 2, helped = true)
        assertEquals(session, ruJson.decodeFromString<RuChatSession>(ruJson.encodeToString(RuChatSession.serializer(), session)))
    }
}
