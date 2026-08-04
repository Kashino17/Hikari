package com.hikari.app.player

import com.hikari.app.domain.model.MusicSong
import org.junit.Test
import kotlin.test.assertEquals

class QueueNavigatorTest {

    private fun song(id: String) = MusicSong(
        videoId = id,
        title = "Song $id",
        uploader = "Uploader",
        uploaderUrl = "",
        thumbnailUrl = "",
        duration = 180,
        views = 0,
    )

    private val queue = listOf(song("a"), song("b"), song("c"))

    // --- autoAdvanceIndex ---

    @Test fun `autoAdvance nimmt den geplanten Index, wenn die mediaId dazu passt`() {
        assertEquals(
            1,
            QueueNavigator.autoAdvanceIndex(queue, currentIndex = 0, playingMediaId = "b", plannedNextIndex = 1),
        )
    }

    @Test fun `autoAdvance folgt der mediaId des Players statt dem Plan`() {
        // Shuffle-Toggle mitten im Song: Player spielt „c", geplant war „b" —
        // die Wahrheit des Players gewinnt, sonst zeigt der State auf Song B,
        // während C läuft.
        assertEquals(
            2,
            QueueNavigator.autoAdvanceIndex(queue, currentIndex = 0, playingMediaId = "c", plannedNextIndex = 1),
        )
    }

    @Test fun `autoAdvance faellt auf den Plan zurueck, wenn die mediaId unbekannt ist`() {
        assertEquals(
            1,
            QueueNavigator.autoAdvanceIndex(queue, currentIndex = 0, playingMediaId = "fremd", plannedNextIndex = 1),
        )
    }

    @Test fun `autoAdvance ohne Plan geht zum naechsten Index`() {
        assertEquals(
            1,
            QueueNavigator.autoAdvanceIndex(queue, currentIndex = 0, playingMediaId = null, plannedNextIndex = null),
        )
    }

    @Test fun `autoAdvance am Ende springt zum Anfang (Repeat-All)`() {
        assertEquals(
            0,
            QueueNavigator.autoAdvanceIndex(queue, currentIndex = 2, playingMediaId = null, plannedNextIndex = null),
        )
    }

    // --- advanceIndex ---

    @Test fun `advance vorwaerts zum naechsten Index`() {
        assertEquals(
            1,
            QueueNavigator.advanceIndex(
                queueSize = 3, currentIndex = 0, forward = true,
                shuffle = false, repeatAll = false, plannedNextIndex = null,
            ),
        )
    }

    @Test fun `advance vorwaerts am Ende mit Repeat-All springt zum Anfang`() {
        assertEquals(
            0,
            QueueNavigator.advanceIndex(
                queueSize = 3, currentIndex = 2, forward = true,
                shuffle = false, repeatAll = true, plannedNextIndex = null,
            ),
        )
    }

    @Test fun `advance vorwaerts am Ende ohne Repeat-All meldet EXTEND`() {
        assertEquals(
            QueueNavigator.EXTEND,
            QueueNavigator.advanceIndex(
                queueSize = 3, currentIndex = 2, forward = true,
                shuffle = false, repeatAll = false, plannedNextIndex = null,
            ),
        )
    }

    @Test fun `advance rueckwaerts vom Anfang springt ans Ende`() {
        assertEquals(
            2,
            QueueNavigator.advanceIndex(
                queueSize = 3, currentIndex = 0, forward = false,
                shuffle = false, repeatAll = false, plannedNextIndex = null,
            ),
        )
    }

    @Test fun `advance bei Shuffle konsumiert den geplanten Index statt neu zu wuerfeln`() {
        assertEquals(
            2,
            QueueNavigator.advanceIndex(
                queueSize = 3, currentIndex = 0, forward = true,
                shuffle = true, repeatAll = false, plannedNextIndex = 2,
            ),
        )
    }

    @Test fun `advance bei Shuffle ohne Plan wuerfelt, aber nie den aktuellen Index`() {
        // roll gibt absichtlich zuerst den aktuellen Index zurück — die
        // Schleife muss erneut würfeln.
        var calls = 0
        val roll: (IntRange) -> Int = { range ->
            calls++
            if (calls == 1) 0 else range.last
        }
        assertEquals(
            2,
            QueueNavigator.advanceIndex(
                queueSize = 3, currentIndex = 0, forward = true,
                shuffle = true, repeatAll = false, plannedNextIndex = null,
                roll = roll,
            ),
        )
        assertEquals(2, calls)
    }

    @Test fun `advance bei Shuffle mit Ein-Song-Queue bleibt auf dem Index`() {
        assertEquals(
            QueueNavigator.EXTEND,
            QueueNavigator.advanceIndex(
                queueSize = 1, currentIndex = 0, forward = true,
                shuffle = true, repeatAll = false, plannedNextIndex = null,
            ),
        )
    }

    @Test fun `advance bei Shuffle ignoriert geplanten Index ausserhalb der Queue`() {
        val roll: (IntRange) -> Int = { 1 }
        assertEquals(
            1,
            QueueNavigator.advanceIndex(
                queueSize = 3, currentIndex = 0, forward = true,
                shuffle = true, repeatAll = false, plannedNextIndex = 7,
                roll = roll,
            ),
        )
    }
}
