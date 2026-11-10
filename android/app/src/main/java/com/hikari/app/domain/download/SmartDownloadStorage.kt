package com.hikari.app.domain.download

import com.hikari.app.data.db.LocalDownloadDao
import com.hikari.app.data.db.LocalDownloadSource
import com.hikari.app.data.db.LocalMusicDownloadDao
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.player.VideoDockManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Wählbare Budgets für Smart-Downloads in GB. */
val SMART_BUDGET_GB_CHOICES = listOf(2, 5, 10, 20, 50)

/** Ein Smart-Download auf dem Gerät — Video oder Song. */
data class SmartItem(val id: String, val bytes: Long, val downloadedAt: Long, val isSong: Boolean)

/** Reine Auswahlregel, getrennt vom Löschen — für sich testbar. */
object SmartBudgetPolicy {
    /**
     * Welche Smart-Downloads weichen, damit wieder Platz im Budget ist: nur
     * solche, die nicht mehr gebraucht werden ([wanted]) und nicht gerade
     * laufen, die ältesten zuerst — und nur so viele wie nötig. Was noch
     * gebraucht wird, bleibt liegen; dann wird eben nichts Neues geladen.
     */
    fun toEvict(items: List<SmartItem>, budgetBytes: Long, wanted: Set<String>, playing: String?): List<SmartItem> {
        var used = items.sumOf { it.bytes }
        if (used < budgetBytes) return emptyList()
        val out = mutableListOf<SmartItem>()
        for (item in items.sortedBy { it.downloadedAt }) {
            if (used < budgetBytes) break
            if (item.id in wanted || item.id == playing) continue
            out += item
            used -= item.bytes
        }
        return out
    }
}

/**
 * Speicher der Smart-Downloads: wie viel sie belegen, ob noch Platz im
 * eingestellten Budget ist, und Platz schaffen. Zählt Videos und Songs.
 */
@Singleton
class SmartDownloadStorage @Inject constructor(
    private val videoDao: LocalDownloadDao,
    private val musicDao: LocalMusicDownloadDao,
    private val videos: LocalDownloadManager,
    private val music: LocalMusicDownloadManager,
    private val settings: SettingsStore,
    private val dock: VideoDockManager,
) {
    private suspend fun items(): List<SmartItem> {
        val smart = LocalDownloadSource.SMART.name
        return videoDao.all().filter { it.source == smart }
            .map { SmartItem(it.videoId, it.byteSize, it.downloadedAt, isSong = false) } +
            musicDao.getAll().filter { it.source == smart }
                .map { SmartItem(it.videoId, it.byteSize, it.downloadedAt, isSong = true) }
    }

    private suspend fun budgetBytes(): Long = settings.smartBudgetGb.first() * GB

    /**
     * Ist Platz für einen weiteren Smart-Download? Wenn das Budget voll ist,
     * werden zuerst nicht mehr gebrauchte Smart-Downloads entfernt.
     */
    suspend fun makeRoom(wanted: Set<String>): Boolean {
        val budget = budgetBytes()
        val current = items()
        val evict = SmartBudgetPolicy.toEvict(current, budget, wanted, dock.activeVideo.value?.videoId)
        evict.forEach { delete(it) }
        return current.sumOf { it.bytes } - evict.sumOf { it.bytes } < budget
    }

    /** Entfernt alle Smart-Downloads vom Gerät; liefert die frei gewordenen Bytes. */
    suspend fun clearAll(): Long {
        val playing = dock.activeVideo.value?.videoId
        val all = items().filter { it.id != playing }
        all.forEach { delete(it) }
        return all.sumOf { it.bytes }
    }

    private suspend fun delete(item: SmartItem) {
        if (item.isSong) music.delete(item.id) else videos.delete(item.id)
    }

    private companion object {
        const val GB = 1024L * 1024 * 1024
    }
}
