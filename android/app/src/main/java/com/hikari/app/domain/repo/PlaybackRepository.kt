package com.hikari.app.domain.repo

import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.ProgressBody
import com.hikari.app.data.db.PlaybackPositionDao
import com.hikari.app.data.db.PlaybackPositionEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackRepository @Inject constructor(
    private val dao: PlaybackPositionDao,
    private val api: HikariApi,
) {
    suspend fun getPosition(videoId: String): Long = dao.get(videoId)?.positionMs ?: 0L

    /**
     * Persist locally for instant resume (Room) AND push to backend so
     * /library Continue-Watching populates. Backend write is best-effort —
     * local save is the authoritative one for the player.
     */
    suspend fun savePosition(videoId: String, positionMs: Long) {
        dao.put(PlaybackPositionEntity(videoId, positionMs, System.currentTimeMillis()))
        runCatching { api.setProgress(videoId, ProgressBody(seconds = positionMs / 1000f)) }
    }
}
