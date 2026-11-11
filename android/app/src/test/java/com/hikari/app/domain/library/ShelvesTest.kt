package com.hikari.app.domain.library

import com.hikari.app.data.api.dto.LibraryVideoDto
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class ShelvesTest {
    private fun video(id: String, series: String? = null) = LibraryVideoDto(
        id = id,
        channel_id = "manual",
        series_id = series,
        title = id,
        published_at = 0,
        duration_seconds = 1000,
        discovered_at = 0,
    )

    @Test fun alleGibtEsNurWennDieReiheNichtSchonAllesZeigt() {
        val one = listOf("Interstellar")
        assertFalse(Shelves.hasMore(one))
        assertEquals(one, Shelves.preview(one))

        val ten = (1..10).map { "Film $it" }
        assertFalse(Shelves.hasMore(ten), "genau zehn passen in die Reihe")

        val eleven = (1..11).map { "Film $it" }
        assertTrue(Shelves.hasMore(eleven))
        assertEquals(10, Shelves.preview(eleven).size)
        assertEquals("Film 1", Shelves.preview(eleven).first())
    }

    @Test fun weiterschauen_zeigtJedeSerieNurEinmal_mitIhrerJuengstenFolge() {
        val row = Shelves.continueRow(
            listOf(
                video("ted-1-2", "ted"),
                video("ahs-3-10", "ahs"),
                video("ahs-3-9", "ahs"),
                video("film"),
                video("ahs-2-9", "ahs"),
                video("ted-1-1", "ted"),
            ),
        )
        assertEquals(listOf("ted-1-2", "ahs-3-10", "film"), row.map { it.id })
    }

    @Test fun weiterschauen_hoechstensZehn() {
        val row = Shelves.continueRow((1..25).map { video("film-$it") })
        assertEquals(10, row.size)
        assertEquals("film-1", row.first().id)
    }

    @Test fun karte_nenntRestOderNaechsteFolge() {
        assertEquals("S1 · F2 · noch 9 min", Shelves.continueDetail("S1 · F2", "9 min", upNext = false))
        assertEquals("S1 · F3 · Nächste Folge", Shelves.continueDetail("S1 · F3", "22 min", upNext = true))
        assertEquals("noch 1 h 12 min", Shelves.continueDetail("", "1 h 12 min", upNext = false))
    }
}
