package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

// --- YouTube-Music-artige Suche (/music/search/*) ---

@Serializable
data class SearchArtistDto(
    val channelId: String,
    val name: String = "",
    val thumbnailUrl: String = "",
    val subscribers: Long = 0,
)

@Serializable
data class SearchAlbumDto(
    val playlistId: String,
    val name: String = "",
    val artistName: String = "",
    val thumbnailUrl: String = "",
    val videoCount: Int = 0,
)

@Serializable
data class SearchPlaylistDto(
    val playlistId: String,
    val name: String = "",
    val uploaderName: String = "",
    val thumbnailUrl: String = "",
    val videoCount: Int = 0,
)

/**
 * Union der vier Top-Ergebnis-Shapes: [type] sagt, welche Felder belegt sind
 * ("artist", "song", "album", "playlist"). Robust gegenüber polymorpher
 * Serialisierung, die das Backend ohnehin nicht sauber auszeichnen könnte.
 */
@Serializable
data class TopResultDto(
    val type: String,
    // Song
    val videoId: String? = null,
    val title: String? = null,
    val uploader: String? = null,
    val durationSeconds: Int? = null,
    val uploaderUrl: String? = null,
    val views: Long? = null,
    // Artist
    val channelId: String? = null,
    val subscribers: Long? = null,
    // Album / Playlist
    val playlistId: String? = null,
    val name: String? = null,
    val artistName: String? = null,
    val uploaderName: String? = null,
    val videoCount: Int? = null,
    // gemeinsam
    val thumbnailUrl: String? = null,
)

@Serializable
data class FullSearchDto(
    val topResult: TopResultDto? = null,
    val songs: List<MusicTrackDto> = emptyList(),
    val artists: List<SearchArtistDto> = emptyList(),
    val albums: List<SearchAlbumDto> = emptyList(),
    val playlists: List<SearchPlaylistDto> = emptyList(),
)
