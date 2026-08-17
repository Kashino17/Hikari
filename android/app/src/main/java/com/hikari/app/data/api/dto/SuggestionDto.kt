package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

/**
 * Suchvorschlag von /music/suggestions: reine Text-Query (kind "query")
 * oder Entity-Treffer mit Mini-Thumbnail. Alle Felder defensiv optional —
 * unbekannte kinds behandelt der Mapper wie Queries.
 */
@Serializable
data class SuggestionDto(
    val text: String = "",
    val kind: String = "query",
    val thumbnailUrl: String? = null,
    val subtitle: String? = null,
    val videoId: String? = null,
    val channelId: String? = null,
    val playlistId: String? = null,
)
