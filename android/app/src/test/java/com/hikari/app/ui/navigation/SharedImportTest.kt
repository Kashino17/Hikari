package com.hikari.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedImportTest {

    @Test
    fun `nimmt die URL aus Titel plus Link`() {
        assertEquals(
            "https://aniworld.to/anime/stream/solo-leveling/staffel-1/episode-3",
            extractSharedUrl("Solo Leveling Folge 3 https://aniworld.to/anime/stream/solo-leveling/staffel-1/episode-3"),
        )
    }

    @Test
    fun `streift angehaengte Satzzeichen ab`() {
        assertEquals("https://voe.sx/e/abc123", extractSharedUrl("Schau mal: https://voe.sx/e/abc123."))
        assertEquals("https://voe.sx/e/abc123", extractSharedUrl("(https://voe.sx/e/abc123)"))
    }

    @Test
    fun `ohne Link nichts`() {
        assertNull(extractSharedUrl("nur Text"))
        assertNull(extractSharedUrl(null))
        assertNull(extractSharedUrl(""))
    }
}
