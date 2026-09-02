package com.hikari.app.ui.channels

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonPageTest {

    @Test
    fun `Staffel-Uebersichten werden als Mehr-Folgen erkannt`() {
        assertTrue(SeasonPage.looksLikeMultiEpisode("https://serienstream.to/serie/ted/staffel-1"))
        assertTrue(SeasonPage.looksLikeMultiEpisode("https://aniworld.to/anime/stream/solo-leveling/staffel-2"))
        assertTrue(SeasonPage.looksLikeMultiEpisode("https://serienstream.to/serie/ted"))
        assertTrue(SeasonPage.looksLikeMultiEpisode("https://aniworld.to/anime/stream/solo-leveling"))
    }

    @Test
    fun `einzelne Folgen sind keine Uebersicht`() {
        assertFalse(SeasonPage.looksLikeMultiEpisode("https://serienstream.to/serie/ted/staffel-1/episode-1"))
        assertFalse(SeasonPage.looksLikeMultiEpisode("https://aniworld.to/anime/stream/solo-leveling/staffel-2/episode-7"))
        assertFalse(SeasonPage.looksLikeMultiEpisode("https://site.to/serie/x/s1e3"))
    }

    @Test
    fun `Hoster- und Direktlinks sind keine Uebersicht`() {
        assertFalse(SeasonPage.looksLikeMultiEpisode("https://voe.sx/e/abc123"))
        assertFalse(SeasonPage.looksLikeMultiEpisode("https://youtube.com/watch?v=abc"))
        assertFalse(SeasonPage.looksLikeMultiEpisode("https://x.test/1"))
    }
}
