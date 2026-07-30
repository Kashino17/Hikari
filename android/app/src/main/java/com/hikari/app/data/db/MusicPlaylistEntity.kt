package com.hikari.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "music_playlists")
data class MusicPlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String = "",
    val thumbnailUrl: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)
