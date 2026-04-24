package com.hikari.app.player

import com.hikari.app.data.sponsor.SponsorSegment
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SponsorSkipListenerTest {
    @Test fun noSegments_returnsNull() {
        assertNull(SponsorSkipListener.skipTargetMs(currentMs = 5000, segments = emptyList()))
    }

    @Test fun currentInsideSegment_returnsEndMs() {
        val segs = listOf(SponsorSegment(10.0, 20.0, "sponsor"))
        assertEquals(20_000L, SponsorSkipListener.skipTargetMs(currentMs = 12_000, segments = segs))
    }

    @Test fun currentBeforeAllSegments_returnsNull() {
        val segs = listOf(SponsorSegment(10.0, 20.0, "sponsor"))
        assertNull(SponsorSkipListener.skipTargetMs(currentMs = 5000, segments = segs))
    }

    @Test fun currentBetweenSegments_returnsNull() {
        val segs = listOf(
            SponsorSegment(5.0, 10.0, "sponsor"),
            SponsorSegment(30.0, 40.0, "selfpromo"),
        )
        assertNull(SponsorSkipListener.skipTargetMs(currentMs = 20_000, segments = segs))
    }
}
