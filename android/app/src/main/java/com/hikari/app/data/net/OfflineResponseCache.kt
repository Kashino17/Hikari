package com.hikari.app.data.net

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Merkt sich die letzte erfolgreiche Antwort des Macs je Adresse und liefert sie, wenn kein Netz da ist.
 * So sehen Bibliothek, Reihen-Seiten und Titelbilder offline genauso aus wie online — keine Ansicht muss
 * davon wissen. Nur ausdrücklich genannte Pfade (keine Leser-Seiten, keine Downloads, keine Abgleiche).
 */
class OfflineResponseCache(
    private val dir: File,
    private val paths: List<Regex>,
    private val maxBytes: Long = 150L * 1024 * 1024,
) {
    companion object {
        /** Daten der Lesen-Bereiche (Manga und Bücher). */
        val API_PATHS = listOf(
            Regex("^/api/manga/series$"),
            Regex("^/api/manga/series/[^/]+$"),
            Regex("^/api/manga/continue$"),
            Regex("^/api/manga/catalog/(home|list)$"),
            Regex("^/api/ebooks/(home|list)$"),
            Regex("^/api/ebooks/(?!state)[^/]+$"),
        )

        /** Titelbilder und Porträts. */
        val IMAGE_PATHS = listOf(
            Regex("^/api/manga/cover/[^/]+$"),
            Regex("^/api/manga/catalog/[^/]+/cover$"),
            Regex("^/api/ebooks/[^/]+/cover$"),
            Regex("^/api/ebooks/author-photo$"),
        )
    }

    init { dir.mkdirs() }

    fun covers(path: String): Boolean = paths.any { it.matches(path) }

    private fun key(path: String, query: String?): String {
        val raw = if (query.isNullOrEmpty()) path else "$path?$query"
        return MessageDigest.getInstance("SHA-1").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun interceptor(): Interceptor = Interceptor { chain ->
        val req = chain.request()
        val path = req.url.encodedPath
        if (req.method != "GET" || !covers(path)) return@Interceptor chain.proceed(req)
        val k = key(path, req.url.encodedQuery)
        val body = File(dir, "$k.body")
        val type = File(dir, "$k.type")
        try {
            val resp = chain.proceed(req)
            if (!resp.isSuccessful) return@Interceptor resp
            val bytes = resp.body.bytes()
            runCatching {
                File(dir, "$k.tmp").apply { writeBytes(bytes) }.renameTo(body)
                type.writeText(resp.header("Content-Type") ?: "")
                trim()
            }
            resp.newBuilder().body(bytes.toResponseBody(resp.body.contentType())).build()
        } catch (e: IOException) {
            if (!body.exists()) throw e
            body.setLastModified(System.currentTimeMillis())
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK (offline)")
                .header("X-Hikari-Offline", "1")
                .body(body.readBytes().toResponseBody(type.takeIf { it.exists() }?.readText()?.toMediaTypeOrNull()))
                .build()
        }
    }

    /** Älteste Einträge weg, wenn der Speicher zu voll wird. */
    private fun trim() {
        val files = dir.listFiles { f -> f.name.endsWith(".body") } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= maxBytes * 0.8) break
            total -= f.length()
            f.delete()
            File(dir, f.name.removeSuffix(".body") + ".type").delete()
        }
    }
}
