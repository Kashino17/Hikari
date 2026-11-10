package com.hikari.app.domain.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hikari.app.data.api.dto.LibraryResponse
import com.hikari.app.data.api.dto.isArchive
import com.hikari.app.data.api.dto.isFilm
import com.hikari.app.data.db.LocalDownloadKind
import com.hikari.app.data.db.LocalDownloadSource
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.library.SeriesResume
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.domain.repo.FeedRepository
import com.hikari.app.domain.repo.MusicRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Smart-Downloads: hält im WLAN bereit, was man als Nächstes braucht — je Art
 * getrennt schaltbar und zusammen begrenzt auf ein Speicherbudget:
 *  - Serien & Filme: die nächsten Folgen laufender Serien, angefangene Filme
 *  - Musik: die Lieblingssongs
 *  - YouTube: das neueste Video jedes abonnierten Kanals
 *
 * Ist das Budget voll, weichen zuerst Smart-Downloads, die nicht mehr gebraucht
 * werden (z. B. geschaute Folgen). Reicht das nicht, wird nichts Neues geladen.
 */
@HiltWorker
class SmartDownloadWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val feedRepo: FeedRepository,
    private val localDownloads: LocalDownloadManager,
    private val musicRepo: MusicRepository,
    private val musicDownloads: LocalMusicDownloadManager,
    private val settings: SettingsStore,
    private val retention: DownloadRetentionCleaner,
    private val storage: SmartDownloadStorage,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        // Abgelaufene Downloads räumen — unabhängig davon, ob Smart-Downloads an sind.
        runCatching { retention.run() }

        val enabled = runCatching { settings.smartDownloads.first() }.getOrDefault(true)
        if (!enabled) return Result.success()

        // Erst zusammentragen, was gebraucht wird — danach richtet sich, was
        // bei vollem Budget weichen darf.
        val library = runCatching { feedRepo.getLibrary() }.getOrNull()
        val videos = buildList {
            if (library != null && settings.smartSeries.first()) addAll(archivePlan(library))
            if (library != null && settings.smartYoutube.first()) addAll(youtubePlan(library))
        }
        val songs = if (settings.smartMusic.first()) {
            runCatching { musicRepo.getFavorites() }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val wanted = videos.map { it.meta.videoId }.toSet() + songs.map { it.videoId }

        downloadVideos(videos, wanted)
        downloadSongs(songs, wanted)
        return Result.success()
    }

    /** Ein geplanter Video-Download; YouTube-Videos muss der Server erst selbst holen. */
    private data class Planned(val meta: LocalDownloadMetadata, val needsServerDownload: Boolean)

    /** Nächste Folgen der zuletzt geschauten Serien und angefangene Filme. */
    private suspend fun archivePlan(library: LibraryResponse): List<Planned> {
        val archive = library.continueWatching.filter { it.isArchive() }
        val plan = mutableListOf<Planned>()
        for (seriesId in archive.mapNotNull { it.series_id }.distinct().take(MAX_SERIES)) {
            val series = runCatching { feedRepo.getSeries(seriesId) }.getOrNull() ?: continue
            SeriesResume.upcoming(series.videos, EPISODES_AHEAD).forEach { video ->
                plan += Planned(
                    LocalDownloadMetadata(
                        videoId = video.id,
                        kind = LocalDownloadKind.SERIES,
                        title = video.title,
                        durationSeconds = video.duration_seconds,
                        thumbnailUrl = video.thumbnail_url,
                        seriesId = series.id,
                        seriesTitle = series.title,
                        seriesThumbnailUrl = series.thumbnail_url,
                        season = video.season,
                        episode = video.episode,
                        source = LocalDownloadSource.SMART,
                    ),
                    needsServerDownload = false,
                )
            }
        }
        archive.filter { it.isFilm() }.forEach { film ->
            plan += Planned(
                LocalDownloadMetadata(
                    videoId = film.id,
                    kind = LocalDownloadKind.MOVIE,
                    title = film.title,
                    durationSeconds = film.duration_seconds,
                    thumbnailUrl = film.thumbnail_url,
                    source = LocalDownloadSource.SMART,
                ),
                needsServerDownload = false,
            )
        }
        return plan
    }

    /** Je abonniertem Kanal das neueste Video, das noch nicht zu Ende geschaut ist. */
    private fun youtubePlan(library: LibraryResponse): List<Planned> {
        val channels = library.channels.associateBy { it.id }
        return library.channelUploads
            .filterNot { SeriesResume.isFinished(it) }
            .groupBy { it.channel_id }
            .mapNotNull { (_, uploads) -> uploads.maxByOrNull { it.published_at } }
            .sortedByDescending { it.published_at }
            .take(MAX_CHANNELS)
            .map { video ->
                val channel = channels[video.channel_id]
                Planned(
                    LocalDownloadMetadata(
                        videoId = video.id,
                        kind = LocalDownloadKind.CHANNEL,
                        title = video.title,
                        durationSeconds = video.duration_seconds,
                        thumbnailUrl = video.thumbnail_url,
                        channelId = video.channel_id,
                        channelTitle = channel?.title ?: video.channelTitle,
                        channelThumbnailUrl = channel?.thumbnail_url,
                        source = LocalDownloadSource.SMART,
                    ),
                    needsServerDownload = true,
                )
            }
    }

    private suspend fun downloadVideos(plan: List<Planned>, wanted: Set<String>) {
        var done = 0
        for (item in plan) {
            if (done >= MAX_PER_FIRE) return
            if (localDownloads.isDownloaded(item.meta.videoId)) continue
            if (!storage.makeRoom(wanted)) return
            // Gestreamte Videos liegen nicht auf dem Server — erst dort anstoßen.
            // „queued" heißt: er lädt gerade, der nächste Lauf holt die Datei.
            if (item.needsServerDownload && feedRepo.requestServerDownload(item.meta.videoId) != "ready") continue
            if (localDownloads.download(item.meta).isSuccess) done += 1
        }
    }

    /** Lieblingssongs liegen lokal in Room — das braucht nur Netz für den Stream, keinen Server. */
    private suspend fun downloadSongs(songs: List<MusicSong>, wanted: Set<String>) {
        var done = 0
        for (song in songs) {
            if (done >= MAX_PER_FIRE) return
            if (musicDownloads.isDownloaded(song.videoId)) continue
            if (!storage.makeRoom(wanted)) return
            if (musicDownloads.download(song, LocalDownloadSource.SMART).isSuccess) done += 1
        }
    }

    companion object {
        private const val MAX_PER_FIRE = 5
        /** So viele laufende Serien werden bedient … */
        private const val MAX_SERIES = 3
        /** … und je Serie so viele Folgen vorgehalten (die aktuelle und die nächste). */
        private const val EPISODES_AHEAD = 2
        /** Höchstens so viele Kanäle bekommen ihr neuestes Video aufs Gerät. */
        private const val MAX_CHANNELS = 6
    }
}
