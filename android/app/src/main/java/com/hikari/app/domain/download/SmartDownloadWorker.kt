package com.hikari.app.domain.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hikari.app.data.db.LocalDownloadKind
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.repo.FeedRepository
import com.hikari.app.domain.repo.MusicRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Periodic background sync that downloads any "saved" video that isn't yet
 * locally available, plus favorited songs. Runs only when the user's
 * Smart-Downloads preference is on AND the device is on an unmetered network
 * (constraint set in the scheduler).
 *
 * Limits per fire: at most 5 downloads each — keeps the worker bounded and lets
 * other Workers run.
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
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val enabled = runCatching { settings.smartDownloads.first() }.getOrDefault(true)
        if (!enabled) return Result.success()

        downloadFavoriteSongs()

        // Backend down or fetch-saved errors → skip this fire silently. Next
        // periodic run is in 6h anyway. Result.retry() here would trigger
        // exponential backoff that hammers the backend during outages —
        // reserved for genuinely-transient cases where a sooner re-attempt
        // actually helps.
        val saved = runCatching { feedRepo.fetchSaved() }.getOrNull() ?: return Result.success()

        var queued = 0
        for (item in saved) {
            if (queued >= MAX_PER_FIRE) break
            if (localDownloads.isDownloaded(item.videoId)) continue
            // Smart-Downloads ziehen "Saved"-Feed-Items — die haben Channel-Bezug,
            // aber keinen Series-Kontext. Daher CHANNEL als kind.
            val res = localDownloads.download(
                LocalDownloadMetadata(
                    videoId = item.videoId,
                    kind = LocalDownloadKind.CHANNEL,
                    title = item.title,
                    durationSeconds = item.durationSeconds,
                    thumbnailUrl = item.thumbnailUrl,
                    channelTitle = item.channelTitle.ifBlank { null },
                ),
            )
            if (res.isSuccess) queued += 1
        }
        return Result.success()
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
    }
}
