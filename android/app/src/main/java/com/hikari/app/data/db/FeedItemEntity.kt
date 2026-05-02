package com.hikari.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feed_items")
data class FeedItemEntity(
    @PrimaryKey val videoId: String,
    // Clipper-Felder. Müssen via Room-Migration 7→8 ergänzt werden, sonst werden
    // Clip-Items als 'legacy' missinterpretiert und Android holt sie unter
    // /videos/<id>.mp4 statt /clips/<id>.mp4 (404 → Black-Screen).
    @ColumnInfo(defaultValue = "legacy") val kind: String = "legacy",
    @ColumnInfo(defaultValue = "") val parentVideoId: String = "",
    val title: String,
    val durationSeconds: Int,
    val aspectRatio: String?,
    val thumbnailUrl: String?,
    val channelId: String,
    val channelTitle: String,
    val category: String,
    val reasoning: String,
    val overallScore: Int? = null,
    val educationalValue: Int? = null,
    val addedAt: Long,
    val saved: Boolean,
    val seen: Boolean,
    @ColumnInfo(defaultValue = "NULL") val captionsJson: String? = null,
)
