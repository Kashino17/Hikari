package com.hikari.app.player

import com.hikari.app.data.sponsor.SponsorSegment

object SponsorSkipListener {
    fun skipTargetMs(currentMs: Long, segments: List<SponsorSegment>): Long? {
        val current = currentMs / 1000.0
        val inside = segments.firstOrNull { current >= it.startSeconds && current < it.endSeconds }
        return inside?.let { (it.endSeconds * 1000).toLong() }
    }
}
