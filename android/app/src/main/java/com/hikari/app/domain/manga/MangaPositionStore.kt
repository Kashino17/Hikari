package com.hikari.app.domain.manga

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Wo du in jeder Manga-Reihe stehst — auf dem Handy, sofort bei jedem Umblättern gespeichert.
 * Hält auch, wenn die App beendet wird oder kein Netz da ist. `synced` = der Mac kennt diesen Stand.
 * Abgleich mit dem Mac: der neueste Stand (Zeitstempel) gewinnt, in beide Richtungen.
 */
class MangaPositionStore(private val storage: Storage) {
    interface Storage {
        fun read(): String?
        fun write(json: String)
    }

    @Serializable
    data class Pos(val chapterId: String, val page: Int, val at: Long, val synced: Boolean = false)

    private val json = Json { ignoreUnknownKeys = true }
    private val mapSerializer: KSerializer<Map<String, Pos>> = MapSerializer(String.serializer(), Pos.serializer())
    private val lock = Any()
    private var cache: MutableMap<String, Pos>? = null

    private fun all(): MutableMap<String, Pos> = cache ?: (
        runCatching { json.decodeFromString(mapSerializer, storage.read() ?: "{}") }.getOrNull()?.toMutableMap()
            ?: mutableMapOf()
        ).also { cache = it }

    private fun save() = storage.write(json.encodeToString(mapSerializer, cache ?: emptyMap()))

    fun get(seriesId: String): Pos? = synchronized(lock) { all()[seriesId] }

    /** Gelesen: sofort merken (noch nicht beim Mac). */
    fun put(seriesId: String, chapterId: String, page: Int, at: Long = System.currentTimeMillis()) = synchronized(lock) {
        all()[seriesId] = Pos(chapterId, page, at, synced = false)
        save()
    }

    /** Der Mac hat den Stand mit diesem Zeitstempel bekommen — nur dann gilt er als abgeglichen. */
    fun markSynced(seriesId: String, at: Long) = synchronized(lock) {
        val p = all()[seriesId] ?: return@synchronized
        if (p.at == at && !p.synced) {
            all()[seriesId] = p.copy(synced = true)
            save()
        }
    }

    /** Stand vom Mac übernehmen, wenn er neuer ist als der auf dem Handy. */
    fun mergeServer(seriesId: String, chapterId: String, page: Int, at: Long) = synchronized(lock) {
        val p = all()[seriesId]
        if (p == null || at > p.at) {
            all()[seriesId] = Pos(chapterId, page, at, synced = true)
            save()
        }
    }

    /** Was der Mac noch nicht kennt (offline gelesen). */
    fun pending(): Map<String, Pos> = synchronized(lock) { all().filterValues { !it.synced } }
}

@Singleton
class MangaPositions @Inject constructor(@ApplicationContext ctx: Context) {
    private val prefs = ctx.getSharedPreferences("hikari_manga_positions", Context.MODE_PRIVATE)
    val store = MangaPositionStore(object : MangaPositionStore.Storage {
        override fun read(): String? = prefs.getString("positions", null)
        // commit statt apply: auch beim harten Beenden der App direkt auf der Platte.
        override fun write(json: String) { prefs.edit().putString("positions", json).commit() }
    })
}
