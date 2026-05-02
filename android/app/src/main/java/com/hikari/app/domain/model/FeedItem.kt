package com.hikari.app.domain.model

data class FeedItem(
    val videoId: String,
    val title: String,
    val durationSeconds: Int,
    val aspectRatio: String?,
    val thumbnailUrl: String?,
    val channelTitle: String,
    val category: String,
    val reasoning: String,
    val saved: Boolean,
    val overallScore: Int? = null,
    val educationalValue: Int? = null,
    // Auto-Clipper fields kept at the end so existing positional callers
    // (incl. tests that predate the clipper) keep compiling without churn.
    val kind: String = "legacy",
    val parentVideoId: String = "",
)
