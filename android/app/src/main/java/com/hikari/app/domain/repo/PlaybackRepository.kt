package com.hikari.app.domain.repo

import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.NextVideoDto
import com.hikari.app.data.api.dto.ProgressBody
import com.hikari.app.data.db.PlaybackPositionDao
import com.hikari.app.data.db.PlaybackPositionEntity
import com.hikari.app.domain.sync.ProgressSyncScheduler
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import retrofit2.HttpException

@Singleton
class PlaybackRepository @Inject constructor(
    private val dao: PlaybackPositionDao,
    private val api: HikariApi,
    private val syncScheduler: ProgressSyncScheduler,
) {
    /** Lokale Positionen live — Grundlage für Fortschrittsbalken in Downloads. */
    val positions: Flow<List<PlaybackPositionEntity>> = dao.observeAll()

    suspend fun getPosition(videoId: String): Long = dao.get(videoId)?.positionMs ?: 0L

    /** Liefert alle Wiedergaben der letzten X Millisekunden (z.B. 30 Tage für Top 10 Charts). */
    suspend fun getRecentPositions(sinceMs: Long): List<PlaybackPositionEntity> = dao.getRecentSince(sinceMs)

    /**
     * Persistiert lokal für sofortiges Resume (Room) und versucht den Push ans
     * Backend. Schlägt er fehl (offline), bleibt die Position als ausstehend
     * markiert und wird vom [ProgressSyncWorker] nachgezogen, sobald wieder
     * Netz da ist. Lokal ist immer die authoritative Quelle für den Player.
     */
    suspend fun savePosition(videoId: String, positionMs: Long) {
        val now = System.currentTimeMillis()
        val previous = dao.get(videoId)
        dao.put(
            PlaybackPositionEntity(
                videoId = videoId,
                positionMs = positionMs,
                updatedAt = now,
                syncedPositionMs = previous?.syncedPositionMs ?: -1L,
                syncedAt = previous?.syncedAt ?: 0L,
            ),
        )
        if (push(videoId, positionMs)) {
            dao.markSynced(videoId, positionMs, now)
        } else {
            syncScheduler.enqueueRetry()
        }
    }

    /**
     * Überträgt alle noch ausstehenden Positionen. Rückgabe `false`, wenn
     * mindestens eine Übertragung fehlgeschlagen ist (Worker → retry).
     */
    suspend fun syncPending(): Boolean = withContext(Dispatchers.IO) {
        var allSucceeded = true
        for (entry in dao.getPending()) {
            if (push(entry.videoId, entry.positionMs)) {
                dao.markSynced(entry.videoId, entry.positionMs, System.currentTimeMillis())
            } else {
                allSucceeded = false
            }
        }
        allSucceeded
    }

    private suspend fun push(videoId: String, positionMs: Long): Boolean = try {
        api.setProgress(videoId, ProgressBody(seconds = positionMs / 1000f))
        true
    } catch (e: HttpException) {
        // 4xx (außer Timeout/Rate-Limit) ist endgültig — z. B. ein längst
        // gelöschtes Video. Solche Einträge dürfen die Queue nicht ewig
        // blockieren.
        e.code() in 400..499 && e.code() != 408 && e.code() != 429
    } catch (e: Exception) {
        false
    }

    /** Nächste Folge derselben Serie — null bei 404 (kein Nachfolger) oder Fehler. */
    suspend fun nextVideo(videoId: String): NextVideoDto? =
        runCatching { api.getNextVideo(videoId) }.getOrNull()
}
