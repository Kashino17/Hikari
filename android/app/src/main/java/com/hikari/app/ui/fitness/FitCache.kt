package com.hikari.app.ui.fitness

import android.content.Context
import android.util.AtomicFile
import android.webkit.WebResourceResponse
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Offline-Training: Server-Antworten des Fitness-Bereichs (Plan, Übungen, Katalog …) und Übungsvideos
 * liegen zusätzlich auf dem Gerät. Ohne Verbindung zum Mac liefert der Proxy den letzten Stand
 * (Kopfzeile X-Hikari-Offline), Videos kommen nach dem ersten Laden immer aus dem Speicher.
 * Ergebnisse, die offline entstehen (Training beendet, Skill, Achtsamkeit …), wartet eine Schlange
 * ab und sendet sie, sobald der Server wieder erreichbar ist.
 */
object FitCache {
    private const val MAX_BYTES = 350L * 1024 * 1024

    private fun dir(ctx: Context) = File(ctx.filesDir, "fit-cache").apply { mkdirs() }
    private fun key(path: String) = MessageDigest.getInstance("SHA-1").digest(path.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Was zwischengespeichert wird: alle GET des Fitness-Bereichs außer erzwungenen Neuberechnungen. */
    fun cacheable(path: String) = path.startsWith("/fitness/") && "force=" !in path && "regen=" !in path
    fun isMedia(path: String) = path.startsWith("/fitness/media/")

    class Entry(val bytes: ByteArray, val type: String)

    fun has(ctx: Context, path: String) = File(dir(ctx), key(path)).exists()

    fun get(ctx: Context, path: String): Entry? {
        val f = File(dir(ctx), key(path))
        val m = File(dir(ctx), key(path) + ".type")
        if (!f.exists()) return null
        f.setLastModified(System.currentTimeMillis())
        return Entry(f.readBytes(), m.takeIf { it.exists() }?.readText() ?: "application/octet-stream")
    }

    fun put(ctx: Context, path: String, type: String, bytes: ByteArray) {
        runCatching {
            val d = dir(ctx)
            // Eigene Zwischendatei je Schreiber (Vorladen und Proxy können gleichzeitig schreiben),
            // Typ zuerst — die Datei selbst erscheint erst vollständig.
            val tmp = File(d, key(path) + "." + java.util.UUID.randomUUID().toString().take(8) + ".part")
            tmp.writeBytes(bytes)
            File(d, key(path) + ".type").writeText(type)
            if (!tmp.renameTo(File(d, key(path)))) tmp.delete()
            trim(d)
        }
    }

    private fun trim(d: File) {
        val files = d.listFiles { f -> !f.name.endsWith(".type") && !f.name.endsWith(".part") && !f.name.startsWith("queue.json") }?.toList() ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_BYTES) return
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= MAX_BYTES * 0.8) break
            total -= f.length()
            f.delete(); File(d, f.name + ".type").delete()
        }
    }

    /** Antwort aus Bytes, mit Teilbereich (Videos fragen in Stücken an). */
    fun serve(e: Entry, range: String?, offline: Boolean): WebResourceResponse {
        val headers = mutableMapOf("Cache-Control" to "no-store", "Accept-Ranges" to "bytes")
        if (offline) headers["X-Hikari-Offline"] = "1"
        val text = "json" in e.type || e.type.startsWith("text/")
        val m = range?.let { Regex("bytes=(\\d*)-(\\d*)").find(it) }
        val size = e.bytes.size.toLong()
        if (m != null && size > 0 && (m.groupValues[1].isNotEmpty() || m.groupValues[2].isNotEmpty())) {
            val a = m.groupValues[1].toLongOrNull()
            val b = m.groupValues[2].toLongOrNull()
            // »bytes=-N« = die letzten N Bytes
            val start = (if (a == null) size - (b ?: 0) else a).coerceAtLeast(0)
            val end = (if (a == null || b == null) size - 1 else b).coerceAtMost(size - 1)
            if (start >= size || end < start) {
                return WebResourceResponse(e.type.substringBefore(';'), null, 416, "Range Not Satisfiable",
                    mapOf("Content-Range" to "bytes */$size", "Cache-Control" to "no-store"), ByteArrayInputStream(ByteArray(0)))
            }
            headers["Content-Range"] = "bytes $start-$end/${e.bytes.size}"
            headers["Content-Length"] = (end - start + 1).toString()
            return WebResourceResponse(e.type.substringBefore(';'), if (text) "utf-8" else null, 206, "Partial Content", headers,
                ByteArrayInputStream(e.bytes, start.toInt(), (end - start + 1).toInt()))
        }
        headers["Content-Length"] = e.bytes.size.toString()
        return WebResourceResponse(e.type.substringBefore(';'), if (text) "utf-8" else null, 200, "OK", headers, ByteArrayInputStream(e.bytes))
    }

    /** Im Hintergrund vorladen (Pfade aus der Oberfläche: heutiger Plan + seine Übungsvideos). */
    fun prefetch(ctx: Context, okHttp: OkHttpClient, paths: List<String>) {
        for (p in paths) {
            if (!cacheable(p) || (isMedia(p) && File(dir(ctx), key(p)).exists())) continue
            runCatching {
                okHttp.newCall(Request.Builder().url("http://hikari.invalid$p").build()).execute().use { r ->
                    if (r.isSuccessful) put(ctx, p, r.header("Content-Type") ?: "application/octet-stream", r.body?.bytes() ?: return@use)
                }
            }
        }
    }

    // ── Warteschlange für Ergebnisse, die offline entstehen ──
    private val QUEUEABLE = listOf(
        Regex("^/fitness/session/[^/]+/(start|finish)$"), Regex("^/fitness/skills/[^/]+/log$"),
        Regex("^/fitness/mind/log$"), Regex("^/fitness/body$"), Regex("^/fitness/health$"), Regex("^/fitness/test$"),
        Regex("^/fitness/benchmark/[^/]+$"), Regex("^/fitness/body/measure$"),
    )
    fun queueable(path: String) = QUEUEABLE.any { it.matches(path) }

    private val lock = Any()
    private val flushing = AtomicBoolean(false)
    private fun qFile(ctx: Context) = AtomicFile(File(dir(ctx), "queue.json"))
    private fun readQ(ctx: Context): JSONArray =
        runCatching { JSONArray(String(qFile(ctx).readFully())) }.getOrDefault(JSONArray())

    /** Atomar schreiben: ein Absturz mitten im Schreiben lässt die alte Schlange heil. */
    private fun writeQ(ctx: Context, a: JSONArray) {
        val f = qFile(ctx)
        if (a.length() == 0) { f.delete(); return }
        val out = f.startWrite()
        try {
            out.write(a.toString().toByteArray())
            f.finishWrite(out)
        } catch (e: Exception) {
            f.failWrite(out)
        }
    }

    /** Kennung je Ergebnis — der Server wertet dieselbe Kennung nur einmal (auch wenn sie zweimal ankommt). */
    private fun reqOf(o: JSONObject): String =
        o.optString("req").ifBlank { "old-${o.optLong("at")}-${o.optString("path").hashCode()}" }

    fun enqueue(ctx: Context, path: String, json: String, req: String, at: Long) {
        synchronized(lock) {
            val a = readQ(ctx)
            a.put(JSONObject().put("path", path).put("body", json).put("at", at).put("req", req))
            writeQ(ctx, a)
        }
        scheduleFlush(ctx)
    }

    fun pending(ctx: Context): Int = synchronized(lock) { readQ(ctx).length() }

    /** Nachsenden im Hintergrund, sobald Netz da ist — auch wenn die Fitness-Oberfläche zu ist. */
    fun scheduleFlush(ctx: Context) {
        runCatching {
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                "fit-queue-flush", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<FitFlushWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 2, TimeUnit.MINUTES)
                    .build(),
            )
        }
    }

    /**
     * Wartende Ergebnisse senden — der Reihe nach. Jeder gesendete Eintrag verlässt die Schlange sofort
     * (ein Abbruch mittendrin schickt nichts doppelt). Keine Verbindung → aufhören. Server-Fehler (5xx)
     * → später noch einmal, nach 8 Versuchen verwerfen, damit ein kaputter Eintrag nicht alles blockiert.
     * Das Netz läuft außerhalb der Sperre, Einreihen wartet also nie auf eine langsame Verbindung.
     */
    fun flush(ctx: Context, okHttp: OkHttpClient): Int {
        if (!flushing.compareAndSet(false, true)) return 0
        var sent = 0
        try {
            while (true) {
                val o = synchronized(lock) { readQ(ctx).optJSONObject(0) } ?: break
                val req = reqOf(o)
                val code = runCatching {
                    okHttp.newCall(
                        Request.Builder().url("http://hikari.invalid${o.getString("path")}")
                            .header("X-Hikari-Req", req)
                            .header("X-Hikari-At", o.optLong("at").toString())
                            .post(o.getString("body").toRequestBody("application/json".toMediaTypeOrNull())).build(),
                    ).execute().use { it.code }
                }.getOrDefault(-1)
                if (code < 0) break
                var stop = false
                synchronized(lock) {
                    val a = readQ(ctx)
                    val first = a.optJSONObject(0)
                    if (first != null && reqOf(first) == req) {
                        if (code >= 500 && first.optInt("tries") + 1 < 8) {
                            first.put("tries", first.optInt("tries") + 1)
                            stop = true
                        } else a.remove(0)
                        writeQ(ctx, a)
                    }
                }
                if (stop) break
                if (code < 500) sent++
            }
        } finally {
            flushing.set(false)
        }
        return sent
    }
}

@HiltWorker
class FitFlushWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val okHttp: OkHttpClient,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { FitCache.flush(context, okHttp) }
        // Mac noch nicht erreichbar → später erneut (WorkManager wartet immer länger).
        return if (FitCache.pending(context) > 0 && runAttemptCount < 30) Result.retry() else Result.success()
    }
}
