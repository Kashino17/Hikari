package com.hikari.app.voice

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.hikari.app.domain.voice.VoiceFormat
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Nimmt geteilte Audiodateien entgegen. Die Content-URIs von WhatsApp gelten
 * nur, solange die empfangende Activity lebt — deshalb wird sofort in den
 * App-Cache kopiert und erst dann im Hintergrund hochgeladen.
 */
object VoiceIntake {

    private const val BATCH_ROOT = "voice-share"

    /** Alle Audio-URIs aus ACTION_SEND / ACTION_SEND_MULTIPLE / ClipData. */
    fun urisFrom(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        val out = LinkedHashSet<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> intent.streamExtra()?.let(out::add)
            Intent.ACTION_SEND_MULTIPLE -> out.addAll(intent.streamListExtra())
        }
        intent.clipData?.let { clip -> for (i in 0 until clip.itemCount) clip.uriAt(i)?.let(out::add) }
        return out.toList()
    }

    private fun ClipData.uriAt(i: Int): Uri? = getItemAt(i)?.uri

    @Suppress("DEPRECATION")
    private fun Intent.streamExtra(): Uri? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else getParcelableExtra(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun Intent.streamListExtra(): List<Uri> =
        (if (Build.VERSION.SDK_INT >= 33) getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else getParcelableArrayListExtra(Intent.EXTRA_STREAM)) ?: emptyList()

    private fun displayName(ctx: Context, uri: Uri): String? = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment

    /**
     * Kopiert die Dateien nach cache/voice-share/<batch>/NN_<name>.
     * Gibt den Ordner zurück, oder null, wenn keine Datei lesbar war.
     */
    fun copyToBatch(ctx: Context, uris: List<Uri>): File? {
        val dir = File(ctx.cacheDir, "$BATCH_ROOT/${System.currentTimeMillis()}")
        dir.mkdirs()
        var copied = 0
        uris.forEachIndexed { i, uri ->
            val name = VoiceFormat.safeFileName(displayName(ctx, uri), i)
            val target = File(dir, "%02d_%s".format(i, name))
            val ok = runCatching {
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { input.copyTo(it) }
                } != null
            }.getOrDefault(false)
            if (ok && target.length() > 0) copied++ else target.delete()
        }
        if (copied == 0) {
            dir.deleteRecursively()
            return null
        }
        return dir
    }

    /** Dateien eines Batches in Upload-Reihenfolge, mit ursprünglichem Namen. */
    fun batchFiles(dir: File): List<Pair<File, String>> =
        (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && it.length() > 0 }
            .sortedBy { it.name }
            .map { it to it.name.replaceFirst(Regex("^\\d{2}_"), "") }

    /** Hochladen und Ergebnis abwarten — läuft auch, wenn Hikari geschlossen ist. */
    fun enqueue(ctx: Context, batchDir: File, label: String? = null) {
        val req = OneTimeWorkRequestBuilder<VoiceJobWorker>()
            .setInputData(
                workDataOf(
                    VoiceJobWorker.KEY_BATCH to batchDir.absolutePath,
                    VoiceJobWorker.KEY_LABEL to label,
                ),
            )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 20, TimeUnit.SECONDS)
            .addTag(VoiceJobWorker.TAG)
            .build()
        WorkManager.getInstance(ctx).enqueue(req)
    }
}
