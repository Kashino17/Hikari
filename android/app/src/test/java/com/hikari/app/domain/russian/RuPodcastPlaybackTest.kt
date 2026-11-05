package com.hikari.app.domain.russian

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuPodcastPlaybackTest {
    @Test
    fun fortsetzenDreiSekundenFrueher() {
        assertEquals(249_000L, RuPodcastPlayback.resumeStartMs(252_000, 770_000))
    }

    @Test
    fun kaumAngefangenStartetVonVorn() {
        assertEquals(0L, RuPodcastPlayback.resumeStartMs(4_000, 770_000))
    }

    @Test
    fun amEndeStartetVonVorn() {
        assertEquals(0L, RuPodcastPlayback.resumeStartMs(767_000, 770_000))
    }

    @Test
    fun unbekannteDauerSetztTrotzdemFort() {
        assertEquals(57_000L, RuPodcastPlayback.resumeStartMs(60_000, 0))
    }

    @Test
    fun gehoertAbNeunzigProzent() {
        assertFalse(RuPodcastPlayback.isListened(692_000, 770_000))
        assertTrue(RuPodcastPlayback.isListened(693_000, 770_000))
        assertFalse(RuPodcastPlayback.isListened(5_000, 0))
    }

    @Test
    fun tempoZyklus() {
        assertEquals(1.25f, RuPodcastPlayback.nextSpeed(1f))
        assertEquals(0.75f, RuPodcastPlayback.nextSpeed(1.5f))
        assertEquals(1f, RuPodcastPlayback.nextSpeed(1.1f))
    }

    @Test
    fun tempoBeschriftung() {
        assertEquals("1,0×", RuPodcastPlayback.speedLabel(1f))
        assertEquals("0,75×", RuPodcastPlayback.speedLabel(0.75f))
        assertEquals("0,9×", RuPodcastPlayback.speedLabel(0.9f))
    }

    @Test
    fun zeitformat() {
        assertEquals("0:00", RuPodcastPlayback.formatTime(0))
        assertEquals("12:49", RuPodcastPlayback.formatTime(769_462))
        assertEquals("1:02:03", RuPodcastPlayback.formatTime(3_723_000))
    }
}
