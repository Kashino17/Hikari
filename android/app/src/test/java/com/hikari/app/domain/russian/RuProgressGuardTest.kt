package com.hikari.app.domain.russian

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuProgressGuardTest {
    private val full = RuProgress(
        gender = RuGender.MALE,
        xp = 2400,
        streak = 5,
        bestStreak = 9,
        lastActiveDay = 100,
        dayStars = mapOf(1 to 3, 2 to 2, 3 to 3),
        podcastDayStars = mapOf(1 to 3, 2 to 3),
        podcastListened = setOf(1, 2, 3),
        cards = mapOf("a" to RuCard(box = 4, due = 110), "b" to RuCard(box = 2, due = 101)),
        xpByDay = mapOf(99L to 50, 100L to 30),
        bestPairsSeconds = 20,
        favorites = setOf("a"),
    )

    @Test fun normalesLernenIstKeinRueckschritt() {
        val next = full.apply(RuSessionResult(xp = 10, graded = mapOf("c" to true)), 100)
        assertFalse(RuProgressGuard.isRegression(full, next))
    }

    @Test fun leererStandIstRueckschritt() {
        assertTrue(RuProgressGuard.isRegression(full, RuProgress()))
    }

    @Test fun leererStandPlusEineUebungBehaeltAlles() {
        // Genau der Fehlerfall: Oberfläche startet leer, bewertet eine Karte, schreibt zurück.
        val wiped = RuProgress().apply(RuSessionResult(xp = 5, graded = mapOf("neu" to true)), 101)
        val merged = RuProgressGuard.merge(full, wiped)
        assertEquals(2400, merged.xp)
        assertEquals(setOf(1, 2, 3), merged.dayStars.keys)
        assertEquals(setOf(1, 2), merged.podcastDayStars.keys)
        assertEquals(setOf(1, 2, 3), merged.podcastListened)
        assertEquals(RuGender.MALE, merged.gender)
        assertNotNull(merged.cards["a"])
        assertNotNull(merged.cards["neu"])
        assertEquals(101L, merged.lastActiveDay)
        assertEquals(20, merged.bestPairsSeconds)
        assertEquals(setOf("a"), merged.favorites)
        assertFalse(RuProgressGuard.isRegression(full, merged))
    }

    @Test fun scoreWieDerServer() {
        // backend/src/api/russian-backup.ts: xp + Karten + 10·Kurstage + 10·Podcasttage + gehörte Folgen
        assertEquals(2400 + 2 + 30 + 20 + 3, RuProgressGuard.score(full))
    }

    @Test fun lesenVerzeihtZahlenInAnfuehrungszeichen() {
        val json = """{"xp":"12","lastActiveDay":null,"cards":{"a":{"box":2,"due":5}}}"""
        val p = ruJsonLenient.decodeFromString(RuProgress.serializer(), json)
        assertEquals(12, p.xp)
        assertEquals(-1L, p.lastActiveDay)
    }
}
