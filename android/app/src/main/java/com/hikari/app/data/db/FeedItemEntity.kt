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
    @ColumnInfo(defaultValue = "NULL") val context: String? = null,
    // KI-Kurzbeschreibung fuer Langvideo-Vorschaukarten (Etappe 2).
    @ColumnInfo(defaultValue = "NULL") val summary: String? = null,
    // Server-assigned feed position. The backend curates + interleaves the
    // order for variety; we MUST render in that order, not re-sort by addedAt
    // (which clusters one channel and kills the variety). Set from the server
    // response index on each refresh.
    @ColumnInfo(defaultValue = "0") val position: Int = 0,
)
