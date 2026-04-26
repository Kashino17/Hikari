package com.hikari.app.domain.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.repo.FeedRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Periodic background sync that downloads any "saved" video that isn't yet
 * locally available. Runs only when the user's Smart-Downloads preference is
 * on AND the device is on an unmetered network (constraint set in the
 * scheduler).
 *
 * Limits per fire: at most 5 downloads — keeps the worker bounded and lets
 * other Workers run.
 */
@HiltWorker
class SmartDownloadWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val feedRepo: FeedRepository,
    private val localDownloads: LocalDownloadManager,
    private val settings: SettingsStore,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val enabled = runCatching { settings.smartDownloads.first() }.getOrDefault(true)
        if (!enabled) return Result.success()

        val saved = runCatching { feedRepo.fetchSaved() }.getOrNull() ?: return Result.retry()

        var queued = 0
        for (item in saved) {
            if (queued >= MAX_PER_FIRE) break
            if (localDownloads.isDownloaded(item.videoId)) continue
            val res = localDownloads.download(
                videoId = item.videoId,
                durationSeconds = item.durationSeconds,
            )
            if (res.isSuccess) queued += 1
        }
        return Result.success()
    }

    companion object {
        private const val MAX_PER_FIRE = 5
    }
}
