package com.hikari.app.domain.repo

import com.hikari.app.data.db.PlaybackPositionDao
import com.hikari.app.data.db.PlaybackPositionEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackRepository @Inject constructor(private val dao: PlaybackPositionDao) {
    suspend fun getPosition(videoId: String): Long = dao.get(videoId)?.positionMs ?: 0L
    suspend fun savePosition(videoId: String, positionMs: Long) {
        dao.put(PlaybackPositionEntity(videoId, positionMs, System.currentTimeMillis()))
    }
}
