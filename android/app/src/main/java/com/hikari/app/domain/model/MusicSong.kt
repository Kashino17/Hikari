package com.hikari.app.domain.model

data class MusicSong(
    val videoId: String,
    val title: String,
    val uploader: String,
    val uploaderUrl: String,
    val thumbnailUrl: String,
    val duration: Int, // seconds
    val views: Long,
    val addedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
)

data class MusicPlaylist(
    val id: Int = 0,
    val name: String,
    val description: String = "",
    val thumbnailUrl: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

data class PlaylistSong(
    val playlistId: Int,
    val song: MusicSong,
    val addedAt: Long,
)
