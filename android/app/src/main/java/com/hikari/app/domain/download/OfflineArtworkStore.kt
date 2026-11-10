package com.hikari.app.domain.download

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bilder, die zu heruntergeladenen Inhalten gehören, fest auf dem Gerät:
 * Vorschaubilder, Plakate, Serien-Hintergründe, Kanalbilder, Song-Cover.
 *
 * Coils eigener Zwischenspeicher reicht dafür nicht — er ist begrenzt und
 * räumt nach eigenem Ermessen auf. Ohne Netz stünden dann graue Flächen da,
 * wo eben noch Plakate waren. Was hier liegt, bleibt, solange der Download
 * bleibt.
 *
 * Der Schlüssel ist die Bild-Adresse genau so, wie sie in den Daten steht
 * (auch relativ wie "/covers/poster_x.jpg"). Der ImageLoader fragt bei jedem
 * Bild hier nach ([localFile]) — deshalb zeigt jede Ansicht der App die
 * gesicherten Bilder, ohne selbst etwas davon wissen zu müssen.
 */
@Singleton
class OfflineArtworkStore @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    private val dir = File(ctx.filesDir, "offline_art")

    /**
     * true, solange die Bibliothek den Server nicht erreicht (kein Netz oder
     * PC aus). Dann gewinnt das gesicherte Bild sofort, statt erst auf eine
     * Zeitüberschreitung zu warten.
     */
    @Volatile var preferLocal: Boolean = false

    /** Das gesicherte Bild zu [url], falls es eines gibt. */
    fun localFile(url: String?): File? {
        if (url.isNullOrBlank()) return null
        return fileFor(url).takeIf { it.exists() }
    }

    /**
     * Sichert alle [urls], die noch fehlen. Geladen wird über den ImageLoader
     * der App: der kennt Backend-Adresse und Anmeldung — und findet ein Bild
     * auch ohne Netz, solange es noch in seinem Zwischenspeicher liegt.
     * Rückgabe: wie viele Bilder neu gesichert wurden.
     */
    suspend fun pin(urls: Collection<String?>): Int = withContext(Dispatchers.IO) {
        var stored = 0
        for (url in urls.filterNotNull().filter { it.isNotBlank() }.distinct()) {
            val target = fileFor(url)
            if (target.exists()) continue
            runCatching { fetch(url, target) }
                .onSuccess { if (it) stored++ }
                .onFailure { Log.w(TAG, "Bild nicht gesichert: $url", it) }
        }
        stored
    }

    /** Räumt alles weg, was zu keinem der [keep]-Bilder mehr gehört (gelöschte Downloads). */
    suspend fun prune(keep: Collection<String?>) = withContext(Dispatchers.IO) {
        val wanted = keep.filterNotNull().filter { it.isNotBlank() }.mapTo(HashSet()) { fileFor(it).name }
        dir.listFiles()?.forEach { file ->
            if (file.name !in wanted) file.delete()
        }
    }

    private suspend fun fetch(url: String, target: File): Boolean {
        val request = ImageRequest.Builder(ctx)
            .data(url)
            .size(MAX_EDGE, MAX_EDGE)
            // Software-Bitmap: nur die lässt sich in eine Datei schreiben.
            .allowHardware(false)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .build()
        val result = ctx.imageLoader.execute(request)
        if (result !is SuccessResult) {
            Log.d(TAG, "Bild nicht ladbar: $url (${(result as? ErrorResult)?.throwable?.message})")
            return false
        }
        val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return false
        dir.mkdirs()
        // Erst in eine Nebendatei, dann umbenennen — nie liegt ein halbes Bild da.
        val tmp = File(dir, "${target.name}.tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        return tmp.renameTo(target)
    }

    private fun fileFor(url: String): File = File(dir, "${sha1(url)}.jpg")

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val TAG = "HikariArtwork"

        /** Längste Kante in Pixeln — reicht für einen Hintergrund in voller Bildschirmbreite. */
        const val MAX_EDGE = 1280
    }
}
