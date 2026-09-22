package com.hikari.app.domain.browser

/**
 * Liest Serienname, Staffel, Folge und Film-Hinweis aus der URL einer Seite —
 * dieselben Regeln wie `episode-parser.ts` im Backend, damit die Import-Karte
 * dasselbe zeigt, was der Server später speichert.
 *
 * Muster:
 *  - aniworld/serienstream: `/serie/stream/<slug>/staffel-<N>/episode-<M>`
 *  - generisch: `staffel-N`/`season-N`/`s-N`, `episode-M`/`folge-M`/`ep-M`,
 *    `SxxEyy` inline (`arcane-s02e05`), `/film/`, `/filme/`, `/movie/`.
 *
 * Der Serien-Slug ist das erste Segment vor Staffel/Folge, das kein
 * Container-Wort ("serie", "stream", …) ist. Aus dem Slug wird ein lesbarer
 * Titel ("solo-leveling" → "Solo Leveling").
 *
 * Was die Seite selbst über sich sagt (JSON-LD, og:title, h1) ist verlässlicher
 * als der Slug — [PageMeta.mergedWith] lässt das DOM gewinnen.
 */
object PageMetaParser {

    data class PageMeta(
        val seriesTitle: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
        /** Echter Folgentitel („Home Invasion"), falls die Seite ihn nennt. */
        val episodeTitle: String? = null,
        val isMovie: Boolean = false,
    ) {
        /** DOM-Angaben (aus dem Seiten-Scan) gewinnen über URL-Ableitungen. */
        fun mergedWith(dom: PageMeta?): PageMeta {
            if (dom == null) return this
            return PageMeta(
                seriesTitle = dom.seriesTitle?.takeIf { it.isNotBlank() } ?: seriesTitle,
                season = dom.season ?: season,
                episode = dom.episode ?: episode,
                episodeTitle = dom.episodeTitle?.takeIf { it.isNotBlank() } ?: episodeTitle,
                isMovie = dom.isMovie || (isMovie && dom.episode == null && dom.seriesTitle.isNullOrBlank()),
            )
        }
    }

    private val SEASON_SEGMENT = Regex("""^(?:staffel|season|s)[-_]?(\d{1,3})$""", RegexOption.IGNORE_CASE)
    private val EPISODE_SEGMENT = Regex("""^(?:episode|folge|ep)[-_]?(\d{1,4})$""", RegexOption.IGNORE_CASE)
    private val SE_INLINE = Regex("""(?:^|[-_])s(\d{1,2})e(\d{1,4})(?:[-_]|$)""", RegexOption.IGNORE_CASE)
    private val MOVIE_SEGMENTS = setOf("film", "filme", "movie", "movies", "kinofilm", "kinofilme")

    /** Pfadsegmente, die Struktur statt Serienname tragen. */
    private val NOISE_SEGMENTS = setOf(
        "serie", "series", "serien", "stream", "anime", "animes", "watch", "video", "videos",
        "film", "filme", "movie", "movies", "staffel", "season", "episode", "folge",
        "deutsch", "german", "ger-sub", "ger-dub", "sub", "dub",
    )

    fun parse(url: String): PageMeta {
        val path = runCatching { java.net.URI(url).path.orEmpty() }.getOrDefault("")
        val segments = path.split('/').map { it.trim() }.filter { it.isNotBlank() }

        var season: Int? = null
        var episode: Int? = null
        var seriesIdx = -1
        var inlineSeries: String? = null

        segments.forEachIndexed { i, seg ->
            val se = SE_INLINE.find(seg)
            if (se != null) {
                val s = se.groupValues[1].toIntOrNull()
                val e = se.groupValues[2].toIntOrNull()
                if (s != null && e != null && s in 1..100 && e in 1..2000) {
                    if (season == null) season = s
                    if (episode == null) episode = e
                    if (seriesIdx < 0) seriesIdx = i - 1
                    if (se.range.first > 0) inlineSeries = humanize(seg.substring(0, se.range.first))
                    return@forEachIndexed
                }
            }
            SEASON_SEGMENT.matchEntire(seg)?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..100 }?.let {
                if (season == null) season = it
                if (seriesIdx < 0) seriesIdx = i - 1
                return@forEachIndexed
            }
            EPISODE_SEGMENT.matchEntire(seg)?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..2000 }?.let {
                if (episode == null) episode = it
                if (seriesIdx < 0 && season == null) seriesIdx = i - 1
            }
        }

        var seriesTitle: String? = null
        for (i in seriesIdx downTo 0) {
            val seg = segments[i]
            if (seg.lowercase() in NOISE_SEGMENTS) continue
            if (SEASON_SEGMENT.matches(seg) || EPISODE_SEGMENT.matches(seg)) continue
            seriesTitle = humanize(seg)?.takeIf { it.isNotBlank() }
            if (seriesTitle != null) break
        }
        if (seriesTitle == null) seriesTitle = inlineSeries?.takeIf { it.isNotBlank() }

        val isMovie = episode == null && segments.any { it.lowercase() in MOVIE_SEGMENTS }
        return PageMeta(
            seriesTitle = if (isMovie) null else seriesTitle,
            season = if (isMovie) null else season,
            episode = episode,
            isMovie = isMovie,
        )
    }

    /**
     * DOM-Angaben aus dem Seiten-Scan ([PageScripts.SCAN]) zu einer
     * [PageMeta]. Alles optional — was die Seite nicht nennt, bleibt null.
     */
    fun fromScan(o: org.json.JSONObject): PageMeta {
        fun str(key: String): String? = o.optString(key).trim().replace(Regex("\\s+"), " ").takeIf { it.isNotBlank() }
        fun int(key: String): Int? = if (o.isNull(key)) null else o.optInt(key, -1).takeIf { it > 0 }
        val isMovie = o.optBoolean("isMovie", false)
        val seriesName = str("seriesName")?.takeIf { it.length >= 2 && it.length <= 120 }
        val episodeName = str("episodeName")?.takeIf { it.length >= 2 }
        return PageMeta(
            seriesTitle = if (isMovie) null else seriesName,
            season = if (isMovie) null else int("season"),
            episode = if (isMovie) null else int("episode"),
            episodeTitle = if (isMovie) str("movieName") ?: episodeName else episodeName,
            isMovie = isMovie,
        )
    }

    /** "solo-leveling" → "Solo Leveling" */
    fun humanize(slug: String): String? {
        val cleaned = runCatching { java.net.URLDecoder.decode(slug, "UTF-8") }.getOrDefault(slug)
            .replace(Regex("""\.(mp4|mkv|webm|m3u8|mpd|html?|php)$""", RegexOption.IGNORE_CASE), "")
            .split('-', '_', '+')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
        return cleaned.takeIf { it.length >= 3 }
    }
}
