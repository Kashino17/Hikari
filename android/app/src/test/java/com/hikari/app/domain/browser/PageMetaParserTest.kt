package com.hikari.app.domain.browser

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class PageMetaParserTest {

    @Test
    fun aniworldStilLiefertSlugStaffelUndFolge() {
        val meta = PageMetaParser.parse(
            "https://aniworld.to/serie/stream/solo-leveling/staffel-2/episode-7",
        )
        assertEquals("Solo Leveling", meta.seriesTitle)
        assertEquals(2, meta.season)
        assertEquals(7, meta.episode)
        assertFalse(meta.isMovie)
    }

    @Test
    fun slugWirdHumanisiert() {
        val meta = PageMetaParser.parse("https://x.test/stream/the-boys/staffel-1/episode-1")
        assertEquals("The Boys", meta.seriesTitle)
    }

    @Test
    fun generischesSeasonSegment() {
        val meta = PageMetaParser.parse("https://x.test/watch/severance/season-1")
        assertEquals("Severance", meta.seriesTitle)
        assertEquals(1, meta.season)
        assertNull(meta.episode)
    }

    // Steht staffel/season direkt hinter einem Container-Wort, gibt es keinen
    // Seriennamen — "serie" oder "stream" als Titel waere Muell im Import.
    @Test
    fun containerSegmenteSindKeinSerienname() {
        val meta = PageMetaParser.parse("https://x.test/serie/staffel-3/episode-1")
        assertNull(meta.seriesTitle)
        assertEquals(3, meta.season)
    }

    @Test
    fun staffelOhneVorherigesSegment() {
        val meta = PageMetaParser.parse("https://x.test/staffel-1/episode-4")
        assertNull(meta.seriesTitle)
        assertEquals(1, meta.season)
    }

    // Wie im Backend: auch ohne Staffel-Segment werden Serie und Folge erkannt.
    @Test
    fun folgeOhneStaffelSegment() {
        val meta = PageMetaParser.parse("https://x.test/solo-leveling/folge-3")
        assertEquals("Solo Leveling", meta.seriesTitle)
        assertNull(meta.season)
        assertEquals(3, meta.episode)
    }

    @Test
    fun sxxEyyInline() {
        val meta = PageMetaParser.parse("https://x.test/watch/arcane-s02e05-deutsch")
        assertEquals("Arcane", meta.seriesTitle)
        assertEquals(2, meta.season)
        assertEquals(5, meta.episode)
    }

    @Test
    fun filmPfadErgibtFilm() {
        val meta = PageMetaParser.parse("https://x.test/filme/interstellar")
        assertTrue(meta.isMovie)
        assertNull(meta.seriesTitle)
        assertNull(meta.episode)
    }

    @Test
    fun queryUndFragmentStoerenNicht() {
        val meta = PageMetaParser.parse(
            "https://x.test/stream/arcane/staffel-2/episode-1?lang=de#player",
        )
        assertEquals("Arcane", meta.seriesTitle)
        assertEquals(2, meta.season)
    }

    @Test
    fun domGewinntUeberUrl() {
        val url = PageMetaParser.parse(
            "https://s.to/serie/american-horror-story-die-dunkle-seite-in-dir/staffel-1/episode-2",
        )
        assertEquals("American Horror Story Die Dunkle Seite In Dir", url.seriesTitle)
        val merged = url.mergedWith(
            PageMetaParser.PageMeta(seriesTitle = "American Horror Story", episodeTitle = "Home Invasion"),
        )
        assertEquals("American Horror Story", merged.seriesTitle)
        assertEquals(1, merged.season)
        assertEquals(2, merged.episode)
        assertEquals("Home Invasion", merged.episodeTitle)
        assertFalse(merged.isMovie)
    }

    @Test
    fun domFilmMarkierungGewinnt() {
        val merged = PageMetaParser.parse("https://x.test/watch/abc").mergedWith(
            PageMetaParser.PageMeta(isMovie = true, episodeTitle = "Interstellar"),
        )
        assertTrue(merged.isMovie)
    }
}
