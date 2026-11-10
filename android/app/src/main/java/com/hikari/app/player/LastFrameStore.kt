package com.hikari.app.player

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hält pro Video das zuletzt gesehene Bild als kleine JPEG-Datei fest. Die
 * Bibliothek zeigt es bei „Weiterschauen" — auch für gestreamte YouTube-Videos,
 * von denen der Server keine Datei hat, aus der er ein Standbild holen könnte.
 */
@Singleton
class LastFrameStore @Inject constructor(
    @ApplicationContext ctx: Context,
) {
    private val dir = File(ctx.filesDir, "last_frames")

    /** Das gespeicherte Standbild, falls es eines gibt. */
    fun frameFile(videoId: String): File? = fileFor(videoId).takeIf { it.exists() }

    /** Schreibt [frame] als Standbild für [videoId]; aufrufen von einem Hintergrund-Thread. */
    fun save(videoId: String, frame: Bitmap) {
        runCatching {
            dir.mkdirs()
            // Erst in eine Nebendatei, dann umbenennen — die Bibliothek liest nie ein halbes Bild.
            val tmp = File(dir, "${safe(videoId)}.tmp")
            tmp.outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            tmp.renameTo(fileFor(videoId))
            prune()
        }
    }

    private fun prune() {
        val files = dir.listFiles { f -> f.name.endsWith(".jpg") } ?: return
        if (files.size <= MAX_FRAMES) return
        files.sortedBy { it.lastModified() }.take(files.size - MAX_FRAMES).forEach { it.delete() }
    }

    private fun fileFor(videoId: String) = File(dir, "${safe(videoId)}.jpg")

    private fun safe(videoId: String) = videoId.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private companion object {
        const val MAX_FRAMES = 120
    }
}
