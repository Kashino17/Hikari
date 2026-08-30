package com.hikari.app.domain.browser

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class PageMetaParserTest {

    @Test
    fun aniworldStilLiefertSlugUndStaffel() {
        val meta = PageMetaParser.parse(
            "https://aniworld.to/serie/stream/solo-leveling/staffel-2/episode-7",
        )
        assertEquals("Solo Leveling", meta.seriesTitle)
        assertEquals(2, meta.season)
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

    @Test
    fun ohneStaffelSegmentKeineMeta() {
        val meta = PageMetaParser.parse("https://x.test/solo-leveling/folge-3")
        assertNull(meta.seriesTitle)
        assertNull(meta.season)
    }

    @Test
    fun queryUndFragmentStoerenNicht() {
        val meta = PageMetaParser.parse(
            "https://x.test/stream/arcane/staffel-2/episode-1?lang=de#player",
        )
        assertEquals("Arcane", meta.seriesTitle)
        assertEquals(2, meta.season)
    }
}
