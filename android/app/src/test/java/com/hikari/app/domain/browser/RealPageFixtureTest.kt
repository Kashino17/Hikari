package com.hikari.app.domain.browser

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

/**
 * Werte stammen 1:1 aus der echten Seite serienstream.to/serie/ted/staffel-1/episode-2
 * (abgerufen 2026-10-07): <title>, <h1>, <h2> und meta description.
 */
class RealPageFixtureTest {

    private val headings = listOf(
        "Ted",
        "S01E02: Schulanfang (2) (Just Say Yes (2))",
        "Kommentare",
        "Charaktere & Synchronsprecher",
    )

    @Test
    fun folgentitelUndSerieAusDenUeberschriften() {
        val f = HeadingEpisode.find(headings)!!
        assertEquals("Ted", f.seriesName)
        assertEquals(1, f.season)
        assertEquals(2, f.episode)
        assertEquals("Schulanfang (2)", f.title)
    }

    @Test
    fun einzelneKlammerBleibtErhalten() {
        assertEquals("Das Ende (Teil 2)", HeadingEpisode.dropAlternativeTitle("Das Ende (Teil 2)"))
        assertEquals("Zwei Väter zuviel", HeadingEpisode.dropAlternativeTitle("Zwei Väter zuviel (My Two Dads)"))
    }

    @Test
    fun keineFolgenueberschriftGibtNull() {
        assertNull(HeadingEpisode.find(listOf("Ted", "Kommentare")))
    }

    @Test
    fun beschreibungOhneWerbefloskel() {
        assertEquals(
            "Teds und Johns Plan, sich an dem Schultyrann zu rächen, versetzt sie ungewollt in die Rolle eines Elternteils.",
            DescriptionCleaner.clean(
                "Schaue Ted Staffel 1 Episode 2 online an. Teds und Johns Plan, sich an dem Schultyrann zu rächen, " +
                    "versetzt sie ungewollt in die Rolle eines Elternteils.",
            ),
        )
    }

    @Test
    fun reinerStandardtextWirdZuNull() {
        assertNull(
            DescriptionCleaner.clean("Schaue Ted Staffel 1 an. Alle Episoden verfügbar zum Streamen online."),
        )
    }

    @Test
    fun echteBeschreibungBleibtUnberuehrt() {
        val d = "Ein junger Magier muss die Welt vor dem Untergang retten."
        assertEquals(d, DescriptionCleaner.clean(d))
    }

    // Überschriften, 1:1 aus dem echten DOM abgegriffen (Playwright, 2026-10-07).
    @Test
    fun echteFolgenDerSeite() {
        val real = mapOf(
            3 to ("S01E03: Zwei Väter zuviel (My Two Dads)" to "Zwei Väter zuviel"),
            7 to ("S01E07: Lauter die Glocken nie klingen (Loud Night)" to "Lauter die Glocken nie klingen"),
        )
        for ((n, p) in real) {
            val f = HeadingEpisode.find(listOf("Ted", p.first, "Kommentare"))!!
            assertEquals(n, f.episode)
            assertEquals(1, f.season)
            assertEquals(p.second, f.title)
            assertEquals("Ted", f.seriesName)
        }
    }

    @Test
    fun werbeNetzeDerFolgenseiteSindBlockiert() {
        for (u in listOf("https://255md.com/5/11597207/?oo=1", "https://jhnwr.com/400/11596888")) {
            kotlin.test.assertTrue(AdHosts.isAdUrl(u), u)
        }
    }
}
