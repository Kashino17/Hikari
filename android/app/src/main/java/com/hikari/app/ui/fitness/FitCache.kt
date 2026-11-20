package com.hikari.app.ui.fitness

import android.content.Context
import android.webkit.WebResourceResponse
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
            val tmp = File(d, key(path) + ".part")
            tmp.writeBytes(bytes)
            tmp.renameTo(File(d, key(path)))
            File(d, key(path) + ".type").writeText(type)
            trim(d)
        }
    }

    private fun trim(d: File) {
        val files = d.listFiles { f -> !f.name.endsWith(".type") && !f.name.endsWith(".part") && f.name != "queue.json" }?.toList() ?: return
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
        val m = range?.let { Regex("bytes=(\\d+)-(\\d*)").find(it) }
        if (m != null && e.bytes.isNotEmpty()) {
            val start = m.groupValues[1].toLong().coerceAtMost(e.bytes.size - 1L).toInt()
            val end = (m.groupValues[2].toLongOrNull() ?: (e.bytes.size - 1L)).coerceAtMost(e.bytes.size - 1L).toInt()
            headers["Content-Range"] = "bytes $start-$end/${e.bytes.size}"
            headers["Content-Length"] = (end - start + 1).toString()
            return WebResourceResponse(e.type.substringBefore(';'), if (text) "utf-8" else null, 206, "Partial Content", headers,
                ByteArrayInputStream(e.bytes, start, end - start + 1))
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
    )
    fun queueable(path: String) = QUEUEABLE.any { it.matches(path) }

    @Synchronized
    fun enqueue(ctx: Context, path: String, json: String) {
        val f = File(dir(ctx), "queue.json")
        val a = runCatching { JSONArray(f.readText()) }.getOrDefault(JSONArray())
        a.put(JSONObject().put("path", path).put("body", json).put("at", System.currentTimeMillis()))
        f.writeText(a.toString())
    }

    fun pending(ctx: Context): Int = runCatching { JSONArray(File(dir(ctx), "queue.json").readText()).length() }.getOrDefault(0)

    /** Wartende Ergebnisse senden — der Reihe nach, bei der ersten Störung aufhören. → Anzahl gesendet. */
    @Synchronized
    fun flush(ctx: Context, okHttp: OkHttpClient): Int {
        val f = File(dir(ctx), "queue.json")
        if (!f.exists()) return 0
        val a = runCatching { JSONArray(f.readText()) }.getOrDefault(JSONArray())
        var sent = 0
        while (sent < a.length()) {
            val o = a.getJSONObject(sent)
            val ok = runCatching {
                okHttp.newCall(
                    Request.Builder().url("http://hikari.invalid${o.getString("path")}")
                        .post(o.getString("body").toRequestBody("application/json".toMediaTypeOrNull())).build(),
                ).execute().use { it.code < 500 }
            }.getOrDefault(false)
            if (!ok) break
            sent++
        }
        val rest = JSONArray()
        for (i in sent until a.length()) rest.put(a.get(i))
        if (rest.length() == 0) f.delete() else f.writeText(rest.toString())
        return sent
    }
}
