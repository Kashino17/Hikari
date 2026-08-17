package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

// --- Hikari backend (/music/*) ---

@Serializable
data class MusicTrackDto(
    val videoId: String,
    val title: String = "",
    val uploader: String = "",
    val thumbnailUrl: String = "",
    val durationSeconds: Int = 0,
    val uploaderUrl: String? = null,
    val views: Long? = null,
    /** Alle beteiligten Artists (Kollaborationen) — uploader bleibt der Anzeige-String. */
    val artists: List<SongArtistDto> = emptyList(),
)

@Serializable
data class SongArtistDto(
    val name: String,
    val channelId: String? = null,
)

@Serializable
data class MusicStreamDto(
    val url: String? = null,
)

// --- Piped direct fallback (used only when the backend is unreachable) ---

@Serializable
data class PipedSearchItemDto(
    val url: String? = null,
    val type: String? = null,
    val title: String? = null,
    val uploaderName: String? = null,
    val uploaderUrl: String? = null,
    val thumbnail: String? = null,
    val duration: Int? = null,
    val views: Long? = null,
)

@Serializable
data class PipedSearchPageDto(
    val items: List<PipedSearchItemDto> = emptyList(),
)

@Serializable
data class PipedAudioStreamDto(
    val url: String? = null,
    val mimeType: String? = null,
    val bitrate: Long? = null,
)

@Serializable
data class PipedStreamsDto(
    val audioStreams: List<PipedAudioStreamDto> = emptyList(),
)
