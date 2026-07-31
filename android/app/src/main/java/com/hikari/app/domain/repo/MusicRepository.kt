package com.hikari.app.domain.repo

import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.MusicTrackDto
import com.hikari.app.data.api.dto.PipedSearchPageDto
import com.hikari.app.data.api.dto.PipedStreamsDto
import com.hikari.app.data.db.LocalMusicDownloadDao
import com.hikari.app.data.db.MusicPlaylistDao
import com.hikari.app.data.db.MusicPlaylistEntity
import com.hikari.app.data.db.MusicPlaylistSongDao
import com.hikari.app.data.db.MusicPlaylistSongEntity
import com.hikari.app.data.db.MusicSongDao
import com.hikari.app.data.db.MusicSongEntity
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.model.MusicPlaylist
import com.hikari.app.domain.model.MusicSong
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
    ;

    companion object {
        /** Modus zu einem API-Wert — unbekannte Werte fallen auf Musik zurück. */
        fun fromApiValue(value: String?): MusicSearchMode =
            entries.firstOrNull { it.apiValue == value } ?: MUSIC
    }
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
    private val api: HikariApi,
    private val fallbackClient: OkHttpClient,
    private val json: Json,
    private val settings: SettingsStore,
) {
    companion object {
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

    suspend fun getDiscoverSections(mode: MusicSearchMode = MusicSearchMode.MUSIC): List<DiscoverSection> =
        coroutineScope {
            val sections = when {
                mode == MusicSearchMode.AUDIOBOOK -> AUDIOBOOK_SECTIONS
                mode == MusicSearchMode.PODCAST -> PODCAST_SECTIONS
                instrumentalOnly() -> INSTRUMENTAL_SECTIONS
                else -> DISCOVER_SECTIONS
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
            MusicSearchMode.PODCAST -> all.filter { it.duration >= 180 }
        }
        return if (tracks.size >= 4) tracks else all
    }

    /**
     * Nachschub, wenn die Warteschlange leerläuft. Sucht im Umfeld des zuletzt
     * gespielten Stücks weiter; da der Weg über [searchMusic] führt, gilt der
     * Instrumental-Filter hier genauso wie überall sonst.
     */
    suspend fun getAutoplaySongs(seed: MusicSong, exclude: Set<String>): List<MusicSong> {
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

    suspend fun getAudioStream(videoId: String): String? {
        try {
            api.getMusicStream(videoId).url?.let { return it }
        } catch (_: Exception) {
            // backend down or extraction failed — try Piped directly
        }
        return pipedStreamFallback(videoId)
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
        val byId = songDao.getAll().associateBy { it.videoId }
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
                            uploaderUrl = "",
                            thumbnailUrl = "https://i.ytimg.com/vi/$videoId/mqdefault.jpg",
                            duration = item.duration ?: 0,
                            views = 0,
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
        uploaderUrl = "",
        thumbnailUrl = thumbnailUrl,
        duration = durationSeconds,
        views = 0,
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
