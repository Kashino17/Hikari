package com.hikari.app.domain.ebooks

import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.EbookDetailDto
import com.hikari.app.data.api.dto.EbookHomeDto
import com.hikari.app.data.api.dto.EbookListDto
import com.hikari.app.data.db.EbookStateDao
import com.hikari.app.data.db.EbookStateEntity
import com.hikari.app.data.db.LocalEbookDao
import com.hikari.app.data.db.LocalEbookEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

data class EbookFilter(
    val q: String? = null,
    val category: String? = null,
    val source: String? = null,
    val format: String? = null,
    val length: String? = null,
    val state: String? = null,
    val sort: String = "beliebt",
)

/**
 * E-Books: Katalog vom Mac, geladene Bücher und Lesestand auf dem Handy.
 * Der Lesestand wird immer zuerst lokal gespeichert (auch ohne Netz) und dann mit dem Mac
 * zusammengeführt — so geht auf keiner Seite etwas verloren.
 */
class EbookRepository(
    private val api: HikariApi,
    private val local: LocalEbookDao,
    private val states: EbookStateDao,
    private val files: EbookFileStore,
    private val json: Json,
) {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val stateLock = Mutex()

    suspend fun home(): Result<EbookHomeDto> = runCatching { api.getEbookHome() }

    suspend fun list(filter: EbookFilter, offset: Int, limit: Int = 30): Result<EbookListDto> = runCatching {
        api.listEbooks(
            q = filter.q?.takeIf { it.isNotBlank() }, category = filter.category, source = filter.source,
            format = filter.format, length = filter.length, state = filter.state, sort = filter.sort,
            offset = offset, limit = limit,
        )
    }

    suspend fun detail(id: String): Result<EbookDetailDto> = runCatching { api.getEbook(id) }

    fun local(): Flow<List<LocalEbookEntity>> = local.observeAll()

    suspend fun localBook(id: String): LocalEbookEntity? = local.get(id)?.takeIf { java.io.File(it.filePath).exists() }

    /** Lädt das Buch aufs Handy; gleichzeitige Aufrufe für dasselbe Buch laden nur einmal. */
    suspend fun download(id: String, onProgress: (Float) -> Unit): Result<LocalEbookEntity> =
        locks.getOrPut(id) { Mutex() }.withLock {
            localBook(id)?.let { return@withLock Result.success(it) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val d = api.getEbook(id)
                    val res = api.downloadEbookFile(id)
                    val body = res.body()
                    if (!res.isSuccessful || body == null) {
                        error(if (res.code() == 410) "Kein ganzes Buch — es wurde aus dem Katalog genommen." else "Laden fehlgeschlagen (${res.code()})")
                    }
                    val target = files.bookFile(id, d.format)
                    val bytes = files.writeAtomic(target, body.byteStream(), body.contentLength(), onProgress)
                    val cover = if (d.coverUrl != null) runCatching {
                        val c = api.downloadEbookCover(id).body() ?: return@runCatching null
                        files.coverFile(id).also { files.writeAtomic(it, c.byteStream(), c.contentLength()) {} }.absolutePath
                    }.getOrNull() else null
                    val e = LocalEbookEntity(
                        id = id, title = d.title, authors = d.authors.joinToString(", "), format = d.format,
                        coverPath = cover, filePath = target.absolutePath, bytes = bytes, addedAt = System.currentTimeMillis(),
                    )
                    local.upsert(e)
                    e
                }
            }
        }

    suspend fun removeDownload(id: String) {
        val e = local.get(id) ?: return
        files.delete(id, e.format)
        local.delete(id)
    }

    // ── Lesestand ────────────────────────────────────────────────────────────

    fun state(id: String): Flow<BookState> = states.observe(id).map { it?.let(::decode) ?: BookState() }

    /** Ändert den Lesestand lokal (sofort) und versucht, ihn zum Mac zu schicken. */
    suspend fun updateState(id: String, transform: (BookState) -> BookState) {
        updateStateLocal(id, transform)
        runCatching { push(id) }
    }

    suspend fun updateStateLocal(id: String, transform: (BookState) -> BookState) = stateLock.withLock {
        val now = System.currentTimeMillis()
        val cur = states.get(id)?.let(::decode) ?: BookState()
        states.upsert(EbookStateEntity(id, json.encodeToString(BookState.serializer(), transform(cur)), dirty = true, updatedAt = now))
    }

    /** Schickt Ungesendetes, holt dann alles vom Mac und führt es zusammen. */
    suspend fun syncStates() {
        for (row in states.dirty()) runCatching { push(row.bookId) }
        val remote = api.getEbookStates()
        stateLock.withLock {
            for ((id, theirs) in remote) {
                val mine = states.get(id)
                val merged = if (mine == null) theirs else mergeState(decode(mine), theirs)
                states.upsert(
                    EbookStateEntity(id, json.encodeToString(BookState.serializer(), merged), dirty = mine?.dirty == true, updatedAt = System.currentTimeMillis()),
                )
            }
        }
    }

    private suspend fun push(id: String) {
        val mine = states.get(id) ?: return
        val merged = api.putEbookState(id, decode(mine))
        stateLock.withLock {
            val now = states.get(id)
            // Hat sich der Stand während des Sendens geändert, bleibt er schmutzig.
            val changed = now != null && now.json != mine.json
            val base = if (changed) mergeState(merged, decode(now!!)) else merged
            states.upsert(EbookStateEntity(id, json.encodeToString(BookState.serializer(), base), dirty = changed, updatedAt = System.currentTimeMillis()))
        }
    }

    private fun decode(e: EbookStateEntity): BookState =
        runCatching { json.decodeFromString(BookState.serializer(), e.json) }.getOrDefault(BookState())
}
