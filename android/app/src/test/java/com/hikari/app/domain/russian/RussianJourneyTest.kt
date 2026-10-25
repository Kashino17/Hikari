package com.hikari.app.domain.russian

import org.junit.Assert.*
import org.junit.Test

class RussianJourneyTest {
    @Test fun firstLaunchIsQuietAndOnlyFirstLessonIsAvailable() {
        val old = ruJson.decodeFromString<RuChatProgress>("{\"stage\":2}")
        assertFalse(RuChatSettings().autoPlay)
        assertTrue(old.foundation.introduced.isEmpty())
        assertFalse(RuJourney.freeReady(old.foundation))
        assertTrue(RuJourney.available(old.foundation, "hello"))
        assertFalse(RuJourney.available(old.foundation, "choice"))
        assertEquals(old.foundation, RuJourney.start(old.foundation, "choice", "test"))
    }
    @Test fun everyDialogueAndAnswerUsesOnlyPreviouslyIntroducedVocabulary() {
        val known = mutableSetOf<String>()
        for (lesson in RuJourney.lessons) {
            lesson.words.forEach { known += RuJourney.normalize(it.plain).split(" ") }
            for (line in lesson.lines) for (text in listOf(line.ru) + line.answers) {
                val unknown = RuJourney.normalize(text).split(" ").filter { it !in known }
                assertTrue("${lesson.id}: $text has unknown words $unknown", unknown.isEmpty())
            }
        }
    }
    @Test fun teachesAllWordsBeforeGuidedDialogueAndCannotSkipUnansweredTurns() {
        var p = RuJourney.start(RuFoundationProgress(), "hello", "test")
        repeat(4) { index ->
            assertEquals("learn", p.run!!.phase)
            assertEquals(index, p.introduced.size)
            p = RuJourney.nextStep(p)
        }
        assertEquals("guided", p.run!!.phase)
        assertEquals(4, p.introduced.size)
        assertEquals(p, RuJourney.nextStep(p))
        val wrong = p.copy(run = p.run!!.copy(result = "retry", draft = "Нет"))
        assertEquals(wrong, RuJourney.nextStep(wrong))
    }
    @Test fun completesOnlyAfterGuidedThenIndependentAttemptAndPreservesResume() {
        var p = RuJourney.start(RuFoundationProgress(), "hello", "test")
        repeat(4) { p = RuJourney.nextStep(p) }
        repeat(3) { p = RuJourney.nextStep(p.copy(run = p.run!!.copy(result = "correct"))) }
        assertEquals("recall", p.run!!.phase)
        assertTrue(p.completed.isEmpty())
        assertEquals("Чай?", RuJourney.line(p.run!!).ru)
        val saved = ruJson.encodeToString(RuFoundationProgress.serializer(), p)
        assertEquals(p, ruJson.decodeFromString<RuFoundationProgress>(saved))
        repeat(3) { p = RuJourney.nextStep(p.copy(run = p.run!!.copy(result = "correct"))) }
        assertEquals("complete", p.run!!.phase)
        assertEquals(setOf("hello"), p.completed)
        assertTrue(RuJourney.available(p, "choice"))
        assertFalse(RuJourney.freeReady(p))
        assertTrue(RuJourney.freeReady(p.copy(completed = setOf("hello", "choice", "likes"))))
    }
    @Test fun acceptsPunctuationButNeverAnOppositeOrUnrelatedTranscript() {
        val run = RuFoundationRun("test", "hello", phase = "guided", index = 1)
        assertTrue(RuJourney.matches(run, "да!"))
        assertTrue(RuJourney.matches(run, "Да, спасибо"))
        assertFalse(RuJourney.matches(run, "нет"))
        assertFalse(RuJourney.matches(run, "Untertitelung des ZDF 2020"))
        assertFalse(RuJourney.matches(run, "не да"))
    }
}
