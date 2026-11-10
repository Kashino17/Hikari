package com.hikari.app.domain.whatsapp

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class WhatsAppStatusItem(
    val uri: String,
    val name: String,
    val isVideo: Boolean,
    val lastModified: Long,
    val sizeBytes: Long,
)

private data class SafChildEntry(
    val documentId: String,
    val name: String,
    val mimeType: String,
    val lastModified: Long,
    val sizeBytes: Long,
) {
    val isDirectory: Boolean
        get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
}

/**
 * Sichert Medien eingehender WhatsApp-Nachrichten, damit sie eine spätere Löschung überleben.
 *
 * WhatsApp verschlüsselt seine Datenbank, legt automatisch heruntergeladene Fotos, Videos und
 * Sprachnachrichten aber als normale Dateien unter `Android/media/com.whatsapp/WhatsApp/Media`
 * ab — und entfernt sie dort wieder, wenn der Absender „Für alle löschen“ wählt. Hikari kopiert
 * die Datei deshalb sofort beim Eintreffen in den eigenen Speicher (`filesDir/whatsapp_media`).
 * Dafür gibt der Nutzer einmalig den Medienordner frei (Storage Access Framework).
 *
 * Zwei Quellen, in dieser Reihenfolge:
 *  1. Bild-Anhang direkt an der Benachrichtigung (falls WhatsApp einen mitschickt).
 *  2. Die frisch heruntergeladene Datei im freigegebenen Medienordner.
 */
@Singleton
class WhatsAppMediaVault @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs by lazy {
        context.getSharedPreferences("hikari_whatsapp_media_prefs", Context.MODE_PRIVATE)
    }

    fun getSavedTreeUri(): Uri? {
        val raw = prefs.getString(KEY_TREE_URI, null) ?: return null
        return runCatching { Uri.parse(raw) }.getOrNull()
    }

    fun hasMediaFolderAccess(): Boolean {
        val uri = getSavedTreeUri() ?: return false
        return context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
    }

    fun saveTreeUri(uri: Uri) {
        folderCache.clear()
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        prefs.edit().putString(KEY_TREE_URI, uri.toString()).apply()
    }

    /**
     * Liefert die Initial-URI für `OpenDocumentTree`, damit der System-Dialog direkt
     * im WhatsApp-Medienordner (`Android/media/com.whatsapp/WhatsApp/Media`) startet.
     */
    fun buildInitialWhatsAppMediaUri(): Uri {
        return DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Android/media/com.whatsapp/WhatsApp/Media",
        )
    }

    /**
     * Sichert die Datei zu einer gerade eingetroffenen Mediennachricht und gibt den lokalen
     * Pfad zurück. WhatsApp lädt Medien oft erst Sekunden nach der Benachrichtigung herunter,
     * deshalb wird der Ordner mehrfach in wachsenden Abständen geprüft.
     */
    suspend fun captureForMessage(pending: PendingMedia): String? = withContext(Dispatchers.IO) {
        copyNotificationAttachment(pending)?.let { return@withContext it }

        val treeUri = getSavedTreeUri() ?: return@withContext null
        if (!hasMediaFolderAccess()) return@withContext null
        val folderSuffixes = FOLDER_SUFFIXES[pending.mediaType] ?: return@withContext null

        for (waitMs in CAPTURE_SCHEDULE_MS) {
            if (waitMs > 0L) delay(waitMs)
            val claimed = claimMutex.withLock {
                val candidate = findUnclaimedFile(treeUri, folderSuffixes, pending.seenAt) ?: return@withLock null
                claimedDocumentIds += candidate.documentId
                if (claimedDocumentIds.size > 600) {
                    claimedDocumentIds.iterator().let { it.next(); it.remove() }
                }
                candidate
            }
            if (claimed != null) {
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, claimed.documentId)
                return@withContext copyUriToVault(docUri, claimed.name, pending.rowId, pending.mediaType)
            }
        }
        null
    }

    private fun copyNotificationAttachment(pending: PendingMedia): String? {
        val raw = pending.dataUri ?: return null
        return runCatching {
            val ext = when {
                pending.dataMimeType?.contains("png") == true -> "png"
                pending.dataMimeType?.contains("webp") == true -> "webp"
                pending.dataMimeType?.contains("gif") == true -> "gif"
                pending.dataMimeType?.startsWith("video") == true -> "mp4"
                else -> "jpg"
            }
            copyUriToVault(Uri.parse(raw), "attachment.$ext", pending.rowId, pending.mediaType)
        }.getOrNull()
    }

    /**
     * Älteste noch nicht vergebene Datei, die rund um das Eintreffen der Nachricht entstanden
     * ist. „Älteste zuerst“ ordnet mehrere Bilder hintereinander in der richtigen Reihenfolge zu.
     */
    private fun findUnclaimedFile(treeUri: Uri, folderSuffixes: List<String>, seenAt: Long): SafChildEntry? {
        val earliest = seenAt - CAPTURE_LOOKBACK_MS
        return resolveMediaFolders(treeUri, folderSuffixes)
            .asSequence()
            .flatMap { folderDocId -> recentFilesIn(treeUri, folderDocId).asSequence() }
            .filter { entry ->
                entry.lastModified >= earliest &&
                    entry.sizeBytes in 1..MAX_MEDIA_BYTES &&
                    entry.documentId !in claimedDocumentIds &&
                    !entry.name.startsWith(".")
            }
            .minByOrNull { it.lastModified }
    }

    /** Dateien eines Medienordners; Sprachnachrichten liegen in Wochen-Unterordnern. */
    private fun recentFilesIn(treeUri: Uri, folderDocId: String): List<SafChildEntry> {
        // Kommen mehrere Bilder auf einmal, teilen sich die Suchläufe eine Ordnerabfrage —
        // der Bilderordner kann zehntausende Dateien enthalten.
        val now = System.currentTimeMillis()
        listingCache[folderDocId]?.let { (at, files) -> if (now - at < LISTING_REUSE_MS) return files }
        return readFilesIn(treeUri, folderDocId).also { listingCache[folderDocId] = now to it }
    }

    private fun readFilesIn(treeUri: Uri, folderDocId: String): List<SafChildEntry> {
        val children = listChildren(treeUri, folderDocId)
        val files = children.filter { !it.isDirectory }
        val weekFolders = children
            .filter { it.isDirectory && it.name.firstOrNull()?.isDigit() == true }
            .sortedByDescending { it.name }
            .take(2)
        return files + weekFolders.flatMap { week ->
            listChildren(treeUri, week.documentId).filter { !it.isDirectory }
        }
    }

    /**
     * Sucht die passenden Unterordner („WhatsApp Images“, „WhatsApp Business Images“ …) — egal ob
     * der Nutzer `Media`, `WhatsApp` oder `com.whatsapp` freigegeben hat.
     */
    private fun resolveMediaFolders(treeUri: Uri, folderSuffixes: List<String>): List<String> {
        val cacheKey = treeUri.toString() + "|" + folderSuffixes.joinToString(",")
        folderCache[cacheKey]?.let { return it }

        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return emptyList()
        var level = listOf(rootDocId)
        repeat(4) {
            val children = level.flatMap { listChildren(treeUri, it) }.filter { it.isDirectory }
            val hits = children.filter { dir ->
                folderSuffixes.any { suffix -> dir.name.endsWith(suffix, ignoreCase = true) }
            }
            if (hits.isNotEmpty()) {
                return hits.map { it.documentId }.also { folderCache[cacheKey] = it }
            }
            level = children
                .filter { dir -> CONTAINER_FOLDERS.any { dir.name.equals(it, ignoreCase = true) } }
                .map { it.documentId }
            if (level.isEmpty()) return emptyList()
        }
        return emptyList()
    }

    /**
     * Lädt alle aktuell im `.Statuses`-Ordner vorhandenen WhatsApp-Status-Bilder & -Videos.
     */
    suspend fun listWhatsAppStatuses(): List<WhatsAppStatusItem> = withContext(Dispatchers.IO) {
        val treeUri = getSavedTreeUri() ?: return@withContext emptyList()
        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return@withContext emptyList()

        val statusDocId = if (rootDocId.endsWith(".Statuses")) {
            rootDocId
        } else {
            findSubfolderDocId(treeUri, rootDocId, ".Statuses")
        } ?: return@withContext emptyList()

        listChildren(treeUri, statusDocId)
            .filter { entry ->
                val lower = entry.name.lowercase()
                !entry.isDirectory &&
                    !lower.endsWith(".nomedia") &&
                    (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
                        lower.endsWith(".mp4") || lower.endsWith(".webp"))
            }
            .map { entry ->
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, entry.documentId)
                WhatsAppStatusItem(
                    uri = docUri.toString(),
                    name = entry.name,
                    isVideo = entry.name.lowercase().endsWith(".mp4"),
                    lastModified = entry.lastModified,
                    sizeBytes = entry.sizeBytes,
                )
            }
            .sortedByDescending { it.lastModified }
    }

    /**
     * Speichert ein WhatsApp-Status-Element dauerhaft in der Geräte-Galerie (`Pictures/Hikari_WhatsApp`
     * bzw. `Movies/Hikari_WhatsApp`).
     */
    suspend fun saveStatusToGallery(status: WhatsAppStatusItem): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val sourceUri = Uri.parse(status.uri)
            val mimeType = if (status.isVideo) "video/mp4" else "image/jpeg"
            val collection = if (status.isVideo) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val relativePath = if (status.isVideo) {
                "${Environment.DIRECTORY_MOVIES}/Hikari_WhatsApp"
            } else {
                "${Environment.DIRECTORY_PICTURES}/Hikari_WhatsApp"
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "Hikari_${status.name}")
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                }
            }

            val resolver = context.contentResolver
            val destUri = resolver.insert(collection, values) ?: return@withContext false
            resolver.openInputStream(sourceUri)?.use { input ->
                resolver.openOutputStream(destUri)?.use { output ->
                    input.copyTo(output)
                }
            }
            true
        }.getOrDefault(false)
    }

    private fun findSubfolderDocId(treeUri: Uri, rootDocId: String, targetName: String): String? {
        val rootChildren = listChildren(treeUri, rootDocId)
        rootChildren.firstOrNull { it.isDirectory && it.name.equals(targetName, ignoreCase = true) }
            ?.let { return it.documentId }

        val mediaDir = rootChildren.firstOrNull { it.isDirectory && it.name.equals("Media", ignoreCase = true) }
            ?: return null
        return listChildren(treeUri, mediaDir.documentId)
            .firstOrNull { it.isDirectory && it.name.equals(targetName, ignoreCase = true) }
            ?.documentId
    }

    private fun listChildren(treeUri: Uri, parentDocId: String): List<SafChildEntry> {
        val result = mutableListOf<SafChildEntry>()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val modCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                while (cursor.moveToNext()) {
                    result.add(
                        SafChildEntry(
                            documentId = cursor.getString(idCol).orEmpty(),
                            name = cursor.getString(nameCol).orEmpty(),
                            mimeType = cursor.getString(mimeCol).orEmpty(),
                            lastModified = if (cursor.isNull(modCol)) 0L else cursor.getLong(modCol),
                            sizeBytes = if (cursor.isNull(sizeCol)) 0L else cursor.getLong(sizeCol),
                        ),
                    )
                }
            }
        }
        return result
    }

    private fun copyUriToVault(
        docUri: Uri,
        originalName: String,
        rowId: Long,
        mediaType: String,
    ): String? {
        return runCatching {
            val ext = originalName.substringAfterLast('.', "").takeIf { it.length in 1..5 }
                ?: when (mediaType) {
                    "voice" -> "opus"
                    "photo" -> "jpg"
                    "video", "gif" -> "mp4"
                    "audio" -> "mp3"
                    else -> "bin"
                }
            val dir = File(context.filesDir, WhatsAppRepository.MEDIA_DIR).apply { mkdirs() }
            val targetFile = File(dir, "wa_${mediaType}_${rowId}_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(docUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (targetFile.exists() && targetFile.length() > 0L) {
                targetFile.absolutePath
            } else {
                targetFile.delete()
                null
            }
        }.getOrNull()
    }

    private val claimMutex = Mutex()
    private val claimedDocumentIds = LinkedHashSet<String>()
    private val folderCache = java.util.concurrent.ConcurrentHashMap<String, List<String>>()
    private val listingCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<SafChildEntry>>>()

    companion object {
        private const val KEY_TREE_URI = "whatsapp_media_tree_uri"

        /** Medientyp -> Endung des WhatsApp-Unterordners. */
        private val FOLDER_SUFFIXES = mapOf(
            "photo" to listOf("Images"),
            "video" to listOf("Video"),
            "voice" to listOf("Voice Notes"),
            "audio" to listOf("Audio"),
            "gif" to listOf("Animated Gifs"),
            "document" to listOf("Documents"),
        )
        private val CONTAINER_FOLDERS = listOf("Media", "WhatsApp", "WhatsApp Business", "com.whatsapp", "com.whatsapp.w4b")

        /** Wartezeiten zwischen den Suchläufen (zusammen gut eine Minute). */
        private val CAPTURE_SCHEDULE_MS = listOf(0L, 3_000L, 10_000L, 25_000L, 40_000L)
        private const val LISTING_REUSE_MS = 1_500L
        private const val CAPTURE_LOOKBACK_MS = 120_000L
        private const val MAX_MEDIA_BYTES = 64L * 1024L * 1024L
    }
}
