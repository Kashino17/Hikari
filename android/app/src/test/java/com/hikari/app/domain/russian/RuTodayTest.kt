package com.hikari.app.domain.russian

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuTodayTest {
    private val today = 20_000L

    @Test
    fun neuerNutzerBekommtTagEinsUndErsteFolge() {
        val plan = RuToday.plan(RuProgress(), today, courseDays = 20, podcastDays = 100)
        assertEquals("russian/lesson/1", plan[0].route)
        assertFalse(plan[0].done)
        assertTrue("nichts fällig = erledigt", plan[1].done)
        assertEquals("russian/podcast/1", plan[2].route)
    }

    @Test
    fun heuteAbgeschlossenerKurstagIstErledigt() {
        val p = RuProgress(dayStars = mapOf(1 to 3, 2 to 2), courseLastCompletedDay = today)
        val course = RuToday.plan(p, today, 20, 100)[0]
        assertTrue(course.done)
        assertEquals(2, course.day)
    }

    @Test
    fun gesternAbgeschlossenStartetDenNaechstenTag() {
        val p = RuProgress(dayStars = mapOf(1 to 3, 2 to 2), courseLastCompletedDay = today - 1)
        val course = RuToday.plan(p, today, 20, 100)[0]
        assertFalse(course.done)
        assertEquals("russian/lesson/3", course.route)
    }

    @Test
    fun faelligeSaetzeWerdenGezaehlt() {
        val cards = mapOf("a" to RuCard(box = 1, due = today), "b" to RuCard(box = 2, due = today + 3))
        val review = RuToday.plan(RuProgress(cards = cards), today, 20, 100)[1]
        assertEquals(1, review.count)
        assertFalse(review.done)
    }

    @Test
    fun gehoerteFolgeFuehrtZuDenUebungen() {
        val p = RuProgress(podcastDayStars = mapOf(1 to 3, 2 to 3), podcastListened = setOf(1, 2, 3))
        val podcast = RuToday.plan(p, today, 20, 100)[2]
        assertTrue(podcast.practice)
        assertEquals("russian/podcastPractice/3", podcast.route)
    }

    @Test
    fun angefangeneFolgeGehtVor() {
        val podcast = RuToday.plan(RuProgress(), today, 20, 100, podcastResumeDay = 1)[2]
        assertEquals("russian/podcast/1", podcast.route)
    }
}
