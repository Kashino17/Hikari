package com.hikari.app.domain.genre

import com.hikari.app.data.api.dto.LibraryVideoDto
import com.hikari.app.data.api.dto.SeriesDto
import com.hikari.app.domain.model.FeedItem

object GenreClassifier {

    private val ACTION_KEYWORDS = setOf(
        "action", "abenteuer", "adventure", "kampf", "fight", "battle", "war", "krieg",
        "chase", "hero", "held", "explosion", "mission", "strike", "combat", "kung fu",
        "ninja", "samurai", "revenge", "rache", "verfolgung", "superhero"
    )

    private val SCIFI_KEYWORDS = setOf(
        "sci-fi", "scifi", "science fiction", "weltall", "space", "alien", "galaxy", "galaxis",
        "future", "zukunft", "cyber", "cyberpunk", "magic", "magie", "fantasy", "dragon", "drache",
        "star wars", "star trek", "interstellar", "dune", "matrix", "zeitreise", "time travel"
    )

    private val ANIME_KEYWORDS = setOf(
        "anime", "manga", "animation", "amv", "otaku", "crunchyroll", "ghibli", "shonen",
        "naruto", "one piece", "bleach", "jujutsu", "attack on titan", "dragon ball",
        "demon slayer", "my hero academia", "death note", "fullmetal", "isekai"
    )

    private val DOCS_KEYWORDS = setOf(
        "doku", "dokumentation", "documentary", "wissen", "science", "wissenschaft", "natur",
        "nature", "geschichte", "history", "reportage", "biologie", "planet", "universum",
        "warum", "wie funktioniert", "erklärung", "biopic", "tiere", "animals", "bbc", "national geographic"
    )

    private val COMEDY_KEYWORDS = setOf(
        "comedy", "lustig", "humor", "witz", "parodie", "parody", "satire", "sketch",
        "funny", "prank", "standup", "stand-up", "meme", "fail", "spaß", "fun", "lachen",
        "roast", "sitcom"
    )

    private val DRAMA_KEYWORDS = setOf(
        "drama", "romance", "romantik", "liebe", "love", "story", "gefühle", "tragedy",
        "tragödie", "beziehung", "trennung", "schicksal", "heartbreak", "emotional", "tränen"
    )

    private val THRILLER_KEYWORDS = setOf(
        "thriller", "krimi", "crime", "mystery", "mord", "murder", "killer", "detektiv",
        "detective", "polizei", "police", "fbi", "tatort", "true crime", "suspense",
        "psychothriller", "grusel", "horror", "spuk"
    )

    private val GAMING_KEYWORDS = setOf(
        "gaming", "gameplay", "walkthrough", "let's play", "lets play", "playthrough",
        "minecraft", "gta", "playstation", "xbox", "nintendo", "switch", "steam", "twitch",
        "esports", "speedrun", "boss fight", "multiplayer", "fps", "rpg", "zelda", "pokemon"
    )

    private val TECH_KEYWORDS = setOf(
        "tech", "technologie", "technology", "coding", "programmieren", "software",
        "hardware", "computer", "smartphone", "apple", "android", "iphone", "linux",
        "windows", "ai", "künstliche intelligenz", "chatgpt", "code", "developer", "review",
        "unboxing", "setup"
    )

    private val MUSIC_KEYWORDS = setOf(
        "musik", "music", "song", "audio", "soundtrack", "ost", "album", "single",
        "live", "concert", "konzert", "klavier", "piano", "gitarre", "guitar", "bass",
        "remix", "cover", "beat", "lofi", "hip hop", "rap", "pop", "rock", "jazz", "art", "kunst"
    )

    /**
     * Ermittelt alle passenden Genres anhand von Textfeldern.
     */
    fun classify(
        title: String,
        description: String? = null,
        channelTitle: String? = null,
        seriesTitle: String? = null,
    ): Set<Genre> {
        val combined = buildString {
            append(title.lowercase())
            append(" ")
            seriesTitle?.let { append(it.lowercase()).append(" ") }
            channelTitle?.let { append(it.lowercase()).append(" ") }
            description?.let { append(it.lowercase()).append(" ") }
        }

        val result = mutableSetOf<Genre>()

        if (containsAny(combined, ANIME_KEYWORDS)) result.add(Genre.ANIME)
        if (containsAny(combined, GAMING_KEYWORDS)) result.add(Genre.GAMING)
        if (containsAny(combined, TECH_KEYWORDS)) result.add(Genre.TECH)
        if (containsAny(combined, MUSIC_KEYWORDS)) result.add(Genre.MUSIC)
        if (containsAny(combined, SCIFI_KEYWORDS)) result.add(Genre.SCIFI)
        if (containsAny(combined, ACTION_KEYWORDS)) result.add(Genre.ACTION)
        if (containsAny(combined, DOCS_KEYWORDS)) result.add(Genre.DOCS)
        if (containsAny(combined, COMEDY_KEYWORDS)) result.add(Genre.COMEDY)
        if (containsAny(combined, THRILLER_KEYWORDS)) result.add(Genre.THRILLER)
        if (containsAny(combined, DRAMA_KEYWORDS)) result.add(Genre.DRAMA)

        // Fallback: Wenn nichts spezifisches zutrifft, Standardzuordnung
        if (result.isEmpty()) {
            result.add(Genre.DOCS)
        }

        return result
    }

    private fun containsAny(text: String, keywords: Set<String>): Boolean {
        for (kw in keywords) {
            if (kw in text) return true
        }
        return false
    }
}

fun LibraryVideoDto.detectGenres(): Set<Genre> =
    GenreClassifier.classify(
        title = title,
        description = description,
        channelTitle = channelTitle,
    )

fun SeriesDto.detectGenres(): Set<Genre> =
    GenreClassifier.classify(
        title = title,
        description = description,
    )

fun FeedItem.detectGenres(): Set<Genre> =
    GenreClassifier.classify(
        title = title,
        description = summary,
        channelTitle = channelTitle,
    )
