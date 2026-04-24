package com.hikari.app.player

import com.hikari.app.data.sponsor.SegmentBehavior
import com.hikari.app.data.sponsor.SponsorSegment
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SponsorSkipListenerTest {
    private val allAuto = mapOf(
        "sponsor" to SegmentBehavior.SKIP_AUTO,
        "intro" to SegmentBehavior.SKIP_AUTO,
    )
    private val introManual = mapOf(
        "sponsor" to SegmentBehavior.SKIP_AUTO,
        "intro" to SegmentBehavior.SKIP_MANUAL,
    )
    private val introIgnore = mapOf(
        "sponsor" to SegmentBehavior.SKIP_AUTO,
        "intro" to SegmentBehavior.IGNORE,
    )

    @Test fun none_when_outside_any_segment() {
        val segs = listOf(SponsorSegment(10.0, 20.0, "sponsor"))
        assertTrue(SponsorSkipListener.evaluate(5_000, segs, allAuto) is SponsorSkipListener.Decision.None)
    }

    @Test fun auto_returns_end_of_containing_segment() {
        val segs = listOf(SponsorSegment(10.0, 20.0, "sponsor"))
        val d = SponsorSkipListener.evaluate(12_000, segs, allAuto)
        assertTrue(d is SponsorSkipListener.Decision.Auto)
        assertEquals(20_000L, (d as SponsorSkipListener.Decision.Auto).targetMs)
    }

    @Test fun manual_when_behavior_is_manual() {
        val segs = listOf(SponsorSegment(10.0, 20.0, "intro"))
        val d = SponsorSkipListener.evaluate(12_000, segs, introManual)
        assertTrue(d is SponsorSkipListener.Decision.Manual)
    }

    @Test fun none_when_behavior_is_ignore() {
        val segs = listOf(SponsorSegment(10.0, 20.0, "intro"))
        assertTrue(SponsorSkipListener.evaluate(12_000, segs, introIgnore) is SponsorSkipListener.Decision.None)
    }

    @Test fun overlapping_segments_prefers_longer_ending() {
        val segs = listOf(
            SponsorSegment(10.0, 15.0, "sponsor"),
            SponsorSegment(10.0, 25.0, "sponsor"),
        )
        val d = SponsorSkipListener.evaluate(12_000, segs, allAuto)
        assertTrue(d is SponsorSkipListener.Decision.Auto)
        assertEquals(25_000L, (d as SponsorSkipListener.Decision.Auto).targetMs)
    }
}
