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

    // ── Abschnitte zusammenfassen ────────────────────────────────────────────

    @Test fun nearby_auto_segments_become_one_jump() {
        // So markiert die Community oft eine Werbepassage: zwei Stücke, 15 s Lücke.
        val segs = listOf(SponsorSegment(71.9, 83.0, "sponsor"), SponsorSegment(97.9, 114.8, "sponsor"))
        val merged = SponsorSkipListener.mergeAutoSegments(segs, allAuto, chapterStartsMs = listOf(0L, 71_000L, 130_000L))
        assertEquals(listOf(SponsorSegment(71.9, 114.8, "sponsor")), merged)
    }

    @Test fun segments_are_not_merged_across_a_chapter_start() {
        val segs = listOf(SponsorSegment(10.0, 20.0, "sponsor"), SponsorSegment(30.0, 40.0, "sponsor"))
        val merged = SponsorSkipListener.mergeAutoSegments(segs, allAuto, chapterStartsMs = listOf(0L, 25_000L))
        assertEquals(segs, merged)
    }

    @Test fun distant_segments_stay_separate() {
        val segs = listOf(SponsorSegment(10.0, 20.0, "sponsor"), SponsorSegment(80.0, 90.0, "sponsor"))
        assertEquals(segs, SponsorSkipListener.mergeAutoSegments(segs, allAuto))
    }

    @Test fun only_automatic_segments_are_merged() {
        val segs = listOf(SponsorSegment(10.0, 20.0, "sponsor"), SponsorSegment(25.0, 40.0, "intro"))
        assertEquals(segs, SponsorSkipListener.mergeAutoSegments(segs, introManual))
        assertEquals(
            listOf(SponsorSegment(10.0, 40.0, "sponsor")),
            SponsorSkipListener.mergeAutoSegments(segs, allAuto),
        )
    }
}
