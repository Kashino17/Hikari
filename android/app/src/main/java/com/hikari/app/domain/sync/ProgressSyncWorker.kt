package com.hikari.app.domain.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hikari.app.domain.repo.PlaybackRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Überträgt lokal gespeicherte Wiedergabepositionen ans Backend. Läuft nur mit
 * Netzverbindung, damit ein offline gesehener Stand automatisch nachzieht,
 * sobald das Gerät wieder online ist.
 */
@HiltWorker
class ProgressSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val playbackRepo: PlaybackRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        if (playbackRepo.syncPending()) Result.success() else Result.retry()
}
