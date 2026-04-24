package com.hikari.app.player

import com.hikari.app.data.sponsor.SegmentBehavior
import com.hikari.app.data.sponsor.SponsorSegment

object SponsorSkipListener {
    sealed class Decision {
        data object None : Decision()
        data class Auto(val segment: SponsorSegment, val targetMs: Long) : Decision()
        data class Manual(val segment: SponsorSegment, val targetMs: Long) : Decision()
    }

    /**
     * Given the current playback position, segments for this video, and the user's
     * per-category behavior, decide what to do:
     *   - Auto → player should seekTo(targetMs)
     *   - Manual → UI should show a "skip" pill with onClick seekTo(targetMs)
     *   - None → nothing to do
     *
     * If multiple segments contain the current position, prefer the one ending latest
     * (largest endMs). This avoids bouncing between nested segments.
     */
    fun evaluate(
        currentMs: Long,
        segments: List<SponsorSegment>,
        behaviors: Map<String, SegmentBehavior>,
    ): Decision {
        if (segments.isEmpty()) return Decision.None
        val currentSeconds = currentMs / 1000.0
        val containing = segments.filter {
            currentSeconds >= it.startSeconds && currentSeconds < it.endSeconds
        }.maxByOrNull { it.endSeconds } ?: return Decision.None

        val behavior = behaviors[containing.category] ?: SegmentBehavior.IGNORE
        val targetMs = (containing.endSeconds * 1000).toLong()
        return when (behavior) {
            SegmentBehavior.SKIP_AUTO -> Decision.Auto(containing, targetMs)
            SegmentBehavior.SKIP_MANUAL -> Decision.Manual(containing, targetMs)
            SegmentBehavior.IGNORE -> Decision.None
        }
    }

    /** Legacy helper kept for backward compat while migrating callers. */
    @Deprecated("Use evaluate with behaviors map", ReplaceWith("evaluate(...)"))
    fun skipTargetMs(currentMs: Long, segments: List<SponsorSegment>): Long? =
        evaluate(
            currentMs,
            segments,
            behaviors = com.hikari.app.data.sponsor.SegmentCategories.all
                .associate { cat -> cat.apiKey to SegmentBehavior.SKIP_AUTO },
        ).let { d -> (d as? Decision.Auto)?.targetMs }
}
