package com.hikari.app.domain.repo

import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.NextVideoDto
import com.hikari.app.data.api.dto.ProgressBody
import com.hikari.app.data.db.LocalDownloadDao
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
    private val downloadDao: LocalDownloadDao,
) {
    /** Lokale Positionen live — Grundlage für Fortschrittsbalken in Downloads. */
    val positions: Flow<List<PlaybackPositionEntity>> = dao.observeAll()

    /** Alle lokalen Positionen auf einmal — Grundlage für „Weiterschauen" ohne Netz. */
    suspend fun allPositions(): List<PlaybackPositionEntity> = dao.getAll()

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

    /**
     * Nächste Folge derselben Serie — null bei 404 (kein Nachfolger). Ohne
     * Server (offline) gilt die nächste Folge, die auf dem Gerät liegt.
     */
    suspend fun nextVideo(videoId: String): NextVideoDto? =
        try {
            api.getNextVideo(videoId)
        } catch (e: HttpException) {
            null
        } catch (e: Exception) {
            localNext(videoId)
        }

    private suspend fun localNext(videoId: String): NextVideoDto? {
        val current = downloadDao.get(videoId) ?: return null
        val seriesId = current.seriesId ?: return null
        val episodes = downloadDao.all()
            .filter { it.seriesId == seriesId }
            .sortedWith(compareBy({ it.season ?: 1 }, { it.episode ?: 0 }))
        val next = episodes.getOrNull(episodes.indexOfFirst { it.videoId == videoId } + 1) ?: return null
        return NextVideoDto(
            id = next.videoId,
            title = next.title,
            season = next.season,
            episode = next.episode,
            thumbnailUrl = next.thumbnailUrl,
        )
    }
}
