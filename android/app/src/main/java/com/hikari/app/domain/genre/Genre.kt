package com.hikari.app.domain.genre

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.SentimentSatisfiedAlt
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class Genre(
    val id: String,
    val title: String,
    val accentColor: Color,
) {
    ALL("all", "Alle", Color(0xFFF59E0B)),
    ACTION("action", "Action & Abenteuer", Color(0xFFEF4444)),
    SCIFI("scifi", "Sci-Fi & Fantasy", Color(0xFF8B5CF6)),
    ANIME("anime", "Anime & Animation", Color(0xFFEC4899)),
    DOCS("docs", "Doku & Wissen", Color(0xFF10B981)),
    COMEDY("comedy", "Comedy & Humor", Color(0xFFFBBF24)),
    DRAMA("drama", "Drama & Emotion", Color(0xFFF97316)),
    THRILLER("thriller", "Thriller & Krimi", Color(0xFF6366F1)),
    GAMING("gaming", "Gaming & Esports", Color(0xFF14B8A6)),
    TECH("tech", "Tech & Zukunft", Color(0xFF06B6D4)),
    MUSIC("music", "Musik & Kunst", Color(0xFFA855F7));

    val icon: ImageVector
        get() = when (this) {
            ALL -> Icons.Outlined.GridView
            ACTION -> Icons.Outlined.Bolt
            SCIFI -> Icons.Outlined.RocketLaunch
            ANIME -> Icons.Outlined.Palette
            DOCS -> Icons.Outlined.Psychology
            COMEDY -> Icons.Outlined.SentimentSatisfiedAlt
            DRAMA -> Icons.Outlined.TheaterComedy
            THRILLER -> Icons.Outlined.Visibility
            GAMING -> Icons.Outlined.SportsEsports
            TECH -> Icons.Outlined.Memory
            MUSIC -> Icons.Outlined.Headphones
        }

    /** Leer-Fallback für Codestellen ohne Emojis */
    val emoji: String get() = ""

    companion object {
        fun fromId(id: String?): Genre = entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: ALL
    }
}

