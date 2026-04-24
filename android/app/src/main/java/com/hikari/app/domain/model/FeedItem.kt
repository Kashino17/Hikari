package com.hikari.app.domain.model

data class FeedItem(
    val videoId: String,
    val title: String,
    val durationSeconds: Int,
    val aspectRatio: String,
    val thumbnailUrl: String,
    val channelTitle: String,
    val category: String,
    val reasoning: String,
    val saved: Boolean,
)
