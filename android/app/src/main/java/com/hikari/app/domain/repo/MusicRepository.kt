package com.hikari.app.domain.repo

import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.ArtistAlbumDto
import com.hikari.app.data.api.dto.ArtistDto
import com.hikari.app.data.api.dto.ArtistPlaylistDto
import com.hikari.app.data.api.dto.MusicTrackDto
import com.hikari.app.data.api.dto.PipedSearchPageDto
import com.hikari.app.data.api.dto.PipedStreamsDto
import com.hikari.app.data.api.dto.SearchAlbumDto
import com.hikari.app.data.api.dto.SearchArtistDto
import com.hikari.app.data.api.dto.SearchPlaylistDto
import com.hikari.app.data.api.dto.TopResultDto
import com.hikari.app.data.db.LocalMusicDownloadDao
import com.hikari.app.data.db.MusicPlaylistDao
import com.hikari.app.data.db.MusicPlaylistEntity
import com.hikari.app.data.db.MusicPlaylistSongDao
import com.hikari.app.data.db.MusicPlaylistSongEntity
import com.hikari.app.data.db.MusicSongDao
import com.hikari.app.data.db.MusicSongEntity
import com.hikari.app.data.db.SearchHistoryDao
import com.hikari.app.data.db.SearchHistoryEntity
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.model.Artist
import com.hikari.app.domain.model.ArtistAlbum
import com.hikari.app.domain.model.ArtistPage
import com.hikari.app.domain.model.ArtistPlaylist
import com.hikari.app.domain.model.FullSearchResults
import com.hikari.app.domain.model.HomeSection
import com.hikari.app.domain.model.HomeSectionKind
import com.hikari.app.domain.model.MusicAlbum
import com.hikari.app.domain.model.MusicPlaylist
import com.hikari.app.domain.model.MusicSearchResult
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.domain.model.RemotePlaylist
import com.hikari.app.domain.model.SearchArtist
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

data class DiscoverSection(
    val title: String,
    val songs: List<MusicSong>,
    /** Suchbegriff hinter dem Mix — erlaubt es, ihn später erneut zu laden. */
    val query: String = "",
)

/**
 * Suchmodus der Musik-Suche. YouTube hat Hörbücher und Podcasts nicht als
 * eigenen Filter — sie laufen als gewöhnliche Videosuche, die über ein
 * Stichwort im Query und eine Dauerheuristik in Richtung des Formats gelenkt
 * wird.
 */
enum class MusicSearchMode(
    /** Wert für den `mode`-Parameter des Backends. */
    val apiValue: String,
    /** Piped-Filter für den Direkt-Fallback ohne Backend. */
    val pipedFilter: String,
    /** Mindestdauer in Sekunden — kurze Clips sind selten Hörbuch oder Episode. */
    val minDurationSeconds: Int,
) {
    MUSIC("music", "music_songs", 0),
    AUDIOBOOK("audiobook", "videos", 600),
    PODCAST("podcast", "videos", 300),
    TRUECRIME("truecrime", "videos", 300),
    ;

    companion object {
        /** Modus zu einem API-Wert — unbekannte Werte fallen auf Musik zurück. */
        fun fromApiValue(value: String?): MusicSearchMode =
            entries.firstOrNull { it.apiValue == value } ?: MUSIC
    }
}

/** Ergebnisfilter der YouTube-Music-artigen Suche. */
enum class MusicSearchFilter(val label: String) {
    ALLE("Alle"),
    SONGS("Songs"),
    ALBEN("Alben"),
    KUENSTLER("Künstler"),
    PLAYLISTS("Playlists"),
    ;
}

/** Playlist samt Songs und wie viele davon offline verfügbar sind. */
data class PlaylistWithSongs(
    val playlist: MusicPlaylist,
    val songs: List<MusicSong>,
    val downloadedCount: Int,
)

/**
 * Erkanntes Hörbuch bzw. Podcast-Show in den Suchergebnissen: mehrere
 * Treffer desselben Kanals gehören zusammen und werden als eine Gruppe mit
 * geordneten Kapiteln/Folgen geführt.
 */
data class ChapterGroup(
    val uploader: String,
    val chapters: List<MusicSong>,
)

/**
 * Search + streaming go through the Hikari backend (yt-dlp — the same
 * extraction the clipper uses, so it works even when public Piped instances
 * are blocked). If the backend is unreachable the repo falls back to querying
 * Piped instances directly from the device.
 */
class MusicRepository(
    private val songDao: MusicSongDao,
    private val playlistDao: MusicPlaylistDao,
    private val playlistSongDao: MusicPlaylistSongDao,
    private val downloadDao: LocalMusicDownloadDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val api: HikariApi,
    private val fallbackClient: OkHttpClient,
    private val json: Json,
    private val settings: SettingsStore,
) {
    companion object {
        /** Gültigkeit des personalisierten Home-Feeds im Speicher. */
        private const val HOME_CACHE_TTL_MS = 15 * 60 * 1000L

        private val PIPED_INSTANCES = listOf(
            "https://api.piped.private.coffee",
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.reallyaweso.me",
        )
        /**
         * Kuratierte Mixe. Reihenfolge ist die Anzeigereihenfolge im
         * Entdecken-Tab — der erste Eintrag wird als Rangliste gerendert,
         * seine Reihenfolge trägt also Bedeutung.
         */
        private val DISCOVER_SECTIONS = listOf(
            "Top Charts" to "top charts 2026",
            "Zum Chillen" to "chill music playlist",
            "Fokus & Lernen" to "lofi hip hop study beats",
            "Deutschrap" to "deutschrap 2026",
            "Anime & Gaming" to "anime opening songs",
            "Zum Trainieren" to "workout music motivation",
            "Party" to "party hits dance",
        )

        /** Nummern-Muster in Kapitel- und Episodentiteln. */
        private val CHAPTER_NUMBER_RE = Regex(
            """(?i)\b(?:kapitel|kap\.?|chapter|ch\.?|part|pt\.?|teil|episode|ep\.?|folge|#)\s*0*(\d{1,4})\b""",
        )

        /** Eigene Mixe für den Instrumental-Modus — Suchbegriffe, die von
         *  vornherein bei Stücken ohne Gesang landen. */
        private val INSTRUMENTAL_SECTIONS = listOf(
            "Lofi Beats" to "lofi hip hop instrumental beats no vocals",
            "Piano" to "relaxing piano music instrumental",
            "Fokus & Lernen" to "focus study music instrumental no vocals",
            "Ambient" to "ambient instrumental music calm",
            "Jazz" to "smooth jazz instrumental",
            "Klassik" to "classical music instrumental",
            "Soundtracks" to "epic cinematic instrumental soundtrack",
        )

        /** Kuratierte Genres für den Hörbuch-Modus — Gegenstück zu den Musik-Mixen. */
        private val AUDIOBOOK_SECTIONS = listOf(
            "Krimi & Thriller" to "krimi thriller hörbuch",
            "Fantasy & Sci-Fi" to "fantasy hörbuch deutsch",
            "Sachbuch & Wissen" to "sachbuch hörbuch",
            "Selbstentwicklung" to "selbsthilfe hörbuch",
            "Klassiker" to "hörbuch klassiker deutsch",
            "Horror" to "horror hörbuch deutsch",
        )

        /** Kuratierte Genres für den Podcast-Modus — Gegenstück zu den Musik-Mixen. */
        private val PODCAST_SECTIONS = listOf(
            "True Crime" to "true crime podcast deutsch",
            "Comedy" to "comedy podcast deutsch",
            "Wissen & Dokus" to "wissen podcast deutsch",
            "Geschichte" to "geschichte podcast",
            "Technologie" to "tech podcast deutsch",
            "Wirtschaft & Finanzen" to "wirtschaft podcast deutsch",
        )

        /**
         * True-Crime-Sektionen, bewusst nach Sprache getrennt: Der Sektionstitel
         * trägt die Sprache, die Suchbegriffe steuern sie. Piped liefert keine
         * Sprach-Metadaten — nur so ist die Beschriftung verlässlich.
         */
        private val TRUECRIME_SECTIONS = listOf(
            "Wahre Verbrechen (DE)" to "true crime podcast deutsch",
            "Kriminalfälle (DE)" to "kriminalfall podcast deutsch",
            "Mordfälle (DE)" to "mordfall true crime deutsch",
            "True Crime (EN)" to "true crime podcast english",
            "Cold Cases (EN)" to "cold case true crime english",
            "Unsolved (EN)" to "unsolved mystery true crime english",
        )

        /**
         * Deutsche Marker für das DE/EN-Sprach-Badge. Umlaute prüft der Aufrufer
         * vorab — diese Funktionswörter und Begriffe fangen Titel ohne Umlaute.
         */
        private val GERMAN_LANGUAGE_MARKERS = listOf(
            " der ", " die ", " das ", " und ", " ein ", " eine ", " von ", " zu ",
            " ist ", " mit ", " im ", " am ", " für ", " auf ", "wahre verbrechen",
            "mordfall", "kriminalfall", "deutschland", " deutsch",
        )

        /**
         * Merkmale, die Gesang belegen — sie schlagen jedes Instrumental-Wort.
         * "You (Vocals only)" vom Kanal "izza beats" darf nicht durchrutschen,
         * bloß weil "beats" im Namen steht.
         */
        private val VOCAL_MARKERS = listOf(
            "vocal only", "vocals only", "vocals)", "(vocals", "with vocals",
            "acapella", "a cappella", "lyrics", "lyric video", "sing along",
            "singalong", "singing", "vocal cover", "gesang", "chor",
        )

        /**
         * Merkmale, die ein Stück als instrumental ausweisen und ein
         * Gesangs-Merkmal überstimmen. "karaoke" gehört hierher: ein
         * Karaoke-Playback ist gerade die Fassung ohne Leadstimme.
         */
        private val INSTRUMENTAL_MARKERS = listOf(
            "instrumental", "no vocal", "without vocal", "ohne gesang", "backing track",
            "karaoke", "playback", "lofi", "lo-fi", "beats", "piano", "guitar solo",
            "ambient", "bgm", "background music", "soundtrack", "ost", "orchestral",
            "orchestra", "classical", "jazz", "meditation", "relaxing",
            "study music", "sleep music", "no lyrics", "no copyright music",
        )
    }

    /**
     * Behält nur Stücke, die sich selbst als instrumental ausweisen.
     *
     * Bewusst als Positivliste: Piped sagt nichts über Gesang, und die
     * Mehrheit der Treffer hat welchen, ohne es im Titel zu erwähnen. Wer nur
     * ausschließt, was "lyrics" heißt, lässt fast alles durch — deshalb muss
     * ein Stück den Nachweis erbringen statt nur unverdächtig zu wirken.
     *
     * Ohne Sicherheitsnetz: Ist der Schalter an, darf nichts mit Stimme
     * durchrutschen. Eine leere Liste ist das ehrlichere Ergebnis — die Suche
     * erklärt sie dann und bietet an, den Filter abzuschalten.
     */
    private fun filterInstrumental(songs: List<MusicSong>): List<MusicSong> =
        songs.filter { song ->
            val haystack = "${song.title} ${song.uploader}".lowercase()
            if (VOCAL_MARKERS.any { it in haystack }) return@filter false
            INSTRUMENTAL_MARKERS.any { it in haystack }
        }

    private suspend fun instrumentalOnly(): Boolean =
        runCatching { settings.instrumentalOnly.first() }.getOrDefault(false)

    suspend fun searchMusic(query: String, mode: MusicSearchMode = MusicSearchMode.MUSIC): List<MusicSong> {
        // Gesangsfilter gibt es nur für Musik — Hörbücher und Podcasts leben vom gesprochenen Wort.
        val instrumental = mode == MusicSearchMode.MUSIC && instrumentalOnly()
        // Stichwort und Filter ziehen die Anfrage schon in die richtige Richtung;
        // der Nachfilter räumt die Ausreißer weg. Doppelt anhängen träfe nur den Cache.
        val effectiveQuery = when {
            instrumental && !query.contains("instrumental", ignoreCase = true) -> "$query instrumental"
            mode == MusicSearchMode.AUDIOBOOK &&
                !query.contains("hörbuch", ignoreCase = true) &&
                !query.contains("audiobook", ignoreCase = true) -> "$query hörbuch"
            mode == MusicSearchMode.PODCAST &&
                !query.contains("podcast", ignoreCase = true) -> "$query podcast"
            mode == MusicSearchMode.TRUECRIME &&
                !query.contains("true crime", ignoreCase = true) -> "$query true crime"
            else -> query
        }
        val tracks = try {
            api.searchMusic(effectiveQuery, mode.apiValue).map { it.toSong() }
        } catch (_: Exception) {
            pipedSearchFallback(effectiveQuery, mode)
        }
        val filtered = when {
            instrumental -> filterInstrumental(tracks)
            mode.minDurationSeconds > 0 -> filterByDuration(tracks, mode.minDurationSeconds)
            else -> tracks
        }
        return withFavoriteState(filtered)
    }

    // --- YouTube-Music-artige Suche (full/typed/suggestions) ---

    /**
     * Vorschläge fürs Suchfeld. Bewusst ohne jede Query-Mutation — der
     * Nutzer tippt, das Backend vervollständigt. Fehler liefern schlicht
     * keine Vorschläge, niemals eine Exception.
     */
    suspend fun getSuggestions(query: String): List<String> =
        try {
            api.getSuggestions(query.trim())
        } catch (_: Exception) {
            emptyList()
        }

    /** Vollsuche über alle Kategorien; null bei Fehler. */
    suspend fun searchFullMusic(query: String): FullSearchResults? =
        try {
            val dto = api.searchFullMusic(query.trim())
            FullSearchResults(
                topResult = dto.topResult?.toSearchResult(),
                songs = withFavoriteState(dto.songs.map { it.toSong() }),
                artists = dto.artists.map { it.toModel() },
                albums = dto.albums.map { it.toModel() },
                playlists = dto.playlists.map { it.toModel() },
            )
        } catch (_: Exception) {
            null
        }

    suspend fun searchTypedSongs(query: String): List<MusicSong> =
        try {
            withFavoriteState(api.searchTypedSongs(query.trim()).map { it.toSong() })
        } catch (_: Exception) {
            emptyList()
        }

    suspend fun searchTypedAlbums(query: String): List<MusicAlbum> =
        try {
            api.searchTypedAlbums(query.trim()).map { it.toModel() }
        } catch (_: Exception) {
            emptyList()
        }

    suspend fun searchTypedArtists(query: String): List<SearchArtist> =
        try {
            api.searchTypedArtists(query.trim()).map { it.toModel() }
        } catch (_: Exception) {
            emptyList()
        }

    suspend fun searchTypedPlaylists(query: String): List<RemotePlaylist> =
        try {
            api.searchTypedPlaylists(query.trim()).map { it.toModel() }
        } catch (_: Exception) {
            emptyList()
        }

    /** Tracks einer Remote-Playlist oder eines Albums; Fehler → leere Liste. */
    suspend fun getRemotePlaylistTracks(playlistId: String): List<MusicSong> =
        try {
            withFavoriteState(api.getPlaylistTracks(playlistId).map { it.toSong() })
        } catch (_: Exception) {
            emptyList()
        }

    private fun TopResultDto.toSearchResult(): MusicSearchResult? = when (type) {
        "song" -> videoId?.let {
            MusicSearchResult.Song(
                MusicSong(
                    videoId = it,
                    title = title.orEmpty(),
                    uploader = uploader.orEmpty(),
                    uploaderUrl = uploaderUrl.orEmpty(),
                    thumbnailUrl = thumbnailUrl.orEmpty(),
                    duration = durationSeconds ?: 0,
                    views = views ?: 0,
                ),
            )
        }
        "artist" -> channelId?.let {
            MusicSearchResult.Artist(
                SearchArtist(
                    channelId = it,
                    name = name.orEmpty(),
                    thumbnailUrl = thumbnailUrl.orEmpty(),
                    subscribers = subscribers ?: 0,
                ),
            )
        }
        "album" -> playlistId?.let {
            MusicSearchResult.Album(
                MusicAlbum(
                    playlistId = it,
                    name = name.orEmpty(),
                    artistName = artistName.orEmpty(),
                    thumbnailUrl = thumbnailUrl.orEmpty(),
                    videoCount = videoCount ?: 0,
                ),
            )
        }
        "playlist" -> playlistId?.let {
            MusicSearchResult.Playlist(
                RemotePlaylist(
                    playlistId = it,
                    name = name.orEmpty(),
                    uploaderName = uploaderName.orEmpty(),
                    thumbnailUrl = thumbnailUrl.orEmpty(),
                    videoCount = videoCount ?: 0,
                ),
            )
        }
        else -> null
    }

    private fun SearchArtistDto.toModel() = SearchArtist(
        channelId = channelId, name = name, thumbnailUrl = thumbnailUrl, subscribers = subscribers,
    )

    private fun SearchAlbumDto.toModel() = MusicAlbum(
        playlistId = playlistId, name = name, artistName = artistName,
        thumbnailUrl = thumbnailUrl, videoCount = videoCount,
    )

    private fun SearchPlaylistDto.toModel() = RemotePlaylist(
        playlistId = playlistId, name = name, uploaderName = uploaderName,
        thumbnailUrl = thumbnailUrl, videoCount = videoCount,
    )

    // --- Suchverlauf ---

    fun observeSearchHistory(): Flow<List<String>> =
        searchHistoryDao.recent().map { entries -> entries.map { it.query } }

    suspend fun recordSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        searchHistoryDao.upsert(SearchHistoryEntity(trimmed, System.currentTimeMillis()))
        searchHistoryDao.trim()
    }

    suspend fun deleteSearchHistoryEntry(query: String) = searchHistoryDao.delete(query)

    suspend fun clearSearchHistory() = searchHistoryDao.clear()

    /**
     * Hörbuch- und Podcast-Treffer sind lang; Ausschnitte und Trailer fliegen
     * raus. Bleibt zu wenig übrig, lieber ungefiltert zeigen als eine leere Liste.
     */
    private fun filterByDuration(songs: List<MusicSong>, minSeconds: Int): List<MusicSong> {
        val longEnough = songs.filter { it.duration >= minSeconds }
        return if (longEnough.size >= 4) longEnough else songs
    }

    /**
     * Gruppiert Treffer nach Kanal: Kapitel eines Hörbuchs und Folgen eines
     * Podcasts stammen fast immer vom selben Uploader und gehören so zusammen.
     * Innerhalb einer Gruppe ordnet die Nummer im Titel ("Kapitel 3",
     * "Episode 12"); ohne Nummer zählt die ursprüngliche Suchreihenfolge.
     * Kanäle mit nur einem Treffer bleiben Einzelergebnisse.
     *
     * @return Gruppen in Reihenfolge ihres ersten Treffers, danach die Einzelgänger.
     */
    fun groupIntoShows(songs: List<MusicSong>): Pair<List<ChapterGroup>, List<MusicSong>> {
        val groups = mutableListOf<ChapterGroup>()
        val singles = mutableListOf<MusicSong>()
        for (bucket in songs.groupBy { it.uploader.trim().lowercase() }.values) {
            if (bucket.size < 2) {
                singles += bucket
                continue
            }
            groups += ChapterGroup(
                uploader = bucket.first().uploader,
                chapters = bucket.sortedBy { chapterNumberOf(it.title) ?: Int.MAX_VALUE },
            )
        }
        return groups to singles
    }

    /** Nummer aus Titeln wie "Kapitel 3", "Chapter 04", "Pt. 1", "Folge 12". */
    private fun chapterNumberOf(title: String): Int? =
        CHAPTER_NUMBER_RE.find(title)?.groupValues?.get(1)?.toIntOrNull()

    /**
     * Grobe Sprachschätzung für das DE/EN-Badge im True-Crime-Modus. Piped
     * liefert keine Sprache mit — Umlaute sind ein fast sicheres Zeichen für
     * Deutsch, danach helfen deutsche Funktionswörter und Genrebegriffe.
     * Im Zweifel EN: der englische True-Crime-Markt ist der größere.
     */
    fun languageBadgeOf(song: MusicSong): String {
        val text = " ${song.title} ${song.uploader} ".lowercase()
        if (text.any { it in "äöüß" }) return "DE"
        return if (GERMAN_LANGUAGE_MARKERS.any { it in text }) "DE" else "EN"
    }

    suspend fun getDiscoverSections(mode: MusicSearchMode = MusicSearchMode.MUSIC): List<DiscoverSection> =
        coroutineScope {
            val sections = when (mode) {
                MusicSearchMode.AUDIOBOOK -> AUDIOBOOK_SECTIONS
                MusicSearchMode.PODCAST -> PODCAST_SECTIONS
                MusicSearchMode.TRUECRIME -> TRUECRIME_SECTIONS
                MusicSearchMode.MUSIC -> if (instrumentalOnly()) INSTRUMENTAL_SECTIONS else DISCOVER_SECTIONS
            }
            sections.map { (title, query) ->
                async {
                    val songs = try {
                        getMixSongs(query, mode).take(12)
                    } catch (_: Exception) {
                        emptyList()
                    }
                    DiscoverSection(title, songs, query)
                }
            }.map { it.await() }.filter { it.songs.isNotEmpty() }
        }

    /**
     * Songs eines kuratierten Mixes. Bei Musik fliegen stundenlange Mitschnitte
     * und Endlos-Mixe raus — in einer Entdecken-Liste sollen echte Songs stehen.
     * Hörbücher und Podcasts dürfen dagegen lang sein; dort fallen nur Clips
     * und Trailer weg. Greift der Filter zu hart, lieber ungefiltert zeigen
     * als eine leere Liste.
     */
    suspend fun getMixSongs(query: String, mode: MusicSearchMode = MusicSearchMode.MUSIC): List<MusicSong> {
        val all = searchMusic(query, mode)
        val tracks = when (mode) {
            MusicSearchMode.MUSIC -> all.filter { it.duration in 60..900 }
            MusicSearchMode.AUDIOBOOK -> all.filter { it.duration >= 120 }
            MusicSearchMode.PODCAST, MusicSearchMode.TRUECRIME -> all.filter { it.duration >= 180 }
        }
        return if (tracks.size >= 4) tracks else all
    }

    /**
     * Nachschub, wenn die Warteschlange leerläuft. Erste Wahl ist die echte
     * Radio-Queue von YouTube Music ([getRelatedSongs]); erst wenn sie nichts
     * liefert, greift die alte Stichwortsuche im Umfeld des Seeds. Bei aktivem
     * Instrumental-Filter bleibt es beim Suchweg — nur der filtert verlässlich
     * auf Stücke ohne Gesang.
     */
    suspend fun getAutoplaySongs(seed: MusicSong, exclude: Set<String>): List<MusicSong> {
        if (!instrumentalOnly()) {
            val related = getRelatedSongs(seed.videoId).filter { it.videoId !in exclude }
            if (related.size >= 3) return related.take(15)
        }
        val queries = listOfNotNull(
            seed.uploader.takeIf { it.isNotBlank() },
            seed.title.split(" ", "(", "-").firstOrNull { it.length > 3 },
        )
        for (q in queries) {
            val found = runCatching { searchMusic(q) }.getOrDefault(emptyList())
                .filter { it.videoId !in exclude && it.duration in 60..900 }
            if (found.size >= 3) return found.take(10)
        }
        return emptyList()
    }

    /** Radio-Queue zu einem Song; Fehler → leere Liste. */
    suspend fun getRelatedSongs(videoId: String): List<MusicSong> =
        try {
            withFavoriteState(api.getRelatedSongs(videoId).map { it.toSong() })
        } catch (_: Exception) {
            emptyList()
        }

    // --- Personalisierter Home-Feed ---

    private var homeCache: List<HomeSection>? = null
    private var homeCacheAt = 0L

    /**
     * Baut den Entdecken-Feed: Kern sind IMMER die kuratierten Genre-Mixe —
     * die haben sich als deutlich besser erwiesen als die generischen
     * YouTube-Home-Karussells (anonyme Quick Picks, teils fremdsprachige
     * Community-Playlists), die deshalb hier bewusst nicht mehr auftauchen.
     * Sobald Verlauf/Favoriten existieren, kommen personalisierte Sektionen
     * ("Dein Mix", "Ähnlich wie …", "Deine Favoriten") OBEN dazu.
     * Instrumental-Modus nutzt weiter nur die kuratierten Instrumental-Mixe:
     * Radio-Queues können Gesang nicht ausschließen.
     */
    suspend fun getPersonalizedHome(force: Boolean = false): List<HomeSection> = coroutineScope {
        if (instrumentalOnly()) return@coroutineScope curatedHomeSections(INSTRUMENTAL_SECTIONS)
        val nowMs = System.currentTimeMillis()
        homeCache?.takeIf { !force && nowMs - homeCacheAt < HOME_CACHE_TTL_MS }?.let {
            return@coroutineScope it
        }

        val history = runCatching { getHistory() }.getOrDefault(emptyList())
        val favorites = runCatching { getFavorites() }.getOrDefault(emptyList())
        // Seeds: jüngster Verlauf zuerst, Favoriten mischen den Geschmack
        // abseits der letzten Tage dazu.
        val seeds = (history.take(8) + favorites.shuffled().take(4))
            .distinctBy { it.videoId }
            .shuffled()
            .take(3)

        val relatedDeferred = seeds.map { seed -> async { seed to getRelatedSongs(seed.videoId) } }
        val curatedDeferred = async { curatedHomeSections(DISCOVER_SECTIONS) }
        val relatedBySeed = relatedDeferred.map { it.await() }

        val recentIds = history.take(10).map { it.videoId }.toSet()
        val sections = mutableListOf<HomeSection>()

        // "Dein Mix": alle Radio-Queues gemischt, ohne frisch Gehörtes.
        val mix = relatedBySeed
            .flatMap { it.second }
            .shuffled()
            .distinctBy { it.videoId }
            .filter { it.videoId !in recentIds }
            .take(20)
        if (mix.size >= 5) {
            sections += HomeSection("Dein Mix", HomeSectionKind.MIX, songs = mix)
        }

        // Bis zu zwei "Ähnlich wie …"-Sektionen aus einzelnen Seeds.
        relatedBySeed
            .filter { it.second.size >= 5 }
            .take(2)
            .forEach { (seed, related) ->
                sections += HomeSection(
                    "Ähnlich wie ${seedLabelOf(seed)}",
                    HomeSectionKind.SIMILAR,
                    songs = related.take(12),
                )
            }

        if (favorites.size >= 4) {
            sections += HomeSection(
                "Deine Favoriten",
                HomeSectionKind.FAVORITES,
                songs = favorites.shuffled().take(12),
            )
        }

        // Kuratierte Genre-Mixe als fester Kern — auch für frische Accounts.
        sections += curatedDeferred.await()

        val result = sections.toList()
        if (result.isNotEmpty()) {
            homeCache = result
            homeCacheAt = nowMs
        }
        result
    }

    /** Kuratierte Suchbegriff-Mixe im Home-Sektionsformat (Fallback-Pfad). */
    private suspend fun curatedHomeSections(source: List<Pair<String, String>>): List<HomeSection> =
        coroutineScope {
            source.map { (title, query) ->
                async {
                    val songs = try {
                        getMixSongs(query, MusicSearchMode.MUSIC).take(12)
                    } catch (_: Exception) {
                        emptyList()
                    }
                    HomeSection(title, HomeSectionKind.CURATED, songs = songs, query = query)
                }
            }.map { it.await() }.filter { it.songs.isNotEmpty() }
        }

    /** Kurzform des Seeds für den Sektionstitel — Titel bis zur Klammer, sonst Uploader. */
    private fun seedLabelOf(seed: MusicSong): String {
        val short = seed.title
            .substringBefore("(")
            .substringBefore("[")
            .substringBefore(" - ")
            .trim()
        return when {
            short.length in 2..32 -> "„$short“"
            seed.uploader.isNotBlank() -> seed.uploader
            else -> "„${seed.title.take(28)}“"
        }
    }

    suspend fun getAudioStream(videoId: String, forceRefresh: Boolean = false): String? {
        // Audio läuft über den Backend-Proxy statt direkt gegen googlevideo:
        // deren URLs sind an das Netz gebunden, das sie aufgelöst hat, und
        // spielen vom Handy aus fremden Netzen nicht ab. Abgelaufene URLs
        // löst der Proxy serverseitig selbst neu auf; forceRefresh bleibt für
        // die Retry-Semantik der Aufrufer ohne eigene Wirkung.
        val backend = runCatching { settings.backendUrl.first().trimEnd('/') }.getOrNull()
            ?: return pipedStreamFallback(videoId)
        return "$backend/music/audio/$videoId"
    }

    // --- Library (= play history) & favorites ---

    suspend fun getHistory(): List<MusicSong> = songDao.getAll().map { it.toSong() }

    suspend fun getFavorites(): List<MusicSong> = songDao.getFavorites().map { it.toSong() }

    /** Alle offline verfügbaren Songs — funktioniert ohne jedes Netz. */
    suspend fun getDownloadedSongs(): List<MusicSong> {
        val favorites = getFavoriteIds()
        return downloadDao.getAll().map { row ->
            MusicSong(
                videoId = row.videoId,
                title = row.title,
                uploader = row.uploader,
                uploaderUrl = "",
                thumbnailUrl = row.thumbnailUrl,
                duration = row.durationSeconds,
                views = 0,
                addedAt = row.downloadedAt,
                isFavorite = row.videoId in favorites,
            )
        }
    }

    fun observeDownloadedIds(): Flow<List<String>> = downloadDao.observeIds()

    /**
     * Merkt einen Song in der Bibliothek. [touchRecency] steuert, ob er im
     * Verlauf nach oben rutscht — beim Download soll er das nicht.
     */
    suspend fun recordPlayed(song: MusicSong, touchRecency: Boolean = true) {
        val existing = songDao.getByName(song.videoId)
        songDao.insert(
            song.toEntity().copy(
                isFavorite = existing?.isFavorite ?: song.isFavorite,
                addedAt = if (touchRecency) {
                    System.currentTimeMillis()
                } else {
                    existing?.addedAt ?: song.addedAt
                },
            ),
        )
    }

    /** Returns the new favorite state. Unknown songs get saved first. */
    suspend fun toggleFavorite(song: MusicSong): Boolean {
        val existing = songDao.getByName(song.videoId)
        return if (existing == null) {
            songDao.insert(song.toEntity().copy(isFavorite = true))
            true
        } else {
            val next = !existing.isFavorite
            songDao.setFavorite(song.videoId, next)
            next
        }
    }

    suspend fun removeSong(song: MusicSong) = songDao.delete(song.toEntity())

    suspend fun getFavoriteIds(): Set<String> = songDao.getFavorites().map { it.videoId }.toSet()

    // --- Playlists ---

    suspend fun getPlaylists(): List<PlaylistWithSongs> {
        val downloadedIds = downloadDao.getAll().map { it.videoId }.toSet()
        val favorites = getFavoriteIds()
        return playlistDao.getAll().map { entity ->
            val songs = songsOf(entity.id, favorites)
            PlaylistWithSongs(
                playlist = entity.toModel(),
                songs = songs,
                downloadedCount = songs.count { it.videoId in downloadedIds },
            )
        }
    }

    suspend fun getPlaylist(id: Int): PlaylistWithSongs? {
        val entity = playlistDao.getById(id) ?: return null
        val downloadedIds = downloadDao.getAll().map { it.videoId }.toSet()
        val songs = songsOf(id, getFavoriteIds())
        return PlaylistWithSongs(
            playlist = entity.toModel(),
            songs = songs,
            downloadedCount = songs.count { it.videoId in downloadedIds },
        )
    }

    private suspend fun songsOf(playlistId: Int, favorites: Set<String>): List<MusicSong> {
        val links = playlistSongDao.getByPlaylist(playlistId)
        if (links.isEmpty()) return emptyList()
        // Nur die verlinkten Songs laden — nicht pro Playlist die ganze Tabelle.
        val byId = songDao.getByIds(links.map { it.songVideoId }).associateBy { it.videoId }
        return links.mapNotNull { link ->
            byId[link.songVideoId]?.toSong()?.copy(isFavorite = link.songVideoId in favorites)
        }
    }

    suspend fun createPlaylist(name: String): Int {
        val id = playlistDao.insert(MusicPlaylistEntity(name = name))
        return id.toInt()
    }

    suspend fun renamePlaylist(playlist: MusicPlaylist, newName: String) {
        playlistDao.getById(playlist.id)?.let { playlistDao.update(it.copy(name = newName)) }
    }

    suspend fun deletePlaylist(playlist: MusicPlaylist) {
        playlistDao.getById(playlist.id)?.let { playlistDao.delete(it) }
    }

    /** Song muss in `music_songs` existieren — der Fremdschlüssel verlangt das. */
    suspend fun addToPlaylist(playlistId: Int, song: MusicSong) {
        recordPlayed(song, touchRecency = false)
        playlistSongDao.insert(MusicPlaylistSongEntity(playlistId, song.videoId))
    }

    suspend fun removeFromPlaylist(playlistId: Int, song: MusicSong) {
        playlistSongDao.delete(MusicPlaylistSongEntity(playlistId, song.videoId))
    }

    // --- Fallback path (direct Piped) ---

    private suspend fun pipedSearchFallback(
        query: String,
        mode: MusicSearchMode = MusicSearchMode.MUSIC,
    ): List<MusicSong> =
        withContext(Dispatchers.IO) {
            val q = URLEncoder.encode(query, "UTF-8")
            for (base in PIPED_INSTANCES) {
                try {
                    val body = httpGet("$base/search?q=$q&filter=${mode.pipedFilter}") ?: continue
                    val page = json.decodeFromString<PipedSearchPageDto>(body)
                    val songs = page.items.mapNotNull { item ->
                        val videoId = item.url?.substringAfter("v=", "")?.substringBefore("&")
                        if (videoId.isNullOrBlank()) return@mapNotNull null
                        MusicSong(
                            videoId = videoId,
                            title = item.title.orEmpty(),
                            uploader = item.uploaderName.orEmpty(),
                            uploaderUrl = item.uploaderUrl.orEmpty(),
                            thumbnailUrl = "https://i.ytimg.com/vi/$videoId/mqdefault.jpg",
                            duration = item.duration ?: 0,
                            views = item.views?.takeIf { it > 0 } ?: 0,
                        )
                    }
                    if (songs.isNotEmpty()) return@withContext songs
                } catch (_: Exception) {
                    // dead instance — try the next one
                }
            }
            emptyList()
        }

    private suspend fun pipedStreamFallback(videoId: String): String? =
        withContext(Dispatchers.IO) {
            for (base in PIPED_INSTANCES) {
                try {
                    val body = httpGet("$base/streams/$videoId") ?: continue
                    val streams = json.decodeFromString<PipedStreamsDto>(body)
                    val best = streams.audioStreams
                        .filter { it.url != null }
                        .maxByOrNull { it.bitrate ?: 0L }
                    if (best?.url != null) return@withContext best.url
                } catch (_: Exception) {
                    // dead instance — try the next one
                }
            }
            null
        }

    private fun httpGet(url: String): String? {
        val request = Request.Builder().url(url).build()
        fallbackClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    private suspend fun withFavoriteState(songs: List<MusicSong>): List<MusicSong> {
        if (songs.isEmpty()) return songs
        val favorites = getFavoriteIds()
        return songs.map { it.copy(isFavorite = it.videoId in favorites) }
    }

    private fun MusicTrackDto.toSong() = MusicSong(
        videoId = videoId,
        title = title,
        uploader = uploader,
        uploaderUrl = uploaderUrl.orEmpty(),
        thumbnailUrl = thumbnailUrl,
        duration = durationSeconds,
        views = views ?: 0,
    )

    // --- Artist-Seiten (nur Backend, kein Piped-Fallback — Fehler gehen an den Aufrufer) ---

    suspend fun getArtist(channelId: String): Artist = api.getArtist(channelId).toModel()

    suspend fun getArtistTop(channelId: String, name: String): List<MusicSong> =
        withFavoriteState(api.getArtistTop(channelId, name).map { it.toSong() })

    suspend fun getArtistPlaylists(channelId: String, name: String): List<ArtistPlaylist> =
        api.getArtistPlaylists(channelId, name).map { it.toModel() }

    /**
     * Komplette Artist-Seite in einem Call. Fehlt der neue Endpunkt (altes
     * Backend), wird die Seite aus den drei alten Endpunkten zusammengesetzt —
     * [fallbackName] ist dort der Suchbegriff. Fehler der Zusatz-Sektionen
     * verschlucken sich zu leeren Listen; nur ohne Profil fliegt die Exception
     * zum Aufrufer.
     */
    suspend fun getArtistPage(channelId: String, fallbackName: String): ArtistPage =
        try {
            val dto = api.getArtistPage(channelId, fallbackName)
            // Normale YouTube-Kanäle (True Crime, Podcasts) kennt YouTube Music
            // nicht als Artist — liefert die Page nichts, holt der alte
            // /top-Endpunkt wenigstens die Kanal-Videos.
            val topSongs = dto.topSongs.map { it.toSong() }.ifEmpty {
                runCatching { getArtistTop(channelId, fallbackName) }.getOrDefault(emptyList())
            }
            ArtistPage(
                artist = dto.artist.toModel(),
                topSongs = withFavoriteState(topSongs),
                albums = dto.albums.map { it.toModel() },
                singles = dto.singles.map { it.toModel() },
                playlists = dto.playlists.map { it.toModel() },
                related = dto.related.map { it.toModel() },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            coroutineScope {
                val top = async {
                    runCatching { getArtistTop(channelId, fallbackName) }.getOrDefault(emptyList())
                }
                val playlists = async {
                    runCatching { getArtistPlaylists(channelId, fallbackName) }.getOrDefault(emptyList())
                }
                ArtistPage(
                    artist = getArtist(channelId),
                    topSongs = top.await(),
                    albums = emptyList(),
                    singles = emptyList(),
                    playlists = playlists.await(),
                    related = emptyList(),
                )
            }
        }

    private fun ArtistAlbumDto.toModel() = ArtistAlbum(
        playlistId = playlistId,
        name = name,
        artistName = artistName,
        thumbnailUrl = thumbnailUrl,
        videoCount = videoCount,
        year = year,
    )

    private fun ArtistDto.toModel() = Artist(
        channelId = channelId,
        name = name,
        avatarUrl = avatarUrl,
        bannerUrl = bannerUrl,
        subscriberCount = subscriberCount,
        description = description,
        verified = verified,
    )

    private fun ArtistPlaylistDto.toModel() = ArtistPlaylist(
        playlistId = playlistId,
        name = name,
        thumbnailUrl = thumbnailUrl,
        videoCount = videoCount,
        uploaderName = uploaderName,
    )

    private fun MusicSong.toEntity() = MusicSongEntity(
        videoId = videoId, title = title, uploader = uploader, uploaderUrl = uploaderUrl,
        thumbnailUrl = thumbnailUrl, duration = duration, views = views,
        addedAt = addedAt, isFavorite = isFavorite,
    )

    private fun MusicSongEntity.toSong() = MusicSong(
        videoId = videoId, title = title, uploader = uploader, uploaderUrl = uploaderUrl,
        thumbnailUrl = thumbnailUrl, duration = duration, views = views,
        addedAt = addedAt, isFavorite = isFavorite,
    )

    private fun MusicPlaylistEntity.toModel() = MusicPlaylist(
        id = id, name = name, description = description,
        thumbnailUrl = thumbnailUrl, createdAt = createdAt,
    )
}
