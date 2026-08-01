package com.hikari.app.domain.model

/** Künstler-Profil für die Artist-Seite. */
data class Artist(
    val channelId: String,
    val name: String,
    val avatarUrl: String?,
    val bannerUrl: String?,
    val subscriberCount: Long,
    val description: String,
    val verified: Boolean,
)

/**
 * Playlist eines Künstlers. Der Inhalt wird bewusst über die Suche geladen
 * (Mix-Flow), weil die Piped-Playlist-Endpunkte degradiert sind.
 */
data class ArtistPlaylist(
    val playlistId: String,
    val name: String,
    val thumbnailUrl: String,
    val videoCount: Int,
    val uploaderName: String,
)
