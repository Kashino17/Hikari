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
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

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
 * Verwaltet den optionalen Ordner-Zugriff (Storage Access Framework via [DocumentsContract]) auf
 * `Android/media/com.whatsapp/WhatsApp/Media`:
 *
 * 1. **Auto-Backup von Sprachnachrichten & Fotos**:
 *    Wenn eine WhatsApp-Benachrichtigung über eine Sprachnachricht (`🎤`) oder ein
 *    Foto (`📷`) eingeht, kopiert [backupLatestIncomingMedia] die frisch empfangene
 *    Datei sofort in Hikaris geschützten Tresor (`filesDir/whatsapp_media/`).
 *    Löscht der Absender die Sprachnachricht oder das Foto später „Für alle“,
 *    löscht WhatsApp zwar die Originaldatei im WhatsApp-Ordner — Hikaris Kopie
 *    bleibt aber erhalten und direkt in Hikari abspielbar!
 *
 * 2. **Status-Saver**:
 *    Liest Bilder und Videos aus dem versteckten `.Statuses`-Unterordner und
 *    speichert sie auf Wunsch dauerhaft in der Geräte-Galerie.
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
     * Sucht bei Eingang einer Medien-Benachrichtigung ("photo", "voice", "audio", "video")
     * nach der jüngsten Datei im entsprechenden WhatsApp-Unterordner und sichert
     * eine lokale Kopie in `filesDir/whatsapp_media/`.
     */
    suspend fun backupLatestIncomingMedia(mediaType: String?, notificationTimeMs: Long): String? =
        withContext(Dispatchers.IO) {
            if (mediaType == null) return@withContext null
            val treeUri = getSavedTreeUri() ?: return@withContext null
            val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
                ?: return@withContext null

            val targetFolderNames = when (mediaType) {
                "voice" -> listOf("WhatsApp Voice Notes", "WhatsApp Audio")
                "photo" -> listOf("WhatsApp Images")
                "video" -> listOf("WhatsApp Video")
                "audio" -> listOf("WhatsApp Audio")
                "sticker" -> listOf("WhatsApp Stickers")
                "gif" -> listOf("WhatsApp Animated Gifs")
                else -> return@withContext null
            }

            var newestEntry: SafChildEntry? = null

            for (folderName in targetFolderNames) {
                val folderDocId = findSubfolderDocId(treeUri, rootDocId, folderName) ?: continue
                val children = listChildren(treeUri, folderDocId)
                val candidate = if (folderName == "WhatsApp Voice Notes") {
                    val latestSubDir = children
                        .filter { it.isDirectory }
                        .maxByOrNull { it.name }
                    val files = if (latestSubDir != null) {
                        listChildren(treeUri, latestSubDir.documentId)
                    } else {
                        children
                    }
                    files.filter { !it.isDirectory }.maxByOrNull { it.lastModified }
                } else {
                    children.filter { !it.isDirectory }.maxByOrNull { it.lastModified }
                }

                if (candidate != null && (newestEntry == null || candidate.lastModified > newestEntry.lastModified)) {
                    newestEntry = candidate
                }
            }

            val entry = newestEntry ?: return@withContext null
            val ageNow = abs(System.currentTimeMillis() - entry.lastModified)
            val ageNotif = abs(notificationTimeMs - entry.lastModified)
            if (ageNow > 90_000L && ageNotif > 90_000L) return@withContext null

            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, entry.documentId)
            return@withContext copyUriToVault(docUri, entry.name, entry.lastModified, mediaType)
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
        lastModified: Long,
        mediaType: String,
    ): String? {
        return runCatching {
            val ext = originalName.substringAfterLast('.', "").takeIf { it.isNotEmpty() }
                ?: when (mediaType) {
                    "voice" -> "opus"
                    "photo" -> "jpg"
                    "video" -> "mp4"
                    "audio" -> "mp3"
                    else -> "bin"
                }
            val dir = File(context.filesDir, "whatsapp_media").apply { mkdirs() }
            val safeName = "wa_${mediaType}_${lastModified}.$ext"
            val targetFile = File(dir, safeName)
            if (targetFile.exists() && targetFile.length() > 0L) {
                return targetFile.absolutePath
            }
            context.contentResolver.openInputStream(docUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            targetFile.takeIf { it.exists() && targetFile.length() > 0L }?.absolutePath
        }.getOrNull()
    }

    companion object {
        private const val KEY_TREE_URI = "whatsapp_media_tree_uri"
    }
}
