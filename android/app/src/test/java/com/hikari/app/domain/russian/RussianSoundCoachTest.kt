package com.hikari.app.domain.russian

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RussianSoundCoachTest {

    @Test
    fun yVersusI_isNamed() {
        val sc = RuAnswerCheck.speech("мы были там", listOf("ми были там"))
        val hints = RuSoundCoach.hints(sc)
        assertEquals(1, hints.size)
        assertEquals("мы", hints[0].target)
        assertTrue(hints[0].tip.contains("ы"))
    }

    @Test
    fun missingSoftSign_isNamed() {
        assertTrue(RuSoundCoach.tipFor("мать", "мат")!!.contains("ь"))
    }

    @Test
    fun noHintWhenNothingHeard() {
        val sc = RuAnswerCheck.speech("привет", emptyList())
        assertTrue(RuSoundCoach.hints(sc).isEmpty())
    }

    @Test
    fun unknownDifferenceGivesNoGuess() {
        assertNull(RuSoundCoach.tipFor("привет", "превет"))
    }

    @Test
    fun hintsAreCapped() {
        val sc = RuAnswerCheck.speech("мы шли там жил", listOf("ми сли там зил"))
        assertTrue(RuSoundCoach.hints(sc, max = 2).size <= 2)
    }
}
