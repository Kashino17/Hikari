package com.hikari.app.domain.browser

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class EpisodeTitleCleanerTest {
    private val url = "https://serienstream.to/serie/ted/staffel-1/episode-2"

    @Test fun entferntSerieStaffelFolgeUndSeitenname() {
        assertEquals(
            "Pilot",
            EpisodeTitleCleaner.clean("Ted Staffel 1 Episode 2 - Pilot - Serienstream", "Ted", url),
        )
    }

    @Test fun nurSeitentitelOhneFolgentitelErgibtNull() {
        assertNull(EpisodeTitleCleaner.clean("Ted Staffel 1 Episode 2 - Serienstream", "Ted", url))
    }

    @Test fun seitennameAlleinIstKeinTitel() {
        assertNull(EpisodeTitleCleaner.clean("Serienstream", null, url))
    }

    @Test fun echterTitelBleibt() {
        assertEquals("Home Invasion", EpisodeTitleCleaner.clean("Home Invasion", "Modern Family", url))
    }
}
