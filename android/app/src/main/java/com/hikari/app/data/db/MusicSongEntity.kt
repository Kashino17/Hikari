package com.hikari.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "music_songs")
data class MusicSongEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val uploader: String,
    val uploaderUrl: String,
    val thumbnailUrl: String,
    val duration: Int, // seconds
    val views: Long,
    val addedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    /**
     * Modus, in dem der Song zuletzt gehört wurde (MusicSearchMode.apiValue):
     * "Zuletzt gehört" zeigt pro Modus nur, was in genau diesem Modus lief.
     */
    @ColumnInfo(defaultValue = "music") val listenMode: String = "music",
)
