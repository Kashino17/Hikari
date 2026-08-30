package com.hikari.app.domain.browser

/**
 * Liest Serienname und Staffel aus der URL einer Folgenseite.
 *
 * Zwei Muster genuegen fuer den Grossteil der Streaming-Seiten:
 *  - aniworld-Stil: `/serie/stream/<slug>/staffel-<N>/episode-<M>`
 *  - generisch: irgendwo im Pfad ein Segment `staffel-N` oder `season-N`
 *
 * Der Serien-Slug ist das Segment direkt vor `staffel`/`season` — aber nur,
 * wenn es kein Container-Wort wie "serie" oder "stream" ist. Aus dem Slug
 * wird ein lesbarer Titel: Bindestriche zu Leerzeichen, Wortanfaenge gross
 * ("solo-leveling" → "Solo Leveling").
 */
object PageMetaParser {

    data class PageMeta(
        val seriesTitle: String? = null,
        val season: Int? = null,
    )

    private val SEASON_SEGMENT =
        Regex("""(?:staffel|season)-(\d{1,3})""", RegexOption.IGNORE_CASE)

    /** Pfadsegmente, die Struktur statt Serienname tragen. */
    private val NOISE_SEGMENTS = setOf("serie", "series", "stream", "anime")

    fun parse(url: String): PageMeta {
        val path = runCatching { java.net.URI(url).path.orEmpty() }.getOrDefault("")
        val segments = path.split('/').filter { it.isNotBlank() }

        val seasonIndex = segments.indexOfFirst { SEASON_SEGMENT.matches(it) }
        if (seasonIndex < 0) return PageMeta()

        val season = SEASON_SEGMENT.matchEntire(segments[seasonIndex])
            ?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?.takeIf { it in 1..100 }

        val slug = segments.getOrNull(seasonIndex - 1)
            ?.takeIf { it.lowercase() !in NOISE_SEGMENTS }
            ?.let { humanize(it) }

        return PageMeta(seriesTitle = slug, season = season)
    }

    /** "solo-leveling" → "Solo Leveling" */
    private fun humanize(slug: String): String =
        slug.split('-', '_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
}
