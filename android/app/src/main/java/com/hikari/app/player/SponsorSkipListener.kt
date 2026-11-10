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

    /**
     * Fasst automatisch zu überspringende Abschnitte zusammen, zwischen denen
     * nur eine kurze Lücke liegt. Die Community markiert eine Werbepassage oft
     * in zwei, drei Stücken — ohne das Zusammenfassen springt der Player nur
     * ein Stück, zeigt ein paar Sekunden Werbung und springt wieder.
     *
     * Über eine Kapitelgrenze hinweg wird nie zusammengefasst: dort beginnt
     * erkennbar etwas Neues. Sind keine Kapitel bekannt, gilt eine kleinere Lücke.
     */
    fun mergeAutoSegments(
        segments: List<SponsorSegment>,
        behaviors: Map<String, SegmentBehavior>,
        chapterStartsMs: List<Long> = emptyList(),
    ): List<SponsorSegment> {
        val maxGap = if (chapterStartsMs.isEmpty()) GAP_WITHOUT_CHAPTERS_S else GAP_WITHIN_CHAPTER_S
        fun isAuto(s: SponsorSegment) = behaviors[s.category] == SegmentBehavior.SKIP_AUTO
        val out = mutableListOf<SponsorSegment>()
        for (next in segments.sortedBy { it.startSeconds }) {
            val last = out.lastOrNull()
            val gap = if (last != null) next.startSeconds - last.endSeconds else Double.MAX_VALUE
            val crossesChapter = last != null && chapterStartsMs.any { start ->
                val t = start / 1000.0
                t > last.endSeconds && t <= next.startSeconds
            }
            if (last != null && isAuto(last) && isAuto(next) && gap <= maxGap && !crossesChapter) {
                out[out.lastIndex] = last.copy(endSeconds = maxOf(last.endSeconds, next.endSeconds))
            } else {
                out += next
            }
        }
        return out
    }

    private const val GAP_WITHIN_CHAPTER_S = 30.0
    private const val GAP_WITHOUT_CHAPTERS_S = 20.0

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
