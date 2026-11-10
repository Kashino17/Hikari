package com.hikari.app.domain.library

import com.hikari.app.data.api.dto.LibraryVideoDto
import com.hikari.app.data.api.dto.SeriesDetailResponse
import com.hikari.app.data.api.dto.SeriesDto
import com.hikari.app.data.api.dto.VideoDetailDto
import com.hikari.app.data.db.LocalDownloadEntity
import com.hikari.app.data.db.LocalDownloadKind
import com.hikari.app.data.db.LocalMangaArcEntity
import com.hikari.app.data.db.PlaybackPositionEntity
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.domain.repo.PlaylistWithSongs

/**
 * Die Bibliothek ohne Netz: alles, was auf dem Gerät liegt, in derselben Form,
 * in der die echte Bibliothek ihre Inhalte führt ([SeriesDto],
 * [LibraryVideoDto]). Dadurch zeigen dieselben Karten, derselbe Kopf und
 * dieselbe Serienseite die Downloads — offline sieht aus wie online, nur mit
 * weniger drin.
 */
data class OfflineLibrary(
    /** Womit es weitergeht, zuletzt Geschautes zuerst — je Serie nur eine Folge. */
    val continueWatching: List<LibraryVideoDto> = emptyList(),
    val series: List<SeriesDto> = emptyList(),
    /** Die Folgen je Serie, in Sendereihenfolge. */
    val episodes: Map<String, List<LibraryVideoDto>> = emptyMap(),
    val films: List<LibraryVideoDto> = emptyList(),
    /** Heruntergeladene YouTube-Videos, neueste zuerst. */
    val channelVideos: List<LibraryVideoDto> = emptyList(),
    val manga: List<LocalMangaArcEntity> = emptyList(),
    /** Favoriten, alle Downloads und die eigenen Playlists — in dieser Reihenfolge, jeweils nur Geladenes. */
    val playlists: List<OfflinePlaylist> = emptyList(),
    /** Alle Songs auf dem Gerät, zuletzt geladene zuerst. */
    val songs: List<MusicSong> = emptyList(),
    val videoBytes: Long = 0L,
) {
    val seriesById: Map<String, SeriesDto> = series.associateBy { it.id }

    val videoCount: Int get() = episodes.values.sumOf { it.size } + films.size + channelVideos.size

    val hasVideos: Boolean get() = videoCount > 0
    val hasMusic: Boolean get() = songs.isNotEmpty() || playlists.isNotEmpty()

    val isEmpty: Boolean get() = !hasVideos && !hasMusic && manga.isEmpty()

    /** Das Video für den Kopf der Seite: womit es weitergeht, sonst der neueste Download. */
    val hero: LibraryVideoDto?
        get() = continueWatching.firstOrNull()
            ?: (episodes.values.flatten() + films + channelVideos).maxByOrNull { it.discovered_at }
                ?.let { newest -> newest.series_id?.let { nextEpisode(it) } ?: newest }

    /** Die Folge, mit der eine Serie weitergeht (bzw. anfängt). */
    fun nextEpisode(seriesId: String): LibraryVideoDto? =
        episodes[seriesId]?.let(SeriesResume::target)

    /** Die Serienseite der Bibliothek, gefüllt nur mit den Folgen auf dem Gerät. */
    fun seriesDetail(seriesId: String): SeriesDetailResponse? {
        val series = seriesById[seriesId] ?: return null
        return SeriesDetailResponse(
            id = series.id,
            title = series.title,
            description = series.description,
            thumbnail_url = series.thumbnail_url,
            added_at = series.added_at,
            videos = episodes[seriesId].orEmpty(),
            poster_url = series.poster_url,
            backdrop_url = series.backdrop_url,
        )
    }
}

enum class OfflinePlaylistKind { FAVORITES, DOWNLOADS, PLAYLIST }

/**
 * Eine Playlist, wie sie ohne Netz nutzbar ist: [songs] sind nur die Titel,
 * die als Datei auf dem Gerät liegen. Was nicht geladen ist, taucht nicht auf —
 * so führt kein Tipp ins Leere.
 */
data class OfflinePlaylist(
    val key: String,
    val kind: OfflinePlaylistKind,
    val name: String,
    val songs: List<MusicSong>,
) {
    /** Bis zu vier verschiedene Cover fürs Titelbild. */
    val covers: List<String>
        get() = songs.map { it.thumbnailUrl }.filter { it.isNotBlank() }.distinct().take(4)

    val totalSeconds: Int get() = songs.sumOf { it.duration }
}

object OfflineLibraryBuilder {

    /** Unter zehn Sekunden gilt ein Video nicht als angefangen — so hält es auch der Server. */
    private const val MIN_STARTED_MS = 10_000L

    fun build(
        downloads: List<LocalDownloadEntity>,
        positions: List<PlaybackPositionEntity>,
        mangaArcs: List<LocalMangaArcEntity> = emptyList(),
        downloadedSongs: List<MusicSong> = emptyList(),
        favorites: List<MusicSong> = emptyList(),
        playlists: List<PlaylistWithSongs> = emptyList(),
    ): OfflineLibrary {
        val positionById = positions.associateBy { it.videoId }
        fun LocalDownloadEntity.video() = toLibraryVideo(positionById[videoId])

        val seriesEpisodes = downloads.filter { it.isEpisode() }.groupBy { it.seriesId!! }
        val episodes = seriesEpisodes.mapValues { (_, items) -> SeriesResume.ordered(items.map { it.video() }) }
        val series = seriesEpisodes
            .map { (id, items) -> items.toSeries(id, episodes.getValue(id)) }
            // Zuletzt geschaute Serien vorn, danach die zuletzt geladenen.
            .sortedWith(compareByDescending<SeriesDto> { it.last_watched_at ?: 0L }.thenByDescending { it.added_at })

        val singles = downloads.filterNot { it.isEpisode() }
        val films = singles.filter { it.kind != LocalDownloadKind.CHANNEL.name }
            .sortedByDescending { it.downloadedAt }.map { it.video() }
        val channelVideos = singles.filter { it.kind == LocalDownloadKind.CHANNEL.name }
            .sortedByDescending { it.downloadedAt }.map { it.video() }

        // Weiterschauen: je Serie die Folge, mit der es weitergeht — auch die
        // nächste, wenn die letzte zu Ende ist. Einzelvideos, solange sie
        // angefangen und nicht fertig sind.
        val resumeEpisodes = series.mapNotNull { s ->
            val lastWatched = s.last_watched_at ?: return@mapNotNull null
            val target = SeriesResume.target(episodes.getValue(s.id)) ?: return@mapNotNull null
            // Alles durchgeschaut: nichts mehr anzubieten.
            if (SeriesResume.isFinished(target)) null else target to lastWatched
        }
        val resumeSingles = (films + channelVideos)
            .filter { SeriesResume.isStarted(it) && !SeriesResume.isFinished(it) }
            .map { it to (it.progress_updated_at ?: 0L) }
        val continueWatching = (resumeEpisodes + resumeSingles).sortedByDescending { it.second }.map { it.first }

        val songs = downloadedSongs.sortedByDescending { it.addedAt }
        val onDevice = songs.associateBy { it.videoId }
        // Aus einer fremden Liste nur das übernehmen, was geladen ist — in deren
        // Reihenfolge und mit den Angaben des Downloads (dessen Cover liegt auf dem Gerät).
        fun List<MusicSong>.onDevice(): List<MusicSong> = mapNotNull { onDevice[it.videoId] }

        // Favoriten und Playlists nur mit ihren geladenen Songs; ohne einen
        // einzigen geladenen Song erscheint die Liste gar nicht.
        val offlinePlaylists = buildList {
            favorites.onDevice().takeIf { it.isNotEmpty() }?.let {
                add(OfflinePlaylist("favorites", OfflinePlaylistKind.FAVORITES, "Favoriten", it))
            }
            if (songs.isNotEmpty()) {
                add(OfflinePlaylist("downloads", OfflinePlaylistKind.DOWNLOADS, "Alle Downloads", songs))
            }
            playlists.forEach { p ->
                p.songs.onDevice().takeIf { it.isNotEmpty() }?.let {
                    add(OfflinePlaylist("playlist-${p.playlist.id}", OfflinePlaylistKind.PLAYLIST, p.playlist.name, it))
                }
            }
        }

        return OfflineLibrary(
            continueWatching = continueWatching,
            series = series,
            episodes = episodes,
            films = films,
            channelVideos = channelVideos,
            manga = mangaArcs,
            playlists = offlinePlaylists,
            songs = songs,
            videoBytes = downloads.sumOf { it.byteSize },
        )
    }

    /** Eine Serienfolge — eine „Serie" ohne Serien-Id wird wie ein Film geführt. */
    private fun LocalDownloadEntity.isEpisode(): Boolean =
        kind == LocalDownloadKind.SERIES.name && seriesId != null

    private fun List<LocalDownloadEntity>.toSeries(id: String, episodes: List<LibraryVideoDto>): SeriesDto {
        // Der zuletzt geladene Eintrag trägt die frischesten Angaben zur Serie.
        val newest = maxBy { it.downloadedAt }
        return SeriesDto(
            id = id,
            title = newest.seriesTitle ?: newest.title,
            thumbnail_url = firstNotNullOfOrNull { it.seriesThumbnailUrl } ?: newest.thumbnailUrl,
            added_at = newest.downloadedAt,
            episode_count = size,
            season_count = mapNotNull { it.season }.distinct().size.coerceAtLeast(1),
            last_watched_at = episodes.filter(SeriesResume::isStarted)
                .maxOfOrNull { it.progress_updated_at ?: 0L },
            poster_url = firstNotNullOfOrNull { it.seriesPosterUrl },
            backdrop_url = firstNotNullOfOrNull { it.seriesBackdropUrl },
        )
    }

    internal fun startedSeconds(position: PlaybackPositionEntity?): Float? =
        position?.takeIf { it.positionMs >= MIN_STARTED_MS }?.let { it.positionMs / 1000f }
}

/** Ein Download in der Form, in der die Bibliothek ihre Videos führt — samt lokalem Fortschritt. */
fun LocalDownloadEntity.toLibraryVideo(position: PlaybackPositionEntity? = null): LibraryVideoDto {
    val isEpisode = kind == LocalDownloadKind.SERIES.name && seriesId != null
    val isChannelVideo = kind == LocalDownloadKind.CHANNEL.name
    val progress = OfflineLibraryBuilder.startedSeconds(position)
    return LibraryVideoDto(
        id = videoId,
        // „manual" kennzeichnet das eigene Archiv (Serien und Filme), alles andere einen YouTube-Kanal.
        channel_id = if (isChannelVideo) channelId ?: "channel" else "manual",
        series_id = seriesId.takeIf { isEpisode },
        title = title,
        published_at = downloadedAt,
        duration_seconds = durationSeconds,
        thumbnail_url = thumbnailUrl,
        discovered_at = downloadedAt,
        season = season,
        episode = episode,
        channelTitle = channelTitle,
        progress_seconds = progress,
        progress_updated_at = position?.updatedAt.takeIf { progress != null },
        downloaded = 1,
        is_movie = if (!isEpisode && !isChannelVideo) 1 else 0,
        poster_url = posterUrl,
    )
}

/** Die Angaben, die der Player zu einem Video braucht, wenn der Server nicht antwortet. */
fun LocalDownloadEntity.toVideoDetail(): VideoDetailDto = VideoDetailDto(
    id = videoId,
    channel_id = channelId ?: "manual",
    series_id = seriesId,
    series_title = seriesTitle,
    title = title,
    published_at = downloadedAt,
    duration_seconds = durationSeconds,
    thumbnail_url = thumbnailUrl,
    season = season,
    episode = episode,
    is_movie = if (kind == LocalDownloadKind.MOVIE.name) 1 else 0,
)
