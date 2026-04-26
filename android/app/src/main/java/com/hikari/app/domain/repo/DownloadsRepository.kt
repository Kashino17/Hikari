package com.hikari.app.domain.repo

import com.hikari.app.data.api.dto.DownloadsResponse
import com.hikari.app.data.db.LocalDownloadDao
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.download.OfflineDownloadsBuilder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Liefert den Downloads-View des Profile-Tabs. Online: Server-Response.
 * Offline (oder Server-Fehler): aus den lokal vorhandenen Downloads
 * rekonstruiert. So funktioniert die Seite immer und zeigt offline genau die
 * Inhalte, die wirklich auf dem Gerät liegen.
 */
@Singleton
class DownloadsRepository @Inject constructor(
    private val feedRepo: FeedRepository,
    private val localDao: LocalDownloadDao,
    private val settings: SettingsStore,
) {
    suspend fun load(): DownloadsResponse {
        return runCatching { feedRepo.getDownloads() }
            .onSuccess { settings.setDownloadsLimitBytes(it.limit_bytes) }
            .getOrElse { buildOffline() }
    }

    suspend fun buildOffline(): DownloadsResponse {
        val entities = localDao.observeAll().first()
        val limit = settings.downloadsLimitBytes.first()
        return OfflineDownloadsBuilder.build(entities, limitBytes = limit)
    }

    suspend fun localCount(): Int = localDao.observeAll().first().size
}
