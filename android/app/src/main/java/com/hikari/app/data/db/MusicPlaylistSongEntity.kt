package com.hikari.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "music_playlist_songs",
    primaryKeys = ["playlistId", "songVideoId"],
    foreignKeys = [
        ForeignKey(
            entity = MusicPlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MusicSongEntity::class,
            parentColumns = ["videoId"],
            childColumns = ["songVideoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["songVideoId"])],
)
data class MusicPlaylistSongEntity(
    val playlistId: Int,
    val songVideoId: String,
    val addedAt: Long = System.currentTimeMillis(),
    /** Manuelle Sortierposition; Bestandszeilen (0) ordnen nach addedAt. */
    val position: Int = 0,
)
