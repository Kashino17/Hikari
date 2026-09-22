package com.hikari.app.ui.library

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class EpisodeTitlesTest {
    @Test fun plainTitlesAreDetected() {
        assertTrue(EpisodeTitles.isPlain("Folge 3"))
        assertTrue(EpisodeTitles.isPlain("episode 12"))
        assertFalse(EpisodeTitles.isPlain("Folge 3: Der Anfang"))
        assertFalse(EpisodeTitles.isPlain("Der Anfang"))
    }

    @Test fun listTitleAvoidsDoubleNumbering() {
        assertEquals("Folge 3", EpisodeTitles.listTitle(3, "Folge 3"))
        assertEquals("Folge 4", EpisodeTitles.listTitle(4, "Folge 3"))
        assertEquals("3. Der Anfang", EpisodeTitles.listTitle(3, "Der Anfang"))
        assertEquals("Der Anfang", EpisodeTitles.listTitle(null, "Der Anfang"))
    }
}
