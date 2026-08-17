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
    /**
     * Alle beteiligten Artists (Kollaborationen). Nur bei frisch vom Backend
     * geladenen Songs gefüllt — Room-Persistenz (Verlauf/Downloads) speichert
     * weiterhin nur den zusammengesetzten [uploader]-String.
     */
    val artists: List<SongArtist> = emptyList(),
)

/** Ein einzelner Artist eines Songs — [channelId] öffnet dessen Seite. */
data class SongArtist(
    val name: String,
    val channelId: String? = null,
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
