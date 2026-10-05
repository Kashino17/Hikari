package com.hikari.app.domain.genre

import androidx.compose.ui.graphics.Color

enum class Genre(
    val id: String,
    val title: String,
    val emoji: String,
    val accentColor: Color,
) {
    ALL("all", "Alle", "✨", Color(0xFFF59E0B)),
    ACTION("action", "Action & Abenteuer", "🎬", Color(0xFFEF4444)),
    SCIFI("scifi", "Sci-Fi & Fantasy", "🚀", Color(0xFF8B5CF6)),
    ANIME("anime", "Anime & Animation", "⛩️", Color(0xFFEC4899)),
    DOCS("docs", "Doku & Wissen", "🧠", Color(0xFF10B981)),
    COMEDY("comedy", "Comedy & Humor", "😂", Color(0xFFFBBF24)),
    DRAMA("drama", "Drama & Emotion", "🎭", Color(0xFFF97316)),
    THRILLER("thriller", "Thriller & Krimi", "🔍", Color(0xFF6366F1)),
    GAMING("gaming", "Gaming & Esports", "🎮", Color(0xFF14B8A6)),
    TECH("tech", "Tech & Zukunft", "💻", Color(0xFF06B6D4)),
    MUSIC("music", "Musik & Kunst", "🎵", Color(0xFFA855F7));

    companion object {
        fun fromId(id: String?): Genre = entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: ALL
    }
}
