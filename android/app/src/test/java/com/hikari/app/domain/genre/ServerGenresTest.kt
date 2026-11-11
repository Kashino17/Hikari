package com.hikari.app.domain.genre

import com.hikari.app.data.api.dto.LibraryVideoDto
import com.hikari.app.data.api.dto.SeriesDto
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class ServerGenresTest {
    private fun series(title: String, genres: String?) = SeriesDto(id = title, title = title, added_at = 0, genres = genres)
    private fun film(title: String, genres: String?) = LibraryVideoDto(
        id = title, channel_id = "manual", title = title, published_at = 0, duration_seconds = 6000, discovered_at = 0, genres = genres,
    )

    @Test fun kategorienVomServerGelten() {
        assertEquals(setOf(Genre.COMEDY), series("Modern Family", "comedy").detectGenres())
        assertEquals(setOf(Genre.DRAMA, Genre.THRILLER), series("American Horror Story", "drama,thriller").detectGenres())
        assertEquals(setOf(Genre.SCIFI, Genre.ACTION, Genre.DRAMA), film("Interstellar", "scifi,action,drama").detectGenres())
    }

    @Test fun eineComedySerieLandetNichtMehrBeiDenDokus() {
        // Früher: kein Stichwort im Titel → Rückfall auf »Doku & Wissen«.
        assertEquals(setOf(Genre.DOCS), series("Ted", null).detectGenres())
        assertEquals(setOf(Genre.COMEDY, Genre.SCIFI), series("Ted", "comedy,scifi").detectGenres())
    }

    @Test fun geprueftUndNichtsGefunden_bleibtOhneKategorie() {
        assertTrue(series("Unbekannte Serie", "").detectGenres().isEmpty())
        assertTrue(film("Unbekannter Film", "").detectGenres().isEmpty())
    }

    @Test fun unbekannteKennungenWerdenUebergangen() {
        assertEquals(setOf(Genre.COMEDY), serverGenres("comedy, western ,all"))
        assertNull(serverGenres(null))
    }
}
