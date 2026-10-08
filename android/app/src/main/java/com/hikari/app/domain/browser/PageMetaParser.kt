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
        /**
         * DOM-Angaben (aus dem Seiten-Scan) gewinnen bei Namen und Titeln über
         * URL-Ableitungen. Staffel und Folge dagegen kommen zuerst aus der URL:
         * "/staffel-2/episode-3" ist eindeutig, Überschriften können Teaser oder
         * Folgenlisten erwischen.
         */
        fun mergedWith(dom: PageMeta?): PageMeta {
            if (dom == null) return this
            return PageMeta(
                seriesTitle = dom.seriesTitle?.takeIf { it.isNotBlank() } ?: seriesTitle,
                season = season ?: dom.season,
                episode = episode ?: dom.episode,
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
        val headings = o.optJSONArray("headings")?.let { arr ->
            (0 until arr.length()).map { arr.optString(it) }
        }.orEmpty()
        val fromHeadings = HeadingEpisode.find(headings)
        val seriesName = (str("seriesName") ?: fromHeadings?.seriesName)
            ?.takeIf { it.length >= 2 && it.length <= 120 }
        val episodeName = (str("episodeName") ?: fromHeadings?.title)?.takeIf { it.length >= 2 }
        return PageMeta(
            seriesTitle = if (isMovie) null else seriesName,
            season = if (isMovie) null else int("season") ?: fromHeadings?.season,
            episode = if (isMovie) null else int("episode") ?: fromHeadings?.episode,
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

/**
 * Folgenangaben aus den Überschriften einer Seite.
 *
 * Gemessen an serienstream.to: `<h1>Ted</h1>` gefolgt von
 * `<h2>S01E02: Schulanfang (2) (Just Say Yes (2))</h2>`. Der `<title>` der Seite
 * ("Ted S01E02 | SerienStream") und die Meta-Tags nennen den Folgentitel gar
 * nicht — hier steht er allein.
 */
object HeadingEpisode {

    data class Found(val seriesName: String?, val season: Int?, val episode: Int?, val title: String)

    private val SE_HEADING = Regex("""^S(\d{1,2})\s*E(\d{1,4})\s*[:\-–—.]\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val EP_HEADING = Regex(
        """^(?:(?:staffel|season)\s*(\d{1,2})\s*[,\-–]?\s*)?(?:folge|episode|ep\.?)\s*(\d{1,4})\s*[:\-–—.]\s*(.+)$""",
        RegexOption.IGNORE_CASE,
    )

    fun find(headings: List<String>): Found? {
        val clean = headings.map { it.trim().replace(Regex("\\s+"), " ") }.filter { it.isNotEmpty() }
        // Nennen mehrere Überschriften verschiedene Folgen, ist es eine Folgenliste
        // oder ein Teaser — dann ist "die erste" nur Zufall (daher überall Folge 1).
        val numbers = clean.mapNotNull { h ->
            (SE_HEADING.matchEntire(h) ?: EP_HEADING.matchEntire(h))?.let { m ->
                m.groupValues[1] to m.groupValues[2]
            }
        }.distinct()
        if (numbers.size > 1) return null
        for ((i, h) in clean.withIndex()) {
            val se = SE_HEADING.matchEntire(h)
            val ep = if (se == null) EP_HEADING.matchEntire(h) else null
            val (season, episode, raw) = when {
                se != null -> Triple(se.groupValues[1].toIntOrNull(), se.groupValues[2].toIntOrNull(), se.groupValues[3])
                ep != null -> Triple(ep.groupValues[1].toIntOrNull(), ep.groupValues[2].toIntOrNull(), ep.groupValues[3])
                else -> continue
            }
            val title = dropAlternativeTitle(raw).trim()
            if (title.length < 2) continue
            // Der Serienname steht in der Überschrift davor (h1) — sofern sie
            // selbst keine Folgenangabe ist.
            val series = clean.take(i).firstOrNull {
                it.length in 2..120 && SE_HEADING.matchEntire(it) == null && EP_HEADING.matchEntire(it) == null
            }
            return Found(series, season, episode, title)
        }
        return null
    }

    /**
     * "Schulanfang (2) (Just Say Yes (2))" → "Schulanfang (2)": Hängt hinter dem
     * deutschen Titel in Klammern der englische, fliegt er raus. Eine einzelne
     * Klammer ("Das Ende (Teil 2)") oder eine reine Zahl bleibt.
     */
    internal fun dropAlternativeTitle(title: String): String {
        val groups = mutableListOf<IntRange>()
        var depth = 0
        var start = -1
        for ((i, c) in title.withIndex()) {
            if (c == '(') { if (depth == 0) start = i; depth++ }
            else if (c == ')' && depth > 0) {
                depth--
                if (depth == 0) groups.add(start..i)
            }
        }
        if (groups.isEmpty()) return title
        val last = groups.last()
        if (last.last != title.length - 1 || last.first == 0) return title
        val inner = title.substring(last.first + 1, last.last).trim()
        if (inner.all { it.isDigit() }) return title
        // Eine einzelne Klammer ist nur dann der englische Titel, wenn sie
        // keine Zählung ist ("Das Ende (Teil 2)", "(Part II)").
        val counting = inner.any { it.isDigit() } ||
            Regex("""^(teil|part|pt\.?|vol\.?)\s*[ivx]+$""", RegexOption.IGNORE_CASE).matches(inner)
        if (groups.size < 2 && counting) return title
        return title.substring(0, last.first).trimEnd()
    }
}

/**
 * Entfernt Werbe-Floskeln, die Streamingseiten vor die eigentliche
 * Beschreibung setzen: "Schaue Ted Staffel 1 Episode 2 online an. Teds und
 * Johns Plan …" → "Teds und Johns Plan …". Bleibt nichts übrig (reiner
 * Standardtext), kommt null — dann ist besser keine Beschreibung als eine falsche.
 */
object DescriptionCleaner {
    private val WATCH_PREFIX = Regex(
        """^(?:schaue|sieh|stream(?:e)?|watch|jetzt)\b[^.!?]{0,160}?\b(?:online|an|ansehen|streamen|anschauen|kostenlos|stream|gratis)\b[^.!?]{0,60}[.!]\s*""",
        RegexOption.IGNORE_CASE,
    )
    private val ALL_EPISODES = Regex("""^alle\s+(?:episoden|folgen)\b[^.!?]*[.!]\s*""", RegexOption.IGNORE_CASE)

    fun clean(raw: String?): String? {
        val original = raw?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        var t = original
        repeat(2) {
            t = WATCH_PREFIX.replace(t, "")
            t = ALL_EPISODES.replace(t, "")
        }
        t = t.trim()
        // Wurde nichts entfernt, bleibt der Text wie er ist. Blieb nach dem
        // Entfernen nur ein Fetzen übrig, war es reiner Standardtext.
        return if (t == original) t.takeIf { it.isNotEmpty() } else t.takeIf { it.length >= 12 }
    }
}
