package com.hikari.app.whatsapp

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import com.hikari.app.domain.whatsapp.WhatsAppRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Sichert jede Datei, die WhatsApp herunterlädt, in dem Moment, in dem sie fertig ist.
 *
 * Warum: Gemessen am 20.11. lädt WhatsApp Bilder teils erst Minuten nach der Benachrichtigung
 * und löscht die Datei wieder, sobald der Absender „Für alle löschen“ wählt. Die alte Suche über
 * die Ordnerfreigabe (SAF) brauchte bei ~5.800 Bildern Minuten pro Abfrage und gab nach gut einer
 * Minute auf. Mit „Zugriff auf alle Dateien“ liest Hikari den Ordner direkt — eine Namensliste
 * dauert Millisekunden.
 *
 * Ablauf: Alle [SCAN_INTERVAL_MS] (und sofort, wenn Android eine Änderung meldet) werden die
 * Namen in WhatsApps Medienordnern mit dem letzten Stand verglichen. Eine neue Datei wird kopiert,
 * sobald ihre Größe stillsteht, und an [WhatsAppRepository.onIncomingMedia] übergeben. Was schon
 * beim Start im Ordner lag, wird nie angefasst.
 */
@Singleton
class WhatsAppMediaWatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: WhatsAppRepository,
) {
    private var job: Job? = null
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var observers: List<FileObserver> = emptyList()
    private var observedDirs: Set<String> = emptySet()

    /** Namen je Ordner beim letzten Durchlauf; ein Ordner ohne Eintrag wird erst vermessen. */
    private val known = HashMap<String, Set<String>>()

    /** Neue Dateien, die WhatsApp evtl. noch schreibt: Pfad -> zuletzt gesehene Größe. */
    private val growing = HashMap<String, Long>()

    fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            var announced: Boolean? = null
            while (isActive) {
                val access = hasAllFilesAccess()
                if (access != announced) {
                    WhatsAppProbe.note("watcher access=$access")
                    announced = access
                }
                if (access) {
                    runCatching { scanOnce() }.onFailure { WhatsAppProbe.note("watcher fehler ${it.javaClass.simpleName}") }
                    withTimeoutOrNull(SCAN_INTERVAL_MS) { wake.receive() }
                    // Kurz sammeln, damit eine gerade geschriebene Datei fertig wird.
                    delay(400)
                } else {
                    stopObservers()
                    known.clear()
                    delay(30_000)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        stopObservers()
    }

    private suspend fun scanOnce() {
        val dirs = mediaDirs()
        if (dirs.keys != observedDirs) watch(dirs.keys)

        for ((dir, type) in dirs) {
            val names = File(dir).list()?.toSet() ?: continue
            val before = known[dir]
            known[dir] = names
            if (before == null) continue // erster Blick: Bestand nur merken
            for (name in names - before) {
                if (name.startsWith(".")) continue
                growing["$dir/$name"] = -1L
            }
        }

        val now = System.currentTimeMillis()
        val done = mutableListOf<String>()
        for ((path, lastSize) in growing) {
            val file = File(path)
            if (!file.isFile) {
                done += path
                continue
            }
            val size = file.length()
            val settled = size > 0 && size == lastSize && now - file.lastModified() > 800
            if (!settled) {
                growing[path] = size
                continue
            }
            done += path
            if (size > MAX_BYTES) continue
            val type = dirs[file.parent.orEmpty()] ?: continue
            val copy = copyToVault(file, type) ?: continue
            val row = repository.onIncomingMedia(type, copy.absolutePath, file.lastModified())
            WhatsAppProbe.note("watcher datei type=$type name=${file.name} ${size / 1024}k nachricht=${row ?: "wartet"}")
        }
        done.forEach { growing.remove(it) }
    }

    private fun copyToVault(file: File, type: String): File? = runCatching {
        val dir = File(context.filesDir, WhatsAppRepository.MEDIA_DIR).apply { mkdirs() }
        val target = File(dir, "wa_${type}_in_${System.currentTimeMillis()}_${file.name}")
        file.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        target.takeIf { it.length() > 0 }
    }.getOrNull()

    /** WhatsApps Medienordner, die eingehende Dateien bekommen, mit ihrem Medientyp. */
    private fun mediaDirs(): Map<String, String> {
        val storage = Environment.getExternalStorageDirectory()
        val roots = listOf(
            File(storage, "Android/media/com.whatsapp/WhatsApp/Media"),
            File(storage, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media"),
        )
        val result = LinkedHashMap<String, String>()
        for (root in roots) {
            val children = root.listFiles()?.filter { it.isDirectory } ?: continue
            for (child in children) {
                val type = FOLDER_TYPES.entries.firstOrNull { child.name.endsWith(it.key, ignoreCase = true) }?.value
                    ?: continue
                result[child.path] = type
                // Medien-Sichtbarkeit aus -> "Private"; Sprachnachrichten in Wochenordnern.
                File(child, "Private").takeIf { it.isDirectory }?.let { result[it.path] = type }
                if (type != "voice") continue // nur dort Unterordner; Bilderordner nicht jedes Mal komplett lesen
                child.listFiles()
                    ?.filter { it.isDirectory && it.name.firstOrNull()?.isDigit() == true }
                    ?.sortedByDescending { it.name }
                    ?.take(2)
                    ?.forEach { result[it.path] = type }
            }
        }
        return result
    }

    private fun watch(dirs: Set<String>) {
        stopObservers()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val mask = FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO or FileObserver.CREATE
        observers = dirs.map { dir ->
            object : FileObserver(File(dir), mask) {
                override fun onEvent(event: Int, path: String?) {
                    wake.trySend(Unit)
                }
            }.also { it.startWatching() }
        }
        observedDirs = dirs
        WhatsAppProbe.note("watcher ordner=${dirs.size}")
    }

    private fun stopObservers() {
        observers.forEach { runCatching { it.stopWatching() } }
        observers = emptyList()
        observedDirs = emptySet()
    }

    private companion object {
        const val SCAN_INTERVAL_MS = 4_000L
        const val MAX_BYTES = 64L * 1024L * 1024L

        /** Endung des WhatsApp-Unterordners -> Medientyp wie im Parser. */
        val FOLDER_TYPES = linkedMapOf(
            "Images" to "photo",
            "Video" to "video",
            "Animated Gifs" to "gif",
            "Voice Notes" to "voice",
            "Audio" to "audio",
            "Documents" to "document",
            "Stickers" to "sticker",
        )
    }
}
