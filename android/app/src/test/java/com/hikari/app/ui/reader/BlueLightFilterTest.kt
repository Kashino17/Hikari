package com.hikari.app.ui.reader

import com.hikari.app.ui.manga.MangaReaderSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlueLightFilterTest {
    @Test fun `Aus ist aus, Stufen werden stärker`() {
        assertEquals(0f, blueFilterStrength(BlueFilter.OFF, hour = 22), 0f)
        val l = blueFilterStrength(BlueFilter.LIGHT, 12)
        val m = blueFilterStrength(BlueFilter.MEDIUM, 12)
        val s = blueFilterStrength(BlueFilter.STRONG, 12)
        assertTrue(l > 0f && m > l && s > m && s < 0.5f)
    }

    @Test fun `Abends gilt von 20 bis 7 Uhr, tagsüber nicht`() {
        val m = blueFilterStrength(BlueFilter.MEDIUM, 12)
        assertEquals(m, blueFilterStrength(BlueFilter.EVENING, 20), 0f)
        assertEquals(m, blueFilterStrength(BlueFilter.EVENING, 2), 0f)
        assertEquals(m, blueFilterStrength(BlueFilter.EVENING, 6), 0f)
        assertEquals(0f, blueFilterStrength(BlueFilter.EVENING, 7), 0f)
        assertEquals(0f, blueFilterStrength(BlueFilter.EVENING, 19), 0f)
    }

    @Test fun `alte gespeicherte Einstellungen ohne Filter bleiben gültig`() {
        assertEquals(BlueFilter.OFF, MangaReaderSettings.decode("""{"anim":"SLIDE"}""").blueFilter)
        val s = MangaReaderSettings(blueFilter = BlueFilter.EVENING)
        assertEquals(BlueFilter.EVENING, MangaReaderSettings.decode(s.encode()).blueFilter)
    }
}
