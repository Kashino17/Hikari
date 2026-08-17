package com.hikari.app.domain.model

/** Art eines Suchvorschlags — steuert Miniatur, Untertitel und Tap-Ziel. */
enum class SuggestionKind {
    QUERY, SONG, ARTIST, ALBUM, PLAYLIST, VIDEO,
}

/**
 * Vorschlag der Such-Autovervollständigung. Queries tragen nur [text];
 * Entity-Vorschläge bringen Mini-Thumbnail, Untertitel und die Id ihres
 * Ziels mit (Song/Video → [videoId], Artist → [channelId],
 * Album/Playlist → [playlistId]).
 */
data class SearchSuggestion(
    val text: String,
    val kind: SuggestionKind = SuggestionKind.QUERY,
    val thumbnailUrl: String? = null,
    val subtitle: String? = null,
    val videoId: String? = null,
    val channelId: String? = null,
    val playlistId: String? = null,
)
