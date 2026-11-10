package com.hikari.app.domain.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hikari.app.data.db.LocalDownloadKind
import com.hikari.app.data.db.LocalDownloadSource
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.library.SeriesResume
import com.hikari.app.domain.repo.FeedRepository
import com.hikari.app.domain.repo.MusicRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Smart-Downloads: hält für jede Serie, die gerade läuft, die nächsten Folgen
 * auf dem Gerät bereit — und dazu die favorisierten Songs. Läuft nur, wenn die
 * Einstellung an ist UND das Gerät im WLAN hängt (Constraint im Scheduler).
 *
 * Pro Lauf höchstens 5 Downloads je Art — der Worker bleibt überschaubar und
 * lässt anderen Workern Luft.
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
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        // Abgelaufene Downloads räumen — unabhängig davon, ob Smart-Downloads an sind.
        runCatching { retention.run() }

        val enabled = runCatching { settings.smartDownloads.first() }.getOrDefault(true)
        if (!enabled) return Result.success()

        downloadFavoriteSongs()

        downloadNextEpisodes()
        return Result.success()
    }

    /**
     * Für die zuletzt geschauten Serien die Folge, bei der es weitergeht, und
     * die danach aufs Gerät holen. Ist der Server nicht erreichbar, passiert
     * still nichts — der nächste Lauf kommt in 6 h ohnehin.
     */
    private suspend fun downloadNextEpisodes() {
        val library = runCatching { feedRepo.getLibrary() }.getOrNull() ?: return
        val running = library.continueWatching.mapNotNull { it.series_id }.distinct().take(MAX_SERIES)
        var queued = 0
        for (seriesId in running) {
            val series = runCatching { feedRepo.getSeries(seriesId) }.getOrNull() ?: continue
            for (video in SeriesResume.upcoming(series.videos, EPISODES_AHEAD)) {
                if (queued >= MAX_PER_FIRE) return
                if (localDownloads.isDownloaded(video.id)) continue
                val res = localDownloads.download(
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
                )
                if (res.isSuccess) queued += 1
            }
        }
    }

    /**
     * Favorisierte Songs offline vorhalten — die Favoriten liegen lokal in Room,
     * das braucht also keinen erreichbaren Server, nur das Netz für den Stream.
     */
    private suspend fun downloadFavoriteSongs() {
        val favorites = runCatching { musicRepo.getFavorites() }.getOrNull() ?: return
        var queued = 0
        for (song in favorites) {
            if (queued >= MAX_PER_FIRE) break
            if (musicDownloads.isDownloaded(song.videoId)) continue
            if (musicDownloads.download(song).isSuccess) queued += 1
        }
    }

    companion object {
        private const val MAX_PER_FIRE = 5
        /** So viele laufende Serien werden bedient … */
        private const val MAX_SERIES = 3
        /** … und je Serie so viele Folgen vorgehalten (die aktuelle und die nächste). */
        private const val EPISODES_AHEAD = 2
    }
}
