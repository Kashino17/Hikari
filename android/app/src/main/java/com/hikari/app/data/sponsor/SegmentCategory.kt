package com.hikari.app.data.sponsor

import androidx.compose.ui.graphics.Color

data class SegmentCategory(
    val apiKey: String,
    val label: String,
    val description: String,
    val color: Color,
    val defaultBehavior: SegmentBehavior,
    /** Kurzform für den Knopf im Player („Intro überspringen"). */
    val shortLabel: String = label,
)

object SegmentCategories {
    val all: List<SegmentCategory> = listOf(
        SegmentCategory(
            apiKey = "sponsor",
            label = "Sponsor",
            description = "Bezahlte Werbung",
            color = Color(0xFF34D399),
            defaultBehavior = SegmentBehavior.SKIP_AUTO,
        ),
        SegmentCategory(
            apiKey = "selfpromo",
            label = "Eigenwerbung",
            description = "Merch, Patreon, Shoutouts",
            color = Color(0xFFFDE047),
            defaultBehavior = SegmentBehavior.SKIP_AUTO,
        ),
        SegmentCategory(
            apiKey = "interaction",
            label = "Like/Abo-Aufruf",
            description = "Aufruf zu liken, abonnieren, folgen",
            color = Color(0xFFE879F9),
            defaultBehavior = SegmentBehavior.SKIP_AUTO,
            shortLabel = "Abo-Aufruf",
        ),
        SegmentCategory(
            apiKey = "intro",
            label = "Intro",
            description = "Intro-Animation ohne Inhalt",
            color = Color(0xFF22D3EE),
            defaultBehavior = SegmentBehavior.SKIP_MANUAL,
        ),
        SegmentCategory(
            apiKey = "outro",
            label = "Outro / Endkarten",
            description = "Credits, Endkarten, Abspann",
            color = Color(0xFF6D8BFF),
            defaultBehavior = SegmentBehavior.SKIP_MANUAL,
            shortLabel = "Outro",
        ),
        SegmentCategory(
            apiKey = "preview",
            label = "Vorschau / Recap",
            description = "Ausblick auf später oder Rückblick",
            color = Color(0xFF38BDF8),
            defaultBehavior = SegmentBehavior.IGNORE,
            shortLabel = "Vorschau",
        ),
        SegmentCategory(
            apiKey = "hook",
            label = "Hook / Begrüßung",
            description = "Teaser am Anfang, Begrüßung",
            color = Color(0xFFA5A5D6),
            defaultBehavior = SegmentBehavior.IGNORE,
            shortLabel = "Hook",
        ),
        SegmentCategory(
            apiKey = "filler",
            label = "Abschweifung / Filler",
            description = "Witze und Exkurse abseits des Themas",
            color = Color(0xFFA78BFA),
            defaultBehavior = SegmentBehavior.SKIP_MANUAL,
            shortLabel = "Abschweifung",
        ),
        SegmentCategory(
            apiKey = "music_offtopic",
            label = "Nicht-Musik",
            description = "Nicht-Musik-Passagen in Musikvideos",
            color = Color(0xFFFB923C),
            defaultBehavior = SegmentBehavior.IGNORE,
            shortLabel = "Nicht-Musik-Teil",
        ),
    )

    fun byKey(apiKey: String): SegmentCategory? = all.firstOrNull { it.apiKey == apiKey }
}
