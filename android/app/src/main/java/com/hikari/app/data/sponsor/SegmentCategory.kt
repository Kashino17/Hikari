package com.hikari.app.data.sponsor

import androidx.compose.ui.graphics.Color

data class SegmentCategory(
    val apiKey: String,
    val label: String,
    val description: String,
    val color: Color,
    val defaultBehavior: SegmentBehavior,
)

object SegmentCategories {
    val all: List<SegmentCategory> = listOf(
        SegmentCategory(
            apiKey = "sponsor",
            label = "Sponsor",
            description = "Bezahlte Werbung",
            color = Color(0xFF00D400),
            defaultBehavior = SegmentBehavior.SKIP_AUTO,
        ),
        SegmentCategory(
            apiKey = "selfpromo",
            label = "Eigenwerbung",
            description = "Merch, Patreon, Shoutouts",
            color = Color(0xFFFFFF00),
            defaultBehavior = SegmentBehavior.SKIP_AUTO,
        ),
        SegmentCategory(
            apiKey = "interaction",
            label = "Like/Abo-Aufruf",
            description = "Aufruf zu liken, abonnieren, folgen",
            color = Color(0xFFCC00FF),
            defaultBehavior = SegmentBehavior.SKIP_AUTO,
        ),
        SegmentCategory(
            apiKey = "intro",
            label = "Intro",
            description = "Intro-Animation ohne Inhalt",
            color = Color(0xFF00FFFF),
            defaultBehavior = SegmentBehavior.SKIP_MANUAL,
        ),
        SegmentCategory(
            apiKey = "outro",
            label = "Outro / Endkarten",
            description = "Credits, Endkarten, Abspann",
            color = Color(0xFF0202ED),
            defaultBehavior = SegmentBehavior.SKIP_MANUAL,
        ),
        SegmentCategory(
            apiKey = "preview",
            label = "Vorschau / Recap",
            description = "Clips die zeigen was kommt oder was war",
            color = Color(0xFF008FD6),
            defaultBehavior = SegmentBehavior.IGNORE,
        ),
        SegmentCategory(
            apiKey = "hook",
            label = "Hook / Begrüßung",
            description = "Teaser für das Video, Begrüßung",
            color = Color(0xFF6B6B9A),
            defaultBehavior = SegmentBehavior.IGNORE,
        ),
        SegmentCategory(
            apiKey = "filler",
            label = "Abschweifung / Filler",
            description = "Witze, Tangenten, nicht benötigt für Hauptinhalt",
            color = Color(0xFF7300FF),
            defaultBehavior = SegmentBehavior.SKIP_MANUAL,
        ),
        SegmentCategory(
            apiKey = "music_offtopic",
            label = "Non-Music",
            description = "Nicht-Musik-Passagen in Musikvideos",
            color = Color(0xFFFF9900),
            defaultBehavior = SegmentBehavior.IGNORE,
        ),
        SegmentCategory(
            apiKey = "poi_highlight",
            label = "Highlight",
            description = "Der Hauptteil des Videos (kein Skip)",
            color = Color(0xFFFF1684),
            defaultBehavior = SegmentBehavior.IGNORE,
        ),
    )

    fun byKey(apiKey: String): SegmentCategory? = all.firstOrNull { it.apiKey == apiKey }
}
