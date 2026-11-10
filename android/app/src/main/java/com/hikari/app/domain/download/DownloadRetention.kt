package com.hikari.app.domain.download

import com.hikari.app.data.db.LocalDownloadDao
import com.hikari.app.data.db.LocalDownloadSource
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.player.VideoDockManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Wählbare Aufbewahrungsfristen; 0 Tage heißt „für immer“. */
val RETENTION_DAY_CHOICES = listOf(0, 7, 14, 30, 60)

fun retentionLabel(days: Int): String = when (days) {
    0 -> "Für immer"
    60 -> "2 Monate"
    else -> "$days Tage"
}

/** Reine Regel — getrennt vom Aufräumen, damit sie für sich testbar bleibt. */
object DownloadRetentionPolicy {
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /**
     * Ist ein Download abgelaufen? Gezählt wird ab dem Tag des Herunterladens.
     * [retentionDays] = 0 bedeutet: nie löschen.
     */
    fun isExpired(downloadedAt: Long, retentionDays: Int, now: Long): Boolean =
        retentionDays > 0 && now - downloadedAt >= retentionDays * DAY_MS
}

/**
 * Räumt abgelaufene Video-Downloads vom Gerät — getrennt nach selbst geladenen
 * und Smart-Downloads, jeweils mit eigener Frist aus den Einstellungen.
 * Das gerade laufende Video bleibt immer liegen.
 */
@Singleton
class DownloadRetentionCleaner @Inject constructor(
    private val dao: LocalDownloadDao,
    private val downloads: LocalDownloadManager,
    private val settings: SettingsStore,
    private val dock: VideoDockManager,
) {
    /** Löscht, was abgelaufen ist; liefert die Anzahl der entfernten Downloads. */
    suspend fun run(now: Long = System.currentTimeMillis()): Int {
        val manualDays = settings.manualDownloadRetentionDays.first()
        val smartDays = settings.smartDownloadRetentionDays.first()
        if (manualDays == 0 && smartDays == 0) return 0
        val playing = dock.activeVideo.value?.videoId
        var removed = 0
        for (entity in dao.all()) {
            if (entity.videoId == playing) continue
            val days = if (entity.source == LocalDownloadSource.SMART.name) smartDays else manualDays
            if (DownloadRetentionPolicy.isExpired(entity.downloadedAt, days, now)) {
                downloads.delete(entity.videoId)
                removed++
            }
        }
        return removed
    }
}
