package com.hikari.app.domain.ebooks

import java.io.File
import java.io.InputStream

/** Bücher und Titelbilder auf dem Handy. Schreibt atomar: erst .part, dann umbenennen. */
class EbookFileStore(private val dir: File) {
    fun bookFile(id: String, format: String): File = File(dir, "${safe(id)}.$format")
    fun coverFile(id: String): File = File(File(dir, "covers"), "${safe(id)}.img")

    /** Schreibt [input] nach [target]; bei Fehler bleibt nichts zurück. Liefert die Byte-Zahl. */
    fun writeAtomic(target: File, input: InputStream, total: Long, onProgress: (Float) -> Unit): Long {
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, "${target.name}.part")
        try {
            var done = 0L
            part.outputStream().use { out ->
                input.use { inp ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = inp.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (!part.renameTo(target)) {
                target.delete()
                if (!part.renameTo(target)) error("Datei ließ sich nicht ablegen")
            }
            return done
        } catch (t: Throwable) {
            part.delete()
            throw t
        }
    }

    fun delete(id: String, format: String) {
        bookFile(id, format).delete()
        coverFile(id).delete()
    }

    private fun safe(id: String) = id.replace(Regex("[^A-Za-z0-9_-]"), "_").take(150)
}
