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
import com.hikari.app.data.api.dto.SuggestionDto
import com.hikari.app.data.api.dto.TopResultDto
import com.hikari.app.data.db.LocalMusicDownloadDao
import com.hikari.app.data.db.MusicPlaylistDao
import com.hikari.app.data.db.MusicPlaylistEntity
import com.hikari.app.data.db.MusicPlaylistSongDao
import com.hikari.app.data.db.MusicPlayEventDao
import com.hikari.app.data.db.MusicPlayEventEntity
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
import com.hikari.app.domain.model.HomeItem
import com.hikari.app.domain.model.HomeSection
import com.hikari.app.domain.model.HomeSectionKind
import com.hikari.app.domain.model.MusicAlbum
import com.hikari.app.domain.model.MusicPlaylist
import com.hikari.app.domain.model.MusicSearchResult
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.domain.model.RemotePlaylist
import com.hikari.app.domain.model.SearchArtist
import com.hikari.app.domain.model.SearchSuggestion
import com.hikari.app.domain.model.SongArtist
import com.hikari.app.domain.model.SuggestionKind
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
    /** Modus, mit dem das Backend gefragt wird — Instrumental ist dort Musik. */
    val backendValue: String = apiValue,
) {
    MUSIC("music", "music_songs", 0),
    /** Musik ohne Gesang: eigener Modus mit Instrumenten statt Playlists. */
    INSTRUMENTAL("instrumental", "music_songs", 0, backendValue = "music"),
    AUDIOBOOK("audiobook", "videos", 600),
    PODCAST("podcast", "videos", 300),
    TRUECRIME("truecrime", "videos", 300),
    ;

    /** Songs statt gesprochenem Wort — gleiche Dauerregeln, gleiche Such-UI. */
    val isMusical: Boolean get() = this == MUSIC || this == INSTRUMENTAL

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
 * extraction the video feed uses, so it works even when public Piped instances
 * are blocked). If the backend is unreachable the repo falls back to querying
 * Piped instances directly from the device.
 */
class MusicRepository(
    private val songDao: MusicSongDao,
    private val playlistDao: MusicPlaylistDao,
    private val playlistSongDao: MusicPlaylistSongDao,
    private val downloadDao: LocalMusicDownloadDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val playEventDao: MusicPlayEventDao,
    private val api: HikariApi,
    private val fallbackClient: OkHttpClient,
    private val json: Json,
    private val settings: SettingsStore,
) {
    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile
    private var cachedBackendUrl: String? = null

    init {
        repoScope.launch {
            settings.backendUrl.collect {
                cachedBackendUrl = it.trimEnd('/')
            }
        }
    }

    private suspend fun resolveBackendUrl(): String? {
        cachedBackendUrl?.takeIf { it.isNotBlank() }?.let { return it }
        return runCatching { settings.backendUrl.first().trimEnd('/') }.getOrNull()?.also {
            cachedBackendUrl = it
        }
    }

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
            "Throwback 2010er" to "2010s hits throwback",
            "Feel Good" to "feel good songs happy",
            "R'n'B Nights" to "rnb slow jams",
            "Akustisch" to "acoustic songs cover",
            "Indie Entdeckungen" to "indie pop new songs",
            "Late Night Drive" to "night drive synthwave songs",
            "Deutschpop" to "deutschpop hits 2026",
            "Afrobeats" to "afrobeats hits 2026",
            "Latin Heat" to "reggaeton hits 2026",
        )

        /** So viele kuratierte Mixe zeigt der Home-Feed pro Aufbau (Rest rotiert). */
        private const val CURATED_PER_LOAD = 6

        /** So viele Mixe lädt der endlose Feed pro Nachschub beim Runterscrollen. */
        private const val MORE_PER_PAGE = 3

        /** Schlüssel für Feed-Sektionen ohne Suchbegriff (Detailseite "Alle"). */
        const val KEY_RELATED = "related:"
        const val KEY_FAVORITES = "favorites:"
        const val KEY_STAGED = "staged:"

        /** Suchbegriffe für "Playlists für dich" — eine davon pro Aufbau. */
        private val PLAYLIST_QUERIES = listOf(
            "chill playlist deutsch", "deutschrap playlist 2026", "gym workout playlist 2026",
            "anime opening playlist", "party playlist deutsch 2026", "lofi hip hop playlist",
            "2010er hits playlist", "gute laune playlist", "autofahrt playlist deutsch",
            "pop hits playlist 2026", "rnb playlist 2026", "deutschpop playlist 2026",
        )

        /** Regional-Playlists, die bei englischen Stimmungs-Suchen gern reinrutschen. */
        private val FOREIGN_PLAYLIST_MARKERS = listOf(
            "hindi", "bangla", "bengali", "punjabi", "bollywood", "tamil", "telugu",
            "urdu", "nepali", "marathi", "malayalam", "kannada", "bhojpuri",
        )

        private val MONTHS_EN = listOf(
            "january", "february", "march", "april", "may", "june",
            "july", "august", "september", "october", "november", "december",
        )

        /** Nummern-Muster in Kapitel- und Episodentiteln. */
        private val CHAPTER_NUMBER_RE = Regex(
            """(?i)\b(?:kapitel|kap\.?|chapter|ch\.?|part|pt\.?|teil|episode|ep\.?|folge|#)\s*0*(\d{1,4})\b""",
        )


        /** Kuratierte Genres für den Hörbuch-Modus — Gegenstück zu den Musik-Mixen. */
        private val AUDIOBOOK_SECTIONS = listOf(
            "Krimi & Thriller" to "krimi thriller hörbuch",
            "Fantasy & Sci-Fi" to "fantasy hörbuch deutsch",
            "Sachbuch & Wissen" to "sachbuch hörbuch",
            "Selbstentwicklung" to "selbsthilfe hörbuch",
            "Klassiker" to "hörbuch klassiker deutsch",
            "Horror" to "horror hörbuch deutsch",
            "Abenteuer" to "abenteuer hörbuch deutsch",
            "Biografien" to "biografie hörbuch deutsch",
            "Geschichte" to "geschichte hörbuch deutsch",
            "Philosophie" to "philosophie hörbuch",
        )

        /** Kuratierte Genres für den Podcast-Modus — Gegenstück zu den Musik-Mixen. */
        private val PODCAST_SECTIONS = listOf(
            "True Crime" to "true crime podcast deutsch",
            "Comedy" to "comedy podcast deutsch",
            "Wissen & Dokus" to "wissen podcast deutsch",
            "Geschichte" to "geschichte podcast",
            "Technologie" to "tech podcast deutsch",
            "Wirtschaft & Finanzen" to "wirtschaft podcast deutsch",
            "Psychologie" to "psychologie podcast deutsch",
            "Gesundheit" to "gesundheit podcast deutsch",
            "Mystery" to "mystery podcast deutsch",
            "Sport" to "sport podcast deutsch",
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
            "Ungeklärte Fälle (DE)" to "ungeklärte fälle podcast deutsch",
            "Spurensuche (DE)" to "true crime doku deutsch",
            "Serial Killer (EN)" to "serial killer documentary podcast english",
            "Investigativ (EN)" to "investigative journalism true crime english",
        )

        /** So viele Genres zeigen die Sprech-Modi pro Aufbau (Rest rotiert). */
        private const val SPOKEN_SECTIONS_PER_LOAD = 6

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
            "violin", "violine", "geige", "cello", "saxophone", "saxophon", "sax cover",
            "flute", "flöte", "harp", "harfe", "acoustic guitar", "guitar cover",
            "fingerstyle", "strings", "cinematic", "koto", "shakuhachi", "synthwave",
            "klavier", "piano cover", "instrumental cover",
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

    /** Der global gewählte Hör-Modus — Rückfall, wenn der Player keinen Queue-Modus kennt. */
    private suspend fun currentMode(): MusicSearchMode =
        MusicSearchMode.fromApiValue(runCatching { settings.musicMode.first() }.getOrNull())

    /** Passt die Länge zum Modus? Songs: 1–15 min, Gesprochenes: ab Mindestdauer. */
    private fun fitsMode(song: MusicSong, mode: MusicSearchMode): Boolean =
        if (mode.isMusical) song.duration in 60..900 else song.duration >= mode.minDurationSeconds

    suspend fun searchMusic(query: String, mode: MusicSearchMode = MusicSearchMode.MUSIC): List<MusicSong> {
        // Gesangsfilter nur im Instrumental-Modus — Hörbücher und Podcasts leben vom gesprochenen Wort.
        val instrumental = mode == MusicSearchMode.INSTRUMENTAL
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
            api.searchMusic(effectiveQuery, mode.backendValue).map { it.toSong() }
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
    suspend fun getSuggestions(query: String): List<SearchSuggestion> =
        try {
            api.getSuggestions(query.trim()).mapNotNull { dto ->
                if (dto.text.isBlank()) return@mapNotNull null
                SearchSuggestion(
                    text = dto.text,
                    // Unbekannte kinds defensiv als reine Text-Query behandeln.
                    kind = when (dto.kind) {
                        "song" -> SuggestionKind.SONG
                        "artist" -> SuggestionKind.ARTIST
                        "album" -> SuggestionKind.ALBUM
                        "playlist" -> SuggestionKind.PLAYLIST
                        "video" -> SuggestionKind.VIDEO
                        else -> SuggestionKind.QUERY
                    },
                    thumbnailUrl = dto.thumbnailUrl?.takeIf { it.isNotBlank() },
                    subtitle = dto.subtitle?.takeIf { it.isNotBlank() },
                    videoId = dto.videoId?.takeIf { it.isNotBlank() },
                    channelId = dto.channelId?.takeIf { it.isNotBlank() },
                    playlistId = dto.playlistId?.takeIf { it.isNotBlank() },
                )
            }
        } catch (_: Exception) {
            emptyList()
        }

    /** Vollsuche über alle Kategorien; null bei Fehler. */
    suspend fun searchFullMusic(
        query: String,
        mode: MusicSearchMode = MusicSearchMode.MUSIC,
    ): FullSearchResults? =
        try {
            val instrumental = mode == MusicSearchMode.INSTRUMENTAL
            val effective = query.trim().let {
                if (instrumental && !it.contains("instrumental", ignoreCase = true)) "$it instrumental" else it
            }
            val dto = api.searchFullMusic(effective, mode.backendValue)
            val songs = dto.songs.map { it.toSong() }
            val top = dto.topResult?.toSearchResult()
            if (!instrumental) {
                FullSearchResults(
                    topResult = top,
                    songs = withFavoriteState(songs),
                    artists = dto.artists.map { it.toModel() },
                    albums = dto.albums.map { it.toModel() },
                    playlists = dto.playlists.map { it.toModel() },
                )
            } else {
                // Ohne Gesang: Songs müssen sich als instrumental ausweisen,
                // Playlists über ihren Namen; Alben enthalten fast immer Gesang.
                val kept = filterInstrumental(songs)
                val keptIds = kept.mapTo(HashSet()) { it.videoId }
                FullSearchResults(
                    topResult = top?.takeUnless { it is MusicSearchResult.Song && it.song.videoId !in keptIds }
                        ?.takeUnless { it is MusicSearchResult.Album },
                    songs = withFavoriteState(kept),
                    artists = dto.artists.map { it.toModel() },
                    albums = emptyList(),
                    playlists = dto.playlists.map { it.toModel() }.filter { pl ->
                        val name = pl.name.lowercase()
                        VOCAL_MARKERS.none { it in name } && INSTRUMENTAL_MARKERS.any { it in name }
                    },
                )
            }
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

    /**
     * Entdecken-Sektionen der Sprech-Modi. Der erste Eintrag jedes Pools ist
     * der feste Anker (läuft als Rangliste), der Rest rotiert: jeder Aufbau —
     * auch jedes Pull-to-Refresh — zieht eine neue Auswahl und mischt die
     * Folgen innerhalb der Sektionen neu.
     */
    suspend fun getDiscoverSections(mode: MusicSearchMode = MusicSearchMode.MUSIC): List<DiscoverSection> =
        coroutineScope {
            val pool = when (mode) {
                MusicSearchMode.AUDIOBOOK -> AUDIOBOOK_SECTIONS
                MusicSearchMode.PODCAST -> PODCAST_SECTIONS
                MusicSearchMode.TRUECRIME -> TRUECRIME_SECTIONS
                MusicSearchMode.MUSIC, MusicSearchMode.INSTRUMENTAL -> DISCOVER_SECTIONS
            }
            val picked = listOf(pool.first()) + pool.drop(1).shuffled().take(SPOKEN_SECTIONS_PER_LOAD - 1)
            picked.mapIndexed { index, (title, query) ->
                async {
                    val songs = try {
                        getMixSongs(query, mode).take(24).let { if (index == 0) it else it.shuffled() }
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
    suspend fun getMixSongs(
        query: String,
        mode: MusicSearchMode = MusicSearchMode.MUSIC,
        /** true auf Mix-/Genre-Detailseiten: füllt kurze Suchlisten über
         *  Radio-Queues auf ~50 Songs auf. Die Entdecken-Karussells bleiben
         *  beim schnellen Pfad ohne Zusatz-Requests. */
        expand: Boolean = false,
    ): List<MusicSong> {
        if (isCollectionKey(query)) return getCollectionSongs(query, mode, expand)
        val all = searchMusic(query, mode)
        val tracks = when (mode) {
            MusicSearchMode.MUSIC, MusicSearchMode.INSTRUMENTAL -> all.filter { it.duration in 60..900 }
            MusicSearchMode.AUDIOBOOK -> all.filter { it.duration >= 120 }
            MusicSearchMode.PODCAST, MusicSearchMode.TRUECRIME -> all.filter { it.duration >= 180 }
        }
        val base = if (tracks.size >= 4) tracks else all
        // Radio-Expansion: eine einzelne Suche liefert oft nur eine Handvoll
        // Songs — die Top-Treffer als Seeds der echten YouTube-Radio-Queue
        // füllen den Mix stilistisch passend auf. Nicht im Instrumental-Modus
        // (Related würde den Ohne-Gesang-Filter unterlaufen).
        if (!expand || !mode.isMusical || base.size >= 40) return base
        if (mode == MusicSearchMode.INSTRUMENTAL) {
            // Radio-Queues können Gesang nicht ausschließen — ihr Nachschub
            // muss wie jeder Treffer durch den Instrumental-Filter.
            val related = coroutineScope {
                base.take(3).map { seed -> async { getRelatedSongs(seed.videoId) } }.map { it.await() }
            }
            val seen = base.mapTo(mutableSetOf()) { it.videoId }
            val extra = filterInstrumental(related.flatten())
                .filter { it.duration in 60..900 && seen.add(it.videoId) }
            return (base + extra).take(50)
        }
        val related = coroutineScope {
            base.take(3).map { seed -> async { getRelatedSongs(seed.videoId) } }.map { it.await() }
        }
        val seen = base.mapTo(mutableSetOf()) { it.videoId }
        val extra = related.flatten()
            .filter { it.duration in 60..900 && seen.add(it.videoId) }
        return (base + extra).take(50)
    }

    // --- Feed-Sektionen als eigene Seite ("Alle") ---

    /** Songs, die der Feed gerade zeigt — die Detailseite startet mit genau diesen. */
    private val stagedCollections = java.util.concurrent.ConcurrentHashMap<String, List<MusicSong>>()

    /** Legt die sichtbaren Songs einer Feed-Sektion für ihre Detailseite bereit. */
    fun stageCollection(key: String, songs: List<MusicSong>) {
        if (isCollectionKey(key) && songs.isNotEmpty()) stagedCollections[key] = songs
    }

    private fun isCollectionKey(query: String) =
        query.startsWith(KEY_RELATED) || query.startsWith(KEY_FAVORITES) || query.startsWith(KEY_STAGED)

    /**
     * Inhalt einer Feed-Sektion ohne Suchbegriff ("Ähnlich wie …", Favoriten,
     * Radio): zuerst exakt die Songs, die im Feed zu sehen waren; mit
     * [expand] kommen über die Radio-Queues der ersten Songs weitere passende
     * dazu (im Instrumental-Modus gefiltert). Ohne bereitgelegte Liste —
     * etwa nach einem App-Neustart — wird der Schlüssel neu aufgelöst.
     */
    private suspend fun getCollectionSongs(key: String, mode: MusicSearchMode, expand: Boolean): List<MusicSong> {
        val instrumental = mode == MusicSearchMode.INSTRUMENTAL
        // Favoriten bleiben Favoriten: die Seite zeigt alle (nicht nur die
        // 24 aus dem Feed) und mischt nichts Fremdes unter.
        if (key.startsWith(KEY_FAVORITES)) {
            return getFavorites()
                .filter { (it.listenMode ?: MusicSearchMode.MUSIC.apiValue) == mode.apiValue }
                .ifEmpty { stagedCollections[key].orEmpty() }
        }
        val base = stagedCollections[key] ?: if (key.startsWith(KEY_RELATED)) {
            getRelatedSongs(key.removePrefix(KEY_RELATED)).let { if (instrumental) filterInstrumental(it) else it }
        } else {
            emptyList()
        }
        if (!expand || !mode.isMusical || base.size >= 60 || base.isEmpty()) return base
        val related = coroutineScope {
            base.take(3).map { seed -> async { getRelatedSongs(seed.videoId) } }.map { it.await() }
        }.flatten()
        val seen = base.mapTo(HashSet()) { it.videoId }
        val extra = (if (instrumental) filterInstrumental(related) else related)
            .filter { it.duration in 60..900 && seen.add(it.videoId) }
        return (base + extra).take(60)
    }

    /**
     * Nachschub, wenn die Warteschlange leerläuft. Erste Wahl ist die echte
     * Radio-Queue von YouTube Music ([getRelatedSongs]); erst wenn sie nichts
     * liefert, greift die alte Stichwortsuche im Umfeld des Seeds. Bei aktivem
     * Instrumental-Filter bleibt es beim Suchweg — nur der filtert verlässlich
     * auf Stücke ohne Gesang.
     */
    suspend fun getAutoplaySongs(seed: MusicSong, exclude: Set<String>, queueMode: String? = null): List<MusicSong> {
        val mode = queueMode?.let(MusicSearchMode::fromApiValue) ?: currentMode()
        // Radio-Queues sind Musik: für Songs erste Wahl (im Instrumental-Modus
        // durch den Filter), für Podcasts/Hörbücher/True Crime unbrauchbar —
        // dort geht es über weitere Folgen desselben Kanals weiter.
        if (mode.isMusical) {
            val related = getRelatedSongs(seed.videoId).filter { it.videoId !in exclude }
            val usable = if (mode == MusicSearchMode.INSTRUMENTAL) filterInstrumental(related) else related
            if (usable.size >= 3) return usable.shuffled().take(15)
        }
        val queries = listOfNotNull(
            seed.uploader.takeIf { it.isNotBlank() },
            seed.title.split(" ", "(", "-").firstOrNull { it.length > 3 },
        ) + when (mode) {
            // Damit der Strom ohne Gesang nie abreißt: notfalls ein Instrument.
            MusicSearchMode.INSTRUMENTAL -> listOf(InstrumentalCatalog.INSTRUMENTS.random().query)
            else -> emptyList()
        }
        for (q in queries) {
            val found = runCatching { searchMusic(q, mode) }.getOrDefault(emptyList())
                .filter { it.videoId !in exclude && fitsMode(it, mode) }
            if (found.size >= 3) return found.shuffled().take(10)
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

    private val homeCache = mutableMapOf<MusicSearchMode, Pair<Long, List<HomeSection>>>()

    /** Verlauf eines Modus — Seeds und "schon gehört" kommen nur von dort. */
    private suspend fun historyOf(mode: MusicSearchMode): List<MusicSong> =
        runCatching { getHistory() }.getOrDefault(emptyList())
            .filter { (it.listenMode ?: MusicSearchMode.MUSIC.apiValue) == mode.apiValue }

    /**
     * Baut den Musik-Feed. Kern sind die kuratierten Genre-Mixe — aus einem
     * großen Pool rotiert, damit jedes Pull-to-Refresh andere bringt; dazu
     * fest "Neu erschienen" mit dem aktuellen Monat. Mit Verlauf kommen oben
     * "Neu für dich" (Radio aus nie gehörten Songs, Autoplay hält den Strom
     * am Laufen), "Ähnlich wie …", "Playlists für dich" und die Favoriten.
     * Der Instrumental-Modus hat einen eigenen Aufbau ([getInstrumentalHome]).
     */
    suspend fun getPersonalizedHome(force: Boolean = false): List<HomeSection> = coroutineScope {
        val nowMs = System.currentTimeMillis()
        homeCache[MusicSearchMode.MUSIC]?.takeIf { !force && nowMs - it.first < HOME_CACHE_TTL_MS }?.let {
            return@coroutineScope it.second
        }

        val history = historyOf(MusicSearchMode.MUSIC)
        val favorites = runCatching { getFavorites() }.getOrDefault(emptyList())
            .filter { (it.listenMode ?: MusicSearchMode.MUSIC.apiValue) == MusicSearchMode.MUSIC.apiValue }
        // Seeds: jüngster Verlauf zuerst, Favoriten mischen den Geschmack
        // abseits der letzten Tage dazu.
        val seeds = (history.take(12) + favorites.shuffled().take(4))
            .distinctBy { it.videoId }
            .shuffled()
            .take(3)

        val relatedDeferred = seeds.map { seed -> async { seed to getRelatedSongs(seed.videoId) } }
        val newReleases = "Neu erschienen" to newReleasesQuery(nowMs)
        val curatedPick = listOf(DISCOVER_SECTIONS.first(), newReleases) +
            DISCOVER_SECTIONS.drop(1).shuffled().take(CURATED_PER_LOAD - 1)
        val curatedDeferred = async { curatedHomeSections(curatedPick) }
        val playlistsDeferred = async { playlistsForYou(history) }
        val relatedBySeed = relatedDeferred.map { it.await() }

        val heardIds = history.mapTo(HashSet()) { it.videoId }
        val sections = mutableListOf<HomeSection>()
        val curated = curatedDeferred.await()

        // "Neu für dich": Radio-Strom aus Songs, die noch nie liefen. Ohne
        // Verlauf speist er sich aus "Neu erschienen" und den Mixen.
        val freshPool = relatedBySeed.flatMap { it.second }.ifEmpty { curated.flatMap { it.songs } }
        val fresh = freshPool
            .filter { it.videoId !in heardIds && it.duration in 60..900 }
            .shuffled()
            .distinctBy { it.videoId }
            .take(40)
        if (fresh.size >= 5) {
            sections += HomeSection("Neu für dich", HomeSectionKind.MIX, songs = fresh, query = "${KEY_STAGED}music-radio")
        }

        // Bis zu zwei "Ähnlich wie …"-Sektionen aus einzelnen Seeds.
        val similar = relatedBySeed.filter { it.second.size >= 5 }.take(2).map { (seed, related) ->
            HomeSection(
                "Ähnlich wie ${seedLabelOf(seed)}",
                HomeSectionKind.SIMILAR,
                songs = related.take(24),
                query = "$KEY_RELATED${seed.videoId}",
            )
        }
        similar.firstOrNull()?.let { sections += it }

        val playlists = playlistsDeferred.await()
        if (playlists.size >= 3) {
            sections += HomeSection(
                "Playlists für dich",
                HomeSectionKind.PLAYLISTS,
                items = playlists.map { HomeItem.PlaylistItem(it) },
            )
        }
        sections += similar.drop(1)

        if (favorites.size >= 4) {
            sections += HomeSection(
                "Deine Favoriten",
                HomeSectionKind.FAVORITES,
                songs = favorites.shuffled().take(24),
                query = KEY_FAVORITES,
            )
        }

        // Kuratierte Genre-Mixe als fester Kern — auch für frische Accounts.
        sections += curated

        val result = sections.toList()
        if (result.isNotEmpty()) homeCache[MusicSearchMode.MUSIC] = nowMs to result
        result
    }

    /**
     * Feed des Instrumental-Modus: keine gewöhnlichen Playlists, sondern
     * Mix-Instrumentals (Instrument-Kombinationen, rotierend) und vorn fest
     * "Piano für dich". Dazu ein Radio ohne Gesang und — mit Verlauf — ein
     * gefiltertes "Ähnlich wie …". Die Instrument-Kategorien selbst sind
     * statisch ([InstrumentalCatalog]) und kosten keinen Request.
     */
    suspend fun getInstrumentalHome(force: Boolean = false): List<HomeSection> = coroutineScope {
        val mode = MusicSearchMode.INSTRUMENTAL
        val nowMs = System.currentTimeMillis()
        homeCache[mode]?.takeIf { !force && nowMs - it.first < HOME_CACHE_TTL_MS }?.let {
            return@coroutineScope it.second
        }
        val history = historyOf(mode)
        val heardIds = history.mapTo(HashSet()) { it.videoId }
        val seed = history.take(10).randomOrNull()
        val similarDeferred = seed?.let { s -> async { s to filterInstrumental(getRelatedSongs(s.videoId)) } }

        val piano = InstrumentalCatalog.INSTRUMENTS.first()
        val picks = listOf(InstrumentalCatalog.PIANO_FOCUS_TITLE to piano.query) +
            InstrumentalCatalog.MIXES.shuffled().take(InstrumentalCatalog.MIXES_PER_LOAD)
        val curated = picks.mapIndexed { index, (title, query) ->
            async {
                val songs = runCatching { getMixSongs(query, mode) }.getOrDefault(emptyList())
                    .let { if (index == 0) it.take(24) else it.shuffled().take(16) }
                HomeSection(title, HomeSectionKind.CURATED, songs = songs, query = query)
            }
        }.map { it.await() }.filter { it.songs.isNotEmpty() }

        val sections = mutableListOf<HomeSection>()
        val radio = curated.flatMap { it.songs }
            .filter { it.videoId !in heardIds }
            .shuffled()
            .distinctBy { it.videoId }
            .take(40)
        if (radio.size >= 5) {
            sections += HomeSection(
                "Instrumental-Radio",
                HomeSectionKind.MIX,
                songs = radio,
                query = "${KEY_STAGED}instrumental-radio",
            )
        }
        similarDeferred?.await()?.let { (s, related) ->
            if (related.size >= 5) {
                sections += HomeSection(
                    "Ähnlich wie ${seedLabelOf(s)}",
                    HomeSectionKind.SIMILAR,
                    songs = related.take(24),
                    query = "$KEY_RELATED${s.videoId}",
                )
            }
        }
        sections += curated
        val result = sections.toList()
        if (curated.isNotEmpty()) homeCache[mode] = nowMs to result
        result
    }

    /**
     * Nachschub für den endlosen Feed: weitere, noch nicht gezeigte Mixe aus
     * dem Pool des Modus. Leer, wenn der Pool erschöpft ist.
     */
    suspend fun getMoreHomeSections(mode: MusicSearchMode, shownQueries: Set<String>): List<HomeSection> =
        coroutineScope {
            val pool = if (mode == MusicSearchMode.INSTRUMENTAL) InstrumentalCatalog.MIXES else DISCOVER_SECTIONS
            pool.filter { it.second !in shownQueries }.shuffled().take(MORE_PER_PAGE).map { (title, query) ->
                async {
                    val songs = runCatching { getMixSongs(query, mode) }.getOrDefault(emptyList())
                        .shuffled().take(if (mode == MusicSearchMode.INSTRUMENTAL) 16 else 12)
                    HomeSection(title, HomeSectionKind.CURATED, songs = songs, query = query)
                }
            }.map { it.await() }.filter { it.songs.isNotEmpty() }
        }

    /** Wie [getMoreHomeSections] für die Sprech-Modi (Hörbuch, Podcast, True Crime). */
    suspend fun getMoreDiscoverSections(mode: MusicSearchMode, shownQueries: Set<String>): List<DiscoverSection> =
        coroutineScope {
            val pool = when (mode) {
                MusicSearchMode.AUDIOBOOK -> AUDIOBOOK_SECTIONS
                MusicSearchMode.PODCAST -> PODCAST_SECTIONS
                MusicSearchMode.TRUECRIME -> TRUECRIME_SECTIONS
                else -> DISCOVER_SECTIONS
            }
            pool.filter { it.second !in shownQueries }.shuffled().take(MORE_PER_PAGE).map { (title, query) ->
                async {
                    val songs = runCatching { getMixSongs(query, mode) }.getOrDefault(emptyList()).shuffled().take(24)
                    DiscoverSection(title, songs, query)
                }
            }.map { it.await() }.filter { it.songs.isNotEmpty() }
        }

    /** Suchbegriff für "Neu erschienen" — mit Monat, damit er nicht veraltet. */
    private fun newReleasesQuery(nowMs: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = nowMs }
        return "new songs ${MONTHS_EN[cal.get(java.util.Calendar.MONTH)]} ${cal.get(java.util.Calendar.YEAR)}"
    }

    /**
     * Echte YouTube-Playlists für den Feed: eine Suche zum meistgehörten
     * Künstler der letzten Zeit, eine zu einem rotierenden Stimmungs-Thema.
     */
    private suspend fun playlistsForYou(history: List<MusicSong>): List<RemotePlaylist> = coroutineScope {
        val topArtist = history.take(40)
            .map { it.uploader.substringBefore(",").substringBefore(" & ").trim() }
            .filter { it.isNotBlank() }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .take(3).randomOrNull()?.key
        val queries = listOfNotNull(topArtist?.let { "$it mix" }) +
            PLAYLIST_QUERIES.shuffled().take(if (topArtist == null) 2 else 1)
        val lists = queries.map { q -> async { searchTypedPlaylists(q) } }.map { it.await() }
        // Reißverschluss: abwechselnd aus beiden Quellen statt Block für Block.
        val merged = mutableListOf<RemotePlaylist>()
        for (i in 0 until (lists.maxOfOrNull { it.size } ?: 0)) {
            lists.forEach { l -> l.getOrNull(i)?.let(merged::add) }
        }
        // videoCount kommt von der Typed-Suche nicht mit (immer 0) — nicht danach filtern.
        merged
            .filter { pl ->
                val text = "${pl.name} ${pl.uploaderName}".lowercase()
                FOREIGN_PLAYLIST_MARKERS.none { it in text }
            }
            .distinctBy { it.playlistId }
            .take(12)
    }

    /** Kuratierte Suchbegriff-Mixe im Home-Sektionsformat; Songs neu gemischt. */
    private suspend fun curatedHomeSections(source: List<Pair<String, String>>): List<HomeSection> =
        coroutineScope {
            source.mapIndexed { index, (title, query) ->
                async {
                    val songs = try {
                        // Die erste Sektion (Charts) behält ihre Rangfolge.
                        getMixSongs(query, MusicSearchMode.MUSIC)
                            .let { if (index == 0) it.take(12) else it.shuffled().take(12) }
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
        val backend = resolveBackendUrl() ?: return pipedStreamFallback(videoId)
        return "$backend/music/audio/$videoId"
    }

    /**
     * Wärmt den Audio-Stream auf dem Backend vor (yt-dlp-Auflösung + Stream-Cache),
     * damit der Übergang zum nächsten Track ohne 5-15 s Wartezeit lückenlos startet.
     */
    suspend fun prewarmAudio(videoId: String) {
        val backend = resolveBackendUrl()
        if (backend.isNullOrBlank()) return
        withContext(Dispatchers.IO) {
            runCatching {
                api.getMusicStream(videoId)
            }
        }
    }

    /**
     * Backend-Basis-URL ohne Slash am Ende — null, wenn keine konfiguriert ist.
     * Der Download-Manager entscheidet damit zwischen serverseitiger
     * Warteschlange und direktem Fallback.
     */
    suspend fun backendBase(): String? =
        resolveBackendUrl()?.takeIf { it.isNotBlank() }

    /**
     * Video-Variante für den Audio↔Video-Umschalter (Podcast/True Crime):
     * muxed MP4 über den Backend-Proxy mit Range-Support. Ohne Backend-URL
     * gibt es kein Video — der Player bleibt dann im Audio-Modus.
     */
    suspend fun getVideoStream(videoId: String): String? {
        val backend = resolveBackendUrl() ?: return null
        return "$backend/music/video/$videoId"
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
    suspend fun recordPlayed(song: MusicSong, touchRecency: Boolean = true, mode: String? = null) {
        val existing = songDao.getByName(song.videoId)
        val mode = mode ?: currentMode().apiValue
        val entity = song.toEntity().copy(
            isFavorite = existing?.isFavorite ?: song.isFavorite,
            // Echte Wiedergabe ordnet den Song dem aktuellen Modus zu; Downloads
            // und Playlist-Verknüpfungen lassen eine bestehende Zuordnung stehen.
            listenMode = if (touchRecency) mode else existing?.listenMode ?: mode,
            addedAt = if (touchRecency) {
                System.currentTimeMillis()
            } else {
                existing?.addedAt ?: song.addedAt
            },
        )
        // Wichtig: kein REPLACE-Insert für bekannte Songs — SQLite ersetzt die
        // Zeile per DELETE+INSERT, und der CASCADE-Fremdschlüssel in
        // music_playlist_songs würde alle Playlist-Verknüpfungen mitreißen.
        if (existing == null) {
            songDao.insert(entity)
        } else {
            songDao.update(entity)
        }
        // Nur echte Wiedergaben zählen (touchRecency=false sind Downloads/
        // Playlist-Verknüpfungen); alte Ereignisse gleich mit wegräumen.
        if (touchRecency) {
            val now = System.currentTimeMillis()
            playEventDao.insert(MusicPlayEventEntity(videoId = song.videoId, playedAt = now))
            playEventDao.prune(olderThan = now - 30L * 24 * 60 * 60 * 1000)
        }
    }

    /** Meistgehörte Songs der letzten 7 Tage (für die Schnellauswahl). */
    suspend fun getTopPlayedOfWeek(limit: Int = 40): List<MusicSong> {
        val since = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        return playEventDao.topPlayedSince(since, limit).map { it.toSong() }
    }

    /** Returns the new favorite state. Unknown songs get saved first. */
    suspend fun toggleFavorite(song: MusicSong): Boolean {
        val existing = songDao.getByName(song.videoId)
        return if (existing == null) {
            songDao.insert(song.toEntity().copy(isFavorite = true, listenMode = currentMode().apiValue))
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
    suspend fun addToPlaylist(playlistId: Int, song: MusicSong): Boolean {
        recordPlayed(song, touchRecency = false)
        val nextPos = playlistSongDao.maxPosition(playlistId) + 1
        val rowId = playlistSongDao.insert(MusicPlaylistSongEntity(playlistId, song.videoId, position = nextPos))
        return rowId != -1L
    }

    /** Persistiert die manuelle Reihenfolge aus dem Bearbeiten-Modus. */
    suspend fun reorderPlaylist(playlistId: Int, orderedVideoIds: List<String>) {
        orderedVideoIds.forEachIndexed { index, videoId ->
            playlistSongDao.setPosition(playlistId, videoId, index + 1)
        }
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
        artists = artists.map { SongArtist(name = it.name, channelId = it.channelId) },
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
                latest = withFavoriteState(dto.latest.map { it.toSong() }),
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
        listenMode = listenMode ?: MusicSearchMode.MUSIC.apiValue,
    )

    private fun MusicSongEntity.toSong() = MusicSong(
        videoId = videoId, title = title, uploader = uploader, uploaderUrl = uploaderUrl,
        thumbnailUrl = thumbnailUrl, duration = duration, views = views,
        addedAt = addedAt, isFavorite = isFavorite, listenMode = listenMode,
    )

    private fun MusicPlaylistEntity.toModel() = MusicPlaylist(
        id = id, name = name, description = description,
        thumbnailUrl = thumbnailUrl, createdAt = createdAt,
    )
}
