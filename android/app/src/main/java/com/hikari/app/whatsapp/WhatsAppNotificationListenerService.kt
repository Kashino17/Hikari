package com.hikari.app.whatsapp

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hikari.app.MainActivity
import com.hikari.app.R
import com.hikari.app.domain.whatsapp.RawMessagingItem
import com.hikari.app.domain.whatsapp.RawNotificationPayload
import com.hikari.app.domain.whatsapp.WhatsAppGuardAlert
import com.hikari.app.domain.whatsapp.WhatsAppMediaVault
import com.hikari.app.domain.whatsapp.WhatsAppNotificationParser
import com.hikari.app.domain.whatsapp.WhatsAppRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * Hintergrund-Wächter für WhatsApp & WhatsApp Business.
 *
 * Sobald der Nutzer in den Android-Systemeinstellungen den Benachrichtigungszugriff
 * für Hikari aktiviert hat, empfängt dieser Dienst alle eingehenden WhatsApp-Push-
 * Benachrichtigungen in Echtzeit:
 * - Normale Nachrichten werden kurzzeitig im flüchtigen Puffer gemerkt (nicht in der DB).
 * - Wird eine Nachricht später "Für alle gelöscht" (WhatsApp postet "Diese Nachricht
 *   wurde gelöscht"), sichert Hikari den Originaltext in Room (7 Tage Aufbewahrung).
 * - Wird eine Nachricht bearbeitet, sichert Hikari den Vorher-/Nachher-Verlauf.
 */
@AndroidEntryPoint
class WhatsAppNotificationListenerService : NotificationListenerService() {

    @Inject
    lateinit var repository: WhatsAppRepository

    @Inject
    lateinit var mediaVault: WhatsAppMediaVault

    private val alertScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Strikt sequenzielle Verarbeitung (FIFO), damit eine schnelle Löschnachricht
    // niemals die vorangehende Originalnachricht während des Avatar-Speicherns überholt!
    @OptIn(ExperimentalCoroutinesApi::class)
    private val processingScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO.limitedParallelism(1),
    )

    override fun onCreate() {
        super.onCreate()
        alertScope.launch {
            repository.guardAlerts.collect { alert ->
                showGuardNotification(alert)
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Bestehende aktive WhatsApp-Benachrichtigungen in der Statusleiste sofort einlesen,
        // damit auch bereits vor dem Verbinden eingetroffene Nachrichten bei Löschung gerettet werden.
        runCatching {
            activeNotifications?.forEach { sbn ->
                if (sbn != null && WhatsAppNotificationParser.isSupportedPackage(sbn.packageName.orEmpty())) {
                    enqueueNotification(sbn)
                }
            }
        }
    }

    override fun onDestroy() {
        processingScope.cancel()
        alertScope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return
        if (!WhatsAppNotificationParser.isSupportedPackage(pkg)) return
        enqueueNotification(sbn)
    }

    private fun enqueueNotification(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: Bundle.EMPTY

        val isSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        val isOngoing = sbn.isOngoing || (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
        if (isOngoing) return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        val convTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
        val isGroupConv = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        val messagingItems = extractMessagingItems(notification, extras)
        val textLines = extractTextLines(extras)

        val payload = RawNotificationPayload(
            packageName = pkg,
            notificationKey = sbn.key,
            notificationTag = sbn.tag,
            title = title,
            text = text,
            bigText = bigText,
            subText = subText,
            conversationTitle = convTitle,
            isGroupConversation = isGroupConv,
            isSummary = isSummary,
            isOngoing = isOngoing,
            category = notification.category,
            postTime = sbn.postTime,
            messagingStyleItems = messagingItems,
            textLines = textLines,
        )

        val rawChatName = convTitle?.takeIf { it.isNotBlank() } ?: title.orEmpty()
        val cleanChat = WhatsAppNotificationParser.cleanChatTitle(rawChatName.substringBefore(":"))

        processingScope.launch {
            val avatarPath = saveAvatarIfPresent(notification, pkg, cleanChat)
            val detectedMedia = WhatsAppNotificationParser.detectMediaType(
                (text ?: bigText).orEmpty(),
            )
            val mediaPreviewPath = saveMediaPictureIfPresent(extras, sbn.postTime)
                ?: mediaVault.backupLatestIncomingMedia(detectedMedia, sbn.postTime)
            repository.processNotification(
                payload = payload,
                avatarPath = avatarPath,
                mediaPreviewPath = mediaPreviewPath,
            )
        }
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification?,
        rankingMap: RankingMap?,
        reason: Int,
    ) {
        if (sbn == null) return
        val pkg = sbn.packageName.orEmpty()
        if (!WhatsAppNotificationParser.isSupportedPackage(pkg)) return

        // Falls WhatsApp die Benachrichtigung beim Löschen kurz aktualisiert und sofort entfernt:
        val extras = sbn.notification?.extras
        if (extras != null) {
            val removedText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            val removedBigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
            if (WhatsAppNotificationParser.isDeletedPlaceholder(removedText) ||
                removedBigText.lines().any { WhatsAppNotificationParser.isDeletedPlaceholder(it) }
            ) {
                enqueueNotification(sbn)
            }
        }

        // Sobald die Benachrichtigung aus der Statusleiste verschwindet (angetippt, weggewischt
        // oder durch Öffnen von WhatsApp geschlossen), aktiven Statusleisten-Snapshot leeren,
        // damit eine spätere neue Einzelnachricht niemals als Bearbeitung gewertet wird.
        // (Der flüchtige Puffer `transientRamCache` bleibt für Löschungen voll erhalten!)
        val key = sbn.key
        processingScope.launch {
            repository.onNotificationCleared(key)
        }
    }

    private fun extractTextLines(extras: Bundle): List<String> {
        val rawLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES) ?: return emptyList()
        return rawLines.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
    }

    @Suppress("DEPRECATION")
    private fun extractMessagingItems(
        notification: Notification,
        extras: Bundle,
    ): List<RawMessagingItem> {
        runCatching {
            val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
            if (style != null && style.messages.isNotEmpty()) {
                return style.messages.mapNotNull { msg ->
                    val txt = msg.text?.toString()?.trim().orEmpty()
                    if (txt.isEmpty()) return@mapNotNull null
                    val sender = msg.person?.name?.toString()
                    RawMessagingItem(
                        sender = sender,
                        text = txt,
                        timestamp = msg.timestamp,
                    )
                }
            }
        }

        // Fallback: Direktes Auslesen von EXTRA_MESSAGES (Parcelable-Array von Bundles)
        val rawArray: Array<Parcelable>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelableArray(Notification.EXTRA_MESSAGES, Parcelable::class.java)
        } else {
            extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        }
        if (rawArray.isNullOrEmpty()) return emptyList()

        return rawArray.mapNotNull { parcelable ->
            val bundle = parcelable as? Bundle ?: return@mapNotNull null
            val msgText = bundle.getCharSequence("text")?.toString()?.trim().orEmpty()
            if (msgText.isEmpty()) return@mapNotNull null
            val sender = bundle.getCharSequence("sender")?.toString()
            val time = bundle.getLong("time", 0L)
            RawMessagingItem(
                sender = sender,
                text = msgText,
                timestamp = time,
            )
        }
    }

    private fun saveAvatarIfPresent(
        notification: Notification,
        packageName: String,
        cleanChatTitle: String,
    ): String? {
        if (cleanChatTitle.isBlank()) return null
        return runCatching {
            val icon = notification.getLargeIcon() ?: return null
            val drawable = icon.loadDrawable(applicationContext) ?: return null
            val bitmap = drawableToBitmap(drawable, maxSize = 128) ?: return null
            val dir = File(applicationContext.filesDir, "whatsapp_avatars").apply { mkdirs() }
            val safeKey = "${packageName}_${cleanChatTitle.lowercase()}"
                .replace(Regex("[^a-z0-9_-]"), "_")
                .take(64)
            val file = File(dir, "$safeKey.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            file.absolutePath
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun saveMediaPictureIfPresent(extras: Bundle, postTime: Long): String? {
        return runCatching {
            val bmp: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extras.getParcelable(Notification.EXTRA_PICTURE, Bitmap::class.java)
            } else {
                extras.getParcelable(Notification.EXTRA_PICTURE) as? Bitmap
            }
            if (bmp == null) return null
            val dir = File(applicationContext.filesDir, "whatsapp_media").apply { mkdirs() }
            val file = File(dir, "wa_img_${postTime}.jpg")
            FileOutputStream(file).use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            file.absolutePath
        }.getOrNull()
    }

    private fun drawableToBitmap(drawable: Drawable, maxSize: Int): Bitmap? {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = drawable.intrinsicWidth.coerceIn(1, maxSize)
        val height = drawable.intrinsicHeight.coerceIn(1, maxSize)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun showGuardNotification(alert: WhatsAppGuardAlert) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ensureChannel()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "whatsapp")
        }
        val pending = PendingIntent.getActivity(
            this,
            4300,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val (title, body, notifId) = when (alert) {
            is WhatsAppGuardAlert.MessageDeleted -> {
                val who = if (alert.isGroup) "${alert.senderName} (${alert.chatTitle})" else alert.senderName
                Triple(
                    "🗑️ Gelöschte Nachricht gerettet",
                    "$who: „${alert.preservedText}“",
                    (4301 + (alert.chatTitle.hashCode() and 0x7FF)),
                )
            }
            is WhatsAppGuardAlert.MessageEdited -> {
                val who = if (alert.isGroup) "${alert.senderName} (${alert.chatTitle})" else alert.senderName
                Triple(
                    "✏️ Bearbeitete Nachricht erkannt",
                    "$who: „${alert.oldText}“ → „${alert.newText}“",
                    (5301 + (alert.chatTitle.hashCode() and 0x7FF)),
                )
            }
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        NotificationManagerCompat.from(this).notify(notifId, notification)
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "WhatsApp-Wächter (Gelöscht & Bearbeitet)",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Benachrichtigt dich, wenn eine WhatsApp-Nachricht für alle gelöscht oder bearbeitet wurde."
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "whatsapp_guard_alerts"

        /**
         * Prüft, ob Hikari in den Android-Einstellungen als NotificationListener freigeschaltet ist.
         */
        fun isNotificationAccessGranted(context: Context): Boolean {
            val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
            if (enabledPackages.contains(context.packageName)) return true
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ).orEmpty()
            return flat.contains(context.packageName)
        }

        /**
         * Stellt nach einem App-Update oder beim Öffnen der WhatsApp-Ecke sicher,
         * dass Android den NotificationListenerService sofort wieder aktiv bindet.
         */
        fun ensureServiceBound(context: Context) {
            if (!isNotificationAccessGranted(context)) return
            runCatching {
                val cn = ComponentName(context, WhatsAppNotificationListenerService::class.java)
                requestRebind(cn)
            }
        }
    }
}
