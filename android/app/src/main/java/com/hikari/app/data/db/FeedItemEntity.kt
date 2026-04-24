package com.hikari.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feed_items")
data class FeedItemEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val durationSeconds: Int,
    val aspectRatio: String,
    val thumbnailUrl: String,
    val channelId: String,
    val channelTitle: String,
    val category: String,
    val reasoning: String,
    val addedAt: Long,
    val saved: Boolean,
    val seen: Boolean,
)
